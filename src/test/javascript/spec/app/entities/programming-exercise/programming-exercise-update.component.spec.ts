/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ProgrammingExerciseUpdateComponent } from 'app/entities/programming-exercise/programming-exercise-update.component';
import { ProgrammingExerciseService } from 'app/entities/programming-exercise/programming-exercise.service';
import { ProgrammingExercise } from 'app/shared/model/programming-exercise.model';

describe('Component Tests', () => {
    describe('ProgrammingExercise Management Update Component', () => {
        let comp: ProgrammingExerciseUpdateComponent;
        let fixture: ComponentFixture<ProgrammingExerciseUpdateComponent>;
        let service: ProgrammingExerciseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ProgrammingExerciseUpdateComponent]
            })
                .overrideTemplate(ProgrammingExerciseUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ProgrammingExerciseUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ProgrammingExerciseService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new ProgrammingExercise(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.programmingExercise = entity;
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
                    const entity = new ProgrammingExercise();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.programmingExercise = entity;
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
