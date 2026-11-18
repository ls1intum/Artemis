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
        .map((host) => normalizeHost(host.trim().toLowerCase()))
        .filter((host) => host.length > 0);
}

/**
 * The form of a configured host that the browser compares with: a host name with special characters is in its ASCII form (`xn--...`) in the address of an image, and
 * only there. An entry that is no plain host name, for example one with a port or a path, stays as it is written, so it does not match more than it says.
 * @param host the configured host, in lower case, which may start with `*.`
 */
function normalizeHost(host: string): string {
    const prefix = host.startsWith('*.') ? '*.' : '';
    try {
        const url = new URL(`https://${host.substring(prefix.length)}`);
        const isPlainHost = url.host === url.hostname && url.pathname === '/' && !url.search && !url.hash && !url.username && !url.password;
        if (isPlainHost && url.hostname) {
            return prefix + url.hostname;
        }
    } catch {
        // an entry that cannot be read as a host name stays as it is written
    }
    return host;
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
    let position = skipWhile(srcset, 0, isSeparator);
    while (position < srcset.length) {
        const start = position;
        position = skipWhile(srcset, position, (character) => !isHtmlWhitespace(character));
        let address = srcset.substring(start, position);
        if (address.endsWith(',')) {
            address = withoutTrailingCommas(address);
        } else {
            position = skipDescriptors(srcset, position);
        }
        if (address) {
            addresses.push(address);
        }
        position = skipWhile(srcset, position, isSeparator);
    }
    return addresses;
}

/** The white space of HTML: not the white space of JavaScript, which also holds characters like U+FEFF that HTML treats as part of an address. */
function isHtmlWhitespace(character: string): boolean {
    return character === ' ' || character === '\t' || character === '\n' || character === '\f' || character === '\r';
}

/** What lies between two candidates of a `srcset`. */
function isSeparator(character: string): boolean {
    return isHtmlWhitespace(character) || character === ',';
}

/** The position of the first character from `from` on that `matches` does not accept, which is the end of the text if there is none. */
function skipWhile(text: string, from: number, matches: (character: string) => boolean): number {
    let position = from;
    while (position < text.length && matches(text[position])) {
        position++;
    }
    return position;
}

/** A loop and not a regular expression: one that looks for the commas at the end tries every comma of a long run in the middle as well. */
function withoutTrailingCommas(address: string): string {
    let end = address.length;
    while (end > 0 && address[end - 1] === ',') {
        end--;
    }
    return address.substring(0, end);
}

type DescriptorState = 'descriptor' | 'parentheses' | 'after';

/**
 * Moves behind the descriptors of a candidate, with the states of the HTML standard: a comma ends the candidate except inside parentheses, and the first closing
 * parenthesis leaves them again, whatever was opened before.
 * @param srcset the value of a `srcset` attribute
 * @param from the position behind the address of the candidate
 * @returns the position behind the descriptors, which is the start of the next candidate or the end of the value
 */
function skipDescriptors(srcset: string, from: number): number {
    let position = skipWhile(srcset, from, isHtmlWhitespace);
    let state: DescriptorState = 'descriptor';
    while (position < srcset.length) {
        const character = srcset[position];
        if (state === 'after' && !isHtmlWhitespace(character)) {
            // the next descriptor starts, which is read again in the state for descriptors
            state = 'descriptor';
            continue;
        }
        if (state === 'descriptor' && character === ',') {
            return position + 1;
        }
        state = nextDescriptorState(state, character);
        position++;
    }
    return position;
}

function nextDescriptorState(state: DescriptorState, character: string): DescriptorState {
    if (state === 'parentheses') {
        return character === ')' ? 'descriptor' : 'parentheses';
    }
    if (state === 'descriptor') {
        if (isHtmlWhitespace(character)) {
            return 'after';
        }
        return character === '(' ? 'parentheses' : 'descriptor';
    }
    return 'after';
}

/**
 * Splits a `style` attribute at the semicolons that end a declaration, and not at those inside a string or inside parentheses, so that a value like `'Research; Group'`
 * stays as it was written.
 * @param style the value of a `style` attribute
 */
function splitDeclarations(style: string): string[] {
    const declarations: string[] = [];
    let quote: string | undefined;
    let depth = 0;
    let start = 0;
    for (let position = 0; position < style.length; position++) {
        const character = style[position];
        if (quote) {
            quote = character === quote ? undefined : quote;
        } else if (character === '"' || character === "'") {
            quote = character;
        } else if (character === '(') {
            depth++;
        } else if (character === ')' && depth > 0) {
            depth--;
        } else if (character === ';' && depth === 0) {
            declarations.push(style.substring(start, position));
            start = position + 1;
        }
    }
    declarations.push(style.substring(start));
    return declarations;
}

/**
 * The declarations of a `style` attribute that cannot load an image. A declaration with a function that takes an address, such as `url()` or `image-set()`, is left out,
 * and so is every declaration with a backslash or a comment, as an escape can spell a function name in a way that a text search does not see.
 * @param style the value of a `style` attribute
 */
export function styleWithoutImages(style: string): string {
    return splitDeclarations(style)
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
