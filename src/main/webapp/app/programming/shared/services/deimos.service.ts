import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
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

@Service()
export class DeimosService {
    private http = inject(HttpClient);

    private resourceUrl = 'api/deimos';

    triggerCourseBatch(courseId: number, from: Dayjs, to: Dayjs): Observable<DeimosBatchTriggerResponse> {
        return this.http.post<DeimosBatchTriggerResponse>(`${this.resourceUrl}/courses/${courseId}/analysis-runs`, this.createRequest(from, to));
    }

    triggerExerciseBatch(exerciseId: number, from: Dayjs, to: Dayjs): Observable<DeimosBatchTriggerResponse> {
        return this.http.post<DeimosBatchTriggerResponse>(`${this.resourceUrl}/programming-exercises/${exerciseId}/analysis-runs`, this.createRequest(from, to));
    }

    private createRequest(from: Dayjs, to: Dayjs): DeimosBatchRequest {
        return {
            from: dayjs(from).toISOString(),
            to: dayjs(to).toISOString(),
        };
    }
}
