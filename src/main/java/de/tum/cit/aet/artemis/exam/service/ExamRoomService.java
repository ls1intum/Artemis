package de.tum.cit.aet.artemis.exam.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
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
import de.tum.cit.aet.artemis.exam.repository.ExamRoomRepository;

/*
 * Service implementation for managing exam rooms.
 */
@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamRoomService {

    private static final String ENTITY_NAME = "examroomservice";

    private final ExamRoomRepository examRoomRepository;

    private final ObjectMapper objectMapper;

    public ExamRoomService(ExamRoomRepository examRoomRepository, ObjectMapper objectMapper) {
        this.examRoomRepository = examRoomRepository;
        this.objectMapper = objectMapper;
    }

    /* Multiple records that will be used internally for Jackson deserialization */
    private record ExamRoomInput(@JsonProperty("number") String alternativeNumber, String name, @JsonProperty("shortname") String alternativeName, String building,
            List<RowInput> rows, Map<String, JsonNode> layouts) {
    }

    private record RowInput(String label, List<SeatInput> seats) {
    }

    private record SeatInput(String label, @JsonProperty("flag") String condition, PositionInput position) {
    }

    private record PositionInput(double x, double y) {
    }

    /**
     * Looks through all JSON files contained in a given zip file (recursive search).
     * Then it adds all exam rooms it could parse to the database, ignoring duplicates of the same room.
     * The exam rooms' primary room numbers are the filenames of the JSON files containing their data.
     *
     * @param zipFile A zip file containing JSON files of exam room data.
     * @return A small DTO that can be returned to the client, containing some metrics about the parsing process.
     */
    public ExamRoomUploadInformationDTO parseAndStoreExamRoomDataFromZipFile(MultipartFile zipFile) {
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
                if (roomNumber.isBlank()) {
                    throw new BadRequestAlertException("Invalid room file name: missing room number", ENTITY_NAME, "room.missingRoomNumber");
                }

                try {
                    ExamRoomInput examRoomInput = objectMapper.readValue(zis.readAllBytes(), ExamRoomInput.class);

                    ExamRoom examRoom = convertRoomNumberAndExamRoomInputToExamRoom(roomNumber, examRoomInput);
                    examRooms.add(examRoom);
                }
                finally {
                    zis.closeEntry();
                }
            }

        }
        catch (IOException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "room.parseIoException", Map.of("errorMessage", e.getMessage()));
        }

        examRoomRepository.saveAll(examRooms);

        return getExamRoomUploadInformationDTO(zipFile, examRooms);
    }

    /**
     * Converts {@link ExamRoomInput}, which is automatically deserialized from Jackson,
     * and together with {@code roomNumber} generate an {@link ExamRoom}.
     *
     * @param roomNumber    The roomNumber of the exam room we want to create.
     * @param examRoomInput The Jackson parsed exam room input
     * @return The ExamRoom as stored in the JSON room data.
     */
    private static ExamRoom convertRoomNumberAndExamRoomInputToExamRoom(String roomNumber, ExamRoomInput examRoomInput) {
        if (examRoomInput == null) {
            throw new BadRequestAlertException("Malformed room JSON", ENTITY_NAME, "room.malformedJson", Map.of("roomNumber", roomNumber));
        }

        if (examRoomInput.name == null || examRoomInput.name.isBlank() || examRoomInput.building == null || examRoomInput.building.isBlank()) {
            throw new BadRequestAlertException("Room name and building are required fields", ENTITY_NAME, "room.missingNameOrBuilding", Map.of("roomNumber", roomNumber));
        }

        ExamRoom room = extractSimpleExamRoomFields(roomNumber, examRoomInput);

        return extractSeatsAndLayouts(room, examRoomInput);
    }

    private static ExamRoom extractSimpleExamRoomFields(String roomNumber, ExamRoomInput examRoomInput) {
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
        return room;
    }

    private static ExamRoom extractSeatsAndLayouts(ExamRoom room, ExamRoomInput examRoomInput) {
        // It is imperative that the seats are parsed before the layout strategies are parsed, as the size calculation
        // for relative layouts will only be done if the rooms have already been parsed
        room.setSeats(convertRowInputsToExamSeatDTOs(examRoomInput.rows, room.getRoomNumber()));

        room.setLayoutStrategies(convertLayoutInputsToLayoutStrategies(examRoomInput.layouts, room));
        return room;
    }

    /**
     * Converts {@link RowInput}s into {@link ExamSeatDTO}s
     *
     * @param rows The list of Jackson parsed row inputs
     * @return The list of all exam seats it could convert from the {@code rows}
     */
    private static List<ExamSeatDTO> convertRowInputsToExamSeatDTOs(List<RowInput> rows, String roomNumber) {
        if (rows == null) {
            throw new BadRequestAlertException("Seats are missing", ENTITY_NAME, "room.missingSeats", Map.of("roomNumber", roomNumber));
        }

        List<ExamSeatDTO> seats = new ArrayList<>();

        for (RowInput rowInput : rows) {
            if (rowInput == null || rowInput.seats == null) {
                throw new BadRequestAlertException("Malformed row: seats missing", ENTITY_NAME, "room.malformedRow", Map.of("roomNumber", roomNumber));
            }

            final String rowLabel = rowInput.label == null ? "" : rowInput.label;
            for (SeatInput seatInput : rowInput.seats) {
                if (seatInput == null || seatInput.position == null) {
                    throw new BadRequestAlertException("Malformed seat: position missing", ENTITY_NAME, "room.malformedSeat", Map.of("roomNumber", roomNumber));
                }

                final String seatLabel = seatInput.label == null ? "" : seatInput.label;
                final String seatName = rowLabel.isEmpty() ? seatLabel : (rowLabel + ", " + seatLabel);
                SeatCondition seatCondition;
                try {
                    seatCondition = SeatCondition.seatConditionFromFlag(seatInput.condition);
                }
                catch (IllegalArgumentException e) {
                    throw new BadRequestAlertException("Invalid seat condition", ENTITY_NAME, "room.invalidSeatCondition",
                            Map.of("roomNumber", roomNumber, "seatCondition", seatInput.condition));
                }
                ExamSeatDTO seat = new ExamSeatDTO(seatName, seatCondition, seatInput.position.x, seatInput.position.y);

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
        if (layoutNamesToLayoutNode == null) {
            throw new BadRequestAlertException("Couldn't parse room " + room.getRoomNumber() + " because the layouts are missing", ENTITY_NAME, "room.missingLayouts",
                    Map.of("roomNumber", room.getRoomNumber()));
        }

        List<LayoutStrategy> layouts = new ArrayList<>();

        layoutNamesToLayoutNode.forEach((layoutName, layoutNode) -> {
            if (layoutNode == null || !layoutNode.fieldNames().hasNext()) {
                throw new BadRequestAlertException("Couldn't parse room " + room.getRoomNumber() + " because the layouts couldn't be converted", ENTITY_NAME,
                        "room.malformedLayout", Map.of("roomNumber", room.getRoomNumber(), "layoutName", layoutName));
            }

            // We assume there's only a single layout type, e.g., "auto_layout" or "usable_seats"
            final String layoutType = layoutNode.fieldNames().next();
            final JsonNode layoutDetailNode = layoutNode.path(layoutType);

            LayoutStrategy layoutStrategy = new LayoutStrategy();
            layoutStrategy.setName(layoutName);
            layoutStrategy.setExamRoom(room);
            switch (layoutType) {
                case "auto_layout" -> layoutStrategy.setType(LayoutStrategyType.RELATIVE_DISTANCE);
                case "useable_seats" -> layoutStrategy.setType(LayoutStrategyType.FIXED_SELECTION);
                default -> throw new BadRequestAlertException("Couldn't parse room " + room.getRoomNumber() + " because the layouts couldn't be converted", ENTITY_NAME,
                        "room.unknownLayoutType", Map.of("roomNumber", room.getRoomNumber(), "layoutName", layoutName, "layoutType", layoutType));
            }
            layoutStrategy.setParametersJson(String.valueOf(layoutDetailNode));

            // pre-calculate the capacity
            switch (layoutStrategy.getType()) {
                case LayoutStrategyType.FIXED_SELECTION -> {
                    if (!layoutDetailNode.isArray()) {
                        throw new BadRequestAlertException("Couldn't parse room " + room.getRoomNumber() + " because the layouts couldn't be converted", ENTITY_NAME,
                                "room.malformedLayout", Map.of("roomNumber", room.getRoomNumber(), "layoutName", layoutName));
                    }

                    layoutStrategy.setCapacity(layoutDetailNode.size());
                }
                case LayoutStrategyType.RELATIVE_DISTANCE -> {
                    if (!layoutDetailNode.isObject()) {
                        throw new BadRequestAlertException("Couldn't parse room " + room.getRoomNumber() + " because the layouts couldn't be converted", ENTITY_NAME,
                                "room.malformedLayout", Map.of("roomNumber", room.getRoomNumber(), "layoutName", layoutName));
                    }

                    layoutStrategy.setCapacity(calculateSeatsFromRelativeDistanceLayout(layoutDetailNode, room));
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
     * @return The number of exam seats that can be used with the given layout
     */
    private static int calculateSeatsFromRelativeDistanceLayout(JsonNode layoutDetailNode, ExamRoom examRoom) {
        if (examRoom.getSeats().isEmpty()) {
            return 0;
        }

        // first_row in some rooms is -1. I assume this means the same as 0
        final int firstRow = layoutDetailNode.path("first_row").asInt(0);
        final double xSpace = layoutDetailNode.path("xspace").asDouble(0);
        final double ySpace = layoutDetailNode.path("yspace").asDouble(0);

        List<ExamSeatDTO> examSeatsFilteredAndSorted = examRoom.getSeats().stream()
                // Filter out all exam rooms that are before the "first row". The coords start at 0, the row numbers at 1
                .filter(examSeatDTO -> examSeatDTO.yCoordinate() >= (firstRow - 1)).filter(examSeatDTO -> examSeatDTO.seatCondition() == SeatCondition.USABLE)
                .sorted(Comparator.comparingDouble(ExamSeatDTO::yCoordinate).thenComparingDouble(ExamSeatDTO::xCoordinate)).toList();

        List<ExamSeatDTO> selectedSeats = new ArrayList<>();
        for (ExamSeatDTO examSeatDTO : examSeatsFilteredAndSorted) {
            boolean isFarEnough = selectedSeats.stream().noneMatch(
                    existing -> Math.abs(existing.yCoordinate() - examSeatDTO.yCoordinate()) <= ySpace && Math.abs(existing.xCoordinate() - examSeatDTO.xCoordinate()) <= xSpace);
            if (isFarEnough) {
                selectedSeats.add(examSeatDTO);
            }
        }

        return selectedSeats.size();
    }

    private static ExamRoomUploadInformationDTO getExamRoomUploadInformationDTO(MultipartFile zipFile, Set<ExamRoom> examRooms) {
        String uploadedFileName = zipFile.getOriginalFilename();
        int numberOfUploadedRooms = examRooms.size();
        int numberOfUploadedSeats = examRooms.stream().mapToInt(room -> room.getSeats().size()).sum();
        List<String> roomNames = examRooms.stream().map(ExamRoom::getName).toList();

        return new ExamRoomUploadInformationDTO(uploadedFileName, numberOfUploadedRooms, numberOfUploadedSeats, roomNames);
    }

    /**
     * Calculates information about the current state of the exam rooms in the database.
     *
     * @return A DTO that can be sent to the client, containing basic information about the state of the exam room DB.
     */
    public ExamRoomAdminOverviewDTO getExamRoomAdminOverview() {
        final Set<ExamRoom> examRooms = examRoomRepository.findAllExamRoomsWithEagerLayoutStrategies();

        final int numberOfStoredExamRooms = examRooms.size();
        final int numberOfStoredExamSeats = examRooms.stream().mapToInt(er -> er.getSeats().size()).sum();
        final int numberOfStoredLayoutStrategies = examRooms.stream().mapToInt(er -> er.getLayoutStrategies().size()).sum();

        final Set<ExamRoomDTO> examRoomDTOS = examRooms.stream()
                .map(examRoom -> new ExamRoomDTO(examRoom.getRoomNumber(), examRoom.getName(), examRoom.getBuilding(), examRoom.getSeats().size(),
                        examRoom.getLayoutStrategies().stream().map(ls -> new ExamRoomLayoutStrategyDTO(ls.getName(), ls.getType(), ls.getCapacity())).collect(Collectors.toSet())))
                .collect(Collectors.toSet());

        return new ExamRoomAdminOverviewDTO(numberOfStoredExamRooms, numberOfStoredExamSeats, numberOfStoredLayoutStrategies, examRoomDTOS);
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
        Set<Long> outdatedAndUnusedExamRoomIds = examRoomRepository.findAllIdsOfOutdatedAndUnusedExamRooms();
        examRoomRepository.deleteAllById(outdatedAndUnusedExamRoomIds);

        return new ExamRoomDeletionSummaryDTO(outdatedAndUnusedExamRoomIds.size());
    }

}
