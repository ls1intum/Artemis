import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../mocks/mock-sync.storage';
import { Result } from 'app/entities/result.model';
import { MockComponent } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { AlertService } from 'app/core/alert/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TextAssessmentComponent } from 'app/exercises/text/assess/text-assessment.component';
import { TextAssessmentEditorComponent } from 'app/exercises/text/assess/text-assessment-editor/text-assessment-editor.component';
import { ResizableInstructionsComponent } from 'app/exercises/text/assess/resizable-instructions/resizable-instructions.component';
import { AssessmentDetailComponent } from 'app/assessment/assessment-detail/assessment-detail.component';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from '../../mocks/mock-account.service';
import { Location } from '@angular/common';
import { textAssessmentRoutes } from 'app/exercises/text/assess/text-assessment.route';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintService } from 'app/complaints/complaint.service';
import { MockComplaintResponse, MockComplaintService } from '../../mocks/mock-complaint.service';
import { TranslateModule } from '@ngx-translate/core';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { ResultComponent } from 'app/shared/result/result.component';
import { SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { MockActivatedRoute } from '../../mocks/mock-activated-route';
import { TextAssessmentsService } from 'app/exercises/text/assess/text-assessments.service';
import { Course } from 'app/entities/course.model';
import { Feedback } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { AssessmentGeneralFeedbackComponent } from 'app/assessment/assessment-general-feedback/assessment-general-feedback.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('TextAssessmentComponent', () => {
    let comp: TextAssessmentComponent;
    let fixture: ComponentFixture<TextAssessmentComponent>;
    let textSubmissionService: TextSubmissionService;
    let assessmentsService: TextAssessmentsService;
    let getTextSubmissionForExerciseWithoutAssessmentStub: SinonStub;
    let getFeedbackDataForExerciseSubmissionStub: SinonStub;
    let saveAssessmentStub: SinonStub;
    let submitAsssessmentStub: SinonStub;
    let getResultWithPredefinedTextblocksStub: SinonStub;
    let updateAssessmentAfterComplaintStub: SinonStub;
    let debugElement: DebugElement;
    let router: Router;
    let location: Location;
    let activatedRouteMock: MockActivatedRoute = new MockActivatedRoute();

    const exercise = { id: 20, type: ExerciseType.TEXT, assessmentType: AssessmentType.MANUAL } as TextExercise;
    const participation: Participation = <Participation>(<unknown>{ type: ParticipationType.STUDENT, exercise: exercise });
    const submission = {
        submissionExerciseType: SubmissionExerciseType.TEXT,
        id: 2278,
        submitted: true,
        type: SubmissionType.MANUAL,
        submissionDate: moment('2019-07-09T10:47:33.244Z'),
        text: 'asdfasdfasdfasdf',
        participation: participation,
    } as TextSubmission;
    const result = ({
        id: 2374,
        resultString: '1 of 12 points',
        completionDate: moment('2019-07-09T11:51:23.251Z'),
        successful: false,
        score: 8,
        rated: true,
        hasFeedback: false,
        hasComplaint: false,
        submission: submission,
        participation: participation,
    } as unknown) as Result;
    submission.result = result;
    submission.participation.submissions = [submission];
    submission.participation.results = [submission.result];
    const refFeedback = { reference: 'reference', credits: 5 } as Feedback;
    const refFeedback2 = { reference: 'reference2', credits: 5 } as Feedback;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule, TranslateModule.forRoot(), RouterTestingModule.withRoutes([textAssessmentRoutes[0]])],
            declarations: [
                TextAssessmentComponent,
                MockComponent(ResultComponent),
                MockComponent(TextAssessmentEditorComponent),
                MockComponent(ResizableInstructionsComponent),
                MockComponent(AssessmentDetailComponent),
                MockComponent(ComplaintsForTutorComponent),
                MockComponent(AssessmentGeneralFeedbackComponent),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: AlertService, useClass: MockAlertService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ComplaintService, useClass: MockComplaintService },
                { provide: ActivatedRoute, useValue: activatedRouteMock },
            ],
        })
            .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextAssessmentComponent);
                comp = fixture.componentInstance;
                comp.exercise = exercise;
                debugElement = fixture.debugElement;

                router = debugElement.injector.get(Router);
                location = debugElement.injector.get(Location);
                textSubmissionService = debugElement.injector.get(TextSubmissionService);
                assessmentsService = debugElement.injector.get(TextAssessmentsService);

                getTextSubmissionForExerciseWithoutAssessmentStub = stub(textSubmissionService, 'getTextSubmissionForExerciseWithoutAssessment');
                getFeedbackDataForExerciseSubmissionStub = stub(assessmentsService, 'getFeedbackDataForExerciseSubmission');
                saveAssessmentStub = stub(assessmentsService, 'save');
                submitAsssessmentStub = stub(assessmentsService, 'submit');
                getResultWithPredefinedTextblocksStub = stub(assessmentsService, 'getResultWithPredefinedTextblocks');
                updateAssessmentAfterComplaintStub = stub(assessmentsService, 'updateAssessmentAfterComplaint');

                router.initialNavigation();
            });
    });

    afterEach(() => {
        getTextSubmissionForExerciseWithoutAssessmentStub.restore();
        getFeedbackDataForExerciseSubmissionStub.restore();
        saveAssessmentStub.restore();
        submitAsssessmentStub.restore();
        getResultWithPredefinedTextblocksStub.restore();
        updateAssessmentAfterComplaintStub.restore();
    });

    it('AssessNextButton should be visible', fakeAsync(() => {
        activatedRouteMock.testParams = { exerciseId: 1, submissionId: 'new' };
        getTextSubmissionForExerciseWithoutAssessmentStub.returns(throwError({ status: 404 }));
        // set all attributes for comp
        comp.ngOnInit();
        tick();

        // not found state is correctly set on the component
        expect(comp.notFound).to.be.true;

        comp.userId = 99;
        comp.submission = submission;
        comp.result = result;
        comp.isAssessor = true;
        comp.isAtLeastInstructor = true;
        comp.assessmentsAreValid = true;
        const unassessedSubmission = { submissionExerciseType: 'text', id: 2279, submitted: true, type: 'MANUAL' };

        fixture.detectChanges();

        // check if assessNextButton is available
        const assessNextButton = debugElement.query(By.css('#assessNextButton'));
        expect(assessNextButton).to.exist;
    }));

    it('Should set the result and participation properly for new submission', fakeAsync(() => {
        activatedRouteMock.testParams = { exerciseId: 1, submissionId: 'new' };
        result.hasComplaint = true;
        getTextSubmissionForExerciseWithoutAssessmentStub.returns(of(submission));
        comp.ngOnInit();
        tick();
        expect(comp.submission).to.be.deep.equal(submission);
        expect(comp.result).to.be.deep.equal(result);
        expect(comp.exercise).to.be.deep.equal(exercise);
        expect(comp.participation).to.be.deep.equal(participation);
        expect(comp.isAtLeastInstructor).to.be.true;
        expect(comp.complaint).to.be.deep.equal(MockComplaintResponse.body);
    }));

    it('Should navigate to tutor dashboard when locked submission limit reached', fakeAsync(() => {
        const course = new Course();
        course.id = 1;
        exercise.course = course;
        comp.exercise = exercise;
        activatedRouteMock.testParams = { exerciseId: 1, submissionId: 'new' };
        getTextSubmissionForExerciseWithoutAssessmentStub.returns(throwError({ error: { errorKey: 'lockedSubmissionsLimitReached' } }));
        const spy = stub(router, 'navigateByUrl');
        spy.returns(new Promise((resolve) => true));
        comp.ngOnInit();
        tick();
        expect(spy.called).to.be.true;
    }));

    it('Should alert error message on error response', fakeAsync(() => {
        const course = new Course();
        course.id = 1;
        exercise.course = course;
        comp.exercise = exercise;
        activatedRouteMock.testParams = { exerciseId: 1, submissionId: 'new' };
        getTextSubmissionForExerciseWithoutAssessmentStub.returns(throwError({ headers: { get: (s: string) => 'error' } }));
        const spy = stub(TestBed.inject(AlertService), 'error');
        spy.returns({ type: 'danger', msg: '' });
        comp.ngOnInit();
        tick();
        expect(spy.called).to.be.true;
    }));

    it('Should set the result and participation properly for existing submission', fakeAsync(() => {
        activatedRouteMock.testParams = { exerciseId: 1, submissionId: 1 };
        getFeedbackDataForExerciseSubmissionStub.returns(of(participation));
        comp.ngOnInit();
        tick();
        expect(comp.exercise).to.be.deep.equal(exercise);
        expect(comp.isAtLeastInstructor).to.be.true;
    }));

    it('Should invalidate assessment without general and referenced feedback', fakeAsync(() => {
        comp.validateAssessment();
        expect(comp.assessmentsAreValid).to.be.false;
        expect(comp.totalScore).to.be.equal(0);
    }));

    it('Should validate assessment with general feedback', fakeAsync(() => {
        const feedback = new Feedback();
        feedback.detailText = 'General Feedback';
        comp.generalFeedback = feedback;
        comp.validateAssessment();
        expect(comp.assessmentsAreValid).to.be.true;
        expect(comp.totalScore).to.be.equal(0);
    }));

    it('Should invalidate assessment with referenced feedback without reference', fakeAsync(() => {
        comp.referencedFeedback = [new Feedback()];
        comp.validateAssessment();
        expect(comp.assessmentsAreValid).to.be.false;
        expect(comp.invalidError).to.be.equal('artemisApp.textAssessment.error.feedbackReferenceTooLong');
    }));

    it('Should invalidate assessment with referenced feedback with undefined credits', fakeAsync(() => {
        const feedback = new Feedback();
        feedback.reference = 'reference';
        comp.referencedFeedback = [feedback];
        comp.validateAssessment();
        expect(comp.assessmentsAreValid).to.be.false;
        expect(comp.invalidError).to.be.equal('artemisApp.textAssessment.error.invalidNeedScoreOrFeedback');
    }));

    it('Should invalidate assessment with referenced feedback without credits', fakeAsync(() => {
        const feedback = new Feedback();
        feedback.reference = 'reference';
        feedback.credits = null as any;
        comp.referencedFeedback = [feedback];
        comp.validateAssessment();
        expect(comp.assessmentsAreValid).to.be.false;
        expect(comp.invalidError).to.be.equal('artemisApp.textAssessment.error.invalidScoreMustBeNumber');
    }));

    it('Should validate assessment with referenced feedback with credits', fakeAsync(() => {
        comp.referencedFeedback = [refFeedback, refFeedback2];
        comp.validateAssessment();
        expect(comp.assessmentsAreValid).to.be.true;
        expect(comp.totalScore).to.be.equal(10);
    }));

    it('Should delete assessment', fakeAsync(() => {
        comp.referencedFeedback = [refFeedback, refFeedback2];
        comp.referencedTextBlocks = [new TextBlock(), new TextBlock()];
        comp.deleteAssessment(refFeedback);
        expect(comp.referencedFeedback.length).to.be.equal(1);
        expect(comp.referencedFeedback).to.contain(refFeedback2);
        expect(comp.referencedTextBlocks.length).to.be.equal(1);
        expect(comp.totalScore).to.be.equal(5);
        expect(comp.assessmentsAreValid).to.be.true;
    }));

    it('Should add assessment', fakeAsync(() => {
        const assessmentText = 'new assessment';
        comp.referencedTextBlocks = [];
        comp.addAssessment(assessmentText);
        expect(comp.referencedFeedback.length).to.be.equal(1);
        expect(comp.referencedFeedback[0].reference).to.be.equal(assessmentText);
        expect(comp.referencedFeedback[0].credits).to.be.equal(0);
        expect(comp.referencedTextBlocks.length).to.be.equal(1);
    }));

    it('Should not save invalid assessments', fakeAsync(() => {
        comp.save();
        expect(saveAssessmentStub.called).to.be.false;
    }));

    it('Should handle error on save assessment', fakeAsync(() => {
        comp.referencedFeedback = [refFeedback];
        comp.result = { id: 1 } as any;
        saveAssessmentStub.returns(throwError({ headers: { get: (s: string) => 'error' } }));
        const spy = stub(TestBed.inject(AlertService), 'error');
        spy.returns({ type: 'danger', msg: '' });
        comp.save();
        tick();
        expect(saveAssessmentStub.called).to.be.true;
    }));

    it('Should save valid assessments', fakeAsync(() => {
        comp.referencedFeedback = [refFeedback];
        comp.result = { id: 1 } as any;
        participation.results = [];
        comp.participation = participation;
        saveAssessmentStub.returns(of({ body: result }));
        comp.save();
        expect(saveAssessmentStub.called).to.be.true;
        expect(comp.result).to.be.equal(result);
        expect(comp.participation.results[0]).to.be.equal(result);
        expect(comp.showResult).to.be.true;
    }));

    it('Should submit valid assessments', fakeAsync(() => {
        comp.referencedFeedback = [refFeedback];
        comp.result = { id: 1 } as any;
        participation.results = [];
        comp.participation = participation;
        submitAsssessmentStub.returns(of({ body: result }));
        comp.submit();
        expect(submitAsssessmentStub.called).to.be.true;
        expect(comp.result).to.be.equal(result);
        expect(comp.participation.results[0]).to.be.equal(result);
        expect(comp.showResult).to.be.true;
    }));

    it('Should not submit without already existing result', fakeAsync(() => {
        comp.result = {} as any;
        submitAsssessmentStub.returns(of({ body: result }));
        comp.submit();
        expect(submitAsssessmentStub.called).to.be.false;
    }));

    it('Should not submit without valid assessments', fakeAsync(() => {
        comp.result = { id: 1 } as any;
        submitAsssessmentStub.returns(of({ body: result }));
        comp.submit();
        expect(submitAsssessmentStub.called).to.be.false;
    }));

    it('Should set predefined text blocks', fakeAsync(() => {
        submission.blocks = [{ id: '1' } as TextBlock];
        comp.submission = submission;
        const generalFeedback = { id: 1, detailText: 'general' } as Feedback;
        const referencedFeedback = { id: 2, referenceId: '1' } as Feedback;
        comp.result = { id: 1 } as Result;
        getResultWithPredefinedTextblocksStub.returns(of({ body: { submission, feedbacks: [generalFeedback, referencedFeedback] } }));
        comp.predefineTextBlocks();
        tick();
        expect(comp.submission.blocks?.length).to.be.equal(1);
        expect(comp.generalFeedback).to.be.equal(generalFeedback);
        expect(comp.referencedFeedback.length).to.be.equal(1);
        expect(comp.referencedFeedback).to.contain(referencedFeedback);
        expect(comp.referencedTextBlocks.length).to.be.equal(1);
    }));

    it('Should update assessment after complaint', fakeAsync(() => {
        comp.referencedFeedback = [refFeedback];
        comp.submission = { id: 2 } as any;
        participation.results = [];
        comp.participation = participation;
        updateAssessmentAfterComplaintStub.returns(of({ body: result }));
        comp.onUpdateAssessmentAfterComplaint(new ComplaintResponse());
        expect(updateAssessmentAfterComplaintStub.called).to.be.true;
        expect(comp.result).to.be.equal(result);
        expect(comp.participation.results[0]).to.be.equal(result);
        expect(comp.showResult).to.be.true;
    }));

    it('Should not update assessment after complaint when assessments not valid', fakeAsync(() => {
        updateAssessmentAfterComplaintStub.returns(of({ body: result }));
        comp.onUpdateAssessmentAfterComplaint(new ComplaintResponse());
        expect(updateAssessmentAfterComplaintStub.called).to.be.false;
        expect(comp.result).to.be.undefined;
    }));

    it('Should handle error on update assessment after complaint', fakeAsync(() => {
        comp.referencedFeedback = [refFeedback];
        comp.submission = { id: 2 } as any;
        participation.results = [];
        updateAssessmentAfterComplaintStub.returns(throwError({}));
        comp.onUpdateAssessmentAfterComplaint(new ComplaintResponse());
        expect(updateAssessmentAfterComplaintStub.called).to.be.true;
        expect(comp.result).to.be.undefined;
    }));
});
