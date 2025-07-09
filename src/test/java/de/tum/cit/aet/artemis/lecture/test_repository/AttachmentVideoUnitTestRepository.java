package de.tum.cit.aet.artemis.lecture.test_repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;

@Repository
@Primary
public interface AttachmentVideoUnitTestRepository extends AttachmentVideoUnitRepository {

    @EntityGraph(type = LOAD, attributePaths = "competencyLinks")
    AttachmentVideoUnit findOneWithCompetencyLinksById(long attachmentVideoUnitId);
}
