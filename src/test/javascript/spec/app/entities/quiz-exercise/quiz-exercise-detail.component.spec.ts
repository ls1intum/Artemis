/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuizExerciseDetailComponent } from '../../../../../../main/webapp/app/entities/quiz-exercise/quiz-exercise-detail.component';
import { QuizExerciseService } from '../../../../../../main/webapp/app/entities/quiz-exercise/quiz-exercise.service';
import { QuizExercise } from '../../../../../../main/webapp/app/entities/quiz-exercise/quiz-exercise.model';

describe('Component Tests', () => {

    describe('QuizExercise Management Detail Component', () => {
        let comp: QuizExerciseDetailComponent;
        let fixture: ComponentFixture<QuizExerciseDetailComponent>;
        let service: QuizExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuizExerciseDetailComponent],
                providers: [
                    QuizExerciseService
                ]
            })
            .overrideTemplate(QuizExerciseDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuizExerciseDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new QuizExercise(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.quizExercise).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
