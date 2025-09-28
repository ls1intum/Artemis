package de.tum.cit.aet.artemis.exam.service;

import java.util.ArrayList;
import java.util.Comparator;
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
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategy;
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
    public void distributeRegisteredStudents(long examId, @NotEmpty Set<Long> examRoomIds, boolean useOnlyDefaultLayouts) {
        final Exam exam = examRepository.findByIdWithExamUsersElseThrow(examId);
        final Set<ExamRoom> examRoomsForExam = examRoomRepository.findAllWithEagerLayoutStrategiesByIdIn(examRoomIds);

        final int numberOfDefaultUsableSeats = examRoomsForExam.stream().mapToInt(examRoom -> examRoomService.getDefaultLayoutStrategyOrElseThrow(examRoom).getCapacity()).sum();
        final int numberOfExamUsers = exam.getExamUsers().size();
        boolean defaultLayoutsSuffice = true;

        if (numberOfDefaultUsableSeats < numberOfExamUsers) {
            if (useOnlyDefaultLayouts) {
                throw new BadRequestAlertException("Not enough seats available in the selected rooms", ENTITY_NAME, "notEnoughExamSeats",
                        Map.of("numberOfUsableSeats", numberOfDefaultUsableSeats, "numberOfExamUsers", numberOfExamUsers));
            }

            defaultLayoutsSuffice = false;

            final int numberOfMaximumUsableSeats = examRoomsForExam.stream()
                    .map(examRoom -> examRoom.getLayoutStrategies().stream().max(Comparator.comparingInt(LayoutStrategy::getCapacity)))
                    .mapToInt(layoutStrategy -> layoutStrategy.isPresent() ? layoutStrategy.get().getCapacity() : 0).sum();

            if (numberOfMaximumUsableSeats < numberOfExamUsers) {
                throw new BadRequestAlertException("Not enough seats available in the selected rooms", ENTITY_NAME, "notEnoughExamSeats",
                        Map.of("numberOfUsableSeats", numberOfMaximumUsableSeats, "numberOfExamUsers", numberOfExamUsers));
            }
        }

        assignExamRoomsToExam(exam, examRoomsForExam);
        if (defaultLayoutsSuffice) {
            distributeExamUsersToDefaultUsableSeatsInRooms(exam, examRoomsForExam);
        }
        else {
            distributeExamUsersToAnyUsableSeatsInRooms(exam, examRoomsForExam);
        }
    }

    private void assignExamRoomsToExam(Exam exam, Set<ExamRoom> examRoomsForExam) {
        examRoomExamAssignmentRepository.deleteAllByExamId(exam.getId());
        List<ExamRoomExamAssignment> examRoomExamAssignments = new ArrayList<>();
        for (ExamRoom examRoom : examRoomsForExam) {
            var examRoomExamAssignment = new ExamRoomExamAssignment();
            examRoomExamAssignment.setExamRoom(examRoom);
            examRoomExamAssignment.setExam(exam);
            examRoomExamAssignments.add(examRoomExamAssignment);
        }
        examRoomExamAssignmentRepository.saveAll(examRoomExamAssignments);
    }

    private void distributeExamUsersToDefaultUsableSeatsInRooms(Exam exam, Set<ExamRoom> examRoomsForExam) {
        Map<String, List<ExamSeatDTO>> roomNumberToUsableSeatsDefaultLayout = new HashMap<>();
        for (ExamRoom examRoom : examRoomsForExam) {
            roomNumberToUsableSeatsDefaultLayout.put(examRoom.getRoomNumber(), examRoomService.getDefaultUsableSeats(examRoom));
        }

        setPlannedRoomAndPlannedSeatForExamUsersRandomly(exam, roomNumberToUsableSeatsDefaultLayout);
    }

    /**
     * This function assumes that the default layout capacity of each room doesn't suffice, and attempts to find a
     * selection of layouts that would allow to seat all students and also minimizes the wasted space.
     * <p>
     * This function assumes that there is at least one valid layout combination, so that all students fit.
     *
     * @param exam             the exam
     * @param examRoomsForExam all exam rooms that students of this exam can be distributed to
     */
    private void distributeExamUsersToAnyUsableSeatsInRooms(Exam exam, Set<ExamRoom> examRoomsForExam) {
        final int numberOfExamUsers = exam.getExamUsers().size();

        Map<Long, List<LayoutStrategy>> layoutStrategiesByRoomId = getLayoutStrategiesAtLeastDefaultSizeByExamRoomId(examRoomsForExam);
        List<Map<Long, LayoutStrategy>> layoutPermutations = getAllLayoutPermutations(layoutStrategiesByRoomId);
        Map<Long, LayoutStrategy> layoutStrategyToBeUsedByRoomId = findBestPermutation(layoutPermutations, numberOfExamUsers);

        Map<String, List<ExamSeatDTO>> roomNumberToUsableSeats = new HashMap<>();
        for (ExamRoom examRoom : examRoomsForExam) {
            LayoutStrategy layoutStrategyForThisRoom = layoutStrategyToBeUsedByRoomId.get(examRoom.getId());
            List<ExamSeatDTO> seatsForThisStrategy = examRoomService.getUsableSeatsForLayout(examRoom, layoutStrategyForThisRoom);

            roomNumberToUsableSeats.put(examRoom.getRoomNumber(), seatsForThisStrategy);
        }

        setPlannedRoomAndPlannedSeatForExamUsersRandomly(exam, roomNumberToUsableSeats);
    }

    private Map<Long, List<LayoutStrategy>> getLayoutStrategiesAtLeastDefaultSizeByExamRoomId(Set<ExamRoom> examRoomsForExam) {
        Map<Long, List<LayoutStrategy>> layoutStrategiesAtLeastDefaultSizeByExamRoom = new HashMap<>();
        for (ExamRoom examRoom : examRoomsForExam) {
            final LayoutStrategy defaultStrategy = examRoomService.getDefaultLayoutStrategyOrElseThrow(examRoom);

            for (LayoutStrategy layoutStrategy : examRoom.getLayoutStrategies()) {
                if (layoutStrategy.getCapacity() < defaultStrategy.getCapacity()) {
                    continue;
                }

                layoutStrategiesAtLeastDefaultSizeByExamRoom.putIfAbsent(examRoom.getId(), new ArrayList<>());
                layoutStrategiesAtLeastDefaultSizeByExamRoom.get(examRoom.getId()).add(layoutStrategy);
            }
        }

        return layoutStrategiesAtLeastDefaultSizeByExamRoom;
    }

    private List<Map<Long, LayoutStrategy>> getAllLayoutPermutations(Map<Long, List<LayoutStrategy>> layoutsByRoom) {
        List<Map<Long, LayoutStrategy>> permutations = new ArrayList<>();
        permutations.add(new HashMap<>()); // start with an empty selection

        for (var entry : layoutsByRoom.entrySet()) {
            final long roomId = entry.getKey();
            final List<LayoutStrategy> strategies = entry.getValue();

            List<Map<Long, LayoutStrategy>> newPermutations = new ArrayList<>();

            for (Map<Long, LayoutStrategy> permutation : permutations) {
                for (LayoutStrategy strategy : strategies) {
                    Map<Long, LayoutStrategy> extendedPermutation = new HashMap<>(permutation);
                    extendedPermutation.put(roomId, strategy);
                    newPermutations.add(extendedPermutation);
                }
            }

            permutations = newPermutations; // replace with the up to this room extended list
        }

        return permutations;
    }

    private Map<Long, LayoutStrategy> findBestPermutation(List<Map<Long, LayoutStrategy>> layoutPermutations, int requiredCapacity) {
        return layoutPermutations.stream().min(Comparator.comparingInt(permutation -> {
            int total = permutation.values().stream().mapToInt(LayoutStrategy::getCapacity).sum();

            return total < requiredCapacity ? Integer.MAX_VALUE : total;
        })).orElseThrow(() -> new IllegalStateException("Couldn't find a best permutation. This should never be possible"));
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

        examUserRepository.saveAll(exam.getExamUsers());
        examRepository.save(exam);
    }
}
