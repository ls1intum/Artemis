import { Component, EventEmitter, Input, OnDestroy, QueryList, ViewChildren } from '@angular/core';
import { ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors } from '@angular/forms';
import { map } from 'rxjs/operators';
import { Subscription } from 'rxjs';
import { SafeHtml } from '@angular/platform-browser';
import { faPen } from '@fortawesome/free-solid-svg-icons/faPen';
import { faImage } from '@fortawesome/free-solid-svg-icons/faImage';
import { faICursor } from '@fortawesome/free-solid-svg-icons/faICursor';
import { faEye } from '@fortawesome/free-solid-svg-icons/faEye';
import { faSpinner } from '@fortawesome/free-solid-svg-icons/faSpinner';
import { faExpand } from '@fortawesome/free-solid-svg-icons/faExpand';
import { faEyeSlash } from '@fortawesome/free-solid-svg-icons/faEyeSlash';
import { faClose } from '@fortawesome/free-solid-svg-icons/faClose';

import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ButtonSize, ButtonType } from 'app/shared/components/button.component';

import { MathTaskExpressionValue } from './types';
import { ExpressionChildInput } from './input';

enum ExpressionInputMode {
    TEXT,
    SKETCH,
    IMAGE,
}

const ExpressionInputModes = [ExpressionInputMode.TEXT, ExpressionInputMode.SKETCH, ExpressionInputMode.IMAGE];

const ExpressionInputModeIcons = {
    [ExpressionInputMode.TEXT]: faICursor,
    [ExpressionInputMode.SKETCH]: faPen,
    [ExpressionInputMode.IMAGE]: faImage,
};

@Component({
    selector: 'jhi-math-task-expression-input',
    templateUrl: './expression-input.component.html',
    styleUrls: ['./expression-input.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            multi: true,
            useExisting: ExpressionInputComponent,
        },
        {
            provide: NG_VALIDATORS,
            multi: true,
            useExisting: ExpressionInputComponent,
        },
    ],
})
export class ExpressionInputComponent implements ControlValueAccessor, OnDestroy {
    protected inputMode = ExpressionInputMode.TEXT;
    protected readonly ExpressionInputMode = ExpressionInputMode;
    protected readonly ExpressionInputModes = ExpressionInputModes;
    protected readonly ExpressionInputModeIcons = ExpressionInputModeIcons;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;
    protected readonly faSpinner = faSpinner;
    protected readonly faExpand = faExpand;
    protected readonly faEye = faEye;
    protected readonly faEyeSlash = faEyeSlash;
    protected readonly faClose = faClose;

    /**
     * Whether the input is disabled. Used internally to store the value,
     * which is modified by the ControlValueAccessor.
     * @protected
     */
    protected disabled = false;

    /**
     * Whether the preview is shown.
     * @protected
     */
    protected preview = false;

    /**
     * Whether the preview has an error.
     * @protected
     */
    protected previewError: string | null;

    /**
     * Whether the preview is currently loading.
     * @protected
     */
    protected previewLoading = false;

    /**
     * The preview HTML.
     * @protected
     */
    protected previewSafeHtml: SafeHtml | null;

    /**
     * The current expression value. Used internally to store the value,
     * and should not be used directly. Use the expression property instead.
     * @private
     */
    private _value: string | null;

    /**
     * An event emitter for the expression value. Used internally to emit
     * changes to the expression value, and should not be used directly.
     * Use the expression property setter instead.
     * @private
     */
    private _valueChange = new EventEmitter<string | null>();

    /**
     * References to the child inputs. Used to get the expression from the
     * dynamic list of child inputs. However, only one child input is active
     * at a time, so the list has effectively only one element.
     * @protected
     */
    @ViewChildren('childInputs')
    protected childInputs: QueryList<ExpressionChildInput>;

    /**
     * A store for the values of the child inputs, so that when switching
     * between them, the values are not lost.
     * @protected
     */
    protected valueStore: any | null;

    private onTouched = () => {};
    private onChangeSubs: Subscription[] = [];

    /**
     * The current expression value.
     * @protected
     */
    protected get expression(): string | null {
        return this._value;
    }

    /**
     * Set the current expression value.
     * This will emit the valueChange event.
     * @param value
     * @protected
     */
    protected set expression(value: string | null) {
        this._value = value;
        this._valueChange.emit(value);
    }

    /**
     *
     * @param mode
     * @protected
     */
    protected setInputMode(mode: ExpressionInputMode) {
        this.inputMode = mode;
        this.resetValueStore();
        this.resetPreview();
    }

    /**
     * Get whether the preview is available. This is the case if the
     * value store is not empty, which contains the value of the
     * currently active child input.
     * @protected
     */
    protected get previewAvailable(): boolean {
        return !!this.valueStore;
    }

    /**
     * Change whether the preview is shown. This will load the expression
     * from the child input and render it as HTML.
     * @param showPreview
     * @protected
     */
    protected set showPreview(showPreview: boolean) {
        this.previewSafeHtml = null;
        this.previewError = null;

        if (!showPreview) {
            this.preview = false;
            return;
        }

        this.previewLoading = true;

        this.childInputs.first.getExpression().subscribe(({ expression, error }) => {
            // store the expression
            this.expression = expression ?? null;
            // render the expression as HTML
            this.previewSafeHtml = expression ? this.markdown.safeHtmlForMarkdown(expression) : null;
            // close the preview if the expression is empty
            this.previewLoading = false;
            this.previewError = error ?? null;
            this.preview = true;
        });
    }

    /**
     * Get whether the preview is shown.
     * @protected
     */
    protected get showPreview(): boolean {
        return this.preview;
    }

    /**
     * Reset the preview state.
     * @private
     */
    private resetPreview() {
        this.preview = false;
        this.previewError = null;
        this.previewLoading = false;
        this.previewSafeHtml = null;
    }

    /**
     * Get whether the fullscreen button is supported by the current input mode.
     * @protected
     */
    protected get fullscreenSupported(): boolean {
        return [ExpressionInputMode.TEXT, ExpressionInputMode.SKETCH].includes(this.inputMode);
    }

    @Input()
    exerciseId: number;

    constructor(private markdown: ArtemisMarkdownService) {
        this.resetValueStore();
    }

    private resetValueStore() {
        this.valueStore = null;
    }

    // Lifecycle hooks

    ngOnDestroy() {
        for (const sub of this.onChangeSubs) {
            sub.unsubscribe();
        }
    }

    // ControlValueAccessor implementation

    writeValue(value: MathTaskExpressionValue | undefined | null) {
        if (!value) return;

        this._value = value.expression;
    }

    registerOnChange(onChange: (_: MathTaskExpressionValue) => void) {
        this.onChangeSubs.push(this._valueChange.pipe(map((expression) => ({ expression }))).subscribe(onChange));
    }

    registerOnTouched(fn: () => void) {
        this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.disabled = isDisabled;
    }

    validate(): ValidationErrors | null {
        // TODO: validation
        return null;
    }
}
