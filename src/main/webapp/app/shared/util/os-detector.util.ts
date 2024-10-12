export function getOS(): string {
    const userAgent = window.navigator.userAgent;

    if (userAgent.indexOf('Win') !== -1) {
        return 'Windows';
    } else if (userAgent.indexOf('Mac') !== -1) {
        return 'MacOS';
    } else if (userAgent.indexOf('Linux') !== -1) {
        return 'Linux';
    } else if (/Android/.test(userAgent)) {
        return 'Android';
    } else if (/iPhone|iPad|iPod/.test(userAgent)) {
        return 'iOS';
    } else {
        return 'Unknown';
    }
}
