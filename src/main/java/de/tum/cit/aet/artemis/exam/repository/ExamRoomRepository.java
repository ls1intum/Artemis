package de.tum.cit.aet.artemis.exam.repository;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomSeatCountDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomUniqueRoomsDTO;

/**
 * Spring Data JPA repository for the {@link de.tum.cit.aet.artemis.exam.domain.room.ExamRoom} entity.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamRoomRepository extends ArtemisJpaRepository<ExamRoom, Long> {

    @Query("""
            WITH latestRooms AS (
                SELECT
                    roomNumber AS roomNumber,
                    alternativeRoomNumber AS alternativeRoomNumber,
                    name AS name,
                    alternativeName AS alternativeName,
                    MAX(createdDate) AS latestCreatedDate
                FROM ExamRoom
                GROUP BY roomNumber, name
                HAVING COUNT(*) > 1
            )
            SELECT examRoom
            FROM ExamRoom examRoom
            JOIN latestRooms latestRoom
                ON examRoom.roomNumber = latestRoom.roomNumber
                AND examRoom.name = latestRoom.name
                AND examRoom.createdDate < latestRoom.latestCreatedDate
            ORDER BY examRoom.roomNumber, examRoom.name, examRoom.createdDate DESC
            """)
    Set<ExamRoom> findAllOutdatedExamRooms();

    @Query("SELECT COUNT(*) FROM ExamRoom")
    Integer countAllExamRooms();

    @Query("SELECT COUNT(*) FROM ExamSeat")
    Integer countAllExamSeats();

    @Query("SELECT COUNT(*) FROM LayoutStrategy")
    Integer countAllLayoutStrategies();

    @Query("SELECT DISTINCT strategy.name FROM LayoutStrategy strategy")
    Set<String> findDistinctLayoutStrategyNames();

    @Query("""
            SELECT er
            FROM ExamRoom er
            LEFT JOIN FETCH er.layoutStrategies
            """)
    Set<ExamRoom> findAllExamRoomsWithEagerLayoutStrategies();

    @Query("""
            SELECT NEW de.tum.cit.aet.artemis.exam.dto.room.ExamRoomSeatCountDTO(
                er.id,
                COUNT(seat)
            )
            FROM ExamRoom er
            LEFT JOIN er.seats seat
            GROUP BY er.id
            """)
    Set<ExamRoomSeatCountDTO> countSeatsPerRoom();

    @Query("""
            WITH latestRoomsRoomNumberNameAndCreatedDate AS (
                SELECT
                    roomNumber AS roomNumber,
                    name AS name,
                    MAX(createdDate) AS createdDate
                FROM ExamRoom
                GROUP BY roomNumber, name
            ),
            latestRooms AS (
                SELECT er.id AS id
                FROM ExamRoom er
                JOIN latestRoomsRoomNumberNameAndCreatedDate roomNumberNameCreated
                    ON er.roomNumber = roomNumberNameCreated.roomNumber
                    AND er.name = roomNumberNameCreated.name
                    AND er.createdDate = roomNumberNameCreated.createdDate
            )
            SELECT NEW de.tum.cit.aet.artemis.exam.dto.room.ExamRoomUniqueRoomsDTO(
                COUNT(*),
                (SELECT COUNT(*) FROM ExamSeat seat WHERE seat.examRoom.id IN (SELECT id FROM latestRooms)),
                (SELECT COUNT(*) FROM LayoutStrategy ls WHERE ls.examRoom.id IN (SELECT id FROM latestRooms))
            )
            FROM latestRooms
            """)
    ExamRoomUniqueRoomsDTO countUniqueRoomsSeatsAndLayoutStrategies();

}
