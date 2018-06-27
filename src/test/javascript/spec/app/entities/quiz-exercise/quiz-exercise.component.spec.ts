/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuizExerciseComponent } from '../../../../../../main/webapp/app/entities/quiz-exercise/quiz-exercise.component';
import { QuizExerciseService } from '../../../../../../main/webapp/app/entities/quiz-exercise/quiz-exercise.service';
import { QuizExercise } from '../../../../../../main/webapp/app/entities/quiz-exercise/quiz-exercise.model';

describe('Component Tests', () => {

    describe('QuizExercise Management Component', () => {
        let comp: QuizExerciseComponent;
        let fixture: ComponentFixture<QuizExerciseComponent>;
        let service: QuizExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuizExerciseComponent],
                providers: [
                    QuizExerciseService
                ]
            })
            .overrideTemplate(QuizExerciseComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuizExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new QuizExercise(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.quizExercises[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
