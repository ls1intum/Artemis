import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';

export type RepositoryExportOptions = {
    exportAllParticipants: boolean;
    filterLateSubmissions: boolean;
    filterLateSubmissionsDate?: dayjs.Dayjs;
    excludePracticeSubmissions: boolean;
    addParticipantName: boolean;
    combineStudentCommits: boolean;
    anonymizeRepository: boolean;
    normalizeCodeStyle: boolean;
};

@Injectable({ providedIn: 'root' })
export class ProgrammingAssessmentRepoExportService {
    private http = inject(HttpClient);

    // TODO: We should move this endpoint to api/programming-exercises.
    public resourceUrl = 'api/programming-exercises';

    /**
     * Exports repositories to the server by their participant identifiers
     * @param {number} exerciseId - Id of the exercise
     * @param {string[]} participantIdentifiers - Identifiers of participants
     * @param {RepositoryExportOptions} repositoryExportOptions
     */
    exportReposByParticipantIdentifiers(exerciseId: number, participantIdentifiers: string[], repositoryExportOptions: RepositoryExportOptions): Observable<HttpResponse<Blob>> {
        const url = `${this.resourceUrl}/${exerciseId}/export-repos-by-participant-identifiers/${participantIdentifiers}`;
        return this.http.post(url, repositoryExportOptions, {
            observe: 'response',
            responseType: 'blob',
        });
    }

    /**
     * Exports repositories to the server by their participation ids
     * @param {number} exerciseId - Id of the exercise
     * @param {number[]} participationIds - Ids of participations
     * @param {RepositoryExportOptions} repositoryExportOptions
     */
    exportReposByParticipations(exerciseId: number, participationIds: number[], repositoryExportOptions: RepositoryExportOptions): Observable<HttpResponse<Blob>> {
        const url = `${this.resourceUrl}/${exerciseId}/export-repos-by-participation-ids/${participationIds}`;
        return this.http.post(url, repositoryExportOptions, {
            observe: 'response',
            responseType: 'blob',
        });
    }
}
