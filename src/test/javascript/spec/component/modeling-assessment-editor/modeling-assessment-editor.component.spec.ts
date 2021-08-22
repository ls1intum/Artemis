import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { AssessmentHeaderComponent } from 'app/assessment/assessment-header/assessment-header.component';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { ComplaintService } from 'app/complaints/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { User } from 'app/core/user/user.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Complaint } from 'app/entities/complaint.model';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exercise } from 'app/entities/exercise.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Result } from 'app/entities/result.model';
import { getLatestSubmissionResult } from 'app/entities/submission.model';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import * as chai from 'chai';
import * as moment from 'moment';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { mockedActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { MockComponent } from 'ng-mocks';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { UnreferencedFeedbackComponent } from 'app/exercises/shared/unreferenced-feedback/unreferenced-feedback.component';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('ModelingAssessmentEditorComponent', () => {
    let component: ModelingAssessmentEditorComponent;
    let fixture: ComponentFixture<ModelingAssessmentEditorComponent>;
    let service: ModelingAssessmentService;
    let mockAuth: MockAccountService;
    let modelingSubmissionService: ModelingSubmissionService;
    let complaintService: ComplaintService;
    let modelingSubmissionStub: SinonStub;
    let complaintStub: SinonStub;
    let router: any;
    let submissionService: SubmissionService;
    let exampleSubmissionService: ExampleSubmissionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule],
            declarations: [
                ModelingAssessmentEditorComponent,
                MockComponent(AssessmentLayoutComponent),
                MockComponent(ModelingAssessmentComponent),
                MockComponent(CollapsableAssessmentInstructionsComponent),
                MockComponent(UnreferencedFeedbackComponent),
            ],
            providers: [
                JhiLanguageHelper,
                mockedActivatedRoute({}, { showBackButton: 'false', submissionId: 'new', exerciseId: 1 }),
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingAssessmentEditorComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(ModelingAssessmentService);
                modelingSubmissionService = TestBed.inject(ModelingSubmissionService);
                complaintService = TestBed.inject(ComplaintService);
                router = TestBed.inject(Router);
                submissionService = TestBed.inject(SubmissionService);
                mockAuth = fixture.debugElement.injector.get(AccountService) as any as MockAccountService;
                exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
                mockAuth.hasAnyAuthorityDirect([]);
                mockAuth.identity();
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        sinon.restore();
    });
    describe('ngOnInit tests', () => {
        it('ngOnInit', fakeAsync(() => {
            modelingSubmissionStub = stub(modelingSubmissionService, 'getSubmission');
            complaintStub = stub(complaintService, 'findByResultId');
            const submission = {
                id: 1,
                submitted: true,
                type: 'MANUAL',
                text: 'Test\n\nTest\n\nTest',
                participation: {
                    type: ParticipationType.SOLUTION,
                    exercise: {
                        id: 1,
                        problemStatement: 'problemo',
                        gradingInstructions: 'grading',
                        title: 'title',
                        shortName: 'name',
                        exerciseGroup: {
                            exam: {
                                course: new Course(),
                            } as unknown as Exam,
                        } as unknown as ExerciseGroup,
                    } as unknown as Exercise,
                } as unknown as Participation,
                results: [
                    {
                        id: 2374,
                        resultString: '1 of 12 points',
                        score: 8,
                        rated: true,
                        hasFeedback: true,
                        hasComplaint: true,
                        feedbacks: [
                            {
                                id: 2,
                                detailText: 'Feedback',
                                credits: 1,
                            } as Feedback,
                        ],
                    } as unknown as Result,
                ],
            } as unknown as ModelingSubmission;

            modelingSubmissionStub.returns(of(submission));
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
            const complaint = <Complaint>{ id: 1, complaintText: 'Why only 80%?', result };
            complaintStub.returns(of({ body: complaint } as HttpResponse<Complaint>));

            const handleFeedbackStub = stub(submissionService, 'handleFeedbackCorrectionRoundTag');

            component.ngOnInit();
            tick(500);
            expect(modelingSubmissionStub).to.have.been.calledOnce;
            expect(component.isLoading).to.be.false;
            expect(component.complaint).to.deep.equal(complaint);
            modelingSubmissionStub.restore();
            expect(handleFeedbackStub).to.have.been.called;
        }));

        it('wrongly call ngOnInit and throw exception', fakeAsync(() => {
            modelingSubmissionStub = stub(modelingSubmissionService, 'getSubmission');
            const response = new HttpErrorResponse({ status: 403 });
            modelingSubmissionStub.returns(throwError(response));

            const accountStub = stub(mockAuth, 'hasAnyAuthorityDirect');
            accountStub.returns(true);

            component.ngOnInit();
            tick(500);
            expect(modelingSubmissionStub).to.have.been.calledOnce;
            expect(accountStub).to.have.been.calledTwice;
            modelingSubmissionStub.restore();
            accountStub.restore();
        }));
    });

    it('should propagate isAtLeastInstructor', fakeAsync(() => {
        const course = new Course();
        course.isAtLeastInstructor = true;
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        mockAuth.isAtLeastInstructorInCourse(course);
        component['checkPermissions']();
        fixture.detectChanges();
        expect(component.isAtLeastInstructor).to.be.true;

        let assessmentLayoutComponent: AssessmentHeaderComponent = fixture.debugElement.query(By.directive(AssessmentLayoutComponent)).componentInstance;
        expect(assessmentLayoutComponent.isAtLeastInstructor).to.be.true;

        course.isAtLeastInstructor = false;
        mockAuth.isAtLeastInstructorInCourse(course);
        component['checkPermissions']();
        fixture.detectChanges();
        expect(component.isAtLeastInstructor).to.be.false;
        assessmentLayoutComponent = fixture.debugElement.query(By.directive(AssessmentLayoutComponent)).componentInstance;
        expect(assessmentLayoutComponent.isAtLeastInstructor).to.be.false;
    }));

    describe('should test the overwrite access rights and return true', () => {
        it('tests the method with instructor rights', fakeAsync(() => {
            const course = new Course();
            course.isAtLeastInstructor = true;
            component.ngOnInit();
            tick(500);
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            expect(component.canOverride).to.be.true;
        }));

        it('tests the method with tutor rights and as assessor', fakeAsync(() => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.isAssessor = true;
            component.complaint = new Complaint();
            component.complaint.id = 0;
            component.complaint.complaintText = 'complaint';
            component.ngOnInit();
            tick(500);
            course.isAtLeastInstructor = false;
            mockAuth.isAtLeastInstructorInCourse(course);
            component['checkPermissions']();
            fixture.detectChanges();
            expect(component.isAtLeastInstructor).to.be.false;
            expect(component.canOverride).to.be.false;
        }));
    });

    it('should return if user is allowed to only read', fakeAsync(() => {
        component.ngOnInit();
        tick(500);
        component.isAtLeastInstructor = false;
        component.complaint = new Complaint();
        component.complaint.id = 0;
        component.complaint.complaintText = 'complaint';
        component.isAssessor = true;
        expect(component.readOnly).to.be.true;
    }));

    it('should save assessment', fakeAsync(() => {
        const feedback = new Feedback();
        feedback.id = 2;
        feedback.text = 'This is a test feedback';
        feedback.detailText = 'Feedback';
        feedback.credits = 1;
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
        component.unreferencedFeedback = [feedback];

        component.result = {
            id: 2374,
            resultString: '1 of 12 points',
            score: 8,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
        } as unknown as Result;

        component.submission = {
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission;
        component.submission.results = [component.result];
        getLatestSubmissionResult(component.submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const fake = sinon.fake.returns(of(getLatestSubmissionResult(component.submission)));
        sinon.replace(service, 'saveAssessment', fake);

        component.ngOnInit();
        tick(500);
        component.onSaveAssessment();
        expect(fake).to.have.been.calledOnce;
    }));

    it('should try to submit assessment', fakeAsync(() => {
        const course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.assessmentDueDate = moment().subtract(2, 'days');

        // make sure feedback is valid
        const feedback = new Feedback();
        feedback.id = 2;
        feedback.text = 'This is a test feedback';
        feedback.detailText = 'Feedback';
        feedback.credits = 1;
        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
        component.unreferencedFeedback = [feedback];

        component.submission = {
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission;
        component.submission.results = [
            {
                id: 2374,
                resultString: '1 of 12 points',
                score: 8,
                rated: true,
                hasFeedback: true,
                hasComplaint: false,
            } as unknown as Result,
        ];
        getLatestSubmissionResult(component.submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const fake = sinon.fake.returns(of(getLatestSubmissionResult(component.submission)));
        sinon.replace(service, 'saveAssessment', fake);

        const secondFake = sinon.fake.returns(false);
        sinon.replace(window, 'confirm', secondFake);

        component.ngOnInit();
        tick(500);

        component.onSubmitAssessment();

        expect(window.confirm).to.be.calledOnce;
        expect(component.highlightMissingFeedback).to.be.true;
    }));

    it('should update assessment after complaint', fakeAsync(() => {
        const complaintResponse = new ComplaintResponse();
        complaintResponse.id = 1;
        complaintResponse.responseText = 'response';

        component.submission = {
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission;

        const comp_result = {
            id: 2374,
            resultString: '1 of 12 points',
            score: 8,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
            participation: {
                type: ParticipationType.SOLUTION,
                results: [],
            } as unknown as Participation,
        } as unknown as Result;

        const fake = sinon.fake.returns(of({ body: comp_result }));
        sinon.replace(service, 'updateAssessmentAfterComplaint', fake);

        component.ngOnInit();
        tick(500);

        component.onUpdateAssessmentAfterComplaint(complaintResponse);
        expect(fake).to.have.been.calledOnce;
        expect(component.result?.participation?.results).to.deep.equal([comp_result]);
    }));

    it('should cancel the current assessment', fakeAsync(() => {
        const windowFake = sinon.fake.returns(true);
        sinon.replace(window, 'confirm', windowFake);

        component.submission = {
            id: 2,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as ModelingSubmission;

        const fake = sinon.fake.returns(of());
        sinon.replace(service, 'cancelAssessment', fake);

        component.ngOnInit();
        tick(500);

        component.onCancelAssessment();
        expect(windowFake).to.have.been.calledOnce;
        expect(fake).to.have.been.calledOnce;
    }));

    it('should handle changed feedback', fakeAsync(() => {
        const feedbacks = [
            {
                id: 0,
                credits: 3,
                reference: 'reference',
            } as Feedback,
            {
                id: 1,
                credits: 1,
            } as Feedback,
        ];

        component.ngOnInit();
        tick(500);

        const course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.maxPoints = 5;
        component.modelingExercise.bonusPoints = 5;
        const handleFeedbackStub = stub(submissionService, 'handleFeedbackCorrectionRoundTag');
        component.onFeedbackChanged(feedbacks);
        expect(component.referencedFeedback).to.have.lengthOf(1);
        expect(component.totalScore).to.be.equal(3);
        expect(handleFeedbackStub).to.have.been.calledOnce;
    }));

    describe('test assessNext', () => {
        it('no submissions left', fakeAsync(() => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.id = 1;

            const routerStub = stub(router, 'navigate');
            const modelingSubmission: ModelingSubmission = { id: 1 };
            const fake = sinon.fake.returns(of(modelingSubmission));
            sinon.replace(modelingSubmissionService, 'getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment', fake);

            component.ngOnInit();

            const correctionRound = 1;
            const courseId = 1;
            const exerciseId = 1;
            component.correctionRound = correctionRound;
            component.courseId = courseId;
            component.modelingExercise = { id: exerciseId } as Exercise;
            component.exerciseId = exerciseId;
            const url = ['/course-management', courseId.toString(), 'modeling-exercises', exerciseId.toString(), 'submissions', modelingSubmission.id!.toString(), 'assessment'];
            const queryParams = { queryParams: { 'correction-round': correctionRound } };

            tick(500);
            component.assessNext();
            tick(500);

            expect(fake).to.have.been.calledOnce;
            expect(routerStub).to.have.been.calledWith(url, queryParams);
        }));

        it('throw error while assessNext', fakeAsync(() => {
            const course = new Course();
            component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
            component.modelingExercise.id = 1;

            const response = new HttpErrorResponse({ status: 403 });
            const fake = sinon.fake.returns(throwError(response));
            sinon.replace(modelingSubmissionService, 'getModelingSubmissionForExerciseForCorrectionRoundWithoutAssessment', fake);

            component.ngOnInit();
            tick(500);
            component.assessNext();
            expect(fake).to.have.been.calledOnce;
        }));
    });
    it('should import modeling submission as an example submission', fakeAsync(() => {
        component.ngOnInit();
        tick();
    }));
});
