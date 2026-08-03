package cn.researchmind.admin;

import cn.researchmind.config.AdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AdminRepository adminRepository;
    private final AdminProperties properties;

    public AdminBootstrap(
            AdminRepository adminRepository,
            AdminProperties properties
    ) {
        this.adminRepository = adminRepository;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = properties.initialEmail();
        if (email == null || email.isBlank() || adminRepository.countAdmins() > 0) return;
        int promoted = adminRepository.promoteInitialAdmin(email.trim().toLowerCase());
        if (promoted > 0) {
            log.info("Configured initial administrator account was activated");
        } else {
            log.warn("INITIAL_ADMIN_EMAIL does not match an existing active account");
        }
    }
}
