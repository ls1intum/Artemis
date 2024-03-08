import { Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import * as monaco from 'monaco-editor';
import { Subscription } from 'rxjs';
import { Theme, ThemeService } from 'app/core/theme/theme.service';

export type EditorPosition = { lineNumber: number; column: number };
export type MarkdownString = monaco.IMarkdownString;
export type GlyphDecoration = { lineNumber: number; glyphMarginClassName: string; hoverMessage: MarkdownString };
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

    constructor(private themeService: ThemeService) {}

    @Input()
    textChangedEmitDelay: number | undefined;

    @Output()
    textChanged = new EventEmitter<string>();

    @Output()
    modelChanged = new EventEmitter<string>();

    private textChangedEmitTimeout: NodeJS.Timeout | undefined = undefined;

    ngOnInit(): void {
        const program: string = `
        public class ExampleProgram() {
          public static void main(String[] args) {
            System.out.println("Hello!");
            foo();
          }

          static void foo() {
            System.out.println("bar");
          }
        }
        `;
        this._editor = monaco.editor.create(this.monacoEditorContainer.nativeElement, {
            value: program,
            theme: 'vs-dark',
            glyphMargin: true,
            minimap: { enabled: false },
            lineNumbersMinChars: 4,
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

    getPosition(): EditorPosition {
        return this._editor.getPosition() ?? { column: 0, lineNumber: 0 };
    }

    setPosition(position: EditorPosition) {
        this._editor.setPosition(position);
    }

    getText(): string {
        return this._editor.getValue();
    }

    setText(text: string): void {
        this._editor.setValue(text);
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
        this._editor.setModel(model);
        monaco.editor.setModelLanguage(model, model.getLanguageId());
    }

    changeTheme(artemisTheme: Theme): void {
        this._editor.updateOptions({
            theme: artemisTheme === Theme.DARK ? 'vs-dark' : 'vs-light',
        });
    }

    addGlyphDecorations(decorations: GlyphDecoration[]) {
        this.addDecorations(
            decorations.map((d) => ({
                range: new monaco.Range(d.lineNumber, 0, d.lineNumber, 0),
                options: {
                    glyphMarginClassName: d.glyphMarginClassName,
                    glyphMargin: { position: monaco.editor.GlyphMarginLane.Left },
                    glyphMarginHoverMessage: d.hoverMessage,
                },
            })),
        );
    }

    // TODO: Return value
    private addDecorations(decorations: monaco.editor.IModelDeltaDecoration[]) {
        return this._editor?.createDecorationsCollection(decorations);
    }
}
