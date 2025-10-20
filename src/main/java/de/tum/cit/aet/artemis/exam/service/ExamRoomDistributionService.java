package de.tum.cit.aet.artemis.exam.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.validation.constraints.NotEmpty;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoomExamAssignment;
import de.tum.cit.aet.artemis.exam.dto.room.AttendanceCheckerAppExamInformationDTO;
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

    private static final String ENTITY_NAME = "examRoomDistributionService";

    private final ExamRepository examRepository;

    private final ExamRoomRepository examRoomRepository;

    private final ExamRoomService examRoomService;

    private final ExamRoomExamAssignmentRepository examRoomExamAssignmentRepository;

    private final ExamUserRepository examUserRepository;

    private final ExamUserService examUserService;

    public ExamRoomDistributionService(ExamRepository examRepository, ExamRoomRepository examRoomRepository, ExamRoomService examRoomService,
            ExamRoomExamAssignmentRepository examRoomExamAssignmentRepository, ExamUserRepository examUserRepository, ExamUserService examUserService) {
        this.examRepository = examRepository;
        this.examRoomRepository = examRoomRepository;
        this.examRoomService = examRoomService;
        this.examRoomExamAssignmentRepository = examRoomExamAssignmentRepository;
        this.examUserRepository = examUserRepository;
        this.examUserService = examUserService;
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
        final Set<ExamRoom> examRoomsForExam = examRoomRepository.findAllWithEagerLayoutStrategiesByIdIn(examRoomIds);

        final int numberOfUsableSeats = examRoomsForExam.stream().mapToInt(examRoom -> examRoomService.getDefaultLayoutStrategyOrElseThrow(examRoom).getCapacity()).sum();
        final int numberOfExamUsers = exam.getExamUsers().size();

        if (numberOfUsableSeats < numberOfExamUsers) {
            throw new BadRequestAlertException("Not enough seats available in the selected rooms", ENTITY_NAME, "notEnoughExamSeats",
                    Map.of("numberOfUsableSeats", numberOfUsableSeats, "numberOfExamUsers", numberOfExamUsers));
        }

        examRoomExamAssignmentRepository.deleteAllByExamId(examId);
        List<ExamRoomExamAssignment> examRoomExamAssignments = new ArrayList<>();
        for (ExamRoom examRoom : examRoomsForExam) {
            var examRoomExamAssignment = new ExamRoomExamAssignment();
            examRoomExamAssignment.setExamRoom(examRoom);
            examRoomExamAssignment.setExam(exam);
            examRoomExamAssignments.add(examRoomExamAssignment);
        }
        examRoomExamAssignmentRepository.saveAll(examRoomExamAssignments);

        distributeExamUsersToUsableSeatsInRooms(exam, examRoomsForExam);
    }

    private void distributeExamUsersToUsableSeatsInRooms(Exam exam, Set<ExamRoom> examRoomsForExam) {
        Map<String, List<ExamSeatDTO>> roomNumberToUsableSeatsDefaultLayout = new HashMap<>();
        for (ExamRoom examRoom : examRoomsForExam) {
            roomNumberToUsableSeatsDefaultLayout.put(examRoom.getRoomNumber(), examRoomService.getDefaultUsableSeats(examRoom));
        }

        setPlannedRoomAndPlannedSeatForExamUsersRandomly(exam, roomNumberToUsableSeatsDefaultLayout);

        examUserRepository.saveAll(exam.getExamUsers());
        examRepository.save(exam);
    }

    private void setPlannedRoomAndPlannedSeatForExamUsersRandomly(Exam exam, Map<String, List<ExamSeatDTO>> roomNumberToUsableSeats) {
        Iterator<ExamUser> examUsersIterator = exam.getExamUsers().iterator();

        for (var roomNumberToUsableSeatsEntry : roomNumberToUsableSeats.entrySet()) {
            final String roomNumber = roomNumberToUsableSeatsEntry.getKey();
            final List<ExamSeatDTO> usableSeatsForThisRoom = roomNumberToUsableSeatsEntry.getValue();

            for (ExamSeatDTO seat : usableSeatsForThisRoom) {
                if (!examUsersIterator.hasNext()) {
                    return;
                }

                ExamUser nextExamUser = examUsersIterator.next();
                nextExamUser.setPlannedRoom(roomNumber);
                nextExamUser.setPlannedSeat(seat.name());
                exam.addExamUser(nextExamUser);
            }
        }
    }

    /**
     * Generates information relevant for displaying rooms and students in the attendance checker app
     *
     * @param examId The exam id
     * @return the generated information
     */
    public AttendanceCheckerAppExamInformationDTO getAttendanceCheckerAppInformation(long examId) {
        Exam exam = examRepository.findByIdWithExamUsersElseThrow(examId);

        Set<ExamUser> examUsers = exam.getExamUsers();
        examUserService.setPlannedRoomAndSeatTransientForExamUsers(examUsers);
        examUserService.setActualRoomAndSeatTransientForExamUsers(examUsers);

        Set<ExamRoom> examRooms = examRoomRepository.findAllByExamId(examId);

        return AttendanceCheckerAppExamInformationDTO.from(exam, examRooms);
    }
}
