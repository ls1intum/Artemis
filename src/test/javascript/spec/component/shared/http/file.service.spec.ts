import { FileService } from 'app/shared/http/file.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { v4 as uuid } from 'uuid';
import { provideHttpClient } from '@angular/common/http';
import { ProgrammingLanguage, ProjectType } from 'app/entities/programming/programming-exercise.model';

jest.mock('uuid', () => ({
    v4: jest.fn(),
}));
describe('FileService', () => {
    const firstUniqueFileName = 'someOtherUniqueFileName';
    const secondUniqueFileName = 'someUniqueFileName';
    const thirdUniqueFileName = 'someFinalUniqueFileName';

    let fileService: FileService;
    let httpMock: HttpTestingController;
    let getUniqueFileNameSpy: jest.SpyInstance;
    let v4Mock: jest.Mock;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting(), FileService],
        });
        fileService = TestBed.inject(FileService);
        httpMock = TestBed.inject(HttpTestingController);
        getUniqueFileNameSpy = jest.spyOn(fileService, 'getUniqueFileName');

        v4Mock = uuid as jest.Mock;
        v4Mock.mockReturnValueOnce(firstUniqueFileName).mockReturnValueOnce(secondUniqueFileName).mockReturnValueOnce(thirdUniqueFileName);
    });

    afterEach(() => {
        v4Mock.mockReset();
    });

    describe('getFile', () => {
        it('should return a file', async () => {
            const filePath = 'api/file/path/test.png';
            const blob = new Blob(['123456789']);

            const filePromise = fileService.getFile(filePath);
            const req = httpMock.expectOne({
                url: filePath,
                method: 'GET',
            });
            req.flush(blob);

            const file = await filePromise;

            expect(file.size).toEqual(blob.size);
            expect(file.name).toBe(firstUniqueFileName + '.png');
            expect(getUniqueFileNameSpy).toHaveBeenCalledExactlyOnceWith('png', undefined);
            expect(v4Mock).toHaveBeenCalledOnce();
        });

        it('should return a file with unique name', async () => {
            const filePath = 'api/file/path/test.png';
            const blob = new Blob(['123456789']);
            const existingFileNames = new Map<string, { file: File; path?: string }>([
                [secondUniqueFileName + '.png', { file: new File([], secondUniqueFileName) }],
                [firstUniqueFileName + '.png', { file: new File([], firstUniqueFileName) }],
            ]);

            const filePromise = fileService.getFile(filePath, existingFileNames);
            const req = httpMock.expectOne({
                url: filePath,
                method: 'GET',
            });
            req.flush(blob);

            const file = await filePromise;

            expect(file.size).toEqual(blob.size);
            expect(file.name).toBe(thirdUniqueFileName + '.png');
            expect(getUniqueFileNameSpy).toHaveBeenCalledExactlyOnceWith('png', existingFileNames);
            expect(v4Mock).toHaveBeenCalledTimes(3);
        });
    });

    describe('getTemplateFile', () => {
        it('should fetch the template file without project type', () => {
            const language = ProgrammingLanguage.JAVA;
            const expectedUrl = `api/files/templates/JAVA`;
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
            const expectedUrl = `api/files/templates/JAVA/PLAIN_MAVEN`;
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
            const expectedUrl = `api/files/attachments/lecture/${lectureId}/merge-pdf`;
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

    describe('getAeolusTemplateFile', () => {
        it('should fetch the aeolus template file with all parameters', () => {
            const language = ProgrammingLanguage.PYTHON;
            const projectType = ProjectType.PLAIN;
            const staticAnalysis = true;
            const sequentialRuns = false;
            const coverage = true;
            const expectedUrl = `api/files/aeolus/templates/PYTHON/PLAIN?staticAnalysis=true&sequentialRuns=false&testCoverage=true`;
            const response = 'aeolus template content';

            fileService.getAeolusTemplateFile(language, projectType, staticAnalysis, sequentialRuns, coverage).subscribe((data) => {
                expect(data).toEqual(response);
            });

            const req = httpMock.expectOne({
                url: expectedUrl,
                method: 'GET',
            });
            expect(req.request.responseType).toBe('text');
            req.flush(response);
        });

        it('should fetch the aeolus template file with missing optional parameters', () => {
            const expectedUrl = `api/files/aeolus/templates/PYTHON?staticAnalysis=false&sequentialRuns=false&testCoverage=false`;
            const response = 'aeolus template content';

            fileService.getAeolusTemplateFile(ProgrammingLanguage.PYTHON).subscribe((data) => {
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

    describe('getTemplateCodeOfConduct', () => {
        it('should fetch the template code of conduct', () => {
            const expectedUrl = `api/files/templates/code-of-conduct`;
            const response = 'code of conduct content';

            fileService.getTemplateCodeOfCondcut().subscribe((data) => {
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
});
