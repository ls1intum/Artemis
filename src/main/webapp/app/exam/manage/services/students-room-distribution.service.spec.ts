import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { StudentsRoomDistributionService } from 'app/exam/manage/services/students-room-distribution.service';
import { ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { HttpErrorResponse } from '@angular/common/http';

describe('StudentsRoomDistributionService', () => {
    let service: StudentsRoomDistributionService;
    let httpMock: HttpTestingController;

    const BASE_URL = '/api/exam';

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [StudentsRoomDistributionService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(StudentsRoomDistributionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should initialize signals with default values', () => {
        expect(service.availableRooms()).toEqual([]);
        expect(service.capacityData()).toEqual({
            combinedDefaultCapacity: 0,
            combinedMaximumCapacity: 0,
        });
    });

    describe('loadRoomData', () => {
        it('should update availableRooms on successful GET', () => {
            const mockRooms: RoomForDistributionDTO[] = [
                { id: 1, name: 'Room 1', roomNumber: 'R1', building: 'B1' },
                { id: 2, name: 'Room 2', roomNumber: 'R2', building: 'B2' },
            ];

            service.loadRoomData(42, 7);

            const req = httpMock.expectOne(`${BASE_URL}/courses/42/exams/7/rooms/distribution-data`);
            expect(req.request.method).toBe('GET');
            req.flush(mockRooms);

            expect(service.availableRooms()).toEqual(mockRooms);
        });

        it('should reset availableRooms to empty array on error', () => {
            service.loadRoomData(42, 7);

            const req = httpMock.expectOne(`${BASE_URL}/courses/42/exams/7/rooms/distribution-data`);
            req.error(new ProgressEvent('error'));

            expect(service.availableRooms()).toEqual([]);
        });
    });

    describe('updateCapacityData', () => {
        const mockCapacity: ExamDistributionCapacityDTO = {
            combinedDefaultCapacity: 50,
            combinedMaximumCapacity: 80,
        };

        it('should immediately reset capacity data if no room IDs are provided', () => {
            service.updateCapacityData(42, 7, [], 0.2);
            expect(service.capacityData()).toEqual({
                combinedDefaultCapacity: 0,
                combinedMaximumCapacity: 0,
            });
        });

        it('should update capacityData on successful GET', () => {
            const roomIds = [1, 2];
            const reserveFactor = 0.1;

            service.updateCapacityData(42, 7, roomIds, reserveFactor);

            const req = httpMock.expectOne({ method: 'GET', url: `${BASE_URL}/courses/42/exams/7/rooms/distribution-capacities?reserveFactor=0.1&examRoomIds=1&examRoomIds=2` });

            req.flush(mockCapacity);

            expect(service.capacityData()).toEqual(mockCapacity);
        });

        it('should reset capacityData on error', () => {
            const roomIds = [1];
            service.updateCapacityData(42, 7, roomIds, 0.1);

            const req = httpMock.expectOne(`${BASE_URL}/courses/42/exams/7/rooms/distribution-capacities?reserveFactor=0.1&examRoomIds=1`);
            req.error(new ProgressEvent('error'));

            expect(service.capacityData()).toEqual({
                combinedDefaultCapacity: 0,
                combinedMaximumCapacity: 0,
            });
        });
    });

    it.each([
        [undefined, 7],
        [42, undefined],
        [undefined, undefined],
    ])('should return no rooms without course %s or exam %s without an HTTP request', (courseId, examId) => {
        let rooms: RoomForDistributionDTO[] | undefined;
        service.loadRoomsUsedInExam(courseId, examId).subscribe((response) => (rooms = response));
        expect(rooms).toEqual([]);
        httpMock.expectNone(() => true);
    });

    it('should propagate the HTTP error of the used rooms', () => {
        let actualError: HttpErrorResponse | undefined;
        let emitted = false;
        service.loadRoomsUsedInExam(42, 7).subscribe({
            next: () => (emitted = true),
            error: (error: HttpErrorResponse) => (actualError = error),
        });
        const request = httpMock.expectOne(`${BASE_URL}/courses/42/exams/7/rooms-used`);
        expect(request.request.method).toBe('GET');
        const errorBody = { message: 'distribution unavailable' };
        request.flush(errorBody, { status: 503, statusText: 'Service Unavailable' });
        expect(emitted).toBe(false);
        expect(actualError).toBeInstanceOf(HttpErrorResponse);
        expect(actualError?.status).toBe(503);
        expect(actualError?.error).toEqual(errorBody);
    });

    describe('loadRoomsUsedInExam', () => {
        it('should send GET request and return rooms', () => {
            const courseId = 5;
            const examId = 10;
            const mockRooms: RoomForDistributionDTO[] = [
                { id: 1, name: 'A', roomNumber: 'R1', building: 'H1' },
                { id: 2, name: 'B', roomNumber: 'R2', building: 'H2' },
            ];

            let actualRooms: RoomForDistributionDTO[] | undefined;

            service.loadRoomsUsedInExam(courseId, examId).subscribe((rooms) => (actualRooms = rooms));

            const req = httpMock.expectOne(`${BASE_URL}/courses/${courseId}/exams/${examId}/rooms-used`);

            expect(req.request.method).toBe('GET');
            req.flush(mockRooms);

            expect(actualRooms).toEqual(mockRooms);
        });
    });
});
