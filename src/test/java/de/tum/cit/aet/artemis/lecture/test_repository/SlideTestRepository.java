package de.tum.cit.aet.artemis.lecture.test_repository;

import java.util.List;

import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Repository
public interface SlideTestRepository extends SlideRepository {

    List<Slide> findAllByAttachmentUnitId(Long attachmentUnitId);
}
