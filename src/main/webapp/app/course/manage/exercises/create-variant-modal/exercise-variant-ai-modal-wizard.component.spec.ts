import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MockPipe } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService } from 'app/course/manage/exercises/exercise-variant-group.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { VariantGenerationRequest } from 'app/openapi/model/variantGenerationRequest';
import { QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';

/**
 * Vitest specs for the exam path of the AI variant wizard: exam exercises must place the variant into the
 * source's exam exercise group automatically (SAME_EXAM_GROUP, no placement step). All adaptation options,
 * including difficulty, stay available — an instructor may deliberately generate a harder variant of a
 * too-easy exam exercise and delete the easy one afterwards.
 */
describe('ExerciseVariantAiModalWizardComponent (exam path)', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseVariantAiModalWizardComponent>;
    let component: ExerciseVariantAiModalWizardComponent;
    let generationServiceMock: {
        startGeneration: ReturnType<typeof vi.fn>;
        jobEvents: ReturnType<typeof vi.fn>;
        getJobDetail: ReturnType<typeof vi.fn>;
        cancelJob: ReturnType<typeof vi.fn>;
    };
    let groupServiceMock: { getGroupsForCourse: ReturnType<typeof vi.fn> };

    const examExercise: Exercise = { id: 55, title: 'Sorting: Exam Edition', type: ExerciseType.PROGRAMMING, difficulty: DifficultyLevel.HARD } as Exercise;

    beforeEach(async () => {
        generationServiceMock = {
            startGeneration: vi.fn().mockReturnValue(of('job-exam')),
            jobEvents: vi.fn().mockReturnValue(of()),
            getJobDetail: vi.fn().mockReturnValue(of({ job: undefined, stepOutputs: {}, request: undefined })),
            cancelJob: vi.fn().mockReturnValue(of(undefined)),
        };
        groupServiceMock = { getGroupsForCourse: vi.fn().mockReturnValue(of([])) };

        await TestBed.configureTestingModule({
            imports: [ExerciseVariantAiModalWizardComponent],
            providers: [
                { provide: ExerciseVariantGenerationService, useValue: generationServiceMock },
                { provide: ExerciseVariantGroupService, useValue: groupServiceMock },
                { provide: ExerciseService, useValue: { find: vi.fn().mockReturnValue(of({ body: undefined })) } },
                {
                    provide: TranslateService,
                    useValue: { instant: (key: string) => key, get: (key: string) => of(key), onLangChange: of(), onTranslationChange: of(), onDefaultLangChange: of() },
                },
            ],
        })
            .overrideComponent(ExerciseVariantAiModalWizardComponent, {
                remove: { imports: [ArtemisTranslatePipe] },
                add: { imports: [MockPipe(ArtemisTranslatePipe, (key) => key)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseVariantAiModalWizardComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('sourceExercise', examExercise);
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('examExercise', true);
        fixture.componentRef.setInput('visible', true);
    });

    it('detects the exam context from the explicit input even without a nested exerciseGroup', () => {
        fixture.detectChanges();
        expect(component.isExamExercise()).toBe(true);
        // Exam context must not trigger a course variant-group lookup — placement is forced, not chosen.
        expect(groupServiceMock.getGroupsForCourse).not.toHaveBeenCalled();
    });

    it('keeps the difficulty adaptation option for exam exercises', () => {
        fixture.detectChanges();
        expect(document.body.querySelector('[data-testid="variant-option-difficulty"]')).not.toBeNull();
    });

    it('drops the placement step from the indicator for exam exercises', () => {
        fixture.detectChanges();

        // Placement is forced to SAME_EXAM_GROUP, so advertising the step would promise a choice that never comes.
        expect(component.wizardSteps().map((step) => step.label)).toEqual(['Select', 'Configure', 'Generating', 'Result']);
        expect(document.body.querySelector('[data-testid="variant-wizard-indicator-Placement"]')).toBeNull();
        expect(document.body.querySelector('[data-testid="variant-wizard-indicator-Configure"]')).not.toBeNull();
    });

    it('skips the placement step and starts generation with SAME_EXAM_GROUP', () => {
        fixture.detectChanges();
        component.changeDomain.set(true);
        component.domainText.set('space exploration');

        component.goToPlacement();

        // Never lands on the placement step (3) — jumps straight to the running view (4).
        expect(component.wizardStep()).toBe(4);
        expect(generationServiceMock.startGeneration).toHaveBeenCalledTimes(1);
        const request = generationServiceMock.startGeneration.mock.calls[0][1] as VariantGenerationRequest;
        expect(request.placement).toEqual({ type: 'SAME_EXAM_GROUP' });
    });

    it('sends the selected target difficulty for an exam exercise', () => {
        fixture.detectChanges();
        component.changeDifficulty.set(true);
        component.targetDifficulty.set(DifficultyLevel.EASY);
        component.changeDomain.set(true);
        component.domainText.set('banking');

        component.startGeneration();

        const request = generationServiceMock.startGeneration.mock.calls[0][1] as VariantGenerationRequest;
        expect(request.targetDifficulty).toBe(DifficultyLevel.EASY);
        expect(request.domainText).toBe('banking');
    });
});

/**
 * The NEW_GROUP placement promises to group the variant WITH its source. The server skips the source when it
 * cannot legally join (already grouped, or a non-individual quiz whose single shared run cannot adopt a group
 * timeline), which would silently leave a group holding only the variant — so the option must not be offered.
 */
describe('ExerciseVariantAiModalWizardComponent (new-group placement availability)', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseVariantAiModalWizardComponent>;
    let component: ExerciseVariantAiModalWizardComponent;
    let groupServiceMock: { getGroupsForCourse: ReturnType<typeof vi.fn> };

    const createWizard = async (source: Exercise, groups: ExerciseVariantGroupDTO[] = []) => {
        groupServiceMock = { getGroupsForCourse: vi.fn().mockReturnValue(of(groups)) };

        await TestBed.configureTestingModule({
            imports: [ExerciseVariantAiModalWizardComponent],
            providers: [
                {
                    provide: ExerciseVariantGenerationService,
                    useValue: {
                        startGeneration: vi.fn().mockReturnValue(of('job-1')),
                        jobEvents: vi.fn().mockReturnValue(of()),
                        getJobDetail: vi.fn().mockReturnValue(of({ job: undefined, stepOutputs: {}, request: undefined })),
                        cancelJob: vi.fn().mockReturnValue(of(undefined)),
                    },
                },
                { provide: ExerciseVariantGroupService, useValue: groupServiceMock },
                { provide: ExerciseService, useValue: { find: vi.fn().mockReturnValue(of({ body: undefined })) } },
                {
                    provide: TranslateService,
                    useValue: { instant: (key: string) => key, get: (key: string) => of(key), onLangChange: of(), onTranslationChange: of(), onDefaultLangChange: of() },
                },
            ],
        })
            .overrideComponent(ExerciseVariantAiModalWizardComponent, {
                remove: { imports: [ArtemisTranslatePipe] },
                add: { imports: [MockPipe(ArtemisTranslatePipe, (key) => key)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseVariantAiModalWizardComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('sourceExercise', source);
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
    };

    const programmingExercise = { id: 1, title: 'Sorting', type: ExerciseType.PROGRAMMING } as Exercise;

    /** The dialog renders with appendTo="body", so its content is outside the fixture's own element tree. */
    const placementOption = (testId: string) => document.body.querySelector(`[data-testid="${testId}"]`);

    afterEach(() => {
        fixture.destroy();
        TestBed.resetTestingModule();
    });

    it('keeps the placement step in the indicator for course exercises', async () => {
        await createWizard(programmingExercise);

        expect(component.wizardSteps().map((step) => step.label)).toEqual(['Select', 'Configure', 'Placement', 'Generating', 'Result']);
        expect(document.body.querySelector('[data-testid="variant-wizard-indicator-Placement"]')).not.toBeNull();
    });

    it('offers the new-group placement for an ungrouped programming exercise', async () => {
        await createWizard(programmingExercise);

        component.goToPlacement();
        fixture.detectChanges();

        expect(component.canGroupSourceWithVariant()).toBe(true);
        expect(component.placementChoice()).toBe('new-group');
        expect(placementOption('variant-placement-new-group')).not.toBeNull();
    });

    it('offers the new-group placement for an individual-mode quiz', async () => {
        await createWizard({ id: 2, title: 'Quiz', type: ExerciseType.QUIZ, quizMode: QuizMode.INDIVIDUAL } as QuizExercise);

        expect(component.canGroupSourceWithVariant()).toBe(true);
    });

    it.each([QuizMode.SYNCHRONIZED, QuizMode.BATCHED])('hides the new-group placement for a %s quiz and falls back to standalone', async (quizMode) => {
        await createWizard({ id: 3, title: 'Quiz', type: ExerciseType.QUIZ, quizMode } as QuizExercise);

        component.goToPlacement();
        fixture.detectChanges();

        expect(component.canGroupSourceWithVariant()).toBe(false);
        expect(component.placementChoice()).toBe('standalone');
        expect(placementOption('variant-placement-new-group')).toBeNull();
    });

    it('hides the new-group placement when the source already belongs to a group', async () => {
        await createWizard(programmingExercise, [{ id: 9, title: 'Sorting variants', exerciseIds: [1] } as ExerciseVariantGroupDTO]);

        component.goToPlacement();
        fixture.detectChanges();

        // The source keeps its current group, so "add to existing group" is the meaningful choice here.
        expect(component.canGroupSourceWithVariant()).toBe(false);
        expect(component.placementChoice()).toBe('existing-group');
        expect(placementOption('variant-placement-new-group')).toBeNull();
    });
});
