import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { map, take } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { provideHttpClient } from '@angular/common/http';

describe('TutorialGroupService', () => {
    setupTestBed({ zoneless: true });

    let service: TutorialGroupsService;
    let httpMock: HttpTestingController;
    let elemDefault: TutorialGroup;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(TutorialGroupsService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateExampleTutorialGroup({});
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('getUniqueCampusValues', () => {
        const returnedFromService = ['Test', 'Test2'];
        let result: any;
        service
            .getUniqueCampusValues(1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));
        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: returnedFromService });
    });

    it('getOneOfCourse', () => {
        const returnedFromService = { ...elemDefault };
        let result: any;
        service
            .getOneOfCourse(1, 1)
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
            .create(new TutorialGroup(), 1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    it('update', () => {
        const returnedFromService = { ...elemDefault, title: 'Test' };
        const expected = { ...returnedFromService };
        let result: any;

        service
            .update(1, 1, expected)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    it('getAllOfCourse', () => {
        const returnedFromService = { ...elemDefault, title: 'Test' };
        const expected = { ...returnedFromService };
        let result: any;

        service
            .getAllForCourse(1)
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => (result = body));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([returnedFromService]);
        expect(result).toContainEqual(expected);
    });

    it('deregisterStudent', () => {
        let result: any;
        service
            .deregisterStudent(1, 1, 'login')
            .pipe(take(1))
            .subscribe((res) => (result = res));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        expect(result.body).toEqual({});
    });

    it('registerStudent', () => {
        let result: any;
        service
            .registerStudent(1, 1, 'login')
            .pipe(take(1))
            .subscribe((res) => (result = res));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        expect(result.body).toEqual({});
    });

    it('delete', () => {
        let result: any;
        service
            .delete(1, 1)
            .pipe(take(1))
            .subscribe((res) => (result = res));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        expect(result.body).toEqual({});
    });

    it('registerMultipleStudents', () => {
        const returnedFromService = new StudentDTO();
        returnedFromService.login = 'login';
        const expected = { ...returnedFromService };
        let result: any;

        service
            .registerMultipleStudentsViaLoginOrRegistrationNumber(1, 1, [returnedFromService])
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => (result = body));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush([returnedFromService]);
        expect(result).toContainEqual(expected);
    });
});
