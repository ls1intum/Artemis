import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { map, take } from 'rxjs/operators';
import { firstValueFrom } from 'rxjs';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { RawTutorialGroupDetailGroupDTO, TutorialGroup, TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { provideHttpClient } from '@angular/common/http';

function createRegisteredStudent(id: number, login: string): TutorialGroupRegisteredStudentDTO {
    return {
        id,
        login,
        name: `${login} name`,
        email: `${login}@tum.de`,
        registrationNumber: `${id}`,
    };
}

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

    it('should get tutorial group detail group dto', async () => {
        const returnedFromService: RawTutorialGroupDetailGroupDTO = {
            id: 9,
            title: 'Tutorial 1',
            language: 'English',
            isOnline: false,
            tutorName: 'Ada Lovelace',
            tutorLogin: 'ada',
            tutorImageUrl: 'https://example.org/ada.png',
            capacity: 15,
            campus: 'Garching',
            groupChannelId: 21,
            tutorChatId: 22,
            sessions: [
                {
                    start: '2026-01-10T10:00:00.000Z',
                    end: '2026-01-10T12:00:00.000Z',
                    location: 'MI HS 1',
                    isCancelled: false,
                    locationChanged: true,
                    timeChanged: false,
                    dateChanged: true,
                    attendanceCount: 12,
                },
            ],
        };
        const resultPromise = firstValueFrom(service.getTutorialGroupDetailGroupDTO(7, 9));

        const req = httpMock.expectOne({ method: 'GET', url: 'api/tutorialgroup/courses/7/tutorial-group-detail/9' });
        req.flush(returnedFromService);

        const result = await resultPromise;
        expect(result).toMatchObject({
            id: returnedFromService.id,
            title: returnedFromService.title,
            language: returnedFromService.language,
            isOnline: returnedFromService.isOnline,
            tutorName: returnedFromService.tutorName,
            tutorLogin: returnedFromService.tutorLogin,
            tutorImageUrl: returnedFromService.tutorImageUrl,
            capacity: returnedFromService.capacity,
            campus: returnedFromService.campus,
            groupChannelId: returnedFromService.groupChannelId,
            tutorChatId: returnedFromService.tutorChatId,
        });
        expect(result.sessions).toHaveLength(1);
        expect(result.sessions[0]).toMatchObject({
            location: returnedFromService.sessions?.[0].location,
            isCancelled: returnedFromService.sessions?.[0].isCancelled,
            locationChanged: returnedFromService.sessions?.[0].locationChanged,
            timeChanged: returnedFromService.sessions?.[0].timeChanged,
            dateChanged: returnedFromService.sessions?.[0].dateChanged,
            attendanceCount: returnedFromService.sessions?.[0].attendanceCount,
        });
        expect(result.sessions[0].start.toISOString()).toBe(returnedFromService.sessions?.[0].start);
        expect(result.sessions[0].end.toISOString()).toBe(returnedFromService.sessions?.[0].end);
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
            .importRegistrations(1, 1, [returnedFromService])
            .pipe(
                take(1),
                map((resp) => resp.body),
            )
            .subscribe((body) => (result = body));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush([returnedFromService]);
        expect(result).toContainEqual(expected);
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

    it('should register multiple students via login', async () => {
        const logins = ['ada', 'alan'];
        const resultPromise = firstValueFrom(service.registerMultipleStudentsViaLogin(7, 9, logins));

        const req = httpMock.expectOne({ method: 'POST', url: 'api/tutorialgroup/courses/7/tutorial-groups/9/register-via-login' });
        expect(req.request.body).toEqual(logins);
        req.flush({});

        const result = await resultPromise;
        expect(result.ok).toBe(true);
    });
});
