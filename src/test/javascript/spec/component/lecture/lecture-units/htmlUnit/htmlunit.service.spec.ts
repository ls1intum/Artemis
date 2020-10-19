import { TestBed } from '@angular/core/testing';

import { HTMLUnitService } from 'app/lecture/lecture-unit/htmlUnit/htmlunit.service';

describe('HTMLUnitService', () => {
    let service: HTMLUnitService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(HTMLUnitService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
