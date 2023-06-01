import { EventEmitter, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
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

    onLinkFound: EventEmitter<Array<Link>> = new EventEmitter<Array<Link>>();

    links: Link[] = [];

    constructor(private http: HttpClient) {
        this.onLinkFound.subscribe((links: Array<Link>) => (this.links = links));
    }

    fetchLink(url: string): Observable<LinkPreview> {
        console.log('fetching the following link: ', url);

        return this.http.get(this.resourceUrl).pipe(map((value) => value as LinkPreview));
    }
}
