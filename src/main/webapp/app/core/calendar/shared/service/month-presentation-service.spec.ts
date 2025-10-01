import { TestBed } from '@angular/core/testing';

import { CalendarMonthPresentationService } from './calendar-month-presentation.service';

describe('CalendarMonthPresentationService', () => {
    let service: CalendarMonthPresentationService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(CalendarMonthPresentationService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    // TODO: implement
});
