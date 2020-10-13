import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArtemisTestModule } from '../../../test.module';
import { MockEventManager } from '../../../helpers/mock-event-manager.service';
import { MockActiveModal } from '../../../helpers/mock-active-modal.service';
import { SubmissionDeleteDialogComponent } from 'app/entities/submission/submission-delete-dialog.component';
import { SubmissionService } from 'app/entities/submission/submission.service';

describe('Component Tests', () => {
    describe('Submission Management Delete Component', () => {
        let comp: SubmissionDeleteDialogComponent;
        let fixture: ComponentFixture<SubmissionDeleteDialogComponent>;
        let service: SubmissionService;
        let mockEventManager: MockEventManager;
        let mockActiveModal: MockActiveModal;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [SubmissionDeleteDialogComponent],
            })
                .overrideTemplate(SubmissionDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(SubmissionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmissionService);
            mockEventManager = TestBed.get(JhiEventManager);
            mockActiveModal = TestBed.get(NgbActiveModal);
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
                    expect(mockActiveModal.closeSpy).toHaveBeenCalled();
                    expect(mockEventManager.broadcastSpy).toHaveBeenCalled();
                }),
            ));

            it('Should not call delete service on clear', () => {
                // GIVEN
                spyOn(service, 'delete');

                // WHEN
                comp.cancel();

                // THEN
                expect(service.delete).not.toHaveBeenCalled();
                expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
            });
        });
    });
});
