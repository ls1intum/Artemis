import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';
import { LspConfigModel } from 'app/exercises/programming/shared/code-editor/model/lsp-config.model';
import { FileSubmission } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { supportedTextFileExtensions } from 'app/exercises/programming/shared/code-editor/file-browser/supported-file-extensions';

const resourceUrl = 'api/monaco/';

@Injectable({ providedIn: 'root' })
export class CodeEditorMonacoService {
    runSubject = new Subject<string>();
    refreshSubject = new Subject<FileSubmission>();
    constructor(private http: HttpClient) {}

    /**
     * Initializes the LSP by cloning the provided participation's repository to
     * the server and returning the url to which the client can connect through a websocket
     * connection.
     *
     * @param participationId the id of the participation to initialize
     */
    initLsp(participationId: number): Observable<LspConfigModel> {
        return this.http.get<LspConfigModel>(resourceUrl + `init-lsp/${participationId}`, {
            headers: { 'Content-Type': 'application/json', observe: 'response' },
        });
    }

    /**
     * Requests the initialization of a terminal;
     *
     * @param participationId the id of the participation used to initialize the terminal
     * @param lspServerUrl the URL to the LSP server where the repository has been initialized
     */
    initTerminal(participationId: number, lspServerUrl: URL): Observable<LspConfigModel> {
        return this.http.get<LspConfigModel>(resourceUrl + `init-terminal/${participationId}`, {
            params: new HttpParams().set('monacoServerUrl', lspServerUrl.toString()),
            headers: { 'Content-Type': 'application/json', observe: 'response' },
        });
    }

    /**
     * Initializes a websocket connection to a remote terminal
     * @param lspConfig containing the configuration on which server to connect
     */
    initTerminalWebsocketConnection(lspConfig: LspConfigModel): WebSocket {
        const url = new URL(lspConfig.serverUrl);
        url.protocol = url.protocol === 'https:' ? 'wss' : 'ws';
        url.pathname = url.pathname.endsWith('/') ? url.pathname : url.pathname + '/';
        url.pathname += 'terminal';
        url.searchParams.set('id', lspConfig.containerId);
        return new WebSocket(url);
    }

    /**
     * Request to update a given set of files on the external Monaco server.
     *
     * To avoid updating binary files, only supported text files are included.
     *
     * @param files the files to update
     * @param lspConfig containing the configuration on which server to connect
     * @param participationId the id of the participation used to check the user's permissions
     */
    updateFiles(files: FileSubmission, lspConfig: LspConfigModel, participationId: number): Observable<void> {
        let filesArray = Object.entries(files).map(([fileName, fileContent]) => ({ fileName, fileContent }));
        filesArray = filesArray.filter((entry) => {
            const entrySplit = entry.fileName.split('.');
            return entrySplit.length === 1 || supportedTextFileExtensions.includes(entrySplit.pop()!);
        });
        return this.http.put<void>(resourceUrl + `update-files/${participationId}`, filesArray, {
            params: new HttpParams().set('monacoServerUrl', lspConfig.serverUrl.toString()),
        });
    }

    /**
     * Communicates to the run listeners the request to run the current submission's code
     */
    run(args: string) {
        this.runSubject.next(args);
    }

    /**
     * Communicates to the refresh listeners the request to refresh the current submission's code
     * with the provided files' content
     */
    refresh(files: FileSubmission) {
        this.refreshSubject.next(files);
    }
}
