package de.tum.cit.aet.artemis.exam.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
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
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategy;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomForDistributionDTO;
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

    private static final Logger log = LoggerFactory.getLogger(ExamRoomDistributionService.class);

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
     * @param examId                The exam
     * @param examRoomIds           The ids of the rooms to distribute to
     * @param useOnlyDefaultLayouts if we want to only use 'default' layouts
     * @throws BadRequestAlertException if the capacity doesn't suffice to seat the students
     */
    public void distributeRegisteredStudents(long examId, @NotEmpty Set<Long> examRoomIds, boolean useOnlyDefaultLayouts, double reserveFactor) {
        final Exam exam = examRepository.findByIdWithExamUsersElseThrow(examId);
        final Set<ExamRoom> examRoomsForExam = examRoomRepository.findAllWithEagerLayoutStrategiesByIdIn(examRoomIds);

        final int numberOfDefaultUsableSeats = examRoomsForExam.stream()
                .mapToInt(examRoom -> (int) (examRoomService.getDefaultLayoutStrategyOrElseThrow(examRoom).getCapacity() * (1 - reserveFactor))).sum();
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

    /**
     * Sets up the DB connections between the given exam and the given exam rooms.
     * Existing connections are removed first.
     *
     * @param exam             the exam
     * @param examRoomsForExam all exam rooms that students of this exam can be distributed to
     */
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

    /**
     * This function assumes that the default layout capacity of each room does suffice.
     *
     * @param exam             the exam
     * @param examRoomsForExam all exam rooms that students of this exam can be distributed to
     */
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

        Map<Long, LayoutStrategy> layoutStrategyToBeUsedByRoomId = getBestLayoutPerRoomCombination(examRoomsForExam, numberOfExamUsers);

        Map<String, List<ExamSeatDTO>> roomNumberToUsableSeats = new HashMap<>();
        for (ExamRoom examRoom : examRoomsForExam) {
            LayoutStrategy layoutStrategyForThisRoom = layoutStrategyToBeUsedByRoomId.get(examRoom.getId());
            List<ExamSeatDTO> seatsForThisStrategy = examRoomService.getUsableSeatsForLayout(examRoom, layoutStrategyForThisRoom);

            roomNumberToUsableSeats.put(examRoom.getRoomNumber(), seatsForThisStrategy);
        }

        setPlannedRoomAndPlannedSeatForExamUsersRandomly(exam, roomNumberToUsableSeats);
    }

    private Map<Long, LayoutStrategy> getBestLayoutPerRoomCombination(Set<ExamRoom> examRoomsForExam, int numberOfExamUsers) {
        Map<Long, List<LayoutStrategy>> layoutStrategiesByRoomId = getLayoutStrategiesAtLeastDefaultSizeByExamRoomId(examRoomsForExam);
        return getBestLayoutByRoomIdPermutation(layoutStrategiesByRoomId, numberOfExamUsers);
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

    private Map<Long, LayoutStrategy> getBestLayoutByRoomIdPermutation(Map<Long, List<LayoutStrategy>> layoutsByRoom, int requiredCapacity) {
        List<Long> roomIds = new ArrayList<>(layoutsByRoom.keySet());

        return getBestLayoutPermutationRecursive(layoutsByRoom, roomIds, 0, new HashMap<>(), 0, requiredCapacity, null, Integer.MAX_VALUE);
    }

    private Map<Long, LayoutStrategy> getBestLayoutPermutationRecursive(Map<Long, List<LayoutStrategy>> layoutsByRoom, List<Long> roomIds, int roomIndex,
            Map<Long, LayoutStrategy> current, int currentCapacity, int requiredCapacity, Map<Long, LayoutStrategy> best, int bestCapacity) {

        // If we've assigned all rooms, check if it's valid/better
        if (roomIndex == roomIds.size()) {
            if (currentCapacity >= requiredCapacity && currentCapacity < bestCapacity) {
                best = new HashMap<>(current); // copy since 'current' is mutable
            }

            return best;
        }

        long roomId = roomIds.get(roomIndex);
        for (LayoutStrategy strategy : layoutsByRoom.get(roomId)) {
            current.put(roomId, strategy);
            int newCapacity = currentCapacity + strategy.getCapacity();

            // pruning: if capacity already >= bestCapacity, no need to continue
            if (newCapacity < bestCapacity) {
                best = getBestLayoutPermutationRecursive(layoutsByRoom, roomIds, roomIndex + 1, current, newCapacity, requiredCapacity, best, bestCapacity);

                // update bestCapacity if best changed
                if (best != null) {
                    bestCapacity = best.values().stream().mapToInt(LayoutStrategy::getCapacity).sum();
                }
            }

            current.remove(roomId); // backtrack
        }

        return best;
    }

    private void setPlannedRoomAndPlannedSeatForExamUsersRandomly(Exam exam, Map<String, List<ExamSeatDTO>> roomNumberToUsableSeats) {
        Iterator<ExamUser> examUsersIterator = exam.getExamUsers().iterator();

        do_while_students: for (var roomNumberToUsableSeatsEntry : roomNumberToUsableSeats.entrySet()) {
            final String roomNumber = roomNumberToUsableSeatsEntry.getKey();
            final List<ExamSeatDTO> usableSeatsForThisRoom = roomNumberToUsableSeatsEntry.getValue();

            for (ExamSeatDTO seat : usableSeatsForThisRoom) {
                if (!examUsersIterator.hasNext()) {
                    break do_while_students;
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

    public Set<ExamRoomForDistributionDTO> getRoomDataForDistribution() {
        return examRoomRepository.findAllCurrentExamRoomsForDistribution();
    }
}
