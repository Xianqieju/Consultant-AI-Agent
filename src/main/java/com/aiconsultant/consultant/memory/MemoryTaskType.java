package com.aiconsultant.consultant.memory;

public enum MemoryTaskType {
    SUMMARY,
    PROFILE,
    BOTH;

    public boolean includesSummary() {
        return this == SUMMARY || this == BOTH;
    }

    public boolean includesProfile() {
        return this == PROFILE || this == BOTH;
    }
}
