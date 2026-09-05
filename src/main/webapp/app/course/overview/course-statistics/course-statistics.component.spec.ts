import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { CourseScores } from 'app/course/manage/course-scores/course-scores';
import { ScoresStorageService } from 'app/course/manage/course-scores/scores-storage.service';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { ParticipationResultDTO } from 'app/course/shared/entities/course-for-dashboard-dto';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { CourseStatisticsComponent, NgxExercise, Series } from 'app/course/overview/course-statistics/course-statistics.component';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ChartCategoryFilter } from 'app/exercise/chart/chart-category-filter';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import dayjs from 'dayjs/esm';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseExercisesForOverviewDTO } from 'app/course/shared/entities/course-exercises-for-overview-dto';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradeDTO } from 'app/assessment/shared/entities/grade-step.model';
import { TumUiChartTooltipConfig } from '@tumaet/ui-angular';

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

    const createOverviewResponse = (exercises: Exercise[]): CourseExercisesForOverviewDTO => {
        const emptyScores = () =>
            new CourseScores(0, 0, 0, {
                absoluteScore: 0,
                absoluteScoreTotal: 0,
                relativeScore: 0,
                currentRelativeScore: 0,
                presentationScore: 0,
            });

        return {
            exercises,
            totalScores: emptyScores(),
            textScores: emptyScores(),
            programmingScores: emptyScores(),
            modelingScores: emptyScores(),
            fileUploadScores: emptyScores(),
            quizScores: emptyScores(),
            participationResults: [],
        };
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
            totalNumberOfAssessments: 0,
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
            totalNumberOfAssessments: 0,
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
            totalNumberOfAssessments: 0,
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
            totalNumberOfAssessments: 0,
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
            totalNumberOfAssessments: 0,
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
                submissions: [
                    {
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
        totalNumberOfAssessments: 0,
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
                submissions: [
                    {
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
        totalNumberOfAssessments: 0,
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
                submissions: [
                    {
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
        totalNumberOfAssessments: 0,
        numberOfComplaints: 0,
    } as ProgrammingExercise;

    const course = new Course();
    course.id = 64;
    course.title = 'Checking statistics';
    course.description = 'Testing the statistics view';
    course.shortName = 'CHS';
    course.onlineCourse = false;
    course.enrollmentEnabled = false;
    course.exercises = [];
    course.presentationScore = 1;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: { parent: { params: of({ courseId: '1' }) } },
                        pathFromRoot: [{ snapshot: { url: [{ path: 'courses' }, { path: '1' }] } }, { snapshot: { url: [{ path: 'statistics' }] } }],
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        await TestBed.compileComponents();
        fixture = TestBed.createComponent(CourseStatisticsComponent);
        comp = fixture.componentInstance;
        courseStorageService = TestBed.inject(CourseStorageService);
        scoresStorageService = TestBed.inject(ScoresStorageService);
        categoryFilter = TestBed.inject(ChartCategoryFilter);
        categoryFilter.exerciseCategories = testCategories;
    });

    afterEach(() => {
        // has to be done so the component can cleanup properly
        vi.spyOn(comp, 'ngOnDestroy').mockImplementation(() => {});
        fixture.destroy();
        vi.restoreAllMocks();
    });

    it('should show the translated doughnut chart label as tooltip title and the value as body', () => {
        const tooltip = comp.doughnutConfig().tooltip as TumUiChartTooltipConfig;
        const datum = { seriesIndex: 0, index: 0, label: 'artemisApp.courseOverview.statistics.missingPointsLabel', value: 400 };

        expect(tooltip.title!([datum])).toBe('artemisApp.courseOverview.statistics.missingPointsLabel');
        expect(tooltip.label!(datum)).toBe('400');
    });

    it('should not ask the server to match a grade when no scores are stored for the course', () => {
        // getScoreByScoreType returns NaN for a missing CourseScores, and match-grade-step?gradePercentage=NaN is
        // a request the server can only reject
        const gradeSpy = vi.spyOn(TestBed.inject(GradingService), 'matchPercentageToGradeStep');
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ id: 1, title: 'Course 1' } as Course);

        comp.ngOnInit();

        expect(gradeSpy).not.toHaveBeenCalled();
    });

    it('should cancel, reset, and reload when Angular reuses the statistics tab for another course', () => {
        const params = new BehaviorSubject({ courseId: '1' });
        const firstLoad = new Subject<CourseExercisesForOverviewDTO>();
        const secondLoad = new Subject<CourseExercisesForOverviewDTO>();
        const firstCourseUpdates = new Subject<Course>();
        const secondCourseUpdates = new Subject<Course>();
        const firstGrade = new Subject<GradeDTO>();
        const secondGrade = new Subject<GradeDTO>();
        (TestBed.inject(ActivatedRoute) as any).parent.parent.params = params.asObservable();
        const loadSpy = vi.spyOn(TestBed.inject(CourseOverviewExercisesService), 'loadIfNeeded').mockImplementation((courseId) => (courseId === 1 ? firstLoad : secondLoad));
        const courseUpdatesSpy = vi
            .spyOn(courseStorageService, 'subscribeToCourseUpdates')
            .mockImplementation((courseId) => (courseId === 1 ? firstCourseUpdates : secondCourseUpdates));
        vi.spyOn(courseStorageService, 'getCourse').mockImplementation((courseId) => ({ id: courseId, title: `Course ${courseId}` }) as Course);
        const gradeSpy = vi.spyOn(TestBed.inject(GradingService), 'matchPercentageToGradeStep').mockReturnValueOnce(firstGrade).mockReturnValueOnce(secondGrade);

        // The mocked loader bypasses the storage the real one writes to; without scores the relative score is NaN and
        // the grade lookup is correctly skipped, which is not what this test is about
        const totalScores = (relativeScore: number) =>
            new CourseScores(10, 10, 0, {
                absoluteScore: relativeScore / 10,
                absoluteScoreTotal: relativeScore / 10,
                relativeScore,
                currentRelativeScore: relativeScore,
                presentationScore: 0,
            });
        scoresStorageService.setStoredTotalScores(1, totalScores(80));
        scoresStorageService.setStoredTotalScores(2, totalScores(0));

        comp.ngOnInit();
        firstCourseUpdates.next({ ...course, id: 1, exercises: [modelingExercises[0]] });
        firstLoad.next(createOverviewResponse([modelingExercises[0]]));
        expect(comp.ngxExerciseGroups().size).toBe(1);
        expect(firstLoad.observed).toBe(true);
        expect(firstCourseUpdates.observed).toBe(true);
        expect(firstGrade.observed).toBe(true);

        params.next({ courseId: '2' });

        expect(comp.courseId).toBe(2);
        expect(comp.course()?.id).toBe(2);
        expect(comp.ngxExerciseGroups().size).toBe(0);
        expect(comp.overallPoints()).toBe(0);
        expect(comp.overallPointsTotal()).toBe(0);
        expect(comp.totalRelativeScore()).toBe(0);
        expect(comp.filteredExerciseIDs()).toEqual([]);
        expect(comp.gradeDTO()).toBeUndefined();
        expect(comp.gradingScaleExists()).toBe(false);
        expect(firstLoad.observed).toBe(false);
        expect(firstCourseUpdates.observed).toBe(false);
        expect(firstGrade.observed).toBe(false);
        expect(secondLoad.observed).toBe(true);
        expect(secondCourseUpdates.observed).toBe(true);
        expect(loadSpy).toHaveBeenNthCalledWith(1, 1);
        expect(loadSpy).toHaveBeenNthCalledWith(2, 2);
        expect(courseUpdatesSpy).toHaveBeenNthCalledWith(1, 1);
        expect(courseUpdatesSpy).toHaveBeenNthCalledWith(2, 2);

        firstCourseUpdates.next({ ...course, id: 1, title: 'Stale course' });
        firstLoad.next(createOverviewResponse([modelingExercises[1]]));
        secondCourseUpdates.next({ ...course, id: 2, exercises: [] });
        secondLoad.next(createOverviewResponse([]));
        params.next({ courseId: '2' });

        expect(comp.course()?.id).toBe(2);
        expect(comp.course()?.title).not.toBe('Stale course');
        expect(gradeSpy).toHaveBeenNthCalledWith(1, expect.any(Number), 1);
        expect(gradeSpy).toHaveBeenNthCalledWith(2, 0, 2);
        expect(secondGrade.observed).toBe(true);
        expect(loadSpy).toHaveBeenCalledTimes(2);
        expect(courseUpdatesSpy).toHaveBeenCalledTimes(2);
    });

    it.each([
        [undefined, []],
        [
            { name: 'Achieved bonus', value: 80, absoluteValue: 8, isProgrammingExercise: true },
            ['artemisApp.courseOverview.statistics.programmingExercisePassedTests | artemisApp.courseOverview.statistics.bonusPointTooltip'],
        ],
        [
            { name: 'Achieved (not included)', value: 75, absoluteValue: 6, isProgrammingExercise: false },
            ['artemisApp.courseOverview.statistics.exerciseAchievedScore | artemisApp.courseOverview.statistics.notIncludedTooltip'],
        ],
        [
            { name: 'Missed points', value: 100, absoluteValue: 10, notParticipated: true, exerciseTitle: 'Missed exercise' },
            ['artemisApp.courseOverview.statistics.exerciseNotParticipated'],
        ],
        [
            { name: 'Missed points', value: 50, absoluteValue: 5, afterDueDate: true, isProgrammingExercise: true, exerciseTitle: 'Late exercise' },
            ['artemisApp.courseOverview.statistics.exerciseParticipatedAfterDueDate', 'artemisApp.courseOverview.statistics.programmingExerciseFailedTests'],
        ],
        [{ name: 'Not graded', value: 100, exerciseTitle: 'Pending exercise' }, ['artemisApp.courseOverview.statistics.exerciseNotGraded']],
    ])('should build the expected stacked-bar tooltip for %#', (series, expectedLines) => {
        const item = { seriesIndex: 0, index: 0, label: '', value: 0, meta: series as Series | undefined };

        const lines = (comp as any).barTooltipLines(item);

        expect(lines).toEqual(expectedLines);
    });

    it('should group all exercises', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [programmingExercise, quizExercise, ...modelingExercises, fileUploadExercise];
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(courseToAdd);
        const mockParticipationResult: ParticipationResultDTO = { rated: true, score: 100, participationId: 1 };
        vi.spyOn(scoresStorageService, 'getStoredParticipationResult').mockReturnValue(mockParticipationResult);
        fixture.detectChanges();
        comp.ngOnInit();
        // Include all exercises
        comp.toggleNotIncludedInScoreExercises();
        fixture.changeDetectorRef.detectChanges();
        expect(comp.ngxExerciseGroups().size).toBe(4);
        const modelingWrapper = fixture.debugElement.query(By.css('#modeling-wrapper'));
        expect(modelingWrapper.query(By.css('h4')).nativeElement.textContent).toBe('artemisApp.courseOverview.statistics.exerciseCount');
        expect(modelingWrapper.query(By.css('#absolute-score')).nativeElement.textContent).toBe('artemisApp.courseOverview.statistics.yourPoints');
        expect(modelingWrapper.query(By.css('#reachable-score')).nativeElement.textContent).toBe('artemisApp.courseOverview.statistics.reachablePoints');
        expect(modelingWrapper.query(By.css('#max-score')).nativeElement.textContent).toBe('artemisApp.courseOverview.statistics.totalPoints');
        expect(fixture.debugElement.query(By.css('#presentation-score')).nativeElement.textContent).toBe('artemisApp.courseOverview.statistics.presentationScore');

        const programming: NgxExercise = comp.ngxExerciseGroups().get(ExerciseType.PROGRAMMING)![0];
        expect(programming.series).toHaveLength(6);
        expect(programming.series[2].isProgrammingExercise).toBe(true);
    });

    it('should filter all exercises not included in score', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [...modelingExercises];
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(courseToAdd);
        const mockParticipationResult: ParticipationResultDTO = { rated: true, score: 100, participationId: 1 };
        vi.spyOn(scoresStorageService, 'getStoredParticipationResult').mockReturnValue(mockParticipationResult);
        fixture.detectChanges();
        comp.ngOnInit();

        let exercises = comp.ngxExerciseGroups().get(ExerciseType.MODELING)!;
        expect(exercises[0].name).toBe('Until 18:20');
        expect(exercises[1].name).toBe('Until 18:20 too');
        expect(exercises[2].name).toBe('test 17.06. 1');
        expect(exercises[3].name).toBe('test 18.06. 1');

        comp.toggleNotIncludedInScoreExercises();

        exercises = comp.ngxExerciseGroups().get(ExerciseType.MODELING)!;
        expect(exercises[0].name).toBe('Until 18:20');
        expect(exercises[1].name).toBe('Until 18:20 too');
        expect(exercises[2].name).toBe('test 17.06. 1');
        expect(exercises[3].name).toBe('test 17.06. 2');
        expect(exercises[4].name).toBe('test 18.06. 1');

        comp.toggleNotIncludedInScoreExercises();

        exercises = comp.ngxExerciseGroups().get(ExerciseType.MODELING)!;
        expect(exercises[0].name).toBe('Until 18:20');
        expect(exercises[1].name).toBe('Until 18:20 too');
        expect(exercises[2].name).toBe('test 17.06. 1');
        expect(exercises[3].name).toBe('test 18.06. 1');
    });

    it('should set the scores correctly after retrieving them from the store', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [...modelingExercises];
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(courseToAdd);
        const mockScoresPerExerciseType: Map<ExerciseType, CourseScores> = new Map<ExerciseType, CourseScores>();
        const mockCourseScores: CourseScores = new CourseScores(36, 36, 0, {
            absoluteScore: 20,
            absoluteScoreTotal: 20,
            relativeScore: 0,
            currentRelativeScore: 0,
            presentationScore: 0,
        });
        mockScoresPerExerciseType.set(ExerciseType.MODELING, mockCourseScores);
        vi.spyOn(scoresStorageService, 'getStoredScoresPerExerciseType').mockReturnValue(mockScoresPerExerciseType);
        fixture.detectChanges();
        comp.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(comp.ngxExerciseGroups().size).toBe(1);
        const exercise: NgxExercise = comp.ngxExerciseGroups().get(ExerciseType.MODELING)![0];
        expect(exercise.absoluteScore).toBe(20);
        expect(exercise.reachablePoints).toBe(36);
        expect(exercise.overallMaxPoints).toBe(36);

        // check that html file displays the correct elements
        let debugElement = fixture.debugElement.query(By.css('#absolute-course-score'));
        expect(debugElement.nativeElement.textContent).toBe('artemisApp.courseOverview.statistics.yourPoints');
        debugElement = fixture.debugElement.query(By.css('#reachable-course-score'));
        expect(debugElement.nativeElement.textContent).toBe('artemisApp.courseOverview.statistics.reachablePoints');
        debugElement = fixture.debugElement.query(By.css('#max-course-score'));
        expect(debugElement.nativeElement.textContent).toBe('artemisApp.courseOverview.statistics.totalPoints');
    });

    it('should show total and credited points separately when the course has exercise variants', () => {
        const variantExercise = {
            type: 'modeling',
            id: 901,
            title: 'variant',
            includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
            maxPoints: 12,
            exerciseVariantGroup: { id: 5, maxPoints: 5 },
        } as Exercise;
        const courseToAdd = { ...course, exercises: [variantExercise] } as Course;
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(courseToAdd);
        // Credited (capped) 5, total (uncapped) 18.
        const totalScores = new CourseScores(20, 20, 0, { absoluteScore: 5, absoluteScoreTotal: 18, relativeScore: 0, currentRelativeScore: 0, presentationScore: 0 });
        vi.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(totalScores);
        fixture.detectChanges();
        comp.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(comp.courseHasExerciseVariants()).toBe(true);
        expect(comp.overallPoints()).toBe(5);
        expect(comp.overallPointsTotal()).toBe(18);

        // Two separate rows are shown instead of the single "Your points" row.
        expect(fixture.debugElement.query(By.css('#total-course-score'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('#credited-course-score'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('#absolute-course-score'))).toBeNull();
    });

    it('should set the course after being notified about a course update', () => {
        (TestBed.inject(ActivatedRoute) as any).parent.parent.params = of({ courseId: String(course.id) });
        fixture.detectChanges();
        comp.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        // Should not have found a course yet.
        expect(comp.course()).toBeUndefined();

        const courseToSubscribeTo = { ...course };
        courseToSubscribeTo.exercises = [...modelingExercises];
        courseStorageService.setCourses([courseToSubscribeTo]);

        const updateCourseSpy = vi.spyOn(courseStorageService, 'updateCourse');

        courseStorageService.updateCourse(courseToSubscribeTo);

        expect(comp.course()).toEqual(courseToSubscribeTo);
        expect(updateCourseSpy).toHaveBeenCalledWith(courseToSubscribeTo);
    });

    it('should delegate the user correctly', () => {
        const courseToAdd = { ...course };
        courseToAdd.exercises = [...modelingExercises];
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(courseToAdd);
        const mockParticipationResult: ParticipationResultDTO = { rated: true, score: 100, participationId: 1 };
        vi.spyOn(scoresStorageService, 'getStoredParticipationResult').mockReturnValue(mockParticipationResult);
        const routingService = TestBed.inject(ArtemisNavigationUtilService);
        const routingStub = vi.spyOn(routingService, 'routeInNewTab').mockImplementation(() => {});
        comp.ngOnInit();

        // series 4 holds the 'Not graded' segments, index 0 is the first bar ('Until 18:20', exercise 191)
        const series = comp.groupChartData().get(ExerciseType.MODELING)!.series;
        comp.onSelect({ seriesIndex: 4, index: 0, meta: series[4].meta?.[0] });

        expect(routingStub).toHaveBeenCalledWith(['courses', 64, 'exercises', 191]);

        // clicks that carry no exercise must not navigate
        routingStub.mockClear();
        comp.onSelect({ seriesIndex: 0, index: 0, meta: undefined });
        expect(routingStub).not.toHaveBeenCalled();
    });

    describe('test chart filters', () => {
        let exercises: Exercise[];

        beforeEach(() => {
            exercises = setupExercisesWithCategories();
        });

        it('should filter optional exercises correctly', () => {
            const mockParticipationResult: ParticipationResultDTO = { rated: true, score: 100, participationId: 1 };
            vi.spyOn(scoresStorageService, 'getStoredParticipationResult').mockReturnValue(mockParticipationResult);
            comp.toggleNotIncludedInScoreExercises();

            expect(comp.currentlyHidingNotIncludedInScoreExercises()).toBe(false);
            expect(comp.ngxExerciseGroups().size).toBe(3);
            const modelingExercises = comp.ngxExerciseGroups().get(ExerciseType.MODELING)!;
            expect(modelingExercises).toHaveLength(5);
            expect(modelingExercises[0].name).toBe('Until 18:20');
            expect(modelingExercises[1].name).toBe('Until 18:20 too');
        });

        it('should toggle categories', () => {
            const getCurrentFilterStateMock = vi.spyOn(categoryFilter, 'getCurrentFilterState').mockReturnValue(false);
            const toggleCategoryMock = vi.spyOn(categoryFilter, 'toggleCategory').mockReturnValue(exercises);

            comp.toggleCategory('test1');

            expect(getCurrentFilterStateMock).toHaveBeenCalledOnce();
            expect(getCurrentFilterStateMock).toHaveBeenCalledWith('test1');
            expect(toggleCategoryMock).toHaveBeenCalledOnce();
            expect(toggleCategoryMock).toHaveBeenCalledWith(exercises, 'test1');
        });

        it('should toggle all categories', () => {
            const toggleAllCategoriesMock = vi.spyOn(categoryFilter, 'toggleAllCategories').mockReturnValue(exercises);

            comp.toggleAllCategories();

            expect(toggleAllCategoriesMock).toHaveBeenCalledOnce();
            expect(toggleAllCategoriesMock).toHaveBeenCalledWith(exercises);
        });

        it('should toggle exercises with no categories', () => {
            const toggleExercisesWithNoCategoryMock = vi.spyOn(categoryFilter, 'toggleExercisesWithNoCategory').mockReturnValue(exercises);

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
            vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(courseToAdd);
            comp.ngOnInit();
            // return all exercises that are included in score
            return [modelingExercises[0], modelingExercises[2], modelingExercises[3], modelingExercises[4], quizWithCategory];
        };
    });
});
