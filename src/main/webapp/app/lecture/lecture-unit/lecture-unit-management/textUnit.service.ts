import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';

type EntityResponseType = HttpResponse<TextUnit>;

@Injectable({
    providedIn: 'root',
})
export class TextUnitService {
    private httpClient = inject(HttpClient);
    private lectureUnitService = inject(LectureUnitService);

    private resourceURL = 'api';

    create(textUnit: TextUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<TextUnit>(`${this.resourceURL}/lectures/${lectureId}/text-units`, textUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    findById(textUnitId: number, lectureId: number) {
        return this.httpClient
            .get<TextUnit>(`${this.resourceURL}/lectures/${lectureId}/text-units/${textUnitId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    update(textUnit: TextUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .put<TextUnit>(`${this.resourceURL}/lectures/${lectureId}/text-units`, textUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }
}
