/* tslint:disable max-line-length */
import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArtemisTestModule } from '../../../test.module';
import { DropLocationDeleteDialogComponent } from 'app/entities/drop-location/drop-location-delete-dialog.component';
import { DropLocationService } from 'app/entities/drop-location/drop-location.service';

describe('Component Tests', () => {
    describe('DropLocation Management Delete Component', () => {
        let comp: DropLocationDeleteDialogComponent;
        let fixture: ComponentFixture<DropLocationDeleteDialogComponent>;
        let service: DropLocationService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [DropLocationDeleteDialogComponent],
            })
                .overrideTemplate(DropLocationDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(DropLocationDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(DropLocationService);
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
                }),
            ));
        });
    });
});
