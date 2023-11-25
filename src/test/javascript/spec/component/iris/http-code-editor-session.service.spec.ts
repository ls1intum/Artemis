import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { irisExercise, mockConversation } from '../../helpers/sample/iris-sample-data';
import { IrisHttpCodeEditorSessionService } from 'app/iris/http-code-editor-session.service';

describe('Iris Http Code Editor Session Service', () => {
    let service: IrisHttpCodeEditorSessionService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [IrisHttpCodeEditorSessionService],
        });
        service = TestBed.inject(IrisHttpCodeEditorSessionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    describe('Service methods', () => {
        it('should create a session', fakeAsync(() => {
            const returnedFromService = { id: '1' };
            service
                .createSession(1)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toEqual(returnedFromService));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService, { status: 201, statusText: 'Created' });
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
