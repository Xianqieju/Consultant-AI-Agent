package com.aiconsultant.consultant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aiconsultant.consultant.entity.RagAgentIdentity;
import com.aiconsultant.consultant.entity.RagDocumentIdentity;
import com.aiconsultant.consultant.entity.RagUserIdentity;
import com.aiconsultant.consultant.mapper.RagAgentIdentityMapper;
import com.aiconsultant.consultant.mapper.RagDocumentIdentityMapper;
import com.aiconsultant.consultant.mapper.RagUserIdentityMapper;
import com.aiconsultant.consultant.notegrading.protocol.ExpertDomain;
import com.aiconsultant.consultant.pojo.RagIdentityEvaluation;
import com.aiconsultant.consultant.pojo.RagRetrievalHitDTO;
import com.aiconsultant.consultant.service.RagIdentityAccessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RagIdentityAccessServiceImpl implements RagIdentityAccessService {

    private static final String POLICY_STRICT = "strict";

    @Value("${app.rag.identity-access-enabled:false}")
    private boolean identityAccessEnabled;

    @Value("${app.rag.identity-policy:permissive}")
    private String identityPolicy;

    @Value("${app.rag.identity-skip-post-filter:true}")
    private boolean identitySkipPostFilter;

    @Autowired
    private RagDocumentIdentityMapper ragDocumentIdentityMapper;
    @Autowired
    private RagUserIdentityMapper ragUserIdentityMapper;
    @Autowired
    private RagAgentIdentityMapper ragAgentIdentityMapper;

    @Override
    public RagIdentityEvaluation evaluateForRetrieval(
            Set<Long> candidateDocumentIds,
            Long requestUserId,
            ExpertDomain expertScope
    ) {
        if (!identityAccessEnabled || candidateDocumentIds == null || candidateDocumentIds.isEmpty()) {
            return RagIdentityEvaluation.unrestricted();
        }
        ExpertDomain expert = expertScope == null ? ExpertDomain.GENERAL : expertScope;
        if (expert == ExpertDomain.GENERAL) {
            return RagIdentityEvaluation.unrestricted();
        }

        List<RagDocumentIdentity> bindings = ragDocumentIdentityMapper.selectList(
                new LambdaQueryWrapper<RagDocumentIdentity>()
                        .in(RagDocumentIdentity::getDocumentId, candidateDocumentIds)
        );
        Map<Long, Set<Long>> docToIdentities = new HashMap<>();
        for (RagDocumentIdentity b : bindings) {
            if (b.getDocumentId() == null || b.getIdentityId() == null) {
                continue;
            }
            docToIdentities.computeIfAbsent(b.getDocumentId(), k -> new HashSet<>()).add(b.getIdentityId());
        }

        Set<Long> subject = new HashSet<>();
        subject.addAll(loadUserIdentityIds(requestUserId));
        subject.addAll(loadAgentIdentityIds(expert.name()));

        boolean strict = POLICY_STRICT.equalsIgnoreCase(identityPolicy == null ? "" : identityPolicy.trim());

        Set<Long> allowed = new HashSet<>();
        for (Long did : candidateDocumentIds) {
            if (did == null) {
                continue;
            }
            Set<Long> required = docToIdentities.get(did);
            if (required == null || required.isEmpty()) {
                allowed.add(did);
                continue;
            }
            if (subject.isEmpty()) {
                continue;
            }
            if (strict) {
                if (subject.containsAll(required)) {
                    allowed.add(did);
                }
            } else {
                for (Long rid : required) {
                    if (subject.contains(rid)) {
                        allowed.add(did);
                        break;
                    }
                }
            }
        }
        return new RagIdentityEvaluation(true, allowed, Collections.unmodifiableMap(new HashMap<>(docToIdentities)));
    }

    @Override
    public List<RagRetrievalHitDTO> filterHitsByIdentity(
            List<RagRetrievalHitDTO> hits,
            Long requestUserId,
            ExpertDomain expertScope
    ) {
        if (!identityAccessEnabled || hits == null || hits.isEmpty()) {
            return hits == null ? List.of() : hits;
        }
        ExpertDomain expert = expertScope == null ? ExpertDomain.GENERAL : expertScope;
        if (expert == ExpertDomain.GENERAL) {
            return hits;
        }
        if (identitySkipPostFilter) {
            return hits;
        }

        Set<Long> docIds = hits.stream()
                .map(RagRetrievalHitDTO::getDocumentId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (docIds.isEmpty()) {
            return hits;
        }

        RagIdentityEvaluation ev = evaluateForRetrieval(docIds, requestUserId, expertScope);
        if (!ev.gateActive()) {
            return hits;
        }
        Set<Long> allowed = ev.allowedDocumentIds();
        List<RagRetrievalHitDTO> out = new ArrayList<>();
        for (RagRetrievalHitDTO h : hits) {
            Long did = h.getDocumentId();
            if (did == null) {
                continue;
            }
            if (allowed.contains(did)) {
                out.add(h);
            }
        }
        return out;
    }

    private Set<Long> loadUserIdentityIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        List<RagUserIdentity> rows = ragUserIdentityMapper.selectList(
                new LambdaQueryWrapper<RagUserIdentity>().eq(RagUserIdentity::getUserId, userId)
        );
        Set<Long> s = new HashSet<>();
        for (RagUserIdentity r : rows) {
            if (r.getIdentityId() != null) {
                s.add(r.getIdentityId());
            }
        }
        return s;
    }

    private Set<Long> loadAgentIdentityIds(String agentCode) {
        if (agentCode == null || agentCode.isBlank()) {
            return Set.of();
        }
        List<RagAgentIdentity> rows = ragAgentIdentityMapper.selectList(
                new LambdaQueryWrapper<RagAgentIdentity>().eq(RagAgentIdentity::getAgentCode, agentCode)
        );
        Set<Long> s = new HashSet<>();
        for (RagAgentIdentity r : rows) {
            if (r.getIdentityId() != null) {
                s.add(r.getIdentityId());
            }
        }
        return s;
    }
}
