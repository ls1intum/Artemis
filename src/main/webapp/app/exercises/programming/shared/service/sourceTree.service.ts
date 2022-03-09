import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class SourceTreeService {
    constructor() {}

    /**
     * Build source tree url.
     * @param baseUrl - the base url of the version control system (e.g. Bitbucket or Bamboo)
     * @param cloneUrl - url of the target.
     */
    buildSourceTreeUrl(baseUrl: string, cloneUrl: string | undefined) {
        return cloneUrl ? 'sourcetree://cloneRepo?type=stash&cloneUrl=' + encodeURI(cloneUrl) + '&baseWebUrl=' + baseUrl : undefined;
    }
}
