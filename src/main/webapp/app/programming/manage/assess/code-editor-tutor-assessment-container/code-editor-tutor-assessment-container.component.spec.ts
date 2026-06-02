import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { DebugElement } from '@angular/core';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { BehaviorSubject, asapScheduler, firstValueFrom, of, scheduled, throwError } from 'rxjs';
import { outputToObservable } from '@angular/core/rxjs-interop';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { RepositoryFileService } from 'app/programming/shared/services/repository.service';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Feedback, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Complaint } from 'app/assessment/shared/entities/complaint.model';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { MockRepositoryFileService } from 'test/helpers/mocks/service/mock-repository-file.service';

import { CodeEditorTutorAssessmentContainerComponent } from 'app/programming/manage/assess/code-editor-tutor-assessment-container/code-editor-tutor-assessment-container.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { AssessmentLayoutComponent } from 'app/assessment/manage/assessment-layout/assessment-layout.component';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { Course } from 'app/course/shared/entities/course.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorFileBrowserComponent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';
import { AlertService } from 'app/foundation/service/alert.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockAthenaService } from 'test/helpers/mocks/service/mock-athena.service';
import { AthenaService } from 'app/assessment/shared/services/athena.service';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { EntityResponseType } from 'app/exercise/result/result.service';
import { CodeEditorMonacoComponent } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import dayjs from 'dayjs/esm';
import { MonacoEditorLineHighlight } from 'app/editor/monaco-editor/model/monaco-editor-line-highlight.model';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { CodeEditorHeaderComponent } from 'app/programming/manage/code-editor/header/code-editor-header.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ComplaintDTO } from 'app/assessment/shared/entities/complaint-dto.model';

/**
 * Typed view onto the component's private members and methods the spec needs to reach,
 * so they can be accessed without a blanket `(comp as any)` cast.
 */
type ContainerInternals = CodeEditorTutorAssessmentContainerComponent & {
    athenaService: AthenaService;
    dialogService: DialogService;
    submission?: ProgrammingSubmission;
    loadFeedbackSuggestions: () => Promise<void>;
    onSubmissionReceived: (submissionId: string, submission?: ProgrammingSubmission) => Promise<void>;
};
const internals = (c: CodeEditorTutorAssessmentContainerComponent): ContainerInternals => c as ContainerInternals;

/**
 * Drains the pending microtask queue (identity promise, asap-scheduled submission emission, and the chained
 * async handlers) without scheduling a macrotask. Staying on the microtask queue keeps the framework's zoneless
 * initial change detection — which runs on a macrotask — from re-invoking ngOnInit and inflating call counts.
 */
async function flushMicrotasks(): Promise<void> {
    for (let i = 0; i < 10; i++) {
        await Promise.resolve();
    }
}

function addFeedbackAndValidateScore(comp: CodeEditorTutorAssessmentContainerComponent, pointsAwarded: number, scoreExpected: number) {
    comp.unreferencedFeedback.push({
        type: FeedbackType.MANUAL_UNREFERENCED,
        detailText: 'unreferenced feedback',
        credits: pointsAwarded,
    });
    comp.validateFeedback();
    expect(comp.manualResult?.score).toEqual(scoreExpected);
}

describe('CodeEditorTutorAssessmentContainerComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: CodeEditorTutorAssessmentContainerComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentContainerComponent>;
    let debugElement: DebugElement;
    let programmingAssessmentManualResultService: ProgrammingAssessmentManualResultService;
    let complaintService: ComplaintService;
    let accountService: AccountService;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let programmingExerciseService: ProgrammingExerciseService;
    let repositoryFileService: CodeEditorRepositoryFileService;
    let router: Router;

    let updateAfterComplaintStub: ReturnType<typeof vi.spyOn>;
    let findBySubmissionIdStub: ReturnType<typeof vi.spyOn>;
    let getIdentityStub: ReturnType<typeof vi.spyOn>;
    let getProgrammingSubmissionForExerciseWithoutAssessmentStub: ReturnType<typeof vi.spyOn>;
    let lockAndGetProgrammingSubmissionParticipationStub: ReturnType<typeof vi.spyOn>;
    let findWithParticipationsStub: ReturnType<typeof vi.spyOn>;

    const user = <User>{ id: 99, groups: ['instructorGroup'] };
    const result: Result = {
        feedbacks: [new Feedback()],
        score: 80,
        successful: true,
        submission: new ProgrammingSubmission(),
        assessor: user,
        hasComplaint: true,
        assessmentType: AssessmentType.SEMI_AUTOMATIC,
        id: 2,
    };
    result.submission!.id = 1;

    const complaint = <Complaint>{ id: 1, complaintText: 'Why only 80%?', result };
    const exercise = {
        id: 1,
        templateParticipation: {
            id: 3,
            repositoryUri: 'test2',
            results: [{ id: 9, submission: { id: 1, buildFailed: false } }],
        },
        maxPoints: 100,
        gradingInstructions: 'Grading Instructions',
        course: <Course>{ instructorGroupName: 'instructorGroup' },
    } as unknown as ProgrammingExercise;

    const participation: ProgrammingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();
    participation.exercise = exercise;
    participation.id = 1;
    participation.student = { login: 'student1' } as User;
    participation.repositoryUri = 'http://student1@artemis.tum.de/git/TEST/test-repo-student1.git';
    result.submission!.participation = participation;

    const submission: ProgrammingSubmission = new ProgrammingSubmission();
    submission.results = [result];
    submission.participation = participation;
    submission.id = 1234;
    submission.latestResult = result;
    participation.submissions = [submission];

    const unassessedSubmission = new ProgrammingSubmission();
    unassessedSubmission.id = 12;

    const afterComplaintResult = new Result();
    afterComplaintResult.score = 100;

    const afterOverrideResult: Result = new Result();
    afterOverrideResult.feedbacks = [
        {
            type: FeedbackType.AUTOMATIC,
            testCase: { testName: 'testCase1' },
            detailText: 'testCase1 failed',
            credits: 0,
        },
    ];
    afterOverrideResult.assessor = user;

    const overrideEntityResponse: EntityResponseType = new HttpResponse({ body: afterOverrideResult });

    const route = (): ActivatedRoute =>
        ({
            params: of({ submissionId: 123 }),
            queryParamMap: of(convertToParamMap({ testRun: false })),
        }) as any as ActivatedRoute;
    const fileContent = 'This is the content of a file';
    const templateFileSessionReturn: { [fileName: string]: string } = { 'folder/file1': fileContent };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CodeEditorMonacoComponent],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: RepositoryFileService, useClass: MockRepositoryFileService },
                { provide: DialogService, useValue: { open: vi.fn() } },
                SessionStorageService,
                LocalStorageService,
                { provide: AthenaService, useClass: MockAthenaService },
                { provide: ActivatedRoute, useValue: route() },
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: DialogService, useClass: MockDialogService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(CodeEditorMonacoComponent, { set: { imports: [MonacoEditorComponent, MockComponent(CodeEditorHeaderComponent)] } })
            .compileComponents();
        // Ignore console errors
        console.error = () => {
            return false;
        };
        fixture = TestBed.createComponent(CodeEditorTutorAssessmentContainerComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        router = TestBed.inject(Router);
        programmingAssessmentManualResultService = TestBed.inject(ProgrammingAssessmentManualResultService);
        programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
        complaintService = TestBed.inject(ComplaintService);
        accountService = TestBed.inject(AccountService);
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        repositoryFileService = TestBed.inject(CodeEditorRepositoryFileService);
        updateAfterComplaintStub = vi.spyOn(programmingAssessmentManualResultService, 'updateAfterComplaint').mockReturnValue(of(afterComplaintResult));
        // Defer the submission emission onto the microtask queue (asapScheduler) so the accountService.identity()
        // promise — queued first inside ngOnInit — resolves before the submission is handled. This preserves the
        // original ordering (identity then submission, so checkPermissions sees the resolved userId) without the
        // real-timer delay that fakeAsync/tick previously relied on and that does not work under zoneless.
        lockAndGetProgrammingSubmissionParticipationStub = vi
            .spyOn(programmingSubmissionService, 'lockAndGetProgrammingSubmissionParticipation')
            .mockReturnValue(scheduled([submission], asapScheduler));
        findBySubmissionIdStub = vi.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({ body: complaint } as HttpResponse<ComplaintDTO>));
        getIdentityStub = vi.spyOn(accountService, 'identity').mockReturnValue(new Promise((promise) => promise(user)));
        getProgrammingSubmissionForExerciseWithoutAssessmentStub = vi
            .spyOn(programmingSubmissionService, 'getSubmissionWithoutAssessment')
            .mockReturnValue(of(unassessedSubmission));
        findWithParticipationsStub = vi.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation');
        findWithParticipationsStub.mockReturnValue(of({ body: exercise }));
        // Mock the ResizeObserver, which is not available in the test environment. Assign the mock class directly:
        // a vi.fn().mockImplementation returning a new instance is not usable as a constructor under vitest.
        global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
    });

    afterEach(() => {
        vi.restoreAllMocks();
        result.assessor = user;
        result.hasComplaint = true;
    });

    it('should highlight lines that were changed', async () => {
        // Stub
        const getFilesWithContentStub = vi.spyOn(repositoryFileService, 'getFilesWithContent');
        getFilesWithContentStub.mockReturnValue(of(templateFileSessionReturn));
        // Stub for code editor
        const getFileStub = vi.spyOn(repositoryFileService, 'getFile');
        const fileSubject = new BehaviorSubject({ fileContent: 'new file text' });
        getFileStub.mockReturnValue(fileSubject);

        // Data for file browser
        const treeItems = [
            new TreeViewItem({
                internalDisabled: false,
                internalChecked: false,
                internalCollapsed: false,
                text: 'folder/file1',
                value: 'file1',
            } as any),
        ];

        const repositoryFiles = {
            folder: FileType.FOLDER,
            'folder/file1': FileType.FILE,
        };

        // Initialize component and children
        const feedbackLoaded = firstValueFrom(outputToObservable(comp.onFeedbackLoaded));
        fixture.detectChanges();
        // wait until data is loaded from CodeEditorTutorAssessmentContainer
        await feedbackLoaded;
        // Let the remainder of the init observable chain settle (template/solution fetch + getFilesWithContent,
        // which finally sets loadingParticipation = false so the code editor and its file browser render).
        await flushMicrotasks();
        fixture.changeDetectorRef.detectChanges();

        // Setup tree for file browser
        const codeEditorFileBrowserComp = fixture.debugElement.query(By.directive(CodeEditorFileBrowserComponent)).componentInstance;
        codeEditorFileBrowserComp.filesTreeViewItem = treeItems;
        codeEditorFileBrowserComp.repositoryFiles = repositoryFiles;
        fixture.changeDetectorRef.detectChanges();
        codeEditorFileBrowserComp.selectedFileChange.emit('folder/file1');
        fixture.changeDetectorRef.detectChanges();
        codeEditorFileBrowserComp.isLoadingFiles = false;
        fixture.changeDetectorRef.detectChanges();
        const browserComponent = fixture.debugElement.query(By.directive(CodeEditorFileBrowserComponent)).componentInstance;
        expect(browserComponent).toBeDefined();
        expect(browserComponent.filesTreeViewItem).toHaveLength(1);

        const codeEditorMonacoComp: CodeEditorMonacoComponent = fixture.debugElement.query(By.directive(CodeEditorMonacoComponent)).componentInstance;
        codeEditorMonacoComp.loadingCount.set(0);
        const highlightedLines: MonacoEditorLineHighlight[] = await firstValueFrom(outputToObservable(codeEditorMonacoComp.onHighlightLines));
        expect(highlightedLines).toHaveLength(1);

        getFilesWithContentStub.mockRestore();
        getFileStub.mockRestore();
        fixture.destroy();
    });

    it('should use jhi-assessment-layout', () => {
        const assessmentLayout = fixture.debugElement.query(By.directive(AssessmentLayoutComponent));
        expect(assessmentLayout).toBeDefined();
    });

    it('should load the grading criteria on initialisation', async () => {
        comp.ngOnInit();
        await flushMicrotasks();

        expect(findWithParticipationsStub).toHaveBeenCalledWith(exercise.id, false, true);
    });

    it('should update assessor correctly if the manual assessment is overridden', async () => {
        const user2 = <User>{ id: 100, groups: ['instructorGroup'] };
        const discardPendingSubmissionsWithConfirmationStub = vi.spyOn(comp, 'discardPendingSubmissionsWithConfirmation').mockReturnValue(Promise.resolve(true));
        const updateAfterNewAssessment = vi.spyOn(programmingAssessmentManualResultService, 'saveAssessment').mockReturnValue(of(overrideEntityResponse));
        result.assessor = user2;
        result.hasComplaint = false;
        comp.ngOnInit();
        await flushMicrotasks();
        expect(comp.isAssessor).toBe(false);
        addFeedbackAndValidateScore(comp, 0, 0);
        await comp.submit();
        fixture.changeDetectorRef.detectChanges();
        const alertElementSubmit = debugElement.queryAll(By.css('jhi-alert'));
        expect(alertElementSubmit).not.toBeNull();

        expect(getIdentityStub).toHaveBeenCalled();
        expect(discardPendingSubmissionsWithConfirmationStub).toHaveBeenCalled();
        expect(updateAfterNewAssessment).toHaveBeenCalledOnce();
        expect(comp.isAssessor).toBe(true);
    });

    it('should be able to override directly after submitting', () => {
        vi.spyOn(programmingAssessmentManualResultService, 'saveAssessment');

        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.isAtLeastInstructor = true;
        exercise.dueDate = dayjs();
        comp.exercise = exercise;
        comp.isAssessor = true;
        comp.participation = participation;
        comp.manualResult = result;
        comp.submit();
        expect(comp.canOverride).toBe(true);
    });

    it('should show unreferenced feedback suggestions', () => {
        comp.feedbackSuggestions = [{ reference: 'file:src/Test.java_line:1' }, { reference: 'file:src/Test.java_line:2' }, { reference: undefined }];
        expect(comp.unreferencedFeedbackSuggestions).toHaveLength(1);
    });

    it('should not show feedback suggestions where there are already existing manual feedbacks', async () => {
        comp.unreferencedFeedback = [{ text: 'unreferenced test', detailText: 'some detail', reference: undefined }];
        comp.referencedFeedback = [
            {
                text: 'referenced test',
                detailText: 'some detail',
                reference: 'file:src/Test.java_line:1',
            },
        ];
        const feedbackSuggestionsStub = vi.spyOn(internals(comp).athenaService, 'getProgrammingFeedbackSuggestions');
        feedbackSuggestionsStub.mockReturnValue(
            of([
                { text: 'FeedbackSuggestion:unreferenced test', detailText: 'some detail' },
                {
                    text: 'FeedbackSuggestion:referenced test',
                    detailText: 'some detail',
                    reference: 'file:src/Test.java_line:1',
                },
                {
                    text: 'FeedbackSuggestion:suggestion to pass',
                    detailText: 'some detail',
                    reference: 'file:src/Test.java_line:2',
                },
            ] as Feedback[]),
        );
        internals(comp).submission = { id: undefined } as ProgrammingSubmission; // Needed for loadFeedbackSuggestions
        await internals(comp).loadFeedbackSuggestions();
        expect(comp.feedbackSuggestions).toStrictEqual([
            {
                text: 'FeedbackSuggestion:suggestion to pass',
                detailText: 'some detail',
                reference: 'file:src/Test.java_line:2',
            },
        ]);
    });

    it('should show complaint for result with complaint and check assessor', async () => {
        comp.ngOnInit();
        // Flush the identity() microtask and the asap-scheduled submission chain. Assertions are captured before any
        // macrotask, so the framework's zoneless initial change detection cannot re-run ngOnInit and inflate counts.
        await flushMicrotasks();

        expect(getIdentityStub).toHaveBeenCalledOnce();
        expect(lockAndGetProgrammingSubmissionParticipationStub).toHaveBeenCalledOnce();
        expect(findBySubmissionIdStub).toHaveBeenCalledOnce();
        expect(comp.isAssessor).toBe(true);
        expect(comp.complaint).not.toBeNull();
        fixture.changeDetectorRef.detectChanges();

        const complaintsForm = debugElement.query(By.css('jhi-complaints-for-tutor-form'));
        expect(complaintsForm).not.toBeNull();
        expect(comp.complaint).not.toBeNull();
    });

    it('should lock a new submission', () => {
        const activatedRoute: ActivatedRoute = TestBed.inject(ActivatedRoute);
        activatedRoute.params = of({ submissionId: 'new' });
        TestBed.inject(ActivatedRoute);

        getProgrammingSubmissionForExerciseWithoutAssessmentStub.mockReturnValue(of(submission));

        comp.ngOnInit();
        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).toHaveBeenCalledOnce();
    });

    it('should not show complaint when participation contains no complaint', async () => {
        findBySubmissionIdStub.mockReturnValue(of({ body: undefined }));
        comp.ngOnInit();
        await flushMicrotasks();

        expect(getIdentityStub).toHaveBeenCalledOnce();
        expect(lockAndGetProgrammingSubmissionParticipationStub).toHaveBeenCalledOnce();
        expect(findBySubmissionIdStub).toHaveBeenCalledOnce();
        expect(comp.complaint).toBeUndefined();
        fixture.changeDetectorRef.detectChanges();

        const complaintsForm = debugElement.query(By.css('jhi-complaints-for-tutor-form'));
        expect(complaintsForm).toBeNull();
    });

    it('should calculate score correctly for IncludedCompletelyWithBonusPointsExercise', async () => {
        comp.ngOnInit();
        await flushMicrotasks();

        comp.exercise.maxPoints = 10;
        comp.exercise.bonusPoints = 10;
        comp.automaticFeedback = [];
        comp.referencedFeedback = [];
        comp.unreferencedFeedback = [];
        addFeedbackAndValidateScore(comp, 0, 0);
        addFeedbackAndValidateScore(comp, -1, 0);
        addFeedbackAndValidateScore(comp, 1, 0);
        addFeedbackAndValidateScore(comp, 5, 50);
        addFeedbackAndValidateScore(comp, 5, 100);
        addFeedbackAndValidateScore(comp, 5, 150);
        addFeedbackAndValidateScore(comp, 5, 200);
        addFeedbackAndValidateScore(comp, 5, 200);
    });

    it('should calculate score correctly for IncludedCompletelyWithoutBonusPointsExercise', async () => {
        comp.ngOnInit();
        await flushMicrotasks();

        comp.exercise.maxPoints = 10;
        comp.exercise.bonusPoints = 0;
        comp.automaticFeedback = [];
        comp.referencedFeedback = [];
        comp.unreferencedFeedback = [];
        addFeedbackAndValidateScore(comp, 0, 0);
        addFeedbackAndValidateScore(comp, -1, 0);
        addFeedbackAndValidateScore(comp, 1, 0);
        addFeedbackAndValidateScore(comp, 5, 50);
        addFeedbackAndValidateScore(comp, 5, 100);
        addFeedbackAndValidateScore(comp, 5, 100);
    });

    it('should calculate score correctly for IncludedAsBonusExercise', async () => {
        comp.ngOnInit();
        await flushMicrotasks();

        comp.exercise.maxPoints = 10;
        comp.exercise.bonusPoints = 0;
        comp.automaticFeedback = [];
        comp.referencedFeedback = [];
        comp.unreferencedFeedback = [];
        addFeedbackAndValidateScore(comp, 0, 0);
        addFeedbackAndValidateScore(comp, -1, 0);
        addFeedbackAndValidateScore(comp, 1, 0);
        addFeedbackAndValidateScore(comp, 5, 50);
        addFeedbackAndValidateScore(comp, 5, 100);
        addFeedbackAndValidateScore(comp, 5, 100);
    });

    it('should calculate score correctly for NotIncludedExercise', async () => {
        comp.ngOnInit();
        await flushMicrotasks();

        comp.exercise.maxPoints = 10;
        comp.exercise.bonusPoints = 0;
        comp.automaticFeedback = [];
        comp.referencedFeedback = [];
        comp.unreferencedFeedback = [];
        addFeedbackAndValidateScore(comp, 0, 0);
        addFeedbackAndValidateScore(comp, -1, 0);
        addFeedbackAndValidateScore(comp, 1, 0);
        addFeedbackAndValidateScore(comp, 5, 50);
        addFeedbackAndValidateScore(comp, 5, 100);
        addFeedbackAndValidateScore(comp, 5, 100);
    });

    it('should calculate score for result of submission', async () => {
        // When score is undefined
        result.score = undefined;
        comp.ngOnInit();
        await flushMicrotasks();

        // Should calculate the score
        expect(comp.submission?.results?.[0].score).toBeDefined();
    });

    it('should save and submit manual result', async () => {
        comp.ngOnInit();
        await flushMicrotasks();
        comp.automaticFeedback = [
            {
                type: FeedbackType.AUTOMATIC,
                testCase: { testName: 'testCase1' },
                detailText: 'testCase1 failed',
                credits: 0,
            },
        ];
        comp.referencedFeedback = [
            {
                type: FeedbackType.MANUAL,
                text: 'manual feedback',
                detailText: 'manual feedback for a file:1',
                credits: 2,
                reference: 'file:1_line:1',
            },
        ];
        comp.unreferencedFeedback = [
            {
                type: FeedbackType.MANUAL_UNREFERENCED,
                detailText: 'unreferenced feedback',
                credits: 1,
            },
        ];
        comp.validateFeedback();
        comp.save();
        const alertElement = debugElement.queryAll(By.css('jhi-alert'));

        expect(comp.manualResult?.feedbacks).toHaveLength(3);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.AUTOMATIC)).toBe(true);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL)).toBe(true);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)).toBe(true);
        expect(alertElement).not.toBeNull();

        // Reset feedbacks
        comp.manualResult!.feedbacks! = [];
        comp.validateFeedback();
        await comp.submit();
        const alertElementSubmit = debugElement.queryAll(By.css('jhi-alert'));

        expect(comp.manualResult?.feedbacks).toHaveLength(3);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.AUTOMATIC)).toBe(true);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL)).toBe(true);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)).toBe(true);
        expect(alertElementSubmit).not.toBeNull();
    });

    it('should cancel the assessment and navigate back', async () => {
        comp.ngOnInit();
        await flushMicrotasks();
        const navigateBackStub = vi.spyOn(comp, 'navigateBack');
        const cancelBackStub = vi.spyOn(programmingAssessmentManualResultService, 'cancelAssessment').mockReturnValue(of(undefined));
        global.confirm = () => true;
        const confirmSpy = vi.spyOn(window, 'confirm');
        comp.cancel();

        expect(confirmSpy).toHaveBeenCalledOnce();
        expect(comp.cancelBusy).toBe(false);
        expect(navigateBackStub).toHaveBeenCalledOnce();
        expect(cancelBackStub).toHaveBeenCalledOnce();
    });

    it('should go to next submission', async () => {
        const routerStub = vi.spyOn(router, 'navigate');

        comp.ngOnInit();
        const courseId = 123;
        comp.courseId = courseId;
        comp.exerciseId = exercise.id!;
        await flushMicrotasks();
        comp.nextSubmission();

        const url = [
            '/course-management',
            courseId!.toString(),
            'programming-exercises',
            exercise.id!.toString(),
            'submissions',
            unassessedSubmission.id!.toString(),
            'assessment',
        ];
        const queryParams = { queryParams: { 'correction-round': 0 } };
        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).toHaveBeenCalledOnce();
        expect(routerStub).toHaveBeenCalledWith(url, queryParams);
    });

    it('should show a message if no more unassessed submissions are present', () => {
        comp.exercise = exercise;
        comp.ngOnInit();

        getProgrammingSubmissionForExerciseWithoutAssessmentStub.mockReturnValue(of(undefined));
        comp.nextSubmission();

        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).toHaveBeenCalledOnce();
        expect(comp.submission).toBeUndefined();
    });

    it.each([undefined, 'genericErrorKey', 'complaintLock'])('should update assessment after complaint, errorKeyFromServer=%s', async (errorKeyFromServer: string | undefined) => {
        comp.ngOnInit();
        await flushMicrotasks();

        let onSuccessCalled = false;
        let onErrorCalled = false;
        const assessmentAfterComplaint: AssessmentAfterComplaint = {
            complaintResponse: new ComplaintResponse(),
            onSuccess: () => (onSuccessCalled = true),
            onError: () => (onErrorCalled = true),
        };

        const errorMessage = 'errMsg';
        const errorParams = ['errParam1', 'errParam2'];
        if (errorKeyFromServer) {
            updateAfterComplaintStub.mockReturnValue(
                throwError(
                    () =>
                        new HttpErrorResponse({
                            status: 400,
                            error: { message: errorMessage, errorKey: errorKeyFromServer, params: errorParams },
                        }),
                ),
            );
        }

        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');
        const validateSpy = vi.spyOn(comp, 'validateFeedback').mockImplementation(() => (comp.assessmentsAreValid = true));

        comp.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

        expect(validateSpy).toHaveBeenCalledOnce();
        expect(updateAfterComplaintStub).toHaveBeenCalledOnce();
        expect(comp.manualResult!.score).toBe(errorKeyFromServer ? 0 : 100);
        expect(onSuccessCalled).toBe(!errorKeyFromServer);
        expect(onErrorCalled).toBe(!!errorKeyFromServer);
        if (!errorKeyFromServer) {
            expect(errorSpy).not.toHaveBeenCalled();
        } else if (errorKeyFromServer === 'complaintLock') {
            expect(errorSpy).toHaveBeenCalledOnce();
            expect(errorSpy).toHaveBeenCalledWith(errorMessage, errorParams);
        } else {
            // Handle all other errors
            expect(errorSpy).toHaveBeenCalledOnce();
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.assessment.messages.updateAfterComplaintFailed');
        }
    });

    it('should send the reassembled feedbacks (not a stale snapshot) when resolving a complaint', async () => {
        comp.ngOnInit();
        await flushMicrotasks();

        // The editor state holds the up-to-date feedbacks the tutor just edited...
        comp.referencedFeedback = [{ detailText: 'REF', credits: 1, reference: 'file:1', type: FeedbackType.MANUAL } as Feedback];
        comp.unreferencedFeedback = [{ detailText: 'UNREF', credits: 1, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback];
        comp.automaticFeedback = [{ detailText: 'AUTO', credits: 0, type: FeedbackType.AUTOMATIC } as Feedback];
        // ...while the manual result still carries a stale feedback list that must NOT be the one sent to the server.
        comp.manualResult!.feedbacks = [{ detailText: 'STALE', credits: 99, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback];

        vi.spyOn(comp, 'validateFeedback').mockImplementation(() => (comp.assessmentsAreValid = true));
        const assessmentAfterComplaint: AssessmentAfterComplaint = {
            complaintResponse: new ComplaintResponse(),
            onSuccess: () => {},
            onError: () => {},
        };

        comp.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

        expect(updateAfterComplaintStub).toHaveBeenCalledOnce();
        const sentFeedbacks: Feedback[] = updateAfterComplaintStub.mock.calls[0][0] as Feedback[];
        expect(sentFeedbacks.map((feedback) => feedback.detailText)).toEqual(['REF', 'UNREF', 'AUTO']);
        expect(sentFeedbacks.some((feedback) => feedback.detailText === 'STALE')).toBe(false);
    });

    it('should validate assessments after submission is received during component init', async () => {
        // make assessment valid
        submission.results![0].feedbacks = [
            {
                detailText: 'text',
                credits: 1,
                type: FeedbackType.MANUAL_UNREFERENCED,
            },
        ];

        await internals(comp).onSubmissionReceived('123', submission);
        expect(comp.assessmentsAreValid).toBe(true);
    });

    it('should not invalidate assessment after saving', async () => {
        vi.spyOn(programmingAssessmentManualResultService, 'saveAssessment');

        submission.results![0].feedbacks = [
            {
                detailText: 'text',
                credits: 1,
                type: FeedbackType.MANUAL_UNREFERENCED,
            },
        ];
        await internals(comp).onSubmissionReceived('123', submission);
        comp.save();
        expect(comp.assessmentsAreValid).toBe(true);
    });

    it('should display error when complaint resolved but assessment invalid', () => {
        let onSuccessCalled = false;
        let onErrorCalled = false;
        const assessmentAfterComplaint: AssessmentAfterComplaint = {
            complaintResponse: new ComplaintResponse(),
            onSuccess: () => (onSuccessCalled = true),
            onError: () => (onErrorCalled = true),
        };
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');

        const validateSpy = vi.spyOn(comp, 'validateFeedback').mockImplementation(() => (comp.assessmentsAreValid = false));

        comp.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);
        expect(validateSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.programmingAssessment.invalidAssessments');
        expect(onSuccessCalled).toBe(false);
        expect(onErrorCalled).toBe(true);
    });

    it.each([
        [
            0,
            {
                complaintResponse: { complaint: { accepted: false } },
                onSuccess: () => {},
                onError: () => {},
            },
            [],
            false,
        ],
        [
            0,
            {
                complaintResponse: { complaint: { accepted: false } },
                onSuccess: () => {},
                onError: () => {},
            },
            [{ credits: 1 }],
            false,
        ],
        [
            1,
            {
                complaintResponse: { complaint: { accepted: false } },
                onSuccess: () => {},
                onError: () => {},
            },
            [],
            false,
        ],
        [
            1,
            {
                complaintResponse: { complaint: { accepted: false } },
                onSuccess: () => {},
                onError: () => {},
            },
            [{ credits: 1 }],
            false,
        ],
        [
            0,
            {
                complaintResponse: { complaint: { accepted: true } },
                onSuccess: () => {},
                onError: () => {},
            },
            [],
            true,
        ],
        [
            0,
            {
                complaintResponse: { complaint: { accepted: true } },
                onSuccess: () => {},
                onError: () => {},
            },
            [{ credits: 1 }],
            false,
        ],
        [
            1,
            {
                complaintResponse: { complaint: { accepted: true } },
                onSuccess: () => {},
                onError: () => {},
            },
            [],
            true,
        ],
        [
            1,
            {
                complaintResponse: { complaint: { accepted: true } },
                onSuccess: () => {},
                onError: () => {},
            },
            [{ credits: 1 }],
            true,
        ],
    ])(
        'should get confirmation if complaint is accepted without higher score',
        (totalScoreBeforeAssessment: number, assessmentAfterComplaint: AssessmentAfterComplaint, newFeedback: Feedback[], needsConfirmation: boolean) => {
            comp.exercise = { maxPoints: 2 } as Exercise;
            comp.totalScoreBeforeAssessment = totalScoreBeforeAssessment;
            comp.referencedFeedback = [];
            comp.automaticFeedback = [];
            comp.unreferencedFeedback = newFeedback;
            vi.spyOn(window, 'confirm').mockReturnValue(false);

            comp.checkFeedbackChangeForAcceptedComplaint(assessmentAfterComplaint);

            if (needsConfirmation) {
                expect(window.confirm).toHaveBeenCalledOnce();
            } else {
                expect(window.confirm).not.toHaveBeenCalled();
            }
        },
    );

    it('should update and validate referenced feedback', () => {
        const feedbacks = [
            { reference: 'file:src/Test.java_line:1', type: FeedbackType.MANUAL },
            { reference: 'file:src/Test.java_line:2', type: FeedbackType.MANUAL },
            { reference: undefined, type: FeedbackType.MANUAL },
        ];
        const validateFeedbackStub = vi.spyOn(comp, 'validateFeedback');
        validateFeedbackStub.mockReturnValue(undefined);
        comp.onUpdateFeedback(feedbacks);
        expect(comp.referencedFeedback).toEqual([
            { reference: 'file:src/Test.java_line:1', type: FeedbackType.MANUAL },
            { reference: 'file:src/Test.java_line:2', type: FeedbackType.MANUAL },
        ]);
        expect(validateFeedbackStub).toHaveBeenCalled();
    });

    it('should correctly remove feedback suggestions', () => {
        const feedbackSuggestion1 = { id: 1, credits: 1 };
        const feedbackSuggestion2 = { id: 2, credits: 2 };
        const feedbackSuggestion3 = { id: 3, credits: 3 };
        comp.feedbackSuggestions = [feedbackSuggestion1, feedbackSuggestion2, feedbackSuggestion3];
        comp.removeSuggestion(feedbackSuggestion2);
        expect(comp.feedbackSuggestions).toEqual([feedbackSuggestion1, feedbackSuggestion3]);
    });

    it('should show a confirmation dialog if there are pending feedback suggestions', async () => {
        const modalOpenStub = vi.spyOn(internals(comp).dialogService, 'open').mockReturnValue({ onClose: of(true) } as DynamicDialogRef); // Confirm dismissal
        comp.feedbackSuggestions = [{ id: 1, credits: 1 }];
        await comp.discardPendingSubmissionsWithConfirmation();
        expect(modalOpenStub).toHaveBeenCalled();
        // Dismissal should clear all feedback suggestions
        expect(comp.feedbackSuggestions).toHaveLength(0);
    });

    it('should keep feedback suggestions if the confirmation dialog is cancelled', async () => {
        const modalOpenStub = vi.spyOn(internals(comp).dialogService, 'open').mockReturnValue({ onClose: of(false) } as DynamicDialogRef); // Cancel suggestion dismissal
        comp.feedbackSuggestions = [{ id: 1, credits: 1 }];
        await comp.discardPendingSubmissionsWithConfirmation();
        expect(modalOpenStub).toHaveBeenCalled();
        // Cancelling should keep everything intact
        expect(comp.feedbackSuggestions).not.toHaveLength(0);
    });

    it('should not show a confirmation dialog if there are no feedback suggestions left', async () => {
        const modalOpenStub = vi.spyOn(internals(comp).dialogService, 'open');
        comp.feedbackSuggestions = [];
        await comp.discardPendingSubmissionsWithConfirmation();
        expect(modalOpenStub).not.toHaveBeenCalled();
    });
});
