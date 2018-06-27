/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionStatisticDeleteDialogComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic-delete-dialog.component';
import { DragAndDropQuestionStatisticService } from '../../../../../../main/webapp/app/entities/drag-and-drop-question-statistic/drag-and-drop-question-statistic.service';

describe('Component Tests', () => {

    describe('DragAndDropQuestionStatistic Management Delete Component', () => {
        let comp: DragAndDropQuestionStatisticDeleteDialogComponent;
        let fixture: ComponentFixture<DragAndDropQuestionStatisticDeleteDialogComponent>;
        let service: DragAndDropQuestionStatisticService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropQuestionStatisticDeleteDialogComponent],
                providers: [
                    DragAndDropQuestionStatisticService
                ]
            })
            .overrideTemplate(DragAndDropQuestionStatisticDeleteDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropQuestionStatisticDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropQuestionStatisticService);
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
