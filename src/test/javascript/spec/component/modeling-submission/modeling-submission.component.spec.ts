import * as ace from 'brace';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ModelingSubmissionComponent } from 'app/exercises/modeling/participate/modeling-submission.component';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockParticipationWebsocketService } from '../../helpers/mocks/service/mock-participation-websocket.service';
import { MockCookieService } from '../../helpers/mocks/service/mock-cookie.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie-service';
import { TranslateModule } from '@ngx-translate/core';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { MockComplaintService } from '../../helpers/mocks/service/mock-complaint.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import * as moment from 'moment';
import * as sinon from 'sinon';
import { stub } from 'sinon';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { DeviceDetectorService } from 'ngx-device-detector';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ComplaintService } from 'app/complaints/complaint.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { routes } from 'app/exercises/modeling/participate/modeling-participation.route';
import { ArtemisTeamModule } from 'app/exercises/shared/team/team.module';
import { ArtemisTeamSubmissionSyncModule } from 'app/exercises/shared/team-submission-sync/team-submission-sync.module';
import { ArtemisHeaderExercisePageWithDetailsModule } from 'app/exercises/shared/exercise-headers/exercise-headers.module';
import { ArtemisFullscreenModule } from 'app/shared/fullscreen/fullscreen.module';
import { RatingModule } from 'app/exercises/shared/rating/rating.module';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { UMLElement, UMLModel } from '@ls1intum/apollon';

chai.use(sinonChai);
const expect = chai.expect;

describe('Component Tests', () => {
    describe('ModelingSubmission Management Component', () => {
        // needed to make sure ace is defined
        ace.acequire('ace/ext/modelist.js');
        let comp: ModelingSubmissionComponent;
        let fixture: ComponentFixture<ModelingSubmissionComponent>;
        let debugElement: DebugElement;
        let service: ModelingSubmissionService;
        let router: Router;

        const route = ({ params: of({ courseId: 5, exerciseId: 22, participationId: 123 }) } as any) as ActivatedRoute;
        const participation = new StudentParticipation();
        participation.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
        participation.id = 1;
        const submission = <ModelingSubmission>(<unknown>{ id: 20, submitted: true, participation });
        const result = { id: 1 } as Result;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [
                    ArtemisTestModule,
                    TranslateModule.forRoot(),
                    MockModule(ArtemisSharedModule),
                    MockModule(ArtemisResultModule),
                    MockModule(ArtemisSharedComponentModule),
                    ModelingAssessmentModule,
                    MockModule(ArtemisComplaintsModule),
                    MockModule(ArtemisTeamModule),
                    MockModule(ArtemisFullscreenModule),
                    MockModule(ArtemisTeamSubmissionSyncModule),
                    MockModule(ArtemisHeaderExercisePageWithDetailsModule),
                    MockModule(RatingModule),
                    RouterTestingModule.withRoutes([routes[0]]),
                ],
                declarations: [ModelingSubmissionComponent, MockComponent(ModelingEditorComponent), MockPipe(HtmlForMarkdownPipe)],
                providers: [
                    { provide: ComplaintService, useClass: MockComplaintService },
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: CookieService, useClass: MockCookieService },
                    { provide: ActivatedRoute, useValue: route },
                    { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                    { provide: DeviceDetectorService },
                ],
            })
                .overrideModule(ArtemisTestModule, { set: { declarations: [], exports: [] } })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(ModelingSubmissionComponent);
                    comp = fixture.componentInstance;
                    debugElement = fixture.debugElement;
                    service = debugElement.injector.get(ModelingSubmissionService);
                    router = debugElement.injector.get(Router);
                    comp.modelingEditor = TestBed.createComponent(MockComponent(ModelingEditorComponent)).componentInstance;
                });
        });

        afterEach(() => {
            sinon.restore();
        });

        it('Should call load getDataForModelingEditor on init', () => {
            // GIVEN
            const fake = sinon.fake.returns(of(submission));
            sinon.replace(service, 'getLatestSubmissionForModelingEditor', fake);

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(fake).to.have.been.calledOnce;
            expect(comp.submission).to.be.include({ id: 20 });
        });

        it('should allow to submit when exercise due date not set', () => {
            // GIVEN
            sinon.replace(service, 'getLatestSubmissionForModelingEditor', sinon.fake.returns(of(submission)));

            // WHEN
            comp.isLoading = false;
            fixture.detectChanges();

            expect(debugElement.query(By.css('div'))).to.exist;

            const submitButton = debugElement.query(By.css('jhi-button'));
            expect(submitButton).to.exist;
            expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('false');
            expect(comp.isActive).to.be.true;
        });

        it('should not allow to submit after the deadline if the initialization date is before the due date', () => {
            submission.participation!.initializationDate = moment().subtract(2, 'days');
            (<StudentParticipation>submission.participation).exercise!.dueDate = moment().subtract(1, 'days');
            sinon.replace(service, 'getLatestSubmissionForModelingEditor', sinon.fake.returns(of(submission)));

            fixture.detectChanges();

            const submitButton = debugElement.query(By.css('jhi-button'));
            expect(submitButton).to.exist;
            expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('true');
        });

        it('should allow to submit after the deadline if the initialization date is after the due date', () => {
            submission.participation!.initializationDate = moment().add(1, 'days');
            (<StudentParticipation>submission.participation).exercise!.dueDate = moment();
            sinon.replace(service, 'getLatestSubmissionForModelingEditor', sinon.fake.returns(of(submission)));

            fixture.detectChanges();

            expect(comp.isLate).to.be.true;
            const submitButton = debugElement.query(By.css('jhi-button'));
            expect(submitButton).to.exist;
            expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('false');
        });

        it('should not allow to submit if there is a result and no due date', () => {
            comp.result = result;
            sinon.replace(service, 'getLatestSubmissionForModelingEditor', sinon.fake.returns(of(submission)));

            fixture.detectChanges();

            const submitButton = debugElement.query(By.css('jhi-button'));
            expect(submitButton).to.exist;
            expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('true');
        });

        it('should get inactive as soon as the due date passes the current date', () => {
            (<StudentParticipation>submission.participation).exercise!.dueDate = moment().add(1, 'days');
            sinon.replace(service, 'getLatestSubmissionForModelingEditor', sinon.fake.returns(of(submission)));

            fixture.detectChanges();
            comp.participation.initializationDate = moment();

            expect(comp.isActive).to.be.true;

            comp.modelingExercise.dueDate = moment().subtract(1, 'days');

            fixture.detectChanges();
            expect(comp.isActive).to.be.false;
        });

        it('should navigate to access denied page on 403 error status', () => {
            sinon.replace(service, 'getLatestSubmissionForModelingEditor', sinon.fake.returns(throwError({ status: 403 })));
            const spy = stub(router, 'navigate');
            spy.returns(new Promise(() => true));
            fixture.detectChanges();
            expect(spy.called).to.be.true;
        });

        it('should set correct properties on modeling exercise update when saving', () => {
            sinon.replace(service, 'getLatestSubmissionForModelingEditor', sinon.fake.returns(of(submission)));
            fixture.detectChanges();

            const fake = sinon.replace(service, 'update', sinon.fake.returns(of({ body: submission })));
            comp.saveDiagram();
            expect(fake).to.have.been.calledOnce;
            expect(comp.submission).to.be.deep.equal(submission);
        });

        it('should set correct properties on modeling exercise create when saving', () => {
            fixture.detectChanges();

            const fake = sinon.replace(service, 'create', sinon.fake.returns(of({ body: submission })));
            comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
            comp.modelingExercise.id = 1;
            comp.saveDiagram();
            expect(fake).to.have.been.calledOnce;
            expect(comp.submission).to.be.deep.equal(submission);
        });

        it('should set correct properties on modeling exercise create when submitting', () => {
            fixture.detectChanges();

            const modelSubmission = <ModelingSubmission>(<unknown>{ model: '{"elements": [{"id": 1}]}', submitted: true, participation });
            comp.submission = modelSubmission;
            const fake = sinon.replace(service, 'create', sinon.fake.returns(of({ body: submission })));
            comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
            comp.modelingExercise.id = 1;
            comp.submit();
            expect(fake).to.have.been.calledOnce;
            expect(comp.submission).to.be.deep.equal(submission);
        });

        it('should set result when new result comes in from websocket', () => {
            submission.model = '{"elements": [{"id": 1}]}';
            sinon.replace(service, 'getLatestSubmissionForModelingEditor', sinon.fake.returns(of(submission)));
            const participationWebSocketService = debugElement.injector.get(ParticipationWebsocketService);

            const unreferencedFeedback = new Feedback();
            unreferencedFeedback.id = 1;
            unreferencedFeedback.detailText = 'General Feedback';
            unreferencedFeedback.credits = 5;
            unreferencedFeedback.type = FeedbackType.MANUAL_UNREFERENCED;
            const newResult = new Result();
            newResult.score = 50.0;
            newResult.assessmentType = AssessmentType.MANUAL;
            newResult.submission = submission;
            newResult.participation = submission.participation;
            newResult.completionDate = moment();
            newResult.feedbacks = [unreferencedFeedback];
            sinon.replace(participationWebSocketService, 'subscribeForLatestResultOfParticipation', sinon.fake.returns(of(newResult)));
            fixture.detectChanges();
            expect(comp.assessmentResult).to.deep.equal(newResult);
        });

        it('should update submission when new submission comes in from websocket', () => {
            submission.submitted = false;
            sinon.replace(service, 'getLatestSubmissionForModelingEditor', sinon.fake.returns(of(submission)));
            const websocketService = debugElement.injector.get(JhiWebsocketService);
            sinon.stub(websocketService, 'subscribe');
            const receiveStub = sinon.stub(websocketService, 'receive');
            const modelSubmission = <ModelingSubmission>(<unknown>{
                id: 1,
                model: '{"elements": [{"id": 1}]}',
                submitted: true,
                participation,
            });
            receiveStub.returns(of(modelSubmission));
            fixture.detectChanges();
            expect(comp.submission).to.deep.equal(modelSubmission);
            expect(receiveStub).to.have.been.called;
        });

        it('should set correct properties on modeling exercise update when submitting', () => {
            const modelSubmission = <ModelingSubmission>(<unknown>{
                id: 1,
                model: '{"elements": [{"id": 1}]}',
                submitted: true,
                participation,
            });
            comp.submission = modelSubmission;
            const fake = sinon.replace(service, 'update', sinon.fake.returns(of({ body: submission })));
            comp.modelingExercise = new ModelingExercise(UMLDiagramType.DeploymentDiagram, undefined, undefined);
            comp.modelingExercise.id = 1;
            fixture.detectChanges();
            comp.submit();
            expect(fake).to.have.been.calledOnce;
            expect(comp.submission).to.be.deep.equal(submission);
        });

        it('should calculate number of elements from model', () => {
            const elements = [{ id: 1 }, { id: 2 }, { id: 3 }];
            const relationships = [{ id: 4 }, { id: 5 }];
            submission.model = JSON.stringify({ elements, relationships });
            comp.submission = submission;
            fixture.detectChanges();
            expect(comp.calculateNumberOfModelElements()).to.equal(elements.length + relationships.length);
        });

        it('should update selected entities with given elements', () => {
            const relationships = ['relationShip1', 'relationShip2'];
            const selection = { elements: ['ownerId1', 'ownerId2'], relationships };
            comp.umlModel = <UMLModel>(<unknown>{
                elements: [<UMLElement>(<unknown>{ owner: 'ownerId1', id: 'elementId1' }), <UMLElement>(<unknown>{ owner: 'ownerId2', id: 'elementId2' })],
            });
            fixture.detectChanges();
            comp.onSelectionChanged(selection);
            expect(comp.selectedRelationships).to.deep.equal(relationships);
            expect(comp.selectedEntities).to.deep.equal(['ownerId1', 'ownerId2', 'elementId1', 'elementId2']);
        });

        it('should isSelected return true if no selectedEntities and selectedRelationships', () => {
            const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: '5' });
            comp.selectedEntities = [];
            comp.selectedRelationships = [];
            fixture.detectChanges();
            expect(comp.isSelected(feedback)).to.equal(true);
            comp.selectedEntities = ['3'];
            fixture.detectChanges();
            expect(comp.isSelected(feedback)).to.equal(false);
        });

        it('should isSelected return true if feedback reference is in selectedEntities or selectedRelationships', () => {
            const id = 'referenceId';
            const feedback = <Feedback>(<unknown>{ referenceType: 'Activity', referenceId: id });
            comp.selectedEntities = [id];
            comp.selectedRelationships = [];
            fixture.detectChanges();
            expect(comp.isSelected(feedback)).to.equal(true);
            comp.selectedEntities = [];
            comp.selectedRelationships = [id];
            fixture.detectChanges();
            expect(comp.isSelected(feedback)).to.equal(false);
        });

        it('should update submission with current values', () => {
            const model = <UMLModel>(<unknown>{
                elements: [<UMLElement>(<unknown>{ owner: 'ownerId1', id: 'elementId1' }), <UMLElement>(<unknown>{ owner: 'ownerId2', id: 'elementId2' })],
            });
            const currentModelStub = stub(comp.modelingEditor, 'getCurrentModel').returns(model);
            comp.explanation = 'Explanation Test';
            comp.updateSubmissionWithCurrentValues();
            expect(currentModelStub).to.have.been.called;
            expect(comp.hasElements).to.equal(true);
            expect(comp.submission).to.exist;
            expect(comp.submission.model).to.equal(JSON.stringify(model));
            expect(comp.submission.explanationText).to.equal('Explanation Test');
        });
    });
});
