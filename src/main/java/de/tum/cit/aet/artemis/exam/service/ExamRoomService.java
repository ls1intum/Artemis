package de.tum.cit.aet.artemis.exam.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.domain.room.ExamSeat;
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategy;
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategyType;
import de.tum.cit.aet.artemis.exam.domain.room.SeatCondition;
import de.tum.cit.aet.artemis.exam.dto.ExamRoomUploadInformationDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomRepository;

@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamRoomService {

    private static final Logger log = LoggerFactory.getLogger(ExamRoomService.class);

    private final ExamRoomRepository examRoomRepository;

    public ExamRoomService(ExamRoomRepository examRoomRepository) {
        this.examRoomRepository = examRoomRepository;
    }

    public ExamRoomUploadInformationDTO parseAndStoreExamRoomDataFromZipFile(MultipartFile zipFile) {
        // TODO: Delete old, unused entries
        final long startTime = System.nanoTime();
        log.info("Starting to parse rooms from {}...", zipFile.getOriginalFilename());
        Set<ExamRoom> examRooms = new HashSet<>();

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            ObjectMapper mapper = new ObjectMapper();

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // validate file type
                if (entry.isDirectory() || !entryName.endsWith(".json")) {
                    log.debug("Skipping {} because it's not a json file", entryName);
                    continue;
                }

                // extract the filename - remove the folder path if existent
                // Math.max(0, entryName.lastIndexOf('/') + 1); === entryName.lastIndexOf('/') + 1;
                int longRoomNumberStartIdx = entryName.lastIndexOf('/') + 1;
                String longRoomNumber = entryName.substring(longRoomNumberStartIdx);

                log.debug("Parsing room {}...", longRoomNumber);
                String jsonData = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                JsonNode jsonRoot = mapper.readTree(jsonData);

                var examRoom = parseExamRoomJsonFile(longRoomNumber, jsonRoot);
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

    private static ExamRoom parseExamRoomJsonFile(final String longRoomNumber, final JsonNode jsonRoot) throws IOException {
        // Manual mapping of JSON to fields
        ExamRoom room = new ExamRoom();
        room.setLongRoomNumber(longRoomNumber);
        room.setShortRoomNumber(jsonRoot.get("number").asText());
        room.setName(jsonRoot.path("name").asText());
        room.setAlternativeName(jsonRoot.path("shortname").asText(null));
        room.setBuilding(jsonRoot.path("building").asText());

        // Extract the seats
        JsonNode rowsArrayNode = jsonRoot.path("rows");
        List<ExamSeat> seats = parseExamSeats(longRoomNumber, rowsArrayNode, room);
        if (seats == null)
            return null;

        room.setSeats(seats);

        // Extract the rows
        JsonNode layoutsObjectNode = jsonRoot.path("layouts");
        List<LayoutStrategy> layouts = parseLayoutStrategies(longRoomNumber, layoutsObjectNode, room);
        if (layouts == null)
            return null;

        room.setLayoutStrategies(layouts);

        return room;
    }

    private static List<LayoutStrategy> parseLayoutStrategies(String longRoomNumber, JsonNode layoutsObjectNode, ExamRoom room) {
        List<LayoutStrategy> layouts = new ArrayList<>();

        if (!layoutsObjectNode.isObject()) {
            log.warn("Skipping room {} because the layouts are not correctly stored", longRoomNumber);
            return null;
        }

        // Iterate over all possible room layout names, e.g., "default" or "wide"
        for (Iterator<String> it = layoutsObjectNode.fieldNames(); it.hasNext();) {
            String layoutName = it.next();
            JsonNode layoutNode = layoutsObjectNode.path(layoutName);

            // We assume there's only a single layout type, e.g., "auto_layout" or "usable_seats"
            final String layoutType = layoutNode.fieldNames().next();
            final JsonNode layoutDetailNode = layoutNode.path(layoutType);

            LayoutStrategy layoutStrategy = new LayoutStrategy();
            layoutStrategy.setName(layoutName);
            layoutStrategy.setRoom(room);
            switch (layoutType) {
                case "auto_layout" -> layoutStrategy.setType(LayoutStrategyType.RELATIVE_DISTANCE);
                // useable_seats is a common typo in the JSON files
                case "usable_seats", "useable_seats" -> layoutStrategy.setType(LayoutStrategyType.FIXED_SELECTION);
                default -> {
                    log.warn("Unknown layout type '{}' in room {}", layoutType, longRoomNumber);
                    continue;
                }
            }
            layoutStrategy.setParametersJson(String.valueOf(layoutDetailNode));

            // pre-calculate the capacity if it's easy/efficient to do so.
            // Right now this is only the case for the fixed_selection
            switch (layoutStrategy.getType()) {
                case LayoutStrategyType.FIXED_SELECTION -> {
                    if (!layoutDetailNode.isArray()) {
                        log.warn("Skipping layout '{}' of room {} because it's a fixed selection, but parameters aren't an array", layoutName, longRoomNumber);
                        continue;
                    }

                    layoutStrategy.setCapacity(layoutDetailNode.size());
                }
                case LayoutStrategyType.RELATIVE_DISTANCE -> {
                    // Here it's not obvious. It may be done/optionally enabled in the future.
                }
            }

            layouts.add(layoutStrategy);
        }
        return layouts;
    }

    private static List<ExamSeat> parseExamSeats(String longRoomNumber, JsonNode rowsArrayNode, ExamRoom room) {
        List<ExamSeat> seats = new ArrayList<>();
        if (!rowsArrayNode.isArray()) {
            log.warn("Skipping room {} because the rows are incorrectly stored", longRoomNumber);
            return null;
        }

        for (JsonNode rowNode : rowsArrayNode) {
            JsonNode seatsArray = rowNode.path("seats");
            if (!seatsArray.isArray()) {
                log.warn("Skipping room {} because the seats are incorrectly stored", longRoomNumber);
                return null;
            }

            String rowLabel = rowNode.path("label").asText();

            for (JsonNode seatNode : seatsArray) {
                String seatLabel = seatNode.path("label").asText();
                String seatName = rowLabel.isEmpty() ? seatLabel : (seatLabel + ", " + rowLabel);

                ExamSeat seat = new ExamSeat();
                seat.setLabel(seatName);
                seat.setX(seatNode.path("position").path("x").asDouble());
                seat.setY(seatNode.path("position").path("y").asDouble());
                seat.setSeatCondition(SeatCondition.SeatConditionFromFlag(seatNode.path("flag").asText()));
                seat.setRoom(room);
                seats.add(seat);
            }
        }
        return seats;
    }

    private static ExamRoomUploadInformationDTO getExamRoomUploadInformationDTO(MultipartFile zipFile, long startTime, Set<ExamRoom> examRooms) {
        long startTimeDTO = System.nanoTime();

        String uploadedFileName = zipFile.getOriginalFilename();
        PeriodFormatter formatter = new PeriodFormatterBuilder().appendDays().appendSuffix("d ").appendHours().appendSuffix("h ").appendMinutes().appendSuffix("m ").appendSeconds()
                .appendSuffix("s ").appendMillis().appendSuffix("ms").toFormatter();
        String uploadDuration = formatter.print(Period.millis((int) Duration.ofNanos(startTimeDTO - startTime).toMillis()).normalizedStandard());
        Integer numberOfUploadedRooms = examRooms.size();
        Integer numberOfUploadedSeats = examRooms.stream().mapToInt(room -> room.getSeats().size()).sum();
        List<String> roomNames = examRooms.stream().map(ExamRoom::getName).toList();

        return new ExamRoomUploadInformationDTO(uploadedFileName, uploadDuration, numberOfUploadedRooms, numberOfUploadedSeats, roomNames);
    }
}
