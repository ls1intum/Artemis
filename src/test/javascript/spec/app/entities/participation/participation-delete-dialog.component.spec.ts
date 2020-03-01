/* tslint:disable max-line-length */
import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArtemisTestModule } from '../../../test.module';
import { ParticipationDeleteDialogComponent } from 'app/exercises/shared/participation/participation-delete-dialog.component';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

describe('Component Tests', () => {
    describe('Participation Management Delete Component', () => {
        let comp: ParticipationDeleteDialogComponent;
        let fixture: ComponentFixture<ParticipationDeleteDialogComponent>;
        let service: ParticipationService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ParticipationDeleteDialogComponent],
            })
                .overrideTemplate(ParticipationDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ParticipationDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ParticipationService);
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
