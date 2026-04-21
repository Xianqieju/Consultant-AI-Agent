package com.aiconsultant.consultant.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * v1 画像 JSON 骨架：固定键 + ext 扩展。
 */
public final class MemoryProfileDefaults {
    private MemoryProfileDefaults() {}

    private static final ObjectMapper OM = new ObjectMapper();

    public static final String EMPTY_PROFILE_JSON =
            "{\"displayName\":\"\",\"preferences\":[],\"topics\":[],\"ext\":{}}";

    /**
     * 判断是否仍为「空壳」画像：不向模型注入额外 System 块时可返回 true。
     */
    public static boolean isEffectivelyEmpty(String json) {
        if (json == null || json.isBlank()) {
            return true;
        }
        try {
            JsonNode n = OM.readTree(json);
            if (!n.path("displayName").asText("").isBlank()) {
                return false;
            }
            if (n.path("preferences").isArray() && n.path("preferences").size() > 0) {
                return false;
            }
            if (n.path("topics").isArray() && n.path("topics").size() > 0) {
                return false;
            }
            JsonNode ext = n.path("ext");
            return !ext.isObject() || ext.isEmpty();
        } catch (Exception e) {
            return true;
        }
    }
}
