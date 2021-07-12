package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextBlockType;
import de.tum.in.www1.artemis.domain.analytics.TextAssessmentEvent;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;

/**
 * Spring Data repository for the TextAssessmentEvent entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextAssessmentEventRepository extends JpaRepository<TextAssessmentEvent, Long> {

    List<TextAssessmentEvent> findAllByFeedbackType(FeedbackType type);

    List<TextAssessmentEvent> findAllByEventType(TextAssessmentEvent type);

    List<TextAssessmentEvent> findAllBySegmentType(TextBlockType type);

    List<TextAssessmentEvent> findAllByCourseId(Long id);
}
