package com.aiconsultant.consultant.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.ChatAgent;
import com.aiconsultant.consultant.mapper.ChatAgentMapper;
import com.aiconsultant.consultant.pojo.ChatAgentDTO;
import com.aiconsultant.consultant.pojo.ChatAgentDescription;
import com.aiconsultant.consultant.pojo.Result;
import com.aiconsultant.consultant.service.ChatAgentService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.aiconsultant.consultant.utils.Constants.*;

@Slf4j
@Service
public class ChatAgentServiceImpl extends ServiceImpl<ChatAgentMapper, ChatAgent> implements ChatAgentService {
    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ConcurrentHashMap<Long, CompletableFuture<ChatAgentDTO>> inflightRequests = new ConcurrentHashMap<>();

    private void waitForNotification(Long id) throws InterruptedException {
        String topicKey = AGENT_TOPIC_KEY + id;
        RTopic topic = redissonClient.getTopic(topicKey);
        CountDownLatch latch = new CountDownLatch(1);

        // 订阅主题
        int listenerId = topic.addListener(String.class, (channel, msg) -> {
            if ("WAKE_UP".equals(msg)) {
                latch.countDown();
            }
        });

        try {
            // 在指定时间内等待唤醒
            boolean notified = latch.await(2, TimeUnit.SECONDS);
            if (!notified) {
                log.warn("等待智能体数据加载超时, id: "+Long.toString(id));
            }
        } finally {
            // 务必移除监听器，防止内存泄漏
            topic.removeListener(listenerId);
        }
    }

    private ChatAgentDTO getFromDbWithLocalFlight(Long id) {
        // 1. 获取或创建航站楼凭证
        CompletableFuture<ChatAgentDTO> future = inflightRequests.computeIfAbsent(id, k -> {
            CompletableFuture<ChatAgentDTO> f = new CompletableFuture<>();
            // 建议：实际生产中这里可以用异步线程池处理，避免长时间占用 Map 的分段锁
            try {
                Thread.sleep(4000);
                ChatAgent agent = this.getById(id);
                ChatAgentDTO dto = new ChatAgentDTO();
                dto.setId(agent.getId());
                dto.setName(agent.getName());
                dto.setSystemPrompt(agent.getSystemPrompt());
                f.complete(dto);
            } catch (Exception ex) {
                f.completeExceptionally(ex);
            }
            return f;
        });

        try {
            // 2. 将 .join() 替换为 .get()，设置 5 秒硬超时
            return future.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 【关键】超时自救：立刻从 Map 移除该 ID，防止后续请求一直卡在同一个超时的 Future 上
            inflightRequests.remove(id);
            log.error("单飞查询超时，已强制移除阻塞任务, id: {}", id);
            throw new RuntimeException("系统响应超时，请稍后重试");
        } catch (InterruptedException | ExecutionException e) {
            // 业务逻辑报错或线程中断
            inflightRequests.remove(id);
            log.error("单飞查询异常, id: {}", id, e);
            throw new RuntimeException("查询失败: " + e.getMessage());
        }
    }

    @Override
    public ChatAgentDTO getAgentById(Long id) {
        // 2. 进来先看“航站楼”里有没有对应的航线
        // 使用 computeIfAbsent 保证原子性：如果没有则创建一个新的 Future，如果有则返回已有的
        boolean[] isLeader = {false};
        CompletableFuture<ChatAgentDTO> future = inflightRequests.computeIfAbsent(id, k -> {
            isLeader[0] = true; // 标记当前线程为“领头羊”
            return new CompletableFuture<>();
        });

        // 3. 情况 A：如果是跟随者（不是领头羊）
        if (!isLeader[0]) {
            log.info("【单飞模式】发现已有线程在请求 id: {}，进入队列等待...", id);
            try {
                // 原地挂起，等待领头羊完成并填充结果
                return future.get(15, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("等待领头羊结果超时/异常, id: {}", id);
                throw new RuntimeException("系统繁忙，请稍后再试");
            }
        }

        // 4. 情况 B：如果是领头羊，执行后续的重试、Redis 缓存、分布式锁逻辑
        try {
            log.info("【单飞模式】我是领头羊，开始执行查询逻辑 id: {}", id);
//            Thread.sleep(4000);
            // 这里放入你之前写的那个带 3 次重试、分布式锁、Redis 查询、Pub/Sub 的逻辑
            ChatAgentDTO result = executeComplexGetAgentLogic(id);

            // 5. 任务完成，填入结果，唤醒所有在 future 上等待的跟随者
            future.complete(result);
            return result;
        }
//        catch(InterruptedException e){
//            Thread.currentThread().interrupt();
//             // 2. 这里的后续处理同你之前的 Throwable 逻辑
//            future.completeExceptionally(e);
//            return null;
//        }
        catch (Exception e) {
            // 发生异常也要唤醒，否则跟随者会卡死到超时
            future.completeExceptionally(e);
            throw e;
        } finally {
            // 6. 航程结束，从航站楼移除，允许下一波请求重新起飞
            inflightRequests.remove(id);
        }
    }

    public ChatAgentDTO executeComplexGetAgentLogic(Long id) {
        int retryCount = 0;
        String cacheKey = AGENT_CACHE_KEY + id;

        // 循环尝试，直到获取结果
        while (retryCount < 3) {
            // 1. 先去 redis 缓存中查询
            String json = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, ChatAgentDTO.class);
            }

            // 2. 若缓存不存在，则尝试获取分布式锁
            String lockKey = AGENT_LOCK_KEY + id;
            RLock lock = redissonClient.getLock(lockKey);

            try {
                // 尝试获取锁（不等待，立即返回结果）
                if (lock.tryLock(0, 10, TimeUnit.SECONDS)) {
                    try {
                        // 2.1 获取锁成功，双重检查缓存（防止在竞争锁期间其他线程已写回）
                        json = stringRedisTemplate.opsForValue().get(cacheKey);
                        if (StrUtil.isNotBlank(json)) {
                            if ("null".equals(json)) return null;
                            return JSONUtil.toBean(json, ChatAgentDTO.class);
                        }

                        // 执行数据库查询
                        ChatAgent agent = this.getById(id);
                        if (agent == null) {
                            stringRedisTemplate.opsForValue().set(cacheKey, "null", 5, TimeUnit.MINUTES);
                            return null;//智能体不存在，设空值并返回null
                        }

                        ChatAgentDTO dto = new ChatAgentDTO();
                        BeanUtil.copyProperties(agent, dto);

                        // 3. 查询完成后写回缓存
                        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(dto), 1, TimeUnit.DAYS);

                        // 发布通知唤醒队列里的所有线程
                        RTopic topic = redissonClient.getTopic(AGENT_TOPIC_KEY + id);
                        topic.publish("WAKE_UP");

                        return dto;
                    } finally {
                        lock.unlock();
                    }
                } else {
                    // 2.2 获取锁不成功，进入基于 Pub/Sub 的等待队列
                    waitForNotification(id);

                    // 4. 被唤醒后随机等待一段时间，避免惊群效应
                    long jitter = ThreadLocalRandom.current().nextLong(50, 200);
                    Thread.sleep(jitter);
                    retryCount++;
                    // 进入下一次循环，重新查询缓存或竞争锁
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("查询智能体过程被中断", e);
            } catch (RedisException e){
                return getFromDbWithLocalFlight(id);
            }
        }
        // 4. 退出循环后的兜底逻辑：返回失败或抛出自定义异常
        // 这里建议抛出异常或返回特定标识，由 Controller 层封装为 Result.fail()
        throw new RuntimeException("当前咨询人数过多，请稍后再试");
    }

    @Override
    public Result getAgentDescriptionList() {
        // 1. 从数据库查询所有智能体记录
        List<ChatAgent> agents = this.list();

        // 2. 将 Entity 转换为精简的 DTO
        return Result.ok(agents.stream().map(agent -> {
            ChatAgentDescription dto = new ChatAgentDescription();
            dto.setId(agent.getId());
            dto.setName(agent.getName());
            dto.setDescription(agent.getDescription());
            return dto;
        }).collect(Collectors.toList()));
    }
}
