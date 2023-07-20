import { Injectable } from '@angular/core';

export interface Link {
    type: string;
    value: string;
    isLink?: boolean;
    href: string;
    start?: number;
    end?: number;
}

@Injectable()
export class LinkifyService {
    /**
     * Find any links in a given text as a string
     *
     * @param text - the string to find some links
     */
    find(text: string): Link[] {
        const linkableItems: Link[] = [];

        // Regular expression pattern to match URLs
        const urlRegex = /https?:\/\/[^\s/$.?#].[^\s]*/g;

        // Find all URL matches in the text (in the content of the post)
        let match;
        while ((match = urlRegex.exec(text)) !== null) {
            const url = match[0];
            const start = match.index;
            const end = start + url.length;

            const linkableItem = {
                type: 'url',
                value: url,
                isLink: true,
                href: url,
                start,
                end,
            };

            linkableItems.push(linkableItem);
        }

        return linkableItems;
    }
}
