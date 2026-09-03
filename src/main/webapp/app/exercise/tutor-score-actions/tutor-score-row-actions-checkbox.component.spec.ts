import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TutorScoreRowActionsCheckboxComponent } from 'app/exercise/tutor-score-actions/tutor-score-row-actions-checkbox.component';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TutorScoreRowActionsCheckboxComponent', () => {
    let fixture: ComponentFixture<TutorScoreRowActionsCheckboxComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorScoreRowActionsCheckboxComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorScoreRowActionsCheckboxComponent);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render the checkbox reflecting the current flag value', async () => {
        const exercise = { id: 1, title: 'Exercise 1', allowTutorScoreRowActions: true } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const checkbox = fixture.nativeElement.querySelector('#field_allowTutorScoreRowActions') as HTMLInputElement;
        expect(checkbox).toBeTruthy();
        expect(checkbox.checked).toBe(true);
    });

    it('should update the exercise flag when the checkbox is toggled', async () => {
        const exercise = { id: 2, title: 'Exercise 2', allowTutorScoreRowActions: false } as Exercise;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const checkbox = fixture.nativeElement.querySelector('#field_allowTutorScoreRowActions') as HTMLInputElement;
        checkbox.checked = true;
        checkbox.dispatchEvent(new Event('change'));
        fixture.detectChanges();

        expect(exercise.allowTutorScoreRowActions).toBe(true);
    });
});
