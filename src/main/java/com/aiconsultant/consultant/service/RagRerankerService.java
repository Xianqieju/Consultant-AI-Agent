package com.aiconsultant.consultant.service;

import java.util.Map;

public interface RagRerankerService {

    /**
     * @param query     用户检索问题
     * @param documents 候选文本列表（index 与输入顺序一一对应）
     * @return key=index, value=rerankScore；失败或空结果返回空 map
     */
    Map<Integer, Double> rerank(String query, java.util.List<String> documents);
}
