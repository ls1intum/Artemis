import { TestBed } from '@angular/core/testing';

import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit.service';

describe('LectureUnitService', () => {
    let service: LectureUnitService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(LectureUnitService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
