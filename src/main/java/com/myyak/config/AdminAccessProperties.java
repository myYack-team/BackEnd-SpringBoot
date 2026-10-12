package com.myyak.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "admin.access")
public class AdminAccessProperties {
    private boolean enabled;
    private String email = "";
    private String sender = "";
    private String origin = "https://api.myyak.xyz";
    private String region = "ap-northeast-2";
    private boolean secureCookie = true;
    private long sessionSeconds = 28800;

    public boolean isConfigured() {
        return enabled && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                && sender.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                && sessionSeconds >= 300 && sessionSeconds <= 28800;
    }
}
