import { SERVER_API_URL } from 'app/app.constants';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { AttachmentUnit } from 'app/entities/lecture-unit/attachmentUnit.model';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<AttachmentUnit>;
type EntityArrayResponseType = HttpResponse<AttachmentUnit[]>;

@Injectable({
    providedIn: 'root',
})
export class AttachmentUnitService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient, private lectureUnitService: LectureUnitService) {}

    create(attachmentUnit: AttachmentUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<AttachmentUnit>(`${this.resourceURL}/lectures/${lectureId}/attachment-units`, attachmentUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertDateFromServer(res)));
    }
}
