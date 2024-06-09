import { Injectable } from '@angular/core';

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
     * Builds an url to clone a Repository in IntelliJ
     * Structure: jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo=:RepoUrl
     * @param cloneUrl the url of the repository to clone
     */
    buildJetbrainsUrl(cloneUrl: string | undefined): string | undefined {
        if (!cloneUrl) {
            return undefined;
        }
        return `jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo=${encodeURIComponent(cloneUrl)}`;
    }
}
