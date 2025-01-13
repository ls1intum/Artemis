import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class IrisTranscriptionService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient) {}

    ingestTranscription(courseId: number, lectureId: number): Observable<string> {
        console.log('inserting123');
        console.log(`${this.resourceURL}/courses/${courseId}/ingest-transcription?lectureId=${lectureId}`);
        return this.httpClient.put(
            `${this.resourceURL}/courses/${courseId}/ingest-transcription?lectureId=${lectureId}`,
            {},
            {
                responseType: 'text',
            },
        );
    }
}
