package com.aiconsultant.consultant.utils;

public class Constants {
    public static final String SALT = "1133567";
    public static final String PREFIX_TOKEN = "token:user:";
    public static final Long TOKEN_TTL = 30L;

    public static final String PREFIX_REGISTER_PHONE = "register:phone:";
    public static final String PREFIX_REGISTER_USERNAME = "register:username:";
    public static final String REGISTER_TTL = "10000";

    public static final Integer DEFAULT_QUOTA = 10;

    public static final String EXCHANGE_NAME = "chat.exchange";
    public static final String ROUTING_KEY = "chat.history.routing";

    public static final String AGENT_CACHE_KEY = "chat:agent:detail:";
    public static final String AGENT_LOCK_KEY = "chat:agent:lock:";
    public static final String AGENT_TOPIC_KEY = "chat:agent:topic:";
}
