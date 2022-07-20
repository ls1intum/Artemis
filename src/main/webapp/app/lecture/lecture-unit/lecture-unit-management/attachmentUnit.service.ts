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
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient, private lectureUnitService: LectureUnitService) {}

    findById(attachmentUnitId: number, lectureId: number) {
        return this.httpClient
            .get<AttachmentUnit>(`${this.resourceURL}/lectures/${lectureId}/attachment-units/${attachmentUnitId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    create(attachmentUnit: AttachmentUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<AttachmentUnit>(`${this.resourceURL}/lectures/${lectureId}/attachment-units`, attachmentUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    update(attachmentUnit: AttachmentUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .put<AttachmentUnit>(`${this.resourceURL}/lectures/${lectureId}/attachment-units`, attachmentUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }
}
