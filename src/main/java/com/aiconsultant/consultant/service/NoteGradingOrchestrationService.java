package com.aiconsultant.consultant.service;

import com.aiconsultant.consultant.aiservice.notegrading.DevGuideExpertAgent;
import com.aiconsultant.consultant.aiservice.notegrading.EmployeePolicyExpertAgent;
import com.aiconsultant.consultant.aiservice.notegrading.InternalNetworkExpertAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteAggregatorAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteFormatSummarizerAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteGradingFallbackSummarizerAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteQualityInspectorAgent;
import com.aiconsultant.consultant.aiservice.notegrading.NoteTechnicalGuideAgent;
import com.aiconsultant.consultant.config.NoteGradingAuditProperties;
import com.aiconsultant.consultant.config.NoteGradingFallbackProperties;
import com.aiconsultant.consultant.config.NoteGradingProtocolProperties;
import com.aiconsultant.consultant.config.NoteGradingRetryProperties;
import com.aiconsultant.consultant.notegrading.NoteGradingPoolStore;
import com.aiconsultant.consultant.notegrading.NoteGradingReactiveRetry;
import com.aiconsultant.consultant.notegrading.NoteGradingRedisKeys;
import com.aiconsultant.consultant.notegrading.access.ExpertAccessPolicy;
import com.aiconsultant.consultant.notegrading.audit.NoteAuditEvent;
import com.aiconsultant.consultant.notegrading.protocol.AgentRole;
import com.aiconsultant.consultant.notegrading.protocol.ExpertDomain;
import com.aiconsultant.consultant.notegrading.protocol.NoteSectionTask;
import com.aiconsultant.consultant.notegrading.protocol.NoteTaskEnvelope;
import com.aiconsultant.consultant.notegrading.protocol.NoteTaskPlan;
import com.aiconsultant.consultant.notegrading.protocol.NoteTaskResult;
import com.aiconsultant.consultant.notegrading.protocol.TaskStage;
import com.aiconsultant.consultant.pojo.NoteGradingQualityReportDTO;
import com.aiconsultant.consultant.pojo.NoteGradingStructuredResponseDTO;
import com.aiconsultant.consultant.pojo.RagCitationDTO;
import com.aiconsultant.consultant.pojo.RagRetrievalHitDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 技术指导拆任务 → 权限过滤 → RAG + 专家 → 聚合 → 质检 → 排版总结。
 */
@Slf4j
@Service
public class NoteGradingOrchestrationService {

    private static final String FIXED_UNAVAILABLE = "技术服务暂时不可用，请稍后重试。";
    private static final String FALLBACK_PREFIX = "【说明】多专家链路暂时不可用，以下为要点整理稿，仅供参考。\n\n";
    private static final int ORIGINAL_NOTE_MAX_FOR_FALLBACK = 12000;
    private static final int ERROR_SNIP = 800;

    private final NoteTechnicalGuideAgent technicalGuideAgent;
    private final InternalNetworkExpertAgent internalNetworkExpertAgent;
    private final DevGuideExpertAgent devGuideExpertAgent;
    private final EmployeePolicyExpertAgent employeePolicyExpertAgent;
    private final NoteAggregatorAgent aggregator;
    private final NoteQualityInspectorAgent qualityInspectorAgent;
    private final NoteFormatSummarizerAgent formatSummarizerAgent;
    private final RagRetrievalService ragRetrievalService;
    private final ExpertAccessPolicy expertAccessPolicy;
    private final ObjectMapper objectMapper;
    private final NoteGradingPoolStore poolStore;
    private final NoteGradingRetryProperties retryProperties;
    private final NoteGradingFallbackProperties fallbackProperties;
    private final NoteGradingFallbackSummarizerAgent fallbackSummarizer;
    private final NoteGradingAuditProperties auditProperties;
    private final NoteGradingProtocolProperties protocolProperties;

    public NoteGradingOrchestrationService(
            NoteTechnicalGuideAgent technicalGuideAgent,
            InternalNetworkExpertAgent internalNetworkExpertAgent,
            DevGuideExpertAgent devGuideExpertAgent,
            EmployeePolicyExpertAgent employeePolicyExpertAgent,
            NoteAggregatorAgent aggregator,
            NoteQualityInspectorAgent qualityInspectorAgent,
            NoteFormatSummarizerAgent formatSummarizerAgent,
            RagRetrievalService ragRetrievalService,
            ExpertAccessPolicy expertAccessPolicy,
            ObjectMapper objectMapper,
            NoteGradingPoolStore poolStore,
            NoteGradingRetryProperties retryProperties,
            NoteGradingFallbackProperties fallbackProperties,
            NoteGradingFallbackSummarizerAgent fallbackSummarizer,
            NoteGradingAuditProperties auditProperties,
            NoteGradingProtocolProperties protocolProperties
    ) {
        this.technicalGuideAgent = technicalGuideAgent;
        this.internalNetworkExpertAgent = internalNetworkExpertAgent;
        this.devGuideExpertAgent = devGuideExpertAgent;
        this.employeePolicyExpertAgent = employeePolicyExpertAgent;
        this.aggregator = aggregator;
        this.qualityInspectorAgent = qualityInspectorAgent;
        this.formatSummarizerAgent = formatSummarizerAgent;
        this.ragRetrievalService = ragRetrievalService;
        this.expertAccessPolicy = expertAccessPolicy;
        this.objectMapper = objectMapper;
        this.poolStore = poolStore;
        this.retryProperties = retryProperties;
        this.fallbackProperties = fallbackProperties;
        this.fallbackSummarizer = fallbackSummarizer;
        this.auditProperties = auditProperties;
        this.protocolProperties = protocolProperties;
    }

    public Mono<String> gradeNoteReactive(String fullNote) {
        return gradeNoteReactive(null, null, fullNote);
    }

    public Mono<String> gradeNoteReactive(Long sessionId, Long userId, String fullNote) {
        return gradeNoteStructuredReactive(sessionId, userId, fullNote)
                .map(NoteGradingStructuredResponseDTO::getFinalText);
    }

    public Mono<NoteGradingStructuredResponseDTO> gradeNoteStructuredReactive(Long sessionId, Long userId, String fullNote) {
        if (fullNote == null || fullNote.isBlank()) {
            return Mono.just(emptyStructured("本次未识别到可处理的技术内容。"));
        }
        final String pipelineId = UUID.randomUUID().toString();
        final String poolKey = NoteGradingRedisKeys.pool(sessionId, pipelineId);
        final String eventsKey = NoteGradingRedisKeys.events(sessionId, pipelineId);
        if (poolStore.isEnabled()) {
            poolStore.initPool(poolKey, pipelineId, sessionId, userId, fullNote);
        }
        appendEvent(eventsKey, event("PIPELINE_START", TaskStage.PLAN, null, AgentRole.TECH_GUIDE, "STARTED", null, 0));

        Mono<String> planMono = withLlmRetry(
                Mono.fromCallable(() -> technicalGuideAgent.planAndScore(fullNote))
                        .subscribeOn(Schedulers.boundedElastic())
        );

        return planMono
                .map(NoteGradingOrchestrationService::stripMarkdownFence)
                .map(this::parseTaskPlan)
                .flatMap(plan -> runPlan(plan, fullNote, userId, pipelineId, poolKey, eventsKey))
                .onErrorResume(e -> recoverWithFallback(fullNote, poolKey, eventsKey, pipelineId, e));
    }

    private Mono<NoteGradingStructuredResponseDTO> runPlan(
            NoteTaskPlan plan,
            String fullNote,
            Long userId,
            String pipelineId,
            String poolKey,
            String eventsKey
    ) {
        if (plan.getEnvelope() == null) {
            plan.setEnvelope(defaultEnvelope(pipelineId, AgentRole.TECH_GUIDE, TaskStage.PLAN, "tech-guide-plan"));
        }
        List<NoteSectionTask> filtered = filterTasksByAccess(plan.getTasks(), userId, plan);
        plan.setTasks(filtered);

        if (poolStore.isEnabled()) {
            poolStore.saveTechGuidePlan(
                    poolKey,
                    writeJson(plan),
                    writeJson(plan.getTasks()),
                    plan.getTasks() == null ? 0 : plan.getTasks().size(),
                    protocolProperties.getSchemaVersion()
            );
        }
        appendEvent(eventsKey, event("TECH_GUIDE_PLAN", TaskStage.PLAN, null, AgentRole.TECH_GUIDE, "SUCCEEDED", "tasks=" + safeSize(plan.getTasks()), 0));

        List<NoteSectionTask> tasks = normalizeTasks(plan, fullNote);
        if (tasks.isEmpty()) {
            NoteGradingStructuredResponseDTO out = toStructuredResponse(
                    pipelineId,
                    "本次未识别到可处理的技术内容。",
                    plan,
                    new ArrayList<>(),
                    null,
                    false
            );
            return Mono.just(out);
        }

        AtomicInteger doneCounter = new AtomicInteger(0);
        List<Mono<NoteTaskResult>> jobMonos = tasks.stream()
                .map(task -> executeTask(task, fullNote, userId, poolKey, eventsKey, doneCounter))
                .collect(Collectors.toList());

        return Flux.mergeSequential(jobMonos)
                .collectList()
                .flatMap(results -> {
                    String combinedDraft = buildCombinedDraftFromResults(results);
                    if (poolStore.isEnabled()) {
                        poolStore.saveCombinedDraft(poolKey, combinedDraft);
                    }
                    appendEvent(eventsKey, event("EXPERTS_DONE", TaskStage.REWRITE, null, AgentRole.AGGREGATOR, "SUCCEEDED", "count=" + results.size(), 0));
                    Mono<String> aggMono = withLlmRetry(
                            Mono.fromCallable(() -> aggregator.aggregate(fullNote, combinedDraft))
                                    .subscribeOn(Schedulers.boundedElastic())
                    );
                    return aggMono.flatMap(aggText -> postAggregateChain(
                            fullNote,
                            aggText,
                            results,
                            plan,
                            pipelineId,
                            poolKey,
                            eventsKey
                    ));
                });
    }

    private Mono<NoteGradingStructuredResponseDTO> postAggregateChain(
            String fullNote,
            String aggText,
            List<NoteTaskResult> results,
            NoteTaskPlan plan,
            String pipelineId,
            String poolKey,
            String eventsKey
    ) {
        if (poolStore.isEnabled()) {
            poolStore.saveAggregatorRaw(poolKey, aggText);
        }
        appendEvent(eventsKey, event("AGGREGATE_DONE", TaskStage.AGGREGATE, null, AgentRole.AGGREGATOR, "SUCCEEDED", null, 0));

        String citationsSummary = buildCitationsSummary(results);
        Mono<String> qualityMono = withLlmRetry(
                Mono.fromCallable(() -> qualityInspectorAgent.inspect(fullNote, aggText, citationsSummary))
                        .subscribeOn(Schedulers.boundedElastic())
        );

        return qualityMono.flatMap(qualityJson -> {
            NoteGradingQualityReportDTO qualityReport = parseQualityReport(stripMarkdownFence(qualityJson));
            if (poolStore.isEnabled()) {
                poolStore.saveQualityReport(poolKey, writeJson(qualityReport));
            }
            appendEvent(eventsKey, event("QUALITY_DONE", TaskStage.QUALITY_REVIEW, null, AgentRole.QUALITY_INSPECTOR, "SUCCEEDED", null, 0));

            Mono<String> formatMono = withLlmRetry(
                    Mono.fromCallable(() -> formatSummarizerAgent.formatOnly(aggText))
                            .subscribeOn(Schedulers.boundedElastic())
            );
            return formatMono.map(formatted -> {
                String outText = formatted == null ? aggText : formatted.trim();
                if (outText.isEmpty()) {
                    outText = aggText;
                }
                if (poolStore.isEnabled()) {
                    poolStore.saveFinal(poolKey, outText);
                }
                appendEvent(eventsKey, event("FORMAT_DONE", TaskStage.FORMAT, null, AgentRole.FORMAT_SUMMARIZER, "SUCCEEDED", null, 0));
                return toStructuredResponse(pipelineId, outText, plan, results, qualityReport, true);
            });
        });
    }

    private List<NoteSectionTask> filterTasksByAccess(List<NoteSectionTask> tasks, Long userId, NoteTaskPlan plan) {
        if (tasks == null || tasks.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> denied = new ArrayList<>();
        List<NoteSectionTask> kept = new ArrayList<>();
        for (NoteSectionTask t : tasks) {
            if (t == null) {
                continue;
            }
            ExpertDomain d = t.getDomain() == null ? ExpertDomain.GENERAL : t.getDomain();
            if (expertAccessPolicy.canAccess(userId, d)) {
                kept.add(t);
            } else {
                denied.add(d.name());
            }
        }
        if (!denied.isEmpty()) {
            String suffix = "；已剔除无权限专家任务: " + String.join(", ", denied);
            plan.setRoutingReason(safe(plan.getRoutingReason()) + suffix);
        }
        return kept;
    }

    private Mono<NoteTaskResult> executeTask(
            NoteSectionTask task,
            String fullNote,
            Long userId,
            String poolKey,
            String eventsKey,
            AtomicInteger doneCounter
    ) {
        long start = System.currentTimeMillis();
        appendEvent(eventsKey, event("TASK_START", TaskStage.REWRITE, task.getSectionId(), roleOf(task.getDomain()), "STARTED", null, 0));

        String ragQuery = (safe(task.getSourceText()) + " " + safe(task.getTargetGoal())).trim();
        appendEvent(eventsKey, event("RAG_START", TaskStage.RAG_RETRIEVE, task.getSectionId(), roleOf(task.getDomain()), "STARTED", null, 0));
        List<RagRetrievalHitDTO> hits = ragRetrievalService.retrieve(ragQuery, userId, task.getDomain() == null ? ExpertDomain.GENERAL : task.getDomain());
        appendEvent(eventsKey, event("RAG_DONE", TaskStage.RAG_RETRIEVE, task.getSectionId(), roleOf(task.getDomain()), "SUCCEEDED", "hits=" + hits.size(), System.currentTimeMillis() - start));

        String ragContext = formatHitsForPrompt(hits);
        String taskBlock = """
                原始整段输入（供参考）：
                %s

                片段ID：%s
                专家领域：%s
                片段原文：
                %s

                技术指导评分提示（0-10，越低越需优先修复）：%d
                评分原因：%s
                执行指引：%s
                目标：%s
                """.formatted(
                truncate(fullNote, 2000),
                safe(task.getSectionId()),
                task.getDomain() == null ? "GENERAL" : task.getDomain().name(),
                safe(task.getSourceText()),
                task.getScoreHint(),
                safe(task.getReason()),
                safe(task.getRewriteHint()),
                safe(task.getTargetGoal())
        );

        Mono<ExpertTaskOutcome> outcomeMono = withLlmRetry(
                Mono.fromCallable(() -> invokeExpert(task.getDomain(), ragContext, taskBlock, fullNote))
                        .subscribeOn(Schedulers.boundedElastic())
        );

        return outcomeMono.map(outcome -> {
            NoteTaskResult result = new NoteTaskResult();
            result.setEnvelope(defaultEnvelope(
                    null,
                    roleOf(task.getDomain()),
                    TaskStage.REWRITE,
                    safe(task.getSectionId())
            ));
            result.setSectionId(task.getSectionId());
            result.setDomain(task.getDomain());
            result.setRewriteText(outcome.answerText());
            result.setIssues("");
            result.setAddressedHints(task.getRewriteHint());
            result.setWorkerScore(task.getScoreHint());
            result.setWorkerScoreReason(task.getReason());
            if (outcome.citations() != null) {
                result.setRagCitations(outcome.citations());
            }
            if (poolStore.isEnabled()) {
                int n = doneCounter.incrementAndGet();
                poolStore.saveTaskResult(poolKey, task.getSectionId(), outcome.answerText(), n);
                String hashField = fieldByDomain(task.getDomain());
                if (hashField != null) {
                    poolStore.saveWorkerOutput(poolKey, hashField, outcome.answerText());
                }
            }
            appendEvent(eventsKey, event("TASK_DONE", TaskStage.REWRITE, task.getSectionId(), roleOf(task.getDomain()), "SUCCEEDED", null, System.currentTimeMillis() - start));
            return result;
        });
    }

    private ExpertTaskOutcome invokeExpert(ExpertDomain domain, String ragContext, String taskBlock, String fullNote) {
        ExpertDomain d = domain == null ? ExpertDomain.GENERAL : domain;
        return switch (d) {
            case GENERAL -> new ExpertTaskOutcome(
                    aggregator.aggregate(truncate(fullNote, 4000), taskBlock),
                    List.of()
            );
            case INTERNAL_NETWORK -> parseExpertJson(internalNetworkExpertAgent.answer(ragContext, taskBlock));
            case DEV_GUIDE -> parseExpertJson(devGuideExpertAgent.answer(ragContext, taskBlock));
            case EMPLOYEE_POLICY -> parseExpertJson(employeePolicyExpertAgent.answer(ragContext, taskBlock));
        };
    }

    private ExpertTaskOutcome parseExpertJson(String raw) {
        String s = stripMarkdownFence(raw == null ? "" : raw.trim());
        try {
            JsonNode root = objectMapper.readTree(s);
            String answer = text(root, "expertAnswer");
            if (answer.isEmpty()) {
                answer = s;
            }
            List<RagCitationDTO> cites = new ArrayList<>();
            JsonNode arr = root.get("ragCitations");
            if (arr != null && arr.isArray()) {
                for (JsonNode c : arr) {
                    RagCitationDTO dto = new RagCitationDTO();
                    dto.setDocumentId(longOrNull(c, "documentId"));
                    dto.setChunkId(longOrNull(c, "chunkId"));
                    dto.setQuote(text(c, "quote"));
                    dto.setRelevance(text(c, "relevance"));
                    cites.add(dto);
                }
            }
            return new ExpertTaskOutcome(answer, cites);
        } catch (Exception e) {
            log.warn("专家 JSON 解析失败，使用原文: {}", e.getMessage());
            return new ExpertTaskOutcome(s, List.of());
        }
    }

    private static Long longOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isIntegralNumber()) {
            return v.longValue();
        }
        try {
            return Long.parseLong(v.asText().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private record ExpertTaskOutcome(String answerText, List<RagCitationDTO> citations) {
    }

    private NoteGradingQualityReportDTO parseQualityReport(String json) {
        NoteGradingQualityReportDTO dto = new NoteGradingQualityReportDTO();
        dto.setRawJson(json);
        try {
            JsonNode root = objectMapper.readTree(json);
            dto.setHallucinationRiskScore(intValue(root, "hallucinationRiskScore", 0));
            dto.setHallucinationNotes(text(root, "hallucinationNotes"));
            dto.setSpliceIssueScore(intValue(root, "spliceIssueScore", 0));
            dto.setSpliceNotes(text(root, "spliceNotes"));
            dto.setCompletenessScore(intValue(root, "completenessScore", 0));
            dto.setCompletenessNotes(text(root, "completenessNotes"));
            dto.setPassSuggested(root.path("passSuggested").asBoolean(false));
        } catch (Exception e) {
            log.warn("质检 JSON 解析失败: {}", e.getMessage());
        }
        return dto;
    }

    private String buildCitationsSummary(List<NoteTaskResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (NoteTaskResult r : results) {
            sb.append("section=").append(safe(r.getSectionId()))
                    .append(" domain=").append(r.getDomain() == null ? "" : r.getDomain().name())
                    .append("\n");
            if (r.getRagCitations() != null) {
                for (RagCitationDTO c : r.getRagCitations()) {
                    sb.append("  doc=").append(c.getDocumentId())
                            .append(" chunk=").append(c.getChunkId())
                            .append(" quote=").append(truncate(safe(c.getQuote()), 200))
                            .append("\n");
                }
            }
            sb.append("answer_head=").append(truncate(safe(r.getRewriteText()), 400)).append("\n---\n");
        }
        return sb.toString();
    }

    private String formatHitsForPrompt(List<RagRetrievalHitDTO> hits) {
        if (hits == null || hits.isEmpty()) {
            return "（当前无检索命中片段，请严格说明证据不足，勿编造。）";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (RagRetrievalHitDTO h : hits) {
            sb.append("【证据").append(i++).append("】")
                    .append(" documentId=").append(h.getDocumentId())
                    .append(" chunkId=").append(h.getChunkId())
                    .append(" fineScore=").append(String.format("%.4f", h.getFineScore()))
                    .append("\n摘要片段: ").append(truncate(safe(h.getOutlineSnippet()), 400))
                    .append("\n正文片段: ").append(truncate(safe(h.getChunkTextSnippet()), 1200))
                    .append("\n\n");
        }
        return sb.toString();
    }

    private Mono<NoteGradingStructuredResponseDTO> recoverWithFallback(
            String fullNote,
            String poolKey,
            String eventsKey,
            String pipelineId,
            Throwable e
    ) {
        log.warn("笔记批改流水线失败 pipelineId={}", pipelineId, e);
        appendEvent(eventsKey, event("PIPELINE_ERROR", TaskStage.AUDIT, null, AgentRole.FALLBACK, "FAILED", e.getMessage(), 0));
        if (!fallbackProperties.isEnabled()) {
            if (poolStore.isEnabled()) {
                poolStore.markFailed(poolKey, e);
            }
            return Mono.just(emptyStructured(FIXED_UNAVAILABLE));
        }
        String ctx = poolStore.readFallbackContextSummary(poolKey, fallbackProperties.getContextMaxChars());
        String noteSnip = truncate(fullNote, ORIGINAL_NOTE_MAX_FOR_FALLBACK);
        String errMsg = e.getMessage() == null ? "" : e.getMessage();
        if (errMsg.length() > ERROR_SNIP) {
            errMsg = errMsg.substring(0, ERROR_SNIP);
        }
        String errMsgFinal = errMsg;
        return Mono.fromCallable(() -> fallbackSummarizer.summarizeStructured(noteSnip, ctx, errMsgFinal))
                .subscribeOn(Schedulers.boundedElastic())
                .map(text -> {
                    String body = text == null ? "" : text.trim();
                    String out = FALLBACK_PREFIX + body;
                    if (poolStore.isEnabled()) {
                        poolStore.saveFallback(poolKey, out);
                    }
                    appendEvent(eventsKey, event("FALLBACK_DONE", TaskStage.AUDIT, null, AgentRole.FALLBACK, "SUCCEEDED", null, 0));
                    NoteGradingStructuredResponseDTO dto = emptyStructured(out);
                    dto.setPipelineId(pipelineId);
                    return dto;
                })
                .onErrorResume(fbErr -> {
                    log.warn("笔记批改降级总结失败 pipelineId={}", pipelineId, fbErr);
                    if (poolStore.isEnabled()) {
                        poolStore.markFailed(poolKey, fbErr);
                    }
                    return Mono.just(emptyStructured(FIXED_UNAVAILABLE));
                });
    }

    private <T> Mono<T> withLlmRetry(Mono<T> source) {
        Retry r = NoteGradingReactiveRetry.retrySpec(retryProperties);
        if (r == null) {
            return source;
        }
        return source.retryWhen(r);
    }

    private NoteTaskPlan parseTaskPlan(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            NoteTaskPlan plan = new NoteTaskPlan();
            plan.setRoutingReason(text(root, "routingReason"));
            plan.setGlobalConstraints(text(root, "globalConstraints"));
            plan.setOverallScore(intValue(root, "overallScore", 6));
            plan.setOverallScoreReason(text(root, "overallScoreReason"));
            JsonNode arr = root.get("tasks");
            List<NoteSectionTask> tasks = new ArrayList<>();
            if (arr != null && arr.isArray()) {
                for (JsonNode t : arr) {
                    NoteSectionTask nt = new NoteSectionTask();
                    nt.setSectionId(text(t, "sectionId"));
                    nt.setDomain(parseExpertDomain(text(t, "domain")));
                    nt.setSourceText(text(t, "sourceText"));
                    nt.setTargetGoal(text(t, "targetGoal"));
                    nt.setScoreHint(intValue(t, "scoreHint", 6));
                    nt.setReason(text(t, "reason"));
                    nt.setRewriteHint(text(t, "rewriteHint"));
                    tasks.add(nt);
                }
            }
            plan.setTasks(tasks);
            return plan;
        } catch (Exception ex) {
            log.warn("技术指导计划解析失败，回退单条 GENERAL: {}", ex.getMessage());
            NoteTaskPlan plan = new NoteTaskPlan();
            plan.setRoutingReason("JSON解析失败，采用通用兜底");
            plan.setGlobalConstraints("");
            plan.setOverallScore(6);
            plan.setOverallScoreReason(ex.getMessage() == null ? "parse error" : ex.getMessage());
            List<NoteSectionTask> tasks = new ArrayList<>();
            NoteSectionTask nt = new NoteSectionTask();
            nt.setSectionId("sec-1");
            nt.setDomain(ExpertDomain.GENERAL);
            nt.setSourceText("");
            nt.setTargetGoal("整理输入并给出通用技术答复");
            nt.setScoreHint(6);
            nt.setReason("解析失败兜底");
            nt.setRewriteHint("保持原意");
            tasks.add(nt);
            plan.setTasks(tasks);
            return plan;
        }
    }

    private List<NoteSectionTask> normalizeTasks(NoteTaskPlan plan, String fullNote) {
        List<NoteSectionTask> tasks = plan.getTasks() == null ? new ArrayList<>() : new ArrayList<>(plan.getTasks());
        tasks.removeIf(t -> t == null);
        if (tasks.isEmpty()) {
            NoteSectionTask fallback = new NoteSectionTask();
            fallback.setSectionId("sec-1");
            fallback.setDomain(ExpertDomain.GENERAL);
            fallback.setSourceText(fullNote);
            fallback.setTargetGoal("整理原文并给出通用技术说明");
            fallback.setScoreHint(6);
            fallback.setReason("未识别明确专家领域，采用通用处理");
            fallback.setRewriteHint("保持原意，按条目补全");
            tasks.add(fallback);
            return tasks;
        }
        int idx = 1;
        for (NoteSectionTask t : tasks) {
            if (t.getSectionId() == null || t.getSectionId().isBlank()) {
                t.setSectionId("sec-" + idx);
            }
            if (t.getDomain() == null) {
                t.setDomain(ExpertDomain.GENERAL);
            }
            if (t.getSourceText() == null || t.getSourceText().isBlank()) {
                t.setSourceText(fullNote);
            }
            idx++;
        }
        return tasks;
    }

    private NoteGradingStructuredResponseDTO toStructuredResponse(
            String pipelineId,
            String finalText,
            NoteTaskPlan plan,
            List<NoteTaskResult> results,
            NoteGradingQualityReportDTO qualityReport,
            boolean formattedBySummarizer
    ) {
        NoteGradingStructuredResponseDTO dto = new NoteGradingStructuredResponseDTO();
        dto.setPipelineId(pipelineId);
        dto.setFinalText(finalText);
        dto.setOverallScore(plan == null ? 0 : plan.getOverallScore());
        dto.setOverallScoreReason(plan == null ? "" : plan.getOverallScoreReason());
        dto.setQualityReport(qualityReport);
        dto.setFormattedBySummarizer(formattedBySummarizer);
        Map<String, NoteTaskResult> bySection = new HashMap<>();
        if (results != null) {
            for (NoteTaskResult r : results) {
                if (r.getSectionId() != null) {
                    bySection.put(r.getSectionId(), r);
                }
            }
        }
        if (plan != null && plan.getTasks() != null) {
            for (NoteSectionTask t : plan.getTasks()) {
                NoteGradingStructuredResponseDTO.SectionScoreDTO s = new NoteGradingStructuredResponseDTO.SectionScoreDTO();
                s.setSectionId(t.getSectionId());
                s.setDomain(t.getDomain() == null ? "GENERAL" : t.getDomain().name());
                s.setScoreHint(t.getScoreHint());
                s.setReason(t.getReason());
                s.setRewriteHint(t.getRewriteHint());
                NoteTaskResult tr = bySection.get(t.getSectionId());
                if (tr != null && tr.getRagCitations() != null) {
                    s.setRagCitations(new ArrayList<>(tr.getRagCitations()));
                }
                dto.getSections().add(s);
            }
        }
        return dto;
    }

    private NoteGradingStructuredResponseDTO emptyStructured(String text) {
        NoteGradingStructuredResponseDTO dto = new NoteGradingStructuredResponseDTO();
        dto.setFinalText(text);
        dto.setOverallScore(0);
        dto.setOverallScoreReason("");
        dto.setFormattedBySummarizer(false);
        return dto;
    }

    private void appendEvent(String eventsKey, NoteAuditEvent event) {
        if (!auditProperties.isEnabled()) {
            return;
        }
        poolStore.appendAuditEvent(eventsKey, event, auditProperties.getEventsTtlHours());
    }

    private NoteAuditEvent event(
            String eventType,
            TaskStage stage,
            String taskId,
            AgentRole role,
            String status,
            String error,
            long durationMs
    ) {
        NoteAuditEvent e = new NoteAuditEvent();
        e.setEventType(eventType);
        e.setStage(stage == null ? "" : stage.name());
        e.setTaskId(taskId);
        e.setAgentRole(role == null ? "" : role.name());
        e.setAttempt(0);
        e.setStatus(status);
        e.setDurationMs(durationMs);
        e.setError(error);
        e.setPayloadRef("");
        e.setTs(System.currentTimeMillis());
        return e;
    }

    private String buildCombinedDraftFromResults(List<NoteTaskResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (NoteTaskResult r : results) {
            sb.append("【")
                    .append(r.getDomain() == null ? "GENERAL" : r.getDomain().name())
                    .append(" | ")
                    .append(safe(r.getSectionId()))
                    .append("】\n")
                    .append(safe(r.getRewriteText()))
                    .append("\n\n");
        }
        return sb.toString();
    }

    private NoteTaskEnvelope defaultEnvelope(String pipelineId, AgentRole role, TaskStage stage, String taskId) {
        NoteTaskEnvelope env = new NoteTaskEnvelope();
        env.setSchemaVersion(protocolProperties.getSchemaVersion());
        env.setPipelineId(pipelineId);
        env.setTaskId(taskId);
        env.setAgentRole(role);
        env.setStage(stage);
        env.setAttempt(0);
        env.setCreatedAt(System.currentTimeMillis());
        return env;
    }

    private static String stripMarkdownFence(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('{');
            int end = s.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return s.substring(start, end + 1);
            }
        }
        return s;
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull() || !n.isTextual()) {
            return "";
        }
        return n.asText("");
    }

    private static int intValue(JsonNode root, String field, int def) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return def;
        }
        if (n.isInt()) {
            return n.asInt(def);
        }
        try {
            return Integer.parseInt(n.asText());
        } catch (Exception ignore) {
            return def;
        }
    }

    private static ExpertDomain parseExpertDomain(String raw) {
        if (raw == null || raw.isBlank()) {
            return ExpertDomain.GENERAL;
        }
        try {
            return ExpertDomain.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            return ExpertDomain.GENERAL;
        }
    }

    private static AgentRole roleOf(ExpertDomain d) {
        if (d == null) {
            return AgentRole.AGGREGATOR;
        }
        return switch (d) {
            case INTERNAL_NETWORK -> AgentRole.EXPERT_INTERNAL_NETWORK;
            case DEV_GUIDE -> AgentRole.EXPERT_DEV_GUIDE;
            case EMPLOYEE_POLICY -> AgentRole.EXPERT_EMPLOYEE_POLICY;
            case GENERAL -> AgentRole.AGGREGATOR;
        };
    }

    private static String fieldByDomain(ExpertDomain d) {
        if (d == null) {
            return null;
        }
        return switch (d) {
            case INTERNAL_NETWORK -> NoteGradingPoolStore.F_EXPERT_INTERNAL_NETWORK_OUT;
            case DEV_GUIDE -> NoteGradingPoolStore.F_EXPERT_DEV_GUIDE_OUT;
            case EMPLOYEE_POLICY -> NoteGradingPoolStore.F_EXPERT_EMPLOYEE_POLICY_OUT;
            case GENERAL -> null;
        };
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static int safeSize(List<?> xs) {
        return xs == null ? 0 : xs.size();
    }

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s == null ? "" : s;
        }
        return s.substring(0, max);
    }
}
