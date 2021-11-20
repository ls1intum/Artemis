import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs';
import { map } from 'rxjs/operators';

import { createRequestOption } from 'app/shared/util/request.util';
import { Lecture } from 'app/entities/lecture.model';
import { AccountService } from 'app/core/auth/account.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';

type EntityResponseType = HttpResponse<Lecture>;
type EntityArrayResponseType = HttpResponse<Lecture[]>;

@Injectable({ providedIn: 'root' })
export class LectureService {
    public resourceUrl = SERVER_API_URL + 'api/lectures';

    constructor(protected http: HttpClient, private accountService: AccountService, private lectureUnitService: LectureUnitService) {}

    create(lecture: Lecture): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(lecture);
        return this.http.post<Lecture>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(lecture: Lecture): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(lecture);
        return this.http.put<Lecture>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(lectureId: number): Observable<EntityResponseType> {
        return this.http.get<Lecture>(`${this.resourceUrl}/${lectureId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                this.convertDateFromServer(res);
                this.setAccessRightsLecture(res.body);
                return res;
            }),
        );
    }

    findWithDetails(lectureId: number): Observable<EntityResponseType> {
        return this.http.get<Lecture>(`${this.resourceUrl}/${lectureId}/details`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                if (res.body) {
                    // insert an empty list to avoid additional calls in case the list is empty on the server (because then it would be undefined in the client)
                    if (res.body.posts === undefined) {
                        res.body.posts = [];
                    }
                }
                this.convertDateFromServer(res);
                this.setAccessRightsLecture(res.body);
                return res;
            }),
        );
    }

    /**
     * Fetches the title of the lecture with the given id
     *
     * @param lectureId the id of the lecture
     * @return the title of the lecture in an HttpResponse, or an HttpErrorResponse on error
     */
    getTitle(lectureId: number): Observable<HttpResponse<string>> {
        return this.http.get(`${this.resourceUrl}/${lectureId}/title`, { observe: 'response', responseType: 'text' });
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Lecture[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    findAllByCourseId(courseId: number, withLectureUnits = false): Observable<EntityArrayResponseType> {
        const params = new HttpParams().set('withLectureUnits', withLectureUnits ? '1' : '0');
        return this.http
            .get<Lecture[]>(`api/courses/${courseId}/lectures`, {
                params,
                observe: 'response',
            })
            .pipe(
                map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)),
                map((res: EntityArrayResponseType) => this.setAccessRightsLectureEntityArrayResponseType(res)),
            );
    }

    delete(lectureId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${lectureId}`, { observe: 'response' });
    }

    protected convertDateFromClient(lecture: Lecture): Lecture {
        const copy: Lecture = Object.assign({}, lecture, {
            startDate: lecture.startDate && lecture.startDate.isValid() ? lecture.startDate.toJSON() : undefined,
            endDate: lecture.endDate && lecture.endDate.isValid() ? lecture.endDate.toJSON() : undefined,
        });
        if (copy.lectureUnits) {
            copy.lectureUnits = this.lectureUnitService.convertDateArrayFromClient(copy.lectureUnits);
        }
        if (copy.course) {
            copy.course.exercises = undefined;
            copy.course.lectures = undefined;
        }
        return copy;
    }

    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.startDate = res.body.startDate ? dayjs(res.body.startDate) : undefined;
            res.body.endDate = res.body.endDate ? dayjs(res.body.endDate) : undefined;
            if (res.body.lectureUnits) {
                res.body.lectureUnits = this.lectureUnitService.convertDateArrayFromServerEntity(res.body.lectureUnits);
            }
        }
        return res;
    }

    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.map((lecture: Lecture) => {
                return this.convertDatesForLectureFromServer(lecture);
            });
        }
        return res;
    }

    private setAccessRightsLectureEntityArrayResponseType<ERT extends EntityArrayResponseType>(res: ERT): ERT {
        if (res.body) {
            res.body.forEach((lecture: Lecture) => {
                this.setAccessRightsLecture(lecture);
            });
        }
        return res;
    }

    /**
     * Besides the within the lecture included variables for access rights the access rights of the
     * respective course are set aswell.
     *
     * @param lecture for which the access rights shall be set
     * @return lecture that with set access rights if the course was set
     */
    private setAccessRightsLecture(lecture: Lecture | null) {
        if (lecture) {
            if (lecture.course) {
                this.accountService.setAccessRightsForCourse(lecture.course);
                lecture.isAtLeastEditor = lecture.course.isAtLeastEditor;
                lecture.isAtLeastInstructor = lecture.course.isAtLeastInstructor;
            }
        }
        return lecture;
    }

    public convertDatesForLectureFromServer(lecture?: Lecture) {
        if (lecture) {
            lecture.startDate = lecture.startDate ? dayjs(lecture.startDate) : undefined;
            lecture.endDate = lecture.endDate ? dayjs(lecture.endDate) : undefined;
            if (lecture.lectureUnits) {
                lecture.lectureUnits = this.lectureUnitService.convertDateArrayFromServerEntity(lecture.lectureUnits);
            }
        }
        return lecture;
    }

    public convertDatesForLecturesFromServer(lectures?: Lecture[]) {
        if (lectures) {
            return lectures.map((lecture) => {
                return this.convertDatesForLectureFromServer(lecture)!;
            });
        }
    }
}
