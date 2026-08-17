package com.vibeagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ExecutorConfiguration {

    @Bean(name = "orchestrationExecutor", destroyMethod = "close")
    ExecutorService orchestrationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "agentExecutor", destroyMethod = "close")
    ExecutorService agentExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
