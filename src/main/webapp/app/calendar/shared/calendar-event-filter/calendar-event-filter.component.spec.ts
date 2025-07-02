import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarEventFilterComponent } from './calendar-event-filter.component';

describe('CalendarEventFilterComponent', () => {
    let component: CalendarEventFilterComponent;
    let fixture: ComponentFixture<CalendarEventFilterComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarEventFilterComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarEventFilterComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
