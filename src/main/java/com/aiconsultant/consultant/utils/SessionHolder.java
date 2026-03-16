package com.aiconsultant.consultant.utils;

public class SessionHolder {
    // 存储经过校验后的 sessionId
    private static final ThreadLocal<Long> tl = new ThreadLocal<>();

    public static void saveSessionId(Long sessionId){
        tl.set(sessionId);
    }

    public static Long getSessionId(){
        return tl.get();
    }

    public static void removeSession(){
        tl.remove();
    }
}
