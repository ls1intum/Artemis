import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

// Mock y-monaco to avoid needing the full Monaco API in tests. The component transitively imports the
// editable-instruction editor, which pulls in y-monaco's deep `monaco-editor/esm/...` import; that subpath
// escapes the `monaco-editor` alias in vitest.config and breaks dependency resolution. Stubbing the module
// here (mirrors programming-exercise-editable-instruction.component.spec.ts) avoids the import entirely.
vi.mock('y-monaco', () => ({
    // Use a real `function` (not an arrow) so the production code can invoke it with `new`.
    MonacoBinding: vi.fn(function (this: any) {
        this.destroy = vi.fn();
    }),
}));

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseProblemComponent } from 'app/programming/manage/update/update-components/problem/programming-exercise-problem.component';
import { ProgrammingExerciseEditableInstructionComponent } from 'app/programming/manage/instructions-editor/programming-exercise-editable-instruction.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ProblemStatementGenerationResponse } from 'app/openapi/model/problemStatementGenerationResponse';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProblemStatementGenerationRequest } from 'app/openapi/model/problemStatementGenerationRequest';
import { ProblemStatementRefinementResponse } from 'app/openapi/model/problemStatementRefinementResponse';
import { ProblemStatementAiOperationsHelper } from 'app/programming/manage/shared/problem-statement-ai-operations.helper';

/**
 * Typed view onto the private `currentAiOperationSubscription` field of the AI operations helper so the
 * spec can assert subscription teardown without a blanket `(component as any)` cast.
 */
type AiOpsInternalsOverrides = {
    currentAiOperationSubscription: Subscription | undefined;
};
type AiOpsInternals = Omit<ProblemStatementAiOperationsHelper, keyof AiOpsInternalsOverrides> & AiOpsInternalsOverrides;
const aiOpsInternals = (c: ProgrammingExerciseProblemComponent): AiOpsInternals => c.aiOps as unknown as AiOpsInternals;

/**
 * Typed accessor that overrides the `editableInstructions` viewChild signal with a stub for tests that
 * need a controllable editor instance. The viewChild is exposed as a callable signal, so the stub mirrors
 * that shape.
 */
type EditableInstructionsHolderOverrides = {
    editableInstructions: () => ProgrammingExerciseEditableInstructionComponent | undefined;
};
type EditableInstructionsHolder = Omit<ProgrammingExerciseProblemComponent, keyof EditableInstructionsHolderOverrides> & EditableInstructionsHolderOverrides;
const setEditableInstructions = (c: ProgrammingExerciseProblemComponent, stub: Partial<ProgrammingExerciseEditableInstructionComponent>): void => {
    (c as unknown as EditableInstructionsHolder).editableInstructions = () => stub as ProgrammingExerciseEditableInstructionComponent;
};

describe('ProgrammingExerciseProblemComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseProblemComponent>;
    let comp: ProgrammingExerciseProblemComponent;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: '1' }) },
        queryParams: of({}),
    } as ActivatedRoute;

    const mockHyperionApiService = {
        generateProblemStatement: vi.fn(),
        refineProblemStatementGlobally: vi.fn(),
        refineProblemStatementTargeted: vi.fn(),
    };

    const mockAlertService = {
        success: vi.fn(),
        error: vi.fn(),
        warning: vi.fn(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: HyperionProblemStatementApiService, useValue: mockHyperionApiService },
                { provide: AlertService, useValue: mockAlertService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseProblemComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
            problemStatement: true,
            linkedCompetencies: true,
        });

        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(undefined, undefined));
        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllMocks();
    });

    it('should initialize and store exercise', () => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();

        const exercise = comp.programmingExercise();
        expect(exercise).toBeDefined();
    });

    it('should generate problem statement successfully', () => {
        const courseId = 42;
        const userPrompt = 'Create a Java exercise about binary search trees';
        const generatedText = 'Generated draft problem statement about binary search trees';

        const mockResponse: ProblemStatementGenerationResponse = {
            draftProblemStatement: generatedText,
        };

        const request: ProblemStatementGenerationRequest = {
            userPrompt: userPrompt,
        };

        // Set up the programming exercise with a course
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: courseId } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.generateProblemStatement.mockReturnValue(of(mockResponse));

        // Trigger the generation
        comp.userPrompt.set(userPrompt);
        comp.aiOps.generateProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        // Verify the API was called correctly
        expect(mockHyperionApiService.generateProblemStatement).toHaveBeenCalledWith(courseId, request);

        // Verify the alert was shown
        expect(mockAlertService.success).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.generationSuccess');

        // Verify the exercise was updated
        expect(programmingExercise.problemStatement).toBe(generatedText);
    });

    it('should not generate when userPrompt is empty', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt.set('');
        comp.aiOps.generateProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        expect(mockHyperionApiService.generateProblemStatement).not.toHaveBeenCalled();
    });

    it('should not generate when no course id', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        // No course set
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt.set('Test prompt');
        comp.aiOps.generateProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        expect(mockHyperionApiService.generateProblemStatement).not.toHaveBeenCalled();
    });

    it('should handle generation error', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.generateProblemStatement.mockReturnValue(throwError(() => new Error('API error')));

        comp.userPrompt.set('Test prompt');
        comp.aiOps.generateProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.generationError');
        expect(comp.isGeneratingOrRefining()).toBe(false);
    });

    it('should handle empty generation response', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: '' }));

        comp.userPrompt.set('Test prompt');
        comp.aiOps.generateProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.generationError');
    });

    it('should refine problem statement successfully', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original problem statement';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const mockResponse: ProblemStatementRefinementResponse = {
            refinedProblemStatement: 'Refined problem statement',
        };

        mockHyperionApiService.refineProblemStatementGlobally.mockReturnValue(of(mockResponse));

        comp.userPrompt.set('Improve clarity');
        comp.aiOps.refineProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        expect(mockHyperionApiService.refineProblemStatementGlobally).toHaveBeenCalledWith(
            42,
            expect.objectContaining({ problemStatementText: 'Original problem statement', userPrompt: 'Improve clarity' }),
        );
        expect(comp.showDiff()).toBe(true);
        expect(mockAlertService.success).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.refinementSuccess');
        expect(comp.isGeneratingOrRefining()).toBe(false);
    });

    it('should revert refinement and close diff', () => {
        comp.showDiff.set(true);
        // Mock editableInstructions as a callable (signal/viewChild) returning the mock
        const mockEditableInstructions = {
            revertAll: vi.fn(),
            getCurrentContent: vi.fn().mockReturnValue('Reverted content'),
        };
        setEditableInstructions(comp, mockEditableInstructions);

        comp.revertAllChanges();

        expect(mockEditableInstructions.revertAll).toHaveBeenCalled();
        expect(comp.showDiff()).toBe(false);
    });

    it('should cancel generation and reset states', () => {
        comp.isGeneratingOrRefining.set(true);

        comp.cancelAiOperation();

        expect(comp.isGeneratingOrRefining()).toBe(false);
    });

    it('should handle onInstructionChange and emit problemStatementChange', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges();

        const emitSpy = vi.spyOn(comp.problemStatementChange, 'emit');

        comp.onInstructionChange('New problem statement');

        expect(emitSpy).toHaveBeenCalledWith('New problem statement');
        expect(programmingExercise.problemStatement).toBe('New problem statement');
    });

    it('should handle competency links change', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const emitSpy = vi.spyOn(comp.programmingExerciseChange, 'emit');

        const mockLink = new CompetencyExerciseLink({ id: 1, title: 'Test' } as any, programmingExercise, 1);
        comp.onCompetencyLinksChange([mockLink]);

        expect(programmingExercise.competencyLinks).toHaveLength(1);
        expect(programmingExercise.competencyLinks![0]).toBeInstanceOf(CompetencyExerciseLink);
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should handle handleProblemStatementAction for generate', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = ''; // Empty, should trigger generate
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const generateSpy = vi.spyOn(comp.aiOps, 'generateProblemStatement');

        comp.userPrompt.set('Test');
        comp.handleProblemStatementAction();

        expect(generateSpy).toHaveBeenCalled();
    });

    it('should handle handleProblemStatementAction for refine', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Existing problem statement'; // Non-empty, should trigger refine
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges(); // Trigger ngOnInit to set currentProblemStatement signal

        // Set templateLoaded so shouldShowGenerateButton returns false for non-template content
        comp.aiOps.templateLoaded.set(true);
        comp.aiOps.templateProblemStatement.set('Different template');

        const refineSpy = vi.spyOn(comp.aiOps, 'refineProblemStatement');

        comp.userPrompt.set('Improve this');
        comp.handleProblemStatementAction();

        expect(refineSpy).toHaveBeenCalled();
    });

    it('should return translated placeholder', () => {
        const translateService = TestBed.inject(TranslateService);
        translateService.instant = vi.fn().mockReturnValue('Translated placeholder');

        const result = comp.getTranslatedPlaceholder();

        expect(result).toBe('Translated placeholder');
        expect(translateService.instant).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.examplePlaceholder');
    });

    it('should accept refinement and apply changes', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.course = { id: 42 } as any;
        exercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', exercise);

        // Mock editableInstructions to return refined content
        const mockEditable = { getCurrentContent: vi.fn().mockReturnValue('Refined content'), revertAll: vi.fn() };
        setEditableInstructions(comp, mockEditable);

        comp.showDiff.set(true);
        comp.closeDiffView();

        expect(comp.showDiff()).toBe(false);
        expect(exercise.problemStatement).toBe('Refined content');
    });

    it('should handle generate with existing non-empty problem statement', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.course = { id: 42 } as any;
        exercise.problemStatement = 'Existing statement';
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.detectChanges();

        // Set templateLoaded so shouldShowGenerateButton compares against template
        comp.aiOps.templateLoaded.set(true);
        comp.aiOps.templateProblemStatement.set('Different template');

        // shouldShowGenerateButton should be false for existing content that differs from template
        expect(comp.shouldShowGenerateButton()).toBe(false);
    });

    it('should handle inline refinement successfully', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original problem statement with content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const mockResponse: ProblemStatementRefinementResponse = {
            refinedProblemStatement: 'Refined problem statement',
        };

        mockHyperionApiService.refineProblemStatementTargeted.mockReturnValue(of(mockResponse));

        const event = {
            instruction: 'Improve this section',
            startLine: 1,
            endLine: 2,
            startColumn: 1,
            endColumn: 10,
        };

        comp.onInlineRefinement(event);

        expect(mockHyperionApiService.refineProblemStatementTargeted).toHaveBeenCalledWith(
            42,
            expect.objectContaining({
                problemStatementText: 'Original problem statement with content',
                instruction: 'Improve this section',
                startLine: 1,
                endLine: 2,
                startColumn: 1,
                endColumn: 10,
            }),
        );

        expect(comp.showDiff()).toBe(true);
        expect(mockAlertService.success).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.inlineRefinement.success');
    });

    it('should handle inline refinement error when no courseId', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.problemStatement = 'Some content';
        // No course set
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const event = {
            instruction: 'Improve this',
            startLine: 1,
            endLine: 2,
            startColumn: 1,
            endColumn: 10,
        };

        comp.onInlineRefinement(event);

        // Service returns silently with success: false when courseId is missing
        expect(mockHyperionApiService.refineProblemStatementTargeted).not.toHaveBeenCalled();
    });

    it('should handle inline refinement error when problem statement is empty', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = '   '; // Only whitespace
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const event = {
            instruction: 'Improve this',
            startLine: 1,
            endLine: 2,
            startColumn: 1,
            endColumn: 10,
        };

        comp.onInlineRefinement(event);

        // Component validates empty content before calling service
        expect(mockHyperionApiService.refineProblemStatementTargeted).not.toHaveBeenCalled();
    });

    it('should handle inline refinement API error', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.refineProblemStatementTargeted.mockReturnValue(throwError(() => new Error('API error')));

        const event = {
            instruction: 'Improve this',
            startLine: 1,
            endLine: 2,
            startColumn: 1,
            endColumn: 10,
        };

        comp.onInlineRefinement(event);

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.inlineRefinement.error');
        expect(comp.isGeneratingOrRefining()).toBe(false);
    });

    it('should handle inline refinement with empty response', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.refineProblemStatementTargeted.mockReturnValue(of({ refinedProblemStatement: '' }));

        const event = {
            instruction: 'Improve this',
            startLine: 1,
            endLine: 2,
            startColumn: 1,
            endColumn: 10,
        };

        comp.onInlineRefinement(event);

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.inlineRefinement.error');
    });

    it('should handle refinement with completely empty response', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const mockResponse: ProblemStatementRefinementResponse = { refinedProblemStatement: '' };

        mockHyperionApiService.refineProblemStatementGlobally.mockReturnValue(of(mockResponse));

        comp.userPrompt.set('Improve clarity');
        comp.aiOps.refineProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.refinementError');
    });

    it('should handle refinement error', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.refineProblemStatementGlobally.mockReturnValue(throwError(() => new Error('API error')));

        comp.userPrompt.set('Improve clarity');
        comp.aiOps.refineProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.refinementError');
        expect(comp.isGeneratingOrRefining()).toBe(false);
    });

    it('should not refine when userPrompt is empty', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt.set('');
        comp.aiOps.refineProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        expect(mockHyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
    });

    it('should not refine when no courseId', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt.set('Improve');
        comp.aiOps.refineProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        expect(mockHyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
    });

    it('should use exerciseGroup course id when course is not set', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.exerciseGroup = { exam: { course: { id: 99 } } } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const mockResponse: ProblemStatementGenerationResponse = {
            draftProblemStatement: 'Generated statement',
        };

        mockHyperionApiService.generateProblemStatement.mockReturnValue(of(mockResponse));

        comp.userPrompt.set('Create exercise');
        comp.aiOps.generateProblemStatement(comp.programmingExercise(), comp.editableInstructions());

        expect(mockHyperionApiService.generateProblemStatement).toHaveBeenCalledWith(99, expect.any(Object));
    });

    it('should cancel active subscription on cancelAiOperation', () => {
        const mockSubscription = { unsubscribe: vi.fn() };
        aiOpsInternals(comp).currentAiOperationSubscription = mockSubscription as any;

        comp.cancelAiOperation();

        expect(mockSubscription.unsubscribe).toHaveBeenCalled();
        expect(aiOpsInternals(comp).currentAiOperationSubscription).toBeUndefined();
    });

    it('should unsubscribe on destroy', () => {
        const mockSubscription = { unsubscribe: vi.fn() };
        aiOpsInternals(comp).currentAiOperationSubscription = mockSubscription as any;

        comp.ngOnDestroy();

        expect(mockSubscription.unsubscribe).toHaveBeenCalled();
    });

    it('should show generate button when problem statement matches template', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.problemStatement = 'Template content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges();

        // Set the template to match the problem statement
        comp.aiOps.templateProblemStatement.set('Template content');
        comp.aiOps.templateLoaded.set(true);
        comp.aiOps.currentProblemStatement.set('Template content');

        expect(comp.shouldShowGenerateButton()).toBe(true);
    });

    it('should show refine button when problem statement differs from template', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.problemStatement = 'Custom content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges();

        comp.aiOps.templateProblemStatement.set('Template content');
        comp.aiOps.templateLoaded.set(true);
        comp.aiOps.currentProblemStatement.set('Custom content');

        expect(comp.shouldShowGenerateButton()).toBe(false);
    });

    it('should NOT show generate button when template loading fails and existing content is present', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.problemStatement = 'Existing content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges();

        comp.aiOps.templateLoaded.set(false);
        comp.aiOps.currentProblemStatement.set('Existing content');

        expect(comp.shouldShowGenerateButton()).toBe(false);
    });

    it('should handle onInstructionChange', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        fixture.componentRef.setInput('programmingExercise', exercise);

        const problemStatementSpy = vi.spyOn(comp.problemStatementChange, 'emit');
        const programmingExerciseSpy = vi.spyOn(comp.programmingExerciseChange, 'emit');

        comp.onInstructionChange('Updated statement');

        expect(programmingExerciseSpy).toHaveBeenCalledOnce();
        expect(programmingExerciseSpy).toHaveBeenCalledWith(expect.objectContaining({ problemStatement: 'Updated statement' }));
        expect(exercise.problemStatement).toBe('Updated statement');
        expect(problemStatementSpy).toHaveBeenCalledWith('Updated statement');
    });

    it('should delegate onChecklistActionDiffRequest to aiOps.applyChecklistActionDiff', () => {
        fixture.detectChanges();

        const applyChecklistSpy = vi.spyOn(comp.aiOps, 'applyChecklistActionDiff').mockImplementation(() => {});

        comp.onChecklistActionDiffRequest('Proposed content');

        expect(applyChecklistSpy).toHaveBeenCalledOnce();
        expect(applyChecklistSpy).toHaveBeenCalledWith('Proposed content', comp.editableInstructions());
    });
});
