import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class SourceTreeService {
    constructor(private httpClient: HttpClient) {}

    /**
     * Build source tree url.
     * @param baseUrl - the base url of the version control system (e.g. Bitbucket or Bamboo)
     * @param cloneUrl - url of the target.
     */
    buildSourceTreeUrl(baseUrl: string, cloneUrl: string | undefined) {
        return cloneUrl ? 'sourcetree://cloneRepo?type=stash&cloneUrl=' + encodeURI(cloneUrl) + '&baseWebUrl=' + baseUrl : undefined;
    }

    /**
     * Return password of the repository.
     */
    getRepositoryPassword(): Observable<Object> {
        return this.httpClient.get(`${SERVER_API_URL}/api/account/password`);
    }
}
