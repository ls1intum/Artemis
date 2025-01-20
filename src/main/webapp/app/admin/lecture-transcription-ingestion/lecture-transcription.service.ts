import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LectureTranscriptionService {
    constructor(private httpClient: HttpClient) {}

    ingestTranscription(courseId: number, lectureId: number): Observable<string> {
        return this.httpClient.put(
            `api/courses/${courseId}/ingest-transcription?lectureId=${lectureId}`,
            {},
            {
                responseType: 'text',
            },
        );
    }

    createTranscription(transcription: any): Observable<string> {
        return this.httpClient.post(`api/transcription`, transcription, {
            responseType: 'text',
        });
    }
}
