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
        fixture.componentRef.setInput('day', dayjs('2025-07-04'));
        fixture.detectChanges();

        const badge = fixture.debugElement.query(By.css('.day-badge')).nativeElement;
        expect(badge.textContent.trim()).toBe('4');
    });

    it('should apply the "today" class when the day is today', () => {
        fixture.componentRef.setInput('day', dayjs());
        fixture.detectChanges();

        const badge = fixture.debugElement.query(By.css('.day-badge')).nativeElement;
        expect(badge.classList).toContain('today');
        expect(badge.classList).not.toContain('other');
    });

    it('should apply the "other" class when the day is not today', () => {
        fixture.componentRef.setInput('day', dayjs().add(1, 'day'));
        fixture.detectChanges();

        const badge = fixture.debugElement.query(By.css('.day-badge')).nativeElement;
        expect(badge.classList).toContain('other');
        expect(badge.classList).not.toContain('today');
    });
});
