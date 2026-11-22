import type { ExamRoom } from 'app/openapi/model/exam-room';

export interface ExamRoomWithCapacity extends ExamRoom {
    defaultCapacity: number;
    maxCapacity: number;
}

export interface ExamRoomOverviewNumberOfAvailable {
    examRooms: number;
    examSeats: number;
}
