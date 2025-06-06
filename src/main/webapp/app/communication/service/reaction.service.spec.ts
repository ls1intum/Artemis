import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { ReactionService } from 'app/communication/service/reaction.service';
import { metisReactionToCreate, metisReactionUser2 } from 'test/helpers/sample/metis-sample-data';
import { provideHttpClient } from '@angular/common/http';

describe('Reaction Service', () => {
    let service: ReactionService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(ReactionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should create a Reaction', fakeAsync(() => {
            const returnedFromService = { ...metisReactionToCreate };
            const expected = { ...returnedFromService };
            service
                .create(1, new Reaction())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should delete a Reaction', fakeAsync(() => {
            service.delete(1, metisReactionUser2).subscribe((resp) => expect(resp.ok).toBeTrue());
            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
