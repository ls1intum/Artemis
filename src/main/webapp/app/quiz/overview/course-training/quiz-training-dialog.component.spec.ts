import { ComponentFixture, TestBed } from '@angular/core/testing';
import { QuizTrainingDialogComponent } from './quiz-training-dialog.component';
import { MockBuilder } from 'ng-mocks';
import { DialogModule } from 'primeng/dialog';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

describe('QuizTrainingDialogComponent', () => {
    let component: QuizTrainingDialogComponent;
    let fixture: ComponentFixture<QuizTrainingDialogComponent>;

    beforeEach(async () => {
        await MockBuilder(QuizTrainingDialogComponent).keep(DialogModule).keep(FontAwesomeModule);

        fixture = TestBed.createComponent(QuizTrainingDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.clearAllMocks();
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
