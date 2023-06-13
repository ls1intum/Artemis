import 'monaco-editor/esm/vs/editor/editor.all.js';
import 'monaco-editor/esm/vs/editor/standalone/browser/inspectTokens/inspectTokens.js';
import 'monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneHelpQuickAccess.js';
import 'monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneGotoLineQuickAccess.js';
import 'monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneGotoSymbolQuickAccess.js';
import 'monaco-editor/esm/vs/editor/standalone/browser/quickAccess/standaloneCommandsQuickAccess.js';
import 'monaco-editor/esm/vs/editor/standalone/browser/quickInput/standaloneQuickInputService.js';
import 'monaco-editor/esm/vs/editor/standalone/browser/referenceSearch/standaloneReferenceSearch.js';
import 'monaco-editor/esm/vs/basic-languages/cpp/cpp.contribution.js';
import 'monaco-editor/esm/vs/basic-languages/python/python.contribution.js';
import 'monaco-editor/esm/vs/basic-languages/java/java.contribution.js';

import * as monaco from 'monaco-editor/esm/vs/editor/editor.api.js';

import { CloseAction, ErrorAction, MessageTransports, MonacoLanguageClient, MonacoServices } from 'monaco-languageclient';
import { WebSocketMessageReader, toSocket } from 'vscode-ws-jsonrpc';

import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { LocalStorageService } from 'ngx-webstorage';
import { MonacoConfigModel, MonacoTheme } from './monaco-config.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Observable, catchError, debounceTime, of, tap, throttleTime } from 'rxjs';
import { CommitState, EditorState, FileSubmission, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { Feedback } from 'app/entities/feedback.model';
import { Course } from 'app/entities/course.model';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { faCircle, faCircleNotch, faFileAlt, faGear, faSync } from '@fortawesome/free-solid-svg-icons';
import { Participation } from 'app/entities/participation/participation.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { LspConfigModel } from 'app/exercises/programming/shared/code-editor/model/lsp-config.model';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { AlertService } from 'app/core/util/alert.service';
import { CodeEditorMonacoService } from 'app/exercises/programming/shared/code-editor/service/code-editor-monaco.service';
import { CustomWebSocketMessageWriter } from './custom-message-writer.model';

@Component({
    selector: 'jhi-monaco',
    templateUrl: './monaco.component.html',
    styleUrls: ['./monaco.component.scss'],
})
export class MonacoComponent implements OnInit, OnDestroy, OnChanges {
    @Input()
    selectedFile: string;
    @Input()
    files: { [fileName: string]: FileType };
    @Input()
    sessionId: number | string;
    @Input()
    readOnlyManualFeedback: boolean;

    @Input()
    participation: Participation;

    @Input()
    readonly commitState: CommitState;
    @Input()
    readonly editorState: EditorState;
    @Input()
    isTutorAssessment = false;
    @Input()
    feedbacks: Feedback[];
    @Input()
    highlightDifferences: boolean;
    @Input()
    course?: Course;

    _lspConfig?: LspConfigModel;

    get lspConfig(): LspConfigModel | undefined {
        return this._lspConfig;
    }

    @Input() set lspConfig(value: LspConfigModel | undefined) {
        this._lspConfig = value;
        this.initializeLanguageClient();
    }

    @Output()
    onFileContentChange = new EventEmitter<{ file: string; fileContent: string }>();
    @Output()
    onError = new EventEmitter<string>();
    @Output()
    onUpdateFeedback = new EventEmitter<Feedback[]>();
    @Output()
    onPresentationMode = new EventEmitter<boolean>();

    // ICONS
    farFileAlt = faFileAlt;
    fasCircle = faCircle;
    faCircleNotch = faCircleNotch;
    faGear = faGear;
    faSync = faSync;

    supportedLanguages: any[] = [];

    currentLang = 'plaintext';
    currentModel: monaco.editor.ITextModel;
    currentWebSocket?: WebSocket;
    editor?: monaco.editor.IStandaloneCodeEditor;
    languageClient?: MonacoLanguageClient;
    fileSession: { [fileName: string]: { code: string; cursor: { column: number; row: number } } } = {};

    // Settings
    editorConfigs = new MonacoConfigModel();
    themes = Object.entries(MonacoTheme);
    isPresentationMode = false;

    isLangServerOnline?: boolean;
    lspError?: string;

    isLoading = false;

    modalRef?: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private repositoryFileService: CodeEditorRepositoryFileService,
        protected localStorageService: LocalStorageService,
        private themeService: ThemeService,
        private alertService: AlertService,
        private monacoService: CodeEditorMonacoService,
    ) {}

    createUrl(host: URL, path: string): string {
        const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
        let hostPath = host.host + host.pathname;
        hostPath = hostPath.endsWith('/') ? hostPath : hostPath + '/';
        return `${protocol}://${hostPath}${path}`;
    }

    ngOnInit(): void {
        this.supportedLanguages = monaco.languages.getLanguages();

        // create Monaco editor
        this.editor = monaco.editor.create(document.getElementById('container')!, {
            model: null,
            glyphMargin: false,
            folding: false,
            lightbulb: {
                enabled: true,
            },
            automaticLayout: true,
            autoClosingBrackets: 'never', // to be re-enabled when the debounce method considers this feature
            autoClosingQuotes: 'never',
            fontSize: this.editorConfigs.fontSize,
            theme: this.editorConfigs.theme,
        });

        window.onresize = () => {
            this.editor!.layout({} as monaco.editor.IDimension);
        };

        this.currentModel = monaco.editor.createModel('', 'plaintext');
        this.editor.setModel(this.currentModel);

        this.monacoService.refreshSubject.pipe(throttleTime(20000)).subscribe((files: FileSubmission) => {
            this.refreshModels(files);
            if (this.lspConfig && this.participation.id) {
                this.monacoService.updateFiles(files, this.lspConfig, this.participation.id).subscribe();
            }
        });

        this.themeService.getCurrentThemeObservable().subscribe((theme) => {
            this.handleGlobalThemeChange(theme);
        });

        this.currentLang = this.getProgrammingLanguageOfExercise();
    }

    /**
     * COPIED from code-editor-ace.component.ts
     * @function ngOnChanges
     * @desc New clean state       => reset the editor and file update subscriptions
     *       New selectedFile      => load the file from the repository and open it in the editor
     * @param {SimpleChanges} changes
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (
            (changes.commitState && changes.commitState.previousValue !== CommitState.UNDEFINED && this.commitState === CommitState.UNDEFINED) ||
            (changes.editorState && changes.editorState.previousValue === EditorState.REFRESHING && this.editorState === EditorState.CLEAN)
        ) {
            /*this.currentModel?.setValue('');*/
        }
        if (
            (changes.selectedFile && this.selectedFile) ||
            (changes.editorState && changes.editorState.previousValue === EditorState.REFRESHING && this.editorState === EditorState.CLEAN)
        ) {
            // Current file has changed
            // Only load the file from server if there hasn't been stored a model yet
            if (this.selectedFile && !monaco.editor.getModel(this.getUriForFile(this.selectedFile))) {
                this.loadFileModel(this.selectedFile);
            } else {
                this.initSelectedFileModel();
            }
        }
        if (changes.commitState && changes.commitState.currentValue === CommitState.CONFLICT) {
            this.editor?.updateOptions({ readOnly: true });
        } else if (changes.commitState && changes.commitState.previousValue === CommitState.CONFLICT && changes.commitState.currentValue !== CommitState.CONFLICT) {
            this.editor?.updateOptions({ readOnly: false });
        }
    }

    /**
     * Initializes the editor with the the model of the selected file
     */
    initSelectedFileModel() {
        const fileModel = monaco.editor.getModel(this.getUriForFile(this.selectedFile));

        if (fileModel) {
            this.currentModel = fileModel;
        } else {
            this.loadFileModel();
            return;
        }
        this.currentLang = this.currentModel.getLanguageId();
        this.editor?.setModel(this.currentModel);
        this.editor?.focus();
    }

    /**
     * Fetches the requested file by filename and opens a new editor session for it (if not yet done)
     * @param fileName: Name of the file to be opened in the editor
     */
    loadFileModel(fileName?: string) {
        this.isLoading = true;

        fileName = fileName ?? this.selectedFile;

        if (!fileName) {
            return;
        }

        // check if a model has already been created for this file
        const fileModel = monaco.editor.getModel(this.getUriForFile(fileName));
        if (fileModel && this.selectedFile === fileName) {
            this.initSelectedFileModel();
            return;
        }

        this.repositoryFileService
            .getFile(fileName)
            .pipe(
                tap((fileObj) => {
                    const newModel: monaco.editor.ITextModel = monaco.editor.createModel(fileObj.fileContent, this.getLangForFile(fileName), this.getUriForFile(fileName));

                    const onChangeContentObservable = new Observable((subscriber) => {
                        newModel.onDidChangeContent(() => subscriber.next());
                    });

                    onChangeContentObservable.pipe(debounceTime(2000)).subscribe(() => {
                        this.onFileContentChange.emit({ file: fileName!, fileContent: newModel.getValue() });
                    });

                    // It is possible that the selected file has changed - in this case don't update the editor.
                    if (this.selectedFile === fileName) {
                        this.initSelectedFileModel();
                    }
                }),
                catchError(() => {
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isLoading = false;
            });
    }

    ngOnDestroy(): void {
        this.editor?.dispose();
        this.currentModel.dispose();
        if (this.languageClient?.isRunning() && this.languageClient?.state !== 3) {
            this.languageClient?.dispose();
        }
        this.currentWebSocket?.close(1000);
    }

    /**
     * Creates and returns the URI for the provided file name if any, or one for
     * the currently selected file instead
     * @param fileName
     * @private
     */
    private getUriForFile(fileName?: string) {
        if (!fileName) {
            fileName = this.selectedFile;
        }
        return monaco.Uri.parse(`file://${this.lspConfig?.repoPath}/${fileName}`);
    }

    /**
     * Returns a string representing the programming language of a given file if any,
     * or the language of the currently selected file instead
     * @param fileName of the file to retrieve tumfrahe programming language from
     * @private
     */
    private getLangForFile(fileName?: string) {
        if (!fileName) {
            fileName = this.selectedFile;
        }
        switch (fileName.toLowerCase().substring(fileName.lastIndexOf('.'))) {
            case '.py':
            case '.python':
                return 'python';
            case '.java':
                return 'java';
            case '.c':
            case '.cpp':
                return 'c';
        }
    }

    private createLanguageClient(langId: string, transports: MessageTransports): MonacoLanguageClient {
        return new MonacoLanguageClient({
            name: `${langId} Language Client`,
            clientOptions: {
                documentSelector: [langId],
                errorHandler: {
                    error: () => {
                        this.alertService.error('artemisApp.monaco.connectionError');
                        this.isLangServerOnline = false;
                        return { action: ErrorAction.Shutdown };
                    },
                    closed: () => {
                        this.isLangServerOnline = undefined;
                        return { action: CloseAction.DoNotRestart };
                    },
                },
            },
            connectionProvider: {
                get: () => {
                    return Promise.resolve(transports);
                },
            },
        });
    }

    private initializeLanguageClient() {
        if (!this.lspConfig || !this.isProgrammingLanguageSupported((this.participation.exercise as ProgrammingExercise).programmingLanguage)) {
            return; // TODO add alert informing disabled lsp
        }
        if (this.currentWebSocket && this.currentWebSocket.readyState === WebSocket.OPEN) {
            this.currentWebSocket.close();
        }

        try {
            MonacoServices.install({ workspaceFolders: [{ uri: `file://${this.lspConfig.repoPath}` }] });

            const url = this.getLanguageServerUrlByLanguageId(this.lspConfig!.serverUrl) ?? this.lspConfig.serverUrl;
            if (!url) {
                this.isLangServerOnline = undefined;
                return;
            }
            this.currentWebSocket = new WebSocket(url);

            this.currentWebSocket.onopen = () => {
                const socket = toSocket(this.currentWebSocket!);
                const reader = new WebSocketMessageReader(socket);
                const writer = new CustomWebSocketMessageWriter(socket);

                this.languageClient = this.createLanguageClient(this.currentLang, {
                    reader,
                    writer,
                });
                this.languageClient
                    .start()
                    .then(() => {
                        this.isLangServerOnline = this.languageClient?.isRunning();
                    })
                    .catch((e) => console.log('errorcatched', e));
            };

            this.currentWebSocket.onerror = () => {
                this.isLangServerOnline = false;
                this.lspError = 'Websocket connection failed!';
            };

            this.currentWebSocket.onclose = () => {
                this.isLangServerOnline = undefined;
                if (this.languageClient?.isRunning() && this.languageClient?.state !== 3) {
                    this.languageClient?.dispose();
                }
            };
        } catch (e) {
            this.lspError = e ?? undefined;
            this.isLangServerOnline = false;
        }
    }

    /**
     * Used to restart the LSP server when an error occurred
     */
    public restartLanguageClient() {
        if (!this.languageClient) {
            this.initializeLanguageClient();
            return;
        }
        this.languageClient
            .dispose()
            .then(() => {
                this.currentWebSocket?.close();
            })
            .finally(() => {
                this.initializeLanguageClient();
            });
    }

    public updateTheme(value?: MonacoTheme) {
        value = value ? value : this.editorConfigs.theme;
        this.editorConfigs.theme = value;
        this.editor?.updateOptions({ theme: value });
    }

    public updateMinimap() {
        this.editor?.updateOptions({ minimap: { enabled: this.editorConfigs.minimap } });
    }

    /**
     * Reflects global theme changes to the editor's theme.
     * @param theme the current global Theme
     * @private
     */
    private handleGlobalThemeChange(theme: Theme) {
        if (theme === Theme.DARK) {
            this.updateTheme(MonacoTheme.VS_DARK);
        } else if (theme === Theme.LIGHT) {
            this.updateTheme(MonacoTheme.VS);
        }
    }

    /**
     * Updates the current editor's font size.
     * If the value is greater than MAX or less than MIN, nothing is updated
     * @param value
     */
    public updateFontSize(value?: number) {
        value = value ?? this.editorConfigs.fontSize;
        if ((this.editorConfigs.minFontSize && value < this.editorConfigs.minFontSize) || (this.editorConfigs.maxFontSize && value > this.editorConfigs.maxFontSize)) {
            return;
        }
        this.editorConfigs.fontSize = value;
        this.editor?.updateOptions({ fontSize: this.editorConfigs.fontSize });
    }

    public togglePresentationMode() {
        this.isPresentationMode = !this.isPresentationMode;
        this.isPresentationMode ? this.updateFontSize(24) : this.updateFontSize(15);
        this.editor?.focus();
        this.editor!.layout({} as monaco.editor.IDimension);
        this.onPresentationMode.emit(this.isPresentationMode);
    }

    /**
     * Removes all created file models, forcing the user to reload all files' content
     * @private
     */
    private refreshModels(files: FileSubmission) {
        Object.entries(files).forEach(([fileName, fileContent]) => {
            const model: monaco.editor.ITextModel | null = monaco.editor.getModel(this.getUriForFile(fileName));
            if (model) {
                model.setValue(fileContent || '');
            }
        });
    }

    /**
     * Utility function used to retrieve and map the current exercise's programming language
     * to its corresponding monaco language id.
     * @private
     */
    private getProgrammingLanguageOfExercise() {
        const lang = (this.participation.exercise as ProgrammingExercise).programmingLanguage;

        switch (lang) {
            case ProgrammingLanguage.JAVA:
                return 'java';
            case ProgrammingLanguage.C:
                return 'c';
            case ProgrammingLanguage.PYTHON:
                return 'python';
            default:
                return 'plaintext';
        }
    }

    private getLanguageServerUrlByLanguageId(lspServerUrl: string | URL) {
        if (!(lspServerUrl instanceof URL)) {
            lspServerUrl = new URL(lspServerUrl);
        }
        switch (this.currentLang) {
            case 'python':
                return this.createUrl(lspServerUrl, 'python');
            case 'java':
                return this.createUrl(lspServerUrl, 'java');
            case 'c':
                return this.createUrl(lspServerUrl, 'clang');
            default:
                return null;
        }
    }

    /**
     * Checks if the exercise's programming language is currently supported
     * by the LSP implementation
     * @param programmingLanguage the programming language to check
     */
    private isProgrammingLanguageSupported(programmingLanguage: ProgrammingLanguage | undefined) {
        return (
            programmingLanguage &&
            (programmingLanguage === ProgrammingLanguage.JAVA || programmingLanguage === ProgrammingLanguage.PYTHON || programmingLanguage === ProgrammingLanguage.C)
        );
    }
}
