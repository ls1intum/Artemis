/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionDeleteDialogComponent } from 'app/entities/drag-and-drop-question/drag-and-drop-question-delete-dialog.component';
import { DragAndDropQuestionService } from 'app/entities/drag-and-drop-question/drag-and-drop-question.service';

describe('Component Tests', () => {
    describe('DragAndDropQuestion Management Delete Component', () => {
        let comp: DragAndDropQuestionDeleteDialogComponent;
        let fixture: ComponentFixture<DragAndDropQuestionDeleteDialogComponent>;
        let service: DragAndDropQuestionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropQuestionDeleteDialogComponent]
            })
                .overrideTemplate(DragAndDropQuestionDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DragAndDropQuestionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropQuestionService);
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
