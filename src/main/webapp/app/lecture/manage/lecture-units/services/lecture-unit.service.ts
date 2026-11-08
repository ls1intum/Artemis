import { LectureUnitApi } from 'app/openapi/api/lecture-unit-api';
import type { LectureUnitCombinedStatus } from 'app/openapi/model/lecture-unit-combined-status';
import { AlertService } from 'app/foundation/service/alert.service';
import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { LectureUnitCompletionEvent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { onError } from 'app/foundation/util/global.utils';
import { Service, inject } from '@angular/core';
import { AttachmentVideoUnit, TranscriptionStatus } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';
import { AttachmentService } from 'app/lecture/manage/services/attachment.service';
import { ExerciseUnit } from 'app/lecture/shared/entities/lecture-unit/exerciseUnit.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { convertDateFromServer } from 'app/foundation/util/date.utils';

@Service()
export class LectureUnitService {
    private lectureUnitApi = inject(LectureUnitApi);
    private attachmentService = inject(AttachmentService);
    private alertService = inject(AlertService);

    completeLectureUnit(lecture: Lecture, event: LectureUnitCompletionEvent, onSuccess?: () => void): void {
        if (event.lectureUnit.visibleToStudents && event.lectureUnit.completed !== event.completed) {
            this.lectureUnitApi.completeLectureUnit(event.lectureUnit.id!, lecture.id!, event.completed).subscribe({
                next: () => {
                    event.lectureUnit.completed = event.completed;
                    // Callers hold the unit in a signal; the callback lets them publish a new reference so the UI updates (zoneless change detection).
                    onSuccess?.();
                },
                error: (res: HttpErrorResponse) => onError(this.alertService, res),
            });
        }
    }

    convertLectureUnitResponseDatesFromServer<T extends LectureUnit>(res: HttpResponse<T>): HttpResponse<T> {
        if (res.body) {
            // Convert the unit's own release date for every type so the raw ISO string from the server never reaches the date picker (e.g. when editing an attachment/video unit).
            res.body.releaseDate = convertDateFromServer(res.body.releaseDate);
            if (res.body.type === LectureUnitType.ATTACHMENT_VIDEO) {
                if ((res.body as AttachmentVideoUnit).attachment) {
                    (res.body as AttachmentVideoUnit).attachment = this.attachmentService.convertAttachmentFromServer((res.body as AttachmentVideoUnit).attachment);
                }
            } else if (res.body.type === LectureUnitType.EXERCISE) {
                if ((res.body as ExerciseUnit).exercise) {
                    (res.body as ExerciseUnit).exercise = ExerciseService.convertExerciseDatesFromServer((res.body as ExerciseUnit).exercise);
                    ExerciseService.parseExerciseCategories((res.body as ExerciseUnit).exercise);
                }
            }
        }
        return res;
    }

    getLectureUnitName(lectureUnit: LectureUnit) {
        if (lectureUnit.type === LectureUnitType.ATTACHMENT_VIDEO) {
            return lectureUnit.name;
        } else if (lectureUnit.type === LectureUnitType.EXERCISE) {
            return (lectureUnit as ExerciseUnit)?.exercise?.title;
        } else {
            return lectureUnit.name;
        }
    }

    getLectureUnitReleaseDate(lectureUnit: LectureUnit) {
        if (lectureUnit.type === LectureUnitType.ATTACHMENT_VIDEO) {
            return (lectureUnit as AttachmentVideoUnit)?.attachment?.releaseDate;
        } else if (lectureUnit.type === LectureUnitType.EXERCISE) {
            return (lectureUnit as ExerciseUnit)?.exercise?.releaseDate;
        } else {
            return lectureUnit.releaseDate;
        }
    }
}

/**
 * DTO representing the processing status of a lecture unit.
 * The stage fields carry the live stage ledger Iris reports with its heartbeats,
 * so the status badge can render progress like "Indexing · 41/142".
 */
export interface LectureUnitProcessingStatus {
    lectureUnitId: number;
    phase?: ProcessingPhase;
    retryCount?: number;
    startedAt?: string;
    errorKey?: string;
    stageName?: string;
    stageProgress?: number;
    stageTotal?: number;
    /** Server time of the worker's last lease renewal; absent for runs without a worker lease. */
    lastHeartbeatAt?: string;
    /**
     * Client clock when this status was received. Liveness staleness is computed against this rather
     * than against the server timestamp, so clock skew between browser and server cannot fake or
     * hide a lost run: lease renewals arrive as updates every few seconds, and their receipt time is
     * measured on the same clock the badge ticks with.
     */
    receivedAt?: number;
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
    SKIPPED = 'SKIPPED',
}

/**
 * Map a combined status (REST or websocket payload) to the processing status the UI keeps per unit.
 * The single place new observability fields have to be threaded through when the wire format grows.
 *
 * @param status the generated combined status
 * @returns the processing status, or undefined for a payload that names no lecture unit
 */
export function toProcessingStatus(status: LectureUnitCombinedStatus): LectureUnitProcessingStatus | undefined {
    if (status.lectureUnitId === undefined) {
        return undefined;
    }
    return {
        lectureUnitId: status.lectureUnitId,
        phase: status.processingPhase && ProcessingPhase[status.processingPhase],
        retryCount: status.retryCount,
        startedAt: status.startedAt,
        errorKey: status.processingErrorKey,
        stageName: status.stageName,
        stageProgress: status.stageProgress,
        stageTotal: status.stageTotal,
        lastHeartbeatAt: status.lastHeartbeatAt,
        receivedAt: Date.now(),
    };
}

/**
 * Map the transcription status of a combined status to the client enum.
 *
 * @param status the generated combined status
 * @returns the transcription status, or undefined when the unit has no transcription
 */
export function toTranscriptionStatus(status: LectureUnitCombinedStatus): TranscriptionStatus | undefined {
    return status.transcriptionStatus && TranscriptionStatus[status.transcriptionStatus];
}
