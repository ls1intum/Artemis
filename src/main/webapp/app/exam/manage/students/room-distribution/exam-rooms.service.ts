import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ExamRoomDeletionSummaryDTO, ExamRoomOverviewDTO, ExamRoomUploadInformationDTO } from 'app/exam/manage/students/room-distribution/exam-rooms.model';

@Service()
export class ExamRoomsService {
    private http = inject(HttpClient);

    readonly baseUrl = 'api/exam/rooms';

    /**
     * Send a GET request to retrieve an overview over the stored exam rooms.
     * Instructors reach the overview in the context of an exam of their course; without an exam only administrators may load it.
     *
     * @param courseId the id of the course of the exam, if the overview is opened for an exam
     * @param examId the id of the exam, if the overview is opened for an exam
     */
    getRoomOverview(courseId?: number, examId?: number): Observable<HttpResponse<ExamRoomOverviewDTO>> {
        if (courseId !== undefined && examId !== undefined) {
            const params = new HttpParams().set('courseId', courseId).set('examId', examId);
            return this.http.get<ExamRoomOverviewDTO>(`${this.baseUrl}/overview`, { params, observe: 'response' });
        }
        return this.http.get<ExamRoomOverviewDTO>(`${this.baseUrl}/admin/overview`, { observe: 'response' });
    }

    /**
     * Send a POST request that uploads exam rooms to be parsed and stored in the DB
     *
     * @param file A ZIP file containing exam room data
     */
    uploadRoomDataZipFile(file: File): Observable<HttpResponse<ExamRoomUploadInformationDTO>> {
        const formData = new FormData();
        formData.append('file', file);

        return this.http.post<ExamRoomUploadInformationDTO>(`${this.baseUrl}/admin/upload`, formData, { observe: 'response' });
    }

    /**
     * Send a DELETE request to remove all outdated and unused exam rooms. Besides keeping the newest version of
     * each room, this also keeps all versions that are (still) being connected to an exam.
     */
    deleteOutdatedAndUnusedExamRooms(): Observable<HttpResponse<ExamRoomDeletionSummaryDTO>> {
        return this.http.delete<ExamRoomDeletionSummaryDTO>(`${this.baseUrl}/admin/outdated-and-unused`, { observe: 'response' });
    }
}
