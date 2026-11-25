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
        fixture.componentRef.setInput('choices', ['TRY_MYSELF', 'SHOW_SOLUTION']);
        fixture.detectChanges();
    });

    it.each(['TRY_MYSELF', 'SHOW_SOLUTION'])('emits an explicit action for %s', (choice) => {
        const emit = vi.spyOn(fixture.componentInstance.choice, 'emit');
        fixture.nativeElement.querySelector(`[data-testid="iris-choice-${choice}"]`).click();
        expect(emit).toHaveBeenCalledExactlyOnceWith(choice);
    });

    it('shows the buttons of the offer in its order', () => {
        fixture.componentRef.setInput('choices', ['SUMMARIZE', 'SKIP_SUMMARY']);
        fixture.detectChanges();
        const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
        expect(buttons.map((button) => button.getAttribute('data-testid'))).toEqual(['iris-choice-SUMMARIZE', 'iris-choice-SKIP_SUMMARY']);
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

    it('keeps only the chosen button, pressed and disabled, so an offer cannot be answered twice', () => {
        fixture.componentRef.setInput('selected', 'SHOW_SOLUTION');
        fixture.detectChanges();
        const selected: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="iris-choice-SHOW_SOLUTION"]');
        expect(selected.getAttribute('aria-pressed')).toBe('true');
        expect(selected.disabled).toBe(true);
        expect(fixture.nativeElement.querySelector('[data-testid="iris-choice-TRY_MYSELF"]')).toBeNull();
    });
});
