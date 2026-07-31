package cn.researchmind.graph;

import java.math.BigDecimal;
import java.util.Map;

public record GraphRelationView(
        String id,
        String source,
        String target,
        GraphRelationType type,
        BigDecimal weight,
        Map<String, Object> properties
) {
}
