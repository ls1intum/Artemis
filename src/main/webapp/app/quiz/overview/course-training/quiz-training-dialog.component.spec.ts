import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QuizTrainingDialogComponent } from './quiz-training-dialog.component';
import { DialogModule } from 'primeng/dialog';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('QuizTrainingDialogComponent', () => {
    setupTestBed({ zoneless: true });
    let component: QuizTrainingDialogComponent;
    let fixture: ComponentFixture<QuizTrainingDialogComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [QuizTrainingDialogComponent, DialogModule, FontAwesomeModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(QuizTrainingDialogComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should disable save when showInLeaderboard equals initialShowInLeaderboard', () => {
        component.showInLeaderboard.set(true);
        fixture.componentRef.setInput('initialShowInLeaderboard', true);
        fixture.detectChanges();

        expect(component.saveDisabled()).toBeTrue();
    });

    it('should enable save when showInLeaderboard differs from initialShowInLeaderboard', () => {
        component.showInLeaderboard.set(false);
        fixture.componentRef.setInput('initialShowInLeaderboard', true);
        fixture.detectChanges();

        expect(component.saveDisabled()).toBeFalse();
    });

    it('should always enable save when disableSaveValidation is true', () => {
        component.showInLeaderboard.set(true);
        fixture.componentRef.setInput('initialShowInLeaderboard', true);
        fixture.componentRef.setInput('disableSaveValidation', true);
        fixture.detectChanges();

        expect(component.saveDisabled()).toBeFalse();
    });
});
