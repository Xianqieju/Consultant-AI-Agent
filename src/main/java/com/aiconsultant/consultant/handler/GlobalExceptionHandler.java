package com.aiconsultant.consultant.handler;

import com.aiconsultant.consultant.exception.BusinessException;
import com.aiconsultant.consultant.pojo.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截我们自定义的业务异常 (例如 Token 过期、签名错误)
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        // 业务异常通常是预料之中的，打 warn 日志即可
        log.warn("业务拦截命中: code={}, msg={}", e.getCode(), e.getMessage());

        // 严格遵守 Result.fail 只接受 String 的规范
        // 前端通过 HTTP 状态码 401 配合这里的特定提示语来进行后续操作（跳转或刷新）
        return Result.fail(e.getMessage());
    }

    /**
     * 拦截所有未知的系统运行时异常 (兜底)
     */
    @ExceptionHandler(Exception.class)
    public Result handleSystemException(Exception e) {
        // 未知异常可能是严重的 Bug，打 error 日志并记录堆栈
        log.error("系统发生未知异常", e);
        return Result.fail("服务器内部繁忙，请稍后再试");
    }
}