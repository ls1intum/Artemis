import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { map, take } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/course/tutorial-groups/services/tutorial-groups.service';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { StudentDTO } from 'app/entities/student-dto.model';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { TutorialGroupRegistrationImportDTO } from 'app/entities/tutorial-group/tutorial-group-import-dto.model';
import { provideHttpClient } from '@angular/common/http';

describe('TutorialGroupService', () => {
    let service: TutorialGroupsService;
    let httpMock: HttpTestingController;
    let tutorialGroupSessionService: jest.Mocked<TutorialGroupSessionService>;
    // following service is being used therefore the suppression is being used
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    let tutorialGroupsConfigurationService: jest.Mocked<TutorialGroupsConfigurationService>;
    let elemDefault: TutorialGroup;
    const resourceURL = 'api';

    beforeEach(() => {
        const spySessionService = {
            convertTutorialGroupSessionDatesFromServer: jest.fn(),
        };
        const spyConfigService = {
            convertTutorialGroupsConfigurationDatesFromServer: jest.fn(),
        };

        TestBed.configureTestingModule({
            imports: [],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TutorialGroupSessionService, useValue: spySessionService },
                { provide: TutorialGroupsConfigurationService, useValue: spyConfigService },
            ],
        });
        service = TestBed.inject(TutorialGroupsService);
        httpMock = TestBed.inject(HttpTestingController);
        tutorialGroupSessionService = TestBed.inject(TutorialGroupSessionService) as jest.Mocked<TutorialGroupSessionService>;
        tutorialGroupsConfigurationService = TestBed.inject(TutorialGroupsConfigurationService) as jest.Mocked<TutorialGroupsConfigurationService>;

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
    it('should get unique language values', fakeAsync(() => {
        const courseId = 1;
        const mockResponse = ['English', 'German'];

        service.getUniqueLanguageValues(courseId).subscribe((res) => {
            expect(res.body).toEqual(mockResponse);
        });

        const req = httpMock.expectOne(`${resourceURL}/courses/${courseId}/tutorial-groups/language-values`);
        expect(req.request.method).toBe('GET');
        req.flush(mockResponse);
        tick();
    }));

    it('should import tutorial groups', fakeAsync(() => {
        const courseId = 1;
        const tutorialGroups: TutorialGroupRegistrationImportDTO[] = [{ title: 'Group A', student: { login: 'student1' } as StudentDTO }];

        service.import(courseId, tutorialGroups).subscribe((res) => {
            expect(res.body).toEqual(tutorialGroups);
        });

        const req = httpMock.expectOne(`${resourceURL}/courses/${courseId}/tutorial-groups/import`);
        expect(req.request.method).toBe('POST');
        req.flush(tutorialGroups);
        tick();
    }));

    it('should convert tutorial group array dates from server', () => {
        const tutorialGroups: TutorialGroup[] = [
            { id: 1, title: 'Group A' },
            { id: 2, title: 'Group B' },
        ];

        service.convertTutorialGroupArrayDatesFromServer(tutorialGroups);
        expect(tutorialGroups).toBeTruthy(); // basic check to ensure the method runs
    });

    it('should convert tutorial group dates from server with sessions', () => {
        const tutorialGroup: TutorialGroup = {
            id: 1,
            title: 'Group A',
            tutorialGroupSessions: [{ id: 1, start: '2020-01-01T00:00:00Z', end: '2020-01-01T01:00:00Z' }] as unknown as TutorialGroupSession[],
        };

        service.convertTutorialGroupDatesFromServer(tutorialGroup);
        expect(tutorialGroup.tutorialGroupSessions).toBeTruthy();
        expect(tutorialGroupSessionService.convertTutorialGroupSessionDatesFromServer).toHaveBeenCalled();
    });
});
