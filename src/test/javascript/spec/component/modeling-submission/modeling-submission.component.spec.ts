/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { ModelingSubmissionComponent } from 'app/modeling-submission/modeling-submission.component';
import { ModelingSubmissionService } from 'app/entities/modeling-submission/modeling-submission.service';
import { ModelingSubmission } from 'app/entities/modeling-submission/modeling-submission.model';
import { MockCookieService, MockParticipationWebsocketService, MockSyncStorage } from '../../mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { CookieService } from 'ngx-cookie';
import { TranslateModule } from '@ngx-translate/core';
import { RouterTestingModule } from '@angular/router/testing';
import { modelingSubmissionRoute } from 'app/modeling-submission/modeling-submission.route';
import { ActivatedRoute } from '@angular/router';
import { ParticipationWebsocketService, StudentParticipation } from 'app/entities/participation';
import { ModelingExercise } from 'app/entities/modeling-exercise';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { MockAlertService } from '../../helpers/mock-alert.service';
import { ComplaintService } from 'app/entities/complaint';
import { MockComplaintService } from '../../mocks/mock-complaint.service';
import { JhiAlertService } from 'ng-jhipster';
import { ArtemisSharedModule } from 'app/shared';
import { ArtemisResultModule } from 'app/entities/result';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisModelingEditorModule, ModelingEditorComponent } from 'app/modeling-editor';
import { ModelingAssessmentModule } from 'app/modeling-assessment';
import { ArtemisComplaintsModule } from 'app/complaints';
import { spy } from 'sinon';
import * as sinon from 'sinon';
import { MockComponent } from 'ng-mocks';

chai.use(sinonChai);
const expect = chai.expect;

describe('Component Tests', () => {
    describe('ModelingSubmission Management Component', () => {
        let comp: ModelingSubmissionComponent;
        let fixture: ComponentFixture<ModelingSubmissionComponent>;
        let debugElement: DebugElement;
        let service: ModelingSubmissionService;

        const route = ({ params: of({ participationId: 123 }) } as any) as ActivatedRoute;
        const participation = new StudentParticipation();
        participation.exercise = new ModelingExercise('ClassDiagram');
        const submission = <ModelingSubmission>(<unknown>{ id: 20, submitted: true, participation: participation });

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
                    RouterTestingModule.withRoutes([modelingSubmissionRoute[0]]),
                ],
                declarations: [ModelingSubmissionComponent, MockComponent(ModelingEditorComponent)],
                providers: [
                    { provide: JhiAlertService, useClass: MockAlertService },
                    { provide: ComplaintService, useClass: MockComplaintService },
                    { provide: LocalStorageService, useClass: MockSyncStorage },
                    { provide: SessionStorageService, useClass: MockSyncStorage },
                    { provide: CookieService, useClass: MockCookieService },
                    { provide: ActivatedRoute, useValue: route },
                    { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                ],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(ModelingSubmissionComponent);
                    comp = fixture.componentInstance;
                    debugElement = fixture.debugElement;
                    service = debugElement.injector.get(ModelingSubmissionService);
                });
        });

        it('Should call load getDataForModelingEditor on init', () => {
            // Ignore window confirm
            window.scroll = () => {
                return false;
            };
            // GIVEN
            const fake = sinon.fake.returns(of(submission));
            sinon.replace(service, 'getDataForModelingEditor', fake);

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(fake).to.have.been.calledOnce;
            expect(comp.submission).to.be.include({ id: 20 });
        });

        it('Allow to submit when exercise due date not set', () => {
            // Ignore window confirm
            window.scroll = () => {
                return false;
            };
            // GIVEN
            const fake = sinon.fake.returns(of(submission));
            sinon.replace(service, 'getDataForModelingEditor', fake);

            // WHEN
            comp.ngOnInit();
            comp.isLoading = false;
            fixture.detectChanges();

            expect(debugElement.query(By.css('div'))).to.exist;

            let submitButton = debugElement.query(By.css('jhi-button'));
            expect(submitButton).to.exist;
            expect(submitButton.attributes['ng-reflect-disabled']).to.be.equal('false');
            expect(comp.isActive).to.be.true;
        });
    });
});
