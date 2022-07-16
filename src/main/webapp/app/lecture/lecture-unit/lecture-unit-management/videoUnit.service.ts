import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { VideoUnit } from 'app/entities/lecture-unit/videoUnit.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<VideoUnit>;

@Injectable({
    providedIn: 'root',
})
export class VideoUnitService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient, private lectureUnitService: LectureUnitService) {}

    create(videoUnit: VideoUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .post<VideoUnit>(`${this.resourceURL}/lectures/${lectureId}/video-units`, videoUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    findById(videoUnitId: number, lectureId: number) {
        return this.httpClient
            .get<VideoUnit>(`${this.resourceURL}/lectures/${lectureId}/video-units/${videoUnitId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }

    update(videoUnit: VideoUnit, lectureId: number): Observable<EntityResponseType> {
        return this.httpClient
            .put<VideoUnit>(`${this.resourceURL}/lectures/${lectureId}/video-units`, videoUnit, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.lectureUnitService.convertLectureUnitResponseDatesFromServer(res)));
    }
}
