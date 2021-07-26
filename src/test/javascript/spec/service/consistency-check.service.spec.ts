import { TestBed } from '@angular/core/testing';

import { ConsistencyCheckService } from 'app/shared/consistency-check/consistency-check.service';

describe('ConsistencyCheckService', () => {
    let service: ConsistencyCheckService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(ConsistencyCheckService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
