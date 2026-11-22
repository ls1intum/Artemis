import { Observable, of } from 'rxjs';
import { ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { signal, WritableSignal } from '@angular/core';
import { vi } from 'vitest';

export class MockStudentsRoomDistributionService {
    availableRooms: WritableSignal<RoomForDistributionDTO[]> = signal([]);
    capacityData: WritableSignal<ExamDistributionCapacityDTO> = signal({
        combinedDefaultCapacity: 0,
        combinedMaximumCapacity: 0,
    } as ExamDistributionCapacityDTO);

    loadRoomData = vi.fn(() => {});

    updateCapacityData = vi.fn((roomIds: number[], reserveFactor: number) => {});

    loadRoomsUsedInExam = vi.fn((courseId: number, examId: number): Observable<RoomForDistributionDTO[]> => {
        return of([]);
    });
}
