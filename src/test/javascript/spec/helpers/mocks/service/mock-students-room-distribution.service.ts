import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { ExamDistributionCapacityDTO, RoomForDistributionDTO, SeatsOfExamRoomDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
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

    distributeStudentsAcrossRooms = vi.fn((courseId: number, examId: number, roomIds: number[], reserveFactor: number, useOnlyDefaultLayouts: boolean) => {
        return of(this.convertBodyToHttpResponse());
    });

    loadRoomsUsedInExam = vi.fn((courseId: number, examId: number): Observable<RoomForDistributionDTO[]> => {
        return of([]);
    });

    loadSeatsOfExamRoom = vi.fn((examRoomId: number): Observable<SeatsOfExamRoomDTO> => {
        return of({ seats: [] } as SeatsOfExamRoomDTO);
    });

    reseatStudent = vi.fn((courseId: number, examId: number, examUserId: number, newRoom: string, newSeat?: string): Observable<void> => {
        return of();
    });

    private convertBodyToHttpResponse<T>(body?: T): HttpResponse<T> {
        return new HttpResponse<T>({ status: 200, body: body });
    }
}
