import { TestBed } from '@angular/core/testing';

import { TutorialGroupTutorService } from './tutorial-group-tutor.service';

describe('TutorialGroupTutor', () => {
    let service: TutorialGroupTutorService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(TutorialGroupTutorService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
