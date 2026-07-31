package cn.researchmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "researchmind.storage")
public record StorageProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKey,
        @NotBlank String secretKey,
        @NotBlank String bucket
) {
}
