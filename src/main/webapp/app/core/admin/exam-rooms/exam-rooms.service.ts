import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExamRoomAdminOverviewDTO, ExamRoomDeletionSummaryDTO, ExamRoomUploadInformationDTO } from 'app/core/admin/exam-rooms/exam-rooms.model';

@Injectable({ providedIn: 'root' })
export class ExamRoomsService {
    private http = inject(HttpClient);

    readonly baseUrl = 'api/exam/admin/exam-rooms';

    /**
     * Send a GET request to retrieve an overview over the stored exam rooms
     */
    getAdminOverview(): Observable<ExamRoomAdminOverviewDTO> {
        return this.http.get<ExamRoomAdminOverviewDTO>(`${this.baseUrl}/admin-overview`);
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
     * Send a DELETE request to remove everything related to exam rooms.
     * This also removes exam rooms that are connected to an exam.
     */
    deleteAllExamRooms(): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.baseUrl}`, { observe: 'response' });
    }

    /**
     * Send a DELETE request to remove all outdated and unused exam rooms. Besides keeping the newest version of
     * each room, this also keeps all versions that are (still) being connected to an exam.
     */
    deleteOutdatedAndUnusedExamRooms(): Observable<HttpResponse<ExamRoomDeletionSummaryDTO>> {
        return this.http.delete<ExamRoomDeletionSummaryDTO>(`${this.baseUrl}/outdated-and-unused`, { observe: 'response' });
    }
}
