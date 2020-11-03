import * as ace from 'brace';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModal, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { FormsModule } from '@angular/forms';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { SinonStub, spy, stub } from 'sinon';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { FormDateTimePickerModule } from 'app/shared/date-time-picker/date-time-picker.module';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { By } from '@angular/platform-browser';
import { JhiAlertService } from 'ng-jhipster';
import { MockComponent } from 'ng-mocks';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { RepositoryFileService } from 'app/exercises/shared/result/repository.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
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
import { ArtemisProgrammingAssessmentModule } from 'app/exercises/programming/assess/programming-assessment.module';
import { Result } from 'app/entities/result.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { delay } from 'rxjs/operators';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ComplaintResponse } from 'app/entities/complaint-response.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('CodeEditorTutorAssessmentContainerComponent', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');
    let comp: CodeEditorTutorAssessmentContainerComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentContainerComponent>;
    let debugElement: DebugElement;
    let programmingAssessmentManualResultService: ProgrammingAssessmentManualResultService;
    let complaintService: ComplaintService;
    let accountService: AccountService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;
    let programmingSubmissionService: ProgrammingSubmissionService;

    let updateAfterComplaintStub: SinonStub;
    let getStudentParticipationWithResultsStub: SinonStub;
    let findByResultIdStub: SinonStub;
    let getIdentityStub: SinonStub;
    let getProgrammingSubmissionForExerciseWithoutAssessmentStub: SinonStub;
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
    };
    result.submission!.id = 1;
    const complaint = <Complaint>{ id: 1, complaintText: 'Why only 80%?', result };
    const exercise = <ProgrammingExercise>{ id: 1, maxScore: 100, gradingInstructions: 'Grading Instructions', course: <Course>{ instructorGroupName: 'instructorGroup' } };
    const automaticResult: Result = { feedbacks: [new Feedback()], assessmentType: AssessmentType.AUTOMATIC, id: 1, resultString: '1 of 13 passed' };
    const participation: ProgrammingExerciseStudentParticipation = new ProgrammingExerciseStudentParticipation();
    participation.results = [result, automaticResult];
    participation.exercise = exercise;
    participation.id = 1;

    const unassessedSubmission = new ProgrammingSubmission();
    const participation2 = new ProgrammingExerciseStudentParticipation();
    participation2.id = 12;
    unassessedSubmission.participation = participation2;

    const afterComplaintResult = new Result();
    afterComplaintResult.score = 100;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), ArtemisTestModule, ArtemisSharedModule, NgbModule, FormDateTimePickerModule, FormsModule, ArtemisProgrammingAssessmentModule],
            declarations: [MockComponent(ProgrammingAssessmentRepoExportButtonComponent)],
            providers: [
                ProgrammingAssessmentManualResultService,
                ProgrammingExerciseParticipationService,
                ComplaintService,
                BuildLogService,
                AccountService,
                JhiAlertService,
                ResultService,
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: RepositoryFileService, useClass: MockRepositoryFileService },
                { provide: ExerciseHintService, useClass: MockExerciseHintService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
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
                programmingExerciseParticipationService = debugElement.injector.get(ProgrammingExerciseParticipationService);
                programmingSubmissionService = debugElement.injector.get(ProgrammingSubmissionService);
                complaintService = debugElement.injector.get(ComplaintService);
                accountService = debugElement.injector.get(AccountService);

                updateAfterComplaintStub = stub(programmingAssessmentManualResultService, 'updateAfterComplaint').returns(of(afterComplaintResult));
                getStudentParticipationWithResultsStub = stub(programmingExerciseParticipationService, 'getStudentParticipationWithResults').returns(
                    of(participation).pipe(delay(100)),
                );
                findByResultIdStub = stub(complaintService, 'findByResultId').returns(of({ body: complaint } as HttpResponse<Complaint>));
                getIdentityStub = stub(accountService, 'identity').returns(new Promise((promise) => promise(user)));
                getProgrammingSubmissionForExerciseWithoutAssessmentStub = stub(programmingSubmissionService, 'getProgrammingSubmissionForExerciseWithoutAssessment').returns(
                    of(unassessedSubmission),
                );
            });
    });

    afterEach(fakeAsync(() => {
        updateAfterComplaintStub.restore();
        findByResultIdStub.restore();
        getStudentParticipationWithResultsStub.restore();
    }));

    it('should use jhi-assessment-layout', () => {
        const assessmentLayout = fixture.debugElement.query(By.directive(AssessmentLayoutComponent));
        expect(assessmentLayout).to.exist;
    });

    it('should show complaint for result with complaint and check assessor', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);

        expect(getIdentityStub.calledOnce).to.be.true;
        expect(getStudentParticipationWithResultsStub.calledOnce).to.be.true;
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

    it('should not show complaint when result does not have it', fakeAsync(() => {
        result.hasComplaint = false;
        comp.ngOnInit();
        tick(100);

        expect(getIdentityStub.calledOnce).to.be.true;
        expect(getStudentParticipationWithResultsStub.calledOnce).to.be.true;
        expect(findByResultIdStub.notCalled).to.be.true;
        expect(comp.complaint).to.not.exist;
        fixture.detectChanges();

        const complaintsForm = debugElement.query(By.css('jhi-complaints-for-tutor-form'));
        expect(complaintsForm).to.not.exist;

        // Wait until periodic timer has passed out
        tick(100);
    }));

    it('should save and submit manual result', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);
        comp.automaticFeedback = [{ type: FeedbackType.AUTOMATIC, text: 'testCase1', detailText: 'testCase1 failed', credits: 0 }];
        comp.referencedFeedback = [{ type: FeedbackType.MANUAL, text: 'manual feedback', detailText: 'manual feedback for a file:1', credits: 2, reference: 'file:1_line:1' }];
        comp.unreferencedFeedback = [{ type: FeedbackType.MANUAL_UNREFERENCED, detailText: 'unreferenced feedback', credits: 1 }];
        comp.generalFeedback = { detailText: 'general feedback' };
        comp.validateFeedback();
        comp.save();
        const alertElement = debugElement.queryAll(By.css('jhi-alert'));

        expect(comp.manualResult?.feedbacks?.length).to.be.equal(4);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.AUTOMATIC)).to.be.true;
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL)).to.be.true;
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)).to.be.true;
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type !== FeedbackType.MANUAL_UNREFERENCED && feedback.reference == null)).to.be.true;
        expect(alertElement).to.exist;

        // Reset feedbacks
        comp.manualResult!.feedbacks! = [];
        comp.validateFeedback();
        comp.submit();
        const alertElementSubmit = debugElement.queryAll(By.css('jhi-alert'));

        expect(comp.manualResult?.feedbacks?.length).to.be.equal(4);
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.AUTOMATIC)).to.be.true;
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL)).to.be.true;
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)).to.be.true;
        expect(comp.manualResult?.feedbacks!.some((feedback) => feedback.type !== FeedbackType.MANUAL_UNREFERENCED && feedback.reference == null)).to.be.true;
        expect(alertElementSubmit).to.exist;
        flush();
    }));

    it('should cancel the assessment and navigate back', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);
        const navigateBackStub = stub(comp, 'navigateBack');
        global.confirm = () => true;
        const confirmSpy = spy(window, 'confirm');
        comp.cancel();

        expect(confirmSpy).to.be.calledOnce;
        expect(comp.cancelBusy).to.be.true;
        expect(navigateBackStub).to.be.calledOnce;
    }));

    it('should go to next submission', fakeAsync(() => {
        comp.ngOnInit();
        tick(100);
        comp.nextSubmission();
        expect(getProgrammingSubmissionForExerciseWithoutAssessmentStub).to.be.calledOnce;
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
