package com.aiconsultant.consultant.manager;

import com.aiconsultant.consultant.service.TokenBlacklistService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.ByteArrayCodec;
import org.roaringbitmap.longlong.Roaring64NavigableMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.aiconsultant.consultant.entity.OauthTokenBlacklist;
import java.io.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Component
@Slf4j
public class LocalTokenBlacklistManager {

    // 支持 64 位长整型的咆哮位图
    private final Roaring64NavigableMap blacklist = new Roaring64NavigableMap();

    // 读写锁，保障并发安全，读多写少场景下性能优异
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final String SNAPSHOT_KEY = "token:blacklist:snapshot:data";
    private static final String SNAPSHOT_TIME_KEY = "token:blacklist:snapshot:time";

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void generateSnapshot() {
        log.info("开始生成 RoaringBitmap 二进制快照...");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) {

            // 获取读锁，防止在序列化时发生结构变更
            lock.readLock().lock();
            try {
                // RoaringBitmap 原生支持高效序列化
                blacklist.serialize(dos);
            } finally {
                lock.readLock().unlock();
            }

            byte[] snapshotBytes = bos.toByteArray();

            // 使用 ByteArrayCodec 确保以纯二进制格式写入 Redis
            RBucket<byte[]> dataBucket = redissonClient.getBucket(SNAPSHOT_KEY, ByteArrayCodec.INSTANCE);
            dataBucket.set(snapshotBytes);

            // 记录当前快照的时间戳，用于后续增量查询
            RBucket<LocalDateTime> timeBucket = redissonClient.getBucket(SNAPSHOT_TIME_KEY);
            timeBucket.set(LocalDateTime.now());

            log.info("快照生成完成，大小: {} bytes", snapshotBytes.length);

        } catch (IOException e) {
            log.error("咆哮位图序列化失败", e);
        }
    }

    @PostConstruct
    public void initBlacklistOnStartup() {
        log.info("开始初始化黑名单位图...");

        // 1. 尝试从 Redis 读取快照数据和快照时间
        RBucket<byte[]> dataBucket = redissonClient.getBucket(SNAPSHOT_KEY, ByteArrayCodec.INSTANCE);
        byte[] snapshotBytes = dataBucket.get();

        RBucket<LocalDateTime> timeBucket = redissonClient.getBucket(SNAPSHOT_TIME_KEY);
        LocalDateTime lastSnapshotTime = timeBucket.get();

        lock.writeLock().lock();
        try {
            if (snapshotBytes != null && snapshotBytes.length > 0) {
                // 2A. 存在快照，进行反序列化
                try (ByteArrayInputStream bis = new ByteArrayInputStream(snapshotBytes);
                     DataInputStream dis = new DataInputStream(bis)) {

                    // 覆盖当前的空位图
                    blacklist.deserialize(dis);
                    log.info("快照反序列化完成，恢复至时间点: {}", lastSnapshotTime);
                } catch (IOException e) {
                    log.error("反序列化失败，退化为全量拉取", e);
                    lastSnapshotTime = null; // 标记为拉取全量
                }
            } else {
                log.info("未找到历史快照，准备进行全量构建");
            }

            // 3. 确定从数据库拉取数据的起点时间
            // 如果没快照，拉取起点设为 7 天前（因为 RT 最长 7 天，再早的数据无意义）
            LocalDateTime fetchStartTime = (lastSnapshotTime != null) ?
                    lastSnapshotTime : LocalDateTime.now().minusDays(7);

            // 4. 从数据库获取增量（或全量）数据
            // SQL 逻辑：SELECT token_id FROM oauth_token_blacklist WHERE create_time >= #{fetchStartTime} AND expire_time > NOW()
            List<Long> deltaIds = tokenBlacklistService.lambdaQuery()
                    // 1. 筛选：快照时间之后产生的数据
                    .ge(OauthTokenBlacklist::getCreateTime, fetchStartTime)
                    // 2. 筛选：依然在物理有效期内的 Token (未到 expire_time)
                    .gt(OauthTokenBlacklist::getExpireTime, LocalDateTime.now())
                    // 3. 投影：只查询 tokenId 这一列，减少网络传输开销
                    .select(OauthTokenBlacklist::getTokenId)
                    // 执行查询获取实体列表
                    .list()
                    // 4. 转换：将 List<OauthTokenBlacklist> 提取为 List<Long>
                    .stream()
                    .map(OauthTokenBlacklist::getTokenId)
                    .collect(Collectors.toList());


            // 5. 补偿增量数据
            if (deltaIds != null && !deltaIds.isEmpty()) {
                for (Long id : deltaIds) {
                    blacklist.addLong(id);
                }
                log.info("增量数据补偿完成，共补齐 {} 条记录", deltaIds.size());
            }

            // 优化内存结构
            blacklist.runOptimize();

        } finally {
            lock.writeLock().unlock();
        }
        log.info("黑名单位图初始化完毕");
    }
    /**
     * 加入黑名单 (写操作)
     */
    public void addBlacklist(Long tokenId) {
        lock.writeLock().lock();
        try {
            blacklist.addLong(tokenId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 校验是否在黑名单中 (读操作，纯本地计算)
     */
    public boolean isBlacklisted(Long tokenId) {
        lock.readLock().lock();
        try {
            return blacklist.contains(tokenId);
        } finally {
            lock.readLock().unlock();
        }
    }
}
