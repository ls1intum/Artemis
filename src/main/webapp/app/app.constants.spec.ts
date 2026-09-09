import { describe, expect, it } from 'vitest';
import { FILES_PATH_PREFIX, addPublicFilePrefix } from 'app/app.constants';

describe('addPublicFilePrefix', () => {
    it('should return undefined for a missing reference', () => {
        expect(addPublicFilePrefix(undefined)).toBeUndefined();
        expect(addPublicFilePrefix('')).toBeUndefined();
    });

    it('should prefix a reference as the server sends it', () => {
        expect(addPublicFilePrefix('courses/3/icons/icon.png')).toBe(`${FILES_PATH_PREFIX}courses/3/icons/icon.png`);
        expect(addPublicFilePrefix('users/7/profile-pictures/me.png')).toBe(`${FILES_PATH_PREFIX}users/7/profile-pictures/me.png`);
        expect(addPublicFilePrefix('attachments/lectures/4/script.pdf')).toBe(`${FILES_PATH_PREFIX}attachments/lectures/4/script.pdf`);
    });

    it('should leave a reference to somewhere else alone', () => {
        // An attachment may point at a document hosted elsewhere, and the Iris bot picture is a static asset shipped with the client.
        expect(addPublicFilePrefix('https://example.org/lecture-notes.pdf')).toBe('https://example.org/lecture-notes.pdf');
        expect(addPublicFilePrefix('/public/images/iris/iris-logo.png')).toBe('/public/images/iris/iris-logo.png');
    });

    it('should leave a locally held preview of a not-yet-uploaded file alone', () => {
        expect(addPublicFilePrefix('blob:http://localhost:9000/9c8e')).toBe('blob:http://localhost:9000/9c8e');
    });

    it('should leave an already prefixed value alone rather than doubling the prefix', () => {
        // A server that has been upgraded past this release sends the complete URL; prefixing it again would request a path that does not exist.
        expect(addPublicFilePrefix(`${FILES_PATH_PREFIX}courses/3/icons/icon.png`)).toBe(`${FILES_PATH_PREFIX}courses/3/icons/icon.png`);
    });
});
