package com.myyak.service.serverLogService;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerLogFileConfigurationTest {
    @TempDir Path directory;

    @Test void realLogbackWritesUtf8AndRetainsMessagesAcrossRestart() throws Exception {
        Path file = directory.resolve("application.log");
        for (int attempt = 0; attempt < 2; attempt++) {
            LoggerContext context = new LoggerContext();
            context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
            context.putProperty("LOG_FILE", file.toString());
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            try {
                String configuration;
                try (var input = getClass().getResourceAsStream("/logback-spring.xml")) {
                    configuration = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                assertTrue(configuration.contains("<maxFileSize>10MB</maxFileSize>"));
                // Exercise the production rolling policy at a smaller boundary.
                configurator.doConfigure(new java.io.ByteArrayInputStream(configuration.replace(
                        "<maxFileSize>10MB</maxFileSize>", "<maxFileSize>1KB</maxFileSize>").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                var appender = (RollingFileAppender<?>) context.getLogger("ROOT").getAppender("ADMIN_FILE");
                assertTrue(appender.isStarted());
                var policy = (SizeAndTimeBasedRollingPolicy<?>) appender.getRollingPolicy();
                assertEquals(7, policy.getMaxHistory());
                context.getLogger("ROOT").detachAppender("CONSOLE");
                context.getLogger("com.myyak.Synthetic").error("한글 합성 로그 " + attempt,
                        new IllegalStateException("synthetic exception"));
                if (attempt == 0) {
                    context.getLogger("com.myyak.Synthetic").error("x".repeat(2048));
                    context.getLogger("com.myyak.Synthetic").error("rotation boundary");
                }
            } finally {
                context.stop();
            }
        }
        var lines = new java.util.ArrayList<String>();
        new ServerLogServiceImpl(file.toString()).readLines(24, lines::add);
        String text = String.join("\n", lines);
        assertTrue(text.contains("한글 합성 로그 0"));
        assertTrue(text.contains("한글 합성 로그 1"));
        assertTrue(text.contains("IllegalStateException"));
        assertFalse(text.contains("\u001b["), "File logs must not contain console ANSI colors");
        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(path -> path.toString().endsWith(".gz")));
        }
    }
}
