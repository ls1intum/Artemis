import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { BarChartModule, PieChartModule } from '@swimlane/ngx-charts';
import { CourseScores } from 'app/course/course-scores/course-scores';
import { ScoresStorageService } from 'app/course/course-scores/scores-storage.service';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { ParticipationResultDTO } from 'app/course/manage/course-for-dashboard-dto';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Course } from 'app/entities/course.model';
import { ExerciseCategory } from 'app/entities/exercise-category.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { TreeviewModule } from 'app/exercises/programming/shared/code-editor/treeview/treeview.module';
import { CourseCompetenciesComponent } from 'app/overview/course-competencies/course-competencies.component';
import { CourseStatisticsComponent, NgxExercise } from 'app/overview/course-statistics/course-statistics.component';
import { ExerciseScoresChartComponent } from 'app/overview/visualizations/exercise-scores-chart/exercise-scores-chart.component';
import { ChartCategoryFilter } from 'app/shared/chart/chart-category-filter';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import dayjs from 'dayjs/esm';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { MockTranslateValuesDirective } from '../../../helpers/mocks/directive/mock-translate-values.directive';
import { ArtemisTestModule } from '../../../test.module';

describe('CourseStatisticsComponent', () => {
    let comp: CourseStatisticsComponent;
    let fixture: ComponentFixture<CourseStatisticsComponent>;
    let courseStorageService: CourseStorageService;
    let scoresStorageService: ScoresStorageService;
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
    course.studentGroupName = 'eist2019students';
    course.teachingAssistantGroupName = 'artemis-dev';
    course.instructorGroupName = 'artemis-dev';
    course.onlineCourse = false;
    course.enrollmentEnabled = false;
    course.exercises = [];
    course.presentationScore = 1;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule, TreeviewModule.forRoot(), MockModule(PieChartModule), MockModule(BarChartModule), MockModule(NgbTooltipModule)],
            declarations: [
                CourseStatisticsComponent,
                MockComponent(CourseCompetenciesComponent),
                MockComponent(ExerciseScoresChartComponent),
                MockComponent(DocumentationButtonComponent),
                MockTranslateValuesDirective,
                ArtemisTranslatePipe,
            ],
            providers: [
                MockProvider(ArtemisNavigationUtilService),
                MockProvider(ChartCategoryFilter),
                {
                    provide: ActivatedRoute,
                    useValue: { parent: { params: of(1) } },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseStatisticsComponent);
                comp = fixture.componentInstance;
                courseStorageService = TestBed.inject(CourseStorageService);
                scoresStorageService = TestBed.inject(ScoresStorageService);
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
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(courseToAdd);
        const mockParticipationResult: ParticipationResultDTO = { rated: true, score: 100, participationId: 1 };
        jest.spyOn(scoresStorageService, 'getStoredParticipationResult').mockReturnValue(mockParticipationResult);
        fixture.detectChanges();
        comp.ngOnInit();
        // Include all exercises
        comp.toggleNotIncludedInScoreExercises();
        fixture.detectChanges();
        expect(comp.ngxExerciseGroups.size).toBe(4);
        const modelingWrapper = fixture.debugElement.query(By.css('#modeling-wrapper'));
        expect(modelingWrapper.query(By.css('h4')).nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.exerciseCount ');
        expect(modelingWrapper.query(By.css('#absolute-score')).nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.yourPoints ');
        expect(modelingWrapper.query(By.css('#reachable-score')).nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.reachablePoints ');
        expect(modelingWrapper.query(By.css('#max-score')).nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.totalPoints ');
        expect(fixture.debugElement.query(By.css('#presentation-score')).nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.presentationScore ');

        const programming: NgxExercise = comp.ngxExerciseGroups.get(ExerciseType.PROGRAMMING)![0];
        expect(programming.series).toHaveLength(6);
        expect(programming.series[2].isProgrammingExercise).toBeTrue();
    });

    it('should filter all exercises not included in score', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [...modelingExercises];
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(courseToAdd);
        const mockParticipationResult: ParticipationResultDTO = { rated: true, score: 100, participationId: 1 };
        jest.spyOn(scoresStorageService, 'getStoredParticipationResult').mockReturnValue(mockParticipationResult);
        fixture.detectChanges();
        comp.ngOnInit();

        let exercises = comp.ngxExerciseGroups.get(ExerciseType.MODELING)!;
        expect(exercises[0].name).toBe('Until 18:20');
        expect(exercises[1].name).toBe('Until 18:20 too');
        expect(exercises[2].name).toBe('test 17.06. 1');
        expect(exercises[3].name).toBe('test 18.06. 1');

        comp.toggleNotIncludedInScoreExercises();

        exercises = comp.ngxExerciseGroups.get(ExerciseType.MODELING)!;
        expect(exercises[0].name).toBe('Until 18:20');
        expect(exercises[1].name).toBe('Until 18:20 too');
        expect(exercises[2].name).toBe('test 17.06. 1');
        expect(exercises[3].name).toBe('test 17.06. 2');
        expect(exercises[4].name).toBe('test 18.06. 1');

        comp.toggleNotIncludedInScoreExercises();

        exercises = comp.ngxExerciseGroups.get(ExerciseType.MODELING)!;
        expect(exercises[0].name).toBe('Until 18:20');
        expect(exercises[1].name).toBe('Until 18:20 too');
        expect(exercises[2].name).toBe('test 17.06. 1');
        expect(exercises[3].name).toBe('test 18.06. 1');
    });

    it('should set the scores correctly after retrieving them from the store', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [...modelingExercises];
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(courseToAdd);
        const mockScoresPerExerciseType: Map<ExerciseType, CourseScores> = new Map<ExerciseType, CourseScores>();
        const mockCourseScores: CourseScores = new CourseScores(36, 36, 0, {
            absoluteScore: 20,
            relativeScore: 0,
            currentRelativeScore: 0,
            presentationScore: 0,
        });
        mockScoresPerExerciseType.set(ExerciseType.MODELING, mockCourseScores);
        jest.spyOn(scoresStorageService, 'getStoredScoresPerExerciseType').mockReturnValue(mockScoresPerExerciseType);
        fixture.detectChanges();
        comp.ngOnInit();
        fixture.detectChanges();
        expect(comp.ngxExerciseGroups.size).toBe(1);
        const exercise: NgxExercise = comp.ngxExerciseGroups.get(ExerciseType.MODELING)![0];
        expect(exercise.absoluteScore).toBe(20);
        expect(exercise.reachablePoints).toBe(36);
        expect(exercise.overallMaxPoints).toBe(36);

        // check that html file displays the correct elements
        let debugElement = fixture.debugElement.query(By.css('#absolute-course-score'));
        expect(debugElement.nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.yourPoints ');
        debugElement = fixture.debugElement.query(By.css('#reachable-course-score'));
        expect(debugElement.nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.reachablePoints ');
        debugElement = fixture.debugElement.query(By.css('#max-course-score'));
        expect(debugElement.nativeElement.textContent).toBe(' artemisApp.courseOverview.statistics.totalPoints ');
    });

    it('should set the course after being notified about a course update', () => {
        comp.courseId = course.id!;
        fixture.detectChanges();
        comp.ngOnInit();
        fixture.detectChanges();

        // Should not have found a course yet.
        expect(comp.course).toBeUndefined();

        const courseToSubscribeTo = { ...course };
        courseToSubscribeTo.exercises = [...modelingExercises];
        courseStorageService.setCourses([courseToSubscribeTo]);

        const updateCourseSpy = jest.spyOn(courseStorageService, 'updateCourse');

        courseStorageService.updateCourse(courseToSubscribeTo);

        expect(comp.course).toEqual(courseToSubscribeTo);
        expect(updateCourseSpy).toHaveBeenCalledWith(courseToSubscribeTo);
    });

    it('should delegate the user correctly', () => {
        const clickEvent = { exerciseId: 42 };
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
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
            const mockParticipationResult: ParticipationResultDTO = { rated: true, score: 100, participationId: 1 };
            jest.spyOn(scoresStorageService, 'getStoredParticipationResult').mockReturnValue(mockParticipationResult);
            comp.toggleNotIncludedInScoreExercises();

            expect(comp.currentlyHidingNotIncludedInScoreExercises).toBeFalse();
            expect(comp.ngxExerciseGroups.size).toBe(3);
            const modelingExercises = comp.ngxExerciseGroups.get(ExerciseType.MODELING)!;
            expect(modelingExercises).toHaveLength(5);
            expect(modelingExercises[0].name).toBe('Until 18:20');
            expect(modelingExercises[1].name).toBe('Until 18:20 too');
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
            const programmingWithCategory = {
                ...programmingExercise,
                categories: [programmingCategory] as ExerciseCategory[],
            };
            const quizCategory = generateExerciseCategory(ExerciseType.QUIZ, 1);
            const quizWithCategory = { ...quizExercise, categories: [quizCategory] as ExerciseCategory[] };
            courseToAdd.exercises = [...modelingExercises, programmingWithCategory, quizWithCategory];
            jest.spyOn(courseStorageService, 'getCourse').mockReturnValue(courseToAdd);
            comp.ngOnInit();
            // return all exercises that are included in score
            return [modelingExercises[0], modelingExercises[2], modelingExercises[3], modelingExercises[4], quizWithCategory];
        };
    });
});
