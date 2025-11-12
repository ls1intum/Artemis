import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LectureTranscriptionDTO, TranscriptionStatusDTO } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';

@Injectable({ providedIn: 'root' })
export class LectureTranscriptionService {
    private httpClient = inject(HttpClient);

    ingestTranscription(courseId: number, lectureId: number, lectureUnitId: number): Observable<boolean> {
        return this.httpClient
            .post(
                `api/lecture/lectures/${lectureId}/lecture-units/${lectureUnitId}/ingest`,
                {},
                {
                    observe: 'response',
                },
            )
            .pipe(
                map((response) => response.status === 200),
                catchError(() => of(false)),
            );
    }

    createTranscription(lectureId: number, lectureUnitId: number, transcription: LectureTranscriptionDTO): Observable<boolean> {
        return this.httpClient
            .post(`api/lecture/${lectureId}/lecture-unit/${lectureUnitId}/transcription`, transcription, {
                observe: 'response',
            })
            .pipe(
                map((response) => response.status == 201),
                catchError(() => of(false)),
            );
    }

    getTranscription(lectureUnitId: number): Observable<LectureTranscriptionDTO | undefined> {
        return this.httpClient
            .get<LectureTranscriptionDTO>(`api/lecture/lecture-unit/${lectureUnitId}/transcript`, {
                observe: 'response',
            })
            .pipe(
                map((response) => response.body ?? undefined),
                catchError(() => of(undefined)),
            );
    }

    getTranscriptionStatus(lectureUnitId: number): Observable<TranscriptionStatusDTO | undefined> {
        return this.httpClient
            .get<TranscriptionStatusDTO>(`api/lecture/lecture-unit/${lectureUnitId}/transcript/status`, {
                observe: 'response',
            })
            .pipe(
                map((response) => response.body ?? undefined),
                catchError(() => of(undefined)),
            );
    }

    cancelTranscription(jobId: string): Observable<boolean> {
        return this.httpClient
            .post(
                `api/nebula/transcribe/cancel/${jobId}`,
                {},
                {
                    observe: 'response',
                },
            )
            .pipe(
                map((response) => response.status === 200),
                catchError(() => of(false)),
            );
    }
}
