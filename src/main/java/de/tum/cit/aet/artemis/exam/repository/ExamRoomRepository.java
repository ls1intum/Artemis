package de.tum.cit.aet.artemis.exam.repository;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
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
            SELECT DISTINCT er
            FROM ExamRoom er
            LEFT JOIN FETCH er.layoutStrategies
            """)
    Set<ExamRoom> findAllExamRoomsWithEagerLayoutStrategies();

    /**
     * Finds and returns all IDs of outdated and unused exam rooms.
     * An exam room is outdated if there exists a newer entry of the same (number, name) combination.
     * An exam room is unused if it isn't connected to any exam.
     *
     * @return A collection of all outdated and unused exam rooms
     */
    @Query("""
            WITH latestRooms AS (
                SELECT
                    roomNumber AS roomNumber,
                    name AS name,
                    MAX(createdDate) AS maxCreatedDate
                FROM ExamRoom
                GROUP BY roomNumber, name
                HAVING COUNT(*) > 1
            )
            SELECT examRoom.id
            FROM ExamRoom examRoom
            JOIN latestRooms latestRoom
                ON examRoom.roomNumber = latestRoom.roomNumber
                AND examRoom.name = latestRoom.name
                AND examRoom.createdDate < latestRoom.maxCreatedDate
            WHERE NOT EXISTS (
                SELECT 1
                FROM ExamRoomExamAssignment erea
                WHERE erea.examRoom.id = examRoom.id
            )
            """)
    Set<Long> findAllIdsOfOutdatedAndUnusedExamRooms();

    /**
     * Returns IDs of the current (latest) version of each unique exam room (roomNumber, name).
     *
     * @return All IDs of the current exam rooms
     */
    @Query("""
            SELECT id
            FROM (
                SELECT er.id AS id, er.roomNumber AS roomNumber, er.name AS name, er.createdDate AS createdDate, ROW_NUMBER() OVER (
                    PARTITION BY er.roomNumber, er.name
                    ORDER BY er.createdDate DESC, er.id DESC
                ) AS rowNumber
                FROM ExamRoom er
            )
            WHERE rowNumber = 1
            """)
    Set<Long> findAllIdsOfCurrentExamRooms();

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = { "layoutStrategies" })
    Set<ExamRoom> findAllWithEagerLayoutStrategiesByIdIn(Set<Long> ids);
}
