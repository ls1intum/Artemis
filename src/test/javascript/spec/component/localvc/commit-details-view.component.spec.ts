import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ActivatedRoute } from '@angular/router';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Observable, of } from 'rxjs';
import { CommitInfo } from 'app/entities/programming-submission.model';
import { MockComponent, MockPipe } from 'ng-mocks';
import { CommitDetailsViewComponent } from 'app/localvc/commit-details-view/commit-details-view.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { GitDiffReportComponent } from 'app/exercises/programming/hestia/git-diff-report/git-diff-report.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { HttpResponse } from '@angular/common/http';
describe('CommitDetailsViewComponent', () => {
    let component: CommitDetailsViewComponent;
    let fixture: ComponentFixture<CommitDetailsViewComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let activatedRoute: MockActivatedRoute;
    let programmingExerciseService: ProgrammingExerciseService;

    // Define mock data for participation and commits
    const exercise = { id: 1, numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()], studentAssignedTeamIdComputed: true, secondCorrectionEnabled: true };

    const mockExerciseWithTemplateAndSolution: ProgrammingExercise = {
        id: 1,
        templateParticipation: { id: 1, repositoryUri: 'template-repo-uri' },
        solutionParticipation: { id: 1, repositoryUri: 'solution-repo-uri' },
        numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
        studentAssignedTeamIdComputed: true,
        secondCorrectionEnabled: true,
    };

    const mockTemplateCommit1: CommitInfo = { hash: 'templateCommit1', author: 'author1', message: 'message1', timestamp: dayjs('2021-01-01') };
    const mockTemplateCommit2: CommitInfo = { hash: 'templateCommit2', author: 'author2', message: 'message2', timestamp: dayjs('2021-01-02') };
    const mockTemplateCommits: CommitInfo[] = [mockTemplateCommit2, mockTemplateCommit1];

    const mockParticipation: ProgrammingExerciseStudentParticipation = {
        id: 2,
        repositoryUri: 'student-repo-uri',
        exercise: exercise,
        results: [
            {
                id: 1,
                successful: true,
                completionDate: dayjs('2021-01-02'),
            },
            {
                id: 2,
                successful: false,
                completionDate: dayjs('2021-01-03'),
            },
        ],
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
    const mockDiffReportForCommits: ProgrammingExerciseGitDiffReport = {
        id: 1,
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

        mockRepositoryFiles.set('file1', 'content1');
        mockRepositoryFiles.set('file2', 'content2');

        jest.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithAllResults').mockReturnValue(of(mockParticipation));

        const mockExerciseResponse: HttpResponse<ProgrammingExercise> = new HttpResponse({ body: mockExerciseWithTemplateAndSolution });
        jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation').mockReturnValue(of(mockExerciseResponse));

        jest.spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForParticipation').mockReturnValue(of(mockCommits));
        jest.spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(of(mockTemplateCommits));
        jest.spyOn(programmingExerciseService, 'getDiffReportForCommits').mockReturnValue(of(mockDiffReportForCommits));

        fixture.detectChanges();
    }

    it('should create', () => {
        setupComponent();
        expect(component).toBeTruthy();
    });

    it('should load student participation', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2, commitHash: 'commit2', exerciseId: 1 });

        // Trigger ngOnInit
        component.ngOnInit();

        // Expectations
        expect(component.participation).toEqual(mockParticipation);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should load template participation', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2, commitHash: 'templateCommit2', exerciseId: 1, repositoryType: 'TEMPLATE' });

        // Trigger ngOnInit
        component.ngOnInit();

        // Expectations
        expect(component.participation).toEqual(mockExerciseWithTemplateAndSolution.templateParticipation);
        expect(component.exercise).toEqual(mockExerciseWithTemplateAndSolution);
        expect(component.participationId).toEqual(mockExerciseWithTemplateAndSolution.templateParticipation?.id);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should handle commits for student participation', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2, commitHash: 'commit2', exerciseId: 1 });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.currentCommit).toEqual(commit2);
        expect(component.previousCommit).toEqual(commit1);
        expect(component.commits).toEqual([commit3, commit2, commit1]);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
    });

    it('should handle commits for template participation', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2, commitHash: 'templateCommit2', exerciseId: 1, repositoryType: 'TEMPLATE' });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.currentCommit).toEqual(mockTemplateCommit2);
        expect(component.previousCommit).toEqual(mockTemplateCommit1);
        expect(component.commits).toEqual(mockTemplateCommits);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
    });

    it('should handle new report for commit with template', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2, commitHash: 'commit2', exerciseId: 1 });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.report).toEqual(mockDiffReportForCommits);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
        expect(component.repoFilesSubscription?.closed).toBeTrue();
    });

    it('should handle new report for template commit', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2, commitHash: 'commit1', exerciseId: 1 });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.currentCommit).toEqual(commit1);
        expect(component.previousCommit).toEqual(commit1);
        expect(component.isTemplate).toBeTrue();
        expect(component.leftCommitFileContentByPath).toEqual(new Map<string, string>());

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
        expect(component.repoFilesSubscription?.closed).toBeTrue();
    });

    it('should handle new report for commits', () => {
        setupComponent();
        //different commit hash than usual
        activatedRoute.setParameters({ participationId: 2, commitHash: 'commit3', exerciseId: 1 });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.report).toEqual(mockDiffReportForCommits);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
        expect(component.repoFilesSubscription?.closed).toBeTrue();
    });

    it('should fetch repository files', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2, commitHash: 'commit2', exerciseId: 1 });
        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView').mockReturnValue(of(mockRepositoryFiles));

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

    it('should handle error when fetching left repository files', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2, commitHash: 'commit2', exerciseId: 1 });
        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView').mockReturnValue(
            new Observable((subscriber) => {
                subscriber.error('Error');
            }),
        );

        fixture.detectChanges();

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.leftCommitFileContentByPath).toEqual(new Map<string, string>());
        expect(component.rightCommitFileContentByPath).toEqual(new Map<string, string>());
        expect(component.errorWhileFetchingRepos).toBeTrue();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.commitsInfoSubscription?.closed).toBeTrue();
        expect(component.repoFilesSubscription?.closed).toBeTrue();
        expect(component.participationRepoFilesAtLeftCommitSubscription?.closed).toBeTrue();
        expect(component.participationRepoFilesAtRightCommitSubscription?.closed).toBeTrue();
    });

    it('should handle error when fetching right repository files', () => {
        setupComponent();
        activatedRoute.setParameters({ participationId: 2, commitHash: 'commit2', exerciseId: 1 });

        // Define a variable to track the number of calls to the method
        let callCount = 0;

        // Mock the service to return an error the second time it is called
        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView').mockImplementation(() => {
            if (callCount === 0) {
                callCount++;
                return of(mockRepositoryFiles);
            } else {
                return new Observable((subscriber) => {
                    subscriber.error('Error');
                });
            }
        });

        fixture.detectChanges();

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.leftCommitFileContentByPath).toEqual(mockRepositoryFiles);
        expect(component.rightCommitFileContentByPath).toEqual(new Map<string, string>());
        expect(component.errorWhileFetchingRepos).toBeTrue();

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
