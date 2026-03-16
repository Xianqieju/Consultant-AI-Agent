package com.aiconsultant.consultant.utils;

import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisScript {
    public static final DefaultRedisScript<Long> LOCK_SCRIPT;

    public static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        LOCK_SCRIPT = new DefaultRedisScript<>();
        LOCK_SCRIPT.setScriptText(
                "if redis.call('EXISTS', KEYS[1]) == 1 or redis.call('EXISTS', KEYS[2]) == 1 then " +
                        "  return 0 " +
                        "end " +
                        "redis.call('SET', KEYS[1], ARGV[1], 'PX', ARGV[2]) " +
                        "redis.call('SET', KEYS[2], ARGV[1], 'PX', ARGV[2]) " +
                        "return 1"
        );
        LOCK_SCRIPT.setResultType(Long.class);
    }

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "local count = 0 " +
                        "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
                        "  count = count + redis.call('DEL', KEYS[1]) " +
                        "end " +
                        "if redis.call('GET', KEYS[2]) == ARGV[1] then " +
                        "  count = count + redis.call('DEL', KEYS[2]) " +
                        "end " +
                        "return count"
        );
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

}
