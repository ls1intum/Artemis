import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import dayjs from 'dayjs/esm';

import { CourseRequestService } from 'app/core/course/request/course-request.service';
import { BaseCourseRequest, CourseRequestStatus } from 'app/core/shared/entities/course-request.model';

describe('CourseRequestService', () => {
    let service: CourseRequestService;
    let httpMock: HttpTestingController;

    const resourceUrl = 'api/core/course-requests';
    const adminResourceUrl = 'api/core/admin/course-requests';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(CourseRequestService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('create', () => {
        it('should create a course request and convert dates', () => {
            const baseCourseRequest: BaseCourseRequest = {
                title: 'Test Course',
                shortName: 'TC001',
                semester: 'WS2025',
                startDate: dayjs('2025-01-01'),
                endDate: dayjs('2025-06-30'),
                testCourse: false,
                reason: 'I need this course for teaching.',
            };

            const mockResponse = {
                id: 1,
                title: 'Test Course',
                shortName: 'TC001',
                semester: 'WS2025',
                startDate: '2025-01-01T00:00:00Z',
                endDate: '2025-06-30T00:00:00Z',
                testCourse: false,
                reason: 'I need this course for teaching.',
                status: CourseRequestStatus.PENDING,
                createdDate: '2025-01-15T10:30:00Z',
                requester: { id: 1, login: 'student1' },
            };

            service.create(baseCourseRequest).subscribe((result) => {
                expect(result.id).toBe(1);
                expect(result.title).toBe('Test Course');
                expect(result.shortName).toBe('TC001');
                expect(result.semester).toBe('WS2025');
                expect(result.status).toBe(CourseRequestStatus.PENDING);
                expect(result.startDate).toBeDefined();
                expect(result.endDate).toBeDefined();
                expect(result.createdDate).toBeDefined();
                expect(result.requester?.login).toBe('student1');
            });

            const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
            expect(req.request.body.title).toBe('Test Course');
            expect(req.request.body.shortName).toBe('TC001');
            expect(req.request.body.semester).toBe('WS2025');
            expect(req.request.body.testCourse).toBeFalse();
            expect(req.request.body.reason).toBe('I need this course for teaching.');
            expect(req.request.body.startDate).toBeDefined();
            expect(req.request.body.endDate).toBeDefined();

            req.flush(mockResponse);
        });

        it('should create a course request without optional fields', () => {
            const baseCourseRequest: BaseCourseRequest = {
                title: 'Minimal Course',
                shortName: 'MC001',
                testCourse: true,
                reason: 'Testing purpose.',
            };

            const mockResponse = {
                id: 2,
                title: 'Minimal Course',
                shortName: 'MC001',
                testCourse: true,
                reason: 'Testing purpose.',
                status: CourseRequestStatus.PENDING,
            };

            service.create(baseCourseRequest).subscribe((result) => {
                expect(result.id).toBe(2);
                expect(result.title).toBe('Minimal Course');
                expect(result.testCourse).toBeTrue();
                expect(result.semester).toBeUndefined();
                expect(result.startDate).toBeUndefined();
                expect(result.endDate).toBeUndefined();
            });

            const req = httpMock.expectOne({ method: 'POST', url: resourceUrl });
            req.flush(mockResponse);
        });
    });

    describe('findAdminOverview', () => {
        it('should retrieve admin overview with pending and decided requests', () => {
            const mockResponse = {
                pendingRequests: [
                    {
                        id: 1,
                        title: 'Course 1',
                        shortName: 'C1',
                        testCourse: false,
                        reason: 'Reason 1',
                        status: CourseRequestStatus.PENDING,
                        createdDate: '2025-01-10T08:00:00Z',
                        requester: { id: 1, login: 'user1' },
                        instructorCourseCount: 2,
                    },
                ],
                decidedRequests: [
                    {
                        id: 2,
                        title: 'Course 2',
                        shortName: 'C2',
                        testCourse: true,
                        reason: 'Reason 2',
                        status: CourseRequestStatus.ACCEPTED,
                        createdDate: '2025-01-11T09:00:00Z',
                        processedDate: '2025-01-12T10:00:00Z',
                        createdCourseId: 100,
                        requester: { id: 2, login: 'user2' },
                    },
                ],
                totalDecidedCount: 1,
            };

            service.findAdminOverview().subscribe((result) => {
                expect(result.pendingRequests).toHaveLength(1);
                expect(result.pendingRequests[0].id).toBe(1);
                expect(result.pendingRequests[0].status).toBe(CourseRequestStatus.PENDING);
                expect(result.pendingRequests[0].instructorCourseCount).toBe(2);
                expect(result.decidedRequests).toHaveLength(1);
                expect(result.decidedRequests[0].id).toBe(2);
                expect(result.decidedRequests[0].status).toBe(CourseRequestStatus.ACCEPTED);
                expect(result.decidedRequests[0].createdCourseId).toBe(100);
                expect(result.totalDecidedCount).toBe(1);
            });

            const req = httpMock.expectOne({ method: 'GET', url: `${adminResourceUrl}/overview?decidedPage=0&decidedPageSize=20` });
            req.flush(mockResponse);
        });

        it('should return empty arrays when no requests exist', () => {
            const mockResponse = {
                pendingRequests: [],
                decidedRequests: [],
                totalDecidedCount: 0,
            };

            service.findAdminOverview().subscribe((result) => {
                expect(result.pendingRequests).toHaveLength(0);
                expect(result.decidedRequests).toHaveLength(0);
                expect(result.totalDecidedCount).toBe(0);
            });

            const req = httpMock.expectOne({ method: 'GET', url: `${adminResourceUrl}/overview?decidedPage=0&decidedPageSize=20` });
            req.flush(mockResponse);
        });

        it('should support pagination parameters', () => {
            const mockResponse = {
                pendingRequests: [],
                decidedRequests: [],
                totalDecidedCount: 50,
            };

            service.findAdminOverview(2, 10).subscribe((result) => {
                expect(result.totalDecidedCount).toBe(50);
            });

            const req = httpMock.expectOne({ method: 'GET', url: `${adminResourceUrl}/overview?decidedPage=2&decidedPageSize=10` });
            req.flush(mockResponse);
        });
    });

    describe('acceptRequest', () => {
        it('should accept a course request', () => {
            const courseRequestId = 1;
            const mockResponse = {
                id: 1,
                title: 'Accepted Course',
                shortName: 'AC001',
                testCourse: false,
                reason: 'Valid reason',
                status: CourseRequestStatus.ACCEPTED,
                createdDate: '2025-01-10T08:00:00Z',
                processedDate: '2025-01-15T12:00:00Z',
                createdCourseId: 50,
                requester: { id: 1, login: 'instructor1' },
            };

            service.acceptRequest(courseRequestId).subscribe((result) => {
                expect(result.id).toBe(1);
                expect(result.status).toBe(CourseRequestStatus.ACCEPTED);
                expect(result.processedDate).toBeDefined();
                expect(result.createdCourseId).toBe(50);
            });

            const req = httpMock.expectOne({ method: 'POST', url: `${adminResourceUrl}/${courseRequestId}/accept` });
            expect(req.request.body).toEqual({});
            req.flush(mockResponse);
        });
    });

    describe('rejectRequest', () => {
        it('should reject a course request with a reason', () => {
            const courseRequestId = 2;
            const rejectionReason = 'Course already exists with a similar name.';
            const mockResponse = {
                id: 2,
                title: 'Rejected Course',
                shortName: 'RC001',
                testCourse: false,
                reason: 'Original reason',
                status: CourseRequestStatus.REJECTED,
                createdDate: '2025-01-10T08:00:00Z',
                processedDate: '2025-01-16T14:00:00Z',
                decisionReason: rejectionReason,
                requester: { id: 2, login: 'instructor2' },
            };

            service.rejectRequest(courseRequestId, rejectionReason).subscribe((result) => {
                expect(result.id).toBe(2);
                expect(result.status).toBe(CourseRequestStatus.REJECTED);
                expect(result.decisionReason).toBe(rejectionReason);
                expect(result.processedDate).toBeDefined();
                expect(result.createdCourseId).toBeUndefined();
            });

            const req = httpMock.expectOne({ method: 'POST', url: `${adminResourceUrl}/${courseRequestId}/reject` });
            expect(req.request.body).toEqual({ reason: rejectionReason });
            req.flush(mockResponse);
        });
    });

    describe('date conversion', () => {
        it('should properly convert dates from server response', () => {
            const mockResponse = {
                pendingRequests: [
                    {
                        id: 1,
                        title: 'Date Test Course',
                        shortName: 'DTC',
                        testCourse: false,
                        reason: 'Testing dates',
                        status: CourseRequestStatus.PENDING,
                        startDate: '2025-02-01T00:00:00Z',
                        endDate: '2025-07-31T23:59:59Z',
                        createdDate: '2025-01-20T15:30:00Z',
                    },
                ],
                decidedRequests: [],
                totalDecidedCount: 0,
            };

            service.findAdminOverview().subscribe((result) => {
                const request = result.pendingRequests[0];
                expect(request.startDate?.isValid()).toBeTrue();
                expect(request.endDate?.isValid()).toBeTrue();
                expect(request.createdDate?.isValid()).toBeTrue();
            });

            const req = httpMock.expectOne({ method: 'GET', url: `${adminResourceUrl}/overview?decidedPage=0&decidedPageSize=20` });
            req.flush(mockResponse);
        });

        it('should handle null/undefined dates gracefully', () => {
            const mockResponse = {
                pendingRequests: [
                    {
                        id: 1,
                        title: 'No Dates Course',
                        shortName: 'NDC',
                        testCourse: false,
                        reason: 'No dates provided',
                        status: CourseRequestStatus.PENDING,
                    },
                ],
                decidedRequests: [],
                totalDecidedCount: 0,
            };

            service.findAdminOverview().subscribe((result) => {
                const request = result.pendingRequests[0];
                expect(request.startDate).toBeUndefined();
                expect(request.endDate).toBeUndefined();
                expect(request.processedDate).toBeUndefined();
            });

            const req = httpMock.expectOne({ method: 'GET', url: `${adminResourceUrl}/overview?decidedPage=0&decidedPageSize=20` });
            req.flush(mockResponse);
        });
    });
});
