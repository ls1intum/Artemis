/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { ProgrammingExerciseDetailComponent } from '../../../../../../main/webapp/app/entities/programming-exercise/programming-exercise-detail.component';
import { ProgrammingExerciseService } from '../../../../../../main/webapp/app/entities/programming-exercise/programming-exercise.service';
import { ProgrammingExercise } from '../../../../../../main/webapp/app/entities/programming-exercise/programming-exercise.model';

describe('Component Tests', () => {

    describe('ProgrammingExercise Management Detail Component', () => {
        let comp: ProgrammingExerciseDetailComponent;
        let fixture: ComponentFixture<ProgrammingExerciseDetailComponent>;
        let service: ProgrammingExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ProgrammingExerciseDetailComponent],
                providers: [
                    ProgrammingExerciseService
                ]
            })
            .overrideTemplate(ProgrammingExerciseDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ProgrammingExerciseDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new ProgrammingExercise(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.programmingExercise).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
