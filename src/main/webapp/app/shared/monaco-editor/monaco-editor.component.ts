import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import * as monaco from 'monaco-editor';

@Component({
    selector: 'jhi-monaco-editor',
    templateUrl: 'monaco-editor.component.html',
    styleUrls: ['monaco-editor.component.scss'],
})
export class MonacoEditorComponent implements OnInit {
    @ViewChild('monacoEditorContainer', { static: true }) private monacoEditorContainer: ElementRef;
    private _editor: monaco.editor.IStandaloneCodeEditor;

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
            lineNumbersMinChars: 5,
        });

        const resizeObserver = new ResizeObserver(() => {
            this._editor.layout();
        });
        resizeObserver.observe(this.monacoEditorContainer.nativeElement);
    }

    setText(text: string): void {
        this._editor.setValue(text);
    }

    changeModel(fileName: string, newFileContent?: string) {
        const uri = monaco.Uri.parse(`inmemory://model/${this._editor.getId()}/${fileName}`);
        const model = monaco.editor.getModel(uri) ?? monaco.editor.createModel(newFileContent ?? '', undefined, uri);
        if (newFileContent !== undefined) {
            model.setValue(newFileContent);
        }
        this._editor.setModel(model);
        monaco.editor.setModelLanguage(model, model.getLanguageId());
    }
}
