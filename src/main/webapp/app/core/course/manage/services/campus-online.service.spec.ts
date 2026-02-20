import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { take } from 'rxjs/operators';
import {
    CampusOnlineCourseDTO,
    CampusOnlineLinkRequest,
    CampusOnlineOrgUnit,
    CampusOnlineOrgUnitImportDTO,
    CampusOnlineService,
    CampusOnlineSyncResultDTO,
} from './campus-online.service';

describe('CampusOnline Service', () => {
    setupTestBed({ zoneless: true });

    let service: CampusOnlineService;
    let httpMock: HttpTestingController;
    const resourceUrl = 'api/core/admin/campus-online';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(CampusOnlineService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    describe('getOrgUnits', () => {
        it('should get all org units', () => {
            const mockOrgUnits: CampusOnlineOrgUnit[] = [
                { id: 1, externalId: '12345', name: 'CIT' },
                { id: 2, externalId: '67890', name: 'Management' },
            ];

            service
                .getOrgUnits()
                .pipe(take(1))
                .subscribe((results) => {
                    expect(results).toHaveLength(2);
                    expect(results[0].externalId).toBe('12345');
                    expect(results[1].name).toBe('Management');
                });

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/org-units` });
            req.flush(mockOrgUnits);
        });
    });

    describe('getOrgUnit', () => {
        it('should get a single org unit by id', () => {
            const mockOrgUnit: CampusOnlineOrgUnit = { id: 1, externalId: '12345', name: 'CIT' };

            service
                .getOrgUnit(1)
                .pipe(take(1))
                .subscribe((result) => {
                    expect(result.id).toBe(1);
                    expect(result.externalId).toBe('12345');
                });

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/org-units/1` });
            req.flush(mockOrgUnit);
        });
    });

    describe('createOrgUnit', () => {
        it('should create a new org unit', () => {
            const newOrgUnit: CampusOnlineOrgUnit = { externalId: '99999', name: 'New Faculty' };

            service
                .createOrgUnit(newOrgUnit)
                .pipe(take(1))
                .subscribe((res) => {
                    expect(res.body).toBeTruthy();
                    expect(res.body!.externalId).toBe('99999');
                });

            const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/org-units` });
            expect(req.request.body).toEqual(newOrgUnit);
            req.flush({ id: 3, externalId: '99999', name: 'New Faculty' });
        });
    });

    describe('updateOrgUnit', () => {
        it('should update an existing org unit', () => {
            const orgUnit = { id: 1, externalId: '12345', name: 'Updated CIT' } as CampusOnlineOrgUnit & { id: number };

            service
                .updateOrgUnit(orgUnit)
                .pipe(take(1))
                .subscribe((res) => {
                    expect(res.body).toBeTruthy();
                    expect(res.body!.name).toBe('Updated CIT');
                });

            const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/org-units/1` });
            expect(req.request.body).toEqual(orgUnit);
            req.flush(orgUnit);
        });
    });

    describe('deleteOrgUnit', () => {
        it('should delete an org unit', () => {
            service
                .deleteOrgUnit(1)
                .pipe(take(1))
                .subscribe((res) => expect(res.status).toBe(204));

            const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/org-units/1` });
            req.flush(null, { status: 204, statusText: 'No Content' });
        });
    });

    describe('importOrgUnits', () => {
        it('should import org units from CSV data', () => {
            const importDTOs: CampusOnlineOrgUnitImportDTO[] = [
                { externalId: '11111', name: 'Faculty A' },
                { externalId: '22222', name: 'Faculty B' },
            ];
            const mockResult: CampusOnlineOrgUnit[] = [
                { id: 10, externalId: '11111', name: 'Faculty A' },
                { id: 11, externalId: '22222', name: 'Faculty B' },
            ];

            service
                .importOrgUnits(importDTOs)
                .pipe(take(1))
                .subscribe((result) => {
                    expect(result).toHaveLength(2);
                    expect(result[0].externalId).toBe('11111');
                });

            const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/org-units/import` });
            expect(req.request.body).toEqual(importDTOs);
            req.flush(mockResult);
        });
    });

    describe('searchCourses', () => {
        it('should search courses by query and orgUnitId', () => {
            const mockResults: CampusOnlineCourseDTO[] = [
                {
                    campusOnlineCourseId: 'CO-101',
                    title: 'Introduction to Computer Science',
                    semester: '2025W',
                    responsibleInstructor: 'Prof. Smith',
                    department: 'CS Department',
                    studyProgram: 'Informatik BSc',
                    alreadyImported: false,
                },
            ];

            service
                .searchCourses('Computer', '12345')
                .pipe(take(1))
                .subscribe((results) => {
                    expect(results).toHaveLength(1);
                    expect(results[0].title).toBe('Introduction to Computer Science');
                    expect(results[0].campusOnlineCourseId).toBe('CO-101');
                });

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/courses/search?query=Computer&orgUnitId=12345` });
            req.flush(mockResults);
        });

        it('should search courses with semester parameter', () => {
            service
                .searchCourses('Math', '12345', '2025W')
                .pipe(take(1))
                .subscribe((results) => expect(results).toEqual([]));

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/courses/search?query=Math&orgUnitId=12345&semester=2025W` });
            req.flush([]);
        });

        it('should encode special characters in query', () => {
            service
                .searchCourses('C++ Programming', '12345')
                .pipe(take(1))
                .subscribe((results) => expect(results).toEqual([]));

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/courses/search?query=C%2B%2B%20Programming&orgUnitId=12345` });
            req.flush([]);
        });
    });

    describe('linkCourse', () => {
        it('should link a CAMPUSOnline course to an Artemis course', () => {
            const linkRequest: CampusOnlineLinkRequest = {
                campusOnlineCourseId: 'CO-101',
                responsibleInstructor: 'Prof. Smith',
                department: 'CS Department',
                studyProgram: 'Informatik BSc',
            };

            service
                .linkCourse(1, linkRequest)
                .pipe(take(1))
                .subscribe((res) => {
                    expect(res.body).toBeTruthy();
                    expect(res.body!.campusOnlineCourseId).toBe('CO-101');
                    expect(res.body!.title).toBe('Test Course');
                });

            const req = httpMock.expectOne({ method: 'PUT', url: `${resourceUrl}/courses/1/link` });
            expect(req.request.body).toEqual(linkRequest);
            req.flush({ campusOnlineCourseId: 'CO-101', title: 'Test Course', alreadyImported: false });
        });
    });

    describe('unlinkCourse', () => {
        it('should unlink a CAMPUSOnline course from an Artemis course', () => {
            service
                .unlinkCourse(1)
                .pipe(take(1))
                .subscribe((res) => expect(res.status).toBe(204));

            const req = httpMock.expectOne({ method: 'DELETE', url: `${resourceUrl}/courses/1/link` });
            req.flush(null, { status: 204, statusText: 'No Content' });
        });
    });

    describe('syncCourse', () => {
        it('should sync enrollment for a single course', () => {
            const mockResult: CampusOnlineSyncResultDTO = {
                coursesSynced: 1,
                coursesFailed: 0,
                usersAdded: 5,
                usersNotFound: 1,
            };

            service
                .syncCourse(1)
                .pipe(take(1))
                .subscribe((result) => {
                    expect(result.coursesSynced).toBe(1);
                    expect(result.usersAdded).toBe(5);
                    expect(result.usersNotFound).toBe(1);
                });

            const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/courses/1/sync` });
            req.flush(mockResult);
        });
    });

    describe('syncAllCourses', () => {
        it('should sync enrollment for all courses', () => {
            const mockResult: CampusOnlineSyncResultDTO = {
                coursesSynced: 3,
                coursesFailed: 0,
                usersAdded: 15,
                usersNotFound: 2,
            };

            service
                .syncAllCourses()
                .pipe(take(1))
                .subscribe((result) => {
                    expect(result.coursesSynced).toBe(3);
                    expect(result.usersAdded).toBe(15);
                });

            const req = httpMock.expectOne({ method: 'POST', url: `${resourceUrl}/sync` });
            req.flush(mockResult);
        });
    });
});
