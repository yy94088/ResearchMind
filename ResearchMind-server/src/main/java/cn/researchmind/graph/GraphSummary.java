package cn.researchmind.graph;

import java.util.Map;

public record GraphSummary(
        int nodeCount,
        int relationCount,
        Map<GraphNodeType, Long> nodeTypes,
        Map<GraphRelationType, Long> relationTypes
) {
}
