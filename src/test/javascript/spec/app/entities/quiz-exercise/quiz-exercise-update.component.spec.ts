/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuizExerciseUpdateComponent } from 'app/entities/quiz-exercise/quiz-exercise-update.component';
import { QuizExerciseService } from 'app/entities/quiz-exercise/quiz-exercise.service';
import { QuizExercise } from 'app/shared/model/quiz-exercise.model';

describe('Component Tests', () => {
    describe('QuizExercise Management Update Component', () => {
        let comp: QuizExerciseUpdateComponent;
        let fixture: ComponentFixture<QuizExerciseUpdateComponent>;
        let service: QuizExerciseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuizExerciseUpdateComponent]
            })
                .overrideTemplate(QuizExerciseUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(QuizExerciseUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizExerciseService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new QuizExercise(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.quizExercise = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.update).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );

            it(
                'Should call create service on save for new entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new QuizExercise();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.quizExercise = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.create).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );
        });
    });
});
