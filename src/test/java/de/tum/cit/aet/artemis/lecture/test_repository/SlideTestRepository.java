package de.tum.cit.aet.artemis.lecture.test_repository;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Lazy
@Repository
@Primary
public interface SlideTestRepository extends SlideRepository {
    // Intentionally empty: it exists so tests resolve slides through a test repository, as the architecture rules
    // require. It used to re-declare findAllByAttachmentVideoUnitId, which shadowed the parent's declaration and
    // dropped its @Param binding once that method gained an explicit @Query - every call then failed with
    // "No argument for named parameter ':attachmentUnitId'".
}
