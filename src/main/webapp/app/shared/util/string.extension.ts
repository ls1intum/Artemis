export {};

declare global {
    /**
     * Extends the String prototype with a replaceAll method,
     * allowing replacement of all matches of a substring or regular expression.
     *
     * @param search The string or regular expression to search for.
     * @param replacement The string to replace each match with.
     * @returns A new string with all occurrences replaced.
     */
    export interface String {
        replaceAll(search: string | RegExp, replacement: string): string;
    }
}

if (!String.prototype.replaceAll) {
    Object.defineProperty(String.prototype, 'replaceAll', {
        value: function (search: string | RegExp, replacement: string): string {
            // Handle RegExp: ensure it has the 'g' (global) flag to replace all matches.
            if (search instanceof RegExp) {
                // If 'g' is missing, add it to avoid only replacing the first match.
                const flags = search.flags.includes('g') ? search.flags : search.flags + 'g';
                const globalRegex = new RegExp(search.source, flags);

                // Use String.prototype.replace with the global RegExp
                return this.replace(globalRegex, replacement);
            } else {
                // Escape special characters in the search string to use it safely in a RegExp
                const escapedSearch = search.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

                // Create a global RegExp from the escaped string
                const regex = new RegExp(escapedSearch, 'g');

                // Replace all matches of the string
                return this.replace(regex, replacement);
            }
        },
        enumerable: false, // Do not enumerate this method in for...in loops
        writable: true, // Allow reassignment if needed
        configurable: true, // Allow deletion or redefinition
    });
}
