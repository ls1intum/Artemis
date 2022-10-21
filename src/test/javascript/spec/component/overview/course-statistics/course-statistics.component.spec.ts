import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockProvider } from 'ng-mocks';
import { ArtemisTestModule } from '../../../test.module';
import { ActivatedRoute } from '@angular/router';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { CourseStatisticsComponent } from 'app/overview/course-statistics/course-statistics.component';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { CourseLearningGoalsComponent } from 'app/overview/course-learning-goals/course-learning-goals.component';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExerciseScoresChartComponent } from 'app/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { RouterTestingModule } from '@angular/router/testing';
import { BarChartModule, PieChartModule } from '@swimlane/ngx-charts';
import { MockTranslateValuesDirective } from '../../../helpers/mocks/directive/mock-translate-values.directive';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TreeviewModule } from 'app/exercises/programming/shared/code-editor/treeview/treeview.module';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { ChartCategoryFilter } from 'app/shared/chart/chart-category-filter';

describe('CourseStatisticsComponent', () => {
    let comp: CourseStatisticsComponent;
    let fixture: ComponentFixture<CourseStatisticsComponent>;
    let courseScoreCalculationService: CourseScoreCalculationService;
    let categoryFilter: ChartCategoryFilter;

    const testCategories = new Set(['test1', 'test2']);

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

    const fileUploadExercise = {
        type: 'file-upload',
        id: 196,
        title: 'Until 18:20 too',
        includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS,
        dueDate: dayjs('2019-06-16T18:15:03+02:00'),
        assessmentDueDate: dayjs('2019-06-16T18:30:57+02:00'),
        maxPoints: 12.0,
        studentParticipations: [
            {
                id: 250,
                initializationState: 'FINISHED',
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
        numberOfSubmissions: new DueDateStat(),
        totalNumberOfAssessments: new DueDateStat(),
        numberOfComplaints: 0,
    } as FileUploadExercise;

    const quizExercise = {
        type: 'quiz',
        id: 197,
        title: 'Until 18:20 too',
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        maxPoints: 3.0,
        studentParticipations: [
            {
                id: 251,
                initializationState: 'FINISHED',
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
        numberOfSubmissions: new DueDateStat(),
        totalNumberOfAssessments: new DueDateStat(),
        numberOfComplaints: 0,
    } as QuizExercise;

    const programmingExercise = {
        type: 'programming',
        id: 198,
        title: 'Until 18:20 too',
        includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
        dueDate: dayjs('2019-06-16T18:15:03+02:00'),
        assessmentDueDate: dayjs('2019-06-16T18:30:57+02:00'),
        maxPoints: 17.0,
        studentParticipations: [
            {
                id: 252,
                initializationState: 'FINISHED',
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
        numberOfSubmissions: new DueDateStat(),
        totalNumberOfAssessments: new DueDateStat(),
        numberOfComplaints: 0,
    } as ProgrammingExercise;

    const course = new Course();
    course.id = 64;
    course.title = 'Checking statistics';
    course.description = 'Testing the statistics view';
    course.shortName = 'CHS';
    course.studentGroupName = 'jira-users';
    course.teachingAssistantGroupName = 'artemis-dev';
    course.instructorGroupName = 'artemis-dev';
    course.onlineCourse = false;
    course.registrationEnabled = false;
    course.exercises = [];
    course.presentationScore = 1;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule, TreeviewModule.forRoot(), MockModule(PieChartModule), MockModule(BarChartModule)],
            declarations: [
                CourseStatisticsComponent,
                MockComponent(CourseLearningGoalsComponent),
                MockComponent(ExerciseScoresChartComponent),
                MockTranslateValuesDirective,
                ArtemisTranslatePipe,
                MockDirective(NgbTooltip),
            ],
            providers: [MockProvider(ArtemisNavigationUtilService), MockProvider(ChartCategoryFilter), { provide: ActivatedRoute, useValue: { parent: { params: of(1) } } }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseStatisticsComponent);
                comp = fixture.componentInstance;
                courseScoreCalculationService = TestBed.inject(CourseScoreCalculationService);
                categoryFilter = TestBed.inject(ChartCategoryFilter);
                categoryFilter.exerciseCategories = testCategories;
            });
    });

    afterEach(() => {
        // has to be done so the component can cleanup properly
        jest.spyOn(comp, 'ngOnDestroy').mockImplementation();
        fixture.destroy();
    });

    it('should group all exercises', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [programmingExercise, quizExercise, ...modelingExercises, fileUploadExercise];
        jest.spyOn(courseScoreCalculationService, 'getCourse').mockReturnValue(courseToAdd);
        fixture.detectChanges();
        comp.ngOnInit();
        // Include all exercises
        comp.toggleNotIncludedInScoreExercises();
        fixture.detectChanges();
        expect(comp.ngxExerciseGroups).toHaveLength(4);
        const modelingWrapper = fixture.debugElement.query(By.css('#modeling-wrapper'));
        expect(modelingWrapper.query(By.css('h4')).nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.exerciseCount ');
        expect(modelingWrapper.query(By.css('#absolute-score')).nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.yourPoints ');
        expect(modelingWrapper.query(By.css('#reachable-score')).nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.reachablePoints ');
        expect(modelingWrapper.query(By.css('#max-score')).nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.totalPoints ');
        expect(fixture.debugElement.query(By.css('#presentation-score')).nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.presentationScore ');

        const programming: any = comp.ngxExerciseGroups[0][0];
        // as it is not included, the presentation score should be 0
        expect(programming.presentationScore).toBe(0);
        expect(programming.series).toHaveLength(6);
        expect(programming.series[2].isProgrammingExercise).toBeTrue();
        expect(programming.series[2].absoluteValue).toBe(17);

        const quiz: any = comp.ngxExerciseGroups[1][0];
        // as the quiz does not have a due date, the presentation score should be 0
        expect(quiz.presentationScore).toBe(0);
        expect(quiz.series[0].absoluteValue).toBe(1);

        const modeling: any = comp.ngxExerciseGroups[2][0];
        expect(modeling.presentationScore).toBe(2);
        expect(modeling.series[1].absoluteValue).toBe(11);

        const fileUpload: any = comp.ngxExerciseGroups[3][0];
        expect(fileUpload.presentationScore).toBe(1);
        expect(fileUpload.series[3].absoluteValue).toBe(9);
    });

    it('should filter all exercises not included in score', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [...modelingExercises];
        jest.spyOn(courseScoreCalculationService, 'getCourse').mockReturnValue(courseToAdd);
        fixture.detectChanges();
        comp.ngOnInit();

        let exercises = comp.ngxExerciseGroups[0];
        expect(exercises[0].name).toBe('Until 18:20');
        expect(exercises[1].name).toBe('Until 18:20 too');
        expect(exercises[2].name).toBe('test 17.06. 1');
        expect(exercises[3].name).toBe('test 18.06. 1');

        comp.toggleNotIncludedInScoreExercises();

        exercises = comp.ngxExerciseGroups[0];
        expect(exercises[0].name).toBe('Until 18:20');
        expect(exercises[1].name).toBe('Until 18:20 too');
        expect(exercises[2].name).toBe('test 17.06. 1');
        expect(exercises[3].name).toBe('test 17.06. 2');
        expect(exercises[4].name).toBe('test 18.06. 1');

        comp.toggleNotIncludedInScoreExercises();

        exercises = comp.ngxExerciseGroups[0];
        expect(exercises[0].name).toBe('Until 18:20');
        expect(exercises[1].name).toBe('Until 18:20 too');
        expect(exercises[2].name).toBe('test 17.06. 1');
        expect(exercises[3].name).toBe('test 18.06. 1');
    });

    it('should calculate scores correctly', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [...modelingExercises];
        jest.spyOn(courseScoreCalculationService, 'getCourse').mockReturnValue(courseToAdd);
        fixture.detectChanges();
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.ngxExerciseGroups).toHaveLength(1);
        let exercise: any = comp.ngxExerciseGroups[0][0];
        expect(exercise.absoluteScore).toBe(20);
        expect(exercise.reachableScore).toBe(36);
        expect(exercise.overallMaxPoints).toBe(36);

        const newExercise = [
            {
                type: 'text',
                id: 200,
                title: 'Until 18:20 too',
                dueDate: dayjs('2019-06-16T18:15:03+02:00'),
                assessmentDueDate: dayjs('2019-06-16T18:30:57+02:00'),
                maxPoints: 10.0,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                studentParticipations: [
                    {
                        id: 289,
                        initializationState: 'FINISHED',
                        initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
                        results: [
                            {
                                id: 222,
                                completionDate: dayjs('2019-06-17T09:30:17.761+02:00'),
                                successful: false,
                                score: 55,
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
            } as unknown as TextExercise,
            {
                type: 'text',
                id: 999,
                title: 'Until 18:20 tooo',
                dueDate: dayjs('2019-06-16T18:15:03+02:00'),
                assessmentDueDate: dayjs().add(1, 'days'),
                maxPoints: 10.0,
                includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
                studentParticipations: [
                    {
                        id: 888,
                        initializationState: 'FINISHED',
                        initializationDate: dayjs('2019-06-16T18:10:28.293+02:00'),
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
            } as unknown as TextExercise,
        ];
        courseToAdd.exercises = [...modelingExercises, ...newExercise];
        fixture.detectChanges();
        comp.ngOnInit();
        fixture.detectChanges();

        // check that exerciseGroup scores are untouched
        exercise = comp.ngxExerciseGroups[0][0];
        expect(exercise.absoluteScore).toBe(20);
        expect(exercise.reachableScore).toBe(36);
        expect(exercise.overallMaxPoints).toBe(36);

        // check that overall course score is adapted accordingly -> one exercise after assessment, one before
        expect(comp.overallPoints).toBe(25.5);
        expect(comp.reachablePoints).toBe(46);
        expect(comp.overallMaxPoints).toBe(56);

        // check that html file displays the correct elements
        let debugElement = fixture.debugElement.query(By.css('#absolute-course-score'));
        expect(debugElement.nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.yourPoints ');
        debugElement = fixture.debugElement.query(By.css('#reachable-course-score'));
        expect(debugElement.nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.reachablePoints ');
        debugElement = fixture.debugElement.query(By.css('#max-course-score'));
        expect(debugElement.nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.totalPoints ');
    });

    it('should delegate the user correctly', () => {
        const clickEvent = { exerciseId: 42 };
        jest.spyOn(courseScoreCalculationService, 'getCourse').mockReturnValue(course);
        const routingService = TestBed.inject(ArtemisNavigationUtilService);
        const routingStub = jest.spyOn(routingService, 'routeInNewTab').mockImplementation();
        comp.ngOnInit();

        comp.onSelect(clickEvent);

        expect(routingStub).toHaveBeenCalledWith(['courses', 64, 'exercises', 42]);
    });

    describe('test chart filters', () => {
        let exercises: Exercise[];

        beforeEach(() => {
            exercises = setupExercisesWithCategories();
        });

        it('should filter optional exercises correctly', () => {
            comp.toggleNotIncludedInScoreExercises();

            expect(comp.currentlyHidingNotIncludedInScoreExercises).toBeFalse();
            expect(comp.ngxExerciseGroups).toHaveLength(3);
            expect(comp.ngxExerciseGroups[0][0].name).toBe('Until 18:20 too');
        });

        it('should toggle categories', () => {
            const getCurrentFilterStateMock = jest.spyOn(categoryFilter, 'getCurrentFilterState').mockReturnValue(false);
            const toggleCategoryMock = jest.spyOn(categoryFilter, 'toggleCategory').mockReturnValue(exercises);

            comp.toggleCategory('test1');

            expect(getCurrentFilterStateMock).toHaveBeenCalledOnce();
            expect(getCurrentFilterStateMock).toHaveBeenCalledWith('test1');
            expect(toggleCategoryMock).toHaveBeenCalledOnce();
            expect(toggleCategoryMock).toHaveBeenCalledWith(exercises, 'test1');
        });

        it('should toggle all categories', () => {
            const toggleAllCategoriesMock = jest.spyOn(categoryFilter, 'toggleAllCategories').mockReturnValue(exercises);

            comp.toggleAllCategories();

            expect(toggleAllCategoriesMock).toHaveBeenCalledOnce();
            expect(toggleAllCategoriesMock).toHaveBeenCalledWith(exercises);
        });

        it('should toggle exercises with no categories', () => {
            const toggleExercisesWithNoCategoryMock = jest.spyOn(categoryFilter, 'toggleExercisesWithNoCategory').mockReturnValue(exercises);

            comp.toggleExercisesWithNoCategory();

            expect(toggleExercisesWithNoCategoryMock).toHaveBeenCalledOnce();
            expect(toggleExercisesWithNoCategoryMock).toHaveBeenCalledWith(exercises);
        });

        const setupExercisesWithCategories = () => {
            const courseToAdd = { ...course };
            const programmingCategory = generateExerciseCategory(ExerciseType.PROGRAMMING, 1);
            const programmingWithCategory = { ...programmingExercise, categories: [programmingCategory] as ExerciseCategory[] };
            const quizCategory = generateExerciseCategory(ExerciseType.QUIZ, 1);
            const quizWithCategory = { ...quizExercise, categories: [quizCategory] as ExerciseCategory[] };
            courseToAdd.exercises = [...modelingExercises, programmingWithCategory, quizWithCategory];
            jest.spyOn(courseScoreCalculationService, 'getCourse').mockReturnValue(courseToAdd);
            comp.ngOnInit();
            // return all exercises that are included in score
            return [modelingExercises[0], modelingExercises[2], modelingExercises[3], modelingExercises[4], quizWithCategory];
        };
    });
});
