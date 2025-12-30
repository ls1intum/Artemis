import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { generateExampleTutorialGroupSession } from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import { provideHttpClient } from '@angular/common/http';
import { TutorialGroupSessionApi } from 'app/openapi/api/tutorial-group-session-api';
import { of } from 'rxjs';

describe('TutorialGroupSessionService', () => {
    let service: TutorialGroupSessionService;
    let httpMock: HttpTestingController;
    let tutorialGroupSessionApi: jest.Mocked<TutorialGroupSessionApi>;
    let elemDefault: TutorialGroupSession;

    beforeEach(() => {
        const spySessionApi = {
            deleteSession: jest.fn(),
        };
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TutorialGroupSessionApi, useValue: spySessionApi }],
        });
        service = TestBed.inject(TutorialGroupSessionService);
        httpMock = TestBed.inject(HttpTestingController);
        tutorialGroupSessionApi = TestBed.inject(TutorialGroupSessionApi) as jest.Mocked<TutorialGroupSessionApi>;

        elemDefault = generateExampleTutorialGroupSession({});
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('getOneOfTutorialGroup', fakeAsync(() => {
        const returnedFromService = { ...elemDefault };
        service
            .getOneOfTutorialGroup(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('create', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(1, 1, new TutorialGroupSessionDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, location: 'Test' };
        const expected = { ...returnedFromService };

        service
            .update(1, 1, 1, new TutorialGroupSessionDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('updateAttendanceCount', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, attendanceCount: 5 };
        const expected = { ...returnedFromService };

        service
            .updateAttendanceCount(1, 1, 1, 5)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PATCH' });
        req.flush(returnedFromService);
        tick();
    }));

    it('cancel', fakeAsync(() => {
        const returnedFromService = { ...elemDefault };
        const expected = { ...returnedFromService };
        service
            .cancel(1, 1, 1, 'test')
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('activate', fakeAsync(() => {
        const returnedFromService = { ...elemDefault };
        const expected = { ...returnedFromService };
        service
            .activate(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('delete', fakeAsync(() => {
        tutorialGroupSessionApi.deleteSession.mockReturnValue(of(undefined));
        service
            .delete(1, 1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toBeUndefined());
        expect(tutorialGroupSessionApi.deleteSession).toHaveBeenCalledWith(1, 1, 1);
        tick();
    }));
});
