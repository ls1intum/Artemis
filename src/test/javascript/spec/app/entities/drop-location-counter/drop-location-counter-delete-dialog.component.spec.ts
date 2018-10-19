/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { DropLocationCounterDeleteDialogComponent } from 'app/entities/drop-location-counter/drop-location-counter-delete-dialog.component';
import { DropLocationCounterService } from 'app/entities/drop-location-counter/drop-location-counter.service';

describe('Component Tests', () => {
    describe('DropLocationCounter Management Delete Component', () => {
        let comp: DropLocationCounterDeleteDialogComponent;
        let fixture: ComponentFixture<DropLocationCounterDeleteDialogComponent>;
        let service: DropLocationCounterService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [DropLocationCounterDeleteDialogComponent]
            })
                .overrideTemplate(DropLocationCounterDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DropLocationCounterDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DropLocationCounterService);
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
