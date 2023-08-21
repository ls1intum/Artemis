import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

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

    getSplitUnitsData(lectureId: number, formData: FormData) {
        return this.httpClient.post(`${this.resourceURL}/lectures/${lectureId}/process-units`, formData, { observe: 'response' });
    }

    createUnits(lectureId: number, formData: FormData) {
        return this.httpClient.post(`${this.resourceURL}/lectures/${lectureId}/attachment-units/split`, formData, { observe: 'response' });
    }
}
