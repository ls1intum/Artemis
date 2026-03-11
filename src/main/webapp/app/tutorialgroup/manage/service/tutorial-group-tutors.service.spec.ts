import { TestBed } from '@angular/core/testing';

import { TutorialGroupTutorsService } from './tutorial-group-tutors.service';

describe('TutorialGroupTutor', () => {
    let service: TutorialGroupTutorsService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(TutorialGroupTutorsService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
