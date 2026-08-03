package cn.researchmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "researchmind.admin")
public record AdminProperties(String initialEmail) {
}
