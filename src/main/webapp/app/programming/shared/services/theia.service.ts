import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

@Service()
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
     * Starts the online IDE (Theia) in a new window. Secret values (the Artemis token and a git URI with credentials) travel in the URL fragment
     */
    async startOnlineIDE(theiaPortalURL: string, theiaImage: string, repositoryUri: string, userName?: string, userEmail?: string): Promise<void> {
        const newWindow = window.open('', '_blank');
        if (!newWindow) {
            return;
        }

        const artemisToken: string = (await firstValueFrom(this.accountService.getToolToken('SCORPIO'))) ?? '';

        let artemisUrl: string = '';
        if (window.location.protocol) {
            artemisUrl += window.location.protocol + '//';
        }
        if (window.location.host) {
            artemisUrl += window.location.host;
        }

        // Secrets go into the URL fragment, which browsers never send to servers, so they stay out of access logs and Referer headers
        const query = new URLSearchParams({ appDef: theiaImage, artemisUrl });
        const fragment = new URLSearchParams({ artemisToken });
        const gitUri = new URL(repositoryUri);
        (gitUri.username || gitUri.password ? fragment : query).set('gitUri', repositoryUri);
        if (userName) {
            query.set('gitUser', userName);
        }
        if (userEmail) {
            query.set('gitMail', userEmail);
        }

        const url = new URL(theiaPortalURL);
        url.search = query.toString();
        url.hash = fragment.toString();
        newWindow.location.href = url.toString();
    }
}
