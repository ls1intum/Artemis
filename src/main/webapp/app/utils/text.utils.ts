export const escapeString = (input: string): string => input.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');

export const convertToHtmlLinebreaks = (input: string): string => input.replace(/(?:\r\n|\r|\n)/g, '<br>');

/**
 * If the string has more characters than the defined maximum length, the string is cut and an ellipsis is added.
 * @param input The string to shorten (if needed)
 * @param maxLength The maximum length in characters
 */
export const abbreviateString = (input: string, maxLength: number): string => {
    return input.length > maxLength ? input.substring(0, maxLength) + '…' : input;
};

/**
 * Returns a pseudo-random numeric value for a given string using a simple hash function.
 * @param {string} str - The string used for the hash function.
 */
export const deterministicRandomValueFromString = (str: string): number => {
    let seed = 0;
    for (let i = 0; i < str.length; i++) {
        seed = str.charCodeAt(i) + ((seed << 5) - seed);
    }
    const m = 0x80000000;
    const a = 1103515245;
    const c = 42718;

    seed = (a * seed + c) % m;

    return seed / (m - 1);
};

/**
 * Returns 2 capitalized initials of a given string.
 * If it has multiple names, it takes the first and last (Albert Berta Muster -> AM)
 * If it has one name, it'll return a deterministic random other string (Albert -> AB)
 * If it consists of a single letter it will return the single letter.
 * @param {string} username - The string used to generate the initials.
 */
export const getInitialsFromString = (username: string): string => {
    const parts = username.trim().split(/\s+/);

    let initials = '';

    if (parts.length > 1) {
        // Takes first and last word in string and returns their initials.
        initials = parts[0][0] + parts[parts.length - 1][0];
    } else {
        // If only one single word, it will take the first letter and a random second.
        initials = parts[0][0];
        const remainder = parts[0].slice(1);
        const secondInitial = remainder.match(/[a-zA-Z0-9]/);
        if (secondInitial) {
            initials += secondInitial[Math.floor(deterministicRandomValueFromString(username) * secondInitial.length)];
        }
    }

    return initials.toUpperCase();
};
