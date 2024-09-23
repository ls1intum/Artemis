import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { LectureUnitInformationDTO } from 'app/lecture/lecture-unit/lecture-unit-management/attachment-units/attachment-units.component';

type EntityResponseType = HttpResponse<AttachmentUnit>;

@Injectable({
    providedIn: 'root',
})
export class AttachmentUnitService {
    private resourceURL = 'api';

    constructor(
        private httpClient: HttpClient,
        private lectureUnitService: LectureUnitService,
    ) {}

    findById(attachmentUnitId: number, lectureId: number) {
        return this.httpClient
            .get<AttachmentUnit>(`${this.resourceURL}/lectures/${lectureId}/attachment-units/${attachmentUnitId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    create(formData: FormData, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<AttachmentUnit>(`${this.resourceURL}/lectures/${lectureId}/attachment-units?keepFilename=true`, formData, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    update(lectureId: number, attachmentUnitId: number, formData: FormData, notificationText?: string): Observable<EntityResponseType> {
        return this.httpClient
            .put<AttachmentUnit>(
                `${this.resourceURL}/lectures/${lectureId}/attachment-units/${attachmentUnitId}?keepFilename=true` +
                    (notificationText ? `&notificationText=${notificationText}` : ''),
                formData,
                { observe: 'response' },
            )
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    getSplitUnitsData(lectureId: number, filename: string) {
        return this.httpClient.get<LectureUnitInformationDTO>(`${this.resourceURL}/lectures/${lectureId}/attachment-units/data/${filename}`, { observe: 'response' });
    }

    createUnits(lectureId: number, filename: string, lectureUnitInformation: LectureUnitInformationDTO) {
        return this.httpClient.post(`${this.resourceURL}/lectures/${lectureId}/attachment-units/split/${filename}`, lectureUnitInformation, { observe: 'response' });
    }

    uploadSlidesForProcessing(lectureId: number, file: File) {
        const formData: FormData = new FormData();
        formData.append('file', file);
        return this.httpClient.post<string>(`${this.resourceURL}/lectures/${lectureId}/attachment-units/upload`, formData, { observe: 'response' });
    }

    getSlidesToRemove(lectureId: number, filename: string, keyPhrases: string) {
        const params = new HttpParams().set('commaSeparatedKeyPhrases', keyPhrases);
        return this.httpClient.get<Array<number>>(`${this.resourceURL}/lectures/${lectureId}/attachment-units/slides-to-remove/${filename}`, { params, observe: 'response' });
    }

    /**
     * Retrieve the file associated with a given attachment ID as a Blob object
     *
     * @param courseId The ID of the course that the Attachment Unit belongs to
     * @param attachmentUnitId The ID of the attachment to retrieve
     * @returns An Observable that emits the Blob object of the file when the HTTP request completes successfully
     */
    getAttachmentFile(courseId: number, attachmentUnitId: number): Observable<Blob> {
        return this.httpClient.get(`${this.resourceURL}/files/courses/${courseId}/attachment-units/${attachmentUnitId}`, { responseType: 'blob' });
    }
}
