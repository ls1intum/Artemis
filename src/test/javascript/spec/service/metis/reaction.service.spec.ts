import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { Reaction } from 'app/entities/metis/reaction.model';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { User } from 'app/core/user/user.model';

const expect = chai.expect;

describe('Reaction Service', () => {
    let injector: TestBed;
    let service: ReactionService;
    let httpMock: HttpTestingController;
    let elemDefault: Reaction;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(ReactionService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new Reaction();
        elemDefault.creationDate = undefined;
        elemDefault.emojiId = 'smile';
        elemDefault.user = { id: 1, name: 'username', login: 'login' } as User;
    });

    describe('Service methods', () => {
        it('should create a Reaction', fakeAsync(() => {
            const returnedFromService = { ...elemDefault, id: 0 };
            const expected = { ...returnedFromService };
            service
                .create(1, new Reaction())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should delete a Reaction', fakeAsync(() => {
            service.delete(1, elemDefault).subscribe((resp) => expect(resp.ok).to.be.true);
            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
    });
});
