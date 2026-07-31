package cn.researchmind.graph;

import java.time.OffsetDateTime;
import java.util.List;

public record KnowledgeGraphView(
        List<GraphNodeView> nodes,
        List<GraphRelationView> relations,
        GraphSummary summary,
        OffsetDateTime generatedAt
) {
}
