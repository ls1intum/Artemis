import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { ReactionService } from 'app/communication/service/reaction.service';
import { metisReactionToCreate, metisReactionUser2 } from 'test/helpers/sample/metis-sample-data';
import { provideHttpClient } from '@angular/common/http';

describe('Reaction Service', () => {
    setupTestBed({ zoneless: true });

    let service: ReactionService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        vi.useFakeTimers();
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(ReactionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        vi.useRealTimers();
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('Service methods', () => {
        it('should create a Reaction', () => {
            const returnedFromService = { ...metisReactionToCreate };
            const expected = { ...returnedFromService };
            service
                .create(1, new Reaction())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            vi.advanceTimersByTime(0);
        });

        it('should delete a Reaction', () => {
            service.delete(1, metisReactionUser2).subscribe((resp) => expect(resp.ok).toBe(true));
            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            vi.advanceTimersByTime(0);
        });
    });
});
