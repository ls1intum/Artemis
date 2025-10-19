import { AttachmentVideoUnit } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { LectureUnitInformationDTO } from 'app/lecture/manage/lecture-units/attachment-video-units/attachment-video-units.component';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';

type EntityResponseType = HttpResponse<AttachmentVideoUnit>;

@Injectable({
    providedIn: 'root',
})
export class AttachmentVideoUnitService {
    private httpClient = inject(HttpClient);
    private lectureUnitService = inject(LectureUnitService);
    private alertService = inject(AlertService);

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

    /**
     * Update only the student version of an attachment video unit's attachment
     * @param lectureId the id of the lecture
     * @param attachmentVideoUnitId the id of the attachment video unit
     * @param formData the FormData containing only the student version file
     */
    updateStudentVersion(lectureId: number, attachmentVideoUnitId: number, formData: FormData): Observable<EntityResponseType> {
        return this.httpClient
            .put<AttachmentVideoUnit>(`${this.resourceURL}/lectures/${lectureId}/attachment-video-units/${attachmentVideoUnitId}/student-version`, formData, {
                headers: { 'ngsw-bypass': 'true' },
                observe: 'response',
            })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    startTranscription(lectureId: number, lectureUnitId: number, videoUrl: string): Observable<void> {
        const body = {
            videoUrl,
            lectureId,
            lectureUnitId,
        };

        return this.httpClient
            .post(`/api/lecture/nebula/${lectureId}/lecture-unit/${lectureUnitId}/transcriber`, body, {
                observe: 'response',
                responseType: 'text',
            })
            .pipe(
                map(() => {
                    this.alertService.success('artemisApp.lectureUnit.attachmentVideoUnit.transcription.started');
                }),
                catchError((error: any) => {
                    this.alertService.error('artemisApp.lectureUnit.attachmentVideoUnit.transcription.error');
                    return of();
                }),
            );
    }
}
