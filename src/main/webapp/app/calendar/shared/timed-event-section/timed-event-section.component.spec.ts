import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TimedEventSectionComponent } from './timed-event-section.component';

describe('TimedEventSectionComponent', () => {
    let component: TimedEventSectionComponent;
    let fixture: ComponentFixture<TimedEventSectionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TimedEventSectionComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TimedEventSectionComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
