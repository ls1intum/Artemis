import { Injectable, inject } from '@angular/core';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Ide, IdeMappingDTO } from 'app/shared/user-settings/ide-preferences/ide.model';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class IdeSettingsService {
    private http = inject(HttpClient);

    public ideSettingsUrl = 'api/ide-settings';
    error?: string;

    /**
     * GET call to the server to receive the stored ide preferences of the current user
     * @return the saved ide preference which were found in the database or error
     */
    public loadPredefinedIdes(): Observable<Ide[]> {
        return this.http.get<Ide[]>(this.ideSettingsUrl + '/predefined');
    }

    /**
     * GET call to the server to receive the stored ide preferences of the current user
     * @return the saved ide preference which were found in the database or error
     */
    public loadIdePreferences(): Observable<Map<ProgrammingLanguage, Ide>> {
        return this.http.get<IdeMappingDTO[]>(this.ideSettingsUrl).pipe(map((data) => new Map<ProgrammingLanguage, Ide>(data.map((x) => [x.programmingLanguage, x.ide]))));
    }

    /**
     * PUT call to the server to update a stored ide preferences of the current user
     * @param programmingLanguage the programming language for which the ide preference should be updated
     * @param ide the new ide preference
     * @return the newly saved ide preference or error
     */
    public saveIdePreference(programmingLanguage: ProgrammingLanguage, ide: Ide): Observable<Ide> {
        const params = new HttpParams().set('programmingLanguage', programmingLanguage);
        return this.http.put<IdeMappingDTO>(this.ideSettingsUrl, ide, { params }).pipe(
            map((res) => {
                return res.ide;
            }),
        );
    }

    /**
     * DELETE call to the server to delete an ide preference of the current user
     * @param programmingLanguage the programming language for which the ide preference should be deleted
     * @return the deleted ide preference or error
     */
    public deleteIdePreference(programmingLanguage: ProgrammingLanguage): Observable<HttpResponse<void>> {
        const params = new HttpParams().set('programmingLanguage', programmingLanguage);
        return this.http.delete<void>(this.ideSettingsUrl, { params, observe: 'response' });
    }
}
