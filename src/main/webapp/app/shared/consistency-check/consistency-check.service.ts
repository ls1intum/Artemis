import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ConsistencyCheckError } from 'app/entities/consistency-check-result.model';

@Injectable({
    providedIn: 'root',
})
export class ConsistencyCheckService {
    private http = inject(HttpClient);

    private readonly RESOURCE_URL = 'api/programming-exercises';

    /**
     * Request consistency checks for a given programming exercise
     * @param exerciseId id of the programming exercise to check
     */
    checkConsistencyForProgrammingExercise(exerciseId: number) {
        return this.http.get<ConsistencyCheckError[]>(`${this.RESOURCE_URL}/${exerciseId}/consistency-check`, { responseType: 'json' });
    }
}
