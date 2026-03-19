import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { firstValueFrom, of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { TutorialGroupsService } from './tutorial-groups.service';
import {
    CreateOrUpdateTutorialGroupDTO,
    RawTutorialGroupDTO,
    TutorialGroup,
    TutorialGroupRegisteredStudentDTO,
    TutorialGroupScheduleDTO,
} from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { Student } from 'app/openapi/model/student';
import { TutorialGroupRegistrationImport } from 'app/openapi/model/tutorialGroupRegistrationImport';
import { TutorialGroupExport } from 'app/openapi/model/tutorialGroupExport';
import { TutorialGroupSchedule } from 'app/tutorialgroup/shared/entities/tutorial-group-schedule.model';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { Course } from 'app/core/course/shared/entities/course.model';

interface TutorialGroupApiServiceMock {
    getUniqueLanguageValues: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
    deregisterStudent: ReturnType<typeof vi.fn>;
    importRegistrations: ReturnType<typeof vi.fn>;
    exportTutorialGroupsToCSV: ReturnType<typeof vi.fn>;
    exportTutorialGroupsToJSON: ReturnType<typeof vi.fn>;
}

function createTutorialGroup(): TutorialGroup {
    const tutorialGroupSchedule = new TutorialGroupSchedule();
    tutorialGroupSchedule.validFromInclusive = dayjs('2025-01-10');
    tutorialGroupSchedule.validToInclusive = dayjs('2025-01-20');

    const tutorialGroupSession: TutorialGroupSession = {
        id: 11,
        start: dayjs('2025-01-10T09:00:00Z'),
        end: dayjs('2025-01-10T10:00:00Z'),
    };

    const nextSession: TutorialGroupSession = {
        id: 12,
        start: dayjs('2025-01-11T09:00:00Z'),
        end: dayjs('2025-01-11T10:00:00Z'),
    };

    const tutorialGroupsConfiguration = new TutorialGroupsConfiguration();
    tutorialGroupsConfiguration.tutorialPeriodStartInclusive = dayjs('2025-01-01');
    tutorialGroupsConfiguration.tutorialPeriodEndInclusive = dayjs('2025-02-01');
    tutorialGroupsConfiguration.tutorialGroupFreePeriods = [];

    const course = new Course();
    course.tutorialGroupsConfiguration = tutorialGroupsConfiguration;

    return {
        id: 1,
        title: 'TG 1',
        tutorialGroupSchedule,
        tutorialGroupSessions: [tutorialGroupSession],
        nextSession,
        course,
    };
}

function createTutorialGroupDTO(): CreateOrUpdateTutorialGroupDTO {
    return {
        title: 'Group A',
        tutorId: 5,
        language: 'English',
        isOnline: true,
        capacity: 12,
    };
}

function createRegisteredStudent(id: number, login: string): TutorialGroupRegisteredStudentDTO {
    return {
        id,
        login,
        name: `${login} name`,
        email: `${login}@tum.de`,
        registrationNumber: `${id}`,
    };
}

// TODO: have a close look at these tests again
describe('TutorialGroupsService', () => {
    setupTestBed({ zoneless: true });

    let service: TutorialGroupsService;
    let httpMock: HttpTestingController;

    let tutorialGroupApiServiceMock: TutorialGroupApiServiceMock;

    beforeEach(() => {
        tutorialGroupApiServiceMock = {
            getUniqueLanguageValues: vi.fn(),
            delete: vi.fn(),
            deregisterStudent: vi.fn(),
            importRegistrations: vi.fn(),
            exportTutorialGroupsToCSV: vi.fn(),
            exportTutorialGroupsToJSON: vi.fn(),
        };

        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TutorialGroupApiService, useValue: tutorialGroupApiServiceMock }],
        });

        service = TestBed.inject(TutorialGroupsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should get unique language values from the api service', async () => {
        tutorialGroupApiServiceMock.getUniqueLanguageValues.mockReturnValue(of(new HttpResponse({ body: ['English', 'German'] })));

        const result = await firstValueFrom(service.getUniqueLanguageValues(7));

        expect(tutorialGroupApiServiceMock.getUniqueLanguageValues).toHaveBeenCalledWith(7, 'response');
        expect(result).toEqual(['English', 'German']);
    });

    it('should get an empty list when the api service returns no language values', async () => {
        tutorialGroupApiServiceMock.getUniqueLanguageValues.mockReturnValue(of(new HttpResponse({ body: undefined })));

        const result = await firstValueFrom(service.getUniqueLanguageValues(7));

        expect(result).toEqual([]);
    });

    it('should get all tutorial groups for a course and convert nested dates', async () => {
        const tutorialGroup = createTutorialGroup();

        const resultPromise = firstValueFrom(service.getAllForCourse(42));

        const req = httpMock.expectOne({ method: 'GET', url: 'api/tutorialgroup/courses/42/tutorial-groups' });
        req.flush([tutorialGroup]);

        const result = await resultPromise;
        const resultBody = result.body;
        expect(resultBody).toBeDefined();
        expect(resultBody).toHaveLength(1);

        expect(resultBody?.[0].tutorialGroupSchedule?.validFromInclusive?.toISOString()).toBe(dayjs('2025-01-10').toISOString());
        expect(resultBody?.[0].tutorialGroupSchedule?.validToInclusive?.toISOString()).toBe(dayjs('2025-01-20').toISOString());
        expect(resultBody?.[0].tutorialGroupSessions?.[0].start?.toISOString()).toBe(dayjs('2025-01-10T09:00:00Z').toISOString());
        expect(resultBody?.[0].tutorialGroupSessions?.[0].end?.toISOString()).toBe(dayjs('2025-01-10T10:00:00Z').toISOString());
        expect(resultBody?.[0].nextSession?.start?.toISOString()).toBe(dayjs('2025-01-11T09:00:00Z').toISOString());
        expect(resultBody?.[0].nextSession?.end?.toISOString()).toBe(dayjs('2025-01-11T10:00:00Z').toISOString());
        expect(resultBody?.[0].course?.tutorialGroupsConfiguration?.tutorialPeriodStartInclusive?.toISOString()).toBe(dayjs('2025-01-01').toISOString());
        expect(resultBody?.[0].course?.tutorialGroupsConfiguration?.tutorialPeriodEndInclusive?.toISOString()).toBe(dayjs('2025-02-01').toISOString());
    });

    it('should get a tutorial group dto and map it to a TutorialGroupDTO', async () => {
        const rawDto: RawTutorialGroupDTO = {
            id: 1,
            title: 'Group A',
            language: 'English',
            isOnline: true,
            tutorName: 'Ada',
            tutorLogin: 'ada',
            tutorId: 2,
            sessions: [
                {
                    id: 9,
                    start: '2025-01-10T09:00:00Z',
                    end: '2025-01-10T10:00:00Z',
                    location: 'MI 00.01.001',
                    isCancelled: false,
                    locationChanged: false,
                    timeChanged: false,
                    dateChanged: false,
                    attendanceCount: 7,
                },
            ],
            capacity: 15,
        };

        const resultPromise = firstValueFrom(service.getTutorialGroupDTO(7, 9));

        const req = httpMock.expectOne({ method: 'GET', url: 'api/tutorialgroup/courses/7/tutorial-groups/9/dto' });
        req.flush(rawDto);

        const result = await resultPromise;
        expect(result.id).toBe(1);
        expect(result.sessions).toHaveLength(1);
        expect(result.sessions[0].start.toISOString()).toBe(dayjs('2025-01-10T09:00:00Z').toISOString());
        expect(result.sessions[0].end.toISOString()).toBe(dayjs('2025-01-10T10:00:00Z').toISOString());
    });

    it('should create a tutorial group', async () => {
        const dto = createTutorialGroupDTO();
        const resultPromise = firstValueFrom(service.create(7, dto));

        const req = httpMock.expectOne({ method: 'POST', url: 'api/tutorialgroup/courses/7/tutorial-groups' });
        expect(req.request.body).toEqual(dto);
        req.flush(null);

        await expect(resultPromise).resolves.toBeNull();
    });

    it('should update a tutorial group', async () => {
        const dto = createTutorialGroupDTO();
        const resultPromise = firstValueFrom(service.update(7, 9, dto));

        const req = httpMock.expectOne({ method: 'PUT', url: 'api/tutorialgroup/courses/7/tutorial-groups/9' });
        expect(req.request.body).toEqual(dto);
        req.flush(null);

        await expect(resultPromise).resolves.toBeNull();
    });

    it('should delete a tutorial group via the api service', async () => {
        tutorialGroupApiServiceMock.delete.mockReturnValue(of(new HttpResponse<void>({ status: 204 })));

        const result = await firstValueFrom(service.delete(7, 9));

        expect(tutorialGroupApiServiceMock.delete).toHaveBeenCalledWith(7, 9, 'response');
        expect(result.status).toBe(204);
    });

    it('should get undefined when the tutorial group response is null', async () => {
        const resultPromise = firstValueFrom(service.getTutorialGroupScheduleDTO(7, 9));

        const req = httpMock.expectOne({ method: 'GET', url: 'api/tutorialgroup/courses/7/tutorial-groups/9/schedule' });
        req.flush(null);

        await expect(resultPromise).resolves.toBeUndefined();
    });

    it('should get the tutorial group schedule dto', async () => {
        const scheduleDto: TutorialGroupScheduleDTO = {
            firstSessionStart: '2025-01-10T09:00:00',
            firstSessionEnd: '2025-01-10T10:00:00',
            repetitionFrequency: 1,
            tutorialPeriodEnd: '2025-02-01',
            location: 'MI 00.01.001',
        };

        const resultPromise = firstValueFrom(service.getTutorialGroupScheduleDTO(7, 9));

        const req = httpMock.expectOne({ method: 'GET', url: 'api/tutorialgroup/courses/7/tutorial-groups/9/schedule' });
        req.flush(scheduleDto);

        await expect(resultPromise).resolves.toEqual(scheduleDto);
    });

    it('should get registered student dtos', async () => {
        const students = [createRegisteredStudent(1, 'ada')];
        const resultPromise = firstValueFrom(service.getRegisteredStudentDTOs(7, 9));

        const req = httpMock.expectOne({ method: 'GET', url: 'api/tutorialgroup/courses/7/tutorial-groups/9/registered-students' });
        req.flush(students);

        await expect(resultPromise).resolves.toEqual(students);
    });

    it('should get unregistered student dtos', async () => {
        const students = [createRegisteredStudent(2, 'alan')];
        const resultPromise = firstValueFrom(service.getUnregisteredStudentDTOs(7, 9, 'ada', 3, 25));

        const req = httpMock.expectOne((request) => request.method === 'GET' && request.url === 'api/tutorialgroup/courses/7/tutorial-groups/9/unregistered-students');
        expect(req.request.params.get('loginOrName')).toBe('ada');
        expect(req.request.params.get('pageIndex')).toBe('3');
        expect(req.request.params.get('pageSize')).toBe('25');
        req.flush(students);

        await expect(resultPromise).resolves.toEqual(students);
    });

    it('should deregister a student via the api service', async () => {
        tutorialGroupApiServiceMock.deregisterStudent.mockReturnValue(of(new HttpResponse<void>({ status: 200 })));

        const result = await firstValueFrom(service.deregisterStudent(7, 9, 'ada'));

        expect(tutorialGroupApiServiceMock.deregisterStudent).toHaveBeenCalledWith(7, 9, 'ada', 'response');
        expect(result.status).toBe(200);
    });

    it('should import registrations', async () => {
        const studentDtos = [{ login: 'ada' }, { login: 'alan' }] as Student[];
        const responseBody = [{ login: 'ada' }];
        const resultPromise = firstValueFrom(service.importRegistrations(7, 9, studentDtos));

        const req = httpMock.expectOne({ method: 'POST', url: 'api/tutorialgroup/courses/7/tutorial-groups/9/register-multiple' });
        expect(req.request.body).toEqual(studentDtos);
        req.flush(responseBody);

        const result = await resultPromise;
        expect(result.body).toEqual(responseBody);
    });

    it('should register multiple students via login', async () => {
        const logins = ['ada', 'alan'];
        const resultPromise = firstValueFrom(service.registerMultipleStudentsViaLogin(7, 9, logins));

        const req = httpMock.expectOne({ method: 'POST', url: 'api/tutorialgroup/courses/7/tutorial-groups/9/register-via-login' });
        expect(req.request.body).toEqual(logins);
        req.flush({});

        const result = await resultPromise;
        expect(result.ok).toBe(true);
    });

    it('should import tutorial group registrations via the api service', async () => {
        const tutorialGroups: TutorialGroupRegistrationImport[] = [{ title: 'A', student: { login: 'ada' } as Student }];
        tutorialGroupApiServiceMock.importRegistrations.mockReturnValue(of(new HttpResponse({ body: tutorialGroups })));

        const result = await firstValueFrom(service.import(7, tutorialGroups));

        expect(tutorialGroupApiServiceMock.importRegistrations).toHaveBeenCalledWith(7, tutorialGroups, 'response');
        expect(result.body).toEqual(tutorialGroups);
    });

    it('should export tutorial groups to csv via the api service', async () => {
        const blob = new Blob(['csv']);
        tutorialGroupApiServiceMock.exportTutorialGroupsToCSV.mockReturnValue(of(blob));

        const result = await firstValueFrom(service.exportTutorialGroupsToCSV(7, ['title', 'language']));

        expect(tutorialGroupApiServiceMock.exportTutorialGroupsToCSV).toHaveBeenCalledWith(7, ['title', 'language']);
        expect(result).toBe(blob);
    });

    it('should export tutorial groups to json via the api service', async () => {
        const exports = [{ title: 'Group A' }, { title: 'Group B' }] as TutorialGroupExport[];
        tutorialGroupApiServiceMock.exportTutorialGroupsToJSON.mockReturnValue(of(exports));

        const result = await firstValueFrom(service.exportToJson(7, ['title']));

        expect(tutorialGroupApiServiceMock.exportTutorialGroupsToJSON).toHaveBeenCalledWith(7, ['title']);
        expect(result).toBe(JSON.stringify(exports));
    });
});
