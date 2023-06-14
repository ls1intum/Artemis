import { Injectable } from '@angular/core';
import * as linkify from 'linkifyjs';
import { Link } from 'app/shared/link-preview/linkify/interfaces/linkify.interface';

@Injectable()
export class LinkifyService {
    constructor() {}

    /**
     * Find any links in a given text as a string
     *
     * @param text - the string to find some links
     */
    find(text: string): Array<Link> {
        return linkify.find(text);
    }
}
