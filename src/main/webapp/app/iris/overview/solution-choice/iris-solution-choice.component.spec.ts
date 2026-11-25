import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { IrisSolutionChoiceComponent } from './iris-solution-choice.component';

describe('IrisSolutionChoiceComponent', () => {
    let fixture: ComponentFixture<IrisSolutionChoiceComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [IrisSolutionChoiceComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(IrisSolutionChoiceComponent);
        fixture.detectChanges();
    });

    it.each([
        ['iris-try-myself', 'TRY_MYSELF'],
        ['iris-show-solution', 'SHOW_SOLUTION'],
    ])('emits an explicit action for %s', (testId, choice) => {
        const emit = vi.spyOn(fixture.componentInstance.choice, 'emit');
        fixture.nativeElement.querySelector(`[data-testid="${testId}"]`).click();
        expect(emit).toHaveBeenCalledExactlyOnceWith(choice);
    });

    it('disables both buttons while another answer is pending or the offer is stale', () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        const emit = vi.spyOn(fixture.componentInstance.choice, 'emit');
        const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
        expect(buttons).toHaveLength(2);
        for (const button of buttons) {
            expect(button.disabled).toBe(true);
            button.click();
        }
        expect(emit).not.toHaveBeenCalled();
    });

    it('restores the selection and prevents changing an already consumed offer', () => {
        fixture.componentRef.setInput('selected', 'SHOW_SOLUTION');
        fixture.detectChanges();
        const selected: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="iris-show-solution"]');
        expect(selected.getAttribute('aria-pressed')).toBe('true');
        expect(selected.disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="iris-try-myself"]').disabled).toBe(true);
    });
});
