import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { SERVER_API_URL } from 'app/app.constants';
import { ConsistencyCheckError } from 'app/entities/consistency-check-result.model';

@Injectable({
    providedIn: 'root',
})
export class ConsistencyCheckService {
    private readonly consistencyCheckUrl = SERVER_API_URL + 'api/consistency-check/';

    constructor(private http: HttpClient) {}

    /**
     * Request consistency checks for a given course
     * @param courseId id of the course to check
     */
    checkConsistencyForCourse(courseId: number) {
        return this.http.get<ConsistencyCheckError[]>(`${this.consistencyCheckUrl}course/${courseId}`, { responseType: 'json' });
    }

    /**
     * Request consistency checks for a given programming exercise
     * @param exerciseId id of the programming exercise to check
     */
    checkConsistencyForProgrammingExercise(exerciseId: number) {
        return this.http.get<ConsistencyCheckError[]>(`${this.consistencyCheckUrl}exercise/${exerciseId}`, { responseType: 'json' });
    }
}
