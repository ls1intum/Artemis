/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ModelingExerciseComponent } from 'app/entities/modeling-exercise/modeling-exercise.component';
import { ModelingExerciseService } from 'app/entities/modeling-exercise/modeling-exercise.service';
import { ModelingExercise } from 'app/shared/model/modeling-exercise.model';

describe('Component Tests', () => {
    describe('ModelingExercise Management Component', () => {
        let comp: ModelingExerciseComponent;
        let fixture: ComponentFixture<ModelingExerciseComponent>;
        let service: ModelingExerciseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ModelingExerciseComponent],
                providers: []
            })
                .overrideTemplate(ModelingExerciseComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ModelingExerciseComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ModelingExerciseService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ModelingExercise(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.modelingExercises[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
