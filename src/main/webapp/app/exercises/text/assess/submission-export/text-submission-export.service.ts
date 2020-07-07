import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SERVER_API_URL } from 'app/app.constants';
import { Moment } from 'moment';

export type RepositoryExportOptions = {
    exportAllParticipants: boolean;
    filterLateSubmissions: boolean;
    filterLateSubmissionsDate: Moment | null;
    participantIdentifierList: string; // comma separated
};

@Injectable({ providedIn: 'root' })
export class TextSubmissionExportService {
    public resourceUrl = SERVER_API_URL + 'api/text-exercises';

    constructor(private http: HttpClient) {}

    /**
     * Exports submissions to the server by their participant identifiers
     * @param {number} exerciseId - Id of the exercise
     * @param {string[]} participantIdentifiers - Identifiers of participants
     * @param {RepositoryExportOptions} repositoryExportOptions
     */
    exportSubmissionsByParticipantIdentifiers(exerciseId: number, repositoryExportOptions: RepositoryExportOptions): Observable<HttpResponse<Blob>> {
        return this.http.post(`${this.resourceUrl}/${exerciseId}/export-submissions-by-participant-identifiers`, repositoryExportOptions, {
            observe: 'response',
            responseType: 'blob',
        });
    }
}
