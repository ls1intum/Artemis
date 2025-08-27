import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CalendarSubscriptionPopoverComponent } from './calendar-subscription-popover.component';

describe('CalendarSubscriptionPopover', () => {
    let component: CalendarSubscriptionPopoverComponent;
    let fixture: ComponentFixture<CalendarSubscriptionPopoverComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CalendarSubscriptionPopoverComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(CalendarSubscriptionPopoverComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
