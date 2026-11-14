import { Service, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LectureTranscriptionApi } from 'app/openapi/api/lecture-transcription-api';
import type { LectureTranscription } from 'app/openapi/model/lecture-transcription';

@Service()
export class LectureTranscriptionService {
    private lectureTranscriptionApi = inject(LectureTranscriptionApi);

    getTranscription(lectureUnitId: number): Observable<LectureTranscription | undefined> {
        return this.lectureTranscriptionApi.getTranscript(lectureUnitId).pipe(catchError(() => of(undefined)));
    }
}
