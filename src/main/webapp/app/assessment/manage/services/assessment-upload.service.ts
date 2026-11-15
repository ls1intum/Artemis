import { HttpClient, HttpResponse } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';

/**
 * Downloads the template zip of the manual assessment upload. Kept next to the generated `AssessmentUploadApi` because the spec declares this
 * endpoint (`downloadManualAssessmentTemplate`) as a JSON string, while it returns a zip file whose name travels in the `filename` header.
 */
@Service()
export class AssessmentUploadService {
    private readonly http = inject(HttpClient);

    /**
     * Downloads a template zip for a programming exercise: an `assessment-scores.csv` pre-filled with every participant's identifier and an empty `Manual points` column, plus one
     * empty `.txt` feedback file per participant. The instructor fills in the points and feedback and uploads the archive.
     *
     * @param exerciseId the id of the programming exercise
     * @return an observable emitting the template zip response
     */
    downloadTemplate(exerciseId: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`api/assessment/exercises/${exerciseId}/manual-assessments/template`, { observe: 'response', responseType: 'blob' });
    }
}
