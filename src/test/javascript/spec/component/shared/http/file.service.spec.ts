import { FileService } from 'app/shared/http/file.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { v4 as uuid } from 'uuid';

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
            imports: [HttpClientTestingModule],
            providers: [FileService],
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
});
