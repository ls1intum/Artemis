package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.TextAssesmentEvent;
import de.tum.in.www1.artemis.domain.TextBlockType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;

/**
 * Spring Data repository for the TextAssessmentEvent entity.
 */
@SuppressWarnings("unused")
@Repository
public interface TextAssessmentEventRepository extends JpaRepository<TextAssesmentEvent, Long> {

    List<TextAssesmentEvent> findAllByFeedbackType(FeedbackType type);

    List<TextAssesmentEvent> findAllByEventType(TextAssesmentEvent type);

    List<TextAssesmentEvent> findAllBySegmentType(TextBlockType type);
}
