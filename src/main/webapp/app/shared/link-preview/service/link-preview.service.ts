import { EventEmitter, Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, shareReplay } from 'rxjs/operators';
import { Link } from 'app/shared/link-preview/linkify/interfaces/linkify.interface';

export interface LinkPreview {
    title: string;
    description: string;
    image: string;
    url: string;
}
@Injectable()
export class LinkPreviewService {
    public resourceUrl = 'api/link-preview';

    private linkPreview$?: Observable<LinkPreview>;
    onLinkFound: EventEmitter<Array<Link>> = new EventEmitter<Array<Link>>();

    links: Link[] = [];

    constructor(private http: HttpClient) {
        console.log('fetching LinkPreviewService  : ');
        this.onLinkFound.subscribe((links: Array<Link>) => (this.links = links));
    }

    fetchLink(url: string): Observable<LinkPreview> {
        console.log('fetching the following link: ', url);

        return this.http.post(this.resourceUrl, url).pipe(map((value) => value as LinkPreview));
    }

    // TODO: implement caching and re-check why the preview is the same all the time
    // fetchLink(url: string): Observable<LinkPreview> {
    //     console.log('fetching the following link: ', url);
    //
    //     if (!this.linkPreview$) {
    //         this.linkPreview$ = this.http.post(this.resourceUrl, url, { observe: 'response' }).pipe(
    //             map((res: HttpResponse<LinkPreview>) => {
    //                 return res.body!;
    //             }),
    //             shareReplay({ bufferSize: 1, refCount: true }),
    //         );
    //     }
    //
    //     return this.linkPreview$;
    // }
}
