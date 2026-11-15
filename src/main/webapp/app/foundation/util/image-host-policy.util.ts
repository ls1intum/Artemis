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
    let position = 0;
    while (position < srcset.length) {
        while (position < srcset.length && /[\s,]/.test(srcset[position])) {
            position++;
        }
        if (position >= srcset.length) {
            break;
        }
        const start = position;
        while (position < srcset.length && !/\s/.test(srcset[position])) {
            position++;
        }
        let address = srcset.substring(start, position);
        if (address.endsWith(',')) {
            address = address.replace(/,+$/, '');
        } else {
            // skip the descriptor, which may contain a comma only inside parentheses
            let depth = 0;
            while (position < srcset.length && (srcset[position] !== ',' || depth > 0)) {
                if (srcset[position] === '(') depth++;
                if (srcset[position] === ')' && depth > 0) depth--;
                position++;
            }
        }
        if (address) {
            addresses.push(address);
        }
    }
    return addresses;
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
