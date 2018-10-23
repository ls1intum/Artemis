/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ExerciseResultUpdateComponent } from 'app/entities/exercise-result/exercise-result-update.component';
import { ExerciseResultService } from 'app/entities/exercise-result/exercise-result.service';
import { ExerciseResult } from 'app/shared/model/exercise-result.model';

describe('Component Tests', () => {
    describe('ExerciseResult Management Update Component', () => {
        let comp: ExerciseResultUpdateComponent;
        let fixture: ComponentFixture<ExerciseResultUpdateComponent>;
        let service: ExerciseResultService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ExerciseResultUpdateComponent]
            })
                .overrideTemplate(ExerciseResultUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ExerciseResultUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ExerciseResultService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new ExerciseResult(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.exerciseResult = entity;
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
                    const entity = new ExerciseResult();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.exerciseResult = entity;
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
