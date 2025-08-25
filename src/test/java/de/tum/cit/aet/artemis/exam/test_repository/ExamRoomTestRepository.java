package de.tum.cit.aet.artemis.exam.test_repository;

import java.util.List;
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

    /**
     * Finds and returns all outdated and unused exam rooms.
     * An exam room is outdated if there exists a newer entry of the same (number, name) combination.
     * An exam room is unused if it isn't connected to any exam.
     *
     * @return A collection of all outdated and unused exam rooms
     */
    @Query(value = """
            WITH latestRooms AS (
                SELECT
                    roomNumber AS roomNumber,
                    name AS name,
                    MAX(createdDate) AS maxCreatedDate
                FROM ExamRoom
                GROUP BY roomNumber, name
                HAVING COUNT(*) > 1
            )
            SELECT examRoom
            FROM ExamRoom examRoom
            JOIN latestRooms latestRoom
                ON examRoom.roomNumber = latestRoom.roomNumber
                AND examRoom.name = latestRoom.name
                AND examRoom.createdDate < latestRoom.maxCreatedDate
            WHERE examRoom.id NOT IN ( SELECT DISTINCT examRoom.id FROM ExamRoomExamAssignment )
            """)
    List<ExamRoom> findAllOutdatedAndUnusedExamRooms();

    @Query(value = """
                    WITH latestRooms AS (
                        SELECT
                            roomNumber AS roomNumber,
                            name AS name,
                            MAX(createdDate) AS maxCreatedDate
                        FROM ExamRoom
                        GROUP BY roomNumber, name
                    )
                    SELECT examRoom
                    FROM ExamRoom examRoom
                    JOIN latestRooms latestRoom
                        ON examRoom.roomNumber = latestRoom.roomNumber
                        AND examRoom.name = latestRoom.name
                        AND examRoom.createdDate = latestRoom.maxCreatedDate
                    LEFT JOIN FETCH examRoom.layoutStrategies
            """)
    Set<ExamRoom> findAllNewestExamRoomVersionsWithEagerLayoutStrategies();

    @Query(value = """
                    WITH latestRooms AS (
                        SELECT
                            roomNumber AS roomNumber,
                            name AS name,
                            MAX(createdDate) AS maxCreatedDate
                        FROM ExamRoom
                        GROUP BY roomNumber, name
                    )
                    SELECT examRoom
                    FROM ExamRoom examRoom
                    JOIN latestRooms latestRoom
                        ON examRoom.roomNumber = latestRoom.roomNumber
                        AND examRoom.name = latestRoom.name
                        AND examRoom.createdDate = latestRoom.maxCreatedDate
            """)
    Set<ExamRoom> findAllNewestExamRoomVersions();
}
