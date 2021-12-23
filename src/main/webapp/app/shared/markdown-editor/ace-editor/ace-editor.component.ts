import { Component, EventEmitter, Output, ElementRef, Input, forwardRef, OnInit, OnDestroy, NgZone } from '@angular/core';
import { NG_VALUE_ACCESSOR, ControlValueAccessor } from '@angular/forms';
import 'brace';
import 'brace/theme/monokai';

declare var ace: any;

@Component({
    selector: 'jhi-ace-editor',
    template: '',
    styles: [':host { display:block;width:100%; }'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: forwardRef(() => AceEditorComponent),
            multi: true,
        },
    ],
})
export class AceEditorComponent implements ControlValueAccessor, OnInit, OnDestroy {
    @Output() textChanged = new EventEmitter();
    @Output() textChange = new EventEmitter();
    @Input() style: any = {};
    _options: any = {};
    _readOnly = false;
    _theme = 'monokai';
    _mode = 'html';
    _autoUpdateContent = true;
    _editor: any; // TODO: use Editor (defined in brace) or Editor (defined in ace-builds) and make sure to use typings consistently
    _durationBeforeCallback = 0;
    _text = '';
    oldText: string;
    timeoutSaving: any;

    constructor(elementRef: ElementRef, private zone: NgZone) {
        const el = elementRef.nativeElement;
        this.zone.runOutsideAngular(() => {
            this._editor = ace['edit'](el);
        });
        this._editor.$blockScrolling = Infinity;
    }

    ngOnInit() {
        this.init();
        this.initEvents();
    }

    ngOnDestroy() {
        this._editor.destroy();
    }

    init() {
        this.setOptions(this._options || {});
        this.setTheme(this._theme);
        this.setMode(this._mode);
        this.setReadOnly(this._readOnly);
    }

    initEvents() {
        this._editor.on('change', () => this.updateText());
        this._editor.on('paste', () => this.updateText());
    }

    updateText() {
        const newVal = this._editor.getValue();
        if (newVal === this.oldText) {
            return;
        }
        if (!this._durationBeforeCallback) {
            this._text = newVal;
            this.zone.run(() => {
                this.textChange.emit(newVal);
                this.textChanged.emit(newVal);
            });
            this._onChange(newVal);
        } else {
            if (this.timeoutSaving) {
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
        this.setOptions(options);
    }

    setOptions(options: any) {
        this._options = options;
        this._editor.setOptions(options || {});
    }

    @Input()
    set readOnly(readOnly: boolean) {
        this.setReadOnly(readOnly);
    }

    setReadOnly(readOnly: boolean) {
        this._readOnly = readOnly;
        this._editor.setReadOnly(readOnly);
    }

    @Input()
    set theme(theme: string) {
        this.setTheme(theme);
    }

    setTheme(theme: string) {
        this._theme = theme;
        this._editor.setTheme(`ace/theme/${theme}`);
    }

    @Input()
    set mode(mode: string) {
        this.setMode(mode);
    }

    setMode(mode: string) {
        this._mode = mode;
        if (typeof this._mode === 'object') {
            this._editor.getSession().setMode(this._mode);
        } else {
            this._editor.getSession().setMode(`ace/mode/${this._mode}`);
        }
    }

    get value() {
        return this.text;
    }

    @Input()
    set value(value: string) {
        this.setText(value);
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    writeValue(value: string) {
        this.setText(value);
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    private _onChange = (_: any) => {};

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    registerOnChange(fn: any) {
        this._onChange = fn;
    }

    private _onTouched = () => {};

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    registerOnTouched(fn: any) {
        this._onTouched = fn;
    }

    get text() {
        return this._text;
    }

    @Input()
    set text(text: string) {
        this.setText(text);
    }

    setText(text: string) {
        if (text == undefined) {
            text = '';
        }
        if (this._text !== text && this._autoUpdateContent) {
            this._text = text;
            this._editor.setValue(text);
            this._onChange(text);
            this._editor.clearSelection();
        }
    }

    @Input()
    set autoUpdateContent(status: boolean) {
        this.setAutoUpdateContent(status);
    }

    setAutoUpdateContent(status: boolean) {
        this._autoUpdateContent = status;
    }

    @Input()
    set durationBeforeCallback(num: number) {
        this.setDurationBeforeCallback(num);
    }

    setDurationBeforeCallback(num: number) {
        this._durationBeforeCallback = num;
    }

    getEditor() {
        return this._editor;
    }
}
