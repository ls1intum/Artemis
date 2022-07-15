import { ChartCategoryFilter } from 'app/shared/chart/chart-category-filter';
import { TestBed } from '@angular/core/testing';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import dayjs from 'dayjs/esm';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { InitializationState } from 'app/entities/participation/participation.model';
import { AssessmentType } from 'app/entities/assessment-type.model';

const generateExerciseCategory = (type: ExerciseType, index: number) => {
    return { category: type + index.toString(), color: '#9f34eb' };
};

const modelingExercises = [
    {
        type: 'modeling',
        id: 192,
        title: 'test 17.06. 1',
        dueDate: dayjs('2019-06-17T09:47:12+02:00'),
        assessmentDueDate: dayjs('2019-06-17T09:55:17+02:00'),
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        maxPoints: 12.0,
        studentParticipations: [
            {
                id: 248,
                initializationState: 'FINISHED',
                initializationDate: dayjs('2019-06-17T09:29:34.908+02:00'),
                presentationScore: 2,
                student: {
                    id: 9,
                    login: 'artemis_test_user_1',
                    firstName: 'Artemis Test User 1',
                    email: 'krusche+testuser_1@in.tum.de',
                    activated: true,
                    langKey: 'en',
                },
            },
        ],
        diagramType: 'ClassDiagram',
        numberOfSubmissions: new DueDateStat(),
        totalNumberOfAssessments: new DueDateStat(),
        numberOfComplaints: 0,
        presentationScoreEnabled: true,
    },
    {
        type: 'modeling',
        id: 193,
        title: 'test 17.06. 2',
        dueDate: dayjs('2019-06-17T17:50:08+02:00'),
        assessmentDueDate: dayjs('2019-06-17T17:51:13+02:00'),
        includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
        maxPoints: 12.0,
        studentParticipations: [
            {
                id: 249,
                initializationState: 'FINISHED',
                initializationDate: dayjs('2019-06-18T10:53:27.997+02:00'),
                student: {
                    id: 9,
                    login: 'artemis_test_user_1',
                    firstName: 'Artemis Test User 1',
                    email: 'krusche+testuser_1@in.tum.de',
                    activated: true,
                    langKey: 'en',
                },
            },
        ],
        diagramType: 'ClassDiagram',
        numberOfSubmissions: new DueDateStat(),
        totalNumberOfAssessments: new DueDateStat(),
        numberOfComplaints: 0,
    },
    {
        type: 'modeling',
        id: 194,
        title: 'test 18.06. 1',
        dueDate: dayjs('2019-06-18T07:56:41+02:00'),
        includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS,
        maxPoints: 12.0,
        studentParticipations: [],
        diagramType: 'ClassDiagram',
        numberOfSubmissions: new DueDateStat(),
        totalNumberOfAssessments: new DueDateStat(),
        numberOfComplaints: 0,
    },
    {
        type: 'modeling',
        id: 191,
        title: 'Until 18:20',
        dueDate: dayjs('2019-06-16T18:15:03+02:00'),
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        assessmentDueDate: dayjs('2019-06-16T18:30:57+02:00'),
        maxPoints: 12.0,
        studentParticipations: [
            {
                id: 246,
                initializationState: 'FINISHED',
                initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
                results: [
                    {
                        id: 231,
                        completionDate: dayjs('2019-06-17T09:30:17.761+02:00'),
                        successful: false,
                        score: 92,
                        rated: true,
                        hasFeedback: false,
                        assessmentType: 'MANUAL',
                        hasComplaint: false,
                    },
                ],
                student: {
                    id: 9,
                    login: 'artemis_test_user_1',
                    firstName: 'Artemis Test User 1',
                    email: 'krusche+testuser_1@in.tum.de',
                    activated: true,
                    langKey: 'en',
                },
            },
        ],
        diagramType: 'ClassDiagram',
        numberOfSubmissions: new DueDateStat(),
        totalNumberOfAssessments: new DueDateStat(),
        numberOfComplaints: 0,
    },
    {
        type: 'modeling',
        id: 195,
        title: 'Until 18:20 too',
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        dueDate: dayjs('2019-06-16T18:15:03+02:00'),
        assessmentDueDate: dayjs('2019-06-16T18:30:57+02:00'),
        maxPoints: 12.0,
        studentParticipations: [
            {
                id: 249,
                initializationState: 'FINISHED',
                initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
                results: [
                    {
                        id: 230,
                        completionDate: dayjs('2019-06-17T09:30:17.761+02:00'),
                        successful: false,
                        score: 75,
                        rated: true,
                        hasFeedback: false,
                        assessmentType: 'MANUAL',
                        hasComplaint: false,
                    },
                ],
                student: {
                    id: 9,
                    login: 'artemis_test_user_1',
                    firstName: 'Artemis Test User 1',
                    email: 'krusche+testuser_1@in.tum.de',
                    activated: true,
                    langKey: 'en',
                },
            },
        ],
        diagramType: 'ClassDiagram',
        numberOfSubmissions: new DueDateStat(),
        totalNumberOfAssessments: new DueDateStat(),
        numberOfComplaints: 0,
    },
] as ModelingExercise[];

const fileUploadExercise: FileUploadExercise = {
    type: ExerciseType.FILE_UPLOAD,
    id: 196,
    title: 'Until 18:20 too',
    includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS,
    dueDate: dayjs('2019-06-16T18:15:03+02:00'),
    assessmentDueDate: dayjs('2019-06-16T18:30:57+02:00'),
    maxPoints: 12.0,
    studentParticipations: [
        {
            id: 250,
            initializationState: InitializationState.FINISHED,
            initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
            presentationScore: 1,
            results: [
                {
                    id: 231,
                    completionDate: dayjs('2019-06-17T09:30:17.761+02:00'),
                    successful: false,
                    score: 75,
                    rated: true,
                    hasFeedback: false,
                    assessmentType: AssessmentType.MANUAL,
                    hasComplaint: false,
                },
            ],
            student: {
                id: 9,
                login: 'artemis_test_user_1',
                firstName: 'Artemis Test User 1',
                email: 'krusche+testuser_1@in.tum.de',
                activated: true,
                langKey: 'en',
                internal: false,
                guidedTourSettings: [],
            },
        },
    ],
    numberOfSubmissions: new DueDateStat(),
    totalNumberOfAssessments: new DueDateStat(),
    numberOfComplaints: 0,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: false,
    studentAssignedTeamIdComputed: false,
};

const quizExercise: QuizExercise = {
    type: ExerciseType.QUIZ,
    id: 197,
    title: 'Until 18:20 too',
    categories: [generateExerciseCategory(ExerciseType.QUIZ, 1)] as ExerciseCategory[],
    includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
    maxPoints: 3.0,
    studentParticipations: [
        {
            id: 251,
            initializationState: InitializationState.FINISHED,
            initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
            presentationScore: 7,
            results: [
                {
                    id: 232,
                    completionDate: dayjs('2019-06-17T09:30:17.761+02:00'),
                    successful: false,
                    score: 33,
                    rated: true,
                    hasFeedback: false,
                    assessmentType: AssessmentType.MANUAL,
                    hasComplaint: false,
                },
            ],
            student: {
                id: 9,
                login: 'artemis_test_user_1',
                firstName: 'Artemis Test User 1',
                email: 'krusche+testuser_1@in.tum.de',
                activated: true,
                langKey: 'en',
                internal: false,
                guidedTourSettings: [],
            },
        },
    ],
    numberOfSubmissions: new DueDateStat(),
    totalNumberOfAssessments: new DueDateStat(),
    numberOfComplaints: 0,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: false,
    studentAssignedTeamIdComputed: false,
};

const programmingExercise: ProgrammingExercise = {
    type: ExerciseType.PROGRAMMING,
    id: 198,
    title: 'Until 18:20 too',
    categories: [generateExerciseCategory(ExerciseType.PROGRAMMING, 1)] as ExerciseCategory[],
    includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
    dueDate: dayjs('2019-06-16T18:15:03+02:00'),
    assessmentDueDate: dayjs('2019-06-16T18:30:57+02:00'),
    maxPoints: 17.0,
    studentParticipations: [
        {
            id: 252,
            initializationState: InitializationState.FINISHED,
            initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
            presentationScore: 6,
            results: [
                {
                    id: 233,
                    completionDate: dayjs('2019-06-17T09:30:17.761+02:00'),
                    successful: false,
                    score: 100,
                    rated: true,
                    hasFeedback: false,
                    assessmentType: AssessmentType.MANUAL,
                    hasComplaint: false,
                },
            ],
            student: {
                id: 9,
                login: 'artemis_test_user_1',
                firstName: 'Artemis Test User 1',
                email: 'krusche+testuser_1@in.tum.de',
                activated: true,
                langKey: 'en',
                internal: false,
                guidedTourSettings: [],
            },
        },
    ],
    numberOfSubmissions: new DueDateStat(),
    totalNumberOfAssessments: new DueDateStat(),
    numberOfComplaints: 0,
    numberOfAssessmentsOfCorrectionRounds: [],
    secondCorrectionEnabled: false,
    studentAssignedTeamIdComputed: false,
};

let results: Exercise[];
const courseExercises = [...modelingExercises, programmingExercise, quizExercise, fileUploadExercise];

describe('ChartCategoryFilter', () => {
    let categoryFilter: ChartCategoryFilter;

    beforeEach(() => {
        TestBed.configureTestingModule({})
            .compileComponents()
            .then(() => {
                categoryFilter = TestBed.inject(ChartCategoryFilter);
                categoryFilter.setupCategoryFilter(courseExercises);
            });
    });

    it('should deselect and select all categories', () => {
        // 3 Filters: Exercises with no categories, programming1 and quiz1
        expect(categoryFilter.numberOfActiveFilters).toBe(3);
        expect(categoryFilter.filterMap.get('quiz1')).toBeTrue();
        expect(categoryFilter.filterMap.get('programming1')).toBeTrue();
        expect(categoryFilter.allCategoriesSelected).toBeTrue();

        results = categoryFilter.toggleAllCategories(courseExercises);

        expect(categoryFilter.allCategoriesSelected).toBeFalse();
        expect(categoryFilter.filterMap.get('quiz1')).toBeFalse();
        expect(categoryFilter.filterMap.get('programming1')).toBeFalse();
        expect(categoryFilter.numberOfActiveFilters).toBe(0);
        expect(results).toEqual([]);

        results = categoryFilter.toggleAllCategories(courseExercises);

        expect(categoryFilter.allCategoriesSelected).toBeTrue();
        expect(categoryFilter.filterMap.get('quiz1')).toBeTrue();
        expect(categoryFilter.filterMap.get('programming1')).toBeTrue();
        expect(categoryFilter.numberOfActiveFilters).toBe(3);
        expect(results).toEqual(courseExercises);
    });

    it('should switch filter categories correctly', () => {
        results = categoryFilter.toggleCategory(courseExercises, 'programming1');

        expect(categoryFilter.numberOfActiveFilters).toBe(2);
        expect(categoryFilter.filterMap.get('programming1')).toBeFalse();
        expect(results).toEqual([...modelingExercises, quizExercise, fileUploadExercise]);

        results = categoryFilter.toggleCategory(courseExercises, 'quiz1');

        expect(categoryFilter.numberOfActiveFilters).toBe(1);
        expect(categoryFilter.filterMap.get('programming1')).toBeFalse();
        expect(categoryFilter.filterMap.get('quiz1')).toBeFalse();
        expect(results).toEqual([...modelingExercises, fileUploadExercise]);

        results = categoryFilter.toggleExercisesWithNoCategory(courseExercises);

        expect(categoryFilter.numberOfActiveFilters).toBe(0);
        expect(results).toEqual([]);

        results = categoryFilter.toggleCategory(courseExercises, 'programming1');

        expect(categoryFilter.numberOfActiveFilters).toBe(1);
        expect(categoryFilter.filterMap.get('programming1')).toBeTrue();
        expect(results).toEqual([programmingExercise]);
    });

    it('should handle manual reselection of all categories correctly', () => {
        categoryFilter.toggleAllCategories(courseExercises);
        categoryFilter.toggleExercisesWithNoCategory(courseExercises);
        categoryFilter.toggleCategory(courseExercises, 'quiz1');
        results = categoryFilter.toggleCategory(courseExercises, 'programming1');

        expect(categoryFilter.numberOfActiveFilters).toBe(3);
        expect(results).toEqual(courseExercises);
    });

    it('should display an exercise with multiple categories unless both are deselected', () => {
        const quizCategory1 = generateExerciseCategory(ExerciseType.QUIZ, 1);
        const quizCategory2 = generateExerciseCategory(ExerciseType.QUIZ, 2);
        const newQuizExercise = { ...quizExercise, categories: [quizCategory1, quizCategory2] as ExerciseCategory[] };

        categoryFilter.setupCategoryFilter([newQuizExercise]);

        results = categoryFilter.toggleCategory([newQuizExercise], 'quiz1');

        expect(results).toEqual([newQuizExercise]);

        results = categoryFilter.toggleCategory([newQuizExercise], 'quiz2');

        expect(results).toEqual([]);

        results = categoryFilter.toggleCategory([newQuizExercise], 'quiz1');

        expect(results).toEqual([newQuizExercise]);
    });
});
