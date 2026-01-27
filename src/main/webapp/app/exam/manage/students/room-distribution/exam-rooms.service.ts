import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExamRoomDeletionSummaryDTO, ExamRoomOverviewDTO, ExamRoomUploadInformationDTO } from 'app/exam/manage/students/room-distribution/exam-rooms.model';

@Injectable({ providedIn: 'root' })
export class ExamRoomsService {
    private http = inject(HttpClient);

    readonly baseUrl = 'api/exam/rooms';

    /**
     * Send a GET request to retrieve an overview over the stored exam rooms
     */
    getRoomOverview(): Observable<HttpResponse<ExamRoomOverviewDTO>> {
        return this.http.get<ExamRoomOverviewDTO>(`${this.baseUrl}/overview`, { observe: 'response' });
    }

    /**
     * Send a POST request that uploads exam rooms to be parsed and stored in the DB
     *
     * @param file A ZIP file containing exam room data
     */
    uploadRoomDataZipFile(file: File): Observable<HttpResponse<ExamRoomUploadInformationDTO>> {
        const formData = new FormData();
        formData.append('file', file);

        return this.http.post<ExamRoomUploadInformationDTO>(`${this.baseUrl}/upload`, formData, { observe: 'response' });
    }

    /**
     * Send a DELETE request to remove all outdated and unused exam rooms. Besides keeping the newest version of
     * each room, this also keeps all versions that are (still) being connected to an exam.
     */
    deleteOutdatedAndUnusedExamRooms(): Observable<HttpResponse<ExamRoomDeletionSummaryDTO>> {
        return this.http.delete<ExamRoomDeletionSummaryDTO>(`${this.baseUrl}/outdated-and-unused`, { observe: 'response' });
    }
}
