package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.ZonedDateTime.now;

import java.io.IOException;
import java.net.URI;
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

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.dto.calendar.QuizExerciseCalendarEventDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.CalendarEventType;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.lecture.api.SlideApi;
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
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseFromEditorDTO;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.AnswerOptionReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.DragAndDropQuestionReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.DragItemReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.DropLocationReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.MultipleChoiceQuestionReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.ShortAnswerMappingReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.ShortAnswerQuestionReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.ShortAnswerSolutionReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.reevaluate.ShortAnswerSpotReEvaluateDTO;
import de.tum.cit.aet.artemis.quiz.repository.DragAndDropMappingRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;
import de.tum.cit.aet.artemis.quiz.repository.ShortAnswerMappingRepository;

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

    public QuizExerciseService(QuizExerciseRepository quizExerciseRepository, ResultRepository resultRepository, QuizSubmissionRepository quizSubmissionRepository,
            InstanceMessageSendService instanceMessageSendService, QuizStatisticService quizStatisticService, QuizBatchService quizBatchService,
            ExerciseSpecificationService exerciseSpecificationService, DragAndDropMappingRepository dragAndDropMappingRepository,
            ShortAnswerMappingRepository shortAnswerMappingRepository, ExerciseService exerciseService, UserRepository userRepository, QuizBatchRepository quizBatchRepository,
            ChannelService channelService, GroupNotificationScheduleService groupNotificationScheduleService, Optional<CompetencyProgressApi> competencyProgressApi,
            Optional<SlideApi> slideApi) {
        super(dragAndDropMappingRepository, shortAnswerMappingRepository);
        this.quizExerciseRepository = quizExerciseRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.instanceMessageSendService = instanceMessageSendService;
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
            throw new BadRequestAlertException("The " + itemType + " with id " + id + " is marked as invalid and cannot be set to valid again", ENTITY_NAME, null);
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
                    throw new BadRequestAlertException("The drag item with id " + dragItemDTO.id() + " has no text or picture", ENTITY_NAME, null);
                }
                if (dragItemDTO.text() != null && dragItemDTO.pictureFilePath() != null) {
                    throw new BadRequestAlertException("The drag item with id " + dragItemDTO.id() + " has both text and picture", ENTITY_NAME, null);
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
        originalQuestion.getCorrectMappings().removeAll(mappingsToRemove);
        Set<DragAndDropMapping> existingMappings = new HashSet<>(originalQuestion.getCorrectMappings());
        for (var mappingDTO : dndDTO.correctMappings()) {
            boolean mappingExists = existingMappings.stream()
                    .anyMatch(mapping -> mapping.getDragItem().getId().equals(mappingDTO.dragItemId()) && mapping.getDropLocation().getId().equals(mappingDTO.dropLocationId()));
            if (!mappingExists) {
                DragItem dragItem = originalQuestion.getDragItems().stream().filter(item -> item.getId().equals(mappingDTO.dragItemId())).findFirst()
                        .orElseThrow(() -> new BadRequestAlertException("The drag item with id " + mappingDTO.dragItemId() + " does not exist", ENTITY_NAME, null));
                DropLocation dropLocation = originalQuestion.getDropLocations().stream().filter(location -> location.getId().equals(mappingDTO.dropLocationId())).findFirst()
                        .orElseThrow(() -> new BadRequestAlertException("The drop location with id " + mappingDTO.dropLocationId() + " does not exist", ENTITY_NAME, null));
                DragAndDropMapping newMapping = new DragAndDropMapping();
                newMapping.setDragItem(dragItem);
                newMapping.setDropLocation(dropLocation);
                originalQuestion.getCorrectMappings().add(newMapping);
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

    private static boolean applyShortAnswerSolutionsFromDTOs(List<ShortAnswerSolutionReEvaluateDTO> solutionDTOs, List<ShortAnswerSolution> originalSolution) {
        boolean recalculationNecessary = false;
        List<ShortAnswerSolution> solutionsToRemove = new ArrayList<>();
        Map<Long, ShortAnswerSolutionReEvaluateDTO> solutionReEvaluateDTOMap = solutionDTOs.stream()
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
        for (ShortAnswerSolutionReEvaluateDTO solutionDTO : solutionDTOs) {
            if (solutionDTO.id() == null && solutionDTO.tempID() == null) {
                throw new BadRequestAlertException("A new short answer solution must have a tempID to identify it", ENTITY_NAME, null);
            }
            else if (solutionDTO.id() != null && solutionDTO.tempID() != null) {
                throw new BadRequestAlertException("An existing short answer solution cannot have a tempID", ENTITY_NAME, null);
            }
            if (solutionDTO.tempID() != null) {
                ShortAnswerSolution newSolution = new ShortAnswerSolution();
                newSolution.setTempID(solutionDTO.tempID());
                newSolution.setText(solutionDTO.text());
                newSolution.setInvalid(solutionDTO.invalid());
                originalSolution.add(newSolution);
                recalculationNecessary = true;
            }
        }
        return recalculationNecessary;
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
            Set<ShortAnswerMapping> existingMappings) {
        if (mappingDTO.solutionId() == null && mappingDTO.solutionTempID() == null) {
            throw new BadRequestAlertException("The short answer mapping for spot id " + mappingDTO.spotId() + " has no solutionId or solutionTempID", ENTITY_NAME,
                    "mappingSolutionIDMissing");
        }
        if (mappingDTO.solutionId() != null && mappingDTO.solutionTempID() != null) {
            throw new BadRequestAlertException("The short answer mapping for spot id " + mappingDTO.spotId() + " has both solutionId and solutionTempID", ENTITY_NAME, null);
        }
        boolean mappingExists;
        if (mappingDTO.solutionTempID() != null) {
            mappingExists = existingMappings.stream()
                    .anyMatch(mapping -> mapping.getSpot().getId().equals(mappingDTO.spotId()) && Objects.equals(mapping.getSolution().getTempID(), mappingDTO.solutionTempID()));
        }
        else {
            mappingExists = existingMappings.stream()
                    .anyMatch(mapping -> mapping.getSpot().getId().equals(mappingDTO.spotId()) && mapping.getSolution().getId().equals(mappingDTO.solutionId()));
        }
        if (mappingExists) {
            return false;
        }
        ShortAnswerSpot spot = originalQuestion.getSpots().stream().filter(item -> item.getId().equals(mappingDTO.spotId())).findFirst()
                .orElseThrow(() -> new BadRequestAlertException("The short answer spot with id " + mappingDTO.spotId() + " does not exist", ENTITY_NAME, null));
        ShortAnswerSolution solution;
        if (mappingDTO.solutionTempID() != null) {
            solution = originalQuestion.getSolutions().stream().filter(item -> Objects.equals(item.getTempID(), mappingDTO.solutionTempID())).findFirst()
                    .orElseThrow(() -> new BadRequestAlertException("The short answer solution with tempID " + mappingDTO.solutionTempID() + " does not exist", ENTITY_NAME, null));
        }
        else {
            solution = originalQuestion.getSolutions().stream().filter(item -> item.getId().equals(mappingDTO.solutionId())).findFirst()
                    .orElseThrow(() -> new BadRequestAlertException("The short answer solution with id " + mappingDTO.solutionId() + " does not exist", ENTITY_NAME, null));
        }
        ShortAnswerMapping newMapping = new ShortAnswerMapping();
        newMapping.setSpot(spot);
        newMapping.setSolution(solution);
        originalQuestion.getCorrectMappings().add(newMapping);
        return true;
    }

    private static boolean applyShortAnswerMappingFromDTOs(ShortAnswerQuestionReEvaluateDTO saDTO, ShortAnswerQuestion originalQuestion) {
        boolean recalculationNecessary = false;
        List<ShortAnswerMapping> mappingsToRemove = new ArrayList<>();
        for (ShortAnswerMapping originalMapping : originalQuestion.getCorrectMappings()) {
            boolean mappingExistsInDTO = saDTO.correctMappings().stream()
                    .anyMatch(dto -> dto.spotId().equals(originalMapping.getSpot().getId()) && dto.solutionId().equals(originalMapping.getSolution().getId()));
            if (!mappingExistsInDTO) {
                mappingsToRemove.add(originalMapping);
                recalculationNecessary = true;
            }
        }
        originalQuestion.getCorrectMappings().removeAll(mappingsToRemove);
        Set<ShortAnswerMapping> existingMappings = new HashSet<>(originalQuestion.getCorrectMappings());
        for (var mappingDTO : saDTO.correctMappings()) {
            if (addNewShortAnswerMappingFromDTO(originalQuestion, mappingDTO, existingMappings)) {
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
        recalculationNecessary = applyShortAnswerSolutionsFromDTOs(shortAnswerQuestionDTO.solutions(), originalQuestion.getSolutions()) || recalculationNecessary;
        recalculationNecessary = applyShortAnswerMappingFromDTOs(shortAnswerQuestionDTO, originalQuestion) || recalculationNecessary;

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
                            .orElseThrow(() -> new BadRequestAlertException("The drag and drop question with id " + dragAndDropQuestionReEvaluateDTO.id() + " does not exist",
                                    ENTITY_NAME, null));
                    questionsChanged = applyDragAndDropQuestionFromDTO(dragAndDropQuestionReEvaluateDTO, originalQuestion) || questionsChanged;
                    newQuestions.add(originalQuestion);
                }
                case ShortAnswerQuestionReEvaluateDTO shortAnswerQuestionReEvaluateDTO -> {
                    ShortAnswerQuestion originalQuestion = (ShortAnswerQuestion) originalQuizExercise.getQuizQuestions().stream()
                            .filter(q -> q.getId().equals(shortAnswerQuestionReEvaluateDTO.id())).findFirst()
                            .orElseThrow(() -> new BadRequestAlertException("The short answer question with id " + shortAnswerQuestionReEvaluateDTO.id() + " does not exist",
                                    ENTITY_NAME, null));
                    questionsChanged = applyShortAnswerQuestionFromDTO(shortAnswerQuestionReEvaluateDTO, originalQuestion) || questionsChanged;
                    newQuestions.add(originalQuestion);
                }
                case MultipleChoiceQuestionReEvaluateDTO multipleChoiceQuestionReEvaluateDTO -> {
                    MultipleChoiceQuestion originalQuestion = (MultipleChoiceQuestion) originalQuizExercise.getQuizQuestions().stream()
                            .filter(q -> q.getId().equals(multipleChoiceQuestionReEvaluateDTO.id())).findFirst()
                            .orElseThrow(() -> new BadRequestAlertException("The multiple choice question with id " + multipleChoiceQuestionReEvaluateDTO.id() + " does not exist",
                                    ENTITY_NAME, null));
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
        List<Result> results = resultRepository.findBySubmissionParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
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
    public QuizExercise reEvaluate(QuizExerciseReEvaluateDTO quizExerciseDTO, QuizExercise originalQuizExercise, @NotNull List<MultipartFile> files) throws IOException {
        Map<FilePathType, Set<String>> oldPaths = getAllPathsFromDragAndDropQuestionsOfExercise(originalQuizExercise);
        boolean questionsChanged = applyBaseQuizQuestionData(quizExerciseDTO, originalQuizExercise);
        questionsChanged = applyQuizQuestionsFromDTOAndCheckIfChanged(quizExerciseDTO, originalQuizExercise) || questionsChanged;
        validateQuizExerciseFiles(originalQuizExercise, files, false);
        Map<FilePathType, Set<String>> filesToRemove = new HashMap<>(oldPaths);
        Map<String, MultipartFile> fileMap = files.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, Function.identity()));
        for (var question : originalQuizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                handleDndQuestionUpdate(dragAndDropQuestion, oldPaths, filesToRemove, fileMap, dragAndDropQuestion);
            }
        }
        var allFilesToRemoveMerged = filesToRemove.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(path -> FilePathConverter.fileSystemPathForExternalUri(URI.create(path), entry.getKey()))).filter(Objects::nonNull)
                .toList();
        FileUtil.deleteFiles(allFilesToRemoveMerged);
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

        // for quizzes, we need to delete the statistics, and we need to reset the quiz to its original state
        quizExercise.setIsOpenForPractice(Boolean.FALSE);
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

    public void cancelScheduledQuiz(Long quizExerciseId) {
        instanceMessageSendService.sendQuizExerciseStartCancel(quizExerciseId);
    }

    /**
     * Update a QuizExercise so that it ends at a specific date and moves the start date of the batches as required.
     * Does not save the quiz.
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
    public SearchResultPageDTO<QuizExercise> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final Boolean isCourseFilter, final Boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        final var searchTerm = search.getSearchTerm();
        Specification<QuizExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<QuizExercise> exercisePage = quizExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
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
        validateQuizExerciseFiles(quizExercise, nullsafeFiles, true);
        Map<String, MultipartFile> fileMap = nullsafeFiles.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, file -> file));

        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (dragAndDropQuestion.getBackgroundFilePath() != null) {
                    saveDndQuestionBackground(dragAndDropQuestion, fileMap, null);
                }
                handleDndQuizDragItemsCreation(dragAndDropQuestion, fileMap);
            }
        }
    }

    private void handleDndQuizDragItemsCreation(DragAndDropQuestion dragAndDropQuestion, Map<String, MultipartFile> fileMap) throws IOException {
        for (var dragItem : dragAndDropQuestion.getDragItems()) {
            if (dragItem.getPictureFilePath() != null) {
                saveDndDragItemPicture(dragItem, fileMap, null);
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
    public void handleDndQuizFileUpdates(QuizExercise updatedExercise, QuizExercise originalExercise, List<MultipartFile> files) throws IOException {
        List<MultipartFile> nullsafeFiles = files == null ? new ArrayList<>() : files;
        validateQuizExerciseFiles(updatedExercise, nullsafeFiles, false);
        Map<FilePathType, Set<String>> oldPaths = getAllPathsFromDragAndDropQuestionsOfExercise(originalExercise);
        Map<FilePathType, Set<String>> filesToRemove = new HashMap<>(oldPaths);

        Map<String, MultipartFile> fileMap = nullsafeFiles.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, file -> file));

        for (var question : updatedExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                handleDndQuestionUpdate(dragAndDropQuestion, oldPaths, filesToRemove, fileMap, dragAndDropQuestion);
            }
        }

        var allFilesToRemoveMerged = filesToRemove.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(path -> FilePathConverter.fileSystemPathForExternalUri(URI.create(path), entry.getKey()))).filter(Objects::nonNull)
                .toList();

        FileUtil.deleteFiles(allFilesToRemoveMerged);
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
            Map<String, MultipartFile> fileMap, DragAndDropQuestion questionUpdate) throws IOException {
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
                saveDndQuestionBackground(dragAndDropQuestion, fileMap, questionUpdate.getId());
            }
        }

        for (var dragItem : dragAndDropQuestion.getDragItems()) {
            String newDragItemPath = dragItem.getPictureFilePath();
            Set<String> dragItemOldPaths = oldPaths.get(FilePathType.DRAG_ITEM);
            if (newDragItemPath != null && !dragItemOldPaths.contains(newDragItemPath)) {
                // Path changed and file was provided
                saveDndDragItemPicture(dragItem, fileMap, null);
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
     * @param isCreate      On create all files get validated, on update only changed files get validated
     */
    public void validateQuizExerciseFiles(QuizExercise quizExercise, @NotNull List<MultipartFile> providedFiles, boolean isCreate) {
        long fileCount = providedFiles.size();

        Map<FilePathType, Set<String>> exerciseFilePathsMap = getAllPathsFromDragAndDropQuestionsOfExercise(quizExercise);

        Map<FilePathType, Set<String>> newFilePathsMap = new HashMap<>();

        if (isCreate) {
            newFilePathsMap = new HashMap<>(exerciseFilePathsMap);
        }
        else {
            for (Map.Entry<FilePathType, Set<String>> entry : exerciseFilePathsMap.entrySet()) {
                FilePathType type = entry.getKey();
                Set<String> paths = entry.getValue();
                paths.forEach(FileUtil::sanitizeFilePathByCheckingForInvalidCharactersElseThrow);
                paths.stream().filter(path -> Files.exists(FilePathConverter.fileSystemPathForExternalUri(URI.create(path), type))).forEach(path -> {
                    URI intendedSubPath = type == FilePathType.DRAG_AND_DROP_BACKGROUND ? URI.create(FileUtil.BACKGROUND_FILE_SUBPATH) : URI.create(FileUtil.PICTURE_FILE_SUBPATH);
                    FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(URI.create(path), intendedSubPath);
                });

                Set<String> newPaths = paths.stream().filter(filePath -> !Files.exists(FilePathConverter.fileSystemPathForExternalUri(URI.create(filePath), type)))
                        .collect(Collectors.toSet());

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
     * Saves the background image of a drag and drop question without saving the question itself
     *
     * @param question   the drag and drop question
     * @param files      all provided files
     * @param questionId the id of the question, null on creation
     */
    public void saveDndQuestionBackground(DragAndDropQuestion question, Map<String, MultipartFile> files, @Nullable Long questionId) throws IOException {
        MultipartFile file = files.get(question.getBackgroundFilePath());
        if (file == null) {
            // Should not be reached as the file is validated before
            throw new BadRequestAlertException("The file " + question.getBackgroundFilePath() + " was not provided", ENTITY_NAME, null);
        }

        question.setBackgroundFilePath(
                saveDragAndDropImage(FilePathConverter.getDragAndDropBackgroundFilePath(), file, FilePathType.DRAG_AND_DROP_BACKGROUND, questionId).toString());
    }

    /**
     * Saves the picture of a drag item without saving the drag item itself
     *
     * @param dragItem the drag item
     * @param files    all provided files
     * @param entityId The entity id connected to this file, can be question id for background, or the drag item id
     *                     for drag item images
     */
    public void saveDndDragItemPicture(DragItem dragItem, Map<String, MultipartFile> files, @Nullable Long entityId) throws IOException {
        MultipartFile file = files.get(dragItem.getPictureFilePath());
        if (file == null) {
            // Should not be reached as the file is validated before
            throw new BadRequestAlertException("The file " + dragItem.getPictureFilePath() + " was not provided", ENTITY_NAME, null);
        }

        dragItem.setPictureFilePath(saveDragAndDropImage(FilePathConverter.getDragItemFilePath(), file, FilePathType.DRAG_ITEM, entityId).toString());
    }

    /**
     * Saves an image for an DragAndDropQuestion. Either a background image or a drag item image.
     *
     * @return the public path of the saved image
     */
    private URI saveDragAndDropImage(Path basePath, MultipartFile file, FilePathType filePathType, @Nullable Long entityId) throws IOException {
        String sanitizedFilename = FileUtil.checkAndSanitizeFilename(file.getOriginalFilename());
        Path savePath = basePath.resolve(FileUtil.generateFilename("dnd_image_", sanitizedFilename, true));
        FileUtils.copyToFile(file.getInputStream(), savePath.toFile());
        return FilePathConverter.externalUriForFileSystemPath(savePath, filePathType, entityId);
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

        // Set released for practice to false if not set already
        if (quizExercise.isIsOpenForPractice() == null) {
            quizExercise.setIsOpenForPractice(Boolean.FALSE);
        }

        // make sure the pointers in the statistics are correct
        quizExercise.recalculatePointCounters();

        QuizExercise savedQuizExercise = exerciseService.saveWithCompetencyLinks(quizExercise, super::save);

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
                URI publicPathUri = URI.create(dragAndDropQuestion.getBackgroundFilePath());
                if (!Files.exists(FilePathConverter.fileSystemPathForExternalUri(publicPathUri, FilePathType.DRAG_AND_DROP_BACKGROUND))) {
                    saveDndQuestionBackground(dragAndDropQuestion, fileMap, dragAndDropQuestion.getId());
                }
                for (DragItem dragItem : dragAndDropQuestion.getDragItems()) {
                    if (dragItem.getPictureFilePath() != null
                            && !Files.exists(FilePathConverter.fileSystemPathForExternalUri(URI.create(dragItem.getPictureFilePath()), FilePathType.DRAG_ITEM))) {
                        saveDndDragItemPicture(dragItem, fileMap, dragItem.getId());
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
     * @param originalQuiz     the original quiz exercise loaded from the database, used for comparisons
     *                             and checks (e.g., to verify if the quiz has started or for file change detection).
     * @param updatedQuiz      the quiz exercise object containing the updated values to be applied and saved.
     * @param files            the list of multipart files for drag-and-drop question updates (may be null or empty).
     * @param notificationText optional text to include in notifications sent about the exercise update.
     * @return the updated and saved quiz exercise.
     * @throws IOException              if an error occurs during file handling or updates.
     * @throws BadRequestAlertException if the updated quiz is invalid (e.g., fails validation checks,
     *                                      quiz has already started, or conversion between exam/course types).
     */
    public QuizExercise performUpdate(QuizExercise originalQuiz, QuizExercise updatedQuiz, List<MultipartFile> files, String notificationText) throws IOException {

        if (!updatedQuiz.isValid()) {
            throw new BadRequestAlertException("The quiz exercise is not valid", ENTITY_NAME, "invalidQuiz");
        }

        updatedQuiz.validateGeneralSettings();

        updatedQuiz.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        User user = userRepository.getUserWithGroupsAndAuthorities();

        // Check if quiz has already started
        Set<QuizBatch> batches = quizBatchRepository.findAllByQuizExercise(originalQuiz);
        if (batches.stream().anyMatch(QuizBatch::isStarted)) {
            throw new BadRequestAlertException("The quiz has already started. Use the re-evaluate endpoint to make retroactive corrections.", ENTITY_NAME, "quizHasStarted");
        }

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
        competencyProgressApi.ifPresent(api -> api.updateProgressForUpdatedLearningObjectAsync(originalQuiz, Optional.of(finalQuizExercise)));
        slideApi.ifPresent(api -> api.handleDueDateChange(originalQuiz, finalQuizExercise));
        return updatedQuiz;
    }

    /**
     * Merges the properties of the QuizExerciseFromEditorDTO into the QuizExercise domain object.
     *
     * @param quizExercise              The QuizExercise domain object to be updated
     * @param quizExerciseFromEditorDTO The DTO containing the properties to be merged into the domain object.
     */
    public void mergeDTOIntoDomainObject(QuizExercise quizExercise, QuizExerciseFromEditorDTO quizExerciseFromEditorDTO) {
        if (quizExerciseFromEditorDTO.title() != null) {
            quizExercise.setTitle(quizExerciseFromEditorDTO.title());
        }
        if (quizExerciseFromEditorDTO.channelName() != null) {
            quizExercise.setChannelName(quizExerciseFromEditorDTO.channelName());
        }
        if (quizExerciseFromEditorDTO.categories() != null) {
            quizExercise.setCategories(quizExerciseFromEditorDTO.categories());
        }
        if (quizExerciseFromEditorDTO.competencyLinks() != null) {
            quizExercise.getCompetencyLinks().clear();
            quizExercise.getCompetencyLinks().addAll(quizExerciseFromEditorDTO.competencyLinks());
        }
        if (quizExerciseFromEditorDTO.difficulty() != null) {
            quizExercise.setDifficulty(quizExerciseFromEditorDTO.difficulty());
        }
        if (quizExerciseFromEditorDTO.duration() != null) {
            quizExercise.setDuration(quizExerciseFromEditorDTO.duration());
        }
        if (quizExerciseFromEditorDTO.randomizeQuestionOrder() != null) {
            quizExercise.setRandomizeQuestionOrder(quizExerciseFromEditorDTO.randomizeQuestionOrder());
        }
        if (quizExerciseFromEditorDTO.quizMode() != null) {
            quizExercise.setQuizMode(quizExerciseFromEditorDTO.quizMode());
        }
        if (quizExerciseFromEditorDTO.quizBatches() != null) {
            quizExercise.getQuizBatches().clear();
            quizExercise.getQuizBatches().addAll(quizExerciseFromEditorDTO.quizBatches());
        }
        if (quizExerciseFromEditorDTO.releaseDate() != null) {
            quizExercise.setReleaseDate(quizExerciseFromEditorDTO.releaseDate());
        }
        if (quizExerciseFromEditorDTO.startDate() != null) {
            quizExercise.setStartDate(quizExerciseFromEditorDTO.startDate());
        }
        if (quizExerciseFromEditorDTO.dueDate() != null) {
            quizExercise.setDueDate(quizExerciseFromEditorDTO.dueDate());
        }
        if (quizExerciseFromEditorDTO.includedInOverallScore() != null) {
            quizExercise.setIncludedInOverallScore(quizExerciseFromEditorDTO.includedInOverallScore());
        }
        if (quizExerciseFromEditorDTO.quizQuestions() != null) {
            quizExercise.setQuizQuestions(quizExerciseFromEditorDTO.quizQuestions());
        }
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
        copy.setQuizQuestions(quizExercise.getQuizQuestions());
        copy.setQuizPointStatistic(quizExercise.getQuizPointStatistic());
        copy.setCompetencyLinks(quizExercise.getCompetencyLinks());
        copy.setQuizBatches(quizExercise.getQuizBatches());
        copy.setGradingCriteria(quizExercise.getGradingCriteria());
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

        QuizBatch synchronizedBatch = dto.quizBatch();
        if (synchronizedBatch == null || synchronizedBatch.getStartTime() == null || dto.duration() == null) {
            return Optional.empty();
        }

        return Optional.of(new CalendarEventDTO("exerciseStartAndEndEvent-" + dto.originEntityId(), CalendarEventType.QUIZ_EXERCISE, dto.title(), synchronizedBatch.getStartTime(),
                synchronizedBatch.getStartTime().plusSeconds(dto.duration()), null, null));
    }

    /**
     * Derives one event for start/end of the duration during which the user can choose to participate in the {@link QuizExercise} represented by the given DAO.
     * <p>
     * The events are only derived given that either the exercise is visible to students or the logged-in user is a course
     * staff member (either tutor, editor ot student of the {@link Course} associated to the exam).
     * <p>
     * Context: <br>
     * For {@link QuizExercise}s in {@code QuizMode.INDIVIDUAL} the user can decide when to start the quiz himself.
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
     * Resolves the mappings in DragAndDrop and ShortAnswer questions within the given QuizExercise.
     * <p>
     * This method iterates through all questions in the quiz exercise. For DragAndDropQuestions and ShortAnswerQuestions,
     * it replaces the temporary objects in the correct mappings with the actual objects from the question's collections,
     * matching them by their temporary IDs (tempID).
     * <p>
     * If a mapping cannot be resolved (i.e., no matching object found for a tempID), a BadRequestAlertException is thrown.
     *
     * @param quizExercise the QuizExercise containing the questions to process
     * @throws BadRequestAlertException if any mapping cannot be resolved due to invalid tempIDs
     */
    public void resolveQuizQuestionMappings(QuizExercise quizExercise) throws BadRequestAlertException {
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dnd) {
                Map<Long, DragItem> idToDragItem = dnd.getDragItems().stream().collect(Collectors.toMap(DragItem::getTempID, Function.identity()));
                Map<Long, DropLocation> idToDropLocation = dnd.getDropLocations().stream().collect(Collectors.toMap(DropLocation::getTempID, Function.identity()));
                for (DragAndDropMapping mapping : dnd.getCorrectMappings()) {
                    Long dragItemTempId = mapping.getDragItem().getTempID();
                    Long dropLocationTempId = mapping.getDropLocation().getTempID();
                    DragItem dragItem = idToDragItem.get(dragItemTempId);
                    DropLocation dropLocation = idToDropLocation.get(dropLocationTempId);
                    if (dragItem == null || dropLocation == null) {
                        throw new BadRequestAlertException("Could not resolve drag and drop mappings", ENTITY_NAME, "invalidMappings");
                    }
                    mapping.setDragItem(dragItem);
                    mapping.setDropLocation(dropLocation);
                }
            }
            else if (question instanceof ShortAnswerQuestion sa) {
                Map<Long, ShortAnswerSpot> idToSpot = sa.getSpots().stream().collect(Collectors.toMap(ShortAnswerSpot::getTempID, Function.identity()));
                Map<Long, ShortAnswerSolution> idToSolution = sa.getSolutions().stream().collect(Collectors.toMap(ShortAnswerSolution::getTempID, Function.identity()));
                for (ShortAnswerMapping mapping : sa.getCorrectMappings()) {
                    Long spotTempId = mapping.getSpot().getTempID();
                    Long solutionTempId = mapping.getSolution().getTempID();
                    ShortAnswerSpot spot = idToSpot.get(spotTempId);
                    ShortAnswerSolution solution = idToSolution.get(solutionTempId);
                    if (spot == null || solution == null) {
                        throw new BadRequestAlertException("Could not resolve short answer mappings", ENTITY_NAME, "invalidMappings");
                    }
                    mapping.setSpot(spot);
                    mapping.setSolution(solution);
                }
            }
        }
    }

    /**
     * Creates a new quiz exercise, handling validation, file processing, saving, and related updates.
     *
     * @param quizExercise the quiz exercise domain object to create
     * @param files        the files for drag and drop questions (optional)
     * @param isExam       true if creating for an exam, false for a course
     * @return the created and saved quiz exercise
     * @throws IOException if there is an error handling the files
     */
    public QuizExercise createQuizExercise(QuizExercise quizExercise, List<MultipartFile> files, boolean isExam) throws IOException {
        resolveQuizQuestionMappings(quizExercise);
        if (!quizExercise.isValid()) {
            throw new BadRequestAlertException("The quiz exercise is invalid", ENTITY_NAME, "invalidQuiz");
        }
        quizExercise.validateGeneralSettings();
        handleDndQuizFileCreation(quizExercise, files);
        QuizExercise result = save(quizExercise);
        if (!isExam) {
            channelService.createExerciseChannel(result, Optional.ofNullable(quizExercise.getChannelName()));
        }
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(result));
        return result;
    }
}
