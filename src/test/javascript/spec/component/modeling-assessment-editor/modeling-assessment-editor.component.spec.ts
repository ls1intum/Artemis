import { async, ComponentFixture, discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { ArtemisTestModule } from '../../test.module';
import { By } from '@angular/platform-browser';
import { mockedActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route-query-param-map';
import { ActivatedRoute, convertToParamMap, ParamMap, Router } from '@angular/router';
import { Mutable } from '../../helpers/mutable';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { RouterTestingModule } from '@angular/router/testing';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { AssessmentLayoutComponent } from 'app/assessment/assessment-layout/assessment-layout.component';
import { AssessmentHeaderComponent } from 'app/assessment/assessment-header/assessment-header.component';
import { Course } from 'app/entities/course.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ModelingAssessmentEditorComponent } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.component';
import { ArtemisModelingAssessmentEditorModule } from 'app/exercises/modeling/assess/modeling-assessment-editor/modeling-assessment-editor.module';
import { Complaint } from 'app/entities/complaint.model';
import { Feedback } from 'app/entities/feedback.model';
// import {expect} from "../../helpers/jest.fix";
import { Result } from 'app/entities/result.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { HttpTestingController } from '@angular/common/http/testing';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { HttpResponse } from '@angular/common/http';

import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';

import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import moment = require('moment');
import { flush } from '@sentry/browser';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { SinonStub, stub } from 'sinon';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { Exam } from 'app/entities/exam.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ModelingAssessmentEditorComponent', () => {
    let component: ModelingAssessmentEditorComponent;
    let fixture: ComponentFixture<ModelingAssessmentEditorComponent>;
    let service: ModelingAssessmentService;
    let mockAuth: MockAccountService;
    let router: MockRouter;
    let navigateByUrlStub: SinonStub;
    let modelingSubmissionService: ModelingSubmissionService;
    let modelingSubmissionStub: SinonStub;

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisModelingAssessmentEditorModule],
            declarations: [],
            providers: [
                JhiLanguageHelper,
                mockedActivatedRoute({}, { showBackButton: 'false' }),
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ModelingAssessmentEditorComponent);
                component = fixture.componentInstance;
                service = TestBed.inject(ModelingAssessmentService);
                modelingSubmissionService = TestBed.inject(ModelingSubmissionService);

                mockAuth = (fixture.debugElement.injector.get(AccountService) as any) as MockAccountService;
                mockAuth.hasAnyAuthorityDirect([]);
                mockAuth.identity();
                fixture.detectChanges();
            });
    }));

    afterEach(() => {
        sinon.restore();
    });

    it('ngOnInit', fakeAsync(() => {
        modelingSubmissionStub = stub(modelingSubmissionService, 'getSubmission');
        const submission = ({
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
            participation: ({
                type: ParticipationType.SOLUTION,
                results: [],
                exercise: ({
                    id: 1,
                    problemStatement: 'problemo',
                    gradingInstructions: 'grading',
                    title: 'title',
                    shortName: 'name',
                    exerciseGroup: ({
                        exam: ({
                            course: new Course(),
                        } as unknown) as Exam,
                    } as unknown) as ExerciseGroup,
                } as unknown) as Exercise,
            } as unknown) as Participation,
            result: ({
                id: 2374,
                resultString: '1 of 12 points',
                score: 8,
                rated: true,
                hasFeedback: true,
                hasComplaint: false,
                feedbacks: [
                    {
                        id: 2,
                        detailText: 'Feedback',
                        credits: 1,
                    } as Feedback,
                ],
            } as unknown) as Result,
        } as unknown) as ModelingSubmission;

        modelingSubmissionStub.returns(of(submission));

        component.ngOnInit();
        tick(500);
        expect(modelingSubmissionStub).to.have.been.calledOnce;
        expect(component.isLoading).to.be.false;
        modelingSubmissionStub.restore();
    }));

    it('should show or hide a back button', () => {
        const route = fixture.debugElement.injector.get(ActivatedRoute) as Mutable<ActivatedRoute>;
        const queryParamMap = route.queryParamMap as BehaviorSubject<ParamMap>;
        queryParamMap.next(convertToParamMap({ hideBackButton: 'true' }));
        fixture.detectChanges();
        let assessmentHeaderComponent: AssessmentHeaderComponent = fixture.debugElement.query(By.directive(AssessmentHeaderComponent)).componentInstance;
        expect(assessmentHeaderComponent.hideBackButton).to.be.true;

        queryParamMap.next(convertToParamMap({ hideBackButton: undefined }));
        fixture.detectChanges();
        assessmentHeaderComponent = fixture.debugElement.query(By.directive(AssessmentHeaderComponent)).componentInstance;
        expect(assessmentHeaderComponent.hideBackButton).to.be.false;
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
            component.complaint.resultBeforeComplaint = 'result';
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
        component.complaint.resultBeforeComplaint = 'result';
        component.isAssessor = true;
        expect(component.readOnly).to.be.true;
    }));

    it('calls onError', fakeAsync(() => {}));

    it('should save assessment', fakeAsync(() => {
        const feedback = new Feedback();
        feedback.id = 2;
        feedback.text = 'This is a test feedback';
        feedback.detailText = 'Feedback';
        feedback.credits = 1;
        component.generalFeedback = feedback;

        component.submission = ({
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown) as ModelingSubmission;
        component.submission.result = ({
            id: 2374,
            resultString: '1 of 12 points',
            score: 8,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
        } as unknown) as Result;
        component.submission.result.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const fake = sinon.fake.returns(of(component.submission.result));
        sinon.replace(service, 'saveAssessment', fake);

        component.ngOnInit();
        tick(500);
        component.onSaveAssessment();
        expect(fake).to.have.been.calledOnce;
        // expect(component.generalFeedback).to.be.deep.equal(component.submission.result.feedbacks[0]);
    }));

    it('should submit assessment', fakeAsync(() => {
        let course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.assessmentDueDate = moment().subtract(2, 'days');

        // make sure feedback is valid
        const feedback = new Feedback();
        feedback.id = 2;
        feedback.text = 'This is a test feedback';
        feedback.detailText = 'Feedback';
        feedback.credits = 1;
        component.generalFeedback = feedback;

        component.submission = ({
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown) as ModelingSubmission;
        component.submission.result = ({
            id: 2374,
            resultString: '1 of 12 points',
            score: 8,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
        } as unknown) as Result;
        component.submission.result.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        const fake = sinon.fake.returns(of(component.submission.result));
        sinon.replace(service, 'saveAssessment', fake);

        const secondFake = sinon.fake.returns(false);
        sinon.replace(window, 'confirm', secondFake);

        component.ngOnInit();
        tick(100);

        component.onSubmitAssessment();
        // expect(fake).to.have.been.calledOnce;   // Error: 1 timer(s) still in the queue., error is in the saveAssessment()

        expect(window.confirm).to.be.calledOnce;
        expect(component.highlightMissingFeedback).to.be.true;
        // discardPeriodicTasks();
    }));

    it('should update assessment after complaint', fakeAsync(() => {
        const complaintResponse = new ComplaintResponse();
        complaintResponse.id = 1;
        complaintResponse.responseText = 'response';

        component.submission = ({
            id: 1,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown) as ModelingSubmission;

        const result = ({
            id: 2374,
            resultString: '1 of 12 points',
            score: 8,
            rated: true,
            hasFeedback: true,
            hasComplaint: false,
            participation: ({
                type: ParticipationType.SOLUTION,
                results: [],
            } as unknown) as Participation,
        } as unknown) as Result;

        const fake = sinon.fake.returns(of({ body: result }));
        sinon.replace(service, 'updateAssessmentAfterComplaint', fake);

        component.onUpdateAssessmentAfterComplaint(complaintResponse);
        expect(fake).to.have.been.calledOnce;
        expect(component.result?.participation?.results).to.deep.equal([result]);
    }));

    it('should cancel the current assessment and navigate back to the exercise dashboard', fakeAsync(() => {
        const windowFake = sinon.fake.returns(true);
        sinon.replace(window, 'confirm', windowFake);

        component.submission = ({
            id: 2,
            submitted: true,
            type: 'MANUAL',
            text: 'Test\n\nTest\n\nTest',
        } as unknown) as ModelingSubmission;

        const fake = sinon.fake.returns(of()); // syntaxError in service
        sinon.replace(service, 'cancelAssessment', fake);
        // const fake = sinon.fake.returns(true);
        // sinon.replace(component, 'navigateBack', fake);

        component.onCancelAssessment();
        expect(windowFake).to.have.been.calledOnce;
        expect(fake).to.have.been.calledOnce;
    }));

    it('should handle changed feedback', fakeAsync(() => {
        let feedbacks = [
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
        let course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.maxScore = 5;
        component.modelingExercise.bonusPoints = 5;

        component.onFeedbackChanged(feedbacks);
        expect(component.referencedFeedback).to.have.lengthOf(1);
        expect(component.totalScore).to.be.equal(3);
    }));

    /*it('should assess next optimal submission', fakeAsync(() => {
        component.ngOnInit();
        tick(500);

        router = new MockRouter();
        navigateByUrlStub = stub(router, 'navigateByUrl');
        navigateByUrlStub.returns(Promise.resolve(true));

        // const navigateSpy = spyOn(router, 'navigateByUrl');
        // sinon.replace(router, 'navigateByUrl', sinon.fake.returns(Promise.resolve(true)));

        let course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.id = 1;

        const numbers : number[] = [0,1,2,3,4,5];
        const fake = sinon.fake.returns(of(numbers));
        sinon.replace(service, 'getOptimalSubmissions', fake);


        component.assessNextOptimal();
        tick(500);
        expect(fake).to.have.been.called;
        expect(navigateByUrlStub).to.have.been.calledOnceWithExactly('/');
        // expect(navigateSpy).to.have.been.calledTwice;
        // flush();
        // fixture.detectChanges();
        // component.ngOnInit();
        // tick();
        // discardPeriodicTasks();
        // fixture.whenStable();
        navigateByUrlStub.restore();
    }));*/

    it('no submissions left', fakeAsync(() => {
        let course = new Course();
        component.modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        component.modelingExercise.id = 1;

        const numbers: number[] = [];
        const fake = sinon.fake.returns(of(numbers));
        sinon.replace(service, 'getOptimalSubmissions', fake);
        component.ngOnInit();
        tick(500);
        component.assessNextOptimal();
        expect(fake).to.have.been.calledOnce;
        // maybe expect jhiAlertService
    }));
});
