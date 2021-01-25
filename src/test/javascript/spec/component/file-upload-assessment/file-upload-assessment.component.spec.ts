import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { of, throwError } from 'rxjs';
import { cloneDeep } from 'lodash';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as moment from 'moment';
import { SinonStub, stub } from 'sinon';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';

import { Router } from '@angular/router';
import { FileUploadAssessmentComponent } from 'app/exercises/file-upload/assess/file-upload-assessment.component';
import { DebugElement } from '@angular/core';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintService, EntityResponseType } from 'app/complaints/complaint.service';
import { MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';
import { ArtemisAssessmentSharedModule } from 'app/assessment/assessment-shared.module';
import { TranslateModule } from '@ngx-translate/core';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadAssessmentsService } from 'app/exercises/file-upload/assess/file-upload-assessment.service';
import { ComplaintsForTutorComponent } from 'app/complaints/complaints-for-tutor/complaints-for-tutor.component';
import { UpdatingResultComponent } from 'app/exercises/shared/result/updating-result.component';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { getFirstResult, setLatestSubmissionResult, SubmissionExerciseType, SubmissionType } from 'app/entities/submission.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Result } from 'app/entities/result.model';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { routes } from 'app/exercises/file-upload/assess/file-upload-assessment.route';
import { By } from '@angular/platform-browser';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { AssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { Complaint } from 'app/entities/complaint.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';

chai.use(sinonChai);

const expect = chai.expect;

describe('FileUploadAssessmentComponent', () => {
    let comp: FileUploadAssessmentComponent;
    let fixture: ComponentFixture<FileUploadAssessmentComponent>;
    let fileUploadSubmissionService: FileUploadSubmissionService;
    let fileUploadAssessmentsService: FileUploadAssessmentsService;
    let accountService: AccountService;
    let complaintService: ComplaintService;
    let getFileUploadSubmissionForExerciseWithoutAssessmentStub: SinonStub;
    let debugElement: DebugElement;
    let router: Router;
    const activatedRouteMock: MockActivatedRoute = new MockActivatedRoute();

    const exercise = { id: 20, type: ExerciseType.FILE_UPLOAD, maxScore: 100, bonusPoints: 0 } as FileUploadExercise;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [
                ArtemisTestModule,
                ArtemisSharedModule,
                RouterTestingModule.withRoutes([routes[0]]),
                ArtemisAssessmentSharedModule,
                ModelingAssessmentModule,
                TranslateModule.forRoot(),
            ],
            declarations: [
                FileUploadAssessmentComponent,
                MockComponent(UpdatingResultComponent),
                MockComponent(CollapsableAssessmentInstructionsComponent),
                MockComponent(ComplaintsForTutorComponent),
                MockComponent(AssessmentInstructionsComponent),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: AccountService, useClass: MockAccountService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ComplaintService, useClass: MockComplaintService },
            ],
        })
            .overrideModule(ArtemisTestModule, {
                remove: {
                    declarations: [MockComponent(FaIconComponent)],
                    exports: [MockComponent(FaIconComponent)],
                },
            })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(FileUploadAssessmentComponent);
                comp = fixture.componentInstance;
                comp.userId = 1;
                comp.exercise = exercise;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                fileUploadSubmissionService = TestBed.inject(FileUploadSubmissionService);
                // The TestBed only knows about it's providers and the component has it's own injector, so the component's service needs to be injected by
                // getting the injector.
                fileUploadAssessmentsService = fixture.componentRef.injector.get(FileUploadAssessmentsService);
                accountService = TestBed.inject(AccountService);
                complaintService = TestBed.inject(ComplaintService);
                getFileUploadSubmissionForExerciseWithoutAssessmentStub = stub(
                    fileUploadSubmissionService,
                    'getFileUploadSubmissionForExerciseForCorrectionRoundWithoutAssessment',
                );
                stub(accountService, 'isAtLeastInstructorInCourse').returns(false);

                fixture.ngZone!.run(() => {
                    router.initialNavigation();
                });
            });
    });

    afterEach(() => {
        getFileUploadSubmissionForExerciseWithoutAssessmentStub.restore();
    });

    it('AssessNextButton should be visible', () => {
        activatedRouteMock.testParams = { exerciseId: 1, submissionId: 'new' };
        getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(throwError({ status: 404 }));

        // set all attributes for comp
        comp.ngOnInit();

        comp.userId = 99;
        comp.submission = createSubmission(exercise);
        comp.result = createResult(comp.submission);
        setLatestSubmissionResult(comp.submission, comp.result);
        comp.submission.participation!.submissions = [comp.submission];
        comp.submission.participation!.results = [comp.submission.latestResult!];
        comp.isAssessor = true;
        comp.isAtLeastInstructor = true;
        comp.assessmentsAreValid = true;
        comp.isLoading = false;

        fixture.detectChanges();

        expect(getFirstResult(comp.submission)).to.equal(comp.result);
        const assessNextButton = debugElement.query(By.css('#assessNextButton'));
        expect(assessNextButton).to.exist;
    });

    it('should load submission', () => {
        const submission = createSubmission(exercise);
        const result = createResult(submission);
        result.hasComplaint = true;
        const complaint = new Complaint();
        complaint.id = 0;
        complaint.complaintText = 'complaint';
        complaint.resultBeforeComplaint = 'result';
        stub(fileUploadSubmissionService, 'get').returns(of({ body: submission } as EntityResponseType));
        stub(complaintService, 'findByResultId').returns(of({ body: complaint } as EntityResponseType));
        comp.submission = submission;
        setLatestSubmissionResult(comp.submission, result);

        fixture.detectChanges();
        expect(comp.result).to.equal(result);
        expect(comp.submission).to.equal(submission);
        expect(comp.complaint).to.equal(complaint);
        expect(comp.result.feedbacks?.length === 0).to.equal(true);
        expect(comp.busy).to.be.false;
    });

    it('should load correct feedbacks and update general feedback', () => {
        const submission = createSubmission(exercise);
        const result = createResult(submission);
        result.hasFeedback = true;
        const feedback1 = new Feedback();
        feedback1.type = FeedbackType.AUTOMATIC;
        const feedback2 = new Feedback();
        feedback2.credits = 10;
        feedback2.type = FeedbackType.AUTOMATIC;
        const feedbacks = [feedback1, feedback2];
        result.feedbacks = cloneDeep(feedbacks);
        stub(fileUploadSubmissionService, 'get').returns(of({ body: submission } as EntityResponseType));
        comp.submission = submission;
        setLatestSubmissionResult(comp.submission, result);

        fixture.detectChanges();
        expect(comp.generalFeedback).to.deep.equal(feedbacks[0]);
        expect(comp.referencedFeedback.length).to.equal(1);
        expect(comp.busy).to.be.false;
        expect(comp.totalScore).to.equal(10);

        // delete feedback
        comp.deleteAssessment(comp.referencedFeedback[0]);
        expect(comp.referencedFeedback.length).to.equal(0);
        expect(comp.totalScore).to.equal(0);
    });

    it('should add a feedback', () => {
        expect(comp.referencedFeedback.length).to.equal(0);
        comp.addFeedback();
        expect(comp.referencedFeedback.length).to.equal(1);
        expect(comp.totalScore).to.equal(0);
    });

    it('should delete a feedback', () => {
        expect(comp.referencedFeedback.length).to.equal(0);
        comp.addFeedback();
        expect(comp.referencedFeedback.length).to.equal(1);
        expect(comp.totalScore).to.equal(0);
    });

    it('should update assessment correctly', () => {
        const feedback = new Feedback();
        feedback.credits = 20;
        feedback.type = FeedbackType.AUTOMATIC;
        const feedback2 = new Feedback();
        feedback2.credits = 85;
        feedback2.type = FeedbackType.AUTOMATIC;
        comp.referencedFeedback = [feedback, feedback2];
        comp.updateAssessment();
        expect(comp.totalScore).to.equal(100);
    });

    it('should save the assessment', () => {
        const submission = createSubmission(exercise);
        // initial result
        const initResult = createResult(submission);
        initResult.assessmentType = AssessmentType.AUTOMATIC;
        // changed result
        const feedback = new Feedback();
        feedback.credits = 10;
        feedback.type = FeedbackType.AUTOMATIC;
        const changedResult = cloneDeep(initResult);
        changedResult.feedbacks = [feedback];
        changedResult.hasFeedback = true;
        stub(fileUploadSubmissionService, 'get').returns(of({ body: submission } as EntityResponseType));
        stub(fileUploadAssessmentsService, 'saveAssessment').returns(of(changedResult));
        comp.submission = submission;
        setLatestSubmissionResult(comp.submission, initResult);

        fixture.detectChanges();
        comp.onSaveAssessment();
        expect(comp.result).to.equal(changedResult);
        expect(comp.isLoading).to.be.false;
    });

    it('should update the assessment after submit', () => {
        const submission = createSubmission(exercise);
        const feedback = new Feedback();
        feedback.credits = 20;
        feedback.type = FeedbackType.AUTOMATIC;
        // initial result
        const initResult = createResult(submission);
        initResult.assessmentType = AssessmentType.AUTOMATIC;
        initResult.hasFeedback = true;
        initResult.feedbacks = [feedback];
        // changed result
        const changedResult = cloneDeep(initResult);
        changedResult.feedbacks = [feedback, feedback];
        stub(fileUploadSubmissionService, 'get').returns(of({ body: submission } as EntityResponseType));
        stub(fileUploadAssessmentsService, 'saveAssessment').returns(of(changedResult));
        comp.submission = submission;
        setLatestSubmissionResult(comp.submission, initResult);

        fixture.detectChanges();
        comp.onSubmitAssessment();

        expect(comp.isLoading).to.be.false;
        expect(comp.result).to.equal(changedResult);
        expect(comp.participation.results![0]).to.equal(changedResult);
    });

    it('should update assessment after complaint', () => {
        const submission = createSubmission(exercise);
        const feedback = new Feedback();
        feedback.credits = 10;
        feedback.type = FeedbackType.AUTOMATIC;
        // initial result
        const initResult = createResult(submission);
        initResult.assessmentType = AssessmentType.AUTOMATIC;
        initResult.hasFeedback = true;
        initResult.feedbacks = [feedback];
        // changed result
        const changedResult = cloneDeep(initResult);
        changedResult.feedbacks = [feedback, feedback];
        stub(fileUploadSubmissionService, 'get').returns(of({ body: submission } as EntityResponseType));
        stub(fileUploadAssessmentsService, 'updateAssessmentAfterComplaint').returns(of({ body: changedResult } as EntityResponseType));
        comp.submission = submission;
        setLatestSubmissionResult(comp.submission, initResult);

        fixture.detectChanges();
        const complaintResponse = new ComplaintResponse();
        comp.onUpdateAssessmentAfterComplaint(complaintResponse);
        expect(comp.isLoading).to.be.false;
        expect(comp.result).to.equal(changedResult);
        expect(comp.participation.results![0]).to.equal(changedResult);
    });
});

const createSubmission = (exercise: FileUploadExercise) => {
    return {
        submissionExerciseType: SubmissionExerciseType.FILE_UPLOAD,
        id: 2278,
        submitted: true,
        type: SubmissionType.MANUAL,
        submissionDate: moment('2019-07-09T10:47:33.244Z'),
        participation: ({ type: ParticipationType.STUDENT, exercise } as unknown) as Participation,
    } as FileUploadSubmission;
};

const createResult = (submission: FileUploadSubmission) => {
    const result = new Result();
    result.id = 2374;
    result.resultString = '1 of 12 points';
    result.completionDate = moment('2019-07-09T11:51:23.251Z');
    result.successful = false;
    result.score = 1;
    result.rated = true;
    result.hasFeedback = false;
    result.submission = submission;
    result.participation = undefined;
    result.assessmentType = AssessmentType.MANUAL;
    result.exampleResult = false;
    result.hasComplaint = false;
    return result;
};
