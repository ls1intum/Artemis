/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuizExerciseComponent } from 'app/entities/quiz-exercise/quiz-exercise.component';
import { QuizExerciseService } from 'app/entities/quiz-exercise/quiz-exercise.service';
import { QuizExercise } from 'app/shared/model/quiz-exercise.model';

describe('Component Tests', () => {
    describe('QuizExercise Management Component', () => {
        let comp: QuizExerciseComponent;
        let fixture: ComponentFixture<QuizExerciseComponent>;
        let service: QuizExerciseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuizExerciseComponent],
                providers: []
            })
                .overrideTemplate(QuizExerciseComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(QuizExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizExerciseService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new QuizExercise(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.quizExercises[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
