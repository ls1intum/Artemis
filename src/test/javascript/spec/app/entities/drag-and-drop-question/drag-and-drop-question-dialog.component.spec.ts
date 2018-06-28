/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropQuestionDialogComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-question/drag-and-drop-question-dialog.component';
import { DragAndDropQuestionService } from '../../../../../../main/webapp/app/entities/drag-and-drop-question/drag-and-drop-question.service';
import { DragAndDropQuestion } from '../../../../../../main/webapp/app/entities/drag-and-drop-question/drag-and-drop-question.model';

describe('Component Tests', () => {

    describe('DragAndDropQuestion Management Dialog Component', () => {
        let comp: DragAndDropQuestionDialogComponent;
        let fixture: ComponentFixture<DragAndDropQuestionDialogComponent>;
        let service: DragAndDropQuestionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropQuestionDialogComponent],
                providers: [
                    DragAndDropQuestionService
                ]
            })
            .overrideTemplate(DragAndDropQuestionDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropQuestionDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropQuestionService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DragAndDropQuestion(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dragAndDropQuestion = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dragAndDropQuestionListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DragAndDropQuestion();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dragAndDropQuestion = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dragAndDropQuestionListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
