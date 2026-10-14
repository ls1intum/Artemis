import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { DomainType, FileType } from 'app/programming/shared/code-editor/model/code-editor.model';

describe('CodeEditorRepositoryFileService', () => {
    const commitId = '0123456789abcdef0123456789abcdef01234567';
    const participationUrl = 'api/programming/participations/42/repository';

    let service: CodeEditorRepositoryFileService;
    let httpTestingController: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: WebsocketService, useValue: {} }],
        });
        TestBed.inject(DomainService).setDomain([DomainType.PARTICIPATION, { id: 42 }]);
        service = TestBed.inject(CodeEditorRepositoryFileService);
        httpTestingController = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpTestingController.verify());

    it('should read the files of the working copy if no commit is given', async () => {
        const result = firstValueFrom(service.getRepositoryContent());
        httpTestingController.expectOne({ method: 'GET', url: `${participationUrl}/files` }).flush({ 'a.txt': FileType.FILE });
        expect(await result).toEqual({ 'a.txt': FileType.FILE });
    });

    it('should read the files of the given commit', async () => {
        const result = firstValueFrom(service.getRepositoryContent(undefined, commitId));
        httpTestingController.expectOne({ method: 'GET', url: `${participationUrl}/commits/${commitId}/files` }).flush({ 'a.txt': FileType.FILE });
        expect(await result).toEqual({ 'a.txt': FileType.FILE });
    });

    it('should read the change information of the working copy if no commit is given', async () => {
        const result = firstValueFrom(service.getFilesWithInformationAboutChange());
        httpTestingController.expectOne({ method: 'GET', url: `${participationUrl}/files-change` }).flush({ 'a.txt': true });
        expect(await result).toEqual({ 'a.txt': true });
    });

    it('should read the change information of the given commit', async () => {
        const result = firstValueFrom(service.getFilesWithInformationAboutChange(undefined, commitId));
        httpTestingController.expectOne({ method: 'GET', url: `${participationUrl}/commits/${commitId}/files-change` }).flush({ 'a.txt': true });
        expect(await result).toEqual({ 'a.txt': true });
    });

    it('should read a file of the working copy if no commit is given', async () => {
        const result = firstValueFrom(service.getFile('a.txt'));
        const request = httpTestingController.expectOne((req) => req.method === 'GET' && req.url === `${participationUrl}/file`);
        expect(request.request.params.get('file')).toBe('a.txt');
        request.flush('working copy');
        expect(await result).toEqual({ fileContent: 'working copy' });
    });

    it('should read a file of the given commit', async () => {
        const result = firstValueFrom(service.getFile('a.txt', undefined, commitId));
        const request = httpTestingController.expectOne((req) => req.method === 'GET' && req.url === `${participationUrl}/commits/${commitId}/file`);
        expect(request.request.params.get('file')).toBe('a.txt');
        request.flush('submitted');
        expect(await result).toEqual({ fileContent: 'submitted' });
    });

    it('should read a file of the given commit as blob', async () => {
        const result = firstValueFrom(service.getFileAsBlob('image.png', undefined, commitId));
        const request = httpTestingController.expectOne((req) => req.method === 'GET' && req.url === `${participationUrl}/commits/${commitId}/file`);
        expect(request.request.responseType).toBe('blob');
        request.flush(new Blob(['x']));
        expect(await result).toBeInstanceOf(Blob);
    });

    it('should use the url of the given domain when reading a commit', async () => {
        const result = firstValueFrom(service.getRepositoryContent([DomainType.PARTICIPATION, { id: 7 }], commitId));
        httpTestingController.expectOne({ method: 'GET', url: `api/programming/participations/7/repository/commits/${commitId}/files` }).flush({});
        expect(await result).toEqual({});
    });
});
