/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuizExerciseDeleteDialogComponent } from 'app/entities/quiz-exercise/quiz-exercise-delete-dialog.component';
import { QuizExerciseService } from 'app/entities/quiz-exercise/quiz-exercise.service';

describe('Component Tests', () => {
    describe('QuizExercise Management Delete Component', () => {
        let comp: QuizExerciseDeleteDialogComponent;
        let fixture: ComponentFixture<QuizExerciseDeleteDialogComponent>;
        let service: QuizExerciseService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuizExerciseDeleteDialogComponent]
            })
                .overrideTemplate(QuizExerciseDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(QuizExerciseDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizExerciseService);
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
