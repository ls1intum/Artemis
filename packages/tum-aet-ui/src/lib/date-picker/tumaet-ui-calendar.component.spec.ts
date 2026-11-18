import { ComponentFixture, TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { By } from '@angular/platform-browser';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import dayjs from 'dayjs/esm';
import { TumAetUiCalendarComponent } from './tumaet-ui-calendar.component';

describe('TumAetUiCalendarComponent', () => {
    let component: TumAetUiCalendarComponent;
    let fixture: ComponentFixture<TumAetUiCalendarComponent>;

    beforeEach(async () => {
        vi.useFakeTimers({ toFake: ['Date'] });
        vi.setSystemTime(new Date('2026-07-15T12:00:00'));
        await TestBed.configureTestingModule({ imports: [TumAetUiCalendarComponent, FontAwesomeTestingModule] }).compileComponents();
        fixture = TestBed.createComponent(TumAetUiCalendarComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('activeMonth', dayjs('2026-06-01'));
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    function dayButtons(): HTMLButtonElement[] {
        return fixture.debugElement.queryAll(By.css('td[role="gridcell"] button')).map((d) => d.nativeElement);
    }

    function dayButton(date: string): HTMLButtonElement {
        const label = new Intl.DateTimeFormat(undefined, { dateStyle: 'full' }).format(dayjs(date).toDate());
        return dayButtons().find((button) => button.getAttribute('aria-label') === label)!;
    }

    function pressOnFocused(key: string): void {
        (document.activeElement as HTMLElement).dispatchEvent(new KeyboardEvent('keydown', { key }));
    }

    it('renders 7 weekday headers and 42 day cells', () => {
        const headings = fixture.debugElement.queryAll(By.css('thead th'));
        expect(headings).toHaveLength(7);
        expect(headings.every((heading) => heading.attributes['aria-label'])).toBe(true);
        expect(fixture.debugElement.query(By.css('[role="grid"]')).attributes['aria-label']).toContain('2026');
        expect(dayButtons().length).toBe(42);
    });

    it('gives every day cell its ISO date, independent of the locale, so a test can find it', () => {
        const dates = dayButtons().map((button) => button.getAttribute('data-date'));

        expect(dates).toHaveLength(42);
        expect(dates).toContain('2026-06-15');
        expect(new Set(dates).size).toBe(42);
    });

    it('emits daySelected on click', () => {
        const spy = vi.spyOn(component.daySelected, 'emit');
        dayButtons()[10].click();
        expect(spy).toHaveBeenCalledOnce();
        expect(dayjs.isDayjs(spy.mock.calls[0][0])).toBe(true);
    });

    describe('min and max', () => {
        const dayButtonOf = (iso: string) => dayButtons().find((button) => button.getAttribute('data-date') === iso)!;

        beforeEach(() => {
            fixture.componentRef.setInput('min', dayjs('2026-06-10T15:30'));
            fixture.componentRef.setInput('max', dayjs('2026-06-20T08:00'));
            fixture.detectChanges();
        });

        it('disables the days before min and after max, and only those, whatever the time of day of the limits', () => {
            expect(dayButtonOf('2026-06-09').getAttribute('aria-disabled')).toBe('true');
            expect(dayButtonOf('2026-06-21').getAttribute('aria-disabled')).toBe('true');
            expect(dayButtonOf('2026-06-10').getAttribute('aria-disabled')).toBeNull();
            expect(dayButtonOf('2026-06-15').getAttribute('aria-disabled')).toBeNull();
            expect(dayButtonOf('2026-06-20').getAttribute('aria-disabled')).toBeNull();
        });

        it('selects an allowed day and ignores a click on a disabled one', () => {
            const spy = vi.spyOn(component.daySelected, 'emit');

            dayButtonOf('2026-06-09').click();
            dayButtonOf('2026-06-21').click();
            expect(spy).not.toHaveBeenCalled();

            dayButtonOf('2026-06-10').click();
            expect(spy).toHaveBeenCalledOnce();
            expect((spy.mock.calls[0][0] as dayjs.Dayjs).format('YYYY-MM-DD')).toBe('2026-06-10');
        });

        it('ignores Enter on a disabled day but still lets the focus move onto it', () => {
            const spy = vi.spyOn(component.daySelected, 'emit');
            const disabled = dayButtonOf('2026-06-09');

            disabled.focus();
            disabled.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));

            expect(spy).not.toHaveBeenCalled();
            expect(document.activeElement).toBe(disabled);
        });

        it('leaves every day enabled when no limit is set', () => {
            fixture.componentRef.setInput('min', undefined);
            fixture.componentRef.setInput('max', undefined);
            fixture.detectChanges();

            expect(dayButtons().filter((button) => button.getAttribute('aria-disabled') === 'true')).toHaveLength(0);
        });
    });

    it('emits monthChange from the next button', () => {
        const spy = vi.spyOn(component.monthChange, 'emit');
        fixture.debugElement.query(By.css('button[aria-label^="Next month:"]')).nativeElement.click();
        expect(spy).toHaveBeenCalledOnce();
        expect((spy.mock.calls[0][0] as dayjs.Dayjs).month()).toBe(6);
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

    it('continues into the adjacent month at the grid boundary', () => {
        const monthChange = vi.spyOn(component.monthChange, 'emit');
        dayButton('2026-06-01').dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft' }));
        expect(monthChange.mock.calls[0][0].isSame(dayjs('2026-05-01'), 'month')).toBe(true);

        fixture.componentRef.setInput('activeMonth', dayjs('2026-05-01'));
        fixture.detectChanges();
        expect(document.activeElement).toBe(dayButton('2026-05-31'));
    });

    it('changes the month via PageUp/PageDown and the previous button', () => {
        const spy = vi.spyOn(component.monthChange, 'emit');
        const buttons = dayButtons();
        buttons[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'PageDown' }));
        buttons[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'PageUp' }));
        fixture.debugElement.query(By.css('button[aria-label^="Previous month:"]')).nativeElement.click();
        expect(spy).toHaveBeenCalledTimes(3);
    });

    it('restores roving focus to the grid after a keyboard month change (PageDown)', () => {
        const buttons = dayButtons();
        buttons[10].focus();
        buttons[10].dispatchEvent(new KeyboardEvent('keydown', { key: 'PageDown' }));
        fixture.componentRef.setInput('activeMonth', dayjs('2026-07-01'));
        fixture.detectChanges();
        expect(document.activeElement?.tagName).toBe('BUTTON');
        expect(document.activeElement).not.toBe(document.body);
    });

    it('preserves the focused day-of-month across PageDown (arrow to June 11 → July 11, not July 1)', () => {
        dayButtons()[0].focus();
        pressOnFocused('ArrowDown');
        pressOnFocused('ArrowRight');
        pressOnFocused('ArrowRight');
        pressOnFocused('ArrowRight');
        expect(document.activeElement).toBe(dayButton('2026-06-11'));
        pressOnFocused('PageDown');
        fixture.componentRef.setInput('activeMonth', dayjs('2026-07-01'));
        fixture.detectChanges();
        expect(document.activeElement).toBe(dayButton('2026-07-11'));
    });

    it('preserves the focused day-of-month across PageUp (June 11 → May 11)', () => {
        fixture.componentRef.setInput('selected', dayjs('2026-06-11'));
        fixture.detectChanges();
        dayButton('2026-06-11').dispatchEvent(new KeyboardEvent('keydown', { key: 'PageUp' }));
        fixture.componentRef.setInput('activeMonth', dayjs('2026-05-01'));
        fixture.detectChanges();
        expect(document.activeElement).toBe(dayButton('2026-05-11'));
    });

    it('clamps the preserved day into a shorter month on PageDown (Jan 31 → Feb 28)', () => {
        fixture.componentRef.setInput('activeMonth', dayjs('2026-01-01'));
        fixture.componentRef.setInput('selected', dayjs('2026-01-31'));
        fixture.detectChanges();
        dayButton('2026-01-31').dispatchEvent(new KeyboardEvent('keydown', { key: 'PageDown' }));
        fixture.componentRef.setInput('activeMonth', dayjs('2026-02-01'));
        fixture.detectChanges();
        expect(document.activeElement).toBe(dayButton('2026-02-28'));
    });
});
