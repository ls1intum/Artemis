import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { generateExampleTutorialGroupSession } from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import { provideHttpClient } from '@angular/common/http';

describe('TutorialGroupSessionService', () => {
    let service: TutorialGroupSessionService;
    let httpMock: HttpTestingController;
    let elemDefault: TutorialGroupSession;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(TutorialGroupSessionService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateExampleTutorialGroupSession({});
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('getOneOfTutorialGroup', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .getOneOfTutorialGroup(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('create', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
        const expected = Object.assign({}, returnedFromService);
        service
            .create(1, 1, new TutorialGroupSessionDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { location: 'Test' });
        const expected = Object.assign({}, returnedFromService);

        service
            .update(1, 1, 1, new TutorialGroupSessionDTO())
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('updateAttendanceCount', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { attendanceCount: 5 });
        const expected = Object.assign({}, returnedFromService);

        service
            .updateAttendanceCount(1, 1, 1, 5)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PATCH' });
        req.flush(returnedFromService);
        tick();
    }));

    it('cancel', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        const expected = Object.assign({}, returnedFromService);
        service
            .cancel(1, 1, 1, 'test')
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('activate', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        const expected = Object.assign({}, returnedFromService);
        service
            .activate(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('delete', fakeAsync(() => {
        service
            .delete(1, 1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        tick();
    }));
});
