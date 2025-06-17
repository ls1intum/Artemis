import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AllDayEventSectionComponent } from './all-day-event-section.component';

describe('AllDayEventSectionComponent', () => {
    let component: AllDayEventSectionComponent;
    let fixture: ComponentFixture<AllDayEventSectionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AllDayEventSectionComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(AllDayEventSectionComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
