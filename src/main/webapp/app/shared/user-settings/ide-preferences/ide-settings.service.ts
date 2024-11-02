import { Injectable } from '@angular/core';
import { map } from 'rxjs/operators';
import { Observable, lastValueFrom } from 'rxjs';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Ide, IdeMappingDTO } from 'app/shared/user-settings/ide-preferences/ide.model';
import { ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class IdeSettingsService {
    public readonly ideSettingsUrl = 'api/ide-settings';

    // cached value of the ide preferences to avoid unnecessary requests to the server
    private idePreferences?: Map<ProgrammingLanguage, Ide>;

    constructor(private http: HttpClient) {}

    /**
     * GET call to the server to receive the stored ide preferences of the current user
     * @return the saved ide preference which were found in the database or error
     */
    public loadPredefinedIdes(): Observable<Ide[]> {
        return this.http.get<Ide[]>(this.ideSettingsUrl + '/predefined');
    }

    private ongoingRequest: Promise<Map<ProgrammingLanguage, Ide>> | undefined = undefined;
    private cacheTimestamp: number | undefined = undefined; // To store the timestamp when the preferences were loaded
    private readonly cacheDuration = 60 * 1000; // 1 minute in milliseconds

    /**
     * GET call to the server to receive the stored ide preferences of the current user
     * Prevent concurrent requests by caching the ongoing request and the timestamp when the preferences were loaded
     * Load the settings again after 1min in case they have changed
     * @param force if true, the cache will be ignored and a new request will be made
     * @return the saved ide preference which were found in the database or error
     */
    public loadIdePreferences(force?: boolean): Promise<Map<ProgrammingLanguage, Ide>> {
        const currentTime = new Date().getTime();

        // If preferences are already loaded and the cache is valid, return them immediately
        if (this.idePreferences && !force && this.cacheTimestamp && currentTime - this.cacheTimestamp < this.cacheDuration) {
            return Promise.resolve(this.idePreferences);
        }

        // If there's already an ongoing request, return that promise to prevent multiple calls
        if (this.ongoingRequest) {
            return this.ongoingRequest;
        }

        // Make the REST call and cache the ongoing request
        this.ongoingRequest = lastValueFrom(
            this.http.get<IdeMappingDTO[]>(this.ideSettingsUrl).pipe(
                map((data) => {
                    this.idePreferences = new Map<ProgrammingLanguage, Ide>(data.map((x) => [x.programmingLanguage, x.ide]));
                    this.cacheTimestamp = new Date().getTime(); // Update the timestamp when the data is cached
                    return this.idePreferences;
                }),
            ),
        ).finally(() => {
            // Clear the ongoingRequest once the promise resolves
            this.ongoingRequest = undefined;
        });

        return this.ongoingRequest;
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
