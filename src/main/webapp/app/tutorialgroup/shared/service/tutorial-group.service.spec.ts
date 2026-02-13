import { TestBed } from '@angular/core/testing';

import { TutorialGroupService } from './tutorial-group.service';

describe('TutorialGroup', () => {
    let service: TutorialGroupService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(TutorialGroupService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
