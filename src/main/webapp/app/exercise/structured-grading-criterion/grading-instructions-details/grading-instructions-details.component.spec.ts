import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GradingInstructionAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-instruction.action';
import { GradingCreditsAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-credits.action';
import { GradingScaleAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-scale.action';
import { GradingDescriptionAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-description.action';
import { GradingFeedbackAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-feedback.action';
import { GradingUsageCountAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-usage-count.action';
import { GradingCriterionAction } from 'app/editor/monaco-editor/model/actions/grading-criteria/grading-criterion.action';
import { TextWithDomainAction } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AssessmentCriteriaGenerationService } from 'app/exercise/structured-grading-criterion/assessment-criteria-generation.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Subject, of, throwError } from 'rxjs';
import { ConfirmationService } from 'primeng/api';

describe('GradingInstructionsDetailsComponent', () => {
    let component: GradingInstructionsDetailsComponent;
    let fixture: ComponentFixture<GradingInstructionsDetailsComponent>;
    let gradingInstruction: GradingInstruction;
    let gradingCriterion: GradingCriterion;
    let gradingInstructionWithoutId: GradingInstruction;
    let gradingCriterionWithoutId: GradingCriterion;
    let exercise: Exercise;
    let backupExercise: Exercise;
    let generationService: { generate: ReturnType<typeof vi.fn> };
    let alertService: MockAlertService;

    const criterionMarkdownText =
        '[criterion] testCriteria\n' +
        '\t[instruction]\n' +
        '\t[credits] 1\n' +
        '\t[gradingScale] scale\n' +
        '\t[description] description\n' +
        '\t[feedback] feedback\n' +
        '\t[maxCountInScore] 0\n\n';

    beforeEach(async () => {
        generationService = { generate: vi.fn() };
        await TestBed.configureTestingModule({
            imports: [GradingInstructionsDetailsComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useValue: { isModuleFeatureActive: () => true } },
                { provide: AssessmentCriteriaGenerationService, useValue: generationService },
                { provide: AlertService, useClass: MockAlertService },
            ],
        })
            .overrideTemplate(GradingInstructionsDetailsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(GradingInstructionsDetailsComponent);
        component = fixture.componentInstance;
        alertService = TestBed.inject(AlertService) as unknown as MockAlertService;
        exercise = { id: 1 } as Exercise;
        backupExercise = { id: 1 } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        component.backupExercise = backupExercise;
        gradingInstruction = { id: 1, credits: 1, gradingScale: 'scale', instructionDescription: 'description', feedback: 'feedback', usageCount: 0 };
        gradingCriterion = { id: 1, title: 'testCriteria', structuredGradingInstructions: [gradingInstruction] };
        gradingInstructionWithoutId = { credits: 1, gradingScale: 'scale', instructionDescription: 'description', feedback: 'feedback', usageCount: 0 };
        gradingCriterionWithoutId = { title: 'testCriteria', structuredGradingInstructions: [gradingInstructionWithoutId] };
    });

    describe('assessment criteria generation', () => {
        beforeEach(() => {
            exercise.type = ExerciseType.TEXT;
            exercise.problemStatement = 'Explain the concept';
            exercise.maxPoints = 5;
            exercise.course = { id: 7, isAtLeastEditor: true };
            component.ngOnInit();
        });

        it('should gate generation by prerequisites', () => {
            expect(component.canShowGenerationButton()).toBe(true);
            expect(component.isGenerationDisabled()).toBe(false);

            exercise.problemStatement = ' ';
            expect(component.isGenerationDisabled()).toBe(true);
            expect(component.generationDisabledReason()).toBe('artemisApp.exercise.assessmentCriteriaGeneration.disabledProblemStatement');

            exercise.problemStatement = 'Problem';
            exercise.maxPoints = 0;
            expect(component.generationDisabledReason()).toBe('artemisApp.exercise.assessmentCriteriaGeneration.disabledMaxPoints');

            exercise.isAtLeastEditor = true;
            exercise.course = undefined;
            expect(component.canShowGenerationButton()).toBe(false);

            Object.defineProperty(component, 'hyperionEnabled', { value: false });
            expect(component.canShowGenerationButton()).toBe(false);
        });

        it('should show generation for every exercise type that uses the component', () => {
            for (const exerciseType of [ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.PROGRAMMING]) {
                exercise.type = exerciseType;
                expect(component.canShowGenerationButton()).toBe(true);
            }
        });

        it('should generate once, replace criteria, preserve general text, and prevent duplicate clicks', () => {
            const response = new Subject<GradingCriterion[]>();
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            exercise.gradingInstructions = 'Keep this text';
            generationService.generate.mockReturnValue(response);
            vi.spyOn(alertService, 'success');
            const generatedSpy = vi.spyOn(component.criteriaGenerated, 'emit');

            component.generateAssessmentCriteria();
            component.generateAssessmentCriteria();

            expect(generationService.generate).toHaveBeenCalledTimes(1);
            expect(generationService.generate).toHaveBeenCalledWith(exercise, { exampleSolution: undefined, additionalContext: undefined });
            expect(component.isGenerating()).toBe(true);
            response.next([generatedCriterion]);
            response.complete();

            expect(exercise.gradingCriteria).toEqual([generatedCriterion]);
            expect(exercise.gradingInstructions).toBe('Keep this text');
            expect(component.isGenerating()).toBe(false);
            expect(generatedSpy).toHaveBeenCalledOnce();
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.exercise.assessmentCriteriaGeneration.success');
        });

        it('should parse, generate, and remain in edit-as-text mode', () => {
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            const markdownEditor = {
                parseMarkdown: vi.fn(() => {
                    exercise.gradingInstructions = 'Current unsaved text';
                    exercise.gradingCriteria = [];
                }),
                setMarkdown: vi.fn(),
            };
            Object.defineProperty(component, 'markdownEditor', { value: () => markdownEditor });
            component.showEditMode.set(false);
            generationService.generate.mockReturnValue(of([generatedCriterion]));

            component.generateAssessmentCriteria();

            expect(markdownEditor.parseMarkdown).toHaveBeenCalledOnce();
            expect(markdownEditor.setMarkdown).toHaveBeenCalledOnce();
            expect(component.showEditMode()).toBe(false);
            expect(exercise.gradingInstructions).toBe('Current unsaved text');
        });

        it('should keep generated criterion markup out of the general editor when grading instruction feedback is used', () => {
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            const markdownEditor = {
                parseMarkdown: vi.fn(() => {
                    exercise.gradingInstructions = 'General assessment instructions';
                }),
                setMarkdown: vi.fn(),
            };
            Object.defineProperty(component, 'markdownEditor', { value: () => markdownEditor });
            exercise.gradingInstructionFeedbackUsed = true;
            generationService.generate.mockReturnValue(of([generatedCriterion]));
            const initializeMarkdownSpy = vi.spyOn(component, 'initializeMarkdown').mockImplementation(() => undefined);
            const generateMarkdownSpy = vi.spyOn(component, 'generateMarkdown');

            component.generateAssessmentCriteria();

            expect(component.markdownEditorText()).toBe('General assessment instructions\n\n');
            expect(component.markdownEditorText()).not.toContain(GradingCriterionAction.IDENTIFIER);
            expect(generateMarkdownSpy).not.toHaveBeenCalled();
            expect(initializeMarkdownSpy).toHaveBeenCalledOnce();
            expect(markdownEditor.setMarkdown).not.toHaveBeenCalled();
            expect(exercise.gradingInstructions).toBe('General assessment instructions');
        });

        it('should abort when edit-as-text syntax cannot be parsed', () => {
            const markdownEditor = {
                parseMarkdown: vi.fn(() => {
                    exercise.gradingCriteria = [{ title: '', structuredGradingInstructions: [] } as GradingCriterion];
                }),
            };
            Object.defineProperty(component, 'markdownEditor', { value: () => markdownEditor });
            component.showEditMode.set(false);
            vi.spyOn(alertService, 'error');

            component.generateAssessmentCriteria();

            expect(generationService.generate).not.toHaveBeenCalled();
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.exercise.assessmentCriteriaGeneration.invalidSyntax');
        });

        it('should confirm replacement and make no request when confirmation is cancelled', () => {
            exercise.gradingCriteria = [gradingCriterion];
            const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
            const confirmSpy = vi.spyOn(confirmationService, 'confirm');

            component.generateAssessmentCriteria();

            expect(confirmSpy).toHaveBeenCalledOnce();
            expect(generationService.generate).not.toHaveBeenCalled();
        });

        it('should preserve criteria when generation fails', () => {
            exercise.gradingCriteria = [];
            const previousCriteria = [gradingCriterion];
            exercise.gradingCriteria = previousCriteria;
            const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
            vi.spyOn(confirmationService, 'confirm').mockImplementation((confirmation) => confirmation.accept?.());
            generationService.generate.mockReturnValue(throwError(() => new Error('generation failed')));

            component.generateAssessmentCriteria();

            expect(exercise.gradingCriteria).toBe(previousCriteria);
            expect(component.isGenerating()).toBe(false);
        });

        it('should stop generating and report an error when request setup fails synchronously', () => {
            generationService.generate.mockImplementation(() => {
                throw new Error('request setup failed');
            });
            const addAlertSpy = vi.spyOn(alertService, 'addAlert');

            component.generateAssessmentCriteria();

            expect(component.isGenerating()).toBe(false);
            expect(addAlertSpy).toHaveBeenCalledWith(expect.objectContaining({ message: 'request setup failed' }));
        });

        it('should generate immediately when no structured criteria exist', () => {
            exercise.gradingCriteria = [];
            generationService.generate.mockReturnValue(of([]));
            fixture.componentRef.setInput('exampleSolution', 'Example answer');
            fixture.componentRef.setInput('additionalGenerationContext', () => 'Diagram type: ClassDiagram');

            component.generateAssessmentCriteria();

            expect(generationService.generate).toHaveBeenCalledWith(exercise, {
                exampleSolution: 'Example answer',
                additionalContext: 'Diagram type: ClassDiagram',
            });
        });
    });

    describe('onInit', () => {
        it('should initialize the component', () => {
            // WHEN
            component.ngOnInit();

            // THEN
            expect(component).toBeTruthy();
        });
        it('should set the grading criteria based on the exercise', () => {
            exercise.gradingCriteria = [gradingCriterion];
            // WHEN
            component.ngOnInit();
            // THEN
            expect(component.markdownEditorText()).toEqual('Add Assessment Instruction text here\n\n' + criterionMarkdownText);
        });

        it('should initialize only general instructions in the main editor when grading instruction feedback is used', () => {
            exercise.gradingInstructions = 'General assessment instructions';
            exercise.gradingCriteria = [gradingCriterion];
            exercise.gradingInstructionFeedbackUsed = true;

            component.ngOnInit();

            expect(component.markdownEditorText()).toBe('General assessment instructions\n\n');
            expect(component.markdownEditorText()).not.toContain(GradingCriterionAction.IDENTIFIER);
        });
    });

    it('should return grading criteria index', () => {
        exercise.gradingCriteria = [gradingCriterion];
        const index = component.findCriterionIndex(gradingCriterion, exercise);
        fixture.changeDetectorRef.detectChanges();

        expect(index).toBe(0);
    });

    it('should return grading instruction index', () => {
        exercise.gradingCriteria = [gradingCriterion];
        const index = component.findInstructionIndex(gradingInstruction, exercise, 0);
        fixture.changeDetectorRef.detectChanges();

        expect(index).toBe(0);
    });

    it('should add new grading instruction to criteria', () => {
        exercise.gradingCriteria = [gradingCriterion];
        component.addNewInstruction(gradingCriterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria[0].structuredGradingInstructions).toHaveLength(2);
    });

    it('should delete the grading criterion', () => {
        exercise.gradingCriteria = [gradingCriterion];
        component.deleteGradingCriterion(gradingCriterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria).toHaveLength(0);
    });

    it('should reset the grading criterion', () => {
        exercise.gradingCriteria = [gradingCriterion];
        component.backupExercise.gradingCriteria = [gradingCriterion];
        component.resetCriterionTitle(gradingCriterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria).toEqual(component.backupExercise.gradingCriteria);
    });

    it('should add new grading criteria to corresponding exercise', () => {
        exercise.gradingCriteria = [gradingCriterion];
        component.addNewGradingCriterion();
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria).toHaveLength(2);
    });

    it('should change grading criteria title', () => {
        exercise.gradingCriteria = [gradingCriterion];
        const event = { target: { value: 'changed Title' } };
        component.onCriterionTitleChange(event as unknown as Event, gradingCriterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria[0].title).toEqual(event.target.value);
    });

    it('should change grading instruction', () => {
        const newDescription = 'new text';
        const domainActions = [{ text: newDescription, action: new GradingDescriptionAction() }] as TextWithDomainAction[];

        exercise.gradingCriteria = [gradingCriterion];
        component.onInstructionChange(domainActions, gradingInstruction);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria[0].structuredGradingInstructions[0].instructionDescription).toEqual(newDescription);
    });

    it('should delete a grading instruction', () => {
        exercise.gradingCriteria = [gradingCriterion];
        component.deleteInstruction(gradingInstruction, gradingCriterion);

        expect(component.exercise().gradingCriteria![0].structuredGradingInstructions).toHaveLength(0);
    });

    it('should set grading instruction text for exercise', () => {
        const markdownText = 'new text';
        const domainActions = [{ text: markdownText, action: undefined }] as TextWithDomainAction[];

        component.setExerciseGradingInstructionText(domainActions);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingInstructions).toEqual(markdownText);
    });

    const getDomainActionArray = () => {
        const creditsAction = new GradingCreditsAction();
        const scaleAction = new GradingScaleAction();
        const descriptionAction = new GradingDescriptionAction();
        const feedbackAction = new GradingFeedbackAction();
        const usageCountAction = new GradingUsageCountAction();
        const instructionAction = new GradingInstructionAction(creditsAction, scaleAction, descriptionAction, feedbackAction, usageCountAction);
        const criterionAction = new GradingCriterionAction(instructionAction);

        return [
            { text: 'testCriteria', action: criterionAction },
            { text: '', action: instructionAction },
            { text: '1', action: creditsAction },
            { text: 'scale', action: scaleAction },
            { text: 'description', action: descriptionAction },
            { text: 'feedback', action: feedbackAction },
            { text: '0', action: usageCountAction },
        ] as TextWithDomainAction[];
    };

    it('should set grading instruction without criterion action when markdown-change triggered', () => {
        const domainActionsWithoutCriterion = getDomainActionArray().slice(1);

        component.onDomainActionsFound(domainActionsWithoutCriterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria).toBeDefined();
        const gradingCriteria = exercise.gradingCriteria![0];
        expect(gradingCriteria.structuredGradingInstructions[0]).toEqual(gradingInstructionWithoutId);
    });

    it('should set grading instruction with criterion action when markdown-change triggered', () => {
        const domainActions = getDomainActionArray();

        component.onDomainActionsFound(domainActions);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria).toBeDefined();
        const gradingCriteria = exercise.gradingCriteria![0];
        expect(gradingCriteria).toEqual(gradingCriterionWithoutId);
    });

    it('should update properties for grading instruction', () => {
        exercise.gradingCriteria = [gradingCriterion];
        const instruction = gradingInstruction;
        const criterion = gradingCriterion;

        instruction.credits = 5;
        component.updateGradingInstruction(instruction, criterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria[0].structuredGradingInstructions[0].credits).toBe(5);

        instruction.gradingScale = 'changed grading scale';
        component.updateGradingInstruction(instruction, criterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria[0].structuredGradingInstructions[0].gradingScale).toBe('changed grading scale');

        instruction.instructionDescription = 'changed instruction description';
        component.updateGradingInstruction(instruction, criterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria[0].structuredGradingInstructions[0].instructionDescription).toBe('changed instruction description');

        instruction.feedback = 'changed feedback';
        component.updateGradingInstruction(instruction, criterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria[0].structuredGradingInstructions[0].feedback).toBe('changed feedback');

        instruction.usageCount = 2;
        component.updateGradingInstruction(instruction, criterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria[0].structuredGradingInstructions[0].usageCount).toBe(2);
    });
});
