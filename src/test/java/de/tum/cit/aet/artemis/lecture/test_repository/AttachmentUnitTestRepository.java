package de.tum.cit.aet.artemis.lecture.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentUnitRepository;

@Repository
@Primary
public interface AttachmentUnitTestRepository extends AttachmentUnitRepository {

    @EntityGraph(type = LOAD, attributePaths = "competencyLinks")
    AttachmentUnit findOneWithCompetencyLinksById(long attachmentUnitId);
}
