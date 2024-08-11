import { Injectable } from '@angular/core';
import { Ide } from 'app/shared/user-settings/ide-preferences/ide.model';

@Injectable({ providedIn: 'root' })
export class ExternalCloningService {
    /**
     * Build source tree url.
     * @param baseUrl - the base url of the version control system
     * @param cloneUrl - url of the target.
     */
    buildSourceTreeUrl(baseUrl: string, cloneUrl: string | undefined): string | undefined {
        return cloneUrl ? `sourcetree://cloneRepo?type=stash&cloneUrl=${encodeURI(cloneUrl)}&baseWebUrl=${baseUrl}` : undefined;
    }

    /**
     * Builds an url to clone a Repository in the corresponding ide
     * @param cloneUrl the url of the repository to clone
     * @param ide the ide with the deeplink to build the url for
     */
    // TODO shift exercise Programming Language Logic here
    buildIDEUrl(cloneUrl: string | undefined, ide: Ide): string | undefined {
        if (!cloneUrl) {
            console.error('cloneUrl is undefined');
            return undefined;
        }
        if (!ide.deepLink.includes('{cloneUrl}')) {
            console.error('ide deepLink does not contain {cloneUrl}');
            return undefined;
        }

        return ide.deepLink.replace('{cloneUrl}', encodeURIComponent(cloneUrl));
    }
}
