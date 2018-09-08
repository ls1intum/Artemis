/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { LtiOutcomeUrlDeleteDialogComponent } from 'app/entities/lti-outcome-url/lti-outcome-url-delete-dialog.component';
import { LtiOutcomeUrlService } from 'app/entities/lti-outcome-url/lti-outcome-url.service';

describe('Component Tests', () => {
    describe('LtiOutcomeUrl Management Delete Component', () => {
        let comp: LtiOutcomeUrlDeleteDialogComponent;
        let fixture: ComponentFixture<LtiOutcomeUrlDeleteDialogComponent>;
        let service: LtiOutcomeUrlService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [LtiOutcomeUrlDeleteDialogComponent]
            })
                .overrideTemplate(LtiOutcomeUrlDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(LtiOutcomeUrlDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiOutcomeUrlService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('confirmDelete', () => {
            it('Should call delete service on confirmDelete', inject(
                [],
                fakeAsync(() => {
                    // GIVEN
                    spyOn(service, 'delete').and.returnValue(of({}));

                    // WHEN
                    comp.confirmDelete(123);
                    tick();

                    // THEN
                    expect(service.delete).toHaveBeenCalledWith(123);
                    expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    expect(mockEventManager.broadcastSpy).toHaveBeenCalled();
                })
            ));
        });
    });
});
