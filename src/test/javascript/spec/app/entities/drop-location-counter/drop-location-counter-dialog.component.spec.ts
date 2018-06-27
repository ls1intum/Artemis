/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { DropLocationCounterDialogComponent } from '../../../../../../main/webapp/app/entities/drop-location-counter/drop-location-counter-dialog.component';
import { DropLocationCounterService } from '../../../../../../main/webapp/app/entities/drop-location-counter/drop-location-counter.service';
import { DropLocationCounter } from '../../../../../../main/webapp/app/entities/drop-location-counter/drop-location-counter.model';
import { DropLocationService } from '../../../../../../main/webapp/app/entities/drop-location';
import { DragAndDropQuestionStatisticService } from '../../../../../../main/webapp/app/entities/drag-and-drop-question-statistic';

describe('Component Tests', () => {

    describe('DropLocationCounter Management Dialog Component', () => {
        let comp: DropLocationCounterDialogComponent;
        let fixture: ComponentFixture<DropLocationCounterDialogComponent>;
        let service: DropLocationCounterService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DropLocationCounterDialogComponent],
                providers: [
                    DropLocationService,
                    DragAndDropQuestionStatisticService,
                    DropLocationCounterService
                ]
            })
            .overrideTemplate(DropLocationCounterDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DropLocationCounterDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DropLocationCounterService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DropLocationCounter(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dropLocationCounter = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dropLocationCounterListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new DropLocationCounter();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.dropLocationCounter = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'dropLocationCounterListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
