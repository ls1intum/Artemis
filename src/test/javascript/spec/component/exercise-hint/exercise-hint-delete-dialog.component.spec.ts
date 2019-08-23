/* tslint:disable max-line-length */
import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArtemisTestModule } from '../../test.module';
import { ExerciseHintDeleteDialogComponent } from 'app/entities/exercise-hint/exercise-hint-delete-dialog.component';
import { ExerciseHintService } from 'app/entities/exercise-hint/exercise-hint.service';

describe('Component Tests', () => {
    describe('ExerciseHint Management Delete Component', () => {
        let comp: ExerciseHintDeleteDialogComponent;
        let fixture: ComponentFixture<ExerciseHintDeleteDialogComponent>;
        let service: ExerciseHintService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ExerciseHintDeleteDialogComponent],
            })
                .overrideTemplate(ExerciseHintDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ExerciseHintDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ExerciseHintService);
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
