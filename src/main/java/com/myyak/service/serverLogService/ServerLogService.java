package com.myyak.service.serverLogService;

import java.util.function.Consumer;

/** Supplies application log lines without executing host commands. */
public interface ServerLogService {
    void readLines(int hours, Consumer<String> consumer);
}
