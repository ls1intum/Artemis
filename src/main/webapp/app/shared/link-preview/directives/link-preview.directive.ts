import { Directive, OnInit } from '@angular/core';
import { fromEvent } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';

import { LinkPreviewService } from 'app/shared/link-preview/service/link-preview.service';
import { LinkifyService } from 'app/shared/link-preview/linkify/services/linkify.service';
import { Link } from 'app/shared/link-preview/linkify/interfaces/linkify.interface';

@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[linkPreview]',
    exportAs: '[linkPreview]',
})
export class LinkPreviewDirective implements OnInit {
    constructor(public linkifyService: LinkifyService, public linkPreviewService: LinkPreviewService) {}

    ngOnInit(): void {
        console.log('init : ');
        this._init();
    }

    private _init() {
        fromEvent(document, 'input')
            .pipe(
                debounceTime(2000),
                distinctUntilChanged(),
                map((event) => {
                    console.log('event: ', event);

                    const data = event.target ? event.target['value'] : undefined;
                    const links: Link[] = this.linkifyService.find(data);
                    console.log('data: ', data);
                    console.log('links: ', links);
                    // event.target['value'] = this.linkifyService.linkify(data)
                    return links;
                }),
            )
            .subscribe((links) => {
                console.log('emitting links: ', links);
                this.linkPreviewService.onLinkFound.emit(links);
            });
    }
}
