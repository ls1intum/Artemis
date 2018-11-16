/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { SubmissionDeleteDialogComponent } from 'app/entities/submission/submission-delete-dialog.component';
import { SubmissionService } from 'app/entities/submission/submission.service';

describe('Component Tests', () => {
    describe('Submission Management Delete Component', () => {
        let comp: SubmissionDeleteDialogComponent;
        let fixture: ComponentFixture<SubmissionDeleteDialogComponent>;
        let service: SubmissionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [SubmissionDeleteDialogComponent]
            })
                .overrideTemplate(SubmissionDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(SubmissionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmissionService);
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
