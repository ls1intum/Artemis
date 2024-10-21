import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { createRequestOption } from 'app/shared/util/request.util';
import { Lecture } from 'app/entities/lecture.model';
import { AccountService } from 'app/core/auth/account.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { IngestionState } from 'app/entities/lecture-unit/attachmentUnit.model';

type EntityResponseType = HttpResponse<Lecture>;
type EntityArrayResponseType = HttpResponse<Lecture[]>;

@Injectable({ providedIn: 'root' })
export class LectureService {
    public resourceUrl = 'api/lectures';

    constructor(
        protected http: HttpClient,
        private accountService: AccountService,
        private lectureUnitService: LectureUnitService,
        private entityTitleService: EntityTitleService,
    ) {}

    create(lecture: Lecture): Observable<EntityResponseType> {
        const copy = this.convertLectureDatesFromClient(lecture);
        return this.http.post<Lecture>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertLectureResponseDatesFromServer(res)));
    }

    update(lecture: Lecture): Observable<EntityResponseType> {
        const copy = this.convertLectureDatesFromClient(lecture);
        return this.http.put<Lecture>(this.resourceUrl, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertLectureResponseDatesFromServer(res)));
    }

    find(lectureId: number): Observable<EntityResponseType> {
        return this.http.get<Lecture>(`${this.resourceUrl}/${lectureId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                this.convertLectureResponseDatesFromServer(res);
                this.setAccessRightsLecture(res.body);
                this.sendTitlesToEntityTitleService(res?.body);
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
                this.convertLectureResponseDatesFromServer(res);
                this.setAccessRightsLecture(res.body);
                this.sendTitlesToEntityTitleService(res?.body);
                return res;
            }),
        );
    }

    findWithDetailsWithSlides(lectureId: number): Observable<EntityResponseType> {
        return this.http.get<Lecture>(`${this.resourceUrl}/${lectureId}/details-with-slides`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                if (res.body) {
                    // insert an empty list to avoid additional calls in case the list is empty on the server (because then it would be undefined in the client)
                    if (res.body.posts === undefined) {
                        res.body.posts = [];
                    }
                }
                this.convertLectureResponseDatesFromServer(res);
                this.setAccessRightsLecture(res.body);
                this.sendTitlesToEntityTitleService(res?.body);
                return res;
            }),
        );
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http.get<Lecture[]>(this.resourceUrl, { params: options, observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => this.convertLectureArrayResponseDatesFromServer(res)),
            tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
        );
    }

    findAllByCourseId(courseId: number, withLectureUnits = false): Observable<EntityArrayResponseType> {
        const params = new HttpParams().set('withLectureUnits', withLectureUnits ? '1' : '0');
        return this.http
            .get<Lecture[]>(`api/courses/${courseId}/lectures`, {
                params,
                observe: 'response',
            })
            .pipe(
                map((res: EntityArrayResponseType) => this.convertLectureArrayResponseDatesFromServer(res)),
                map((res: EntityArrayResponseType) => this.setAccessRightsLectureEntityArrayResponseType(res)),
                tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
            );
    }

    findAllByCourseIdWithSlides(courseId: number): Observable<EntityArrayResponseType> {
        return this.http
            .get<Lecture[]>(`api/courses/${courseId}/lectures-with-slides`, {
                observe: 'response',
            })
            .pipe(
                map((res: EntityArrayResponseType) => this.convertLectureArrayResponseDatesFromServer(res)),
                map((res: EntityArrayResponseType) => this.setAccessRightsLectureEntityArrayResponseType(res)),
                tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
            );
    }
    /**
     * triggers the ingestion of All the lectures inside the course specified or one lecture inside of the course
     *
     * @param courseId Course containing the lecture(s)
     * @param lectureId The lecture to be ingested in pyris
     */
    ingestLecturesInPyris(courseId: number, lectureId?: number): Observable<HttpResponse<boolean>> {
        let params = new HttpParams();
        if (lectureId !== undefined) {
            params = params.set('lectureId', lectureId.toString());
        }

        return this.http.post<boolean>(`api/courses/${courseId}/ingest`, null, {
            params: params,
            observe: 'response',
        });
    }
    /**
     * Fetch the ingestion state of all the lectures inside the course specified
     * @param courseId
     */
    getIngestionState(courseId: number): Observable<HttpResponse<Record<number, IngestionState>>> {
        return this.http.get<Record<number, IngestionState>>(`api/iris/courses/${courseId}/lectures/ingestion-state`, { observe: 'response' });
    }
    /**
     * Clones and imports the lecture to the course
     *
     * @param courseId Course to import the lecture into
     * @param lectureId Lecture to be cloned and imported
     */
    import(courseId: number, lectureId: number): Observable<EntityResponseType> {
        const params = new HttpParams().set('courseId', courseId);
        return this.http
            .post<Lecture>(`${this.resourceUrl}/import/${lectureId}`, null, {
                params,
                observe: 'response',
            })
            .pipe(
                map((res: EntityResponseType) => {
                    this.convertLectureResponseDatesFromServer(res);
                    this.setAccessRightsLecture(res.body);
                    this.sendTitlesToEntityTitleService(res?.body);
                    return res;
                }),
            );
    }

    delete(lectureId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${lectureId}`, { observe: 'response' });
    }

    protected convertLectureDatesFromClient(lecture: Lecture): Lecture {
        const copy: Lecture = Object.assign({}, lecture, {
            startDate: convertDateFromClient(lecture.startDate),
            endDate: convertDateFromClient(lecture.endDate),
            visibleDate: convertDateFromClient(lecture.visibleDate),
        });
        if (copy.lectureUnits) {
            copy.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromClient(copy.lectureUnits);
        }
        if (copy.course) {
            copy.course.exercises = undefined;
            copy.course.lectures = undefined;
        }
        return copy;
    }

    protected convertLectureResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.startDate = convertDateFromServer(res.body.startDate);
            res.body.endDate = convertDateFromServer(res.body.endDate);
            res.body.visibleDate = convertDateFromServer(res.body.visibleDate);
            if (res.body.lectureUnits) {
                res.body.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromServer(res.body.lectureUnits);
            }
        }
        return res;
    }

    protected convertLectureArrayResponseDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.map((lecture: Lecture) => {
                return this.convertLectureDatesFromServer(lecture);
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
     * respective course are set as well.
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

    public convertLectureDatesFromServer(lecture?: Lecture) {
        if (lecture) {
            lecture.startDate = convertDateFromServer(lecture.startDate);
            lecture.endDate = convertDateFromServer(lecture.endDate);
            lecture.visibleDate = convertDateFromServer(lecture.visibleDate);
            if (lecture.lectureUnits) {
                lecture.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromServer(lecture.lectureUnits);
            }
        }
        return lecture;
    }

    public convertLectureArrayDatesFromServer(lectures?: Lecture[]) {
        if (lectures) {
            return lectures.map((lecture) => {
                return this.convertLectureDatesFromServer(lecture)!;
            });
        }
    }

    private sendTitlesToEntityTitleService(lecture: Lecture | undefined | null) {
        this.entityTitleService.setTitle(EntityType.LECTURE, [lecture?.id], lecture?.title);
    }
}
