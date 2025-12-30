import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { map, take } from 'rxjs/operators';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { provideHttpClient } from '@angular/common/http';
import { Student } from 'app/openapi/models/student';
import { TutorialGroupRegistrationImport } from 'app/openapi/models/tutorial-group-registration-import';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import { of } from 'rxjs';
describe('TutorialGroupService', () => {
    let service: TutorialGroupsService;
    let httpMock: HttpTestingController;
    let tutorialGroupSessionService: jest.Mocked<TutorialGroupSessionService>;
    // following service is being used therefore the suppression is being used
    // @ts-expect-error
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    let tutorialGroupsConfigurationService: jest.Mocked<TutorialGroupsConfigurationService>;
    let tutorialGroupApi: jest.Mocked<TutorialGroupApi>;
    let elemDefault: TutorialGroup;

    beforeEach(() => {
        const spySessionService = {
            convertTutorialGroupSessionDatesFromServer: jest.fn(),
        };
        const spyConfigService = {
            convertTutorialGroupsConfigurationDatesFromServer: jest.fn(),
        };
        const spyTutorialGroupApi = {
            deregisterStudent: jest.fn(),
            registerStudent: jest.fn(),
            registerMultipleStudentsToTutorialGroup: jest.fn(),
            importRegistrations: jest.fn(),
            delete: jest.fn(),
        };

        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TutorialGroupSessionService, useValue: spySessionService },
                { provide: TutorialGroupsConfigurationService, useValue: spyConfigService },
                { provide: TutorialGroupApi, useValue: spyTutorialGroupApi },
            ],
        });
        service = TestBed.inject(TutorialGroupsService);
        httpMock = TestBed.inject(HttpTestingController);
        tutorialGroupSessionService = TestBed.inject(TutorialGroupSessionService) as jest.Mocked<TutorialGroupSessionService>;
        tutorialGroupsConfigurationService = TestBed.inject(TutorialGroupsConfigurationService) as jest.Mocked<TutorialGroupsConfigurationService>;
        tutorialGroupApi = TestBed.inject(TutorialGroupApi) as jest.Mocked<TutorialGroupApi>;

        elemDefault = new TutorialGroup();
        elemDefault.id = 0;
        elemDefault.title = 'Test';
    });

    afterEach(() => {
        httpMock.verify();
    });

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

    it('deregisterStudent', fakeAsync(() => {
        tutorialGroupApi.deregisterStudent.mockReturnValue(of(undefined));
        service
            .deregisterStudent(1, 1, 'login')
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toBeUndefined());
        expect(tutorialGroupApi.deregisterStudent).toHaveBeenCalledWith(1, 1, 'login');
        tick();
    }));

    it('registerStudent', fakeAsync(() => {
        tutorialGroupApi.registerStudent.mockReturnValue(of(undefined));
        service
            .registerStudent(1, 1, 'login')
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toBeUndefined());
        expect(tutorialGroupApi.registerStudent).toHaveBeenCalledWith(1, 1, 'login');
        tick();
    }));

    it('delete', fakeAsync(() => {
        tutorialGroupApi.delete.mockReturnValue(of(undefined));
        service
            .delete(1, 1)
            .pipe(take(1))
            .subscribe((res) => expect(res.body).toBeUndefined());
        expect(tutorialGroupApi.delete).toHaveBeenCalledWith(1, 1);
        tick();
    }));

    it('registerMultipleStudents', fakeAsync(() => {
        const returnedFromService = {} as Student;
        returnedFromService.login = 'login';
        const expected = { ...returnedFromService };
        tutorialGroupApi.registerMultipleStudentsToTutorialGroup.mockReturnValue(of([returnedFromService]));

        service
            .registerMultipleStudents(1, 1, [returnedFromService])
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => expect(body).toContainEqual(expected));
        expect(tutorialGroupApi.registerMultipleStudentsToTutorialGroup).toHaveBeenCalledWith(1, 1, [returnedFromService]);
        tick();
    }));

    it('should import tutorial groups', fakeAsync(() => {
        const courseId = 1;
        const tutorialGroups: TutorialGroupRegistrationImport[] = [{ title: 'Group A', student: { login: 'student1' } as Student }];
        tutorialGroupApi.importRegistrations.mockReturnValue(of(tutorialGroups));
        service.import(courseId, tutorialGroups).subscribe((res) => {
            expect(res.body).toEqual(tutorialGroups);
        });
        expect(tutorialGroupApi.importRegistrations).toHaveBeenCalledWith(courseId, tutorialGroups);
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
