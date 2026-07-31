package cn.researchmind.graph;

import cn.researchmind.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph")
public class KnowledgeGraphController {

    private final KnowledgeGraphService graphService;

    public KnowledgeGraphController(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping
    public KnowledgeGraphView findCurrent(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return graphService.rebuild(principal.id());
    }

    @PostMapping("/rebuild")
    public KnowledgeGraphView rebuild(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return graphService.rebuild(principal.id());
    }
}
