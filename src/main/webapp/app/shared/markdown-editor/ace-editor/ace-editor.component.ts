import { Component, ElementRef, EventEmitter, forwardRef, Input, NgZone, OnDestroy, OnInit, Output } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import 'brace';
import 'brace/theme/monokai';
import 'brace/theme/chrome';
import 'brace/theme/dreamweaver';
import 'brace/theme/dracula';
import { ThemeService } from 'app/core/theme/theme.service';
import { Subscription } from 'rxjs';

declare var ace: any;

export const MAX_TAB_SIZE = 8;

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

    /**
     * Sets the size in spaces of newly inserted tabs and the display size of existing true tabs.
     *
     * @param value The display width between 1 and {@link MAX_TAB_SIZE} (both inclusive).
     */
    @Input('tabSize')
    public set tabSize(value: number) {
        if (value > 0 && value <= MAX_TAB_SIZE) {
            this._editor.session.setTabSize(value);
        }
    }

    oldText: string;
    timeoutSaving: any;

    private _options: any = {};
    private _readOnly = false;
    private _theme = 'dreamweaver';
    private _mode = 'java';
    private _autoUpdateContent = true;
    private _editor: any; // TODO: use Editor (defined in brace) or Editor (defined in ace-builds) and make sure to use typings consistently
    private _durationBeforeCallback = 0;
    private _text = '';

    private themeSubscription: Subscription;

    constructor(elementRef: ElementRef, private zone: NgZone, private themeService: ThemeService) {
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
        this.themeSubscription?.unsubscribe();
    }

    init() {
        this.setOptions(this._options || {});
        this.setMode(this._mode);
        this.setReadOnly(this._readOnly);
    }

    initEvents() {
        this._editor.on('change', () => this.updateText());
        this._editor.on('paste', () => this.updateText());

        this.themeSubscription = this.themeService.getCurrentThemeObservable().subscribe(() => this.setThemeFromMode());
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

        this.setThemeFromMode();
    }

    private setThemeFromMode() {
        const currentApplicationTheme = this.themeService.getCurrentTheme();
        this._theme = this._mode.toLowerCase() === 'markdown' ? currentApplicationTheme.markdownAceTheme : currentApplicationTheme.codeAceTheme;
        this._editor.setTheme(`ace/theme/${this._theme}`);
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
