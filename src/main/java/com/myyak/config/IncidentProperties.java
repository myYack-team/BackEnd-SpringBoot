package com.myyak.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "incident")
public class IncidentProperties {
    private boolean enabled;
    private String tableName = "";
    private String region = "ap-northeast-2";
    public boolean isConfigured() { return enabled && !tableName.isBlank(); }
}
