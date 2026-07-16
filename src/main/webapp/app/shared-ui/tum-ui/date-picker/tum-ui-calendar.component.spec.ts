import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import dayjs from 'dayjs/esm';
import { TumUiCalendarComponent } from 'app/shared-ui/tum-ui/date-picker/tum-ui-calendar.component';

describe('TumUiCalendarComponent', () => {
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

    /** The day cell whose accessible label is the given `DD.MM.YYYY` date. */
    function dayButton(label: string): HTMLButtonElement {
        return dayButtons().find((button) => button.getAttribute('aria-label') === label)!;
    }

    /** Dispatch a keydown on whichever day cell currently holds DOM focus (the roving cursor). */
    function pressOnFocused(key: string): void {
        (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key }));
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

    it('applies exactly one text color per state (selected wins over the base color)', () => {
        fixture.componentRef.setInput('selected', dayjs('2026-06-15'));
        fixture.detectChanges();
        const selected = dayButtons().find((b) => b.textContent?.trim() === '15')!;
        expect(selected.classList).toContain('bg-primary');
        expect(selected.classList).toContain('text-surface-0');
        // the base color must NOT co-exist on the selected cell (would collide in the cascade)
        expect(selected.classList).not.toContain('text-surface-900');
        // an other-month cell is dimmed and not also the base color
        const other = dayButtons().find((b) => b.classList.contains('text-surface-400'))!;
        expect(other.classList).not.toContain('text-surface-900');
    });

    it('restores roving focus to the grid after a keyboard month change (PageDown)', () => {
        const buttons = dayButtons();
        buttons[10].focus();
        buttons[10].dispatchEvent(new KeyboardEvent('keydown', { key: 'PageDown' }));
        // simulate the parent applying the emitted next month
        fixture.componentRef.setInput('activeMonth', dayjs('2026-07-01'));
        fixture.detectChanges();
        expect(document.activeElement?.tagName).toBe('BUTTON');
        expect(document.activeElement).not.toBe(document.body);
    });

    it('preserves the focused day-of-month across PageDown (arrow to June 11 → July 11, not July 1)', () => {
        // Arrow from the default cursor (June 1) to June 11, then page to the next month.
        dayButtons()[0].focus();
        pressOnFocused('ArrowDown'); // June 8
        pressOnFocused('ArrowRight'); // June 9
        pressOnFocused('ArrowRight'); // June 10
        pressOnFocused('ArrowRight'); // June 11
        expect(document.activeElement).toBe(dayButton('11.06.2026'));
        pressOnFocused('PageDown');
        fixture.componentRef.setInput('activeMonth', dayjs('2026-07-01')); // parent applies the emitted month
        fixture.detectChanges();
        // The cursor must land on July 11 — the same day — not snap back to July 1.
        expect(document.activeElement).toBe(dayButton('11.07.2026'));
    });

    it('preserves the focused day-of-month across PageUp (June 11 → May 11)', () => {
        fixture.componentRef.setInput('selected', dayjs('2026-06-11')); // seed the cursor on June 11
        fixture.detectChanges();
        dayButton('11.06.2026').dispatchEvent(new KeyboardEvent('keydown', { key: 'PageUp' }));
        fixture.componentRef.setInput('activeMonth', dayjs('2026-05-01'));
        fixture.detectChanges();
        expect(document.activeElement).toBe(dayButton('11.05.2026'));
    });

    it('clamps the preserved day into a shorter month on PageDown (Jan 31 → Feb 28)', () => {
        fixture.componentRef.setInput('activeMonth', dayjs('2026-01-01'));
        fixture.componentRef.setInput('selected', dayjs('2026-01-31')); // seed the cursor on Jan 31
        fixture.detectChanges();
        dayButton('31.01.2026').dispatchEvent(new KeyboardEvent('keydown', { key: 'PageDown' }));
        fixture.componentRef.setInput('activeMonth', dayjs('2026-02-01'));
        fixture.detectChanges();
        // February 2026 has 28 days, so the cursor clamps to the 28th rather than overflowing.
        expect(document.activeElement).toBe(dayButton('28.02.2026'));
    });
});
