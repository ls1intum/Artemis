import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';

@Injectable({ providedIn: 'root' })
export class StudentsRoomDistributionService {
    private http = inject(HttpClient);

    readonly baseUrl = 'api/exam';

    /**
     * Send a GET request to retrieve basic data about all exam rooms available for distribution
     */
    getRoomData(): Observable<HttpResponse<RoomForDistributionDTO[]>> {
        return this.http.get<RoomForDistributionDTO[]>(`${this.baseUrl}/rooms/distribution-data`, { observe: 'response' });
    }

    /**
     * Sends a POST request that distributes the exam users of the selected exam across the rooms, which are provided by id
     *
     * @param courseId id of the course
     * @param examId id of the exam
     * @param roomIds ids of the rooms
     */
    distributeStudentsAcrossRooms(courseId: number, examId: number, roomIds: number[]): Observable<HttpResponse<void>> {
        return this.http.post<void>(`${this.baseUrl}/courses/${courseId}/exams/${examId}/distribute-registered-students`, roomIds, { observe: 'response' });
    }
}
