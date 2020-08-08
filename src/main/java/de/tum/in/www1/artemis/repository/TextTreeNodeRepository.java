package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.text.TextExercise;
import de.tum.in.www1.artemis.domain.text.TextTreeNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data repository for the TextTreeNode entity.
 */
@Repository
public interface TextTreeNodeRepository extends JpaRepository<TextTreeNode, Long> {

    List<TextTreeNode> findAllByExercise(TextExercise exercise);

    List<TextTreeNode> findAllByParentAndExercise(long parent, TextExercise exercise);
}
