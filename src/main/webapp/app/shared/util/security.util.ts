export const safeUnescape = (s: string) => {
    const parser = new DOMParser();
    return parser.parseFromString(s, 'text/html').body.textContent;
};
