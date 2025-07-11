package de.tum.cit.aet.artemis.exam.web.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.domain.room.ExamSeat;
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategy;
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategyType;
import de.tum.cit.aet.artemis.exam.domain.room.SeatCondition;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomRepository;

/**
 * REST controller for administrating Exam.
 */
@Conditional(ExamEnabled.class)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/exam/admin/")
public class AdminExamResource {

    private static final Logger log = LoggerFactory.getLogger(AdminExamResource.class);

    private final ExamRepository examRepository;

    private final ExamRoomRepository examRoomRepository;

    public AdminExamResource(ExamRepository examRepository, ExamRoomRepository examRoomRepository) {
        this.examRepository = examRepository;
        this.examRoomRepository = examRoomRepository;
    }

    /**
     * GET /exams/upcoming : Find all current and upcoming exams.
     *
     * @return the ResponseEntity with status 200 (OK) and a list of exams.
     */
    @GetMapping("courses/upcoming-exams")
    public ResponseEntity<List<Exam>> getCurrentAndUpcomingExams() {
        log.debug("REST request to get all upcoming exams");

        List<Exam> upcomingExams = examRepository.findAllCurrentAndUpcomingExams();
        return ResponseEntity.ok(upcomingExams);
    }

    /**
     * POST /api/exam/admin/exam-rooms/upload : Upload a zip file containing room data to be parsed and added to Artemis.
     *
     * @param zipFile The zip file to be uploaded. It needs to contain the `.json` files containing the room data in
     *                    the following format:
     *                    filename : long room number
     *                    "number" : short room number
     *                    "name" : long room name
     *                    "shortname" : short room name
     *                    "building" : short enclosing building name
     *                    "rows" : list of rows
     *                    "layouts" : list of layouts
     * @return A set of all newly created exam rooms
     */
    @PostMapping("exam-rooms/upload")
    public ResponseEntity<Set<ExamRoom>> uploadRoomZip(@RequestParam("file") MultipartFile zipFile) {
        Set<ExamRoom> examRooms = new HashSet<>();

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            ObjectMapper mapper = new ObjectMapper();

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                if (entry.isDirectory() || !entryName.endsWith(".json")) {
                    log.info("Skipping entry: {} because it's not a json file", entryName);
                    continue;
                }

                Pattern fileNameExtractorPattern = Pattern.compile("^.*/([^/]+)\\.json$");
                Matcher fileNameExtractorMatcher = fileNameExtractorPattern.matcher(entryName);
                if (!fileNameExtractorMatcher.find(1)) {
                    log.info("Skipping entry: {} because the filename could not be obtained", entryName);
                }
                String longRoomNumber = fileNameExtractorMatcher.group(1);

                String jsonData = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                JsonNode jsonRoot = mapper.readTree(jsonData);

                // Manual mapping of JSON to fields
                ExamRoom room = new ExamRoom();
                room.setLongRoomNumber(longRoomNumber);
                room.setShortRoomNumber(jsonRoot.get("number").asText());
                room.setName(jsonRoot.path("name").asText());
                room.setAlternativeName(jsonRoot.path("shortname").asText(null));
                room.setBuilding(jsonRoot.path("building").asText());
                // capacity is not stored directly in the JSON, but needs to be calculated.
                // We skip this for now but will instantiate the field once we first access it.

                /* Extract the seats from the rows */
                List<ExamSeat> seats = new ArrayList<>();
                JsonNode rowsArray = jsonRoot.path("rows");
                if (!rowsArray.isArray()) {
                    log.warn("Skipping entry '{}' because rowsArray is not an array", entryName);
                    continue;
                }

                for (JsonNode rowNode : rowsArray) {
                    JsonNode seatsArray = rowNode.path("seats");
                    if (!seatsArray.isArray()) {
                        log.warn("Skipping entry '{}' because seatsArray is not an array", entryName);
                        continue;
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

                room.setSeats(seats);
                /* Extracting seats End */

                /* Extract layout strategies */
                List<LayoutStrategy> layouts = new ArrayList<>();
                JsonNode layoutsArray = jsonRoot.path("layouts");
                if (!layoutsArray.isArray()) {
                    log.warn("Skipping entry '{}' because layouts is not an array", entryName);
                    continue;
                }
                for (JsonNode layoutNode : layoutsArray) {
                    layoutNode.fieldNames().forEachRemaining(log::info);
                    log.info("Skipping here for now");
                    LayoutStrategy ls = new LayoutStrategy();
                    ls.setName(layoutNode.path("name").asText());
                    ls.setType(LayoutStrategyType.valueOf(layoutNode.path("type").asText("DEFAULT").toUpperCase()));
                    ls.setParametersJson(layoutNode.path("parameters").toString());
                    ls.setRoom(room);
                    layouts.add(ls);
                }

                room.setLayoutStrategies(layouts);
                /* Extract layout strategies End */

                examRooms.add(room);
            }

        }
        catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Internal error while trying to parse the rooms");
        }

        return ResponseEntity.ok(examRooms);
    }

}
