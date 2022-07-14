import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
import { FormBuilder, FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ExerciseHintUpdateComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-update.component';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ActivatedRoute } from '@angular/router';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';

describe('ExerciseHint Management Update Component', () => {
    let comp: ExerciseHintUpdateComponent;
    let fixture: ComponentFixture<ExerciseHintUpdateComponent>;
    let service: ExerciseHintService;
    let programmingExerciseService: ProgrammingExerciseService;

    const task1 = new ProgrammingExerciseServerSideTask();
    task1.id = 1;
    task1.taskName = 'Task 1';
    task1.testCases = [new ProgrammingExerciseTestCase(), new ProgrammingExerciseTestCase()];

    const task2 = new ProgrammingExerciseServerSideTask();
    task2.id = 2;
    task2.taskName = 'Task 2';
    task2.testCases = [new ProgrammingExerciseTestCase(), new ProgrammingExerciseTestCase()];

    const exerciseHint = new ExerciseHint();
    const route = { data: of({ exerciseHint }), params: of({ courseId: 12, exerciseId: 15 }) } as any as ActivatedRoute;

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [ExerciseHintUpdateComponent, MockComponent(MarkdownEditorComponent)],
            providers: [
                FormBuilder,
                MockProvider(ProgrammingExerciseService),
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
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                flush();
            });
    }));

    afterEach(() => {
        exerciseHint.programmingExerciseTask = undefined;
        jest.restoreAllMocks();
    });

    it('should load params and data onInit', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.id = 15;
        const headers = new HttpHeaders().append('link', 'link;link');
        const findByExerciseIdSpy = jest.spyOn(programmingExerciseService, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: exercise,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();

        expect(findByExerciseIdSpy).toHaveBeenCalledOnce();
        expect(findByExerciseIdSpy).toHaveBeenCalledWith(15);
        expect(comp.exerciseHint.exercise).toEqual(exercise);
        expect(comp.exerciseHint).toEqual(exerciseHint);
        expect(comp.courseId).toBe(12);
        expect(comp.exerciseId).toBe(15);
    });

    it('should load and set tasks for exercise hint', fakeAsync(() => {
        exerciseHint.id = 4;
        comp.exerciseHint = exerciseHint;
        const getTasksSpy = jest.spyOn(programmingExerciseService, 'getTasksAndTestsExtractedFromProblemStatement').mockReturnValue(of([task1, task2]));
        comp.exerciseHint.programmingExerciseTask = task2;

        comp.ngOnInit();

        expect(getTasksSpy).toHaveBeenCalledOnce();
        expect(getTasksSpy).toHaveBeenCalledWith(15);
        tick();
        expect(comp.tasks).toEqual([task1, task2]);
        expect(comp.exerciseHint.programmingExerciseTask).toEqual(task2);
    }));

    it('should load and set first tasks to exercise hint', fakeAsync(() => {
        comp.exerciseHint = exerciseHint;
        const getTasksSpy = jest.spyOn(programmingExerciseService, 'getTasksAndTestsExtractedFromProblemStatement').mockReturnValue(of([task1, task2]));

        comp.ngOnInit();

        expect(getTasksSpy).toHaveBeenCalledOnce();
        expect(getTasksSpy).toHaveBeenCalledWith(15);
        tick();
        expect(comp.tasks).toEqual([task1, task2]);
        expect(comp.exerciseHint.programmingExerciseTask).toEqual(task1);
    }));

    describe('save', () => {
        it('Should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new ExerciseHint();
            entity.id = 123;
            entity.programmingExerciseTask = task2;
            jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.exerciseHint = entity;
            comp.courseId = 1;
            comp.exerciseId = 2;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.update).toHaveBeenCalledWith(2, entity);
            expect(comp.isSaving).toBeFalse();
        }));

        it('Should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new ExerciseHint();
            entity.programmingExerciseTask = task2;
            jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.exerciseHint = entity;
            comp.courseId = 1;
            comp.exerciseId = 2;
            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(2, entity);
            expect(comp.isSaving).toBeFalse();
        }));
    });
});
