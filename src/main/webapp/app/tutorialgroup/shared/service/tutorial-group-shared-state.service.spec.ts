import { TestBed } from '@angular/core/testing';

import { TutorialGroupSharedStateService } from './tutorial-group-shared-state.service';

describe('TutorialGroupManagementSharedState', () => {
    let service: TutorialGroupSharedStateService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(TutorialGroupSharedStateService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
