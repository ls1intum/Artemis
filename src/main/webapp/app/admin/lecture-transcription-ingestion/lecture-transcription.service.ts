import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LectureTranscriptionService {
    constructor(private httpClient: HttpClient) {}

    ingestTranscription(courseId: number, lectureId: number): Observable<boolean> {
        return this.httpClient
            .put(
                `api/courses/${courseId}/lectures/${lectureId}/ingest-transcription`,
                {},
                {
                    observe: 'response',
                },
            )
            .pipe(map((response) => response.status == 200));
    }

    createTranscription(courseId: number, lectureId: number, transcription: any): Observable<boolean> {
        return this.httpClient
            .post(`api/courses/${courseId}/lecture/${lectureId}/transcriptions`, transcription, {
                observe: 'response',
            })
            .pipe(map((response) => response.status == 201));
    }
}
