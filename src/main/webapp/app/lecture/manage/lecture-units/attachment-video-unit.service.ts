import { LectureUnitService } from 'app/lecture/manage/lecture-units/lectureUnit.service';
import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { LectureUnitInformationDTO } from 'app/lecture/manage/lecture-units/attachment-video-units/attachment-video-units.component';

type EntityResponseType = HttpResponse<AttachmentVideoUnit>;

@Injectable({
    providedIn: 'root',
})
export class AttachmentVideoUnitService {
    private httpClient = inject(HttpClient);
    private lectureUnitService = inject(LectureUnitService);

    private resourceURL = 'api/lecture';

    findById(attachmentVideoUnitId: number, lectureId: number) {
        return this.httpClient
            .get<AttachmentVideoUnit>(`${this.resourceURL}/lectures/${lectureId}/attachment-video-units/${attachmentVideoUnitId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    create(formData: FormData, lectureId: number): Observable<EntityResponseType> {
        /** Ngsw-worker is bypassed temporarily to fix Chromium file upload issue
         * See: https://issues.chromium.org/issues/374550348
         **/
        return this.httpClient
            .post<AttachmentVideoUnit>(`${this.resourceURL}/lectures/${lectureId}/attachment-video-units?keepFilename=true`, formData, {
                headers: { 'ngsw-bypass': 'true' },
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    update(lectureId: number, attachmentVideoUnitId: number, formData: FormData, notificationText?: string): Observable<EntityResponseType> {
        /** Ngsw-worker is bypassed temporarily to fix Chromium file upload issue
         * See: https://issues.chromium.org/issues/374550348
         **/
        return this.httpClient
            .put<AttachmentVideoUnit>(
                `${this.resourceURL}/lectures/${lectureId}/attachment-video-units/${attachmentVideoUnitId}?keepFilename=true` +
                    (notificationText ? `&notificationText=${notificationText}` : ''),
                formData,
                { headers: { 'ngsw-bypass': 'true' }, observe: 'response' },
            )
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    getSplitUnitsData(lectureId: number, filename: string) {
        return this.httpClient.get<LectureUnitInformationDTO>(`${this.resourceURL}/lectures/${lectureId}/attachment-video-units/data/${filename}`, { observe: 'response' });
    }

    createUnits(lectureId: number, filename: string, lectureUnitInformation: LectureUnitInformationDTO) {
        return this.httpClient.post(`${this.resourceURL}/lectures/${lectureId}/attachment-video-units/split/${filename}`, lectureUnitInformation, { observe: 'response' });
    }

    uploadSlidesForProcessing(lectureId: number, file: File) {
        const formData: FormData = new FormData();
        formData.append('file', file);
        return this.httpClient.post<string>(`${this.resourceURL}/lectures/${lectureId}/attachment-video-units/upload`, formData, { observe: 'response' });
    }

    getSlidesToRemove(lectureId: number, filename: string, keyPhrases: string) {
        const params = new HttpParams().set('commaSeparatedKeyPhrases', keyPhrases);
        return this.httpClient.get<Array<number>>(`${this.resourceURL}/lectures/${lectureId}/attachment-video-units/slides-to-remove/${filename}`, { params, observe: 'response' });
    }

    /**
     * Retrieve the file associated with a given attachment ID as a Blob object
     *
     * @param courseId The ID of the course that the Attachment Unit belongs to
     * @param attachmentVideoUnitId The ID of the attachment to retrieve
     * @returns An Observable that emits the Blob object of the file when the HTTP request completes successfully
     */
    getAttachmentFile(courseId: number, attachmentVideoUnitId: number): Observable<Blob> {
        return this.httpClient.get(`api/core/files/courses/${courseId}/attachment-units/${attachmentVideoUnitId}`, { responseType: 'blob' });
    }
}
