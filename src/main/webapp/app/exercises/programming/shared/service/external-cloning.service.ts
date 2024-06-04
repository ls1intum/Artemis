import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ExternalCloningService {
    /**
     * Build source tree url.
     * @param baseUrl - the base url of the version control system
     * @param cloneUrl - url of the target.
     */
    buildSourceTreeUrl(baseUrl: string, cloneUrl: string | undefined) {
        return cloneUrl ? `sourcetree://cloneRepo?type=stash&cloneUrl=${encodeURI(cloneUrl)}&baseWebUrl=${baseUrl}` : undefined;
    }

    /**
     * Builds a url to clone a Repository in IntelliJ
     * Structure: jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo=:RepoUrl
     * @param cloneUrl
     */
    buildIntelliJUrl(cloneUrl: string | undefined): string | undefined {
        // jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo=git%40gitlab.db.in.tum.de%3Amoderndbs-2024%2Fexternal-sort.git
        if (!cloneUrl) {
            return undefined;
        }
        return `jetbrains://idea/checkout/git?idea.required.plugins.id=Git4Idea&checkout.repo=${encodeURIComponent(cloneUrl)}`;
    }
}
