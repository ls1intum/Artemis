/**
 * Prepares a string for insertion into a regex.
 * Example: [test].*[/test] -> \[test\].*\[\/test\]
 * @param s
 */
export const escapeStringForUseInRegex = (s: string) => {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
};
