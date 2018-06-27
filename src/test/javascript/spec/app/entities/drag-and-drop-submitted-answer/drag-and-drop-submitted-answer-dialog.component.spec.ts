/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragAndDropSubmittedAnswerDialogComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer-dialog.component';
import { DragAndDropSubmittedAnswerService } from '../../../../../../main/webapp/app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.service';
import { DragAndDropSubmittedAnswer } from '../../../../../../main/webapp/app/entities/drag-and-drop-submitted-answer/drag-and-drop-submitted-answer.model';

describe('Component Tests', () => {

    describe('DragAndDropSubmittedAnswer Management Dialog Component', () => {
        let comp: DragAndDropSubmittedAnswerDialogComponent;
        let fixture: ComponentFixture<DragAndDropSubmittedAnswerDialogComponent>;
        let service: DragAndDropSubmittedAnswerService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragAndDropSubmittedAnswerDialogComponent],
                providers: [
                    DragAndDropSubmittedAnswerService
                ]
            })
            .overrideTemplate(DragAndDropSubmittedAnswerDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropSubmittedAnswerDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropSubmittedAnswerService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DragAndDropSubmittedAnswer(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dragAndDropSubmittedAnswer = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dragAndDropSubmittedAnswerListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DragAndDropSubmittedAnswer();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dragAndDropSubmittedAnswer = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dragAndDropSubmittedAnswerListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
