import { Injectable } from '@angular/core';
import * as linkify from 'linkifyjs';
import linkifyStr from 'linkify-string';
import { Link, LinkifyOptions } from 'app/shared/link-preview/linkify/interfaces/linkify.interface';

@Injectable()
export class LinkifyService {
    constructor() {}

    /**
     * Convert the passed text as a string to an appropriate url
     *
     * @param text - the string to convert
     * @param options - options to pass it to the linkifyjs library
     */
    linkify(text: string, options?: LinkifyOptions): string {
        return linkifyStr(text, options);
    }

    /**
     * Find any links in a given text as a string
     *
     * @param text - the string to find some links
     */
    find(text: string): Array<Link> {
        return linkify.find(text);
    }

    /**
     * Test if a given value is a link or an array of all links
     *
     * @param value - the value to test
     */
    test(value: string | string[]): boolean {
        if (typeof value === 'string') {
            return linkify.test(value);
        }
        return value.find((v) => !linkify.test(v)) === undefined;
    }
}
