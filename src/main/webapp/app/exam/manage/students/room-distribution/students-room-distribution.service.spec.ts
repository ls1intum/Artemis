import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { StudentsRoomDistributionService } from 'app/exam/manage/students/room-distribution/students-room-distribution.service';
import { ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { HttpResponse } from '@angular/common/http';

describe('StudentsRoomDistributionService', () => {
    let service: StudentsRoomDistributionService;
    let httpMock: HttpTestingController;

    const BASE_URL = 'api/exam';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [StudentsRoomDistributionService],
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

            service.loadRoomData();

            const req = httpMock.expectOne(`${BASE_URL}/rooms/distribution-data`);
            expect(req.request.method).toBe('GET');
            req.flush(mockRooms);

            expect(service.availableRooms()).toEqual(mockRooms);
        });

        it('should reset availableRooms to empty array on error', () => {
            service.loadRoomData();

            const req = httpMock.expectOne(`${BASE_URL}/rooms/distribution-data`);
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
            service.updateCapacityData([], 0.2);
            expect(service.capacityData()).toEqual({
                combinedDefaultCapacity: 0,
                combinedMaximumCapacity: 0,
            });
        });

        it('should update capacityData on successful GET', () => {
            const roomIds = [1, 2];
            const reserveFactor = 0.1;

            service.updateCapacityData(roomIds, reserveFactor);

            const req = httpMock.expectOne((r) => r.url === `${BASE_URL}/rooms/distribution-capacities` && r.method === 'GET');
            expect(req.request.params.getAll('examRoomIds')).toEqual(['1', '2']);
            expect(req.request.params.get('reserveFactor')).toBe(reserveFactor.toString());

            req.flush(mockCapacity);

            expect(service.capacityData()).toEqual(mockCapacity);
        });

        it('should reset capacityData on error', () => {
            const roomIds = [1];
            service.updateCapacityData(roomIds, 0.1);

            const req = httpMock.expectOne((r) => r.url === `${BASE_URL}/rooms/distribution-capacities` && r.method === 'GET');
            req.error(new ProgressEvent('error'));

            expect(service.capacityData()).toEqual({
                combinedDefaultCapacity: 0,
                combinedMaximumCapacity: 0,
            });
        });
    });

    describe('distributeStudentsAcrossRooms', () => {
        it('should send POST request and return HttpResponse<void>', () => {
            const courseId = 42;
            const examId = 7;
            const roomIds = [1, 2];
            const reserveFactor = 0.15;
            const useOnlyDefaultLayouts = true;

            let actualResponse: HttpResponse<void> | undefined;
            service.distributeStudentsAcrossRooms(courseId, examId, roomIds, reserveFactor, useOnlyDefaultLayouts).subscribe((resp) => (actualResponse = resp));

            const req = httpMock.expectOne((r) => r.url === `${BASE_URL}/courses/${courseId}/exams/${examId}/distribute-registered-students` && r.method === 'POST');

            expect(req.request.body).toEqual(roomIds);
            expect(req.request.params.get('reserveFactor')).toBe(reserveFactor.toString());
            expect(req.request.params.get('useOnlyDefaultLayouts')).toBe('true');

            req.flush(null, { status: 200, statusText: 'OK' });

            expect(actualResponse?.status).toBe(200);
        });
    });
});
