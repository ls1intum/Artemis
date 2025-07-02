import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarLegendComponent } from './calendar-legend.component';

describe('CalendarLegendComponent', () => {
    let component: CalendarLegendComponent;
    let fixture: ComponentFixture<CalendarLegendComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarLegendComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarLegendComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
