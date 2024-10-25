package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.FilePathService;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
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

    private final ExerciseRepository exerciseRepository;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    private final CompetencyProgressService competencyProgressService;

    private final CourseCompetencyRepository courseCompetencyRepository;

    public LectureUnitService(LectureUnitRepository lectureUnitRepository, LectureRepository lectureRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            FileService fileService, SlideRepository slideRepository, ExerciseRepository exerciseRepository, Optional<PyrisWebhookService> pyrisWebhookService,
            CompetencyProgressService competencyProgressService, CourseCompetencyRepository courseCompetencyRepository) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.fileService = fileService;
        this.slideRepository = slideRepository;
        this.exerciseRepository = exerciseRepository;
        this.pyrisWebhookService = pyrisWebhookService;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.competencyProgressService = competencyProgressService;
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

        if (!(lectureUnitToDelete instanceof ExerciseUnit)) {
            // update associated competencies
            Set<CourseCompetency> competencies = lectureUnitToDelete.getCompetencies();
            courseCompetencyRepository.saveAll(competencies.stream().map(competency -> {
                competency = courseCompetencyRepository.findByIdWithLectureUnitsElseThrow(competency.getId());
                competency.getLectureUnits().remove(lectureUnitToDelete);
                return competency;
            }).toList());
        }

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
            competencyProgressService.updateProgressForUpdatedLearningObjectAsync(lectureUnitToDelete, Optional.empty());
        }
    }

    /**
     * Link the competency to a set of lecture units (and exercises if it includes exercise units)
     *
     * @param competency           The competency to be linked
     * @param lectureUnitsToAdd    A set of lecture units to link to the specified competency
     * @param lectureUnitsToRemove A set of lecture units to unlink from the specified competency
     */
    public void linkLectureUnitsToCompetency(CourseCompetency competency, Set<LectureUnit> lectureUnitsToAdd, Set<LectureUnit> lectureUnitsToRemove) {
        final Predicate<LectureUnit> isExerciseUnit = lectureUnit -> lectureUnit instanceof ExerciseUnit;

        // Remove the competency from the old lecture units
        var lectureUnitsToRemoveFromDb = lectureUnitRepository.findAllByIdWithCompetenciesBidirectional(lectureUnitsToRemove.stream().map(LectureUnit::getId).toList());
        lectureUnitRepository.saveAll(lectureUnitsToRemoveFromDb.stream().filter(isExerciseUnit.negate()).peek(lectureUnit -> lectureUnit.getCompetencies().remove(competency))
                .collect(Collectors.toSet()));
        exerciseRepository.saveAll(lectureUnitsToRemoveFromDb.stream().filter(isExerciseUnit).map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise())
                .peek(exercise -> exercise.getCompetencies().remove(competency)).collect(Collectors.toSet()));

        // Add the competency to the new lecture units
        var lectureUnitsFromDb = lectureUnitRepository.findAllByIdWithCompetenciesBidirectional(lectureUnitsToAdd.stream().map(LectureUnit::getId).toList());
        var lectureUnitsWithoutExercises = lectureUnitsFromDb.stream().filter(isExerciseUnit.negate()).collect(Collectors.toSet());
        var exercises = lectureUnitsFromDb.stream().filter(isExerciseUnit).map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise()).collect(Collectors.toSet());
        lectureUnitsWithoutExercises.stream().map(LectureUnit::getCompetencies).forEach(competencies -> competencies.add(competency));
        exercises.stream().map(Exercise::getCompetencies).forEach(competencies -> competencies.add(competency));
        lectureUnitRepository.saveAll(lectureUnitsWithoutExercises);
        exerciseRepository.saveAll(exercises);
        competency.setLectureUnits(lectureUnitsToAdd);
    }

    /**
     * Removes competency from all lecture units.
     *
     * @param lectureUnits set of lecture units
     * @param competency   competency to remove
     */
    public void removeCompetency(Set<LectureUnit> lectureUnits, CourseCompetency competency) {
        lectureUnits.forEach(lectureUnit -> lectureUnit.getCompetencies().remove(competency));
        lectureUnitRepository.saveAll(lectureUnits);
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
}
