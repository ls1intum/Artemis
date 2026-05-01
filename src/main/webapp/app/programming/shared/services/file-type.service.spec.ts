import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { FileTypeService } from 'app/programming/shared/services/file-type.service';
import { provideHttpClient } from '@angular/common/http';

describe('FileTypeService', () => {
    let service: FileTypeService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(FileTypeService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([
        { content: '', isBinary: false },
        { content: 'this is ordinary text', isBinary: false },
        { content: 'this contains\0 a 0-byte', isBinary: true },
        { content: 'more\x01\x02\x03 binary content', isBinary: true },
        { content: 'newlines\n, tabs \t, and carriage returns\r do not indicate binaries', isBinary: false },
    ])('should correctly identify binary file content', ({ content, isBinary }) => {
        const result = service.isBinaryContent(content);
        expect(result).toBe(isBinary);
    });

    it.each([
        { fileName: 'logo.png', isImage: true, mimeType: 'image/png' },
        { fileName: 'icon.PNG', isImage: true, mimeType: 'image/png' },
        { fileName: 'photo.jpg', isImage: true, mimeType: 'image/jpeg' },
        { fileName: 'photo.jpeg', isImage: true, mimeType: 'image/jpeg' },
        { fileName: 'animated.gif', isImage: true, mimeType: 'image/gif' },
        { fileName: 'old.bmp', isImage: true, mimeType: 'image/bmp' },
        { fileName: 'modern.webp', isImage: true, mimeType: 'image/webp' },
        { fileName: 'vector.svg', isImage: true, mimeType: 'image/svg+xml' },
        { fileName: 'favicon.ico', isImage: true, mimeType: 'image/x-icon' },
        { fileName: 'src/path/to/picture.png', isImage: true, mimeType: 'image/png' },
        { fileName: 'README.md', isImage: false, mimeType: undefined },
        { fileName: 'archive.tar.gz', isImage: false, mimeType: undefined },
        { fileName: 'noextension', isImage: false, mimeType: undefined },
        { fileName: '', isImage: false, mimeType: undefined },
    ])('should detect image files by extension', ({ fileName, isImage, mimeType }) => {
        expect(service.isImageFile(fileName)).toBe(isImage);
        expect(service.getImageMimeType(fileName)).toBe(mimeType);
    });
});
