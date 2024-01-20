package de.tum.in.www1.artemis.service;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.*;

import javax.validation.constraints.NotNull;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.lecture.*;
import de.tum.in.www1.artemis.repository.*;

@Service
public class LectureUnitService {

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureRepository lectureRepository;

    private final CompetencyRepository competencyRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final FileService fileService;

    private final FilePathService filePathService;

    private final SlideRepository slideRepository;

    public LectureUnitService(LectureUnitRepository lectureUnitRepository, LectureRepository lectureRepository, CompetencyRepository competencyRepository,
            LectureUnitCompletionRepository lectureUnitCompletionRepository, FileService fileService, FilePathService filePathService, SlideRepository slideRepository) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
        this.competencyRepository = competencyRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.fileService = fileService;
        this.filePathService = filePathService;
        this.slideRepository = slideRepository;
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
                // Create a completion status for this lecture unit (only if it does not exist)
                LectureUnitCompletion completion = new LectureUnitCompletion();
                completion.setLectureUnit(lectureUnit);
                completion.setUser(user);
                completion.setCompletedAt(ZonedDateTime.now());
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
     * Deletes a lecture unit correctly in the database
     *
     * @param lectureUnit lecture unit to delete
     */
    public void removeLectureUnit(@NotNull LectureUnit lectureUnit) {
        LectureUnit lectureUnitToDelete = lectureUnitRepository.findByIdWithCompetenciesElseThrow(lectureUnit.getId());

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
            fileService.schedulePathForDeletion(filePathService.actualPathForPublicPathOrThrow(URI.create((attachmentUnit.getAttachment().getLink()))), 5);
            if (attachmentUnit.getSlides() != null && !attachmentUnit.getSlides().isEmpty()) {
                List<Slide> slides = attachmentUnit.getSlides();
                for (Slide slide : slides) {
                    fileService.schedulePathForDeletion(filePathService.actualPathForPublicPathOrThrow(URI.create(slide.getSlideImagePath())), 5);
                }
                slideRepository.deleteAll(slides);
            }
        }

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureUnitToDelete.getLecture().getId());
        // Creating a new list of lecture units without the one we want to remove
        lecture.getLectureUnits().removeIf(unit -> unit == null || unit.getId().equals(lectureUnitToDelete.getId()));
        lectureRepository.save(lecture);
    }
}
