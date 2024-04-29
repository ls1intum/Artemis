import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { FileTypeService } from 'app/exercises/programming/shared/service/file-type.service';

describe('FileTypeService', () => {
    let service: FileTypeService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
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
