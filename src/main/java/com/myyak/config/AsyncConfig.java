package com.myyak.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정
 * 데이터 수집 배치 작업을 비동기로 실행하기 위한 설정
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "drugBatchExecutor")
    public Executor drugBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);        // 동시에 1개의 배치 작업만 실행
        executor.setMaxPoolSize(1);         // 최대 1개
        executor.setQueueCapacity(5);       // 대기열 5개
        executor.setThreadNamePrefix("DrugBatch-");
        executor.initialize();
        return executor;
    }
}
