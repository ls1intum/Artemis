import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Result } from 'app/entities/result.model';
import { Submission } from 'app/entities/submission.model';
import { Team } from 'app/entities/team.model';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-button.component';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ExerciseScoresExportButtonComponent } from 'app/exercises/shared/exercise-scores/exercise-scores-export-button.component';
import { ExerciseScoresComponent, FilterProp } from 'app/exercises/shared/exercise-scores/exercise-scores.component';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { SubmissionExportButtonComponent } from 'app/exercises/shared/submission-export/submission-export-button.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Range } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { Subscription, of } from 'rxjs';
import { MockHasAnyAuthorityDirective } from '../../../../helpers/mocks/directive/mock-has-any-authority.directive';
import { MockTranslateValuesDirective } from '../../../../helpers/mocks/directive/mock-translate-values.directive';
import { MockCourseManagementService } from '../../../../helpers/mocks/service/mock-course-management.service';
import { MockExerciseService } from '../../../../helpers/mocks/service/mock-exercise.service';
import { MockParticipationService } from '../../../../helpers/mocks/service/mock-participation.service';
import { MockProfileService } from '../../../../helpers/mocks/service/mock-profile.service';
import { MockProgrammingSubmissionService } from '../../../../helpers/mocks/service/mock-programming-submission.service';
import { MockResultService } from '../../../../helpers/mocks/service/mock-result.service';
import { ArtemisTestModule } from '../../../../test.module';

describe('Exercise Scores Component', () => {
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
                guidedTourSettings: [],
            },
            {
                login: 'login2',
                name: 'name2',
                internal: true,
                guidedTourSettings: [],
            },
        ],
    };

    const participation: ProgrammingExerciseStudentParticipation = {
        buildPlanId: '1',
        userIndependentRepositoryUri: 'url',
        participantIdentifier: 'participationId',
        participantName: 'participantName',
        results: [{ assessmentType: AssessmentType.MANUAL }],
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
            studentParticipation.results = [{ score, successful: score >= 100 }];
            return studentParticipation;
        });
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule.withRoutes([]), MockModule(NgxDatatableModule)],
            declarations: [
                ExerciseScoresComponent,
                MockComponent(ExerciseScoresExportButtonComponent),
                MockComponent(ProgrammingAssessmentRepoExportButtonComponent),
                MockComponent(SubmissionExportButtonComponent),
                MockComponent(DataTableComponent),
                MockComponent(ResultComponent),
                MockDirective(FeatureToggleLinkDirective),
                MockTranslateValuesDirective,
                MockHasAnyAuthorityDirective,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgModel),
            ],
            providers: [
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: ActivatedRoute, useValue: route },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: ParticipationService, useClass: MockParticipationService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseScoresComponent);
                component = fixture.componentInstance;
                resultService = TestBed.inject(ResultService);
                participationService = TestBed.inject(ParticipationService);
                programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
                courseService = TestBed.inject(CourseManagementService);
                exerciseService = TestBed.inject(ExerciseService);
                component.exercise = exercise;
                jest.spyOn(programmingSubmissionService, 'unsubscribeAllWebsocketTopics');
                component.paramSub = new Subscription();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should be correctly set onInit', fakeAsync(() => {
        const findCourseSpy = jest.spyOn(courseService, 'find');
        const findExerciseSpy = jest.spyOn(exerciseService, 'find');
        const getParticipationsMock = jest
            .spyOn(participationService, 'findAllParticipationsByExercise')
            .mockReturnValue(of(new HttpResponse<Result[]>({ body: participationsToFilter })));

        component.ngOnInit();
        tick();

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
    }));

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

    it('should update result', fakeAsync(() => {
        component.updateParticipationFilter(component.FilterProp.MANUAL);

        expect(component.isLoading).toBeTrue();
        tick();
        expect(component.resultCriteria.filterProp).toBe(component.FilterProp.MANUAL);
        expect(component.isLoading).toBeFalse();
    }));

    it.each([
        [FilterProp.ALL, {} as Participation, true],
        [FilterProp.SUCCESSFUL, { results: [{ successful: true }] } as Participation, true],
        [FilterProp.SUCCESSFUL, { results: [{ successful: false }] } as Participation, false],
        [FilterProp.SUCCESSFUL, { results: [{ successful: false }, { successful: true }] } as Participation, true], // always use the latest result
        [FilterProp.SUCCESSFUL, { results: [{ successful: true }, { successful: false }] } as Participation, false],
        [FilterProp.UNSUCCESSFUL, { results: [{ successful: false }] } as Participation, true],
        [FilterProp.UNSUCCESSFUL, { results: [{ successful: true }] } as Participation, false],
        [FilterProp.UNSUCCESSFUL, { results: [{ successful: false }, { successful: true }] } as Participation, false],
        [FilterProp.UNSUCCESSFUL, { results: [{ successful: true }, { successful: false }] } as Participation, true],
        [FilterProp.BUILD_FAILED, { results: [{}], submissions: [{ buildFailed: true } as Submission] } as Participation, true],
        [FilterProp.BUILD_FAILED, { results: [{}], submissions: [{}] } as Participation, false],
        [FilterProp.MANUAL, { results: [{ assessmentType: AssessmentType.SEMI_AUTOMATIC }] } as Participation, true],
        [FilterProp.MANUAL, { results: [{ assessmentType: AssessmentType.AUTOMATIC }] } as Participation, false],
        [FilterProp.MANUAL, { results: [{ assessmentType: AssessmentType.AUTOMATIC }, { assessmentType: AssessmentType.SEMI_AUTOMATIC }] } as Participation, true],
        [FilterProp.AUTOMATIC, { results: [{ assessmentType: AssessmentType.AUTOMATIC }] } as Participation, true],
        [FilterProp.AUTOMATIC, { results: [{ assessmentType: AssessmentType.SEMI_AUTOMATIC }] } as Participation, false],
        [FilterProp.AUTOMATIC, { results: [{ assessmentType: AssessmentType.AUTOMATIC }, { assessmentType: AssessmentType.SEMI_AUTOMATIC }] } as Participation, false],
        [FilterProp.LOCKED, { results: [{ completionDate: undefined }] } as Participation, true],
        [FilterProp.LOCKED, { results: [{ completionDate: dayjs() }] } as Participation, false],
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
        const rows = ['data:text/csv;charset=utf-8,participantName'];
        const resultServiceStub = jest.spyOn(resultService, 'triggerDownloadCSV');

        component.exportNames();

        expect(resultServiceStub).toHaveBeenCalledOnce();
        expect(resultServiceStub).toHaveBeenCalledWith(rows, 'results-names.csv');
    });

    it('should export names correctly for team participation', () => {
        participation.team = team;
        component.participations = [participation];
        const rows = ['data:text/csv;charset=utf-8,Team Name,Team Short Name,Students', 'name,shortName,"name1, name2"'];
        const resultServiceStub = jest.spyOn(resultService, 'triggerDownloadCSV');

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
            guidedTourSettings: [],
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
        const participationServiceStub = jest
            .spyOn(participationService, 'findAllParticipationsByExercise')
            .mockReturnValue(of(new HttpResponse<Result[]>({ body: [participation] })));

        component.refresh();

        expect(participationServiceStub).toHaveBeenCalledOnce();
        expect(participationServiceStub).toHaveBeenCalledWith(1, true);
        expect(component.participations).toEqual([participation]);
        expect(component.filteredParticipations).toEqual([participation]);
        expect(component.isLoading).toBeFalse();
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
