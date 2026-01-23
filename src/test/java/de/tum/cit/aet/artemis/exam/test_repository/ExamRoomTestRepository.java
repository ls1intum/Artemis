package de.tum.cit.aet.artemis.exam.test_repository;

import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomRepository;

@Lazy
@Repository
@Primary
public interface ExamRoomTestRepository extends ExamRoomRepository {

    @Query("""
            SELECT DISTINCT examRoom
            FROM ExamRoom examRoom
            LEFT JOIN FETCH examRoom.layoutStrategies
            WHERE examRoom.createdDate = (
                SELECT MAX(er2.createdDate)
                FROM ExamRoom er2
                WHERE er2.roomNumber = examRoom.roomNumber
            )
            """)
    Set<ExamRoom> findAllNewestExamRoomVersionsWithEagerLayoutStrategies();

    @Query("""
            SELECT examRoom
            FROM ExamRoom examRoom
            WHERE examRoom.createdDate = (
                SELECT MAX(er2.createdDate)
                FROM ExamRoom er2
                WHERE er2.roomNumber = examRoom.roomNumber
            )
            """)
    Set<ExamRoom> findAllNewestExamRoomVersions();

    @Query("""
            SELECT examRoom.id
            FROM ExamRoom examRoom
            WHERE examRoom.roomNumber IN :roomNumbers
                AND examRoom.createdDate = (
                    SELECT MAX(er2.createdDate)
                    FROM ExamRoom er2
                    WHERE er2.roomNumber = examRoom.roomNumber
                )
            """)
    Set<Long> findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set<String> roomNumbers);
}
