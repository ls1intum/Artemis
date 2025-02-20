import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { shareReplay } from 'rxjs/operators';
import { Link } from 'app/shared/link-preview/services/linkify.service';

export interface LinkPreview {
    title: string;
    description: string;
    image: string;
    url: string;
    shouldPreviewBeShown?: boolean;
}

@Injectable({ providedIn: 'root' })
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

        // Encode the URL to ensure special characters are properly handled
        const encodedUrl = encodeURIComponent(url);
        const params = new HttpParams().set('url', encodedUrl);

        const preview$ = this.http.get<LinkPreview>(this.resourceUrl, { params }).pipe(shareReplay(1));

        this.cache[url] = preview$;
        return preview$;
    }
}
