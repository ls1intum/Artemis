import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class PlagiarismResultsService {
    private resourceUrlExercises = 'api/exercises';

    constructor(private http: HttpClient) {}

    getNumberOfPlagiarismResultsForExercise(exerciseId: number): Observable<number> {
        return this.http.get<number>(`${this.resourceUrlExercises}/${exerciseId}/potential-plagiarism-count`);
    }
}
