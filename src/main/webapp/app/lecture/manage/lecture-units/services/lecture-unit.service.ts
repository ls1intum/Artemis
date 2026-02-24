import { AlertService } from 'app/shared/service/alert.service';
import { LectureUnit, LectureUnitForLearningPathNodeDetailsDTO, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { HttpClient, HttpErrorResponse, HttpParams, HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUnitCompletionEvent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { onError } from 'app/shared/util/global.utils';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Injectable, inject } from '@angular/core';
import { AttachmentVideoUnit, TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { AttachmentService } from 'app/lecture/manage/services/attachment.service';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { convertDateFromClient, convertDateFromServer } from 'app/shared/util/date.utils';

type EntityArrayResponseType = HttpResponse<LectureUnit[]>;

@Injectable({
    providedIn: 'root',
})
export class LectureUnitService {
    private httpClient = inject(HttpClient);
    private attachmentService = inject(AttachmentService);
    private alertService = inject(AlertService);

    private resourceURL = 'api/lecture';

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
        if (lectureUnit.type === LectureUnitType.ATTACHMENT_VIDEO) {
            if ((<AttachmentVideoUnit>lectureUnit).attachment) {
                (<AttachmentVideoUnit>lectureUnit).attachment = this.attachmentService.convertAttachmentDatesFromClient((<AttachmentVideoUnit>lectureUnit).attachment!);
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
            if (res.body.type === LectureUnitType.ATTACHMENT_VIDEO) {
                if ((<AttachmentVideoUnit>res.body).attachment) {
                    (<AttachmentVideoUnit>res.body).attachment = this.attachmentService.convertAttachmentFromServer((<AttachmentVideoUnit>res.body).attachment);
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
        if (lectureUnit.type === LectureUnitType.ATTACHMENT_VIDEO) {
            if ((<AttachmentVideoUnit>lectureUnit).attachment) {
                (<AttachmentVideoUnit>lectureUnit).attachment = this.attachmentService.convertAttachmentFromServer((<AttachmentVideoUnit>lectureUnit).attachment);
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
        if (lectureUnit.type === LectureUnitType.ATTACHMENT_VIDEO) {
            return lectureUnit.name;
        } else if (lectureUnit.type === LectureUnitType.EXERCISE) {
            return (<ExerciseUnit>lectureUnit)?.exercise?.title;
        } else {
            return lectureUnit.name;
        }
    }

    getLectureUnitReleaseDate(lectureUnit: LectureUnit) {
        if (lectureUnit.type === LectureUnitType.ATTACHMENT_VIDEO) {
            return (<AttachmentVideoUnit>lectureUnit)?.attachment?.releaseDate;
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
     * Retry processing for a failed lecture unit.
     * @param lectureId the ID of the lecture
     * @param lectureUnitId the ID of the lecture unit
     * @returns Observable with the updated combined status
     */
    retryProcessing(lectureId: number, lectureUnitId: number): Observable<LectureUnitCombinedStatus> {
        return this.httpClient.post<LectureUnitCombinedStatus>(`${this.resourceURL}/lectures/${lectureId}/lecture-units/${lectureUnitId}/retry-processing`, null);
    }

    /**
     * Get the combined processing and transcription status for all attachment video units in a lecture.
     * This bulk endpoint reduces the number of HTTP requests from 2N to 1 when loading the lecture unit management view.
     * @param lectureId the ID of the lecture
     * @returns Observable with the list of combined statuses
     */
    getUnitStatuses(lectureId: number): Observable<LectureUnitCombinedStatus[]> {
        return this.httpClient.get<LectureUnitCombinedStatus[]>(`${this.resourceURL}/lectures/${lectureId}/lecture-units/statuses`);
    }
}

/**
 * DTO representing the processing status of a lecture unit.
 */
export interface LectureUnitProcessingStatus {
    lectureUnitId: number;
    phase: ProcessingPhase;
    retryCount: number;
    startedAt?: string;
    errorKey?: string;
}

/**
 * Enum for processing phases.
 */
export enum ProcessingPhase {
    IDLE = 'IDLE',
    TRANSCRIBING = 'TRANSCRIBING',
    INGESTING = 'INGESTING',
    DONE = 'DONE',
    FAILED = 'FAILED',
}

/**
 * DTO representing the combined processing and transcription status of a lecture unit.
 * Used by the bulk status endpoint to reduce HTTP requests.
 */
export interface LectureUnitCombinedStatus {
    lectureUnitId: number;
    processingPhase: ProcessingPhase;
    retryCount: number;
    startedAt?: string;
    processingErrorKey?: string;
    transcriptionStatus?: TranscriptionStatus;
}
