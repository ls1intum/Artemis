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
                `api/courses/${courseId}/ingest-transcription?lectureId=${lectureId}`,
                {},
                {
                    observe: 'response',
                },
            )
            .pipe(map((response) => response.status == 200));
    }

    createTranscription(transcription: any): Observable<boolean> {
        return this.httpClient
            .post(`api/transcription`, transcription, {
                observe: 'response',
            })
            .pipe(map((response) => response.status == 201));
    }
}
