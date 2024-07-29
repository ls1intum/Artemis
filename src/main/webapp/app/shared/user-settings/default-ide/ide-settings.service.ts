import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Ide, IdeMappingDTO } from 'app/shared/user-settings/default-ide/ide.model';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class IdeSettingsService {
    public ideSettingsUrl = 'api/ide-settings';
    error?: string;

    constructor(private http: HttpClient) {}

    /**
     * GET call to the server to receive the stored ide preferences of the current user
     * @return the saved ide preference which were found in the database or error
     */
    public loadIdePreferences(): Observable<HttpResponse<IdeMappingDTO[]>> {
        return this.http.get<IdeMappingDTO[]>(this.ideSettingsUrl, { observe: 'response' });
    }

    /**
     * PUT call to the server to update a stored ide preferences of the current user
     * @return the newly saved ide preference or error
     */
    public saveIdePreference(programmingLanguage: ProgrammingLanguage, ide: Ide): Observable<HttpResponse<IdeMappingDTO>> {
        const params = new HttpParams().set('programmingLanguage', programmingLanguage);
        return this.http.put<IdeMappingDTO>(this.ideSettingsUrl, ide, { params, observe: 'response' });
    }

    /**
     * DELETE call to the server to delete an ide preference of the current user
     * @return the deleted ide preference or error
     */
    public deleteIdePreference(programmingLanguage: ProgrammingLanguage): Observable<HttpResponse<any>> {
        const params = new HttpParams().set('programmingLanguage', programmingLanguage);
        return this.http.delete<IdeMappingDTO[]>(this.ideSettingsUrl, { params, observe: 'response' });
    }
}
