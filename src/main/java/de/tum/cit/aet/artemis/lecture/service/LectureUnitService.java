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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import org.hibernate.Hibernate;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyRepositoryApi;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.api.LectureContentProcessingApi;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class LectureUnitService {

    private static final Logger log = LoggerFactory.getLogger(LectureUnitService.class);

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureRepository lectureRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    private final FileService fileService;

    private final Optional<IrisLectureApi> irisLectureApi;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    private final Optional<CompetencyRelationApi> competencyRelationApi;

    private final Optional<CompetencyRepositoryApi> competencyRepositoryApi;

    private final LectureContentProcessingApi contentProcessingApi;

    public LectureUnitService(LectureUnitRepository lectureUnitRepository, LectureRepository lectureRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository,
            FileService fileService, Optional<IrisLectureApi> irisLectureApi, Optional<CompetencyProgressApi> competencyProgressApi,
            Optional<CourseCompetencyApi> courseCompetencyApi, Optional<CompetencyRelationApi> competencyRelationApi, Optional<CompetencyRepositoryApi> competencyRepositoryApi,
            LectureContentProcessingApi contentProcessingApi) {
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
        this.fileService = fileService;
        this.irisLectureApi = irisLectureApi;
        this.courseCompetencyApi = courseCompetencyApi;
        this.competencyProgressApi = competencyProgressApi;
        this.competencyRelationApi = competencyRelationApi;
        this.competencyRepositoryApi = competencyRepositoryApi;
        this.contentProcessingApi = contentProcessingApi;
    }

    /**
     * Set the completion status of the lecture unit for the give user
     * If the user completed the unit and completion status already exists, nothing happens
     *
     * @param lectureUnit The lecture unit for which set the completion flag
     * @param user        The user that completed/uncompleted the lecture unit
     * @param completed   True if the lecture unit was completed, false otherwise
     */
    public void setLectureUnitCompletion(@NonNull LectureUnit lectureUnit, @NonNull User user, boolean completed) {
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
     * @param <T>          The type of the concrete lecture unit
     * @param lectureUnits List of all lecture units for which to set the completion flag
     * @param user         The user that completed/uncompleted the lecture unit
     * @param completed    True if the lecture unit was completed, false otherwise
     */
    public <T extends LectureUnit> void setCompletedForAllLectureUnits(List<T> lectureUnits, @NonNull User user, boolean completed) {
        var existingCompletion = lectureUnitCompletionRepository.findByLectureUnitsAndUserId(lectureUnits, user.getId());
        if (!completed) {
            lectureUnitCompletionRepository.deleteAll(existingCompletion);
            return;
        }
        // make lectureUnits modifiable
        List<LectureUnit> completedLectureUnits = new ArrayList<>(lectureUnits);

        // remove existing completions
        if (!existingCompletion.isEmpty()) {
            for (var completion : existingCompletion) {
                completedLectureUnits.remove(completion.getLectureUnit());
            }
        }

        var completions = completedLectureUnits.stream().map(unit -> createLectureUnitCompletion(unit, user)).toList();

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
     * Deletes a lecture unit correctly in the database.
     * Also cancels any ongoing content processing jobs (Nebula transcription, Pyris ingestion).
     * <p>
     * Note: The processing state is automatically deleted by database CASCADE DELETE
     * when the lecture unit is deleted.
     *
     * @param lectureUnit lecture unit to delete
     */
    public void removeLectureUnit(@NonNull LectureUnit lectureUnit) {
        LectureUnit lectureUnitToDelete = lectureUnitRepository.findByIdWithCompetenciesAndSlidesElseThrow(lectureUnit.getId());

        // Cancel any ongoing processing jobs (transcription, ingestion) on external services
        // Processing state deletion is handled by DB cascade when lecture unit is deleted
        contentProcessingApi.cancelProcessingIfActive(lectureUnit.getId());

        if (lectureUnitToDelete instanceof AttachmentVideoUnit attachmentVideoUnit) {
            if (attachmentVideoUnit.getAttachment() != null && attachmentVideoUnit.getAttachment().getLink() != null) {
                fileService.schedulePathForDeletion(
                        FilePathConverter.fileSystemPathForExternalUri(URI.create(attachmentVideoUnit.getAttachment().getLink()), FilePathType.ATTACHMENT_UNIT), 5);
            }
        }

        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureUnitToDelete.getLecture().getId());
        // Creating a new list of lecture units without the one we want to remove
        lecture.removeLectureUnitById(lectureUnit.getId());
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
     * Disconnects the competency exercise links from the exercise before the cycle is broken by the deserialization.
     * NOTE: this is a workaround for a Jackson/Hibernate issue with bidirectional relationships and lazy loading.
     * Ideally, we convert entities to DTOs before sending them to the client.
     *
     * @param lectureUnit The lecture unit to disconnect the competency links
     */
    public void disconnectCompetencyLectureUnitLinks(LectureUnit lectureUnit) {
        if (lectureUnit.getCompetencyLinks() != null && Hibernate.isInitialized(lectureUnit.getCompetencyLinks())) {
            lectureUnit.getCompetencyLinks().forEach(link -> {
                // avoid circular references
                link.setLectureUnit(null);
                link.getCompetency().setLectureUnitLinks(null);
            });
        }
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

    /**
     * Update the competency links of an existing text unit based on the provided DTO.
     * Supports removing links, updating weights of existing ones, and adding new links.
     * This method ensures that the managed entity's collection is updated correctly to avoid JPA issues and unnecessary database operations.
     * It makes sure to be Hibernate compliant by modifying the existing collection rather than replacing it.
     *
     * @param lectureUnitDto      the DTO (from the client) containing the new state of competency links (new, existing or removed ones)
     * @param existingLectureUnit the existing DB entity to update
     */
    public void updateCompetencyLinks(LectureUnitDTO lectureUnitDto, LectureUnit existingLectureUnit) {
        if (competencyRepositoryApi.isEmpty()) {
            return;
        }
        // TODO: think about optimizing this by loading all new competencies in a single query
        if (lectureUnitDto.competencyLinks() == null || lectureUnitDto.competencyLinks().isEmpty()) {
            // this handles the case where all competency links were removed
            existingLectureUnit.getCompetencyLinks().clear();
        }
        else {
            // 1) Existing links indexed by competency id
            Map<Long, CompetencyLectureUnitLink> existingLinksByCompetencyId = existingLectureUnit.getCompetencyLinks().stream()
                    .collect(Collectors.toMap(link -> link.getCompetency().getId(), Function.identity()));

            // 2) New state of links (reusing existing ones where possible)
            Set<CompetencyLectureUnitLink> updatedLinks = new HashSet<>();

            for (var dtoLink : lectureUnitDto.competencyLinks()) {
                long competencyId = dtoLink.competency().id();
                double weight = dtoLink.weight();

                var existingLink = existingLinksByCompetencyId.get(competencyId);
                if (existingLink != null) {
                    // reuse managed entity, just update the weight
                    existingLink.setWeight(weight);
                    updatedLinks.add(existingLink);
                }
                else {
                    // no existing link → create a new one
                    var competency = competencyRepositoryApi.get().findCompetencyOrPrerequisiteByIdElseThrow(competencyId);
                    var newLink = new CompetencyLectureUnitLink(competency, existingLectureUnit, weight);

                    updatedLinks.add(newLink);
                }
            }

            // 3) Replace the contents of the managed collection, NOT the collection itself
            var managedSet = existingLectureUnit.getCompetencyLinks();
            managedSet.clear();
            managedSet.addAll(updatedLinks);
        }
    }

}
