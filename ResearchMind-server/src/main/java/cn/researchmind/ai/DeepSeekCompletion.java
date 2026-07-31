package cn.researchmind.ai;

public record DeepSeekCompletion(
        String content,
        String model,
        int totalTokens
) {
}
