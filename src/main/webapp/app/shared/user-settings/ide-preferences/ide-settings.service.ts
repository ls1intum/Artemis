import { Injectable } from '@angular/core';
import { map } from 'rxjs/operators';
import { BehaviorSubject, Observable } from 'rxjs';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Ide, IdeMappingDTO } from 'app/shared/user-settings/ide-preferences/ide.model';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class IdeSettingsService {
    public ideSettingsUrl = 'api/ide-settings';
    public static readonly fallbackIde: Ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };
    private predefinedIdes: Ide[] = [IdeSettingsService.fallbackIde];
    private idePreferencesSubject: BehaviorSubject<Map<ProgrammingLanguage, Ide>> = new BehaviorSubject<Map<ProgrammingLanguage, Ide>>(
        new Map([[ProgrammingLanguage.EMPTY, IdeSettingsService.fallbackIde]]),
    );
    error?: string;

    constructor(private http: HttpClient) {
        // initially load predefined ides and ide preferences
        this.loadPredefinedIdes().subscribe();
        this.loadIdePreferences().subscribe();
    }

    public getPredefinedIdes(): Ide[] {
        return this.predefinedIdes;
    }

    /**
     * GET call to the server to receive the stored ide preferences of the current user
     * @return the saved ide preference which were found in the database or error
     */
    public loadPredefinedIdes(): Observable<Ide[]> {
        return this.http.get<Ide[]>(this.ideSettingsUrl + '/predefined').pipe(
            map((ides) => {
                this.predefinedIdes = ides;
                return ides;
            }),
        );
    }

    public getIdePreferences(): Observable<Map<ProgrammingLanguage, Ide>> {
        return this.idePreferencesSubject.asObservable();
    }

    /**
     * GET call to the server to receive the stored ide preferences of the current user
     * @return the saved ide preference which were found in the database or error
     */
    public loadIdePreferences(): Observable<Map<ProgrammingLanguage, Ide>> {
        return this.http.get<IdeMappingDTO[]>(this.ideSettingsUrl).pipe(
            map((data) => {
                const idePreferences = new Map<ProgrammingLanguage, Ide>(data.map((x) => [x.programmingLanguage, x.ide]));
                if (!idePreferences.has(ProgrammingLanguage.EMPTY)) {
                    idePreferences.set(ProgrammingLanguage.EMPTY, this.predefinedIdes[0]);
                }
                this.idePreferencesSubject.next(idePreferences);
                return idePreferences;
            }),
        );
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
                const prev = new Map(this.idePreferencesSubject.getValue());
                prev.set(programmingLanguage, res.ide);
                this.idePreferencesSubject.next(prev);
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
        return this.http.delete<void>(this.ideSettingsUrl, { params, observe: 'response' }).pipe(
            map((response) => {
                const prev = new Map(this.idePreferencesSubject.getValue());
                prev.delete(programmingLanguage);
                this.idePreferencesSubject.next(prev);
                return response;
            }),
        );
    }
}
