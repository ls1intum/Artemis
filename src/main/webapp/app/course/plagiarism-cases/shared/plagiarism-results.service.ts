import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { PlagiarismSubmissionElement } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmissionElement';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import { PlagiarismCaseInfo } from 'app/exercises/shared/plagiarism/types/PlagiarismCaseInfo';
import { Exercise } from 'app/entities/exercise.model';
import { PlagiarismResult } from 'app/exercises/shared/plagiarism/types/PlagiarismResult';

export type EntityResponseType = HttpResponse<PlagiarismCase>;
export type EntityArrayResponseType = HttpResponse<PlagiarismCase[]>;
export type Comparison = PlagiarismComparison<PlagiarismSubmissionElement>;

@Injectable({ providedIn: 'root' })
export class PlagiarismResultsService {
    private resourceUrlExercises = 'api/exercises';

    constructor(private http: HttpClient) {}

    getNumberOfPlagiarismResultsForExercise(exerciseId: number): Observable<number> {
        return this.http.get<number>(`${this.resourceUrlExercises}/${exerciseId}/plagiarism-results`);
    }
}
