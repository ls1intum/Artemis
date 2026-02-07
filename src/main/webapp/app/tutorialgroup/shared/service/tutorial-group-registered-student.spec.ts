import { TestBed } from '@angular/core/testing';

import { TutorialGroupRegisteredStudentsService } from './tutorial-group-registered-students.service';

describe('TutorialGroupRegisteredStudent', () => {
    let service: TutorialGroupRegisteredStudentsService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(TutorialGroupRegisteredStudentsService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
