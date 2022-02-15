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
        .slice(0, 12);
}

export function randomArrayValue(array) {
    return array[Math.floor(Math.random() * array.length)];
}

export function extractDestination(message) {
    return extractHeader(message, 'destination');
}

export function extractHeader(message, header) {
    const headers = extractSTOMPHeaders(message);
    return headers.match('(?:.*:.*|\\n)*' + header + ':(.*)\\n(?:.*:.*|\\n)*')[1];
}

export function extractSTOMPHeaders(message) {
    return message.match('MESSAGE\\n((?:.|\\n)*)\\n\\n(?:(?:.|\\n)*)')[1];
}

export function extractMessageContent(message) {
    return message.match('MESSAGE\n(?:.|\\n)*\\n\\n((?:.|\\n)*)\u0000')[1];
}
