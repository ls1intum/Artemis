/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { DragItemDeleteDialogComponent } from 'app/entities/drag-item/drag-item-delete-dialog.component';
import { DragItemService } from 'app/entities/drag-item/drag-item.service';

describe('Component Tests', () => {
    describe('DragItem Management Delete Component', () => {
        let comp: DragItemDeleteDialogComponent;
        let fixture: ComponentFixture<DragItemDeleteDialogComponent>;
        let service: DragItemService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [DragItemDeleteDialogComponent]
            })
                .overrideTemplate(DragItemDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DragItemDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DragItemService);
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
