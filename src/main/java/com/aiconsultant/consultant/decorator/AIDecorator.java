package com.aiconsultant.consultant.decorator;

import com.aiconsultant.consultant.pojo.UserDTO;
import com.aiconsultant.consultant.utils.SessionHolder;
import com.aiconsultant.consultant.utils.UserHolder;
import org.springframework.core.task.TaskDecorator;

public class AIDecorator implements TaskDecorator{

    @Override
    public Runnable decorate(Runnable runnable) {


        UserDTO user = UserHolder.getUser();
        Long sessionId = SessionHolder.getSessionId();

        return new Runnable() {
            @Override
            public void run() {

                try {
                    // 1. 上下文重构：把主线程传过来的变量，塞进当前异步线程的 ThreadLocal
                    UserHolder.saveUser(user);
                    SessionHolder.saveSessionId(sessionId);

                    // 2. 执行核心：调用最初始的那个业务逻辑
                    runnable.run();

                } finally {
                    // 3. 安全清理：异步线程执行完毕，必须清空，避免线程复用导致数据串乱
                    UserHolder.removeUser();
                    SessionHolder.removeSession();
                }
            }
        };
    }
}
