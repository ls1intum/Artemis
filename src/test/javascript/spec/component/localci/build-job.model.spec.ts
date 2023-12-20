import { TestBed } from '@angular/core/testing';

import { BuildJob } from 'app/entities/build-job.model';

describe('BuildJob', () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildJob],
        }).compileComponents();
    });
});
