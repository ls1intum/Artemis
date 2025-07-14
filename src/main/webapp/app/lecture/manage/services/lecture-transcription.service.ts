import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { Observable, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { LectureTranscriptionDTO } from 'app/lecture/shared/entities/lecture-unit/attachmentVideoUnit.model';

@Injectable({ providedIn: 'root' })
export class LectureTranscriptionService {
    private httpClient = inject(HttpClient);

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

    getTranscription(lectureUnitId: number): Observable<LectureTranscriptionDTO | null> {
        return this.httpClient
            .get<LectureTranscriptionDTO>(`api/lecture/lecture-unit/${lectureUnitId}/transcript`, {
                observe: 'response',
            })
            .pipe(
                map((response) => response.body),
                catchError(() => of(null)),
            );
    }
}
