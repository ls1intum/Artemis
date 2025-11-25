package de.tum.cit.aet.artemis.exam.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotEmpty;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoomExamAssignment;
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategy;
import de.tum.cit.aet.artemis.exam.dto.room.AttendanceCheckerAppExamInformationDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamDistributionCapacityDTO;
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
     * Calculates the usable seats of all rooms, both for the default, and the largest layouts, while respecting the reserve factor
     *
     * @param examRoomIds   The ids of the rooms to distribute to
     * @param reserveFactor Percentage of seats that should not be included
     * @return Information regarding the combined default and maximum room capacities
     */
    public ExamDistributionCapacityDTO getDistributionCapacitiesByIds(@NonNull Set<Long> examRoomIds, double reserveFactor) {
        final Set<ExamRoom> examRoomsForExam = examRoomRepository.findAllWithEagerLayoutStrategiesByIdIn(examRoomIds);

        return getDistributionCapacities(examRoomsForExam, reserveFactor);
    }

    private ExamDistributionCapacityDTO getDistributionCapacities(@NonNull Set<ExamRoom> examRooms, double reserveFactor) {
        final int numberOfDefaultUsableSeats = examRooms.stream()
                .mapToInt(examRoom -> examRoomService.getSizeAfterApplyingReserveFactor(examRoomService.getDefaultLayoutStrategyOrElseThrow(examRoom).getCapacity(), reserveFactor))
                .sum();

        final int numberOfMaximumUsableSeats = examRooms.stream().map(examRoom -> examRoom.getLayoutStrategies().stream().max(Comparator.comparingInt(LayoutStrategy::getCapacity)))
                .mapToInt(layoutStrategy -> layoutStrategy.map(strategy -> examRoomService.getSizeAfterApplyingReserveFactor(strategy.getCapacity(), reserveFactor)).orElse(0))
                .sum();

        return new ExamDistributionCapacityDTO(numberOfDefaultUsableSeats, numberOfMaximumUsableSeats);
    }

    /**
     * Distribute all students who are registered for a given exam across a selection of rooms.
     * Existing planned seats and room assignments are replaced.
     *
     * @param examId                The exam
     * @param examRoomIds           The ids of the rooms to distribute to
     * @param useOnlyDefaultLayouts if we want to only use 'default' layouts
     * @param reserveFactor         Percentage of seats that should not be included
     * @throws BadRequestAlertException if the capacity doesn't suffice to seat the students
     */
    public void distributeRegisteredStudents(long examId, @NotEmpty List<Long> examRoomIds, boolean useOnlyDefaultLayouts, double reserveFactor) {
        final Exam exam = examRepository.findByIdWithExamUsersElseThrow(examId);
        final Set<ExamRoom> examRoomsForExam = examRoomRepository.findAllWithEagerLayoutStrategiesByIdIn(Set.copyOf(examRoomIds));

        ExamDistributionCapacityDTO capacities = getDistributionCapacities(examRoomsForExam, reserveFactor);
        int numberOfExamUsers = exam.getExamUsers().size();

        boolean defaultLayoutsSuffice = capacities.combinedDefaultCapacity() >= numberOfExamUsers;
        if (!defaultLayoutsSuffice && useOnlyDefaultLayouts) {
            throw new BadRequestAlertException("Not enough seats available in the selected rooms", ENTITY_NAME, "notEnoughExamSeats",
                    Map.of("numberOfUsableSeats", capacities.combinedDefaultCapacity(), "numberOfExamUsers", numberOfExamUsers));
        }

        boolean maxLayoutsSuffice = capacities.combinedMaximumCapacity() >= numberOfExamUsers;
        if (!maxLayoutsSuffice) {
            throw new BadRequestAlertException("Not enough seats available in the selected rooms", ENTITY_NAME, "notEnoughExamSeats",
                    Map.of("numberOfUsableSeats", capacities.combinedMaximumCapacity(), "numberOfExamUsers", numberOfExamUsers));
        }

        assignExamRoomsToExam(exam, examRoomsForExam);

        Map<Long, ExamRoom> roomById = examRoomsForExam.stream().collect(Collectors.toMap(ExamRoom::getId, Function.identity()));

        List<ExamRoom> orderedExamRooms = examRoomIds.stream().map(roomById::get).toList();

        if (defaultLayoutsSuffice) {
            distributeExamUsersToDefaultUsableSeatsInRooms(exam, orderedExamRooms, reserveFactor);
        }
        else {
            distributeExamUsersToAnyUsableSeatsInRooms(exam, orderedExamRooms, reserveFactor);
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
     * @param reserveFactor    Percentage of seats that should not be included
     */
    private void distributeExamUsersToDefaultUsableSeatsInRooms(Exam exam, List<ExamRoom> examRoomsForExam, double reserveFactor) {
        SequencedMap<String, List<ExamSeatDTO>> roomNumberToUsableSeatsDefaultLayout = new LinkedHashMap<>();
        for (ExamRoom examRoom : examRoomsForExam) {
            roomNumberToUsableSeatsDefaultLayout.put(examRoom.getRoomNumber(), examRoomService.getDefaultUsableSeats(examRoom, reserveFactor));
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
     * @param reserveFactor    Percentage of seats that should not be included
     */
    private void distributeExamUsersToAnyUsableSeatsInRooms(Exam exam, List<ExamRoom> examRoomsForExam, double reserveFactor) {
        final int numberOfExamUsers = exam.getExamUsers().size();

        Map<Long, LayoutStrategy> layoutStrategyToBeUsedByRoomId = getBestLayoutPerRoomCombination(Set.copyOf(examRoomsForExam), numberOfExamUsers, reserveFactor);

        SequencedMap<String, List<ExamSeatDTO>> roomNumberToUsableSeats = new LinkedHashMap<>();
        for (ExamRoom examRoom : examRoomsForExam) {
            LayoutStrategy layoutStrategyForThisRoom = layoutStrategyToBeUsedByRoomId.get(examRoom.getId());
            List<ExamSeatDTO> seatsForThisStrategy = examRoomService.getUsableSeatsForLayout(examRoom, layoutStrategyForThisRoom, reserveFactor);

            roomNumberToUsableSeats.put(examRoom.getRoomNumber(), seatsForThisStrategy);
        }

        setPlannedRoomAndPlannedSeatForExamUsersRandomly(exam, roomNumberToUsableSeats);
    }

    private Map<Long, LayoutStrategy> getBestLayoutPerRoomCombination(Set<ExamRoom> examRoomsForExam, int numberOfExamUsers, double reserveFactor) {
        Map<Long, List<LayoutStrategy>> layoutStrategiesByRoomId = getLayoutStrategiesAtLeastDefaultSizeByExamRoomId(examRoomsForExam);
        return getBestLayoutByRoomIdPermutation(layoutStrategiesByRoomId, numberOfExamUsers, reserveFactor);
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

    private Map<Long, LayoutStrategy> getBestLayoutByRoomIdPermutation(Map<Long, List<LayoutStrategy>> layoutsByRoom, int requiredCapacity, double reserveFactor) {
        List<Long> roomIds = new ArrayList<>(layoutsByRoom.keySet());

        return getBestLayoutPermutationRecursive(layoutsByRoom, roomIds, 0, new HashMap<>(), 0, requiredCapacity, reserveFactor, null, Integer.MAX_VALUE);
    }

    private Map<Long, LayoutStrategy> getBestLayoutPermutationRecursive(Map<Long, List<LayoutStrategy>> layoutsByRoom, List<Long> roomIds, int roomIndex,
            Map<Long, LayoutStrategy> current, int currentCapacity, int requiredCapacity, double reserveFactor, Map<Long, LayoutStrategy> best, int bestCapacity) {

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
            int newCapacity = currentCapacity + examRoomService.getSizeAfterApplyingReserveFactor(strategy.getCapacity(), reserveFactor);

            // pruning: if capacity already >= bestCapacity, no need to continue
            if (newCapacity < bestCapacity) {
                best = getBestLayoutPermutationRecursive(layoutsByRoom, roomIds, roomIndex + 1, current, newCapacity, requiredCapacity, reserveFactor, best, bestCapacity);

                // update bestCapacity if best changed
                if (best != null) {
                    bestCapacity = best.values().stream().mapToInt(layoutStrategy -> examRoomService.getSizeAfterApplyingReserveFactor(layoutStrategy.getCapacity(), reserveFactor))
                            .sum();
                }
            }

            current.remove(roomId); // backtrack
        }

        return best;
    }

    private void setPlannedRoomAndPlannedSeatForExamUsersRandomly(Exam exam, SequencedMap<String, List<ExamSeatDTO>> roomNumberToUsableSeats) {
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

    /**
     * Generates information relevant for displaying rooms and students in the attendance checker app
     *
     * @param examId The exam id
     * @return the generated information
     */
    public AttendanceCheckerAppExamInformationDTO getAttendanceCheckerAppInformation(long examId) {
        Exam exam = examRepository.findByIdWithExamUsersElseThrow(examId);
        Set<ExamUser> examUsers = exam.getExamUsers();

        if (examUsers.stream().noneMatch(examUser -> StringUtils.hasText(examUser.getPlannedRoom()) && StringUtils.hasText(examUser.getPlannedSeat()))) {
            throw new BadRequestAlertException("No distribution has happened, yet", ENTITY_NAME, "noStudentDistributed");
        }

        examUserService.setPlannedRoomAndSeatTransientForExamUsers(examUsers);
        examUserService.setActualRoomAndSeatTransientForExamUsers(examUsers);

        Set<ExamRoom> examRooms = examRoomRepository.findAllByExamId(examId);

        return AttendanceCheckerAppExamInformationDTO.from(exam, examRooms);
    }
}
