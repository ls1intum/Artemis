import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockComponent, MockDirective } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DynamicDialogRef } from 'primeng/dynamicdialog';

import { IrisInClassQuizStartWarningComponent } from 'app/iris/overview/ask-user/assessment-review-overview/in-class-quiz-start-warning/iris-in-class-quiz-start-warning.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

describe('IrisInClassQuizStartWarningComponent', () => {
    let fixture: ComponentFixture<IrisInClassQuizStartWarningComponent>;
    let dialogRef: { close: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        dialogRef = { close: vi.fn() };

        await TestBed.configureTestingModule({
            imports: [IrisInClassQuizStartWarningComponent],
            providers: [{ provide: DynamicDialogRef, useValue: dialogRef }],
        })
            .overrideComponent(IrisInClassQuizStartWarningComponent, {
                remove: { imports: [TranslateDirective, FaIconComponent] },
                add: { imports: [MockDirective(TranslateDirective), MockComponent(FaIconComponent)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(IrisInClassQuizStartWarningComponent);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should close the dialog with false when cancelled', () => {
        fixture.debugElement.query(By.css('#cancel-button')).nativeElement.click();

        expect(dialogRef.close).toHaveBeenCalledExactlyOnceWith(false);
    });

    it('should close the dialog with true when the quiz start is confirmed', () => {
        fixture.debugElement.query(By.css('#start-in-class-quiz-button')).nativeElement.click();

        expect(dialogRef.close).toHaveBeenCalledExactlyOnceWith(true);
    });
});
