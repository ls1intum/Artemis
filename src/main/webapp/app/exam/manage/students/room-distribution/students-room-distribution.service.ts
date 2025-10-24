import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExamDistributionCapacityDTO, RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';

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
     * Sends a POST request to retrieve information about the capacities of the chosen rooms
     *
     * @param roomIds ids of the rooms
     * @param reserveFactor percentage of seats that should be left empty
     */
    getCapacityData(roomIds: number[], reserveFactor: number): Observable<HttpResponse<ExamDistributionCapacityDTO>> {
        const requestUrl = `${this.baseUrl}/rooms/distribution-capacities`;
        const params = new HttpParams().set('reserveFactor', reserveFactor);

        return this.http.post<ExamDistributionCapacityDTO>(requestUrl, roomIds, { params, observe: 'response' });
    }

    /**
     * Sends a POST request that distributes the exam users of the selected exam across the rooms, which are provided by id
     *
     * @param courseId id of the course
     * @param examId id of the exam
     * @param roomIds ids of the rooms
     * @param reserveFactor percentage of seats that should be left empty
     * @param useOnlyDefaultLayouts defines if only default layouts should be used for distribution
     */
    distributeStudentsAcrossRooms(courseId: number, examId: number, roomIds: number[], reserveFactor: number, useOnlyDefaultLayouts: boolean): Observable<HttpResponse<void>> {
        const requestUrl = `${this.baseUrl}/courses/${courseId}/exams/${examId}/distribute-registered-students`;
        const params = new HttpParams().set('reserveFactor', reserveFactor).set('useOnlyDefaultLayouts', useOnlyDefaultLayouts);

        return this.http.post<void>(requestUrl, roomIds, { params, observe: 'response' });
    }
}
