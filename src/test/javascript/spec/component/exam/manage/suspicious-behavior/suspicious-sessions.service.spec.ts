import { TestBed } from '@angular/core/testing';

import { SuspiciousSessionsService } from 'app/exam/manage/suspicious-behavior/suspicious-sessions/suspicious-sessions.service';

describe('SuspiciousSessionsService', () => {
    let service: SuspiciousSessionsService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(SuspiciousSessionsService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
