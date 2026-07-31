package cn.researchmind.preference;

import cn.researchmind.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/preferences")
public class UserPreferenceController {

    private final UserPreferenceService preferenceService;

    public UserPreferenceController(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public UserPreferences find(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return preferenceService.find(principal.id());
    }

    @PutMapping
    public UserPreferences save(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody UserPreferences preferences
    ) {
        return preferenceService.save(principal.id(), preferences);
    }
}
