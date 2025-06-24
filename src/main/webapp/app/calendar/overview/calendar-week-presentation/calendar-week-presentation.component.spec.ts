import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarWeekPresentationComponent } from './calendar-week-presentation.component';

describe('CalendarDesktopWeekComponent', () => {
    let component: CalendarWeekPresentationComponent;
    let fixture: ComponentFixture<CalendarWeekPresentationComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarWeekPresentationComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarWeekPresentationComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
