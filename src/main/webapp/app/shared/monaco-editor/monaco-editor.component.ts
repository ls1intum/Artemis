import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import * as monaco from 'monaco-editor';
import { Subscription } from 'rxjs';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { MonacoEditorAnnotation, MonacoEditorAnnotationType } from 'app/shared/monaco-editor/model/monaco-editor-annotation.model';
import { MonacoEditorLineWidget } from 'app/shared/monaco-editor/model/monaco-editor-line-widget.model';

export type EditorPosition = { row: number; column: number };
export type MarkdownString = monaco.IMarkdownString;
export type GlyphDecoration = { lineNumber: number; glyphMarginClassName: string; hoverMessage: MarkdownString };
export type EditorRange = monaco.Range;
@Component({
    selector: 'jhi-monaco-editor',
    templateUrl: 'monaco-editor.component.html',
    styleUrls: ['monaco-editor.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MonacoEditorComponent implements OnInit, OnDestroy {
    @ViewChild('monacoEditorContainer', { static: true }) private monacoEditorContainer: ElementRef;
    private _editor: monaco.editor.IStandaloneCodeEditor;
    private themeSubscription?: Subscription;
    private models: monaco.editor.IModel[] = [];
    private editorLineWidgets: MonacoEditorLineWidget[] = [];
    private editorAnnotations: MonacoEditorAnnotation[] = [];

    constructor(private themeService: ThemeService) {}

    @Input()
    textChangedEmitDelay: number | undefined;

    @Input()
    set readOnly(value: boolean) {
        this._readOnly = value;
        if (this._editor) {
            this._editor.updateOptions({
                readOnly: value,
            });
        }
    }

    private _readOnly: boolean = false;

    @Output()
    textChanged = new EventEmitter<string>();

    private textChangedEmitTimeout: NodeJS.Timeout | undefined = undefined;

    ngOnInit(): void {
        this._editor = monaco.editor.create(this.monacoEditorContainer.nativeElement, {
            value: '',
            theme: 'vs-dark',
            glyphMargin: true,
            minimap: { enabled: false },
            readOnly: this._readOnly,
        });

        const resizeObserver = new ResizeObserver(() => {
            this._editor.layout();
        });
        resizeObserver.observe(this.monacoEditorContainer.nativeElement);

        this._editor.onDidChangeModelContent(() => {
            this.emitTextChangeEvent();
        }, this);

        this.themeSubscription = this.themeService.getCurrentThemeObservable().subscribe((theme) => this.changeTheme(theme));
    }

    ngOnDestroy() {
        this._editor.dispose();
        this.themeSubscription?.unsubscribe();
        this.models.forEach((m) => m.dispose());
    }

    private emitTextChangeEvent() {
        const newValue = this.getText();
        if (!this.textChangedEmitDelay) {
            this.textChanged.emit(newValue);
        } else {
            if (this.textChangedEmitTimeout) {
                clearTimeout(this.textChangedEmitTimeout);
                this.textChangedEmitTimeout = undefined;
            }
            this.textChangedEmitTimeout = setTimeout(() => {
                this.textChanged.emit(newValue);
            }, this.textChangedEmitDelay);
        }
    }

    // The rest of the code uses { row, column }... Will have to refactor
    toEditorPosition(position: monaco.Position | null): EditorPosition {
        if (!position) return { row: 0, column: 0 };
        return { row: position.lineNumber, column: position.column };
    }

    getPosition(): EditorPosition {
        return this.toEditorPosition(this._editor.getPosition());
    }

    setPosition(position: EditorPosition) {
        this._editor.setPosition({ lineNumber: position.row, column: position.column });
    }

    getText(): string {
        return this._editor.getValue();
    }

    setText(text: string): void {
        this._editor.setValue(text);
    }

    getNumberOfLines(): number {
        return this._editor.getModel()?.getLineCount() ?? 0;
    }

    isReadOnly(): boolean {
        return this._editor.getOption(monaco.editor.EditorOption.readOnly);
    }

    changeModel(fileName: string, newFileContent?: string) {
        const uri = monaco.Uri.parse(`inmemory://model/${this._editor.getId()}/${fileName}`);
        const model = monaco.editor.getModel(uri) ?? monaco.editor.createModel(newFileContent ?? '', undefined, uri);
        if (!this.models.includes(model)) {
            this.models.push(model);
        }
        if (newFileContent !== undefined) {
            model.setValue(newFileContent);
        }

        // Some elements remain when the model is changed - dispose of them.
        this.disposeWidgets();

        monaco.editor.setModelLanguage(model, model.getLanguageId());
        this._editor.setModel(model);
    }

    private disposeWidgets() {
        this.editorLineWidgets.forEach((o) => {
            o.dispose();
            this._editor.removeOverlayWidget(o);
        });
        this.editorLineWidgets = [];
        this.disposeAnnotations();
    }

    private disposeAnnotations() {
        this.editorAnnotations.forEach((o) => {
            o.dispose();
            this._editor.removeGlyphMarginWidget(o);
        });
        this.editorAnnotations = [];
    }

    changeTheme(artemisTheme: Theme): void {
        this._editor.updateOptions({
            theme: artemisTheme === Theme.DARK ? 'vs-dark' : 'vs-light',
        });
    }

    setAnnotations(annotations: Array<Annotation>, markAsOutdated: boolean = false) {
        if (!this._editor) return;
        this.disposeAnnotations();
        for (const annotation of annotations) {
            const lineNumber = annotation.row + 1;
            const editorAnnotation = new MonacoEditorAnnotation(
                `${annotation.fileName}:${lineNumber}:${annotation.text}`,
                lineNumber,
                undefined,
                { value: annotation.text },
                annotation.type.toLowerCase() === 'error' ? MonacoEditorAnnotationType.ERROR : MonacoEditorAnnotationType.WARNING,
                this._editor.createDecorationsCollection([]),
            );
            if (markAsOutdated) {
                editorAnnotation.setOutdated(true);
                editorAnnotation.updateDecoration(this.getNumberOfLines());
            }
            this._editor.addGlyphMarginWidget(editorAnnotation);
            const updateListener = this._editor.onDidChangeModelContent(() => {
                editorAnnotation.updateDecoration(this._editor.getModel()?.getLineCount() ?? 0);
            });
            editorAnnotation.setUpdateListener(updateListener);
            this.editorAnnotations.push(editorAnnotation);
        }
    }

    addLineWidget(lineNumber: number, id: string, domNode: HTMLElement) {
        const lineWidget = new MonacoEditorLineWidget(
            id,
            domNode,
            lineNumber,
            (viewZone) => {
                let viewZoneId: string | undefined;
                this._editor.changeViewZones((changeAccessor) => {
                    viewZoneId = changeAccessor.addZone(viewZone);
                });
                if (!viewZoneId) throw new Error('Could not add a ViewZone to the editor.');
                return viewZoneId;
            },
            (viewZoneId: string) => {
                this._editor.changeViewZones((changeAccessor) => {
                    changeAccessor.layoutZone(viewZoneId);
                });
            },
        );

        this._editor.addOverlayWidget(lineWidget);
        this.editorLineWidgets.push(lineWidget);
    }
}
