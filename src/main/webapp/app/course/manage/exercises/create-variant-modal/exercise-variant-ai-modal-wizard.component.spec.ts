import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { NEVER, of } from 'rxjs';
import { MockPipe } from 'ng-mocks';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService } from 'app/course/manage/exercises/exercise-variant-group.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { VariantGenerationRequest } from 'app/openapi/model/variant-generation-request';
import { QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';

/** The wizard owns the "Open in Editor" navigation, so every spec needs a router. */
const routerMock = { navigate: vi.fn() };

/**
 * Vitest specs for the exam path of the AI variant wizard: exam exercises must place the variant into the
 * source's exam exercise group automatically (SAME_EXAM_GROUP, no placement step). All adaptation options,
 * including difficulty, stay available — an instructor may deliberately generate a harder variant of a
 * too-easy exam exercise and delete the easy one afterwards.
 */
describe('ExerciseVariantAiModalWizardComponent (exam path)', () => {
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
                { provide: Router, useValue: routerMock },
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
        expect(component.wizardSteps().map((step) => step.id)).toEqual(['Select', 'Configure', 'Generating', 'Result']);
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
    let fixture: ComponentFixture<ExerciseVariantAiModalWizardComponent>;
    let component: ExerciseVariantAiModalWizardComponent;
    let groupServiceMock: { getGroupsForCourse: ReturnType<typeof vi.fn> };

    const createWizard = async (source: Exercise, groups: ExerciseVariantGroupDTO[] = []) => {
        groupServiceMock = { getGroupsForCourse: vi.fn().mockReturnValue(of(groups)) };

        await TestBed.configureTestingModule({
            imports: [ExerciseVariantAiModalWizardComponent],
            providers: [
                { provide: Router, useValue: routerMock },
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

        expect(component.wizardSteps().map((step) => step.id)).toEqual(['Select', 'Configure', 'Placement', 'Generating', 'Result']);
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

/**
 * Storytelling: the narrative-style adaptation option. Unselected means "stay consistent with the source's
 * narrative" (server-side default), so the request must carry the style only when the card is selected.
 */
describe('ExerciseVariantAiModalWizardComponent (storytelling)', () => {
    let fixture: ComponentFixture<ExerciseVariantAiModalWizardComponent>;
    let component: ExerciseVariantAiModalWizardComponent;
    let generationServiceMock: { startGeneration: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        generationServiceMock = { startGeneration: vi.fn().mockReturnValue(of('job-1')) };

        await TestBed.configureTestingModule({
            imports: [ExerciseVariantAiModalWizardComponent],
            providers: [
                { provide: Router, useValue: routerMock },
                {
                    provide: ExerciseVariantGenerationService,
                    useValue: {
                        ...generationServiceMock,
                        jobEvents: vi.fn().mockReturnValue(of()),
                        getJobDetail: vi.fn().mockReturnValue(of({ job: undefined, stepOutputs: {}, request: undefined })),
                        cancelJob: vi.fn().mockReturnValue(of(undefined)),
                    },
                },
                { provide: ExerciseVariantGroupService, useValue: { getGroupsForCourse: vi.fn().mockReturnValue(of([])) } },
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
        fixture.componentRef.setInput('sourceExercise', { id: 1, title: 'Sorting', type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        TestBed.resetTestingModule();
    });

    it('offers the storytelling option card and accepts it as the only selection', () => {
        expect(document.body.querySelector('[data-testid="variant-option-narrative"]')).not.toBeNull();

        component.toggleNarrative();

        expect(component.anyCardSelected()).toBe(true);
        expect(component.canProceedToPlacement()).toBe(true);
    });

    it('renders all narrative styles with a tooltip description in the configure step', () => {
        component.toggleNarrative();
        component.goToStep2();
        fixture.detectChanges();

        for (const style of component.narrativeStyles) {
            expect(document.body.querySelector(`[data-testid="variant-narrative-${style.value}"]`)).not.toBeNull();
            // Label and tooltip are translated, so the option only has to carry the keys — the i18n files own the text.
            expect(style.labelKey).toBe(`artemisApp.exerciseVariantGeneration.wizard.narrative.${style.value}`);
            expect(style.descriptionKey).toBe(`artemisApp.exerciseVariantGeneration.wizard.narrative.${style.value}_DESCRIPTION`);
        }
    });

    it('sends the selected narrative style with the request', () => {
        component.changeNarrative.set(true);
        component.narrativeStyle.set('IMAGINATIVE');

        component.startGeneration();

        const request = generationServiceMock.startGeneration.mock.calls[0][1] as VariantGenerationRequest;
        expect(request.narrativeStyle).toBe('IMAGINATIVE');
    });

    it('omits the narrative style when the card is not selected (consistent-with-source default)', () => {
        component.changeDomain.set(true);
        component.domainText.set('banking');

        component.startGeneration();

        const request = generationServiceMock.startGeneration.mock.calls[0][1] as VariantGenerationRequest;
        expect(request.narrativeStyle).toBeUndefined();
    });
});

/**
 * Step-output history: a phase visited several times (verify/repair attempts) keeps every message. Expanding a
 * step must show ALL messages, each in its own scrollable code field, oldest first (latest at the bottom) — an
 * instructor debugging a job that failed twice and then succeeded needs the earlier errors, not just the last.
 */
describe('ExerciseVariantAiModalWizardComponent (step-output history)', () => {
    let fixture: ComponentFixture<ExerciseVariantAiModalWizardComponent>;
    let component: ExerciseVariantAiModalWizardComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseVariantAiModalWizardComponent],
            providers: [
                { provide: Router, useValue: routerMock },
                {
                    provide: ExerciseVariantGenerationService,
                    useValue: {
                        startGeneration: vi.fn().mockReturnValue(of('job-1')),
                        jobEvents: vi.fn().mockReturnValue(of()),
                        getJobDetail: vi.fn().mockReturnValue(
                            of({
                                job: { jobId: 'job-1', phase: 'VERIFYING', sourceExerciseTitle: 'Sorting', exerciseType: 'programming' },
                                stepOutputs: {
                                    VERIFYING: [
                                        { summary: '2 finding(s) — attempt 1/3', detail: 'compiler error: CargoBay.java:12' },
                                        { summary: 'All gates green', detail: 'solution build passed' },
                                    ],
                                },
                                request: undefined,
                            }),
                        ),
                        cancelJob: vi.fn().mockReturnValue(of(undefined)),
                    },
                },
                { provide: ExerciseVariantGroupService, useValue: { getGroupsForCourse: vi.fn().mockReturnValue(of([])) } },
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
        fixture.componentRef.setInput('monitorJobId', 'job-1');
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        TestBed.resetTestingModule();
    });

    it('shows the latest summary collapsed and every message expanded, latest at the bottom', () => {
        const toggle = document.body.querySelector('[data-testid="variant-wizard-step-output-toggle"]');
        expect(toggle?.textContent).toContain('All gates green');
        expect(document.body.querySelectorAll('[data-testid="variant-wizard-step-output-detail"]')).toHaveLength(0);

        component.toggleStepOutput('VERIFYING');
        fixture.detectChanges();

        const details = Array.from(document.body.querySelectorAll('[data-testid="variant-wizard-step-output-detail"]'));
        expect(details).toHaveLength(2);
        expect(details[0].textContent).toContain('compiler error: CargoBay.java:12');
        expect(details[1].textContent).toContain('solution build passed');
    });

    it('appends live STEP_OUTPUT messages to the phase history instead of replacing them', () => {
        component.stepOutputs.set({ VERIFYING: [{ summary: 'first', detail: 'first detail' }] });

        component['recordStepOutput']('VERIFYING', { summary: 'second', detail: 'second detail' });

        expect(component.stepOutputs()['VERIFYING'].map((output) => output.summary)).toEqual(['first', 'second']);
    });
});

/**
 * Parallel generation: several variants of the same exercise may be generated at once. Reopening "Create Variant
 * with AI" after starting a generation (and hiding the modal to let it run in the background) must present a fresh
 * step-1 wizard for a NEW generation — not the running job's progress, which is monitored from the navbar tray.
 */
describe('ExerciseVariantAiModalWizardComponent (fresh reopen for parallel generation)', () => {
    let fixture: ComponentFixture<ExerciseVariantAiModalWizardComponent>;
    let component: ExerciseVariantAiModalWizardComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseVariantAiModalWizardComponent],
            providers: [
                { provide: Router, useValue: routerMock },
                {
                    provide: ExerciseVariantGenerationService,
                    useValue: {
                        startGeneration: vi.fn().mockReturnValue(of('job-1')),
                        // Never emits, so the job stays "running" at step 4 after start.
                        jobEvents: vi.fn().mockReturnValue(NEVER),
                        getJobDetail: vi.fn().mockReturnValue(of({ job: undefined, stepOutputs: {}, request: undefined })),
                        cancelJob: vi.fn().mockReturnValue(of(undefined)),
                    },
                },
                { provide: ExerciseVariantGroupService, useValue: { getGroupsForCourse: vi.fn().mockReturnValue(of([])) } },
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
        fixture.componentRef.setInput('sourceExercise', { id: 1, title: 'Sorting', type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        TestBed.resetTestingModule();
    });

    it('opens a fresh step-1 wizard when reopened while a background generation of the same exercise runs', () => {
        // Start a generation and let it run: the wizard sits on the running view (step 4) with a job attached.
        component.changeDomain.set(true);
        component.domainText.set('space');
        component.startGeneration();
        fixture.detectChanges();
        expect(component.wizardStep()).toBe(4);
        expect(component.jobId()).toBe('job-1');
        expect(component.isRunning()).toBe(true);

        // Hide the modal to run in the background (job keeps running), then reopen via the button.
        component.onClose(false);
        fixture.componentRef.setInput('visible', false);
        fixture.detectChanges();
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();

        // Fresh wizard: step 1, no attached job, form cleared — ready to start a second parallel generation.
        expect(component.wizardStep()).toBe(1);
        expect(component.jobId()).toBeUndefined();
        expect(component.changeDomain()).toBe(false);
        expect(component.domainText()).toBe('');
    });
});

/**
 * "Open in Editor" navigates from inside the wizard rather than through an output the hosts bind: the wizard is
 * mounted from five places, and a host that forgot the binding silently degraded the button into "close the
 * dialog". These specs pin the routes for course and exam variants, plus the flagged-draft guidance block.
 */
describe('ExerciseVariantAiModalWizardComponent (open in editor & flagged drafts)', () => {
    let fixture: ComponentFixture<ExerciseVariantAiModalWizardComponent>;
    let component: ExerciseVariantAiModalWizardComponent;

    beforeEach(async () => {
        routerMock.navigate.mockClear();
        await TestBed.configureTestingModule({
            imports: [ExerciseVariantAiModalWizardComponent],
            providers: [
                { provide: Router, useValue: routerMock },
                {
                    provide: ExerciseVariantGenerationService,
                    useValue: {
                        startGeneration: vi.fn().mockReturnValue(of('job-1')),
                        jobEvents: vi.fn().mockReturnValue(NEVER),
                        getJobDetail: vi.fn().mockReturnValue(of({ job: undefined, stepOutputs: {}, request: undefined })),
                        cancelJob: vi.fn().mockReturnValue(of(undefined)),
                    },
                },
                { provide: ExerciseVariantGroupService, useValue: { getGroupsForCourse: vi.fn().mockReturnValue(of([])) } },
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
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        fixture.destroy();
        TestBed.resetTestingModule();
    });

    it('navigates to the course variant’s type-aware edit route and closes', () => {
        component.generatedVariant.set({ id: 4711, type: ExerciseType.QUIZ, course: { id: 9 } } as Exercise);

        component.confirmVariant();

        expect(routerMock.navigate).toHaveBeenCalledWith(['/course-management', 9, 'quiz-exercises', 4711, 'edit']);
    });

    it('falls back to the wizard’s course id when the fetched variant ships without a course', () => {
        component.generatedVariant.set({ id: 4711, type: ExerciseType.PROGRAMMING } as Exercise);

        component.confirmVariant();

        expect(routerMock.navigate).toHaveBeenCalledWith(['/course-management', 7, 'programming-exercises', 4711, 'edit']);
    });

    it('navigates exam variants into their exercise group instead of the course route', () => {
        component.generatedVariant.set({
            id: 42,
            type: ExerciseType.PROGRAMMING,
            exerciseGroup: { id: 3, exam: { id: 2, course: { id: 7 } } },
        } as Exercise);

        component.confirmVariant();

        expect(routerMock.navigate).toHaveBeenCalledWith(['/course-management', 7, 'exams', 2, 'exercise-groups', 3, 'programming-exercises', 42, 'edit']);
    });

    it('offers the "what happened & how to continue" guidance for flagged drafts, not only for failures', () => {
        component.jobPhase.set('DRAFT_WITH_WARNINGS');
        expect(component.fallbackGuidanceKey()).toBe('artemisApp.exerciseVariantGeneration.wizard.draftGuidance');

        component.jobPhase.set('FAILED');
        expect(component.fallbackGuidanceKey()).toBe('artemisApp.exerciseVariantGeneration.wizard.failedGuidanceNoExercise');
    });

    it('keeps the raw gate warnings of a flagged draft collapsed until asked for', () => {
        expect(component.warningsExpanded()).toBe(false);

        component.toggleWarnings();

        expect(component.warningsExpanded()).toBe(true);
    });
});
