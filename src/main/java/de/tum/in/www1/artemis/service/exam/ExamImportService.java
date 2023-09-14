package de.tum.in.www1.artemis.service.exam;

import java.util.*;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseImportService;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseService;
import de.tum.in.www1.artemis.web.rest.errors.ExamConfigurationException;

@Service
public class ExamImportService {

    private final TextExerciseImportService textExerciseImportService;

    private final TextExerciseRepository textExerciseRepository;

    private final ModelingExerciseImportService modelingExerciseImportService;

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ExamRepository examRepository;

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final QuizExerciseImportService quizExerciseImportService;

    private final CourseRepository courseRepository;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseImportService programmingExerciseImportService;

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final FileUploadExerciseImportService fileUploadExerciseImportService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ChannelService channelService;

    public ExamImportService(TextExerciseImportService textExerciseImportService, TextExerciseRepository textExerciseRepository,
            ModelingExerciseImportService modelingExerciseImportService, ModelingExerciseRepository modelingExerciseRepository, ExamRepository examRepository,
            ExerciseGroupRepository exerciseGroupRepository, QuizExerciseRepository quizExerciseRepository, QuizExerciseImportService importQuizExercise,
            CourseRepository courseRepository, ProgrammingExerciseService programmingExerciseService1, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseImportService programmingExerciseImportService, FileUploadExerciseRepository fileUploadExerciseRepository,
            FileUploadExerciseImportService fileUploadExerciseImportService, GradingCriterionRepository gradingCriterionRepository,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ChannelService channelService) {
        this.textExerciseImportService = textExerciseImportService;
        this.textExerciseRepository = textExerciseRepository;
        this.modelingExerciseImportService = modelingExerciseImportService;
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.examRepository = examRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizExerciseImportService = importQuizExercise;
        this.courseRepository = courseRepository;
        this.programmingExerciseService = programmingExerciseService1;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseImportService = programmingExerciseImportService;
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.fileUploadExerciseImportService = fileUploadExerciseImportService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.channelService = channelService;
    }

    /**
     * Imports the given Exam with ExerciseGroups and Exercises to the given target Course
     *
     * @param examToCopy     the exam which should be copied together with exercise groups and exercises
     * @param targetCourseId the course to which the exam should be imported
     * @return the copied Exam with Exercise Groups and Exercises
     */
    public Exam importExamWithExercises(Exam examToCopy, long targetCourseId) {

        Course targetCourse = courseRepository.findByIdElseThrow(targetCourseId);

        preCheckProgrammingExercisesForTitleAndShortNameUniqueness(examToCopy.getExerciseGroups(), targetCourse.getShortName());

        // 1st: Save the exam without exercises to the database and create a new channel for the exam
        List<ExerciseGroup> exerciseGroupsToCopy = examToCopy.getExerciseGroups();
        examToCopy.setExerciseGroups(new ArrayList<>());
        Exam examCopied = createCopyOfExamWithoutConductionSpecificAttributes(examToCopy, targetCourse);

        // 2nd: Copy the exercise groups to the exam
        copyExerciseGroupsWithExercisesToExam(exerciseGroupsToCopy, examCopied);
        channelService.createExamChannel(examCopied, Optional.ofNullable(examToCopy.getChannelName()));
        return examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examCopied.getId());
    }

    /**
     * Imports the given ExerciseGroups with exercises to the given exam
     *
     * @param exerciseGroupsToCopy the Exercise Groups to be imported
     * @param targetExamId         the target exam id
     * @param courseId             the associated course of the exam
     * @return a List of all Exercise Groups of the target exam
     */
    public List<ExerciseGroup> importExerciseGroupsWithExercisesToExistingExam(List<ExerciseGroup> exerciseGroupsToCopy, long targetExamId, long courseId) {
        Course targetCourse = courseRepository.findByIdElseThrow(courseId);

        preCheckProgrammingExercisesForTitleAndShortNameUniqueness(exerciseGroupsToCopy, targetCourse.getShortName());

        Exam targetExam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(targetExamId);

        // The Exam is used to ensure the connection ExerciseGroups <-> Exam
        copyExerciseGroupsWithExercisesToExam(exerciseGroupsToCopy, targetExam);

        return exerciseGroupRepository.findWithExamAndExercisesByExamId(targetExamId);
    }

    /**
     * Checks if programming exercises passed to the method have duplicated titles or short names. When a duplication is found,
     * the title / short name is removed from the corresponding exercises. After this method has been called, no programming exercise in
     * exerciseGroups has a duplicated title / short name.
     *
     * @param programmingExercises programming exercises we have to check for duplications
     * @param checkTitle           if the title should be checked for duplications. In case it is set to false, the short names are checked
     * @return true if any duplications were found and taken care of
     */
    private boolean checkForAndRemoveDuplicatedTitlesAndShortNames(List<Exercise> programmingExercises, boolean checkTitle) {
        List<String> titlesOrShortNames = programmingExercises.stream().map(checkTitle ? BaseExercise::getTitle : BaseExercise::getShortName).toList();
        Set<String> uniqueTitlesOrShortNames = new HashSet<>(titlesOrShortNames);

        // check if there are duplications
        if (titlesOrShortNames.size() != uniqueTitlesOrShortNames.size()) {
            // go through all exercises and use the uniqueTitlesOrShortNames set to see which ones are duplicated. When an
            // exercise is found, the title / shortName is removed and the corresponding entry is removed from the set
            programmingExercises.forEach(exercise -> {
                String searchFor = checkTitle ? exercise.getTitle() : exercise.getShortName();
                if (!uniqueTitlesOrShortNames.contains(searchFor)) {
                    if (checkTitle) {
                        exercise.setTitle("");
                    }
                    else {
                        exercise.setShortName("");
                    }
                }
                else {
                    uniqueTitlesOrShortNames.remove(searchFor);
                }
            });
            return true;
        }
        return false;
    }

    /**
     * Checks if a project with the same key and name already exists on VCS / CI. The number of such occurrences is counted
     *
     * @param programmingExercises that are checked for an existing project
     * @param courseShortName      the short name of the course the exercises will be imported into
     * @return the number of exercises that need to be renamed in the client
     */
    private int checkForExistingProjectAndRemoveTitleShortName(List<Exercise> programmingExercises, String courseShortName) {
        // Count how many programming exercises have conflicts with VCS / CI due to the project with the same key / name already existing
        // Iterate over all programming exercises
        return programmingExercises.stream().mapToInt(exercise -> {
            // Method to check, if the project already exists.
            boolean projectExists = programmingExerciseService.preCheckProjectExistsOnVCSOrCI((ProgrammingExercise) exercise, courseShortName);
            if (projectExists) {
                // If the project already exists the short name and title are removed. It has to be set in the client again
                exercise.setShortName("");
                exercise.setTitle("");
            }
            return projectExists ? 1 : 0;
        }).sum();
    }

    /**
     * Checks that all programming exercises of the given exercise group have a unique title and short name.
     * Additionally, checks if an exercise with the same project key or name already exists on the VCS / CI.
     * In case of an invalid configuration, the exam is sent back to the client with the title / short name removed, wherever a new one must be chosen
     *
     * @param exerciseGroups  the list of all exercises (not only programming) to be checked
     * @param courseShortName the short name of the course the exercises will be imported into
     * @throws ExamConfigurationException in case of duplicated titles / short names or if one or more programming exercise project keys are not unique
     */
    private void preCheckProgrammingExercisesForTitleAndShortNameUniqueness(List<ExerciseGroup> exerciseGroups, String courseShortName) {
        List<Exercise> programmingExercises = exerciseGroups.stream().flatMap(group -> group.getExercises().stream())
                .filter(exercise -> exercise.getExerciseType() == ExerciseType.PROGRAMMING).toList();

        // check for duplicated titles
        boolean duplicatedTitles = checkForAndRemoveDuplicatedTitlesAndShortNames(programmingExercises, true);
        if (duplicatedTitles) {
            throw new ExamConfigurationException(exerciseGroups, 0, "duplicatedProgrammingExerciseTitle");
        }
        // check for duplicated short names
        boolean duplicatedShortNames = checkForAndRemoveDuplicatedTitlesAndShortNames(programmingExercises, false);
        if (duplicatedShortNames) {
            throw new ExamConfigurationException(exerciseGroups, 0, "duplicatedProgrammingExerciseShortName");
        }
        // check for existing project on VCS / CI
        int numberOfInvalidProgrammingExercises = checkForExistingProjectAndRemoveTitleShortName(programmingExercises, courseShortName);
        if (numberOfInvalidProgrammingExercises > 0) {
            throw new ExamConfigurationException(exerciseGroups, numberOfInvalidProgrammingExercises, "invalidKey");
        }
    }

    /**
     * Method to create a copy of the given exerciseGroups and their exercises
     *
     * @param exerciseGroupsToCopy the exerciseGroups to be copied
     * @param targetExam           the nex exam to which the new exerciseGroups should be linked
     */
    private void copyExerciseGroupsWithExercisesToExam(List<ExerciseGroup> exerciseGroupsToCopy, Exam targetExam) {
        // Only exercise groups with at least one exercise should be imported.
        List<ExerciseGroup> filteredExerciseGroupsToCopy = exerciseGroupsToCopy.stream().filter(exerciseGroup -> !exerciseGroup.getExercises().isEmpty()).toList();
        // If no exercise group is existent, we can aboard the process
        if (filteredExerciseGroupsToCopy.isEmpty()) {
            return;
        }

        // Create a copy of each exercise group and add them to the exam
        filteredExerciseGroupsToCopy.forEach(exerciseGroupToCopy -> {
            ExerciseGroup exerciseGroupCopied = new ExerciseGroup();
            exerciseGroupCopied.setTitle(exerciseGroupToCopy.getTitle());
            exerciseGroupCopied.setIsMandatory(exerciseGroupToCopy.getIsMandatory());
            targetExam.addExerciseGroup(exerciseGroupCopied);
        });

        examRepository.save(targetExam);

        // We need to take the exercise groups from the exam to ensure the correct connection exam <-> exercise group
        // subList(from,to) needs the arguments in the following way: [from, to)
        int indexTo = targetExam.getExerciseGroups().size();
        int indexFrom = indexTo - filteredExerciseGroupsToCopy.size();
        List<ExerciseGroup> exerciseGroupsCopied = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(targetExam.getId()).getExerciseGroups().subList(indexFrom,
                indexTo);

        for (int index = 0; index < exerciseGroupsCopied.size(); index++) {
            addExercisesToExerciseGroup(filteredExerciseGroupsToCopy.get(index), exerciseGroupsCopied.get(index));
        }
    }

    /**
     * Helper method to create a copy of the given Exercises within one given exercise group and attaching them to the
     * given new exercise groups
     *
     * @param exerciseGroupToCopy the exercise group to copy
     * @param exerciseGroupCopied the copied exercise group, i.e. the ones attached to the new exam
     */
    private void addExercisesToExerciseGroup(ExerciseGroup exerciseGroupToCopy, ExerciseGroup exerciseGroupCopied) {
        // Copy each exercise within the existing Exercise Group
        exerciseGroupToCopy.getExercises().forEach(exerciseToCopy -> {

            // We need to set the new Exercise Group to the old exercise, so the new exercise group is correctly set for the new exercise
            exerciseToCopy.setExerciseGroup(exerciseGroupCopied);
            Exercise exerciseCopied = null;

            switch (exerciseToCopy.getExerciseType()) {
                case MODELING -> {
                    final Optional<ModelingExercise> optionalOriginalModellingExercise = modelingExerciseRepository
                            .findByIdWithExampleSubmissionsAndResults(exerciseToCopy.getId());
                    // We do not want to abort the whole exam import process, we only skip the relevant exercise
                    if (optionalOriginalModellingExercise.isEmpty()) {
                        break;
                    }
                    exerciseCopied = modelingExerciseImportService.importModelingExercise(optionalOriginalModellingExercise.get(), (ModelingExercise) exerciseToCopy);
                }

                case TEXT -> {
                    final Optional<TextExercise> optionalOriginalTextExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResults(exerciseToCopy.getId());
                    if (optionalOriginalTextExercise.isEmpty()) {
                        break;
                    }
                    exerciseCopied = textExerciseImportService.importTextExercise(optionalOriginalTextExercise.get(), (TextExercise) exerciseToCopy);
                }

                case PROGRAMMING -> {
                    final Optional<ProgrammingExercise> optionalOriginalProgrammingExercise = programmingExerciseRepository
                            .findByIdWithEagerTestCasesStaticCodeAnalysisCategoriesHintsAndTemplateAndSolutionParticipationsAndAuxRepos(exerciseToCopy.getId());
                    if (optionalOriginalProgrammingExercise.isEmpty()) {
                        break;
                    }
                    var originalProgrammingExercise = optionalOriginalProgrammingExercise.get();
                    // Fetching the tasks separately, as putting it in the query above leads to Hibernate duplicating the tasks.
                    var templateTasks = programmingExerciseTaskRepository.findByExerciseIdWithTestCases(originalProgrammingExercise.getId());
                    originalProgrammingExercise.setTasks(new ArrayList<>(templateTasks));

                    prepareProgrammingExerciseForExamImport((ProgrammingExercise) exerciseToCopy);
                    exerciseCopied = programmingExerciseImportService.importProgrammingExercise(originalProgrammingExercise, (ProgrammingExercise) exerciseToCopy, false, false);
                }

                case FILE_UPLOAD -> {
                    final Optional<FileUploadExercise> optionalFileUploadExercise = fileUploadExerciseRepository.findById(exerciseToCopy.getId());
                    if (optionalFileUploadExercise.isEmpty()) {
                        break;
                    }
                    exerciseCopied = fileUploadExerciseImportService.importFileUploadExercise(optionalFileUploadExercise.get(), (FileUploadExercise) exerciseToCopy);
                }

                case QUIZ -> {
                    final Optional<QuizExercise> optionalOriginalQuizExercise = quizExerciseRepository.findById(exerciseToCopy.getId());
                    if (optionalOriginalQuizExercise.isEmpty()) {
                        break;
                    }
                    exerciseCopied = quizExerciseImportService.importQuizExercise(optionalOriginalQuizExercise.get(), (QuizExercise) exerciseToCopy);
                }

            }
            // Attach the newly created Exercise to the new Exercise Group only if the importing was sucessful
            if (exerciseCopied != null) {
                exerciseGroupCopied.addExercise(exerciseCopied);
            }
        });
        exerciseGroupRepository.save(exerciseGroupCopied);
    }

    /**
     * Prepares a Programming Exercise for the import by setting irrelevant data to null.
     * Additionally, the grading criteria is loaded and attached to the exercise, as this needs to be released before the import
     *
     * @param newExercise The new exercise which should be prepared for the import
     */
    private void prepareProgrammingExerciseForExamImport(final ProgrammingExercise newExercise) {

        // we do not support the following values as part of exam exercises
        newExercise.setBuildAndTestStudentSubmissionsAfterDueDate(null);
        newExercise.setSubmissionPolicy(null);
        newExercise.setStartDate(null);
        newExercise.setReleaseDate(null);
        newExercise.setDueDate(null);
        newExercise.setAssessmentDueDate(null);
        newExercise.setExampleSolutionPublicationDate(null);

        // Fetch grading criterion into exercise. For course exercises, this is performed before sending the exercise to the client.
        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(newExercise.getId());
        newExercise.setGradingCriteria(gradingCriteria);

        newExercise.forceNewProjectKey();
    }

    /**
     * Helper method to create a copy of the given Exam without conduction specific attributes
     *
     * @param examToCopy the exam to be copied
     * @return a copy of the given exam without conduction specific attributes
     */
    private Exam createCopyOfExamWithoutConductionSpecificAttributes(Exam examToCopy, Course targetCourse) {
        examToCopy.setExerciseGroups(new ArrayList<>());
        examToCopy.setExamUsers(new HashSet<>());
        examToCopy.setStudentExams(new HashSet<>());
        examToCopy.setId(null);
        examToCopy.setCourse(targetCourse);
        return examRepository.save(examToCopy);
    }
}
