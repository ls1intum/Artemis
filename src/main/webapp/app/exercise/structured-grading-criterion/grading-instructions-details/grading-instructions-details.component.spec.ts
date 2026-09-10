import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApplicationRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { Exercise, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
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
import { parseMarkdownForDomainActions } from 'app/editor/markdown-editor/monaco/markdown-editor-parsing.helper';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AssessmentCriteriaGenerationService } from 'app/exercise/structured-grading-criterion/assessment-criteria-generation.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Subject, of, throwError } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { TumUiConfirmationService, TumUiTooltipDirective } from '@tumaet/ui-angular';

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
    let accountService: { isAtLeastEditorForExercise: ReturnType<typeof vi.fn> };
    let alertService: MockAlertService;

    const criterionMarkdownText =
        '[criterion] {id:1} testCriteria\n' +
        '\t[instruction] {id:1}\n' +
        '\t[credits] 1\n' +
        '\t[gradingScale] scale\n' +
        '\t[description] description\n' +
        '\t[feedback] feedback\n' +
        '\t[maxCountInScore] 0\n\n';

    beforeEach(async () => {
        generationService = { generate: vi.fn() };
        accountService = { isAtLeastEditorForExercise: vi.fn(() => true) };
        await TestBed.configureTestingModule({
            imports: [GradingInstructionsDetailsComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useValue: { isModuleFeatureActive: () => true } },
                { provide: AssessmentCriteriaGenerationService, useValue: generationService },
                { provide: AccountService, useValue: accountService },
                { provide: AlertService, useClass: MockAlertService },
            ],
        }).compileComponents();

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
            component.ngDoCheck();
            vi.spyOn(alertService, 'success');
        });

        it('should gate generation by prerequisites', () => {
            expect(component.canShowGenerationButton()).toBe(true);
            expect(component.isGenerationDisabled()).toBe(false);

            exercise.problemStatement = ' ';
            component.ngDoCheck();
            expect(component.isGenerationDisabled()).toBe(true);
            expect(component.generationDisabledReason()).toBe('artemisApp.exercise.assessmentCriteriaGeneration.disabledProblemStatement');

            exercise.problemStatement = 'Problem';
            exercise.maxPoints = 0;
            component.ngDoCheck();
            expect(component.generationDisabledReason()).toBe('artemisApp.exercise.assessmentCriteriaGeneration.disabledMaxPoints');

            exercise.isAtLeastEditor = true;
            exercise.course = undefined;
            component.ngDoCheck();
            expect(component.canShowGenerationButton()).toBe(false);

            Object.defineProperty(component, 'hyperionEnabled', { value: false });
            component.ngDoCheck();
            expect(component.canShowGenerationButton()).toBe(false);
        });

        it('should gate generation for invalid bonus points and re-enable it for valid values', () => {
            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
            for (const bonusPoints of [-1, Number.NaN, Number.POSITIVE_INFINITY, Number.NEGATIVE_INFINITY]) {
                exercise.bonusPoints = bonusPoints;
                component.ngDoCheck();

                expect(component.isGenerationDisabled()).toBe(true);
                expect(component.generationDisabledReason()).toBe('artemisApp.exercise.assessmentCriteriaGeneration.disabledBonusPoints');
            }

            exercise.bonusPoints = 0;
            component.ngDoCheck();

            expect(component.isGenerationDisabled()).toBe(false);
            expect(component.generationDisabledReason()).toBeUndefined();
        });

        it('should allow generation after switching to a mode that ignores stale invalid bonus points', () => {
            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
            exercise.bonusPoints = -1;
            component.ngDoCheck();
            expect(component.isGenerationDisabled()).toBe(true);

            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_AS_BONUS;
            component.ngDoCheck();
            generationService.generate.mockReturnValue(of([]));

            component.generateAssessmentCriteria();

            expect(component.isGenerationDisabled()).toBe(false);
            expect(component.generationDisabledReason()).toBeUndefined();
            expect(generationService.generate).toHaveBeenCalledOnce();
        });

        it('should expose the disabled reason without shifting the layout or showing a pointer cursor', () => {
            vi.useFakeTimers();
            try {
                exercise.problemStatement = ' ';
                fixture.detectChanges();

                const buttonHost = fixture.nativeElement.querySelector('[data-testid="generate-assessment-criteria"]') as HTMLElement;
                const button = fixture.nativeElement.querySelector('[data-testid="generate-assessment-criteria"] button') as HTMLButtonElement;
                const tooltipTrigger = fixture.debugElement.query(By.directive(TumUiTooltipDirective)).nativeElement as HTMLElement;

                expect(button.disabled).toBe(true);
                expect(tooltipTrigger.getAttribute('tabindex')).toBe('0');
                expect(getComputedStyle(buttonHost).cursor).toBe('default');
                expect(fixture.nativeElement.querySelector('#assessment-criteria-generation-disabled-reason')).toBeNull();

                tooltipTrigger.dispatchEvent(new Event('focusin', { bubbles: true }));
                vi.advanceTimersByTime(151);
                TestBed.inject(ApplicationRef).tick();

                const tooltipId = tooltipTrigger.getAttribute('aria-describedby');
                expect(tooltipId).toBeTruthy();
                const tooltip = document.getElementById(tooltipId!);
                expect(tooltip?.getAttribute('role')).toBe('tooltip');
                expect(tooltip?.textContent).toContain('artemisApp.exercise.assessmentCriteriaGeneration.disabledProblemStatement');

                tooltipTrigger.dispatchEvent(new Event('focusout', { bubbles: true }));
                vi.advanceTimersByTime(101);
            } finally {
                vi.runOnlyPendingTimers();
                vi.useRealTimers();
            }
        });

        it('should render the edit controls and generation button in the same header', () => {
            fixture.detectChanges();

            const header = fixture.nativeElement.querySelector('.assessment-criteria-generation__header') as HTMLElement;

            expect(header).not.toBeNull();
            expect(header.querySelector('#edit-mode')).not.toBeNull();
            expect(header.querySelector('[data-testid="generate-assessment-criteria"]')).not.toBeNull();
        });

        it('should render the add-instruction action as a labelled button', () => {
            exercise.gradingCriteria = [gradingCriterion];
            fixture.detectChanges();

            const buttonHost = fixture.nativeElement.querySelector('#add-instruction-button-0') as HTMLElement;
            const button = buttonHost.querySelector('button') as HTMLButtonElement;

            expect(button.tagName).toBe('BUTTON');
            expect(button.textContent?.trim()).toBe('artemisApp.exercise.addAssessmentInstruction');
        });

        it('should generate without parsing markdown when feedback is already used', () => {
            exercise.gradingInstructionFeedbackUsed = true;
            const markdownEditor = {
                flushLiveMarkdownAndParse: vi.fn(),
            };
            Object.defineProperty(component, 'markdownEditor', { value: () => markdownEditor });
            generationService.generate.mockReturnValue(of([]));

            component.generateAssessmentCriteria();

            expect(markdownEditor.flushLiveMarkdownAndParse).not.toHaveBeenCalled();
            expect(generationService.generate).toHaveBeenCalledWith(exercise, { exampleSolution: undefined, additionalContext: undefined });
        });

        it('should allow switching to edit-as-text when grading instruction feedback is used', () => {
            exercise.gradingInstructionFeedbackUsed = true;
            component.showEditMode.set(true);

            component.setEditMode(false);

            expect(component.showEditMode()).toBe(false);
        });

        it('should render structured instruction fields when grading instruction feedback is used', () => {
            exercise.gradingInstructionFeedbackUsed = true;
            exercise.gradingCriteria = [gradingCriterion];
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('.sqi-instruction__fields-row')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('.sqi-instruction--markdown')).toBeNull();
            expect(component.showEditMode()).toBe(true);
        });

        it('should render the plain markdown editor without criterion cards in edit-as-text mode', () => {
            exercise.gradingCriteria = [gradingCriterion];
            fixture.detectChanges();
            component.setEditMode('text');
            fixture.detectChanges();

            expect(component.showEditMode()).toBe(false);
            expect(fixture.nativeElement.querySelector('jhi-markdown-editor-monaco')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('.sqi-criterion')).toBeNull();
            expect(fixture.nativeElement.querySelector('.grading-instructions-update-border')).toBeNull();
        });

        it('should use the current user permissions for a new exam exercise without populated permission flags', () => {
            const examCourse = { id: 7, isAtLeastEditor: false };
            exercise.course = undefined;
            exercise.isAtLeastEditor = false;
            exercise.exerciseGroup = { exam: { course: examCourse } };
            component.ngDoCheck();

            expect(component.canShowGenerationButton()).toBe(true);
            expect(accountService.isAtLeastEditorForExercise).toHaveBeenCalledWith(exercise);

            accountService.isAtLeastEditorForExercise.mockReturnValue(false);
            component.ngDoCheck();
            expect(component.canShowGenerationButton()).toBe(false);

            examCourse.isAtLeastEditor = true;
            component.ngDoCheck();
            expect(component.canShowGenerationButton()).toBe(true);
        });

        it('should show generation for every exercise type that uses the component', () => {
            for (const exerciseType of [ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD, ExerciseType.PROGRAMMING]) {
                exercise.type = exerciseType;
                component.ngDoCheck();
                expect(component.canShowGenerationButton()).toBe(true);
            }
        });

        it('should discard a response when general instructions change while waiting and prevent duplicate clicks', () => {
            const response = new Subject<GradingCriterion[]>();
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            exercise.gradingInstructions = 'Keep this text';
            generationService.generate.mockReturnValue(response);
            const generatedSpy = vi.spyOn(component.criteriaGenerated, 'emit');

            component.generateAssessmentCriteria();
            component.generateAssessmentCriteria();

            expect(generationService.generate).toHaveBeenCalledTimes(1);
            expect(generationService.generate).toHaveBeenCalledWith(exercise, { exampleSolution: undefined, additionalContext: undefined });
            expect(component.isGenerating()).toBe(true);
            exercise.gradingInstructions = 'Edited while waiting';
            response.next([generatedCriterion]);
            response.complete();

            expect(exercise.gradingCriteria).toBeUndefined();
            expect(exercise.gradingInstructions).toBe('Edited while waiting');
            expect(component.isGenerating()).toBe(false);
            expect(generatedSpy).not.toHaveBeenCalled();
            expect(alertService.success).not.toHaveBeenCalled();
        });

        it('should discard a response when additional generation context changes while waiting', () => {
            const response = new Subject<GradingCriterion[]>();
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            const currentCriteria: GradingCriterion[] = [];
            let additionalContext = 'Initial diagram';
            exercise.gradingCriteria = currentCriteria;
            fixture.componentRef.setInput('additionalGenerationContext', () => additionalContext);
            generationService.generate.mockReturnValue(response);

            component.generateAssessmentCriteria();
            additionalContext = 'Changed diagram';
            response.next([generatedCriterion]);
            response.complete();

            expect(exercise.gradingCriteria).toBe(currentCriteria);
            expect(component.isGenerating()).toBe(false);
            expect(alertService.success).not.toHaveBeenCalled();
        });

        it('should discard a response when max points change while waiting', () => {
            const response = new Subject<GradingCriterion[]>();
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            generationService.generate.mockReturnValue(response);

            component.generateAssessmentCriteria();
            exercise.maxPoints = 10;
            response.next([generatedCriterion]);
            response.complete();

            expect(exercise.gradingCriteria).toBeUndefined();
            expect(component.isGenerating()).toBe(false);
            expect(alertService.success).not.toHaveBeenCalled();
        });

        it('should discard a response when the bonus-points policy changes while waiting', () => {
            const response = new Subject<GradingCriterion[]>();
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
            generationService.generate.mockReturnValue(response);

            component.generateAssessmentCriteria();
            exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_AS_BONUS;
            response.next([generatedCriterion]);
            response.complete();

            expect(exercise.gradingCriteria).toBeUndefined();
            expect(component.isGenerating()).toBe(false);
            expect(alertService.success).not.toHaveBeenCalled();
        });

        it('should preserve criteria edited while generation is in flight', () => {
            const response = new Subject<GradingCriterion[]>();
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            const editedCriteria = [{ title: 'Edited', structuredGradingInstructions: [] } as GradingCriterion];
            generationService.generate.mockReturnValue(response);

            component.generateAssessmentCriteria();
            exercise.gradingCriteria = editedCriteria;
            response.next([generatedCriterion]);
            response.complete();

            expect(exercise.gradingCriteria).toBe(editedCriteria);
            expect(component.isGenerating()).toBe(false);
            expect(alertService.success).not.toHaveBeenCalled();
        });

        it('should apply a deferred response when edit mode changes while waiting', () => {
            const response = new Subject<GradingCriterion[]>();
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            const markdownEditor = { setMarkdown: vi.fn() };
            Object.defineProperty(component, 'markdownEditor', { value: () => markdownEditor });
            generationService.generate.mockReturnValue(response);

            component.generateAssessmentCriteria();
            component.switchMode();
            response.next([generatedCriterion]);
            response.complete();

            expect(exercise.gradingCriteria).toEqual([generatedCriterion]);
            expect(component.isGenerating()).toBe(false);
        });

        it('should parse, generate, and remain in edit-as-text mode', () => {
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            const markdownEditor = {
                flushLiveMarkdownAndParse: vi.fn(() => {
                    exercise.gradingInstructions = 'Current unsaved text';
                    exercise.gradingCriteria = [];
                }),
                setMarkdown: vi.fn(),
            };
            Object.defineProperty(component, 'markdownEditor', { value: () => markdownEditor });
            component.showEditMode.set(false);
            generationService.generate.mockReturnValue(of([generatedCriterion]));

            component.generateAssessmentCriteria();

            expect(markdownEditor.flushLiveMarkdownAndParse).toHaveBeenCalledOnce();
            expect(markdownEditor.setMarkdown).toHaveBeenCalledOnce();
            expect(component.showEditMode()).toBe(false);
            expect(exercise.gradingInstructions).toBe('Current unsaved text');
        });

        it('should refresh structured markdown snapshot after generation when grading instruction feedback is used', () => {
            const generatedCriterion = { title: 'Generated', structuredGradingInstructions: [gradingInstructionWithoutId] } as GradingCriterion;
            const markdownEditor = {
                parseMarkdown: vi.fn(),
                setMarkdown: vi.fn(),
            };
            Object.defineProperty(component, 'markdownEditor', { value: () => markdownEditor });
            exercise.gradingInstructions = 'General assessment instructions';
            exercise.gradingInstructionFeedbackUsed = true;
            generationService.generate.mockReturnValue(of([generatedCriterion]));

            component.generateAssessmentCriteria();

            expect(component.showEditMode()).toBe(true);
            expect(component.markdownEditorText()).toContain('General assessment instructions');
            expect(component.markdownEditorText()).toContain(GradingCriterionAction.IDENTIFIER);
            expect(markdownEditor.setMarkdown).not.toHaveBeenCalled();
            expect(exercise.gradingCriteria).toEqual([generatedCriterion]);
        });

        it('should abort when edit-as-text syntax cannot be parsed', () => {
            const markdownEditor = {
                flushLiveMarkdownAndParse: vi.fn(() => {
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
            const confirmationService = fixture.debugElement.injector.get(TumUiConfirmationService);
            const confirmSpy = vi.spyOn(confirmationService, 'confirm');

            component.generateAssessmentCriteria();

            expect(confirmSpy).toHaveBeenCalledOnce();
            expect(confirmSpy).toHaveBeenCalledWith(expect.objectContaining({ acceptSeverity: 'danger' }));
            expect(generationService.generate).not.toHaveBeenCalled();
        });

        it('should preserve criteria when generation fails', () => {
            exercise.gradingCriteria = [];
            const previousCriteria = [gradingCriterion];
            exercise.gradingCriteria = previousCriteria;
            const confirmationService = fixture.debugElement.injector.get(TumUiConfirmationService);
            vi.spyOn(confirmationService, 'confirm').mockImplementation((confirmation) => confirmation.accept());
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

        it('should not request assessment criteria when the component is not editable', () => {
            fixture.componentRef.setInput('editable', false);
            component.ngDoCheck();

            component.generateAssessmentCriteria();

            expect(generationService.generate).not.toHaveBeenCalled();
            expect(component.canShowGenerationButton()).toBe(false);
        });

        it('should not mutate criteria or mode when the component is not editable', () => {
            exercise.gradingCriteria = [gradingCriterion];
            const originalTitle = gradingCriterion.title;
            const originalMode = component.showEditMode();
            fixture.componentRef.setInput('editable', false);

            component.addNewGradingCriterion();
            component.deleteGradingCriterion(gradingCriterion);
            component.onCriterionTitleChange({ target: { value: 'changed' } } as unknown as Event, gradingCriterion);
            component.switchMode();

            expect(exercise.gradingCriteria).toEqual([gradingCriterion]);
            expect(gradingCriterion.title).toBe(originalTitle);
            expect(component.showEditMode()).toBe(originalMode);
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

        it('should initialize full markdown snapshot when grading instruction feedback is used', () => {
            exercise.gradingInstructions = 'General assessment instructions';
            exercise.gradingCriteria = [gradingCriterion];
            exercise.gradingInstructionFeedbackUsed = true;

            component.ngOnInit();

            expect(component.showEditMode()).toBe(true);
            expect(component.markdownEditorText()).toContain('General assessment instructions');
            expect(component.markdownEditorText()).toContain(GradingCriterionAction.IDENTIFIER);
        });

        it('should skip markdown parsing while in structured edit mode', () => {
            exercise.gradingInstructionFeedbackUsed = true;
            component.ngOnInit();
            const mainEditor = { flushLiveMarkdownAndParse: vi.fn() };
            Object.defineProperty(component, 'markdownEditor', { value: () => mainEditor });

            component.prepareForSave();

            expect(mainEditor.flushLiveMarkdownAndParse).not.toHaveBeenCalled();
        });

        it('should flush the live monaco buffer before switching to structured mode', () => {
            component.showEditMode.set(false);
            const markdownEditor = { flushLiveMarkdownAndParse: vi.fn() };
            Object.defineProperty(component, 'markdownEditor', { value: () => markdownEditor });

            component.setEditMode('structured');

            expect(markdownEditor.flushLiveMarkdownAndParse).toHaveBeenCalledOnce();
            expect(component.showEditMode()).toBe(true);
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

    it('should expose the grading instruction domain actions used by the text editor', () => {
        expect(component.domainActionsForMainEditor).toEqual([
            component.creditsAction,
            component.gradingScaleAction,
            component.descriptionAction,
            component.feedbackAction,
            component.usageCountAction,
            component.gradingInstructionAction,
            component.gradingCriterionAction,
        ]);
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

    it('should reset only the selected no-ID instruction when multiple no-ID objects exist', () => {
        const firstInstruction = { credits: 1, gradingScale: 'first' } as GradingInstruction;
        const secondInstruction = { credits: 2, gradingScale: 'second' } as GradingInstruction;
        const firstCriterion = { title: 'first', structuredGradingInstructions: [firstInstruction] } as GradingCriterion;
        const secondCriterion = { title: 'second', structuredGradingInstructions: [secondInstruction] } as GradingCriterion;
        exercise.gradingCriteria = [firstCriterion, secondCriterion];
        component.backupExercise.gradingCriteria = [
            { title: 'first', structuredGradingInstructions: [{ credits: 3, gradingScale: 'backup first' }] },
            { title: 'second', structuredGradingInstructions: [{ credits: 4, gradingScale: 'backup second' }] },
        ] as GradingCriterion[];

        component.resetInstruction(secondInstruction, secondCriterion);

        expect(exercise.gradingCriteria[0].structuredGradingInstructions[0]).toBe(firstInstruction);
        expect(exercise.gradingCriteria[1].structuredGradingInstructions[0]).not.toBe(secondInstruction);
        expect(exercise.gradingCriteria[1].structuredGradingInstructions[0]).toEqual(new GradingInstruction());
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

    it('should replace a grading instruction looked up by stable id', () => {
        exercise.gradingCriteria = [gradingCriterion];
        const updatedInstruction = {
            id: gradingInstruction.id,
            credits: gradingInstruction.credits,
            gradingScale: gradingInstruction.gradingScale,
            instructionDescription: 'new text',
            feedback: gradingInstruction.feedback,
            usageCount: gradingInstruction.usageCount,
        } as GradingInstruction;

        component.updateGradingInstruction(updatedInstruction, gradingCriterion);
        fixture.changeDetectorRef.detectChanges();

        expect(exercise.gradingCriteria[0].structuredGradingInstructions[0]).toBe(updatedInstruction);
        expect(exercise.gradingCriteria[0].structuredGradingInstructions[0].instructionDescription).toBe('new text');
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

    it('should ignore the display-only grading instruction placeholder while preserving genuine text', () => {
        component.setExerciseGradingInstructionText([{ text: '  Add Assessment Instruction text here  ', action: undefined }]);

        expect(exercise.gradingInstructions).toBeUndefined();

        const genuineInstructions = '  Assess the solution for correctness.  ';
        component.setExerciseGradingInstructionText([{ text: genuineInstructions, action: undefined }]);

        expect(exercise.gradingInstructions).toBe(genuineInstructions);
    });

    const getDomainActionArray = (ids?: { criterionId?: number; instructionId?: number }) => {
        const creditsAction = new GradingCreditsAction();
        const scaleAction = new GradingScaleAction();
        const descriptionAction = new GradingDescriptionAction();
        const feedbackAction = new GradingFeedbackAction();
        const usageCountAction = new GradingUsageCountAction();
        const instructionAction = new GradingInstructionAction(creditsAction, scaleAction, descriptionAction, feedbackAction, usageCountAction);
        const criterionAction = new GradingCriterionAction(instructionAction);
        const criterionText = ids?.criterionId != undefined ? `{id:${ids.criterionId}} testCriteria` : 'testCriteria';
        const instructionText = ids?.instructionId != undefined ? `{id:${ids.instructionId}}` : '';

        return [
            { text: criterionText, action: criterionAction },
            { text: instructionText, action: instructionAction },
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

    it('should retain persisted criterion and instruction ids when parsing text for used feedback', () => {
        exercise.gradingInstructionFeedbackUsed = true;
        exercise.gradingCriteria = [gradingCriterion];
        const originalCriterion = gradingCriterion;
        const originalInstruction = gradingInstruction;
        const domainActions = getDomainActionArray({ criterionId: 1, instructionId: 1 });
        domainActions[5] = { text: 'updated feedback', action: domainActions[5].action };

        component.onDomainActionsFound(domainActions);

        expect(exercise.gradingCriteria![0]).toBe(originalCriterion);
        expect(exercise.gradingCriteria![0].id).toBe(1);
        expect(exercise.gradingCriteria![0].structuredGradingInstructions[0]).toBe(originalInstruction);
        expect(exercise.gradingCriteria![0].structuredGradingInstructions[0].id).toBe(1);
        expect(exercise.gradingCriteria![0].structuredGradingInstructions[0].feedback).toBe('updated feedback');
    });

    it('should keep persisted ids on unchanged instructions when another instruction is inserted', () => {
        exercise.gradingInstructionFeedbackUsed = true;
        exercise.gradingCriteria = [gradingCriterion];
        const originalInstruction = gradingInstruction;
        const domainActions = getDomainActionArray({ criterionId: 1, instructionId: 1 });
        const creditsAction = domainActions[2].action;
        const scaleAction = domainActions[3].action;
        const descriptionAction = domainActions[4].action;
        const feedbackAction = domainActions[5].action;
        const usageCountAction = domainActions[6].action;
        const instructionAction = domainActions[1].action;
        // Insert a second instruction before the original — identity markers / content match must keep id 1 on the original row.
        domainActions.splice(
            1,
            0,
            { text: '', action: instructionAction },
            { text: '9', action: creditsAction },
            { text: 'inserted', action: scaleAction },
            { text: 'new', action: descriptionAction },
            { text: 'new feedback', action: feedbackAction },
            { text: '1', action: usageCountAction },
        );

        component.onDomainActionsFound(domainActions);

        const instructions = exercise.gradingCriteria![0].structuredGradingInstructions;
        expect(instructions).toHaveLength(2);
        expect(instructions[0].id).toBeUndefined();
        expect(instructions[0].gradingScale).toBe('inserted');
        expect(instructions[1]).toBe(originalInstruction);
        expect(instructions[1].id).toBe(1);
    });

    it('should keep instruction ids across reorders via identity markers', () => {
        exercise.gradingInstructionFeedbackUsed = true;
        const instructionA = { id: 10, credits: 1, gradingScale: 'a', instructionDescription: 'a', feedback: 'a', usageCount: 0 } as GradingInstruction;
        const instructionB = { id: 20, credits: 2, gradingScale: 'b', instructionDescription: 'b', feedback: 'b', usageCount: 0 } as GradingInstruction;
        const criterion = { id: 1, title: 'testCriteria', structuredGradingInstructions: [instructionA, instructionB] } as GradingCriterion;
        exercise.gradingCriteria = [criterion];

        const base = getDomainActionArray({ criterionId: 1, instructionId: 10 });
        const instructionAction = base[1].action;
        const creditsAction = base[2].action;
        const scaleAction = base[3].action;
        const descriptionAction = base[4].action;
        const feedbackAction = base[5].action;
        const usageCountAction = base[6].action;
        // Reorder: B then A (same shape — must not remount by position)
        const domainActions = [
            base[0],
            { text: '{id:20}', action: instructionAction },
            { text: '2', action: creditsAction },
            { text: 'b', action: scaleAction },
            { text: 'b', action: descriptionAction },
            { text: 'b', action: feedbackAction },
            { text: '0', action: usageCountAction },
            { text: '{id:10}', action: instructionAction },
            { text: '1', action: creditsAction },
            { text: 'a', action: scaleAction },
            { text: 'a', action: descriptionAction },
            { text: 'a', action: feedbackAction },
            { text: '0', action: usageCountAction },
        ] as TextWithDomainAction[];

        component.onDomainActionsFound(domainActions);

        const instructions = exercise.gradingCriteria![0].structuredGradingInstructions;
        expect(instructions[0]).toBe(instructionB);
        expect(instructions[0].id).toBe(20);
        expect(instructions[1]).toBe(instructionA);
        expect(instructions[1].id).toBe(10);
    });

    it('should keep instruction id when content and structure both change', () => {
        exercise.gradingInstructionFeedbackUsed = true;
        exercise.gradingCriteria = [gradingCriterion];
        const originalInstruction = gradingInstruction;
        const domainActions = getDomainActionArray({ criterionId: 1, instructionId: 1 });
        domainActions[5] = { text: 'edited feedback', action: domainActions[5].action };
        const instructionAction = domainActions[1].action;
        const creditsAction = domainActions[2].action;
        const scaleAction = domainActions[3].action;
        const descriptionAction = domainActions[4].action;
        const feedbackAction = domainActions[5].action;
        const usageCountAction = domainActions[6].action;
        domainActions.push(
            { text: '', action: instructionAction },
            { text: '3', action: creditsAction },
            { text: 'extra', action: scaleAction },
            { text: 'extra', action: descriptionAction },
            { text: 'extra', action: feedbackAction },
            { text: '0', action: usageCountAction },
        );

        component.onDomainActionsFound(domainActions);

        const instructions = exercise.gradingCriteria![0].structuredGradingInstructions;
        expect(instructions).toHaveLength(2);
        expect(instructions[0]).toBe(originalInstruction);
        expect(instructions[0].id).toBe(1);
        expect(instructions[0].feedback).toBe('edited feedback');
        expect(instructions[1].id).toBeUndefined();
    });

    it('should not transfer a persisted instruction id to a copy of a marked block placed before the original', () => {
        exercise.gradingInstructionFeedbackUsed = true;
        exercise.gradingCriteria = [gradingCriterion];
        const originalInstruction = gradingInstruction;
        const domainActions = getDomainActionArray({ criterionId: 1, instructionId: 1 });
        const instructionAction = domainActions[1].action;
        const creditsAction = domainActions[2].action;
        const scaleAction = domainActions[3].action;
        const descriptionAction = domainActions[4].action;
        const feedbackAction = domainActions[5].action;
        const usageCountAction = domainActions[6].action;
        // The marked block was copied and edited, then pasted above its original — both carry {id:1}.
        domainActions.splice(
            1,
            0,
            { text: '{id:1}', action: instructionAction },
            { text: '1', action: creditsAction },
            { text: 'scale', action: scaleAction },
            { text: 'description', action: descriptionAction },
            { text: 'copied feedback', action: feedbackAction },
            { text: '0', action: usageCountAction },
        );

        component.onDomainActionsFound(domainActions);

        const instructions = exercise.gradingCriteria![0].structuredGradingInstructions;
        expect(instructions).toHaveLength(2);
        expect(instructions[0]).not.toBe(originalInstruction);
        expect(instructions[0].id).toBeUndefined();
        expect(instructions[0].feedback).toBe('copied feedback');
        expect(instructions[1]).toBe(originalInstruction);
        expect(instructions[1].id).toBe(1);
        expect(instructions[1].feedback).toBe('feedback');
    });

    it('should keep title-less criterion identity across a used-feedback text round trip', () => {
        exercise.gradingInstructionFeedbackUsed = true;
        const instruction = {
            id: 11,
            credits: 1,
            gradingScale: 'scale',
            instructionDescription: 'description',
            feedback: 'feedback',
            usageCount: 0,
        } as GradingInstruction;
        const dummyCriterion = { id: 7, structuredGradingInstructions: [instruction] } as GradingCriterion;
        exercise.gradingCriteria = [dummyCriterion];

        const markdown = component.generateMarkdown();
        expect(markdown).toContain(`${GradingCriterionAction.IDENTIFIER} {id:7}\n`);
        expect(markdown).toContain(`${GradingInstructionAction.IDENTIFIER} {id:11}`);

        component.onDomainActionsFound(parseMarkdownForDomainActions(markdown, component.domainActionsForMainEditor));

        expect(exercise.gradingCriteria![0]).toBe(dummyCriterion);
        expect(exercise.gradingCriteria![0].id).toBe(7);
        expect(exercise.gradingCriteria![0].title).toBeUndefined();
        expect(exercise.gradingCriteria![0].structuredGradingInstructions[0]).toBe(instruction);
        expect(exercise.gradingCriteria![0].structuredGradingInstructions[0].id).toBe(11);
    });

    it('should drop unknown criterion marker ids that do not match the previous model', () => {
        exercise.gradingInstructionFeedbackUsed = true;
        exercise.gradingCriteria = [gradingCriterion];
        const domainActions = getDomainActionArray({ criterionId: 999, instructionId: 888 });
        domainActions[0] = { text: '{id:999} brand new criterion', action: domainActions[0].action };
        domainActions[5] = { text: 'brand new feedback', action: domainActions[5].action };

        component.onDomainActionsFound(domainActions);

        expect(exercise.gradingCriteria![0]).not.toBe(gradingCriterion);
        expect(exercise.gradingCriteria![0].id).toBeUndefined();
        expect(exercise.gradingCriteria![0].title).toBe('brand new criterion');
        expect(exercise.gradingCriteria![0].structuredGradingInstructions[0].id).toBeUndefined();
    });

    it('should drop unknown instruction marker ids that do not match the previous model', () => {
        exercise.gradingInstructionFeedbackUsed = true;
        exercise.gradingCriteria = [gradingCriterion];
        const domainActions = getDomainActionArray({ criterionId: 1, instructionId: 999 });
        domainActions[2] = { text: '9', action: domainActions[2].action };
        domainActions[3] = { text: 'unknown', action: domainActions[3].action };
        domainActions[4] = { text: 'unknown', action: domainActions[4].action };
        domainActions[5] = { text: 'unknown feedback', action: domainActions[5].action };

        component.onDomainActionsFound(domainActions);

        expect(exercise.gradingCriteria![0]).toBe(gradingCriterion);
        expect(exercise.gradingCriteria![0].id).toBe(1);
        expect(exercise.gradingCriteria![0].structuredGradingInstructions[0]).not.toBe(gradingInstruction);
        expect(exercise.gradingCriteria![0].structuredGradingInstructions[0].id).toBeUndefined();
        expect(exercise.gradingCriteria![0].structuredGradingInstructions[0].feedback).toBe('unknown feedback');
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
