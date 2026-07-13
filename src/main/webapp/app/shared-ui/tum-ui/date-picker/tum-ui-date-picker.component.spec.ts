import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';
import { TumUiDatePickerComponent } from 'app/shared-ui/tum-ui/date-picker/tum-ui-date-picker.component';

describe('TumUiDatePickerComponent', () => {
    setupTestBed({ zoneless: true });

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

    it('parses typed text into the value and emits valueChange', () => {
        const spy = vi.spyOn(component.valueChange, 'emit');
        input().value = '13.06.2026 09:15';
        input().dispatchEvent(new Event('input'));
        expect(spy).toHaveBeenCalled();
        expect(component.value()?.format('DD.MM.YYYY HH:mm')).toBe('13.06.2026 09:15');
        expect(component.isValid()).toBe(true);
    });

    it('flags invalid typed text without changing the value (keepInvalid)', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        input().value = '13.06.2026 08:30xx';
        input().dispatchEvent(new Event('input'));
        expect(component.isValid()).toBe(false);
        expect(component.value()?.format('DD.MM.YYYY HH:mm')).toBe('13.06.2026 08:30');
    });

    it('clears the value and emits', () => {
        fixture.componentRef.setInput('value', dayjs('2026-06-13T08:30'));
        fixture.detectChanges();
        const spy = vi.spyOn(component.valueChange, 'emit');
        fixture.debugElement.query(By.css('[data-testid="tum-ui-date-picker-clear"]')).nativeElement.click();
        expect(component.value()).toBeUndefined();
        expect(spy).toHaveBeenCalled();
    });

    it('shows the error border and is invalid when [error] is set', () => {
        fixture.componentRef.setInput('error', true);
        fixture.detectChanges();
        expect(input().classList).toContain('border-state-danger');
        expect(component.isValid()).toBe(false);
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
});
