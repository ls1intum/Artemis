import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';

export class MockStudentsRoomDistributionService {
    getRoomData = () =>
        of(
            this.convertBodyToHttpResponse([
                { id: 1, number: '1', name: 'one', building: 'AA' },
                { id: 2, number: '2', alternativeNumber: '002', name: 'two', building: 'AA' },
                {
                    id: 3,
                    number: '3',
                    alternativeNumber: '003',
                    name: 'three',
                    alternativeName: 'threeee',
                    building: 'AA',
                },
            ] as RoomForDistributionDTO[]),
        );

    getCapacityData = (roomIds: number[], reserveFactor: number) =>
        of(this.convertBodyToHttpResponse({ combinedDefaultCapacity: 0, combinedMaximumCapacity: 0 } as ExamDistributionCapacityDTO));

    distributeStudentsAcrossRooms = (courseId: number, examId: number, roomIds: number[], reserveFactor: number, useOnlyDefaultLayouts: boolean) =>
        of(this.convertBodyToHttpResponse<void>());

    private convertBodyToHttpResponse<T>(body?: T): HttpResponse<T> {
        return new HttpResponse<T>({ status: 200, body: body });
    }
}
