package com.aiconsultant.consultant.memory;

public final class MemoryOutboxStatus {
    private MemoryOutboxStatus() {}

    public static final int PENDING = 0;
    public static final int PROCESSING = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;
}
