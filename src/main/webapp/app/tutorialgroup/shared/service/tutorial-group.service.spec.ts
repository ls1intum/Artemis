import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { map, take } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { provideHttpClient } from '@angular/common/http';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { Student } from 'app/openapi/model/student';
import { TutorialGroupRegistrationImport } from 'app/openapi/model/tutorialGroupRegistrationImport';
describe('TutorialGroupService', () => {
    let service: TutorialGroupsService;
    let httpMock: HttpTestingController;
    let tutorialGroupSessionService: jest.Mocked<TutorialGroupSessionService>;
    // following service is being used therefore the suppression is being used
    // @ts-expect-error
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    let tutorialGroupsConfigurationService: jest.Mocked<TutorialGroupsConfigurationService>;
    let tutorialGroupApiService: jest.Mocked<TutorialGroupApiService>;
    let elemDefault: TutorialGroup;

    beforeEach(() => {
        const spySessionService = {
            convertTutorialGroupSessionDatesFromServer: jest.fn(),
        };
        const spyConfigService = {
            convertTutorialGroupsConfigurationDatesFromServer: jest.fn(),
        };

        TestBed.configureTestingModule({
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
        tutorialGroupApiService = TestBed.inject(TutorialGroupApiService) as jest.Mocked<TutorialGroupApiService>;

        elemDefault = new TutorialGroup();
        elemDefault.id = 0;
        elemDefault.title = 'Test';
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('getOneOfCourse', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .getOneOfCourse(1, 1)
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
            .create(new TutorialGroup(), 1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { title: 'Test' });
        const expected = Object.assign({}, returnedFromService);

        service
            .update(1, 1, expected)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));

    it('getAllOfCourse', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { title: 'Test' });
        const expected = Object.assign({}, returnedFromService);

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

    it('deregisterStudent', () => {
        const apiServiceSpy = jest.spyOn(tutorialGroupApiService, 'deregisterStudent').mockReturnValue(of(new HttpResponse({ body: {} })));
        service
            .deregisterStudent(1, 1, 'login')
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        expect(apiServiceSpy).toHaveBeenCalledWith(1, 1, 'login', 'response');
    });

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
        const returnedFromService = {} as Student;
        returnedFromService.login = 'login';
        const expected = Object.assign({}, returnedFromService);

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
        const apiServiceSpy = jest.spyOn(tutorialGroupApiService, 'exportTutorialGroupsToCSV').mockReturnValue(of(mockBlob) as Observable<any>);

        service
            .exportTutorialGroupsToCSV(courseId, fields)
            .pipe(take(1))
            .subscribe((blob) => {
                expect(blob).toEqual(mockBlob);
            });

        expect(apiServiceSpy).toHaveBeenCalledWith(courseId, fields);
    });

    it('should export tutorial groups to JSON', () => {
        const courseId = 1;
        const fields = ['ID', 'Title', 'Campus', 'Language'];
        const mockResponse: Array<TutorialGroup> = [
            { id: 1, title: 'Group A', campus: 'Campus 1', language: 'English' },
            { id: 2, title: 'Group B', campus: 'Campus 2', language: 'German' },
        ];
        const apiServiceSpy = jest.spyOn(tutorialGroupApiService, 'exportTutorialGroupsToJSON').mockReturnValue(of(mockResponse) as Observable<any>);

        service
            .exportToJson(courseId, fields)
            .pipe(take(1))
            .subscribe((response) => {
                expect(response).toEqual(mockResponse);
            });

        expect(apiServiceSpy).toHaveBeenCalledWith(courseId, fields);
    });
    it('should get unique language values', () => {
        const courseId = 1;
        const mockResponse = ['English', 'German'];
        const apiServiceSpy = jest.spyOn(tutorialGroupApiService, 'getUniqueLanguageValues').mockReturnValue(of(new HttpResponse({ body: mockResponse })));

        service.getUniqueLanguageValues(courseId).subscribe((res) => {
            expect(res.body).toEqual(mockResponse);
        });

        expect(apiServiceSpy).toHaveBeenCalledWith(courseId, 'response');
    });

    it('should import tutorial groups', fakeAsync(() => {
        const courseId = 1;
        const tutorialGroups: TutorialGroupRegistrationImport[] = [{ title: 'Group A', student: { login: 'student1' } as Student }];
        const apiServiceSpy = jest.spyOn(tutorialGroupApiService, 'importRegistrations').mockReturnValue(of(new HttpResponse({ body: tutorialGroups })));
        service.import(courseId, tutorialGroups).subscribe((res) => {
            expect(res.body).toEqual(tutorialGroups);
        });
        expect(apiServiceSpy).toHaveBeenCalledWith(courseId, tutorialGroups, 'response');
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
