import { TestBed } from '@angular/core/testing';

import { PlannedExerciseService } from './planned-exercise.service';

describe('PlannedEvent', () => {
    let service: PlannedExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(PlannedExerciseService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
