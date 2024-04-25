export {};

declare global {
    export interface String {
        replaceAll(search: string, replacement: string): string;
    }
}

if (!String.prototype.replaceAll) {
    Object.defineProperty(String.prototype, 'replaceAll', {
        value: function (search: string, replacement: string): string {
            // Ensure the search string is escaped to avoid issues with special RegExp characters.
            const escapedSearch = search.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
            return this.replace(new RegExp(escapedSearch, 'g'), replacement);
        },
        enumerable: false,
        writable: true,
        configurable: true,
    });
}
