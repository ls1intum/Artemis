import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarDesktopMonthComponent } from './calendar-desktop-month.component';

describe('CalendarDesktopMonthComponent', () => {
    let component: CalendarDesktopMonthComponent;
    let fixture: ComponentFixture<CalendarDesktopMonthComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarDesktopMonthComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarDesktopMonthComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
