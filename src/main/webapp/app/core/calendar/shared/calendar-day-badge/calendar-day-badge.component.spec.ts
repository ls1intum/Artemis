import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { CalendarDayBadgeComponent } from './calendar-day-badge.component';

describe('CalendarDayBadgeComponent', () => {
    let component: CalendarDayBadgeComponent;
    let fixture: ComponentFixture<CalendarDayBadgeComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CalendarDayBadgeComponent], // Since it's standalone
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarDayBadgeComponent);
        component = fixture.componentInstance;
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should render the correct day number', () => {
        fixture.componentRef.setInput('date', dayjs('2025-07-04'));
        fixture.detectChanges();

        const badge = fixture.debugElement.query(By.css('.day-badge')).nativeElement;
        expect(badge.textContent.trim()).toBe('4');
    });

    it('should apply the "selected-day" class when the day is selected', () => {
        fixture.componentRef.setInput('date', dayjs());
        fixture.componentRef.setInput('isSelectedDay', true);
        fixture.detectChanges();

        const badge = fixture.debugElement.query(By.css('.day-badge')).nativeElement;
        expect(badge.classList).toContain('selected-day');
    });

    it('should apply the "today-normal" class when day is today', () => {
        fixture.componentRef.setInput('date', dayjs());
        fixture.detectChanges();

        const badge = fixture.debugElement.query(By.css('.day-badge')).nativeElement;
        expect(badge.classList).toContain('today-normal');
    });

    it('should apply the "today-minimal" class when day is today', () => {
        fixture.componentRef.setInput('date', dayjs());
        fixture.componentRef.setInput('minimalTodayIndication', true);
        fixture.detectChanges();

        const badge = fixture.debugElement.query(By.css('.day-badge')).nativeElement;
        expect(badge.classList).toContain('today-minimal');
    });

    it('should apply the "not-today" class when day is not today', () => {
        fixture.componentRef.setInput('date', dayjs().add(1, 'day'));
        fixture.detectChanges();

        const badge = fixture.debugElement.query(By.css('.day-badge')).nativeElement;
        expect(badge.classList).toContain('not-today');
    });
});
