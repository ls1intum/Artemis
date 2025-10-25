import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ExamDistributionCapacityDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';

export class MockStudentsRoomDistributionService {
    getRoomData = () => of(this.convertBodyToHttpResponse([]));

    getCapacityData = (roomIds: number[], reserveFactor: number) =>
        of(
            this.convertBodyToHttpResponse({
                combinedDefaultCapacity: 0,
                combinedMaximumCapacity: 0,
            } as ExamDistributionCapacityDTO),
        );

    distributeStudentsAcrossRooms = (courseId: number, examId: number, roomIds: number[], reserveFactor: number, useOnlyDefaultLayouts: boolean) =>
        of(this.convertBodyToHttpResponse());

    private convertBodyToHttpResponse<T>(body?: T): HttpResponse<T> {
        return new HttpResponse<T>({ status: 200, body: body });
    }
}
