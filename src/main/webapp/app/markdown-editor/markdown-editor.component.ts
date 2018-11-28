import { Component, ViewChild, forwardRef, Renderer, Attribute, Input, ElementRef } from '@angular/core';
import { NG_VALUE_ACCESSOR, ControlValueAccessor, NG_VALIDATORS, Validator, AbstractControl, ValidationErrors } from '@angular/forms';
import { DomSanitizer } from '@angular/platform-browser';

declare let ace: any;
declare let marked: any;
declare let hljs: any;

@Component({
    selector: 'jhi-markdown-editor',
    styleUrls: ['./markdown-editor.scss'],
    templateUrl: './markdown-editor.component.html',
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => MarkdownEditorComponent),
            multi: true
        },
        {
            provide: NG_VALIDATORS,
            useExisting: forwardRef(() => MarkdownEditorComponent),
            multi: true
        }
    ]
})
export class MarkdownEditorComponent implements ControlValueAccessor, Validator {
    @ViewChild('aceEditor')
    aceEditorContainer: ElementRef;

    @Input()
    hideToolbar: boolean = false;

    @Input()
    height: string = '300px';

    @Input()
    preRender: Function;

    @Input()
    get mode(): string {
        return this._mode || 'editor';
    }
    set mode(value: string) {
        if (!value || (value.toLowerCase() !== 'editor' && value.toLowerCase() !== 'preview')) {
            value = 'editor';
        }
        this._mode = value;
    }
    _mode: string;

    @Input()
    get options(): any {
        return this._options;
    }
    set options(value: any) {
        this._options = value || {
            showBorder: true,
            hideIcons: []
        };
        this._hideIcons = {};
        (this._options.hideIcons || []).forEach((v: any) => {
            this._hideIcons[v] = true;
        });
    }
    _options: any;
    _hideIcons: any = {};

    get markdownValue(): any {
        return this._markdownValue || '';
    }
    set markdownValue(value: any) {
        this._markdownValue = value;
        this._onChange(value);

        if (this.preRender && this.preRender instanceof Function) {
            value = this.preRender(value);
        }
        if (value !== null && value !== undefined) {
            if (this._renderMarkTimeout) clearTimeout(this._renderMarkTimeout);
            this._renderMarkTimeout = setTimeout(() => {
                // TODO: use showdown or (even better) include the ArtemisMarkdown in markdown.service.ts
                let html = marked(value || '', this._markedOpt);
                this._previewHtml = this._domSanitizer.bypassSecurityTrustHtml(html);
            }, 100);
        }
    }
    _markdownValue: any;

    _renderMarkTimeout: any;

    editor: any;

    showPreviewPanel: boolean = true;
    isFullScreen: boolean = false;

    _markedOpt: any;
    _previewHtml: any;

    _onChange = (_: any) => {};
    _onTouched = () => {};

    constructor(
        @Attribute('required') public required: boolean = false,
        @Attribute('maxlength') public maxlength: number = -1,
        private _renderer: Renderer,
        private _domSanitizer: DomSanitizer
    ) {}

    ngOnInit() {
        let _markedRender = new marked.Renderer();
        _markedRender.code = (code: any, language: any) => {
            let validLang = !!(language && hljs.getLanguage(language));
            let highlighted = validLang ? hljs.highlight(language, code).value : code;
            return `<pre style="padding: 0; border-radius: 0;"><code class="hljs ${language}">${highlighted}</code></pre>`;
        };
        _markedRender.table = (header: string, body: string) => {
            return `<table class="table table-bordered">\n<thead>\n${header}</thead>\n<tbody>\n${body}</tbody>\n</table>\n`;
        };

        _markedRender.listitem = (text: any) => {
            if (/^\s*\[[x ]\]\s*/.test(text)) {
                text = text
                    .replace(/^\s*\[ \]\s*/, '<i class="fa fa-square-o" style="margin: 0 0.2em 0.25em -1.6em;"></i> ')
                    .replace(/^\s*\[x\]\s*/, '<i class="fa fa-check-square" style="margin: 0 0.2em 0.25em -1.6em;"></i> ');
                return `<li style="list-style: none;">${text}</li>`;
            } else {
                return `<li>${text}</li>`;
            }
        };

        this._markedOpt = {
            renderer: _markedRender,
            highlight: (code: any) => hljs.highlightAuto(code).value
        };
    }

    ngAfterViewInit() {
        let editorElement = this.aceEditorContainer.nativeElement;
        this.editor = ace.edit(editorElement);
        this.editor.$blockScrolling = Infinity;
        this.editor.getSession().setUseWrapMode(true);
        this.editor.getSession().setMode('ace/mode/markdown');
        this.editor.setValue(this.markdownValue || '');

        this.editor.on('change', (e: any) => {
            let val = this.editor.getValue();
            this.markdownValue = val;
        });
    }

    ngOnDestroy() {}

    writeValue(value: any | Array<any>): void {
        setTimeout(() => {
            this.markdownValue = value;
            if (typeof value !== 'undefined' && this.editor) {
                this.editor.setValue(value || '');
            }
        }, 1);
    }

    registerOnChange(fn: (_: any) => {}): void {
        this._onChange = fn;
    }

    registerOnTouched(fn: () => {}): void {
        this._onTouched = fn;
    }

    validate(c: AbstractControl): ValidationErrors {
        let result: any = null;
        if (this.required && this.markdownValue.length === 0) {
            result = { required: true };
        }
        if (this.maxlength > 0 && this.markdownValue.length > this.maxlength) {
            result = { maxlength: true };
        }
        return result;
    }

    insertContent(type: string) {
        //TODO: if text is already formatted with the same type, then "unformat it", i.e. remove the type
        if (!this.editor) return;
        let selectedText = this.editor.getSelectedText();
        let isSeleted = !!selectedText;
        let startSize = 2;
        let initText: string = '';
        let range = this.editor.selection.getRange();
        switch (type) {
            case 'Bold':
                initText = 'Bold Text';
                selectedText = `**${selectedText || initText}**`;
                break;
            case 'Italic':
                initText = 'Italic Text';
                selectedText = `*${selectedText || initText}*`;
                startSize = 1;
                break;
            case 'Heading':
                initText = 'Heading';
                selectedText = `# ${selectedText || initText}`;
                break;
            case 'Reference':
                initText = 'Reference';
                selectedText = `> ${selectedText || initText}`;
                break;
            case 'Link':
                selectedText = `[](http://)`;
                startSize = 1;
                break;
            case 'Image':
                selectedText = `![](http://)`;
                break;
            case 'Ul':
                selectedText = `- ${selectedText || initText}`;
                break;
            case 'Ol':
                selectedText = `1. ${selectedText || initText}`;
                startSize = 3;
                break;
            case 'Code':
                initText = 'Source Code';
                selectedText = '```language\r\n' + (selectedText || initText) + '\r\n```';
                startSize = 3;
                break;
        }
        this.editor.session.replace(range, selectedText);
        if (!isSeleted) {
            range.start.column += startSize;
            range.end.column = range.start.column + initText.length;
            this.editor.selection.setRange(range);
        }
        this.editor.focus();
    }

    togglePreview() {
        this.showPreviewPanel = !this.showPreviewPanel;
        this.editorResize();
    }

    previewPanelClick(event: Event) {
        event.preventDefault();
        event.stopPropagation();
    }

    preRenderFunc(content: string) {
        return content.replace(/something/g, 'new value'); // must return a string
    }

    fullScreen() {
        this.isFullScreen = !this.isFullScreen;
        this._renderer.setElementStyle(document.body, 'overflowY', this.isFullScreen ? 'hidden' : 'auto');
        this.editorResize();
    }

    editorResize(timeOut: number = 100) {
        if (this.editor) {
            setTimeout(() => {
                this.editor.resize();
                this.editor.focus();
            }, timeOut);
        }
    }
}
