import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { Subject, firstValueFrom, of } from 'rxjs';
import {
    CodeEditorRepositoryFileService,
    CodeEditorRepositoryService,
    ConnectionError,
    FileCommitError,
} from 'app/programming/shared/code-editor/services/code-editor-repository.service';
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

    it.each([false, true])('should commit JSON updates only after every file was saved (file error: %s)', async (fileError) => {
        const submission = { 'saved.txt': null, 'other.txt': fileError ? 'save failed' : null };
        const result = firstValueFrom(
            service.updateFiles(
                [
                    { fileName: 'saved.txt', fileContent: 'content' },
                    { fileName: 'other.txt', fileContent: 'other' },
                ],
                true,
            ),
        );
        const upload = httpTestingController.expectOne(`${participationUrl}/files?commit=no`);
        expect(upload.request.method).toBe('PUT');
        httpTestingController.expectNone(`${participationUrl}/commit`);
        upload.flush(submission);
        if (fileError) {
            httpTestingController.expectNone(`${participationUrl}/commit`);
        } else {
            httpTestingController.expectOne({ method: 'POST', url: `${participationUrl}/commit` }).flush(null);
        }
        expect(await result).toEqual(submission);
    });

    it('should upload escaped single-file content raw and commit only after the upload succeeds', async () => {
        const content = '\u0001'.repeat(4 * 1024 * 1024);
        expect(new TextEncoder().encode(JSON.stringify([{ fileName: 'escaped.txt', fileContent: content }])).length).toBeGreaterThan(20 * 1024 * 1024);
        const result = firstValueFrom(service.runExclusive(() => service.updateFiles([{ fileName: 'escaped.txt', fileContent: content }], true)));
        const upload = httpTestingController.expectOne(`${participationUrl}/file?file=escaped.txt`);
        expect(upload.request.method).toBe('PUT');
        expect(upload.request.body).toBe(content);
        httpTestingController.expectNone(`${participationUrl}/commit`);
        upload.flush(null);
        httpTestingController.expectOne({ method: 'POST', url: `${participationUrl}/commit` }).flush(null);
        expect(await result).toEqual({ 'escaped.txt': undefined });
    });

    it.each([false, true])('should retain uploaded files and the original error when committing fails (raw: %s)', (raw) => {
        const content = raw ? '\u0001'.repeat(4 * 1024 * 1024) : 'content';
        const submission = { 'file.txt': null };
        const next = vi.fn();
        const error = vi.fn();
        service.updateFiles([{ fileName: 'file.txt', fileContent: content }], true).subscribe({ next, error });
        httpTestingController.expectOne(raw ? `${participationUrl}/file?file=file.txt` : `${participationUrl}/files?commit=no`).flush(raw ? null : submission);
        expect(next).not.toHaveBeenCalled();
        const commit = httpTestingController.expectOne(`${participationUrl}/commit`);
        if (raw) {
            commit.error(new ProgressEvent('error'));
        } else {
            commit.flush({ detail: 'commit failed' }, { status: 500, statusText: 'Internal Server Error' });
        }
        expect(next).not.toHaveBeenCalled();
        expect(error).toHaveBeenCalledExactlyOnceWith(expect.any(FileCommitError));
        const failure: FileCommitError = error.mock.calls[0][0];
        expect(failure.submission).toEqual(raw ? { 'file.txt': undefined } : submission);
        expect(failure.cause).toBeInstanceOf(raw ? ConnectionError : HttpErrorResponse);
    });

    it('should preserve ordering when a queued operation is cancelled', () => {
        const first = new Subject<void>();
        const later = vi.fn(() => of(undefined));
        service.runExclusive(() => first).subscribe();
        service
            .runExclusive(() => of(undefined))
            .subscribe()
            .unsubscribe();
        service.runExclusive(later).subscribe();
        expect(later).not.toHaveBeenCalled();
        first.complete();
        expect(later).toHaveBeenCalledOnce();
    });

    it('should release the next write after a failed operation', () => {
        const first = new Subject<void>();
        const later = vi.fn(() => of(undefined));
        const failed = vi.fn();
        service.runExclusive(() => first).subscribe({ error: failed });
        service.runExclusive(later).subscribe();
        expect(later).not.toHaveBeenCalled();
        const error = new Error('save failed');
        first.error(error);
        expect(failed).toHaveBeenCalledExactlyOnceWith(error);
        expect(later).toHaveBeenCalledOnce();
    });

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

describe('CodeEditorRepositoryService pull routing', () => {
    it('uses POST for the selected repository', () => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: WebsocketService, useValue: {} }],
        });
        const service = TestBed.inject(CodeEditorRepositoryService);
        const http = TestBed.inject(HttpTestingController);
        service.setDomain([DomainType.PARTICIPATION, { id: 12 }]);
        service.pull().subscribe();
        http.expectOne({ method: 'POST', url: 'api/programming/participations/12/repository/pull' }).flush(null);
        http.verify();
    });
});
