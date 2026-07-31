package cn.researchmind.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import cn.researchmind.common.ApiException;
import cn.researchmind.config.DeepSeekProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DeepSeekClient {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekClient.class);

    private final RestClient restClient;
    private final DeepSeekProperties properties;
    private final ObjectMapper objectMapper;

    public DeepSeekClient(
            RestClient deepSeekRestClient,
            DeepSeekProperties properties,
            ObjectMapper objectMapper
    ) {
        this.restClient = deepSeekRestClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String model() {
        return properties.getModel();
    }

    public boolean isConfigured() {
        return properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    public DeepSeekCompletion completeJson(
            String privacySafeUserId,
            String systemPrompt,
            String userPrompt
    ) {
        return complete(
                privacySafeUserId,
                systemPrompt,
                userPrompt,
                true,
                2200
        );
    }

    public DeepSeekCompletion completeText(
            String privacySafeUserId,
            String systemPrompt,
            String userPrompt
    ) {
        return complete(
                privacySafeUserId,
                systemPrompt,
                userPrompt,
                false,
                1200
        );
    }

    private DeepSeekCompletion complete(
            String privacySafeUserId,
            String systemPrompt,
            String userPrompt,
            boolean jsonOutput,
            int maxTokens
    ) {
        ensureConfigured();

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", properties.getModel());
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        request.put("thinking", Map.of("type", "disabled"));
        request.put("max_tokens", maxTokens);
        request.put("stream", false);
        request.put("temperature", 0.2);
        request.put("user_id", "researchmind-" + privacySafeUserId);
        if (jsonOutput) {
            request.put("response_format", Map.of("type", "json_object"));
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
            return parse(response);
        } catch (RestClientResponseException exception) {
            throw mapResponseError(exception);
        } catch (ResourceAccessException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_SERVICE_UNAVAILABLE",
                    "无法连接 DeepSeek，请检查网络或后端代理配置"
            );
        }
    }

    private DeepSeekCompletion parse(JsonNode response) {
        if (response == null || response.path("choices").isEmpty()) {
            throw invalidResponse();
        }
        JsonNode choice = response.path("choices").path(0);
        String finishReason = choice.path("finish_reason").asText();
        String content = choice.path("message").path("content").asText();
        if (content.isBlank()) {
            throw invalidResponse();
        }
        if ("length".equals(finishReason)) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_RESPONSE_TRUNCATED",
                    "DeepSeek 输出被截断，请缩短论文内容后重试"
            );
        }
        return new DeepSeekCompletion(
                content,
                response.path("model").asText(properties.getModel()),
                response.path("usage").path("total_tokens").asInt(0)
        );
    }

    private ApiException mapResponseError(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        String upstreamMessage = extractUpstreamMessage(exception);
        log.warn(
                "DeepSeek rejected a request: status={}, message={}",
                status,
                upstreamMessage.isBlank() ? "(empty)" : upstreamMessage
        );
        if (status == 400 || status == 422) {
            String detail = upstreamMessage.isBlank()
                    ? "请求格式或论文内容不符合接口要求"
                    : upstreamMessage;
            return new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_REQUEST_REJECTED",
                    "DeepSeek 拒绝了本次请求：" + detail
            );
        }
        if (status == 401) {
            return new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_AUTHENTICATION_FAILED",
                    "DeepSeek API Key 无效，请检查后端环境变量"
            );
        }
        if (status == 402) {
            return new ApiException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "AI_BALANCE_INSUFFICIENT",
                    "DeepSeek 账户余额不足，请充值后重试"
            );
        }
        if (status == 429) {
            return new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI_RATE_LIMITED",
                    "DeepSeek 请求过于频繁，请稍后重试"
            );
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AI_UPSTREAM_ERROR",
                "DeepSeek 服务暂时异常（HTTP " + status + "）"
        );
    }

    private String extractUpstreamMessage(RestClientResponseException exception) {
        try {
            JsonNode response = objectMapper.readTree(
                    exception.getResponseBodyAsString()
            );
            String message = response.path("error").path("message").asText();
            if (message.isBlank()) message = response.path("message").asText();
            if (message.isBlank()) return "";
            String safeMessage = message
                    .replaceAll("(?i)bearer\\s+[a-z0-9._-]+", "Bearer [redacted]")
                    .replaceAll("sk-[a-zA-Z0-9_-]+", "sk-[redacted]")
                    .replaceAll("[\\r\\n\\t]+", " ")
                    .trim();
            return safeMessage.length() > 300
                    ? safeMessage.substring(0, 300)
                    : safeMessage;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void ensureConfigured() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_NOT_CONFIGURED",
                    "后端尚未配置 DEEPSEEK_API_KEY"
            );
        }
    }

    private ApiException invalidResponse() {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AI_INVALID_RESPONSE",
                "DeepSeek 返回了无法解析的内容，请稍后重试"
        );
    }
}
