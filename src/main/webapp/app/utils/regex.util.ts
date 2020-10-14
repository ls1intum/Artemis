export const matchesRegexFully = (input: string | undefined, regex: string | undefined): boolean => {
    if (!regex) {
        // count as match if the regex is not defined
        return true;
    }
    if (!input) {
        // count as no match if the input is not defined
        return false;
    }
    // we want to test for a full match, so wrap the regex in ^ and $
    if (!regex.startsWith('^')) {
        regex = '^' + regex;
    }
    if (!regex.endsWith('$')) {
        regex = regex + '$';
    }
    // string.match returns null when there is no match and an array containing matches otherwise
    const matches = input.match(new RegExp(regex));
    return !!matches;
};
