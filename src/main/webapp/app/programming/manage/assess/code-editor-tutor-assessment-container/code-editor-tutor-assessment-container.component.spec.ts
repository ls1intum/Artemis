import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DialogService } from 'primeng/dynamicdialog';
import { DebugElement } from '@angular/core';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { BehaviorSubject, Observable, Subject, asapScheduler, firstValueFrom, of, scheduled, throwError } from 'rxjs';
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
import { ActivatedRoute, ParamMap, Router, convertToParamMap, provideRouter } from '@angular/router';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { CodeEditorFileBrowserComponent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentAfterComplaint } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { TreeViewItem } from 'app/programming/shared/code-editor/treeview/models/tree-view-item';
import { AlertService } from 'app/foundation/service/alert.service';
import { ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING } from 'app/assessment/shared/util/assessment-availability.util';
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
import { FeedbackSuggestionsBannerComponent } from 'app/assessment/manage/feedback-suggestions-banner/feedback-suggestions-banner.component';

/**
 * Typed view onto the component's private members and methods the spec needs to reach,
 * so they can be accessed without a blanket `(comp as any)` cast.
 */
type ContainerInternalsOverrides = {
    athenaService: AthenaService;
    loadFeedbackSuggestions: () => Promise<void>;
    onSubmissionReceived: (submissionId: string, submission?: ProgrammingSubmission) => Promise<void>;
};
type ContainerInternals = Omit<CodeEditorTutorAssessmentContainerComponent, keyof ContainerInternalsOverrides> & ContainerInternalsOverrides;
const internals = (c: CodeEditorTutorAssessmentContainerComponent): ContainerInternals => c as unknown as ContainerInternals;

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
    comp.unreferencedFeedback.update((feedbacks) => [
        ...feedbacks,
        {
            type: FeedbackType.MANUAL_UNREFERENCED,
            detailText: 'unreferenced feedback',
            credits: pointsAwarded,
        },
    ]);
    comp.validateFeedback();
    expect(comp.manualResult()?.score).toEqual(scoreExpected);
}

describe('CodeEditorTutorAssessmentContainerComponent', () => {
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

    const user = <User>{ id: 99 };
    // Rebuilt fresh in beforeEach (not module-scope consts): `manualResult` now shares object identity with
    // participation().submissions[0].results[0] (see the signal's declaration for why), so production code
    // mutates this object in place. Module-scope consts would leak those mutations (feedbacks, rated, score,
    // circular-reference stripping, ...) across unrelated tests.
    let result: Result;
    let complaint: Complaint;
    let exercise: ProgrammingExercise;
    let participation: ProgrammingExerciseStudentParticipation;
    let submission: ProgrammingSubmission;
    let unassessedSubmission: ProgrammingSubmission;
    let afterComplaintResult: Result;
    let afterOverrideResult: Result;
    let overrideEntityResponse: EntityResponseType;

    const route = (): ActivatedRoute =>
        ({
            params: of({ submissionId: 123 }),
            queryParamMap: of(convertToParamMap({ testRun: false })),
        }) as any as ActivatedRoute;
    const fileContent = 'This is the content of a file';
    const templateFileSessionReturn: { [fileName: string]: string } = { 'folder/file1': fileContent };

    beforeEach(async () => {
        result = {
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

        complaint = <Complaint>{ id: 1, complaintText: 'Why only 80%?', result };
        exercise = {
            id: 1,
            templateParticipation: {
                id: 3,
                repositoryUri: 'test2',
                results: [{ id: 9, submission: { id: 1, buildFailed: false } }],
            },
            maxPoints: 100,
            gradingInstructions: 'Grading Instructions',
            course: <Course>{},
        } as unknown as ProgrammingExercise;

        participation = new ProgrammingExerciseStudentParticipation();
        participation.exercise = exercise;
        participation.id = 1;
        participation.student = { login: 'student1' } as User;
        participation.repositoryUri = 'http://student1@artemis.tum.de/git/TEST/test-repo-student1.git';
        result.submission!.participation = participation;

        submission = new ProgrammingSubmission();
        submission.results = [result];
        submission.participation = participation;
        submission.id = 1234;
        submission.latestResult = result;
        participation.submissions = [submission];

        unassessedSubmission = new ProgrammingSubmission();
        unassessedSubmission.id = 12;

        afterComplaintResult = new Result();
        afterComplaintResult.score = 100;

        afterOverrideResult = new Result();
        afterOverrideResult.feedbacks = [
            {
                type: FeedbackType.AUTOMATIC,
                testCase: { testName: 'testCase1' },
                detailText: 'testCase1 failed',
                credits: 0,
            },
        ];
        afterOverrideResult.assessor = user;

        overrideEntityResponse = new HttpResponse({ body: afterOverrideResult });

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
        codeEditorFileBrowserComp.filesTreeViewItem.set(treeItems);
        codeEditorFileBrowserComp.repositoryFiles.set(repositoryFiles);
        fixture.changeDetectorRef.detectChanges();
        codeEditorFileBrowserComp.selectedFileChange.emit('folder/file1');
        fixture.changeDetectorRef.detectChanges();
        codeEditorFileBrowserComp.isLoadingFiles.set(false);
        fixture.changeDetectorRef.detectChanges();
        const browserComponent = fixture.debugElement.query(By.directive(CodeEditorFileBrowserComponent)).componentInstance;
        expect(browserComponent).toBeDefined();
        expect(browserComponent.filesTreeViewItem()).toHaveLength(1);

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
        const user2 = <User>{ id: 100 };
        const updateAfterNewAssessment = vi.spyOn(programmingAssessmentManualResultService, 'saveAssessment').mockReturnValue(of(overrideEntityResponse));
        result.assessor = user2;
        result.hasComplaint = false;
        comp.ngOnInit();
        await flushMicrotasks();
        expect(comp.isAssessor()).toBe(false);
        addFeedbackAndValidateScore(comp, 0, 0);
        await comp.submit();
        fixture.changeDetectorRef.detectChanges();
        const alertElementSubmit = debugElement.queryAll(By.css('jhi-alert'));
        expect(alertElementSubmit).not.toBeNull();

        expect(getIdentityStub).toHaveBeenCalled();
        expect(updateAfterNewAssessment).toHaveBeenCalledOnce();
        expect(comp.isAssessor()).toBe(true);
    });

    it('should be able to override directly after submitting', () => {
        vi.spyOn(programmingAssessmentManualResultService, 'saveAssessment');

        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.isAtLeastInstructor = true;
        exercise.dueDate = dayjs();
        comp.exercise.set(exercise);
        comp.isAssessor.set(true);
        comp.participation.set(participation);
        comp.manualResult.set(result);
        comp.submit();
        expect(comp.canOverride).toBe(true);
    });

    it('should merge new feedback suggestions directly into the editable feedback, skipping duplicates of existing manual feedback', async () => {
        const existingUnreferenced = { text: 'unreferenced test', detailText: 'some detail', reference: undefined, type: FeedbackType.MANUAL_UNREFERENCED, credits: 1 } as Feedback;
        const existingReferenced = {
            text: 'referenced test',
            detailText: 'some detail',
            reference: 'file:src/Test.java_line:1',
            type: FeedbackType.MANUAL,
            credits: 1,
        } as Feedback;
        comp.unreferencedFeedback.set([existingUnreferenced]);
        comp.referencedFeedback.set([existingReferenced]);
        comp.manualResult.set({ feedbacks: [existingUnreferenced, existingReferenced] } as Result);

        const feedbackSuggestionsStub = vi.spyOn(internals(comp).athenaService, 'getProgrammingFeedbackSuggestions');
        feedbackSuggestionsStub.mockReturnValue(
            of([
                { text: 'FeedbackSuggestion:accepted:unreferenced test', detailText: 'some detail' },
                {
                    text: 'FeedbackSuggestion:accepted:referenced test',
                    detailText: 'some detail',
                    reference: 'file:src/Test.java_line:1',
                },
                {
                    text: 'FeedbackSuggestion:accepted:suggestion to pass',
                    detailText: 'some detail',
                    reference: 'file:src/Test.java_line:2',
                    type: FeedbackType.MANUAL,
                    credits: 1,
                },
            ] as Feedback[]),
        );
        comp.submission.set({ id: undefined } as ProgrammingSubmission); // Needed for loadFeedbackSuggestions
        await internals(comp).loadFeedbackSuggestions();

        // Only the genuinely new suggestion is merged in, directly as editable feedback - no separate pending/accept-discard state.
        expect(comp.referencedFeedback()).toContainEqual(
            expect.objectContaining({ text: 'FeedbackSuggestion:accepted:suggestion to pass', reference: 'file:src/Test.java_line:2' }),
        );
        expect(comp.unreferencedFeedback()).toEqual([existingUnreferenced]);
        expect(comp.hasAcceptedFeedbackSuggestions()).toBe(true);
        // Auto-accepted suggestions are unsaved changes: navigating away must warn like any other edit.
        expect(comp.hasPendingChanges).toBe(true);
    });

    it('should render newly auto-accepted AI feedback suggestions as inline widgets in the code editor', async () => {
        // Reproduces the reported bug: AI feedback suggestions get merged into the editable feedback list, but
        // never reach the Monaco editor's `feedbacks` input, so no inline widget appears on the line.
        const getFilesWithContentStub = vi.spyOn(repositoryFileService, 'getFilesWithContent');
        getFilesWithContentStub.mockReturnValue(of(templateFileSessionReturn));
        const getFileStub = vi.spyOn(repositoryFileService, 'getFile');
        getFileStub.mockReturnValue(new BehaviorSubject({ fileContent: 'new file text' }));

        const feedbackLoaded = firstValueFrom(outputToObservable(comp.onFeedbackLoaded));
        fixture.detectChanges();
        await feedbackLoaded;
        await flushMicrotasks();
        fixture.changeDetectorRef.detectChanges();

        const codeEditorMonacoComp: CodeEditorMonacoComponent = fixture.debugElement.query(By.directive(CodeEditorMonacoComponent)).componentInstance;
        expect(codeEditorMonacoComp.feedbacks()).toEqual(result.feedbacks);

        const suggestion = {
            text: 'FeedbackSuggestion:accepted:new suggestion',
            detailText: 'some detail',
            reference: 'file:folder/file1_line:0',
            type: FeedbackType.MANUAL,
            credits: 1,
        } as Feedback;
        vi.spyOn(internals(comp).athenaService, 'getProgrammingFeedbackSuggestions').mockReturnValue(of([suggestion]));

        await internals(comp).loadFeedbackSuggestions();
        fixture.changeDetectorRef.detectChanges();

        expect(codeEditorMonacoComp.feedbacks()).toContainEqual(expect.objectContaining({ reference: 'file:folder/file1_line:0' }));

        getFilesWithContentStub.mockRestore();
        getFileStub.mockRestore();
    });

    it('should reset hasAcceptedFeedbackSuggestions when a new submission is received', async () => {
        // Simulate the banner still being shown for a previously loaded submission's auto-accepted suggestions.
        comp.hasAcceptedFeedbackSuggestions.set(true);

        submission.results![0].feedbacks = [
            {
                detailText: 'text',
                credits: 1,
                type: FeedbackType.MANUAL_UNREFERENCED,
            },
        ];
        await internals(comp).onSubmissionReceived('123', submission);

        expect(comp.hasAcceptedFeedbackSuggestions()).toBe(false);
    });

    it('should show complaint for result with complaint and check assessor', async () => {
        comp.ngOnInit();
        // Flush the identity() microtask and the asap-scheduled submission chain. Assertions are captured before any
        // macrotask, so the framework's zoneless initial change detection cannot re-run ngOnInit and inflate counts.
        await flushMicrotasks();

        expect(getIdentityStub).toHaveBeenCalledOnce();
        expect(lockAndGetProgrammingSubmissionParticipationStub).toHaveBeenCalledOnce();
        expect(findBySubmissionIdStub).toHaveBeenCalledOnce();
        expect(comp.isAssessor()).toBe(true);
        expect(comp.complaint()).not.toBeNull();
        fixture.changeDetectorRef.detectChanges();

        const complaintsForm = debugElement.query(By.css('jhi-complaints-for-tutor-form'));
        expect(complaintsForm).not.toBeNull();
        expect(comp.complaint()).not.toBeNull();
    });

    it('should lock a new submission', () => {
        const activatedRoute: ActivatedRoute = TestBed.inject(ActivatedRoute);
        activatedRoute.params = of({ submissionId: 'new' });
        TestBed.inject(ActivatedRoute);

        getProgrammingSubmissionForExerciseWithoutAssessmentStub.mockReturnValue(of(submission));

        comp.ngOnInit();
        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).toHaveBeenCalledOnce();
    });

    it.each([
        { param: '1', expectedRound: 1, description: 'a usable round' },
        { param: undefined, expectedRound: 0, description: 'an absent round' },
        { param: '   ', expectedRound: 0, description: 'a whitespace only round' },
        { param: 'abc', expectedRound: 0, description: 'a round that is not a number' },
        { param: '1.5', expectedRound: 0, description: 'a fractional round' },
        { param: '-1', expectedRound: 0, description: 'a negative round' },
        { param: '1e3', expectedRound: 0, description: 'an exponential round' },
    ])('should lock the submission for $description', ({ param, expectedRound }) => {
        // The round is sent along when the submission is locked, so an unusable value must not travel on as NaN: the
        // request went out as correction-round=NaN and left the tutor on an empty editor (#13396).
        // queryParamMap is declared readonly on ActivatedRoute, so the mock is reached through a writable view.
        const activatedRoute = TestBed.inject(ActivatedRoute) as unknown as { queryParamMap: Observable<ParamMap> };
        activatedRoute.queryParamMap = of(convertToParamMap(param === undefined ? { testRun: 'false' } : { testRun: 'false', 'correction-round': param }));

        comp.ngOnInit();

        expect(comp.correctionRound()).toBe(expectedRound);
        expect(lockAndGetProgrammingSubmissionParticipationStub).toHaveBeenCalledExactlyOnceWith(123, expectedRound);
    });

    it('should keep the round it locked with when only the correction round in the url changes', () => {
        // This component has no resolver, so a `correction-round` that changes on its own — reachable only by
        // hand-editing the address bar — starts no new load. The round it shows must then stay the round the submission
        // was locked with, because the same value indexes the results of that submission.
        const queryParamMap$ = new BehaviorSubject(convertToParamMap({ testRun: 'false', 'correction-round': '1' }));
        const activatedRoute = TestBed.inject(ActivatedRoute) as unknown as { queryParamMap: Observable<ParamMap> };
        activatedRoute.queryParamMap = queryParamMap$.asObservable();

        comp.ngOnInit();
        expect(comp.correctionRound()).toBe(1);

        queryParamMap$.next(convertToParamMap({ testRun: 'false', 'correction-round': '0' }));

        expect(comp.correctionRound()).toBe(1);
        expect(lockAndGetProgrammingSubmissionParticipationStub).toHaveBeenCalledExactlyOnceWith(123, 1);
    });

    it('should not show complaint when participation contains no complaint', async () => {
        findBySubmissionIdStub.mockReturnValue(of({ body: undefined }));
        comp.ngOnInit();
        await flushMicrotasks();

        expect(getIdentityStub).toHaveBeenCalledOnce();
        expect(lockAndGetProgrammingSubmissionParticipationStub).toHaveBeenCalledOnce();
        expect(findBySubmissionIdStub).toHaveBeenCalledOnce();
        expect(comp.complaint()).toBeUndefined();
        fixture.changeDetectorRef.detectChanges();

        const complaintsForm = debugElement.query(By.css('jhi-complaints-for-tutor-form'));
        expect(complaintsForm).toBeNull();
    });

    it('should calculate score correctly for IncludedCompletelyWithBonusPointsExercise', async () => {
        comp.ngOnInit();
        await flushMicrotasks();

        comp.exercise().maxPoints = 10;
        comp.exercise().bonusPoints = 10;
        comp.automaticFeedback.set([]);
        comp.referencedFeedback.set([]);
        comp.unreferencedFeedback.set([]);
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

        comp.exercise().maxPoints = 10;
        comp.exercise().bonusPoints = 0;
        comp.automaticFeedback.set([]);
        comp.referencedFeedback.set([]);
        comp.unreferencedFeedback.set([]);
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

        comp.exercise().maxPoints = 10;
        comp.exercise().bonusPoints = 0;
        comp.automaticFeedback.set([]);
        comp.referencedFeedback.set([]);
        comp.unreferencedFeedback.set([]);
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

        comp.exercise().maxPoints = 10;
        comp.exercise().bonusPoints = 0;
        comp.automaticFeedback.set([]);
        comp.referencedFeedback.set([]);
        comp.unreferencedFeedback.set([]);
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
        expect(comp.submission()?.results?.[0].score).toBeDefined();
    });

    it('should save and submit manual result', async () => {
        comp.ngOnInit();
        await flushMicrotasks();
        comp.automaticFeedback.set([
            {
                type: FeedbackType.AUTOMATIC,
                testCase: { testName: 'testCase1' },
                detailText: 'testCase1 failed',
                credits: 0,
            },
        ]);
        comp.referencedFeedback.set([
            {
                type: FeedbackType.MANUAL,
                text: 'manual feedback',
                detailText: 'manual feedback for a file:1',
                credits: 2,
                reference: 'file:1_line:1',
            },
        ]);
        comp.unreferencedFeedback.set([
            {
                type: FeedbackType.MANUAL_UNREFERENCED,
                detailText: 'unreferenced feedback',
                credits: 1,
            },
        ]);
        comp.validateFeedback();
        comp.save();
        const alertElement = debugElement.queryAll(By.css('jhi-alert'));

        expect(comp.manualResult()?.feedbacks).toHaveLength(3);
        expect(comp.manualResult()?.feedbacks!.some((feedback) => feedback.type === FeedbackType.AUTOMATIC)).toBe(true);
        expect(comp.manualResult()?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL)).toBe(true);
        expect(comp.manualResult()?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)).toBe(true);
        expect(alertElement).not.toBeNull();

        // Reset feedbacks
        comp.manualResult()!.feedbacks = [];
        comp.validateFeedback();
        await comp.submit();
        const alertElementSubmit = debugElement.queryAll(By.css('jhi-alert'));

        expect(comp.manualResult()?.feedbacks).toHaveLength(3);
        expect(comp.manualResult()?.feedbacks!.some((feedback) => feedback.type === FeedbackType.AUTOMATIC)).toBe(true);
        expect(comp.manualResult()?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL)).toBe(true);
        expect(comp.manualResult()?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)).toBe(true);
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
        expect(comp.cancelBusy()).toBe(false);
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
        // Merge rather than replace, so that the navigation keeps testRun and every other parameter (#13421).
        const queryParams = { queryParams: { 'correction-round': 0 }, queryParamsHandling: 'merge' };
        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).toHaveBeenCalledOnce();
        expect(routerStub).toHaveBeenCalledWith(url, queryParams);
    });

    it('should show a message if no more unassessed submissions are present', () => {
        comp.exercise.set(exercise);
        comp.ngOnInit();

        getProgrammingSubmissionForExerciseWithoutAssessmentStub.mockReturnValue(of(undefined));
        comp.nextSubmission();

        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).toHaveBeenCalledOnce();
        expect(comp.submission()).toBeUndefined();
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
        const validateSpy = vi.spyOn(comp, 'validateFeedback').mockImplementation(() => comp.assessmentsAreValid.set(true));

        comp.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

        expect(validateSpy).toHaveBeenCalledOnce();
        expect(updateAfterComplaintStub).toHaveBeenCalledOnce();
        expect(comp.manualResult()!.score).toBe(errorKeyFromServer ? 0 : 100);
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
        comp.referencedFeedback.set([{ detailText: 'REF', credits: 1, reference: 'file:1', type: FeedbackType.MANUAL } as Feedback]);
        comp.unreferencedFeedback.set([{ detailText: 'UNREF', credits: 1, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback]);
        comp.automaticFeedback.set([{ detailText: 'AUTO', credits: 0, type: FeedbackType.AUTOMATIC } as Feedback]);
        // ...while the manual result still carries a stale feedback list that must NOT be the one sent to the server.
        comp.manualResult()!.feedbacks = [{ detailText: 'STALE', credits: 99, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback];

        vi.spyOn(comp, 'validateFeedback').mockImplementation(() => comp.assessmentsAreValid.set(true));
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
        expect(comp.assessmentsAreValid()).toBe(true);
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
        expect(comp.assessmentsAreValid()).toBe(true);
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

        const validateSpy = vi.spyOn(comp, 'validateFeedback').mockImplementation(() => comp.assessmentsAreValid.set(false));

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
            comp.exercise.set({ maxPoints: 2 } as ProgrammingExercise);
            comp.totalScoreBeforeAssessment = totalScoreBeforeAssessment;
            comp.referencedFeedback.set([]);
            comp.automaticFeedback.set([]);
            comp.unreferencedFeedback.set(newFeedback);
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
        expect(comp.referencedFeedback()).toEqual([
            { reference: 'file:src/Test.java_line:1', type: FeedbackType.MANUAL },
            { reference: 'file:src/Test.java_line:2', type: FeedbackType.MANUAL },
        ]);
        expect(validateFeedbackStub).toHaveBeenCalled();
    });

    it('should return true for hasAutomaticFeedback when automaticFeedback is non-empty', () => {
        comp.automaticFeedback.set([{ type: FeedbackType.AUTOMATIC, credits: 1 }]);
        expect(comp.hasAutomaticFeedback()).toBe(true);
    });

    it('should return false for hasAutomaticFeedback when automaticFeedback is empty and no suggestions were accepted', () => {
        comp.automaticFeedback.set([]);
        expect(comp.hasAutomaticFeedback()).toBe(false);
    });

    it('should return true for hasAutomaticFeedback when Athena feedback suggestions were accepted', () => {
        comp.automaticFeedback.set([]);
        comp.hasAcceptedFeedbackSuggestions.set(true);
        expect(comp.hasAutomaticFeedback()).toBe(true);
    });

    it('should return true for isFeedbackSuggestionsEnabled when feedbackSuggestionModule is set', () => {
        comp.exercise.set(Object.assign({}, exercise, { feedbackSuggestionModule: 'module_text_programming' }) as unknown as ProgrammingExercise);
        expect(comp.isFeedbackSuggestionsEnabled()).toBe(true);
    });

    it('should return false for isFeedbackSuggestionsEnabled when feedbackSuggestionModule is absent', () => {
        comp.exercise.set(Object.assign({}, exercise, { feedbackSuggestionModule: undefined }) as unknown as ProgrammingExercise);
        expect(comp.isFeedbackSuggestionsEnabled()).toBe(false);
    });

    it('should set loadingFeedbackSuggestions to true while fetching and false after', async () => {
        const subject = new Subject<Feedback[]>();
        vi.spyOn(comp['athenaService'], 'getProgrammingFeedbackSuggestions').mockReturnValue(subject.asObservable());
        comp.submission.set({ id: 42 } as ProgrammingSubmission);

        const loadPromise = comp['loadFeedbackSuggestions']();
        expect(comp.loadingFeedbackSuggestions()).toBe(true);

        subject.next([]);
        subject.complete();
        await loadPromise;

        expect(comp.loadingFeedbackSuggestions()).toBe(false);
    });

    it('should reset loadingFeedbackSuggestions when a new submission that needs no suggestions arrives while a previous request is still pending', async () => {
        // Simulate submission A's Athena request still being in flight when navigation loads submission B, an
        // already-assessed submission which never starts its own request.
        comp.loadingFeedbackSuggestions.set(true);

        await internals(comp).onSubmissionReceived('123', submission);

        expect(comp.loadingFeedbackSuggestions()).toBe(false);
    });

    it('should render the feedback suggestions banner when submission is set', async () => {
        vi.spyOn(repositoryFileService, 'getFilesWithContent').mockReturnValue(of(templateFileSessionReturn));
        vi.spyOn(repositoryFileService, 'getFile').mockReturnValue(new BehaviorSubject({ fileContent: '' }));

        fixture.detectChanges();
        await flushMicrotasks();
        fixture.changeDetectorRef.detectChanges();

        const banner = fixture.debugElement.query(By.directive(FeedbackSuggestionsBannerComponent));
        expect(banner).not.toBeNull();
    });

    describe('when assessment is not possible yet', () => {
        // The server rejects opening an assessment while the exam is still running and says when the tutor can come
        // back. The editor is unusable until then, so the container explains that instead of the code editor.
        const notPossibleYetResponse = () =>
            new HttpErrorResponse({
                status: 403,
                error: { errorKey: ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING, params: { date: '2026-08-01T10:00:00Z' } },
            });

        it('should explain when assessment is possible instead of claiming the participation is missing', () => {
            const alertService = TestBed.inject(AlertService);
            const closeAllSpy = vi.spyOn(alertService, 'closeAll');
            const errorSpy = vi.spyOn(alertService, 'error');
            lockAndGetProgrammingSubmissionParticipationStub.mockReturnValue(throwError(() => notPossibleYetResponse()));

            // detectChanges rather than a manual ngOnInit, so that the component initializes exactly once and renders
            fixture.detectChanges();

            expect(comp.assessmentNotPossibleYet()).toEqual({ translationKey: `error.${ASSESSMENT_NOT_POSSIBLE_EXAM_RUNNING}`, date: '2026-08-01T10:00:00Z' });
            expect(comp.participationCouldNotBeFetched()).toBe(false);
            expect(debugElement.query(By.css('#assessment-not-possible-yet'))).not.toBeNull();
            // the submission does exist, so the "no unassessed submissions" fallback must not contradict the explanation
            expect(debugElement.query(By.css('[jhiTranslate="artemisApp.programmingAssessment.notFound"]'))).toBeNull();
            expect(debugElement.query(By.css('[jhiTranslate="artemisApp.editor.errors.participationNotFound"]'))).toBeNull();
            // the panel explains this permanently, so the interceptor's toast is closed and no second one is added
            expect(closeAllSpy).toHaveBeenCalledOnce();
            expect(errorSpy).not.toHaveBeenCalled();
        });

        it('should clear the reason when a submission is loaded into the reused component', async () => {
            const params = new BehaviorSubject<{ submissionId: number }>({ submissionId: 123 });
            TestBed.inject(ActivatedRoute).params = params;
            lockAndGetProgrammingSubmissionParticipationStub.mockReturnValue(throwError(() => notPossibleYetResponse()));

            comp.ngOnInit();
            expect(comp.assessmentNotPossibleYet()).toBeDefined();

            // The exam ends and the tutor opens the next submission: Angular reuses this component instance and only
            // re-emits the route params, so the previous reason has to be cleared or it would hide the loaded editor.
            lockAndGetProgrammingSubmissionParticipationStub.mockReturnValue(scheduled([submission], asapScheduler));
            params.next({ submissionId: 456 });
            await flushMicrotasks();

            expect(comp.assessmentNotPossibleYet()).toBeUndefined();
            expect(comp.submission()).toEqual(submission);
        });
    });
});
