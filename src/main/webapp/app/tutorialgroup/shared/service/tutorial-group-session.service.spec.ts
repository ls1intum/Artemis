import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { TutorialGroupSessionDTO, TutorialGroupSessionRequestDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { generateExampleTutorialGroupSessionDTO } from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import { provideHttpClient } from '@angular/common/http';

describe('TutorialGroupSessionService', () => {
    setupTestBed({ zoneless: true });

    let service: TutorialGroupSessionService;
    let httpMock: HttpTestingController;
    let elemDefault: TutorialGroupSessionDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(TutorialGroupSessionService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateExampleTutorialGroupSessionDTO({});
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('getOneOfTutorialGroup', () => {
        const returnedFromService = { ...elemDefault };
        let result: any;
        service
            .getOneOfTutorialGroup(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: elemDefault });
    });

    it('create', () => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        let result: any;
        service
            .create(1, 1, new TutorialGroupSessionRequestDTO())
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    it('update', () => {
        const returnedFromService = { ...elemDefault, location: 'Test' };
        const expected = { ...returnedFromService };
        let result: any;

        service
            .update(1, 1, 1, new TutorialGroupSessionRequestDTO())
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    it('updateAttendanceCount', () => {
        const returnedFromService = { ...elemDefault, attendanceCount: 5 };
        const expected = { ...returnedFromService };
        let result: any;

        service
            .updateAttendanceCount(1, 1, 1, 5)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'PATCH' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    it('cancel', () => {
        const returnedFromService = { ...elemDefault };
        const expected = { ...returnedFromService };
        let result: any;
        service
            .cancel(1, 1, 1, 'test')
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    it('activate', () => {
        const returnedFromService = { ...elemDefault };
        const expected = { ...returnedFromService };
        let result: any;
        service
            .activate(1, 1, 1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    it('delete', () => {
        let result: any;
        service
            .delete(1, 1, 1)
            .pipe(take(1))
            .subscribe((res) => (result = res));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        expect(result.body).toEqual({});
    });
});
