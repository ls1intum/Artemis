import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Ide, IdeMappingDTO } from 'app/shared/user-settings/ide-preferences/ide.model';
import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class IdeSettingsService {
    public ideSettingsUrl = 'api/ide-settings';
    error?: string;

    programmingLanguageToIde: Map<ProgrammingLanguage, Ide>;

    constructor(private http: HttpClient) {
        this.loadIdePreferences();
    }

    /**
     * GET call to the server to receive the stored ide preferences of the current user
     * @return the saved ide preference which were found in the database or error
     */
    public loadIdePreferences(): void {
        this.http.get<IdeMappingDTO[]>(this.ideSettingsUrl, { observe: 'response' }).subscribe((res) => {
            this.programmingLanguageToIde = new Map(res.body?.map((x) => [x.programmingLanguage, x.ide]));
            if (this.programmingLanguageToIde.size === 0) {
                this.programmingLanguageToIde = new Map([[ProgrammingLanguage.EMPTY, PREDEFINED_IDE[0]]]);
            }
        });
    }

    /**
     * PUT call to the server to update a stored ide preferences of the current user
     * @return the newly saved ide preference or error
     */
    public saveIdePreference(programmingLanguage: ProgrammingLanguage, ide: Ide): void {
        const params = new HttpParams().set('programmingLanguage', programmingLanguage);
        this.http.put<IdeMappingDTO>(this.ideSettingsUrl, ide, { params, observe: 'response' }).subscribe((res) => {
            if (res.body) this.programmingLanguageToIde.set(res.body.programmingLanguage, res.body.ide);
        });
    }

    /**
     * DELETE call to the server to delete an ide preference of the current user
     * @return the deleted ide preference or error
     */
    public deleteIdePreference(programmingLanguage: ProgrammingLanguage): void {
        const params = new HttpParams().set('programmingLanguage', programmingLanguage);
        this.http.delete<IdeMappingDTO[]>(this.ideSettingsUrl, { params, observe: 'response' }).subscribe(() => {
            this.programmingLanguageToIde.delete(programmingLanguage);
        });
    }
}

export const PREDEFINED_IDE: Ide[] = [
    { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' },
    {
        name: 'IntelliJ',
        deepLink: 'jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}',
    },
    { name: 'Eclipse', deepLink: 'eclipse://clone?repo={cloneUrl}' },
    {
        name: 'PyCharm',
        deepLink: 'jetbrains://pycharm/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}',
    },
    {
        name: 'CLion',
        deepLink: 'jetbrains://clion/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo={cloneUrl}',
    },
    { name: 'XCode', deepLink: 'xcode://clone?repo={cloneUrl}' },
];
