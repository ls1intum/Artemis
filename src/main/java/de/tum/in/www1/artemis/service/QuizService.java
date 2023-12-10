package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.exception.FilePathParsingException;
import de.tum.in.www1.artemis.repository.DragAndDropMappingRepository;
import de.tum.in.www1.artemis.repository.ShortAnswerMappingRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public abstract class QuizService<T extends QuizConfiguration> {

    public static final String ENTITY_NAME = "QuizConfiguration";

    private final DragAndDropMappingRepository dragAndDropMappingRepository;

    private final ShortAnswerMappingRepository shortAnswerMappingRepository;

    private final FileService fileService;

    private final FilePathService filePathService;

    /**
     * Save the given QuizConfiguration to the database according to the implementor.
     *
     * @param quizConfiguration the QuizConfiguration to be saved.
     * @return the saved QuizConfiguration
     */
    protected abstract T saveAndFlush(T quizConfiguration);

    protected QuizService(DragAndDropMappingRepository dragAndDropMappingRepository, ShortAnswerMappingRepository shortAnswerMappingRepository, FileService fileService,
            FilePathService filePathService) {
        this.dragAndDropMappingRepository = dragAndDropMappingRepository;
        this.shortAnswerMappingRepository = shortAnswerMappingRepository;
        this.fileService = fileService;
        this.filePathService = filePathService;
    }

    /**
     * Save the given QuizConfiguration
     *
     * @param quizConfiguration the QuizConfiguration to be saved
     * @return saved QuizConfiguration
     */
    public T save(T quizConfiguration) {
        // fix references in all questions (step 1/2)
        for (var quizQuestion : quizConfiguration.getQuizQuestions()) {
            if (quizQuestion.getQuizQuestionStatistic() == null) {
                quizQuestion.initializeStatistic();
            }

            if (quizQuestion instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                fixReferenceMultipleChoice(multipleChoiceQuestion);
            }
            else if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                fixReferenceDragAndDrop(dragAndDropQuestion);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                fixReferenceShortAnswer(shortAnswerQuestion);
            }
        }

        T savedQuizConfiguration = saveAndFlush(quizConfiguration);

        // fix references in all drag and drop questions and short answer questions (step 2/2)
        for (QuizQuestion quizQuestion : savedQuizConfiguration.getQuizQuestions()) {
            if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                // restore references from index after save
                restoreCorrectMappingsFromIndicesDragAndDrop(dragAndDropQuestion);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                // restore references from index after save
                restoreCorrectMappingsFromIndicesShortAnswer(shortAnswerQuestion);
            }
        }

        return savedQuizConfiguration;
    }

    /**
     * Fix references of Multiple Choice Question before saving to database
     *
     * @param multipleChoiceQuestion the MultipleChoiceQuestion which references are to be fixed
     */
    private void fixReferenceMultipleChoice(MultipleChoiceQuestion multipleChoiceQuestion) {
        MultipleChoiceQuestionStatistic multipleChoiceQuestionStatistic = (MultipleChoiceQuestionStatistic) multipleChoiceQuestion.getQuizQuestionStatistic();
        fixComponentReference(multipleChoiceQuestion, multipleChoiceQuestion.getAnswerOptions(), answerOption -> {
            multipleChoiceQuestionStatistic.addAnswerOption(answerOption);
            return null;
        });
        removeCounters(multipleChoiceQuestion.getAnswerOptions(), multipleChoiceQuestionStatistic.getAnswerCounters());
    }

    /**
     * Fix references of Drag and Drop Question before saving to database
     *
     * @param dragAndDropQuestion the DragAndDropQuestion which references are to be fixed
     */
    private void fixReferenceDragAndDrop(DragAndDropQuestion dragAndDropQuestion) {
        DragAndDropQuestionStatistic dragAndDropQuestionStatistic = (DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic();
        fixComponentReference(dragAndDropQuestion, dragAndDropQuestion.getDropLocations(), dropLocation -> {
            dragAndDropQuestionStatistic.addDropLocation(dropLocation);
            return null;
        });
        removeCounters(dragAndDropQuestion.getDropLocations(), dragAndDropQuestionStatistic.getDropLocationCounters());
        saveCorrectMappingsInIndicesDragAndDrop(dragAndDropQuestion);
    }

    /**
     * Fix references of Short Answer Question before saving to database
     *
     * @param shortAnswerQuestion the ShortAnswerQuestion which references are to be fixed
     */
    private void fixReferenceShortAnswer(ShortAnswerQuestion shortAnswerQuestion) {
        ShortAnswerQuestionStatistic shortAnswerQuestionStatistic = (ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic();
        fixComponentReference(shortAnswerQuestion, shortAnswerQuestion.getSpots(), shortAnswerSpot -> {
            shortAnswerQuestionStatistic.addSpot(shortAnswerSpot);
            return null;
        });
        removeCounters(shortAnswerQuestion.getSpots(), shortAnswerQuestionStatistic.getShortAnswerSpotCounters());
        saveCorrectMappingsInIndicesShortAnswer(shortAnswerQuestion);
    }

    /**
     * Fix reference of the given components which belong to the given quizQuestion and apply the callback for each component.
     *
     * @param quizQuestion the QuizQuestion of which the given components belong to
     * @param components   the QuizQuestionComponent of which the references are to be fixed
     * @param callback     the Function that is applied for each given component
     */
    private <C extends QuizQuestionComponent<Q>, Q extends QuizQuestion> void fixComponentReference(Q quizQuestion, Collection<C> components, Function<C, Void> callback) {
        for (C component : components) {
            component.setQuestion(quizQuestion);
            callback.apply(component);
        }
    }

    /**
     * Remove statisticComponents that are not associated with any of the given components.
     *
     * @param components          the Collection of QuizQuestionComponent to be checked
     * @param statisticComponents the Collection of QuizQuestionStatisticComponent to be removed
     */
    private <C extends QuizQuestionComponent<Q>, Q extends QuizQuestion, SC extends QuizQuestionStatisticComponent<S, C, Q>, S extends QuizQuestionStatistic> void removeCounters(
            Collection<C> components, Collection<SC> statisticComponents) {
        Set<SC> toDelete = new HashSet<>();
        for (SC statisticComponent : statisticComponents) {
            if (statisticComponent.getId() != null) {
                if (!(components.contains(statisticComponent.getQuizQuestionComponent()))) {
                    statisticComponent.setQuizQuestionComponent(null);
                    toDelete.add(statisticComponent);
                }
            }
        }
        statisticComponents.removeAll(toDelete);
    }

    /**
     * remove dragItem and dropLocation from correct mappings and set dragItemIndex and dropLocationIndex instead
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void saveCorrectMappingsInIndicesDragAndDrop(DragAndDropQuestion dragAndDropQuestion) {
        List<DragAndDropMapping> mappingsToBeRemoved = new ArrayList<>();
        for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
            // check for NullPointers
            if (mapping.getDragItem() == null || mapping.getDropLocation() == null) {
                mappingsToBeRemoved.add(mapping);
                continue;
            }

            // drag item index
            boolean dragItemFound = findComponent(dragAndDropQuestion.getDragItems(), mapping.getDragItem(), questionDragItem -> {
                mapping.setDragItemIndex(dragAndDropQuestion.getDragItems().indexOf(questionDragItem));
                mapping.setDragItem(null);
                return null;
            });

            // drop location index
            boolean dropLocationFound = findComponent(dragAndDropQuestion.getDropLocations(), mapping.getDropLocation(), questionDropLocation -> {
                mapping.setDropLocationIndex(dragAndDropQuestion.getDropLocations().indexOf(questionDropLocation));
                mapping.setDropLocation(null);
                return null;
            });

            // if one of them couldn't be found, remove the mapping entirely
            if (!dragItemFound || !dropLocationFound) {
                mappingsToBeRemoved.add(mapping);
            }
        }

        for (DragAndDropMapping mapping : mappingsToBeRemoved) {
            dragAndDropQuestion.removeCorrectMapping(mapping);
        }
    }

    /**
     * remove solutions and spots from correct mappings and set solutionIndex and spotIndex instead
     *
     * @param shortAnswerQuestion the question for which to perform these actions
     */
    private void saveCorrectMappingsInIndicesShortAnswer(ShortAnswerQuestion shortAnswerQuestion) {
        List<ShortAnswerMapping> mappingsToBeRemoved = new ArrayList<>();
        for (ShortAnswerMapping mapping : shortAnswerQuestion.getCorrectMappings()) {
            // check for NullPointers
            if (mapping.getSolution() == null || mapping.getSpot() == null) {
                mappingsToBeRemoved.add(mapping);
                continue;
            }

            // solution index
            boolean solutionFound = findComponent(shortAnswerQuestion.getSolutions(), mapping.getSolution(), questionSolution -> {
                mapping.setShortAnswerSolutionIndex(shortAnswerQuestion.getSolutions().indexOf(questionSolution));
                mapping.setSolution(null);
                return null;
            });

            // replace spot
            boolean spotFound = findComponent(shortAnswerQuestion.getSpots(), mapping.getSpot(), questionSpot -> {
                mapping.setShortAnswerSpotIndex(shortAnswerQuestion.getSpots().indexOf(questionSpot));
                mapping.setSpot(null);
                return null;
            });

            // if one of them couldn't be found, remove the mapping entirely
            if (!solutionFound || !spotFound) {
                mappingsToBeRemoved.add(mapping);
            }
        }

        for (ShortAnswerMapping mapping : mappingsToBeRemoved) {
            shortAnswerQuestion.removeCorrectMapping(mapping);
        }
    }

    /**
     * Find the given componentToBeSearched in components. If found, apply the given foundCallback.
     *
     * @param components            the collection of QuizQuestionComponent to be searched from
     * @param componentToBeSearched the QuizQuestionComponent to be searched
     * @param foundCallback         the callback to be applied if the given componentToBeSearched is found
     * @return true if the given componentToBeSearched is found or false otherwise
     */
    private <C extends QuizQuestionComponent<Q>, Q extends QuizQuestion> boolean findComponent(Collection<C> components, C componentToBeSearched, Function<C, Void> foundCallback) {
        for (C component : components) {
            if (componentToBeSearched.equals(component)) {
                foundCallback.apply(component);
                return true;
            }
        }
        return false;
    }

    /**
     * restore dragItem and dropLocation for correct mappings using dragItemIndex and dropLocationIndex
     *
     * @param dragAndDropQuestion the question for which to perform these actions
     */
    private void restoreCorrectMappingsFromIndicesDragAndDrop(DragAndDropQuestion dragAndDropQuestion) {
        for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
            // drag item
            mapping.setDragItem(dragAndDropQuestion.getDragItems().get(mapping.getDragItemIndex()));
            // drop location
            mapping.setDropLocation(dragAndDropQuestion.getDropLocations().get(mapping.getDropLocationIndex()));
            // set question
            mapping.setQuestion(dragAndDropQuestion);
            // save mapping
            dragAndDropMappingRepository.save(mapping);
        }
    }

    /**
     * restore solution and spots for correct mappings using solutionIndex and spotIndex
     *
     * @param shortAnswerQuestion the question for which to perform these actions
     */
    private void restoreCorrectMappingsFromIndicesShortAnswer(ShortAnswerQuestion shortAnswerQuestion) {
        for (ShortAnswerMapping mapping : shortAnswerQuestion.getCorrectMappings()) {
            // solution
            mapping.setSolution(shortAnswerQuestion.getSolutions().get(mapping.getShortAnswerSolutionIndex()));
            // spot
            mapping.setSpot(shortAnswerQuestion.getSpots().get(mapping.getShortAnswerSpotIndex()));
            // set question
            mapping.setQuestion(shortAnswerQuestion);
            // save mapping
            shortAnswerMappingRepository.save(mapping);
        }
    }

    /**
     * Verifies that for DragAndDropQuestions all files are present and valid. Saves the files and updates the configuration accordingly.
     *
     * @param quizConfiguration the quiz configuration to create
     * @param files             the provided files
     */
    public void handleDndQuizFileCreation(QuizConfiguration quizConfiguration, List<MultipartFile> files) throws IOException {
        List<MultipartFile> nullsafeFiles = files == null ? new ArrayList<>() : files;
        validateQuizConfigurationFiles(quizConfiguration, nullsafeFiles, true);
        Map<String, MultipartFile> fileMap = nullsafeFiles.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, file -> file));

        for (var question : quizConfiguration.getQuizQuestions()) {
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
                saveDndDragItemPicture(dragItem, fileMap);
            }
        }
    }

    /**
     * Verifies that for DragAndDropQuestions all files are present and valid. Saves the files and updates the configuration accordingly.
     * Ignores unchanged paths and removes deleted background images.
     *
     * @param updatedConfiguration  the updated quiz configuration
     * @param originalConfiguration the original quiz configuration
     * @param files                 the provided files
     */
    public void handleDndQuizFileUpdates(QuizConfiguration updatedConfiguration, QuizConfiguration originalConfiguration, List<MultipartFile> files) throws IOException {
        List<MultipartFile> nullsafeFiles = files == null ? new ArrayList<>() : files;
        validateQuizConfigurationFiles(updatedConfiguration, nullsafeFiles, false);
        // Find old drag items paths
        Set<String> oldPaths = getAllPathsFromDragAndDropQuestionsOfExercise(originalConfiguration);
        // Init files to remove with all old paths
        Set<String> filesToRemove = new HashSet<>(oldPaths);

        Map<String, MultipartFile> fileMap = nullsafeFiles.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, file -> file));

        for (var question : updatedConfiguration.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                handleDndQuestionUpdate(dragAndDropQuestion, oldPaths, filesToRemove, fileMap, dragAndDropQuestion);
            }
        }

        fileService.deleteFiles(filesToRemove.stream().map(Paths::get).toList());
    }

    private Set<String> getAllPathsFromDragAndDropQuestionsOfExercise(QuizConfiguration quizConfiguration) {
        Set<String> paths = new HashSet<>();
        for (var question : quizConfiguration.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (dragAndDropQuestion.getBackgroundFilePath() != null) {
                    paths.add(dragAndDropQuestion.getBackgroundFilePath());
                }
                paths.addAll(dragAndDropQuestion.getDragItems().stream().map(DragItem::getPictureFilePath).filter(Objects::nonNull).collect(Collectors.toSet()));
            }
        }
        return paths;
    }

    private void handleDndQuestionUpdate(DragAndDropQuestion dragAndDropQuestion, Set<String> oldPaths, Set<String> filesToRemove, Map<String, MultipartFile> fileMap,
            DragAndDropQuestion questionUpdate) throws IOException {
        String newBackgroundPath = dragAndDropQuestion.getBackgroundFilePath();

        // Don't do anything if the path is null because it's getting removed
        if (newBackgroundPath != null) {
            if (oldPaths.contains(newBackgroundPath)) {
                // Path didn't change
                filesToRemove.remove(dragAndDropQuestion.getBackgroundFilePath());
            }
            else {
                // Path changed and file was provided
                saveDndQuestionBackground(dragAndDropQuestion, fileMap, questionUpdate.getId());
            }
        }

        for (var dragItem : dragAndDropQuestion.getDragItems()) {
            String newDragItemPath = dragItem.getPictureFilePath();
            if (dragItem.getPictureFilePath() != null && !oldPaths.contains(newDragItemPath)) {
                // Path changed and file was provided
                saveDndDragItemPicture(dragItem, fileMap);
            }
        }
    }

    /**
     * Verifies that the provided files match the provided filenames in the configuration entity.
     *
     * @param quizConfiguration the quiz configuration to validate
     * @param providedFiles     the provided files to validate
     * @param isCreate          On create all files get validated, on update only changed files get validated
     */
    public void validateQuizConfigurationFiles(QuizConfiguration quizConfiguration, @Nonnull List<MultipartFile> providedFiles, boolean isCreate) {
        long fileCount = providedFiles.size();
        Set<String> configurationFileNames = getAllPathsFromDragAndDropQuestionsOfExercise(quizConfiguration);
        Set<String> newFileNames = isCreate ? configurationFileNames : configurationFileNames.stream().filter(fileNameOrUri -> {
            try {
                return !Files.exists(filePathService.actualPathForPublicPathOrThrow(URI.create(fileNameOrUri)));
            }
            catch (FilePathParsingException e) {
                // File is not in the internal API format and hence expected to be a new file
                return true;
            }
        }).collect(Collectors.toSet());

        if (newFileNames.size() != fileCount) {
            throw new BadRequestAlertException("Number of files does not match number of new drag items and backgrounds", ENTITY_NAME, null);
        }
        Set<String> providedFileNames = providedFiles.stream().map(MultipartFile::getOriginalFilename).collect(Collectors.toSet());
        if (!newFileNames.equals(providedFileNames)) {
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

        question.setBackgroundFilePath(saveDragAndDropImage(FilePathService.getDragAndDropBackgroundFilePath(), file, questionId).toString());
    }

    /**
     * Saves the picture of a drag item without saving the drag item itself
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

        dragItem.setPictureFilePath(saveDragAndDropImage(FilePathService.getDragItemFilePath(), file, null).toString());
    }

    /**
     * Saves an image for an DragAndDropQuestion. Either a background image or a drag item image.
     *
     * @return the public path of the saved image
     */
    private URI saveDragAndDropImage(Path basePath, MultipartFile file, @Nullable Long entityId) throws IOException {
        String clearFileExtension = FileService.sanitizeFilename(FilenameUtils.getExtension(Objects.requireNonNull(file.getOriginalFilename())));
        Path savePath = fileService.generateFilePath("dnd_image_", clearFileExtension, basePath);
        FileUtils.copyToFile(file.getInputStream(), savePath.toFile());
        return filePathService.publicPathForActualPathOrThrow(savePath, entityId);
    }
}
