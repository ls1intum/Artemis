import { AlertService } from 'app/core/util/alert.service';
import { LectureUnit, LectureUnitForLearningPathNodeDetailsDTO, LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/entities/lecture.model';
import { LectureUnitCompletionEvent } from 'app/overview/course-lectures/course-lecture-details.component';
import { onError } from 'app/shared/util/global.utils';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Injectable } from '@angular/core';
import { AttachmentUnit, IngestionState } from 'app/entities/lecture-unit/attachmentUnit.model';
import { AttachmentService } from 'app/lecture/attachment.service';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';

type EntityArrayResponseType = HttpResponse<LectureUnit[]>;

@Injectable({
    providedIn: 'root',
})
export class LectureUnitService {
    private resourceURL = 'api';

    constructor(
        private httpClient: HttpClient,
        private attachmentService: AttachmentService,
        private alertService: AlertService,
    ) {}

    updateOrder(lectureId: number, lectureUnits: LectureUnit[]): Observable<HttpResponse<LectureUnit[]>> {
        // Send an ordered list of ids of the lecture units
        // This also overcomes circular structure issues with participations and categories in exercise units
        const lectureUnitIds = lectureUnits.map((lectureUnit) => lectureUnit.id);
        return this.httpClient
            .put<LectureUnit[]>(`${this.resourceURL}/lectures/${lectureId}/lecture-units-order`, lectureUnitIds, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertLectureUnitResponseArrayDatesFromServer(res)));
    }

    delete(lectureUnitId: number, lectureId: number) {
        return this.httpClient.delete(`${this.resourceURL}/lectures/${lectureId}/lecture-units/${lectureUnitId}`, { observe: 'response' });
    }

    completeLectureUnit(lecture: Lecture, event: LectureUnitCompletionEvent): void {
        if (event.lectureUnit.visibleToStudents && event.lectureUnit.completed !== event.completed) {
            this.setCompletion(event.lectureUnit.id!, lecture.id!, event.completed).subscribe({
                next: () => {
                    event.lectureUnit.completed = event.completed;
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        }
    }

    setCompletion(lectureUnitId: number, lectureId: number, completed: boolean): Observable<HttpResponse<void>> {
        const params = new HttpParams().set('completed', completed.toString());
        return this.httpClient.post<void>(`${this.resourceURL}/lectures/${lectureId}/lecture-units/${lectureUnitId}/completion`, null, {
            params,
            observe: 'response',
        });
    }

    convertLectureUnitDatesFromClient<T extends LectureUnit>(lectureUnit: T): T {
        if (lectureUnit.type === LectureUnitType.ATTACHMENT) {
            if ((<AttachmentUnit>lectureUnit).attachment) {
                (<AttachmentUnit>lectureUnit).attachment = this.attachmentService.convertAttachmentDatesFromClient((<AttachmentUnit>lectureUnit).attachment!);
                return lectureUnit;
            }
        } else if (lectureUnit.type === LectureUnitType.EXERCISE) {
            if ((<ExerciseUnit>lectureUnit).exercise) {
                (<ExerciseUnit>lectureUnit).exercise = ExerciseService.convertExerciseDatesFromClient((<ExerciseUnit>lectureUnit).exercise!);
                (<ExerciseUnit>lectureUnit).exercise!.categories = ExerciseService.stringifyExerciseCategories((<ExerciseUnit>lectureUnit).exercise!);
                return lectureUnit;
            }
        }
        return Object.assign({}, lectureUnit, {
            releaseDate: convertDateFromClient(lectureUnit.releaseDate),
        });
    }

    convertLectureUnitArrayDatesFromClient<T extends LectureUnit>(lectureUnits: T[]): T[] {
        if (lectureUnits?.length) {
            for (let _i = 0; _i < lectureUnits.length; _i++) {
                lectureUnits[_i] = this.convertLectureUnitDatesFromClient(lectureUnits[_i]);
            }
        }
        return lectureUnits;
    }

    convertLectureUnitResponseDatesFromServer<T extends LectureUnit>(res: HttpResponse<T>): HttpResponse<T> {
        if (res.body) {
            if (res.body.type === LectureUnitType.ATTACHMENT) {
                if ((<AttachmentUnit>res.body).attachment) {
                    (<AttachmentUnit>res.body).attachment = this.attachmentService.convertAttachmentDatesFromServer((<AttachmentUnit>res.body).attachment);
                }
            } else if (res.body.type === LectureUnitType.EXERCISE) {
                if ((<ExerciseUnit>res.body).exercise) {
                    (<ExerciseUnit>res.body).exercise = ExerciseService.convertExerciseDatesFromServer((<ExerciseUnit>res.body).exercise);
                    ExerciseService.parseExerciseCategories((<ExerciseUnit>res.body).exercise);
                }
            } else {
                res.body.releaseDate = convertDateFromServer(res.body.releaseDate);
            }
        }
        return res;
    }

    convertLectureUnitDateFromServer<T extends LectureUnit>(lectureUnit: T): T {
        if (lectureUnit.type === LectureUnitType.ATTACHMENT) {
            if ((<AttachmentUnit>lectureUnit).attachment) {
                (<AttachmentUnit>lectureUnit).attachment = this.attachmentService.convertAttachmentDatesFromServer((<AttachmentUnit>lectureUnit).attachment);
            }
        } else if (lectureUnit.type === LectureUnitType.EXERCISE) {
            if ((<ExerciseUnit>lectureUnit).exercise) {
                (<ExerciseUnit>lectureUnit).exercise = ExerciseService.convertExerciseDatesFromServer((<ExerciseUnit>lectureUnit).exercise);
                ExerciseService.parseExerciseCategories((<ExerciseUnit>lectureUnit).exercise);
            }
        } else {
            lectureUnit.releaseDate = convertDateFromServer(lectureUnit.releaseDate);
        }
        return lectureUnit;
    }

    convertLectureUnitResponseArrayDatesFromServer<T extends LectureUnit>(res: HttpResponse<T[]>): HttpResponse<T[]> {
        if (res.body) {
            res.body.forEach((lectureUnit: LectureUnit) => {
                this.convertLectureUnitDateFromServer(lectureUnit);
            });
        }
        return res;
    }

    convertLectureUnitArrayDatesFromServer<T extends LectureUnit>(res: T[]): T[] {
        if (res) {
            res.forEach((lectureUnit: LectureUnit) => {
                this.convertLectureUnitDateFromServer(lectureUnit);
            });
        }
        return res;
    }

    getLectureUnitName(lectureUnit: LectureUnit) {
        if (lectureUnit.type === LectureUnitType.ATTACHMENT) {
            return (<AttachmentUnit>lectureUnit)?.attachment?.name;
        } else if (lectureUnit.type === LectureUnitType.EXERCISE) {
            return (<ExerciseUnit>lectureUnit)?.exercise?.title;
        } else {
            return lectureUnit.name;
        }
    }

    getLectureUnitReleaseDate(lectureUnit: LectureUnit) {
        if (lectureUnit.type === LectureUnitType.ATTACHMENT) {
            return (<AttachmentUnit>lectureUnit)?.attachment?.releaseDate;
        } else if (lectureUnit.type === LectureUnitType.EXERCISE) {
            return (<ExerciseUnit>lectureUnit)?.exercise?.releaseDate;
        } else {
            return lectureUnit.releaseDate;
        }
    }

    getLectureUnitForLearningPathNodeDetails(lectureUnitId: number) {
        return this.httpClient.get<LectureUnitForLearningPathNodeDetailsDTO>(`${this.resourceURL}/lecture-units/${lectureUnitId}/for-learning-path-node-details`, {
            observe: 'response',
        });
    }

    getLectureUnitById(lectureUnitId: number): Observable<LectureUnit> {
        return this.httpClient.get<LectureUnit>(`${this.resourceURL}/lecture-units/${lectureUnitId}`);
    }
    /**
     * Fetch the actual ingestion state for all lecture units from an external service (e.g., Pyris).
     * @param courseId
     * @param lectureId ID of the lecture
     * @returns Observable with the ingestion state
     */
    getIngestionState(courseId: number, lectureId: number): Observable<HttpResponse<Record<number, IngestionState>>> {
        return this.httpClient.get<Record<number, IngestionState>>(`${this.resourceURL}/public/pyris/courses/${courseId}/lectures/${lectureId}/lecture-units/ingestion-state`, {
            observe: 'response',
        });
    }

    /**
     * Triggers the ingestion of one lecture unit.
     *
     * @param lectureUnitId - The ID of the lecture unit to be ingested.
     * @param lectureId - The ID of the lecture to which the unit belongs.
     * @returns An Observable with an HttpResponse 200 if the request was successful .
     */
    ingestLectureUnitInPyris(lectureUnitId: number, lectureId: number): Observable<HttpResponse<void>> {
        return this.httpClient.post<void>(`${this.resourceURL}/lectures/${lectureId}/lecture-units/${lectureUnitId}/ingest`, null, {
            observe: 'response',
        });
    }
}
