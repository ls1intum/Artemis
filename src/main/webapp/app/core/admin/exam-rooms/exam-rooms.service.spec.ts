/**
 * Vitest tests for ExamRoomsService.
 * Tests the HTTP service for managing exam rooms.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { ExamRoomsService } from 'app/core/admin/exam-rooms/exam-rooms.service';
import { ExamRoomAdminOverviewDTO, ExamRoomDeletionSummaryDTO, ExamRoomUploadInformationDTO } from 'app/core/admin/exam-rooms/exam-rooms.model';

describe('ExamRoomsService', () => {
    setupTestBed({ zoneless: true });

    let service: ExamRoomsService;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [ExamRoomsService, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        service = TestBed.inject(ExamRoomsService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('getAdminOverview', () => {
        it('should send GET request to retrieve admin overview', () => {
            const mockOverview: ExamRoomAdminOverviewDTO = {
                numberOfStoredExamRooms: 10,
                numberOfStoredExamSeats: 500,
                numberOfStoredLayoutStrategies: 3,
                newestUniqueExamRooms: [
                    { roomNumber: 'R101', name: 'Room 101', building: 'Building A', numberOfSeats: 50 },
                    { roomNumber: 'R102', name: 'Room 102', building: 'Building B', numberOfSeats: 75 },
                ],
            };

            service.getAdminOverview().subscribe((response) => {
                expect(response.body).toEqual(mockOverview);
            });

            const req = httpMock.expectOne('api/exam/admin/exam-rooms/admin-overview');
            expect(req.request.method).toBe('GET');
            req.flush(mockOverview);
        });

        it('should handle empty overview response', () => {
            const emptyOverview: ExamRoomAdminOverviewDTO = {
                numberOfStoredExamRooms: 0,
                numberOfStoredExamSeats: 0,
                numberOfStoredLayoutStrategies: 0,
                newestUniqueExamRooms: [],
            };

            service.getAdminOverview().subscribe((response) => {
                expect(response.body).toEqual(emptyOverview);
                expect(response.body?.newestUniqueExamRooms).toHaveLength(0);
            });

            const req = httpMock.expectOne('api/exam/admin/exam-rooms/admin-overview');
            req.flush(emptyOverview);
        });
    });

    describe('uploadRoomDataZipFile', () => {
        it('should send POST request with file as FormData', () => {
            const mockFile = new File(['test content'], 'rooms.zip', { type: 'application/zip' });
            const mockResponse: ExamRoomUploadInformationDTO = {
                uploadedFileName: 'rooms.zip',
                numberOfUploadedRooms: 5,
                numberOfUploadedSeats: 250,
                uploadedRoomNames: ['Room A', 'Room B', 'Room C', 'Room D', 'Room E'],
            };

            service.uploadRoomDataZipFile(mockFile).subscribe((response) => {
                expect(response.body).toEqual(mockResponse);
                expect(response.body?.numberOfUploadedRooms).toBe(5);
            });

            const req = httpMock.expectOne('api/exam/admin/exam-rooms/upload');
            expect(req.request.method).toBe('POST');
            expect(req.request.body instanceof FormData).toBe(true);
            expect(req.request.body.get('file')).toBe(mockFile);
            req.flush(mockResponse);
        });

        it('should handle upload with single room', () => {
            const mockFile = new File(['single room'], 'single-room.zip', { type: 'application/zip' });
            const mockResponse: ExamRoomUploadInformationDTO = {
                uploadedFileName: 'single-room.zip',
                numberOfUploadedRooms: 1,
                numberOfUploadedSeats: 30,
                uploadedRoomNames: ['Single Room'],
            };

            service.uploadRoomDataZipFile(mockFile).subscribe((response) => {
                expect(response.body?.uploadedRoomNames).toHaveLength(1);
            });

            const req = httpMock.expectOne('api/exam/admin/exam-rooms/upload');
            req.flush(mockResponse);
        });
    });

    describe('deleteOutdatedAndUnusedExamRooms', () => {
        it('should send DELETE request to remove outdated rooms', () => {
            const mockResponse: ExamRoomDeletionSummaryDTO = {
                numberOfDeletedExamRooms: 15,
            };

            service.deleteOutdatedAndUnusedExamRooms().subscribe((response) => {
                expect(response.body).toEqual(mockResponse);
                expect(response.body?.numberOfDeletedExamRooms).toBe(15);
            });

            const req = httpMock.expectOne('api/exam/admin/exam-rooms/outdated-and-unused');
            expect(req.request.method).toBe('DELETE');
            req.flush(mockResponse);
        });

        it('should handle deletion when no rooms are deleted', () => {
            const mockResponse: ExamRoomDeletionSummaryDTO = {
                numberOfDeletedExamRooms: 0,
            };

            service.deleteOutdatedAndUnusedExamRooms().subscribe((response) => {
                expect(response.body?.numberOfDeletedExamRooms).toBe(0);
            });

            const req = httpMock.expectOne('api/exam/admin/exam-rooms/outdated-and-unused');
            req.flush(mockResponse);
        });
    });

    describe('baseUrl', () => {
        it('should have correct base URL', () => {
            expect(service.baseUrl).toBe('api/exam/admin/exam-rooms');
        });
    });
});
