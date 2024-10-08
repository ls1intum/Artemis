import { TestBed } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { FileTypeService } from 'app/exercises/programming/shared/service/file-type.service';
import { provideHttpClient } from '@angular/common/http';

describe('FileTypeService', () => {
    let service: FileTypeService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
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
});
