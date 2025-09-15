package de.tum.cit.aet.artemis.exam.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoomExamAssignment;
import de.tum.cit.aet.artemis.exam.dto.room.ExamSeatDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomExamAssignmentRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;

/**
 * Service Implementation for managing distributions of exam users to exam rooms in an exam.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamRoomDistributionService {

    private static final Logger log = LoggerFactory.getLogger(ExamRoomDistributionService.class);

    private static final String ENTITY_NAME = "examRoomDistributionService";

    private final ExamRepository examRepository;

    private final ExamRoomRepository examRoomRepository;

    private final ExamRoomService examRoomService;

    private final ExamRoomExamAssignmentRepository examRoomExamAssignmentRepository;

    private final ExamUserRepository examUserRepository;

    public ExamRoomDistributionService(ExamRepository examRepository, ExamRoomRepository examRoomRepository, ExamRoomService examRoomService,
            ExamRoomExamAssignmentRepository examRoomExamAssignmentRepository, ExamUserRepository examUserRepository) {
        this.examRepository = examRepository;
        this.examRoomRepository = examRoomRepository;
        this.examRoomService = examRoomService;
        this.examRoomExamAssignmentRepository = examRoomExamAssignmentRepository;
        this.examUserRepository = examUserRepository;
    }

    /**
     * Distribute all students who are registered for a given exam across a selection of rooms.
     * Existing planned seats and room assignments are replaced.
     *
     * @param examId      The exam
     * @param examRoomIds The ids of the rooms to distribute to
     * @implNote Currently only the "default" layout strategy is used.
     */
    public void distributeRegisteredStudents(long examId, @NotEmpty Set<Long> examRoomIds) {
        final Exam exam = examRepository.findByIdWithExamUsersElseThrow(examId);
        final var examRoomsForExam = examRoomRepository.findAllWithEagerLayoutStrategiesByIdIn(examRoomIds);

        final var examUsers = exam.getExamUsers();

        final int numberOfUsableSeats = examRoomsForExam.stream().mapToInt(examRoom -> examRoomService.getDefaultLayoutStrategyOrElseThrow(examRoom).getCapacity()).sum();

        if (numberOfUsableSeats < examUsers.size()) {
            throw new BadRequestAlertException("Not enough seats available in the selected rooms", ENTITY_NAME, "notEnoughExamSeats",
                    Map.of("numberOfUsableSeats", numberOfUsableSeats, "numberOfExamUsers", examUsers.size()));
        }

        examRoomExamAssignmentRepository.deleteAllByExamId(examId);
        for (ExamRoom examRoom : examRoomsForExam) {
            var examRoomExamAssignment = new ExamRoomExamAssignment();
            examRoomExamAssignment.setExamRoom(examRoom);
            examRoomExamAssignment.setExam(exam);
            examRoomExamAssignmentRepository.save(examRoomExamAssignment);
        }

        // Now we distribute students to the seats
        Map<String, List<ExamSeatDTO>> roomNumberToUsableSeatsDefaultLayout = new HashMap<>();
        for (ExamRoom examRoom : examRoomsForExam) {
            roomNumberToUsableSeatsDefaultLayout.put(examRoom.getRoomNumber(), examRoomService.getDefaultUsableSeats(examRoom));
        }

        final var examUsersIterator = examUsers.iterator();
        roomNumberToUsableSeatsDefaultLayout.forEach((roomNumber, usableSeats) -> usableSeats.stream().takeWhile(ignored -> examUsersIterator.hasNext()).forEach(seat -> {
            ExamUser nextExamUser = examUsersIterator.next();
            nextExamUser.setPlannedRoom(roomNumber);
            nextExamUser.setPlannedSeat(seat.name());
            exam.addExamUser(nextExamUser);
        }));

        examUserRepository.saveAll(examUsers);
        examRepository.save(exam);
    }
}
