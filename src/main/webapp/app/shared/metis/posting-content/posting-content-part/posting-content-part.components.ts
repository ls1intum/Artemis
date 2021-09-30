import { Component, Input } from '@angular/core';
import { PostingContentPart } from '../../metis.util';

@Component({
    selector: 'jhi-posting-content-part',
    templateUrl: './posting-content-part.component.html',
    styleUrls: ['./posting-content-part.component.scss'],
})
export class PostingContentPartComponent {
    @Input() postingContentPart: PostingContentPart;

    // Only allow certain html tags and attributes
    allowedHtmlTags: string[] = ['a', 'b', 'blockquote', 'code', 'del', 'em', 'i', 'ins', 'li', 'mark', 'ol', 'p', 'pre', 'small', 'span', 'strong', 'sub', 'sup', 'ul'];
    allowedHtmlAttributes: string[] = ['href'];

    constructor() {}
}
