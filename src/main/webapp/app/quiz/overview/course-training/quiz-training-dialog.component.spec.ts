import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QuizTrainingDialogComponent } from './quiz-training-dialog.component';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('QuizTrainingDialogComponent', () => {
    let component: QuizTrainingDialogComponent;
    let fixture: ComponentFixture<QuizTrainingDialogComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [QuizTrainingDialogComponent, FontAwesomeModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizTrainingDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should disable save when showInLeaderboard equals initialShowInLeaderboard', () => {
        component.showInLeaderboard.set(true);
        fixture.componentRef.setInput('initialShowInLeaderboard', true);
        fixture.detectChanges();

        expect(component.saveDisabled()).toBe(true);
    });

    it('should enable save when showInLeaderboard differs from initialShowInLeaderboard', () => {
        component.showInLeaderboard.set(false);
        fixture.componentRef.setInput('initialShowInLeaderboard', true);
        fixture.detectChanges();

        expect(component.saveDisabled()).toBe(false);
    });

    it('should always enable save when disableSaveValidation is true', () => {
        component.showInLeaderboard.set(true);
        fixture.componentRef.setInput('initialShowInLeaderboard', true);
        fixture.componentRef.setInput('disableSaveValidation', true);
        fixture.detectChanges();

        expect(component.saveDisabled()).toBe(false);
    });

    describe('dialog', () => {
        const open = async (closable: boolean) => {
            fixture.componentRef.setInput('closable', closable);
            component.visible.set(true);
            fixture.detectChanges();
            await fixture.whenStable();
        };
        const pressEscape = async () => {
            document.querySelector('[role="dialog"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
            fixture.detectChanges();
            await fixture.whenStable();
        };

        afterEach(() => {
            component.visible.set(false);
            fixture.detectChanges();
        });

        it('should render the explanation and the confirm button in a modal dialog', async () => {
            await open(false);

            const content = document.querySelector('[data-testid="quiz-training-dialog"]');
            expect(content).not.toBeNull();
            expect(document.querySelector('[role="dialog"]')!.contains(content)).toBe(true);
            expect(document.querySelector('[data-testid="quiz-training-confirm-button"]')).not.toBeNull();
        });

        it('should keep a non-closable dialog open on Escape', async () => {
            await open(false);

            await pressEscape();

            expect(component.visible()).toBe(true);
        });

        it('should close a closable dialog on Escape', async () => {
            await open(true);

            await pressEscape();

            expect(component.visible()).toBe(false);
        });
    });
});
