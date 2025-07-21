package de.tum.cit.aet.artemis.exam.repository;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;

/**
 * Spring Data JPA repository for the {@link de.tum.cit.aet.artemis.exam.domain.room.ExamRoom} entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamRoomRepository extends ArtemisJpaRepository<ExamRoom, Long> {

    @Query("""
            WITH latestRooms AS (
                SELECT roomNumber, alternativeRoomNumber, name, alternativeName, MAX(createdDate) AS createdDate
                FROM ExamRoom
                GROUP BY roomNumber, alternativeRoomNumber, name, alternativeName
                HAVING COUNT(*) > 1
            )
            SELECT examRoom
            FROM ExamRoom examRoom
            JOIN latestRooms latestRoom
                ON examRoom.roomNumber = latestRoom.roomNumber
                AND examRoom.alternativeName = latestRoom.alternativeName
                AND examRoom.name = latestRoom.name
                AND examRoom.alternativeName = latestRoom.alternativeName
                AND examRoom.createdDate < latestRoom.createdDate
            ORDER BY examRoom.roomNumber, examRoom.alternativeRoomNumber, examRoom.name, examRoom.alternativeName, examRoom.createdDate DESC
            """)
    List<ExamRoom> findAllOutdatedExamRooms();
}
