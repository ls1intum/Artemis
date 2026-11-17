/** The hosts that images of rendered markdown may come from, as configured on the server. Empty means that every host is allowed. */
let allowedImageHosts: string[] = [];

/**
 * Sets the hosts that images of rendered markdown may come from, from the comma separated setting of the server (`info.allowedImageHosts`). An entry that starts with
 * `*.` stands for every subdomain of the host after it. The host of the page itself is always allowed. An empty setting allows every host.
 * @param configuredHosts the comma separated hosts, or undefined
 */
export function setAllowedImageHosts(configuredHosts: string | undefined): void {
    allowedImageHosts = (configuredHosts ?? '')
        .split(',')
        .map((host) => host.trim().toLowerCase())
        .filter((host) => host.length > 0);
}

/**
 * Whether an image with this address may be shown. Relative addresses, addresses of the page itself and inline images always may, an image of another host only if the
 * server allows the host.
 * @param src the value of the `src` attribute of the image
 */
export function isImageSourceAllowed(src: string | null): boolean {
    if (allowedImageHosts.length === 0 || !src) {
        return true;
    }
    let url: URL;
    try {
        url = new URL(src, window.location.href);
    } catch {
        return false;
    }
    if (url.protocol === 'data:' || url.protocol === 'blob:') {
        return true;
    }
    if (url.protocol !== 'http:' && url.protocol !== 'https:') {
        return false;
    }
    const host = url.hostname.toLowerCase();
    return (
        host === window.location.hostname.toLowerCase() || allowedImageHosts.some((allowed) => (allowed.startsWith('*.') ? host.endsWith(allowed.substring(1)) : allowed === host))
    );
}

/**
 * Splits the value of a `srcset` attribute into the addresses of its candidates, following the parsing rules of the HTML standard: an address ends at white space, and an
 * address that ends with commas is a candidate without a descriptor. A comma inside an address, as in a `data:` address, is part of the address.
 * @param srcset the value of a `srcset` attribute
 */
export function srcsetAddresses(srcset: string): string[] {
    const addresses: string[] = [];
    const length = srcset.length;
    let position = 0;
    while (position < length) {
        while (position < length && (isHtmlWhitespace(srcset[position]) || srcset[position] === ',')) {
            position++;
        }
        if (position >= length) {
            break;
        }
        const start = position;
        while (position < length && !isHtmlWhitespace(srcset[position])) {
            position++;
        }
        let address = srcset.substring(start, position);
        if (address.endsWith(',')) {
            // a loop and not a regular expression: one that looks for the commas at the end tries every comma of a long run in the middle as well
            let end = address.length;
            while (end > 0 && address[end - 1] === ',') {
                end--;
            }
            address = address.substring(0, end);
        } else {
            position = skipDescriptors(srcset, position);
        }
        if (address) {
            addresses.push(address);
        }
    }
    return addresses;
}

/** The white space of HTML: not the white space of JavaScript, which also holds characters like U+FEFF that HTML treats as part of an address. */
function isHtmlWhitespace(character: string): boolean {
    return character === ' ' || character === '\t' || character === '\n' || character === '\f' || character === '\r';
}

/**
 * Moves behind the descriptors of a candidate, with the states of the HTML standard: a comma ends the candidate except inside parentheses, and the first closing
 * parenthesis leaves them again, whatever was opened before.
 * @param srcset the value of a `srcset` attribute
 * @param from the position behind the address of the candidate
 * @returns the position behind the descriptors, which is the start of the next candidate or the end of the value
 */
function skipDescriptors(srcset: string, from: number): number {
    const length = srcset.length;
    let position = from;
    while (position < length && isHtmlWhitespace(srcset[position])) {
        position++;
    }
    let state: 'descriptor' | 'parentheses' | 'after' = 'descriptor';
    while (position < length) {
        const character = srcset[position];
        if (state === 'descriptor') {
            if (isHtmlWhitespace(character)) {
                state = 'after';
            } else if (character === ',') {
                return position + 1;
            } else if (character === '(') {
                state = 'parentheses';
            }
            position++;
        } else if (state === 'parentheses') {
            if (character === ')') {
                state = 'descriptor';
            }
            position++;
        } else if (isHtmlWhitespace(character)) {
            position++;
        } else {
            // the next descriptor starts, which is read again in the state for descriptors
            state = 'descriptor';
        }
    }
    return position;
}

/**
 * The declarations of a `style` attribute that cannot load an image. A declaration with a function that takes an address, such as `url()` or `image-set()`, is left out,
 * and so is every declaration with a backslash or a comment, as an escape can spell a function name in a way that a text search does not see.
 * @param style the value of a `style` attribute
 */
export function styleWithoutImages(style: string): string {
    return style
        .split(';')
        .map((declaration) => declaration.trim())
        .filter((declaration) => declaration.length > 0 && !/\\|\/\*|@import|(?:url|image-set|image|element|cross-fade|paint|src)\s*\(/i.test(declaration))
        .join(';');
}

/**
 * Whether every candidate of a `srcset` attribute may be shown, see `isImageSourceAllowed`.
 * @param srcset the value of a `srcset` attribute
 */
export function isImageSourceSetAllowed(srcset: string | null): boolean {
    if (allowedImageHosts.length === 0 || !srcset) {
        return true;
    }
    return srcsetAddresses(srcset).every((address) => isImageSourceAllowed(address));
}

/**
 * Whether an image host list is configured, so that attributes that load an image have to be checked.
 */
export function hasImageHostRestriction(): boolean {
    return allowedImageHosts.length > 0;
}
