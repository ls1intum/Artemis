/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { ExerciseDetailComponent } from '../../../../../../main/webapp/app/entities/exercise/exercise-detail.component';
import { ExerciseService } from '../../../../../../main/webapp/app/entities/exercise/exercise.service';
import { Exercise } from '../../../../../../main/webapp/app/entities/exercise/exercise.model';

describe('Component Tests', () => {

    describe('Exercise Management Detail Component', () => {
        let comp: ExerciseDetailComponent;
        let fixture: ComponentFixture<ExerciseDetailComponent>;
        let service: ExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ExerciseDetailComponent],
                providers: [
                    ExerciseService
                ]
            })
            .overrideTemplate(ExerciseDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ExerciseDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new Exercise(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.exercise).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
