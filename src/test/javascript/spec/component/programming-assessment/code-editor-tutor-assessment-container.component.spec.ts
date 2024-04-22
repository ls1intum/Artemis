import * as ace from 'brace';
import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { DebugElement } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { By } from '@angular/platform-browser';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-button.component';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Complaint } from 'app/entities/complaint.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { MockRepositoryFileService } from '../../helpers/mocks/service/mock-repository-file.service';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { Result } from 'app/entities/result.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { delay } from 'rxjs/operators';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { RouterTestingModule } from '@angular/router/testing';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { AssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentHeaderComponent } from 'app/assessment/assessment-header/assessment-header.component';
import { AssessmentAfterComplaint, ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { AssessmentComplaintAlertComponent } from 'app/assessment/assessment-complaint-alert/assessment-complaint-alert.component';
import { CodeEditorGridComponent } from 'app/exercises/programming/shared/code-editor/layout/code-editor-grid.component';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { KeysPipe } from 'app/shared/pipes/keys.pipe';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { CodeEditorFileBrowserCreateNodeComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-create-node.component';
import { CodeEditorStatusComponent } from 'app/exercises/programming/shared/code-editor/status/code-editor-status.component';
import { CodeEditorFileBrowserFolderComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-folder.component';
import { CodeEditorFileBrowserFileComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser-file.component';
import { CodeEditorTutorAssessmentInlineFeedbackComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-inline-feedback.component';
import { AceEditorComponent } from 'app/shared/markdown-editor/ace-editor/ace-editor.component';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';
import { TreeviewComponent } from 'app/exercises/programming/shared/code-editor/treeview/components/treeview/treeview.component';
import { TreeviewItem } from 'app/exercises/programming/shared/code-editor/treeview/models/treeview-item';
import { AlertService } from 'app/core/util/alert.service';
import { Exercise } from 'app/entities/exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockAthenaService } from '../../helpers/mocks/service/mock-athena.service';
import { AthenaService } from 'app/assessment/athena.service';
import { MockResizeObserver } from '../../helpers/mocks/service/mock-resize-observer';
import { EntityResponseType } from 'app/exercises/shared/result/result.service';

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
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');
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

    let updateAfterComplaintStub: jest.SpyInstance;
    let findBySubmissionIdStub: jest.SpyInstance;
    let getIdentityStub: jest.SpyInstance;
    let getProgrammingSubmissionForExerciseWithoutAssessmentStub: jest.SpyInstance;
    let lockAndGetProgrammingSubmissionParticipationStub: jest.SpyInstance;
    let findWithParticipationsStub: jest.SpyInstance;

    const user = <User>{ id: 99, groups: ['instructorGroup'] };
    const result: Result = {
        feedbacks: [new Feedback()],
        participation: new StudentParticipation(),
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
    participation.results = [result];
    participation.exercise = exercise;
    participation.id = 1;
    participation.student = { login: 'student1' } as User;
    participation.repositoryUri = 'http://student1@gitlab.ase.in.tum.de/scm/TEST/test-repo-student1.git';
    result.submission!.participation = participation;

    const submission: ProgrammingSubmission = new ProgrammingSubmission();
    submission.results = [result];
    submission.participation = participation;
    submission.id = 1234;
    submission.latestResult = result;

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

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                CodeEditorTutorAssessmentContainerComponent,
                MockComponent(ProgrammingAssessmentRepoExportButtonComponent),
                AssessmentLayoutComponent,
                MockComponent(AssessmentComplaintAlertComponent),
                MockComponent(AssessmentHeaderComponent),
                MockComponent(ComplaintsForTutorComponent),
                CodeEditorContainerComponent,
                CodeEditorFileBrowserComponent,
                MockPipe(KeysPipe),
                TreeviewComponent,
                MockComponent(CodeEditorStatusComponent),
                MockComponent(CodeEditorFileBrowserCreateNodeComponent),
                MockComponent(CodeEditorFileBrowserFolderComponent),
                MockComponent(CodeEditorFileBrowserFileComponent),
                MockComponent(CodeEditorBuildOutputComponent),
                MockComponent(CodeEditorGridComponent),
                MockComponent(CodeEditorActionsComponent),
                CodeEditorAceComponent,
                MockComponent(CodeEditorTutorAssessmentInlineFeedbackComponent),
                AceEditorComponent,
                MockComponent(CodeEditorInstructionsComponent),
                MockComponent(ResultComponent),
                MockComponent(IncludedInScoreBadgeComponent),
                MockComponent(AssessmentInstructionsComponent),
                MockComponent(UnreferencedFeedbackComponent),
                MockPipe(ArtemisTranslatePipe),
                ExtensionPointDirective,
            ],
            providers: [
                MockProvider(Router),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: RepositoryFileService, useClass: MockRepositoryFileService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: AthenaService, useClass: MockAthenaService },
                { provide: ActivatedRoute, useValue: route() },
                MockProvider(ProfileService, { getProfileInfo: () => of({ activeProfiles: [] }) }, 'useValue'),
            ],
        })
            .compileComponents()
            .then(() => {
                // Ignore console errors
                console.error = () => {
                    return false;
                };
                fixture = TestBed.createComponent(CodeEditorTutorAssessmentContainerComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                router = TestBed.inject(Router);

                programmingAssessmentManualResultService = debugElement.injector.get(ProgrammingAssessmentManualResultService);
                programmingSubmissionService = debugElement.injector.get(ProgrammingSubmissionService);
                complaintService = debugElement.injector.get(ComplaintService);
                accountService = debugElement.injector.get(AccountService);
                programmingExerciseService = debugElement.injector.get(ProgrammingExerciseService);
                repositoryFileService = debugElement.injector.get(CodeEditorRepositoryFileService);

                updateAfterComplaintStub = jest.spyOn(programmingAssessmentManualResultService, 'updateAfterComplaint').mockReturnValue(of(afterComplaintResult));
                lockAndGetProgrammingSubmissionParticipationStub = jest
                    .spyOn(programmingSubmissionService, 'lockAndGetProgrammingSubmissionParticipation')
                    .mockReturnValue(of(submission).pipe(delay(100)));
                findBySubmissionIdStub = jest.spyOn(complaintService, 'findBySubmissionId').mockReturnValue(of({ body: complaint } as HttpResponse<Complaint>));
                getIdentityStub = jest.spyOn(accountService, 'identity').mockReturnValue(new Promise((promise) => promise(user)));
                getProgrammingSubmissionForExerciseWithoutAssessmentStub = jest
                    .spyOn(programmingSubmissionService, 'getSubmissionWithoutAssessment')
                    .mockReturnValue(of(unassessedSubmission));

                findWithParticipationsStub = jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation');
                findWithParticipationsStub.mockReturnValue(of({ body: exercise }));

                // Mock the ResizeObserver, which is not available in the test environment
                global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
                    return new MockResizeObserver(callback);
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
        result.assessor = user;
        result.hasComplaint = true;
    });

    it('should use jhi-assessment-layout', () => {
        const assessmentLayout = fixture.debugElement.query(By.directive(AssessmentLayoutComponent));
        expect(assessmentLayout).toBeDefined();
    });

    it('should load the grading criteria on initialisation', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);

        expect(findWithParticipationsStub).toHaveBeenCalledWith(exercise.id, false, true);
    }));

    it('should update assessor correctly if the manual assessment is overridden', fakeAsync(() => {
        const user2 = <User>{ id: 100, groups: ['instructorGroup'] };
        const discardPendingSubmissionsWithConfirmationStub = jest.spyOn(comp, 'discardPendingSubmissionsWithConfirmation').mockReturnValue(Promise.resolve(true));
        const updateAfterNewAssessment = jest.spyOn(programmingAssessmentManualResultService, 'saveAssessment').mockReturnValue(of(overrideEntityResponse));
        result.assessor = user2;
        result.hasComplaint = false;
        comp.ngOnInit();
        tick(100);
        expect(comp.isAssessor).toBeFalse();
        addFeedbackAndValidateScore(comp, 0, 0);
        comp.submit().then(() => {
            fixture.detectChanges();
            const alertElementSubmit = debugElement.queryAll(By.css('jhi-alert'));
            expect(alertElementSubmit).not.toBeNull();

            expect(getIdentityStub).toHaveBeenCalled();
            expect(discardPendingSubmissionsWithConfirmationStub).toHaveBeenCalled();
            expect(updateAfterNewAssessment).toHaveBeenCalledOnce();
            expect(comp.isAssessor).toBeTrue();
        });
        flush();
    }));

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
        const feedbackSuggestionsStub = jest.spyOn(comp['athenaService'], 'getProgrammingFeedbackSuggestions');
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
        comp['submission'] = { id: undefined }; // Needed for loadFeedbackSuggestions
        await comp['loadFeedbackSuggestions']();
        expect(comp.feedbackSuggestions).toStrictEqual([
            {
                text: 'FeedbackSuggestion:suggestion to pass',
                detailText: 'some detail',
                reference: 'file:src/Test.java_line:2',
            },
        ]);
    });

    it('should show complaint for result with complaint and check assessor', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);

        expect(getIdentityStub).toHaveBeenCalledOnce();
        expect(lockAndGetProgrammingSubmissionParticipationStub).toHaveBeenCalledOnce();
        expect(findBySubmissionIdStub).toHaveBeenCalledOnce();
        expect(comp.isAssessor).toBeTrue();
        expect(comp.complaint).not.toBeNull();
        fixture.detectChanges();

        const complaintsForm = debugElement.query(By.css('jhi-complaints-for-tutor-form'));
        expect(complaintsForm).not.toBeNull();
        expect(comp.complaint).not.toBeNull();

        // Wait until periodic timer has passed out
        tick(100);
        flush();
    }));

    it('should lock a new submission', fakeAsync(() => {
        const activatedRoute: ActivatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
        activatedRoute.params = of({ submissionId: 'new' });
        TestBed.inject(ActivatedRoute);

        getProgrammingSubmissionForExerciseWithoutAssessmentStub.mockReturnValue(of(submission));

        comp.ngOnInit();
        tick(100);
        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).toHaveBeenCalledOnce();
        flush();
    }));

    it('should not show complaint when participation contains no complaint', fakeAsync(() => {
        findBySubmissionIdStub.mockReturnValue(of({ body: undefined }));
        comp.ngOnInit();
        tick(100);

        expect(getIdentityStub).toHaveBeenCalledOnce();
        expect(lockAndGetProgrammingSubmissionParticipationStub).toHaveBeenCalledOnce();
        expect(findBySubmissionIdStub).toHaveBeenCalledOnce();
        expect(comp.complaint).toBeUndefined();
        fixture.detectChanges();

        const complaintsForm = debugElement.query(By.css('jhi-complaints-for-tutor-form'));
        expect(complaintsForm).toBeNull();

        // Wait until periodic timer has passed out
        tick(100);
        flush();
    }));

    it('should calculate score correctly for IncludedCompletelyWithBonusPointsExercise', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);

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
        flush();
    }));

    it('should calculate score correctly for IncludedCompletelyWithoutBonusPointsExercise', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);

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
        flush();
    }));

    it('should calculate score correctly for IncludedAsBonusExercise', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);

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
        flush();
    }));

    it('should calculate score correctly for NotIncludedExercise', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);

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
        flush();
    }));

    it('should calculate score for result of submission', fakeAsync(() => {
        // When score is undefined
        result.score = undefined;
        comp.ngOnInit();
        tick(100);

        // Should calculate the score
        expect(comp.submission?.results?.[0].score).toBeDefined();
    }));

    it('should save and submit manual result', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);
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
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.AUTOMATIC)).toBeTrue();
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL)).toBeTrue();
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)).toBeTrue();
        expect(alertElement).not.toBeNull();

        // Reset feedbacks
        comp.manualResult!.feedbacks! = [];
        comp.validateFeedback();
        comp.submit();
        const alertElementSubmit = debugElement.queryAll(By.css('jhi-alert'));

        expect(comp.manualResult?.feedbacks).toHaveLength(3);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.AUTOMATIC)).toBeTrue();
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL)).toBeTrue();
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)).toBeTrue();
        expect(alertElementSubmit).not.toBeNull();
        flush();
    }));

    it('should cancel the assessment and navigate back', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);
        const navigateBackStub = jest.spyOn(comp, 'navigateBack');
        const cancelBackStub = jest.spyOn(programmingAssessmentManualResultService, 'cancelAssessment').mockReturnValue(of(undefined));
        global.confirm = () => true;
        const confirmSpy = jest.spyOn(window, 'confirm');
        comp.cancel();

        expect(confirmSpy).toHaveBeenCalledOnce();
        tick(100);
        expect(comp.cancelBusy).toBeFalse();
        expect(navigateBackStub).toHaveBeenCalledOnce();
        expect(cancelBackStub).toHaveBeenCalledOnce();
        flush();
    }));

    it('should go to next submission', fakeAsync(() => {
        const routerStub = jest.spyOn(router, 'navigate');

        comp.ngOnInit();
        const courseId = 123;
        comp.courseId = courseId;
        comp.exerciseId = exercise.id!;
        tick(100);
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
        flush();
    }));

    it('should show a message if no more unassessed submissions are present', () => {
        comp.exercise = exercise;
        comp.ngOnInit();

        getProgrammingSubmissionForExerciseWithoutAssessmentStub.mockReturnValue(of(undefined));
        comp.nextSubmission();

        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).toHaveBeenCalledOnce();
        expect(comp.submission).toBeUndefined();
    });

    it('should highlight lines that were changed', fakeAsync(() => {
        // Stub
        const getFilesWithContentStub = jest.spyOn(repositoryFileService, 'getFilesWithContent');
        getFilesWithContentStub.mockReturnValue(of(templateFileSessionReturn));
        // Stub for ace editor
        const getFileStub = jest.spyOn(repositoryFileService, 'getFile');
        getFileStub.mockReturnValue(of({ fileContent: 'new file text' }));

        // Data for file browser
        const treeItems = [
            new TreeviewItem({
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
        fixture.detectChanges();
        // wait until data is loaded from CodeEditorTutorAssessmentContainer
        tick(100);
        fixture.detectChanges();

        // Setup tree for file browser
        const codeEditorFileBrowserComp = fixture.debugElement.query(By.directive(CodeEditorFileBrowserComponent)).componentInstance;
        codeEditorFileBrowserComp.filesTreeViewItem = treeItems;
        codeEditorFileBrowserComp.repositoryFiles = repositoryFiles;
        codeEditorFileBrowserComp.selectedFile = 'folder/file1';
        fixture.detectChanges();
        codeEditorFileBrowserComp.isLoadingFiles = false;
        fixture.detectChanges();
        const browserComponent = fixture.debugElement.query(By.directive(CodeEditorFileBrowserComponent)).componentInstance;
        expect(browserComponent).toBeDefined();
        expect(browserComponent.filesTreeViewItem).toHaveLength(1);

        const codeEditorAceComp = fixture.debugElement.query(By.directive(CodeEditorAceComponent)).componentInstance;
        codeEditorAceComp.isLoading = false;
        fixture.whenStable().then(() => {
            fixture.detectChanges();
            expect(codeEditorAceComp.markerIds).toHaveLength(1);
        });

        getFilesWithContentStub.mockRestore();
        getFileStub.mockRestore();
        fixture.destroy();
        flush();
    }));

    it.each([undefined, 'genericErrorKey', 'complaintLock'])(
        'should update assessment after complaint, errorKeyFromServer=%s',
        fakeAsync((errorKeyFromServer: string | undefined) => {
            comp.ngOnInit();
            tick(100);

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
            const errorSpy = jest.spyOn(alertService, 'error');
            const validateSpy = jest.spyOn(comp, 'validateFeedback').mockImplementation(() => (comp.assessmentsAreValid = true));

            comp.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);

            expect(validateSpy).toHaveBeenCalledOnce();
            expect(updateAfterComplaintStub).toHaveBeenCalledOnce();
            expect(comp.manualResult!.score).toBe(errorKeyFromServer ? 0 : 100);
            flush();
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
        }),
    );

    it('should display error when complaint resolved but assessment invalid', () => {
        let onSuccessCalled = false;
        let onErrorCalled = false;
        const assessmentAfterComplaint: AssessmentAfterComplaint = {
            complaintResponse: new ComplaintResponse(),
            onSuccess: () => (onSuccessCalled = true),
            onError: () => (onErrorCalled = true),
        };
        const alertService = TestBed.inject(AlertService);
        const errorSpy = jest.spyOn(alertService, 'error');

        const validateSpy = jest.spyOn(comp, 'validateFeedback').mockImplementation(() => (comp.assessmentsAreValid = false));

        comp.onUpdateAssessmentAfterComplaint(assessmentAfterComplaint);
        expect(validateSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledOnce();
        expect(errorSpy).toHaveBeenCalledWith('artemisApp.programmingAssessment.invalidAssessments');
        expect(onSuccessCalled).toBeFalse();
        expect(onErrorCalled).toBeTrue();
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
            jest.spyOn(window, 'confirm').mockReturnValue(false);

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
        const validateFeedbackStub = jest.spyOn(comp, 'validateFeedback');
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
        const modalOpenStub = jest.spyOn(comp['modalService'], 'open').mockReturnValue({ closed: of(true) } as NgbModalRef); // Confirm dismissal
        comp.feedbackSuggestions = [{ id: 1, credits: 1 }];
        await comp.discardPendingSubmissionsWithConfirmation();
        expect(modalOpenStub).toHaveBeenCalled();
        // Dismissal should clear all feedback suggestions
        expect(comp.feedbackSuggestions).toBeEmpty();
    });

    it('should keep feedback suggestions if the confirmation dialog is cancelled', async () => {
        const modalOpenStub = jest.spyOn(comp['modalService'], 'open').mockReturnValue({ closed: of(false) } as NgbModalRef); // Cancel suggestion dismissal
        comp.feedbackSuggestions = [{ id: 1, credits: 1 }];
        await comp.discardPendingSubmissionsWithConfirmation();
        expect(modalOpenStub).toHaveBeenCalled();
        // Cancelling should keep everything intact
        expect(comp.feedbackSuggestions).not.toBeEmpty();
    });

    it('should not show a confirmation dialog if there are no feedback suggestions left', async () => {
        const modalOpenStub = jest.spyOn(comp['modalService'], 'open');
        comp.feedbackSuggestions = [];
        await comp.discardPendingSubmissionsWithConfirmation();
        expect(modalOpenStub).not.toHaveBeenCalled();
    });
});
