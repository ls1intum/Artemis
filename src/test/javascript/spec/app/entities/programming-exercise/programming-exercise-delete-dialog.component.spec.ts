/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { ProgrammingExerciseDeleteDialogComponent } from 'app/entities/programming-exercise/programming-exercise-delete-dialog.component';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/programming-exercise.service';

describe('Component Tests', () => {
    describe('ProgrammingExercise Management Delete Component', () => {
        let comp: ProgrammingExerciseDeleteDialogComponent;
        let fixture: ComponentFixture<ProgrammingExerciseDeleteDialogComponent>;
        let service: ProgrammingExerciseService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ProgrammingExerciseDeleteDialogComponent]
            })
                .overrideTemplate(ProgrammingExerciseDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ProgrammingExerciseDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingExerciseService);
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
