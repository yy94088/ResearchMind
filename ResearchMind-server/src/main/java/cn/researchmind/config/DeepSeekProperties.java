package cn.researchmind.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "researchmind.deepseek")
public class DeepSeekProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-v4-flash";
    private String proxyUrl = "";
    private Duration connectTimeout = Duration.ofSeconds(15);
    private Duration readTimeout = Duration.ofMinutes(3);

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getProxyUrl() {
        return proxyUrl;
    }

    public void setProxyUrl(String proxyUrl) {
        this.proxyUrl = proxyUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
