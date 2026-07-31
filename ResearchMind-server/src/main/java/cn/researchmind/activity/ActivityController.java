package cn.researchmind.activity;

import java.util.List;

import cn.researchmind.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final OperationLogService operationLogService;

    public ActivityController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public List<RecentActivityView> recent(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return operationLogService.findRecent(principal.id());
    }
}
