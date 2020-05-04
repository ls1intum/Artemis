// https://stackoverflow.com/questions/10726909/random-alpha-numeric-string-in-javascript
const randString = function randomString(length, chars) {
    let result = '';
    for (let i = length; i > 0; --i) result += chars[Math.floor(Math.random() * chars.length)];
    return result;
};

export function nextAlphanumeric(length) {
    // helpers for random titles
    let allowedChars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';

    return randString(length, allowedChars);
}

export function nextWSSubscriptionId() {
    return Math.random()
        .toString(36)
        .replace(/[^a-z]+/g, '')
        .substr(0, 12);
}

export function randomArrayValue(array) {
    return array[Math.floor(Math.random() * array.length)];
}
