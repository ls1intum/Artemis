import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { ExamDistributionCapacityDTO, RoomForDistributionDTO, SeatsOfExamRoomDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { signal, WritableSignal } from '@angular/core';

export class MockStudentsRoomDistributionService {
    availableRooms: WritableSignal<RoomForDistributionDTO[]> = signal([]);
    capacityData: WritableSignal<ExamDistributionCapacityDTO> = signal({
        combinedDefaultCapacity: 0,
        combinedMaximumCapacity: 0,
    } as ExamDistributionCapacityDTO);

    loadRoomData = jest.fn(() => {
        this.availableRooms.set([]);
    });

    updateCapacityData = jest.fn((roomIds: number[], reserveFactor: number) => {
        this.capacityData.set({
            combinedDefaultCapacity: 0,
            combinedMaximumCapacity: 0,
        } as ExamDistributionCapacityDTO);
    });

    distributeStudentsAcrossRooms = jest.fn((courseId: number, examId: number, roomIds: number[], reserveFactor: number, useOnlyDefaultLayouts: boolean) => {
        return of(this.convertBodyToHttpResponse());
    });

    loadRoomsUsedInExam = jest.fn((courseId: number, examId: number): Observable<RoomForDistributionDTO[]> => {
        return of([]);
    });

    loadSeatsOfExamRoom = jest.fn((examRoomId: number): Observable<SeatsOfExamRoomDTO> => {
        return of({ seats: [] } as SeatsOfExamRoomDTO);
    });

    reseatStudent = jest.fn((courseId: number, examId: number, examUserId: number, newRoom: string, newSeat?: string): Observable<void> => {
        return of();
    });

    private convertBodyToHttpResponse<T>(body?: T): HttpResponse<T> {
        return new HttpResponse<T>({ status: 200, body: body });
    }
}
