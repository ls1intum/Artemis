package de.tum.cit.aet.artemis.exam.repository;

import java.util.Optional;
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
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategy;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomForDistributionDTO;

/**
 * Spring Data JPA repository for the {@link ExamRoom} entity.
 *
 * <p>
 * <b>Important:</b> Do NOT use {@code SELECT DISTINCT} on queries that return {@link ExamRoom}
 * or its related entities (e.g., LayoutStrategy). These entities contain {@code json} columns
 * ({@code exam_seats}, {@code parameters}) and PostgreSQL's {@code json} type does not support
 * equality operators, causing {@code SELECT DISTINCT} to fail at runtime.
 * Use {@link java.util.Set} return types or {@code stream().distinct()} in Java to deduplicate instead.
 *
 * <p>
 * TODO: Remove this restriction once the json columns are migrated to jsonb.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamRoomRepository extends ArtemisJpaRepository<ExamRoom, Long> {

    /**
     * Finds and returns all IDs of outdated and unused exam rooms.
     * An exam room is outdated if there exists a newer entry with the same room number.
     * An exam room is unused if it isn't connected to any exam.
     *
     * @return A collection of all outdated and unused exam rooms
     */
    @Query("""
            WITH latestRooms AS (
                SELECT
                    roomNumber AS roomNumber,
                    MAX(createdDate) AS maxCreatedDate
                FROM ExamRoom
                GROUP BY roomNumber
                HAVING COUNT(*) > 1
            )
            SELECT examRoom.id
            FROM ExamRoom examRoom
            JOIN latestRooms latestRoom
                ON examRoom.roomNumber = latestRoom.roomNumber
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
     * its {@code roomNumber}.
     *
     * <p>
     * Exam rooms that share the same {@code roomNumber} are successive versions of the same physical room,
     * ordered from oldest to newest by {@code createdDate} and, when two versions share the same creation
     * timestamp (the {@code datetime(3)} column only has millisecond resolution, so rapid re-uploads can
     * collide), by {@code id}. The latest version is therefore the row for which no other row with the same
     * {@code roomNumber} has a more recent {@code (createdDate, id)} pair.
     *
     * <p>
     * This is expressed as an anti-join via {@code NOT EXISTS}: a room is returned exactly when no "newer"
     * room with the same {@code roomNumber} exists. We deliberately avoid the SQL window function
     * {@code ROW_NUMBER() OVER (PARTITION BY ...)} wrapped in a derived sub-query: that pattern breaks
     * Hibernate's query bootstrap once {@code hibernate.boot.allow_jdbc_metadata_access} is disabled (which
     * we keep off to shorten production startup), whereas this {@code NOT EXISTS} formulation is plain SQL
     * that Hibernate can compile without probing JDBC metadata. Since {@code id} is unique, exactly one row
     * per {@code roomNumber} survives, and on the small {@code exam_room} table the optimizer resolves the
     * anti-join to an efficient self-join.
     *
     * @return a {@link Set} of IDs corresponding to the latest version of each unique exam room
     */
    @Query("""
            SELECT er.id
            FROM ExamRoom er
            WHERE NOT EXISTS (
                SELECT 1
                FROM ExamRoom newerVersion
                WHERE newerVersion.roomNumber = er.roomNumber
                    AND (
                        newerVersion.createdDate > er.createdDate
                        OR (newerVersion.createdDate = er.createdDate AND newerVersion.id > er.id)
                    )
            )
            """)
    Set<Long> findAllIdsOfNewestExamRoomVersions();

    @EntityGraph(type = EntityGraph.EntityGraphType.LOAD, attributePaths = { "layoutStrategies" })
    Set<ExamRoom> findAllWithEagerLayoutStrategiesByIdIn(Set<Long> ids);

    /**
     * Returns a collection of {@link ExamRoomForDistributionDTO}, which are derived from {@link ExamRoom}.
     *
     * @implNote Selects the latest version of each unique exam room with the same {@code NOT EXISTS}
     *           anti-join as {@link #findAllIdsOfNewestExamRoomVersions} and projects it directly into the DTO.
     *
     * @return Basic room information for distribution
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exam.dto.room.ExamRoomForDistributionDTO(
                er.id,
                er.roomNumber,
                er.alternativeRoomNumber,
                er.name,
                er.alternativeName,
                er.building
            )
            FROM ExamRoom er
            WHERE NOT EXISTS (
                SELECT 1
                FROM ExamRoom newerVersion
                WHERE newerVersion.roomNumber = er.roomNumber
                    AND (
                        newerVersion.createdDate > er.createdDate
                        OR (newerVersion.createdDate = er.createdDate AND newerVersion.id > er.id)
                    )
            )
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

    /**
     * Finds the exam room connected to the given exam ID with the specified room number, including its layout strategies.
     *
     * @param examId     The exam id
     * @param roomNumber The room number
     * @return The exam room with layout eagerly loaded layout strategies if it exists, empty otherwise
     */
    @Query("""
            SELECT er
            FROM ExamRoom er
            JOIN ExamRoomExamAssignment erea
                ON er.id = erea.examRoom.id
            LEFT JOIN FETCH er.layoutStrategies
            WHERE erea.exam.id = :examId
                AND er.roomNumber = :roomNumber
            """)
    Optional<ExamRoom> findByExamIdAndRoomNumberWithLayoutStrategies(@Param("examId") long examId, @Param("roomNumber") String roomNumber);

    /**
     * Checks whether a room with the given room number is connected to the specified exam.
     *
     * @param roomNumber The room number
     * @param examId     The exam id
     * @return {@code true} if such a room exists and is connected to the exam, {@code false} otherwise
     */
    @Query("""
            SELECT COUNT(*) > 0
            FROM ExamRoom er
            JOIN ExamRoomExamAssignment erea
                ON er.id = erea.examRoom.id
            WHERE erea.exam.id = :examId
                AND er.roomNumber = :roomNumber
            """)
    boolean existsByRoomNumberAndIsConnectedToExam(@Param("roomNumber") String roomNumber, @Param("examId") long examId);

    /**
     * Finds all newest versions of {@link ExamRoom}s with eagerly loaded {@link LayoutStrategy}s
     *
     * @return All newest room versions with eagerly loaded layout strategies.
     */
    default Set<ExamRoom> findAllNewestExamRoomVersionsWithEagerLayoutStrategies() {
        Set<Long> idsOfNewestExamRoomVersions = findAllIdsOfNewestExamRoomVersions();
        return findAllWithEagerLayoutStrategiesByIdIn(idsOfNewestExamRoomVersions);
    }
}
