import { ComponentFixture, fakeAsync, TestBed, tick, flush } from '@angular/core/testing';
import { FormBuilder, FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ExerciseHintUpdateComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-update.component';
import { ArtemisTestModule } from '../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/manage/exercise-hint.service';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { ActivatedRoute } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';

describe('ExerciseHint Management Update Component', () => {
    let comp: ExerciseHintUpdateComponent;
    let fixture: ComponentFixture<ExerciseHintUpdateComponent>;
    let service: ExerciseHintService;
    let exerciseService: ExerciseService;
    const exerciseHint = new ExerciseHint();
    const route = { data: of({ exerciseHint }), params: of({ courseId: 12, exerciseId: 15 }) } as any as ActivatedRoute;

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [ExerciseHintUpdateComponent, MockComponent(MarkdownEditorComponent)],
            providers: [
                FormBuilder,
                MockProvider(ExerciseService),
                MockProvider(ExerciseHintService),
                MockProvider(TranslateService),
                { provide: ActivatedRoute, useValue: route },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseHintUpdateComponent);
                comp = fixture.componentInstance;

                service = TestBed.inject(ExerciseHintService);
                exerciseService = TestBed.inject(ExerciseService);
                flush();
            });
    }));

    it('should load params and data onInit', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.id = 15;
        const headers = new HttpHeaders().append('link', 'link;link');
        const findByExerciseIdSpy = jest.spyOn(exerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: exercise,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();

        expect(findByExerciseIdSpy).toHaveBeenCalledTimes(1);
        expect(findByExerciseIdSpy).toHaveBeenCalledWith(15);
        expect(comp.exerciseHint.exercise).toEqual(exercise);
        expect(comp.exerciseHint).toEqual(exerciseHint);
        expect(comp.courseId).toBe(12);
        expect(comp.exerciseId).toBe(15);
    });

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new ExerciseHint();
            entity.id = 123;
            jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.exerciseHint = entity;
            comp.courseId = 1;
            comp.exerciseId = 2;
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
            jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.exerciseHint = entity;
            comp.courseId = 1;
            comp.exerciseId = 2;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toEqual(false);
        }));
    });
});
