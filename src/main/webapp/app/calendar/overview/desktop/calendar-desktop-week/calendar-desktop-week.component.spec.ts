import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarDesktopWeekComponent } from './calendar-desktop-week.component';

describe('CalendarDesktopWeekComponent', () => {
    let component: CalendarDesktopWeekComponent;
    let fixture: ComponentFixture<CalendarDesktopWeekComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarDesktopWeekComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarDesktopWeekComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
