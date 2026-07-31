package cn.researchmind.config;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class DeepSeekConfig {

    @Bean
    public RestClient deepSeekRestClient(DeepSeekProperties properties) {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout());

        String proxyUrl = firstNonBlank(
                properties.getProxyUrl(),
                System.getenv("HTTPS_PROXY"),
                System.getenv("https_proxy")
        );
        if (proxyUrl != null) {
            URI proxy = URI.create(proxyUrl);
            int port = proxy.getPort() > 0 ? proxy.getPort() : 80;
            clientBuilder.proxy(ProxySelector.of(
                    new InetSocketAddress(proxy.getHost(), port)
            ));
        }

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(clientBuilder.build());
        requestFactory.setReadTimeout(properties.getReadTimeout());

        RestClient.Builder restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory);
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            restClient.defaultHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + properties.getApiKey().trim()
            );
        }
        return restClient.build();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
