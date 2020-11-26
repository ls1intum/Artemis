import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared/util/request-util';
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
        return this.http
            .post<Lecture>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(lecture: Lecture): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(lecture);
        return this.http
            .put<Lecture>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(lectureId: number): Observable<EntityResponseType> {
        return this.http
            .get<Lecture>(`${this.resourceUrl}/${lectureId}`, { observe: 'response' })
            .map((res: EntityResponseType) => {
                if (res.body) {
                    // insert an empty list to avoid additional calls in case the list is empty on the server (because then it would be undefined in the client)
                    if (res.body.studentQuestions === undefined) {
                        res.body.studentQuestions = [];
                    }
                }
                return res;
            })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Lecture[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    findAllByCourseId(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Lecture[]>(`api/courses/${courseId}/lectures`, { observe: 'response' })
            .pipe(
                map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)),
                map((res: EntityArrayResponseType) => this.checkPermission(res)),
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
            res.body.startDate = res.body.startDate ? moment(res.body.startDate) : undefined;
            res.body.endDate = res.body.endDate ? moment(res.body.endDate) : undefined;
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

    private checkPermission<ERT extends EntityArrayResponseType>(res: ERT): ERT {
        if (res.body) {
            res.body.forEach((lecture: Lecture) => {
                if (lecture.course) {
                    lecture.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(lecture.course);
                }
            });
        }
        return res;
    }

    public convertDatesForLectureFromServer(lecture?: Lecture) {
        if (lecture) {
            lecture.startDate = lecture.startDate ? moment(lecture.startDate) : undefined;
            lecture.endDate = lecture.endDate ? moment(lecture.endDate) : undefined;
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
