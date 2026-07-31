package cn.researchmind.graph;

import java.util.Map;

public record GraphNodeView(
        String id,
        String referenceId,
        GraphNodeType type,
        String name,
        Map<String, Object> properties,
        int degree
) {
}
