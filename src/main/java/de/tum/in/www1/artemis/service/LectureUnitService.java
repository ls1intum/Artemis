package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

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

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;
import de.tum.in.www1.artemis.domain.lecture.Slide;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitCompletionRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.repository.SlideRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;

@Profile(PROFILE_CORE)
@Service
public class LectureUnitService {

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureRepository lectureRepository;

    private final CompetencyRepository competencyRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final FileService fileService;

    private final SlideRepository slideRepository;

    private final ExerciseRepository exerciseRepository;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    public LectureUnitService(LectureUnitRepository lectureUnitRepository, LectureRepository lectureRepository, CompetencyRepository competencyRepository,
            LectureUnitCompletionRepository lectureUnitCompletionRepository, FileService fileService, SlideRepository slideRepository, ExerciseRepository exerciseRepository,
            Optional<PyrisWebhookService> pyrisWebhookService) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
        this.competencyRepository = competencyRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.fileService = fileService;
        this.slideRepository = slideRepository;
        this.exerciseRepository = exerciseRepository;
        this.pyrisWebhookService = pyrisWebhookService;
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
                    lectureUnitCompletionRepository.save(completion);
                }
                catch (DataIntegrityViolationException e) {
                    // In rare instances the completion status might already exist if this method runs in parallel.
                    // This fails the SQL unique constraint and throws an exception. We can safely ignore it.
                }
            }
        }
        else {
            // Delete the completion status for this lecture unit (if it exists)
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
            Set<Competency> competencies = lectureUnitToDelete.getCompetencies();
            competencyRepository.saveAll(competencies.stream().map(competency -> {
                competency = competencyRepository.findByIdWithLectureUnitsElseThrow(competency.getId());
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
    }

    /**
     * Link the competency to a set of lecture units (and exercises if it includes exercise units)
     *
     * @param competency           The competency to be linked
     * @param lectureUnitsToAdd    A set of lecture units to link to the specified competency
     * @param lectureUnitsToRemove A set of lecture units to unlink from the specified competency
     */
    public void linkLectureUnitsToCompetency(Competency competency, Set<LectureUnit> lectureUnitsToAdd, Set<LectureUnit> lectureUnitsToRemove) {
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
    public void removeCompetency(Set<LectureUnit> lectureUnits, Competency competency) {
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
}
