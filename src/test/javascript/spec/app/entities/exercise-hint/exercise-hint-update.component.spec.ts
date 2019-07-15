/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { FormBuilder } from '@angular/forms';
import { Observable, of } from 'rxjs';

import { ArtemisTestModule } from '../../../test.module';
import { ExerciseHintUpdateComponent } from 'app/entities/exercise-hint/exercise-hint-update.component';
import { ExerciseHintService } from 'app/entities/exercise-hint/exercise-hint.service';
import { ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';

describe('Component Tests', () => {
    describe('ExerciseHint Management Update Component', () => {
        let comp: ExerciseHintUpdateComponent;
        let fixture: ComponentFixture<ExerciseHintUpdateComponent>;
        let service: ExerciseHintService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule],
                declarations: [ExerciseHintUpdateComponent],
                providers: [FormBuilder],
            })
                .overrideTemplate(ExerciseHintUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ExerciseHintUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ExerciseHintService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ExerciseHint(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.updateForm(entity);
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ExerciseHint();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.updateForm(entity);
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
