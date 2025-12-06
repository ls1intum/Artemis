package de.tum.cit.aet.artemis.exam.repository;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomForDistributionDTO;

/**
 * Spring Data JPA repository for the {@link ExamRoom} entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamRoomRepository extends ArtemisJpaRepository<ExamRoom, Long> {

    @Query("""
            SELECT er
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
     * Returns the IDs of the current (latest) version of each unique exam room, uniquely identified by
     * its combination of {@code roomNumber} and {@code name}.
     *
     * <p>
     * This query uses the SQL window function {@code ROW_NUMBER()} in combination with
     * the {@code OVER (PARTITION BY ... ORDER BY ...)} clause to identify, for each group of
     * exam rooms that share the same {@code roomNumber} and {@code name}, which row represents
     * the latest version.
     *
     * <p>
     * <b>Why {@code OVER (PARTITION BY ...)} works:</b><br>
     * The {@code OVER} clause defines a <i>window</i> — a set of rows related to the current row —
     * over which a window function (here {@code ROW_NUMBER()}) operates. Unlike aggregate
     * functions (e.g., {@code MAX()}, {@code COUNT()}), window functions do not collapse rows
     * into a single result. Instead, each row retains its identity while having access to other rows
     * in its partition.
     * <ul>
     * <li>{@code PARTITION BY er.roomNumber, er.name} divides the full result set into
     * partitions, one per unique room number/name combination.</li>
     * <li>{@code ORDER BY er.createdDate DESC, er.id DESC} orders each partition so that the
     * most recently created room (and, in case of ties, the one with the highest ID) appears first.</li>
     * <li>{@code ROW_NUMBER()} then assigns an increasing row number within each partition
     * according to this order.</li>
     * </ul>
     * By selecting only rows where {@code rowNumber = 1}, the query effectively filters out all
     * but the latest version of each unique exam room.
     *
     * <p>
     * This use of window functions is compliant with the SQL standard and supported by
     * including MySQL ≥ 8.0 and PostgreSQL ≥ 9.4. Hibernate also supports such queries in JPQL/HQL,
     * as documented in <a href="https://docs.hibernate.org/orm/current/userguide/html_single/#hql-aggregate-functions-window">the Hibernate User Guide</a>
     *
     * @return a {@link Set} of IDs corresponding to the latest version of each unique exam room
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

    /**
     * Returns a collection of {@link ExamRoomForDistributionDTO}, which are derived from {@link ExamRoom}.
     *
     * @implNote Uses the same PARTITION BY trick as explained in {@link #findAllIdsOfCurrentExamRooms}
     *
     * @return Basic room information for distribution
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exam.dto.room.ExamRoomForDistributionDTO(
                roomPartition.id,
                roomPartition.roomNumber,
                roomPartition.alternativeRoomNumber,
                roomPartition.name,
                roomPartition.alternativeName,
                roomPartition.building
            )
            FROM (
                SELECT er.id AS id, er.roomNumber AS roomNumber, er.alternativeRoomNumber AS alternativeRoomNumber, er.name AS name, er.alternativeName AS alternativeName, er.building AS building, er.createdDate AS createdDate, ROW_NUMBER() OVER (
                    PARTITION BY er.roomNumber, er.name
                    ORDER BY er.createdDate DESC, er.id DESC
                ) AS rowNumber
                FROM ExamRoom er
            ) roomPartition
            WHERE roomPartition.rowNumber = 1
            """)
    Set<ExamRoomForDistributionDTO> findAllCurrentExamRoomsForDistribution();

    @Query("""
            SELECT er
            FROM ExamRoom er
            JOIN ExamRoomExamAssignment erea
                ON er.id = erea.examRoom.id
            WHERE erea.exam.id = :examId
            """)
    Set<ExamRoom> findAllByExamId(@Param("examId") long examId);

    List<ExamRoom> findAllByRoomNumber(String roomNumber);

    @Query("""
            SELECT er
            FROM ExamRoom er
            JOIN ExamRoomExamAssignment erea
                ON er.id = erea.examRoom.id
            LEFT JOIN FETCH er.layoutStrategies
            WHERE erea.exam.id = :examId
                AND er.roomNumber = :newRoomNumber
            """)
    Set<ExamRoom> findAllByExamIdAndRoomNumberWithLayoutStrategies(@Param("examId") long examId, @Param("newRoomNumber") String newRoomNumber);
}
