/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { LtiUserIdDeleteDialogComponent } from '../../../../../../main/webapp/app/entities/lti-user-id/lti-user-id-delete-dialog.component';
import { LtiUserIdService } from '../../../../../../main/webapp/app/entities/lti-user-id/lti-user-id.service';

describe('Component Tests', () => {

    describe('LtiUserId Management Delete Component', () => {
        let comp: LtiUserIdDeleteDialogComponent;
        let fixture: ComponentFixture<LtiUserIdDeleteDialogComponent>;
        let service: LtiUserIdService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [LtiUserIdDeleteDialogComponent],
                providers: [
                    LtiUserIdService
                ]
            })
            .overrideTemplate(LtiUserIdDeleteDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(LtiUserIdDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(LtiUserIdService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('confirmDelete', () => {
            it('Should call delete service on confirmDelete',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        spyOn(service, 'delete').and.returnValue(Observable.of({}));

                        // WHEN
                        comp.confirmDelete(123);
                        tick();

                        // THEN
                        expect(service.delete).toHaveBeenCalledWith(123);
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
