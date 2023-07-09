import { Injectable } from '@angular/core';

export interface Link {
    type: string;
    value: string;
    isLink?: boolean;
    href: string;
    start?: number;
    end?: number;
    isLinkPreviewRemoved?: boolean;
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
        // eslint-disable-next-line no-useless-escape
        const urlRegex = /https?:\/\/[^\s/$.?#>][^\s>]*?(?=\s|[\]\)]|$)/g;

        // Find all URL matches in the text (in the content of the post)
        let match;
        while ((match = urlRegex.exec(text)) !== null) {
            const url = match[0];
            const start = match.index;
            const end = start + url.length;

            // Check if url is wrapped in <> tags
            const isRemoved = text[start - 1] === '<' && text[end] === '>';
            const linkableItem: Link = {
                type: 'url',
                value: url,
                isLink: true,
                href: url,
                start,
                end,
                isLinkPreviewRemoved: isRemoved,
            };

            if (!isRemoved) {
                linkableItems.push(linkableItem);
            }
        }

        return linkableItems;
    }
}
