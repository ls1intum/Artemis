import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { TutorialGroupSessionDTO, TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { generateExampleTutorialGroupSession } from '../helpers/tutorialGroupSessionExampleModels';

describe('TutorialGroupSessionService', () => {
    let service: TutorialGroupSessionService;
    let httpMock: HttpTestingController;
    let elemDefault: TutorialGroupSession;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(TutorialGroupSessionService);
        httpMock = TestBed.inject(HttpTestingController);

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
        service
            .delete(1, 1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        tick();
    }));
});
