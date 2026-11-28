import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PlagiarismApi } from 'app/openapi/api/plagiarism-api';

@Service()
export class PlagiarismResultsService {
    private plagiarismApi = inject(PlagiarismApi);

    getNumberOfPlagiarismResultsForExercise(exerciseId: number): Observable<number> {
        return this.plagiarismApi.getNumberOfPotentialPlagiarismCasesForExercise(exerciseId);
    }
}
