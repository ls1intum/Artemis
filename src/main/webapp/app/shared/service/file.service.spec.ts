import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { FileService } from 'app/shared/service/file.service';

describe('FileService', () => {
    const firstUniqueFileName = 'someOtherUniqueFileName';
    const secondUniqueFileName = 'someUniqueFileName';

    let fileService: FileService;
    let httpMock: HttpTestingController;
    let getUniqueFileNameSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), FileService],
        });
        fileService = TestBed.inject(FileService);
        httpMock = TestBed.inject(HttpTestingController);
        getUniqueFileNameSpy = jest.spyOn(fileService, 'getUniqueFileName');
    });

    describe('getFile', () => {
        it('should return a file', async () => {
            const filePath = 'path/test.png';
            const blob = new Blob(['123456789']);

            const filePromise = fileService.getFile(filePath);
            const req = httpMock.expectOne({
                url: `api/core/files/${filePath}`,
                method: 'GET',
            });
            req.flush(blob);

            const file = await filePromise;

            expect(file.size).toEqual(blob.size);
            expect(getUniqueFileNameSpy).toHaveBeenCalledExactlyOnceWith('png', undefined);
        });

        it('should return a file with unique name', async () => {
            const filePath = 'path/test.png';
            const blob = new Blob(['123456789']);
            const existingFileNames = new Map<string, { file: File; path?: string }>([
                [secondUniqueFileName + '.png', { file: new File([], secondUniqueFileName) }],
                [firstUniqueFileName + '.png', { file: new File([], firstUniqueFileName) }],
            ]);

            const filePromise = fileService.getFile(filePath, existingFileNames);
            const req = httpMock.expectOne({
                url: `api/core/files/${filePath}`,
                method: 'GET',
            });
            req.flush(blob);

            const file = await filePromise;

            expect(file.size).toEqual(blob.size);
            expect(getUniqueFileNameSpy).toHaveBeenCalledExactlyOnceWith('png', existingFileNames);
        });
    });

    describe('getTemplateFile', () => {
        it('should fetch the template file without project type', () => {
            const language = ProgrammingLanguage.JAVA;
            const expectedUrl = `api/core/files/templates/JAVA`;
            const response = 'template content';

            fileService.getTemplateFile(language).subscribe((data) => {
                expect(data).toEqual(response);
            });

            const req = httpMock.expectOne({
                url: expectedUrl,
                method: 'GET',
            });
            expect(req.request.responseType).toBe('text');
            req.flush(response);
        });

        it('should fetch the template file with project type', () => {
            const language = ProgrammingLanguage.JAVA;
            const projectType = ProjectType.PLAIN_MAVEN;
            const expectedUrl = `api/core/files/templates/JAVA/PLAIN_MAVEN`;
            const response = 'template content';

            fileService.getTemplateFile(language, projectType).subscribe((data) => {
                expect(data).toEqual(response);
            });

            const req = httpMock.expectOne({
                url: expectedUrl,
                method: 'GET',
            });
            expect(req.request.responseType).toBe('text');
            req.flush(response);
        });
    });

    describe('downloadMergedFile', () => {
        it('should download the merged PDF file', () => {
            const lectureId = 123;
            const expectedUrl = `api/core/files/attachments/lecture/${lectureId}/merge-pdf`;
            const blobResponse = new Blob(['PDF content'], { type: 'application/pdf' });

            fileService.downloadMergedFile(lectureId).subscribe((response) => {
                expect(response.body).toEqual(blobResponse);
                expect(response.status).toBe(200);
            });

            const req = httpMock.expectOne({
                url: expectedUrl,
                method: 'GET',
            });
            expect(req.request.responseType).toBe('blob');
            req.flush(blobResponse, { status: 200, statusText: 'OK' });
        });
    });

    describe('getTemplateCodeOfConduct', () => {
        it('should fetch the template code of conduct', () => {
            const expectedUrl = `api/core/files/templates/code-of-conduct`;
            const response = 'code of conduct content';

            fileService.getTemplateCodeOfConduct().subscribe((data) => {
                expect(data.body).toEqual(response);
            });

            const req = httpMock.expectOne({
                url: expectedUrl,
                method: 'GET',
            });
            expect(req.request.responseType).toBe('text');
            req.flush(response);
        });
    });

    describe('downloadFile', () => {
        it('should open a new window with the normalized URL', () => {
            const downloadUrl = 'http://example.com/files/some file name.txt';
            const encodedUrl = 'http://example.com/files/some%20file%20name.txt';
            const newWindowMock = { location: { href: '' } } as Window;

            jest.spyOn(window, 'open').mockReturnValue(newWindowMock);

            const newWindow = fileService.downloadFile(downloadUrl);
            expect(newWindow).not.toBeNull();
            expect(newWindow!.location.href).toBe(encodedUrl);
        });
    });

    describe('downloadFileByAttachmentName', () => {
        it('should open a new window with the normalized URL and attachment name', () => {
            const downloadUrl = 'http://example.com/files/attachment.txt';
            const downloadName = 'newAttachment';
            const encodedUrl = 'http://example.com/files/newAttachment.txt';
            const newWindowMock = { location: { href: '' } } as Window;

            jest.spyOn(window, 'open').mockReturnValue(newWindowMock);

            const newWindow = fileService.downloadFileByAttachmentName(downloadUrl, downloadName);
            expect(newWindow).not.toBeNull();
            expect(newWindow!.location.href).toBe(encodedUrl);
        });
    });

    describe('replaceAttachmentPrefixAndUnderscores', () => {
        it('should replace the prefix and underscores in a file name', () => {
            const fileName = 'AttachmentUnit_2023-01-01T00-00-00-000_some_file_name';
            const expected = 'some file name';

            const result = fileService.replaceAttachmentPrefixAndUnderscores(fileName);
            expect(result).toBe(expected);
        });
    });

    describe('createStudentLink', () => {
        it('should return the student version of the given link', () => {
            const link = 'http://example.com/course/attachment/file.pdf';
            const expectedStudentLink = 'http://example.com/course/attachment/student/file.pdf';

            const result = fileService.createStudentLink(link);
            expect(result).toBe(expectedStudentLink);
        });
    });
});
