package de.tum.cit.aet.artemis.exam.service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategy;
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategyType;
import de.tum.cit.aet.artemis.exam.domain.room.SeatCondition;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomAdminOverviewDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomLayoutStrategyDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomUploadInformationDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamSeatDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomAssignmentRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomRepository;
import de.tum.cit.aet.artemis.exam.repository.LayoutStrategyRepository;

/*
 * Service implementation for managing exam rooms.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamRoomService {

    private static final Logger log = LoggerFactory.getLogger(ExamRoomService.class);

    private static final String ENTITY_NAME = "examRoom";

    private final ExamRoomRepository examRoomRepository;

    private final LayoutStrategyRepository layoutStrategyRepository;

    private final ExamRoomAssignmentRepository examRoomAssignmentRepository;

    private final ObjectMapper objectMapper;

    public ExamRoomService(ExamRoomRepository examRoomRepository, LayoutStrategyRepository layoutStrategyRepository, ExamRoomAssignmentRepository examRoomAssignmentRepository,
            ObjectMapper objectMapper) {
        this.examRoomRepository = examRoomRepository;
        this.layoutStrategyRepository = layoutStrategyRepository;
        this.examRoomAssignmentRepository = examRoomAssignmentRepository;
        this.objectMapper = objectMapper;
    }

    /* Multiple records that will be used internally for Jackson deserialization */
    // @formatter:off
    private record ExamRoomInput(
        @JsonProperty("number") String alternativeNumber,
        @JsonProperty("name") String name,
        @JsonProperty("shortname") String alternativeName,
        @JsonProperty("building") String building,
        @JsonProperty("rows") List<RowInput> rows,
        @JsonProperty("layouts") Map<String, JsonNode> layouts
    ) {}

    private record RowInput(
        @JsonProperty("label") String label,
        @JsonProperty("seats") List<SeatInput> seats
    ) {}

    private record SeatInput(
        @JsonProperty("label") String label,
        @JsonProperty("flag") String condition,
        @JsonProperty("position") PositionInput position
    ) {}

    private record PositionInput(
        @JsonProperty("x") float x,
        @JsonProperty("y") float y
    ) {}
    // @formatter:on
    /* End of the Jackson records */

    /**
     * Looks through all JSON files contained in a given zip file (recursive search).
     * Then it adds all exam rooms it could parse to the database, ignoring duplicates of the same room.
     * The exam rooms' primary room numbers are the filenames of the JSON files containing their data.
     *
     * @param zipFile A zip file containing JSON files of exam room data.
     * @return A small DTO that can be returned to the client, containing some metrics about the parsing process.
     */
    public ExamRoomUploadInformationDTO parseAndStoreExamRoomDataFromZipFile(MultipartFile zipFile) {
        final long startTime = System.nanoTime();
        log.info("Starting to parse rooms from {}...", zipFile.getOriginalFilename());
        // We want to discard any duplicate rooms. Having duplicate rooms is only possible if you also nest the same
        // .json file in one or more subfolders. Doing this is either a (malicious) mistake, or perhaps a backup file,
        // and will thus be ignored. In this equality we only consider the room number, the name, and the building,
        // as it would be a mistake to store the same room twice and risk a potential creation date collision later on.
        // All 3 fields are explicitly not nullable.
        Set<ExamRoom> examRooms = new TreeSet<>(Comparator.comparing(ExamRoom::getRoomNumber).thenComparing(ExamRoom::getName).thenComparing(ExamRoom::getBuilding));

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // validate file type
                if (entry.isDirectory() || !entryName.endsWith(".json")) {
                    continue;
                }

                // extract the filename - remove the folder path (if existent) and remove the trailing '.json'
                // Math.max(0, entryName.lastIndexOf('/') + 1); === entryName.lastIndexOf('/') + 1;
                int roomNumberStartIndex = entryName.lastIndexOf('/') + 1;
                int roomNumberEndIndex = entryName.lastIndexOf(".json");
                String roomNumber = entryName.substring(roomNumberStartIndex, roomNumberEndIndex);

                ExamRoomInput examRoomInput = objectMapper.readValue(zis.readAllBytes(), ExamRoomInput.class);

                var examRoom = convertRoomNumberAndExamRoomInputToExamRoom(roomNumber, examRoomInput);
                if (examRoom == null)
                    continue;

                examRooms.add(examRoom);
            }

        }
        catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Internal error while trying to parse the rooms");
        }
        log.info("Parsed rooms in {}", TimeLogUtil.formatDurationFrom(startTime));

        // save exam rooms, exam seats, and exam room layouts in the DB
        long startTimeDB = System.nanoTime();
        examRoomRepository.saveAll(examRooms);
        log.info("Saved rooms in {}", TimeLogUtil.formatDurationFrom(startTimeDB));

        // Build ExamRoomUploadInformationDTO
        log.debug("Constructing exam room upload information");
        long startTimeDTO = System.nanoTime();
        ExamRoomUploadInformationDTO result = getExamRoomUploadInformationDTO(zipFile, startTime, examRooms);
        log.info("Constructed exam room upload information in {}", TimeLogUtil.formatDurationFrom(startTimeDTO));

        return result;
    }

    /**
     * Converts {@link ExamRoomInput}, which is automatically deserialized from Jackson,
     * and together with {@code roomNumber} generate an {@link ExamRoom}.
     *
     * @param roomNumber    The roomNumber of the exam room we want to create.
     * @param examRoomInput The Jackson parsed exam room input
     * @return The ExamRoom as stored in the JSON room data.
     */
    private static ExamRoom convertRoomNumberAndExamRoomInputToExamRoom(final String roomNumber, final ExamRoomInput examRoomInput) {
        if (examRoomInput == null) {
            return null;
        }

        /* Extract simple exam room fields */
        ExamRoom room = new ExamRoom();
        room.setRoomNumber(roomNumber);
        final String alternativeRoomNumber = examRoomInput.alternativeNumber;
        if (!roomNumber.equals(alternativeRoomNumber)) {
            room.setAlternativeRoomNumber(alternativeRoomNumber);
        }

        room.setName(examRoomInput.name);
        final String alternativeName = examRoomInput.alternativeName;
        if (!room.getName().equals(alternativeName)) {
            room.setAlternativeName(alternativeName);
        }

        room.setBuilding(examRoomInput.building);
        /* Extract simple exam room fields - End */

        /* Extract the seats */
        // It is imperative that the seats are parsed before the layout strategies are parsed, as the size calculation
        // for relative layouts will only be done if the rooms have already been parsed
        List<ExamSeatDTO> seats = convertRowInputsToExamSeatDTOs(examRoomInput.rows);
        if (seats == null) {
            throw new BadRequestAlertException("Couldn't parse room" + room.getRoomNumber() + "because the seats couldn't be converted", ENTITY_NAME, "cannotConvertSeats");
        }
        room.setSeats(seats);

        /* Extract the layouts */
        List<LayoutStrategy> layouts = convertLayoutInputsToLayoutStrategies(examRoomInput.layouts, room);
        room.setLayoutStrategies(layouts);

        return room;
    }

    /**
     * Converts {@link RowInput}s into {@link ExamSeatDTO}s
     *
     * @param rows The list of Jackson parsed row inputs
     * @return The list of all exam seats it could convert from the {@code rows}
     */
    private static List<ExamSeatDTO> convertRowInputsToExamSeatDTOs(List<RowInput> rows) {
        if (rows == null) {
            return null;
        }

        List<ExamSeatDTO> seats = new ArrayList<>();

        for (RowInput rowInput : rows) {
            if (rowInput == null) {
                return null;
            }

            final String rowLabel = rowInput.label == null ? "" : rowInput.label;
            for (SeatInput seatInput : rowInput.seats) {
                if (seatInput == null || seatInput.position == null) {
                    return null;
                }

                final String seatLabel = seatInput.label == null ? "" : seatInput.label;
                final String seatName = rowLabel.isEmpty() ? seatLabel : (rowLabel + ", " + seatLabel);

                ExamSeatDTO seat = new ExamSeatDTO(seatName, SeatCondition.seatConditionFromFlag(seatInput.condition), seatInput.position.x, seatInput.position.y);

                seats.add(seat);
            }
        }

        return seats;
    }

    /**
     * Converts {@code layoutNamesToLayoutNode} to {@link List<LayoutStrategy>},
     * and associates each {@link LayoutStrategy} with the {@code room}.
     * <p/>
     * The content of the JSON nodes depends on the layout type.
     *
     * @param layoutNamesToLayoutNode Mapping of layout names to layout JSON nodes
     * @param room                    The exam room for which the parsed layout strategies are.
     * @return A list of all layout strategies this room has to offer.
     */
    private static List<LayoutStrategy> convertLayoutInputsToLayoutStrategies(Map<String, JsonNode> layoutNamesToLayoutNode, ExamRoom room) {
        List<LayoutStrategy> layouts = new ArrayList<>();

        // Iterate over all possible room layout names, e.g., "default" or "wide"
        layoutNamesToLayoutNode.forEach((layoutName, layoutNode) -> {
            // We assume there's only a single layout type, e.g., "auto_layout" or "usable_seats"
            final String layoutType = layoutNode.fieldNames().next();
            final JsonNode layoutDetailNode = layoutNode.path(layoutType);

            LayoutStrategy layoutStrategy = new LayoutStrategy();
            layoutStrategy.setName(layoutName);
            layoutStrategy.setExamRoom(room);
            switch (layoutType) {
                case "auto_layout" -> layoutStrategy.setType(LayoutStrategyType.RELATIVE_DISTANCE);
                // useable_seats is a common typo in the JSON files
                case "usable_seats", "useable_seats" -> layoutStrategy.setType(LayoutStrategyType.FIXED_SELECTION);
                default -> throw new BadRequestAlertException("Couldn't parse room" + room.getRoomNumber() + "because the layouts couldn't be converted", ENTITY_NAME,
                        "cannotConvertLayouts");
            }
            layoutStrategy.setParametersJson(String.valueOf(layoutDetailNode));

            // pre-calculate the capacity
            switch (layoutStrategy.getType()) {
                case LayoutStrategyType.FIXED_SELECTION -> {
                    if (!layoutDetailNode.isArray()) {
                        throw new BadRequestAlertException("Couldn't parse room" + room.getRoomNumber() + "because the layouts couldn't be converted", ENTITY_NAME,
                                "cannotConvertLayouts");
                    }

                    layoutStrategy.setCapacity(layoutDetailNode.size());
                }
                case LayoutStrategyType.RELATIVE_DISTANCE -> {
                    if (!layoutDetailNode.isObject()) {
                        throw new BadRequestAlertException("Couldn't parse room" + room.getRoomNumber() + "because the layouts couldn't be converted", ENTITY_NAME,
                                "cannotConvertLayouts");
                    }

                    calculateSeatsFromRelativeDistanceLayout(layoutDetailNode, room).ifPresent(layoutStrategy::setCapacity);
                }
            }

            layouts.add(layoutStrategy);
        });

        return layouts;
    }

    /**
     * Calculates how many seats can be used for a given {@link LayoutStrategyType#RELATIVE_DISTANCE} layout strategy.
     * <p/>
     * The layout we expect is:
     * <ul>
     * <li>"first_row": Integer</li>
     * <li>"xspace": float</li>
     * <li>"yspace": float</li>
     * </ul>
     * <p/>
     * However, any or all of those can be omitted. If omitted, they will default to 0, which means no restrictions.
     *
     * @param layoutDetailNode The JSON node containing the expected relative layout information
     * @return The number of exam seats that can be used with the given layout, or an empty optional if it couldn't
     *         determine the size.
     */
    private static Optional<Integer> calculateSeatsFromRelativeDistanceLayout(JsonNode layoutDetailNode, ExamRoom examRoom) {
        // If we don't have exam seat information, we can't calculate the size
        if (examRoom.getSeats().isEmpty()) {
            return Optional.empty();
        }

        // first_row in some rooms is -1. I assume this means the same as 0
        final int firstRow = layoutDetailNode.path("first_row").asInt(0);
        final double xSpace = layoutDetailNode.path("xspace").asDouble(0);
        final double ySpace = layoutDetailNode.path("yspace").asDouble(0);

        List<ExamSeatDTO> examSeatsFilteredAndSorted = examRoom.getSeats().stream()
                // Filter out all exam rooms that are before the "first row". The coords start at 0, the row numbers at 1
                .filter(examSeatDTO -> examSeatDTO.yCoordinate() >= (firstRow - 1))
                // Filter out all exam rooms that are not default usable
                .filter(examSeatDTO -> examSeatDTO.seatCondition() == SeatCondition.USABLE)
                // Sort by X, then by Y to ensure stable processing later
                .sorted(Comparator.comparingDouble(ExamSeatDTO::yCoordinate).thenComparingDouble(ExamSeatDTO::xCoordinate)).toList();

        List<ExamSeatDTO> selectedSeats = new ArrayList<>();
        for (ExamSeatDTO examSeatDTO : examSeatsFilteredAndSorted) {
            boolean isFarEnough = selectedSeats.stream().noneMatch(
                    existing -> Math.abs(existing.yCoordinate() - examSeatDTO.yCoordinate()) <= ySpace && Math.abs(existing.xCoordinate() - examSeatDTO.xCoordinate()) <= xSpace);
            if (isFarEnough) {
                selectedSeats.add(examSeatDTO);
            }
        }

        return Optional.of(selectedSeats.size());
    }

    private static ExamRoomUploadInformationDTO getExamRoomUploadInformationDTO(MultipartFile zipFile, long startTime, Set<ExamRoom> examRooms) {
        long startTimeOfDTO = System.nanoTime();

        String uploadedFileName = zipFile.getOriginalFilename();
        PeriodFormatter formatter = new PeriodFormatterBuilder().appendDays().appendSuffix("d ").appendHours().appendSuffix("h ").appendMinutes().appendSuffix("m ").appendSeconds()
                .appendSuffix("s ").appendMillis().appendSuffix("ms").toFormatter();

        String uploadDuration = formatter.print(Period.millis((int) Duration.ofNanos(startTimeOfDTO - startTime).toMillis()).normalizedStandard());
        Integer numberOfUploadedRooms = examRooms.size();
        Integer numberOfUploadedSeats = examRooms.stream().mapToInt(room -> room.getSeats().size()).sum();
        List<String> roomNames = examRooms.stream().map(ExamRoom::getName).toList();

        return new ExamRoomUploadInformationDTO(uploadedFileName, uploadDuration, numberOfUploadedRooms, numberOfUploadedSeats, roomNames);
    }

    /**
     * Calculates information about the current state of the exam rooms in the database.
     *
     * @return A DTO that can be sent to the client, containing basic information about the state of the exam room DB.
     */
    public ExamRoomAdminOverviewDTO getExamRoomAdminOverviewDTO() {
        final List<ExamRoom> examRooms = examRoomRepository.findAllExamRoomsWithEagerLayoutStrategies();

        final Integer numberOfStoredExamRooms = examRooms.size();
        final Integer numberOfStoredExamSeats = examRooms.stream().mapToInt(er -> er.getSeats().size()).sum();
        final Integer numberOfStoredLayoutStrategies = examRooms.stream().mapToInt(er -> er.getLayoutStrategies().size()).sum();

        final Set<ExamRoomDTO> examRoomDTOS = examRooms.stream()
                .map(examRoom -> new ExamRoomDTO(examRoom.getRoomNumber(), examRoom.getName(), examRoom.getBuilding(), examRoom.getSeats().size(),
                        examRoom.getLayoutStrategies().stream().map(ls -> new ExamRoomLayoutStrategyDTO(ls.getName(), ls.getType(), ls.getCapacity())).collect(Collectors.toSet())))
                .collect(Collectors.toSet());

        return new ExamRoomAdminOverviewDTO(numberOfStoredExamRooms, numberOfStoredExamSeats, numberOfStoredLayoutStrategies, examRoomDTOS);
    }

    /**
     * Purges the DB of all exam room related data.
     */
    public void deleteAllExamRooms() {
        final long startTime = System.nanoTime();

        // deleting everything in batch is more efficient
        examRoomRepository.deleteAll();

        log.debug("Deleting all exam rooms took {}", TimeLogUtil.formatDurationFrom(startTime));
    }

    /**
     * Deletes all outdated and unused exam rooms.
     * <p/>
     * An exam room is outdated if another exam room with the same room-number and room-name exists, and that exam
     * room's creation date is before the other's. An exam room is unused if there is no existing mapping to an exam.
     *
     * @return A summary containing some information about the deletion process.
     */
    public ExamRoomDeletionSummaryDTO deleteAllOutdatedAndUnusedExamRooms() {
        final long startTime = System.nanoTime();

        Set<Long> outdatedAndUnusedExamRoomIds = examRoomRepository.findAllIdsOfOutdatedAndUnusedExamRooms();
        examRoomRepository.deleteAllById(outdatedAndUnusedExamRoomIds);

        log.debug("Deleting all unused and outdated exam rooms took {}", TimeLogUtil.formatDurationFrom(startTime));
        PeriodFormatter formatter = new PeriodFormatterBuilder().appendDays().appendSuffix("d ").appendHours().appendSuffix("h ").appendMinutes().appendSuffix("m ").appendSeconds()
                .appendSuffix("s ").appendMillis().appendSuffix("ms").toFormatter();

        String duration = formatter.print(Period.millis((int) Duration.ofNanos(System.nanoTime() - startTime).toMillis()).normalizedStandard());
        return new ExamRoomDeletionSummaryDTO(duration, outdatedAndUnusedExamRoomIds.size());
    }

}
