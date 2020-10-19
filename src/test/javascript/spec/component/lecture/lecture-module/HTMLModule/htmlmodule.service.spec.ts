import { TestBed } from '@angular/core/testing';

import { HTMLModuleService } from 'app/lecture/lecture-module/HTMLModule/htmlmodule.service';

describe('HTMLModuleService', () => {
    let service: HTMLModuleService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(HTMLModuleService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
