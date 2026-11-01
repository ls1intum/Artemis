package de.tum.cit.aet.artemis.communication.test_repository;

import java.time.Instant;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.repository.FaqRepository;

@Lazy
@Repository
@Primary
public interface FaqTestRepository extends FaqRepository {

    @Transactional
    @Modifying
    @Query("UPDATE Faq f SET f.createdDate=:date WHERE f.id=:id")
    void setCreationDate(@Param("id") long id, @Param("date") Instant date);
}
