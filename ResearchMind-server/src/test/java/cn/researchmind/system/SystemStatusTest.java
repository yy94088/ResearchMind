package cn.researchmind.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class SystemStatusTest {

    @Test
    void shouldExposeInfrastructureState() {
        SystemStatus status = new SystemStatus(
                "UP",
                "UP",
                23,
                "PONG",
                "UP",
                OffsetDateTime.now()
        );

        assertThat(status.application()).isEqualTo("UP");
        assertThat(status.databaseTableCount()).isEqualTo(23);
        assertThat(status.redis()).isEqualTo("PONG");
        assertThat(status.objectStorage()).isEqualTo("UP");
    }
}
