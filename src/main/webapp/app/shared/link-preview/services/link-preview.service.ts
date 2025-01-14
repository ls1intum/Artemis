import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, shareReplay } from 'rxjs/operators';
import { Link } from 'app/shared/link-preview/services/linkify.service';

export interface LinkPreview {
    title: string;
    description: string;
    image: string;
    url: string;
    shouldPreviewBeShown?: boolean;
}

@Injectable()
export class LinkPreviewService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/link-preview';

    // object used to store the link preview data as observables, with the URL of the link as the key
    private cache: { [url: string]: Observable<LinkPreview> } = {};

    links: Link[] = [];

    fetchLink(url: string): Observable<LinkPreview> {
        if (this.cache[url]) {
            return this.cache[url];
        }

        const preview$ = this.http.post(this.resourceUrl, url).pipe(
            map((value) => value as LinkPreview),
            shareReplay(1),
        );

        this.cache[url] = preview$;
        return preview$;
    }
}
