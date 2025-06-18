import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarDesktopComponent } from './calendar-desktop.component';

describe('CalendarDesktopComponent', () => {
    let component: CalendarDesktopComponent;
    let fixture: ComponentFixture<CalendarDesktopComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarDesktopComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarDesktopComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
