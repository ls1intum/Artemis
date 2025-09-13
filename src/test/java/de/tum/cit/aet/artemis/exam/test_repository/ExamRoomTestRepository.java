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
            WITH latestRooms AS (
                SELECT
                    roomNumber AS roomNumber,
                    name AS name,
                    MAX(createdDate) AS maxCreatedDate
                FROM ExamRoom
                GROUP BY roomNumber, name
            )
            SELECT DISTINCT examRoom
            FROM ExamRoom examRoom
            JOIN latestRooms latestRoom
                ON examRoom.roomNumber = latestRoom.roomNumber
                AND examRoom.name = latestRoom.name
                AND examRoom.createdDate = latestRoom.maxCreatedDate
            LEFT JOIN FETCH examRoom.layoutStrategies
            """)
    Set<ExamRoom> findAllNewestExamRoomVersionsWithEagerLayoutStrategies();

    @Query("""
            WITH latestRooms AS (
                SELECT
                    roomNumber AS roomNumber,
                    name AS name,
                    MAX(createdDate) AS maxCreatedDate
                FROM ExamRoom
                GROUP BY roomNumber, name
            )
            SELECT examRoom.id
            FROM ExamRoom examRoom
            JOIN latestRooms latestRoom
                ON examRoom.roomNumber = latestRoom.roomNumber
                AND examRoom.name = latestRoom.name
                AND examRoom.createdDate = latestRoom.maxCreatedDate
            WHERE examRoom.roomNumber IN :roomNumbers
            """)
    Set<Long> findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set<String> roomNumbers);
}
