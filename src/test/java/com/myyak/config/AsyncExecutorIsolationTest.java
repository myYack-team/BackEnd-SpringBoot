package com.myyak.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig({AsyncConfig.class, AsyncExecutorIsolationTest.Probes.class})
class AsyncExecutorIsolationTest {
    @Autowired AsyncProbe probe;

    @Test void statisticsExecutorDoesNotChangeExistingUnnamedAsyncWork() throws Exception {
        assertThat(probe.existingTask().get(5, TimeUnit.SECONDS)).startsWith("DrugBatch-");
        assertThat(probe.statisticsTask().get(5, TimeUnit.SECONDS)).startsWith("VisitStatistics-");
    }

    @Configuration static class Probes {
        @Bean AsyncProbe asyncProbe() { return new AsyncProbe(); }
    }

    static class AsyncProbe {
        @Async public CompletableFuture<String> existingTask() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
        @Async("visitStatisticsExecutor") public CompletableFuture<String> statisticsTask() {
            return CompletableFuture.completedFuture(Thread.currentThread().getName());
        }
    }
}
