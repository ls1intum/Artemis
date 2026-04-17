import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import dayjs, { Dayjs } from 'dayjs/esm';
import { Observable } from 'rxjs';

interface DeimosBatchRequest {
    from: string;
    to: string;
}

export interface DeimosBatchTriggerResponse {
    runId: string;
    status: string;
}

@Injectable({ providedIn: 'root' })
export class DeimosService {
    private http = inject(HttpClient);

    private resourceUrl = 'api/programming';

    triggerCourseBatch(courseId: number, from: Dayjs, to: Dayjs): Observable<DeimosBatchTriggerResponse> {
        return this.http.post<DeimosBatchTriggerResponse>(`${this.resourceUrl}/courses/${courseId}/deimos/batch`, this.createRequest(from, to));
    }

    triggerExerciseBatch(exerciseId: number, from: Dayjs, to: Dayjs): Observable<DeimosBatchTriggerResponse> {
        return this.http.post<DeimosBatchTriggerResponse>(`${this.resourceUrl}/programming-exercises/${exerciseId}/deimos/batch`, this.createRequest(from, to));
    }

    private createRequest(from: Dayjs, to: Dayjs): DeimosBatchRequest {
        return {
            from: dayjs(from).toISOString(),
            to: dayjs(to).toISOString(),
        };
    }
}
