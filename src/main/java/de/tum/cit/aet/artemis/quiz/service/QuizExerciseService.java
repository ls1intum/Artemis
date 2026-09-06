package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.ZonedDateTime.now;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.ws.rs.BadRequestException;

import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.dto.QuizExerciseCalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.util.CalendarEventType;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileSystemLocation;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.CompetencyExerciseLinkService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizPointStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseForSearchDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseWithoutQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.UpdateQuizExerciseDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.AnswerOptionReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.DragAndDropQuestionReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.DragItemReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.DropLocationReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.MultipleChoiceQuestionReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.ShortAnswerMappingReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.ShortAnswerQuestionReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.ShortAnswerSolutionReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.ShortAnswerSpotReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizExerciseService extends QuizService<QuizExercise> {

    public static final String ENTITY_NAME = "QuizExercise";

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseService.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final ResultRepository resultRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    private final Optional<QuizScheduleService> quizScheduleService;

    private final QuizStatisticService quizStatisticService;

    private final QuizBatchService quizBatchService;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final ExerciseService exerciseService;

    private final UserRepository userRepository;

    private final QuizBatchRepository quizBatchRepository;

    private final ChannelService channelService;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<SlideApi> slideApi;

    private final CompetencyExerciseLinkService competencyExerciseLinkService;

    private final Optional<ExamDateApi> examDateApi;

    public QuizExerciseService(QuizExerciseRepository quizExerciseRepository, ResultRepository resultRepository, QuizSubmissionRepository quizSubmissionRepository,
            InstanceMessageSendService instanceMessageSendService, Optional<QuizScheduleService> quizScheduleService, QuizStatisticService quizStatisticService,
            QuizBatchService quizBatchService, ExerciseSpecificationService exerciseSpecificationService, ExerciseService exerciseService, UserRepository userRepository,
            QuizBatchRepository quizBatchRepository, ChannelService channelService, GroupNotificationScheduleService groupNotificationScheduleService,
            Optional<CompetencyProgressApi> competencyProgressApi, Optional<SlideApi> slideApi, CompetencyExerciseLinkService competencyExerciseLinkService,
            Optional<ExamDateApi> examDateApi) {
        super();
        this.quizExerciseRepository = quizExerciseRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.quizScheduleService = quizScheduleService;
        this.quizStatisticService = quizStatisticService;
        this.quizBatchService = quizBatchService;
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.exerciseService = exerciseService;
        this.userRepository = userRepository;
        this.quizBatchRepository = quizBatchRepository;
        this.channelService = channelService;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.competencyProgressApi = competencyProgressApi;
        this.slideApi = slideApi;
        this.competencyExerciseLinkService = competencyExerciseLinkService;
        this.examDateApi = examDateApi;
    }

    /**
     * Apply the base data of a QuizExerciseReEvaluateDTO to a QuizExercise. This includes title, includedInOverallScore and randomizeQuestionOrder.
     *
     * @param reEvaluateDTO        the DTO containing the new data
     * @param originalQuizExercise the original quiz exercise to apply the data to
     * @return true if a recalculation of the scores is necessary, false otherwise
     */
    private static boolean applyBaseQuizQuestionData(QuizExerciseReEvaluateDTO reEvaluateDTO, QuizExercise originalQuizExercise) {
        boolean recalculationNecessary = false;
        originalQuizExercise.setTitle(reEvaluateDTO.title());
        if (!originalQuizExercise.getIncludedInOverallScore().equals(reEvaluateDTO.includedInOverallScore())) {
            recalculationNecessary = true;
            originalQuizExercise.setIncludedInOverallScore(reEvaluateDTO.includedInOverallScore());
        }
        originalQuizExercise.setRandomizeQuestionOrder(reEvaluateDTO.randomizeQuestionOrder());
        return recalculationNecessary;
    }

    private static boolean shouldSetInvalid(boolean originalInvalid, boolean newInvalid, Long id, String itemType) {
        if (originalInvalid && !newInvalid) {
            throw new BadRequestException("The " + itemType + " with id " + id + " is marked as invalid and cannot be set to valid again");
        }
        return !originalInvalid && newInvalid;
    }

    private static boolean applyDropLocationsFromDTO(List<DropLocationReEvaluateDTO> dropLocationDTOs, List<DropLocation> originalDropLocations) {
        boolean recalculationNecessary = false;
        List<DropLocation> dropLocationsToRemove = new ArrayList<>();
        Map<Long, DropLocationReEvaluateDTO> dropLocationReEvaluateDTOMap = dropLocationDTOs.stream().collect(Collectors.toMap(DropLocationReEvaluateDTO::id, Function.identity()));
        for (DropLocation originalDropLocation : originalDropLocations) {
            DropLocationReEvaluateDTO dropLocationDTO = dropLocationReEvaluateDTOMap.get(originalDropLocation.getId());
            if (dropLocationDTO == null) {
                dropLocationsToRemove.add(originalDropLocation);
                recalculationNecessary = true;
            }
            else {
                if (shouldSetInvalid(originalDropLocation.isInvalid(), dropLocationDTO.invalid(), dropLocationDTO.id(), "drop location")) {
                    recalculationNecessary = true;
                    originalDropLocation.setInvalid(true);
                }
            }
        }
        originalDropLocations.removeAll(dropLocationsToRemove);
        return recalculationNecessary;
    }

    private static boolean applyDragItemsFromDTO(List<DragItemReEvaluateDTO> dragItemDTOs, List<DragItem> originalDragItems) {
        boolean recalculationNecessary = false;
        List<DragItem> dragItemsToRemove = new ArrayList<>();
        Map<Long, DragItemReEvaluateDTO> dragItemReEvaluateDTOMap = dragItemDTOs.stream().collect(Collectors.toMap(DragItemReEvaluateDTO::id, Function.identity()));
        for (DragItem originalDragItem : originalDragItems) {
            DragItemReEvaluateDTO dragItemDTO = dragItemReEvaluateDTOMap.get(originalDragItem.getId());
            if (dragItemDTO == null) {
                dragItemsToRemove.add(originalDragItem);
                recalculationNecessary = true;
            }
            else {
                if (shouldSetInvalid(originalDragItem.isInvalid(), dragItemDTO.invalid(), dragItemDTO.id(), "drag item")) {
                    recalculationNecessary = true;
                    originalDragItem.setInvalid(true);
                }
                if (dragItemDTO.text() == null && dragItemDTO.pictureFilePath() == null) {
                    throw new BadRequestException("The drag item with id " + dragItemDTO.id() + " has no text or picture");
                }
                if (dragItemDTO.text() != null && dragItemDTO.pictureFilePath() != null) {
                    throw new BadRequestException("The drag item with id " + dragItemDTO.id() + " has both text and picture");
                }
                originalDragItem.setText(dragItemDTO.text());
                originalDragItem.setPictureFilePath(dragItemDTO.pictureFilePath());
            }
        }
        originalDragItems.removeAll(dragItemsToRemove);
        return recalculationNecessary;
    }

    private static boolean applyDragAndDropMappingsFromDTO(DragAndDropQuestionReEvaluateDTO dndDTO, DragAndDropQuestion originalQuestion) {
        boolean recalculationNecessary = false;
        List<DragAndDropMapping> mappingsToRemove = new ArrayList<>();
        for (DragAndDropMapping originalMapping : originalQuestion.getCorrectMappings()) {
            boolean mappingExistsInDTO = dndDTO.correctMappings().stream()
                    .anyMatch(dto -> dto.dragItemId().equals(originalMapping.getDragItem().getId()) && dto.dropLocationId().equals(originalMapping.getDropLocation().getId()));
            if (!mappingExistsInDTO) {
                mappingsToRemove.add(originalMapping);
                recalculationNecessary = true;
            }
        }
        mappingsToRemove.forEach(originalQuestion::removeCorrectMapping);
        Set<DragAndDropMapping> existingMappings = originalQuestion.getCorrectMappings();
        for (var mappingDTO : dndDTO.correctMappings()) {
            boolean mappingExists = existingMappings.stream()
                    .anyMatch(mapping -> mapping.getDragItem().getId().equals(mappingDTO.dragItemId()) && mapping.getDropLocation().getId().equals(mappingDTO.dropLocationId()));
            if (!mappingExists) {
                DragItem dragItem = originalQuestion.getDragItems().stream().filter(item -> item.getId().equals(mappingDTO.dragItemId())).findFirst()
                        .orElseThrow(() -> new BadRequestException("The drag item with id " + mappingDTO.dragItemId() + " does not exist"));
                DropLocation dropLocation = originalQuestion.getDropLocations().stream().filter(location -> location.getId().equals(mappingDTO.dropLocationId())).findFirst()
                        .orElseThrow(() -> new BadRequestException("The drop location with id " + mappingDTO.dropLocationId() + " does not exist"));
                DragAndDropMapping newMapping = new DragAndDropMapping();
                newMapping.setDragItem(dragItem);
                newMapping.setDropLocation(dropLocation);
                originalQuestion.addCorrectMapping(newMapping);
                recalculationNecessary = true;
            }
        }
        return recalculationNecessary;
    }

    private static boolean applyDragAndDropQuestionFromDTO(DragAndDropQuestionReEvaluateDTO dndDTO, DragAndDropQuestion originalQuestion) {
        boolean recalculationNecessary = false;
        originalQuestion.setTitle(dndDTO.title());
        originalQuestion.setText(dndDTO.text());
        originalQuestion.setHint(dndDTO.hint());
        originalQuestion.setExplanation(dndDTO.explanation());
        if (!dndDTO.scoringType().equals(originalQuestion.getScoringType())) {
            recalculationNecessary = true;
            originalQuestion.setScoringType(dndDTO.scoringType());
        }
        originalQuestion.setRandomizeOrder(dndDTO.randomizeOrder());
        if (shouldSetInvalid(originalQuestion.isInvalid(), dndDTO.invalid(), dndDTO.id(), "drag and drop question")) {
            originalQuestion.setInvalid(Boolean.TRUE);
            recalculationNecessary = true;
        }
        recalculationNecessary = applyDropLocationsFromDTO(dndDTO.dropLocations(), originalQuestion.getDropLocations()) || recalculationNecessary;
        recalculationNecessary = applyDragItemsFromDTO(dndDTO.dragItems(), originalQuestion.getDragItems()) || recalculationNecessary;
        recalculationNecessary = applyDragAndDropMappingsFromDTO(dndDTO, originalQuestion) || recalculationNecessary;
        // Drop correct mappings orphaned by a drop-location / drag-item removal above (they resolve to null and are filtered on read, so the mapping apply above never sees them).
        originalQuestion.removeOrphanCorrectMappings();
        return recalculationNecessary;
    }

    private static boolean applyAnswerOptionsFromDTO(List<AnswerOptionReEvaluateDTO> answerOptionDTO, List<AnswerOption> originalAnswerOption) {
        boolean recalculationNecessary = false;
        List<AnswerOption> answerOptionsToRemove = new ArrayList<>();
        Map<Long, AnswerOptionReEvaluateDTO> answerOptionReEvaluateDTOMap = answerOptionDTO.stream().collect(Collectors.toMap(AnswerOptionReEvaluateDTO::id, Function.identity()));
        for (AnswerOption originalAnswerOptionItem : originalAnswerOption) {
            AnswerOptionReEvaluateDTO answerOptionDTOItem = answerOptionReEvaluateDTOMap.get(originalAnswerOptionItem.getId());
            if (answerOptionDTOItem == null) {
                answerOptionsToRemove.add(originalAnswerOptionItem);
                recalculationNecessary = true;
            }
            else {
                if (shouldSetInvalid(originalAnswerOptionItem.isInvalid(), answerOptionDTOItem.invalid(), answerOptionDTOItem.id(), "answer option")) {
                    recalculationNecessary = true;
                    originalAnswerOptionItem.setInvalid(Boolean.TRUE);
                }
                originalAnswerOptionItem.setText(answerOptionDTOItem.text());
                originalAnswerOptionItem.setHint(answerOptionDTOItem.hint());
                originalAnswerOptionItem.setExplanation(answerOptionDTOItem.explanation());
                if (originalAnswerOptionItem.isIsCorrect() != answerOptionDTOItem.isCorrect()) {
                    recalculationNecessary = true;
                    originalAnswerOptionItem.setIsCorrect(answerOptionDTOItem.isCorrect());
                }
            }
        }
        originalAnswerOption.removeAll(answerOptionsToRemove);
        return recalculationNecessary;
    }

    private static boolean applyMultipleChoiceQuestionFromDTO(MultipleChoiceQuestionReEvaluateDTO mcDTO, MultipleChoiceQuestion originalQuestion) {
        boolean recalculationNecessary = false;
        originalQuestion.setTitle(mcDTO.title());
        if (!mcDTO.scoringType().equals(originalQuestion.getScoringType())) {
            recalculationNecessary = true;
            originalQuestion.setScoringType(mcDTO.scoringType());
        }
        originalQuestion.setRandomizeOrder(mcDTO.randomizeOrder());
        if (shouldSetInvalid(originalQuestion.isInvalid(), mcDTO.invalid(), mcDTO.id(), "multiple choice question")) {
            originalQuestion.setInvalid(Boolean.TRUE);
            recalculationNecessary = true;
        }
        originalQuestion.setText(mcDTO.text());
        originalQuestion.setHint(mcDTO.hint());
        originalQuestion.setExplanation(mcDTO.explanation());
        recalculationNecessary = applyAnswerOptionsFromDTO(mcDTO.answerOptions(), originalQuestion.getAnswerOptions()) || recalculationNecessary;
        return recalculationNecessary;
    }

    /**
     * @return a map from DTO tempID to the newly created ShortAnswerSolution entities (for mapping resolution)
     */
    private static ApplyResult applyShortAnswerSolutionsFromDTOs(List<ShortAnswerSolutionReEvaluateDTO> solutionDTOs, ShortAnswerQuestion question) {
        List<ShortAnswerSolution> originalSolution = question.getSolutions();
        boolean recalculationNecessary = false;
        Map<Long, ShortAnswerSolution> tempIdToNewSolution = new HashMap<>();
        List<ShortAnswerSolution> solutionsToRemove = new ArrayList<>();
        // Validate every incoming solution up front, before the toMap below: each must carry exactly one of {id, tempID}, and neither ids nor tempIDs may repeat. A duplicate id
        // would otherwise blow up the toMap with an uncontrolled IllegalStateException; a duplicate tempID would silently overwrite the tempID -> solution mapping and leave an
        // orphan solution.
        Set<Long> seenSolutionIds = new HashSet<>();
        Set<Long> seenSolutionTempIds = new HashSet<>();
        for (ShortAnswerSolutionReEvaluateDTO solutionDTO : solutionDTOs) {
            if (solutionDTO.id() == null && solutionDTO.tempID() == null) {
                throw new BadRequestException("A new short answer solution must have a tempID to identify it");
            }
            if (solutionDTO.id() != null && solutionDTO.tempID() != null) {
                throw new BadRequestException("An existing short answer solution cannot have a tempID");
            }
            if (solutionDTO.id() != null && !seenSolutionIds.add(solutionDTO.id())) {
                throw new BadRequestException("Duplicate short answer solution id " + solutionDTO.id());
            }
            if (solutionDTO.tempID() != null && !seenSolutionTempIds.add(solutionDTO.tempID())) {
                throw new BadRequestException("Duplicate short answer solution tempID " + solutionDTO.tempID());
            }
        }
        // ids of the solutions that already exist on the question; a DTO carrying an id not in this set is a newly added solution (client-minted, question-scoped id)
        Set<Long> originalSolutionIds = originalSolution.stream().map(ShortAnswerSolution::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        // Only map existing solutions (id != null); new solutions (id=null with tempID, or an id not in originalSolutionIds) are handled separately below
        Map<Long, ShortAnswerSolutionReEvaluateDTO> solutionReEvaluateDTOMap = solutionDTOs.stream().filter(dto -> dto.id() != null)
                .collect(Collectors.toMap(ShortAnswerSolutionReEvaluateDTO::id, Function.identity()));
        for (ShortAnswerSolution originalSolutionItem : originalSolution) {
            ShortAnswerSolutionReEvaluateDTO solutionDTOItem = solutionReEvaluateDTOMap.get(originalSolutionItem.getId());
            if (solutionDTOItem == null) {
                solutionsToRemove.add(originalSolutionItem);
                recalculationNecessary = true;
            }
            else {
                if (shouldSetInvalid(originalSolutionItem.isInvalid(), solutionDTOItem.invalid(), solutionDTOItem.id(), "short answer solution")) {
                    recalculationNecessary = true;
                    originalSolutionItem.setInvalid(Boolean.TRUE);
                }
            }
        }
        originalSolution.removeAll(solutionsToRemove);
        // First add the new solutions that already carry a client-minted, question-scoped id (their correct mappings resolve them by that id). Adding these before minting the
        // tempID solutions below guarantees the server-minted ids (max+1) cannot collide with a client-provided one; ids were already validated unique above.
        for (ShortAnswerSolutionReEvaluateDTO solutionDTO : solutionDTOs) {
            if (solutionDTO.id() != null && !originalSolutionIds.contains(solutionDTO.id())) {
                ShortAnswerSolution newSolution = new ShortAnswerSolution();
                newSolution.setId(solutionDTO.id());
                newSolution.setText(solutionDTO.text());
                newSolution.setInvalid(solutionDTO.invalid());
                originalSolution.add(newSolution);
                recalculationNecessary = true;
            }
        }
        // Then mint the tempID solutions server-side; addSolution's max+1 now accounts for any client-provided ids added above, so the ids never collide.
        for (ShortAnswerSolutionReEvaluateDTO solutionDTO : solutionDTOs) {
            if (solutionDTO.tempID() != null) {
                ShortAnswerSolution newSolution = new ShortAnswerSolution();
                newSolution.setText(solutionDTO.text());
                newSolution.setInvalid(solutionDTO.invalid());
                question.addSolution(newSolution);
                tempIdToNewSolution.put(solutionDTO.tempID(), newSolution);
                recalculationNecessary = true;
            }
        }
        return new ApplyResult(recalculationNecessary, tempIdToNewSolution);
    }

    private record ApplyResult(boolean recalculationNecessary, Map<Long, ShortAnswerSolution> tempIdToNewSolution) {
    }

    private static boolean applyShortAnswerSpotsFromDTOs(List<ShortAnswerSpotReEvaluateDTO> spotDTOs, List<ShortAnswerSpot> originalSpots) {
        boolean recalculationNecessary = false;
        List<ShortAnswerSpot> spotsToRemove = new ArrayList<>();
        Map<Long, ShortAnswerSpotReEvaluateDTO> spotReEvaluateDTOMap = spotDTOs.stream().collect(Collectors.toMap(ShortAnswerSpotReEvaluateDTO::id, Function.identity()));
        for (ShortAnswerSpot originalSpot : originalSpots) {
            ShortAnswerSpotReEvaluateDTO spotDTO = spotReEvaluateDTOMap.get(originalSpot.getId());
            if (spotDTO == null) {
                spotsToRemove.add(originalSpot);
                recalculationNecessary = true;
            }
            else {
                if (shouldSetInvalid(originalSpot.isInvalid(), spotDTO.invalid(), spotDTO.id(), "short answer spot")) {
                    recalculationNecessary = true;
                    originalSpot.setInvalid(Boolean.TRUE);
                }
            }
        }
        originalSpots.removeAll(spotsToRemove);
        return recalculationNecessary;
    }

    private static boolean addNewShortAnswerMappingFromDTO(ShortAnswerQuestion originalQuestion, ShortAnswerMappingReEvaluateDTO mappingDTO,
            Set<ShortAnswerMapping> existingMappings, Map<Long, ShortAnswerSolution> tempIdToNewSolution) {
        if (mappingDTO.solutionId() == null && mappingDTO.solutionTempID() == null) {
            throw new BadRequestException("The short answer mapping for spot id " + mappingDTO.spotId() + " has no solutionId or solutionTempID");
        }
        if (mappingDTO.solutionId() != null && mappingDTO.solutionTempID() != null) {
            throw new BadRequestException("The short answer mapping for spot id " + mappingDTO.spotId() + " has both solutionId and solutionTempID");
        }
        boolean mappingExists;
        if (mappingDTO.solutionTempID() != null) {
            // For new solutions (identified by tempID), check if a mapping to this new solution already exists
            ShortAnswerSolution newSolution = tempIdToNewSolution.get(mappingDTO.solutionTempID());
            mappingExists = newSolution != null
                    && existingMappings.stream().anyMatch(mapping -> mapping.getSpot().getId().equals(mappingDTO.spotId()) && mapping.getSolution() == newSolution);
        }
        else {
            mappingExists = existingMappings.stream()
                    .anyMatch(mapping -> mapping.getSpot().getId().equals(mappingDTO.spotId()) && mapping.getSolution().getId().equals(mappingDTO.solutionId()));
        }
        if (mappingExists) {
            return false;
        }
        ShortAnswerSpot spot = originalQuestion.getSpots().stream().filter(item -> item.getId().equals(mappingDTO.spotId())).findFirst()
                .orElseThrow(() -> new BadRequestException("The short answer spot with id " + mappingDTO.spotId() + " does not exist"));
        ShortAnswerSolution solution;
        if (mappingDTO.solutionTempID() != null) {
            // Look up the new solution from the tempID map (built during applyShortAnswerSolutionsFromDTOs)
            solution = tempIdToNewSolution.get(mappingDTO.solutionTempID());
            if (solution == null) {
                throw new BadRequestException("The short answer solution with tempID " + mappingDTO.solutionTempID() + " does not exist");
            }
        }
        else {
            solution = originalQuestion.getSolutions().stream().filter(item -> item.getId().equals(mappingDTO.solutionId())).findFirst()
                    .orElseThrow(() -> new BadRequestException("The short answer solution with id " + mappingDTO.solutionId() + " does not exist"));
        }
        ShortAnswerMapping newMapping = new ShortAnswerMapping();
        newMapping.setSpot(spot);
        newMapping.setSolution(solution);
        originalQuestion.addCorrectMapping(newMapping);
        return true;
    }

    private static boolean applyShortAnswerMappingFromDTOs(ShortAnswerQuestionReEvaluateDTO saDTO, ShortAnswerQuestion originalQuestion,
            Map<Long, ShortAnswerSolution> tempIdToNewSolution) {
        boolean recalculationNecessary = false;
        List<ShortAnswerMapping> mappingsToRemove = new ArrayList<>();
        for (ShortAnswerMapping originalMapping : originalQuestion.getCorrectMappings()) {
            boolean mappingExistsInDTO = saDTO.correctMappings().stream()
                    .anyMatch(dto -> dto.spotId().equals(originalMapping.getSpot().getId()) && Objects.equals(dto.solutionId(), originalMapping.getSolution().getId()));
            if (!mappingExistsInDTO) {
                mappingsToRemove.add(originalMapping);
                recalculationNecessary = true;
            }
        }
        mappingsToRemove.forEach(originalQuestion::removeCorrectMapping);
        Set<ShortAnswerMapping> existingMappings = originalQuestion.getCorrectMappings();
        for (var mappingDTO : saDTO.correctMappings()) {
            if (addNewShortAnswerMappingFromDTO(originalQuestion, mappingDTO, existingMappings, tempIdToNewSolution)) {
                recalculationNecessary = true;
            }
        }
        return recalculationNecessary;
    }

    private static boolean applyShortAnswerQuestionFromDTO(ShortAnswerQuestionReEvaluateDTO shortAnswerQuestionDTO, ShortAnswerQuestion originalQuestion) {
        boolean recalculationNecessary = false;
        originalQuestion.setTitle(shortAnswerQuestionDTO.title());
        originalQuestion.setText(shortAnswerQuestionDTO.text());
        if (!shortAnswerQuestionDTO.scoringType().equals(originalQuestion.getScoringType())) {
            recalculationNecessary = true;
            originalQuestion.setScoringType(shortAnswerQuestionDTO.scoringType());
        }
        originalQuestion.setRandomizeOrder(shortAnswerQuestionDTO.randomizeOrder());
        if (shouldSetInvalid(originalQuestion.isInvalid(), shortAnswerQuestionDTO.invalid(), shortAnswerQuestionDTO.id(), "short answer question")) {
            originalQuestion.setInvalid(Boolean.TRUE);
            recalculationNecessary = true;
        }
        if (!originalQuestion.getSimilarityValue().equals(shortAnswerQuestionDTO.similarityValue())) {
            recalculationNecessary = true;
            originalQuestion.setSimilarityValue(shortAnswerQuestionDTO.similarityValue());
        }
        if (!originalQuestion.getMatchLetterCase().equals(shortAnswerQuestionDTO.matchLetterCase())) {
            recalculationNecessary = true;
            originalQuestion.setMatchLetterCase(shortAnswerQuestionDTO.matchLetterCase());
        }

        recalculationNecessary = applyShortAnswerSpotsFromDTOs(shortAnswerQuestionDTO.spots(), originalQuestion.getSpots()) || recalculationNecessary;
        ApplyResult solutionResult = applyShortAnswerSolutionsFromDTOs(shortAnswerQuestionDTO.solutions(), originalQuestion);
        recalculationNecessary = solutionResult.recalculationNecessary() || recalculationNecessary;
        recalculationNecessary = applyShortAnswerMappingFromDTOs(shortAnswerQuestionDTO, originalQuestion, solutionResult.tempIdToNewSolution()) || recalculationNecessary;
        // Drop correct mappings orphaned by a spot / solution removal above (they resolve to null and are filtered on read, so the mapping apply above never sees them).
        originalQuestion.removeOrphanCorrectMappings();

        return recalculationNecessary;
    }

    private static boolean applyQuizQuestionsFromDTOAndCheckIfChanged(QuizExerciseReEvaluateDTO reEvaluateDTO, QuizExercise originalQuizExercise) {
        List<QuizQuestion> newQuestions = new ArrayList<>();
        boolean questionsChanged = false;
        for (var questionDTO : reEvaluateDTO.quizQuestions()) {
            switch (questionDTO) {
                case DragAndDropQuestionReEvaluateDTO dragAndDropQuestionReEvaluateDTO -> {
                    DragAndDropQuestion originalQuestion = (DragAndDropQuestion) originalQuizExercise.getQuizQuestions().stream()
                            .filter(q -> q.getId().equals(dragAndDropQuestionReEvaluateDTO.id())).findFirst()
                            .orElseThrow(() -> new BadRequestException("The drag and drop question with id " + dragAndDropQuestionReEvaluateDTO.id() + " does not exist"));
                    questionsChanged = applyDragAndDropQuestionFromDTO(dragAndDropQuestionReEvaluateDTO, originalQuestion) || questionsChanged;
                    newQuestions.add(originalQuestion);
                }
                case ShortAnswerQuestionReEvaluateDTO shortAnswerQuestionReEvaluateDTO -> {
                    ShortAnswerQuestion originalQuestion = (ShortAnswerQuestion) originalQuizExercise.getQuizQuestions().stream()
                            .filter(q -> q.getId().equals(shortAnswerQuestionReEvaluateDTO.id())).findFirst()
                            .orElseThrow(() -> new BadRequestException("The short answer question with id " + shortAnswerQuestionReEvaluateDTO.id() + " does not exist"));
                    questionsChanged = applyShortAnswerQuestionFromDTO(shortAnswerQuestionReEvaluateDTO, originalQuestion) || questionsChanged;
                    newQuestions.add(originalQuestion);
                }
                case MultipleChoiceQuestionReEvaluateDTO multipleChoiceQuestionReEvaluateDTO -> {
                    MultipleChoiceQuestion originalQuestion = (MultipleChoiceQuestion) originalQuizExercise.getQuizQuestions().stream()
                            .filter(q -> q.getId().equals(multipleChoiceQuestionReEvaluateDTO.id())).findFirst()
                            .orElseThrow(() -> new BadRequestException("The multiple choice question with id " + multipleChoiceQuestionReEvaluateDTO.id() + " does not exist"));
                    questionsChanged = applyMultipleChoiceQuestionFromDTO(multipleChoiceQuestionReEvaluateDTO, originalQuestion) || questionsChanged;
                    newQuestions.add(originalQuestion);
                }
            }
        }
        if (originalQuizExercise.getQuizQuestions().size() != newQuestions.size()) {
            questionsChanged = true;
        }
        originalQuizExercise.setQuizQuestions(newQuestions);
        return questionsChanged;
    }

    /**
     * adjust existing results if an answer or and question was deleted and recalculate the scores
     *
     * @param quizExercise the changed quizExercise.
     */
    private void updateResultsOnQuizChanges(QuizExercise quizExercise) {
        // change existing results if an answer or and question was deleted
        List<Result> results = resultRepository.findByExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        log.info("Found {} results to update for quiz re-evaluate", results.size());
        List<QuizSubmission> submissions = new ArrayList<>();
        for (Result result : results) {

            Set<SubmittedAnswer> submittedAnswersToDelete = new HashSet<>();
            QuizSubmission quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(result.getSubmission().getId());
            result.setSubmission(quizSubmission);

            for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                // Delete all references to question and question-elements if the question was changed
                submittedAnswer.checkAndDeleteReferences(quizExercise);
                if (!quizExercise.getQuizQuestions().contains(submittedAnswer.getQuizQuestion())) {
                    submittedAnswersToDelete.add(submittedAnswer);
                }
            }
            quizSubmission.getSubmittedAnswers().removeAll(submittedAnswersToDelete);

            // recalculate existing score
            quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
            // update Successful-Flag in Result
            StudentParticipation studentParticipation = (StudentParticipation) result.getSubmission().getParticipation();
            studentParticipation.setExercise(quizExercise);
            result.evaluateQuizSubmission(quizExercise);

            submissions.add(quizSubmission);
        }
        // save the updated submissions and results
        quizSubmissionRepository.saveAll(submissions);
        resultRepository.saveAll(results);
        log.info("{} results have been updated successfully for quiz re-evaluate", results.size());
    }

    /**
     * @param quizExerciseDTO      the changed quiz exercise from the client
     * @param originalQuizExercise the original quiz exercise (with statistics)
     * @param files                the files that were uploaded
     * @return the updated quiz exercise with the changed statistics
     */
    public QuizExercise reEvaluate(QuizExerciseReEvaluateDTO quizExerciseDTO, QuizExercise originalQuizExercise, @NonNull List<MultipartFile> files) throws IOException {
        Map<FilePathType, Set<String>> oldPaths = getAllPathsFromDragAndDropQuestionsOfExercise(originalQuizExercise);
        boolean questionsChanged = applyBaseQuizQuestionData(quizExerciseDTO, originalQuizExercise);
        questionsChanged = applyQuizQuestionsFromDTOAndCheckIfChanged(quizExerciseDTO, originalQuizExercise) || questionsChanged;
        validateQuizExerciseFiles(originalQuizExercise, files, oldPaths);
        Map<FilePathType, Set<String>> filesToRemove = new HashMap<>(oldPaths);

        deleteOldFiles(originalQuizExercise, files, oldPaths, filesToRemove);

        originalQuizExercise.setMaxPoints(originalQuizExercise.getOverallQuizPoints());
        originalQuizExercise.reconnectJSONIgnoreAttributes();
        updateResultsOnQuizChanges(originalQuizExercise);
        QuizExercise savedQuizExercise = save(originalQuizExercise);

        if (questionsChanged) {
            savedQuizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(savedQuizExercise.getId());
            quizStatisticService.recalculateStatistics(savedQuizExercise);
        }
        return quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(savedQuizExercise.getId());
    }

    /**
     * Reset a QuizExercise to its original state, delete statistics and cleanup the schedule service.
     *
     * @param exerciseId id of the exercise to reset
     */
    public void resetExercise(Long exerciseId) {
        // fetch exercise again to make sure we have an updated version
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(exerciseId);

        if (!quizExercise.isExamExercise()) {
            // do not set the release date of exam exercises
            quizExercise.setReleaseDate(ZonedDateTime.now());
        }
        quizExercise.setDueDate(null);
        quizExercise.setQuizBatches(Set.of());

        resetInvalidQuestions(quizExercise);

        QuizExercise savedQuizExercise = save(quizExercise);

        // in case the quiz has not yet started or the quiz is currently running, we have to clean up
        instanceMessageSendService.sendQuizExerciseStartSchedule(savedQuizExercise.getId());

        // clean up the statistics
        quizStatisticService.recalculateStatistics(savedQuizExercise);
    }

    /**
     * Cancels the scheduled start of a quiz exercise both locally and across the cluster.
     * <p>
     * On nodes that run the scheduling profile the local scheduler entry is cleared synchronously
     * so the calling node immediately observes the cancellation. The cluster-wide message is
     * always published so all other nodes catch up regardless of profile.
     *
     * @param quizExerciseId the id of the quiz exercise whose scheduled start should be canceled
     */
    public void cancelScheduledQuiz(Long quizExerciseId) {
        quizScheduleService.ifPresent(service -> service.cancelScheduledQuizStart(quizExerciseId));
        instanceMessageSendService.sendQuizExerciseStartCancel(quizExerciseId);
    }

    /**
     * Update a QuizExercise so that it ends at a specific date and moves the start date of the batches as required.
     * Does not save the quiz — callers that want to persist the change are expected to either write the scalar fields
     * explicitly (e.g. via {@link de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository#updateDueDate} and
     * {@link de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository#clampBatchStartTimesForEndNow}, as the REST
     * lifecycle handler does to avoid the full-graph cascade) or follow up with a full quiz update endpoint (as the
     * re-evaluation tests do).
     *
     * @param quizExercise The quiz to end
     */
    public void endQuiz(QuizExercise quizExercise) {
        quizExercise.setDueDate(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(quizBatchService.quizBatchStartDate(quizExercise, batch.getStartTime())));
    }

    /**
     * Search for all quiz exercises fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in courses for exercises
     * @param isExamFilter   Whether to search in exams for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<QuizExerciseForSearchDTO> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final Boolean isCourseFilter,
            final Boolean isExamFilter, final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(List.of(), 0);
        }
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        final var searchTerm = search.getSearchTerm();
        Specification<QuizExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<QuizExercise> exercisePage = quizExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent().stream().map(QuizExerciseForSearchDTO::of).toList(), exercisePage.getTotalPages());
    }

    /**
     * Verifies that for DragAndDropQuestions all files are present and valid. Saves the files and updates the
     * exercise accordingly.
     *
     * @param quizExercise the quiz exercise to create
     * @param files        the provided files
     */
    public void handleDndQuizFileCreation(QuizExercise quizExercise, List<MultipartFile> files) throws IOException {
        List<MultipartFile> nullsafeFiles = files == null ? new ArrayList<>() : files;
        validateQuizExerciseFiles(quizExercise, nullsafeFiles);
        Map<String, MultipartFile> fileMap = nullsafeFiles.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, file -> file));

        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (dragAndDropQuestion.getBackgroundFilePath() != null) {
                    handleDndBackgroundForCreation(dragAndDropQuestion, fileMap);
                }
                handleDndQuizDragItemsCreation(dragAndDropQuestion, fileMap);
            }
        }
    }

    /**
     * Handles the background file for a DragAndDropQuestion during creation. If the file already exists in the file system, it copies it to a new location.
     * This logic is necessary to handle the case where a DragAndDropQuestion is created based on an existing one (e.g. via import).
     *
     * @param question the DragAndDropQuestion
     * @param fileMap  the map of provided files
     * @throws IOException if file operations fail
     */
    public void handleDndBackgroundForCreation(DragAndDropQuestion question, Map<String, MultipartFile> fileMap) throws IOException {
        String path = question.getBackgroundFilePath();
        FilePathType type = FilePathType.DRAG_AND_DROP_BACKGROUND;
        Path basePath = FilePathConverter.getDragAndDropBackgroundFilePath();

        Path oldPath = new FileSystemLocation.DragAndDropBackground(path).path();
        if (Files.exists(oldPath)) {
            Path newPath = FileUtil.copyExistingFileToTarget(oldPath, basePath, type);
            if (newPath == null) {
                throw new IOException("Failed to copy existing drag and drop background file to new location for path: " + oldPath);
            }
            question.setBackgroundFilePath(newPath.getFileName().toString());
        }
        else {
            saveDndQuestionBackground(question, fileMap);
        }
    }

    /**
     * Handles the drag items for a DragAndDropQuestion during creation. If the files already exist in the file system, it copies them to new locations.
     *
     * @param dragAndDropQuestion the DragAndDropQuestion
     * @param fileMap             the map of provided files
     * @throws IOException if file operations fail
     */
    private void handleDndQuizDragItemsCreation(DragAndDropQuestion dragAndDropQuestion, Map<String, MultipartFile> fileMap) throws IOException {
        FilePathType type = FilePathType.DRAG_ITEM;
        Path basePath = FilePathConverter.getDragItemFilePath();
        // A drag item needs an id of its own before it is stored, because the client addresses its picture by it. QuizService.save() would mint the missing ones a moment later
        // anyway.
        dragAndDropQuestion.assignMissingComponentIds();

        for (var dragItem : dragAndDropQuestion.getDragItems()) {
            if (dragItem.getPictureFilePath() != null) {
                String path = dragItem.getPictureFilePath();
                Path oldPath = new FileSystemLocation.DragItem(path).path();
                if (Files.exists(oldPath)) {
                    Path newPath = FileUtil.copyExistingFileToTarget(oldPath, basePath, type);
                    if (newPath == null) {
                        throw new IOException("Failed to copy existing drag item file to new location for path: " + oldPath);
                    }
                    dragItem.setPictureFilePath(newPath.getFileName().toString());
                }
                else {
                    saveDndDragItemPicture(dragItem, fileMap);
                }
            }
        }
    }

    /**
     * Verifies that for DragAndDropQuestions all files are present and valid. Saves the files and updates the
     * exercise accordingly.
     * Ignores unchanged paths and removes deleted background images.
     *
     * @param updatedExercise  the updated quiz exercise
     * @param originalExercise the original quiz exercise
     * @param files            the provided files
     */
    public void handleDndQuizFileUpdates(QuizExercise updatedExercise, QuizExercise originalExercise, @NonNull List<MultipartFile> files) throws IOException {
        Map<FilePathType, Set<String>> oldPaths = getAllPathsFromDragAndDropQuestionsOfExercise(originalExercise);
        validateQuizExerciseFiles(updatedExercise, files, oldPaths);
        Map<FilePathType, Set<String>> filesToRemove = new HashMap<>(oldPaths);

        deleteOldFiles(updatedExercise, files, oldPaths, filesToRemove);
    }

    private void deleteOldFiles(QuizExercise quizExercise, @NonNull List<MultipartFile> files, Map<FilePathType, Set<String>> oldPaths,
            Map<FilePathType, Set<String>> filesToRemove) throws IOException {
        Map<String, MultipartFile> fileMap = files.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, file -> file));
        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                handleDndQuestionUpdate(dragAndDropQuestion, oldPaths, filesToRemove, fileMap);
            }
        }
        var allFilesToRemoveMerged = filesToRemove.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(path -> dragAndDropImageLocation(path, entry.getKey())))
                .filter(Objects::nonNull).toList();
        FileUtil.deleteFiles(allFilesToRemoveMerged);
    }

    /**
     * Deletes all drag-and-drop image files (question background images and drag-item pictures) of the given quiz exercise from the file system.
     * <p>
     * These files used to be removed by the {@code @PostRemove} lifecycle callbacks on {@code DragAndDropQuestion}/{@code DragItem}. Now that drag-and-drop content lives inside
     * the
     * question's JSON {@code content} column and is no longer made up of JPA entities, the cleanup must be triggered explicitly from every quiz deletion entry point. It is invoked
     * from the shared {@link de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService#delete} path so that course, exam, and exercise-group deletions clean up the images
     * too,
     * not only the direct REST deletion.
     *
     * @param quizExerciseId the id of the quiz exercise whose drag-and-drop images should be deleted
     */
    public void deleteDragAndDropImages(long quizExerciseId) {
        FileUtil.deleteFiles(collectDragAndDropImagePaths(quizExerciseId));
    }

    /**
     * Collects the file-system paths of all drag-and-drop image files (question background images and drag-item pictures) of the given quiz exercise, without deleting anything.
     * <p>
     * The paths live inside the question's JSON {@code content}, so they are no longer readable once the exercise row is gone. Callers that delete the exercise must therefore
     * collect the paths first and delete the files only after the database deletion succeeded — otherwise a failure in between leaves a quiz whose images are already gone.
     *
     * @param quizExerciseId the id of the quiz exercise whose drag-and-drop image paths should be collected
     * @return the resolvable file-system paths of the exercise's drag-and-drop images
     */
    public List<Path> collectDragAndDropImagePaths(long quizExerciseId) {
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExerciseId);
        Map<FilePathType, Set<String>> imagePaths = getAllPathsFromDragAndDropQuestionsOfExercise(quizExercise);
        return imagePaths.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(path -> resolveFileSystemPathForDeletion(path, entry.getKey())))
                .filter(Objects::nonNull).toList();
    }

    /**
     * Resolves a stored external file URI to its file-system path for deletion, returning {@code null} (and logging) instead of throwing when the path cannot be parsed, so a
     * single malformed path does not abort the deletion of the remaining files.
     *
     * @param pathString   the stored external file URI
     * @param filePathType the type of file the path refers to
     * @return the resolved file-system path, or {@code null} if it could not be resolved
     */
    private Path resolveFileSystemPathForDeletion(String pathString, FilePathType filePathType) {
        try {
            return dragAndDropImageLocation(pathString, filePathType);
        }
        catch (IllegalArgumentException e) {
            log.warn("Could not resolve file {} for deletion", pathString);
            return null;
        }
    }

    /**
     * The file system location of a drag and drop image, which is either a question background or a drag item picture.
     * <p>
     * Both types keep every file of their kind in one directory, so the location needs nothing but the filename. The ids the stored value carries are the ones its URL needs, not
     * the ones the directory does, which is why nothing here reads them.
     *
     * @param storedPath   the stored value of the image
     * @param filePathType the type of image, which selects the directory
     * @return the location of the image on disk
     */
    private static Path dragAndDropImageLocation(String storedPath, FilePathType filePathType) {
        return switch (filePathType) {
            case DRAG_AND_DROP_BACKGROUND -> new FileSystemLocation.DragAndDropBackground(storedPath).path();
            case DRAG_ITEM -> new FileSystemLocation.DragItem(storedPath).path();
            default -> throw new IllegalArgumentException("A quiz exercise holds no drag and drop image of type " + filePathType);
        };
    }

    private Map<FilePathType, Set<String>> getAllPathsFromDragAndDropQuestionsOfExercise(QuizExercise quizExercise) {
        Map<FilePathType, Set<String>> paths = new HashMap<>();
        paths.put(FilePathType.DRAG_AND_DROP_BACKGROUND, new HashSet<>());
        paths.put(FilePathType.DRAG_ITEM, new HashSet<>());

        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (dragAndDropQuestion.getBackgroundFilePath() != null) {
                    paths.get(FilePathType.DRAG_AND_DROP_BACKGROUND).add(dragAndDropQuestion.getBackgroundFilePath());
                }
                Set<String> dragItemPaths = dragAndDropQuestion.getDragItems().stream().map(DragItem::getPictureFilePath).filter(Objects::nonNull).collect(Collectors.toSet());
                paths.get(FilePathType.DRAG_ITEM).addAll(dragItemPaths);
            }
        }

        return paths;
    }

    private void handleDndQuestionUpdate(DragAndDropQuestion dragAndDropQuestion, Map<FilePathType, Set<String>> oldPaths, Map<FilePathType, Set<String>> filesToRemove,
            Map<String, MultipartFile> fileMap) throws IOException {
        // A drag item added by this update has no id yet, and the client addresses its picture by that id, so mint the missing ids before writing any file. QuizService.save()
        // would mint them a moment later anyway.
        dragAndDropQuestion.assignMissingComponentIds();
        String newBackgroundPath = dragAndDropQuestion.getBackgroundFilePath();

        // Don't do anything if the path is null because it's getting removed
        if (newBackgroundPath != null) {
            Set<String> oldBackgroundPaths = oldPaths.get(FilePathType.DRAG_AND_DROP_BACKGROUND);
            if (oldBackgroundPaths.contains(newBackgroundPath)) {
                // Path didn't change
                filesToRemove.get(FilePathType.DRAG_AND_DROP_BACKGROUND).remove(newBackgroundPath);
            }
            else {
                // Path changed and file was provided
                saveDndQuestionBackground(dragAndDropQuestion, fileMap);
            }
        }

        for (var dragItem : dragAndDropQuestion.getDragItems()) {
            String newDragItemPath = dragItem.getPictureFilePath();
            Set<String> dragItemOldPaths = oldPaths.get(FilePathType.DRAG_ITEM);
            if (newDragItemPath != null && !dragItemOldPaths.contains(newDragItemPath)) {
                // Path changed and file was provided
                saveDndDragItemPicture(dragItem, fileMap);
            }
            else if (newDragItemPath != null) {
                filesToRemove.get(FilePathType.DRAG_ITEM).remove(newDragItemPath);
            }
        }
    }

    /**
     * Verifies that the provided files match the provided filenames in the exercise entity.
     *
     * @param quizExercise  the quiz exercise to validate
     * @param providedFiles the provided files to validate
     */
    public void validateQuizExerciseFiles(QuizExercise quizExercise, @NonNull List<MultipartFile> providedFiles) {
        validateQuizExerciseFiles(quizExercise, providedFiles, null);
    }

    /**
     * Verifies that the provided files match the provided filenames in the exercise entity.
     *
     * @param quizExercise  the quiz exercise to validate
     * @param providedFiles the provided files to validate
     * @param oldPaths      Optional map of paths that already existed in the original exercise (should not require new files)
     */
    public void validateQuizExerciseFiles(QuizExercise quizExercise, @NonNull List<MultipartFile> providedFiles, @Nullable Map<FilePathType, Set<String>> oldPaths) {
        long fileCount = providedFiles.size();

        Map<FilePathType, Set<String>> exerciseFilePathsMap = getAllPathsFromDragAndDropQuestionsOfExercise(quizExercise);
        Map<FilePathType, Set<String>> newFilePathsMap = new HashMap<>();

        for (Map.Entry<FilePathType, Set<String>> entry : exerciseFilePathsMap.entrySet()) {
            FilePathType type = entry.getKey();
            Set<String> paths = entry.getValue();
            for (String path : paths) {
                // The value names a file inside the one directory its type stores files in, so rejecting a traversal is all the checking the value needs. Which directory that is
                // follows from the type, not from the value, so there is no prefix left to verify.
                FileUtil.sanitizeFilePathByCheckingForInvalidCharactersElseThrow(path);

                // A path is "new" if it doesn't exist on disk AND it wasn't in the original exercise
                Set<String> oldPathsForType = oldPaths != null ? oldPaths.getOrDefault(type, Set.of()) : Set.of();
                Set<String> newPaths = paths.stream().filter(filePath -> !Files.exists(dragAndDropImageLocation(filePath, type)))
                        .filter(filePath -> !oldPathsForType.contains(filePath)).collect(Collectors.toSet());

                if (!newPaths.isEmpty()) {
                    newFilePathsMap.put(type, newPaths);
                }
            }
        }

        int totalNewPathsCount = newFilePathsMap.values().stream().mapToInt(Set::size).sum();

        if (totalNewPathsCount != fileCount) {
            throw new BadRequestAlertException("Number of files does not match number of new drag items and " + "backgrounds", ENTITY_NAME, null);
        }

        Set<String> allNewFilePaths = newFilePathsMap.values().stream().flatMap(Set::stream).collect(Collectors.toSet());

        Set<String> providedFileNames = providedFiles.stream().map(MultipartFile::getOriginalFilename).collect(Collectors.toSet());

        if (!allNewFilePaths.equals(providedFileNames)) {
            throw new BadRequestAlertException("File names do not match new drag item and background file names", ENTITY_NAME, null);
        }
    }

    /**
     * Saves the background image of a drag and drop question without saving the question itself. The question is not needed to name the file: every background lives in one
     * directory and the column stores only the filename.
     *
     * @param question the drag and drop question
     * @param files    all provided files
     */
    public void saveDndQuestionBackground(DragAndDropQuestion question, Map<String, MultipartFile> files) throws IOException {
        MultipartFile file = files.get(question.getBackgroundFilePath());
        if (file == null) {
            // Should not be reached as the file is validated before
            throw new BadRequestAlertException("The file " + question.getBackgroundFilePath() + " was not provided", ENTITY_NAME, null);
        }

        Path savePath = copyDragAndDropImageToUploads(FilePathConverter.getDragAndDropBackgroundFilePath(), file);
        question.setBackgroundFilePath(savePath.getFileName().toString());
    }

    /**
     * Saves the picture of a drag item without saving the drag item itself.
     *
     * @param dragItem the drag item
     * @param files    all provided files
     */
    public void saveDndDragItemPicture(DragItem dragItem, Map<String, MultipartFile> files) throws IOException {
        MultipartFile file = files.get(dragItem.getPictureFilePath());
        if (file == null) {
            // Should not be reached as the file is validated before
            throw new BadRequestAlertException("The file " + dragItem.getPictureFilePath() + " was not provided", ENTITY_NAME, null);
        }

        Path savePath = copyDragAndDropImageToUploads(FilePathConverter.getDragItemFilePath(), file);
        dragItem.setPictureFilePath(savePath.getFileName().toString());
    }

    /**
     * Copies an uploaded drag-and-drop image into the given upload directory under a generated filename.
     *
     * @param basePath the upload directory the image belongs in
     * @param file     the uploaded file
     * @return the file system path the image was written to
     */
    private Path copyDragAndDropImageToUploads(Path basePath, MultipartFile file) throws IOException {
        String sanitizedFilename = FileUtil.checkAndSanitizeFilename(file.getOriginalFilename());
        Path savePath = basePath.resolve(FileUtil.generateFilename("dnd_image_", sanitizedFilename, true));
        FileUtils.copyToFile(file.getInputStream(), savePath.toFile());
        return savePath;
    }

    /**
     * Reset the invalid status of questions of given quizExercise to false
     *
     * @param quizExercise The quiz exercise which questions to be reset
     */
    private void resetInvalidQuestions(QuizExercise quizExercise) {
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            question.setInvalid(false);
        }
    }

    @Override
    public QuizExercise save(QuizExercise quizExercise) {
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());

        // create a quizPointStatistic if it does not yet exist
        if (quizExercise.getQuizPointStatistic() == null) {
            QuizPointStatistic quizPointStatistic = new QuizPointStatistic();
            quizExercise.setQuizPointStatistic(quizPointStatistic);
            quizPointStatistic.setQuiz(quizExercise);
        }

        // make sure the pointers in the statistics are correct
        quizExercise.recalculatePointCounters();

        QuizExercise savedQuizExercise = super.save(quizExercise);

        if (savedQuizExercise.isCourseExercise()) {
            // only schedule quizzes for course exercises, not for exam exercises
            instanceMessageSendService.sendQuizExerciseStartSchedule(savedQuizExercise.getId());
        }

        return savedQuizExercise;
    }

    @Override
    protected QuizExercise saveAndFlush(QuizExercise quizExercise) {
        if (quizExercise.getQuizBatches() != null) {
            for (QuizBatch quizBatch : quizExercise.getQuizBatches()) {
                quizBatch.setQuizExercise(quizExercise);
                if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
                    if (quizBatch.getStartTime() != null) {
                        quizExercise.setDueDate(quizBatch.getStartTime().plusSeconds(quizExercise.getDuration() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS));
                    }
                }
                else {
                    quizBatch.setStartTime(quizBatchService.quizBatchStartDate(quizExercise, quizBatch.getStartTime()));
                }
            }
        }

        // Note: save will automatically remove deleted questions from the exercise and deleted answer options from
        // the questions
        // and delete the now orphaned entries from the database
        log.debug("Save quiz exercise to database: {}", quizExercise);
        return quizExerciseRepository.saveAndFlush(quizExercise);
    }

    /**
     * @param newQuizExercise the newly created quiz exercise, after importing basis of imported exercise
     * @param files           the new files to be added to the newQuizExercise which do not have a previous path and
     *                            need to be saved in the server
     * @return the new exercise with the updated file paths which have been created and saved
     * @throws IOException throws IO exception if corrupted files
     */
    public QuizExercise uploadNewFilesToNewImportedQuiz(QuizExercise newQuizExercise, List<MultipartFile> files) throws IOException {
        Map<String, MultipartFile> fileMap = files.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, Function.identity()));
        for (var question : newQuizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (!Files.exists(new FileSystemLocation.DragAndDropBackground(dragAndDropQuestion.getBackgroundFilePath()).path())) {
                    saveDndQuestionBackground(dragAndDropQuestion, fileMap);
                }
                for (DragItem dragItem : dragAndDropQuestion.getDragItems()) {
                    if (dragItem.getPictureFilePath() != null && !Files.exists(new FileSystemLocation.DragItem(dragItem.getPictureFilePath()).path())) {
                        saveDndDragItemPicture(dragItem, fileMap);
                    }
                }
            }
        }
        return newQuizExercise;
    }

    /**
     * Performs the update of a quiz exercise, including validations, file handling, saving, logging,
     * notifications, and asynchronous updates. This method uses the original quiz for comparisons
     * (e.g., to detect changes or prevent invalid modifications) and applies updates from the provided
     * updated quiz exercise.
     *
     * @param originalQuiz          the original quiz exercise loaded from the database, used for comparisons
     *                                  and checks (e.g., to verify if the quiz has started or for file change detection).
     * @param updatedQuiz           the quiz exercise object containing the updated values to be applied and saved.
     * @param files                 the list of multipart files for drag-and-drop question updates (may be null or empty).
     * @param notificationText      optional text to include in notifications sent about the exercise update.
     * @param originalCompetencyIds the IDs of competencies originally linked to the exercise before the update
     * @return the updated and saved quiz exercise.
     * @throws IOException              if an error occurs during file handling or updates.
     * @throws BadRequestAlertException if the updated quiz is invalid (e.g., fails validation checks,
     *                                      quiz has already started, or conversion between exam/course types).
     */
    public QuizExercise performUpdate(QuizExercise originalQuiz, QuizExercise updatedQuiz, @NonNull List<MultipartFile> files, String notificationText,
            Set<Long> originalCompetencyIds) throws IOException {

        if (!updatedQuiz.isValid()) {
            throw new BadRequestAlertException("The quiz exercise is not valid", ENTITY_NAME, "invalidQuiz");
        }

        updatedQuiz.validateGeneralSettings();

        updatedQuiz.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        User user = userRepository.getUserWithAuthorities();

        // Check if quiz has already started or ended, and reuse the fetched batches
        Set<QuizBatch> batches = checkQuizEditable(originalQuiz);

        updatedQuiz.reconnectJSONIgnoreAttributes();

        // don't allow changing batches except in synchronized mode as the client doesn't have the full list and saving the exercise could otherwise end up deleting a bunch
        if (updatedQuiz.getQuizMode() != QuizMode.SYNCHRONIZED || updatedQuiz.getQuizBatches() == null || updatedQuiz.getQuizBatches().size() > 1) {
            updatedQuiz.setQuizBatches(batches);
        }

        handleDndQuizFileUpdates(updatedQuiz, originalQuiz, files);

        Channel updatedChannel = channelService.updateExerciseChannel(originalQuiz, updatedQuiz);

        updatedQuiz = save(updatedQuiz);
        exerciseService.logUpdate(updatedQuiz, updatedQuiz.getCourseViaExerciseGroupOrCourseMember(), user);
        groupNotificationScheduleService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(originalQuiz, updatedQuiz, notificationText);
        if (updatedChannel != null) {
            updatedQuiz.setChannelName(updatedChannel.getName());
        }
        QuizExercise finalQuizExercise = updatedQuiz;
        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsyncWithOriginalCompetencyIds(originalCompetencyIds, finalQuizExercise));
        slideApi.ifPresent(api -> api.handleDueDateChange(originalQuiz, finalQuizExercise));
        return updatedQuiz;
    }

    /**
     * Merges the properties of the UpdateQuizExerciseDTO into the QuizExercise domain object.
     * This method converts DTOs to new entity objects to avoid Hibernate detached entity issues.
     *
     * @param quizExercise          The QuizExercise domain object to be updated
     * @param updateQuizExerciseDTO The DTO containing the properties to be merged into the domain object.
     */
    public void mergeDTOIntoDomainObject(QuizExercise quizExercise, UpdateQuizExerciseDTO updateQuizExerciseDTO) {
        // PUT semantics: all fields are assigned directly; null means "clear/unset".
        quizExercise.setTitle(updateQuizExerciseDTO.title());
        quizExercise.setChannelName(updateQuizExerciseDTO.channelName());
        quizExercise.setCategories(updateQuizExerciseDTO.categories());
        quizExercise.setDifficulty(updateQuizExerciseDTO.difficulty());
        quizExercise.setDuration(updateQuizExerciseDTO.duration());
        quizExercise.setRandomizeQuestionOrder(updateQuizExerciseDTO.randomizeQuestionOrder());
        quizExercise.setQuizMode(updateQuizExerciseDTO.quizMode());

        if (updateQuizExerciseDTO.quizBatches() != null) {
            // Preserve existing batch IDs to avoid orphaning QuizSubmission.quizBatch references.
            // Use applyTo() for existing batches (id != null), toDomainObject() only for new ones.
            Map<Long, QuizBatch> existingBatchesById = quizExercise.getQuizBatches().stream().filter(b -> b.getId() != null)
                    .collect(Collectors.toMap(QuizBatch::getId, b -> b, (a, b) -> a));

            Set<QuizBatch> mergedBatches = updateQuizExerciseDTO.quizBatches().stream().map(dto -> {
                if (dto.id() != null) {
                    QuizBatch existing = existingBatchesById.get(dto.id());
                    if (existing != null) {
                        dto.applyTo(existing);
                        return existing;
                    }
                }
                return dto.toDomainObject();
            }).collect(Collectors.toSet());

            quizExercise.getQuizBatches().clear();
            quizExercise.getQuizBatches().addAll(mergedBatches);
        }
        else {
            quizExercise.getQuizBatches().clear();
        }

        quizExercise.setReleaseDate(updateQuizExerciseDTO.releaseDate());
        quizExercise.setStartDate(updateQuizExerciseDTO.startDate());
        quizExercise.setDueDate(updateQuizExerciseDTO.dueDate());
        quizExercise.setIncludedInOverallScore(updateQuizExerciseDTO.includedInOverallScore());

        if (updateQuizExerciseDTO.quizQuestions() != null) {
            // Build a map of existing questions by ID so we can preserve statistics
            Map<Long, QuizQuestion> existingQuestionsById = quizExercise.getQuizQuestions().stream().filter(q -> q.getId() != null)
                    .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));

            // Convert DTOs to new entities to avoid detached entity issues
            List<QuizQuestion> newQuestions = new ArrayList<>(updateQuizExerciseDTO.quizQuestions().stream().map(dto -> {
                QuizQuestion newQuestion = dto.toDomainObject();
                // For existing questions, preserve statistics from the managed entity
                if (newQuestion.getId() != null && existingQuestionsById.containsKey(newQuestion.getId())) {
                    QuizQuestion existingQuestion = existingQuestionsById.get(newQuestion.getId());
                    newQuestion.setQuizQuestionStatistic(existingQuestion.getQuizQuestionStatistic());
                }
                return newQuestion;
            }).toList());
            quizExercise.setQuizQuestions(newQuestions);
        }
        else {
            quizExercise.getQuizQuestions().clear();
        }

        competencyExerciseLinkService.updateCompetencyLinks(updateQuizExerciseDTO, quizExercise);
    }

    /**
     * Creates a copy of the quiz exercise with all fields that are necessary to compare the updated
     * quiz exercise with the original one.
     *
     * @param quizExercise the quiz exercise to copy
     * @return a copy of the quiz exercise with all fields required for an update.
     */
    public QuizExercise copyFieldsForUpdate(QuizExercise quizExercise) {
        QuizExercise copy = new QuizExercise();
        BeanUtils.copyProperties(quizExercise, copy);
        if (!quizExercise.isExamExercise()) {
            copy.setCourse(quizExercise.getCourseViaExerciseGroupOrCourseMember());
        }
        copy.setExerciseGroup(quizExercise.getExerciseGroup());
        copy.setQuizQuestions(new ArrayList<>(quizExercise.getQuizQuestions()));
        copy.setQuizPointStatistic(quizExercise.getQuizPointStatistic());
        copy.setCompetencyLinks(new HashSet<>(quizExercise.getCompetencyLinks()));
        copy.setQuizBatches(new HashSet<>(quizExercise.getQuizBatches()));
        copy.setGradingCriteria(new HashSet<>(quizExercise.getGradingCriteria()));
        return copy;
    }

    /**
     * Retrieves a {@link QuizExerciseCalendarEventDTO} for each {@link QuizExercise} associated to the given courseId.
     * Each DTO encapsulates the quizMode, title, releaseDate, dueDate, quizBatches and duration of the respective QuizExercise.
     * <p>
     * The method then derives a set of {@link CalendarEventDTO}s from the DTOs. Whether events are included in the result
     * depends on the quizMode of the given exercise and whether the logged-in user is a student of the {@link Course}.
     *
     * @param courseId      the ID of the course
     * @param userIsStudent indicates whether the logged-in user is a student of the course
     * @param language      the language that will be used add context information to titles (e.g. the title of a release event will be prefixed with "Release: ")
     * @return the set of results
     */
    public Set<CalendarEventDTO> getCalendarEventDTOsFromQuizExercises(long courseId, boolean userIsStudent, Language language) {
        Set<QuizExerciseCalendarEventDTO> dtos = quizExerciseRepository.getQuizExerciseCalendarEventDTOsForCourseId(courseId);
        return dtos.stream().flatMap(dto -> deriveCalendarEventDTOs(dto, userIsStudent, language).stream()).collect(Collectors.toSet());
    }

    private Set<CalendarEventDTO> deriveCalendarEventDTOs(QuizExerciseCalendarEventDTO dto, boolean userIsStudent, Language language) {
        if (dto.quizMode() == QuizMode.SYNCHRONIZED) {
            return deriveCalendarEventDTOForSynchronizedQuizExercise(dto, userIsStudent).map(Set::of).orElseGet(Collections::emptySet);
        }
        else {
            return deriveCalendarEventDTOsForIndividualAndBatchedQuizExercises(dto, userIsStudent, language);
        }
    }

    /**
     * Derives one event represents the working time period of the {@link QuizExercise} represented by the given DTO.
     * <p>
     * The events are only derived given that either the exercise is visible to students or the logged-in user is a course
     * staff member (either tutor, editor ot student of the {@link Course} associated to the exam).
     * <p>
     * Context: <br>
     * The startDate and dueDate properties of {@link QuizExercise}s in {@code QuizMode.SYNCHRONIZED} are always null. Instead, such quizzes have exactly one {@link QuizBatch}
     * for which the startTime property is set. The end of the quiz can be calculated by adding the duration property of the exercise to the startTime of the batch.
     *
     * @param dto           the DTO from which to derive the event
     * @param userIsStudent indicates whether the logged-in user is a student of the course related to the exercise
     * @return one event representing the working time period of the exercise
     */
    private Optional<CalendarEventDTO> deriveCalendarEventDTOForSynchronizedQuizExercise(QuizExerciseCalendarEventDTO dto, boolean userIsStudent) {
        if (userIsStudent && dto.releaseDate() != null && ZonedDateTime.now().isBefore(dto.releaseDate())) {
            return Optional.empty();
        }

        ZonedDateTime synchronizedBatchStartTime = dto.quizBatchStartTime();
        if (synchronizedBatchStartTime == null || dto.duration() == null) {
            return Optional.empty();
        }

        return Optional.of(new CalendarEventDTO("exerciseStartAndEndEvent-" + dto.originEntityId(), CalendarEventType.QUIZ_EXERCISE, dto.title(), synchronizedBatchStartTime,
                synchronizedBatchStartTime.plusSeconds(dto.duration()), null, null));
    }

    /**
     * Derives one event for start/end of the duration during which the user can choose to participate in the {@link QuizExercise} represented by the given DAO.
     * <p>
     * The events are only derived given that either the exercise is visible to students or the logged-in user is a course
     * staff member (either tutor, editor ot student of the {@link Course} associated to the exam).
     * <p>
     * Context: <br>
     * For {@link QuizExercise}s in {@code QuizMode.INDIVIDUAL} the user can decide when to start the quiz themselves.
     * For {@link QuizExercise}s in {@code QuizMode.BATCHED} the user can join a quiz by using a password. The instructor can then start the quiz manually.
     * For both modes, the period in which the quiz can be held may be constrained by releaseDate (defining a start of the period) or dueDate (defining an end of the period).
     * The dueDate and startDate can be set independent of each other.
     *
     * @param dto           the DTO from which to derive the events
     * @param userIsStudent indicates whether the logged-in user is a student of the course associated to the quizExercise
     * @param language      the language that will be used add context information to titles (e.g. the title of a release event will be prefixed with "Release: ")
     * @return the derived events
     */
    private Set<CalendarEventDTO> deriveCalendarEventDTOsForIndividualAndBatchedQuizExercises(QuizExerciseCalendarEventDTO dto, boolean userIsStudent, Language language) {
        Set<CalendarEventDTO> events = new HashSet<>();
        boolean userIsCourseStaff = !userIsStudent;
        if (userIsCourseStaff || dto.releaseDate() == null || dto.releaseDate().isBefore(now())) {
            if (dto.releaseDate() != null) {
                String releaseDateTitlePrefix = switch (language) {
                    case ENGLISH -> "Release: ";
                    case GERMAN -> "Veröffentlichung: ";
                };
                events.add(new CalendarEventDTO("exerciseReleaseEvent-" + dto.originEntityId(), CalendarEventType.QUIZ_EXERCISE, releaseDateTitlePrefix + dto.title(),
                        dto.releaseDate(), null, null, null));
            }
            if (dto.dueDate() != null) {
                String dueDateTitlePrefix = switch (language) {
                    case ENGLISH -> "Due: ";
                    case GERMAN -> "Abgabefrist: ";
                };
                events.add(new CalendarEventDTO("exerciseDueEvent-" + dto.originEntityId(), CalendarEventType.QUIZ_EXERCISE, dueDateTitlePrefix + dto.title(), dto.dueDate(), null,
                        null, null));
            }
        }
        return events;
    }

    /**
     * Creates a new quiz exercise, handling validation, file processing, saving, and related updates.
     *
     * @param quizExercise    the quiz exercise domain object to create (without competency links)
     * @param files           the files for drag and drop questions (optional)
     * @param isExam          true if creating for an exam, false for a course
     * @param competencyLinks the competency links to associate with the exercise (can be null or empty)
     * @return the created and saved quiz exercise
     * @throws IOException if there is an error handling the files
     */
    public QuizExercise createQuizExercise(QuizExercise quizExercise, List<MultipartFile> files, boolean isExam, Set<CompetencyLinkDTO> competencyLinks) throws IOException {
        // Mapping resolution is handled in the Create DTO toDomainObject() methods at question level.
        if (!quizExercise.isValid()) {
            throw new BadRequestAlertException("The quiz exercise is invalid", ENTITY_NAME, "invalidQuiz");
        }
        quizExercise.validateGeneralSettings();
        handleDndQuizFileCreation(quizExercise, files);

        // Save the exercise first to get an ID (competency links are passed separately and require the exercise ID)
        QuizExercise savedExercise = save(quizExercise);

        // Add competency links after the initial save (they need the exercise ID for @MapsId).
        // IMPORTANT: Do NOT re-save the exercise (neither via QuizService.save() nor via
        // quizExerciseRepository.saveAndFlush()). Re-saving causes Hibernate's @OrderColumn
        // management to null out the exercise_id FK on quiz_question rows.
        // Instead, save the competency links directly via their own repository.
        if (competencyLinks != null && !competencyLinks.isEmpty()) {
            competencyExerciseLinkService.updateCompetencyLinks(() -> competencyLinks, savedExercise);
            competencyExerciseLinkService.saveAll(savedExercise.getCompetencyLinks());
        }

        QuizExercise result = savedExercise;
        if (!isExam) {
            channelService.createExerciseChannel(result, Optional.ofNullable(quizExercise.getChannelName()));
        }
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(result));
        return result;
    }

    /**
     * Creates the appropriate DTO for a student based on the quiz state and batch.
     *
     * @param quizExercise the quiz exercise to map
     * @param batch        the optional quiz batch associated with the student
     * @return the mapped DTO (QuizExerciseWithoutQuestionsDTO, QuizExerciseWithQuestionsDTO, or QuizExerciseWithSolutionsDTO)
     */
    public Object createQuizExerciseDTOForStudent(QuizExercise quizExercise, Optional<QuizBatch> batch) {
        if (quizExercise.isQuizEnded()) {
            return QuizExerciseWithSolutionDTO.of(quizExercise);
        }
        else if (batch.isEmpty() || !batch.get().isSubmissionAllowed()) {
            return QuizExerciseWithoutQuestionsDTO.of(quizExercise);
        }
        else {
            return QuizExerciseWithQuestionsDTO.of(quizExercise);
        }
    }

    /**
     * Determines if the given quiz exercise is editable.
     * For exam exercises, the quiz is not editable once the exam has started.
     * For course exercises, the quiz is not editable if any batch has started or the quiz has ended.
     *
     * @param quizExercise the quiz exercise to check
     * @return true if the quiz exercise is editable, false otherwise
     */
    public boolean isEditable(QuizExercise quizExercise) {
        if (quizExercise.isExamExercise()) {
            Exam exam = quizExercise.getExerciseGroup().getExam();
            return exam.getStartDate() == null || ZonedDateTime.now().isBefore(exam.getStartDate());
        }
        Set<QuizBatch> batches = quizBatchRepository.findAllByQuizExercise(quizExercise);
        if (batches.stream().anyMatch(QuizBatch::isStarted)) {
            return false;
        }
        return !quizExercise.isQuizEnded();
    }

    /**
     * Checks if the given quiz exercise is editable and throws an appropriate exception if not.
     * For exam exercises, uses ExamDateApi to distinguish between "during exam" and "after exam end".
     * For course exercises, checks quiz batches and due date.
     *
     * @param quizExercise the quiz exercise to check
     * @return the quiz batches for course exercises (empty set for exam exercises), so callers can reuse them
     * @throws AccessForbiddenException if the quiz is not editable
     */
    public Set<QuizBatch> checkQuizEditable(QuizExercise quizExercise) {
        if (quizExercise.isExamExercise()) {
            Exam exam = quizExercise.getExerciseGroup().getExam();
            if (exam.getStartDate() != null && ZonedDateTime.now().isAfter(exam.getStartDate())) {
                ExamDateApi api = examDateApi.orElseThrow(() -> new ExamApiNotPresentException(ExamDateApi.class));
                ZonedDateTime latestEnd = api.getLatestIndividualExamEndDate(exam);
                if (latestEnd != null && ZonedDateTime.now().isAfter(latestEnd)) {
                    throw new AccessForbiddenException("After the end of the quiz working time, editing is not possible.");
                }
                throw new AccessForbiddenException("During the quiz, editing is not possible. You can re-evaluate after the quiz has finished.");
            }
            return Set.of();
        }
        else {
            Set<QuizBatch> batches = quizBatchRepository.findAllByQuizExercise(quizExercise);
            if (quizExercise.isQuizEnded()) {
                throw new AccessForbiddenException("After the end of the quiz working time, editing is not possible.");
            }
            if (batches.stream().anyMatch(QuizBatch::isStarted)) {
                throw new AccessForbiddenException("During the quiz, editing is not possible. You can re-evaluate after the quiz has finished.");
            }
            return batches;
        }
    }

}
