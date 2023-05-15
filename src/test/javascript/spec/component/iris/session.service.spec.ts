import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { irisExercise, mockConversation } from '../../helpers/sample/iris-sample-data';
import { IrisConversation } from 'app/entities/iris/iris.model';
import { SessionService } from 'app/iris/session.service';

describe('Session Service', () => {
    let service: SessionService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(SessionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should create a session', fakeAsync(() => {
            const returnedFromService = { ...mockConversation };
            const expected = { ...returnedFromService };
            service
                .createSessionForProgrammingExercise(1, new IrisConversation())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return current session', fakeAsync(() => {
            const returnedFromService = mockConversation;
            const expected = returnedFromService;
            service
                .getCurrentSession(irisExercise.id!)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).toEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        afterEach(() => {
            httpMock.verify();
        });
    });
});
