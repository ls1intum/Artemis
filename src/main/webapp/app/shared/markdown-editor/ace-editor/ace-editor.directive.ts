import { Directive, EventEmitter, Output, ElementRef, Input, OnInit, OnDestroy, NgZone } from '@angular/core';
import 'brace';
import 'brace/theme/monokai';

declare var ace: any;

@Directive({
    selector: '[jhiAceEditor]',
})
export class AceEditorDirective implements OnInit, OnDestroy {
    @Output() textChanged = new EventEmitter();
    @Output() textChange = new EventEmitter();
    _options: any = {};
    _readOnly = false;
    _theme = 'monokai';
    _mode = 'html';
    _autoUpdateContent = true;
    _durationBeforeCallback = 0;
    _text = '';
    editor: any;
    oldText: string;
    timeoutSaving: any;

    constructor(elementRef: ElementRef, private zone: NgZone) {
        const el = elementRef.nativeElement;
        this.zone.runOutsideAngular(() => {
            this.editor = ace['edit'](el);
        });
        this.editor.$blockScrolling = Infinity;
    }

    ngOnInit() {
        this.init();
        this.initEvents();
    }

    ngOnDestroy() {
        this.editor.destroy();
    }

    init() {
        this.editor.setOptions(this._options || {});
        this.editor.setTheme(`ace/theme/${this._theme}`);
        this.setMode(this._mode);
        this.editor.setReadOnly(this._readOnly);
    }

    initEvents() {
        this.editor.on('change', () => this.updateText());
        this.editor.on('paste', () => this.updateText());
    }

    updateText() {
        const newVal = this.editor.getValue();
        if (newVal === this.oldText) {
            return;
        }
        if (!this._durationBeforeCallback) {
            this._text = newVal;
            this.zone.run(() => {
                this.textChange.emit(newVal);
                this.textChanged.emit(newVal);
            });
        } else {
            if (this.timeoutSaving != undefined) {
                clearTimeout(this.timeoutSaving);
            }

            this.timeoutSaving = setTimeout(() => {
                this._text = newVal;
                this.zone.run(() => {
                    this.textChange.emit(newVal);
                    this.textChanged.emit(newVal);
                });
                this.timeoutSaving = null;
            }, this._durationBeforeCallback);
        }
        this.oldText = newVal;
    }

    @Input()
    set options(options: any) {
        this._options = options;
        this.editor.setOptions(options || {});
    }

    @Input()
    set readOnly(readOnly: boolean) {
        this._readOnly = readOnly;
        this.editor.setReadOnly(readOnly);
    }

    @Input()
    set theme(theme: string) {
        this._theme = theme;
        this.editor.setTheme(`ace/theme/${theme}`);
    }

    @Input()
    set mode(mode: string) {
        this.setMode(mode);
    }

    setMode(mode: string) {
        this._mode = mode;
        if (typeof this._mode === 'object') {
            this.editor.getSession().setMode(this._mode);
        } else {
            this.editor.getSession().setMode(`ace/mode/${this._mode}`);
        }
    }

    @Input()
    get text() {
        return this._text;
    }

    set text(text: string) {
        this.setText(text);
    }

    setText(text: string) {
        if (this._text !== text) {
            if (text == undefined) {
                text = '';
            }

            if (this._autoUpdateContent) {
                this._text = text;
                this.editor.setValue(text);
                this.editor.clearSelection();
            }
        }
    }

    @Input()
    set autoUpdateContent(status: boolean) {
        this._autoUpdateContent = status;
    }

    @Input()
    set durationBeforeCallback(num: number) {
        this.setDurationBeforeCallback(num);
    }

    setDurationBeforeCallback(num: number) {
        this._durationBeforeCallback = num;
    }

    get aceEditor() {
        return this.editor;
    }
}
