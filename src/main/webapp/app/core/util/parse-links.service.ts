import { Injectable } from '@angular/core';

/**
 * A utility service for link parsing. Note: this class comes from the jhipster template
 */
@Injectable({
    providedIn: 'root',
})
export class ParseLinks {
    /**
     * Method to parse the links
     */
    parse(header: string): { [key: string]: number } {
        if (header.length === 0) {
            throw new Error('input must not be of zero length');
        }

        // Split parts by comma
        const parts: string[] = header.split(',');
        const links: { [key: string]: number } = {};

        // Parse each part into a named link
        parts.forEach((part) => {
            const section: string[] = part.split(';');

            if (section.length !== 2) {
                throw new Error('section could not be split on ";"');
            }

            const url: string = section[0].replace(/<(.*)>/, '$1').trim();
            const queryString: { [key: string]: string | undefined } = {};

            url.replace(/([^?=&]+)(=([^&]*))?/g, ($0: string, $1: string | undefined, $2: string | undefined, $3: string | undefined) => {
                if ($1 !== undefined) {
                    queryString[$1] = $3;
                }
                return $3 ?? '';
            });

            if (queryString.page !== undefined) {
                const name: string = section[1].replace(/rel="(.*)"/, '$1').trim();
                links[name] = parseInt(queryString.page, 10);
            }
        });
        return links;
    }
}
