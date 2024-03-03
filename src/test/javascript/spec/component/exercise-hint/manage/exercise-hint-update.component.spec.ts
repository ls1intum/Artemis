import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { FormBuilder, FormsModule } from '@angular/forms';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

import { ExerciseHintUpdateComponent } from 'app/exercises/shared/exercise-hint/manage/exercise-hint-update.component';
import { ArtemisTestModule } from '../../../test.module';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ActivatedRoute } from '@angular/router';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { CodeHintService } from 'app/exercises/shared/exercise-hint/services/code-hint.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { IrisSettingsService } from 'app/iris/settings/shared/iris-settings.service';
import { IrisSettings } from 'app/entities/iris/settings/iris-settings.model';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { PROFILE_IRIS } from 'app/app.constants';

describe('ExerciseHint Management Update Component', () => {
    let comp: ExerciseHintUpdateComponent;
    let fixture: ComponentFixture<ExerciseHintUpdateComponent>;
    let service: ExerciseHintService;
    let codeHintService: CodeHintService;
    let programmingExerciseService: ProgrammingExerciseService;

    const task1 = new ProgrammingExerciseServerSideTask();
    task1.id = 1;
    task1.taskName = 'Task 1';
    task1.testCases = [new ProgrammingExerciseTestCase(), new ProgrammingExerciseTestCase()];

    const task2 = new ProgrammingExerciseServerSideTask();
    task2.id = 2;
    task2.taskName = 'Task 2';
    task2.testCases = [new ProgrammingExerciseTestCase(), new ProgrammingExerciseTestCase()];

    const programmingExercise = new ProgrammingExercise(undefined, undefined);
    programmingExercise.id = 15;

    const exerciseHint = new ExerciseHint();
    const route = { data: of({ exerciseHint, exercise: programmingExercise }), params: of({ courseId: 12 }) } as any as ActivatedRoute;

    beforeEach(fakeAsync(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [ExerciseHintUpdateComponent, MockComponent(MarkdownEditorComponent), MockComponent(HelpIconComponent)],
            providers: [
                FormBuilder,
                MockProvider(ProgrammingExerciseService),
                MockProvider(ExerciseHintService),
                MockProvider(TranslateService),
                MockProvider(IrisSettingsService),
                MockProfileService,
                { provide: ActivatedRoute, useValue: route },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseHintUpdateComponent);
                comp = fixture.componentInstance;

                service = TestBed.inject(ExerciseHintService);
                codeHintService = TestBed.inject(CodeHintService);
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

        comp.ngOnInit();

        expect(comp.exercise?.id).toBe(15);
        expect(comp.exerciseHint).toEqual(exerciseHint);
        expect(comp.courseId).toBe(12);
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

    it('should update description and content using iris', fakeAsync(() => {
        // GIVEN
        const codeHint1 = new CodeHint();
        codeHint1.id = 123;
        codeHint1.programmingExerciseTask = task2;
        codeHint1.solutionEntries = [new ProgrammingExerciseSolutionEntry()];
        const codeHint2 = new CodeHint();
        codeHint2.id = 123;
        codeHint2.programmingExerciseTask = task2;
        codeHint2.content = 'Hello Content';
        codeHint2.description = 'Hello Description';

        jest.spyOn(codeHintService, 'generateDescriptionForCodeHint').mockReturnValue(of(new HttpResponse({ body: codeHint2 })));
        comp.exerciseHint = codeHint1;
        comp.courseId = 1;
        comp.exercise = programmingExercise;

        // WHEN
        comp.generateDescriptionForCodeHint();
        tick();

        // THEN
        expect(codeHintService.generateDescriptionForCodeHint).toHaveBeenCalledWith(15, 123);
        expect(comp.isGeneratingDescription).toBeFalse();
        expect(comp.exerciseHint.content).toBe('Hello Content');
        expect(comp.exerciseHint.description).toBe('Hello Description');
    }));

    it.each<[string[]]>([[[]], [[PROFILE_IRIS]]])(
        'should load iris settings only if profile iris is active',
        fakeAsync((activeProfiles: string[]) => {
            // Mock getProfileInfo to return activeProfiles
            const profileService = TestBed.inject(ProfileService);
            jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(of({ activeProfiles } as any as ProfileInfo));

            // Mock getTasksAndTestsExtractedFromProblemStatement
            jest.spyOn(programmingExerciseService, 'getTasksAndTestsExtractedFromProblemStatement').mockReturnValue(of([]));

            const fakeSettings = {} as any as IrisSettings;

            // Mock getCombinedProgrammingExerciseSettings
            const irisSettingsService = TestBed.inject(IrisSettingsService);
            const getCombinedProgrammingExerciseSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedProgrammingExerciseSettings').mockReturnValue(of(fakeSettings));

            // Run ngOnInit
            comp.ngOnInit();
            tick();

            if (activeProfiles.includes(PROFILE_IRIS)) {
                // Should have called getCombinedProgrammingExerciseSettings if 'iris' is active
                expect(getCombinedProgrammingExerciseSettingsSpy).toHaveBeenCalledOnce();
                expect(comp.irisSettings).toBe(fakeSettings);
            } else {
                // Should not have called getCombinedProgrammingExerciseSettings if 'iris' is not active
                expect(getCombinedProgrammingExerciseSettingsSpy).not.toHaveBeenCalled();
                expect(comp.irisSettings).toBeUndefined();
            }
        }),
    );

    it('should generate descriptions', () => {
        const codeHint = new CodeHint();
        codeHint.id = 123;
        codeHint.programmingExerciseTask = task2;
        codeHint.solutionEntries = [new ProgrammingExerciseSolutionEntry()];

        comp.exerciseHint = codeHint;
        comp.courseId = 1;
        comp.exercise = programmingExercise;

        const generateDescSpy = jest.spyOn(codeHintService, 'generateDescriptionForCodeHint');

        comp.generateDescriptionForCodeHint();

        expect(generateDescSpy).toHaveBeenCalledOnce();
        expect(generateDescSpy).toHaveBeenCalledWith(15, 123);
    });

    describe('save', () => {
        it('should call update service on save for existing entity', fakeAsync(() => {
            // GIVEN
            const entity = new ExerciseHint();
            entity.id = 123;
            entity.programmingExerciseTask = task2;
            jest.spyOn(service, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.exerciseHint = entity;
            comp.courseId = 1;
            comp.exercise = programmingExercise;

            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.update).toHaveBeenCalledWith(15, entity);
            expect(comp.isSaving).toBeFalse();
        }));

        it('should call create service on save for new entity', fakeAsync(() => {
            // GIVEN
            const entity = new ExerciseHint();
            entity.programmingExerciseTask = task2;
            jest.spyOn(service, 'create').mockReturnValue(of(new HttpResponse({ body: entity })));
            comp.exerciseHint = entity;
            comp.courseId = 1;
            comp.exercise = programmingExercise;

            // WHEN
            comp.save();
            tick(); // simulate async

            // THEN
            expect(service.create).toHaveBeenCalledWith(15, entity);
            expect(comp.isSaving).toBeFalse();
        }));
    });
});
