import { expect, vi } from 'vitest';
import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRoute } from '@angular/router';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ExerciseScoresComponent, FilterProp } from 'app/exercise/exercise-scores/exercise-scores.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ResultService } from 'app/exercise/result/result.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Range } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';
import { Subscription, of } from 'rxjs';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { MockParticipationService } from 'test/helpers/mocks/service/mock-participation.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockProgrammingSubmissionService } from 'test/helpers/mocks/service/mock-programming-submission.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';

describe('Exercise Scores Component', () => {
    setupTestBed({ zoneless: true });
    let component: ExerciseScoresComponent;
    let fixture: ComponentFixture<ExerciseScoresComponent>;
    let resultService: ResultService;
    let participationService: ParticipationService;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let courseService: CourseManagementService;
    let exerciseService: ExerciseService;

    const exercise: Exercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };

    const course = new Course();
    course.id = 1;

    const team: Team = {
        name: 'name',
        shortName: 'shortName',
        students: [
            {
                login: 'login1',
                name: 'name1',
                internal: true,
            },
            {
                login: 'login2',
                name: 'name2',
                internal: true,
            },
        ],
    };

    const participation: ProgrammingExerciseStudentParticipation = {
        buildPlanId: '1',
        userIndependentRepositoryUri: 'url',
        participantIdentifier: 'participationId',
        participantName: 'participantName',
        submissions: [
            {
                results: [{ assessmentType: AssessmentType.MANUAL }],
            },
        ],
    };
    const scoresToFilter = [3, 11, 22, 33, 44, 55, 66, 77, 88, 100];
    let participationsToFilter: Participation[];
    const filterRanges = [
        new Range(0, 10),
        new Range(10, 20),
        new Range(20, 30),
        new Range(30, 40),
        new Range(40, 50),
        new Range(50, 60),
        new Range(60, 70),
        new Range(70, 80),
        new Range(80, 90),
        new Range(90, 100),
    ];

    const route = {
        data: of({ courseId: 1 }),
        children: [],
        params: of({ courseId: 1, exerciseId: 2 }),
        snapshot: { queryParamMap: { get: () => undefined } },
    } as any as ActivatedRoute;

    beforeAll(() => {
        participationsToFilter = scoresToFilter.map((score: number) => {
            const studentParticipation = new StudentParticipation();
            const submission = new TextSubmission();
            submission.results = [{ score, successful: score >= 100 }];
            submission.participation = studentParticipation;
            studentParticipation.submissions = [submission];
            return studentParticipation;
        });
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseScoresComponent],
            providers: [
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: ActivatedRoute, useValue: route },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: ParticipationService, useClass: MockParticipationService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseScoresComponent);
        component = fixture.componentInstance;
        resultService = TestBed.inject(ResultService);
        participationService = TestBed.inject(ParticipationService);
        programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
        courseService = TestBed.inject(CourseManagementService);
        exerciseService = TestBed.inject(ExerciseService);
        component.exercise = exercise;
        vi.spyOn(programmingSubmissionService, 'unsubscribeAllWebsocketTopics');
        component.paramSub = new Subscription();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllTimers();
        vi.useRealTimers();
    });

    it('should be correctly set onInit', () => {
        const findCourseSpy = vi.spyOn(courseService, 'find');
        const findExerciseSpy = vi.spyOn(exerciseService, 'find');
        const getParticipationsMock = vi
            .spyOn(participationService, 'findAllParticipationsByExercise')
            .mockReturnValue(of(new HttpResponse<Result[]>({ body: participationsToFilter })));

        component.ngOnInit();
        expect(findCourseSpy).toHaveBeenCalledOnce();
        expect(findCourseSpy).toHaveBeenCalledWith(1);
        expect(findExerciseSpy).toHaveBeenCalledOnce();
        expect(findExerciseSpy).toHaveBeenCalledWith(2);
        expect(getParticipationsMock).toHaveBeenCalledOnce();
        expect(getParticipationsMock).toHaveBeenCalledWith(2, true);
        expect(component.filteredParticipations).toEqual(participationsToFilter);
        expect(component.participationsPerFilter).toEqual(
            new Map([
                ['All', 10],
                ['Successful', 1],
                ['Unsuccessful', 9],
            ]),
        );
    });

    it('should get exercise participation link for exercise without an exercise group', () => {
        const expectedLink = ['/course-management', course.id!.toString(), 'programming-exercises', exercise.id!.toString(), 'participations', '1', 'submissions'];
        component.course = course;

        const returnedLink = component.getExerciseParticipationsLink(1);

        expect(returnedLink).toEqual(expectedLink);
    });

    it('should get exercise participation link for exercise with an exercise group', () => {
        const expectedLink = [
            '/course-management',
            course.id!.toString(),
            'exams',
            '1',
            'exercise-groups',
            '1',
            'programming-exercises',
            exercise.id!.toString(),
            'participations',
            '2',
        ];
        component.course = course;
        component.exercise.exerciseGroup = {
            id: 1,
            exam: {
                id: 1,
            },
        };

        const returnedLink = component.getExerciseParticipationsLink(2);

        expect(returnedLink).toEqual(expectedLink);
    });

    it('should update result', () => {
        vi.useFakeTimers();
        component.updateParticipationFilter(component.FilterProp.MANUAL);

        expect(component.isLoading).toBe(true);
        vi.runAllTimers();
        expect(component.resultCriteria.filterProp).toBe(component.FilterProp.MANUAL);
        expect(component.isLoading).toBe(false);
    });

    it.each([
        [FilterProp.ALL, {} as Participation, true],
        [FilterProp.SUCCESSFUL, { submissions: [{ results: [{ successful: true }] }] } as Participation, true],
        [FilterProp.SUCCESSFUL, { submissions: [{ results: [{ successful: false }] }] } as Participation, false],
        [FilterProp.SUCCESSFUL, { submissions: [{ results: [{ successful: false }, { successful: true }] }] } as Participation, true], // always use the latest result
        [FilterProp.SUCCESSFUL, { submissions: [{ results: [{ successful: true }, { successful: false }] }] } as Participation, false],
        [FilterProp.UNSUCCESSFUL, { submissions: [{ results: [{ successful: false }] }] } as Participation, true],
        [FilterProp.UNSUCCESSFUL, { submissions: [{ results: [{ successful: true }] }] } as Participation, false],
        [FilterProp.UNSUCCESSFUL, { submissions: [{ results: [{ successful: false }, { successful: true }] }] } as Participation, false],
        [FilterProp.UNSUCCESSFUL, { submissions: [{ results: [{ successful: true }, { successful: false }] }] } as Participation, true],
        [FilterProp.BUILD_FAILED, { submissions: [{ buildFailed: true, results: [{}] } as Submission] } as Participation, true],
        [FilterProp.BUILD_FAILED, { submissions: [{ results: [{}] }] } as Participation, false],
        [FilterProp.MANUAL, { submissions: [{ results: [{ assessmentType: AssessmentType.SEMI_AUTOMATIC }] }] } as Participation, true],
        [FilterProp.MANUAL, { submissions: [{ results: [{ assessmentType: AssessmentType.AUTOMATIC }] }] } as Participation, false],
        [
            FilterProp.MANUAL,
            { submissions: [{ results: [{ assessmentType: AssessmentType.AUTOMATIC }, { assessmentType: AssessmentType.SEMI_AUTOMATIC }] }] } as Participation,
            true,
        ],
        [FilterProp.AUTOMATIC, { submissions: [{ results: [{ assessmentType: AssessmentType.AUTOMATIC }] }] } as Participation, true],
        [FilterProp.AUTOMATIC, { submissions: [{ results: [{ assessmentType: AssessmentType.SEMI_AUTOMATIC }] }] } as Participation, false],
        [
            FilterProp.AUTOMATIC,
            { submissions: [{ results: [{ assessmentType: AssessmentType.AUTOMATIC }, { assessmentType: AssessmentType.SEMI_AUTOMATIC }] }] } as Participation,
            false,
        ],
        [FilterProp.LOCKED, { submissions: [{ results: [{ completionDate: undefined }] }] } as Participation, true],
        [FilterProp.LOCKED, { submissions: [{ results: [{ completionDate: dayjs() }] }] } as Participation, false],
    ])('should filter participations correctly', (filter: FilterProp, participation: Participation, expected: boolean) => {
        component.resultCriteria.filterProp = filter;
        expect(component.filterParticipationsByProp(participation)).toBe(expected);
    });

    it.each([
        [FilterProp.ALL, { type: ExerciseType.PROGRAMMING } as Exercise, false, true],
        [FilterProp.ALL, { type: ExerciseType.TEXT }, true, true],
        [FilterProp.SUCCESSFUL, { type: ExerciseType.PROGRAMMING }, false, true],
        [FilterProp.SUCCESSFUL, { type: ExerciseType.TEXT }, true, true],
        [FilterProp.UNSUCCESSFUL, { type: ExerciseType.PROGRAMMING }, false, true],
        [FilterProp.UNSUCCESSFUL, { type: ExerciseType.TEXT }, true, true],
        [FilterProp.BUILD_FAILED, { type: ExerciseType.PROGRAMMING }, false, true],
        [FilterProp.BUILD_FAILED, { type: ExerciseType.TEXT }, true, false],
        [FilterProp.MANUAL, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: true }, false, true],
        [FilterProp.MANUAL, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: false }, false, false],
        [FilterProp.MANUAL, { type: ExerciseType.TEXT }, true, true],
        [FilterProp.AUTOMATIC, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: true }, false, true],
        [FilterProp.AUTOMATIC, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: false }, false, false],
        [FilterProp.AUTOMATIC, { type: ExerciseType.TEXT }, true, true],
        [FilterProp.LOCKED, { type: ExerciseType.PROGRAMMING, isAtLeastInstructor: true }, true, true],
        [FilterProp.LOCKED, { type: ExerciseType.PROGRAMMING, isAtLeastInstructor: false }, false, false],
        [FilterProp.LOCKED, { type: ExerciseType.TEXT }, true, false],
    ])('should determine if filter is relevant for exercise configuration', (filter: FilterProp, exercise: Exercise, newManualResultsAllowed: boolean, expected: boolean) => {
        component.exercise = exercise;
        component.newManualResultAllowed = newManualResultsAllowed;
        expect(component.isFilterRelevantForConfiguration(filter)).toBe(expected);
    });

    it('should return build plan id', () => {
        expect(component.buildPlanId(participation)).toBe('1');
    });

    it('should return project key', () => {
        component.exercise = {
            type: ExerciseType.PROGRAMMING,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
            projectKey: 'key',
        } as ProgrammingExercise;

        expect(component.projectKey()).toBe('key');
    });

    it('should return repository link', () => {
        expect(component.getRepositoryLink(participation)).toBe('url');
    });

    it('should export names correctly for student participation', () => {
        component.participations = [participation];
        const rows = ['participantName'];
        const resultServiceStub = vi.spyOn(resultService, 'triggerDownloadCSV');

        component.exportNames();

        expect(resultServiceStub).toHaveBeenCalledOnce();
        expect(resultServiceStub).toHaveBeenCalledWith(rows, 'results-names.csv');
    });

    it('should export names correctly for team participation', () => {
        participation.team = team;
        component.participations = [participation];
        const rows = ['Team Name,Team Short Name,Students', 'name,shortName,"name1, name2"'];
        const resultServiceStub = vi.spyOn(resultService, 'triggerDownloadCSV');

        component.exportNames();

        expect(resultServiceStub).toHaveBeenCalledOnce();
        expect(resultServiceStub).toHaveBeenCalledWith(rows, 'results-names.csv');
        participation.team = undefined;
    });

    it('should search result for student participation', () => {
        participation.student = {
            login: 'login',
            name: 'name',
            internal: true,
        };

        expect(component.searchParticipationFormatter(participation)).toBe('login (name)');

        participation.student = undefined;
    });

    it('should search result for team', () => {
        participation.team = team;
        const expectedResult = 'name (shortName) ⟹ name1 (login1), name2 (login2)';

        expect(component.searchParticipationFormatter(participation)).toBe(expectedResult);

        participation.team = undefined;
    });

    it('should search result and return empty string', () => {
        expect(component.searchParticipationFormatter(participation)).toBe('');
    });

    it('should search text from result', () => {
        expect(component.searchTextFromParticipation(participation)).toBe('participationId');
    });

    it('should refresh properly', () => {
        const participationServiceStub = vi
            .spyOn(participationService, 'findAllParticipationsByExercise')
            .mockReturnValue(of(new HttpResponse<Result[]>({ body: [participation] })));

        component.refresh();

        expect(participationServiceStub).toHaveBeenCalledOnce();
        expect(participationServiceStub).toHaveBeenCalledWith(1, true);
        expect(component.participations).toEqual([participation]);
        expect(component.filteredParticipations).toEqual([participation]);
        expect(component.isLoading).toBe(false);
    });

    it.each(filterRanges)('should filter results correctly and reset the filter', (rangeFilter: Range) => {
        component.rangeFilter = rangeFilter;
        component.participations = [participation];

        const returnedResults = component.filterByScoreRange(participationsToFilter);

        expect(returnedResults).toEqual([participationsToFilter[filterRanges.indexOf(rangeFilter)]]);

        component.resetFilterOptions();

        expect(component.rangeFilter).toBeUndefined();
        expect(component.filteredParticipations).toEqual([participation]);
    });
});
