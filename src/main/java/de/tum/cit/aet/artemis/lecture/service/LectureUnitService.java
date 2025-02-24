package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;

@Profile(PROFILE_CORE)
@Service
public class LectureUnitService {

    private static final Logger log = LoggerFactory.getLogger(LectureUnitService.class);

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureRepository lectureRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final FileService fileService;

    private final SlideRepository slideRepository;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final Optional<CompetencyRelationApi> competencyRelationApi;

    public LectureUnitService(LectureUnitRepository lectureUnitRepository, LectureRepository lectureRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            FileService fileService, SlideRepository slideRepository, Optional<PyrisWebhookService> pyrisWebhookService, Optional<CompetencyProgressApi> competencyProgressApi,
            Optional<CourseCompetencyApi> courseCompetencyApi, Optional<CompetencyRelationApi> competencyRelationApi) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.fileService = fileService;
        this.slideRepository = slideRepository;
        this.pyrisWebhookService = pyrisWebhookService;
        this.courseCompetencyApi = courseCompetencyApi;
        this.competencyProgressApi = competencyProgressApi;
        this.competencyRelationApi = competencyRelationApi;
    }

    /**
     * Set the completion status of the lecture unit for the give user
     * If the user completed the unit and completion status already exists, nothing happens
     *
     * @param lectureUnit The lecture unit for which set the completion flag
     * @param user        The user that completed/uncompleted the lecture unit
     * @param completed   True if the lecture unit was completed, false otherwise
     */
    public void setLectureUnitCompletion(@NotNull LectureUnit lectureUnit, @NotNull User user, boolean completed) {
        Optional<LectureUnitCompletion> existingCompletion = lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lectureUnit.getId(), user.getId());
        if (completed) {
            if (existingCompletion.isEmpty()) {
                LectureUnitCompletion completion = createLectureUnitCompletion(lectureUnit, user);
                try {
                    // Flush, so that the asynchronous mastery calculation uses the correct completion status
                    lectureUnitCompletionRepository.saveAndFlush(completion);
                }
                catch (DataIntegrityViolationException e) {
                    // In rare instances the completion status might already exist if this method runs in parallel.
                    // This fails the SQL unique constraint and throws an exception. We can safely ignore it.
                }
            }
        }
        else {
            existingCompletion.ifPresent(lectureUnitCompletionRepository::delete);
        }
    }

    /**
     * Set the completion status of all passed lecture units for the give user
     * If the user completed the unit and completion status already exists, nothing happens
     *
     * @param lectureUnits List of all lecture units for which to set the completion flag
     * @param user         The user that completed/uncompleted the lecture unit
     * @param completed    True if the lecture unit was completed, false otherwise
     */
    public void setCompletedForAllLectureUnits(List<? extends LectureUnit> lectureUnits, @NotNull User user, boolean completed) {
        var existingCompletion = lectureUnitCompletionRepository.findByLectureUnitsAndUserId(lectureUnits, user.getId());
        if (!completed) {
            lectureUnitCompletionRepository.deleteAll(existingCompletion);
            return;
        }

        if (!existingCompletion.isEmpty()) {
            var alreadyCompletedUnits = existingCompletion.stream().map(LectureUnitCompletion::getLectureUnit).collect(Collectors.toSet());

            // make lectureUnits modifiable
            lectureUnits = new ArrayList<>(lectureUnits);
            lectureUnits.removeAll(alreadyCompletedUnits);
        }

        var completions = lectureUnits.stream().map(unit -> createLectureUnitCompletion(unit, user)).toList();

        try {
            lectureUnitCompletionRepository.saveAll(completions);
        }
        catch (DataIntegrityViolationException e) {
            // In rare instances the completion status might already exist if this method runs in parallel.
            // This fails the SQL unique constraint and throws an exception. We can safely ignore it.
        }
    }

    private LectureUnitCompletion createLectureUnitCompletion(LectureUnit lectureUnit, User user) {
        // Create a completion status for this lecture unit (only if it does not exist)
        LectureUnitCompletion completion = new LectureUnitCompletion();
        completion.setLectureUnit(lectureUnit);
        completion.setUser(user);
        completion.setCompletedAt(ZonedDateTime.now());
        return completion;
    }

    /**
     * Deletes a lecture unit correctly in the database
     *
     * @param lectureUnit lecture unit to delete
     */
    public void removeLectureUnit(@NotNull LectureUnit lectureUnit) {
        LectureUnit lectureUnitToDelete = lectureUnitRepository.findByIdWithCompetenciesAndSlidesElseThrow(lectureUnit.getId());

        if (lectureUnitToDelete instanceof AttachmentUnit attachmentUnit) {
            fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(URI.create((attachmentUnit.getAttachment().getLink()))), 5);
            if (attachmentUnit.getSlides() != null && !attachmentUnit.getSlides().isEmpty()) {
                List<Slide> slides = attachmentUnit.getSlides();
                for (Slide slide : slides) {
                    fileService.schedulePathForDeletion(FilePathService.actualPathForPublicPathOrThrow(URI.create(slide.getSlideImagePath())), 5);
                }
                pyrisWebhookService.ifPresent(service -> service.deleteLectureFromPyrisDB(List.of(attachmentUnit)));
                slideRepository.deleteAll(slides);
            }
        }

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureUnitToDelete.getLecture().getId());
        // Creating a new list of lecture units without the one we want to remove
        lecture.getLectureUnits().removeIf(unit -> unit == null || unit.getId().equals(lectureUnitToDelete.getId()));
        lectureRepository.save(lecture);

        if (!(lectureUnitToDelete instanceof ExerciseUnit)) {
            // update associated competency progress objects
            competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(lectureUnitToDelete, Optional.empty()));
        }
    }

    /**
     * Link the competency to a set of lecture units
     *
     * @param competency       The competency to be linked
     * @param lectureUnitLinks New set of lecture unit links to associate with the competency
     */
    public void linkLectureUnitsToCompetency(CourseCompetency competency, Set<CompetencyLectureUnitLink> lectureUnitLinks) {
        if (courseCompetencyApi.isEmpty()) {
            return;
        }
        lectureUnitLinks.forEach(link -> link.setCompetency(competency));
        competency.setLectureUnitLinks(lectureUnitLinks);
        courseCompetencyApi.get().save(competency);
    }

    /**
     * Removes competency from all lecture units.
     *
     * @param lectureUnitLinks set of lecture unit links
     * @param competency       competency to remove
     */
    public void removeCompetency(Set<CompetencyLectureUnitLink> lectureUnitLinks, CourseCompetency competency) {
        competencyRelationApi.ifPresent(api -> api.deleteAllLectureUnitLinks(lectureUnitLinks));
        competency.getLectureUnitLinks().removeAll(lectureUnitLinks);
    }

    /**
     * Validates the given URL string and returns the URL object
     *
     * @param urlString The URL string to validate
     * @return The URL object
     * @throws BadRequestException If the URL string is invalid
     */
    public URL validateUrlStringAndReturnUrl(String urlString) {
        try {
            return new URI(urlString).toURL();
        }
        catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            throw new BadRequestException();
        }
    }

    /**
     * This method is responsible for ingesting a specific `LectureUnit` into Pyris, but only if it is an instance of
     * `AttachmentUnit`. If the Pyris webhook service is available, it attempts to add the `LectureUnit` to the Pyris
     * database.
     * The method responds with different HTTP status codes based on the result:
     * Returns {OK} if the ingestion is successful.
     * Returns {SERVICE_UNAVAILABLE} if the Pyris webhook service is unavailable or if the ingestion fails.
     * Returns {400 BAD_REQUEST} if the provided lecture unit is not of type {AttachmentUnit}.
     *
     * @param lectureUnit the lecture unit to be ingested, which must be an instance of AttachmentUnit.
     * @return ResponseEntity<Void> representing the outcome of the operation with the appropriate HTTP status.
     */
    public ResponseEntity<Void> ingestLectureUnitInPyris(LectureUnit lectureUnit) {
        if (!(lectureUnit instanceof AttachmentUnit)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        if (pyrisWebhookService.isEmpty()) {
            log.error("Could not send Lecture Unit to Pyris: Pyris webhook service is not available, check if IRIS is enabled.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        boolean isIngested = pyrisWebhookService.get().addLectureUnitToPyrisDB((AttachmentUnit) lectureUnit) != null;
        return ResponseEntity.status(isIngested ? HttpStatus.OK : HttpStatus.BAD_REQUEST).build();
    }

    /**
     * Disconnects the competency exercise links from the exercise before the cycle is broken by the deserialization.
     *
     * @param lectureUnit The lecture unit to disconnect the competency links
     */
    public void disconnectCompetencyLectureUnitLinks(LectureUnit lectureUnit) {
        lectureUnit.getCompetencyLinks().forEach(link -> link.getCompetency().setLectureUnitLinks(null));
    }

    /**
     * Reconnects the competency exercise links to the exercise after the cycle was broken by the deserialization.
     *
     * @param lectureUnit The lecture unit to reconnect the competency links
     */
    public void reconnectCompetencyLectureUnitLinks(LectureUnit lectureUnit) {
        lectureUnit.getCompetencyLinks().forEach(link -> link.setLectureUnit(lectureUnit));
    }

    /**
     * Saves the exercise and links it to the competencies.
     *
     * @param lectureUnit  the lecture unit to save
     * @param saveFunction function to save the exercise
     * @param <T>          type of the lecture unit
     * @return saved exercise
     */
    public <T extends LectureUnit> T saveWithCompetencyLinks(T lectureUnit, Function<T, T> saveFunction) {
        // persist lecture Unit before linking it to the competency
        Set<CompetencyLectureUnitLink> links = lectureUnit.getCompetencyLinks();
        lectureUnit.setCompetencyLinks(new HashSet<>());

        T savedLectureUnit = saveFunction.apply(lectureUnit);

        if (Hibernate.isInitialized(links) && links != null && !links.isEmpty()) {
            savedLectureUnit.setCompetencyLinks(links);
            reconnectCompetencyLectureUnitLinks(savedLectureUnit);
            competencyRelationApi.ifPresent(api -> savedLectureUnit.setCompetencyLinks(new HashSet<>(api.saveAllLectureUnitLinks(links))));
        }

        return savedLectureUnit;
    }
}
