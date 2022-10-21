import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CodeHintGenerationStepComponent } from 'app/exercises/programming/hestia/generation-overview/steps/code-hint-generation-step/code-hint-generation-step.component';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { CodeHintService } from 'app/exercises/shared/exercise-hint/services/code-hint.service';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';

describe('CodeHintGenerationStep Component', () => {
    let comp: CodeHintGenerationStepComponent;
    let fixture: ComponentFixture<CodeHintGenerationStepComponent>;

    let exerciseService: ProgrammingExerciseService;
    let codeHintService: CodeHintService;

    let onHintsLoadedSpy: jest.SpyInstance;

    let exercise: ProgrammingExercise;
    let codeHints: CodeHint[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
        }).compileComponents();
        fixture = TestBed.createComponent(CodeHintGenerationStepComponent);
        comp = fixture.componentInstance;

        exerciseService = TestBed.inject(ProgrammingExerciseService);
        codeHintService = TestBed.inject(CodeHintService);

        onHintsLoadedSpy = jest.spyOn(comp.onCodeHintsLoaded, 'emit');

        exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 1;
        comp.exercise = exercise;

        codeHints = [new CodeHint(), new CodeHint()];
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load all code hints on init', () => {
        const loadHintsSpy = jest.spyOn(exerciseService, 'getCodeHintsForExercise').mockReturnValue(of(codeHints));

        comp.ngOnInit();

        expect(loadHintsSpy).toHaveBeenCalledOnce();
        expect(loadHintsSpy).toHaveBeenCalledWith(1);
        expect(comp.codeHints).toEqual(codeHints);
        expect(comp.isLoading).toBeFalse();
        expect(onHintsLoadedSpy).toHaveBeenCalledOnce();
    });

    it('should generate code hints', () => {
        const generateHintsSpy = jest.spyOn(codeHintService, 'generateCodeHintsForExercise').mockReturnValue(of(codeHints));

        comp.generateCodeHints(true, 'recreateHintsButton');

        expect(generateHintsSpy).toHaveBeenCalledOnce();
        expect(generateHintsSpy).toHaveBeenCalledWith(1, true);
        expect(comp.codeHints).toEqual(codeHints);
        expect(comp.isLoading).toBeFalse();
        expect(onHintsLoadedSpy).toHaveBeenCalledOnce();
        expect(onHintsLoadedSpy).toHaveBeenCalledWith(codeHints);
    });
});
