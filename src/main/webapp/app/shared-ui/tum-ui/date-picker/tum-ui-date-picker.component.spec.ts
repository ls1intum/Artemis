import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';
import { TumUiDatePickerComponent } from 'app/shared-ui/tum-ui/date-picker/tum-ui-date-picker.component';

// Host to exercise two-way [(value)] binding, which relies on the model's own valueChange output.
@Component({
    imports: [TumUiDatePickerComponent],
    template: `<tum-ui-date-picker [(value)]="value" />`,
})
class TwoWayHostComponent {
    value?: dayjs.Dayjs;
}

describe('TumUiDatePickerComponent', () => {
    let component: TumUiDatePickerComponent;
    let fixture: ComponentFixture<TumUiDatePickerComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TumUiDatePickerComponent, FontAwesomeTestingModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiDatePickerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => vi.restoreAllMocks());

    function input(): HTMLInputElement {
        return fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-input"]')).nativeElement;
    }

    it('reflects an external value as formatted text and is valid', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        expect(input().value).toBe('13.06.2026 08:30');
        expect(component.isValid()).toBe(true);
    });

    it('parses typed text into the value', () => {
        input().value = '13.06.2026 09:15';
        input().dispatchEvent(new Event('input'));
        expect(component.value()?.format('DD.MM.YYYY HH:mm')).toBe('13.06.2026 09:15');
        expect(component.isValid()).toBe(true);
    });

    it('flags invalid typed text without changing the value or wiping the text (keepInvalid)', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        input().value = '13.06.2026 08:30xx';
        input().dispatchEvent(new Event('input'));
        expect(component.isValid()).toBe(false);
        expect(component.value()?.format('DD.MM.YYYY HH:mm')).toBe('13.06.2026 08:30');
        expect(input().value).toBe('13.06.2026 08:30xx'); // typed text preserved, not cleared
    });

    it('clears the value and the displayed text', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-clear"]')).nativeElement.click();
        fixture.detectChanges();
        expect(component.value()).toBeUndefined();
        expect(input().value).toBe('');
    });

    it('re-validates when the value changes externally after invalid input (no stuck error border)', () => {
        input().value = 'garbage';
        input().dispatchEvent(new Event('input'));
        expect(component.isValid()).toBe(false);
        // An external value write (e.g. the consumer or a form control) must clear the stale invalid state.
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        expect(component.isValid()).toBe(true);
        expect(input().value).toBe('13.06.2026 08:30');
    });

    it('shows the error border and is invalid when [error] is set', () => {
        fixture.componentRef.setInput('error', true);
        fixture.detectChanges();
        expect(input().classList).toContain('border-state-danger');
        expect(component.isValid()).toBe(false);
    });

    it('reports valid parsed input via hasValidInput() even while an external [error] is set', () => {
        // Regression: consumers gate (valueChange) on hasValidInput(), NOT isValid(). An external error
        // (e.g. a from>to range) must not suppress a genuinely-parsed value, or the error can never be
        // corrected (the value would be wiped to undefined on every edit).
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.componentRef.setInput('error', true);
        fixture.detectChanges();
        expect(component.isValid()).toBe(false); // combined validity reflects the external error
        expect(component.hasValidInput()).toBe(true); // but the typed date still parses
        // Typing garbage flips hasValidInput() to false regardless of the external error.
        input().value = 'not a date';
        input().dispatchEvent(new Event('input'));
        expect(component.hasValidInput()).toBe(false);
    });

    it('opens the calendar overlay on trigger click and closes via Done', () => {
        fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-trigger"]')).nativeElement.click();
        fixture.detectChanges();
        expect(document.querySelector('tum-ui-calendar')).not.toBeNull();
        (document.querySelector('[data-testid="tum-ui-date-picker-done"] button') as HTMLElement).click();
        fixture.detectChanges();
        expect(document.querySelector('tum-ui-calendar')).toBeNull();
    });

    it('rejects trailing garbage on blur', () => {
        input().value = '13.06.2026 08:30 and more';
        input().dispatchEvent(new Event('blur'));
        expect(component.isValid()).toBe(false);
    });

    it('sets the value to undefined when the input is emptied', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        input().value = '';
        input().dispatchEvent(new Event('input'));
        expect(component.value()).toBeUndefined();
    });

    it('updates the time-of-day via the time field', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-trigger"]')).nativeElement.click();
        fixture.detectChanges();
        const timeInput = document.querySelector('[data-testid="tum-ui-date-picker-time"]') as HTMLInputElement;
        timeInput.value = '10:45';
        timeInput.dispatchEvent(new Event('input'));
        expect(component.value()?.format('HH:mm')).toBe('10:45');
    });

    it('selects a day from the calendar overlay', () => {
        fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-trigger"]')).nativeElement.click();
        fixture.detectChanges();
        (document.querySelector('td[role="gridcell"] button') as HTMLElement).click();
        expect(component.value()).toBeDefined();
    });

    it('does not open when disabled', () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-trigger"]')).nativeElement.click();
        fixture.detectChanges();
        expect(document.querySelector('tum-ui-calendar')).toBeNull();
    });

    it('bases the date on today, not the 1st of the month, when the time is set on an empty picker', () => {
        // Regression: with no value yet, editing the time must not silently commit the 1st of the current
        // month (activeMonth is always month-start). It should fall back to today, like a day selection.
        expect(component.value()).toBeUndefined();
        fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-trigger"]')).nativeElement.click();
        fixture.detectChanges();
        const timeInput = document.querySelector('[data-testid="tum-ui-date-picker-time"]') as HTMLInputElement;
        timeInput.value = '10:45';
        timeInput.dispatchEvent(new Event('input'));
        expect(component.value()?.format('YYYY-MM-DD')).toBe(dayjs().format('YYYY-MM-DD'));
        expect(component.value()?.format('HH:mm')).toBe('10:45');
    });

    it('renders the timezone warning by default and hides it when disabled', () => {
        // The input defaults to true and must actually render a warning (it is a real, read input, not a stub).
        expect(fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-tz-warning"]'))).not.toBeNull();
        fixture.componentRef.setInput('shouldDisplayTimeZoneWarning', false);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-tz-warning"]'))).toBeNull();
    });

    it('exposes the timezone warning to keyboard and screen-reader users', () => {
        const warning = fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-tz-warning"]')).nativeElement as HTMLElement;
        // Focusable, so keyboard users can reach it and the tooltip directive's focusin handler shows the warning
        // (a non-focusable host would never receive focus, hiding the warning and its aria-describedby from them).
        expect(warning.getAttribute('tabindex')).toBe('0');
        // Roled and named, so screen readers announce it rather than skipping a decorative icon stack.
        expect(warning.getAttribute('role')).toBe('img');
        expect(warning.getAttribute('aria-label')).toBeTruthy();
    });

    describe('two-way [(value)] binding', () => {
        function hostInput(host: ComponentFixture<TwoWayHostComponent>): HTMLInputElement {
            return host.debugElement.query(By.css('[data-testid="tum-ui-date-picker-input"]')).nativeElement;
        }

        it('writes a committed value back to the parent (model output is not shadowed)', () => {
            const host = TestBed.createComponent(TwoWayHostComponent);
            host.detectChanges();
            const inp = hostInput(host);
            inp.value = '13.06.2026 09:15';
            inp.dispatchEvent(new Event('input'));
            host.detectChanges();
            expect(host.componentInstance.value?.format('DD.MM.YYYY HH:mm')).toBe('13.06.2026 09:15');
        });

        it('does not wipe the typed text on an invalid edit (no undefined echo through the parent)', () => {
            const host = TestBed.createComponent(TwoWayHostComponent);
            host.componentInstance.value = dayjs('2026-06-13T08:30');
            host.detectChanges();
            const inp = hostInput(host);
            inp.value = '13.06.2026 08:3'; // incomplete -> unparseable
            inp.dispatchEvent(new Event('input'));
            host.detectChanges();
            expect(inp.value).toBe('13.06.2026 08:3'); // text preserved
            expect(host.componentInstance.value?.format('DD.MM.YYYY HH:mm')).toBe('13.06.2026 08:30'); // value untouched
        });
    });
});
