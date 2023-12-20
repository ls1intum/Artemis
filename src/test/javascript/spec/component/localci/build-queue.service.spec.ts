import { TestBed } from '@angular/core/testing';

import { BuildQueueService } from 'app/localci/build-queue/build-queue.service';

describe('BuildQueueService', () => {
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [BuildQueueService],
        }).compileComponents();
    });
});
