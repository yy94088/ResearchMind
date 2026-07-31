package cn.researchmind.graph;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeGraphService {

    private final KnowledgeGraphRepository graphRepository;

    public KnowledgeGraphService(KnowledgeGraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    @Transactional
    public KnowledgeGraphView rebuild(String ownerId) {
        graphRepository.lockOwner(ownerId);
        graphRepository.rebuild(ownerId);

        List<GraphNodeView> nodes = graphRepository.findNodes(ownerId);
        List<GraphRelationView> relations = graphRepository.findRelations(ownerId);
        return new KnowledgeGraphView(
                nodes,
                relations,
                new GraphSummary(
                        nodes.size(),
                        relations.size(),
                        countsByNodeType(nodes),
                        countsByRelationType(relations)
                ),
                OffsetDateTime.now()
        );
    }

    private Map<GraphNodeType, Long> countsByNodeType(List<GraphNodeView> nodes) {
        Map<GraphNodeType, Long> counts = nodes.stream().collect(Collectors.groupingBy(
                GraphNodeView::type,
                () -> new EnumMap<>(GraphNodeType.class),
                Collectors.counting()
        ));
        return Map.copyOf(counts);
    }

    private Map<GraphRelationType, Long> countsByRelationType(
            List<GraphRelationView> relations
    ) {
        Map<GraphRelationType, Long> counts = relations.stream().collect(
                Collectors.groupingBy(
                        GraphRelationView::type,
                        () -> new EnumMap<>(GraphRelationType.class),
                        Collectors.counting()
                )
        );
        return Map.copyOf(counts);
    }
}
