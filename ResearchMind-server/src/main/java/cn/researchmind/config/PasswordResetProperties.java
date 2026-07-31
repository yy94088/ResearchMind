package cn.researchmind.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "researchmind.password-reset")
public class PasswordResetProperties {

    private boolean enabled;
    private Duration expiration = Duration.ofMinutes(15);
    private String frontendUrl = "http://localhost:5173/login";
    private String fromEmail = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getExpiration() {
        return expiration;
    }

    public void setExpiration(Duration expiration) {
        this.expiration = expiration;
    }

    public String getFrontendUrl() {
        return frontendUrl;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }
}
