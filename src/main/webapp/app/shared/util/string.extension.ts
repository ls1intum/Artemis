export {};

declare global {
    export interface String {
        replaceAll(search: string, replacement: string): string;
    }
}

if (!String.prototype.replaceAll) {
    String.prototype.replaceAll = function (search, replacement) {
        return this.replace(new RegExp(search, 'g'), replacement);
    };
}
