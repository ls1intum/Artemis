import { describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { QuizQuestionPoolSettingsDialogComponent } from './quiz-question-pool-settings-dialog.component';
import { PoolSettings } from 'app/hyperion/manage/quiz-question-pool/quiz-question-pool.types';

describe('QuizQuestionPoolSettingsDialogComponent', () => {
    let fixture: ComponentFixture<QuizQuestionPoolSettingsDialogComponent>;
    let component: QuizQuestionPoolSettingsDialogComponent;

    const settings: PoolSettings = {
        poolGenerationEnabled: false,
        singleChoiceQuestionsPerLectureUnit: 12,
        multipleChoiceEnabled: false,
        multipleChoiceQuestionsPerLectureUnit: 12,
        multipleChoiceMinCorrectOptions: 1,
        multipleChoiceMaxCorrectOptions: 4,
        easyPercent: 20,
        mediumPercent: 50,
        hardPercent: 30,
    };

    function setup(saving = false): void {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(QuizQuestionPoolSettingsDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('visible', true);
        fixture.componentRef.setInput('settings', settings);
        fixture.componentRef.setInput('saving', saving);
        fixture.detectChanges();
    }

    function query(selector: string): HTMLElement | null {
        return document.querySelector(selector);
    }

    it('should start from the stored settings each time it opens', () => {
        setup();
        expect(component.draft()).toEqual(settings);

        component.setSingleChoiceQuestionsPerLectureUnit(30);
        fixture.componentRef.setInput('visible', false);
        fixture.detectChanges();
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();

        expect(component.draft()).toEqual(settings);
    });

    it('should emit the draft on save and nothing while it is invalid', () => {
        setup();
        const emitted = vi.fn();
        component.save.subscribe(emitted);

        component.setSingleChoiceQuestionsPerLectureUnit(0);
        expect(component.isValid()).toBe(false);
        component.submit();
        expect(emitted).not.toHaveBeenCalled();

        component.setSingleChoiceQuestionsPerLectureUnit(30);
        component.setPoolGenerationEnabled(true);
        component.submit();

        expect(emitted).toHaveBeenCalledWith({ ...settings, poolGenerationEnabled: true, singleChoiceQuestionsPerLectureUnit: 30 });
    });

    it('should show the multiple-choice count only while multiple-choice questions are enabled', () => {
        setup();
        expect(query('#pool-multiple-choice-questions')).toBeNull();

        component.setMultipleChoiceEnabled(true);
        fixture.detectChanges();

        expect(query('#pool-multiple-choice-questions')).not.toBeNull();
        component.setMultipleChoiceQuestionsPerLectureUnit(61);
        expect(component.isValid()).toBe(false);
        component.setMultipleChoiceQuestionsPerLectureUnit(5);
        expect(component.isValid()).toBe(true);
    });

    it('should refuse a correct-option range that is out of order while multiple-choice questions are enabled', () => {
        setup();
        component.setMultipleChoiceEnabled(true);
        fixture.detectChanges();
        expect(query('[data-testid="pool-correct-options"]')).not.toBeNull();
        expect(query('[data-testid="pool-correct-options-error"]')).toBeNull();

        component.setMultipleChoiceMinCorrectOptions(3);
        component.setMultipleChoiceMaxCorrectOptions(2);
        fixture.detectChanges();

        expect(component.correctOptionRangeOrdered()).toBe(false);
        expect(component.isValid()).toBe(false);
        expect(query('[data-testid="pool-correct-options-error"]')).not.toBeNull();

        component.setMultipleChoiceMaxCorrectOptions(5);
        expect(component.isValid()).toBe(false);
        component.setMultipleChoiceMaxCorrectOptions(4);
        fixture.detectChanges();

        expect(component.isValid()).toBe(true);
        expect(query('[data-testid="pool-correct-options-error"]')).toBeNull();
    });

    it('should refuse a difficulty mix that does not add up to 100 percent', () => {
        setup();
        expect(query('[data-testid="pool-difficulty-mix-error"]')).toBeNull();

        component.setEasyPercent(30);
        fixture.detectChanges();

        expect(component.difficultyPercentSum()).toBe(110);
        expect(component.isValid()).toBe(false);
        expect(query('[data-testid="pool-difficulty-mix-error"]')).not.toBeNull();

        component.setHardPercent(20);
        component.setMediumPercent(50);
        fixture.detectChanges();

        expect(component.isValid()).toBe(true);
        expect(query('[data-testid="pool-difficulty-mix-error"]')).toBeNull();
    });

    it('should lock the controls while a save is pending', () => {
        setup(true);

        expect((query('#pool-single-choice-questions') as HTMLInputElement).disabled).toBe(true);
        expect((query('#pool-generation-enabled') as HTMLInputElement).disabled).toBe(true);
        expect((query('[data-testid="pool-save-settings-button"]') as HTMLButtonElement).disabled).toBe(true);
    });

    it('should close on cancel', () => {
        setup();

        component.cancel();

        expect(component.visible()).toBe(false);
    });
});
