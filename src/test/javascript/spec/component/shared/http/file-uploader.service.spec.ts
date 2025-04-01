import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAX_FILE_SIZE, MAX_FILE_SIZE_COMMUNICATION } from '../../../../../../main/webapp/app/shared/constants/input.constants';
import { FileUploaderService } from 'app/shared/service/file-uploader.service';

describe('FileUploaderService', () => {
    let service: FileUploaderService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(FileUploaderService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should upload a regular file for the markdown editor', async () => {
        const file = new File([''], 'test.pdf', { type: 'application/pdf' });
        const expectedResponse = { path: 'some-path' };
        const promise = service.uploadMarkdownFile(file);

        const request = httpMock.expectOne({ method: 'POST', url: '/api/core/markdown-file-upload' });
        request.flush(expectedResponse);

        httpMock.verify();
        await expect(promise).resolves.toEqual(expectedResponse);
    });

    it('should upload a regular file for communication', async () => {
        const file = new File([''], 'test.pdf', { type: 'application/pdf' });
        const expectedResponse = { path: 'some-path' };
        const promise = service.uploadMarkdownFileInCurrentMetisConversation(file, 1, 2);

        const request = httpMock.expectOne({ method: 'POST', url: '/api/core/files/courses/1/conversations/2' });
        request.flush(expectedResponse);

        httpMock.verify();
        await expect(promise).resolves.toEqual(expectedResponse);
    });

    it('should reject if the course for communication is not specified', async () => {
        const file = new File([''], 'test.pdf', { type: 'application/pdf' });
        await expect(service.uploadMarkdownFileInCurrentMetisConversation(file, undefined, 2)).rejects.toThrow(Error);
    });

    it('should reject if the conversation for communication is not specified', async () => {
        const file = new File([''], 'test.pdf', { type: 'application/pdf' });
        await expect(service.uploadMarkdownFileInCurrentMetisConversation(file, 1, undefined)).rejects.toThrow(Error);
    });

    it('should reject files with unsupported extensions', async () => {
        const file = new File([''], 'test.docx', { type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' });
        await expect(service.uploadMarkdownFile(file)).rejects.toThrow(Error);
    });

    it('should reject files that are too large (general)', async () => {
        const largeFile = new File([''], 'test.pdf', { type: 'application/pdf' });
        // Overwrite the size property to be larger than the maximum allowed size
        Object.defineProperty(largeFile, 'size', { value: MAX_FILE_SIZE + 1 });
        await expect(service.uploadMarkdownFile(largeFile)).rejects.toThrow(Error);
    });

    it('should reject files that are too large (communication)', async () => {
        const largeFile = new File([''], 'test.pdf', { type: 'application/pdf' });
        // Overwrite the size property to be larger than the maximum allowed size
        Object.defineProperty(largeFile, 'size', { value: MAX_FILE_SIZE_COMMUNICATION + 1 });
        await expect(service.uploadMarkdownFileInCurrentMetisConversation(largeFile, 1, 2)).rejects.toThrow(Error);
    });
});
