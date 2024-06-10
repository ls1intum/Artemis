import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { map, take } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { StudentDTO } from 'app/entities/student-dto.model';

describe('TutorialGroupService', () => {
    let service: TutorialGroupsService;
    let httpMock: HttpTestingController;
    let elemDefault: TutorialGroup;
    const resourceURL = 'api';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(TutorialGroupsService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = new TutorialGroup();
        elemDefault.id = 0;
        elemDefault.title = 'Test';
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('getOneOfCourse', fakeAsync(() => {
        const returnedFromService = { ...elemDefault };
        service
            .getOneOfCourse(1, 1)
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
            .create(new TutorialGroup(), 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, title: 'Test' };
        const expected = { ...returnedFromService };

        service
            .update(1, 1, expected)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('getAllOfCourse', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, title: 'Test' };
        const expected = { ...returnedFromService };

        service
            .getAllForCourse(1)
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toContainEqual(expected));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([returnedFromService]);
        tick();
    }));

    it('deregisterStudent', fakeAsync(() => {
        service
            .deregisterStudent(1, 1, 'login')
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        tick();
    }));

    it('registerStudent', fakeAsync(() => {
        service
            .registerStudent(1, 1, 'login')
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush({});
        tick();
    }));

    it('delete', fakeAsync(() => {
        service
            .delete(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        tick();
    }));

    it('getUniqueLanguageValues', fakeAsync(() => {
        service
            .getUniqueCampusValues(1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual([]));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([]);
        tick();
    }));

    it('registerMultipleStudents', fakeAsync(() => {
        const returnedFromService = new StudentDTO();
        returnedFromService.login = 'login';
        const expected = { ...returnedFromService };

        service
            .registerMultipleStudents(1, 1, [returnedFromService])
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toContainEqual(expected));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush([returnedFromService]);
        tick();
    }));

    it('should export tutorial groups to CSV', () => {
        const courseId = 1;
        const fields = ['ID', 'Title', 'Campus', 'Language'];
        const mockBlob = new Blob(['test'], { type: 'text/csv' });

        service
            .exportTutorialGroupsToCSV(courseId, fields)
            .pipe(take(1))
            .subscribe((blob) => {
                expect(blob).toEqual(mockBlob);
            });

        const req = httpMock.expectOne(`${resourceURL}/courses/${courseId}/tutorial-groups/export/csv?fields=ID&fields=Title&fields=Campus&fields=Language`);
        expect(req.request.method).toBe('GET');
        req.flush(mockBlob);
    });

    it('should export tutorial groups to JSON', () => {
        const courseId = 1;
        const fields = ['ID', 'Title', 'Campus', 'Language'];
        const mockResponse = JSON.stringify({ data: 'test' });

        service
            .exportToJson(courseId, fields)
            .pipe(take(1))
            .subscribe((response) => {
                expect(response).toEqual(mockResponse);
            });

        const req = httpMock.expectOne(`${resourceURL}/courses/${courseId}/tutorial-groups/export/json?fields=ID&fields=Title&fields=Campus&fields=Language`);
        expect(req.request.method).toBe('GET');
        req.flush(mockResponse, { headers: { 'Content-Type': 'application/json' } });
    });
});
