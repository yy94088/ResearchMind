package cn.researchmind;

import cn.researchmind.config.JwtProperties;
import cn.researchmind.config.DeepSeekProperties;
import cn.researchmind.config.StorageProperties;
import cn.researchmind.config.PasswordResetProperties;
import cn.researchmind.config.AdminProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({
        JwtProperties.class,
        StorageProperties.class,
        DeepSeekProperties.class,
        PasswordResetProperties.class,
        AdminProperties.class
})
public class ResearchMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResearchMindApplication.class, args);
    }
}
