import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { TheiaRedirectProps } from 'app/programming/shared/entities/theia-redirect.model';

@Injectable({ providedIn: 'root' })
export class TheiaService {
    private http = inject(HttpClient);
    private accountService = inject(AccountService);

    private resourceUrl = 'api/programming/theia';

    /**
     * Fetches the theia images for the given programming language
     * @param {ProgrammingLanguage} language
     * @returns the theia images or undefined if no images are available for this language
     */
    getTheiaImages(language: ProgrammingLanguage): Observable<{ [key: string]: string } | undefined> {
        return this.http.get<{ [key: string]: string }>(`${this.resourceUrl}/images`, {
            params: {
                language: language,
            },
        });
    }

    /**
     * Starts the online IDE (Theia) in a new window using provided parameters
     */
    async startOnlineIDE(theiaPortalURL: string, theiaImage: string, repositoryUri: string, userName?: string, userEmail?: string): Promise<void> {
        const artemisToken: string = (await this.accountService.getToolToken('SCORPIO').toPromise()) ?? '';

        let artemisUrl: string = '';
        if (window.location.protocol) {
            artemisUrl += window.location.protocol + '//';
        }
        if (window.location.host) {
            artemisUrl += window.location.host;
        }

        const data: TheiaRedirectProps = {
            appDef: theiaImage,
            gitUri: repositoryUri,
            gitUser: userName,
            gitMail: userEmail,
            artemisToken,
            artemisUrl,
        };

        const newWindow = window.open('', '_blank');
        if (!newWindow) {
            return;
        }

        newWindow.name = 'Theia-IDE';

        const form = document.createElement('form');
        form.method = 'GET';
        form.action = theiaPortalURL;
        form.target = newWindow.name;

        Object.entries(data).forEach(([key, value]) => {
            const hiddenField = document.createElement('input');
            hiddenField.type = 'hidden';
            hiddenField.name = key;
            hiddenField.value = value;
            form.appendChild(hiddenField);
        });

        document.body.appendChild(form);
        form.submit();
        document.body.removeChild(form);
    }
}
