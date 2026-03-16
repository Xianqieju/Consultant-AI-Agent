package com.aiconsultant.consultant.utils;

import org.springframework.stereotype.Component;

/**
 * Twitter Snowflake 算法实现
 * 生成的 ID 是一个 64 位的 long 数字
 */
@Component
public class SnowflakeIdWorker {

    // 开始时间截 (2024-01-01)
    private final long twepoch = 1704067200000L;

    // 机器id所占的位数 (可根据实际情况调整，例如5位机房ID，5位机器ID)
    private final long workerIdBits = 10L;
    // 序列在id中占的位数
    private final long sequenceBits = 12L;

    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    private long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdWorker() {
        // 默认构造，实际分布式环境中建议从配置或注册中心读取 workerId
        this.workerId = 1L;
    }

    public SnowflakeIdWorker(long workerId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
        }
        this.workerId = workerId;
    }

    public synchronized long nextId() {
        long timestamp = timeGen();

        // 如果当前时间小于上一次ID生成的时间戳，说明系统时钟回退过，应当抛出异常
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("系统时钟回退，拒绝生成 ID");
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            // 毫秒内序列溢出，等待下一毫秒
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 时间戳改变，毫秒内序列重置
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 移位并通过或运算拼到一起组成64位的ID
        return ((timestamp - twepoch) << (workerIdBits + sequenceBits))
                | (workerId << sequenceBits)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }
}
