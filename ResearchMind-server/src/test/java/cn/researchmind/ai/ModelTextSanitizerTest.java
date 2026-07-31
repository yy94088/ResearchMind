package cn.researchmind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ModelTextSanitizerTest {

    private final ModelTextSanitizer sanitizer = new ModelTextSanitizer();

    @Test
    void shouldRemoveCharactersRejectedByModelGateways() {
        String value = "A\u0000B\uD800C\uE000D\uFFFF 𝑐\n下一行";

        assertThat(sanitizer.sanitize(value))
                .isEqualTo("ABCD 𝑐\n下一行");
    }
}
