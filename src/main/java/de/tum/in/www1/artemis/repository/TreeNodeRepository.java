package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TreeNode;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

/**
 * Spring Data repository for the TreeNode entity.
 */
@Repository
public interface TreeNodeRepository extends JpaRepository<TreeNode, Long> {

    @EntityGraph(type = LOAD, attributePaths = "clusterTree")
    List<TreeNode> findAllByExercise(TextExercise exercise);
}
