import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
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
    setupTestBed({ zoneless: true });

    let service: TutorialGroupsService;
    let httpMock: HttpTestingController;
    let tutorialGroupSessionService: TutorialGroupSessionService;
    let tutorialGroupApiService: TutorialGroupApiService;
    let elemDefault: TutorialGroup;

    beforeEach(() => {
        const spySessionService = {
            convertTutorialGroupSessionDatesFromServer: vi.fn(),
        };
        const spyConfigService = {
            convertTutorialGroupsConfigurationDatesFromServer: vi.fn(),
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
        tutorialGroupSessionService = TestBed.inject(TutorialGroupSessionService);
        tutorialGroupApiService = TestBed.inject(TutorialGroupApiService);

        elemDefault = new TutorialGroup();
        elemDefault.id = 0;
        elemDefault.title = 'Test';
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
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
        const apiServiceSpy = vi.spyOn(tutorialGroupApiService, 'deregisterStudent').mockReturnValue(of(new HttpResponse({ body: {} })));
        service
            .deregisterStudent(1, 1, 'login')
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toEqual({}));
        expect(apiServiceSpy).toHaveBeenCalledWith(1, 1, 'login', 'response');
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

    it('getUniqueLanguageValues', () => {
        let result: any;
        service
            .getUniqueCampusValues(1)
            .pipe(take(1))
            .subscribe((res) => (result = res));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush([]);
        expect(result.body).toEqual([]);
    });

    it('registerMultipleStudents', () => {
        const returnedFromService = {} as Student;
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

    it('should export tutorial groups to CSV', () => {
        const courseId = 1;
        const fields = ['ID', 'Title', 'Campus', 'Language'];
        const mockBlob = new Blob(['test'], { type: 'text/csv' });
        const apiServiceSpy = vi.spyOn(tutorialGroupApiService, 'exportTutorialGroupsToCSV').mockReturnValue(of(mockBlob) as Observable<any>);

        let result: any;
        service
            .exportTutorialGroupsToCSV(courseId, fields)
            .pipe(take(1))
            .subscribe((blob) => {
                result = blob;
            });

        expect(result).toEqual(mockBlob);
        expect(apiServiceSpy).toHaveBeenCalledWith(courseId, fields);
    });

    it('should export tutorial groups to JSON', () => {
        const courseId = 1;
        const fields = ['ID', 'Title', 'Campus', 'Language'];
        const mockResponse: Array<TutorialGroup> = [
            { id: 1, title: 'Group A', campus: 'Campus 1', language: 'English' },
            { id: 2, title: 'Group B', campus: 'Campus 2', language: 'German' },
        ];
        const apiServiceSpy = vi.spyOn(tutorialGroupApiService, 'exportTutorialGroupsToJSON').mockReturnValue(of(mockResponse) as Observable<any>);

        let result: any;
        service
            .exportToJson(courseId, fields)
            .pipe(take(1))
            .subscribe((response) => {
                result = response;
            });

        // exportToJson returns JSON.stringify of the response
        expect(result).toEqual(JSON.stringify(mockResponse));
        expect(apiServiceSpy).toHaveBeenCalledWith(courseId, fields);
    });

    it('should get unique language values', () => {
        const courseId = 1;
        const mockResponse = ['English', 'German'];
        const apiServiceSpy = vi.spyOn(tutorialGroupApiService, 'getUniqueLanguageValues').mockReturnValue(of(new HttpResponse({ body: mockResponse })));

        let result: any;
        service.getUniqueLanguageValues(courseId).subscribe((res) => {
            result = res;
        });

        expect(result.body).toEqual(mockResponse);
        expect(apiServiceSpy).toHaveBeenCalledWith(courseId, 'response');
    });

    it('should import tutorial groups', () => {
        const courseId = 1;
        const tutorialGroups: TutorialGroupRegistrationImport[] = [{ title: 'Group A', student: { login: 'student1' } as Student }];
        const apiServiceSpy = vi.spyOn(tutorialGroupApiService, 'importRegistrations').mockReturnValue(of(new HttpResponse({ body: tutorialGroups })));
        let result: any;
        service.import(courseId, tutorialGroups).subscribe((res) => {
            result = res;
        });
        expect(result.body).toEqual(tutorialGroups);
        expect(apiServiceSpy).toHaveBeenCalledWith(courseId, tutorialGroups, 'response');
    });

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
