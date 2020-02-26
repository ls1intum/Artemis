/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { ModelingSubmissionComponent } from 'app/exercises/modeling/participate/modeling-submission.component';
import { ModelingSubmissionService } from 'app/exercises/modeling/participate/modeling-submission.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { MockSyncStorage } from '../../mocks/mock-sync.storage';
import { MockParticipationWebsocketService } from '../../mocks/mock-participation-websocket.service';
import { MockCookieService } from '../../mocks/mock-cookie.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie-service';
import { TranslateModule } from '@ngx-translate/core';
import { RouterTestingModule } from '@angular/router/testing';
import { ActivatedRoute } from '@angular/router';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { AlertService } from 'app/core/alert/alert.service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import * as moment from 'moment';
import * as sinon from 'sinon';
import { MockComponent } from 'ng-mocks';
import { DeviceDetectorService } from 'ngx-device-detector';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ArtemisComplaintsModule } from 'app/complaints/complaints.module';
import { ComplaintService } from 'app/complaints/complaint.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { ModelingAssessmentModule } from 'app/exercises/modeling/assess/modeling-assessment.module';
import { routes } from 'app/exercises/modeling/participate/modeling-participation.route';

chai.use(sinonChai);
const expect = chai.expect;

describe('Component Tests', () => {
    describe('ModelingSubmission Management Component', () => {
        let comp: ModelingSubmissionComponent;
        let fixture: ComponentFixture<ModelingSubmissionComponent>;
        let debugElement: DebugElement;
        let service: ModelingSubmissionService;

        const route = ({ params: of({ courseId: 5, exerciseId: 22, participationId: 123 }) } as any) as ActivatedRoute;
        const participation = new StudentParticipation();
        participation.exercise = new ModelingExercise('ClassDiagram');
        const submission = <ModelingSubmission>(<unknown>{ id: 20, submitted: true, participation });
        const result = { id: 1 } as Result;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [
                    ArtemisTestModule,
                    TranslateModule.forRoot(),
                    ArtemisSharedModule,
                    ArtemisResultModule,
                    ArtemisSharedComponentModule,
                    ModelingAssessmentModule,
                    ArtemisComplaintsModule,
                    RouterTestingModule.withRoutes([routes[0]]),
                ],
                declarations: [ModelingSubmissionComponent, MockComponent(ModelingEditorComponent)],
                providers: [
                    { provide: AlertService, useClass: MockAlertService },
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
                    // Ignore window scroll
                    window.scroll = () => {
                        return false;
                    };
                });
        });

        it('Should call load getDataForModelingEditor on init', () => {
            // GIVEN
            const fake = sinon.fake.returns(of(submission));
            sinon.replace(service, 'getDataForModelingEditor', fake);

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(fake).to.have.been.calledOnce;
            expect(comp.submission).to.be.include({ id: 20 });
        });

        it('should allow to submit when exercise due date not set', () => {
            // GIVEN
            sinon.replace(service, 'getDataForModelingEditor', sinon.fake.returns(of(submission)));

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
            submission.participation.initializationDate = moment().subtract(2, 'days');
            (<StudentParticipation>submission.participation).exercise.dueDate = moment().subtract(1, 'days');
            sinon.replace(service, 'getDataForModelingEditor', sinon.fake.returns(of(submission)));

            fixture.detectChanges();

            const submitButton = debugElement.query(By.css('jhi-button'));
            expect(submitButton).to.exist;
            expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('true');
        });

        it('should allow to submit after the deadline if the initialization date is after the due date', () => {
            submission.participation.initializationDate = moment().add(1, 'days');
            (<StudentParticipation>submission.participation).exercise.dueDate = moment();
            sinon.replace(service, 'getDataForModelingEditor', sinon.fake.returns(of(submission)));

            fixture.detectChanges();

            expect(comp.isLate).to.be.true;
            const submitButton = debugElement.query(By.css('jhi-button'));
            expect(submitButton).to.exist;
            expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('false');
        });

        it('should not allow to submit if there is a result and no due date', () => {
            comp.result = result;
            sinon.replace(service, 'getDataForModelingEditor', sinon.fake.returns(of(submission)));

            fixture.detectChanges();

            const submitButton = debugElement.query(By.css('jhi-button'));
            expect(submitButton).to.exist;
            expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('true');
        });

        it('should get inactive as soon as the due date passes the current date', () => {
            (<StudentParticipation>submission.participation).exercise.dueDate = moment().add(1, 'days');
            sinon.replace(service, 'getDataForModelingEditor', sinon.fake.returns(of(submission)));

            fixture.detectChanges();
            comp.participation.initializationDate = moment();

            expect(comp.isActive).to.be.true;

            comp.modelingExercise.dueDate = moment().subtract(1, 'days');

            fixture.detectChanges();
            expect(comp.isActive).to.be.false;
        });
    });
});
