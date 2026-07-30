import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FormField, form } from '@angular/forms/signals';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import dayjs from 'dayjs/esm';
import { TumUiDatePickerComponent } from './tum-ui-date-picker.component';

@Component({
    imports: [TumUiDatePickerComponent],
    template: `<tum-ui-date-picker [(value)]="value" />`,
})
class TwoWayHostComponent {
    value?: dayjs.Dayjs;
}

@Component({
    imports: [TumUiDatePickerComponent, FormField],
    template: `<tum-ui-date-picker [formField]="date" />`,
})
class SignalFormHostComponent {
    readonly model = signal<dayjs.Dayjs | undefined>(dayjs('2026-06-13T08:30'));
    readonly date = form(this.model);
}

describe('TumUiDatePickerComponent', () => {
    let component: TumUiDatePickerComponent;
    let fixture: ComponentFixture<TumUiDatePickerComponent>;

    beforeEach(async () => {
        vi.useFakeTimers({ toFake: ['Date'] });
        vi.setSystemTime(new Date('2026-07-15T12:00:00'));
        await TestBed.configureTestingModule({
            imports: [TumUiDatePickerComponent, FontAwesomeTestingModule],
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
        return fixture.debugElement.query(By.css('input[type="text"]')).nativeElement;
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
        expect(input().value).toBe('13.06.2026 08:30xx');
    });

    it('clears the value and the displayed text', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        const clear = fixture.debugElement.query(By.css('button[aria-label="Clear date"]')).nativeElement as HTMLButtonElement;
        clear.focus();
        clear.click();
        fixture.detectChanges();
        expect(component.value()).toBeUndefined();
        expect(input().value).toBe('');
        expect(document.activeElement).toBe(input());
    });

    it('re-validates when the value changes externally after invalid input (no stuck error border)', () => {
        input().value = 'garbage';
        input().dispatchEvent(new Event('input'));
        expect(component.isValid()).toBe(false);
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        expect(component.isValid()).toBe(true);
        expect(input().value).toBe('13.06.2026 08:30');
    });

    it('does not wipe in-progress invalid text when the value is re-supplied as an equal-but-fresh instance', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        input().value = '13.06.2026 08:30xx';
        input().dispatchEvent(new Event('input'));
        expect(component.isValid()).toBe(false);
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        expect(component.isValid()).toBe(false);
        expect(input().value).toBe('13.06.2026 08:30xx');
    });

    it('exposes an external error to assistive technology and is invalid', () => {
        fixture.componentRef.setInput('error', true);
        fixture.detectChanges();
        expect(input().getAttribute('aria-invalid')).toBe('true');
        expect(component.isValid()).toBe(false);
    });

    it('reports valid parsed input via hasValidInput() even while an external [error] is set', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.componentRef.setInput('error', true);
        fixture.detectChanges();
        expect(component.isValid()).toBe(false);
        expect(component.hasValidInput()).toBe(true);
        input().value = 'not a date';
        input().dispatchEvent(new Event('input'));
        expect(component.hasValidInput()).toBe(false);
    });

    it('opens the calendar overlay on trigger click and closes via Done', () => {
        const trigger = fixture.debugElement.query(By.css('button[aria-haspopup="dialog"]')).nativeElement as HTMLButtonElement;
        trigger.focus();
        trigger.click();
        fixture.detectChanges();
        const dialog = document.querySelector('[role="dialog"]');
        expect(dialog).not.toBeNull();
        expect(dialog?.getAttribute('aria-modal')).toBe('true');
        expect(dialog?.getAttribute('aria-label')).toBe('Choose date and time');
        expect(input().getAttribute('role')).toBe('combobox');
        expect(input().getAttribute('aria-expanded')).toBe('true');
        expect(input().getAttribute('aria-controls')).toBe(dialog?.id);
        (document.querySelector('[role="dialog"] tum-ui-button button') as HTMLElement).click();
        fixture.detectChanges();
        expect(document.querySelector('tum-ui-calendar')).toBeNull();
        expect(document.activeElement).toBe(trigger);
    });

    it('keeps the calendar icon out of the tab order because the input opens the same dialog', () => {
        const trigger = fixture.debugElement.query(By.css('button[aria-haspopup="dialog"]')).nativeElement as HTMLButtonElement;
        expect(trigger.tabIndex).toBe(-1);
    });

    it('opens the calendar from the input with ArrowDown', () => {
        input().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
        fixture.detectChanges();
        expect(document.querySelector('[role="dialog"]')).not.toBeNull();
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
        fixture.debugElement.query(By.css('button[aria-haspopup="dialog"]')).nativeElement.click();
        fixture.detectChanges();
    }

    function timeField(label: string): HTMLInputElement {
        return document.querySelector(`[role="dialog"] input[aria-label="${label}"]`) as HTMLInputElement;
    }

    function timeButton(label: string): HTMLButtonElement {
        return document.querySelector(`[role="dialog"] button[aria-label="${label}"]`) as HTMLButtonElement;
    }

    it('updates the time-of-day by typing into the hour and minute fields', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        openPanel();
        const hour = timeField('Hour');
        const minute = timeField('Minute');
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
        const hour = timeField('Hour');
        hour.value = '9';
        hour.dispatchEvent(new Event('change'));
        expect(component.value()?.format('HH:mm')).toBe('09:30');
        expect(hour.value).toBe('09');
    });

    it('rejects an out-of-range typed hour and reverts the field', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        openPanel();
        const hour = timeField('Hour');
        hour.value = '25';
        hour.dispatchEvent(new Event('change'));
        expect(component.value()?.format('HH:mm')).toBe('08:30');
        expect(hour.value).toBe('08');
    });

    it('increments and wraps the hour via the spinner buttons (23 -> 00, no day change)', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T23:30'));
        fixture.detectChanges();
        openPanel();
        timeButton('Increment hour').click();
        expect(component.value()?.format('DD.MM.YYYY HH:mm')).toBe('13.06.2026 00:30');
    });

    it('decrements and wraps the minute via the spinner buttons (00 -> 59, no hour change)', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:00'));
        fixture.detectChanges();
        openPanel();
        timeButton('Decrement minute').click();
        expect(component.value()?.format('HH:mm')).toBe('08:59');
    });

    it('nudges the hour with ArrowUp / ArrowDown for keyboard users', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        openPanel();
        const hour = timeField('Hour');
        hour.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        expect(component.value()?.format('HH:mm')).toBe('09:30');
        hour.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        expect(component.value()?.format('HH:mm')).toBe('08:30');
    });

    it('steps ArrowUp from the uncommitted typed field value, not the committed value', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        openPanel();
        const hour = timeField('Hour');
        hour.value = '10';
        hour.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        expect(component.value()?.format('HH:mm')).toBe('11:30');
        expect(hour.value).toBe('11');
    });

    it('selects a day from the calendar overlay', () => {
        fixture.debugElement.query(By.css('button[aria-haspopup="dialog"]')).nativeElement.click();
        fixture.detectChanges();
        (document.querySelector('td[role="gridcell"] button') as HTMLElement).click();
        expect(component.value()).toBeDefined();
        expect(component.value()?.format('HH:mm')).toBe('00:00');
    });

    it('does not open when disabled', () => {
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        fixture.debugElement.query(By.css('button[aria-haspopup="dialog"]')).nativeElement.click();
        fixture.detectChanges();
        expect(document.querySelector('tum-ui-calendar')).toBeNull();
    });

    it('closes the overlay when the control is disabled while the panel is open', () => {
        fixture.debugElement.query(By.css('button[aria-haspopup="dialog"]')).nativeElement.click();
        fixture.detectChanges();
        expect(document.querySelector('tum-ui-calendar')).not.toBeNull();
        fixture.componentRef.setInput('disabled', true);
        fixture.detectChanges();
        expect(document.querySelector('tum-ui-calendar')).toBeNull();
    });

    it('bases the date on today, not the 1st of the month, when the time is set on an empty picker', () => {
        expect(component.value()).toBeUndefined();
        openPanel();
        const hour = timeField('Hour');
        const minute = timeField('Minute');
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
        expect(fixture.debugElement.query(By.css('[role="img"][tabindex="0"]'))).not.toBeNull();
        fixture.componentRef.setInput('shouldDisplayTimeZoneWarning', false);
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[role="img"][tabindex="0"]'))).toBeNull();
    });

    it('exposes the timezone warning to keyboard and screen-reader users', () => {
        const warning = fixture.debugElement.query(By.css('[role="img"][tabindex="0"]')).nativeElement as HTMLElement;
        expect(warning.getAttribute('tabindex')).toBe('0');
        expect(warning.getAttribute('role')).toBe('img');
        expect(warning.getAttribute('aria-label')).toBeTruthy();
    });

    describe('two-way [(value)] binding', () => {
        function hostInput(host: ComponentFixture<TwoWayHostComponent>): HTMLInputElement {
            return host.debugElement.query(By.css('input[type="text"]')).nativeElement;
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
            inp.value = '13.06.2026 08:3';
            inp.dispatchEvent(new Event('input'));
            host.detectChanges();
            expect(inp.value).toBe('13.06.2026 08:3');
            expect(host.componentInstance.value?.format('DD.MM.YYYY HH:mm')).toBe('13.06.2026 08:30');
        });
    });

    it('integrates value, touch, and focus with Signal Forms', () => {
        const host = TestBed.createComponent(SignalFormHostComponent);
        host.detectChanges();
        const picker = host.debugElement.query(By.directive(TumUiDatePickerComponent));
        const field = picker.injector.get(FormField);
        const dateInput = picker.query(By.css('input[type="text"]')).nativeElement as HTMLInputElement;

        expect(dateInput.value).toBe('13.06.2026 08:30');
        expect(host.componentInstance.date().touched()).toBe(false);

        field.focus();
        expect(document.activeElement).toBe(dateInput);
        dateInput.dispatchEvent(new FocusEvent('blur'));
        host.detectChanges();
        expect(host.componentInstance.date().touched()).toBe(true);

        dateInput.value = '14.06.2026 09:45';
        dateInput.dispatchEvent(new Event('input'));
        host.detectChanges();
        expect(host.componentInstance.model()?.format('DD.MM.YYYY HH:mm')).toBe('14.06.2026 09:45');
    });
});
