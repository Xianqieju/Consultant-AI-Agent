package com.aiconsultant.consultant.notegrading;

import com.aiconsultant.consultant.config.NoteGradingPoolProperties;
import com.aiconsultant.consultant.notegrading.audit.NoteAuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Persists note-grading pipeline state in a Redis Hash (parallel-safe per-worker fields).
 */
@Slf4j
@Component
public class NoteGradingPoolStore {

    public static final String F_STATUS = "status";
    public static final String F_FULL_NOTE = "fullNote";
    public static final String F_SUPERVISOR_RAW = "supervisorRaw";
    public static final String F_EXPERT_INTERNAL_NETWORK_OUT = "expertInternalNetworkOut";
    public static final String F_EXPERT_DEV_GUIDE_OUT = "expertDevGuideOut";
    public static final String F_EXPERT_EMPLOYEE_POLICY_OUT = "expertEmployeePolicyOut";
    public static final String F_COMBINED_DRAFT = "combinedDraft";
    /** 聚合器输出（排版前） */
    public static final String F_AGGREGATOR_RAW_OUT = "aggregatorRawOut";
    /** 用户可见终稿（排版后） */
    public static final String F_AGGREGATOR_OUT = "aggregatorOut";
    public static final String F_QUALITY_REPORT_JSON = "qualityReportJson";
    public static final String F_ERROR = "error";
    public static final String F_CREATED_AT = "createdAt";
    public static final String F_UPDATED_AT = "updatedAt";
    public static final String F_SESSION_ID = "sessionId";
    public static final String F_USER_ID = "userId";
    public static final String F_PIPELINE_ID = "pipelineId";
    public static final String F_FALLBACK_USED = "fallbackUsed";
    public static final String F_FALLBACK_OUT = "fallbackOut";
    public static final String F_TECH_GUIDE_PLAN_JSON = "techGuidePlanJson";
    public static final String F_TECH_GUIDE_SCORES_JSON = "techGuideScoresJson";
    public static final String F_TASK_COUNT = "taskCount";
    public static final String F_COMPLETED_TASK_COUNT = "completedTaskCount";
    public static final String F_AUDIT_VERSION = "auditVersion";
    public static final String F_SCHEMA_VERSION = "schemaVersion";

    public static final String ST_INIT = "INIT";
    public static final String ST_SUPERVISOR_DONE = "SUPERVISOR_DONE";
    public static final String ST_WORKERS_DONE = "WORKERS_DONE";
    public static final String ST_AGGREGATOR_DONE = "AGGREGATOR_DONE";
    public static final String ST_FAILED = "FAILED";
    public static final String ST_FALLBACK_DONE = "FALLBACK_DONE";

    private static final int ERROR_MAX_LEN = 2000;

    private final StringRedisTemplate redisTemplate;
    private final NoteGradingPoolProperties properties;

    public NoteGradingPoolStore(StringRedisTemplate redisTemplate, NoteGradingPoolProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public void initPool(String redisKey, String pipelineId, Long sessionId, Long userId, String fullNote) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            Map<String, String> row = new HashMap<>();
            row.put(F_PIPELINE_ID, pipelineId == null ? "" : pipelineId);
            row.put(F_STATUS, ST_INIT);
            row.put(F_CREATED_AT, Long.toString(now));
            row.put(F_UPDATED_AT, Long.toString(now));
            if (sessionId != null) {
                row.put(F_SESSION_ID, String.valueOf(sessionId));
            }
            if (userId != null) {
                row.put(F_USER_ID, String.valueOf(userId));
            }
            if (properties.isStoreFullNote() && fullNote != null) {
                row.put(F_FULL_NOTE, fullNote);
            }
            redisTemplate.opsForHash().putAll(redisKey, row);
            redisTemplate.expire(redisKey, properties.getTtlHours(), TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("note-grading pool init failed key={}", redisKey, e);
        }
    }

    public void saveSupervisor(String redisKey, String supervisorRaw) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForHash().put(redisKey, F_SUPERVISOR_RAW, supervisorRaw == null ? "" : supervisorRaw);
            redisTemplate.opsForHash().put(redisKey, F_STATUS, ST_SUPERVISOR_DONE);
            redisTemplate.opsForHash().put(redisKey, F_UPDATED_AT, Long.toString(now));
            touchExpire(redisKey);
        } catch (Exception e) {
            log.warn("note-grading pool saveSupervisor failed key={}", redisKey, e);
        }
    }

    public void saveTechGuidePlan(String redisKey, String planJson, String scoreJson, int taskCount, String schemaVersion) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForHash().put(redisKey, F_TECH_GUIDE_PLAN_JSON, planJson == null ? "" : planJson);
            redisTemplate.opsForHash().put(redisKey, F_TECH_GUIDE_SCORES_JSON, scoreJson == null ? "" : scoreJson);
            redisTemplate.opsForHash().put(redisKey, F_TASK_COUNT, Integer.toString(Math.max(taskCount, 0)));
            redisTemplate.opsForHash().put(redisKey, F_COMPLETED_TASK_COUNT, "0");
            redisTemplate.opsForHash().put(redisKey, F_AUDIT_VERSION, "v1");
            if (schemaVersion != null && !schemaVersion.isBlank()) {
                redisTemplate.opsForHash().put(redisKey, F_SCHEMA_VERSION, schemaVersion);
            }
            redisTemplate.opsForHash().put(redisKey, F_STATUS, ST_SUPERVISOR_DONE);
            redisTemplate.opsForHash().put(redisKey, F_UPDATED_AT, Long.toString(now));
            touchExpire(redisKey);
        } catch (Exception e) {
            log.warn("note-grading pool saveTechGuidePlan failed key={}", redisKey, e);
        }
    }

    public void saveWorkerOutput(String redisKey, String hashField, String text) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForHash().put(redisKey, hashField, text == null ? "" : text);
            redisTemplate.opsForHash().put(redisKey, F_UPDATED_AT, Long.toString(now));
            touchExpire(redisKey);
        } catch (Exception e) {
            log.warn("note-grading pool saveWorkerOutput failed key={} field={}", redisKey, hashField, e);
        }
    }

    public void saveTaskResult(String redisKey, String taskId, String text, int completedTaskCount) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            String field = "taskResult:" + (taskId == null ? "unknown" : taskId);
            redisTemplate.opsForHash().put(redisKey, field, text == null ? "" : text);
            redisTemplate.opsForHash().put(redisKey, F_COMPLETED_TASK_COUNT, Integer.toString(Math.max(completedTaskCount, 0)));
            redisTemplate.opsForHash().put(redisKey, F_UPDATED_AT, Long.toString(now));
            touchExpire(redisKey);
        } catch (Exception e) {
            log.warn("note-grading pool saveTaskResult failed key={} taskId={}", redisKey, taskId, e);
        }
    }

    public void saveCombinedDraft(String redisKey, String combinedDraft) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForHash().put(redisKey, F_COMBINED_DRAFT, combinedDraft == null ? "" : combinedDraft);
            redisTemplate.opsForHash().put(redisKey, F_STATUS, ST_WORKERS_DONE);
            redisTemplate.opsForHash().put(redisKey, F_UPDATED_AT, Long.toString(now));
            touchExpire(redisKey);
        } catch (Exception e) {
            log.warn("note-grading pool saveCombinedDraft failed key={}", redisKey, e);
        }
    }

    public void saveAggregatorRaw(String redisKey, String rawText) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForHash().put(redisKey, F_AGGREGATOR_RAW_OUT, rawText == null ? "" : rawText);
            redisTemplate.opsForHash().put(redisKey, F_UPDATED_AT, Long.toString(now));
            touchExpire(redisKey);
        } catch (Exception e) {
            log.warn("note-grading pool saveAggregatorRaw failed key={}", redisKey, e);
        }
    }

    public void saveQualityReport(String redisKey, String qualityJson) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForHash().put(redisKey, F_QUALITY_REPORT_JSON, qualityJson == null ? "" : qualityJson);
            redisTemplate.opsForHash().put(redisKey, F_UPDATED_AT, Long.toString(now));
            touchExpire(redisKey);
        } catch (Exception e) {
            log.warn("note-grading pool saveQualityReport failed key={}", redisKey, e);
        }
    }

    public void saveFinal(String redisKey, String finalUserText) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForHash().put(redisKey, F_AGGREGATOR_OUT, finalUserText == null ? "" : finalUserText);
            redisTemplate.opsForHash().put(redisKey, F_STATUS, ST_AGGREGATOR_DONE);
            redisTemplate.opsForHash().put(redisKey, F_UPDATED_AT, Long.toString(now));
            int shortTtl = properties.getAfterSuccessTtlHours();
            if (shortTtl > 0) {
                redisTemplate.expire(redisKey, shortTtl, TimeUnit.HOURS);
            } else {
                touchExpire(redisKey);
            }
        } catch (Exception e) {
            log.warn("note-grading pool saveFinal failed key={}", redisKey, e);
        }
    }

    public void markFailed(String redisKey, Throwable error) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            String msg = error == null ? "" : String.valueOf(error.getMessage());
            if (msg.length() > ERROR_MAX_LEN) {
                msg = msg.substring(0, ERROR_MAX_LEN);
            }
            redisTemplate.opsForHash().put(redisKey, F_ERROR, msg);
            redisTemplate.opsForHash().put(redisKey, F_STATUS, ST_FAILED);
            redisTemplate.opsForHash().put(redisKey, F_UPDATED_AT, Long.toString(now));
            touchExpire(redisKey);
        } catch (Exception e) {
            log.warn("note-grading pool markFailed failed key={}", redisKey, e);
        }
    }

    private void touchExpire(String redisKey) {
        redisTemplate.expire(redisKey, properties.getTtlHours(), TimeUnit.HOURS);
    }

    /**
     * Concatenate partial pipeline fields for fallback summarizer context (truncated).
     */
    public String readFallbackContextSummary(String redisKey, int maxChars) {
        if (!properties.isEnabled() || redisKey == null || maxChars <= 0) {
            return "";
        }
        try {
            StringBuilder sb = new StringBuilder();
            appendField(sb, "supervisorRaw", redisKey, F_SUPERVISOR_RAW, maxChars);
            appendField(sb, "expertInternalNetworkOut", redisKey, F_EXPERT_INTERNAL_NETWORK_OUT, maxChars);
            appendField(sb, "expertDevGuideOut", redisKey, F_EXPERT_DEV_GUIDE_OUT, maxChars);
            appendField(sb, "expertEmployeePolicyOut", redisKey, F_EXPERT_EMPLOYEE_POLICY_OUT, maxChars);
            appendField(sb, "aggregatorRawOut", redisKey, F_AGGREGATOR_RAW_OUT, maxChars);
            appendField(sb, "combinedDraft", redisKey, F_COMBINED_DRAFT, maxChars);
            if (sb.length() > maxChars) {
                return sb.substring(0, maxChars) + "\n...[truncated]";
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("note-grading pool readFallbackContext failed key={}", redisKey, e);
            return "";
        }
    }

    private void appendField(StringBuilder sb, String label, String redisKey, String field, int budget) {
        Object v = redisTemplate.opsForHash().get(redisKey, field);
        if (v == null) {
            return;
        }
        String s = String.valueOf(v);
        if (s.isBlank()) {
            return;
        }
        int per = Math.min(2000, Math.max(200, budget / 5));
        if (s.length() > per) {
            s = s.substring(0, per) + "...[truncated]";
        }
        sb.append("--- ").append(label).append(" ---\n").append(s).append('\n');
    }

    public void saveFallback(String redisKey, String userVisibleText) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            long now = System.currentTimeMillis();
            String text = userVisibleText == null ? "" : userVisibleText;
            redisTemplate.opsForHash().put(redisKey, F_FALLBACK_USED, "true");
            redisTemplate.opsForHash().put(redisKey, F_FALLBACK_OUT, text);
            redisTemplate.opsForHash().put(redisKey, F_AGGREGATOR_OUT, text);
            redisTemplate.opsForHash().put(redisKey, F_STATUS, ST_FALLBACK_DONE);
            redisTemplate.opsForHash().put(redisKey, F_UPDATED_AT, Long.toString(now));
            int shortTtl = properties.getAfterSuccessTtlHours();
            if (shortTtl > 0) {
                redisTemplate.expire(redisKey, shortTtl, TimeUnit.HOURS);
            } else {
                touchExpire(redisKey);
            }
        } catch (Exception e) {
            log.warn("note-grading pool saveFallback failed key={}", redisKey, e);
        }
    }

    public void appendAuditEvent(String eventsKey, NoteAuditEvent event, int ttlHours) {
        if (!properties.isEnabled() || eventsKey == null || eventsKey.isBlank() || event == null) {
            return;
        }
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("eventType", nz(event.getEventType()));
            body.put("stage", nz(event.getStage()));
            body.put("taskId", nz(event.getTaskId()));
            body.put("agentRole", nz(event.getAgentRole()));
            body.put("attempt", Integer.toString(event.getAttempt()));
            body.put("status", nz(event.getStatus()));
            body.put("durationMs", Long.toString(event.getDurationMs()));
            body.put("error", nz(event.getError()));
            body.put("payloadRef", nz(event.getPayloadRef()));
            body.put("ts", Long.toString(event.getTs() <= 0 ? System.currentTimeMillis() : event.getTs()));
            redisTemplate.opsForStream().add(eventsKey, body);
            if (ttlHours > 0) {
                redisTemplate.expire(eventsKey, ttlHours, TimeUnit.HOURS);
            } else {
                redisTemplate.expire(eventsKey, properties.getTtlHours(), TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.warn("note-grading pool appendAuditEvent failed key={}", eventsKey, e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
