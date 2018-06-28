/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropAssignmentDialogComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-assignment/drag-and-drop-assignment-dialog.component';
import { DragAndDropAssignmentService } from '../../../../../../main/webapp/app/entities/drag-and-drop-assignment/drag-and-drop-assignment.service';
import { DragAndDropAssignment } from '../../../../../../main/webapp/app/entities/drag-and-drop-assignment/drag-and-drop-assignment.model';
import { DragItemService } from '../../../../../../main/webapp/app/entities/drag-item';
import { DropLocationService } from '../../../../../../main/webapp/app/entities/drop-location';
import { DragAndDropSubmittedAnswerService } from '../../../../../../main/webapp/app/entities/drag-and-drop-submitted-answer';

describe('Component Tests', () => {

    describe('DragAndDropAssignment Management Dialog Component', () => {
        let comp: DragAndDropAssignmentDialogComponent;
        let fixture: ComponentFixture<DragAndDropAssignmentDialogComponent>;
        let service: DragAndDropAssignmentService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropAssignmentDialogComponent],
                providers: [
                    DragItemService,
                    DropLocationService,
                    DragAndDropSubmittedAnswerService,
                    DragAndDropAssignmentService
                ]
            })
            .overrideTemplate(DragAndDropAssignmentDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropAssignmentDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropAssignmentService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DragAndDropAssignment(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dragAndDropAssignment = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dragAndDropAssignmentListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DragAndDropAssignment();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dragAndDropAssignment = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dragAndDropAssignmentListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
