import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { of, Subject, throwError } from 'rxjs';
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

import { ActivatedRoute, convertToParamMap, Params, Router } from '@angular/router';
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
import * as sinon from 'sinon';
import { HttpErrorResponse } from '@angular/common/http';
import { JhiAlertService } from 'ng-jhipster';

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
    let navigateByUrlStub: SinonStub;
    let alertService: JhiAlertService;
    let route: ActivatedRoute;

    const exercise = { id: 20, type: ExerciseType.FILE_UPLOAD, maxPoints: 100, bonusPoints: 0 } as FileUploadExercise;
    const map1 = new Map<string, Object>().set('testRun', true).set('correction-round', 1);
    const params1 = { exerciseId: 20, courseId: 123, submissionId: 7 };
    const params2 = { exerciseId: 20, courseId: 123, submissionId: 'new' };

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
                { provide: ActivatedRoute, useValue: { queryParamMap: of(map1), params: of(params1) } },
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
                alertService = TestBed.inject(JhiAlertService);
                getFileUploadSubmissionForExerciseWithoutAssessmentStub = stub(
                    fileUploadSubmissionService,
                    'getFileUploadSubmissionForExerciseForCorrectionRoundWithoutAssessment',
                );
                stub(accountService, 'isAtLeastInstructorInCourse').returns(false);
                navigateByUrlStub = stub(router, 'navigateByUrl');
                fixture.ngZone!.run(() => {
                    router.initialNavigation();
                });
            });
    });

    afterEach(() => {
        getFileUploadSubmissionForExerciseWithoutAssessmentStub.restore();
    });

    describe('ngOnInit', () => {
        it('AssessNextButton should be visible', fakeAsync(() => {
            getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(throwError({ status: 404 }));

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
        }));

        it('should get correctionRound', fakeAsync(() => {
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
            expect(comp.correctionRound).to.be.equal(1);
        }));
    });
    describe('loadSubmission', () => {
        it('should load submission', fakeAsync(() => {
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
        }));

        it('should load optimal submission', () => {
            let activatedRoute: ActivatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
            activatedRoute.params = of(params2);
            TestBed.inject(ActivatedRoute);
            const submission = createSubmission(exercise);
            const result = createResult(submission);
            getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(of(submission));
            stub(fileUploadSubmissionService, 'get').returns(of({ body: submission } as EntityResponseType));
            // stub(complaintService, 'findByResultId').returns(of({ body: complaint } as EntityResponseType));

            fixture.detectChanges();
            expect(comp.result).to.not.equal(result);
            expect(comp.submission).to.equal(submission);
            expect(comp.busy).to.be.true;
        });

        it('should get 404 error when loading optimal submission', () => {
            navigateByUrlStub.returns(Promise.resolve(true));
            let activatedRoute: ActivatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
            activatedRoute.params = of(params2);
            TestBed.inject(ActivatedRoute);
            getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(throwError({ status: 404 }));
            fixture.detectChanges();
            expect(navigateByUrlStub).to.have.been.called;
            expect(comp.busy).to.be.true;
        });
        it('should get lock limit reached error when loading optimal submission', () => {
            navigateByUrlStub.returns(Promise.resolve(true));
            let activatedRoute: ActivatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
            activatedRoute.params = of(params2);
            TestBed.inject(ActivatedRoute);
            getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(throwError({ error: { errorKey: 'lockedSubmissionsLimitReached' } }));
            fixture.detectChanges();
            expect(navigateByUrlStub).to.have.been.called;
            expect(comp.busy).to.be.true;
        });
        it('should fail to load optimal submission', () => {
            navigateByUrlStub.returns(Promise.resolve(true));
            let activatedRoute: ActivatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
            activatedRoute.params = of(params2);
            TestBed.inject(ActivatedRoute);
            getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(throwError({ status: 403 }));
            fixture.detectChanges();
            expect(navigateByUrlStub).to.have.been.called;
            expect(comp.busy).to.be.true;
        });
    });

    it('should load correct feedbacks and update general feedback', () => {
        const submission = createSubmission(exercise);
        const result = createResult(submission);
        result.hasFeedback = true;
        const feedback1 = new Feedback();
        feedback1.type = FeedbackType.MANUAL_UNREFERENCED;
        feedback1.credits = 5;
        feedback1.detailText = 'Unreferenced Feedback 1';
        const feedback2 = new Feedback();
        feedback2.credits = 10;
        feedback2.type = FeedbackType.MANUAL_UNREFERENCED;
        feedback2.detailText = 'Unreferenced Feedback 2';
        const feedbacks = [feedback1, feedback2];
        result.feedbacks = cloneDeep(feedbacks);
        stub(fileUploadSubmissionService, 'get').returns(of({ body: submission } as EntityResponseType));
        comp.submission = submission;
        setLatestSubmissionResult(comp.submission, result);

        fixture.detectChanges();
        expect(comp.unreferencedFeedback.length).to.equal(2);
        expect(comp.busy).to.be.false;
        expect(comp.totalScore).to.equal(15);

        // delete feedback
        comp.deleteAssessment(comp.unreferencedFeedback[0]);
        expect(comp.unreferencedFeedback.length).to.equal(1);
        expect(comp.totalScore).to.equal(10);

        comp.deleteAssessment(comp.unreferencedFeedback[0]);
        expect(comp.unreferencedFeedback.length).to.equal(0);
        expect(comp.totalScore).to.equal(0);
    });

    it('should add a feedback', () => {
        expect(comp.unreferencedFeedback.length).to.equal(0);
        comp.addFeedback();
        expect(comp.unreferencedFeedback.length).to.equal(1);
        expect(comp.totalScore).to.equal(0);
    });

    it('should delete a feedback', () => {
        expect(comp.unreferencedFeedback.length).to.equal(0);
        comp.addFeedback();
        expect(comp.unreferencedFeedback.length).to.equal(1);
        expect(comp.totalScore).to.equal(0);
    });

    it('should update assessment correctly', () => {
        const feedback = new Feedback();
        feedback.credits = 20;
        feedback.type = FeedbackType.AUTOMATIC;
        const feedback2 = new Feedback();
        feedback2.credits = 85;
        feedback2.type = FeedbackType.AUTOMATIC;
        comp.unreferencedFeedback = [feedback, feedback2];
        comp.updateAssessment();
        expect(comp.totalScore).to.equal(100);
    });
    describe('onSaveAssessment', () => {
        it('should save the assessment', () => {
            const submission = createSubmission(exercise);
            // initial result
            const initResult = createResult(submission);
            initResult.assessmentType = AssessmentType.AUTOMATIC;
            // changed result
            const feedback = new Feedback();
            feedback.credits = 10;
            feedback.type = FeedbackType.MANUAL;
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

        it('should not save the assessment if error', () => {
            //errorResponse
            const errorResponse = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
            const alertServiceErrorSpy = sinon.spy(alertService, 'error');
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
            stub(fileUploadAssessmentsService, 'saveAssessment').returns(throwError(errorResponse));
            comp.submission = submission;
            setLatestSubmissionResult(comp.submission, initResult);

            fixture.detectChanges();
            comp.onSaveAssessment();
            sinon.assert.calledOnceWithExactly(alertServiceErrorSpy, 'artemisApp.assessment.messages.saveFailed');
            expect(comp.isLoading).to.be.false;
        });
    });

    it('should update the assessment after submit', () => {
        const submission = createSubmission(exercise);
        const feedback = new Feedback();
        feedback.credits = 20;
        feedback.detailText = 'Feedback 1';
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
        // initial result
        const initResult = createResult(submission);
        initResult.assessmentType = AssessmentType.MANUAL;
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
    describe('onUpdateAssessmentAfterComplaint', () => {
        it('should update assessment after complaint', () => {
            const submission = createSubmission(exercise);
            const feedback = new Feedback();
            feedback.credits = 10;
            feedback.detailText = 'Feedback 1';
            feedback.type = FeedbackType.MANUAL_UNREFERENCED;
            // initial result
            const initResult = createResult(submission);
            initResult.assessmentType = AssessmentType.MANUAL;
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
        it('should not update assessment after complaint if already locked', () => {
            const alertServiceErrorSpy = sinon.spy(alertService, 'error');
            const errorResponse = new HttpErrorResponse({
                error: {
                    errorKey: 'complaintLock',
                    message: 'errormessage',
                    params: [],
                },
                status: 403,
            });
            const submission = createSubmission(exercise);
            const feedback = new Feedback();
            feedback.credits = 10;
            feedback.detailText = 'Feedback 1';
            feedback.type = FeedbackType.MANUAL_UNREFERENCED;
            // initial result
            const initResult = createResult(submission);
            initResult.assessmentType = AssessmentType.MANUAL;
            initResult.hasFeedback = true;
            initResult.feedbacks = [feedback];
            // changed result
            const changedResult = cloneDeep(initResult);
            changedResult.feedbacks = [feedback, feedback];
            stub(fileUploadSubmissionService, 'get').returns(of({ body: submission } as EntityResponseType));
            stub(fileUploadAssessmentsService, 'updateAssessmentAfterComplaint').returns(throwError(errorResponse));
            comp.submission = submission;
            setLatestSubmissionResult(comp.submission, initResult);

            fixture.detectChanges();
            const complaintResponse = new ComplaintResponse();
            comp.onUpdateAssessmentAfterComplaint(complaintResponse);
            expect(comp.isLoading).to.be.false;
            sinon.assert.calledOnceWithExactly(alertServiceErrorSpy, 'errormessage', []);
        });
        it('should not update assessment after complaint', () => {
            const alertServiceErrorSpy = sinon.spy(alertService, 'error');
            const errorResponse = new HttpErrorResponse({
                error: {
                    errorKey: 'notFound',
                    message: 'errormessage',
                    params: [],
                },
                status: 404,
            });
            const submission = createSubmission(exercise);
            const feedback = new Feedback();
            feedback.credits = 10;
            feedback.detailText = 'Feedback 1';
            feedback.type = FeedbackType.MANUAL_UNREFERENCED;
            // initial result
            const initResult = createResult(submission);
            feedback.type = FeedbackType.MANUAL_UNREFERENCED;
            initResult.hasFeedback = true;
            initResult.feedbacks = [feedback];
            // changed result
            const changedResult = cloneDeep(initResult);
            changedResult.feedbacks = [feedback, feedback];
            stub(fileUploadSubmissionService, 'get').returns(of({ body: submission } as EntityResponseType));
            stub(fileUploadAssessmentsService, 'updateAssessmentAfterComplaint').returns(throwError(errorResponse));
            comp.submission = submission;
            setLatestSubmissionResult(comp.submission, initResult);

            fixture.detectChanges();
            const complaintResponse = new ComplaintResponse();
            comp.onUpdateAssessmentAfterComplaint(complaintResponse);
            expect(comp.isLoading).to.be.false;
            sinon.assert.calledOnceWithExactly(alertServiceErrorSpy, 'artemisApp.assessment.messages.updateAfterComplaintFailed');
        });
    });

    describe('attachmentExtension', () => {
        it('should get file extension', () => {
            expect(comp.attachmentExtension('this/is/a/filepath/file.png')).to.equal('png');
        });

        it('should get N/A if filepath is empty', () => {
            expect(comp.attachmentExtension('')).to.equal('N/A');
        });
    });

    describe('assessNexdt', () => {
        it('should assess next result if there is one', () => {
            const returnedSubmission = createSubmission(exercise);
            getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(of(returnedSubmission));
            comp.courseId = 77;
            navigateByUrlStub.returns(Promise.resolve(true));
            comp.assessNext();
            expect(returnedSubmission.id).to.be.not.undefined;
            expect(navigateByUrlStub).to.have.been.calledTwice;
            // the first call comes from the initial navigation in the beforeEach
            sinon.assert.calledWith(navigateByUrlStub.firstCall, ``, { replaceUrl: true });
            sinon.assert.calledWith(
                navigateByUrlStub.secondCall,
                `/course-management/${comp.courseId}/file-upload-exercises/${comp.exercise!.id}/submissions/${returnedSubmission.id}/assessment`,
                {},
            );
            expect(getFileUploadSubmissionForExerciseWithoutAssessmentStub).to.have.been.called;
        });
        it('should alert when no next result is found', () => {
            const alertServiceSpy = sinon.spy(alertService, 'error');
            const errorResponse = new HttpErrorResponse({ error: 'Not Found', status: 404 });
            getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(throwError(errorResponse));
            comp.courseId = 77;
            navigateByUrlStub.returns(Promise.resolve(true));
            comp.assessNext();
            expect(navigateByUrlStub).to.have.been.calledOnce;
            // the first call comes from the initial navigation in the beforeEach
            sinon.assert.calledWith(navigateByUrlStub.firstCall, ``, { replaceUrl: true });
            expect(getFileUploadSubmissionForExerciseWithoutAssessmentStub).to.have.been.called;
            expect(alertServiceSpy).to.have.been.calledOnce;
        });
        it('should alert when assess next is forbidden', () => {
            const alertServiceSpy = sinon.spy(alertService, 'error');
            const errorResponse = new HttpErrorResponse({ error: 'Forbidden', status: 403 });
            getFileUploadSubmissionForExerciseWithoutAssessmentStub.returns(throwError(errorResponse));
            comp.courseId = 77;
            navigateByUrlStub.returns(Promise.resolve(true));
            comp.assessNext();
            expect(navigateByUrlStub).to.have.been.calledOnce;
            // the first call comes from the initial navigation in the beforeEach
            sinon.assert.calledWith(navigateByUrlStub.firstCall, ``, { replaceUrl: true });
            expect(getFileUploadSubmissionForExerciseWithoutAssessmentStub).to.have.been.called;
            expect(alertServiceSpy).to.have.been.calledOnce;
        });
    });
    describe('canOverride', () => {
        it('should not be able to override if tutor is assessor and result has a complaint', () => {
            comp.isAtLeastInstructor = false;
            comp.complaint = { id: 3 };
            comp.isAssessor = true;
            expect(comp.canOverride).to.be.equal(false);
        });
        it('should not be able to override if tutor is assessor and result has a complaint', () => {
            comp.isAtLeastInstructor = false;
            comp.exercise!.assessmentDueDate = moment().add(-100, 'seconds');
            expect(comp.canOverride).to.be.equal(false);
        });
        it('should not be able to override if exercise is undefined', () => {
            comp.exercise = undefined;
            expect(comp.canOverride).to.be.equal(false);
        });
    });
    describe('getComplaint', () => {
        it('should get Complaint', () => {
            const submission = createSubmission(exercise);
            comp.result = createResult(comp.submission);
            comp.result.hasComplaint = true;
            const complaint = new Complaint();
            complaint.id = 0;
            complaint.complaintText = 'complaint';
            complaint.resultBeforeComplaint = 'result';
            stub(complaintService, 'findByResultId').returns(of({ body: complaint } as EntityResponseType));
            expect(comp.complaint).to.be.undefined;
            comp.getComplaint();
            expect(comp.complaint).to.be.equal(complaint);
        });

        it('should get empty Complaint', () => {
            comp.result = createResult(comp.submission);
            stub(complaintService, 'findByResultId').returns(of({} as EntityResponseType));
            expect(comp.complaint).to.be.undefined;
            comp.getComplaint();
            expect(comp.complaint).to.be.undefined;
        });
        it('should get error', () => {
            comp.result = createResult(comp.submission);
            const alertServiceSpy = sinon.spy(alertService, 'error');
            const errorResponse = new HttpErrorResponse({ error: { message: 'Forbidden' }, status: 403 });
            stub(complaintService, 'findByResultId').returns(throwError(errorResponse));
            comp.getComplaint();
            expect(alertServiceSpy).to.have.been.called;
        });
    });

    it('should cancel the current assessment', () => {
        const windowFake = sinon.fake.returns(true);
        const cancelAssessmentStub = stub(fileUploadAssessmentsService, 'cancelAssessment').returns(of());
        sinon.replace(window, 'confirm', windowFake);

        comp.submission = ({
            id: 2,
        } as unknown) as FileUploadSubmission;
        fixture.detectChanges();

        comp.onCancelAssessment();
        expect(windowFake).to.have.been.calledOnce;
        expect(cancelAssessmentStub).to.have.been.calledOnce;
        expect(comp.isLoading).to.be.false;
    });
    it('should navigate back', () => {
        comp.submission = createSubmission(exercise);
        navigateByUrlStub.returns(Promise.resolve(true));
        comp.navigateBack();
        expect(navigateByUrlStub).to.have.been.called;
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
