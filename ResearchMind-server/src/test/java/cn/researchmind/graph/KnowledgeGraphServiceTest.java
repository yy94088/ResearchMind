package cn.researchmind.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphServiceTest {

    @Mock private KnowledgeGraphRepository graphRepository;
    @InjectMocks private KnowledgeGraphService graphService;

    @Test
    void shouldRebuildGraphAndReturnTypeStatistics() {
        List<GraphNodeView> nodes = List.of(
                new GraphNodeView(
                        "node-paper",
                        "paper-1",
                        GraphNodeType.PAPER,
                        "图神经网络综述",
                        Map.of("year", 2025),
                        2
                ),
                new GraphNodeView(
                        "node-keyword",
                        "keyword-1",
                        GraphNodeType.KEYWORD,
                        "图神经网络",
                        Map.of(),
                        1
                )
        );
        List<GraphRelationView> relations = List.of(new GraphRelationView(
                "relation-1",
                "node-paper",
                "node-keyword",
                GraphRelationType.HAS_KEYWORD,
                BigDecimal.ONE,
                Map.of()
        ));
        when(graphRepository.findNodes("user-1")).thenReturn(nodes);
        when(graphRepository.findRelations("user-1")).thenReturn(relations);

        KnowledgeGraphView result = graphService.rebuild("user-1");

        InOrder calls = inOrder(graphRepository);
        calls.verify(graphRepository).lockOwner("user-1");
        calls.verify(graphRepository).rebuild("user-1");
        calls.verify(graphRepository).findNodes("user-1");
        calls.verify(graphRepository).findRelations("user-1");
        assertThat(result.summary().nodeCount()).isEqualTo(2);
        assertThat(result.summary().relationCount()).isEqualTo(1);
        assertThat(result.summary().nodeTypes())
                .containsEntry(GraphNodeType.PAPER, 1L)
                .containsEntry(GraphNodeType.KEYWORD, 1L);
        assertThat(result.summary().relationTypes())
                .containsEntry(GraphRelationType.HAS_KEYWORD, 1L);
    }
}
