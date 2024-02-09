import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ActivatedRoute } from '@angular/router';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { of } from 'rxjs';
import { CommitInfo, ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { MockComponent, MockPipe } from 'ng-mocks';
import { CommitDetailsViewComponent } from 'app/localvc/commit-details-view/commit-details-view.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { GitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.component';
describe('CommitDetailsViewComponent', () => {
    let component: CommitDetailsViewComponent;
    let fixture: ComponentFixture<CommitDetailsViewComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let activatedRoute: MockActivatedRoute;
    let programmingExerciseService: ProgrammingExerciseService;

    // Define mock data for participation and commits
    const submission2: ProgrammingSubmission = { id: 2, commitHash: 'commit2' } as ProgrammingSubmission;
    const submission3: ProgrammingSubmission = { id: 3, commitHash: 'commit3' } as ProgrammingSubmission;
    const exercise = { id: 1, numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()], studentAssignedTeamIdComputed: true, secondCorrectionEnabled: true };

    const mockParticipation: ProgrammingExerciseStudentParticipation = {
        id: 2,
        repositoryUri: 'student-repo-uri',
        exercise: exercise,
        results: [
            {
                id: 1,
                successful: true,
                completionDate: dayjs('2021-01-02'),
                submission: submission2,
            },
            {
                id: 2,
                successful: false,
                completionDate: dayjs('2021-01-03'),
                submission: submission3,
            },
        ],
        submissions: [submission2, submission3],
    };

    // template commit
    const commit1: CommitInfo = {
        hash: 'commit1',
        author: 'author1',
        message: 'message1',
        timestamp: dayjs('2021-01-01'),
    };

    const commit2: CommitInfo = {
        hash: 'commit2',
        author: 'author2',
        message: 'message2',
        timestamp: dayjs('2021-01-02'),
    };
    const commit3: CommitInfo = {
        hash: 'commit3',
        author: 'author3',
        message: 'message3',
        timestamp: dayjs('2021-01-03'),
    };
    const mockCommits: CommitInfo[] = [commit2, commit3, commit1];
    const mockDiffReportWithTemplate: ProgrammingExerciseGitDiffReport = {
        id: 1,
        programmingExercise: exercise,
    };
    const mockDiffReportForSubmissions: ProgrammingExerciseGitDiffReport = {
        id: 2,
        programmingExercise: exercise,
    };
    const mockRepositoryFiles: Map<string, string> = new Map<string, string>();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [CommitDetailsViewComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockComponent(GitDiffReportComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                {
                    provide: ProgrammingExerciseParticipationService,
                    useClass: MockProgrammingExerciseParticipationService,
                },
                {
                    provide: ProgrammingExerciseService,
                    useClass: MockProgrammingExerciseService,
                },
            ],
        }).compileComponents();
    });

    function setupComponent() {
        fixture = TestBed.createComponent(CommitDetailsViewComponent);
        component = fixture.componentInstance;
        activatedRoute = fixture.debugElement.injector.get(ActivatedRoute) as MockActivatedRoute;
        programmingExerciseParticipationService = fixture.debugElement.injector.get(ProgrammingExerciseParticipationService);
        programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);

        activatedRoute.setParameters({ participationId: 2, commitHash: 'commit2', exerciseId: 1 });
        mockRepositoryFiles.set('file1', 'content1');
        mockRepositoryFiles.set('file2', 'content2');

        jest.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithAllResults').mockReturnValue(of(mockParticipation));
        jest.spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForParticipation').mockReturnValue(of(mockCommits));
        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView').mockReturnValue(of(mockRepositoryFiles));
        jest.spyOn(programmingExerciseService, 'getGitDiffReportForCommitDetailsViewForSubmissions').mockReturnValue(of(mockDiffReportForSubmissions));
        jest.spyOn(programmingExerciseService, 'getGitDiffReportForCommitDetailsViewForSubmissionWithTemplate').mockReturnValue(of(mockDiffReportWithTemplate));

        fixture.detectChanges();
    }

    it('should create', () => {
        setupComponent();
        expect(component).toBeTruthy();
    });

    it('should load student participation', () => {
        setupComponent();

        // Trigger ngOnInit
        component.ngOnInit();

        // Expectations
        expect(component.studentParticipation).toEqual(mockParticipation);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should handle submissions', () => {
        setupComponent();

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.currentSubmission).toEqual(submission2);
        expect(component.previousSubmission).toBeUndefined();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should retrieve and handle commits', () => {
        setupComponent();

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.commits).toEqual([commit3, commit2, commit1]);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
    });

    it('should handle new report for submission with template', () => {
        setupComponent();

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.report).toEqual(mockDiffReportWithTemplate);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
        expect(component.repoFilesSubscription?.closed).toBeTrue();
    });

    it('should handle new report for submissions', () => {
        setupComponent();

        activatedRoute.setParameters({ participationId: 2, commitHash: 'commit3', exerciseId: 1 });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.report).toEqual(mockDiffReportForSubmissions);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
        expect(component.repoFilesSubscription?.closed).toBeTrue();
    });

    it('should fetch repository files', () => {
        setupComponent();

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.leftCommitFileContentByPath).toEqual(mockRepositoryFiles);
        expect(component.rightCommitFileContentByPath).toEqual(mockRepositoryFiles);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
        expect(component.repoFilesSubscription?.closed).toBeTrue();
        expect(component.participationRepoFilesAtLeftCommitSubscription?.closed).toBeTrue();
        expect(component.participationRepoFilesAtRightCommitSubscription?.closed).toBeTrue();
    });
});
