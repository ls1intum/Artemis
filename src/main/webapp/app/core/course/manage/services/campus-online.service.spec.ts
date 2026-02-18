import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { CampusOnlineCourseDTO, CampusOnlineLinkRequest, CampusOnlineService, CampusOnlineSyncResultDTO } from './campus-online.service';

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

    describe('searchCourses', () => {
        it('should search courses by query', () => {
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
                .searchCourses('Computer')
                .pipe(take(1))
                .subscribe((results) => {
                    expect(results).toHaveLength(1);
                    expect(results[0].title).toBe('Introduction to Computer Science');
                    expect(results[0].campusOnlineCourseId).toBe('CO-101');
                });

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/courses/search?query=Computer` });
            req.flush(mockResults);
        });

        it('should search courses with semester parameter', () => {
            service
                .searchCourses('Math', '2025W')
                .pipe(take(1))
                .subscribe((results) => expect(results).toEqual([]));

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/courses/search?query=Math&semester=2025W` });
            req.flush([]);
        });

        it('should encode special characters in query', () => {
            service
                .searchCourses('C++ Programming')
                .pipe(take(1))
                .subscribe((results) => expect(results).toEqual([]));

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}/courses/search?query=C%2B%2B%20Programming` });
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
