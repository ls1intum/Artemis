import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ActivatedRoute } from '@angular/router';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { CommitHistoryComponent } from 'app/localvc/commit-history/commit-history.component';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { of } from 'rxjs';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { CommitsInfoComponent } from 'app/exercises/programming/shared/commits-info/commits-info.component';
import { MockComponent } from 'ng-mocks';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HttpResponse } from '@angular/common/http';

describe('CommitHistoryComponent', () => {
    let component: CommitHistoryComponent;
    let fixture: ComponentFixture<CommitHistoryComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let programmingExerciseService: ProgrammingExerciseService;
    let activatedRoute: MockActivatedRoute;

    const templateSubmissions: ProgrammingSubmission[] = [
        {
            id: 1,
            commitHash: 'templateCommit1',
            results: [{ id: 1, successful: true, completionDate: dayjs('2021-01-01'), submission: { id: 1, commitHash: 'templateCommit1' } as ProgrammingSubmission }],
        },
        {
            id: 2,
            commitHash: 'templateCommit2',
            results: [{ id: 2, successful: true, completionDate: dayjs('2021-01-02'), submission: { id: 2, commitHash: 'templateCommit2' } as ProgrammingSubmission }],
        },
    ];

    const solutionSubmissions: ProgrammingSubmission[] = [
        {
            id: 1,
            commitHash: 'solutionCommit1',
            results: [{ id: 1, successful: true, completionDate: dayjs('2021-01-01'), submission: { id: 1, commitHash: 'solutionCommit1' } as ProgrammingSubmission }],
        },
        {
            id: 2,
            commitHash: 'solutionCommit2',
            results: [{ id: 2, successful: true, completionDate: dayjs('2021-01-02'), submission: { id: 2, commitHash: 'solutionCommit2' } as ProgrammingSubmission }],
        },
    ];

    const mockTemplateCommit1: CommitInfo = { hash: 'templateCommit1', author: 'author1', message: 'message1', timestamp: dayjs('2021-01-01') };
    const mockTemplateCommit2: CommitInfo = { hash: 'templateCommit2', author: 'author2', message: 'message2', timestamp: dayjs('2021-01-02') };
    const mockTemplateCommits: CommitInfo[] = [mockTemplateCommit2, mockTemplateCommit1];

    const mockSolutionCommit1: CommitInfo = { hash: 'solutionCommit1', author: 'author1', message: 'message1', timestamp: dayjs('2021-01-01') };
    const mockSolutionCommit2: CommitInfo = { hash: 'solutionCommit2', author: 'author2', message: 'message2', timestamp: dayjs('2021-01-02') };
    const mockSolutionCommits: CommitInfo[] = [mockSolutionCommit2, mockSolutionCommit1];

    const mockTestCommits: CommitInfo[] = [
        { hash: 'testCommit1', author: 'author1', message: 'message1', timestamp: dayjs('2021-01-01') },
        { hash: 'testCommit2', author: 'author2', message: 'message2', timestamp: dayjs('2021-01-02') },
    ];

    const mockExerciseWithTemplateAndSolution: ProgrammingExercise = {
        id: 1,
        templateParticipation: { id: 1, repositoryUri: 'template-repo-uri', submissions: templateSubmissions },
        solutionParticipation: { id: 1, repositoryUri: 'solution-repo-uri', submissions: solutionSubmissions },
        numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
        studentAssignedTeamIdComputed: true,
        secondCorrectionEnabled: true,
    };

    // Define mock data for participation and commits
    const mockParticipation: ProgrammingExerciseStudentParticipation = {
        id: 2,
        repositoryUri: 'student-repo-uri',
        exercise: { id: 1, numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()], studentAssignedTeamIdComputed: true, secondCorrectionEnabled: true },
        results: [
            { id: 1, successful: true, completionDate: dayjs('2021-01-02'), submission: { id: 2, commitHash: 'commit2' } as ProgrammingSubmission },
            { id: 2, successful: false, completionDate: dayjs('2021-01-03'), submission: { id: 3, commitHash: 'commit3' } as ProgrammingSubmission },
        ],
        submissions: [
            { id: 2, commitHash: 'commit2' },
            { id: 3, commitHash: 'commit3' },
        ] as ProgrammingSubmission[],
    };
    // template commit
    const commit1: CommitInfo = { hash: 'commit1', author: 'author1', message: 'message1', timestamp: dayjs('2021-01-01') };

    const commit2: CommitInfo = { hash: 'commit2', author: 'author2', message: 'message2', timestamp: dayjs('2021-01-02') };
    const commit3: CommitInfo = { hash: 'commit3', author: 'author3', message: 'message3', timestamp: dayjs('2021-01-03') };
    const mockCommits: CommitInfo[] = [commit2, commit3, commit1];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CommitHistoryComponent, MockComponent(CommitsInfoComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
            ],
        }).compileComponents();
    });

    function setupComponent() {
        fixture = TestBed.createComponent(CommitHistoryComponent);
        component = fixture.componentInstance;
        activatedRoute = fixture.debugElement.injector.get(ActivatedRoute) as MockActivatedRoute;
        programmingExerciseParticipationService = fixture.debugElement.injector.get(ProgrammingExerciseParticipationService);
        programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);

        activatedRoute.setParameters({ participationId: 2 });
        jest.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithAllResults').mockReturnValue(of(mockParticipation));
        jest.spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForParticipation').mockReturnValue(of(mockCommits));

        const mockExerciseResponse: HttpResponse<ProgrammingExercise> = new HttpResponse({ body: mockExerciseWithTemplateAndSolution });
        jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation').mockReturnValue(of(mockExerciseResponse));

        fixture.detectChanges();
    }

    it('should create', () => {
        setupComponent();

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component).toBeTruthy();
    });

    it('should load student participation', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2 });

        // Trigger ngOnInit
        component.ngOnInit();

        // Expectations
        expect(component.participation).toEqual(mockParticipation);
        expect(component.participation.results).toHaveLength(2); // Updated to reflect the number of results
        expect(component.participation.results![0].participation).toEqual(mockParticipation);
        expect(component.participation.results![1].participation).toEqual(mockParticipation);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationSub?.closed).toBeTrue();
    });

    it('should load student commits', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2 });

        // Trigger ngOnInit
        component.ngOnInit();

        // Expectations
        expect(component.participation).toEqual(mockParticipation);
        expect(component.commits).toEqual([commit3, commit2, commit1]); // Updated to reflect the correct order

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
    });

    it('should set student commit results', () => {
        setupComponent();

        // Trigger ngOnInit
        component.ngOnInit();

        // Expectations
        expect(component.participation).toEqual(mockParticipation);
        expect(component.commits).toEqual([commit3, commit2, commit1]); // Updated to reflect the correct order

        expect(component.commits[0].result).toEqual(mockParticipation.results![1]);
        expect(component.commits[1].result).toEqual(mockParticipation.results![0]);
        expect(component.commits[2].result).toBeUndefined();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
    });

    it('should load template participation and handle commits', () => {
        setupComponent();
        activatedRoute.setParameters({ repositoryType: 'TEMPLATE' });
        jest.spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(of(mockTemplateCommits));

        // Trigger ngOnInit
        component.ngOnInit();

        // Expectations
        expect(component.participation).toEqual(mockExerciseWithTemplateAndSolution.templateParticipation);
        expect(component.participation.results).toHaveLength(2); // Updated to reflect the number of results
        expect(component.participation.results).toEqual(templateSubmissions.map((submission) => submission.results![0]));

        expect(component.commits).toEqual([mockTemplateCommit2, mockTemplateCommit1]); // Updated to reflect the correct order

        //results are set in the template participation
        expect(component.commits[0].result).toEqual(templateSubmissions[1].results![0]);
        expect(component.commits[1].result).toEqual(templateSubmissions[0].results![0]);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
    });

    it('should load solution participation', () => {
        setupComponent();
        activatedRoute.setParameters({ repositoryType: 'SOLUTION' });
        jest.spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(of(mockSolutionCommits));

        // Trigger ngOnInit
        component.ngOnInit();

        // Expectations
        expect(component.participation).toEqual(mockExerciseWithTemplateAndSolution.solutionParticipation);
        expect(component.participation.results).toHaveLength(2); // Updated to reflect the number of results
        expect(component.participation.results).toEqual(solutionSubmissions.map((submission) => submission.results![0]));

        expect(component.commits).toEqual([mockSolutionCommit2, mockSolutionCommit1]); // Updated to reflect the correct order

        // results are set in the solution participation
        expect(component.commits[0].result).toEqual(solutionSubmissions[1].results![0]);
        expect(component.commits[1].result).toEqual(solutionSubmissions[0].results![0]);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationSub?.closed).toBeTrue();
    });

    it('should load test participation', () => {
        setupComponent();
        activatedRoute.setParameters({ repositoryType: 'TESTS' });
        jest.spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(of(mockTestCommits));

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.isTestRepository).toBeTrue();

        // Expectations
        expect(component.participation).toEqual(mockExerciseWithTemplateAndSolution.templateParticipation);

        expect(component.commits).toEqual(mockTestCommits); // Updated to reflect the correct order
        expect(component.commits[0].result).toBeUndefined();
        expect(component.commits[1].result).toBeUndefined();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationSub?.closed).toBeTrue();
    });
});
