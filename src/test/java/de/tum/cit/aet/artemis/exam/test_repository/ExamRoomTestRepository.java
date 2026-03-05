package de.tum.cit.aet.artemis.exam.test_repository;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomRepository;

@Conditional(ExamEnabled.class)
@Lazy
@Repository
@Primary
public interface ExamRoomTestRepository extends ExamRoomRepository {

    // TODO: Add DISTINCT back once the json columns (exam_seats, parameters) are migrated to jsonb.
    // PostgreSQL's json type does not support equality operators, so SELECT DISTINCT fails
    // when Hibernate includes json columns in the SQL. The Set return type deduplicates in Java instead.
    @Query("""
            WITH latestRooms AS (
                SELECT
                    roomNumber AS roomNumber,
                    MAX(createdDate) AS maxCreatedDate
                FROM ExamRoom
                GROUP BY roomNumber
            )
            SELECT examRoom
            FROM ExamRoom examRoom
            JOIN latestRooms latestRoom
                ON examRoom.roomNumber = latestRoom.roomNumber
                AND examRoom.createdDate = latestRoom.maxCreatedDate
            LEFT JOIN FETCH examRoom.layoutStrategies
            """)
    Set<ExamRoom> findAllNewestExamRoomVersionsWithEagerLayoutStrategies();

    // TODO: Add DISTINCT back once the json columns (exam_seats, parameters) are migrated to jsonb.
    // See comment on findAllNewestExamRoomVersionsWithEagerLayoutStrategies.
    @Query("""
            WITH latestRooms AS (
                SELECT
                    roomNumber AS roomNumber,
                    MAX(createdDate) AS maxCreatedDate
                FROM ExamRoom
                GROUP BY roomNumber
            )
            SELECT examRoom
            FROM ExamRoom examRoom
            JOIN latestRooms latestRoom
                ON examRoom.roomNumber = latestRoom.roomNumber
                AND examRoom.createdDate = latestRoom.maxCreatedDate
            """)
    Set<ExamRoom> findAllNewestExamRoomVersions();

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
                AND examRoom.createdDate = latestRoom.maxCreatedDate
            WHERE examRoom.roomNumber IN :roomNumbers
            """)
    Set<Long> findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set<String> roomNumbers);
}
