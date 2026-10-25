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
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { CourseStatisticsComponent } from 'app/course/overview/course-statistics/course-statistics.component';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ChartCategoryFilter } from 'app/exercise/chart/chart-category-filter';
import dayjs from 'dayjs/esm';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { CourseCompetencyService } from 'app/atlas/shared/services/course-competency.service';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CourseOverviewExercisesService } from 'app/course/overview/services/course-overview-exercises.service';
import { CourseExercisesForOverviewDTO } from 'app/course/shared/entities/course-exercises-for-overview-dto';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradeDTO } from 'app/assessment/shared/entities/grade-step.model';
import { TumAetUiChartTooltipConfig } from '@tumaet/ui-angular';
import { CourseStatisticsResultsComponent } from './course-statistics-results.component';
import { CourseAvailableTabsService } from 'app/course/overview/services/course-available-tabs.service';
import { CourseStatisticsService } from './course-statistics.service';
import { throwError } from 'rxjs';

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
        fixture.destroy();
        vi.restoreAllMocks();
    });

    it('should show the translated doughnut chart label as tooltip title and the value as body', () => {
        const tooltip = comp.doughnutConfig.tooltip as TumAetUiChartTooltipConfig;
        const datum = { seriesIndex: 0, index: 0, label: 'artemisApp.courseOverview.statistics.missingPointsLabel', value: 400 };

        expect(tooltip.title!([datum])).toBe('artemisApp.courseOverview.statistics.missingPointsLabel');
        expect(tooltip.label!(datum)).toBe('400');
    });

    it('hands the doughnut translated labels, because the chart renders them into its data table', () => {
        const translateService = TestBed.inject(TranslateService);
        vi.spyOn(translateService, 'instant').mockImplementation((key: string | string[]) => `translated:${key}`);
        comp.doughnutChartEntries.set([
            { name: 'artemisApp.courseOverview.statistics.quizPointLabel', value: 5.7, color: '#000000' },
            { name: 'artemisApp.courseOverview.statistics.missingPointsLabel', value: 5.3, color: '#ffffff' },
        ]);

        // The data table is the only way a screen reader gets the figures, so a raw key there is what the user
        // is read out. The tooltip translating on its own is not enough.
        expect(comp.doughnutData().labels).toEqual([
            'translated:artemisApp.courseOverview.statistics.quizPointLabel',
            'translated:artemisApp.courseOverview.statistics.missingPointsLabel',
        ]);
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
        expect(comp.resultExercises().length).toBe(1);
        expect(firstLoad.observed).toBe(true);
        expect(firstCourseUpdates.observed).toBe(true);
        expect(firstGrade.observed).toBe(true);

        params.next({ courseId: '2' });

        expect(comp.courseId).toBe(2);
        expect(comp.course()?.id).toBe(2);
        expect(comp.resultExercises().length).toBe(0);
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
        const results = fixture.debugElement.query(By.directive(CourseStatisticsResultsComponent)).componentInstance as CourseStatisticsResultsComponent;
        expect(results.types()).toHaveLength(4);
        expect(results.rows()).toHaveLength(courseToAdd.exercises.length);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="statistics-exercise-link"]')).toHaveLength(courseToAdd.exercises.length);
        expect(fixture.debugElement.query(By.css('tumaet-ui-doughnut-chart'))).toBeTruthy();
    });

    it('counts a submitted exercise even while its submission is withheld until assessment', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({
            ...course,
            exercises: [{ ...new TextExercise(undefined, undefined), id: 1, studentParticipations: [{ ...new StudentParticipation(), hasSubmitted: true, submissions: [] }] }],
        });
        fixture.detectChanges();
        expect(comp.submittedExerciseCount()).toBe(1);
    });

    it('hides optional sections after a successful empty response', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
        vi.spyOn(TestBed.inject(CourseAvailableTabsService), 'loadIfNeeded').mockReturnValue(
            of({ lectures: true, competencies: false, exams: false, tutorialGroups: false, iris: false, faq: false, learningPaths: false, communication: false, training: false }),
        );
        vi.spyOn(TestBed.inject(CourseStatisticsService), 'getLearningStatistics').mockReturnValue(of([]));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('#statistics-material-title')).toBeNull();
    });

    it('keeps the score ring and results available if optional material statistics fail', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
        vi.spyOn(TestBed.inject(CourseAvailableTabsService), 'loadIfNeeded').mockReturnValue(
            of({ lectures: true, competencies: false, exams: false, tutorialGroups: false, iris: false, faq: false, learningPaths: false, communication: false, training: false }),
        );
        vi.spyOn(TestBed.inject(CourseStatisticsService), 'getLearningStatistics').mockReturnValue(throwError(() => new Error('unavailable')));
        fixture.detectChanges();
        expect(comp.materialsFailed()).toBe(true);
        expect(fixture.debugElement.query(By.css('tumaet-ui-doughnut-chart'))).toBeTruthy();
        expect(fixture.debugElement.query(By.directive(CourseStatisticsResultsComponent))).toBeTruthy();
    });

    it('should filter all exercises not included in score', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ ...course, exercises: modelingExercises });
        fixture.detectChanges();
        expect(comp.resultExercises().map((exercise) => exercise.title)).not.toContain('test 17.06. 2');
        comp.toggleNotIncludedInScoreExercises();
        expect(comp.resultExercises().map((exercise) => exercise.title)).toContain('test 17.06. 2');
        comp.toggleNotIncludedInScoreExercises();
        expect(comp.resultExercises().map((exercise) => exercise.title)).not.toContain('test 17.06. 2');
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
        expect(comp.pointBreakdown()).toContainEqual({ type: ExerciseType.MODELING, achieved: 20, attainable: 36, total: 36 });

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

    it('keeps a cross-type variant cap out of the ring partition without changing authoritative type totals', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({
            ...course,
            accuracyOfScores: 1,
            exercises: [{ id: 1, type: ExerciseType.PROGRAMMING, exerciseVariantGroup: { id: 1, maxPoints: 10 } }] as Exercise[],
        });
        const scores = (points: number) =>
            new CourseScores(116, 116, 0, { absoluteScore: points, absoluteScoreTotal: points, relativeScore: 0, currentRelativeScore: 0, presentationScore: 0 });
        vi.spyOn(scoresStorageService, 'getStoredScoresPerExerciseType').mockReturnValue(
            new Map([
                [ExerciseType.PROGRAMMING, scores(122.9)],
                [ExerciseType.QUIZ, scores(36.3)],
                [ExerciseType.MODELING, scores(29.4)],
                [ExerciseType.TEXT, scores(34.5)],
                [ExerciseType.FILE_UPLOAD, scores(25.6)],
            ]),
        );
        vi.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(
            new CourseScores(580, 580, 0, { absoluteScore: 244.3, absoluteScoreTotal: 248.7, relativeScore: 42.1, currentRelativeScore: 42.1, presentationScore: 0 }),
        );
        fixture.detectChanges();
        const entries = comp.doughnutChartEntries();
        expect(entries.reduce((total, entry) => total + entry.value, 0)).toBeCloseTo(580);
        expect(entries.map((entry) => entry.value)).toEqual([244.3, 335.7]);
        expect(comp.pointBreakdown()).toContainEqual({ type: ExerciseType.PROGRAMMING, achieved: 122.9, attainable: 116, total: 116 });
        expect(comp.pointBreakdown()).toContainEqual({ type: ExerciseType.TEXT, achieved: 34.5, attainable: 116, total: 116 });
        expect(fixture.nativeElement.querySelector('[data-testid="statistics-type-totals-explanation"]')).not.toBeNull();
    });

    it('honors a one-point cross-type cap at whole-point course precision', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({
            ...course,
            accuracyOfScores: 0,
            exercises: [{ id: 1, type: ExerciseType.PROGRAMMING, exerciseVariantGroup: { id: 1, maxPoints: 5 } }] as Exercise[],
        });
        const scores = new CourseScores(5, 5, 0, { absoluteScore: 3, absoluteScoreTotal: 3, relativeScore: 60, currentRelativeScore: 60, presentationScore: 0 });
        vi.spyOn(scoresStorageService, 'getStoredScoresPerExerciseType').mockReturnValue(
            new Map([
                [ExerciseType.PROGRAMMING, scores],
                [ExerciseType.TEXT, scores],
            ]),
        );
        vi.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(
            new CourseScores(10, 10, 0, { absoluteScore: 5, absoluteScoreTotal: 6, relativeScore: 50, currentRelativeScore: 50, presentationScore: 0 }),
        );
        fixture.detectChanges();
        expect(comp.doughnutChartEntries().map((entry) => entry.value)).toEqual([5, 5]);
    });

    it('retains additive type colors and bonus scores above one hundred percent without a negative missing slice', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({
            ...course,
            exercises: [{ id: 1, type: ExerciseType.PROGRAMMING, exerciseVariantGroup: { id: 1, maxPoints: 100 } }] as Exercise[],
        });
        const scores = (points: number) =>
            new CourseScores(50, 50, 0, { absoluteScore: points, absoluteScoreTotal: points, relativeScore: 0, currentRelativeScore: 0, presentationScore: 0 });
        vi.spyOn(scoresStorageService, 'getStoredScoresPerExerciseType').mockReturnValue(
            new Map([
                [ExerciseType.PROGRAMMING, scores(90)],
                [ExerciseType.QUIZ, scores(60)],
            ]),
        );
        vi.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(
            new CourseScores(100, 100, 0, { absoluteScore: 150, absoluteScoreTotal: 150, relativeScore: 150, currentRelativeScore: 150, presentationScore: 0 }),
        );
        fixture.detectChanges();
        expect(comp.doughnutChartEntries().map((entry) => entry.value)).toEqual([90, 60]);
        expect(comp.doughnutChartEntries().map((entry) => entry.name)).toEqual([
            'artemisApp.courseOverview.statistics.programmingPointLabel',
            'artemisApp.courseOverview.statistics.quizPointLabel',
        ]);
        expect(comp.overallPoints()).toBe(150);
        expect(fixture.nativeElement.querySelector('[data-testid="statistics-type-totals-explanation"]')).toBeNull();
    });

    it('keeps the required presentation count distinct from advanced presentation points', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ ...course, presentationScore: 3 });
        fixture.detectChanges();
        comp.overallPresentationScore.set(2);
        comp.reachablePresentationPoints.set(0);
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('artemisApp.courseOverview.statistics.totalPresentationScore');
        expect(fixture.nativeElement.textContent).toContain('artemisApp.courseOverview.statistics.presentationScoreBonus');
        expect(fixture.nativeElement.textContent).not.toContain('artemisApp.courseOverview.statistics.achievablePresentationPoints');
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

    describe('test chart filters', () => {
        let exercises: Exercise[];

        beforeEach(() => {
            exercises = setupExercisesWithCategories();
        });

        it('should filter optional exercises correctly', () => {
            comp.toggleNotIncludedInScoreExercises();
            expect(comp.currentlyHidingNotIncludedInScoreExercises()).toBe(false);
            expect(new Set(comp.resultExercises().map((exercise) => exercise.type)).size).toBe(3);
            expect(comp.resultExercises().filter((exercise) => exercise.type === ExerciseType.MODELING)).toHaveLength(5);
        });

        it('should toggle categories', () => {
            const getCurrentFilterStateMock = vi.spyOn(categoryFilter, 'getCurrentFilterState').mockReturnValue(false);
            const toggleCategoryMock = vi.spyOn(categoryFilter, 'toggleCategory').mockReturnValue(exercises);

            comp.toggleCategory('test1');

            expect(comp.numberOfAppliedFilters()).toBe(2);
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
    it('shows released material counts and distinguishes competency progress from mastery', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
        vi.spyOn(TestBed.inject(CourseAvailableTabsService), 'loadIfNeeded').mockReturnValue(
            of({ lectures: true, competencies: true, exams: false, tutorialGroups: false, iris: false, faq: false, learningPaths: false, communication: false, training: false }),
        );
        vi.spyOn(TestBed.inject(CourseStatisticsService), 'getLearningStatistics').mockReturnValue(
            of([
                { lectureId: 1, type: 'attachment', total: 5, completed: 3 },
                { lectureId: 1, type: 'text', total: 3, completed: 2 },
                { lectureId: 2, type: 'online', total: 2, completed: 1 },
            ]),
        );
        const mastered = new Competency();
        mastered.id = 31;
        mastered.title = 'Object-oriented design';
        mastered.masteryThreshold = 80;
        mastered.userProgress = [{ progress: 100, confidence: 0.9 }];
        const developing = new Competency();
        developing.id = 32;
        developing.masteryThreshold = 80;
        developing.userProgress = [{ progress: 70, confidence: 0.7 }];
        vi.spyOn(TestBed.inject(CourseCompetencyService), 'getAllForCourse').mockReturnValue(of(new HttpResponse({ body: [mastered, developing] })));
        fixture.detectChanges();
        expect(comp.materialTotal()).toBe(10);
        expect(comp.materialCompleted()).toBe(6);
        expect(comp.materialBreakdown()).toEqual([
            { type: 'attachment', total: 5, completed: 3 },
            { type: 'text', total: 3, completed: 2 },
            { type: 'online', total: 2, completed: 1 },
        ]);
        expect(comp.masteredCompetencies()).toBe(1);
        expect(comp.getProgress(developing.userProgress[0])).toBe(70);
        expect(comp.getMastery(developing.userProgress[0])).toBe(49);
        expect(fixture.nativeElement.textContent).toContain('Object-oriented design');
        expect(comp.showMaterials()).toBe(true);
        expect(comp.showCompetencies()).toBe(true);
        comp.toggleFilters();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('fieldset')).not.toBeNull();
    });

    it('keeps material statistics available while a competency request fails and succeeds on retry', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
        vi.spyOn(TestBed.inject(CourseAvailableTabsService), 'loadIfNeeded').mockReturnValue(
            of({ lectures: true, competencies: true, exams: false, tutorialGroups: false, iris: false, faq: false, learningPaths: false, communication: false, training: false }),
        );
        vi.spyOn(TestBed.inject(CourseStatisticsService), 'getLearningStatistics').mockReturnValue(of([{ lectureId: 1, type: 'text', total: 4, completed: 2 }]));
        vi.spyOn(TestBed.inject(CourseCompetencyService), 'getAllForCourse')
            .mockReturnValueOnce(throwError(() => new Error('unavailable')))
            .mockReturnValueOnce(of(new HttpResponse({ body: [] })));
        fixture.detectChanges();
        expect(comp.competenciesFailed()).toBe(true);
        expect(comp.materialCompleted()).toBe(2);
        expect(comp.showCompetencies()).toBe(true);
        comp.loadLearningStatistics();
        fixture.detectChanges();
        expect(comp.competenciesFailed()).toBe(false);
        expect(comp.showCompetencies()).toBe(false);
        expect(comp.materialCompleted()).toBe(2);
    });

    it('retains exercise statistics when optional feature metadata is unavailable', () => {
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue(course);
        vi.spyOn(TestBed.inject(CourseAvailableTabsService), 'loadIfNeeded').mockReturnValue(throwError(() => new Error('unavailable')));
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('tumaet-ui-doughnut-chart'))).toBeTruthy();
        expect(comp.showMaterials()).toBe(false);
        expect(comp.showCompetencies()).toBe(false);
    });

    it('filters categories reversibly without changing authoritative course totals', () => {
        const first = new TextExercise(undefined, undefined);
        first.id = 31;
        first.title = 'Class diagrams';
        first.categories = [new ExerciseCategory('Design', '#fff')];
        const second = new TextExercise(undefined, undefined);
        second.id = 32;
        second.title = 'Design rationale';
        second.categories = [new ExerciseCategory('Reflection', '#fff')];
        vi.spyOn(courseStorageService, 'getCourse').mockReturnValue({ ...course, exercises: [first, second] });
        const totals = new CourseScores(20, 20, 0, { absoluteScore: 15, absoluteScoreTotal: 15, relativeScore: 75, currentRelativeScore: 75, presentationScore: 0 });
        vi.spyOn(scoresStorageService, 'getStoredTotalScores').mockReturnValue(totals);
        fixture.detectChanges();
        expect(comp.resultExercises().map((exercise) => exercise.id)).toEqual([31, 32]);
        expect(comp.numberOfAppliedFilters()).toBe(1);
        comp.toggleCategory('Design');
        expect(comp.resultExercises().map((exercise) => exercise.id)).toEqual([32]);
        expect(comp.numberOfAppliedFilters()).toBe(2);
        expect(comp.overallPoints()).toBe(15);
        comp.toggleCategory('Design');
        expect(comp.resultExercises().map((exercise) => exercise.id)).toEqual([31, 32]);
        expect(comp.numberOfAppliedFilters()).toBe(1);
        expect(comp.overallPoints()).toBe(15);
    });
});
