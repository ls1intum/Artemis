/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { ModelingSubmissionDeleteDialogComponent } from 'app/entities/modeling-submission/modeling-submission-delete-dialog.component';
import { ModelingSubmissionService } from 'app/entities/modeling-submission/modeling-submission.service';

describe('Component Tests', () => {
    describe('ModelingSubmission Management Delete Component', () => {
        let comp: ModelingSubmissionDeleteDialogComponent;
        let fixture: ComponentFixture<ModelingSubmissionDeleteDialogComponent>;
        let service: ModelingSubmissionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [ModelingSubmissionDeleteDialogComponent]
            })
                .overrideTemplate(ModelingSubmissionDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ModelingSubmissionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ModelingSubmissionService);
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
