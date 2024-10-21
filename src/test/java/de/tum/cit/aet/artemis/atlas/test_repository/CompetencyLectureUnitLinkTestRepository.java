package de.tum.cit.aet.artemis.atlas.test_repository;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;

@Repository
@Primary
public interface CompetencyLectureUnitLinkTestRepository extends CompetencyLectureUnitLinkRepository {

    @Modifying
    @Transactional
    void deleteAllByLectureUnitId(long lectureUnitId);

}
