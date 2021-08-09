import * as ace from 'brace';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, spy, stub } from 'sinon';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { By } from '@angular/platform-browser';
import { JhiAlertService } from 'ng-jhipster';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-button.component';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Complaint } from 'app/entities/complaint.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { MockRepositoryFileService } from '../../helpers/mocks/service/mock-repository-file.service';
import { MockExerciseHintService } from '../../helpers/mocks/service/mock-exercise-hint.service';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { CodeEditorTutorAssessmentContainerComponent } from 'app/exercises/programming/assess/code-editor-tutor-assessment-container.component';
import { Result } from 'app/entities/result.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { delay } from 'rxjs/operators';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { TreeviewComponent, TreeviewItem } from 'ngx-treeview';
import { FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { RouterTestingModule } from '@angular/router/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { IncludedInScoreBadgeComponent } from 'app/exercises/shared/exercise-headers/included-in-score-badge.component';
import { ExerciseHintStudentComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';
import { AssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentHeaderComponent } from 'app/assessment/assessment-header/assessment-header.component';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
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
import { AceEditorComponent } from 'ng2-ace-editor';
import { ExtensionPointDirective } from 'app/shared/extension-point/extension-point.directive';

chai.use(sinonChai);
const expect = chai.expect;

function addFeedbackAndValidateScore(comp: CodeEditorTutorAssessmentContainerComponent, pointsAwarded: number, scoreExpected: number) {
    comp.unreferencedFeedback.push({
        type: FeedbackType.MANUAL_UNREFERENCED,
        detailText: 'unreferenced feedback',
        credits: pointsAwarded,
    });
    comp.validateFeedback();
    expect(comp.manualResult?.score).to.equal(scoreExpected);
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

    let updateAfterComplaintStub: SinonStub;
    let findByResultIdStub: SinonStub;
    let getIdentityStub: SinonStub;
    let getProgrammingSubmissionForExerciseWithoutAssessmentStub: SinonStub;
    let lockAndGetProgrammingSubmissionParticipationStub: SinonStub;
    let findWithParticipationsStub: SinonStub;

    const user = <User>{ id: 99, groups: ['instructorGroup'] };
    const result: Result = <any>{
        feedbacks: [new Feedback()],
        participation: new StudentParticipation(),
        score: 80,
        successful: true,
        submission: new ProgrammingSubmission(),
        assessor: user,
        hasComplaint: true,
        assessmentType: AssessmentType.SEMI_AUTOMATIC,
        id: 2,
        resultString: '1 of 13 passed',
    };
    result.submission!.id = 1;

    const complaint = <Complaint>{ id: 1, complaintText: 'Why only 80%?', result };
    const exercise = {
        id: 1,
        templateParticipation: {
            id: 3,
            repositoryUrl: 'test2',
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
    participation.repositoryUrl = 'http://student1@bitbucket.ase.in.tum.de/scm/TEST/test-repo-student1.git';
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

    const route = { params: of({ submissionId: 123 }), queryParamMap: of(convertToParamMap({ testRun: false })) } as any as ActivatedRoute;
    const fileContent = 'This is the content of a file';
    const templateFileSessionReturn: { [fileName: string]: string } = { 'folder/file1': fileContent };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                CodeEditorTutorAssessmentContainerComponent,
                MockComponent(ProgrammingAssessmentRepoExportButtonComponent),
                MockComponent(FaIconComponent),
                AssessmentLayoutComponent,
                MockComponent(AssessmentComplaintAlertComponent),
                MockComponent(AssessmentHeaderComponent),
                MockComponent(ComplaintsForTutorComponent),
                CodeEditorContainerComponent,
                CodeEditorFileBrowserComponent,
                MockPipe(KeysPipe),
                MockComponent(TreeviewComponent),
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
                MockComponent(ExerciseHintStudentComponent),
                MockComponent(AssessmentInstructionsComponent),
                MockComponent(UnreferencedFeedbackComponent),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(ExtensionPointDirective),
            ],
            providers: [
                ProgrammingAssessmentManualResultService,
                ComplaintService,
                BuildLogService,
                AccountService,
                JhiAlertService,
                ResultService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: RepositoryFileService, useClass: MockRepositoryFileService },
                { provide: ExerciseHintService, useClass: MockExerciseHintService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ActivatedRoute, useValue: route },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                // Ignore console errors
                console.error = () => {
                    return false;
                };
                fixture = TestBed.createComponent(CodeEditorTutorAssessmentContainerComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                programmingAssessmentManualResultService = debugElement.injector.get(ProgrammingAssessmentManualResultService);
                programmingSubmissionService = debugElement.injector.get(ProgrammingSubmissionService);
                complaintService = debugElement.injector.get(ComplaintService);
                accountService = debugElement.injector.get(AccountService);
                programmingExerciseService = debugElement.injector.get(ProgrammingExerciseService);
                repositoryFileService = debugElement.injector.get(CodeEditorRepositoryFileService);

                updateAfterComplaintStub = stub(programmingAssessmentManualResultService, 'updateAfterComplaint').returns(of(afterComplaintResult));
                lockAndGetProgrammingSubmissionParticipationStub = stub(programmingSubmissionService, 'lockAndGetProgrammingSubmissionParticipation').returns(
                    of(submission).pipe(delay(100)),
                );
                findByResultIdStub = stub(complaintService, 'findByResultId').returns(of({ body: complaint } as HttpResponse<Complaint>));
                getIdentityStub = stub(accountService, 'identity').returns(new Promise((promise) => promise(user)));
                getProgrammingSubmissionForExerciseWithoutAssessmentStub = stub(
                    programmingSubmissionService,
                    'getProgrammingSubmissionForExerciseForCorrectionRoundWithoutAssessment',
                ).returns(of(unassessedSubmission));

                findWithParticipationsStub = stub(programmingExerciseService, 'findWithTemplateAndSolutionParticipation');
                findWithParticipationsStub.returns(of({ body: exercise }));
            });
    });

    afterEach(fakeAsync(() => {
        updateAfterComplaintStub.restore();
        findByResultIdStub.restore();
        lockAndGetProgrammingSubmissionParticipationStub.restore();
        getProgrammingSubmissionForExerciseWithoutAssessmentStub.restore();
    }));

    it('should use jhi-assessment-layout', () => {
        const assessmentLayout = fixture.debugElement.query(By.directive(AssessmentLayoutComponent));
        expect(assessmentLayout).to.exist;
    });

    it('should show complaint for result with complaint and check assessor', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);

        expect(getIdentityStub.calledOnce).to.be.true;
        expect(lockAndGetProgrammingSubmissionParticipationStub.calledOnce).to.be.true;
        expect(findByResultIdStub.calledOnce).to.be.true;
        expect(comp.isAssessor).to.be.true;
        expect(comp.complaint).to.exist;
        fixture.detectChanges();

        const complaintsForm = debugElement.query(By.css('jhi-complaints-for-tutor-form'));
        expect(complaintsForm).to.exist;
        expect(comp.complaint).to.exist;

        // Wait until periodic timer has passed out
        tick(100);
    }));

    it('should lock a new submission', fakeAsync(() => {
        const activatedRoute: ActivatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
        activatedRoute.params = of({ submissionId: 'new' });
        TestBed.inject(ActivatedRoute);

        getProgrammingSubmissionForExerciseWithoutAssessmentStub.returns(of(submission));

        comp.ngOnInit();
        tick(100);
        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).to.be.calledOnce;
    }));

    it('should not show complaint when result does not have it', fakeAsync(() => {
        result.hasComplaint = false;
        comp.ngOnInit();
        tick(100);

        expect(getIdentityStub.calledOnce).to.be.true;
        expect(lockAndGetProgrammingSubmissionParticipationStub.calledOnce).to.be.true;
        expect(findByResultIdStub.notCalled).to.be.true;
        expect(comp.complaint).to.not.exist;
        fixture.detectChanges();

        const complaintsForm = debugElement.query(By.css('jhi-complaints-for-tutor-form'));
        expect(complaintsForm).to.not.exist;

        // Wait until periodic timer has passed out
        tick(100);
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
    }));

    it('should save and submit manual result', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);
        comp.automaticFeedback = [{ type: FeedbackType.AUTOMATIC, text: 'testCase1', detailText: 'testCase1 failed', credits: 0 }];
        comp.referencedFeedback = [{ type: FeedbackType.MANUAL, text: 'manual feedback', detailText: 'manual feedback for a file:1', credits: 2, reference: 'file:1_line:1' }];
        comp.unreferencedFeedback = [{ type: FeedbackType.MANUAL_UNREFERENCED, detailText: 'unreferenced feedback', credits: 1 }];
        comp.validateFeedback();
        comp.save();
        const alertElement = debugElement.queryAll(By.css('jhi-alert'));

        expect(comp.manualResult?.feedbacks?.length).to.be.equal(3);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.AUTOMATIC)).to.be.true;
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL)).to.be.true;
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)).to.be.true;
        expect(alertElement).to.exist;

        // Reset feedbacks
        comp.manualResult!.feedbacks! = [];
        comp.validateFeedback();
        comp.submit();
        const alertElementSubmit = debugElement.queryAll(By.css('jhi-alert'));

        expect(comp.manualResult?.feedbacks?.length).to.be.equal(3);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.AUTOMATIC)).to.be.true;
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL)).to.be.true;
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)).to.be.true;
        expect(alertElementSubmit).to.exist;
        flush();
    }));

    it('should cancel the assessment and navigate back', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);
        const navigateBackStub = stub(comp, 'navigateBack');
        const cancelBackStub = stub(programmingAssessmentManualResultService, 'cancelAssessment').returns(of(undefined).pipe(delay(100)));
        global.confirm = () => true;
        const confirmSpy = spy(window, 'confirm');
        comp.cancel();

        expect(confirmSpy).to.be.calledOnce;
        tick(100);
        expect(comp.cancelBusy).to.be.false;
        expect(navigateBackStub).to.be.calledOnce;
        expect(cancelBackStub).to.be.calledOnce;
    }));

    it('should go to next submission', fakeAsync(() => {
        const routerStub = stub(TestBed.inject(Router), 'navigate');

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
        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).to.be.calledOnce;
        expect(routerStub).to.have.been.calledWith(url, queryParams);
    }));

    it('should highlight lines that were changed', fakeAsync(() => {
        // Stub
        const getFilesWithContentStub = stub(repositoryFileService, 'getFilesWithContent');
        getFilesWithContentStub.returns(of(templateFileSessionReturn));
        // Stub for ace editor
        const getFileStub = stub(repositoryFileService, 'getFile');
        getFileStub.returns(of({ fileContent: 'new file text' }));

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
        expect(browserComponent).to.exist;
        expect(browserComponent.filesTreeViewItem).to.have.lengthOf(1);

        const codeEditorAceComp = fixture.debugElement.query(By.directive(CodeEditorAceComponent)).componentInstance;
        codeEditorAceComp.isLoading = false;
        fixture.detectChanges();

        expect(codeEditorAceComp.markerIds).to.have.lengthOf(1);

        fixture.destroy();
        flush();
    }));

    it('should update assessment after complaint', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);
        comp.onUpdateAssessmentAfterComplaint(new ComplaintResponse());
        expect(updateAfterComplaintStub).to.be.calledOnce;
        expect(comp.manualResult!.score).to.be.equal(100);
        flush();
    }));
});
