/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTeMiSTestModule } from '../../../test.module';
import { ModelingExerciseDetailComponent } from '../../../../../../main/webapp/app/entities/modeling-exercise/modeling-exercise-detail.component';
import { ModelingExerciseService } from '../../../../../../main/webapp/app/entities/modeling-exercise/modeling-exercise.service';
import { ModelingExercise } from '../../../../../../main/webapp/app/entities/modeling-exercise/modeling-exercise.model';

describe('Component Tests', () => {

    describe('ModelingExercise Management Detail Component', () => {
        let comp: ModelingExerciseDetailComponent;
        let fixture: ComponentFixture<ModelingExerciseDetailComponent>;
        let service: ModelingExerciseService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ModelingExerciseDetailComponent],
                providers: [
                    ModelingExerciseService
                ]
            })
            .overrideTemplate(ModelingExerciseDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(ModelingExerciseDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ModelingExerciseService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new ModelingExercise(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.modelingExercise).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
