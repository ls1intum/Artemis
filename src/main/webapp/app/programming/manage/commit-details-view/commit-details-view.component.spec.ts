import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ActivatedRoute } from '@angular/router';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { Observable, of, throwError } from 'rxjs';
import { CommitInfo } from 'app/programming/shared/entities/programming-submission.model';
import { MockComponent, MockPipe } from 'ng-mocks';
import { CommitDetailsViewComponent } from 'app/programming/manage/commit-details-view/commit-details-view.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { DiffInformation, FileStatus, RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

// Mock the diff.utils module to avoid Monaco Editor issues in tests
jest.mock('app/programming/shared/utils/diff.utils', () => ({
    ...jest.requireActual('app/programming/shared/utils/diff.utils'),
    processRepositoryDiff: jest.fn().mockImplementation((leftFiles, rightFiles) => {
        // Handle the case where files are undefined (when repository fetch fails)
        if (!leftFiles || !rightFiles) {
            return Promise.resolve(undefined);
        }
        // Return the mockRepositoryDiffInformation to match test expectations
        return Promise.resolve({
            diffInformations: [
                {
                    title: 'src/main/java/modified-file.java',
                    modifiedPath: 'src/main/java/modified-file.java',
                    originalPath: 'src/main/java/modified-file.java',
                    modifiedFileContent: 'some\ncontent\nhere',
                    originalFileContent: 'some\nother\ncontent',
                    diffReady: false,
                    fileStatus: 'unchanged',
                    lineChange: { addedLineCount: 1, removedLineCount: 1 },
                },
                {
                    title: 'src/main/java/new-file.java',
                    modifiedPath: 'src/main/java/new-file.java',
                    originalPath: '',
                    modifiedFileContent: 'new content',
                    originalFileContent: undefined,
                    diffReady: false,
                    fileStatus: 'created',
                    lineChange: { addedLineCount: 1, removedLineCount: 0 },
                },
                {
                    title: 'src/main/java/new-name.java',
                    modifiedPath: 'src/main/java/new-name.java',
                    originalPath: '',
                    modifiedFileContent: 'Hello\nWorld',
                    originalFileContent: undefined,
                    diffReady: false,
                    fileStatus: 'created',
                    lineChange: { addedLineCount: 2, removedLineCount: 0 },
                },
                {
                    title: 'src/main/java/old-name.java',
                    modifiedPath: '',
                    originalPath: 'src/main/java/old-name.java',
                    modifiedFileContent: undefined,
                    originalFileContent: 'Hi\nWorld!',
                    diffReady: false,
                    fileStatus: 'deleted',
                    lineChange: { addedLineCount: 0, removedLineCount: 2 },
                },
            ],
            totalLineChange: { addedLineCount: 4, removedLineCount: 3 },
        });
    }),
}));

describe('CommitDetailsViewComponent', () => {
    let component: CommitDetailsViewComponent;
    let fixture: ComponentFixture<CommitDetailsViewComponent>;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let activatedRoute: MockActivatedRoute;

    // Define mock data for participation and commits
    const exercise = { id: 1, numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()], studentAssignedTeamIdComputed: true, secondCorrectionEnabled: true };

    const mockTemplateCommit1: CommitInfo = { hash: 'templateCommit1', author: 'author1', message: 'message1', timestamp: dayjs('2021-01-01') };
    const mockTemplateCommit2: CommitInfo = { hash: 'templateCommit2', author: 'author2', message: 'message2', timestamp: dayjs('2021-01-02') };
    const mockTemplateCommits: CommitInfo[] = [mockTemplateCommit2, mockTemplateCommit1];

    const mockParticipation: ProgrammingExerciseStudentParticipation = {
        id: 2,
        repositoryUri: 'student-repo-uri',
        exercise: exercise,
        submissions: [
            {
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

    // mock repository diff information, sorted by modifiedPath
    const mockRepositoryDiffInformation: RepositoryDiffInformation = {
        diffInformations: [
            {
                title: 'src/main/java/modified-file.java',
                modifiedPath: 'src/main/java/modified-file.java',
                originalPath: 'src/main/java/modified-file.java',
                modifiedFileContent: 'some\ncontent\nhere',
                originalFileContent: 'some\nother\ncontent',
                diffReady: false,
                fileStatus: FileStatus.UNCHANGED,
                lineChange: { addedLineCount: 1, removedLineCount: 1 },
            },
            {
                title: 'src/main/java/new-file.java',
                modifiedPath: 'src/main/java/new-file.java',
                originalPath: '',
                modifiedFileContent: 'new content',
                originalFileContent: undefined,
                diffReady: false,
                fileStatus: FileStatus.CREATED,
                lineChange: { addedLineCount: 1, removedLineCount: 0 },
            },
            {
                title: 'src/main/java/new-name.java',
                modifiedPath: 'src/main/java/new-name.java',
                originalPath: '',
                modifiedFileContent: 'Hello\nWorld',
                originalFileContent: undefined,
                diffReady: false,
                fileStatus: FileStatus.CREATED,
                lineChange: { addedLineCount: 2, removedLineCount: 0 },
            },
            {
                title: 'src/main/java/old-name.java',
                modifiedPath: '',
                originalPath: 'src/main/java/old-name.java',
                modifiedFileContent: undefined,
                originalFileContent: 'Hi\nWorld!',
                diffReady: false,
                fileStatus: FileStatus.DELETED,
                lineChange: { addedLineCount: 0, removedLineCount: 2 },
            },
        ],
        totalLineChange: { addedLineCount: 4, removedLineCount: 3 },
    };

    const mockLeftCommitFileContentByPath: Map<string, string> = new Map<string, string>(
        mockRepositoryDiffInformation.diffInformations
            .filter((diff: DiffInformation) => diff.originalPath && diff.originalFileContent !== undefined)
            .map((diff: DiffInformation) => [diff.originalPath!, diff.originalFileContent!] as [string, string]),
    );
    const mockRightCommitFileContentByPath: Map<string, string> = new Map<string, string>(
        mockRepositoryDiffInformation.diffInformations
            .filter((diff: DiffInformation) => diff.modifiedPath && diff.modifiedFileContent !== undefined)
            .map((diff: DiffInformation) => [diff.modifiedPath!, diff.modifiedFileContent!] as [string, string]),
    );

    beforeEach(async () => {
        // Mock ResizeObserver before TestBed configuration
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

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

    function setupComponent(throwErrorWhenRetrievingCommitHistory = false) {
        fixture = TestBed.createComponent(CommitDetailsViewComponent);
        component = fixture.componentInstance;
        activatedRoute = TestBed.inject(ActivatedRoute) as MockActivatedRoute;
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);

        jest.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithAllResults').mockReturnValue(of(mockParticipation));
        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView' as any)
            .mockReturnValueOnce(of(mockLeftCommitFileContentByPath))
            .mockReturnValueOnce(of(mockRightCommitFileContentByPath));

        const errorObservable = throwError(() => new Error('Error'));
        jest.spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForParticipation').mockReturnValue(
            throwErrorWhenRetrievingCommitHistory ? errorObservable : of(mockCommits),
        );
        jest.spyOn(programmingExerciseParticipationService, 'retrieveCommitHistoryForTemplateSolutionOrTests').mockReturnValue(
            throwErrorWhenRetrievingCommitHistory ? errorObservable : of(mockTemplateCommits),
        );

        fixture.changeDetectorRef.detectChanges();
    }

    it('should create', () => {
        setupComponent();
        expect(component).toBeTruthy();
    });

    it('should handle commits for student participation', () => {
        setupComponent();
        activatedRoute.setParameters({ repositoryId: 2, repositoryType: 'USER', commitHash: 'commit2', exerciseId: 1 });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.currentCommit).toEqual(commit2);
        expect(component.previousCommit).toEqual(commit1);
        expect(component.commits).toEqual([commit3, commit2, commit1]);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should handle commits for template participation', () => {
        setupComponent();
        activatedRoute.setParameters({ repositoryId: 2, commitHash: 'templateCommit2', exerciseId: 1, repositoryType: 'TEMPLATE' });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.currentCommit).toEqual(mockTemplateCommit2);
        expect(component.previousCommit).toEqual(mockTemplateCommit1);
        expect(component.commits).toEqual(mockTemplateCommits);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should handle new repository files for commit with template', async () => {
        fixture = TestBed.createComponent(CommitDetailsViewComponent);
        component = fixture.componentInstance;
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);

        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView' as any)
            .mockReturnValueOnce(of(mockLeftCommitFileContentByPath))
            .mockReturnValueOnce(of(mockRightCommitFileContentByPath));

        component.exerciseId = 1;
        component.repositoryId = 2;
        component.repositoryType = RepositoryType.USER;
        component.previousCommit = commit1;
        component.currentCommit = commit2;

        (component as any).fetchParticipationRepoFiles();

        await new Promise((resolve) => setTimeout(resolve, 0));

        expect(component.repositoryDiffInformation).toEqual(mockRepositoryDiffInformation);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationRepoFilesSubscription?.closed).toBeTrue();
    });

    it('should handle files for template commit', () => {
        setupComponent();

        activatedRoute.setParameters({ repositoryId: 2, commitHash: 'commit1', exerciseId: 1 });

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
        expect(component.participationRepoFilesSubscription?.closed).toBeTrue();
    });

    it('should handle new commits', () => {
        setupComponent();

        const programmingExerciseParticipationServiceSpy = jest.spyOn(
            programmingExerciseParticipationService,
            'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView' as any,
        );

        //different commit hash than usual
        activatedRoute.setParameters({ repositoryId: 2, commitHash: 'commit3', exerciseId: 1 });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(programmingExerciseParticipationServiceSpy).toHaveBeenNthCalledWith(3, 1, 2, 'commit2', 'USER');
        expect(programmingExerciseParticipationServiceSpy).toHaveBeenNthCalledWith(4, 1, 2, 'commit3', 'USER');

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationRepoFilesSubscription?.closed).toBeTrue();
    });

    it('should handle error when retrieving commit info', () => {
        setupComponent(true);
        activatedRoute.setParameters({ repositoryId: 2, commitHash: 'commit2', exerciseId: 1 });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.commits).toEqual([]);
        expect(component.errorWhileFetching).toBeTrue();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should fetch repository files', () => {
        setupComponent();

        // Set up fresh mocks for this specific test
        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView')
            .mockReturnValueOnce(of(mockLeftCommitFileContentByPath))
            .mockReturnValueOnce(of(mockRightCommitFileContentByPath));

        activatedRoute.setParameters({ repositoryId: 2, commitHash: 'commit2', exerciseId: 1 });

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.leftCommitFileContentByPath).toEqual(mockLeftCommitFileContentByPath);
        expect(component.rightCommitFileContentByPath).toEqual(mockRightCommitFileContentByPath);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationRepoFilesSubscription?.closed).toBeTrue();
    });

    it('should handle error when fetching left repository files', () => {
        setupComponent();
        activatedRoute.setParameters({ repositoryId: 2, commitHash: 'commit2', exerciseId: 1 });
        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView').mockReturnValue(
            new Observable((subscriber) => {
                subscriber.error('Error');
            }),
        );

        fixture.changeDetectorRef.detectChanges();

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.leftCommitFileContentByPath).toEqual(new Map<string, string>());
        expect(component.rightCommitFileContentByPath).toEqual(new Map<string, string>());
        expect(component.errorWhileFetching).toBeTrue();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationRepoFilesSubscription?.closed).toBeTrue();
    });

    it('should handle error when fetching right repository files', () => {
        setupComponent();
        activatedRoute.setParameters({ repositoryId: 2, commitHash: 'commit2', exerciseId: 1 });

        // Define a variable to track the number of calls to the method
        let callCount = 0;

        // Mock the service to return an error the second time it is called
        jest.spyOn(programmingExerciseParticipationService, 'getParticipationRepositoryFilesWithContentAtCommitForCommitDetailsView').mockImplementation(() => {
            if (callCount === 0) {
                callCount++;
                return of(mockLeftCommitFileContentByPath);
            } else {
                return new Observable((subscriber) => {
                    subscriber.error('Error');
                });
            }
        });

        fixture.changeDetectorRef.detectChanges();

        // Trigger ngOnInit
        component.ngOnInit();

        expect(component.rightCommitFileContentByPath).toEqual(new Map<string, string>());
        expect(component.errorWhileFetching).toBeTrue();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.paramSub?.closed).toBeTrue();
        expect(component.participationRepoFilesSubscription?.closed).toBeTrue();
    });
});
