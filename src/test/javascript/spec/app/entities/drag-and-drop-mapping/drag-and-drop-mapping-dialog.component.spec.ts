/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropMappingDialogComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping-dialog.component';
import { DragAndDropMappingService } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping.service';
import { DragAndDropMapping } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping.model';
import { DragItemService } from '../../../../../../main/webapp/app/entities/drag-item';
import { DropLocationService } from '../../../../../../main/webapp/app/entities/drop-location';
import { DragAndDropSubmittedAnswerService } from '../../../../../../main/webapp/app/entities/drag-and-drop-submitted-answer';
import { DragAndDropQuestionService } from '../../../../../../main/webapp/app/entities/drag-and-drop-question';

describe('Component Tests', () => {

    describe('DragAndDropMapping Management Dialog Component', () => {
        let comp: DragAndDropMappingDialogComponent;
        let fixture: ComponentFixture<DragAndDropMappingDialogComponent>;
        let service: DragAndDropMappingService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropMappingDialogComponent],
                providers: [
                    DragItemService,
                    DropLocationService,
                    DragAndDropSubmittedAnswerService,
                    DragAndDropQuestionService,
                    DragAndDropMappingService
                ]
            })
            .overrideTemplate(DragAndDropMappingDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropMappingDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropMappingService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DragAndDropMapping(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dragAndDropMapping = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dragAndDropMappingListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DragAndDropMapping();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dragAndDropMapping = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dragAndDropMappingListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
