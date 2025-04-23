import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class LectureTranscriptionService {
    private httpClient = inject(HttpClient);

    ingestTranscription(courseId: number, lectureId: number, lectureUnitId: number): Observable<boolean> {
        return this.httpClient
            .put(
                `api/lecture/${lectureId}/lecture-unit/${lectureUnitId}/ingest-transcription`,
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

    createTranscription(lectureId: number, lectureUnitId: number, transcription: any): Observable<boolean> {
        return this.httpClient
            .post(`api/lecture/${lectureId}/lecture-unit/${lectureUnitId}/transcription`, transcription, {
                observe: 'response',
            })
            .pipe(
                map((response) => response.status == 201),
                catchError(() => of(false)),
            );
    }
}
