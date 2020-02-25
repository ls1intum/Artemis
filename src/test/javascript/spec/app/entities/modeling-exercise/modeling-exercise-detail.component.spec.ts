/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../../test.module';
import { ModelingExerciseDetailComponent } from 'app/exercises/modeling/manage/modeling-exercise-detail.component';
import { ModelingExercise } from 'app/shared/model/modeling-exercise.model';

describe('Component Tests', () => {
    describe('ModelingExercise Management Detail Component', () => {
        let comp: ModelingExerciseDetailComponent;
        let fixture: ComponentFixture<ModelingExerciseDetailComponent>;
        const route = ({ data: of({ modelingExercise: new ModelingExercise(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ModelingExerciseDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }],
            })
                .overrideTemplate(ModelingExerciseDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ModelingExerciseDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.modelingExercise).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
