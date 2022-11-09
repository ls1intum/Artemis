import { TestBed } from '@angular/core/testing';

import { ProfileAccessService } from './profile-access.service';

describe('ProfileAccessService', () => {
    let service: ProfileAccessService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(ProfileAccessService);
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });
});
