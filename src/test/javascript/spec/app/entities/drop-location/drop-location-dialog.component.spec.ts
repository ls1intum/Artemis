/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { DropLocationDialogComponent } from '../../../../../../main/webapp/app/entities/drop-location/drop-location-dialog.component';
import { DropLocationService } from '../../../../../../main/webapp/app/entities/drop-location/drop-location.service';
import { DropLocation } from '../../../../../../main/webapp/app/entities/drop-location/drop-location.model';
import { DragAndDropQuestionService } from '../../../../../../main/webapp/app/entities/drag-and-drop-question';

describe('Component Tests', () => {

    describe('DropLocation Management Dialog Component', () => {
        let comp: DropLocationDialogComponent;
        let fixture: ComponentFixture<DropLocationDialogComponent>;
        let service: DropLocationService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DropLocationDialogComponent],
                providers: [
                    DragAndDropQuestionService,
                    DropLocationService
                ]
            })
            .overrideTemplate(DropLocationDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DropLocationDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DropLocationService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DropLocation(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dropLocation = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dropLocationListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DropLocation();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dropLocation = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dropLocationListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
