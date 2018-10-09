/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { ModelingExerciseUpdateComponent } from 'app/entities/modeling-exercise/modeling-exercise-update.component';
import { ModelingExerciseService } from 'app/entities/modeling-exercise/modeling-exercise.service';
import { ModelingExercise } from 'app/shared/model/modeling-exercise.model';

describe('Component Tests', () => {
    describe('ModelingExercise Management Update Component', () => {
        let comp: ModelingExerciseUpdateComponent;
        let fixture: ComponentFixture<ModelingExerciseUpdateComponent>;
        let service: ModelingExerciseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [ModelingExerciseUpdateComponent]
            })
                .overrideTemplate(ModelingExerciseUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ModelingExerciseUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ModelingExerciseService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ModelingExercise(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.modelingExercise = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ModelingExercise();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.modelingExercise = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));
        });
    });
});
