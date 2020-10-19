import { TestBed } from '@angular/core/testing';

import { LectureModuleService } from 'app/lecture/lecture-module/lecture-module.service';

describe('LectureModuleService', () => {
    let service: LectureModuleService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(LectureModuleService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
