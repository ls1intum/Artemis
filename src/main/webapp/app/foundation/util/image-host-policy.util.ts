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
