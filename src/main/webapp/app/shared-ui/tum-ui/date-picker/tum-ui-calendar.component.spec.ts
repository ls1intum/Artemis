import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import dayjs from 'dayjs/esm';
import { TumUiCalendarComponent } from 'app/shared-ui/tum-ui/date-picker/tum-ui-calendar.component';

describe('TumUiCalendarComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TumUiCalendarComponent;
    let fixture: ComponentFixture<TumUiCalendarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumUiCalendarComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(TumUiCalendarComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('activeMonth', dayjs('2026-06-01'));
        fixture.detectChanges();
    });

    afterEach(() => vi.restoreAllMocks());

    function dayButtons(): HTMLButtonElement[] {
        return fixture.debugElement.queryAll(By.css('td[role="gridcell"] button')).map((d) => d.nativeElement);
    }

    it('renders 7 weekday headers and 42 day cells', () => {
        expect(fixture.debugElement.queryAll(By.css('thead th')).length).toBe(7);
        expect(dayButtons().length).toBe(42);
    });

    it('emits daySelected on click', () => {
        const spy = vi.spyOn(component.daySelected, 'emit');
        dayButtons()[10].click();
        expect(spy).toHaveBeenCalledOnce();
        expect(dayjs.isDayjs(spy.mock.calls[0][0])).toBe(true);
    });

    it('emits monthChange from the next button', () => {
        const spy = vi.spyOn(component.monthChange, 'emit');
        fixture.debugElement.query(By.css('[data-testid="calendar-next"]')).nativeElement.click();
        expect(spy).toHaveBeenCalledOnce();
        expect((spy.mock.calls[0][0] as dayjs.Dayjs).month()).toBe(6); // July
    });

    it('marks the selected day with aria-selected', () => {
        fixture.componentRef.setInput('selected', dayjs('2026-06-15'));
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('td[aria-selected="true"]'))).toBeTruthy();
    });

    it('moves roving focus with ArrowRight and selects with Enter', () => {
        const spy = vi.spyOn(component.daySelected, 'emit');
        const buttons = dayButtons();
        buttons[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
        fixture.detectChanges();
        buttons[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
        expect(spy).toHaveBeenCalledOnce();
    });

    it('navigates the grid with ArrowDown/Up/Left/Home/End', () => {
        const buttons = dayButtons();
        buttons[10].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
        expect(document.activeElement).toBe(buttons[17]);
        buttons[17].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
        expect(document.activeElement).toBe(buttons[16]);
        buttons[16].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowUp' }));
        expect(document.activeElement).toBe(buttons[9]);
        buttons[9].dispatchEvent(new KeyboardEvent('keydown', { key: 'Home' }));
        expect(document.activeElement).toBe(buttons[7]);
        buttons[7].dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }));
        expect(document.activeElement).toBe(buttons[13]);
    });

    it('changes the month via PageUp/PageDown and the previous button', () => {
        const spy = vi.spyOn(component.monthChange, 'emit');
        const buttons = dayButtons();
        buttons[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'PageDown' }));
        buttons[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'PageUp' }));
        fixture.debugElement.query(By.css('[data-testid="calendar-previous"]')).nativeElement.click();
        expect(spy).toHaveBeenCalledTimes(3);
    });

    it('mutes days from adjacent months', () => {
        expect(dayButtons().some((button) => button.classList.contains('text-surface-400'))).toBe(true);
    });

    it('rings today when the active month contains it', () => {
        fixture.componentRef.setInput('activeMonth', dayjs().startOf('month'));
        fixture.detectChanges();
        expect(dayButtons().some((button) => button.classList.contains('ring-primary'))).toBe(true);
    });
});
