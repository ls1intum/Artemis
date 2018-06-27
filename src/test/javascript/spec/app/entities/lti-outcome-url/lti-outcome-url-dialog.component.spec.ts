/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { LtiOutcomeUrlDialogComponent } from '../../../../../../main/webapp/app/entities/lti-outcome-url/lti-outcome-url-dialog.component';
import { LtiOutcomeUrlService } from '../../../../../../main/webapp/app/entities/lti-outcome-url/lti-outcome-url.service';
import { LtiOutcomeUrl } from '../../../../../../main/webapp/app/entities/lti-outcome-url/lti-outcome-url.model';
import { UserService } from '../../../../../../main/webapp/app/shared';
import { ExerciseService } from '../../../../../../main/webapp/app/entities/exercise';

describe('Component Tests', () => {

    describe('LtiOutcomeUrl Management Dialog Component', () => {
        let comp: LtiOutcomeUrlDialogComponent;
        let fixture: ComponentFixture<LtiOutcomeUrlDialogComponent>;
        let service: LtiOutcomeUrlService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [LtiOutcomeUrlDialogComponent],
                providers: [
                    UserService,
                    ExerciseService,
                    LtiOutcomeUrlService
                ]
            })
            .overrideTemplate(LtiOutcomeUrlDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(LtiOutcomeUrlDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiOutcomeUrlService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new LtiOutcomeUrl(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.ltiOutcomeUrl = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'ltiOutcomeUrlListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new LtiOutcomeUrl();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.ltiOutcomeUrl = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'ltiOutcomeUrlListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
