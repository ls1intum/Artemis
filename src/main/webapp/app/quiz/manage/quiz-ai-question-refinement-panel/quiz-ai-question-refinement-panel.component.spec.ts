import { afterEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { EnvironmentInjector, runInInjectionContext } from '@angular/core';
import { of, throwError } from 'rxjs';
import { QuizAiQuestionRefinementPanelComponent } from './quiz-ai-question-refinement-panel.component';
import { QuizAiGenerationService } from 'app/quiz/manage/update/quiz-ai-generation-modal/quiz-ai-generation.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('QuizAiQuestionRefinementPanelComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<QuizAiQuestionRefinementPanelComponent>;
    let component: QuizAiQuestionRefinementPanelComponent;
    let quizAiGenerationService: QuizAiGenerationService;
    let alertService: AlertService;
    let envInjector: EnvironmentInjector;

    function createMockQuestion(): MultipleChoiceQuestion {
        const q = new MultipleChoiceQuestion();
        q.id = 1;
        q.title = 'Test Question';
        q.text = 'What is 2+2?';
        q.singleChoice = true;
        q.answerOptions = [];
        return q;
    }

    function setupWithHyperionEnabled(enabled: boolean): void {
        const mockProfileService = new MockProfileService();
        vi.spyOn(mockProfileService, 'isModuleFeatureActive').mockReturnValue(enabled);

        TestBed.configureTestingModule({
            providers: [
                { provide: ProfileService, useValue: mockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });

        fixture = TestBed.createComponent(QuizAiQuestionRefinementPanelComponent);
        component = fixture.componentInstance;
        quizAiGenerationService = TestBed.inject(QuizAiGenerationService);
        alertService = TestBed.inject(AlertService);
        envInjector = TestBed.inject(EnvironmentInjector);

        fixture.componentRef.setInput('question', createMockQuestion());
        fixture.componentRef.setInput('courseId', 42);
        fixture.componentRef.setInput('isOpen', true);
        fixture.detectChanges();
    }

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should not render panel when hyperion is disabled', () => {
        setupWithHyperionEnabled(false);

        const panel = fixture.debugElement.query(By.css('.quiz-ai-refinement-panel'));
        expect(panel).toBeNull();
    });

    it('should not render panel when isOpen is false', () => {
        setupWithHyperionEnabled(true);
        fixture.componentRef.setInput('isOpen', false);
        fixture.detectChanges();

        const panel = fixture.debugElement.query(By.css('.quiz-ai-refinement-panel'));
        expect(panel).toBeNull();
    });

    it('should render panel when hyperion is enabled and isOpen is true', () => {
        setupWithHyperionEnabled(true);

        const panel = fixture.debugElement.query(By.css('.quiz-ai-refinement-panel'));
        expect(panel).not.toBeNull();
    });

    it('should not call service when prompt is empty', () => {
        setupWithHyperionEnabled(true);
        const spy = vi.spyOn(quizAiGenerationService, 'refineMultipleChoiceQuestion');

        component.refinePrompt.set('   ');
        component.submitRefinement();

        expect(spy).not.toHaveBeenCalled();
    });

    it('should not call service when already refining', () => {
        setupWithHyperionEnabled(true);
        const spy = vi.spyOn(quizAiGenerationService, 'refineMultipleChoiceQuestion');

        component.refinePrompt.set('make it harder');
        component.isRefining.set(true);
        component.submitRefinement();

        expect(spy).not.toHaveBeenCalled();
    });

    it('should call service and emit refined question on success', () => {
        vi.useFakeTimers();
        setupWithHyperionEnabled(true);

        const refinedQuestion = new MultipleChoiceQuestion();
        refinedQuestion.title = 'Refined Title';
        vi.spyOn(quizAiGenerationService, 'refineMultipleChoiceQuestion').mockReturnValue(of({ refinedQuestion, reasoning: 'Some explanation.' }));

        const emittedValues: MultipleChoiceQuestion[] = [];
        component.questionRefined.subscribe((q) => emittedValues.push(q));

        component.refinePrompt.set('make it harder');
        runInInjectionContext(envInjector, () => component.submitRefinement());
        vi.advanceTimersByTime(200);
        fixture.detectChanges();

        expect(component.refinementExplanation()).toBeDefined();
        expect(component.refinePrompt()).toBe('');
        expect(component.isRefining()).toBe(false);
        expect(emittedValues).toHaveLength(1);
        expect(emittedValues[0]).toBe(refinedQuestion);

        vi.useRealTimers();
    });

    it('should show error alert on service failure', () => {
        vi.useFakeTimers();
        setupWithHyperionEnabled(true);
        vi.spyOn(quizAiGenerationService, 'refineMultipleChoiceQuestion').mockReturnValue(throwError(() => new Error('AI error')));
        const alertSpy = vi.spyOn(alertService, 'error');

        component.refinePrompt.set('make it harder');
        runInInjectionContext(envInjector, () => component.submitRefinement());
        vi.advanceTimersByTime(200);

        expect(alertSpy).toHaveBeenCalledWith('artemisApp.quizExercise.aiGeneration.refinement.errors.failed');
        expect(component.isRefining()).toBe(false);

        vi.useRealTimers();
    });

    it('should reset state when ai refinement panel was closed', async () => {
        setupWithHyperionEnabled(true);

        component.refinementExplanation.set('Some explanation');
        component.refinePrompt.set('some prompt');

        fixture.componentRef.setInput('isOpen', false);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.refinePrompt()).toBe('');
        expect(component.refinementExplanation()).toBeUndefined();
        expect(component.isRefining()).toBe(false);
    });

    it('should show explanation card after successful refinement', () => {
        vi.useFakeTimers();
        setupWithHyperionEnabled(true);

        const refinedQuestion = new MultipleChoiceQuestion();
        const reasoning = 'Some explanation.';
        vi.spyOn(quizAiGenerationService, 'refineMultipleChoiceQuestion').mockReturnValue(of({ refinedQuestion, reasoning }));

        component.refinePrompt.set('improve');
        runInInjectionContext(envInjector, () => component.submitRefinement());
        vi.advanceTimersByTime(200);
        fixture.detectChanges();

        const explanationCard = fixture.debugElement.query(By.css('.refinement-explanation-card'));
        expect(explanationCard).not.toBeNull();
        const explanationText = fixture.debugElement.query(By.css('.refinement-explanation-text'));
        expect(explanationText.nativeElement.textContent).toContain(reasoning);

        vi.useRealTimers();
    });
});
