import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarEventDetailPopoverComponent } from './calendar-event-detail-popover.component';

describe('CalendarEventDetailPopoverComponent', () => {
    let component: CalendarEventDetailPopoverComponent;
    let fixture: ComponentFixture<CalendarEventDetailPopoverComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarEventDetailPopoverComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarEventDetailPopoverComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
