/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { TextExerciseUpdateComponent } from 'app/entities/text-exercise/text-exercise-update.component';
import { TextExerciseService } from 'app/entities/text-exercise/text-exercise.service';
import { TextExercise } from 'app/shared/model/text-exercise.model';

describe('Component Tests', () => {
    describe('TextExercise Management Update Component', () => {
        let comp: TextExerciseUpdateComponent;
        let fixture: ComponentFixture<TextExerciseUpdateComponent>;
        let service: TextExerciseService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [TextExerciseUpdateComponent]
            })
                .overrideTemplate(TextExerciseUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(TextExerciseUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(TextExerciseService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new TextExercise(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.textExercise = entity;
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
                    const entity = new TextExercise();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.textExercise = entity;
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
