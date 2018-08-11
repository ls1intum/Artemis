/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { DragAndDropMappingDeleteDialogComponent } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping-delete-dialog.component';
import { DragAndDropMappingService } from '../../../../../../main/webapp/app/entities/drag-and-drop-mapping/drag-and-drop-mapping.service';

describe('Component Tests', () => {

    describe('DragAndDropMapping Management Delete Component', () => {
        let comp: DragAndDropMappingDeleteDialogComponent;
        let fixture: ComponentFixture<DragAndDropMappingDeleteDialogComponent>;
        let service: DragAndDropMappingService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DragAndDropMappingDeleteDialogComponent],
                providers: [
                    DragAndDropMappingService
                ]
            })
            .overrideTemplate(DragAndDropMappingDeleteDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(DragAndDropMappingDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragAndDropMappingService);
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
