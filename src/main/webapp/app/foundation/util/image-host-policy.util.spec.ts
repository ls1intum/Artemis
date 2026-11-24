import { afterEach, describe, expect, it } from 'vitest';

import { isImageSourceAllowed, setAllowedImageHosts, srcsetAddresses, styleWithoutImages } from 'app/foundation/util/image-host-policy.util';

describe('image host policy', () => {
    afterEach(() => setAllowedImageHosts(undefined));

    describe('configured hosts with special characters', () => {
        it('matches an address in either spelling of the host', () => {
            setAllowedImageHosts('bücher.example');

            expect(isImageSourceAllowed('https://bücher.example/a.png')).toBe(true);
            expect(isImageSourceAllowed('https://xn--bcher-kva.example/a.png')).toBe(true);
            expect(isImageSourceAllowed('https://other.example/a.png')).toBe(false);
        });

        it('matches the subdomains of a configured host with special characters', () => {
            setAllowedImageHosts('*.bücher.example');

            expect(isImageSourceAllowed('https://shop.bücher.example/a.png')).toBe(true);
            expect(isImageSourceAllowed('https://bücher.example/a.png')).toBe(false);
        });

        it('lists the host in its ASCII form as well', () => {
            setAllowedImageHosts('xn--bcher-kva.example');

            expect(isImageSourceAllowed('https://bücher.example/a.png')).toBe(true);
        });

        it('keeps the sharp s and the final sigma, which are other domains than ss and the ordinary sigma', () => {
            setAllowedImageHosts('faß.example, βόλος.example');

            expect(isImageSourceAllowed('https://xn--fa-hia.example/a.png')).toBe(true);
            expect(isImageSourceAllowed('https://fass.example/a.png')).toBe(false);
            expect(isImageSourceAllowed('https://xn--nxasmm1c.example/a.png')).toBe(true);
            expect(isImageSourceAllowed('https://xn--nxasmq6b.example/a.png')).toBe(false);
        });

        it('does not widen an entry that is no plain host name', () => {
            setAllowedImageHosts('images.example.net:8443, images.example.org/path');

            expect(isImageSourceAllowed('https://images.example.net/a.png')).toBe(false);
            expect(isImageSourceAllowed('https://images.example.net:8443/a.png')).toBe(false);
            expect(isImageSourceAllowed('https://images.example.org/a.png')).toBe(false);
        });
    });

    describe('styleWithoutImages', () => {
        it('keeps a semicolon inside a string as it was written', () => {
            expect(styleWithoutImages("font-family:'Research; Group',serif; color: red")).toBe("font-family:'Research; Group',serif;color: red");
            expect(styleWithoutImages('font-family:"Research; Group"')).toBe('font-family:"Research; Group"');
        });

        it('leaves out a declaration whose address contains a semicolon', () => {
            expect(styleWithoutImages('color: red; background: url(data:image/png;base64,AAAA); margin: 0')).toBe('color: red;margin: 0');
        });

        it('leaves out the declaration after a string that is not closed, together with the string', () => {
            expect(styleWithoutImages("color: red; font-family:'broken; background: url(https://unlisted.example/p.png)")).toBe('color: red');
        });

        it('keeps the declarations after one with an escaped quote, and leaves that one out', () => {
            expect(styleWithoutImages('font-family:"Research \\" Group"; color: red')).toBe('color: red');
        });

        it('does not let an escaped quote hide a function that follows inside the same string', () => {
            expect(styleWithoutImages('font-family:"a\\"; background: url(https://unlisted.example/p.png)"; color: red')).toBe('color: red');
        });

        it('reads an escaped semicolon as part of its declaration', () => {
            expect(styleWithoutImages('content: a\\;b; color: red')).toBe('color: red');
        });

        it('keeps declarations without images apart', () => {
            expect(styleWithoutImages(' color: red ;; margin: 0 ;')).toBe('color: red;margin: 0');
        });
    });

    describe('srcsetAddresses', () => {
        it('reads the addresses and skips the descriptors', () => {
            expect(srcsetAddresses('a.png 1x, b.png 2x,c.png')).toEqual(['a.png', 'b.png', 'c.png']);
        });

        it('keeps a comma inside an address and removes the commas at its end', () => {
            expect(srcsetAddresses('data:image/png;base64,AAAA 1x, b.png,, c.png')).toEqual(['data:image/png;base64,AAAA', 'b.png', 'c.png']);
        });

        it('does not end a candidate at a comma inside parentheses of a descriptor', () => {
            expect(srcsetAddresses('a.png (1x, 2x), b.png 2x')).toEqual(['a.png', 'b.png']);
        });

        it('reads a value with nothing but separators', () => {
            expect(srcsetAddresses(' ,, \n')).toEqual([]);
        });
    });
});
