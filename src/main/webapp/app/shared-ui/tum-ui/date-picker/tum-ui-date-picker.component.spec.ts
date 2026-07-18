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
        // Freeze only the clock (not setTimeout / rAF) so `dayjs()`-based assertions (e.g. the "today" fallback)
        // are deterministic and can't flake across midnight, while overlay/focus timing stays real.
        vi.useFakeTimers({ toFake: ['Date'] });
        vi.setSystemTime(new Date('2026-07-15T12:00:00'));
        await TestBed.configureTestingModule({
            imports: [TumUiDatePickerComponent, FontAwesomeTestingModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(TumUiDatePickerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

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

    it('does not wipe in-progress invalid text when the value is re-supplied as an equal-but-fresh instance', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        // User is mid-edit and the text is momentarily unparseable (the keepInvalid window).
        input().value = '13.06.2026 08:30xx';
        input().dispatchEvent(new Event('input'));
        expect(component.isValid()).toBe(false);
        // A concurrent change detection re-supplies [value] as a NEW dayjs of the SAME instant — the churn a
        // consumer causes with [value]="dayjs(x)". Because the fields key on a minute-precision identity (not the
        // object reference), this must NOT re-seed the input and destroy the edit + error state.
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        expect(component.isValid()).toBe(false);
        expect(input().value).toBe('13.06.2026 08:30xx');
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

    function openPanel(): void {
        fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-trigger"]')).nativeElement.click();
        fixture.detectChanges();
    }

    function timeField(testId: string): HTMLInputElement {
        return document.querySelector(`[data-testid="${testId}"]`) as HTMLInputElement;
    }

    function timeButton(testId: string): HTMLButtonElement {
        return document.querySelector(`[data-testid="${testId}"]`) as HTMLButtonElement;
    }

    it('updates the time-of-day by typing into the hour and minute fields', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        openPanel();
        const hour = timeField('tum-ui-date-picker-hour');
        const minute = timeField('tum-ui-date-picker-minute');
        // The 24h spinner seeds from the committed value.
        expect(hour.value).toBe('08');
        expect(minute.value).toBe('30');
        hour.value = '10';
        hour.dispatchEvent(new Event('change'));
        minute.value = '45';
        minute.dispatchEvent(new Event('change'));
        expect(component.value()?.format('HH:mm')).toBe('10:45');
    });

    it('zero-pads a single-digit typed hour/minute', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        openPanel();
        const hour = timeField('tum-ui-date-picker-hour');
        hour.value = '9';
        hour.dispatchEvent(new Event('change'));
        expect(component.value()?.format('HH:mm')).toBe('09:30');
        expect(hour.value).toBe('09');
    });

    it('rejects an out-of-range typed hour and reverts the field', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        openPanel();
        const hour = timeField('tum-ui-date-picker-hour');
        hour.value = '25';
        hour.dispatchEvent(new Event('change'));
        // Out of [0, 23]: the value is untouched and the field reverts to the committed hour.
        expect(component.value()?.format('HH:mm')).toBe('08:30');
        expect(hour.value).toBe('08');
    });

    it('increments and wraps the hour via the spinner buttons (23 -> 00, no day change)', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T23:30'));
        fixture.detectChanges();
        openPanel();
        timeButton('tum-ui-date-picker-hour-up').click();
        expect(component.value()?.format('DD.MM.YYYY HH:mm')).toBe('13.06.2026 00:30');
    });

    it('decrements and wraps the minute via the spinner buttons (00 -> 59, no hour change)', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:00'));
        fixture.detectChanges();
        openPanel();
        timeButton('tum-ui-date-picker-minute-down').click();
        expect(component.value()?.format('HH:mm')).toBe('08:59');
    });

    it('nudges the hour with ArrowUp / ArrowDown for keyboard users', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        openPanel();
        const hour = timeField('tum-ui-date-picker-hour');
        hour.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        expect(component.value()?.format('HH:mm')).toBe('09:30');
        hour.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        expect(component.value()?.format('HH:mm')).toBe('08:30');
    });

    it('steps ArrowUp from the uncommitted typed field value, not the committed value', () => {
        // Regression: typing "10" over "08" and pressing ArrowUp before blur (no change event yet) must
        // step from the typed 10 (-> 11), preserving the edit, not from the committed 08 (-> 09).
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        openPanel();
        const hour = timeField('tum-ui-date-picker-hour');
        hour.value = '10'; // typed but not yet committed (no 'change' dispatched)
        hour.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        expect(component.value()?.format('HH:mm')).toBe('11:30');
        expect(hour.value).toBe('11');
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

    it('closes the overlay when the control is disabled while the panel is open', () => {
        fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-trigger"]')).nativeElement.click();
        fixture.detectChanges();
        expect(document.querySelector('tum-ui-calendar')).not.toBeNull();
        // Disabling mid-open (e.g. a Signal Forms disable) must close the panel so a now-disabled control
        // can no longer commit values from the calendar / time field.
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        expect(document.querySelector('tum-ui-calendar')).toBeNull();
    });

    it('bases the date on today, not the 1st of the month, when the time is set on an empty picker', () => {
        // Regression: with no value yet, editing the time must not silently commit the 1st of the current
        // month (activeMonth is always month-start). It should fall back to today, like a day selection.
        expect(component.value()).toBeUndefined();
        openPanel();
        const hour = timeField('tum-ui-date-picker-hour');
        const minute = timeField('tum-ui-date-picker-minute');
        // An empty picker seeds the 24h spinner to 00:00.
        expect(hour.value).toBe('00');
        expect(minute.value).toBe('00');
        hour.value = '10';
        hour.dispatchEvent(new Event('change'));
        minute.value = '45';
        minute.dispatchEvent(new Event('change'));
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
