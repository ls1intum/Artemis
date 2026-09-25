import * as i0 from '@angular/core';
import { inject, ElementRef, Injectable, InjectionToken, makeEnvironmentProviders, Pipe, input, booleanAttribute, output, computed, ChangeDetectionStrategy, Component, ViewContainerRef, DestroyRef, numberAttribute, viewChild, TemplateRef, signal, effect, forwardRef, afterNextRender, model, contentChild, Directive, linkedSignal, viewChildren, afterRenderEffect, Injector, untracked, contentChildren, HostAttributeToken, ViewEncapsulation } from '@angular/core';
import * as i1$2 from '@angular/forms';
import { NG_VALUE_ACCESSOR, FormsModule } from '@angular/forms';
import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import { TemplatePortal, ComponentPortal } from '@angular/cdk/portal';
import { Overlay } from '@angular/cdk/overlay';
import { Directionality } from '@angular/cdk/bidi';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { faXmark, faSpinner, faCheck, faMinus, faChevronRight, faChevronLeft, faCalendar, faGlobe, faClock, faChevronUp, faChevronDown, faAnglesRight, faAnglesLeft, faAngleRight, faAngleLeft, faMagnifyingGlass, faSort, faSortDown, faSortUp, faCircleQuestion, faForwardStep } from '@fortawesome/free-solid-svg-icons';
import { Dialog } from '@angular/cdk/dialog';
import * as i1 from '@angular/cdk/a11y';
import { A11yModule, ListKeyManager, FocusKeyManager } from '@angular/cdk/a11y';
import dayjs from 'dayjs/esm';
import { Subscription, fromEvent } from 'rxjs';
import customParseFormat from 'dayjs/esm/plugin/customParseFormat';
import * as i1$1 from '@angular/cdk/menu';
import { CdkMenuItem, CdkMenuTrigger, CdkMenu } from '@angular/cdk/menu';
import * as i1$3 from '@angular/cdk/scrolling';
import { ScrollingModule } from '@angular/cdk/scrolling';
import * as i1$4 from '@angular/cdk/table';
import { CdkTable, CdkTableModule } from '@angular/cdk/table';
import { get } from 'lodash-es';

const OFFSET = 8;
const VERTICAL_POSITIONS = {
    top: [
        { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -OFFSET },
        { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: OFFSET },
    ],
    bottom: [
        { originX: 'center', originY: 'bottom', overlayX: 'center', overlayY: 'top', offsetY: OFFSET },
        { originX: 'center', originY: 'top', overlayX: 'center', overlayY: 'bottom', offsetY: -OFFSET },
    ],
};
class TumUiOverlayService {
    overlay = inject(Overlay);
    directionality = inject(Directionality);
    positionStrategy(origin, placement) {
        const positions = placement === 'top' || placement === 'bottom' ? VERTICAL_POSITIONS[placement] : this.horizontalPositions(placement);
        return this.overlay.position().flexibleConnectedTo(origin).withPositions(positions).withFlexibleDimensions(false).withPush(true);
    }
    placementFromPosition(pos) {
        if (pos.overlayY === 'center') {
            const leftEdge = this.directionality.value === 'rtl' ? 'start' : 'end';
            return pos.overlayX === leftEdge ? 'left' : 'right';
        }
        return pos.overlayY === 'bottom' ? 'top' : 'bottom';
    }
    createConnectedOverlay(origin, placement, options = {}) {
        const originElement = origin instanceof ElementRef ? origin.nativeElement : origin;
        const overlayRef = this.overlay.create({
            positionStrategy: this.positionStrategy(origin, placement),
            scrollStrategy: this.overlay.scrollStrategies.reposition(),
            hasBackdrop: options.hasBackdrop ?? false,
            backdropClass: 'cdk-overlay-transparent-backdrop',
            direction: this.directionality,
            width: options.matchOriginWidth ? originElement.getBoundingClientRect().width : undefined,
        });
        if (options.matchOriginWidth && typeof ResizeObserver !== 'undefined') {
            const resizeObserver = new ResizeObserver(() => overlayRef.updateSize({ width: originElement.getBoundingClientRect().width }));
            const disconnect = () => resizeObserver.disconnect();
            resizeObserver.observe(originElement);
            overlayRef.detachments().subscribe({ next: disconnect, complete: disconnect });
        }
        return overlayRef;
    }
    horizontalPositions(placement) {
        const leftEdge = this.directionality.value === 'rtl' ? 'end' : 'start';
        const rightEdge = this.directionality.value === 'rtl' ? 'start' : 'end';
        const left = { originX: leftEdge, originY: 'center', overlayX: rightEdge, overlayY: 'center', offsetX: -OFFSET };
        const right = { originX: rightEdge, originY: 'center', overlayX: leftEdge, overlayY: 'center', offsetX: OFFSET };
        return placement === 'left' ? [left, right] : [right, left];
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiOverlayService, deps: [], target: i0.ɵɵFactoryTarget.Injectable });
    static ɵprov = i0.ɵɵngDeclareInjectable({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiOverlayService, providedIn: 'root' });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiOverlayService, decorators: [{
            type: Injectable,
            args: [{ providedIn: 'root' }]
        }] });

const TUM_UI_DEFAULT_TRANSLATIONS = {
    'tumUi.autocomplete.empty': 'No results found',
    'tumUi.autocomplete.remove': 'Remove',
    'tumUi.chip.remove': 'Remove',
    'tumUi.datePicker.timeZoneWarning': 'The displayed date and time use the {timeZone} time zone.',
    'tumUi.datePicker.clear': 'Clear date',
    'tumUi.datePicker.decrementHour': 'Decrement hour',
    'tumUi.datePicker.decrementMinute': 'Decrement minute',
    'tumUi.datePicker.dialog': 'Choose date and time',
    'tumUi.datePicker.done': 'Done',
    'tumUi.datePicker.hour': 'Hour',
    'tumUi.datePicker.incrementHour': 'Increment hour',
    'tumUi.datePicker.incrementMinute': 'Increment minute',
    'tumUi.datePicker.invalid': 'Enter a valid date and time.',
    'tumUi.datePicker.invalidTime': 'Enter a valid time.',
    'tumUi.datePicker.minute': 'Minute',
    'tumUi.datePicker.open': 'Open calendar',
    'tumUi.datePicker.openTime': 'Open clock',
    'tumUi.datePicker.placeholder': 'DD.MM.YYYY HH:mm',
    'tumUi.datePicker.nextMonth': 'Next month: {month}',
    'tumUi.datePicker.previousMonth': 'Previous month: {month}',
    'tumUi.datePicker.time': 'Time',
    'tumUi.datePicker.timeDialog': 'Choose time',
    'tumUi.datePicker.timePlaceholder': 'HH:mm',
    'tumUi.dialog.close': 'Close',
    'tumUi.panel.collapse': 'Collapse',
    'tumUi.panel.expand': 'Expand',
    'tumUi.paginator.ariaLabel': 'Pagination',
    'tumUi.paginator.currentPageReport': 'Showing {first} to {second} of {total}',
    'tumUi.paginator.first': 'First page',
    'tumUi.paginator.last': 'Last page',
    'tumUi.paginator.next': 'Next page',
    'tumUi.paginator.previous': 'Previous page',
    'tumUi.paginator.rowsPerPage': 'Rows per page',
    'tumUi.searchField.clear': 'Clear search',
    'tumUi.searchField.placeholder': 'Search',
    'tumUi.select.clear': 'Clear selection',
    'tumUi.select.empty': 'No available options',
    'tumUi.select.filter': 'Filter options',
    'tumUi.select.noResults': 'No matching options',
    'tumUi.step.pending': 'Not started',
    'tumUi.step.current': 'In progress',
    'tumUi.step.complete': 'Done',
    'tumUi.step.failed': 'Failed',
    'tumUi.step.skipped': 'Skipped',
    'tumUi.table.actions': 'Actions',
    'tumUi.table.noResults': 'No results found',
    'tumUi.table.searchPlaceholder': 'Search',
};
function interpolate(template, params) {
    if (!params) {
        return template;
    }
    return template.replace(/\{(\w+)\}/g, (match, name) => String(params[name] ?? match));
}
const defaultTranslator = {
    translate: (key, params) => interpolate(TUM_UI_DEFAULT_TRANSLATIONS[key] ?? key, params),
};
const TUM_UI_TRANSLATOR = new InjectionToken('TUM_UI_TRANSLATOR', {
    providedIn: 'root',
    factory: () => defaultTranslator,
});
function provideTumUiTranslator(translator) {
    return makeEnvironmentProviders([{ provide: TUM_UI_TRANSLATOR, useClass: translator }]);
}

class TumUiTranslatePipe {
    translator = inject(TUM_UI_TRANSLATOR);
    transform(key, params) {
        if (!key) {
            return '';
        }
        this.translator.translationChanges?.();
        return this.translator.translate(key, params);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTranslatePipe, deps: [], target: i0.ɵɵFactoryTarget.Pipe });
    static ɵpipe = i0.ɵɵngDeclarePipe({ minVersion: "14.0.0", version: "22.1.5", ngImport: i0, type: TumUiTranslatePipe, isStandalone: true, name: "tumUiTranslate", pure: false });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTranslatePipe, decorators: [{
            type: Pipe,
            args: [{
                    name: 'tumUiTranslate',
                    pure: false,
                }]
        }] });

class TumUiChipComponent {
    label = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "label" }] : /* istanbul ignore next */ []));
    removable = input(false, { ...(ngDevMode ? { debugName: "removable" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    size = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "size" }] : /* istanbul ignore next */ []));
    removeAriaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "removeAriaLabel" }] : /* istanbul ignore next */ []));
    removed = output();
    faXmark = faXmark;
    chipClasses = computed(() => {
        const small = this.size() === 'small';
        const type = small ? 'tum:gap-1 tum:text-sm' : 'tum:gap-2 tum:text-base';
        const padding = small ? (this.removable() ? 'tum:py-1 tum:ps-2 tum:pe-1' : 'tum:px-2 tum:py-1') : this.removable() ? 'tum:py-2 tum:ps-3 tum:pe-2' : 'tum:px-3 tum:py-2';
        const base = 'tum:inline-flex tum:items-center tum:rounded-2xl tum:bg-hover-background tum:text-text';
        return `${base} ${type} ${padding}`;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "chipClasses" }] : /* istanbul ignore next */ []));
    remove(event) {
        this.removed.emit(event);
    }
    onRemoveKeydown(event) {
        if (event.key === 'Backspace' || event.key === 'Delete') {
            event.preventDefault();
            this.remove(event);
        }
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiChipComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiChipComponent, isStandalone: true, selector: "tum-ui-chip", inputs: { label: { classPropertyName: "label", publicName: "label", isSignal: true, isRequired: false, transformFunction: null }, removable: { classPropertyName: "removable", publicName: "removable", isSignal: true, isRequired: false, transformFunction: null }, size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null }, removeAriaLabel: { classPropertyName: "removeAriaLabel", publicName: "removeAriaLabel", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { removed: "removed" }, ngImport: i0, template: "<span [class]=\"chipClasses()\">\n    @if (label(); as chipLabel) {\n        <span class=\"tum-ui-chip-label tum:truncate\">{{ chipLabel }}</span>\n    } @else {\n        <span class=\"tum-ui-chip-label tum:truncate\"><ng-content /></span>\n    }\n    @if (removable()) {\n        <button\n            type=\"button\"\n            class=\"tum-ui-chip-remove tum:inline-flex tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:leading-none tum:text-inherit\"\n            [attr.aria-label]=\"removeAriaLabel() ?? ('tumUi.chip.remove' | tumUiTranslate)\"\n            (click)=\"remove($event)\"\n            (keydown)=\"onRemoveKeydown($event)\"\n        >\n            <fa-icon [icon]=\"faXmark\" />\n        </button>\n    }\n</span>\n", styles: [":host{display:inline-flex;max-width:100%}.tum-ui-chip-remove{border-radius:50%}.tum-ui-chip-remove:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:1px}\n"], dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }, { kind: "pipe", type: TumUiTranslatePipe, name: "tumUiTranslate" }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiChipComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-chip', imports: [FaIconComponent, TumUiTranslatePipe], changeDetection: ChangeDetectionStrategy.OnPush, template: "<span [class]=\"chipClasses()\">\n    @if (label(); as chipLabel) {\n        <span class=\"tum-ui-chip-label tum:truncate\">{{ chipLabel }}</span>\n    } @else {\n        <span class=\"tum-ui-chip-label tum:truncate\"><ng-content /></span>\n    }\n    @if (removable()) {\n        <button\n            type=\"button\"\n            class=\"tum-ui-chip-remove tum:inline-flex tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:leading-none tum:text-inherit\"\n            [attr.aria-label]=\"removeAriaLabel() ?? ('tumUi.chip.remove' | tumUiTranslate)\"\n            (click)=\"remove($event)\"\n            (keydown)=\"onRemoveKeydown($event)\"\n        >\n            <fa-icon [icon]=\"faXmark\" />\n        </button>\n    }\n</span>\n", styles: [":host{display:inline-flex;max-width:100%}.tum-ui-chip-remove{border-radius:50%}.tum-ui-chip-remove:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:1px}\n"] }]
        }], propDecorators: { label: [{ type: i0.Input, args: [{ isSignal: true, alias: "label", required: false }] }], removable: [{ type: i0.Input, args: [{ isSignal: true, alias: "removable", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], removeAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "removeAriaLabel", required: false }] }], removed: [{ type: i0.Output, args: ["removed"] }] } });

let nextAutoCompleteId = 0;
/** Single- or multi-value ControlValueAccessor with consumer-supplied suggestions. */
class TumUiAutoCompleteComponent {
    overlayService = inject(TumUiOverlayService);
    viewContainerRef = inject(ViewContainerRef);
    destroyRef = inject(DestroyRef);
    document = inject(DOCUMENT);
    /** Suggestions supplied in response to a search request. */
    suggestions = input([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "suggestions" }] : /* istanbul ignore next */ []));
    /** Property name used as the visible label for object values. */
    optionLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "optionLabel" }] : /* istanbul ignore next */ []));
    multiple = input(false, { ...(ngDevMode ? { debugName: "multiple" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    placeholder = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "placeholder" }] : /* istanbul ignore next */ []));
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Minimum query length before a search request emits. */
    minLength = input(1, { ...(ngDevMode ? { debugName: "minLength" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    /** Delay between the latest input and a search request. */
    debounceMs = input(300, { ...(ngDevMode ? { debugName: "debounceMs" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    /** Requests suggestions when the empty input receives focus. */
    completeOnFocus = input(false, { ...(ngDevMode ? { debugName: "completeOnFocus" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    inputId = input(`tum-ui-autocomplete-${nextAutoCompleteId++}`, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "inputId" }] : /* istanbul ignore next */ []));
    name = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "name" }] : /* istanbul ignore next */ []));
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    removeAriaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "removeAriaLabel" }] : /* istanbul ignore next */ []));
    /** Message shown when a completed search returns no suggestions. */
    emptyMessage = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "emptyMessage" }] : /* istanbul ignore next */ []));
    /** Requests suggestions for the current text query. */
    searchRequested = output();
    optionSelected = output();
    optionRemoved = output();
    listboxId = `tum-ui-autocomplete-listbox-${nextAutoCompleteId++}`;
    container = viewChild.required('container', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "container" }] : /* istanbul ignore next */ []));
    textInput = viewChild.required('textInput', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "textInput" }] : /* istanbul ignore next */ []));
    panel = viewChild.required('panel', { ...(ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {}), read: TemplateRef });
    overlayRef;
    selectedValues = signal([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "selectedValues" }] : /* istanbul ignore next */ []));
    singleValue = signal(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "singleValue" }] : /* istanbul ignore next */ []));
    query = signal('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "query" }] : /* istanbul ignore next */ []));
    isFocused = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isFocused" }] : /* istanbul ignore next */ []));
    hasSearched = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hasSearched" }] : /* istanbul ignore next */ []));
    activeIndex = signal(-1, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "activeIndex" }] : /* istanbul ignore next */ []));
    disabledByForm = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "disabledByForm" }] : /* istanbul ignore next */ []));
    debounceTimer;
    onChangeCallback = () => { };
    onTouchedCallback = () => { };
    isDisabled = computed(() => this.disabled() || this.disabledByForm(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []));
    labelKey = computed(() => this.optionLabel(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "labelKey" }] : /* istanbul ignore next */ []));
    panelVisible = computed(() => this.isFocused() && this.hasSearched() && !this.isDisabled() && (this.query().length >= this.minLength() || this.completeOnFocus()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "panelVisible" }] : /* istanbul ignore next */ []));
    activeOptionId = computed(() => (this.panelVisible() && this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : undefined), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "activeOptionId" }] : /* istanbul ignore next */ []));
    inputPlaceholder = computed(() => (this.multiple() && this.selectedValues().length > 0 ? undefined : this.placeholder()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "inputPlaceholder" }] : /* istanbul ignore next */ []));
    inputText = computed(() => {
        if (this.multiple()) {
            return this.query();
        }
        const value = this.singleValue();
        return value == undefined ? '' : this.valueLabel(value);
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "inputText" }] : /* istanbul ignore next */ []));
    constructor() {
        this.destroyRef.onDestroy(() => {
            this.overlayRef?.dispose();
            if (this.debounceTimer) {
                clearTimeout(this.debounceTimer);
            }
        });
        effect(() => {
            if (this.panelVisible()) {
                this.openPanel();
            }
            else {
                this.closePanel();
            }
        });
        effect(() => {
            const optionCount = this.suggestions().length;
            if (this.activeIndex() >= optionCount) {
                this.activeIndex.set(optionCount > 0 ? optionCount - 1 : -1);
            }
        });
    }
    writeValue(value) {
        if (this.multiple()) {
            this.selectedValues.set(Array.isArray(value) ? [...value] : value == undefined ? [] : [value]);
        }
        else {
            this.singleValue.set(value ?? undefined);
        }
    }
    registerOnChange(fn) {
        this.onChangeCallback = fn;
    }
    registerOnTouched(fn) {
        this.onTouchedCallback = fn;
    }
    setDisabledState(isDisabled) {
        this.disabledByForm.set(isDisabled);
    }
    valueLabel(value) {
        const key = this.labelKey();
        const raw = key && value !== null && typeof value === 'object' ? value[key] : value;
        return this.toText(raw);
    }
    toText(value) {
        switch (typeof value) {
            case 'string':
                return value;
            case 'number':
            case 'boolean':
            case 'bigint':
                return String(value);
            default:
                return '';
        }
    }
    valuesMatch(a, b) {
        return Object.is(a, b) || a === b;
    }
    isAlreadySelected(option) {
        return this.selectedValues().some((value) => this.valuesMatch(value, option));
    }
    optionId(index) {
        return `${this.listboxId}-option-${index}`;
    }
    focusInput() {
        if (!this.isDisabled()) {
            this.textInput().nativeElement.focus();
        }
    }
    onFocus(event) {
        this.isFocused.set(true);
        if (this.completeOnFocus() && !this.isDisabled()) {
            this.fireComplete(this.query(), event);
        }
    }
    onBlur() {
        this.isFocused.set(false);
        this.onTouchedCallback();
    }
    onInput(event) {
        const value = event.target.value;
        this.query.set(value);
        this.activeIndex.set(-1);
        if (!this.multiple()) {
            const singleVal = value === '' ? undefined : value;
            this.singleValue.set(singleVal);
            this.onChangeCallback(singleVal);
        }
        if (this.debounceTimer) {
            clearTimeout(this.debounceTimer);
        }
        if (value.length >= this.minLength()) {
            this.debounceTimer = setTimeout(() => this.fireComplete(value, event), this.debounceMs());
        }
        else if (this.completeOnFocus()) {
            this.fireComplete(value, event);
        }
        else {
            this.hasSearched.set(false);
        }
    }
    fireComplete(query, originalEvent) {
        this.searchRequested.emit({ originalEvent, query });
        this.hasSearched.set(true);
    }
    onInputKeydown(event) {
        const count = this.suggestions().length;
        switch (event.key) {
            case 'ArrowDown':
                if (this.panelVisible() && count > 0) {
                    event.preventDefault();
                    this.setActive(Math.min(count - 1, this.activeIndex() + 1));
                }
                break;
            case 'ArrowUp':
                if (this.panelVisible() && count > 0) {
                    event.preventDefault();
                    this.setActive(Math.max(0, this.activeIndex() - 1));
                }
                break;
            case 'Enter':
                if (this.panelVisible() && this.activeIndex() >= 0 && this.activeIndex() < count) {
                    event.preventDefault();
                    this.selectOption(this.suggestions()[this.activeIndex()], event);
                }
                break;
            case 'Escape':
                if (this.panelVisible()) {
                    event.stopPropagation();
                    this.hasSearched.set(false);
                }
                break;
            case 'Backspace':
                if (this.multiple() && this.query().length === 0 && this.selectedValues().length > 0) {
                    this.removeAt(this.selectedValues().length - 1, event);
                }
                break;
        }
    }
    setActive(index) {
        this.activeIndex.set(index);
        this.document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: 'nearest' });
    }
    selectOption(option, event) {
        if (this.multiple()) {
            if (!this.isAlreadySelected(option)) {
                const next = [...this.selectedValues(), option];
                this.selectedValues.set(next);
                this.onChangeCallback(next);
                this.optionSelected.emit({ originalEvent: event, value: option });
            }
        }
        else {
            this.singleValue.set(option);
            this.onChangeCallback(option);
            this.optionSelected.emit({ originalEvent: event, value: option });
        }
        this.clearInput();
        this.focusInput();
    }
    removeAt(index, event) {
        const current = this.selectedValues();
        if (index < 0 || index >= current.length) {
            return;
        }
        const removed = current[index];
        const next = current.filter((_, i) => i !== index);
        this.selectedValues.set(next);
        this.onChangeCallback(next);
        this.optionRemoved.emit({ originalEvent: event, value: removed });
        this.focusInput();
    }
    clearInput() {
        this.query.set('');
        this.hasSearched.set(false);
        this.activeIndex.set(-1);
    }
    openPanel() {
        if (this.overlayRef) {
            return;
        }
        const origin = this.container();
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, 'bottom', { matchOriginWidth: true });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
    }
    closePanel() {
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
    }
    optionClasses(option, index) {
        const base = 'tum-ui-autocomplete-option tum:flex tum:cursor-pointer tum:items-center tum:px-3 tum:py-2';
        const active = this.activeIndex() === index;
        if (this.isAlreadySelected(option)) {
            const background = active ? 'tum:bg-highlight-focus-background' : 'tum:bg-highlight-background';
            return `${base} tum:text-highlight ${background}`;
        }
        const activeState = active ? ' tum:bg-highlight-focus-background tum:text-highlight' : '';
        return `${base} tum:text-text tum:hover:bg-hover-background tum:hover:text-text-hover${activeState}`;
    }
    containerClasses() {
        const padding = this.multiple() && this.selectedValues().length > 0 ? 'tum:p-1' : 'tum:py-1 tum:px-3';
        const base = `tum-ui-autocomplete-container tum:box-border tum:flex tum:w-full tum:cursor-text tum:flex-wrap tum:items-center tum:gap-1 tum:rounded-md tum:border tum:text-base tum:transition-colors ` +
            `tum:focus-within:outline tum:focus-within:outline-2 tum:focus-within:outline-focus tum:focus-within:outline-offset-2 ${padding}`;
        let state;
        if (this.isDisabled()) {
            state = 'tum:bg-disabled-background tum:text-disabled tum:border-control-border';
        }
        else if (this.isFocused()) {
            state = 'tum:bg-control-background tum:text-text tum:border-focus';
        }
        else {
            state = 'tum:bg-control-background tum:text-text tum:border-control-border tum:hover:border-control-border-hover';
        }
        return `${base} ${state}`;
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiAutoCompleteComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiAutoCompleteComponent, isStandalone: true, selector: "tum-ui-autocomplete", inputs: { suggestions: { classPropertyName: "suggestions", publicName: "suggestions", isSignal: true, isRequired: false, transformFunction: null }, optionLabel: { classPropertyName: "optionLabel", publicName: "optionLabel", isSignal: true, isRequired: false, transformFunction: null }, multiple: { classPropertyName: "multiple", publicName: "multiple", isSignal: true, isRequired: false, transformFunction: null }, placeholder: { classPropertyName: "placeholder", publicName: "placeholder", isSignal: true, isRequired: false, transformFunction: null }, disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, minLength: { classPropertyName: "minLength", publicName: "minLength", isSignal: true, isRequired: false, transformFunction: null }, debounceMs: { classPropertyName: "debounceMs", publicName: "debounceMs", isSignal: true, isRequired: false, transformFunction: null }, completeOnFocus: { classPropertyName: "completeOnFocus", publicName: "completeOnFocus", isSignal: true, isRequired: false, transformFunction: null }, inputId: { classPropertyName: "inputId", publicName: "inputId", isSignal: true, isRequired: false, transformFunction: null }, name: { classPropertyName: "name", publicName: "name", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, removeAriaLabel: { classPropertyName: "removeAriaLabel", publicName: "removeAriaLabel", isSignal: true, isRequired: false, transformFunction: null }, emptyMessage: { classPropertyName: "emptyMessage", publicName: "emptyMessage", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { searchRequested: "searchRequested", optionSelected: "optionSelected", optionRemoved: "optionRemoved" }, host: { classAttribute: "tum-ui-autocomplete" }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiAutoCompleteComponent), multi: true }], viewQueries: [{ propertyName: "container", first: true, predicate: ["container"], descendants: true, isSignal: true }, { propertyName: "textInput", first: true, predicate: ["textInput"], descendants: true, isSignal: true }, { propertyName: "panel", first: true, predicate: ["panel"], descendants: true, read: TemplateRef, isSignal: true }], ngImport: i0, template: "<div #container [class]=\"containerClasses()\" (click)=\"focusInput()\">\n    @if (multiple()) {\n        @for (value of selectedValues(); track $index; let i = $index) {\n            <tum-ui-chip\n                size=\"small\"\n                [label]=\"valueLabel(value)\"\n                [removable]=\"!isDisabled()\"\n                [removeAriaLabel]=\"removeAriaLabel() ?? ('tumUi.autocomplete.remove' | tumUiTranslate)\"\n                (removed)=\"removeAt(i, $event)\"\n            />\n        }\n    }\n    <input\n        #textInput\n        type=\"text\"\n        role=\"combobox\"\n        [id]=\"inputId()\"\n        [attr.name]=\"name()\"\n        class=\"tum-ui-autocomplete-input tum:min-w-16 tum:flex-1 tum:border-0 tum:bg-transparent tum:p-0 tum:text-base tum:text-inherit tum:outline-none tum:placeholder:text-muted\"\n        [value]=\"inputText()\"\n        [attr.placeholder]=\"inputPlaceholder()\"\n        [disabled]=\"isDisabled()\"\n        autocomplete=\"off\"\n        aria-autocomplete=\"list\"\n        [attr.aria-expanded]=\"panelVisible() ? 'true' : 'false'\"\n        [attr.aria-controls]=\"panelVisible() ? listboxId : null\"\n        [attr.aria-activedescendant]=\"activeOptionId()\"\n        [attr.aria-label]=\"ariaLabel()\"\n        (input)=\"onInput($event)\"\n        (focus)=\"onFocus($event)\"\n        (blur)=\"onBlur()\"\n        (keydown)=\"onInputKeydown($event)\"\n    />\n</div>\n\n<ng-template #panel>\n    <div class=\"tum-ui-autocomplete-panel tum:box-border tum:w-full tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-md\">\n        <ul\n            [id]=\"listboxId\"\n            role=\"listbox\"\n            [attr.aria-label]=\"ariaLabel()\"\n            [attr.aria-multiselectable]=\"multiple() ? 'true' : null\"\n            class=\"tum:m-0 tum:flex tum:max-h-60 tum:list-none tum:flex-col tum:gap-0.5 tum:overflow-y-auto tum:p-1\"\n        >\n            @for (option of suggestions(); track $index; let i = $index) {\n                <li\n                    [id]=\"optionId(i)\"\n                    role=\"option\"\n                    [attr.aria-selected]=\"isAlreadySelected(option) ? 'true' : 'false'\"\n                    [class]=\"optionClasses(option, i)\"\n                    (mousedown)=\"$event.preventDefault()\"\n                    (click)=\"selectOption(option, $event)\"\n                    (mouseenter)=\"setActive(i)\"\n                >\n                    <span class=\"tum:flex-1 tum:truncate\">{{ valueLabel(option) }}</span>\n                </li>\n            } @empty {\n                <li role=\"option\" aria-selected=\"false\" aria-disabled=\"true\" class=\"tum:px-3 tum:py-2 tum:text-muted\">\n                    {{ emptyMessage() ?? ('tumUi.autocomplete.empty' | tumUiTranslate) }}\n                </li>\n            }\n        </ul>\n    </div>\n</ng-template>\n", styles: [":host{display:block;max-width:100%}\n"], dependencies: [{ kind: "component", type: TumUiChipComponent, selector: "tum-ui-chip", inputs: ["label", "removable", "size", "removeAriaLabel"], outputs: ["removed"] }, { kind: "pipe", type: TumUiTranslatePipe, name: "tumUiTranslate" }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiAutoCompleteComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-autocomplete', imports: [TumUiChipComponent, TumUiTranslatePipe], host: {
                        // The application stylesheet excludes TUM UI controls from the JHipster validity accent by this class.
                        class: 'tum-ui-autocomplete',
                    }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiAutoCompleteComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<div #container [class]=\"containerClasses()\" (click)=\"focusInput()\">\n    @if (multiple()) {\n        @for (value of selectedValues(); track $index; let i = $index) {\n            <tum-ui-chip\n                size=\"small\"\n                [label]=\"valueLabel(value)\"\n                [removable]=\"!isDisabled()\"\n                [removeAriaLabel]=\"removeAriaLabel() ?? ('tumUi.autocomplete.remove' | tumUiTranslate)\"\n                (removed)=\"removeAt(i, $event)\"\n            />\n        }\n    }\n    <input\n        #textInput\n        type=\"text\"\n        role=\"combobox\"\n        [id]=\"inputId()\"\n        [attr.name]=\"name()\"\n        class=\"tum-ui-autocomplete-input tum:min-w-16 tum:flex-1 tum:border-0 tum:bg-transparent tum:p-0 tum:text-base tum:text-inherit tum:outline-none tum:placeholder:text-muted\"\n        [value]=\"inputText()\"\n        [attr.placeholder]=\"inputPlaceholder()\"\n        [disabled]=\"isDisabled()\"\n        autocomplete=\"off\"\n        aria-autocomplete=\"list\"\n        [attr.aria-expanded]=\"panelVisible() ? 'true' : 'false'\"\n        [attr.aria-controls]=\"panelVisible() ? listboxId : null\"\n        [attr.aria-activedescendant]=\"activeOptionId()\"\n        [attr.aria-label]=\"ariaLabel()\"\n        (input)=\"onInput($event)\"\n        (focus)=\"onFocus($event)\"\n        (blur)=\"onBlur()\"\n        (keydown)=\"onInputKeydown($event)\"\n    />\n</div>\n\n<ng-template #panel>\n    <div class=\"tum-ui-autocomplete-panel tum:box-border tum:w-full tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-md\">\n        <ul\n            [id]=\"listboxId\"\n            role=\"listbox\"\n            [attr.aria-label]=\"ariaLabel()\"\n            [attr.aria-multiselectable]=\"multiple() ? 'true' : null\"\n            class=\"tum:m-0 tum:flex tum:max-h-60 tum:list-none tum:flex-col tum:gap-0.5 tum:overflow-y-auto tum:p-1\"\n        >\n            @for (option of suggestions(); track $index; let i = $index) {\n                <li\n                    [id]=\"optionId(i)\"\n                    role=\"option\"\n                    [attr.aria-selected]=\"isAlreadySelected(option) ? 'true' : 'false'\"\n                    [class]=\"optionClasses(option, i)\"\n                    (mousedown)=\"$event.preventDefault()\"\n                    (click)=\"selectOption(option, $event)\"\n                    (mouseenter)=\"setActive(i)\"\n                >\n                    <span class=\"tum:flex-1 tum:truncate\">{{ valueLabel(option) }}</span>\n                </li>\n            } @empty {\n                <li role=\"option\" aria-selected=\"false\" aria-disabled=\"true\" class=\"tum:px-3 tum:py-2 tum:text-muted\">\n                    {{ emptyMessage() ?? ('tumUi.autocomplete.empty' | tumUiTranslate) }}\n                </li>\n            }\n        </ul>\n    </div>\n</ng-template>\n", styles: [":host{display:block;max-width:100%}\n"] }]
        }], ctorParameters: () => [], propDecorators: { suggestions: [{ type: i0.Input, args: [{ isSignal: true, alias: "suggestions", required: false }] }], optionLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "optionLabel", required: false }] }], multiple: [{ type: i0.Input, args: [{ isSignal: true, alias: "multiple", required: false }] }], placeholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "placeholder", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], minLength: [{ type: i0.Input, args: [{ isSignal: true, alias: "minLength", required: false }] }], debounceMs: [{ type: i0.Input, args: [{ isSignal: true, alias: "debounceMs", required: false }] }], completeOnFocus: [{ type: i0.Input, args: [{ isSignal: true, alias: "completeOnFocus", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], name: [{ type: i0.Input, args: [{ isSignal: true, alias: "name", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], removeAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "removeAriaLabel", required: false }] }], emptyMessage: [{ type: i0.Input, args: [{ isSignal: true, alias: "emptyMessage", required: false }] }], searchRequested: [{ type: i0.Output, args: ["searchRequested"] }], optionSelected: [{ type: i0.Output, args: ["optionSelected"] }], optionRemoved: [{ type: i0.Output, args: ["optionRemoved"] }], container: [{ type: i0.ViewChild, args: ['container', { isSignal: true }] }], textInput: [{ type: i0.ViewChild, args: ['textInput', { isSignal: true }] }], panel: [{ type: i0.ViewChild, args: ['panel', { ...{ read: TemplateRef }, isSignal: true }] }] } });

class TumUiButtonGroupComponent {
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiButtonGroupComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "14.0.0", version: "22.1.5", type: TumUiButtonGroupComponent, isStandalone: true, selector: "tum-ui-button-group", host: { attributes: { "role": "group" }, classAttribute: "tum-ui-button-group" }, ngImport: i0, template: '<ng-content />', isInline: true, changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiButtonGroupComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'tum-ui-button-group',
                    template: '<ng-content />',
                    host: {
                        role: 'group',
                        class: 'tum-ui-button-group',
                    },
                    changeDetection: ChangeDetectionStrategy.OnPush,
                }]
        }] });

const BASE$2 = 'tum-ui-btn tum:inline-flex tum:appearance-none tum:items-center tum:justify-center tum:gap-2 tum:rounded-md tum:border tum:font-normal tum:transition-colors tum:focus-visible:outline-none tum:disabled:opacity-60 tum:disabled:pointer-events-none';
const SOLID = {
    primary: 'tum:bg-primary tum:text-primary-contrast tum:border-primary',
    secondary: 'tum:bg-hover-background tum:text-text tum:border-hover-background',
    success: 'tum:bg-state-success tum:text-state-success-contrast tum:border-state-success',
    info: 'tum:bg-state-info tum:text-state-info-contrast tum:border-state-info',
    warn: 'tum:bg-state-warning tum:text-state-warning-contrast tum:border-state-warning',
    danger: 'tum:bg-state-danger tum:text-state-danger-contrast tum:border-state-danger',
    contrast: 'tum:bg-contrast-background tum:text-contrast tum:border-contrast-background',
};
const OUTLINED = {
    primary: 'tum:bg-transparent tum:text-accent tum:border-primary',
    secondary: 'tum:bg-transparent tum:text-text tum:border-border',
    success: 'tum:bg-transparent tum:text-state-success-foreground tum:border-state-success',
    info: 'tum:bg-transparent tum:text-state-info-foreground tum:border-state-info',
    warn: 'tum:bg-transparent tum:text-state-warning-foreground tum:border-state-warning',
    danger: 'tum:bg-transparent tum:text-state-danger-foreground tum:border-state-danger',
    contrast: 'tum:bg-transparent tum:text-contrast-background tum:border-contrast-background',
};
const TEXT = {
    primary: 'tum:bg-transparent tum:text-accent tum:border-transparent',
    secondary: 'tum:bg-transparent tum:text-muted tum:border-transparent',
    success: 'tum:bg-transparent tum:text-state-success-foreground tum:border-transparent',
    info: 'tum:bg-transparent tum:text-state-info-foreground tum:border-transparent',
    warn: 'tum:bg-transparent tum:text-state-warning-foreground tum:border-transparent',
    danger: 'tum:bg-transparent tum:text-state-danger-foreground tum:border-transparent',
    contrast: 'tum:bg-transparent tum:text-contrast-background tum:border-transparent',
};
const SIZE = {
    small: 'tum:text-sm tum:px-2.5 tum:py-1.5',
    default: 'tum:text-base tum:px-3 tum:py-2',
    large: 'tum:text-lg tum:px-4 tum:py-2.5',
};
const VARIANTS = { solid: SOLID, outlined: OUTLINED, text: TEXT };
function tumUiButtonClasses(options) {
    return `${BASE$2} ${VARIANTS[options.variant][options.severity]} ${SIZE[options.size]}`;
}

class TumUiButtonComponent {
    severity = input('primary', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []));
    size = input('default', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    variant = input('solid', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "variant" }] : /* istanbul ignore next */ []));
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    rounded = input(false, { ...(ngDevMode ? { debugName: "rounded" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Replaces the icon with a spinner and disables the button. */
    loading = input(false, { ...(ngDevMode ? { debugName: "loading" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    icon = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "icon" }] : /* istanbul ignore next */ []));
    type = input('button', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "type" }] : /* istanbul ignore next */ []));
    /** Accessible name required when projected content does not label the button. */
    ariaLabel = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    ariaExpanded = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaExpanded" }] : /* istanbul ignore next */ []));
    ariaPressed = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaPressed" }] : /* istanbul ignore next */ []));
    ariaControls = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaControls" }] : /* istanbul ignore next */ []));
    ariaDescribedBy = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaDescribedBy" }] : /* istanbul ignore next */ []));
    clicked = output();
    faSpinner = faSpinner;
    isDisabled = computed(() => this.disabled() || this.loading(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []));
    buttonClasses = computed(() => {
        const rounded = this.rounded() ? 'tum-ui-btn-rounded' : '';
        return `${tumUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() })} ${rounded}`.trim();
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "buttonClasses" }] : /* istanbul ignore next */ []));
    onClick(event) {
        this.clicked.emit(event);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiButtonComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiButtonComponent, isStandalone: true, selector: "tum-ui-button", inputs: { severity: { classPropertyName: "severity", publicName: "severity", isSignal: true, isRequired: false, transformFunction: null }, size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null }, variant: { classPropertyName: "variant", publicName: "variant", isSignal: true, isRequired: false, transformFunction: null }, disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, rounded: { classPropertyName: "rounded", publicName: "rounded", isSignal: true, isRequired: false, transformFunction: null }, loading: { classPropertyName: "loading", publicName: "loading", isSignal: true, isRequired: false, transformFunction: null }, icon: { classPropertyName: "icon", publicName: "icon", isSignal: true, isRequired: false, transformFunction: null }, type: { classPropertyName: "type", publicName: "type", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, ariaExpanded: { classPropertyName: "ariaExpanded", publicName: "ariaExpanded", isSignal: true, isRequired: false, transformFunction: null }, ariaPressed: { classPropertyName: "ariaPressed", publicName: "ariaPressed", isSignal: true, isRequired: false, transformFunction: null }, ariaControls: { classPropertyName: "ariaControls", publicName: "ariaControls", isSignal: true, isRequired: false, transformFunction: null }, ariaDescribedBy: { classPropertyName: "ariaDescribedBy", publicName: "ariaDescribedBy", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { clicked: "clicked" }, ngImport: i0, template: "<button\n    [type]=\"type()\"\n    [class]=\"buttonClasses()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-busy]=\"loading() ? 'true' : null\"\n    [attr.aria-label]=\"ariaLabel()\"\n    [attr.aria-expanded]=\"ariaExpanded()\"\n    [attr.aria-pressed]=\"ariaPressed()\"\n    [attr.aria-controls]=\"ariaControls()\"\n    [attr.aria-describedby]=\"ariaDescribedBy()\"\n    (click)=\"onClick($event)\"\n>\n    @if (loading()) {\n        <fa-icon [icon]=\"faSpinner\" class=\"tum:animate-spin tum:motion-reduce:animate-none\" />\n    } @else if (icon(); as buttonIcon) {\n        <fa-icon [icon]=\"buttonIcon\" />\n    }\n    <ng-content />\n</button>\n", styles: [":host{display:inline-flex}.tum-ui-btn.tum-ui-btn-rounded{border-radius:9999px}.tum-ui-btn{position:relative;appearance:none;cursor:pointer;white-space:nowrap;border-radius:var(--tumaet-ui-radius-md)}.tum-ui-btn:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-btn:hover:not(:disabled):after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}.tum-ui-btn:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-btn:disabled{cursor:default}\n"], dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiButtonComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-button', imports: [FaIconComponent], changeDetection: ChangeDetectionStrategy.OnPush, template: "<button\n    [type]=\"type()\"\n    [class]=\"buttonClasses()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-busy]=\"loading() ? 'true' : null\"\n    [attr.aria-label]=\"ariaLabel()\"\n    [attr.aria-expanded]=\"ariaExpanded()\"\n    [attr.aria-pressed]=\"ariaPressed()\"\n    [attr.aria-controls]=\"ariaControls()\"\n    [attr.aria-describedby]=\"ariaDescribedBy()\"\n    (click)=\"onClick($event)\"\n>\n    @if (loading()) {\n        <fa-icon [icon]=\"faSpinner\" class=\"tum:animate-spin tum:motion-reduce:animate-none\" />\n    } @else if (icon(); as buttonIcon) {\n        <fa-icon [icon]=\"buttonIcon\" />\n    }\n    <ng-content />\n</button>\n", styles: [":host{display:inline-flex}.tum-ui-btn.tum-ui-btn-rounded{border-radius:9999px}.tum-ui-btn{position:relative;appearance:none;cursor:pointer;white-space:nowrap;border-radius:var(--tumaet-ui-radius-md)}.tum-ui-btn:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-btn:hover:not(:disabled):after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}.tum-ui-btn:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-btn:disabled{cursor:default}\n"] }]
        }], propDecorators: { severity: [{ type: i0.Input, args: [{ isSignal: true, alias: "severity", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], variant: [{ type: i0.Input, args: [{ isSignal: true, alias: "variant", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], rounded: [{ type: i0.Input, args: [{ isSignal: true, alias: "rounded", required: false }] }], loading: [{ type: i0.Input, args: [{ isSignal: true, alias: "loading", required: false }] }], icon: [{ type: i0.Input, args: [{ isSignal: true, alias: "icon", required: false }] }], type: [{ type: i0.Input, args: [{ isSignal: true, alias: "type", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaExpanded: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaExpanded", required: false }] }], ariaPressed: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaPressed", required: false }] }], ariaControls: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaControls", required: false }] }], ariaDescribedBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaDescribedBy", required: false }] }], clicked: [{ type: i0.Output, args: ["clicked"] }] } });

class TumUiButtonDirective {
    severity = input('primary', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []));
    size = input('default', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    variant = input('solid', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "variant" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => tumUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() }), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiButtonDirective, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiButtonDirective, isStandalone: true, selector: "a[tumUiButton], button[tumUiButton]", inputs: { severity: { classPropertyName: "severity", publicName: "severity", isSignal: true, isRequired: false, transformFunction: null }, size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null }, variant: { classPropertyName: "variant", publicName: "variant", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "class": "hostClasses()" } }, ngImport: i0, template: '<ng-content />', isInline: true, styles: [":host{position:relative;border-radius:var(--tumaet-ui-radius-md);text-decoration:none}:host:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}:host:hover:not(:disabled):after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}:host:disabled{cursor:default}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiButtonDirective, decorators: [{
            type: Component,
            args: [{ selector: 'a[tumUiButton], button[tumUiButton]', template: '<ng-content />', host: {
                        '[class]': 'hostClasses()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{position:relative;border-radius:var(--tumaet-ui-radius-md);text-decoration:none}:host:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}:host:hover:not(:disabled):after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}:host:disabled{cursor:default}\n"] }]
        }], propDecorators: { severity: [{ type: i0.Input, args: [{ isSignal: true, alias: "severity", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], variant: [{ type: i0.Input, args: [{ isSignal: true, alias: "variant", required: false }] }] } });

class TumUiCardComponent {
    header = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "header" }] : /* istanbul ignore next */ []));
    subheader = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "subheader" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiCardComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiCardComponent, isStandalone: true, selector: "tum-ui-card", inputs: { header: { classPropertyName: "header", publicName: "header", isSignal: true, isRequired: false, transformFunction: null }, subheader: { classPropertyName: "subheader", publicName: "subheader", isSignal: true, isRequired: false, transformFunction: null } }, host: { classAttribute: "tum-ui-card tum:flex tum:flex-col tum:rounded-xl tum:shadow-sm tum:bg-overlay-background tum:text-text" }, ngImport: i0, template: "<ng-content select=\"[tumUiCardHeader]\" />\n<div class=\"tum-ui-card-body tum:flex tum:flex-col tum:gap-2 tum:p-5\">\n    @if (header() || subheader()) {\n        <div class=\"tum-ui-card-caption tum:flex tum:flex-col tum:gap-2\">\n            @if (header()) {\n                <div class=\"tum-ui-card-title tum:text-xl tum:font-medium\">{{ header() }}</div>\n            }\n            @if (subheader()) {\n                <div class=\"tum-ui-card-subtitle tum:text-muted\">{{ subheader() }}</div>\n            }\n        </div>\n    }\n    <div class=\"tum-ui-card-content\">\n        <ng-content />\n    </div>\n    <ng-content select=\"[tumUiCardFooter]\" />\n</div>\n", changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiCardComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-card', host: {
                        class: 'tum-ui-card tum:flex tum:flex-col tum:rounded-xl tum:shadow-sm tum:bg-overlay-background tum:text-text',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<ng-content select=\"[tumUiCardHeader]\" />\n<div class=\"tum-ui-card-body tum:flex tum:flex-col tum:gap-2 tum:p-5\">\n    @if (header() || subheader()) {\n        <div class=\"tum-ui-card-caption tum:flex tum:flex-col tum:gap-2\">\n            @if (header()) {\n                <div class=\"tum-ui-card-title tum:text-xl tum:font-medium\">{{ header() }}</div>\n            }\n            @if (subheader()) {\n                <div class=\"tum-ui-card-subtitle tum:text-muted\">{{ subheader() }}</div>\n            }\n        </div>\n    }\n    <div class=\"tum-ui-card-content\">\n        <ng-content />\n    </div>\n    <ng-content select=\"[tumUiCardFooter]\" />\n</div>\n" }]
        }], propDecorators: { header: [{ type: i0.Input, args: [{ isSignal: true, alias: "header", required: false }] }], subheader: [{ type: i0.Input, args: [{ isSignal: true, alias: "subheader", required: false }] }] } });

/**
 * Minimal scale and tick helpers for the chart components.
 *
 * These cover the linear and band scales the charts need. They deliberately do not depend on
 * `d3-scale` / `d3-array`: the required surface is small enough that pulling in six transitive
 * packages is not justified. If log, time or diverging scales are ever needed, swapping in
 * `d3-scale` behind these signatures is a contained change.
 */
function bandScale(count, size, padding = 0.25) {
    const step = size / Math.max(count, 1);
    const bandwidth = Math.max(step * (1 - padding), 0);
    const offset = (step - bandwidth) / 2;
    const position = (index) => index * step + offset;
    return { position, bandwidth, center: (index) => position(index) + bandwidth / 2 };
}
const E10 = Math.sqrt(50);
const E5 = Math.sqrt(10);
const E2 = Math.sqrt(2);
/**
 * The d3-array tick step: the "nicest" round increment of 1, 2 or 5 times a power of ten.
 *
 * `minStep` raises the result to the next multiple of itself. Counts of things are integers, and an
 * axis that labels them with an integer formatter would otherwise render "0, 1, 1, 2, 2, 3" once a
 * half step is collapsed by the formatter.
 */
function tickStep(start, stop, count, minStep = 0) {
    const rough = Math.abs(stop - start) / Math.max(count, 1);
    if (!Number.isFinite(rough) || rough === 0) {
        return Math.max(1, minStep);
    }
    const power = Math.pow(10, Math.floor(Math.log10(rough)));
    const error = rough / power;
    let step = power;
    if (error >= E10) {
        step = power * 10;
    }
    else if (error >= E5) {
        step = power * 5;
    }
    else if (error >= E2) {
        step = power * 2;
    }
    return minStep > 0 ? Math.max(minStep, Math.ceil(step / minStep) * minStep) : step;
}
/**
 * Rounds a value to the precision implied by `step`, so that accumulated floating point error does
 * not surface as tick labels like `0.30000000000000004`.
 */
function roundToStep(value, step) {
    const decimals = Math.max(0, -Math.floor(Math.log10(step)) + 1);
    return Number(value.toFixed(Math.min(decimals, 20)));
}
/** Extends a domain outwards to the next round tick, matching d3's `scale.nice()`. */
function niceDomain(min, max, count = 5, minStep = 0) {
    if (!Number.isFinite(min) || !Number.isFinite(max)) {
        return [0, 1];
    }
    if (min === max) {
        return min === 0 ? [0, 1] : [Math.min(0, min), Math.max(0, max)];
    }
    const step = tickStep(min, max, count, minStep);
    return [roundToStep(Math.floor(min / step) * step, step), roundToStep(Math.ceil(max / step) * step, step)];
}
function linearScale(domain, range) {
    const [d0, d1] = domain;
    const [r0, r1] = range;
    const span = d1 - d0;
    const scale = ((value) => (span === 0 ? r0 : r0 + ((value - d0) / span) * (r1 - r0)));
    scale.domain = domain;
    scale.ticks = (count = 5, minStep = 0) => {
        if (span === 0) {
            return Number.isFinite(d0) ? [d0] : [];
        }
        const step = tickStep(d0, d1, count, minStep);
        const first = Math.ceil(d0 / step);
        const last = Math.floor(d1 / step);
        // An infinite bound would make the loop below run forever and exhaust memory.
        if (!Number.isFinite(first) || !Number.isFinite(last)) {
            return [];
        }
        const result = [];
        for (let i = first; i <= last; i++) {
            result.push(roundToStep(i * step, step));
        }
        return result;
    };
    return scale;
}
/**
 * Approximates the rendered width of a label in px.
 *
 * Charts need tick label widths to reserve axis margins before the SVG is laid out, and measuring
 * every label in the DOM on each resize is disproportionate. The factor matches the average glyph
 * advance of the UI font at the chart's tick font size and errs on the generous side, since an
 * over-wide margin only costs plot area whereas an under-wide one clips the label.
 */
function approximateTextWidth(text, fontSize) {
    return text.length * fontSize * 0.58;
}
/** True when every value is a whole number, meaning the axis should not show fractional ticks. */
function allIntegers(values) {
    return values.every((value) => value === undefined || Number.isInteger(value));
}
/**
 * Keeps only the values a scale can actually place. A single NaN — one missing field in a server
 * response — would otherwise poison the domain and blank the whole chart rather than one bar.
 */
function finiteValues(values) {
    return values.filter((value) => typeof value === 'number' && Number.isFinite(value));
}

const TICK_FONT_SIZE = 11;
const AXIS_TITLE_FONT_SIZE = 12;
const TICK_GAP = 6;
const EDGE_PADDING = 8;
const CATEGORY_PADDING = 0.25;
/** Upper bound on the share of the chart a category axis may spend on its own labels. */
const MAX_CATEGORY_AXIS_SHARE = 0.33;
/** Vertical extent of a rotated label as a fraction of its own length, at the angle the axis draws them. */
const ROTATED_LABEL_PROJECTION = 0.72;
/** Room reserved beyond the end of a bar for its data label. */
const DATA_LABEL_GAP = 4;
function titleAllowance(title) {
    return title ? AXIS_TITLE_FONT_SIZE + 6 : 0;
}
function cartesianFrame(input) {
    const valueAxisVisible = input.valueAxis?.display ?? true;
    const categoryAxisVisible = input.categoryAxis?.display ?? true;
    const valueTickWidth = valueAxisVisible ? Math.max(...input.valueTicks.map((tick) => approximateTextWidth(tick.text, TICK_FONT_SIZE)), 0) : 0;
    // Measure the text that is actually drawn, so an axis formatter that truncates is accounted for.
    const format = input.categoryAxis?.tickFormatter;
    const categoryLabelWidth = categoryAxisVisible ? Math.max(...input.labels.map((label) => approximateTextWidth(format ? format(label) : label, TICK_FONT_SIZE)), 0) : 0;
    const endPadding = input.valueEndPadding ?? 0;
    let margin;
    let rotateCategoryLabels = false;
    // Reserved vertical band for rotated labels; also the budget a rotated label has to fit into.
    let rotatedHeight = 0;
    if (input.horizontal) {
        // A long category title must not eat the plot: past a third of the width the label is
        // truncated instead, which keeps the bars visible rather than collapsing them to nothing.
        const categoryAllowance = Math.min(categoryLabelWidth, input.size.width * MAX_CATEGORY_AXIS_SHARE);
        margin = {
            top: EDGE_PADDING,
            right: EDGE_PADDING + endPadding,
            bottom: (valueAxisVisible ? TICK_FONT_SIZE + TICK_GAP : 0) + titleAllowance(input.xAxisTitle),
            left: categoryAllowance + TICK_GAP + titleAllowance(input.yAxisTitle),
        };
    }
    else {
        const available = Math.max(input.size.width - valueTickWidth - TICK_GAP - EDGE_PADDING, 1);
        const required = input.labels.reduce((sum, label) => sum + approximateTextWidth(format ? format(label) : label, TICK_FONT_SIZE) + 8, 0);
        rotateCategoryLabels = categoryAxisVisible && required > available;
        rotatedHeight = Math.min(categoryLabelWidth * ROTATED_LABEL_PROJECTION, Math.max(input.size.height * MAX_CATEGORY_AXIS_SHARE, 0));
        const categoryBandHeight = categoryAxisVisible ? (rotateCategoryLabels ? rotatedHeight : TICK_FONT_SIZE) + TICK_GAP : 0;
        margin = {
            top: EDGE_PADDING + endPadding,
            right: EDGE_PADDING,
            bottom: categoryBandHeight + titleAllowance(input.xAxisTitle),
            // Rotated labels lean to the left of their tick, so the leftmost one needs room to sit in.
            left: Math.max(valueTickWidth + TICK_GAP, rotateCategoryLabels ? rotatedHeight * 0.7 : 0) + titleAllowance(input.yAxisTitle),
        };
    }
    const plotWidth = Math.max(input.size.width - margin.left - margin.right, 0);
    return {
        margin,
        plot: {
            width: Math.max(input.size.width - margin.left - margin.right, 0),
            height: Math.max(input.size.height - margin.top - margin.bottom, 0),
            left: margin.left,
            top: margin.top,
        },
        rotateCategoryLabels,
        // A vertical chart reserves only a slice of its height for category labels, so the label has to fit
        // that slice. Left unbounded, a long title is drawn in full and runs off the chart over whatever
        // follows it. Rotated labels are measured along their own direction, hence dividing by the projection.
        categoryLabelBudget: input.horizontal
            ? Math.max(margin.left - TICK_GAP - titleAllowance(input.yAxisTitle), 0)
            : rotateCategoryLabels
                ? Math.max(rotatedHeight / ROTATED_LABEL_PROJECTION, 0)
                : Math.max(plotWidth / Math.max(input.labels.length, 1) - TICK_GAP, 0),
    };
}
/** Shortens a label to the pixels available for it, so it cannot spill over the rest of the page. */
function truncateToWidth(text, budget) {
    if (!Number.isFinite(budget) || approximateTextWidth(text, TICK_FONT_SIZE) <= budget) {
        return text;
    }
    const perCharacter = approximateTextWidth('n', TICK_FONT_SIZE);
    const fits = Math.max(Math.floor(budget / perCharacter) - 1, 1);
    return `${text.slice(0, fits)}…`;
}
function valueTickViews(plot, scale, ticks, horizontal) {
    return ticks.map((tick) => horizontal
        ? { key: `v${tick.value}`, text: tick.text, x: scale(tick.value), y: plot.height + TICK_GAP + TICK_FONT_SIZE * 0.8, anchor: 'middle', rotate: 0 }
        : { key: `v${tick.value}`, text: tick.text, x: -TICK_GAP, y: scale(tick.value) + TICK_FONT_SIZE * 0.35, anchor: 'end', rotate: 0 });
}
function categoryTickViews(plot, categories, labels, horizontal, rotate, formatter, labelBudget = Number.POSITIVE_INFINITY) {
    const skip = categoryTickSkip(plot, labels, horizontal, rotate, formatter);
    return labels.flatMap((label, index) => {
        if (index % skip !== 0) {
            return [];
        }
        const center = categories.center(index);
        const text = truncateToWidth(formatter ? formatter(label) : label, labelBudget);
        if (horizontal) {
            return [{ key: `c${index}`, text, x: -TICK_GAP, y: center + TICK_FONT_SIZE * 0.35, anchor: 'end', rotate: 0 }];
        }
        return [
            {
                key: `c${index}`,
                text,
                x: center,
                y: plot.height + TICK_GAP + (rotate ? TICK_FONT_SIZE * 0.4 : TICK_FONT_SIZE * 0.8),
                anchor: rotate ? 'end' : 'middle',
                rotate: rotate ? -45 : 0,
            },
        ];
    });
}
/**
 * How many categories to advance between rendered labels. Rotating buys roughly three times the room
 * of upright text; beyond that, drawing every label would only overprint them into a smear.
 */
function categoryTickSkip(plot, labels, horizontal, rotate, formatter) {
    const available = horizontal ? plot.height : plot.width;
    if (!labels.length || available <= 0) {
        return 1;
    }
    const perLabel = horizontal ? TICK_FONT_SIZE + 4 : Math.max(...labels.map((label) => approximateTextWidth(formatter ? formatter(label) : label, TICK_FONT_SIZE)), 1) + 8;
    const required = labels.length * (rotate ? perLabel / 3 : perLabel);
    return Math.max(Math.ceil(required / available), 1);
}
function gridLineViews(plot, scale, ticks, horizontal) {
    return ticks.map((tick) => {
        const at = scale(tick.value);
        return horizontal ? { key: `g${tick.value}`, x1: at, y1: 0, x2: at, y2: plot.height } : { key: `g${tick.value}`, x1: 0, y1: at, x2: plot.width, y2: at };
    });
}
function axisTitleViews(plot, margin, xTitle, yTitle) {
    return {
        x: xTitle ? { text: xTitle, x: plot.width / 2, y: plot.height + margin.bottom - 2, rotate: 0 } : undefined,
        y: yTitle ? { text: yTitle, x: -(margin.left - AXIS_TITLE_FONT_SIZE), y: plot.height / 2, rotate: -90 } : undefined,
    };
}
function legendPositionOf(legend) {
    if (!legend) {
        return undefined;
    }
    return typeof legend === 'object' ? (legend.position ?? 'right') : 'right';
}
/**
 * Half the width a tooltip is assumed to occupy. The real width depends on its text, so this is an
 * approximation: it only has to stop a tooltip near an edge from escaping a container that clips it.
 */
const ASSUMED_TOOLTIP_HALF_WIDTH = 110;
/** Room a tooltip needs above the pointer before it has to flip below instead. */
const TOOLTIP_CLEARANCE = 90;
function placeTooltip(hovered) {
    const min = Math.min(ASSUMED_TOOLTIP_HALF_WIDTH + EDGE_PADDING, hovered.hostWidth / 2);
    const max = Math.max(hovered.hostWidth - ASSUMED_TOOLTIP_HALF_WIDTH - EDGE_PADDING, min);
    return { x: Math.min(Math.max(hovered.x, min), max), y: hovered.y, below: hovered.y < TOOLTIP_CLEARANCE };
}
/**
 * The accessible name of a single interactive datum. Where a chart draws more than one series, two
 * data points can share a category and a value, which would leave a keyboard or screen reader user
 * unable to tell which one they are about to select, so the series label leads in that case.
 */
function datumAccessibleName(context, multiSeries) {
    const series = multiSeries && context.seriesLabel ? `${context.seriesLabel}, ` : '';
    return `${series}${context.label}: ${context.value}`;
}

/**
 * Renders the grid, tick labels and axis titles of a cartesian chart.
 *
 * Applied to an `<svg:g>` that is already translated into the plot's coordinate system, so every
 * coordinate it receives is relative to the plot origin.
 */
class TumUiChartAxesComponent {
    gridLines = input([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "gridLines" }] : /* istanbul ignore next */ []));
    ticks = input([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ticks" }] : /* istanbul ignore next */ []));
    titles = input([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "titles" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiChartAxesComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiChartAxesComponent, isStandalone: true, selector: "g[tumUiChartAxes]", inputs: { gridLines: { classPropertyName: "gridLines", publicName: "gridLines", isSignal: true, isRequired: false, transformFunction: null }, ticks: { classPropertyName: "ticks", publicName: "ticks", isSignal: true, isRequired: false, transformFunction: null }, titles: { classPropertyName: "titles", publicName: "titles", isSignal: true, isRequired: false, transformFunction: null } }, ngImport: i0, template: `
        @for (gridLine of gridLines(); track gridLine.key) {
            <svg:line class="tum-ui-chart-grid" [attr.x1]="gridLine.x1" [attr.y1]="gridLine.y1" [attr.x2]="gridLine.x2" [attr.y2]="gridLine.y2" />
        }
        @for (tick of ticks(); track tick.key) {
            <svg:text
                class="tum-ui-chart-tick"
                [attr.x]="tick.x"
                [attr.y]="tick.y"
                [attr.text-anchor]="tick.anchor"
                [attr.transform]="tick.rotate ? 'rotate(' + tick.rotate + ' ' + tick.x + ' ' + tick.y + ')' : undefined"
            >
                {{ tick.text }}
            </svg:text>
        }
        @for (title of titles(); track $index) {
            <svg:text
                class="tum-ui-chart-axis-title"
                [attr.x]="title.x"
                [attr.y]="title.y"
                text-anchor="middle"
                [attr.transform]="title.rotate ? 'rotate(' + title.rotate + ' ' + title.x + ' ' + title.y + ')' : undefined"
            >
                {{ title.text }}
            </svg:text>
        }
    `, isInline: true, styles: [".tum-ui-chart-grid{stroke:var(--tumaet-ui-border-color);stroke-width:1;shape-rendering:crispedges}.tum-ui-chart-tick{fill:var(--tumaet-ui-muted-color);font-size:var(--tumaet-ui-font-size-xs);font-family:inherit}.tum-ui-chart-axis-title{fill:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-sm);font-family:inherit}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiChartAxesComponent, decorators: [{
            type: Component,
            args: [{ selector: 'g[tumUiChartAxes]', changeDetection: ChangeDetectionStrategy.OnPush, template: `
        @for (gridLine of gridLines(); track gridLine.key) {
            <svg:line class="tum-ui-chart-grid" [attr.x1]="gridLine.x1" [attr.y1]="gridLine.y1" [attr.x2]="gridLine.x2" [attr.y2]="gridLine.y2" />
        }
        @for (tick of ticks(); track tick.key) {
            <svg:text
                class="tum-ui-chart-tick"
                [attr.x]="tick.x"
                [attr.y]="tick.y"
                [attr.text-anchor]="tick.anchor"
                [attr.transform]="tick.rotate ? 'rotate(' + tick.rotate + ' ' + tick.x + ' ' + tick.y + ')' : undefined"
            >
                {{ tick.text }}
            </svg:text>
        }
        @for (title of titles(); track $index) {
            <svg:text
                class="tum-ui-chart-axis-title"
                [attr.x]="title.x"
                [attr.y]="title.y"
                text-anchor="middle"
                [attr.transform]="title.rotate ? 'rotate(' + title.rotate + ' ' + title.x + ' ' + title.y + ')' : undefined"
            >
                {{ title.text }}
            </svg:text>
        }
    `, styles: [".tum-ui-chart-grid{stroke:var(--tumaet-ui-border-color);stroke-width:1;shape-rendering:crispedges}.tum-ui-chart-tick{fill:var(--tumaet-ui-muted-color);font-size:var(--tumaet-ui-font-size-xs);font-family:inherit}.tum-ui-chart-axis-title{fill:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-sm);font-family:inherit}\n"] }]
        }], propDecorators: { gridLines: [{ type: i0.Input, args: [{ isSignal: true, alias: "gridLines", required: false }] }], ticks: [{ type: i0.Input, args: [{ isSignal: true, alias: "ticks", required: false }] }], titles: [{ type: i0.Input, args: [{ isSignal: true, alias: "titles", required: false }] }] } });

/**
 * Legend for a chart's series or slices.
 *
 * Entries are buttons: clicking one hides or shows what it names, which is how a reader compares two
 * lines out of five. Rendering them as real buttons rather than painted swatches also makes the
 * legend keyboard operable, which the canvas charts never were.
 */
class TumUiChartLegendComponent {
    items = input([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "items" }] : /* istanbul ignore next */ []));
    /** Drives the layout: a legend above or below the plot lays its entries out in a row. */
    position = input('right', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "position" }] : /* istanbul ignore next */ []));
    /** Emits the key of the entry the reader clicked, so the chart can hide or show it. */
    toggleEntry = output();
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiChartLegendComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiChartLegendComponent, isStandalone: true, selector: "tum-ui-chart-legend", inputs: { items: { classPropertyName: "items", publicName: "items", isSignal: true, isRequired: false, transformFunction: null }, position: { classPropertyName: "position", publicName: "position", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { toggleEntry: "toggleEntry" }, host: { properties: { "attr.data-position": "position()" }, classAttribute: "tum-ui-chart-legend" }, ngImport: i0, template: `
        <ul class="tum-ui-chart-legend-list">
            @for (item of items(); track item.key) {
                <li>
                    <button type="button" class="tum-ui-chart-legend-item" [attr.aria-pressed]="!item.hidden" (click)="toggleEntry.emit(item.key)">
                        <span class="tum-ui-chart-legend-swatch" [style.background]="item.color"></span>
                        <span>{{ item.label }}</span>
                    </button>
                </li>
            }
        </ul>
    `, isInline: true, styles: [":host{display:block;align-self:center;font-size:var(--tumaet-ui-font-size-xs);color:var(--tumaet-ui-text-color)}.tum-ui-chart-legend-list{display:flex;flex-direction:column;gap:calc(var(--tumaet-ui-spacing) * 1);margin:0;padding:0;list-style:none}:host([data-position=\"top\"]) .tum-ui-chart-legend-list,:host([data-position=\"bottom\"]) .tum-ui-chart-legend-list{flex-direction:row;flex-wrap:wrap;justify-content:center}.tum-ui-chart-legend-item{display:flex;align-items:center;gap:calc(var(--tumaet-ui-spacing) * 1);white-space:nowrap;min-height:24px;padding:0 calc(var(--tumaet-ui-spacing) * 1);border:0;background:none;color:inherit;font:inherit;cursor:pointer}.tum-ui-chart-legend-item[aria-pressed=false]{opacity:.45;text-decoration:line-through}.tum-ui-chart-legend-swatch{width:10px;height:10px;border-radius:var(--tumaet-ui-radius-sm);flex:none}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiChartLegendComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-chart-legend', changeDetection: ChangeDetectionStrategy.OnPush, host: { class: 'tum-ui-chart-legend', '[attr.data-position]': 'position()' }, template: `
        <ul class="tum-ui-chart-legend-list">
            @for (item of items(); track item.key) {
                <li>
                    <button type="button" class="tum-ui-chart-legend-item" [attr.aria-pressed]="!item.hidden" (click)="toggleEntry.emit(item.key)">
                        <span class="tum-ui-chart-legend-swatch" [style.background]="item.color"></span>
                        <span>{{ item.label }}</span>
                    </button>
                </li>
            }
        </ul>
    `, styles: [":host{display:block;align-self:center;font-size:var(--tumaet-ui-font-size-xs);color:var(--tumaet-ui-text-color)}.tum-ui-chart-legend-list{display:flex;flex-direction:column;gap:calc(var(--tumaet-ui-spacing) * 1);margin:0;padding:0;list-style:none}:host([data-position=\"top\"]) .tum-ui-chart-legend-list,:host([data-position=\"bottom\"]) .tum-ui-chart-legend-list{flex-direction:row;flex-wrap:wrap;justify-content:center}.tum-ui-chart-legend-item{display:flex;align-items:center;gap:calc(var(--tumaet-ui-spacing) * 1);white-space:nowrap;min-height:24px;padding:0 calc(var(--tumaet-ui-spacing) * 1);border:0;background:none;color:inherit;font:inherit;cursor:pointer}.tum-ui-chart-legend-item[aria-pressed=false]{opacity:.45;text-decoration:line-through}.tum-ui-chart-legend-swatch{width:10px;height:10px;border-radius:var(--tumaet-ui-radius-sm);flex:none}\n"] }]
        }], propDecorators: { items: [{ type: i0.Input, args: [{ isSignal: true, alias: "items", required: false }] }], position: [{ type: i0.Input, args: [{ isSignal: true, alias: "position", required: false }] }], toggleEntry: [{ type: i0.Output, args: ["toggleEntry"] }] } });

/** Hover tooltip, positioned by the chart in its own coordinate space. */
class TumUiChartTooltipComponent {
    title = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "title" }] : /* istanbul ignore next */ []));
    lines = input([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "lines" }] : /* istanbul ignore next */ []));
    x = input(0, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "x" }] : /* istanbul ignore next */ []));
    y = input(0, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "y" }] : /* istanbul ignore next */ []));
    /** Renders below the pointer, for a datum too close to the top edge to leave room above. */
    below = input(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "below" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiChartTooltipComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiChartTooltipComponent, isStandalone: true, selector: "tum-ui-chart-tooltip", inputs: { title: { classPropertyName: "title", publicName: "title", isSignal: true, isRequired: false, transformFunction: null }, lines: { classPropertyName: "lines", publicName: "lines", isSignal: true, isRequired: false, transformFunction: null }, x: { classPropertyName: "x", publicName: "x", isSignal: true, isRequired: false, transformFunction: null }, y: { classPropertyName: "y", publicName: "y", isSignal: true, isRequired: false, transformFunction: null }, below: { classPropertyName: "below", publicName: "below", isSignal: true, isRequired: false, transformFunction: null } }, host: { attributes: { "role": "tooltip" }, properties: { "style.left.px": "x()", "style.top.px": "y()", "attr.data-below": "below()" }, classAttribute: "tum-ui-chart-tooltip tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:px-2 tum:py-1.5 tum:text-text tum:shadow-lg" }, ngImport: i0, template: `
        @if (title()) {
            <div class="tum-ui-chart-tooltip-title">{{ title() }}</div>
        }
        @for (line of lines(); track $index) {
            <div>{{ line }}</div>
        }
    `, isInline: true, styles: [":host{display:block;position:absolute;z-index:1;transform:translate(-50%,calc(-100% - 12px));max-width:22rem;font-size:var(--tumaet-ui-font-size-xs);pointer-events:none}:host([data-below=\"true\"]){transform:translate(-50%,12px)}.tum-ui-chart-tooltip-title{font-weight:600}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiChartTooltipComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-chart-tooltip', changeDetection: ChangeDetectionStrategy.OnPush, host: {
                        class: 'tum-ui-chart-tooltip tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:px-2 tum:py-1.5 tum:text-text tum:shadow-lg',
                        role: 'tooltip',
                        '[style.left.px]': 'x()',
                        '[style.top.px]': 'y()',
                        '[attr.data-below]': 'below()',
                    }, template: `
        @if (title()) {
            <div class="tum-ui-chart-tooltip-title">{{ title() }}</div>
        }
        @for (line of lines(); track $index) {
            <div>{{ line }}</div>
        }
    `, styles: [":host{display:block;position:absolute;z-index:1;transform:translate(-50%,calc(-100% - 12px));max-width:22rem;font-size:var(--tumaet-ui-font-size-xs);pointer-events:none}:host([data-below=\"true\"]){transform:translate(-50%,12px)}.tum-ui-chart-tooltip-title{font-weight:600}\n"] }]
        }], propDecorators: { title: [{ type: i0.Input, args: [{ isSignal: true, alias: "title", required: false }] }], lines: [{ type: i0.Input, args: [{ isSignal: true, alias: "lines", required: false }] }], x: [{ type: i0.Input, args: [{ isSignal: true, alias: "x", required: false }] }], y: [{ type: i0.Input, args: [{ isSignal: true, alias: "y", required: false }] }], below: [{ type: i0.Input, args: [{ isSignal: true, alias: "below", required: false }] }] } });

/**
 * Mirrors a chart's values as a table for assistive technology.
 *
 * It is excluded from text selection so that copying the chart's own labels does not also pick up a
 * duplicate of every value.
 */
class TumUiChartDataTableComponent {
    caption = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "caption" }] : /* istanbul ignore next */ []));
    rows = input([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "rows" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiChartDataTableComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiChartDataTableComponent, isStandalone: true, selector: "tum-ui-chart-data-table", inputs: { caption: { classPropertyName: "caption", publicName: "caption", isSignal: true, isRequired: false, transformFunction: null }, rows: { classPropertyName: "rows", publicName: "rows", isSignal: true, isRequired: false, transformFunction: null } }, host: { classAttribute: "tum-ui-chart-data-table" }, ngImport: i0, template: `
        <table>
            <caption>
                {{
                    caption()
                }}
            </caption>
            <tbody>
                @for (row of rows(); track $index) {
                    <tr>
                        <th scope="row">{{ row.label }}</th>
                        @for (cell of row.values; track $index) {
                            <td>{{ cell.seriesLabel ? cell.seriesLabel + ': ' : '' }}{{ cell.value }}</td>
                        }
                    </tr>
                }
            </tbody>
        </table>
    `, isInline: true, styles: [":host{position:absolute;width:1px;height:1px;margin:-1px;padding:0;overflow:hidden;clip-path:inset(50%);white-space:nowrap;border:0;-webkit-user-select:none;user-select:none}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiChartDataTableComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-chart-data-table', changeDetection: ChangeDetectionStrategy.OnPush, host: { class: 'tum-ui-chart-data-table' }, template: `
        <table>
            <caption>
                {{
                    caption()
                }}
            </caption>
            <tbody>
                @for (row of rows(); track $index) {
                    <tr>
                        <th scope="row">{{ row.label }}</th>
                        @for (cell of row.values; track $index) {
                            <td>{{ cell.seriesLabel ? cell.seriesLabel + ': ' : '' }}{{ cell.value }}</td>
                        }
                    </tr>
                }
            </tbody>
        </table>
    `, styles: [":host{position:absolute;width:1px;height:1px;margin:-1px;padding:0;overflow:hidden;clip-path:inset(50%);white-space:nowrap;border:0;-webkit-user-select:none;user-select:none}\n"] }]
        }], propDecorators: { caption: [{ type: i0.Input, args: [{ isSignal: true, alias: "caption", required: false }] }], rows: [{ type: i0.Input, args: [{ isSignal: true, alias: "rows", required: false }] }] } });

const SERIES_GROUP_PADDING = 0.08;
/** A stacked total must ignore values the scale cannot place rather than turning the whole stack into NaN. */
function finiteOr0(value) {
    return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}
/**
 * A bar chart rendered as inline SVG.
 *
 * Because every label is a real `<text>` node, chart text is selectable and copyable, is exposed to
 * assistive technology, and takes its colors from CSS custom properties — so theme switches need no
 * re-render and no color resolution step.
 */
let nextBarChartId = 0;
class TumUiBarChartComponent {
    hostElement = inject(ElementRef);
    /**
     * The plot is measured on the SVG itself rather than on the host, because a legend is a sibling
     * flex item: measuring the host would size the plot as if the legend's band were still free and
     * paint the axis labels underneath it.
     */
    canvas = viewChild.required('canvas', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "canvas" }] : /* istanbul ignore next */ []));
    labels = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "labels" }] : /* istanbul ignore next */ []));
    series = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "series" }] : /* istanbul ignore next */ []));
    config = input({}, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "config" }] : /* istanbul ignore next */ []));
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    /** Names the chart from a visible heading instead of a literal label. */
    ariaLabelledBy = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []));
    /** Marks bars as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
    interactive = input(false, { ...(ngDevMode ? { debugName: "interactive" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    dataSelect = output();
    /** Names an interactive datum for assistive technology; see {@link datumAccessibleName}. */
    accessibleName(context) {
        return datumAccessibleName(context, this.series().length > 1);
    }
    size = signal({ width: 0, height: 0 }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    hovered = signal(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hovered" }] : /* istanbul ignore next */ []));
    resizeObserver;
    constructor() {
        afterNextRender(() => {
            const element = this.canvas().nativeElement;
            const rect = element.getBoundingClientRect();
            this.size.set({ width: rect.width, height: rect.height });
            if (typeof ResizeObserver === 'undefined') {
                return;
            }
            this.resizeObserver = new ResizeObserver((entries) => {
                const box = entries[0]?.contentRect;
                if (box) {
                    this.size.set({ width: box.width, height: box.height });
                }
            });
            this.resizeObserver.observe(element);
        });
    }
    ngOnDestroy() {
        this.resizeObserver?.disconnect();
    }
    /** Series the reader switched off in the legend, by index. */
    hiddenSeries = signal(new Set(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hiddenSeries" }] : /* istanbul ignore next */ []));
    onLegendToggle(key) {
        this.hiddenSeries.update((hidden) => {
            const next = new Set(hidden);
            if (!next.delete(key)) {
                next.add(key);
            }
            return next;
        });
    }
    /** The series actually drawn, paired with their original index so meta and colors stay aligned. */
    visibleSeries = computed(() => this.series()
        .map((entry, index) => ({ entry, index }))
        .filter(({ index }) => !this.hiddenSeries().has(`${index}`)), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "visibleSeries" }] : /* istanbul ignore next */ []));
    horizontal = computed(() => this.config().horizontal ?? false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "horizontal" }] : /* istanbul ignore next */ []));
    stacked = computed(() => this.config().stacked ?? false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "stacked" }] : /* istanbul ignore next */ []));
    /** The axis carrying the numeric values: x for horizontal bars, y otherwise. */
    valueAxis = computed(() => (this.horizontal() ? this.config().xAxis : this.config().yAxis), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "valueAxis" }] : /* istanbul ignore next */ []));
    categoryAxis = computed(() => (this.horizontal() ? this.config().yAxis : this.config().xAxis), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "categoryAxis" }] : /* istanbul ignore next */ []));
    /**
     * Whole numbers on the value axis mean the axis must not step in fractions: a "number of
     * submissions" axis running 0–3 would otherwise be labelled 0, 1, 1, 2, 2, 3 once the caller's
     * integer formatter collapsed the half steps.
     */
    minTickStep = computed(() => (allIntegers(this.visibleSeries().flatMap(({ entry }) => [...entry.data])) ? 1 : 0), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "minTickStep" }] : /* istanbul ignore next */ []));
    valueDomain = computed(() => {
        const axis = this.valueAxis();
        const percent = this.config().percentScale ?? false;
        const visible = this.visibleSeries().map(({ entry }) => entry);
        // A stack grows in both directions independently, so the extent is the tallest positive stack
        // and the deepest negative one, not their net total.
        const totals = this.stacked()
            ? this.labels().flatMap((_, index) => [
                visible.reduce((sum, entry) => sum + Math.max(finiteOr0(entry.data[index]), 0), 0),
                visible.reduce((sum, entry) => sum + Math.min(finiteOr0(entry.data[index]), 0), 0),
            ])
            : finiteValues(visible.flatMap((entry) => [...entry.data]));
        const dataMax = totals.length ? Math.max(...totals) : 0;
        const dataMin = totals.length ? Math.min(...totals) : 0;
        const [niceMin, niceMax] = niceDomain(Math.min(0, dataMin), dataMax, 5, this.minTickStep());
        return [axis?.min ?? niceMin, axis?.max ?? (percent ? 100 : niceMax)];
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "valueDomain" }] : /* istanbul ignore next */ []));
    valueTickLabels = computed(() => {
        if ((this.valueAxis()?.display ?? true) === false) {
            return [];
        }
        const [min, max] = this.valueDomain();
        const percent = this.config().percentScale ?? false;
        const format = this.valueAxis()?.tickFormatter ?? (percent ? (value) => `${value}%` : undefined);
        return linearScale([min, max], [0, 1])
            .ticks(5, this.minTickStep())
            .map((value) => ({ value, text: format ? format(value) : `${value}` }));
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "valueTickLabels" }] : /* istanbul ignore next */ []));
    frame = computed(() => cartesianFrame({
        size: this.size(),
        labels: (this.categoryAxis()?.display ?? true) ? this.labels() : [],
        valueTicks: this.valueTickLabels(),
        horizontal: this.horizontal(),
        valueAxis: this.valueAxis(),
        categoryAxis: this.categoryAxis(),
        xAxisTitle: this.config().xAxis?.label,
        yAxisTitle: this.config().yAxis?.label,
        valueEndPadding: this.config().dataLabels ? TICK_FONT_SIZE + DATA_LABEL_GAP : 0,
    }), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "frame" }] : /* istanbul ignore next */ []));
    plot = computed(() => this.frame().plot, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "plot" }] : /* istanbul ignore next */ []));
    /** Unique per instance, so charts sharing a page do not share a clip path. */
    clipId = `tum-ui-bar-chart-clip-${nextBarChartId++}`;
    valueScale = computed(() => {
        const plot = this.plot();
        const [min, max] = this.valueDomain();
        return this.horizontal() ? linearScale([min, max], [0, plot.width]) : linearScale([min, max], [plot.height, 0]);
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "valueScale" }] : /* istanbul ignore next */ []));
    categoryScale = computed(() => {
        const plot = this.plot();
        return bandScale(this.labels().length, this.horizontal() ? plot.height : plot.width, CATEGORY_PADDING);
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "categoryScale" }] : /* istanbul ignore next */ []));
    bars = computed(() => {
        const plot = this.plot();
        if (plot.width <= 0 || plot.height <= 0) {
            return [];
        }
        const horizontal = this.horizontal();
        const stacked = this.stacked();
        const labels = this.labels();
        const series = this.visibleSeries();
        const [min, max] = this.valueDomain();
        const categories = this.categoryScale();
        const valueScale = this.valueScale();
        const grouped = !stacked && series.length > 1;
        const groupScale = grouped ? bandScale(series.length, categories.bandwidth, SERIES_GROUP_PADDING) : undefined;
        const maxThickness = this.config().maxBarThickness ?? Number.POSITIVE_INFINITY;
        const thickness = Math.min(groupScale ? groupScale.bandwidth : categories.bandwidth, maxThickness);
        const dataLabels = this.config().dataLabels;
        // Positive and negative segments stack away from the baseline independently.
        const positiveOffsets = labels.map(() => 0);
        const negativeOffsets = labels.map(() => 0);
        const bars = [];
        series.forEach(({ entry, index: seriesIndex }, drawIndex) => {
            labels.forEach((label, index) => {
                const raw = entry.data[index];
                // A value the scale cannot place would be drawn at NaN, which blanks the whole chart.
                if (raw === undefined || raw === null || !Number.isFinite(raw)) {
                    return;
                }
                const bandStart = categories.position(index);
                const groupOffset = groupScale ? groupScale.position(drawIndex) : 0;
                const centering = (groupScale ? groupScale.bandwidth : categories.bandwidth) - thickness;
                const crossStart = bandStart + groupOffset + centering / 2;
                const offsets = raw < 0 ? negativeOffsets : positiveOffsets;
                const start = stacked ? offsets[index] : Math.min(Math.max(0, min), max);
                const end = stacked ? offsets[index] + raw : raw;
                if (stacked) {
                    offsets[index] = end;
                }
                const from = valueScale(start);
                const to = valueScale(end);
                const context = {
                    seriesIndex,
                    index,
                    label,
                    seriesLabel: entry.label,
                    value: raw,
                    meta: entry.meta?.[index],
                };
                const color = entry.colors?.[index % entry.colors.length] ?? entry.color ?? 'var(--tumaet-ui-primary-color)';
                const bar = horizontal
                    ? { key: `${seriesIndex}-${index}`, x: Math.min(from, to), y: crossStart, width: Math.abs(to - from), height: thickness, color, context }
                    : { key: `${seriesIndex}-${index}`, x: crossStart, y: Math.min(from, to), width: thickness, height: Math.abs(to - from), color, context };
                if (dataLabels) {
                    const text = dataLabels.formatter(raw, context);
                    bar.dataLabel = horizontal
                        ? { x: bar.x + bar.width + DATA_LABEL_GAP, y: bar.y + bar.height / 2, anchor: 'start', text }
                        : { x: bar.x + bar.width / 2, y: bar.y - DATA_LABEL_GAP, anchor: 'middle', text };
                }
                bars.push(bar);
            });
        });
        return bars;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "bars" }] : /* istanbul ignore next */ []));
    gridLines = computed(() => (this.valueAxis()?.display ?? true) ? gridLineViews(this.plot(), this.valueScale(), this.valueTickLabels(), this.horizontal()) : [], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "gridLines" }] : /* istanbul ignore next */ []));
    ticks = computed(() => {
        const plot = this.plot();
        const horizontal = this.horizontal();
        const value = (this.valueAxis()?.display ?? true) ? valueTickViews(plot, this.valueScale(), this.valueTickLabels(), horizontal) : [];
        const category = (this.categoryAxis()?.display ?? true)
            ? categoryTickViews(plot, this.categoryScale(), this.labels(), horizontal, this.frame().rotateCategoryLabels, this.categoryAxis()?.tickFormatter, this.frame().categoryLabelBudget)
            : [];
        return [...value, ...category];
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ticks" }] : /* istanbul ignore next */ []));
    axisTitles = computed(() => {
        const titles = axisTitleViews(this.plot(), this.frame().margin, this.config().xAxis?.label, this.config().yAxis?.label);
        return [titles.x, titles.y].filter((title) => title !== undefined);
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "axisTitles" }] : /* istanbul ignore next */ []));
    legendPosition = computed(() => legendPositionOf(this.config().legend), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "legendPosition" }] : /* istanbul ignore next */ []));
    legendItems = computed(() => this.series()
        .map((entry, index) => ({ entry, index }))
        .filter(({ entry }) => entry.label)
        .map(({ entry, index }) => ({
        key: `${index}`,
        label: entry.label,
        color: entry.color ?? 'var(--tumaet-ui-primary-color)',
        hidden: this.hiddenSeries().has(`${index}`),
    })), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "legendItems" }] : /* istanbul ignore next */ []));
    tooltip = computed(() => {
        const hovered = this.hovered();
        const config = this.config().tooltip;
        if (!hovered || config === false) {
            return undefined;
        }
        const bar = this.bars().find((candidate) => candidate.context.index === hovered.index && candidate.context.seriesIndex === hovered.seriesIndex);
        if (!bar) {
            return undefined;
        }
        const context = bar.context;
        const title = config?.title ? config.title([context]) : context.label;
        const raw = config?.label ? config.label(context) : `${context.seriesLabel ? `${context.seriesLabel}: ` : ''}${context.value}`;
        const after = config?.afterBody?.([context]);
        const lines = [...(Array.isArray(raw) ? raw : [raw]), ...(after ? (Array.isArray(after) ? after : [after]) : [])].filter((line) => line !== '');
        return { title, lines, ...placeTooltip(hovered) };
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "tooltip" }] : /* istanbul ignore next */ []));
    accessibleRows = computed(() => this.labels().map((label, index) => ({
        label,
        values: this.series().map((entry) => ({ seriesLabel: entry.label, value: entry.data[index] })),
    })), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "accessibleRows" }] : /* istanbul ignore next */ []));
    onBarEnter(bar, event) {
        const host = this.hostElement.nativeElement.getBoundingClientRect();
        this.hovered.set({
            index: bar.context.index,
            seriesIndex: bar.context.seriesIndex,
            x: event.clientX - host.left,
            y: event.clientY - host.top,
            hostWidth: host.width,
            hostHeight: host.height,
        });
    }
    onBarLeave() {
        this.hovered.set(undefined);
    }
    onBarSelect(bar) {
        const { seriesIndex, index, label, seriesLabel, value, meta } = bar.context;
        this.dataSelect.emit({ seriesIndex, index, label, seriesLabel, value, meta });
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiBarChartComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiBarChartComponent, isStandalone: true, selector: "tum-ui-bar-chart", inputs: { labels: { classPropertyName: "labels", publicName: "labels", isSignal: true, isRequired: true, transformFunction: null }, series: { classPropertyName: "series", publicName: "series", isSignal: true, isRequired: true, transformFunction: null }, config: { classPropertyName: "config", publicName: "config", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, ariaLabelledBy: { classPropertyName: "ariaLabelledBy", publicName: "ariaLabelledBy", isSignal: true, isRequired: false, transformFunction: null }, interactive: { classPropertyName: "interactive", publicName: "interactive", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { dataSelect: "dataSelect" }, host: { classAttribute: "tum-ui-bar-chart" }, viewQueries: [{ propertyName: "canvas", first: true, predicate: ["canvas"], descendants: true, isSignal: true }], ngImport: i0, template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        <defs>\n            <clipPath [attr.id]=\"clipId\">\n                <rect [attr.width]=\"plot().width\" [attr.height]=\"plot().height\" />\n            </clipPath>\n        </defs>\n        <g [attr.transform]=\"'translate(' + plot().left + ' ' + plot().top + ')'\">\n            <svg:g tumUiChartAxes [gridLines]=\"gridLines()\" [ticks]=\"ticks()\" [titles]=\"axisTitles()\" />\n\n            <!-- A value beyond the configured axis limits is clipped to the plot rather than drawn over the\n                 heading above it. The data labels stay outside this group so they remain readable. -->\n            <g [attr.clip-path]=\"'url(#' + clipId + ')'\">\n                @for (bar of bars(); track bar.key) {\n                    <rect\n                        class=\"tum-ui-bar-chart-bar\"\n                        [class.tum-ui-bar-chart-bar-interactive]=\"interactive()\"\n                        [attr.x]=\"bar.x\"\n                        [attr.y]=\"bar.y\"\n                        [attr.width]=\"bar.width\"\n                        [attr.height]=\"bar.height\"\n                        [attr.fill]=\"bar.color\"\n                        [attr.role]=\"interactive() ? 'button' : undefined\"\n                        [attr.tabindex]=\"interactive() ? 0 : undefined\"\n                        [attr.aria-label]=\"interactive() ? accessibleName(bar.context) : undefined\"\n                        (mouseenter)=\"onBarEnter(bar, $event)\"\n                        (mousemove)=\"onBarEnter(bar, $event)\"\n                        (mouseleave)=\"onBarLeave()\"\n                        (click)=\"onBarSelect(bar)\"\n                        (keydown.enter)=\"onBarSelect(bar)\"\n                        (keydown.space)=\"onBarSelect(bar); $event.preventDefault()\"\n                    />\n                }\n            </g>\n\n            @for (bar of bars(); track bar.key) {\n                @if (bar.dataLabel; as dataLabel) {\n                    <text class=\"tum-ui-bar-chart-data-label\" [attr.x]=\"dataLabel.x\" [attr.y]=\"dataLabel.y\" [attr.text-anchor]=\"dataLabel.anchor\">\n                        {{ dataLabel.text }}\n                    </text>\n                }\n            }\n        </g>\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n", styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-bar-chart-bar-interactive{cursor:pointer}.tum-ui-bar-chart-bar:hover{filter:brightness(1.08)}.tum-ui-bar-chart-data-label{fill:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-xs);font-family:inherit}\n"], dependencies: [{ kind: "component", type: TumUiChartAxesComponent, selector: "g[tumUiChartAxes]", inputs: ["gridLines", "ticks", "titles"] }, { kind: "component", type: TumUiChartLegendComponent, selector: "tum-ui-chart-legend", inputs: ["items", "position"], outputs: ["toggleEntry"] }, { kind: "component", type: TumUiChartTooltipComponent, selector: "tum-ui-chart-tooltip", inputs: ["title", "lines", "x", "y", "below"] }, { kind: "component", type: TumUiChartDataTableComponent, selector: "tum-ui-chart-data-table", inputs: ["caption", "rows"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiBarChartComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-bar-chart', imports: [TumUiChartAxesComponent, TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent], changeDetection: ChangeDetectionStrategy.OnPush, host: { class: 'tum-ui-bar-chart' }, template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        <defs>\n            <clipPath [attr.id]=\"clipId\">\n                <rect [attr.width]=\"plot().width\" [attr.height]=\"plot().height\" />\n            </clipPath>\n        </defs>\n        <g [attr.transform]=\"'translate(' + plot().left + ' ' + plot().top + ')'\">\n            <svg:g tumUiChartAxes [gridLines]=\"gridLines()\" [ticks]=\"ticks()\" [titles]=\"axisTitles()\" />\n\n            <!-- A value beyond the configured axis limits is clipped to the plot rather than drawn over the\n                 heading above it. The data labels stay outside this group so they remain readable. -->\n            <g [attr.clip-path]=\"'url(#' + clipId + ')'\">\n                @for (bar of bars(); track bar.key) {\n                    <rect\n                        class=\"tum-ui-bar-chart-bar\"\n                        [class.tum-ui-bar-chart-bar-interactive]=\"interactive()\"\n                        [attr.x]=\"bar.x\"\n                        [attr.y]=\"bar.y\"\n                        [attr.width]=\"bar.width\"\n                        [attr.height]=\"bar.height\"\n                        [attr.fill]=\"bar.color\"\n                        [attr.role]=\"interactive() ? 'button' : undefined\"\n                        [attr.tabindex]=\"interactive() ? 0 : undefined\"\n                        [attr.aria-label]=\"interactive() ? accessibleName(bar.context) : undefined\"\n                        (mouseenter)=\"onBarEnter(bar, $event)\"\n                        (mousemove)=\"onBarEnter(bar, $event)\"\n                        (mouseleave)=\"onBarLeave()\"\n                        (click)=\"onBarSelect(bar)\"\n                        (keydown.enter)=\"onBarSelect(bar)\"\n                        (keydown.space)=\"onBarSelect(bar); $event.preventDefault()\"\n                    />\n                }\n            </g>\n\n            @for (bar of bars(); track bar.key) {\n                @if (bar.dataLabel; as dataLabel) {\n                    <text class=\"tum-ui-bar-chart-data-label\" [attr.x]=\"dataLabel.x\" [attr.y]=\"dataLabel.y\" [attr.text-anchor]=\"dataLabel.anchor\">\n                        {{ dataLabel.text }}\n                    </text>\n                }\n            }\n        </g>\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n", styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-bar-chart-bar-interactive{cursor:pointer}.tum-ui-bar-chart-bar:hover{filter:brightness(1.08)}.tum-ui-bar-chart-data-label{fill:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-xs);font-family:inherit}\n"] }]
        }], ctorParameters: () => [], propDecorators: { canvas: [{ type: i0.ViewChild, args: ['canvas', { isSignal: true }] }], labels: [{ type: i0.Input, args: [{ isSignal: true, alias: "labels", required: true }] }], series: [{ type: i0.Input, args: [{ isSignal: true, alias: "series", required: true }] }], config: [{ type: i0.Input, args: [{ isSignal: true, alias: "config", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }], interactive: [{ type: i0.Input, args: [{ isSignal: true, alias: "interactive", required: false }] }], dataSelect: [{ type: i0.Output, args: ["dataSelect"] }] } });

const TAU = Math.PI * 2;
/** Guards against the degenerate case where a full circle's start and end point coincide. */
const FULL_CIRCLE_EPSILON = 1e-6;
function pointOnCircle(centerX, centerY, radius, angle) {
    return [centerX + radius * Math.sin(angle), centerY - radius * Math.cos(angle)];
}
/**
 * Builds the SVG path of a single ring segment. An `innerRadius` of 0 produces a pie slice.
 *
 * A slice covering the whole circle is drawn as two half arcs, because a single arc whose start and
 * end coincide renders as nothing at all.
 */
function arcPath(centerX, centerY, innerRadius, outerRadius, startAngle, endAngle) {
    const sweep = endAngle - startAngle;
    if (sweep <= 0) {
        return '';
    }
    if (sweep >= TAU - FULL_CIRCLE_EPSILON) {
        const half = startAngle + Math.PI;
        return arcPath(centerX, centerY, innerRadius, outerRadius, startAngle, half) + arcPath(centerX, centerY, innerRadius, outerRadius, half, startAngle + TAU);
    }
    const largeArc = sweep > Math.PI ? 1 : 0;
    const [outerStartX, outerStartY] = pointOnCircle(centerX, centerY, outerRadius, startAngle);
    const [outerEndX, outerEndY] = pointOnCircle(centerX, centerY, outerRadius, endAngle);
    if (innerRadius <= 0) {
        return `M${centerX},${centerY}L${outerStartX},${outerStartY}A${outerRadius},${outerRadius} 0 ${largeArc} 1 ${outerEndX},${outerEndY}Z`;
    }
    const [innerEndX, innerEndY] = pointOnCircle(centerX, centerY, innerRadius, endAngle);
    const [innerStartX, innerStartY] = pointOnCircle(centerX, centerY, innerRadius, startAngle);
    return (`M${outerStartX},${outerStartY}` +
        `A${outerRadius},${outerRadius} 0 ${largeArc} 1 ${outerEndX},${outerEndY}` +
        `L${innerEndX},${innerEndY}` +
        `A${innerRadius},${innerRadius} 0 ${largeArc} 0 ${innerStartX},${innerStartY}Z`);
}
/** Splits values into consecutive slices of a full circle. A total of 0 produces no slices. */
function sliceAngles(values) {
    const contribution = (value) => (Number.isFinite(value) ? Math.max(value, 0) : 0);
    const total = values.reduce((sum, value) => sum + contribution(value), 0);
    if (total <= 0) {
        return values.map(() => ({ startAngle: 0, endAngle: 0 }));
    }
    let angle = 0;
    return values.map((value) => {
        const startAngle = angle;
        angle += (contribution(value) / total) * TAU;
        return { startAngle, endAngle: angle };
    });
}

const DEFAULT_ARC_WIDTH = 0.25;
const DEFAULT_PADDING = 20;
/**
 * A doughnut chart rendered as inline SVG, drawn from the first series. An `arcWidth` of 1 fills the
 * ring completely and produces a pie chart.
 */
class TumUiDoughnutChartComponent {
    hostElement = inject(ElementRef);
    canvas = viewChild.required('canvas', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "canvas" }] : /* istanbul ignore next */ []));
    labels = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "labels" }] : /* istanbul ignore next */ []));
    series = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "series" }] : /* istanbul ignore next */ []));
    config = input({}, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "config" }] : /* istanbul ignore next */ []));
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    /** Names the chart from a visible heading instead of a literal label. */
    ariaLabelledBy = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []));
    /** Marks slices as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
    interactive = input(false, { ...(ngDevMode ? { debugName: "interactive" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    dataSelect = output();
    size = signal({ width: 0, height: 0 }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    hovered = signal(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hovered" }] : /* istanbul ignore next */ []));
    resizeObserver;
    constructor() {
        afterNextRender(() => {
            const element = this.canvas().nativeElement;
            const rect = element.getBoundingClientRect();
            this.size.set({ width: rect.width, height: rect.height });
            if (typeof ResizeObserver === 'undefined') {
                return;
            }
            this.resizeObserver = new ResizeObserver((entries) => {
                const box = entries[0]?.contentRect;
                if (box) {
                    this.size.set({ width: box.width, height: box.height });
                }
            });
            this.resizeObserver.observe(element);
        });
    }
    ngOnDestroy() {
        this.resizeObserver?.disconnect();
    }
    /** Slices the reader switched off in the legend, by index. */
    hiddenSlices = signal(new Set(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hiddenSlices" }] : /* istanbul ignore next */ []));
    onLegendToggle(key) {
        this.hiddenSlices.update((hidden) => {
            const next = new Set(hidden);
            if (!next.delete(key)) {
                next.add(key);
            }
            return next;
        });
    }
    primarySeries = computed(() => this.series()[0], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "primarySeries" }] : /* istanbul ignore next */ []));
    slices = computed(() => {
        const { width, height } = this.size();
        const series = this.primarySeries();
        if (!series || width <= 0 || height <= 0) {
            return [];
        }
        const padding = this.config().padding ?? DEFAULT_PADDING;
        const outerRadius = Math.max(Math.min(width, height) / 2 - padding, 0);
        const arcWidth = this.config().arcWidth ?? DEFAULT_ARC_WIDTH;
        const innerRadius = outerRadius * (1 - Math.min(Math.max(arcWidth, 0), 1));
        const centerX = width / 2;
        const centerY = height / 2;
        // A hidden slice contributes nothing, so the remaining slices grow to fill the ring. A negative
        // value cannot be expressed as a share of a circle either, so it too is drawn as no arc at all;
        // the tooltip and the data table still report what the caller passed rather than hiding it.
        const values = series.data.map((value, index) => (this.hiddenSlices().has(`${index}`) ? 0 : (value ?? 0)));
        return sliceAngles(values).map((slice, index) => ({
            key: `${index}`,
            path: arcPath(centerX, centerY, innerRadius, outerRadius, slice.startAngle, slice.endAngle),
            color: series.colors?.[index % series.colors.length] ?? series.color ?? 'var(--tumaet-ui-primary-color)',
            context: {
                seriesIndex: 0,
                index,
                label: this.labels()[index] ?? '',
                seriesLabel: series.label,
                value: values[index],
                meta: series.meta?.[index],
            },
        }));
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "slices" }] : /* istanbul ignore next */ []));
    legendPosition = computed(() => legendPositionOf(this.config().legend), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "legendPosition" }] : /* istanbul ignore next */ []));
    /** A doughnut's legend names the slices rather than the series, so it follows the categories. */
    legendItems = computed(() => this.slices().map((slice) => ({ key: slice.key, label: slice.context.label, color: slice.color, hidden: this.hiddenSlices().has(slice.key) })), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "legendItems" }] : /* istanbul ignore next */ []));
    tooltip = computed(() => {
        const hovered = this.hovered();
        const config = this.config().tooltip;
        if (!hovered || config === false) {
            return undefined;
        }
        const slice = this.slices()[hovered.index];
        if (!slice) {
            return undefined;
        }
        const context = slice.context;
        const title = config?.title ? config.title([context]) : context.label;
        const raw = config?.label ? config.label(context) : `${context.value}`;
        const after = config?.afterBody?.([context]);
        const lines = [...(Array.isArray(raw) ? raw : [raw]), ...(after ? (Array.isArray(after) ? after : [after]) : [])].filter((line) => line !== '');
        return { title, lines, ...placeTooltip(hovered) };
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "tooltip" }] : /* istanbul ignore next */ []));
    accessibleRows = computed(() => this.labels().map((label, index) => ({
        label,
        values: [{ seriesLabel: this.primarySeries()?.label, value: this.primarySeries()?.data[index] }],
    })), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "accessibleRows" }] : /* istanbul ignore next */ []));
    onSliceEnter(slice, event) {
        const host = this.hostElement.nativeElement.getBoundingClientRect();
        this.hovered.set({ index: slice.context.index, x: event.clientX - host.left, y: event.clientY - host.top, hostWidth: host.width, hostHeight: host.height });
    }
    onSliceLeave() {
        this.hovered.set(undefined);
    }
    onSliceSelect(slice) {
        const { seriesIndex, index, label, seriesLabel, value, meta } = slice.context;
        this.dataSelect.emit({ seriesIndex, index, label, seriesLabel, value, meta });
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiDoughnutChartComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiDoughnutChartComponent, isStandalone: true, selector: "tum-ui-doughnut-chart", inputs: { labels: { classPropertyName: "labels", publicName: "labels", isSignal: true, isRequired: true, transformFunction: null }, series: { classPropertyName: "series", publicName: "series", isSignal: true, isRequired: true, transformFunction: null }, config: { classPropertyName: "config", publicName: "config", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, ariaLabelledBy: { classPropertyName: "ariaLabelledBy", publicName: "ariaLabelledBy", isSignal: true, isRequired: false, transformFunction: null }, interactive: { classPropertyName: "interactive", publicName: "interactive", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { dataSelect: "dataSelect" }, host: { classAttribute: "tum-ui-doughnut-chart" }, viewQueries: [{ propertyName: "canvas", first: true, predicate: ["canvas"], descendants: true, isSignal: true }], ngImport: i0, template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        @for (slice of slices(); track slice.key) {\n            <path\n                class=\"tum-ui-doughnut-chart-slice\"\n                [class.tum-ui-doughnut-chart-slice-interactive]=\"interactive()\"\n                [attr.d]=\"slice.path\"\n                [attr.fill]=\"slice.color\"\n                (mouseenter)=\"onSliceEnter(slice, $event)\"\n                (mousemove)=\"onSliceEnter(slice, $event)\"\n                (mouseleave)=\"onSliceLeave()\"\n                (click)=\"onSliceSelect(slice)\"\n            />\n        }\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n", styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-doughnut-chart-slice-interactive{cursor:pointer}.tum-ui-doughnut-chart-slice:hover{filter:brightness(1.08)}\n"], dependencies: [{ kind: "component", type: TumUiChartLegendComponent, selector: "tum-ui-chart-legend", inputs: ["items", "position"], outputs: ["toggleEntry"] }, { kind: "component", type: TumUiChartTooltipComponent, selector: "tum-ui-chart-tooltip", inputs: ["title", "lines", "x", "y", "below"] }, { kind: "component", type: TumUiChartDataTableComponent, selector: "tum-ui-chart-data-table", inputs: ["caption", "rows"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiDoughnutChartComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-doughnut-chart', imports: [TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent], changeDetection: ChangeDetectionStrategy.OnPush, host: { class: 'tum-ui-doughnut-chart' }, template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        @for (slice of slices(); track slice.key) {\n            <path\n                class=\"tum-ui-doughnut-chart-slice\"\n                [class.tum-ui-doughnut-chart-slice-interactive]=\"interactive()\"\n                [attr.d]=\"slice.path\"\n                [attr.fill]=\"slice.color\"\n                (mouseenter)=\"onSliceEnter(slice, $event)\"\n                (mousemove)=\"onSliceEnter(slice, $event)\"\n                (mouseleave)=\"onSliceLeave()\"\n                (click)=\"onSliceSelect(slice)\"\n            />\n        }\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n", styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-doughnut-chart-slice-interactive{cursor:pointer}.tum-ui-doughnut-chart-slice:hover{filter:brightness(1.08)}\n"] }]
        }], ctorParameters: () => [], propDecorators: { canvas: [{ type: i0.ViewChild, args: ['canvas', { isSignal: true }] }], labels: [{ type: i0.Input, args: [{ isSignal: true, alias: "labels", required: true }] }], series: [{ type: i0.Input, args: [{ isSignal: true, alias: "series", required: true }] }], config: [{ type: i0.Input, args: [{ isSignal: true, alias: "config", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }], interactive: [{ type: i0.Input, args: [{ isSignal: true, alias: "interactive", required: false }] }], dataSelect: [{ type: i0.Output, args: ["dataSelect"] }] } });

function moveAndLine(points) {
    return points.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x},${point.y}`).join('');
}
/**
 * The weighted harmonic mean of the slopes on either side of a point, which is the tangent that
 * keeps a cubic segment monotone (Fritsch-Carlson). A sign change means the point is a local
 * extremum, where a zero tangent is the only way to avoid overshooting it.
 */
function tangent(previous, point, next) {
    const leftRun = point.x - previous.x;
    const rightRun = next.x - point.x;
    const leftSlope = (point.y - previous.y) / (leftRun || 1);
    const rightSlope = (next.y - point.y) / (rightRun || 1);
    if (leftSlope * rightSlope <= 0) {
        return 0;
    }
    const weighted = (2 * rightRun + leftRun) / (3 * (leftRun + rightRun)) / leftSlope + (rightRun + 2 * leftRun) / (3 * (leftRun + rightRun)) / rightSlope;
    return 1 / weighted;
}
/**
 * Builds an SVG path through the points using monotone cubic interpolation, the equivalent of d3's
 * `curveMonotoneX`. Unlike a plain cubic spline it never overshoots a data point, so a series that
 * only rises is never drawn dipping below a value it actually reached.
 */
function monotoneCubicPath(points) {
    if (points.length < 3) {
        return moveAndLine(points);
    }
    const tangents = points.map((point, index) => {
        if (index === 0) {
            return (points[1].y - point.y) / (points[1].x - point.x || 1);
        }
        if (index === points.length - 1) {
            return (point.y - points[index - 1].y) / (point.x - points[index - 1].x || 1);
        }
        return tangent(points[index - 1], point, points[index + 1]);
    });
    let path = `M${points[0].x},${points[0].y}`;
    for (let i = 0; i < points.length - 1; i++) {
        const from = points[i];
        const to = points[i + 1];
        const third = (to.x - from.x) / 3;
        path += `C${from.x + third},${from.y + tangents[i] * third},${to.x - third},${to.y - tangents[i + 1] * third},${to.x},${to.y}`;
    }
    return path;
}
/** Builds a straight-segment SVG path through the points. */
function linearPath(points) {
    return moveAndLine(points);
}
/**
 * Splits a series into the runs of consecutive defined points, so that gaps stay gaps. With
 * `spanGaps` the points are treated as one run and the line bridges the missing values.
 */
function segmentsOf(points, spanGaps) {
    const defined = points.filter((point) => point !== undefined);
    if (spanGaps) {
        return defined.length ? [defined] : [];
    }
    const segments = [];
    let current = [];
    for (const point of points) {
        if (point) {
            current.push(point);
        }
        else if (current.length) {
            segments.push(current);
            current = [];
        }
    }
    if (current.length) {
        segments.push(current);
    }
    return segments;
}

const POINT_RADIUS = 3;
/**
 * How close a click has to land before it counts as selecting a point. The hit area spans the whole
 * plot so that hovering anywhere reports a category, but a click often navigates, and a stray click
 * in empty space should not open an exercise in a new tab.
 */
const SELECT_RADIUS = 40;
/** Lines are drawn at the band centers, so the band needs no gap between neighbours. */
const LINE_CATEGORY_PADDING = 0;
/**
 * A line chart rendered as inline SVG, with one line per series.
 *
 * Hovering reports every series at the hovered category at once, which is what makes several lines
 * comparable at a glance; a series marked as a reference line is drawn dashed and stays out of the
 * legend, the tooltip and select events.
 */
let nextLineChartId = 0;
class TumUiLineChartComponent {
    hostElement = inject(ElementRef);
    canvas = viewChild.required('canvas', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "canvas" }] : /* istanbul ignore next */ []));
    labels = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "labels" }] : /* istanbul ignore next */ []));
    series = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "series" }] : /* istanbul ignore next */ []));
    config = input({}, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "config" }] : /* istanbul ignore next */ []));
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    /** Names the chart from a visible heading instead of a literal label. */
    ariaLabelledBy = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []));
    /** Marks points as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
    interactive = input(false, { ...(ngDevMode ? { debugName: "interactive" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    dataSelect = output();
    pointRadius = POINT_RADIUS;
    /** Names an interactive datum for assistive technology; see {@link datumAccessibleName}. */
    accessibleName(context) {
        return datumAccessibleName(context, this.series().length > 1);
    }
    size = signal({ width: 0, height: 0 }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    hovered = signal(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hovered" }] : /* istanbul ignore next */ []));
    resizeObserver;
    constructor() {
        afterNextRender(() => {
            const element = this.canvas().nativeElement;
            const rect = element.getBoundingClientRect();
            this.size.set({ width: rect.width, height: rect.height });
            if (typeof ResizeObserver === 'undefined') {
                return;
            }
            this.resizeObserver = new ResizeObserver((entries) => {
                const box = entries[0]?.contentRect;
                if (box) {
                    this.size.set({ width: box.width, height: box.height });
                }
            });
            this.resizeObserver.observe(element);
        });
    }
    ngOnDestroy() {
        this.resizeObserver?.disconnect();
    }
    /** Series the reader switched off in the legend, by index. */
    hiddenSeries = signal(new Set(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hiddenSeries" }] : /* istanbul ignore next */ []));
    onLegendToggle(key) {
        this.hiddenSeries.update((hidden) => {
            const next = new Set(hidden);
            if (!next.delete(key)) {
                next.add(key);
            }
            return next;
        });
    }
    /** The series actually drawn, paired with their original index so meta and colors stay aligned. */
    visibleSeries = computed(() => this.series()
        .map((entry, index) => ({ entry, index }))
        .filter(({ index }) => !this.hiddenSeries().has(`${index}`)), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "visibleSeries" }] : /* istanbul ignore next */ []));
    /** As for the bar chart: an integer-valued series must not be given fractional ticks. */
    minTickStep = computed(() => (allIntegers(this.visibleSeries().flatMap(({ entry }) => [...entry.data])) ? 1 : 0), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "minTickStep" }] : /* istanbul ignore next */ []));
    valueDomain = computed(() => {
        const axis = this.config().yAxis;
        const values = finiteValues(this.visibleSeries().flatMap(({ entry }) => [...entry.data]));
        const dataMax = values.length ? Math.max(...values) : 0;
        const dataMin = values.length ? Math.min(...values) : 0;
        const [niceMin, niceMax] = niceDomain(dataMin, dataMax, 5, this.minTickStep());
        return [axis?.min ?? niceMin, axis?.max ?? niceMax];
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "valueDomain" }] : /* istanbul ignore next */ []));
    valueTickLabels = computed(() => {
        if ((this.config().yAxis?.display ?? true) === false) {
            return [];
        }
        const [min, max] = this.valueDomain();
        const format = this.config().yAxis?.tickFormatter;
        return linearScale([min, max], [0, 1])
            .ticks(5, this.minTickStep())
            .map((value) => ({ value, text: format ? format(value) : `${value}` }));
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "valueTickLabels" }] : /* istanbul ignore next */ []));
    frame = computed(() => cartesianFrame({
        size: this.size(),
        labels: (this.config().xAxis?.display ?? true) ? this.labels() : [],
        valueTicks: this.valueTickLabels(),
        horizontal: false,
        valueAxis: this.config().yAxis,
        categoryAxis: this.config().xAxis,
        xAxisTitle: this.config().xAxis?.label,
        yAxisTitle: this.config().yAxis?.label,
    }), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "frame" }] : /* istanbul ignore next */ []));
    plot = computed(() => this.frame().plot, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "plot" }] : /* istanbul ignore next */ []));
    /** Unique per instance, so charts sharing a page do not share a clip path. */
    clipId = `tum-ui-line-chart-clip-${nextLineChartId++}`;
    valueScale = computed(() => {
        const [min, max] = this.valueDomain();
        return linearScale([min, max], [this.plot().height, 0]);
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "valueScale" }] : /* istanbul ignore next */ []));
    categoryScale = computed(() => bandScale(this.labels().length, this.plot().width, LINE_CATEGORY_PADDING), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "categoryScale" }] : /* istanbul ignore next */ []));
    lines = computed(() => {
        const plot = this.plot();
        if (plot.width <= 0 || plot.height <= 0) {
            return [];
        }
        const categories = this.categoryScale();
        const valueScale = this.valueScale();
        const monotone = this.config().monotone ?? false;
        const spanGaps = this.config().spanGaps ?? false;
        const showPoints = this.config().points ?? true;
        return this.visibleSeries().map(({ entry, index: seriesIndex }) => {
            const positioned = this.labels().map((_, index) => {
                const value = entry.data[index];
                // Treat a value the scale cannot place as a gap rather than drawing the line to NaN.
                if (value === undefined || value === null || !Number.isFinite(value)) {
                    return undefined;
                }
                return { x: categories.center(index), y: valueScale(value) };
            });
            const build = monotone ? monotoneCubicPath : linearPath;
            const paths = segmentsOf(positioned, spanGaps).map(build);
            const color = entry.color ?? 'var(--tumaet-ui-primary-color)';
            return {
                key: `${seriesIndex}`,
                paths,
                color,
                dashed: entry.referenceLine ?? false,
                points: showPoints && !entry.referenceLine
                    ? positioned.flatMap((point, index) => point
                        ? [
                            {
                                key: `${seriesIndex}-${index}`,
                                x: point.x,
                                y: point.y,
                                context: {
                                    seriesIndex,
                                    index,
                                    label: this.labels()[index],
                                    seriesLabel: entry.label,
                                    value: entry.data[index],
                                    meta: entry.meta?.[index],
                                },
                            },
                        ]
                        : [])
                    : [],
            };
        });
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "lines" }] : /* istanbul ignore next */ []));
    gridLines = computed(() => (this.config().yAxis?.display ?? true) ? gridLineViews(this.plot(), this.valueScale(), this.valueTickLabels(), false) : [], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "gridLines" }] : /* istanbul ignore next */ []));
    ticks = computed(() => {
        const plot = this.plot();
        const value = (this.config().yAxis?.display ?? true) ? valueTickViews(plot, this.valueScale(), this.valueTickLabels(), false) : [];
        const category = (this.config().xAxis?.display ?? true)
            ? categoryTickViews(plot, this.categoryScale(), this.labels(), false, this.frame().rotateCategoryLabels, this.config().xAxis?.tickFormatter, this.frame().categoryLabelBudget)
            : [];
        return [...value, ...category];
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ticks" }] : /* istanbul ignore next */ []));
    axisTitles = computed(() => {
        const titles = axisTitleViews(this.plot(), this.frame().margin, this.config().xAxis?.label, this.config().yAxis?.label);
        return [titles.x, titles.y].filter((title) => title !== undefined);
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "axisTitles" }] : /* istanbul ignore next */ []));
    legendPosition = computed(() => legendPositionOf(this.config().legend), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "legendPosition" }] : /* istanbul ignore next */ []));
    legendItems = computed(() => this.series()
        .map((entry, index) => ({ entry, index }))
        .filter(({ entry }) => entry.label && !entry.referenceLine)
        .map(({ entry, index }) => ({
        key: `${index}`,
        label: entry.label,
        color: entry.color ?? 'var(--tumaet-ui-primary-color)',
        hidden: this.hiddenSeries().has(`${index}`),
    })), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "legendItems" }] : /* istanbul ignore next */ []));
    /** The x coordinate of the guide drawn through the hovered category. */
    guideX = computed(() => {
        const hovered = this.hovered();
        return hovered === undefined ? undefined : this.categoryScale().center(hovered.index);
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "guideX" }] : /* istanbul ignore next */ []));
    tooltip = computed(() => {
        const hovered = this.hovered();
        const config = this.config().tooltip;
        if (!hovered || config === false) {
            return undefined;
        }
        const contexts = [];
        this.visibleSeries().forEach(({ entry, index: seriesIndex }) => {
            const value = entry.data[hovered.index];
            // `lines` treats a non-finite value as a gap, so the tooltip must not report one either.
            if (entry.referenceLine || value === undefined || value === null || !Number.isFinite(value)) {
                return;
            }
            contexts.push({
                seriesIndex,
                index: hovered.index,
                label: this.labels()[hovered.index],
                seriesLabel: entry.label,
                value,
                meta: entry.meta?.[hovered.index],
            });
        });
        if (!contexts.length) {
            return undefined;
        }
        const title = config?.title ? config.title(contexts) : this.labels()[hovered.index];
        const lines = contexts.flatMap((context) => {
            const raw = config?.label ? config.label(context) : `${context.seriesLabel ? `${context.seriesLabel}: ` : ''}${context.value}`;
            return Array.isArray(raw) ? raw : [raw];
        });
        const after = config?.afterBody?.(contexts);
        return {
            title,
            lines: [...lines, ...(after ? (Array.isArray(after) ? after : [after]) : [])].filter((line) => line !== ''),
            ...placeTooltip(hovered),
        };
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "tooltip" }] : /* istanbul ignore next */ []));
    accessibleRows = computed(() => this.labels().map((label, index) => ({
        label,
        values: this.series().map((entry) => ({ seriesLabel: entry.label, value: entry.data[index] })),
    })), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "accessibleRows" }] : /* istanbul ignore next */ []));
    /** Resolves a pointer position to the nearest category, so hovering anywhere reports a series. */
    onPlotMove(event) {
        const labels = this.labels();
        if (!labels.length) {
            return;
        }
        const canvas = this.canvas().nativeElement.getBoundingClientRect();
        const host = this.hostElement.nativeElement.getBoundingClientRect();
        const withinPlot = event.clientX - canvas.left - this.plot().left;
        const categories = this.categoryScale();
        let nearest = 0;
        let shortest = Number.POSITIVE_INFINITY;
        labels.forEach((_, index) => {
            const distance = Math.abs(categories.center(index) - withinPlot);
            if (distance < shortest) {
                shortest = distance;
                nearest = index;
            }
        });
        this.hovered.set({ index: nearest, x: event.clientX - host.left, y: event.clientY - host.top, hostWidth: host.width, hostHeight: host.height });
    }
    onPlotLeave() {
        this.hovered.set(undefined);
    }
    /**
     * Emits the point closest to the click. The hit area covers the plot so that the whole chart is
     * clickable rather than only the few pixels of a marker, which matches how the hover behaves.
     */
    /** Keyboard activation of a focused point, which the plot-wide hit area cannot provide. */
    onPointSelect(context) {
        const { seriesIndex, index, label, seriesLabel, value, meta } = context;
        this.dataSelect.emit({ seriesIndex, index, label, seriesLabel, value, meta });
    }
    onPlotClick(event) {
        const hovered = this.hovered();
        if (!hovered) {
            return;
        }
        const canvas = this.canvas().nativeElement.getBoundingClientRect();
        const plot = this.plot();
        const clickX = event.clientX - canvas.left - plot.left;
        const clickY = event.clientY - canvas.top - plot.top;
        let nearest;
        let shortest = Number.POSITIVE_INFINITY;
        for (const line of this.lines()) {
            for (const point of line.points) {
                const distance = Math.hypot(point.x - clickX, point.y - clickY);
                if (distance < shortest) {
                    shortest = distance;
                    nearest = point.context;
                }
            }
        }
        if (!nearest || shortest > SELECT_RADIUS) {
            return;
        }
        const { seriesIndex, index, label, seriesLabel, value, meta } = nearest;
        this.dataSelect.emit({ seriesIndex, index, label, seriesLabel, value, meta });
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiLineChartComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiLineChartComponent, isStandalone: true, selector: "tum-ui-line-chart", inputs: { labels: { classPropertyName: "labels", publicName: "labels", isSignal: true, isRequired: true, transformFunction: null }, series: { classPropertyName: "series", publicName: "series", isSignal: true, isRequired: true, transformFunction: null }, config: { classPropertyName: "config", publicName: "config", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, ariaLabelledBy: { classPropertyName: "ariaLabelledBy", publicName: "ariaLabelledBy", isSignal: true, isRequired: false, transformFunction: null }, interactive: { classPropertyName: "interactive", publicName: "interactive", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { dataSelect: "dataSelect" }, host: { classAttribute: "tum-ui-line-chart" }, viewQueries: [{ propertyName: "canvas", first: true, predicate: ["canvas"], descendants: true, isSignal: true }], ngImport: i0, template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        <defs>\n            <clipPath [attr.id]=\"clipId\">\n                <rect [attr.width]=\"plot().width\" [attr.height]=\"plot().height\" />\n            </clipPath>\n        </defs>\n        <g [attr.transform]=\"'translate(' + plot().left + ' ' + plot().top + ')'\">\n            <svg:g tumUiChartAxes [gridLines]=\"gridLines()\" [ticks]=\"ticks()\" [titles]=\"axisTitles()\" />\n\n            @if (guideX(); as x) {\n                <line class=\"tum-ui-line-chart-guide\" [attr.x1]=\"x\" [attr.y1]=\"0\" [attr.x2]=\"x\" [attr.y2]=\"plot().height\" />\n            }\n\n            <!-- A point outside the configured axis limits is clipped to the plot rather than drawn past the\n                 container. The hit area below stays unclipped so hovering still works at the edges. -->\n            <g [attr.clip-path]=\"'url(#' + clipId + ')'\">\n                @for (line of lines(); track line.key) {\n                    @for (path of line.paths; track $index) {\n                        <path class=\"tum-ui-line-chart-line\" [class.tum-ui-line-chart-line-dashed]=\"line.dashed\" [attr.d]=\"path\" [attr.stroke]=\"line.color\" fill=\"none\" />\n                    }\n                }\n\n                @for (line of lines(); track line.key) {\n                    @for (point of line.points; track point.key) {\n                        <circle\n                            class=\"tum-ui-line-chart-point\"\n                            [class.tum-ui-line-chart-point-interactive]=\"interactive()\"\n                            [attr.cx]=\"point.x\"\n                            [attr.cy]=\"point.y\"\n                            [attr.r]=\"pointRadius\"\n                            [attr.fill]=\"line.color\"\n                            [attr.role]=\"interactive() ? 'button' : undefined\"\n                            [attr.tabindex]=\"interactive() ? 0 : undefined\"\n                            [attr.aria-label]=\"interactive() ? accessibleName(point.context) : undefined\"\n                            (keydown.enter)=\"onPointSelect(point.context)\"\n                            (keydown.space)=\"onPointSelect(point.context); $event.preventDefault()\"\n                        />\n                    }\n                }\n            </g>\n\n            <!--\n              Sits above the series so that hovering anywhere in the plot reports the nearest\n              category, and clicking selects the nearest point rather than only the marker itself.\n            -->\n            <rect\n                class=\"tum-ui-line-chart-hit-area\"\n                [class.tum-ui-line-chart-hit-area-interactive]=\"interactive()\"\n                [attr.width]=\"plot().width\"\n                [attr.height]=\"plot().height\"\n                (mousemove)=\"onPlotMove($event)\"\n                (mouseleave)=\"onPlotLeave()\"\n                (click)=\"onPlotClick($event)\"\n            />\n        </g>\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n", styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-line-chart-line{stroke-width:2;fill:none}.tum-ui-line-chart-line-dashed{stroke-dasharray:5 5;stroke-width:1.5}.tum-ui-line-chart-point{pointer-events:none}.tum-ui-line-chart-point-interactive{pointer-events:all;cursor:pointer}.tum-ui-line-chart-guide{stroke:var(--tumaet-ui-border-color);stroke-width:1}.tum-ui-line-chart-hit-area{fill:transparent}.tum-ui-line-chart-hit-area-interactive{cursor:pointer}\n"], dependencies: [{ kind: "component", type: TumUiChartAxesComponent, selector: "g[tumUiChartAxes]", inputs: ["gridLines", "ticks", "titles"] }, { kind: "component", type: TumUiChartLegendComponent, selector: "tum-ui-chart-legend", inputs: ["items", "position"], outputs: ["toggleEntry"] }, { kind: "component", type: TumUiChartTooltipComponent, selector: "tum-ui-chart-tooltip", inputs: ["title", "lines", "x", "y", "below"] }, { kind: "component", type: TumUiChartDataTableComponent, selector: "tum-ui-chart-data-table", inputs: ["caption", "rows"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiLineChartComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-line-chart', imports: [TumUiChartAxesComponent, TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent], changeDetection: ChangeDetectionStrategy.OnPush, host: { class: 'tum-ui-line-chart' }, template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        <defs>\n            <clipPath [attr.id]=\"clipId\">\n                <rect [attr.width]=\"plot().width\" [attr.height]=\"plot().height\" />\n            </clipPath>\n        </defs>\n        <g [attr.transform]=\"'translate(' + plot().left + ' ' + plot().top + ')'\">\n            <svg:g tumUiChartAxes [gridLines]=\"gridLines()\" [ticks]=\"ticks()\" [titles]=\"axisTitles()\" />\n\n            @if (guideX(); as x) {\n                <line class=\"tum-ui-line-chart-guide\" [attr.x1]=\"x\" [attr.y1]=\"0\" [attr.x2]=\"x\" [attr.y2]=\"plot().height\" />\n            }\n\n            <!-- A point outside the configured axis limits is clipped to the plot rather than drawn past the\n                 container. The hit area below stays unclipped so hovering still works at the edges. -->\n            <g [attr.clip-path]=\"'url(#' + clipId + ')'\">\n                @for (line of lines(); track line.key) {\n                    @for (path of line.paths; track $index) {\n                        <path class=\"tum-ui-line-chart-line\" [class.tum-ui-line-chart-line-dashed]=\"line.dashed\" [attr.d]=\"path\" [attr.stroke]=\"line.color\" fill=\"none\" />\n                    }\n                }\n\n                @for (line of lines(); track line.key) {\n                    @for (point of line.points; track point.key) {\n                        <circle\n                            class=\"tum-ui-line-chart-point\"\n                            [class.tum-ui-line-chart-point-interactive]=\"interactive()\"\n                            [attr.cx]=\"point.x\"\n                            [attr.cy]=\"point.y\"\n                            [attr.r]=\"pointRadius\"\n                            [attr.fill]=\"line.color\"\n                            [attr.role]=\"interactive() ? 'button' : undefined\"\n                            [attr.tabindex]=\"interactive() ? 0 : undefined\"\n                            [attr.aria-label]=\"interactive() ? accessibleName(point.context) : undefined\"\n                            (keydown.enter)=\"onPointSelect(point.context)\"\n                            (keydown.space)=\"onPointSelect(point.context); $event.preventDefault()\"\n                        />\n                    }\n                }\n            </g>\n\n            <!--\n              Sits above the series so that hovering anywhere in the plot reports the nearest\n              category, and clicking selects the nearest point rather than only the marker itself.\n            -->\n            <rect\n                class=\"tum-ui-line-chart-hit-area\"\n                [class.tum-ui-line-chart-hit-area-interactive]=\"interactive()\"\n                [attr.width]=\"plot().width\"\n                [attr.height]=\"plot().height\"\n                (mousemove)=\"onPlotMove($event)\"\n                (mouseleave)=\"onPlotLeave()\"\n                (click)=\"onPlotClick($event)\"\n            />\n        </g>\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n", styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-line-chart-line{stroke-width:2;fill:none}.tum-ui-line-chart-line-dashed{stroke-dasharray:5 5;stroke-width:1.5}.tum-ui-line-chart-point{pointer-events:none}.tum-ui-line-chart-point-interactive{pointer-events:all;cursor:pointer}.tum-ui-line-chart-guide{stroke:var(--tumaet-ui-border-color);stroke-width:1}.tum-ui-line-chart-hit-area{fill:transparent}.tum-ui-line-chart-hit-area-interactive{cursor:pointer}\n"] }]
        }], ctorParameters: () => [], propDecorators: { canvas: [{ type: i0.ViewChild, args: ['canvas', { isSignal: true }] }], labels: [{ type: i0.Input, args: [{ isSignal: true, alias: "labels", required: true }] }], series: [{ type: i0.Input, args: [{ isSignal: true, alias: "series", required: true }] }], config: [{ type: i0.Input, args: [{ isSignal: true, alias: "config", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }], interactive: [{ type: i0.Input, args: [{ isSignal: true, alias: "interactive", required: false }] }], dataSelect: [{ type: i0.Output, args: ["dataSelect"] }] } });

class TumUiCheckboxComponent {
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    inputId = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "inputId" }] : /* istanbul ignore next */ []));
    name = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "name" }] : /* istanbul ignore next */ []));
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    checked = model(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "checked" }] : /* istanbul ignore next */ []));
    /**
     * Renders the partial-selection dash instead of the tick, for a select-all control whose rows are only
     * partly selected. Purely visual: it never changes `checked`, the model, or what `changed` emits.
     */
    indeterminate = input(false, { ...(ngDevMode ? { debugName: "indeterminate" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    changed = output();
    faCheck = faCheck;
    faMinus = faMinus;
    // Indeterminate takes visual precedence over checked, as a native `<input indeterminate>` does: the dash
    // shows whenever indeterminate is set, the tick only when checked and NOT indeterminate.
    showDash = computed(() => this.indeterminate(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "showDash" }] : /* istanbul ignore next */ []));
    showTick = computed(() => this.checked() && !this.indeterminate(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "showTick" }] : /* istanbul ignore next */ []));
    cvaDisabled = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "cvaDisabled" }] : /* istanbul ignore next */ []));
    isDisabled = computed(() => this.disabled() || this.cvaDisabled(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []));
    boxClasses = computed(() => {
        if (this.isDisabled()) {
            return 'tum:bg-disabled-background tum:border-control-border';
        }
        // Both the checked tick and the indeterminate dash sit on a brand-filled box.
        if (this.checked() || this.indeterminate()) {
            return 'tum:bg-primary tum:border-primary';
        }
        return 'tum:bg-control-background tum:border-control-border';
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "boxClasses" }] : /* istanbul ignore next */ []));
    iconClasses = computed(() => (this.isDisabled() ? 'tum:text-disabled' : 'tum:text-primary-contrast'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "iconClasses" }] : /* istanbul ignore next */ []));
    onModelChange = () => { };
    onModelTouched = () => { };
    onInputChange(event) {
        const newChecked = event.target.checked;
        this.checked.set(newChecked);
        this.onModelChange(newChecked);
        this.onModelTouched();
        this.changed.emit({ originalEvent: event, checked: newChecked });
    }
    onBlur() {
        this.onModelTouched();
    }
    writeValue(value) {
        this.checked.set(!!value);
    }
    registerOnChange(fn) {
        this.onModelChange = fn;
    }
    registerOnTouched(fn) {
        this.onModelTouched = fn;
    }
    setDisabledState(isDisabled) {
        this.cvaDisabled.set(isDisabled);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiCheckboxComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiCheckboxComponent, isStandalone: true, selector: "tum-ui-checkbox", inputs: { disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, inputId: { classPropertyName: "inputId", publicName: "inputId", isSignal: true, isRequired: false, transformFunction: null }, name: { classPropertyName: "name", publicName: "name", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, checked: { classPropertyName: "checked", publicName: "checked", isSignal: true, isRequired: false, transformFunction: null }, indeterminate: { classPropertyName: "indeterminate", publicName: "indeterminate", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { checked: "checkedChange", changed: "changed" }, host: { classAttribute: "tum-ui-checkbox" }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiCheckboxComponent), multi: true }], ngImport: i0, template: "<input\n    type=\"checkbox\"\n    class=\"tum-ui-checkbox-input\"\n    [id]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [checked]=\"checked()\"\n    [indeterminate]=\"indeterminate()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    (change)=\"onInputChange($event)\"\n    (blur)=\"onBlur()\"\n/>\n<div class=\"tum-ui-checkbox-box\" [class]=\"boxClasses()\" aria-hidden=\"true\">\n    @if (showDash()) {\n        <fa-icon [icon]=\"faMinus\" class=\"tum-ui-checkbox-icon\" [class]=\"iconClasses()\" />\n    } @else if (showTick()) {\n        <fa-icon [icon]=\"faCheck\" class=\"tum-ui-checkbox-icon\" [class]=\"iconClasses()\" />\n    }\n</div>\n", styles: [":host{position:relative;display:inline-flex;width:calc(var(--tumaet-ui-spacing, .25rem) * 5);height:calc(var(--tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-checkbox-input{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none}.tum-ui-checkbox-input:disabled{cursor:default}.tum-ui-checkbox-box{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:var(--tumaet-ui-radius-sm);box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent);transition:background .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-checkbox-box:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-checkbox-icon{font-size:var(--tumaet-ui-font-size-sm);line-height:1}:host:has(.tum-ui-checkbox-input:hover:not(:disabled)) .tum-ui-checkbox-box:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:has(.tum-ui-checkbox-input:focus-visible) .tum-ui-checkbox-box{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-checkbox-input{appearance:auto;opacity:1}.tum-ui-checkbox-box{display:none}}\n"], dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiCheckboxComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-checkbox', imports: [FaIconComponent], host: { class: 'tum-ui-checkbox' }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiCheckboxComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<input\n    type=\"checkbox\"\n    class=\"tum-ui-checkbox-input\"\n    [id]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [checked]=\"checked()\"\n    [indeterminate]=\"indeterminate()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    (change)=\"onInputChange($event)\"\n    (blur)=\"onBlur()\"\n/>\n<div class=\"tum-ui-checkbox-box\" [class]=\"boxClasses()\" aria-hidden=\"true\">\n    @if (showDash()) {\n        <fa-icon [icon]=\"faMinus\" class=\"tum-ui-checkbox-icon\" [class]=\"iconClasses()\" />\n    } @else if (showTick()) {\n        <fa-icon [icon]=\"faCheck\" class=\"tum-ui-checkbox-icon\" [class]=\"iconClasses()\" />\n    }\n</div>\n", styles: [":host{position:relative;display:inline-flex;width:calc(var(--tumaet-ui-spacing, .25rem) * 5);height:calc(var(--tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-checkbox-input{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none}.tum-ui-checkbox-input:disabled{cursor:default}.tum-ui-checkbox-box{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:var(--tumaet-ui-radius-sm);box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent);transition:background .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-checkbox-box:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-checkbox-icon{font-size:var(--tumaet-ui-font-size-sm);line-height:1}:host:has(.tum-ui-checkbox-input:hover:not(:disabled)) .tum-ui-checkbox-box:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:has(.tum-ui-checkbox-input:focus-visible) .tum-ui-checkbox-box{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-checkbox-input{appearance:auto;opacity:1}.tum-ui-checkbox-box{display:none}}\n"] }]
        }], propDecorators: { disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], name: [{ type: i0.Input, args: [{ isSignal: true, alias: "name", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], checked: [{ type: i0.Input, args: [{ isSignal: true, alias: "checked", required: false }] }, { type: i0.Output, args: ["checkedChange"] }], indeterminate: [{ type: i0.Input, args: [{ isSignal: true, alias: "indeterminate", required: false }] }], changed: [{ type: i0.Output, args: ["changed"] }] } });

const DIALOG_SIZE_CLASSES = {
    small: 'tum:w-[min(32rem,90dvw)]',
    medium: 'tum:w-[min(48rem,90dvw)]',
    large: 'tum:w-[min(72rem,90dvw)]',
    full: 'tum:h-[90dvh] tum:w-[90dvw]',
};
let nextDialogId = 0;
// Page scroll lock shared by all dialogs, counted so nested dialogs unlock only once the last one closes.
// CDK's own BlockScrollStrategy is deliberately not used: it pins the root with `position: fixed` and a
// negative `top`, which makes the page behind the mask jump. Overflow plus a scrollbar-width pad holds the
// page exactly where it was.
let openDialogCount = 0;
let previousRootOverflow = '';
let previousRootPaddingRight = '';
function lockPageScroll() {
    if (openDialogCount++ > 0) {
        return;
    }
    const root = document.documentElement;
    previousRootOverflow = root.style.overflow;
    previousRootPaddingRight = root.style.paddingRight;
    // Reserve the scrollbar width so the page behind the mask does not reflow.
    const scrollbarWidth = window.innerWidth - root.clientWidth;
    root.style.overflow = 'hidden';
    if (scrollbarWidth > 0) {
        root.style.paddingRight = `${scrollbarWidth}px`;
    }
}
function unlockPageScroll() {
    if (openDialogCount === 0 || --openDialogCount > 0) {
        return;
    }
    const root = document.documentElement;
    root.style.overflow = previousRootOverflow;
    root.style.paddingRight = previousRootPaddingRight;
}
/** Controlled modal dialog built on Angular CDK Dialog. */
class TumUiDialogComponent {
    dialog = inject(Dialog);
    overlay = inject(Overlay);
    viewContainerRef = inject(ViewContainerRef);
    /** Controlled open state; dismissal writes `false`. */
    visible = model(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "visible" }] : /* istanbul ignore next */ []));
    /** Visible title and default accessible name. */
    header = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "header" }] : /* istanbul ignore next */ []));
    /** Hides the header; an `ariaLabel` is then required. */
    showHeader = input(true, { ...(ngDevMode ? { debugName: "showHeader" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Shows the close button without changing Escape or backdrop behavior. */
    closable = input(true, { ...(ngDevMode ? { debugName: "closable" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Allows Escape to close the dialog. */
    closeOnEscape = input(true, { ...(ngDevMode ? { debugName: "closeOnEscape" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Allows a backdrop click to close the dialog. */
    dismissableMask = input(false, { ...(ngDevMode ? { debugName: "dismissableMask" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Responsive dialog dimensions. Omit for content-sized dialogs. */
    size = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "size" }] : /* istanbul ignore next */ []));
    /** Accessible name used when no visible header or header template is present. */
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    closeButtonAriaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "closeButtonAriaLabel" }] : /* istanbul ignore next */ []));
    role = input('dialog', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "role" }] : /* istanbul ignore next */ []));
    ariaDescribedBy = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaDescribedBy" }] : /* istanbul ignore next */ []));
    shown = output();
    hidden = output();
    panel = viewChild.required('panel', { ...(ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {}), read: TemplateRef });
    headerTemplate = contentChild('header', { ...(ngDevMode ? { debugName: "headerTemplate" } : /* istanbul ignore next */ {}), read: TemplateRef });
    footerTemplate = contentChild('footer', { ...(ngDevMode ? { debugName: "footerTemplate" } : /* istanbul ignore next */ {}), read: TemplateRef });
    titleId = `tum-ui-dialog-title-${nextDialogId++}`;
    faXmark = faXmark;
    labelledBy = computed(() => (this.showHeader() && (this.header()?.trim() || this.headerTemplate()) ? this.titleId : undefined), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "labelledBy" }] : /* istanbul ignore next */ []));
    // Only the size varies. The rest of the panel's classes stay a static attribute on the template so the
    // element never renders class-less for a frame: a host that transitions background-color would otherwise
    // animate the panel in from transparent, showing the backdrop through it.
    sizeClasses = computed(() => {
        const size = this.size();
        return size ? DIALOG_SIZE_CLASSES[size] : '';
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "sizeClasses" }] : /* istanbul ignore next */ []));
    dialogRef;
    visibilitySync = effect(() => {
        if (this.visible()) {
            this.open();
        }
        else {
            this.dialogRef?.close();
        }
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "visibilitySync" }] : /* istanbul ignore next */ []));
    close() {
        this.visible.set(false);
    }
    open() {
        if (this.dialogRef) {
            return;
        }
        const ariaLabel = this.ariaLabel()?.trim();
        const labelledBy = this.labelledBy();
        if (!ariaLabel && !labelledBy) {
            throw new Error('tum-ui-dialog requires a visible header, a header template, or ariaLabel');
        }
        const ref = this.dialog.open(this.panel(), {
            viewContainerRef: this.viewContainerRef,
            // Page scrolling is held by lockPageScroll() instead — see the note on that function.
            scrollStrategy: this.overlay.scrollStrategies.noop(),
            hasBackdrop: true,
            backdropClass: 'cdk-overlay-dark-backdrop',
            disableClose: true,
            ariaModal: true,
            role: this.role(),
            ariaLabel: ariaLabel ?? null,
            ariaLabelledBy: labelledBy ?? null,
            ariaDescribedBy: this.ariaDescribedBy() ?? null,
            restoreFocus: true,
        });
        this.dialogRef = ref;
        lockPageScroll();
        ref.backdropClick.subscribe(() => {
            if (this.dismissableMask()) {
                this.close();
            }
        });
        ref.keydownEvents.subscribe((event) => {
            if (event.key === 'Escape' && this.closeOnEscape()) {
                this.close();
            }
        });
        ref.closed.subscribe(() => {
            // Released before the guard below: `closed` fires exactly once per opened dialog, so this covers the
            // destroy-while-open path too, where ngOnDestroy has already cleared `dialogRef`.
            unlockPageScroll();
            if (this.dialogRef !== ref) {
                return;
            }
            this.dialogRef = undefined;
            if (this.visible()) {
                this.visible.set(false);
            }
            this.hidden.emit();
        });
        this.shown.emit();
    }
    ngOnDestroy() {
        this.visibilitySync.destroy();
        const ref = this.dialogRef;
        this.dialogRef = undefined;
        ref?.close();
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiDialogComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiDialogComponent, isStandalone: true, selector: "tum-ui-dialog", inputs: { visible: { classPropertyName: "visible", publicName: "visible", isSignal: true, isRequired: false, transformFunction: null }, header: { classPropertyName: "header", publicName: "header", isSignal: true, isRequired: false, transformFunction: null }, showHeader: { classPropertyName: "showHeader", publicName: "showHeader", isSignal: true, isRequired: false, transformFunction: null }, closable: { classPropertyName: "closable", publicName: "closable", isSignal: true, isRequired: false, transformFunction: null }, closeOnEscape: { classPropertyName: "closeOnEscape", publicName: "closeOnEscape", isSignal: true, isRequired: false, transformFunction: null }, dismissableMask: { classPropertyName: "dismissableMask", publicName: "dismissableMask", isSignal: true, isRequired: false, transformFunction: null }, size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, closeButtonAriaLabel: { classPropertyName: "closeButtonAriaLabel", publicName: "closeButtonAriaLabel", isSignal: true, isRequired: false, transformFunction: null }, role: { classPropertyName: "role", publicName: "role", isSignal: true, isRequired: false, transformFunction: null }, ariaDescribedBy: { classPropertyName: "ariaDescribedBy", publicName: "ariaDescribedBy", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { visible: "visibleChange", shown: "shown", hidden: "hidden" }, queries: [{ propertyName: "headerTemplate", first: true, predicate: ["header"], descendants: true, read: TemplateRef, isSignal: true }, { propertyName: "footerTemplate", first: true, predicate: ["footer"], descendants: true, read: TemplateRef, isSignal: true }], viewQueries: [{ propertyName: "panel", first: true, predicate: ["panel"], descendants: true, read: TemplateRef, isSignal: true }], ngImport: i0, template: "<ng-template #panel>\n    <div\n        class=\"tum-ui-dialog tum:flex tum:max-h-[90dvh] tum:max-w-[90dvw] tum:flex-col tum:overflow-hidden tum:rounded-xl tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-xl\"\n        [class]=\"sizeClasses()\"\n    >\n        @if (showHeader()) {\n            <div class=\"tum-ui-dialog-header tum:flex tum:shrink-0 tum:items-center tum:justify-between tum:gap-2 tum:p-4\">\n                <div [id]=\"titleId\" class=\"tum-ui-dialog-title tum:text-xl tum:font-semibold\">\n                    @if (headerTemplate(); as headerTpl) {\n                        <ng-container [ngTemplateOutlet]=\"headerTpl\" />\n                    } @else {\n                        {{ header() }}\n                    }\n                </div>\n                @if (closable()) {\n                    <button\n                        type=\"button\"\n                        class=\"tum-ui-dialog-close tum:inline-flex tum:h-8 tum:w-8 tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:rounded-full tum:border-0 tum:bg-transparent tum:text-muted tum:transition-colors tum:hover:bg-hover-background\"\n                        [attr.aria-label]=\"closeButtonAriaLabel() ?? ('tumUi.dialog.close' | tumUiTranslate)\"\n                        (click)=\"close()\"\n                    >\n                        <fa-icon [icon]=\"faXmark\" />\n                    </button>\n                }\n            </div>\n        }\n\n        <div class=\"tum-ui-dialog-content tum:grow tum:overflow-y-auto tum:px-4 tum:pb-4\">\n            <ng-content />\n        </div>\n\n        @if (footerTemplate(); as footerTpl) {\n            <div class=\"tum-ui-dialog-footer tum:flex tum:shrink-0 tum:justify-end tum:gap-2 tum:px-4 tum:pb-4\">\n                <ng-container [ngTemplateOutlet]=\"footerTpl\" />\n            </div>\n        }\n    </div>\n</ng-template>\n", dependencies: [{ kind: "directive", type: NgTemplateOutlet, selector: "[ngTemplateOutlet]", inputs: ["ngTemplateOutletContext", "ngTemplateOutlet", "ngTemplateOutletInjector"] }, { kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }, { kind: "pipe", type: TumUiTranslatePipe, name: "tumUiTranslate" }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiDialogComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-dialog', imports: [NgTemplateOutlet, FaIconComponent, TumUiTranslatePipe], changeDetection: ChangeDetectionStrategy.OnPush, template: "<ng-template #panel>\n    <div\n        class=\"tum-ui-dialog tum:flex tum:max-h-[90dvh] tum:max-w-[90dvw] tum:flex-col tum:overflow-hidden tum:rounded-xl tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-xl\"\n        [class]=\"sizeClasses()\"\n    >\n        @if (showHeader()) {\n            <div class=\"tum-ui-dialog-header tum:flex tum:shrink-0 tum:items-center tum:justify-between tum:gap-2 tum:p-4\">\n                <div [id]=\"titleId\" class=\"tum-ui-dialog-title tum:text-xl tum:font-semibold\">\n                    @if (headerTemplate(); as headerTpl) {\n                        <ng-container [ngTemplateOutlet]=\"headerTpl\" />\n                    } @else {\n                        {{ header() }}\n                    }\n                </div>\n                @if (closable()) {\n                    <button\n                        type=\"button\"\n                        class=\"tum-ui-dialog-close tum:inline-flex tum:h-8 tum:w-8 tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:rounded-full tum:border-0 tum:bg-transparent tum:text-muted tum:transition-colors tum:hover:bg-hover-background\"\n                        [attr.aria-label]=\"closeButtonAriaLabel() ?? ('tumUi.dialog.close' | tumUiTranslate)\"\n                        (click)=\"close()\"\n                    >\n                        <fa-icon [icon]=\"faXmark\" />\n                    </button>\n                }\n            </div>\n        }\n\n        <div class=\"tum-ui-dialog-content tum:grow tum:overflow-y-auto tum:px-4 tum:pb-4\">\n            <ng-content />\n        </div>\n\n        @if (footerTemplate(); as footerTpl) {\n            <div class=\"tum-ui-dialog-footer tum:flex tum:shrink-0 tum:justify-end tum:gap-2 tum:px-4 tum:pb-4\">\n                <ng-container [ngTemplateOutlet]=\"footerTpl\" />\n            </div>\n        }\n    </div>\n</ng-template>\n" }]
        }], propDecorators: { visible: [{ type: i0.Input, args: [{ isSignal: true, alias: "visible", required: false }] }, { type: i0.Output, args: ["visibleChange"] }], header: [{ type: i0.Input, args: [{ isSignal: true, alias: "header", required: false }] }], showHeader: [{ type: i0.Input, args: [{ isSignal: true, alias: "showHeader", required: false }] }], closable: [{ type: i0.Input, args: [{ isSignal: true, alias: "closable", required: false }] }], closeOnEscape: [{ type: i0.Input, args: [{ isSignal: true, alias: "closeOnEscape", required: false }] }], dismissableMask: [{ type: i0.Input, args: [{ isSignal: true, alias: "dismissableMask", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], closeButtonAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "closeButtonAriaLabel", required: false }] }], role: [{ type: i0.Input, args: [{ isSignal: true, alias: "role", required: false }] }], ariaDescribedBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaDescribedBy", required: false }] }], shown: [{ type: i0.Output, args: ["shown"] }], hidden: [{ type: i0.Output, args: ["hidden"] }], panel: [{ type: i0.ViewChild, args: ['panel', { ...{ read: TemplateRef }, isSignal: true }] }], headerTemplate: [{ type: i0.ContentChild, args: ['header', { ...{ read: TemplateRef }, isSignal: true }] }], footerTemplate: [{ type: i0.ContentChild, args: ['footer', { ...{ read: TemplateRef }, isSignal: true }] }] } });

/** Coordinates confirmation requests with dialogs in the same injector scope. */
class TumUiConfirmationService {
    requests = signal(new Map(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "requests" }] : /* istanbul ignore next */ []));
    request(key) {
        return this.requests().get(key);
    }
    confirm(request) {
        const next = new Map(this.requests());
        next.set(request.key, request);
        this.requests.set(next);
    }
    close(key) {
        const next = new Map(this.requests());
        next.delete(key);
        this.requests.set(next);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiConfirmationService, deps: [], target: i0.ɵɵFactoryTarget.Injectable });
    static ɵprov = i0.ɵɵngDeclareInjectable({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiConfirmationService });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiConfirmationService, decorators: [{
            type: Injectable
        }] });

let nextConfirmDialogId = 0;
/** Renders requests from the nearest `TumUiConfirmationService` as modal decisions. */
class TumUiConfirmDialogComponent {
    /** Static key used to select this dialog's confirmation requests. */
    key = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "key" }] : /* istanbul ignore next */ []));
    confirmationService = inject(TumUiConfirmationService);
    messageId = `tum-ui-confirm-dialog-message-${nextConfirmDialogId++}`;
    request = computed(() => this.confirmationService.request(this.key()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "request" }] : /* istanbul ignore next */ []));
    visible = computed(() => this.request() !== undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "visible" }] : /* istanbul ignore next */ []));
    accept() {
        const request = this.request();
        // Clear before running the callback's side effects so the dialog is already closing.
        this.confirmationService.close(this.key());
        request?.accept();
    }
    reject() {
        const request = this.request();
        this.confirmationService.close(this.key());
        request?.reject?.();
    }
    onDialogHide() {
        const request = this.request();
        if (request) {
            this.confirmationService.close(this.key());
            request.reject?.();
        }
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiConfirmDialogComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiConfirmDialogComponent, isStandalone: true, selector: "tum-ui-confirm-dialog", inputs: { key: { classPropertyName: "key", publicName: "key", isSignal: true, isRequired: false, transformFunction: null } }, ngImport: i0, template: "@if (request(); as req) {\n    <tum-ui-dialog [visible]=\"visible()\" [closable]=\"true\" [header]=\"req.header\" [role]=\"'alertdialog'\" [ariaDescribedBy]=\"messageId\" (hidden)=\"onDialogHide()\">\n        <div class=\"tum:flex tum:items-center tum:gap-4\">\n            @if (req.icon) {\n                <fa-icon [icon]=\"req.icon\" class=\"tum:size-8 tum:shrink-0 tum:text-2xl tum:text-muted\" />\n            }\n            <span [id]=\"messageId\" class=\"tum-ui-confirm-dialog-message\">{{ req.message }}</span>\n        </div>\n        <ng-template #footer>\n            <tum-ui-button [severity]=\"req.rejectSeverity ?? 'secondary'\" (clicked)=\"reject()\">{{ req.rejectLabel }}</tum-ui-button>\n            <tum-ui-button [severity]=\"req.acceptSeverity ?? 'primary'\" (clicked)=\"accept()\">{{ req.acceptLabel }}</tum-ui-button>\n        </ng-template>\n    </tum-ui-dialog>\n}\n", dependencies: [{ kind: "component", type: TumUiDialogComponent, selector: "tum-ui-dialog", inputs: ["visible", "header", "showHeader", "closable", "closeOnEscape", "dismissableMask", "size", "ariaLabel", "closeButtonAriaLabel", "role", "ariaDescribedBy"], outputs: ["visibleChange", "shown", "hidden"] }, { kind: "component", type: TumUiButtonComponent, selector: "tum-ui-button", inputs: ["severity", "size", "variant", "disabled", "rounded", "loading", "icon", "type", "ariaLabel", "ariaExpanded", "ariaPressed", "ariaControls", "ariaDescribedBy"], outputs: ["clicked"] }, { kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiConfirmDialogComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-confirm-dialog', imports: [TumUiDialogComponent, TumUiButtonComponent, FaIconComponent], changeDetection: ChangeDetectionStrategy.OnPush, template: "@if (request(); as req) {\n    <tum-ui-dialog [visible]=\"visible()\" [closable]=\"true\" [header]=\"req.header\" [role]=\"'alertdialog'\" [ariaDescribedBy]=\"messageId\" (hidden)=\"onDialogHide()\">\n        <div class=\"tum:flex tum:items-center tum:gap-4\">\n            @if (req.icon) {\n                <fa-icon [icon]=\"req.icon\" class=\"tum:size-8 tum:shrink-0 tum:text-2xl tum:text-muted\" />\n            }\n            <span [id]=\"messageId\" class=\"tum-ui-confirm-dialog-message\">{{ req.message }}</span>\n        </div>\n        <ng-template #footer>\n            <tum-ui-button [severity]=\"req.rejectSeverity ?? 'secondary'\" (clicked)=\"reject()\">{{ req.rejectLabel }}</tum-ui-button>\n            <tum-ui-button [severity]=\"req.acceptSeverity ?? 'primary'\" (clicked)=\"accept()\">{{ req.acceptLabel }}</tum-ui-button>\n        </ng-template>\n    </tum-ui-dialog>\n}\n" }]
        }], propDecorators: { key: [{ type: i0.Input, args: [{ isSignal: true, alias: "key", required: false }] }] } });

const ARROW_BASE = 'tum:absolute tum:h-2 tum:w-2 tum:rotate-45 tum:bg-tooltip-background';
const ARROW_POSITION = {
    top: 'tum:left-1/2 tum:top-full tum:-translate-x-1/2 tum:-translate-y-1/2',
    bottom: 'tum:left-1/2 tum:bottom-full tum:-translate-x-1/2 tum:translate-y-1/2',
    left: 'tum:top-1/2 tum:left-full tum:-translate-y-1/2 tum:-translate-x-1/2',
    right: 'tum:top-1/2 tum:right-full tum:-translate-y-1/2 tum:translate-x-1/2',
};
class TumUiTooltipContentComponent {
    text = input('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "text" }] : /* istanbul ignore next */ []));
    id = input('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "id" }] : /* istanbul ignore next */ []));
    placement = input('top', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "placement" }] : /* istanbul ignore next */ []));
    arrowClasses = computed(() => `${ARROW_BASE} ${ARROW_POSITION[this.placement()]}`, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "arrowClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTooltipContentComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiTooltipContentComponent, isStandalone: true, selector: "tum-ui-tooltip-content", inputs: { text: { classPropertyName: "text", publicName: "text", isSignal: true, isRequired: false, transformFunction: null }, id: { classPropertyName: "id", publicName: "id", isSignal: true, isRequired: false, transformFunction: null }, placement: { classPropertyName: "placement", publicName: "placement", isSignal: true, isRequired: false, transformFunction: null } }, host: { attributes: { "role": "tooltip" }, properties: { "attr.id": "id()" }, classAttribute: "tum-ui-tooltip-bubble tum:relative tum:inline-block tum:max-w-50 tum:rounded-md tum:bg-tooltip-background tum:px-3 tum:py-2 tum:text-sm tum:text-tooltip tum:shadow-md" }, ngImport: i0, template: `{{ text() }}<span aria-hidden="true" [class]="arrowClasses()"></span>`, isInline: true, changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTooltipContentComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'tum-ui-tooltip-content',
                    template: `{{ text() }}<span aria-hidden="true" [class]="arrowClasses()"></span>`,
                    host: {
                        role: 'tooltip',
                        '[attr.id]': 'id()',
                        class: 'tum-ui-tooltip-bubble tum:relative tum:inline-block tum:max-w-50 tum:rounded-md tum:bg-tooltip-background tum:px-3 tum:py-2 tum:text-sm tum:text-tooltip tum:shadow-md',
                    },
                    changeDetection: ChangeDetectionStrategy.OnPush,
                }]
        }], propDecorators: { text: [{ type: i0.Input, args: [{ isSignal: true, alias: "text", required: false }] }], id: [{ type: i0.Input, args: [{ isSignal: true, alias: "id", required: false }] }], placement: [{ type: i0.Input, args: [{ isSignal: true, alias: "placement", required: false }] }] } });

let nextTooltipId = 0;
/** Tooltip shown on hover or focus and associated with its host through `aria-describedby`. */
class TumUiTooltipDirective {
    overlayService = inject(TumUiOverlayService);
    elementRef = inject(ElementRef);
    content = input.required({ ...(ngDevMode ? { debugName: "content" } : /* istanbul ignore next */ {}), alias: 'tumUiTooltip' });
    placement = input('top', { ...(ngDevMode ? { debugName: "placement" } : /* istanbul ignore next */ {}), alias: 'tumUiTooltipPlacement' });
    showDelayMs = input(150, { ...(ngDevMode ? { debugName: "showDelayMs" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    hideDelayMs = input(100, { ...(ngDevMode ? { debugName: "hideDelayMs" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    overlayRef;
    contentRef;
    positionSub;
    showTimer;
    hideTimer;
    tooltipId = `tum-ui-tooltip-${nextTooltipId++}`;
    interactionSub;
    triggerHovered = false;
    tooltipHovered = false;
    focused = false;
    constructor() {
        effect(() => {
            // Read content unconditionally so changes remain tracked while the tooltip is hidden.
            const text = this.content();
            if (!text) {
                this.hideNow();
            }
            else {
                this.contentRef?.setInput('text', text);
            }
        });
    }
    onHoverStart() {
        this.triggerHovered = true;
        this.scheduleShow();
    }
    onHoverEnd() {
        this.triggerHovered = false;
        this.scheduleHideIfInactive();
    }
    onFocusStart() {
        this.focused = true;
        this.scheduleShow();
    }
    onFocusEnd() {
        this.focused = false;
        this.scheduleHideIfInactive();
    }
    scheduleHideIfInactive() {
        if (this.triggerHovered || this.tooltipHovered || this.focused) {
            return;
        }
        this.scheduleHide();
    }
    scheduleShow() {
        clearTimeout(this.hideTimer);
        clearTimeout(this.showTimer);
        if (this.overlayRef?.hasAttached() || !this.content()) {
            return;
        }
        this.showTimer = setTimeout(() => this.show(), this.showDelayMs());
    }
    scheduleHide() {
        clearTimeout(this.showTimer);
        clearTimeout(this.hideTimer);
        this.hideTimer = setTimeout(() => this.hideNow(), this.hideDelayMs());
    }
    hideNow() {
        clearTimeout(this.showTimer);
        clearTimeout(this.hideTimer);
        this.removeDescribedBy();
        this.positionSub?.unsubscribe();
        this.positionSub = undefined;
        this.interactionSub?.unsubscribe();
        this.interactionSub = undefined;
        this.tooltipHovered = false;
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.contentRef = undefined;
    }
    show() {
        if (this.overlayRef?.hasAttached()) {
            return;
        }
        this.overlayRef = this.overlayService.createConnectedOverlay(this.elementRef, this.placement());
        const strategy = this.overlayRef.getConfig().positionStrategy;
        let appliedPlacement = this.placement();
        // CDK may emit the initial flipped position synchronously during attachment.
        this.positionSub = strategy.positionChanges.subscribe((change) => {
            appliedPlacement = this.overlayService.placementFromPosition(change.connectionPair);
            this.contentRef?.setInput('placement', appliedPlacement);
        });
        this.contentRef = this.overlayRef.attach(new ComponentPortal(TumUiTooltipContentComponent));
        this.contentRef.setInput('text', this.content());
        this.contentRef.setInput('id', this.tooltipId);
        this.contentRef.setInput('placement', appliedPlacement);
        const contentElement = this.contentRef.location.nativeElement;
        this.interactionSub = new Subscription();
        this.interactionSub.add(fromEvent(contentElement, 'mouseenter').subscribe(() => {
            this.tooltipHovered = true;
            clearTimeout(this.hideTimer);
        }));
        this.interactionSub.add(fromEvent(contentElement, 'mouseleave').subscribe(() => {
            this.tooltipHovered = false;
            this.scheduleHideIfInactive();
        }));
        this.interactionSub.add(this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                this.hideNow();
            }
        }));
        this.addDescribedBy();
    }
    addDescribedBy() {
        const host = this.elementRef.nativeElement;
        const tokens = (host.getAttribute('aria-describedby') ?? '').split(' ').filter(Boolean);
        if (!tokens.includes(this.tooltipId)) {
            tokens.push(this.tooltipId);
        }
        host.setAttribute('aria-describedby', tokens.join(' '));
    }
    removeDescribedBy() {
        const host = this.elementRef.nativeElement;
        const tokens = (host.getAttribute('aria-describedby') ?? '').split(' ').filter((token) => token && token !== this.tooltipId);
        if (tokens.length > 0) {
            host.setAttribute('aria-describedby', tokens.join(' '));
        }
        else {
            host.removeAttribute('aria-describedby');
        }
    }
    ngOnDestroy() {
        this.hideNow();
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTooltipDirective, deps: [], target: i0.ɵɵFactoryTarget.Directive });
    static ɵdir = i0.ɵɵngDeclareDirective({ minVersion: "17.1.0", version: "22.1.5", type: TumUiTooltipDirective, isStandalone: true, selector: "[tumUiTooltip]", inputs: { content: { classPropertyName: "content", publicName: "tumUiTooltip", isSignal: true, isRequired: true, transformFunction: null }, placement: { classPropertyName: "placement", publicName: "tumUiTooltipPlacement", isSignal: true, isRequired: false, transformFunction: null }, showDelayMs: { classPropertyName: "showDelayMs", publicName: "showDelayMs", isSignal: true, isRequired: false, transformFunction: null }, hideDelayMs: { classPropertyName: "hideDelayMs", publicName: "hideDelayMs", isSignal: true, isRequired: false, transformFunction: null } }, host: { listeners: { "mouseenter": "onHoverStart()", "mouseleave": "onHoverEnd()", "focusin": "onFocusStart()", "focusout": "onFocusEnd()", "keydown.escape": "hideNow()" } }, ngImport: i0 });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTooltipDirective, decorators: [{
            type: Directive,
            args: [{
                    selector: '[tumUiTooltip]',
                    host: {
                        '(mouseenter)': 'onHoverStart()',
                        '(mouseleave)': 'onHoverEnd()',
                        '(focusin)': 'onFocusStart()',
                        '(focusout)': 'onFocusEnd()',
                        '(keydown.escape)': 'hideNow()',
                    },
                }]
        }], ctorParameters: () => [], propDecorators: { content: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiTooltip", required: true }] }], placement: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiTooltipPlacement", required: false }] }], showDelayMs: [{ type: i0.Input, args: [{ isSignal: true, alias: "showDelayMs", required: false }] }], hideDelayMs: [{ type: i0.Input, args: [{ isSignal: true, alias: "hideDelayMs", required: false }] }] } });

dayjs.extend(customParseFormat);
const DISPLAY_FORMAT = 'DD.MM.YYYY HH:mm';
const TIME_ONLY_FORMAT = 'HH:mm';
const DISPLAY_REGEX = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;
const TIME_REGEX = /^([01]\d|2[0-3]):[0-5]\d$/;
/** The text format one picker reads and writes: a time on its own, or a full date and time. */
function displayFormat(timeOnly) {
    return timeOnly ? TIME_ONLY_FORMAT : DISPLAY_FORMAT;
}
/** Whether `text` is shaped like a complete entry for that format, used to flag an incomplete one on blur. */
function matchesDisplayFormat(text, timeOnly) {
    return timeOnly ? TIME_REGEX.test(text) : DISPLAY_REGEX.test(text);
}
/**
 * Parses text in the picker's format. A time on its own carries no date, so it is placed on `onDate` - the
 * value already held, or today - which keeps the date stable while only the time is edited.
 */
function parseDisplay(text, timeOnly = false, onDate) {
    const trimmed = text.trim();
    if (!trimmed) {
        return undefined;
    }
    const parsed = dayjs(trimmed, displayFormat(timeOnly), true);
    if (!parsed.isValid()) {
        return undefined;
    }
    if (!timeOnly) {
        return parsed;
    }
    return combineDateAndTime(onDate ?? dayjs(), parsed);
}
function formatDisplay(value, timeOnly = false) {
    return value.format(displayFormat(timeOnly));
}
function buildMonthMatrix(month) {
    const startOfMonth = month.startOf('month');
    const offset = (startOfMonth.day() + 6) % 7;
    let cursor = startOfMonth.subtract(offset, 'day').startOf('day');
    const weeks = [];
    for (let week = 0; week < 6; week++) {
        const days = [];
        for (let day = 0; day < 7; day++) {
            days.push(cursor);
            cursor = cursor.add(1, 'day');
        }
        weeks.push(days);
    }
    return weeks;
}
function combineDateAndTime(date, time) {
    return date.hour(time.hour()).minute(time.minute()).second(0).millisecond(0);
}
function valuesEqual(a, b) {
    if (!a && !b) {
        return true;
    }
    if (!a || !b) {
        return false;
    }
    return a.isSame(b, 'minute');
}

class TumUiCalendarComponent {
    translator = inject(TUM_UI_TRANSLATOR);
    directionality = inject(Directionality);
    direction = signal(this.directionality.value, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "direction" }] : /* istanbul ignore next */ []));
    destroyRef = inject(DestroyRef);
    selected = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "selected" }] : /* istanbul ignore next */ []));
    activeMonth = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "activeMonth" }] : /* istanbul ignore next */ []));
    focusOnInit = input(false, { ...(ngDevMode ? { debugName: "focusOnInit" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    daySelected = output();
    monthChange = output();
    previousMonthIcon = computed(() => (this.direction() === 'rtl' ? faChevronRight : faChevronLeft), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "previousMonthIcon" }] : /* istanbul ignore next */ []));
    nextMonthIcon = computed(() => (this.direction() === 'rtl' ? faChevronLeft : faChevronRight), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "nextMonthIcon" }] : /* istanbul ignore next */ []));
    weeks = computed(() => buildMonthMatrix(this.activeMonth()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "weeks" }] : /* istanbul ignore next */ []));
    flatDays = computed(() => this.weeks().flat(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "flatDays" }] : /* istanbul ignore next */ []));
    weekdayLabels = computed(() => this.weeks()[0].map((day) => this.formatDate(day, { weekday: 'short' })), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "weekdayLabels" }] : /* istanbul ignore next */ []));
    weekdayFullLabels = computed(() => this.weeks()[0].map((day) => this.formatDate(day, { weekday: 'long' })), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "weekdayFullLabels" }] : /* istanbul ignore next */ []));
    monthLabel = computed(() => this.formatDate(this.activeMonth(), { month: 'long', year: 'numeric' }), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "monthLabel" }] : /* istanbul ignore next */ []));
    previousMonthLabel = computed(() => this.translate('tumUi.datePicker.previousMonth', { month: this.formatDate(this.activeMonth().subtract(1, 'month'), { month: 'long', year: 'numeric' }) }), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "previousMonthLabel" }] : /* istanbul ignore next */ []));
    nextMonthLabel = computed(() => this.translate('tumUi.datePicker.nextMonth', { month: this.formatDate(this.activeMonth().add(1, 'month'), { month: 'long', year: 'numeric' }) }), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "nextMonthLabel" }] : /* istanbul ignore next */ []));
    focusedDate = linkedSignal({ ...(ngDevMode ? { debugName: "focusedDate" } : /* istanbul ignore next */ {}), source: () => ({ month: this.activeMonth(), selected: this.selected() }),
        computation: ({ month, selected }, previous) => {
            const previousSelected = previous?.source.selected;
            if (selected && (!previousSelected || !selected.isSame(previousSelected, 'day'))) {
                return selected;
            }
            if (previous) {
                return month.date(Math.min(previous.value.date(), month.daysInMonth()));
            }
            const today = dayjs();
            return today.isSame(month, 'month') ? today : month.startOf('month');
        } });
    focusedIndex = computed(() => {
        const days = this.flatDays();
        const index = days.findIndex((day) => day.isSame(this.focusedDate(), 'day'));
        return index >= 0 ? index : 0;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "focusedIndex" }] : /* istanbul ignore next */ []));
    today = dayjs();
    dayButtons = viewChildren('dayButton', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "dayButtons" }] : /* istanbul ignore next */ []));
    focusedOnInit = false;
    restoreFocusAfterRender = false;
    constructor() {
        const directionChanges = this.directionality.change.subscribe((direction) => this.direction.set(direction));
        this.destroyRef.onDestroy(() => directionChanges.unsubscribe());
        afterRenderEffect(() => {
            this.flatDays();
            if (this.focusOnInit() && !this.focusedOnInit) {
                const initialButton = this.dayButtons()[this.focusedIndex()]?.nativeElement;
                if (initialButton) {
                    this.focusedOnInit = true;
                    initialButton.focus();
                }
            }
            if (this.restoreFocusAfterRender) {
                this.restoreFocusAfterRender = false;
                this.dayButtons()[this.focusedIndex()]?.nativeElement.focus();
            }
        });
    }
    dayButtonClasses(day) {
        const base = 'tum:appearance-none tum:border-0 tum:h-8 tum:w-8 tum:rounded-full tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus';
        let color;
        if (this.isSelected(day)) {
            color = 'tum:bg-primary tum:text-primary-contrast';
        }
        else if (this.isOtherMonth(day)) {
            color = 'tum:bg-transparent tum:text-muted tum:hover:bg-hover-background';
        }
        else {
            color = 'tum:bg-transparent tum:text-text tum:hover:bg-hover-background';
        }
        const today = this.isToday(day) && !this.isSelected(day) ? 'tum:ring-1 tum:ring-primary' : '';
        return `${base} ${color} ${today}`.trim();
    }
    isSelected(day) {
        const selected = this.selected();
        return !!selected && selected.isSame(day, 'day');
    }
    isToday(day) {
        return day.isSame(this.today, 'day');
    }
    isOtherMonth(day) {
        return day.month() !== this.activeMonth().month();
    }
    previousMonth() {
        this.monthChange.emit(this.activeMonth().subtract(1, 'month'));
    }
    nextMonth() {
        this.monthChange.emit(this.activeMonth().add(1, 'month'));
    }
    selectDay(day) {
        this.daySelected.emit(day);
    }
    dayLabel(day) {
        return this.formatDate(day, { dateStyle: 'full' });
    }
    formatDate(day, options) {
        this.translator.translationChanges?.();
        return new Intl.DateTimeFormat(this.translator.locale?.(), options).format(day.toDate());
    }
    translate(key, params) {
        this.translator.translationChanges?.();
        return this.translator.translate(key, params);
    }
    onKeydown(event, index) {
        const total = this.flatDays().length;
        const moveTo = (target) => {
            event.preventDefault();
            if (target >= 0 && target < total) {
                this.focusedDate.set(this.flatDays()[target]);
                this.dayButtons()[target]?.nativeElement.focus();
                return;
            }
            const targetDay = this.flatDays()[index].add(target - index, 'day');
            this.focusedDate.set(targetDay);
            this.restoreFocusAfterRender = true;
            this.monthChange.emit(targetDay.startOf('month'));
        };
        switch (event.key) {
            case 'ArrowRight':
                moveTo(index + 1);
                break;
            case 'ArrowLeft':
                moveTo(index - 1);
                break;
            case 'ArrowDown':
                moveTo(index + 7);
                break;
            case 'ArrowUp':
                moveTo(index - 7);
                break;
            case 'Home':
                moveTo(index - (index % 7));
                break;
            case 'End':
                moveTo(index - (index % 7) + 6);
                break;
            case 'Enter':
            case ' ': {
                event.preventDefault();
                const day = this.flatDays()[index];
                if (this.isOtherMonth(day)) {
                    this.restoreFocusAfterRender = true;
                }
                this.selectDay(day);
                break;
            }
            case 'PageUp':
                event.preventDefault();
                this.restoreFocusAfterRender = true;
                this.monthChange.emit(this.activeMonth()
                    .subtract(event.shiftKey ? 1 : 0, 'year')
                    .subtract(event.shiftKey ? 0 : 1, 'month'));
                break;
            case 'PageDown':
                event.preventDefault();
                this.restoreFocusAfterRender = true;
                this.monthChange.emit(this.activeMonth()
                    .add(event.shiftKey ? 1 : 0, 'year')
                    .add(event.shiftKey ? 0 : 1, 'month'));
                break;
        }
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiCalendarComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiCalendarComponent, isStandalone: true, selector: "tum-ui-calendar", inputs: { selected: { classPropertyName: "selected", publicName: "selected", isSignal: true, isRequired: false, transformFunction: null }, activeMonth: { classPropertyName: "activeMonth", publicName: "activeMonth", isSignal: true, isRequired: true, transformFunction: null }, focusOnInit: { classPropertyName: "focusOnInit", publicName: "focusOnInit", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { daySelected: "daySelected", monthChange: "monthChange" }, viewQueries: [{ propertyName: "dayButtons", predicate: ["dayButton"], descendants: true, isSignal: true }], ngImport: i0, template: "<div class=\"tum:w-72 tum:select-none\">\n    <div class=\"tum:mb-2 tum:flex tum:items-center tum:justify-between\">\n        <button\n            type=\"button\"\n            class=\"tum:appearance-none tum:rounded tum:border-0 tum:bg-transparent tum:p-1 tum:text-text tum:hover:bg-hover-background\"\n            (click)=\"previousMonth()\"\n            [attr.aria-label]=\"previousMonthLabel()\"\n        >\n            <fa-icon [icon]=\"previousMonthIcon()\" />\n        </button>\n        <span class=\"tum:font-semibold tum:text-text\" aria-live=\"polite\">{{ monthLabel() }}</span>\n        <button\n            type=\"button\"\n            class=\"tum:appearance-none tum:rounded tum:border-0 tum:bg-transparent tum:p-1 tum:text-text tum:hover:bg-hover-background\"\n            (click)=\"nextMonth()\"\n            [attr.aria-label]=\"nextMonthLabel()\"\n        >\n            <fa-icon [icon]=\"nextMonthIcon()\" />\n        </button>\n    </div>\n    <table role=\"grid\" [attr.aria-label]=\"monthLabel()\" class=\"tum:w-full tum:border-collapse tum:text-center tum:text-sm\">\n        <thead>\n            <tr>\n                @for (label of weekdayLabels(); track $index) {\n                    <th scope=\"col\" [attr.aria-label]=\"weekdayFullLabels()[$index]\" class=\"tum:p-1 tum:font-medium tum:text-muted\">{{ label }}</th>\n                }\n            </tr>\n        </thead>\n        <tbody>\n            @for (week of weeks(); track $index; let w = $index) {\n                <tr>\n                    @for (day of week; track day.valueOf(); let d = $index) {\n                        <td class=\"tum:p-0.5\" role=\"gridcell\" [attr.aria-selected]=\"isSelected(day) ? 'true' : null\">\n                            <button\n                                #dayButton\n                                type=\"button\"\n                                [class]=\"dayButtonClasses(day)\"\n                                [attr.tabindex]=\"focusedIndex() === w * 7 + d ? 0 : -1\"\n                                [attr.aria-label]=\"dayLabel(day)\"\n                                (click)=\"selectDay(day)\"\n                                (keydown)=\"onKeydown($event, w * 7 + d)\"\n                            >\n                                {{ day.date() }}\n                            </button>\n                        </td>\n                    }\n                </tr>\n            }\n        </tbody>\n    </table>\n</div>\n", dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiCalendarComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-calendar', imports: [FaIconComponent], changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum:w-72 tum:select-none\">\n    <div class=\"tum:mb-2 tum:flex tum:items-center tum:justify-between\">\n        <button\n            type=\"button\"\n            class=\"tum:appearance-none tum:rounded tum:border-0 tum:bg-transparent tum:p-1 tum:text-text tum:hover:bg-hover-background\"\n            (click)=\"previousMonth()\"\n            [attr.aria-label]=\"previousMonthLabel()\"\n        >\n            <fa-icon [icon]=\"previousMonthIcon()\" />\n        </button>\n        <span class=\"tum:font-semibold tum:text-text\" aria-live=\"polite\">{{ monthLabel() }}</span>\n        <button\n            type=\"button\"\n            class=\"tum:appearance-none tum:rounded tum:border-0 tum:bg-transparent tum:p-1 tum:text-text tum:hover:bg-hover-background\"\n            (click)=\"nextMonth()\"\n            [attr.aria-label]=\"nextMonthLabel()\"\n        >\n            <fa-icon [icon]=\"nextMonthIcon()\" />\n        </button>\n    </div>\n    <table role=\"grid\" [attr.aria-label]=\"monthLabel()\" class=\"tum:w-full tum:border-collapse tum:text-center tum:text-sm\">\n        <thead>\n            <tr>\n                @for (label of weekdayLabels(); track $index) {\n                    <th scope=\"col\" [attr.aria-label]=\"weekdayFullLabels()[$index]\" class=\"tum:p-1 tum:font-medium tum:text-muted\">{{ label }}</th>\n                }\n            </tr>\n        </thead>\n        <tbody>\n            @for (week of weeks(); track $index; let w = $index) {\n                <tr>\n                    @for (day of week; track day.valueOf(); let d = $index) {\n                        <td class=\"tum:p-0.5\" role=\"gridcell\" [attr.aria-selected]=\"isSelected(day) ? 'true' : null\">\n                            <button\n                                #dayButton\n                                type=\"button\"\n                                [class]=\"dayButtonClasses(day)\"\n                                [attr.tabindex]=\"focusedIndex() === w * 7 + d ? 0 : -1\"\n                                [attr.aria-label]=\"dayLabel(day)\"\n                                (click)=\"selectDay(day)\"\n                                (keydown)=\"onKeydown($event, w * 7 + d)\"\n                            >\n                                {{ day.date() }}\n                            </button>\n                        </td>\n                    }\n                </tr>\n            }\n        </tbody>\n    </table>\n</div>\n" }]
        }], ctorParameters: () => [], propDecorators: { selected: [{ type: i0.Input, args: [{ isSignal: true, alias: "selected", required: false }] }], activeMonth: [{ type: i0.Input, args: [{ isSignal: true, alias: "activeMonth", required: true }] }], focusOnInit: [{ type: i0.Input, args: [{ isSignal: true, alias: "focusOnInit", required: false }] }], daySelected: [{ type: i0.Output, args: ["daySelected"] }], monthChange: [{ type: i0.Output, args: ["monthChange"] }], dayButtons: [{ type: i0.ViewChildren, args: ['dayButton', { isSignal: true }] }] } });

let nextDatePickerId = 0;
/** Date-and-time field with typed input and an accessible calendar dialog; `timeOnly` reduces it to a time. */
class TumUiDatePickerComponent {
    overlayService = inject(TumUiOverlayService);
    viewContainerRef = inject(ViewContainerRef);
    destroyRef = inject(DestroyRef);
    document = inject(DOCUMENT);
    /**
     * Last committed date. Invalid text remains visible without updating it.
     * Observe `inputValidityChange` when validity must react to uncommitted text.
     */
    value = model(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "value" }] : /* istanbul ignore next */ []));
    /** Adds an external validation error without discarding the last committed value. */
    invalid = input(false, { ...(ngDevMode ? { debugName: "invalid" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Hides the visible label while retaining the input's accessible name. */
    hideLabelName = input(false, { ...(ngDevMode ? { debugName: "hideLabelName" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Hides the built-in validation message without changing validity or `aria-invalid`. */
    hideValidationMessage = input(false, { ...(ngDevMode ? { debugName: "hideValidationMessage" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Shows the browser time-zone indicator beside the label. */
    shouldDisplayTimeZoneWarning = input(true, { ...(ngDevMode ? { debugName: "shouldDisplayTimeZoneWarning" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /**
     * Reduces the field to a time: the text is `HH:mm`, the dialog drops the calendar, and only the clock is
     * shown. The value stays a full Dayjs — the date is carried over from the value already held, or is
     * today — so a caller that only cares about the time can read it and one that needs a moment still gets a
     * complete one.
     */
    timeOnly = input(false, { ...(ngDevMode ? { debugName: "timeOnly" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** ID used to associate the input, label, validation message, and dialog. */
    inputId = input(`tum-ui-date-picker-${nextDatePickerId++}`, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "inputId" }] : /* istanbul ignore next */ []));
    /** Visible label text or package translation key. */
    labelName = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "labelName" }] : /* istanbul ignore next */ []));
    /** Accessible name used when no visible label is rendered. */
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    /** Emits text-input validity independently of the external `invalid` state. */
    inputValidityChange = output();
    /** Emits when the text input loses focus. */
    touch = output();
    faCalendar = faCalendar;
    faXmark = faXmark;
    faGlobe = faGlobe;
    faClock = faClock;
    faChevronUp = faChevronUp;
    faChevronDown = faChevronDown;
    get currentTimeZone() {
        return Intl.DateTimeFormat().resolvedOptions().timeZone;
    }
    // Equivalent Dayjs instances must not reset uncommitted text; key linked state by its displayed value.
    valueKey = computed(() => {
        const current = this.value();
        return current ? formatDisplay(current, this.timeOnly()) : '';
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "valueKey" }] : /* istanbul ignore next */ []));
    isInputValid = linkedSignal(() => {
        this.valueKey();
        return true;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isInputValid" }] : /* istanbul ignore next */ []));
    isOpen = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isOpen" }] : /* istanbul ignore next */ []));
    panelId = computed(() => `${this.inputId()}-dialog`, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "panelId" }] : /* istanbul ignore next */ []));
    activeMonth = signal(dayjs().startOf('month'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "activeMonth" }] : /* istanbul ignore next */ []));
    timeText = signal('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "timeText" }] : /* istanbul ignore next */ []));
    inputText = linkedSignal(() => this.valueKey(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "inputText" }] : /* istanbul ignore next */ []));
    panel = viewChild.required('panel', { ...(ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {}), read: TemplateRef });
    dateInput = viewChild.required('dateInput', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "dateInput" }] : /* istanbul ignore next */ []));
    triggerWrapper = viewChild.required('triggerWrapper', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "triggerWrapper" }] : /* istanbul ignore next */ []));
    hourField = viewChild('hourInput', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hourField" }] : /* istanbul ignore next */ []));
    overlayRef;
    restoreFocusElement;
    pendingHourFocus = false;
    showErrorBorder = computed(() => this.invalid() || !this.isInputValid(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "showErrorBorder" }] : /* istanbul ignore next */ []));
    placeholderKey = computed(() => (this.timeOnly() ? 'tumUi.datePicker.timePlaceholder' : 'tumUi.datePicker.placeholder'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "placeholderKey" }] : /* istanbul ignore next */ []));
    dialogLabelKey = computed(() => (this.timeOnly() ? 'tumUi.datePicker.timeDialog' : 'tumUi.datePicker.dialog'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "dialogLabelKey" }] : /* istanbul ignore next */ []));
    invalidMessageKey = computed(() => (this.timeOnly() ? 'tumUi.datePicker.invalidTime' : 'tumUi.datePicker.invalid'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "invalidMessageKey" }] : /* istanbul ignore next */ []));
    openLabelKey = computed(() => (this.timeOnly() ? 'tumUi.datePicker.openTime' : 'tumUi.datePicker.open'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "openLabelKey" }] : /* istanbul ignore next */ []));
    showClear = computed(() => !!this.inputText(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "showClear" }] : /* istanbul ignore next */ []));
    displayHour = computed(() => (TIME_REGEX.test(this.timeText()) ? this.timeText().split(':')[0] : '00'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "displayHour" }] : /* istanbul ignore next */ []));
    displayMinute = computed(() => (TIME_REGEX.test(this.timeText()) ? this.timeText().split(':')[1] : '00'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "displayMinute" }] : /* istanbul ignore next */ []));
    constructor() {
        this.destroyRef.onDestroy(() => this.overlayRef?.dispose());
        effect(() => this.inputValidityChange.emit(this.isInputValid()));
        effect(() => {
            if (this.disabled()) {
                this.close();
            }
        });
        // A time-only dialog has no calendar to take focus on open, and a modal dialog the user is not inside
        // cannot be reached with a keyboard or a screen reader. The hour is where the editing starts.
        afterRenderEffect(() => {
            const field = this.hourField()?.nativeElement;
            if (field && this.pendingHourFocus) {
                this.pendingHourFocus = false;
                field.focus();
            }
        });
    }
    /** Whether the entered text parses successfully and the external `invalid` state is clear. */
    isValid = computed(() => !this.invalid() && this.isInputValid(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isValid" }] : /* istanbul ignore next */ []));
    onInput(raw) {
        this.inputText.set(raw);
        const parsed = parseDisplay(raw, this.timeOnly(), this.value());
        if (parsed) {
            this.commit(parsed);
        }
        else if (!raw.trim()) {
            if (this.value() !== undefined) {
                this.value.set(undefined);
            }
            else {
                this.isInputValid.set(true);
            }
        }
        else {
            this.isInputValid.set(false);
        }
    }
    onBlur(raw) {
        const trimmed = raw.trim();
        if (trimmed && !matchesDisplayFormat(trimmed, this.timeOnly())) {
            this.isInputValid.set(false);
        }
        this.touch.emit();
    }
    stepHour(delta) {
        const { hour, minute } = this.currentTimeParts();
        this.commitTime((hour + delta + 24) % 24, minute);
    }
    stepMinute(delta) {
        const { hour, minute } = this.currentTimeParts();
        this.commitTime(hour, (minute + delta + 60) % 60);
    }
    onHourInput(input) {
        const parsed = this.parseTimePart(input.value, 23);
        if (parsed === undefined) {
            input.value = this.displayHour();
            return;
        }
        this.commitTime(parsed, this.currentTimeParts().minute);
        input.value = this.displayHour();
    }
    onMinuteInput(input) {
        const parsed = this.parseTimePart(input.value, 59);
        if (parsed === undefined) {
            input.value = this.displayMinute();
            return;
        }
        this.commitTime(this.currentTimeParts().hour, parsed);
        input.value = this.displayMinute();
    }
    onTimeKeydown(event, input, field) {
        const delta = event.key === 'ArrowUp' ? 1 : event.key === 'ArrowDown' ? -1 : 0;
        if (delta === 0) {
            return;
        }
        event.preventDefault();
        const { hour, minute } = this.currentTimeParts();
        if (field === 'hour') {
            const base = this.parseTimePart(input.value, 23) ?? hour;
            this.commitTime((base + delta + 24) % 24, minute);
            input.value = this.displayHour();
        }
        else {
            const base = this.parseTimePart(input.value, 59) ?? minute;
            this.commitTime(hour, (base + delta + 60) % 60);
            input.value = this.displayMinute();
        }
    }
    currentTimeParts() {
        const text = this.timeText();
        if (!TIME_REGEX.test(text)) {
            return { hour: 0, minute: 0 };
        }
        const [hour, minute] = text.split(':').map(Number);
        return { hour, minute };
    }
    parseTimePart(raw, max) {
        const trimmed = raw.trim();
        if (!/^\d{1,2}$/.test(trimmed)) {
            return undefined;
        }
        const value = Number(trimmed);
        return value <= max ? value : undefined;
    }
    commitTime(hour, minute) {
        this.timeText.set(`${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`);
        const base = this.value() ?? dayjs().startOf('day');
        this.commit(base.hour(hour).minute(minute).second(0).millisecond(0));
    }
    onDaySelect(day) {
        const time = this.value() ?? dayjs().startOf('day');
        this.commit(combineDateAndTime(day, time));
    }
    clear() {
        this.isInputValid.set(true);
        this.inputText.set('');
        if (this.value() !== undefined) {
            this.value.set(undefined);
        }
        this.dateInput().nativeElement.focus();
    }
    toggle() {
        if (this.isOpen()) {
            this.close();
        }
        else {
            this.open();
        }
    }
    openFromInput(event) {
        event.preventDefault();
        this.open();
    }
    open() {
        if (this.isOpen() || this.disabled()) {
            return;
        }
        const anchor = this.value() ?? dayjs();
        this.activeMonth.set(anchor.startOf('month'));
        this.timeText.set(this.value()?.format('HH:mm') ?? '');
        const activeElement = this.document.activeElement;
        this.restoreFocusElement = activeElement && typeof activeElement.focus === 'function' ? activeElement : undefined;
        this.overlayRef = this.overlayService.createConnectedOverlay(this.triggerWrapper(), 'bottom', { hasBackdrop: true });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        this.overlayRef.backdropClick().subscribe(() => this.close());
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                this.close();
            }
        });
        this.pendingHourFocus = this.timeOnly();
        this.isOpen.set(true);
    }
    close() {
        if (!this.isOpen()) {
            return;
        }
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.isOpen.set(false);
        if (!this.disabled() && this.restoreFocusElement?.isConnected) {
            this.restoreFocusElement.focus();
        }
        this.restoreFocusElement = undefined;
    }
    focus(options) {
        this.dateInput().nativeElement.focus(options);
    }
    commit(next) {
        this.isInputValid.set(true);
        if (valuesEqual(this.value(), next)) {
            this.inputText.set(formatDisplay(next, this.timeOnly()));
            return;
        }
        this.activeMonth.set(next.startOf('month'));
        this.timeText.set(next.format('HH:mm'));
        this.value.set(next);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiDatePickerComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiDatePickerComponent, isStandalone: true, selector: "tum-ui-date-picker", inputs: { value: { classPropertyName: "value", publicName: "value", isSignal: true, isRequired: false, transformFunction: null }, invalid: { classPropertyName: "invalid", publicName: "invalid", isSignal: true, isRequired: false, transformFunction: null }, disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, hideLabelName: { classPropertyName: "hideLabelName", publicName: "hideLabelName", isSignal: true, isRequired: false, transformFunction: null }, hideValidationMessage: { classPropertyName: "hideValidationMessage", publicName: "hideValidationMessage", isSignal: true, isRequired: false, transformFunction: null }, shouldDisplayTimeZoneWarning: { classPropertyName: "shouldDisplayTimeZoneWarning", publicName: "shouldDisplayTimeZoneWarning", isSignal: true, isRequired: false, transformFunction: null }, timeOnly: { classPropertyName: "timeOnly", publicName: "timeOnly", isSignal: true, isRequired: false, transformFunction: null }, inputId: { classPropertyName: "inputId", publicName: "inputId", isSignal: true, isRequired: false, transformFunction: null }, labelName: { classPropertyName: "labelName", publicName: "labelName", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { value: "valueChange", inputValidityChange: "inputValidityChange", touch: "touch" }, host: { classAttribute: "tum-ui-date-picker" }, viewQueries: [{ propertyName: "panel", first: true, predicate: ["panel"], descendants: true, read: TemplateRef, isSignal: true }, { propertyName: "dateInput", first: true, predicate: ["dateInput"], descendants: true, isSignal: true }, { propertyName: "triggerWrapper", first: true, predicate: ["triggerWrapper"], descendants: true, isSignal: true }, { propertyName: "hourField", first: true, predicate: ["hourInput"], descendants: true, isSignal: true }], ngImport: i0, template: "<div class=\"tum:flex tum:flex-col tum:gap-1\">\n    @if ((!hideLabelName() && labelName()) || shouldDisplayTimeZoneWarning()) {\n        <div class=\"tum:flex tum:items-center tum:gap-1\">\n            @if (!hideLabelName() && labelName()) {\n                <label [attr.for]=\"inputId()\" class=\"tum:font-semibold tum:text-text\">{{ labelName()! | tumUiTranslate }}</label>\n            }\n            @if (shouldDisplayTimeZoneWarning()) {\n                <fa-stack\n                    class=\"tum:h-4 tum:w-4\"\n                    tabindex=\"0\"\n                    role=\"img\"\n                    [attr.aria-label]=\"'tumUi.datePicker.timeZoneWarning' | tumUiTranslate: { timeZone: currentTimeZone }\"\n                    [tumUiTooltip]=\"'tumUi.datePicker.timeZoneWarning' | tumUiTranslate: { timeZone: currentTimeZone }\"\n                >\n                    <fa-icon [icon]=\"faGlobe\" stackItemSize=\"1x\" class=\"tum:text-muted\" />\n                    <fa-icon [icon]=\"faClock\" stackItemSize=\"1x\" transform=\"shrink-6 down-5 right-5\" class=\"tum:text-muted\" />\n                </fa-stack>\n            }\n        </div>\n    }\n\n    <div #triggerWrapper class=\"tum:relative tum:flex tum:items-center\">\n        <input\n            #dateInput\n            [id]=\"inputId()\"\n            type=\"text\"\n            role=\"combobox\"\n            [value]=\"inputText()\"\n            [disabled]=\"disabled()\"\n            [placeholder]=\"placeholderKey() | tumUiTranslate\"\n            aria-haspopup=\"dialog\"\n            [attr.aria-expanded]=\"isOpen()\"\n            [attr.aria-controls]=\"isOpen() ? panelId() : null\"\n            [attr.aria-invalid]=\"showErrorBorder()\"\n            [attr.aria-label]=\"ariaLabel()\"\n            [attr.aria-describedby]=\"!hideValidationMessage() && showErrorBorder() ? inputId() + '-error' : null\"\n            (input)=\"onInput(dateInput.value)\"\n            (blur)=\"onBlur(dateInput.value)\"\n            (keydown.arrowdown)=\"openFromInput($event)\"\n            class=\"tum-ui-date-picker-input tum:box-border tum:w-full tum:rounded-md tum:border tum:bg-control-background tum:py-2 tum:ps-3 tum:pe-17 tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n            [class]=\"showErrorBorder() ? 'tum:border-state-danger' : 'tum:border-control-border'\"\n        />\n        @if (showClear() && !disabled()) {\n            <button\n                type=\"button\"\n                class=\"tum:absolute tum:end-9 tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text-hover\"\n                (click)=\"clear()\"\n                [attr.aria-label]=\"'tumUi.datePicker.clear' | tumUiTranslate\"\n            >\n                <fa-icon [icon]=\"faXmark\" />\n            </button>\n        }\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            class=\"tum:absolute tum:end-2 tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-accent\"\n            [disabled]=\"disabled()\"\n            (click)=\"toggle()\"\n            [attr.aria-label]=\"openLabelKey() | tumUiTranslate\"\n            aria-haspopup=\"dialog\"\n            [attr.aria-expanded]=\"isOpen() ? 'true' : 'false'\"\n            [attr.aria-controls]=\"isOpen() ? panelId() : null\"\n        >\n            <fa-icon [icon]=\"timeOnly() ? faClock : faCalendar\" />\n        </button>\n    </div>\n\n    @if (!hideValidationMessage() && showErrorBorder()) {\n        <span [id]=\"inputId() + '-error'\" role=\"alert\" class=\"tum:text-sm tum:text-state-danger\">\n            {{ invalidMessageKey() | tumUiTranslate }}\n        </span>\n    }\n</div>\n\n<ng-template #panel>\n    <div\n        [id]=\"panelId()\"\n        class=\"tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:p-3 tum:shadow-lg\"\n        role=\"dialog\"\n        aria-modal=\"true\"\n        [attr.aria-label]=\"dialogLabelKey() | tumUiTranslate\"\n        cdkTrapFocus\n    >\n        @if (!timeOnly()) {\n            <tum-ui-calendar [selected]=\"value()\" [activeMonth]=\"activeMonth()\" [focusOnInit]=\"true\" (daySelected)=\"onDaySelect($event)\" (monthChange)=\"activeMonth.set($event)\" />\n        }\n        <div\n            class=\"tum:flex tum:items-center tum:justify-center tum:gap-2\"\n            [class]=\"timeOnly() ? '' : 'tum:mt-3'\"\n            role=\"group\"\n            [attr.aria-label]=\"'tumUi.datePicker.time' | tumUiTranslate\"\n        >\n            <div class=\"tum:flex tum:flex-col tum:items-center\">\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepHour(1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.incrementHour' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronUp\" />\n                </button>\n                <input\n                    #hourInput\n                    type=\"text\"\n                    inputmode=\"numeric\"\n                    maxlength=\"2\"\n                    [value]=\"displayHour()\"\n                    [disabled]=\"disabled()\"\n                    (change)=\"onHourInput(hourInput)\"\n                    (keydown)=\"onTimeKeydown($event, hourInput, 'hour')\"\n                    class=\"tum:box-border tum:w-9 tum:appearance-none tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1 tum:text-center tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                    [attr.aria-label]=\"'tumUi.datePicker.hour' | tumUiTranslate\"\n                />\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepHour(-1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.decrementHour' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronDown\" />\n                </button>\n            </div>\n            <span class=\"tum:pb-0.5 tum:font-semibold tum:text-text\">:</span>\n            <div class=\"tum:flex tum:flex-col tum:items-center\">\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepMinute(1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.incrementMinute' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronUp\" />\n                </button>\n                <input\n                    #minuteInput\n                    type=\"text\"\n                    inputmode=\"numeric\"\n                    maxlength=\"2\"\n                    [value]=\"displayMinute()\"\n                    [disabled]=\"disabled()\"\n                    (change)=\"onMinuteInput(minuteInput)\"\n                    (keydown)=\"onTimeKeydown($event, minuteInput, 'minute')\"\n                    class=\"tum:box-border tum:w-9 tum:appearance-none tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1 tum:text-center tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                    [attr.aria-label]=\"'tumUi.datePicker.minute' | tumUiTranslate\"\n                />\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepMinute(-1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.decrementMinute' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronDown\" />\n                </button>\n            </div>\n        </div>\n        <div class=\"tum:mt-3 tum:flex tum:justify-end\">\n            <tum-ui-button severity=\"secondary\" variant=\"text\" size=\"small\" (clicked)=\"close()\">\n                <span>{{ 'tumUi.datePicker.done' | tumUiTranslate }}</span>\n            </tum-ui-button>\n        </div>\n    </div>\n</ng-template>\n", styles: [":host{display:block}.tum-ui-date-picker-input{border-start-start-radius:var(--tum-ui-input-group-start-radius, var(--tumaet-ui-radius-md));border-end-start-radius:var(--tum-ui-input-group-start-radius, var(--tumaet-ui-radius-md));border-start-end-radius:var(--tum-ui-input-group-end-radius, var(--tumaet-ui-radius-md));border-end-end-radius:var(--tum-ui-input-group-end-radius, var(--tumaet-ui-radius-md))}\n"], dependencies: [{ kind: "ngmodule", type: A11yModule }, { kind: "directive", type: i1.CdkTrapFocus, selector: "[cdkTrapFocus]", inputs: ["cdkTrapFocus", "cdkTrapFocusAutoCapture"], exportAs: ["cdkTrapFocus"] }, { kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }, { kind: "component", type: FaStackComponent, selector: "fa-stack", inputs: ["size"] }, { kind: "directive", type: FaStackItemSizeDirective, selector: "fa-icon[stackItemSize],fa-duotone-icon[stackItemSize]", inputs: ["stackItemSize", "size"] }, { kind: "component", type: TumUiButtonComponent, selector: "tum-ui-button", inputs: ["severity", "size", "variant", "disabled", "rounded", "loading", "icon", "type", "ariaLabel", "ariaExpanded", "ariaPressed", "ariaControls", "ariaDescribedBy"], outputs: ["clicked"] }, { kind: "component", type: TumUiCalendarComponent, selector: "tum-ui-calendar", inputs: ["selected", "activeMonth", "focusOnInit"], outputs: ["daySelected", "monthChange"] }, { kind: "directive", type: TumUiTooltipDirective, selector: "[tumUiTooltip]", inputs: ["tumUiTooltip", "tumUiTooltipPlacement", "showDelayMs", "hideDelayMs"] }, { kind: "pipe", type: TumUiTranslatePipe, name: "tumUiTranslate" }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiDatePickerComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-date-picker', host: {
                        // The application stylesheet excludes TUM UI controls from the JHipster validity accent by this class.
                        class: 'tum-ui-date-picker',
                    }, imports: [A11yModule, FaIconComponent, FaStackComponent, FaStackItemSizeDirective, TumUiButtonComponent, TumUiCalendarComponent, TumUiTooltipDirective, TumUiTranslatePipe], changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum:flex tum:flex-col tum:gap-1\">\n    @if ((!hideLabelName() && labelName()) || shouldDisplayTimeZoneWarning()) {\n        <div class=\"tum:flex tum:items-center tum:gap-1\">\n            @if (!hideLabelName() && labelName()) {\n                <label [attr.for]=\"inputId()\" class=\"tum:font-semibold tum:text-text\">{{ labelName()! | tumUiTranslate }}</label>\n            }\n            @if (shouldDisplayTimeZoneWarning()) {\n                <fa-stack\n                    class=\"tum:h-4 tum:w-4\"\n                    tabindex=\"0\"\n                    role=\"img\"\n                    [attr.aria-label]=\"'tumUi.datePicker.timeZoneWarning' | tumUiTranslate: { timeZone: currentTimeZone }\"\n                    [tumUiTooltip]=\"'tumUi.datePicker.timeZoneWarning' | tumUiTranslate: { timeZone: currentTimeZone }\"\n                >\n                    <fa-icon [icon]=\"faGlobe\" stackItemSize=\"1x\" class=\"tum:text-muted\" />\n                    <fa-icon [icon]=\"faClock\" stackItemSize=\"1x\" transform=\"shrink-6 down-5 right-5\" class=\"tum:text-muted\" />\n                </fa-stack>\n            }\n        </div>\n    }\n\n    <div #triggerWrapper class=\"tum:relative tum:flex tum:items-center\">\n        <input\n            #dateInput\n            [id]=\"inputId()\"\n            type=\"text\"\n            role=\"combobox\"\n            [value]=\"inputText()\"\n            [disabled]=\"disabled()\"\n            [placeholder]=\"placeholderKey() | tumUiTranslate\"\n            aria-haspopup=\"dialog\"\n            [attr.aria-expanded]=\"isOpen()\"\n            [attr.aria-controls]=\"isOpen() ? panelId() : null\"\n            [attr.aria-invalid]=\"showErrorBorder()\"\n            [attr.aria-label]=\"ariaLabel()\"\n            [attr.aria-describedby]=\"!hideValidationMessage() && showErrorBorder() ? inputId() + '-error' : null\"\n            (input)=\"onInput(dateInput.value)\"\n            (blur)=\"onBlur(dateInput.value)\"\n            (keydown.arrowdown)=\"openFromInput($event)\"\n            class=\"tum-ui-date-picker-input tum:box-border tum:w-full tum:rounded-md tum:border tum:bg-control-background tum:py-2 tum:ps-3 tum:pe-17 tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n            [class]=\"showErrorBorder() ? 'tum:border-state-danger' : 'tum:border-control-border'\"\n        />\n        @if (showClear() && !disabled()) {\n            <button\n                type=\"button\"\n                class=\"tum:absolute tum:end-9 tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text-hover\"\n                (click)=\"clear()\"\n                [attr.aria-label]=\"'tumUi.datePicker.clear' | tumUiTranslate\"\n            >\n                <fa-icon [icon]=\"faXmark\" />\n            </button>\n        }\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            class=\"tum:absolute tum:end-2 tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-accent\"\n            [disabled]=\"disabled()\"\n            (click)=\"toggle()\"\n            [attr.aria-label]=\"openLabelKey() | tumUiTranslate\"\n            aria-haspopup=\"dialog\"\n            [attr.aria-expanded]=\"isOpen() ? 'true' : 'false'\"\n            [attr.aria-controls]=\"isOpen() ? panelId() : null\"\n        >\n            <fa-icon [icon]=\"timeOnly() ? faClock : faCalendar\" />\n        </button>\n    </div>\n\n    @if (!hideValidationMessage() && showErrorBorder()) {\n        <span [id]=\"inputId() + '-error'\" role=\"alert\" class=\"tum:text-sm tum:text-state-danger\">\n            {{ invalidMessageKey() | tumUiTranslate }}\n        </span>\n    }\n</div>\n\n<ng-template #panel>\n    <div\n        [id]=\"panelId()\"\n        class=\"tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:p-3 tum:shadow-lg\"\n        role=\"dialog\"\n        aria-modal=\"true\"\n        [attr.aria-label]=\"dialogLabelKey() | tumUiTranslate\"\n        cdkTrapFocus\n    >\n        @if (!timeOnly()) {\n            <tum-ui-calendar [selected]=\"value()\" [activeMonth]=\"activeMonth()\" [focusOnInit]=\"true\" (daySelected)=\"onDaySelect($event)\" (monthChange)=\"activeMonth.set($event)\" />\n        }\n        <div\n            class=\"tum:flex tum:items-center tum:justify-center tum:gap-2\"\n            [class]=\"timeOnly() ? '' : 'tum:mt-3'\"\n            role=\"group\"\n            [attr.aria-label]=\"'tumUi.datePicker.time' | tumUiTranslate\"\n        >\n            <div class=\"tum:flex tum:flex-col tum:items-center\">\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepHour(1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.incrementHour' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronUp\" />\n                </button>\n                <input\n                    #hourInput\n                    type=\"text\"\n                    inputmode=\"numeric\"\n                    maxlength=\"2\"\n                    [value]=\"displayHour()\"\n                    [disabled]=\"disabled()\"\n                    (change)=\"onHourInput(hourInput)\"\n                    (keydown)=\"onTimeKeydown($event, hourInput, 'hour')\"\n                    class=\"tum:box-border tum:w-9 tum:appearance-none tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1 tum:text-center tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                    [attr.aria-label]=\"'tumUi.datePicker.hour' | tumUiTranslate\"\n                />\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepHour(-1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.decrementHour' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronDown\" />\n                </button>\n            </div>\n            <span class=\"tum:pb-0.5 tum:font-semibold tum:text-text\">:</span>\n            <div class=\"tum:flex tum:flex-col tum:items-center\">\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepMinute(1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.incrementMinute' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronUp\" />\n                </button>\n                <input\n                    #minuteInput\n                    type=\"text\"\n                    inputmode=\"numeric\"\n                    maxlength=\"2\"\n                    [value]=\"displayMinute()\"\n                    [disabled]=\"disabled()\"\n                    (change)=\"onMinuteInput(minuteInput)\"\n                    (keydown)=\"onTimeKeydown($event, minuteInput, 'minute')\"\n                    class=\"tum:box-border tum:w-9 tum:appearance-none tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1 tum:text-center tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                    [attr.aria-label]=\"'tumUi.datePicker.minute' | tumUiTranslate\"\n                />\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepMinute(-1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.decrementMinute' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronDown\" />\n                </button>\n            </div>\n        </div>\n        <div class=\"tum:mt-3 tum:flex tum:justify-end\">\n            <tum-ui-button severity=\"secondary\" variant=\"text\" size=\"small\" (clicked)=\"close()\">\n                <span>{{ 'tumUi.datePicker.done' | tumUiTranslate }}</span>\n            </tum-ui-button>\n        </div>\n    </div>\n</ng-template>\n", styles: [":host{display:block}.tum-ui-date-picker-input{border-start-start-radius:var(--tum-ui-input-group-start-radius, var(--tumaet-ui-radius-md));border-end-start-radius:var(--tum-ui-input-group-start-radius, var(--tumaet-ui-radius-md));border-start-end-radius:var(--tum-ui-input-group-end-radius, var(--tumaet-ui-radius-md));border-end-end-radius:var(--tum-ui-input-group-end-radius, var(--tumaet-ui-radius-md))}\n"] }]
        }], ctorParameters: () => [], propDecorators: { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }, { type: i0.Output, args: ["valueChange"] }], invalid: [{ type: i0.Input, args: [{ isSignal: true, alias: "invalid", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], hideLabelName: [{ type: i0.Input, args: [{ isSignal: true, alias: "hideLabelName", required: false }] }], hideValidationMessage: [{ type: i0.Input, args: [{ isSignal: true, alias: "hideValidationMessage", required: false }] }], shouldDisplayTimeZoneWarning: [{ type: i0.Input, args: [{ isSignal: true, alias: "shouldDisplayTimeZoneWarning", required: false }] }], timeOnly: [{ type: i0.Input, args: [{ isSignal: true, alias: "timeOnly", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], labelName: [{ type: i0.Input, args: [{ isSignal: true, alias: "labelName", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], inputValidityChange: [{ type: i0.Output, args: ["inputValidityChange"] }], touch: [{ type: i0.Output, args: ["touch"] }], panel: [{ type: i0.ViewChild, args: ['panel', { ...{ read: TemplateRef }, isSignal: true }] }], dateInput: [{ type: i0.ViewChild, args: ['dateInput', { isSignal: true }] }], triggerWrapper: [{ type: i0.ViewChild, args: ['triggerWrapper', { isSignal: true }] }], hourField: [{ type: i0.ViewChild, args: ['hourInput', { isSignal: true }] }] } });

const TUM_UI_FORM_FIELD = new InjectionToken('TumUiFormField');

let nextFormFieldId = 0;
/**
 * Labelled wrapper around a single form control: it owns the label, the required marker, and the hint and
 * error text, and wires `for`, `aria-describedby`, and the invalid state to the control it wraps.
 *
 * The control adopts the field's generated id, so a minimal field needs no ids at all:
 *
 * ```html
 * <tum-ui-form-field label="Login" required [invalid]="control.dirty && control.invalid">
 *     <input tumUiInput formControlName="login" />
 *     <ng-container tumUiFormFieldError>
 *         @if (control.errors?.required) { <span>Login is required</span> }
 *     </ng-container>
 * </tum-ui-form-field>
 * ```
 *
 * The error region is always rendered so its `role="alert"` announces when it appears; it stays hidden, and
 * out of the control's description, while `invalid` is false. An error replaces the hint as the description,
 * matching how a screen reader should report a field that has just failed validation.
 *
 * `tum-ui-date-picker` renders its own label and validation message, so it is already a complete field and
 * does not need this wrapper.
 */
class TumUiFormFieldComponent {
    /** Label text. Omit it when projecting a `[tumUiFormFieldLabel]` slot instead. */
    label = input('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "label" }] : /* istanbul ignore next */ []));
    /**
     * Id of the control this field labels. Defaults to a generated id, which a TUM UI control adopts unless
     * it carries an id of its own. Set it when the id has to be stable, such as for an end-to-end selector.
     */
    controlId = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "controlId" }] : /* istanbul ignore next */ []));
    /**
     * Renders the required marker. The marker is decorative: the control still needs its own `required`, which
     * is what assistive technology reports.
     */
    required = input(false, { ...(ngDevMode ? { debugName: "required" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Helper text shown below the control while the field is valid. */
    hint = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "hint" }] : /* istanbul ignore next */ []));
    /** Shows the error region and marks the wrapped control invalid. */
    invalid = input(false, { ...(ngDevMode ? { debugName: "invalid" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Error text. Project a `[tumUiFormFieldError]` slot instead when several messages can apply. */
    error = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "error" }] : /* istanbul ignore next */ []));
    /** Id a wrapped control reported because it brought one of its own. */
    reportedControlId = signal(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "reportedControlId" }] : /* istanbul ignore next */ []));
    fieldId = nextFormFieldId++;
    generatedControlId = `tum-ui-form-field-${this.fieldId}-control`;
    hintId = `tum-ui-form-field-${this.fieldId}-hint`;
    errorId = `tum-ui-form-field-${this.fieldId}-error`;
    explicitControlId = this.controlId;
    labelTargetId = computed(() => this.controlId() ?? this.reportedControlId() ?? this.generatedControlId, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "labelTargetId" }] : /* istanbul ignore next */ []));
    adoptControlId(id) {
        this.reportedControlId.set(id);
    }
    showHint = computed(() => !!this.hint()?.trim() && !this.invalid(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "showHint" }] : /* istanbul ignore next */ []));
    describedBy = computed(() => {
        if (this.invalid()) {
            return this.errorId;
        }
        return this.showHint() ? this.hintId : undefined;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "describedBy" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiFormFieldComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiFormFieldComponent, isStandalone: true, selector: "tum-ui-form-field", inputs: { label: { classPropertyName: "label", publicName: "label", isSignal: true, isRequired: false, transformFunction: null }, controlId: { classPropertyName: "controlId", publicName: "controlId", isSignal: true, isRequired: false, transformFunction: null }, required: { classPropertyName: "required", publicName: "required", isSignal: true, isRequired: false, transformFunction: null }, hint: { classPropertyName: "hint", publicName: "hint", isSignal: true, isRequired: false, transformFunction: null }, invalid: { classPropertyName: "invalid", publicName: "invalid", isSignal: true, isRequired: false, transformFunction: null }, error: { classPropertyName: "error", publicName: "error", isSignal: true, isRequired: false, transformFunction: null } }, host: { classAttribute: "tum-ui-form-field" }, providers: [{ provide: TUM_UI_FORM_FIELD, useExisting: TumUiFormFieldComponent }], ngImport: i0, template: "<label class=\"tum-ui-form-field-label tum:flex tum:items-center tum:gap-1 tum:text-sm tum:font-medium tum:text-text\" [for]=\"labelTargetId()\">\n    {{ label() }}<ng-content select=\"[tumUiFormFieldLabel]\" />\n    @if (required()) {\n        <span class=\"tum-ui-form-field-required tum:text-state-danger\" aria-hidden=\"true\">*</span>\n    }\n</label>\n<div class=\"tum-ui-form-field-control\">\n    <ng-content />\n</div>\n@if (showHint()) {\n    <span [id]=\"hintId\" class=\"tum-ui-form-field-hint tum:text-sm tum:text-muted\">{{ hint() }}</span>\n}\n<div [id]=\"errorId\" class=\"tum-ui-form-field-error tum:text-sm tum:text-state-danger\" role=\"alert\" [hidden]=\"!invalid()\">\n    {{ error() }}<ng-content select=\"[tumUiFormFieldError]\" />\n</div>\n", styles: [":host{display:flex;flex-direction:column;gap:calc(var(--tumaet-ui-spacing) * 1.5)}.tum-ui-form-field-label{min-width:0}.tum-ui-form-field-control{display:flex;flex-direction:column;min-width:0}[hidden]{display:none}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiFormFieldComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-form-field', host: {
                        class: 'tum-ui-form-field',
                    }, providers: [{ provide: TUM_UI_FORM_FIELD, useExisting: TumUiFormFieldComponent }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<label class=\"tum-ui-form-field-label tum:flex tum:items-center tum:gap-1 tum:text-sm tum:font-medium tum:text-text\" [for]=\"labelTargetId()\">\n    {{ label() }}<ng-content select=\"[tumUiFormFieldLabel]\" />\n    @if (required()) {\n        <span class=\"tum-ui-form-field-required tum:text-state-danger\" aria-hidden=\"true\">*</span>\n    }\n</label>\n<div class=\"tum-ui-form-field-control\">\n    <ng-content />\n</div>\n@if (showHint()) {\n    <span [id]=\"hintId\" class=\"tum-ui-form-field-hint tum:text-sm tum:text-muted\">{{ hint() }}</span>\n}\n<div [id]=\"errorId\" class=\"tum-ui-form-field-error tum:text-sm tum:text-state-danger\" role=\"alert\" [hidden]=\"!invalid()\">\n    {{ error() }}<ng-content select=\"[tumUiFormFieldError]\" />\n</div>\n", styles: [":host{display:flex;flex-direction:column;gap:calc(var(--tumaet-ui-spacing) * 1.5)}.tum-ui-form-field-label{min-width:0}.tum-ui-form-field-control{display:flex;flex-direction:column;min-width:0}[hidden]{display:none}\n"] }]
        }], propDecorators: { label: [{ type: i0.Input, args: [{ isSignal: true, alias: "label", required: false }] }], controlId: [{ type: i0.Input, args: [{ isSignal: true, alias: "controlId", required: false }] }], required: [{ type: i0.Input, args: [{ isSignal: true, alias: "required", required: false }] }], hint: [{ type: i0.Input, args: [{ isSignal: true, alias: "hint", required: false }] }], invalid: [{ type: i0.Input, args: [{ isSignal: true, alias: "invalid", required: false }] }], error: [{ type: i0.Input, args: [{ isSignal: true, alias: "error", required: false }] }] } });

class TumUiIconFieldComponent {
    icon = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "icon" }] : /* istanbul ignore next */ []));
    iconPosition = input('left', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "iconPosition" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiIconFieldComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiIconFieldComponent, isStandalone: true, selector: "tum-ui-icon-field", inputs: { icon: { classPropertyName: "icon", publicName: "icon", isSignal: true, isRequired: false, transformFunction: null }, iconPosition: { classPropertyName: "iconPosition", publicName: "iconPosition", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "attr.data-position": "iconPosition()", "attr.data-has-icon": "icon() ? \"\" : null" }, classAttribute: "tum-ui-icon-field" }, ngImport: i0, template: "@if (icon(); as fieldIcon) {\n    <fa-icon [icon]=\"fieldIcon\" class=\"tum-ui-input-icon tum:text-muted\" aria-hidden=\"true\" />\n}\n<ng-content />\n", styles: [":host{position:relative;display:block}.tum-ui-input-icon{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}:host[data-position=right] .tum-ui-input-icon{inset-inline-end:calc(var(--tumaet-ui-spacing) * 3)}:host:not([data-position=right]) .tum-ui-input-icon{inset-inline-start:calc(var(--tumaet-ui-spacing) * 3)}\n"], dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiIconFieldComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-icon-field', imports: [FaIconComponent], host: {
                        class: 'tum-ui-icon-field',
                        '[attr.data-position]': 'iconPosition()',
                        '[attr.data-has-icon]': 'icon() ? "" : null',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "@if (icon(); as fieldIcon) {\n    <fa-icon [icon]=\"fieldIcon\" class=\"tum-ui-input-icon tum:text-muted\" aria-hidden=\"true\" />\n}\n<ng-content />\n", styles: [":host{position:relative;display:block}.tum-ui-input-icon{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}:host[data-position=right] .tum-ui-input-icon{inset-inline-end:calc(var(--tumaet-ui-spacing) * 3)}:host:not([data-position=right]) .tum-ui-input-icon{inset-inline-start:calc(var(--tumaet-ui-spacing) * 3)}\n"] }]
        }], propDecorators: { icon: [{ type: i0.Input, args: [{ isSignal: true, alias: "icon", required: false }] }], iconPosition: [{ type: i0.Input, args: [{ isSignal: true, alias: "iconPosition", required: false }] }] } });

class TumUiInputGroupAddonComponent {
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiInputGroupAddonComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "14.0.0", version: "22.1.5", type: TumUiInputGroupAddonComponent, isStandalone: true, selector: "tum-ui-input-group-addon", host: { classAttribute: "tum-ui-input-group-addon tum:bg-control-background tum:text-muted tum:border-y tum:first:border-s tum:first:rounded-s-md tum:last:border-e tum:last:rounded-e-md" }, ngImport: i0, template: '<ng-content />', isInline: true, styles: [":host{display:flex;align-items:center;justify-content:center;padding:calc(var(--tumaet-ui-spacing) * 2);min-width:calc(var(--tumaet-ui-spacing) * 10);white-space:nowrap;border-color:var(--tum-ui-input-group-border-color, var(--tumaet-ui-control-border-color));transition:border-color .2s}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiInputGroupAddonComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-input-group-addon', template: '<ng-content />', host: {
                        class: 'tum-ui-input-group-addon tum:bg-control-background tum:text-muted tum:border-y ' + 'tum:first:border-s tum:first:rounded-s-md tum:last:border-e tum:last:rounded-e-md',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{display:flex;align-items:center;justify-content:center;padding:calc(var(--tumaet-ui-spacing) * 2);min-width:calc(var(--tumaet-ui-spacing) * 10);white-space:nowrap;border-color:var(--tum-ui-input-group-border-color, var(--tumaet-ui-control-border-color));transition:border-color .2s}\n"] }]
        }] });

class TumUiInputGroupComponent {
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiInputGroupComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "14.0.0", version: "22.1.5", type: TumUiInputGroupComponent, isStandalone: true, selector: "tum-ui-input-group", host: { classAttribute: "tum-ui-input-group" }, ngImport: i0, template: '<ng-content />', isInline: true, styles: [":host{display:flex;align-items:stretch;--tum-ui-input-group-border-color: var(--tumaet-ui-control-border-color)}:host:has(:is(input,select,textarea):enabled:hover){--tum-ui-input-group-border-color: var(--tumaet-ui-control-border-hover-color)}:host:has(:is(input,select,textarea):enabled:focus){--tum-ui-input-group-border-color: var(--tumaet-ui-focus-color)}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiInputGroupComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-input-group', template: '<ng-content />', host: {
                        class: 'tum-ui-input-group',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{display:flex;align-items:stretch;--tum-ui-input-group-border-color: var(--tumaet-ui-control-border-color)}:host:has(:is(input,select,textarea):enabled:hover){--tum-ui-input-group-border-color: var(--tumaet-ui-control-border-hover-color)}:host:has(:is(input,select,textarea):enabled:focus){--tum-ui-input-group-border-color: var(--tumaet-ui-focus-color)}\n"] }]
        }] });

const INPUT_BASE = 'tum-ui-input tum:box-border tum:appearance-none tum:rounded-md tum:border tum:bg-control-background tum:text-text tum:shadow-xs ' +
    'tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2 ' +
    'tum:transition-colors tum:duration-200 tum:placeholder:text-muted ' +
    'tum:disabled:opacity-100 tum:disabled:bg-disabled-background tum:disabled:text-disabled';
const INPUT_BORDER = 'tum:border-control-border tum:enabled:hover:border-control-border-hover tum:enabled:focus:border-focus';
const INPUT_BORDER_INVALID = 'tum:border-state-danger';
const INPUT_SIZE = {
    small: 'tum:text-sm tum:px-2.5 tum:py-1.5',
    large: 'tum:text-lg tum:px-3.5 tum:py-2.5',
};
const INPUT_SIZE_NORMAL = 'tum:text-base tum:px-3 tum:py-2';
function tumUiInputClasses(options) {
    const size = options.size ? INPUT_SIZE[options.size] : INPUT_SIZE_NORMAL;
    const border = options.invalid ? INPUT_BORDER_INVALID : INPUT_BORDER;
    return `${INPUT_BASE} ${size} ${border}`;
}

let nextInputId = 0;
class TumUiInputDirective {
    elementRef = inject(ElementRef);
    formField = inject(TUM_UI_FORM_FIELD, { optional: true });
    tumUiInputSize = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "tumUiInputSize" }] : /* istanbul ignore next */ []));
    tumUiInputInvalid = input(false, { ...(ngDevMode ? { debugName: "tumUiInputInvalid" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /**
     * Overrides the element id. Set it from a wrapper that owns the id; a plain `id` attribute on the element
     * works just as well and is left untouched.
     */
    tumUiInputId = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "tumUiInputId" }] : /* istanbul ignore next */ []));
    /** Extra description ids to merge in, for a wrapper component that owns describing text of its own. */
    tumUiInputDescribedBy = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "tumUiInputDescribedBy" }] : /* istanbul ignore next */ []));
    // Read before any binding is applied, so an id written as a static attribute keeps precedence over the id
    // an enclosing form field would otherwise hand down.
    staticId = this.elementRef.nativeElement.getAttribute('id');
    staticDescribedBy = this.elementRef.nativeElement.getAttribute('aria-describedby');
    fallbackId = `tum-ui-input-${nextInputId++}`;
    /** The id this element brought with it, if any, as opposed to one adopted from a form field. */
    ownId = computed(() => this.tumUiInputId() ?? this.staticId ?? undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ownId" }] : /* istanbul ignore next */ []));
    /**
     * Resolved element id. A form field told to label a specific id wins, so the label can never point at an
     * element that is not there; otherwise an id the element brought wins, then the field's, then a generated
     * one.
     */
    controlId = computed(() => this.formField?.explicitControlId() ?? this.ownId() ?? this.formField?.labelTargetId() ?? this.fallbackId, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "controlId" }] : /* istanbul ignore next */ []));
    describedBy = computed(() => {
        const ids = [this.tumUiInputDescribedBy(), this.staticDescribedBy, this.formField?.describedBy()].filter(Boolean);
        return ids.length ? ids.join(' ') : null;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "describedBy" }] : /* istanbul ignore next */ []));
    isInvalid = computed(() => this.tumUiInputInvalid() || (this.formField?.invalid() ?? false), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isInvalid" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => tumUiInputClasses({ size: this.tumUiInputSize(), invalid: this.isInvalid() }), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    constructor() {
        // Tell the field which id it should label whenever this element brought one of its own.
        effect(() => {
            const ownId = this.ownId();
            if (ownId) {
                this.formField?.adoptControlId(ownId);
            }
        });
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiInputDirective, deps: [], target: i0.ɵɵFactoryTarget.Directive });
    static ɵdir = i0.ɵɵngDeclareDirective({ minVersion: "17.1.0", version: "22.1.5", type: TumUiInputDirective, isStandalone: true, selector: "input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]", inputs: { tumUiInputSize: { classPropertyName: "tumUiInputSize", publicName: "tumUiInputSize", isSignal: true, isRequired: false, transformFunction: null }, tumUiInputInvalid: { classPropertyName: "tumUiInputInvalid", publicName: "tumUiInputInvalid", isSignal: true, isRequired: false, transformFunction: null }, tumUiInputId: { classPropertyName: "tumUiInputId", publicName: "tumUiInputId", isSignal: true, isRequired: false, transformFunction: null }, tumUiInputDescribedBy: { classPropertyName: "tumUiInputDescribedBy", publicName: "tumUiInputDescribedBy", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "class": "hostClasses()", "attr.id": "controlId()", "attr.aria-describedby": "describedBy()", "attr.aria-invalid": "isInvalid() || null" } }, ngImport: i0 });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiInputDirective, decorators: [{
            type: Directive,
            args: [{
                    selector: 'input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]',
                    host: {
                        '[class]': 'hostClasses()',
                        '[attr.id]': 'controlId()',
                        '[attr.aria-describedby]': 'describedBy()',
                        // Dropped rather than set to "false" while valid: the invalid border is a visual cue only, so a screen
                        // reader must be told about the error state, but a valid field should carry no state attribute at all.
                        '[attr.aria-invalid]': 'isInvalid() || null',
                    },
                }]
        }], ctorParameters: () => [], propDecorators: { tumUiInputSize: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiInputSize", required: false }] }], tumUiInputInvalid: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiInputInvalid", required: false }] }], tumUiInputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiInputId", required: false }] }], tumUiInputDescribedBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiInputDescribedBy", required: false }] }] } });

/** Numeric input with locale grouping, optional affixes, optional decimals and step controls. */
class TumUiInputNumberComponent {
    /** Lower bound applied on blur and stepping. */
    min = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "min" }] : /* istanbul ignore next */ []));
    /** Upper bound applied on blur and stepping. */
    max = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "max" }] : /* istanbul ignore next */ []));
    /** Increment / decrement applied by the stepper buttons and Arrow Up / Down keys. */
    step = input(1, { ...(ngDevMode ? { debugName: "step" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    /** Shows increment and decrement controls. */
    showButtons = input(false, { ...(ngDevMode ? { debugName: "showButtons" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Text displayed before the formatted number. */
    prefix = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "prefix" }] : /* istanbul ignore next */ []));
    /** Text displayed after the formatted number. */
    suffix = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "suffix" }] : /* istanbul ignore next */ []));
    placeholder = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "placeholder" }] : /* istanbul ignore next */ []));
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Marks the field invalid without changing its value. */
    invalid = input(false, { ...(ngDevMode ? { debugName: "invalid" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Expands the field to the available width. */
    fluid = input(false, { ...(ngDevMode ? { debugName: "fluid" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Enables locale-specific digit grouping. */
    useGrouping = input(true, { ...(ngDevMode ? { debugName: "useGrouping" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /**
     * Maximum fraction digits. `0` (the default) keeps the field integer-only; a positive value lets the user
     * type the locale's decimal separator, and the fraction is truncated — not rounded — to this many digits.
     */
    maxFractionDigits = input(0, { ...(ngDevMode ? { debugName: "maxFractionDigits" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    /** Locale used for formatting; omit it to use the browser locale. */
    locale = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "locale" }] : /* istanbul ignore next */ []));
    /**
     * `id` of the inner `<input>`, so an external `<label for>` associates. Defaults to the id of an enclosing
     * `tum-ui-form-field`, and to a unique per-instance id outside one.
     */
    inputId = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "inputId" }] : /* istanbul ignore next */ []));
    /** Native input name. */
    name = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "name" }] : /* istanbul ignore next */ []));
    /** Accessible name for the inner `<input>` when there is no visible `<label>`. */
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    /** Element `id` values that label the inner `<input>`. */
    ariaLabelledBy = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []));
    /** Element `id` values that describe the inner `<input>`. */
    ariaDescribedBy = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaDescribedBy" }] : /* istanbul ignore next */ []));
    inputRef = viewChild.required('inputEl', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "inputRef" }] : /* istanbul ignore next */ []));
    cvaValue = signal(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "cvaValue" }] : /* istanbul ignore next */ []));
    cvaDisabled = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "cvaDisabled" }] : /* istanbul ignore next */ []));
    isDisabled = computed(() => this.disabled() || this.cvaDisabled(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []));
    faChevronUp = faChevronUp;
    faChevronDown = faChevronDown;
    numberFormatter = computed(() => new Intl.NumberFormat(this.locale(), { useGrouping: this.useGrouping(), maximumFractionDigits: this.maxFractionDigits() }), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "numberFormatter" }] : /* istanbul ignore next */ []));
    localeNumberSyntax = computed(() => this.createLocaleNumberSyntax(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "localeNumberSyntax" }] : /* istanbul ignore next */ []));
    formattedValue = computed(() => this.format(this.cvaValue()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "formattedValue" }] : /* istanbul ignore next */ []));
    displayText = linkedSignal(() => this.formattedValue(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "displayText" }] : /* istanbul ignore next */ []));
    ariaValueNow = computed(() => this.cvaValue(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaValueNow" }] : /* istanbul ignore next */ []));
    ariaValueText = computed(() => this.formattedValue() || null, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaValueText" }] : /* istanbul ignore next */ []));
    onModelChange = () => { };
    onModelTouched = () => { };
    format(value) {
        if (value === undefined || value === null || Number.isNaN(value)) {
            return '';
        }
        const formatted = this.numberFormatter().format(value);
        return `${this.prefix() ?? ''}${formatted}${this.suffix() ?? ''}`;
    }
    createLocaleNumberSyntax() {
        const formatter = new Intl.NumberFormat(this.locale(), { useGrouping: true, maximumFractionDigits: 0 });
        const digitBySymbol = new Map();
        for (let digit = 0; digit <= 9; digit++) {
            const localizedDigit = formatter
                .formatToParts(digit)
                .filter((part) => part.type === 'integer')
                .map((part) => part.value)
                .join('');
            const asciiDigit = String(digit);
            digitBySymbol.set(asciiDigit, asciiDigit);
            digitBySymbol.set(localizedDigit, asciiDigit);
        }
        const digitSymbols = [...digitBySymbol.keys()].sort((left, right) => right.length - left.length);
        const groupSeparators = [
            ...new Set(formatter
                .formatToParts(123456789)
                .filter((part) => part.type === 'group')
                .map((part) => part.value)),
        ].sort((left, right) => right.length - left.length);
        const minusSigns = [
            ...new Set([
                '-',
                ...formatter
                    .formatToParts(-1)
                    .filter((part) => part.type === 'minusSign')
                    .map((part) => part.value),
            ]),
        ].sort((left, right) => right.length - left.length);
        // Only the locale's own decimal symbol counts, never a hardcoded `.`: German uses `.` to group
        // thousands, so accepting it would read `1.234` as a fraction instead of a grouped integer.
        const decimalSeparators = [
            ...new Set(new Intl.NumberFormat(this.locale(), { minimumFractionDigits: 1 })
                .formatToParts(1.1)
                .filter((part) => part.type === 'decimal')
                .map((part) => part.value)),
        ].sort((left, right) => right.length - left.length);
        return { digitBySymbol, digitSymbols, groupSeparators, decimalSeparators, minusSigns };
    }
    stripAffixes(text) {
        let body = text;
        const prefix = this.prefix();
        const suffix = this.suffix();
        if (prefix && body.startsWith(prefix)) {
            body = body.slice(prefix.length);
        }
        if (suffix && body.endsWith(suffix)) {
            body = body.slice(0, body.length - suffix.length);
        }
        return body;
    }
    parse(text) {
        const body = this.stripAffixes(text);
        const syntax = this.localeNumberSyntax();
        const maxFractionDigits = this.maxFractionDigits();
        let integerDigits = '';
        let fractionDigits = '';
        let negative = false;
        let inFraction = false;
        for (let index = 0; index < body.length;) {
            const digit = this.matchAt(body, index, syntax.digitSymbols);
            if (digit) {
                if (inFraction) {
                    // Truncate rather than round: the user is still typing, and rounding here would fight the caret.
                    if (fractionDigits.length < maxFractionDigits) {
                        fractionDigits += syntax.digitBySymbol.get(digit);
                    }
                }
                else {
                    integerDigits += syntax.digitBySymbol.get(digit);
                }
                index += digit.length;
                continue;
            }
            const minusSign = this.matchAt(body, index, syntax.minusSigns);
            if (minusSign) {
                negative ||= integerDigits.length === 0;
                index += minusSign.length;
                continue;
            }
            if (maxFractionDigits > 0 && !inFraction) {
                const decimalSeparator = this.matchAt(body, index, syntax.decimalSeparators);
                if (decimalSeparator) {
                    inFraction = true;
                    index += decimalSeparator.length;
                    continue;
                }
            }
            const groupSeparator = this.matchAt(body, index, syntax.groupSeparators);
            index += groupSeparator?.length ?? (body.codePointAt(index) > 0xffff ? 2 : 1);
        }
        if (integerDigits === '' && fractionDigits === '') {
            return undefined;
        }
        // Always assembled with an ASCII `.`, which is what Number.parseFloat understands regardless of locale.
        const parsed = Number.parseFloat(`${negative ? '-' : ''}${integerDigits || '0'}.${fractionDigits || '0'}`);
        return Number.isNaN(parsed) ? undefined : parsed;
    }
    /**
     * True while the text holds a fraction the user is still entering that reformatting would swallow — a
     * trailing decimal separator (`12.`) or trailing fraction zeros (`12.50`). The raw text is kept until the
     * entry settles on blur, mirroring the lone-minus-sign guard in {@link onInput}.
     */
    fractionEntryInProgress(text) {
        if (this.maxFractionDigits() === 0) {
            return false;
        }
        const body = this.stripAffixes(text);
        const syntax = this.localeNumberSyntax();
        for (let index = 0; index < body.length;) {
            const decimalSeparator = this.matchAt(body, index, syntax.decimalSeparators);
            if (decimalSeparator) {
                // Compared as ASCII so locales with their own digit symbols are handled like any other.
                const fraction = this.toAsciiDigits(body.slice(index + decimalSeparator.length));
                return fraction === '' || fraction.endsWith('0');
            }
            index += body.codePointAt(index) > 0xffff ? 2 : 1;
        }
        return false;
    }
    clamp(value) {
        const min = this.min();
        const max = this.max();
        let clamped = value;
        if (min !== undefined && clamped < min) {
            clamped = min;
        }
        if (max !== undefined && clamped > max) {
            clamped = max;
        }
        return clamped;
    }
    matchAt(text, index, candidates) {
        return candidates.find((candidate) => candidate.length > 0 && text.startsWith(candidate, index));
    }
    toAsciiDigits(text) {
        const syntax = this.localeNumberSyntax();
        let digits = '';
        for (let index = 0; index < text.length;) {
            const digit = this.matchAt(text, index, syntax.digitSymbols);
            if (digit) {
                digits += syntax.digitBySymbol.get(digit);
                index += digit.length;
                continue;
            }
            index += text.codePointAt(index) > 0xffff ? 2 : 1;
        }
        return digits;
    }
    digitCount(text) {
        const digitSymbols = this.localeNumberSyntax().digitSymbols;
        let count = 0;
        for (let index = 0; index < text.length;) {
            const digit = this.matchAt(text, index, digitSymbols);
            if (digit) {
                count++;
                index += digit.length;
                continue;
            }
            index += text.codePointAt(index) > 0xffff ? 2 : 1;
        }
        return count;
    }
    caretAfterDigits(text, digitCount) {
        const start = (this.prefix() ?? '').length;
        if (digitCount <= 0) {
            return start;
        }
        const end = text.length - (this.suffix() ?? '').length;
        const digitSymbols = this.localeNumberSyntax().digitSymbols;
        let seen = 0;
        for (let index = start; index < end;) {
            const digit = this.matchAt(text, index, digitSymbols);
            if (digit) {
                seen++;
                if (seen === digitCount) {
                    return index + digit.length;
                }
            }
            index += digit?.length ?? (text.codePointAt(index) > 0xffff ? 2 : 1);
        }
        return end;
    }
    onInput(event) {
        const el = event.target;
        const caret = el.selectionStart ?? el.value.length;
        const prefixLength = (this.prefix() ?? '').length;
        const digitsBeforeCaret = this.digitCount(el.value.slice(prefixLength, caret));
        const parsed = this.parse(el.value);
        this.cvaValue.set(parsed);
        this.onModelChange(parsed);
        const syntax = this.localeNumberSyntax();
        if (parsed === undefined && syntax.minusSigns.some((minusSign) => el.value.includes(minusSign)) && this.digitCount(el.value) === 0) {
            this.displayText.set(el.value);
            return;
        }
        if (this.fractionEntryInProgress(el.value)) {
            this.displayText.set(el.value);
            return;
        }
        const formatted = this.format(parsed);
        this.displayText.set(formatted);
        el.value = formatted;
        const nextCaret = this.caretAfterDigits(formatted, digitsBeforeCaret);
        el.setSelectionRange(nextCaret, nextCaret);
    }
    onStep(delta) {
        if (this.isDisabled()) {
            return;
        }
        const base = this.cvaValue() ?? 0;
        // Round to the field's precision so a fractional step does not leak binary float error into the model
        // (0.2 + 0.1 is 0.30000000000000004). Harmless in integer mode, where the step is a whole number.
        const stepped = Number((base + delta).toFixed(this.maxFractionDigits()));
        const next = this.clamp(stepped);
        this.cvaValue.set(next);
        this.displayText.set(this.format(next));
        this.onModelChange(next);
        this.inputRef().nativeElement.focus();
    }
    onKeydown(event) {
        if (event.key === 'ArrowUp') {
            event.preventDefault();
            this.onStep(this.step());
        }
        else if (event.key === 'ArrowDown') {
            event.preventDefault();
            this.onStep(-this.step());
        }
    }
    onBlurHandler() {
        const value = this.cvaValue();
        if (value !== undefined) {
            const clamped = this.clamp(value);
            if (clamped !== value) {
                this.cvaValue.set(clamped);
                this.onModelChange(clamped);
            }
        }
        this.displayText.set(this.format(this.cvaValue()));
        this.onModelTouched();
    }
    writeValue(value) {
        this.cvaValue.set(value ?? undefined);
    }
    registerOnChange(fn) {
        this.onModelChange = fn;
    }
    registerOnTouched(fn) {
        this.onModelTouched = fn;
    }
    setDisabledState(isDisabled) {
        this.cvaDisabled.set(isDisabled);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiInputNumberComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiInputNumberComponent, isStandalone: true, selector: "tum-ui-input-number", inputs: { min: { classPropertyName: "min", publicName: "min", isSignal: true, isRequired: false, transformFunction: null }, max: { classPropertyName: "max", publicName: "max", isSignal: true, isRequired: false, transformFunction: null }, step: { classPropertyName: "step", publicName: "step", isSignal: true, isRequired: false, transformFunction: null }, showButtons: { classPropertyName: "showButtons", publicName: "showButtons", isSignal: true, isRequired: false, transformFunction: null }, prefix: { classPropertyName: "prefix", publicName: "prefix", isSignal: true, isRequired: false, transformFunction: null }, suffix: { classPropertyName: "suffix", publicName: "suffix", isSignal: true, isRequired: false, transformFunction: null }, placeholder: { classPropertyName: "placeholder", publicName: "placeholder", isSignal: true, isRequired: false, transformFunction: null }, disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, invalid: { classPropertyName: "invalid", publicName: "invalid", isSignal: true, isRequired: false, transformFunction: null }, fluid: { classPropertyName: "fluid", publicName: "fluid", isSignal: true, isRequired: false, transformFunction: null }, useGrouping: { classPropertyName: "useGrouping", publicName: "useGrouping", isSignal: true, isRequired: false, transformFunction: null }, maxFractionDigits: { classPropertyName: "maxFractionDigits", publicName: "maxFractionDigits", isSignal: true, isRequired: false, transformFunction: null }, locale: { classPropertyName: "locale", publicName: "locale", isSignal: true, isRequired: false, transformFunction: null }, inputId: { classPropertyName: "inputId", publicName: "inputId", isSignal: true, isRequired: false, transformFunction: null }, name: { classPropertyName: "name", publicName: "name", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, ariaLabelledBy: { classPropertyName: "ariaLabelledBy", publicName: "ariaLabelledBy", isSignal: true, isRequired: false, transformFunction: null }, ariaDescribedBy: { classPropertyName: "ariaDescribedBy", publicName: "ariaDescribedBy", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "class.tum-ui-input-number-fluid": "fluid()", "class.tum-ui-input-number-buttons": "showButtons()" }, classAttribute: "tum-ui-input-number" }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiInputNumberComponent), multi: true }], viewQueries: [{ propertyName: "inputRef", first: true, predicate: ["inputEl"], descendants: true, isSignal: true }], ngImport: i0, template: "<input\n    #inputEl\n    tumUiInput\n    type=\"text\"\n    [attr.inputmode]=\"maxFractionDigits() > 0 ? 'decimal' : 'numeric'\"\n    class=\"tum-ui-input-number-input\"\n    role=\"spinbutton\"\n    [value]=\"displayText()\"\n    [tumUiInputId]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [attr.placeholder]=\"placeholder()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    [attr.aria-labelledby]=\"ariaLabelledBy()\"\n    [tumUiInputDescribedBy]=\"ariaDescribedBy()\"\n    [attr.aria-valuenow]=\"ariaValueNow()\"\n    [attr.aria-valuemin]=\"min() ?? null\"\n    [attr.aria-valuemax]=\"max() ?? null\"\n    [attr.aria-valuetext]=\"ariaValueText()\"\n    [disabled]=\"isDisabled()\"\n    [tumUiInputInvalid]=\"invalid()\"\n    (input)=\"onInput($event)\"\n    (blur)=\"onBlurHandler()\"\n    (keydown)=\"onKeydown($event)\"\n/>\n@if (showButtons()) {\n    <span class=\"tum-ui-input-number-button-group\">\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            aria-hidden=\"true\"\n            class=\"tum-ui-input-number-button tum-ui-input-number-increment tum:text-muted tum:hover:bg-hover-background tum:hover:text-text-hover\"\n            [disabled]=\"isDisabled()\"\n            (mousedown)=\"$event.preventDefault()\"\n            (click)=\"onStep(step())\"\n        >\n            <fa-icon [icon]=\"faChevronUp\" />\n        </button>\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            aria-hidden=\"true\"\n            class=\"tum-ui-input-number-button tum-ui-input-number-decrement tum:text-muted tum:hover:bg-hover-background tum:hover:text-text-hover\"\n            [disabled]=\"isDisabled()\"\n            (mousedown)=\"$event.preventDefault()\"\n            (click)=\"onStep(-step())\"\n        >\n            <fa-icon [icon]=\"faChevronDown\" />\n        </button>\n    </span>\n}\n", styles: [":host{display:inline-flex;position:relative}:host(.tum-ui-input-number-fluid){display:flex;width:100%}.tum-ui-input-number-input{flex:1 1 auto}:host(.tum-ui-input-number-fluid) .tum-ui-input-number-input{width:1%}:host(.tum-ui-input-number-buttons) .tum-ui-input-number-input{padding-inline-end:calc(var(--tumaet-ui-spacing) * 13)}.tum-ui-input-number-button-group{display:flex;flex-direction:column;position:absolute;inset-block-start:1px;inset-inline-end:1px;height:calc(100% - 2px);z-index:1}.tum-ui-input-number-button{display:flex;align-items:center;justify-content:center;flex:1 1 auto;width:calc(var(--tumaet-ui-spacing) * 10);padding:0;border:0;background:transparent;cursor:pointer;transition:background .2s,color .2s}.tum-ui-input-number-button:disabled{cursor:auto}.tum-ui-input-number-button fa-icon{display:flex;align-items:center;justify-content:center}.tum-ui-input-number-increment{border-start-end-radius:calc(var(--tumaet-ui-radius-md) - 1px)}.tum-ui-input-number-decrement{border-end-end-radius:calc(var(--tumaet-ui-radius-md) - 1px)}\n"], dependencies: [{ kind: "directive", type: TumUiInputDirective, selector: "input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]", inputs: ["tumUiInputSize", "tumUiInputInvalid", "tumUiInputId", "tumUiInputDescribedBy"] }, { kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiInputNumberComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-input-number', imports: [TumUiInputDirective, FaIconComponent], host: {
                        class: 'tum-ui-input-number',
                        '[class.tum-ui-input-number-fluid]': 'fluid()',
                        '[class.tum-ui-input-number-buttons]': 'showButtons()',
                    }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiInputNumberComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<input\n    #inputEl\n    tumUiInput\n    type=\"text\"\n    [attr.inputmode]=\"maxFractionDigits() > 0 ? 'decimal' : 'numeric'\"\n    class=\"tum-ui-input-number-input\"\n    role=\"spinbutton\"\n    [value]=\"displayText()\"\n    [tumUiInputId]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [attr.placeholder]=\"placeholder()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    [attr.aria-labelledby]=\"ariaLabelledBy()\"\n    [tumUiInputDescribedBy]=\"ariaDescribedBy()\"\n    [attr.aria-valuenow]=\"ariaValueNow()\"\n    [attr.aria-valuemin]=\"min() ?? null\"\n    [attr.aria-valuemax]=\"max() ?? null\"\n    [attr.aria-valuetext]=\"ariaValueText()\"\n    [disabled]=\"isDisabled()\"\n    [tumUiInputInvalid]=\"invalid()\"\n    (input)=\"onInput($event)\"\n    (blur)=\"onBlurHandler()\"\n    (keydown)=\"onKeydown($event)\"\n/>\n@if (showButtons()) {\n    <span class=\"tum-ui-input-number-button-group\">\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            aria-hidden=\"true\"\n            class=\"tum-ui-input-number-button tum-ui-input-number-increment tum:text-muted tum:hover:bg-hover-background tum:hover:text-text-hover\"\n            [disabled]=\"isDisabled()\"\n            (mousedown)=\"$event.preventDefault()\"\n            (click)=\"onStep(step())\"\n        >\n            <fa-icon [icon]=\"faChevronUp\" />\n        </button>\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            aria-hidden=\"true\"\n            class=\"tum-ui-input-number-button tum-ui-input-number-decrement tum:text-muted tum:hover:bg-hover-background tum:hover:text-text-hover\"\n            [disabled]=\"isDisabled()\"\n            (mousedown)=\"$event.preventDefault()\"\n            (click)=\"onStep(-step())\"\n        >\n            <fa-icon [icon]=\"faChevronDown\" />\n        </button>\n    </span>\n}\n", styles: [":host{display:inline-flex;position:relative}:host(.tum-ui-input-number-fluid){display:flex;width:100%}.tum-ui-input-number-input{flex:1 1 auto}:host(.tum-ui-input-number-fluid) .tum-ui-input-number-input{width:1%}:host(.tum-ui-input-number-buttons) .tum-ui-input-number-input{padding-inline-end:calc(var(--tumaet-ui-spacing) * 13)}.tum-ui-input-number-button-group{display:flex;flex-direction:column;position:absolute;inset-block-start:1px;inset-inline-end:1px;height:calc(100% - 2px);z-index:1}.tum-ui-input-number-button{display:flex;align-items:center;justify-content:center;flex:1 1 auto;width:calc(var(--tumaet-ui-spacing) * 10);padding:0;border:0;background:transparent;cursor:pointer;transition:background .2s,color .2s}.tum-ui-input-number-button:disabled{cursor:auto}.tum-ui-input-number-button fa-icon{display:flex;align-items:center;justify-content:center}.tum-ui-input-number-increment{border-start-end-radius:calc(var(--tumaet-ui-radius-md) - 1px)}.tum-ui-input-number-decrement{border-end-end-radius:calc(var(--tumaet-ui-radius-md) - 1px)}\n"] }]
        }], propDecorators: { min: [{ type: i0.Input, args: [{ isSignal: true, alias: "min", required: false }] }], max: [{ type: i0.Input, args: [{ isSignal: true, alias: "max", required: false }] }], step: [{ type: i0.Input, args: [{ isSignal: true, alias: "step", required: false }] }], showButtons: [{ type: i0.Input, args: [{ isSignal: true, alias: "showButtons", required: false }] }], prefix: [{ type: i0.Input, args: [{ isSignal: true, alias: "prefix", required: false }] }], suffix: [{ type: i0.Input, args: [{ isSignal: true, alias: "suffix", required: false }] }], placeholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "placeholder", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], invalid: [{ type: i0.Input, args: [{ isSignal: true, alias: "invalid", required: false }] }], fluid: [{ type: i0.Input, args: [{ isSignal: true, alias: "fluid", required: false }] }], useGrouping: [{ type: i0.Input, args: [{ isSignal: true, alias: "useGrouping", required: false }] }], maxFractionDigits: [{ type: i0.Input, args: [{ isSignal: true, alias: "maxFractionDigits", required: false }] }], locale: [{ type: i0.Input, args: [{ isSignal: true, alias: "locale", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], name: [{ type: i0.Input, args: [{ isSignal: true, alias: "name", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }], ariaDescribedBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaDescribedBy", required: false }] }], inputRef: [{ type: i0.ViewChild, args: ['inputEl', { isSignal: true }] }] } });

const BASE$1 = 'tum-ui-list-item-action tum:flex tum:w-full tum:items-center tum:gap-2 tum:border-0 tum:px-4 tum:py-3 tum:text-start tum:text-base tum:no-underline ' +
    'tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:-outline-offset-2';
// The background lives on the state, not the base: a base `bg-transparent` would win over the active
// background, because both are utilities and neither is more specific.
const INACTIVE = 'tum:cursor-pointer tum:bg-transparent tum:text-text tum:hover:bg-hover-background tum:hover:text-text-hover';
const ACTIVE = 'tum:cursor-pointer tum:bg-highlight-background tum:font-medium tum:text-highlight';
/**
 * Turns the interactive element of a {@link TumUiListItemDirective} into the row itself, so the whole row is
 * the click and focus target rather than just the text inside it.
 *
 * Apply it to an `<a>` for navigation — the element stays a real link, so `routerLink` and opening in a new
 * tab keep working — or to a `<button>` for an action:
 *
 * ```html
 * <li tumUiListItem>
 *     <a tumUiListItemAction routerLink="account" routerLinkActive #link="routerLinkActive" [active]="link.isActive">
 *         Account information
 *     </a>
 * </li>
 * ```
 */
class TumUiListItemActionDirective {
    /** Marks this row as the one currently shown, which sets `aria-current="page"`. */
    active = input(false, { ...(ngDevMode ? { debugName: "active" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    hostClasses = computed(() => `${BASE$1} ${this.active() ? ACTIVE : INACTIVE}`, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiListItemActionDirective, deps: [], target: i0.ɵɵFactoryTarget.Directive });
    static ɵdir = i0.ɵɵngDeclareDirective({ minVersion: "17.1.0", version: "22.1.5", type: TumUiListItemActionDirective, isStandalone: true, selector: "a[tumUiListItemAction], button[tumUiListItemAction]", inputs: { active: { classPropertyName: "active", publicName: "active", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "class": "hostClasses()", "attr.aria-current": "active() ? \"page\" : null" } }, ngImport: i0 });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiListItemActionDirective, decorators: [{
            type: Directive,
            args: [{
                    selector: 'a[tumUiListItemAction], button[tumUiListItemAction]',
                    host: {
                        '[class]': 'hostClasses()',
                        '[attr.aria-current]': 'active() ? "page" : null',
                    },
                }]
        }], propDecorators: { active: [{ type: i0.Input, args: [{ isSignal: true, alias: "active", required: false }] }] } });

// The divider sits on the row rather than the list, so the list's own border is never doubled on the first
// row and a row keeps its separator wherever it is rendered.
const BASE = 'tum-ui-list-item tum:flex tum:min-w-0 tum:border-t tum:border-border tum:text-text tum:first:border-t-0';
const STACKED = 'tum:flex-col';
const INLINE = 'tum:flex-row tum:items-center tum:justify-between tum:gap-3';
// Only a row without an interactive child pads itself: when the row holds an action, the action takes the
// padding so the entire row, not just its text, is clickable.
const OWN_PADDING = 'tum:px-4 tum:py-3';
/**
 * A single row of a {@link TumUiListComponent}.
 *
 * Applied to a real `<li>` so the list keeps its native semantics. Put a `[tumUiListItemAction]` link or
 * button inside for a row that navigates or acts; leave it out for a plain content row.
 */
class TumUiListItemDirective {
    /**
     * Lays the row out on one line — a label beside its value, or a label beside its control — instead of
     * stacking its content. The row owns its direction because the package stylesheet is unlayered and loads
     * after the host's, so an application `flex-row` utility cannot override it.
     */
    inline = input(false, { ...(ngDevMode ? { debugName: "inline" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    action = contentChild(TumUiListItemActionDirective, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "action" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => {
        const direction = this.inline() ? INLINE : STACKED;
        return this.action() ? `${BASE} ${direction}` : `${BASE} ${direction} ${OWN_PADDING}`;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiListItemDirective, deps: [], target: i0.ɵɵFactoryTarget.Directive });
    static ɵdir = i0.ɵɵngDeclareDirective({ minVersion: "17.2.0", version: "22.1.5", type: TumUiListItemDirective, isStandalone: true, selector: "li[tumUiListItem]", inputs: { inline: { classPropertyName: "inline", publicName: "inline", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "class": "hostClasses()" } }, queries: [{ propertyName: "action", first: true, predicate: TumUiListItemActionDirective, descendants: true, isSignal: true }], ngImport: i0 });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiListItemDirective, decorators: [{
            type: Directive,
            args: [{
                    selector: 'li[tumUiListItem]',
                    host: {
                        '[class]': 'hostClasses()',
                    },
                }]
        }], propDecorators: { inline: [{ type: i0.Input, args: [{ isSignal: true, alias: "inline", required: false }] }], action: [{ type: i0.ContentChild, args: [i0.forwardRef(() => TumUiListItemActionDirective), { isSignal: true }] }] } });

/**
 * Bordered, vertically stacked list of {@link TumUiListItemDirective} entries, for a settings section, a
 * navigation column, or a short record of label / value rows.
 *
 * The list owns the outer border and radius and the rows own the divider between them, so a host never has
 * to reach for a border colour of its own:
 *
 * ```html
 * <tum-ui-list ariaLabel="User settings">
 *     <li tumUiListItem>
 *         <a tumUiListItemAction routerLink="account" routerLinkActive #link="routerLinkActive" [active]="link.isActive">
 *             Account information
 *         </a>
 *     </li>
 *     <li tumUiListItem>Joined Artemis in 2021</li>
 * </tum-ui-list>
 * ```
 *
 * Use `tum-ui-table` instead when the content is tabular and needs sorting, selection, or column headers.
 */
class TumUiListComponent {
    /** Accessible name for the list. Set it when the list has no visible heading beside it. */
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    /** Id of the visible heading that names the list. Prefer this over `ariaLabel` when a heading exists. */
    ariaLabelledBy = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiListComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiListComponent, isStandalone: true, selector: "tum-ui-list", inputs: { ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, ariaLabelledBy: { classPropertyName: "ariaLabelledBy", publicName: "ariaLabelledBy", isSignal: true, isRequired: false, transformFunction: null } }, host: { classAttribute: "tum-ui-list" }, ngImport: i0, template: "<ul role=\"list\" class=\"tum-ui-list-items tum:m-0 tum:flex tum:list-none tum:flex-col tum:p-0\" [attr.aria-label]=\"ariaLabel()\" [attr.aria-labelledby]=\"ariaLabelledBy()\">\n    <ng-content />\n</ul>\n", styles: [":host{display:block}.tum-ui-list-items{border:1px solid var(--tumaet-ui-border-color);border-radius:var(--tumaet-ui-radius-md);overflow:hidden}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiListComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-list', host: {
                        class: 'tum-ui-list',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<ul role=\"list\" class=\"tum-ui-list-items tum:m-0 tum:flex tum:list-none tum:flex-col tum:p-0\" [attr.aria-label]=\"ariaLabel()\" [attr.aria-labelledby]=\"ariaLabelledBy()\">\n    <ng-content />\n</ul>\n", styles: [":host{display:block}.tum-ui-list-items{border:1px solid var(--tumaet-ui-border-color);border-radius:var(--tumaet-ui-radius-md);overflow:hidden}\n"] }]
        }], propDecorators: { ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }] } });

/**
 * A single command or navigation entry inside a {@link TumUiMenuComponent}.
 *
 * Apply it to a `<button>` for an action, or to an `<a>` for navigation so the entry keeps native link
 * behaviour such as opening in a new tab. `disabled` and the `triggered` output come from the CDK menu item,
 * which also owns the `role`, roving `tabindex`, and closing the menu once an entry runs.
 */
class TumUiMenuItemDirective {
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiMenuItemDirective, deps: [], target: i0.ɵɵFactoryTarget.Directive });
    static ɵdir = i0.ɵɵngDeclareDirective({ minVersion: "14.0.0", version: "22.1.5", type: TumUiMenuItemDirective, isStandalone: true, selector: "[tumUiMenuItem]", host: { classAttribute: "tum-ui-menu-item tum:flex tum:cursor-pointer tum:items-center tum:gap-2 tum:border-0 tum:bg-transparent tum:px-3 tum:py-2 tum:text-start tum:text-base tum:text-text tum:no-underline tum:hover:bg-hover-background tum:hover:text-text-hover tum:focus-visible:bg-highlight-focus-background tum:focus-visible:text-highlight tum:focus-visible:outline-none tum:aria-disabled:pointer-events-none tum:aria-disabled:cursor-default tum:aria-disabled:text-disabled" }, hostDirectives: [{ directive: i1$1.CdkMenuItem, inputs: ["cdkMenuItemDisabled", "disabled"], outputs: ["cdkMenuItemTriggered", "triggered"] }], ngImport: i0 });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiMenuItemDirective, decorators: [{
            type: Directive,
            args: [{
                    selector: '[tumUiMenuItem]',
                    hostDirectives: [
                        {
                            directive: CdkMenuItem,
                            inputs: ['cdkMenuItemDisabled: disabled'],
                            outputs: ['cdkMenuItemTriggered: triggered'],
                        },
                    ],
                    host: {
                        class: 'tum-ui-menu-item tum:flex tum:cursor-pointer tum:items-center tum:gap-2 tum:border-0 tum:bg-transparent tum:px-3 tum:py-2 tum:text-start tum:text-base tum:text-text tum:no-underline ' +
                            'tum:hover:bg-hover-background tum:hover:text-text-hover tum:focus-visible:bg-highlight-focus-background tum:focus-visible:text-highlight tum:focus-visible:outline-none ' +
                            'tum:aria-disabled:pointer-events-none tum:aria-disabled:cursor-default tum:aria-disabled:text-disabled',
                    },
                }]
        }] });

/**
 * Opens a {@link TumUiMenuComponent} from the element it sits on, which is normally a button.
 *
 * Point it at the `ng-template` that holds the menu: `<button [tumUiMenuTrigger]="actions">`. The CDK menu
 * trigger owns the overlay, `aria-haspopup` / `aria-expanded`, opening on Enter, Space, or ArrowDown, closing
 * on Escape or an outside click, and restoring focus to the trigger afterwards.
 */
class TumUiMenuTriggerDirective {
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiMenuTriggerDirective, deps: [], target: i0.ɵɵFactoryTarget.Directive });
    static ɵdir = i0.ɵɵngDeclareDirective({ minVersion: "14.0.0", version: "22.1.5", type: TumUiMenuTriggerDirective, isStandalone: true, selector: "[tumUiMenuTrigger]", hostDirectives: [{ directive: i1$1.CdkMenuTrigger, inputs: ["cdkMenuTriggerFor", "tumUiMenuTrigger", "cdkMenuPosition", "tumUiMenuPosition"], outputs: ["cdkMenuOpened", "menuOpened", "cdkMenuClosed", "menuClosed"] }], ngImport: i0 });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiMenuTriggerDirective, decorators: [{
            type: Directive,
            args: [{
                    selector: '[tumUiMenuTrigger]',
                    hostDirectives: [
                        {
                            directive: CdkMenuTrigger,
                            inputs: ['cdkMenuTriggerFor: tumUiMenuTrigger', 'cdkMenuPosition: tumUiMenuPosition'],
                            outputs: ['cdkMenuOpened: menuOpened', 'cdkMenuClosed: menuClosed'],
                        },
                    ],
                }]
        }] });

/**
 * Menu surface for a list of actions, opened by {@link TumUiMenuTriggerDirective} and filled with
 * `[tumUiMenuItem]` entries.
 *
 * The roles, arrow-key navigation, typeahead, focus handling, and close-on-Escape / close-on-outside-click
 * behavior come from the CDK menu; this component only owns the surface styling. Declare it inside the
 * `ng-template` the trigger points at, so nothing renders until the menu opens:
 *
 * ```html
 * <button [tumUiMenuTrigger]="actions">Actions</button>
 * <ng-template #actions>
 *     <tum-ui-menu>
 *         <a tumUiMenuItem routerLink="./students">Add students</a>
 *         <button tumUiMenuItem (triggered)="archive()">Archive</button>
 *     </tum-ui-menu>
 * </ng-template>
 * ```
 *
 * Use `tum-ui-popover` instead for rich or non-action content: a menu is for commands and navigation.
 */
class TumUiMenuComponent {
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiMenuComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "14.0.0", version: "22.1.5", type: TumUiMenuComponent, isStandalone: true, selector: "tum-ui-menu", host: { classAttribute: "tum-ui-menu tum:flex tum:min-w-48 tum:flex-col tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:py-1 tum:text-text tum:shadow-md" }, hostDirectives: [{ directive: i1$1.CdkMenu }], ngImport: i0, template: '<ng-content />', isInline: true, styles: [":host{box-sizing:border-box;max-height:inherit;overflow:auto}:host:focus-visible{outline:none}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiMenuComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-menu', template: '<ng-content />', hostDirectives: [CdkMenu], host: {
                        class: 'tum-ui-menu tum:flex tum:min-w-48 tum:flex-col tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:py-1 tum:text-text tum:shadow-md',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{box-sizing:border-box;max-height:inherit;overflow:auto}:host:focus-visible{outline:none}\n"] }]
        }] });

const MESSAGE_BASE = 'tum-ui-message';
const MESSAGE_SEVERITY = {
    info: '',
    success: '',
    warn: '',
    error: '',
    secondary: 'tum:bg-hover-background tum:text-text tum:outline-border',
    contrast: 'tum:bg-contrast-background tum:text-contrast tum:outline-contrast-background',
};
class TumUiMessageComponent {
    severity = input('info', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []));
    /** Disable when a surrounding live announcer already reports this message. */
    announce = input(true, { ...(ngDevMode ? { debugName: "announce" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    text = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "text" }] : /* istanbul ignore next */ []));
    icon = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "icon" }] : /* istanbul ignore next */ []));
    messageRole = computed(() => (this.announce() ? (this.severity() === 'error' ? 'alert' : 'status') : null), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "messageRole" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => `${MESSAGE_BASE} ${MESSAGE_SEVERITY[this.severity()]}`.trim(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiMessageComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiMessageComponent, isStandalone: true, selector: "tum-ui-message", inputs: { severity: { classPropertyName: "severity", publicName: "severity", isSignal: true, isRequired: false, transformFunction: null }, announce: { classPropertyName: "announce", publicName: "announce", isSignal: true, isRequired: false, transformFunction: null }, text: { classPropertyName: "text", publicName: "text", isSignal: true, isRequired: false, transformFunction: null }, icon: { classPropertyName: "icon", publicName: "icon", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "attr.role": "messageRole()", "class": "hostClasses()", "attr.data-severity": "severity()" } }, ngImport: i0, template: "<div class=\"tum-ui-message-content\">\n    @if (icon(); as messageIcon) {\n        <fa-icon [icon]=\"messageIcon\" class=\"tum-ui-message-icon\" />\n    }\n    @if (text(); as messageText) {\n        <span class=\"tum-ui-message-text\">{{ messageText }}</span>\n    } @else {\n        <span class=\"tum-ui-message-text\"><ng-content /></span>\n    }\n</div>\n", styles: [":host{display:grid;grid-template-rows:1fr;border-radius:var(--tumaet-ui-radius-md);outline-width:1px;outline-style:solid;outline-offset:0;font-size:var(--tumaet-ui-font-size-base);font-weight:500}.tum-ui-message-content{display:flex;align-items:center;min-height:0;padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 3);gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-message-icon{flex-shrink:0;font-size:var(--tumaet-ui-font-size-lg)}:host[data-severity=info]{background:color-mix(in srgb,var(--tumaet-ui-state-info) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-info) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-info-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-info),transparent 96%)}:host[data-severity=success]{background:color-mix(in srgb,var(--tumaet-ui-state-success) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-success) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-success-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-success),transparent 96%)}:host[data-severity=warn]{background:color-mix(in srgb,var(--tumaet-ui-state-warning) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-warning) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-warning-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-warning),transparent 96%)}:host[data-severity=error]{background:color-mix(in srgb,var(--tumaet-ui-state-danger) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-danger) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-danger-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-danger),transparent 96%)}\n"], dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiMessageComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-message', imports: [FaIconComponent], host: {
                        '[attr.role]': 'messageRole()',
                        '[class]': 'hostClasses()',
                        '[attr.data-severity]': 'severity()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum-ui-message-content\">\n    @if (icon(); as messageIcon) {\n        <fa-icon [icon]=\"messageIcon\" class=\"tum-ui-message-icon\" />\n    }\n    @if (text(); as messageText) {\n        <span class=\"tum-ui-message-text\">{{ messageText }}</span>\n    } @else {\n        <span class=\"tum-ui-message-text\"><ng-content /></span>\n    }\n</div>\n", styles: [":host{display:grid;grid-template-rows:1fr;border-radius:var(--tumaet-ui-radius-md);outline-width:1px;outline-style:solid;outline-offset:0;font-size:var(--tumaet-ui-font-size-base);font-weight:500}.tum-ui-message-content{display:flex;align-items:center;min-height:0;padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 3);gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-message-icon{flex-shrink:0;font-size:var(--tumaet-ui-font-size-lg)}:host[data-severity=info]{background:color-mix(in srgb,var(--tumaet-ui-state-info) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-info) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-info-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-info),transparent 96%)}:host[data-severity=success]{background:color-mix(in srgb,var(--tumaet-ui-state-success) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-success) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-success-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-success),transparent 96%)}:host[data-severity=warn]{background:color-mix(in srgb,var(--tumaet-ui-state-warning) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-warning) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-warning-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-warning),transparent 96%)}:host[data-severity=error]{background:color-mix(in srgb,var(--tumaet-ui-state-danger) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-danger) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-danger-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-danger),transparent 96%)}\n"] }]
        }], propDecorators: { severity: [{ type: i0.Input, args: [{ isSignal: true, alias: "severity", required: false }] }], announce: [{ type: i0.Input, args: [{ isSignal: true, alias: "announce", required: false }] }], text: [{ type: i0.Input, args: [{ isSignal: true, alias: "text", required: false }] }], icon: [{ type: i0.Input, args: [{ isSignal: true, alias: "icon", required: false }] }] } });

const PAGE_LINK_SIZE = 5;
const NAV_BUTTON_CLASSES = 'tum:inline-flex tum:h-9 tum:w-9 tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:rounded-full tum:border-0 tum:bg-transparent tum:text-sm tum:text-muted tum:transition-colors tum:hover:bg-hover-background tum:disabled:pointer-events-none tum:disabled:opacity-50';
/** Controlled paginator using zero-based page indexes. */
class TumUiPaginatorComponent {
    directionality = inject(Directionality);
    direction = signal(this.directionality.value, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "direction" }] : /* istanbul ignore next */ []));
    destroyRef = inject(DestroyRef);
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    /** Total records in the consumer-owned result set. */
    totalRecords = input(0, { ...(ngDevMode ? { debugName: "totalRecords" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    /** Zero-based active page index. */
    page = input(0, { ...(ngDevMode ? { debugName: "page" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    /** Controlled number of records per page. */
    pageSize = input(50, { ...(ngDevMode ? { debugName: "pageSize" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    pageSizeOptions = input([10, 20, 50, 100, 200], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "pageSizeOptions" }] : /* istanbul ignore next */ []));
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    showCurrentPageReport = input(true, { ...(ngDevMode ? { debugName: "showCurrentPageReport" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    showRowsPerPage = input(true, { ...(ngDevMode ? { debugName: "showRowsPerPage" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Requests a zero-based page without mutating `page`. */
    pageChange = output();
    /** Requests a page size without mutating `pageSize`. */
    pageSizeChange = output();
    firstPageIcon = computed(() => (this.direction() === 'rtl' ? faAnglesRight : faAnglesLeft), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "firstPageIcon" }] : /* istanbul ignore next */ []));
    previousPageIcon = computed(() => (this.direction() === 'rtl' ? faAngleRight : faAngleLeft), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "previousPageIcon" }] : /* istanbul ignore next */ []));
    nextPageIcon = computed(() => (this.direction() === 'rtl' ? faAngleLeft : faAngleRight), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "nextPageIcon" }] : /* istanbul ignore next */ []));
    lastPageIcon = computed(() => (this.direction() === 'rtl' ? faAnglesLeft : faAnglesRight), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "lastPageIcon" }] : /* istanbul ignore next */ []));
    navButtonClasses = NAV_BUTTON_CLASSES;
    selectedPageClasses = NAV_BUTTON_CLASSES.replace('tum:bg-transparent', 'tum:bg-primary/15').replace('tum:text-muted', 'tum:font-semibold tum:text-accent');
    totalPages = computed(() => Math.max(1, Math.ceil(this.totalRecords() / Math.max(1, this.pageSize()))), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "totalPages" }] : /* istanbul ignore next */ []));
    clampedPage = computed(() => Math.min(Math.max(0, this.page()), this.totalPages() - 1), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "clampedPage" }] : /* istanbul ignore next */ []));
    isFirst = computed(() => this.clampedPage() <= 0, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isFirst" }] : /* istanbul ignore next */ []));
    isLast = computed(() => this.clampedPage() >= this.totalPages() - 1, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isLast" }] : /* istanbul ignore next */ []));
    rangeBegin = computed(() => (this.totalRecords() === 0 ? 0 : this.clampedPage() * this.pageSize() + 1), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "rangeBegin" }] : /* istanbul ignore next */ []));
    rangeEnd = computed(() => Math.min(this.totalRecords(), (this.clampedPage() + 1) * this.pageSize()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "rangeEnd" }] : /* istanbul ignore next */ []));
    visiblePages = computed(() => {
        const total = this.totalPages();
        const size = Math.min(PAGE_LINK_SIZE, total);
        let start = Math.max(0, this.clampedPage() - Math.floor(size / 2));
        const end = Math.min(total, start + size);
        start = Math.max(0, end - size);
        return Array.from({ length: end - start }, (_, i) => start + i);
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "visiblePages" }] : /* istanbul ignore next */ []));
    constructor() {
        const directionChanges = this.directionality.change.subscribe((direction) => this.direction.set(direction));
        this.destroyRef.onDestroy(() => directionChanges.unsubscribe());
    }
    goToPage(target) {
        if (!this.disabled() && target !== this.page() && target >= 0 && target < this.totalPages()) {
            this.pageChange.emit(target);
        }
    }
    goToFirst() {
        if (!this.disabled() && !this.isFirst()) {
            this.pageChange.emit(0);
        }
    }
    goToPrevious() {
        if (!this.disabled() && !this.isFirst()) {
            this.pageChange.emit(this.clampedPage() - 1);
        }
    }
    goToNext() {
        if (!this.disabled() && !this.isLast()) {
            this.pageChange.emit(this.clampedPage() + 1);
        }
    }
    goToLast() {
        if (!this.disabled() && !this.isLast()) {
            this.pageChange.emit(this.totalPages() - 1);
        }
    }
    onPageSizeChange(value) {
        this.pageSizeChange.emit(value);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiPaginatorComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiPaginatorComponent, isStandalone: true, selector: "tum-ui-paginator", inputs: { ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, totalRecords: { classPropertyName: "totalRecords", publicName: "totalRecords", isSignal: true, isRequired: false, transformFunction: null }, page: { classPropertyName: "page", publicName: "page", isSignal: true, isRequired: false, transformFunction: null }, pageSize: { classPropertyName: "pageSize", publicName: "pageSize", isSignal: true, isRequired: false, transformFunction: null }, pageSizeOptions: { classPropertyName: "pageSizeOptions", publicName: "pageSizeOptions", isSignal: true, isRequired: false, transformFunction: null }, disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, showCurrentPageReport: { classPropertyName: "showCurrentPageReport", publicName: "showCurrentPageReport", isSignal: true, isRequired: false, transformFunction: null }, showRowsPerPage: { classPropertyName: "showRowsPerPage", publicName: "showRowsPerPage", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { pageChange: "pageChange", pageSizeChange: "pageSizeChange" }, ngImport: i0, template: "<nav\n    [attr.aria-label]=\"ariaLabel() ?? ('tumUi.paginator.ariaLabel' | tumUiTranslate)\"\n    class=\"tum:flex tum:flex-wrap tum:items-center tum:justify-center tum:gap-1 tum:border-t tum:border-border tum:pt-2 tum:text-sm tum:text-muted\"\n>\n    @if (showCurrentPageReport()) {\n        <span class=\"tum:px-2\" aria-live=\"polite\">\n            {{ 'tumUi.paginator.currentPageReport' | tumUiTranslate: { first: rangeBegin(), second: rangeEnd(), total: totalRecords() } }}\n        </span>\n    }\n\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isFirst()\" (click)=\"goToFirst()\" [attr.aria-label]=\"'tumUi.paginator.first' | tumUiTranslate\">\n        <fa-icon [icon]=\"firstPageIcon()\" />\n    </button>\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isFirst()\" (click)=\"goToPrevious()\" [attr.aria-label]=\"'tumUi.paginator.previous' | tumUiTranslate\">\n        <fa-icon [icon]=\"previousPageIcon()\" />\n    </button>\n\n    @for (p of visiblePages(); track p) {\n        <button\n            type=\"button\"\n            [class]=\"p === clampedPage() ? selectedPageClasses : navButtonClasses\"\n            [disabled]=\"disabled()\"\n            (click)=\"goToPage(p)\"\n            [attr.aria-current]=\"p === clampedPage() ? 'page' : null\"\n        >\n            {{ p + 1 }}\n        </button>\n    }\n\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isLast()\" (click)=\"goToNext()\" [attr.aria-label]=\"'tumUi.paginator.next' | tumUiTranslate\">\n        <fa-icon [icon]=\"nextPageIcon()\" />\n    </button>\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isLast()\" (click)=\"goToLast()\" [attr.aria-label]=\"'tumUi.paginator.last' | tumUiTranslate\">\n        <fa-icon [icon]=\"lastPageIcon()\" />\n    </button>\n\n    @if (showRowsPerPage()) {\n        <label class=\"tum:ms-2 tum:flex tum:items-center tum:gap-2 tum:whitespace-nowrap\">\n            <span>{{ 'tumUi.paginator.rowsPerPage' | tumUiTranslate }}</span>\n            <select\n                class=\"tum:box-border tum:h-9 tum:cursor-pointer tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:px-2.5 tum:text-sm tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                [ngModel]=\"pageSize()\"\n                [disabled]=\"disabled()\"\n                (ngModelChange)=\"onPageSizeChange($event)\"\n            >\n                @for (option of pageSizeOptions(); track option) {\n                    <option [ngValue]=\"option\">{{ option }}</option>\n                }\n            </select>\n        </label>\n    }\n</nav>\n", dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }, { kind: "ngmodule", type: FormsModule }, { kind: "directive", type: i1$2.NgSelectOption, selector: "option", inputs: ["ngValue", "value"] }, { kind: "directive", type: i1$2.ɵNgSelectMultipleOption, selector: "option", inputs: ["ngValue", "value"] }, { kind: "directive", type: i1$2.SelectControlValueAccessor, selector: "select:not([multiple]):not([ngNoCva])[formControlName],select:not([multiple]):not([ngNoCva])[formControl],select:not([multiple]):not([ngNoCva])[ngModel]", inputs: ["compareWith"] }, { kind: "directive", type: i1$2.NgControlStatus, selector: "[formControlName],[ngModel],[formControl]" }, { kind: "directive", type: i1$2.NgModel, selector: "[ngModel]:not([formControlName]):not([formControl])", inputs: ["name", "disabled", "ngModel", "ngModelOptions"], outputs: ["ngModelChange"], exportAs: ["ngModel"] }, { kind: "pipe", type: TumUiTranslatePipe, name: "tumUiTranslate" }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiPaginatorComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-paginator', imports: [FaIconComponent, FormsModule, TumUiTranslatePipe], changeDetection: ChangeDetectionStrategy.OnPush, template: "<nav\n    [attr.aria-label]=\"ariaLabel() ?? ('tumUi.paginator.ariaLabel' | tumUiTranslate)\"\n    class=\"tum:flex tum:flex-wrap tum:items-center tum:justify-center tum:gap-1 tum:border-t tum:border-border tum:pt-2 tum:text-sm tum:text-muted\"\n>\n    @if (showCurrentPageReport()) {\n        <span class=\"tum:px-2\" aria-live=\"polite\">\n            {{ 'tumUi.paginator.currentPageReport' | tumUiTranslate: { first: rangeBegin(), second: rangeEnd(), total: totalRecords() } }}\n        </span>\n    }\n\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isFirst()\" (click)=\"goToFirst()\" [attr.aria-label]=\"'tumUi.paginator.first' | tumUiTranslate\">\n        <fa-icon [icon]=\"firstPageIcon()\" />\n    </button>\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isFirst()\" (click)=\"goToPrevious()\" [attr.aria-label]=\"'tumUi.paginator.previous' | tumUiTranslate\">\n        <fa-icon [icon]=\"previousPageIcon()\" />\n    </button>\n\n    @for (p of visiblePages(); track p) {\n        <button\n            type=\"button\"\n            [class]=\"p === clampedPage() ? selectedPageClasses : navButtonClasses\"\n            [disabled]=\"disabled()\"\n            (click)=\"goToPage(p)\"\n            [attr.aria-current]=\"p === clampedPage() ? 'page' : null\"\n        >\n            {{ p + 1 }}\n        </button>\n    }\n\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isLast()\" (click)=\"goToNext()\" [attr.aria-label]=\"'tumUi.paginator.next' | tumUiTranslate\">\n        <fa-icon [icon]=\"nextPageIcon()\" />\n    </button>\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isLast()\" (click)=\"goToLast()\" [attr.aria-label]=\"'tumUi.paginator.last' | tumUiTranslate\">\n        <fa-icon [icon]=\"lastPageIcon()\" />\n    </button>\n\n    @if (showRowsPerPage()) {\n        <label class=\"tum:ms-2 tum:flex tum:items-center tum:gap-2 tum:whitespace-nowrap\">\n            <span>{{ 'tumUi.paginator.rowsPerPage' | tumUiTranslate }}</span>\n            <select\n                class=\"tum:box-border tum:h-9 tum:cursor-pointer tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:px-2.5 tum:text-sm tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                [ngModel]=\"pageSize()\"\n                [disabled]=\"disabled()\"\n                (ngModelChange)=\"onPageSizeChange($event)\"\n            >\n                @for (option of pageSizeOptions(); track option) {\n                    <option [ngValue]=\"option\">{{ option }}</option>\n                }\n            </select>\n        </label>\n    }\n</nav>\n" }]
        }], ctorParameters: () => [], propDecorators: { ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], totalRecords: [{ type: i0.Input, args: [{ isSignal: true, alias: "totalRecords", required: false }] }], page: [{ type: i0.Input, args: [{ isSignal: true, alias: "page", required: false }] }], pageSize: [{ type: i0.Input, args: [{ isSignal: true, alias: "pageSize", required: false }] }], pageSizeOptions: [{ type: i0.Input, args: [{ isSignal: true, alias: "pageSizeOptions", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], showCurrentPageReport: [{ type: i0.Input, args: [{ isSignal: true, alias: "showCurrentPageReport", required: false }] }], showRowsPerPage: [{ type: i0.Input, args: [{ isSignal: true, alias: "showRowsPerPage", required: false }] }], pageChange: [{ type: i0.Output, args: ["pageChange"] }], pageSizeChange: [{ type: i0.Output, args: ["pageSizeChange"] }] } });

let nextPanelId = 0;
class TumUiPanelComponent {
    translator = inject(TUM_UI_TRANSLATOR);
    /**
     * Header title text. Omit when projecting a `[tumUiPanelHeader]` slot instead, and set `toggleAriaLabel`
     * alongside it — projected markup does not label the toggle.
     */
    header = input('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "header" }] : /* istanbul ignore next */ []));
    /** Enables disclosure behavior for the projected content. */
    toggleable = input(false, { ...(ngDevMode ? { debugName: "toggleable" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Overrides the toggle name; otherwise the header or package translation is used. */
    toggleAriaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "toggleAriaLabel" }] : /* istanbul ignore next */ []));
    /** Controlled disclosure state, applied only when `toggleable` is enabled. */
    collapsed = model(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "collapsed" }] : /* istanbul ignore next */ []));
    headerId = `tum-ui-panel-header-${nextPanelId}`;
    contentId = `tum-ui-panel-content-${nextPanelId++}`;
    faChevronDown = faChevronDown;
    faChevronUp = faChevronUp;
    isCollapsed = computed(() => this.toggleable() && this.collapsed(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isCollapsed" }] : /* istanbul ignore next */ []));
    toggleLabelledBy = computed(() => (!this.toggleAriaLabel()?.trim() && this.header().trim() ? this.headerId : null), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "toggleLabelledBy" }] : /* istanbul ignore next */ []));
    toggleLabel = computed(() => {
        const customLabel = this.toggleAriaLabel()?.trim();
        if (customLabel) {
            return customLabel;
        }
        if (this.header().trim()) {
            return null;
        }
        return this.translator.translate(this.collapsed() ? 'tumUi.panel.expand' : 'tumUi.panel.collapse');
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "toggleLabel" }] : /* istanbul ignore next */ []));
    toggle() {
        if (this.toggleable()) {
            this.collapsed.update((collapsed) => !collapsed);
        }
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiPanelComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiPanelComponent, isStandalone: true, selector: "tum-ui-panel", inputs: { header: { classPropertyName: "header", publicName: "header", isSignal: true, isRequired: false, transformFunction: null }, toggleable: { classPropertyName: "toggleable", publicName: "toggleable", isSignal: true, isRequired: false, transformFunction: null }, toggleAriaLabel: { classPropertyName: "toggleAriaLabel", publicName: "toggleAriaLabel", isSignal: true, isRequired: false, transformFunction: null }, collapsed: { classPropertyName: "collapsed", publicName: "collapsed", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { collapsed: "collapsedChange" }, host: { properties: { "attr.data-collapsed": "toggleable() && collapsed()" }, classAttribute: "tum-ui-panel tum:border tum:border-border tum:rounded-md tum:bg-content-background tum:text-text" }, ngImport: i0, template: "<div class=\"tum-ui-panel-header\" [class.tum-ui-panel-header-toggleable]=\"toggleable()\">\n    <span [id]=\"headerId\" class=\"tum-ui-panel-title\">{{ header() }}<ng-content select=\"[tumUiPanelHeader]\" /></span>\n    @if (toggleable()) {\n        <button\n            type=\"button\"\n            class=\"tum-ui-panel-toggler\"\n            [attr.aria-expanded]=\"!collapsed()\"\n            [attr.aria-controls]=\"contentId\"\n            [attr.aria-label]=\"toggleLabel()\"\n            [attr.aria-labelledby]=\"toggleLabelledBy()\"\n            (click)=\"toggle()\"\n        >\n            <fa-icon [icon]=\"collapsed() ? faChevronDown : faChevronUp\" />\n        </button>\n    }\n</div>\n<div\n    [id]=\"contentId\"\n    class=\"tum-ui-panel-content-container\"\n    [attr.role]=\"toggleable() && header().trim() ? 'region' : null\"\n    [attr.aria-labelledby]=\"toggleable() && header().trim() ? headerId : null\"\n    [attr.aria-hidden]=\"isCollapsed() ? 'true' : null\"\n    [attr.inert]=\"isCollapsed() ? '' : null\"\n>\n    <div class=\"tum-ui-panel-content-wrapper\">\n        <div class=\"tum-ui-panel-content\">\n            <ng-content />\n        </div>\n        <ng-content select=\"[tumUiPanelFooter]\" />\n    </div>\n</div>\n", styles: [":host{display:block}.tum-ui-panel-header{display:flex;justify-content:space-between;align-items:center;padding:calc(var(--tumaet-ui-spacing) * 4.5)}.tum-ui-panel-header-toggleable{padding:calc(var(--tumaet-ui-spacing) * 1.5) calc(var(--tumaet-ui-spacing) * 4.5)}.tum-ui-panel-title{line-height:1;font-weight:600;flex:1;min-width:0}.tum-ui-panel-toggler{display:inline-flex;align-items:center;justify-content:center;width:calc(var(--tumaet-ui-spacing) * 8);height:calc(var(--tumaet-ui-spacing) * 8);padding:0;border:0;border-radius:50%;background:transparent;color:inherit;cursor:pointer;transition:background-color .15s ease-in-out}.tum-ui-panel-toggler:hover{background-color:color-mix(in srgb,currentcolor 8%,transparent)}.tum-ui-panel-toggler:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:1px}.tum-ui-panel-content-container{display:grid;grid-template-rows:1fr;transition:grid-template-rows .2s ease-in-out}:host([data-collapsed=true]) .tum-ui-panel-content-container{grid-template-rows:0fr}.tum-ui-panel-content-wrapper{min-height:0;overflow:hidden}.tum-ui-panel-content{padding:0 calc(var(--tumaet-ui-spacing) * 4.5) calc(var(--tumaet-ui-spacing) * 4.5)}@media(prefers-reduced-motion:reduce){.tum-ui-panel-content-container{transition:none}}\n"], dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiPanelComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-panel', imports: [FaIconComponent], host: {
                        class: 'tum-ui-panel tum:border tum:border-border tum:rounded-md tum:bg-content-background tum:text-text',
                        '[attr.data-collapsed]': 'toggleable() && collapsed()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum-ui-panel-header\" [class.tum-ui-panel-header-toggleable]=\"toggleable()\">\n    <span [id]=\"headerId\" class=\"tum-ui-panel-title\">{{ header() }}<ng-content select=\"[tumUiPanelHeader]\" /></span>\n    @if (toggleable()) {\n        <button\n            type=\"button\"\n            class=\"tum-ui-panel-toggler\"\n            [attr.aria-expanded]=\"!collapsed()\"\n            [attr.aria-controls]=\"contentId\"\n            [attr.aria-label]=\"toggleLabel()\"\n            [attr.aria-labelledby]=\"toggleLabelledBy()\"\n            (click)=\"toggle()\"\n        >\n            <fa-icon [icon]=\"collapsed() ? faChevronDown : faChevronUp\" />\n        </button>\n    }\n</div>\n<div\n    [id]=\"contentId\"\n    class=\"tum-ui-panel-content-container\"\n    [attr.role]=\"toggleable() && header().trim() ? 'region' : null\"\n    [attr.aria-labelledby]=\"toggleable() && header().trim() ? headerId : null\"\n    [attr.aria-hidden]=\"isCollapsed() ? 'true' : null\"\n    [attr.inert]=\"isCollapsed() ? '' : null\"\n>\n    <div class=\"tum-ui-panel-content-wrapper\">\n        <div class=\"tum-ui-panel-content\">\n            <ng-content />\n        </div>\n        <ng-content select=\"[tumUiPanelFooter]\" />\n    </div>\n</div>\n", styles: [":host{display:block}.tum-ui-panel-header{display:flex;justify-content:space-between;align-items:center;padding:calc(var(--tumaet-ui-spacing) * 4.5)}.tum-ui-panel-header-toggleable{padding:calc(var(--tumaet-ui-spacing) * 1.5) calc(var(--tumaet-ui-spacing) * 4.5)}.tum-ui-panel-title{line-height:1;font-weight:600;flex:1;min-width:0}.tum-ui-panel-toggler{display:inline-flex;align-items:center;justify-content:center;width:calc(var(--tumaet-ui-spacing) * 8);height:calc(var(--tumaet-ui-spacing) * 8);padding:0;border:0;border-radius:50%;background:transparent;color:inherit;cursor:pointer;transition:background-color .15s ease-in-out}.tum-ui-panel-toggler:hover{background-color:color-mix(in srgb,currentcolor 8%,transparent)}.tum-ui-panel-toggler:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:1px}.tum-ui-panel-content-container{display:grid;grid-template-rows:1fr;transition:grid-template-rows .2s ease-in-out}:host([data-collapsed=true]) .tum-ui-panel-content-container{grid-template-rows:0fr}.tum-ui-panel-content-wrapper{min-height:0;overflow:hidden}.tum-ui-panel-content{padding:0 calc(var(--tumaet-ui-spacing) * 4.5) calc(var(--tumaet-ui-spacing) * 4.5)}@media(prefers-reduced-motion:reduce){.tum-ui-panel-content-container{transition:none}}\n"] }]
        }], propDecorators: { header: [{ type: i0.Input, args: [{ isSignal: true, alias: "header", required: false }] }], toggleable: [{ type: i0.Input, args: [{ isSignal: true, alias: "toggleable", required: false }] }], toggleAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "toggleAriaLabel", required: false }] }], collapsed: [{ type: i0.Input, args: [{ isSignal: true, alias: "collapsed", required: false }] }, { type: i0.Output, args: ["collapsedChange"] }] } });

/**
 * Wires a trigger element to a {@link TumUiPopoverComponent}: click toggles the popover anchored to
 * the trigger, and the trigger reflects `aria-haspopup`/`aria-expanded` for accessibility.
 *
 * Usage: `<button [tumUiPopoverTrigger]="pop">Details</button> <tum-ui-popover #pop>...</tum-ui-popover>`
 */
class TumUiPopoverTriggerDirective {
    elementRef = inject(ElementRef);
    popover = input.required({ ...(ngDevMode ? { debugName: "popover" } : /* istanbul ignore next */ {}), alias: 'tumUiPopoverTrigger' });
    toggle() {
        this.popover().toggle(this.elementRef);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiPopoverTriggerDirective, deps: [], target: i0.ɵɵFactoryTarget.Directive });
    static ɵdir = i0.ɵɵngDeclareDirective({ minVersion: "17.1.0", version: "22.1.5", type: TumUiPopoverTriggerDirective, isStandalone: true, selector: "[tumUiPopoverTrigger]", inputs: { popover: { classPropertyName: "popover", publicName: "tumUiPopoverTrigger", isSignal: true, isRequired: true, transformFunction: null } }, host: { listeners: { "click": "toggle()" }, properties: { "attr.aria-haspopup": "'dialog'", "attr.aria-expanded": "popover().isOpen() ? 'true' : 'false'" } }, ngImport: i0 });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiPopoverTriggerDirective, decorators: [{
            type: Directive,
            args: [{
                    selector: '[tumUiPopoverTrigger]',
                    host: {
                        '(click)': 'toggle()',
                        '[attr.aria-haspopup]': "'dialog'",
                        '[attr.aria-expanded]': "popover().isOpen() ? 'true' : 'false'",
                    },
                }]
        }], propDecorators: { popover: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiPopoverTrigger", required: true }] }] } });

/**
 * Anchored panel for rich or interactive content, opened via {@link TumUiPopoverTriggerDirective}. Use the
 * tooltip instead for a short, non-interactive hint.
 *
 * Built on the shared overlay substrate, so it inherits collision-aware positioning with a flipped fallback.
 * Closes on backdrop click and Escape, and traps then restores focus. Renders nothing inline: the projected
 * content is captured in an `ng-template` and portaled on open.
 */
class TumUiPopoverComponent {
    overlayService = inject(TumUiOverlayService);
    viewContainerRef = inject(ViewContainerRef);
    placement = input('bottom', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "placement" }] : /* istanbul ignore next */ []));
    /** Accessible name announced for the role="dialog" panel. Required: a dialog must have a name. */
    ariaLabel = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    openChange = output();
    panel = viewChild.required('panel', { ...(ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {}), read: TemplateRef });
    overlayRef;
    openState = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "openState" }] : /* istanbul ignore next */ []));
    /** Whether the popover is currently open. Read-only: drive it through open() / close() / toggle(). */
    isOpen = this.openState.asReadonly();
    /** Open the popover anchored to `origin`. No-op if already open. */
    open(origin) {
        if (this.isOpen()) {
            return;
        }
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, this.placement(), { hasBackdrop: true });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        this.overlayRef.backdropClick().subscribe(() => this.close());
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                this.close();
            }
        });
        this.openState.set(true);
        this.openChange.emit(true);
    }
    /** Close the popover and dispose its overlay. No-op if already closed. */
    close() {
        if (!this.isOpen()) {
            return;
        }
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.openState.set(false);
        this.openChange.emit(false);
    }
    /** Open the popover if closed, or close it if open. */
    toggle(origin) {
        if (this.isOpen()) {
            this.close();
        }
        else {
            this.open(origin);
        }
    }
    ngOnDestroy() {
        this.overlayRef?.dispose();
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiPopoverComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.2.0", version: "22.1.5", type: TumUiPopoverComponent, isStandalone: true, selector: "tum-ui-popover", inputs: { placement: { classPropertyName: "placement", publicName: "placement", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: true, transformFunction: null } }, outputs: { openChange: "openChange" }, viewQueries: [{ propertyName: "panel", first: true, predicate: ["panel"], descendants: true, read: TemplateRef, isSignal: true }], ngImport: i0, template: "<ng-template #panel>\n    <div\n        class=\"tum-ui-popover-panel tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:p-3 tum:text-text tum:shadow-lg\"\n        role=\"dialog\"\n        aria-modal=\"true\"\n        [attr.aria-label]=\"ariaLabel()\"\n        tabindex=\"0\"\n        cdkTrapFocus\n        [cdkTrapFocusAutoCapture]=\"true\"\n    >\n        <ng-content />\n    </div>\n</ng-template>\n", dependencies: [{ kind: "ngmodule", type: A11yModule }, { kind: "directive", type: i1.CdkTrapFocus, selector: "[cdkTrapFocus]", inputs: ["cdkTrapFocus", "cdkTrapFocusAutoCapture"], exportAs: ["cdkTrapFocus"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiPopoverComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-popover', imports: [A11yModule], changeDetection: ChangeDetectionStrategy.OnPush, template: "<ng-template #panel>\n    <div\n        class=\"tum-ui-popover-panel tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:p-3 tum:text-text tum:shadow-lg\"\n        role=\"dialog\"\n        aria-modal=\"true\"\n        [attr.aria-label]=\"ariaLabel()\"\n        tabindex=\"0\"\n        cdkTrapFocus\n        [cdkTrapFocusAutoCapture]=\"true\"\n    >\n        <ng-content />\n    </div>\n</ng-template>\n" }]
        }], propDecorators: { placement: [{ type: i0.Input, args: [{ isSignal: true, alias: "placement", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: true }] }], openChange: [{ type: i0.Output, args: ["openChange"] }], panel: [{ type: i0.ViewChild, args: ['panel', { ...{ read: TemplateRef }, isSignal: true }] }] } });

class TumUiProgressBarComponent {
    value = input(0, { ...(ngDevMode ? { debugName: "value" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    showValue = input(true, { ...(ngDevMode ? { debugName: "showValue" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Track height. `small` is a slim rail for dense contexts such as table cells and has no room for the inline label. */
    size = input('default', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    /** Semantic color of the filled track. */
    severity = input('primary', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []));
    unit = input('%', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "unit" }] : /* istanbul ignore next */ []));
    normalizedValue = computed(() => {
        const value = this.value();
        return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "normalizedValue" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiProgressBarComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiProgressBarComponent, isStandalone: true, selector: "tum-ui-progress-bar", inputs: { value: { classPropertyName: "value", publicName: "value", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, showValue: { classPropertyName: "showValue", publicName: "showValue", isSignal: true, isRequired: false, transformFunction: null }, size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null }, severity: { classPropertyName: "severity", publicName: "severity", isSignal: true, isRequired: false, transformFunction: null }, unit: { classPropertyName: "unit", publicName: "unit", isSignal: true, isRequired: false, transformFunction: null } }, host: { attributes: { "role": "progressbar" }, properties: { "attr.data-size": "size()", "attr.aria-valuemin": "0", "attr.aria-valuemax": "100", "attr.aria-valuenow": "normalizedValue()", "attr.aria-label": "ariaLabel()" }, classAttribute: "tum-ui-progress-bar tum:bg-border" }, ngImport: i0, template: "<div class=\"tum-ui-progress-bar-value\" [attr.data-severity]=\"severity()\" [style.width.%]=\"normalizedValue()\">\n    <span class=\"tum-ui-progress-bar-label\">\n        <ng-content>\n            @if (showValue() && normalizedValue() !== 0) {\n                {{ normalizedValue() }}{{ unit() }}\n            }\n        </ng-content>\n    </span>\n</div>\n", styles: [":host{display:block;position:relative;overflow:hidden;height:calc(var(--tumaet-ui-spacing) * 5);border-radius:var(--tumaet-ui-radius-md)}:host([data-size=small]){height:calc(var(--tumaet-ui-spacing) * 1.5)}:host([data-size=small]) .tum-ui-progress-bar-label{display:none}.tum-ui-progress-bar-value{height:100%;width:0;position:absolute;display:flex;align-items:center;justify-content:center;overflow:hidden;background:var(--tumaet-ui-primary-color);transition:width 1s ease-in-out}.tum-ui-progress-bar-value[data-severity=success]{background:var(--tumaet-ui-state-success)}.tum-ui-progress-bar-value[data-severity=warn]{background:var(--tumaet-ui-state-warning)}.tum-ui-progress-bar-value[data-severity=danger]{background:var(--tumaet-ui-state-danger)}.tum-ui-progress-bar-value[data-severity=info]{background:var(--tumaet-ui-state-info)}.tum-ui-progress-bar-label{font-size:var(--tumaet-ui-font-size-xs);font-weight:600;color:var(--tumaet-ui-primary-contrast-color)}.tum-ui-progress-bar-value[data-severity=success] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-success-contrast)}.tum-ui-progress-bar-value[data-severity=warn] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-warning-contrast)}.tum-ui-progress-bar-value[data-severity=danger] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-danger-contrast)}.tum-ui-progress-bar-value[data-severity=info] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-info-contrast)}@media(prefers-reduced-motion:reduce){.tum-ui-progress-bar-value{transition:none}}@media(forced-colors:active){:host{border:1px solid CanvasText}.tum-ui-progress-bar-value{background:Highlight}.tum-ui-progress-bar-label{color:HighlightText}}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiProgressBarComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-progress-bar', host: {
                        class: 'tum-ui-progress-bar tum:bg-border',
                        role: 'progressbar',
                        '[attr.data-size]': 'size()',
                        '[attr.aria-valuemin]': '0',
                        '[attr.aria-valuemax]': '100',
                        '[attr.aria-valuenow]': 'normalizedValue()',
                        '[attr.aria-label]': 'ariaLabel()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum-ui-progress-bar-value\" [attr.data-severity]=\"severity()\" [style.width.%]=\"normalizedValue()\">\n    <span class=\"tum-ui-progress-bar-label\">\n        <ng-content>\n            @if (showValue() && normalizedValue() !== 0) {\n                {{ normalizedValue() }}{{ unit() }}\n            }\n        </ng-content>\n    </span>\n</div>\n", styles: [":host{display:block;position:relative;overflow:hidden;height:calc(var(--tumaet-ui-spacing) * 5);border-radius:var(--tumaet-ui-radius-md)}:host([data-size=small]){height:calc(var(--tumaet-ui-spacing) * 1.5)}:host([data-size=small]) .tum-ui-progress-bar-label{display:none}.tum-ui-progress-bar-value{height:100%;width:0;position:absolute;display:flex;align-items:center;justify-content:center;overflow:hidden;background:var(--tumaet-ui-primary-color);transition:width 1s ease-in-out}.tum-ui-progress-bar-value[data-severity=success]{background:var(--tumaet-ui-state-success)}.tum-ui-progress-bar-value[data-severity=warn]{background:var(--tumaet-ui-state-warning)}.tum-ui-progress-bar-value[data-severity=danger]{background:var(--tumaet-ui-state-danger)}.tum-ui-progress-bar-value[data-severity=info]{background:var(--tumaet-ui-state-info)}.tum-ui-progress-bar-label{font-size:var(--tumaet-ui-font-size-xs);font-weight:600;color:var(--tumaet-ui-primary-contrast-color)}.tum-ui-progress-bar-value[data-severity=success] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-success-contrast)}.tum-ui-progress-bar-value[data-severity=warn] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-warning-contrast)}.tum-ui-progress-bar-value[data-severity=danger] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-danger-contrast)}.tum-ui-progress-bar-value[data-severity=info] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-info-contrast)}@media(prefers-reduced-motion:reduce){.tum-ui-progress-bar-value{transition:none}}@media(forced-colors:active){:host{border:1px solid CanvasText}.tum-ui-progress-bar-value{background:Highlight}.tum-ui-progress-bar-label{color:HighlightText}}\n"] }]
        }], propDecorators: { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], showValue: [{ type: i0.Input, args: [{ isSignal: true, alias: "showValue", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], severity: [{ type: i0.Input, args: [{ isSignal: true, alias: "severity", required: false }] }], unit: [{ type: i0.Input, args: [{ isSignal: true, alias: "unit", required: false }] }] } });

class TumUiProgressSpinnerComponent {
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiProgressSpinnerComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiProgressSpinnerComponent, isStandalone: true, selector: "tum-ui-progress-spinner", inputs: { ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null } }, host: { attributes: { "role": "status", "aria-busy": "true" }, properties: { "attr.aria-label": "ariaLabel()" }, classAttribute: "tum-ui-progress-spinner" }, ngImport: i0, template: "<svg class=\"tum-ui-progress-spinner-spin\" viewBox=\"25 25 50 50\">\n    <circle class=\"tum-ui-progress-spinner-circle\" cx=\"50\" cy=\"50\" r=\"20\" fill=\"none\" stroke-width=\"2\" stroke-miterlimit=\"10\" />\n</svg>\n", styles: [":host{position:relative;margin:0 auto;width:100px;height:100px;display:inline-block}:host:before{content:\"\";display:block;padding-top:100%}.tum-ui-progress-spinner-spin{height:100%;width:100%;transform-origin:center center;position:absolute;inset:0;margin:auto;animation:tum-ui-progress-spinner-rotate 2s linear infinite}.tum-ui-progress-spinner-circle{stroke-dasharray:89,200;stroke-dashoffset:0;stroke:var(--tumaet-ui-primary-color);stroke-linecap:round;animation:tum-ui-progress-spinner-dash 1.5s ease-in-out infinite}@keyframes tum-ui-progress-spinner-rotate{to{transform:rotate(360deg)}}@keyframes tum-ui-progress-spinner-dash{0%{stroke-dasharray:1,200;stroke-dashoffset:0}50%{stroke-dasharray:89,200;stroke-dashoffset:-35px}to{stroke-dasharray:89,200;stroke-dashoffset:-124px}}@media(prefers-reduced-motion:reduce){.tum-ui-progress-spinner-spin,.tum-ui-progress-spinner-circle{animation:none}}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiProgressSpinnerComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-progress-spinner', host: {
                        class: 'tum-ui-progress-spinner',
                        role: 'status',
                        'aria-busy': 'true',
                        '[attr.aria-label]': 'ariaLabel()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<svg class=\"tum-ui-progress-spinner-spin\" viewBox=\"25 25 50 50\">\n    <circle class=\"tum-ui-progress-spinner-circle\" cx=\"50\" cy=\"50\" r=\"20\" fill=\"none\" stroke-width=\"2\" stroke-miterlimit=\"10\" />\n</svg>\n", styles: [":host{position:relative;margin:0 auto;width:100px;height:100px;display:inline-block}:host:before{content:\"\";display:block;padding-top:100%}.tum-ui-progress-spinner-spin{height:100%;width:100%;transform-origin:center center;position:absolute;inset:0;margin:auto;animation:tum-ui-progress-spinner-rotate 2s linear infinite}.tum-ui-progress-spinner-circle{stroke-dasharray:89,200;stroke-dashoffset:0;stroke:var(--tumaet-ui-primary-color);stroke-linecap:round;animation:tum-ui-progress-spinner-dash 1.5s ease-in-out infinite}@keyframes tum-ui-progress-spinner-rotate{to{transform:rotate(360deg)}}@keyframes tum-ui-progress-spinner-dash{0%{stroke-dasharray:1,200;stroke-dashoffset:0}50%{stroke-dasharray:89,200;stroke-dashoffset:-35px}to{stroke-dasharray:89,200;stroke-dashoffset:-124px}}@media(prefers-reduced-motion:reduce){.tum-ui-progress-spinner-spin,.tum-ui-progress-spinner-circle{animation:none}}\n"] }]
        }], propDecorators: { ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }] } });

const UNSET = Symbol('tum-ui-radio-unset');
/** Native radio control with TUM UI styling and Angular forms integration. */
class TumUiRadioButtonComponent {
    /** Value written to the containing form when this option is selected. */
    value = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "value" }] : /* istanbul ignore next */ []));
    /** Native radio-group name. Radios belong together when they share a form owner and name. */
    name = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "name" }] : /* istanbul ignore next */ []));
    /** ID used to associate a consumer-provided label with the native radio. */
    inputId = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "inputId" }] : /* istanbul ignore next */ []));
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Accessible name used when no associated label is rendered. */
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    /** Emits the originating click and selected option value. */
    selected = output();
    cvaValue = signal(UNSET, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "cvaValue" }] : /* istanbul ignore next */ []));
    isChecked = computed(() => this.cvaValue() !== UNSET && this.cvaValue() === this.value(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isChecked" }] : /* istanbul ignore next */ []));
    cvaDisabled = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "cvaDisabled" }] : /* istanbul ignore next */ []));
    isDisabled = computed(() => this.disabled() || this.cvaDisabled(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []));
    boxClasses = computed(() => {
        if (this.isDisabled()) {
            return 'tum:bg-disabled-background tum:border-control-border';
        }
        if (this.isChecked()) {
            return 'tum:bg-primary tum:border-primary';
        }
        return 'tum:bg-control-background tum:border-control-border';
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "boxClasses" }] : /* istanbul ignore next */ []));
    iconClasses = computed(() => (this.isDisabled() ? 'tum:bg-disabled' : 'tum:bg-primary-contrast'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "iconClasses" }] : /* istanbul ignore next */ []));
    onModelChange = () => { };
    onModelTouched = () => { };
    onInputClick(event) {
        if (this.isDisabled()) {
            return;
        }
        this.cvaValue.set(this.value());
        this.onModelChange(this.value());
        this.onModelTouched();
        this.selected.emit({ originalEvent: event, value: this.value() });
    }
    onBlur() {
        this.onModelTouched();
    }
    writeValue(value) {
        this.cvaValue.set(value);
    }
    registerOnChange(fn) {
        this.onModelChange = fn;
    }
    registerOnTouched(fn) {
        this.onModelTouched = fn;
    }
    setDisabledState(isDisabled) {
        this.cvaDisabled.set(isDisabled);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiRadioButtonComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiRadioButtonComponent, isStandalone: true, selector: "tum-ui-radio-button", inputs: { value: { classPropertyName: "value", publicName: "value", isSignal: true, isRequired: false, transformFunction: null }, name: { classPropertyName: "name", publicName: "name", isSignal: true, isRequired: false, transformFunction: null }, inputId: { classPropertyName: "inputId", publicName: "inputId", isSignal: true, isRequired: false, transformFunction: null }, disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { selected: "selected" }, host: { classAttribute: "tum-ui-radio-button" }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiRadioButtonComponent), multi: true }], ngImport: i0, template: "<input\n    type=\"radio\"\n    class=\"tum-ui-radio-button-input\"\n    [id]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [checked]=\"isChecked()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    (click)=\"onInputClick($event)\"\n    (blur)=\"onBlur()\"\n/>\n<div class=\"tum-ui-radio-button-box\" [class]=\"boxClasses()\" aria-hidden=\"true\">\n    <span class=\"tum-ui-radio-button-icon\" [class]=\"iconClasses()\"></span>\n</div>\n", styles: [":host{position:relative;display:inline-flex;width:calc(var(--tumaet-ui-spacing, .25rem) * 5);height:calc(var(--tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-radio-button-input{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none;border-radius:50%}.tum-ui-radio-button-input:disabled{cursor:default}.tum-ui-radio-button-box{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:50%;box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent);transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-radio-button-box:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-radio-button-icon{width:calc(var(--tumaet-ui-spacing, .25rem) * 3);height:calc(var(--tumaet-ui-spacing, .25rem) * 3);border-radius:50%;visibility:hidden}:host:has(.tum-ui-radio-button-input:checked) .tum-ui-radio-button-icon{visibility:visible}:host:has(.tum-ui-radio-button-input:hover:not(:disabled)) .tum-ui-radio-button-box:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:has(.tum-ui-radio-button-input:focus-visible) .tum-ui-radio-button-box{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-radio-button-input{appearance:auto;opacity:1}.tum-ui-radio-button-box{display:none}}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiRadioButtonComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-radio-button', host: { class: 'tum-ui-radio-button' }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiRadioButtonComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<input\n    type=\"radio\"\n    class=\"tum-ui-radio-button-input\"\n    [id]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [checked]=\"isChecked()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    (click)=\"onInputClick($event)\"\n    (blur)=\"onBlur()\"\n/>\n<div class=\"tum-ui-radio-button-box\" [class]=\"boxClasses()\" aria-hidden=\"true\">\n    <span class=\"tum-ui-radio-button-icon\" [class]=\"iconClasses()\"></span>\n</div>\n", styles: [":host{position:relative;display:inline-flex;width:calc(var(--tumaet-ui-spacing, .25rem) * 5);height:calc(var(--tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-radio-button-input{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none;border-radius:50%}.tum-ui-radio-button-input:disabled{cursor:default}.tum-ui-radio-button-box{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:50%;box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent);transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-radio-button-box:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-radio-button-icon{width:calc(var(--tumaet-ui-spacing, .25rem) * 3);height:calc(var(--tumaet-ui-spacing, .25rem) * 3);border-radius:50%;visibility:hidden}:host:has(.tum-ui-radio-button-input:checked) .tum-ui-radio-button-icon{visibility:visible}:host:has(.tum-ui-radio-button-input:hover:not(:disabled)) .tum-ui-radio-button-box:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:has(.tum-ui-radio-button-input:focus-visible) .tum-ui-radio-button-box{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-radio-button-input{appearance:auto;opacity:1}.tum-ui-radio-button-box{display:none}}\n"] }]
        }], propDecorators: { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }], name: [{ type: i0.Input, args: [{ isSignal: true, alias: "name", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], selected: [{ type: i0.Output, args: ["selected"] }] } });

/** Text field for filtering a view: a leading magnifier and a clear control that appears once there is a term. */
class TumUiSearchFieldComponent {
    /** Two-way bindable term. Emits on every keystroke; debounce in the consumer if the term drives a request. */
    value = model('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "value" }] : /* istanbul ignore next */ []));
    /** Translation key, resolved through the configured translator. */
    placeholder = input('tumUi.searchField.placeholder', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "placeholder" }] : /* istanbul ignore next */ []));
    /** Translation key for the accessible name. Falls back to the placeholder. */
    ariaLabel = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    size = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    faMagnifyingGlass = faMagnifyingGlass;
    faXmark = faXmark;
    accessibleNameKey = computed(() => this.ariaLabel() ?? this.placeholder(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "accessibleNameKey" }] : /* istanbul ignore next */ []));
    inputElement = viewChild.required('searchInput', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "inputElement" }] : /* istanbul ignore next */ []));
    onInput(term) {
        this.value.set(term);
    }
    /** Clears the term and returns focus to the field, so the reader can keep typing without reaching for the mouse. */
    clear() {
        this.value.set('');
        this.inputElement().nativeElement.focus();
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiSearchFieldComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiSearchFieldComponent, isStandalone: true, selector: "tum-ui-search-field", inputs: { value: { classPropertyName: "value", publicName: "value", isSignal: true, isRequired: false, transformFunction: null }, placeholder: { classPropertyName: "placeholder", publicName: "placeholder", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { value: "valueChange" }, host: { classAttribute: "tum-ui-search-field" }, viewQueries: [{ propertyName: "inputElement", first: true, predicate: ["searchInput"], descendants: true, isSignal: true }], ngImport: i0, template: "<fa-icon [icon]=\"faMagnifyingGlass\" class=\"tum-ui-search-field-icon tum:text-muted\" aria-hidden=\"true\" />\n\n<input\n    #searchInput\n    tumUiInput\n    type=\"search\"\n    [tumUiInputSize]=\"size()\"\n    [value]=\"value()\"\n    [disabled]=\"disabled()\"\n    [placeholder]=\"placeholder() | tumUiTranslate\"\n    [attr.aria-label]=\"accessibleNameKey() | tumUiTranslate\"\n    (input)=\"onInput(searchInput.value)\"\n/>\n\n@if (value()) {\n    <button\n        type=\"button\"\n        class=\"tum-ui-search-field-clear tum:cursor-pointer tum:appearance-none tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text\"\n        [disabled]=\"disabled()\"\n        [attr.aria-label]=\"'tumUi.searchField.clear' | tumUiTranslate\"\n        (click)=\"clear()\"\n    >\n        <fa-icon [icon]=\"faXmark\" />\n    </button>\n}\n", styles: [":host{position:relative;display:block}.tum-ui-search-field-icon,.tum-ui-search-field-clear{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}.tum-ui-search-field-icon{inset-inline-start:calc(var(--tumaet-ui-spacing) * 3);pointer-events:none}.tum-ui-search-field-clear{inset-inline-end:calc(var(--tumaet-ui-spacing) * 2.5)}.tum-ui-input{width:100%;padding-inline-start:calc(var(--tumaet-ui-spacing) * 10);padding-inline-end:calc(var(--tumaet-ui-spacing) * 8)}.tum-ui-input::-webkit-search-cancel-button{display:none}\n"], dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }, { kind: "directive", type: TumUiInputDirective, selector: "input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]", inputs: ["tumUiInputSize", "tumUiInputInvalid", "tumUiInputId", "tumUiInputDescribedBy"] }, { kind: "pipe", type: TumUiTranslatePipe, name: "tumUiTranslate" }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiSearchFieldComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-search-field', imports: [FaIconComponent, TumUiInputDirective, TumUiTranslatePipe], host: { class: 'tum-ui-search-field' }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<fa-icon [icon]=\"faMagnifyingGlass\" class=\"tum-ui-search-field-icon tum:text-muted\" aria-hidden=\"true\" />\n\n<input\n    #searchInput\n    tumUiInput\n    type=\"search\"\n    [tumUiInputSize]=\"size()\"\n    [value]=\"value()\"\n    [disabled]=\"disabled()\"\n    [placeholder]=\"placeholder() | tumUiTranslate\"\n    [attr.aria-label]=\"accessibleNameKey() | tumUiTranslate\"\n    (input)=\"onInput(searchInput.value)\"\n/>\n\n@if (value()) {\n    <button\n        type=\"button\"\n        class=\"tum-ui-search-field-clear tum:cursor-pointer tum:appearance-none tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text\"\n        [disabled]=\"disabled()\"\n        [attr.aria-label]=\"'tumUi.searchField.clear' | tumUiTranslate\"\n        (click)=\"clear()\"\n    >\n        <fa-icon [icon]=\"faXmark\" />\n    </button>\n}\n", styles: [":host{position:relative;display:block}.tum-ui-search-field-icon,.tum-ui-search-field-clear{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}.tum-ui-search-field-icon{inset-inline-start:calc(var(--tumaet-ui-spacing) * 3);pointer-events:none}.tum-ui-search-field-clear{inset-inline-end:calc(var(--tumaet-ui-spacing) * 2.5)}.tum-ui-input{width:100%;padding-inline-start:calc(var(--tumaet-ui-spacing) * 10);padding-inline-end:calc(var(--tumaet-ui-spacing) * 8)}.tum-ui-input::-webkit-search-cancel-button{display:none}\n"] }]
        }], propDecorators: { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }, { type: i0.Output, args: ["valueChange"] }], placeholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "placeholder", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], inputElement: [{ type: i0.ViewChild, args: ['searchInput', { isSignal: true }] }] } });

const TRIGGER_SIZE = {
    small: 'tum:min-h-8 tum:py-1.5 tum:ps-2.5 tum:text-sm',
    default: 'tum:min-h-10 tum:py-2 tum:ps-3 tum:text-base',
    large: 'tum:min-h-12 tum:py-2.5 tum:ps-3.5 tum:text-lg',
};
let nextSelectId = 0;
const TYPEAHEAD_DEBOUNCE_MS = 500;
/** Single-value ControlValueAccessor backed by a listbox overlay. */
class TumUiSelectComponent {
    overlayService = inject(TumUiOverlayService);
    viewContainerRef = inject(ViewContainerRef);
    destroyRef = inject(DestroyRef);
    document = inject(DOCUMENT);
    injector = inject(Injector);
    formField = inject(TUM_UI_FORM_FIELD, { optional: true });
    options = input([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "options" }] : /* istanbul ignore next */ []));
    /** Property name used as the visible label for object options. */
    optionLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "optionLabel" }] : /* istanbul ignore next */ []));
    /** Property name written to the form value; omit it to write the option itself. */
    optionValue = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "optionValue" }] : /* istanbul ignore next */ []));
    placeholder = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "placeholder" }] : /* istanbul ignore next */ []));
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    showClear = input(false, { ...(ngDevMode ? { debugName: "showClear" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Adds a search field above the option list, for option sets too long to scan. */
    filter = input(false, { ...(ngDevMode ? { debugName: "filter" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /**
     * Comma-separated property names searched by the filter, for object options whose match should not be
     * limited to the visible label — `"name,login"`, say. Defaults to the label alone.
     */
    filterBy = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "filterBy" }] : /* istanbul ignore next */ []));
    filterPlaceholder = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "filterPlaceholder" }] : /* istanbul ignore next */ []));
    size = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "size" }] : /* istanbul ignore next */ []));
    /**
     * `id` of the trigger, so an external `<label for>` associates. Defaults to the id of an enclosing
     * `tum-ui-form-field`, and to a unique per-instance id outside one.
     */
    inputId = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "inputId" }] : /* istanbul ignore next */ []));
    name = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "name" }] : /* istanbul ignore next */ []));
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    clearAriaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "clearAriaLabel" }] : /* istanbul ignore next */ []));
    emptyMessage = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "emptyMessage" }] : /* istanbul ignore next */ []));
    filterAriaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "filterAriaLabel" }] : /* istanbul ignore next */ []));
    selectionChange = output();
    faChevronDown = faChevronDown;
    faCheck = faCheck;
    faXmark = faXmark;
    fallbackInputId = `tum-ui-select-${nextSelectId++}`;
    resolvedInputId = computed(() => this.formField?.explicitControlId() ?? this.inputId() ?? this.formField?.labelTargetId() ?? this.fallbackInputId, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "resolvedInputId" }] : /* istanbul ignore next */ []));
    describedBy = computed(() => this.formField?.describedBy() ?? null, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "describedBy" }] : /* istanbul ignore next */ []));
    isInvalid = computed(() => this.formField?.invalid() ?? false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isInvalid" }] : /* istanbul ignore next */ []));
    listboxId = `tum-ui-select-listbox-${nextSelectId++}`;
    trigger = viewChild.required('trigger', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "trigger" }] : /* istanbul ignore next */ []));
    panel = viewChild.required('panel', { ...(ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {}), read: TemplateRef });
    filterInput = viewChild('filterInput', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "filterInput" }] : /* istanbul ignore next */ []));
    overlayRef;
    isOpen = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isOpen" }] : /* istanbul ignore next */ []));
    activeIndex = signal(-1, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "activeIndex" }] : /* istanbul ignore next */ []));
    filterText = signal('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "filterText" }] : /* istanbul ignore next */ []));
    selectedValue = signal(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "selectedValue" }] : /* istanbul ignore next */ []));
    disabledByForm = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "disabledByForm" }] : /* istanbul ignore next */ []));
    onChangeCallback = () => { };
    onTouchedCallback = () => { };
    isDisabled = computed(() => this.disabled() || this.disabledByForm(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []));
    selectedOption = computed(() => {
        const current = this.selectedValue();
        if (current === undefined || current === null) {
            return undefined;
        }
        return this.options().find((option) => this.valuesMatch(this.resolveValue(option), current));
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "selectedOption" }] : /* istanbul ignore next */ []));
    /**
     * The options the panel shows. Everything index-based - the key manager, `aria-activedescendant`, the
     * option ids and every keyboard action - runs over this list rather than `options()`, so an index can
     * never point at an option the user cannot see.
     */
    /** Whether a query is currently narrowing the list, rather than merely present. */
    isFiltering = computed(() => this.filter() && this.filterText().trim().length > 0, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isFiltering" }] : /* istanbul ignore next */ []));
    visibleOptions = computed(() => {
        if (!this.isFiltering()) {
            return this.options();
        }
        const query = this.filterText().trim().toLocaleLowerCase();
        return this.options().filter((option) => this.filterFields(option).some((field) => field.toLocaleLowerCase().includes(query)));
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "visibleOptions" }] : /* istanbul ignore next */ []));
    hasSelection = computed(() => this.selectedOption() !== undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hasSelection" }] : /* istanbul ignore next */ []));
    displayLabel = computed(() => {
        const option = this.selectedOption();
        return option !== undefined ? this.label(option) : (this.placeholder() ?? '');
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "displayLabel" }] : /* istanbul ignore next */ []));
    showClearButton = computed(() => this.showClear() && this.hasSelection() && !this.isDisabled(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "showClearButton" }] : /* istanbul ignore next */ []));
    triggerClasses = computed(() => `${this.buildTriggerClasses()} ${this.showClearButton() ? 'tum:pe-17' : 'tum:pe-10'}`, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "triggerClasses" }] : /* istanbul ignore next */ []));
    activeOptionId = computed(() => (this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : undefined), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "activeOptionId" }] : /* istanbul ignore next */ []));
    keyManagerOptions = computed(() => this.visibleOptions().map((option) => ({ getLabel: () => this.label(option) })), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "keyManagerOptions" }] : /* istanbul ignore next */ []));
    keyManager = new ListKeyManager(this.keyManagerOptions, this.injector).withVerticalOrientation().withHomeAndEnd().withTypeAhead(TYPEAHEAD_DEBOUNCE_MS);
    typeaheadSequence = '';
    pendingFilterFocus = false;
    typeaheadReset;
    constructor() {
        // Tell an enclosing field which id to label whenever this control was given one of its own.
        effect(() => {
            const ownId = this.inputId();
            if (ownId) {
                this.formField?.adoptControlId(ownId);
            }
        });
        this.destroyRef.onDestroy(() => {
            this.overlayRef?.dispose();
            this.resetTypeahead();
            this.keyManager.destroy();
        });
        this.keyManager.change.subscribe((index) => {
            this.activeIndex.set(index);
            this.scrollOptionIntoView(index);
        });
        effect(() => {
            if (this.isDisabled()) {
                this.close();
            }
        });
        // Focus lands on the search field when one is shown, so typing filters the list rather than running
        // the trigger's typeahead.
        afterRenderEffect(() => {
            const field = this.filterInput()?.nativeElement;
            if (this.pendingFilterFocus && field) {
                this.pendingFilterFocus = false;
                field.focus();
            }
        });
        effect(() => {
            const optionCount = this.visibleOptions().length;
            if (optionCount === 0) {
                this.keyManager.setActiveItem(-1);
            }
            else if (this.activeIndex() >= optionCount) {
                this.keyManager.setActiveItem(optionCount - 1);
            }
            else if (this.isOpen() && this.activeIndex() < 0) {
                this.keyManager.setFirstItemActive();
            }
        });
    }
    writeValue(value) {
        this.selectedValue.set(value ?? undefined);
    }
    registerOnChange(fn) {
        this.onChangeCallback = fn;
    }
    registerOnTouched(fn) {
        this.onTouchedCallback = fn;
    }
    setDisabledState(isDisabled) {
        this.disabledByForm.set(isDisabled);
    }
    label(option) {
        const key = this.optionLabel();
        const raw = key && option !== null && typeof option === 'object' ? option[key] : option;
        return this.toText(raw);
    }
    toText(value) {
        switch (typeof value) {
            case 'string':
                return value;
            case 'number':
            case 'boolean':
            case 'bigint':
                return String(value);
            default:
                return '';
        }
    }
    /** The strings the filter searches for one option: the named `filterBy` fields, or the visible label. */
    filterFields(option) {
        const keys = this.filterBy()
            ?.split(',')
            .map((key) => key.trim())
            .filter((key) => key.length > 0);
        if (!keys?.length || option === null || typeof option !== 'object') {
            return [this.label(option)];
        }
        return keys.map((key) => this.toText(option[key]));
    }
    resolveValue(option) {
        const key = this.optionValue();
        if (key && option !== null && typeof option === 'object') {
            return option[key];
        }
        return option;
    }
    valuesMatch(a, b) {
        return Object.is(a, b) || a === b;
    }
    isSelected(option) {
        const current = this.selectedValue();
        if (current === undefined || current === null) {
            return false;
        }
        return this.valuesMatch(this.resolveValue(option), current);
    }
    optionId(index) {
        return `${this.listboxId}-option-${index}`;
    }
    toggle() {
        if (this.isDisabled()) {
            return;
        }
        if (this.isOpen()) {
            this.close();
        }
        else {
            this.open();
        }
    }
    open() {
        if (this.isOpen() || this.isDisabled()) {
            return;
        }
        const selectedIndex = this.visibleOptions().findIndex((option) => this.isSelected(option));
        const initialIndex = selectedIndex >= 0 ? selectedIndex : this.visibleOptions().length > 0 ? 0 : -1;
        this.keyManager.setActiveItem(initialIndex);
        const origin = this.trigger();
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, 'bottom', { hasBackdrop: true, matchOriginWidth: true });
        this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
        this.scrollOptionIntoView(initialIndex);
        this.overlayRef.backdropClick().subscribe(() => this.close());
        this.overlayRef.keydownEvents().subscribe((event) => {
            if (event.key === 'Escape') {
                this.close();
            }
        });
        this.isOpen.set(true);
        // The portal attaches above, but its input is only in the document once the view has been rendered,
        // so the focus move is deferred to the render effect in the constructor.
        this.pendingFilterFocus = this.filter();
    }
    close(restoreFocus = true) {
        if (!this.isOpen()) {
            return;
        }
        this.overlayRef?.dispose();
        this.overlayRef = undefined;
        this.isOpen.set(false);
        this.filterText.set('');
        this.resetTypeahead();
        this.onTouchedCallback();
        if (restoreFocus && !this.isDisabled()) {
            this.trigger().nativeElement.focus();
        }
    }
    selectOption(option) {
        const value = this.resolveValue(option);
        this.selectedValue.set(value);
        this.onChangeCallback(value);
        this.selectionChange.emit(value);
        this.close();
    }
    clear(event) {
        event.stopPropagation();
        this.selectedValue.set(undefined);
        this.onChangeCallback(undefined);
        this.selectionChange.emit(undefined);
        this.onTouchedCallback();
        this.trigger().nativeElement.focus();
    }
    setActive(index) {
        this.keyManager.setActiveItem(index);
    }
    onTriggerKeydown(event) {
        if (this.isDisabled()) {
            return;
        }
        if (!this.isOpen()) {
            if (event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar') {
                event.preventDefault();
                this.open();
                return;
            }
            if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
                event.preventDefault();
                this.open();
                return;
            }
            if (event.key === 'Home' || event.key === 'End') {
                event.preventDefault();
                this.open();
                this.setActive(event.key === 'Home' ? 0 : this.visibleOptions().length - 1);
                return;
            }
            if (event.key.length === 1 && event.key !== ' ' && !event.ctrlKey && !event.metaKey && !event.altKey) {
                this.open();
                // With a search field the character belongs in it, and focus is moving there anyway.
                if (!this.filter()) {
                    this.handleTypeahead(event);
                }
            }
            return;
        }
        const count = this.visibleOptions().length;
        switch (event.key) {
            case 'Enter':
            case ' ':
            case 'Spacebar':
                event.preventDefault();
                if (this.activeIndex() >= 0 && this.activeIndex() < count) {
                    this.selectOption(this.visibleOptions()[this.activeIndex()]);
                }
                break;
            case 'Escape':
                this.close();
                break;
            case 'Tab':
                if (this.activeIndex() >= 0 && this.activeIndex() < count) {
                    this.selectOption(this.visibleOptions()[this.activeIndex()]);
                }
                else {
                    this.close(false);
                }
                break;
            default:
                if (event.key.length === 1 && event.key !== ' ' && !event.ctrlKey && !event.metaKey && !event.altKey) {
                    this.handleTypeahead(event);
                }
                else {
                    this.keyManager.onKeydown(event);
                }
        }
    }
    onFilterInput(event) {
        this.filterText.set(event.target.value);
        // The previous active option may have been filtered away, so start again at the top of what is left.
        this.keyManager.setActiveItem(this.visibleOptions().length > 0 ? 0 : -1);
    }
    /**
     * Keys typed in the search field. Everything that moves or commits the selection is forwarded to the
     * same handling the trigger uses; the rest is left to the input.
     */
    onFilterKeydown(event) {
        const navigationKeys = ['ArrowDown', 'ArrowUp', 'Home', 'End', 'Enter', 'Escape', 'Tab'];
        if (!navigationKeys.includes(event.key)) {
            return;
        }
        if (event.key === 'Home' || event.key === 'End') {
            // Home and End belong to the text field while the user is editing the query.
            return;
        }
        this.onTriggerKeydown(event);
    }
    handleTypeahead(event) {
        const character = event.key.toLocaleLowerCase();
        const repeatsSequence = this.typeaheadSequence.length > 0 && [...this.typeaheadSequence].every((value) => value === character);
        clearTimeout(this.typeaheadReset);
        if (repeatsSequence) {
            this.keyManager.cancelTypeahead();
            const options = this.keyManagerOptions();
            const start = Math.max(this.activeIndex(), -1);
            const nextMatch = options.findIndex((_, offset) => options[(start + offset + 1) % options.length]?.getLabel().toLocaleLowerCase().startsWith(character));
            if (nextMatch >= 0) {
                this.keyManager.setActiveItem((start + nextMatch + 1) % options.length);
            }
            this.typeaheadSequence = character;
        }
        else {
            this.typeaheadSequence += character;
            this.keyManager.onKeydown(event);
        }
        this.typeaheadReset = setTimeout(() => {
            this.typeaheadSequence = '';
        }, TYPEAHEAD_DEBOUNCE_MS);
    }
    resetTypeahead() {
        this.keyManager.cancelTypeahead();
        clearTimeout(this.typeaheadReset);
        this.typeaheadSequence = '';
    }
    scrollOptionIntoView(index) {
        this.document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: 'nearest' });
    }
    buildTriggerClasses() {
        const base = 'tum-ui-select-trigger tum:box-border tum:flex tum:w-full tum:items-center tum:border tum:text-start tum:transition-colors';
        const size = TRIGGER_SIZE[this.size() ?? 'default'];
        let state;
        if (this.isDisabled()) {
            state = 'tum:cursor-default tum:bg-disabled-background tum:text-disabled tum:border-control-border';
        }
        else if (this.isInvalid()) {
            state = `tum:cursor-pointer tum:bg-control-background tum:text-text tum:border-state-danger`;
        }
        else if (this.isOpen()) {
            state = 'tum:cursor-pointer tum:bg-control-background tum:text-text tum:border-primary';
        }
        else {
            state = 'tum:cursor-pointer tum:bg-control-background tum:text-text tum:border-control-border tum:hover:border-control-border-hover';
        }
        return `${base} ${size} ${state}`;
    }
    optionClasses(option, index) {
        const base = 'tum-ui-select-option tum:flex tum:cursor-pointer tum:items-center tum:px-3 tum:py-2';
        const active = this.activeIndex() === index;
        if (this.isSelected(option)) {
            const background = active ? 'tum:bg-highlight-focus-background' : 'tum:bg-highlight-background';
            return `${base} tum:text-highlight ${background}`;
        }
        const activeState = active ? ' tum:bg-highlight-focus-background tum:text-highlight' : '';
        return `${base} tum:text-text tum:hover:bg-hover-background tum:hover:text-text-hover${activeState}`;
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiSelectComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiSelectComponent, isStandalone: true, selector: "tum-ui-select", inputs: { options: { classPropertyName: "options", publicName: "options", isSignal: true, isRequired: false, transformFunction: null }, optionLabel: { classPropertyName: "optionLabel", publicName: "optionLabel", isSignal: true, isRequired: false, transformFunction: null }, optionValue: { classPropertyName: "optionValue", publicName: "optionValue", isSignal: true, isRequired: false, transformFunction: null }, placeholder: { classPropertyName: "placeholder", publicName: "placeholder", isSignal: true, isRequired: false, transformFunction: null }, disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, showClear: { classPropertyName: "showClear", publicName: "showClear", isSignal: true, isRequired: false, transformFunction: null }, filter: { classPropertyName: "filter", publicName: "filter", isSignal: true, isRequired: false, transformFunction: null }, filterBy: { classPropertyName: "filterBy", publicName: "filterBy", isSignal: true, isRequired: false, transformFunction: null }, filterPlaceholder: { classPropertyName: "filterPlaceholder", publicName: "filterPlaceholder", isSignal: true, isRequired: false, transformFunction: null }, size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null }, inputId: { classPropertyName: "inputId", publicName: "inputId", isSignal: true, isRequired: false, transformFunction: null }, name: { classPropertyName: "name", publicName: "name", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, clearAriaLabel: { classPropertyName: "clearAriaLabel", publicName: "clearAriaLabel", isSignal: true, isRequired: false, transformFunction: null }, emptyMessage: { classPropertyName: "emptyMessage", publicName: "emptyMessage", isSignal: true, isRequired: false, transformFunction: null }, filterAriaLabel: { classPropertyName: "filterAriaLabel", publicName: "filterAriaLabel", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { selectionChange: "selectionChange" }, host: { classAttribute: "tum-ui-select" }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectComponent), multi: true }], viewQueries: [{ propertyName: "trigger", first: true, predicate: ["trigger"], descendants: true, isSignal: true }, { propertyName: "panel", first: true, predicate: ["panel"], descendants: true, read: TemplateRef, isSignal: true }, { propertyName: "filterInput", first: true, predicate: ["filterInput"], descendants: true, isSignal: true }], ngImport: i0, template: "<div class=\"tum:relative tum:inline-flex tum:w-full\">\n    <button\n        #trigger\n        type=\"button\"\n        [id]=\"resolvedInputId()\"\n        [class]=\"triggerClasses()\"\n        [disabled]=\"isDisabled()\"\n        [attr.name]=\"name()\"\n        role=\"combobox\"\n        aria-haspopup=\"listbox\"\n        aria-autocomplete=\"none\"\n        [attr.aria-expanded]=\"isOpen() ? 'true' : 'false'\"\n        [attr.aria-controls]=\"isOpen() ? listboxId : null\"\n        [attr.aria-activedescendant]=\"isOpen() ? activeOptionId() : null\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-describedby]=\"describedBy()\"\n        [attr.aria-invalid]=\"isInvalid() ? 'true' : null\"\n        (click)=\"toggle()\"\n        (keydown)=\"onTriggerKeydown($event)\"\n    >\n        <span class=\"tum:flex-1 tum:truncate\" [class]=\"!hasSelection() ? 'tum:text-muted' : ''\">{{ displayLabel() }}</span>\n    </button>\n    <span class=\"tum:pointer-events-none tum:absolute tum:inset-y-0 tum:end-0 tum:flex tum:w-10 tum:items-center tum:justify-center tum:text-muted\">\n        <fa-icon [icon]=\"faChevronDown\" />\n    </span>\n\n    @if (showClearButton()) {\n        <button\n            type=\"button\"\n            class=\"tum:absolute tum:inset-y-0 tum:end-10 tum:flex tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text-hover\"\n            (click)=\"clear($event)\"\n            [attr.aria-label]=\"clearAriaLabel() ?? ('tumUi.select.clear' | tumUiTranslate)\"\n        >\n            <fa-icon [icon]=\"faXmark\" />\n        </button>\n    }\n</div>\n\n<ng-template #panel>\n    <div class=\"tum-ui-select-panel tum:box-border tum:w-full tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-md\">\n        @if (filter()) {\n            <div class=\"tum:border-b tum:border-border tum:p-1\">\n                <input\n                    #filterInput\n                    type=\"text\"\n                    class=\"tum-ui-select-filter tum:box-border tum:w-full tum:appearance-none tum:rounded-sm tum:border tum:border-control-border tum:bg-control-background tum:px-2 tum:py-1.5 tum:text-sm tum:text-text tum:placeholder:text-muted tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus\"\n                    [value]=\"filterText()\"\n                    [attr.placeholder]=\"filterPlaceholder() ?? ('tumUi.select.filter' | tumUiTranslate)\"\n                    [attr.aria-label]=\"filterAriaLabel() ?? ('tumUi.select.filter' | tumUiTranslate)\"\n                    [attr.aria-controls]=\"listboxId\"\n                    [attr.aria-activedescendant]=\"activeOptionId()\"\n                    (input)=\"onFilterInput($event)\"\n                    (keydown)=\"onFilterKeydown($event)\"\n                />\n            </div>\n        }\n        <ul\n            [id]=\"listboxId\"\n            role=\"listbox\"\n            [attr.aria-label]=\"ariaLabel()\"\n            class=\"tum:m-0 tum:flex tum:max-h-60 tum:list-none tum:flex-col tum:gap-0.5 tum:overflow-y-auto tum:p-1 tum:outline-none\"\n        >\n            @for (option of visibleOptions(); track $index; let i = $index) {\n                <li\n                    [id]=\"optionId(i)\"\n                    role=\"option\"\n                    [attr.aria-selected]=\"isSelected(option) ? 'true' : 'false'\"\n                    [class]=\"optionClasses(option, i)\"\n                    (click)=\"selectOption(option)\"\n                    (mouseenter)=\"setActive(i)\"\n                >\n                    <span class=\"tum:flex-1 tum:truncate\">{{ label(option) }}</span>\n                    @if (isSelected(option)) {\n                        <fa-icon [icon]=\"faCheck\" class=\"tum:ms-2 tum:shrink-0\" />\n                    }\n                </li>\n            } @empty {\n                <li role=\"option\" aria-selected=\"false\" aria-disabled=\"true\" class=\"tum:px-3 tum:py-2 tum:text-muted\">\n                    {{ isFiltering() ? ('tumUi.select.noResults' | tumUiTranslate) : (emptyMessage() ?? ('tumUi.select.empty' | tumUiTranslate)) }}\n                </li>\n            }\n        </ul>\n    </div>\n</ng-template>\n", styles: [":host{display:inline-flex;max-width:100%}.tum-ui-select-trigger{border-radius:var(--tumaet-ui-radius-md);appearance:none}.tum-ui-select-trigger:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-trigger:disabled{opacity:1}\n"], dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }, { kind: "pipe", type: TumUiTranslatePipe, name: "tumUiTranslate" }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiSelectComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-select', imports: [FaIconComponent, TumUiTranslatePipe], host: {
                        // The application stylesheet excludes TUM UI controls from the JHipster validity accent by this class.
                        class: 'tum-ui-select',
                    }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum:relative tum:inline-flex tum:w-full\">\n    <button\n        #trigger\n        type=\"button\"\n        [id]=\"resolvedInputId()\"\n        [class]=\"triggerClasses()\"\n        [disabled]=\"isDisabled()\"\n        [attr.name]=\"name()\"\n        role=\"combobox\"\n        aria-haspopup=\"listbox\"\n        aria-autocomplete=\"none\"\n        [attr.aria-expanded]=\"isOpen() ? 'true' : 'false'\"\n        [attr.aria-controls]=\"isOpen() ? listboxId : null\"\n        [attr.aria-activedescendant]=\"isOpen() ? activeOptionId() : null\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-describedby]=\"describedBy()\"\n        [attr.aria-invalid]=\"isInvalid() ? 'true' : null\"\n        (click)=\"toggle()\"\n        (keydown)=\"onTriggerKeydown($event)\"\n    >\n        <span class=\"tum:flex-1 tum:truncate\" [class]=\"!hasSelection() ? 'tum:text-muted' : ''\">{{ displayLabel() }}</span>\n    </button>\n    <span class=\"tum:pointer-events-none tum:absolute tum:inset-y-0 tum:end-0 tum:flex tum:w-10 tum:items-center tum:justify-center tum:text-muted\">\n        <fa-icon [icon]=\"faChevronDown\" />\n    </span>\n\n    @if (showClearButton()) {\n        <button\n            type=\"button\"\n            class=\"tum:absolute tum:inset-y-0 tum:end-10 tum:flex tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text-hover\"\n            (click)=\"clear($event)\"\n            [attr.aria-label]=\"clearAriaLabel() ?? ('tumUi.select.clear' | tumUiTranslate)\"\n        >\n            <fa-icon [icon]=\"faXmark\" />\n        </button>\n    }\n</div>\n\n<ng-template #panel>\n    <div class=\"tum-ui-select-panel tum:box-border tum:w-full tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-md\">\n        @if (filter()) {\n            <div class=\"tum:border-b tum:border-border tum:p-1\">\n                <input\n                    #filterInput\n                    type=\"text\"\n                    class=\"tum-ui-select-filter tum:box-border tum:w-full tum:appearance-none tum:rounded-sm tum:border tum:border-control-border tum:bg-control-background tum:px-2 tum:py-1.5 tum:text-sm tum:text-text tum:placeholder:text-muted tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus\"\n                    [value]=\"filterText()\"\n                    [attr.placeholder]=\"filterPlaceholder() ?? ('tumUi.select.filter' | tumUiTranslate)\"\n                    [attr.aria-label]=\"filterAriaLabel() ?? ('tumUi.select.filter' | tumUiTranslate)\"\n                    [attr.aria-controls]=\"listboxId\"\n                    [attr.aria-activedescendant]=\"activeOptionId()\"\n                    (input)=\"onFilterInput($event)\"\n                    (keydown)=\"onFilterKeydown($event)\"\n                />\n            </div>\n        }\n        <ul\n            [id]=\"listboxId\"\n            role=\"listbox\"\n            [attr.aria-label]=\"ariaLabel()\"\n            class=\"tum:m-0 tum:flex tum:max-h-60 tum:list-none tum:flex-col tum:gap-0.5 tum:overflow-y-auto tum:p-1 tum:outline-none\"\n        >\n            @for (option of visibleOptions(); track $index; let i = $index) {\n                <li\n                    [id]=\"optionId(i)\"\n                    role=\"option\"\n                    [attr.aria-selected]=\"isSelected(option) ? 'true' : 'false'\"\n                    [class]=\"optionClasses(option, i)\"\n                    (click)=\"selectOption(option)\"\n                    (mouseenter)=\"setActive(i)\"\n                >\n                    <span class=\"tum:flex-1 tum:truncate\">{{ label(option) }}</span>\n                    @if (isSelected(option)) {\n                        <fa-icon [icon]=\"faCheck\" class=\"tum:ms-2 tum:shrink-0\" />\n                    }\n                </li>\n            } @empty {\n                <li role=\"option\" aria-selected=\"false\" aria-disabled=\"true\" class=\"tum:px-3 tum:py-2 tum:text-muted\">\n                    {{ isFiltering() ? ('tumUi.select.noResults' | tumUiTranslate) : (emptyMessage() ?? ('tumUi.select.empty' | tumUiTranslate)) }}\n                </li>\n            }\n        </ul>\n    </div>\n</ng-template>\n", styles: [":host{display:inline-flex;max-width:100%}.tum-ui-select-trigger{border-radius:var(--tumaet-ui-radius-md);appearance:none}.tum-ui-select-trigger:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-trigger:disabled{opacity:1}\n"] }]
        }], ctorParameters: () => [], propDecorators: { options: [{ type: i0.Input, args: [{ isSignal: true, alias: "options", required: false }] }], optionLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "optionLabel", required: false }] }], optionValue: [{ type: i0.Input, args: [{ isSignal: true, alias: "optionValue", required: false }] }], placeholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "placeholder", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], showClear: [{ type: i0.Input, args: [{ isSignal: true, alias: "showClear", required: false }] }], filter: [{ type: i0.Input, args: [{ isSignal: true, alias: "filter", required: false }] }], filterBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "filterBy", required: false }] }], filterPlaceholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "filterPlaceholder", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], name: [{ type: i0.Input, args: [{ isSignal: true, alias: "name", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], clearAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "clearAriaLabel", required: false }] }], emptyMessage: [{ type: i0.Input, args: [{ isSignal: true, alias: "emptyMessage", required: false }] }], filterAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "filterAriaLabel", required: false }] }], selectionChange: [{ type: i0.Output, args: ["selectionChange"] }], trigger: [{ type: i0.ViewChild, args: ['trigger', { isSignal: true }] }], panel: [{ type: i0.ViewChild, args: ['panel', { ...{ read: TemplateRef }, isSignal: true }] }], filterInput: [{ type: i0.ViewChild, args: ['filterInput', { isSignal: true }] }] } });

function isRecord(value) {
    return value !== null && typeof value === 'object';
}
function displayLabel(value) {
    return typeof value === 'string' || typeof value === 'number' || typeof value === 'bigint' || typeof value === 'boolean' ? String(value) : undefined;
}
/** ControlValueAccessor for choosing one value from a small, persistent option set. */
class TumUiSelectButtonComponent {
    /** Options that yield neither a primitive label nor an item template are omitted. */
    options = input([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "options" }] : /* istanbul ignore next */ []));
    /** Object property used as the visible option label. */
    optionLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "optionLabel" }] : /* istanbul ignore next */ []));
    /** Object property written to the form value; omit it to write the option. */
    optionValue = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "optionValue" }] : /* istanbul ignore next */ []));
    size = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "size" }] : /* istanbul ignore next */ []));
    /** Allows the selected option to be toggled back to `undefined`. */
    allowEmpty = input(true, { ...(ngDevMode ? { debugName: "allowEmpty" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Optional presentation template; the option remains its implicit context value. */
    itemTemplate = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "itemTemplate" }] : /* istanbul ignore next */ []));
    /** Emits the selected value, or `undefined` when cleared. */
    changed = output();
    value = signal(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "value" }] : /* istanbul ignore next */ []));
    cvaDisabled = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "cvaDisabled" }] : /* istanbul ignore next */ []));
    effectiveDisabled = computed(() => this.disabled() || this.cvaDisabled(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "effectiveDisabled" }] : /* istanbul ignore next */ []));
    onChange = () => { };
    onTouched = () => { };
    normalizedOptions = computed(() => {
        const labelKey = this.optionLabel();
        const valueKey = this.optionValue();
        const current = this.value();
        return this.options().flatMap((raw) => {
            const record = isRecord(raw) ? raw : undefined;
            if ((labelKey !== undefined || valueKey !== undefined) && !record) {
                return [];
            }
            const value = valueKey !== undefined ? record[valueKey] : raw;
            const labelValue = labelKey !== undefined ? record[labelKey] : raw;
            const label = displayLabel(labelValue);
            return label === undefined && !this.itemTemplate() ? [] : [{ raw, value, label: label ?? '', selected: value === current }];
        });
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "normalizedOptions" }] : /* istanbul ignore next */ []));
    optionClasses(selected) {
        const sizeClass = this.size() === 'small' ? 'tum:text-sm' : this.size() === 'large' ? 'tum:text-lg' : 'tum:text-base';
        const state = selected ? 'tum:bg-primary tum:text-primary-contrast tum:border-primary' : 'tum:bg-hover-background tum:text-text tum:border-border';
        return `tum-ui-select-button-option ${sizeClass} ${state} ${this.effectiveDisabled() ? 'tum:opacity-60' : ''}`.trim();
    }
    select(option) {
        if (this.effectiveDisabled()) {
            return;
        }
        let next;
        if (option.selected) {
            if (!this.allowEmpty()) {
                return;
            }
            next = undefined;
        }
        else {
            next = option.value;
        }
        this.value.set(next);
        this.onChange(next);
        this.onTouched();
        this.changed.emit(next);
    }
    writeValue(value) {
        this.value.set(value ?? undefined);
    }
    registerOnChange(fn) {
        this.onChange = fn;
    }
    registerOnTouched(fn) {
        this.onTouched = fn;
    }
    setDisabledState(isDisabled) {
        this.cvaDisabled.set(isDisabled);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiSelectButtonComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiSelectButtonComponent, isStandalone: true, selector: "tum-ui-select-button", inputs: { options: { classPropertyName: "options", publicName: "options", isSignal: true, isRequired: false, transformFunction: null }, optionLabel: { classPropertyName: "optionLabel", publicName: "optionLabel", isSignal: true, isRequired: false, transformFunction: null }, optionValue: { classPropertyName: "optionValue", publicName: "optionValue", isSignal: true, isRequired: false, transformFunction: null }, size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null }, allowEmpty: { classPropertyName: "allowEmpty", publicName: "allowEmpty", isSignal: true, isRequired: false, transformFunction: null }, disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, itemTemplate: { classPropertyName: "itemTemplate", publicName: "itemTemplate", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { changed: "changed" }, host: { attributes: { "role": "group" }, properties: { "attr.aria-disabled": "effectiveDisabled() || null" }, classAttribute: "tum-ui-select-button" }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectButtonComponent), multi: true }], ngImport: i0, template: "@for (option of normalizedOptions(); track option.value) {\n    <button type=\"button\" [class]=\"optionClasses(option.selected)\" [attr.aria-pressed]=\"option.selected\" [disabled]=\"effectiveDisabled()\" (click)=\"select(option)\">\n        @if (itemTemplate(); as template) {\n            <ng-container [ngTemplateOutlet]=\"template\" [ngTemplateOutletContext]=\"{ $implicit: option.raw }\" />\n        } @else {\n            {{ option.label }}\n        }\n    </button>\n}\n", styles: [":host{display:inline-flex;vertical-align:bottom;-webkit-user-select:none;user-select:none;border-radius:var(--tumaet-ui-radius-md);outline-color:transparent}.tum-ui-select-button-option{position:relative;appearance:none;display:inline-flex;align-items:center;justify-content:center;gap:calc(var(--tumaet-ui-spacing) * 2);padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 4);font-weight:500;white-space:nowrap;cursor:pointer;border-block-width:1px;border-inline-start-width:0;border-inline-end-width:1px;border-style:solid;border-radius:0;transition:background-color .2s,color .2s,border-color .2s,box-shadow .2s,outline-color .2s;outline-color:transparent}.tum-ui-select-button-option:first-child{border-inline-start-width:1px;border-start-start-radius:var(--tumaet-ui-radius-md);border-end-start-radius:var(--tumaet-ui-radius-md)}.tum-ui-select-button-option:last-child{border-start-end-radius:var(--tumaet-ui-radius-md);border-end-end-radius:var(--tumaet-ui-radius-md)}.tum-ui-select-button-option:disabled{cursor:default}.tum-ui-select-button-option:focus-visible{position:relative;z-index:1;outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-button-option:after{content:\"\";position:absolute;inset:0;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-select-button-option:not(:disabled):hover:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}@media(forced-colors:active){.tum-ui-select-button-option[aria-pressed=true]{color:HighlightText;background:Highlight;border-color:Highlight}}\n"], dependencies: [{ kind: "directive", type: NgTemplateOutlet, selector: "[ngTemplateOutlet]", inputs: ["ngTemplateOutletContext", "ngTemplateOutlet", "ngTemplateOutletInjector"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiSelectButtonComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-select-button', imports: [NgTemplateOutlet], host: {
                        role: 'group',
                        class: 'tum-ui-select-button',
                        '[attr.aria-disabled]': 'effectiveDisabled() || null',
                    }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectButtonComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "@for (option of normalizedOptions(); track option.value) {\n    <button type=\"button\" [class]=\"optionClasses(option.selected)\" [attr.aria-pressed]=\"option.selected\" [disabled]=\"effectiveDisabled()\" (click)=\"select(option)\">\n        @if (itemTemplate(); as template) {\n            <ng-container [ngTemplateOutlet]=\"template\" [ngTemplateOutletContext]=\"{ $implicit: option.raw }\" />\n        } @else {\n            {{ option.label }}\n        }\n    </button>\n}\n", styles: [":host{display:inline-flex;vertical-align:bottom;-webkit-user-select:none;user-select:none;border-radius:var(--tumaet-ui-radius-md);outline-color:transparent}.tum-ui-select-button-option{position:relative;appearance:none;display:inline-flex;align-items:center;justify-content:center;gap:calc(var(--tumaet-ui-spacing) * 2);padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 4);font-weight:500;white-space:nowrap;cursor:pointer;border-block-width:1px;border-inline-start-width:0;border-inline-end-width:1px;border-style:solid;border-radius:0;transition:background-color .2s,color .2s,border-color .2s,box-shadow .2s,outline-color .2s;outline-color:transparent}.tum-ui-select-button-option:first-child{border-inline-start-width:1px;border-start-start-radius:var(--tumaet-ui-radius-md);border-end-start-radius:var(--tumaet-ui-radius-md)}.tum-ui-select-button-option:last-child{border-start-end-radius:var(--tumaet-ui-radius-md);border-end-end-radius:var(--tumaet-ui-radius-md)}.tum-ui-select-button-option:disabled{cursor:default}.tum-ui-select-button-option:focus-visible{position:relative;z-index:1;outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-button-option:after{content:\"\";position:absolute;inset:0;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-select-button-option:not(:disabled):hover:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}@media(forced-colors:active){.tum-ui-select-button-option[aria-pressed=true]{color:HighlightText;background:Highlight;border-color:Highlight}}\n"] }]
        }], propDecorators: { options: [{ type: i0.Input, args: [{ isSignal: true, alias: "options", required: false }] }], optionLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "optionLabel", required: false }] }], optionValue: [{ type: i0.Input, args: [{ isSignal: true, alias: "optionValue", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], allowEmpty: [{ type: i0.Input, args: [{ isSignal: true, alias: "allowEmpty", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], itemTemplate: [{ type: i0.Input, args: [{ isSignal: true, alias: "itemTemplate", required: false }] }], changed: [{ type: i0.Output, args: ["changed"] }] } });

const SIZE_PADDING = {
    small: 'tum:[&_thead_th]:px-2 tum:[&_thead_th]:py-1.5 tum:[&_tbody_td]:px-2 tum:[&_tbody_td]:py-1.5',
    normal: 'tum:[&_thead_th]:px-4 tum:[&_thead_th]:py-3 tum:[&_tbody_td]:px-4 tum:[&_tbody_td]:py-3',
    large: 'tum:[&_thead_th]:px-5 tum:[&_thead_th]:py-4 tum:[&_tbody_td]:px-5 tum:[&_tbody_td]:py-4',
};
const HEADER_CLASSES = 'tum:[&_thead_th]:text-start tum:[&_thead_th]:font-semibold tum:[&_thead_th]:whitespace-nowrap ' +
    'tum:[&_thead_th]:bg-content-background tum:[&_thead_th]:text-text ' +
    'tum:[&_thead_th]:border-b tum:[&_thead_th]:border-border';
const BODY_CLASSES = 'tum:[&_tbody_td]:text-text tum:[&_tbody_td]:border-b tum:[&_tbody_td]:border-border';
const STRIPED_CLASSES = 'tum:[&_tbody_tr:nth-child(odd)]:bg-table-striped-background';
const HOVER_CLASSES = 'tum:[&_tbody_tr:hover]:bg-hover-background';
const SCROLLABLE_CLASSES = 'tum:[&_thead_th]:sticky tum:[&_thead_th]:top-0 tum:[&_thead_th]:z-10';
class TumUiTableDirective {
    size = input('normal', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    striped = input(false, { ...(ngDevMode ? { debugName: "striped" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    scrollable = input(false, { ...(ngDevMode ? { debugName: "scrollable" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    rowHover = input(false, { ...(ngDevMode ? { debugName: "rowHover" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    sortField = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "sortField" }] : /* istanbul ignore next */ []));
    sortOrder = input(1, { ...(ngDevMode ? { debugName: "sortOrder" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    defaultSortOrder = input(1, { ...(ngDevMode ? { debugName: "defaultSortOrder" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    sortChange = output();
    hostClasses = computed(() => {
        const parts = ['tum-ui-table tum:w-full tum:border-collapse tum:text-sm', SIZE_PADDING[this.size()], HEADER_CLASSES, BODY_CLASSES];
        if (this.striped()) {
            parts.push(STRIPED_CLASSES);
        }
        if (this.rowHover()) {
            parts.push(HOVER_CLASSES);
        }
        if (this.scrollable()) {
            parts.push(SCROLLABLE_CLASSES);
        }
        return parts.join(' ');
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    requestSort(field) {
        const order = this.sortField() === field ? this.sortOrder() * -1 : this.defaultSortOrder();
        this.sortChange.emit({ field, order });
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTableDirective, deps: [], target: i0.ɵɵFactoryTarget.Directive });
    static ɵdir = i0.ɵɵngDeclareDirective({ minVersion: "17.1.0", version: "22.1.5", type: TumUiTableDirective, isStandalone: true, selector: "table[tumUiTable]", inputs: { size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null }, striped: { classPropertyName: "striped", publicName: "striped", isSignal: true, isRequired: false, transformFunction: null }, scrollable: { classPropertyName: "scrollable", publicName: "scrollable", isSignal: true, isRequired: false, transformFunction: null }, rowHover: { classPropertyName: "rowHover", publicName: "rowHover", isSignal: true, isRequired: false, transformFunction: null }, sortField: { classPropertyName: "sortField", publicName: "sortField", isSignal: true, isRequired: false, transformFunction: null }, sortOrder: { classPropertyName: "sortOrder", publicName: "sortOrder", isSignal: true, isRequired: false, transformFunction: null }, defaultSortOrder: { classPropertyName: "defaultSortOrder", publicName: "defaultSortOrder", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { sortChange: "sortChange" }, host: { properties: { "class": "hostClasses()" } }, ngImport: i0 });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTableDirective, decorators: [{
            type: Directive,
            args: [{
                    selector: 'table[tumUiTable]',
                    host: {
                        '[class]': 'hostClasses()',
                    },
                }]
        }], propDecorators: { size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], striped: [{ type: i0.Input, args: [{ isSignal: true, alias: "striped", required: false }] }], scrollable: [{ type: i0.Input, args: [{ isSignal: true, alias: "scrollable", required: false }] }], rowHover: [{ type: i0.Input, args: [{ isSignal: true, alias: "rowHover", required: false }] }], sortField: [{ type: i0.Input, args: [{ isSignal: true, alias: "sortField", required: false }] }], sortOrder: [{ type: i0.Input, args: [{ isSignal: true, alias: "sortOrder", required: false }] }], defaultSortOrder: [{ type: i0.Input, args: [{ isSignal: true, alias: "defaultSortOrder", required: false }] }], sortChange: [{ type: i0.Output, args: ["sortChange"] }] } });

class TumUiTableSortableColumnComponent {
    field = input.required({ ...(ngDevMode ? { debugName: "field" } : /* istanbul ignore next */ {}), alias: 'tumUiSortableColumn' });
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    table = inject(TumUiTableDirective);
    direction = computed(() => {
        if (this.table.sortField() !== this.field()) {
            return 'none';
        }
        const order = this.table.sortOrder();
        if (order === 0) {
            return 'none';
        }
        return order < 0 ? 'desc' : 'asc';
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "direction" }] : /* istanbul ignore next */ []));
    ariaSort = computed(() => {
        switch (this.direction()) {
            case 'asc':
                return 'ascending';
            case 'desc':
                return 'descending';
            default:
                return 'none';
        }
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaSort" }] : /* istanbul ignore next */ []));
    sortIcon = computed(() => {
        switch (this.direction()) {
            case 'asc':
                return faSortUp;
            case 'desc':
                return faSortDown;
            default:
                return faSort;
        }
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "sortIcon" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => (this.disabled() ? '' : 'tum:cursor-pointer tum:select-none tum:hover:bg-hover-background'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    onActivate() {
        this.table.requestSort(this.field());
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTableSortableColumnComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiTableSortableColumnComponent, isStandalone: true, selector: "th[tumUiSortableColumn]", inputs: { field: { classPropertyName: "field", publicName: "tumUiSortableColumn", isSignal: true, isRequired: true, transformFunction: null }, disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "class": "hostClasses()", "attr.aria-sort": "ariaSort()" } }, ngImport: i0, template: "<button type=\"button\" class=\"tum-ui-sort-button\" [disabled]=\"disabled()\" (click)=\"onActivate()\">\n    <ng-content />\n    <fa-icon [icon]=\"sortIcon()\" class=\"tum-ui-sort-icon tum:text-muted\" aria-hidden=\"true\" />\n</button>\n", styles: [":host{white-space:nowrap}.tum-ui-sort-button{display:flex;align-items:center;width:100%;padding:0;border:0;background:transparent;color:inherit;font:inherit;text-align:inherit;cursor:pointer}.tum-ui-sort-button:disabled{cursor:default}.tum-ui-sort-icon{display:inline-block;width:calc(var(--tumaet-ui-spacing) * 3.5);height:calc(var(--tumaet-ui-spacing) * 3.5);margin-inline-start:calc(var(--tumaet-ui-spacing) * 2);vertical-align:middle;flex-shrink:0}\n"], dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTableSortableColumnComponent, decorators: [{
            type: Component,
            args: [{ selector: 'th[tumUiSortableColumn]', imports: [FaIconComponent], host: {
                        '[class]': 'hostClasses()',
                        '[attr.aria-sort]': 'ariaSort()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<button type=\"button\" class=\"tum-ui-sort-button\" [disabled]=\"disabled()\" (click)=\"onActivate()\">\n    <ng-content />\n    <fa-icon [icon]=\"sortIcon()\" class=\"tum-ui-sort-icon tum:text-muted\" aria-hidden=\"true\" />\n</button>\n", styles: [":host{white-space:nowrap}.tum-ui-sort-button{display:flex;align-items:center;width:100%;padding:0;border:0;background:transparent;color:inherit;font:inherit;text-align:inherit;cursor:pointer}.tum-ui-sort-button:disabled{cursor:default}.tum-ui-sort-icon{display:inline-block;width:calc(var(--tumaet-ui-spacing) * 3.5);height:calc(var(--tumaet-ui-spacing) * 3.5);margin-inline-start:calc(var(--tumaet-ui-spacing) * 2);vertical-align:middle;flex-shrink:0}\n"] }]
        }], propDecorators: { field: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiSortableColumn", required: true }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }] } });

const HEADER_PADDING = {
    small: 'tum:px-2 tum:py-1.5',
    normal: 'tum:px-4 tum:py-3',
    large: 'tum:px-5 tum:py-4',
};
/** Fixed-row-height virtual table for large in-memory collections. */
class TumUiTableVirtualScrollComponent {
    items = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "items" }] : /* istanbul ignore next */ []));
    /** Row height in CSS pixels used by the CDK fixed-size virtual-scroll strategy. */
    itemSize = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "itemSize" }] : /* istanbul ignore next */ []));
    rowTemplate = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "rowTemplate" }] : /* istanbul ignore next */ []));
    size = input('normal', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    striped = input(false, { ...(ngDevMode ? { debugName: "striped" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    rowHover = input(false, { ...(ngDevMode ? { debugName: "rowHover" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    scrollHeight = input('flex', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "scrollHeight" }] : /* istanbul ignore next */ []));
    minWidth = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "minWidth" }] : /* istanbul ignore next */ []));
    trackBy = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "trackBy" }] : /* istanbul ignore next */ []));
    ariaDescribedBy = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaDescribedBy" }] : /* istanbul ignore next */ []));
    isFlexHeight = computed(() => this.scrollHeight() === 'flex', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isFlexHeight" }] : /* istanbul ignore next */ []));
    viewportHeight = computed(() => (this.isFlexHeight() ? undefined : this.scrollHeight()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "viewportHeight" }] : /* istanbul ignore next */ []));
    effectiveTrackBy = computed(() => this.trackBy() ?? ((_, item) => item), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "effectiveTrackBy" }] : /* istanbul ignore next */ []));
    headerClasses = computed(() => {
        const base = 'tum-ui-vs-header tum:box-border tum:flex tum:text-sm tum:font-semibold tum:text-text tum:bg-content-background ' + 'tum:border-b tum:border-border';
        return `${base} ${HEADER_PADDING[this.size()]}`;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "headerClasses" }] : /* istanbul ignore next */ []));
    rowClasses = computed(() => {
        const base = 'tum-ui-vs-row tum:box-border tum:flex tum:items-center tum:text-sm tum:text-text tum:border-b tum:border-border';
        const hover = this.rowHover() ? ' tum:hover:bg-hover-background' : '';
        return `${base}${hover} ${HEADER_PADDING[this.size()]}`;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "rowClasses" }] : /* istanbul ignore next */ []));
    stripeClass(index) {
        return this.striped() && index % 2 === 0 ? ' tum:bg-table-striped-background' : '';
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTableVirtualScrollComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiTableVirtualScrollComponent, isStandalone: true, selector: "tum-ui-table-virtual-scroll", inputs: { items: { classPropertyName: "items", publicName: "items", isSignal: true, isRequired: true, transformFunction: null }, itemSize: { classPropertyName: "itemSize", publicName: "itemSize", isSignal: true, isRequired: true, transformFunction: null }, rowTemplate: { classPropertyName: "rowTemplate", publicName: "rowTemplate", isSignal: true, isRequired: true, transformFunction: null }, size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null }, striped: { classPropertyName: "striped", publicName: "striped", isSignal: true, isRequired: false, transformFunction: null }, rowHover: { classPropertyName: "rowHover", publicName: "rowHover", isSignal: true, isRequired: false, transformFunction: null }, scrollHeight: { classPropertyName: "scrollHeight", publicName: "scrollHeight", isSignal: true, isRequired: false, transformFunction: null }, minWidth: { classPropertyName: "minWidth", publicName: "minWidth", isSignal: true, isRequired: false, transformFunction: null }, trackBy: { classPropertyName: "trackBy", publicName: "trackBy", isSignal: true, isRequired: false, transformFunction: null }, ariaDescribedBy: { classPropertyName: "ariaDescribedBy", publicName: "ariaDescribedBy", isSignal: true, isRequired: false, transformFunction: null } }, ngImport: i0, template: "<div\n    role=\"table\"\n    class=\"tum-ui-vs-table tum:flex tum:min-h-0 tum:flex-col\"\n    [style.flex]=\"isFlexHeight() ? '1 1 0%' : null\"\n    [style.min-width]=\"minWidth()\"\n    [attr.aria-describedby]=\"ariaDescribedBy()\"\n    [attr.aria-rowcount]=\"items().length + 1\"\n>\n    <div role=\"row\" aria-rowindex=\"1\" [class]=\"headerClasses()\">\n        <ng-content />\n    </div>\n\n    <cdk-virtual-scroll-viewport\n        [itemSize]=\"itemSize()\"\n        class=\"tum-ui-vs-viewport\"\n        [style.flex]=\"isFlexHeight() ? '1 1 0%' : null\"\n        [style.min-height]=\"isFlexHeight() ? 0 : null\"\n        [style.height]=\"viewportHeight()\"\n        role=\"rowgroup\"\n        tabindex=\"0\"\n    >\n        <div\n            *cdkVirtualFor=\"let item of items(); let i = index; trackBy: effectiveTrackBy()\"\n            role=\"row\"\n            [attr.aria-rowindex]=\"i + 2\"\n            [class]=\"rowClasses() + stripeClass(i)\"\n            [style.height.px]=\"itemSize()\"\n        >\n            <ng-container [ngTemplateOutlet]=\"rowTemplate()\" [ngTemplateOutletContext]=\"{ $implicit: item, index: i }\" />\n        </div>\n    </cdk-virtual-scroll-viewport>\n</div>\n", styles: [":host{display:flex;flex-direction:column;width:100%;max-width:100%;height:100%;min-height:0;min-width:0;overflow-x:auto}.tum-ui-vs-viewport{width:100%}\n"], dependencies: [{ kind: "ngmodule", type: ScrollingModule }, { kind: "directive", type: i1$3.CdkFixedSizeVirtualScroll, selector: "cdk-virtual-scroll-viewport[itemSize]", inputs: ["itemSize", "minBufferPx", "maxBufferPx"] }, { kind: "directive", type: i1$3.CdkVirtualForOf, selector: "[cdkVirtualFor][cdkVirtualForOf]", inputs: ["cdkVirtualForOf", "cdkVirtualForTrackBy", "cdkVirtualForTemplate", "cdkVirtualForTemplateCacheSize"] }, { kind: "component", type: i1$3.CdkVirtualScrollViewport, selector: "cdk-virtual-scroll-viewport", inputs: ["orientation", "appendOnly"], outputs: ["scrolledIndexChange"] }, { kind: "directive", type: NgTemplateOutlet, selector: "[ngTemplateOutlet]", inputs: ["ngTemplateOutletContext", "ngTemplateOutlet", "ngTemplateOutletInjector"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTableVirtualScrollComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-table-virtual-scroll', imports: [ScrollingModule, NgTemplateOutlet], changeDetection: ChangeDetectionStrategy.OnPush, template: "<div\n    role=\"table\"\n    class=\"tum-ui-vs-table tum:flex tum:min-h-0 tum:flex-col\"\n    [style.flex]=\"isFlexHeight() ? '1 1 0%' : null\"\n    [style.min-width]=\"minWidth()\"\n    [attr.aria-describedby]=\"ariaDescribedBy()\"\n    [attr.aria-rowcount]=\"items().length + 1\"\n>\n    <div role=\"row\" aria-rowindex=\"1\" [class]=\"headerClasses()\">\n        <ng-content />\n    </div>\n\n    <cdk-virtual-scroll-viewport\n        [itemSize]=\"itemSize()\"\n        class=\"tum-ui-vs-viewport\"\n        [style.flex]=\"isFlexHeight() ? '1 1 0%' : null\"\n        [style.min-height]=\"isFlexHeight() ? 0 : null\"\n        [style.height]=\"viewportHeight()\"\n        role=\"rowgroup\"\n        tabindex=\"0\"\n    >\n        <div\n            *cdkVirtualFor=\"let item of items(); let i = index; trackBy: effectiveTrackBy()\"\n            role=\"row\"\n            [attr.aria-rowindex]=\"i + 2\"\n            [class]=\"rowClasses() + stripeClass(i)\"\n            [style.height.px]=\"itemSize()\"\n        >\n            <ng-container [ngTemplateOutlet]=\"rowTemplate()\" [ngTemplateOutletContext]=\"{ $implicit: item, index: i }\" />\n        </div>\n    </cdk-virtual-scroll-viewport>\n</div>\n", styles: [":host{display:flex;flex-direction:column;width:100%;max-width:100%;height:100%;min-height:0;min-width:0;overflow-x:auto}.tum-ui-vs-viewport{width:100%}\n"] }]
        }], propDecorators: { items: [{ type: i0.Input, args: [{ isSignal: true, alias: "items", required: true }] }], itemSize: [{ type: i0.Input, args: [{ isSignal: true, alias: "itemSize", required: true }] }], rowTemplate: [{ type: i0.Input, args: [{ isSignal: true, alias: "rowTemplate", required: true }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], striped: [{ type: i0.Input, args: [{ isSignal: true, alias: "striped", required: false }] }], rowHover: [{ type: i0.Input, args: [{ isSignal: true, alias: "rowHover", required: false }] }], scrollHeight: [{ type: i0.Input, args: [{ isSignal: true, alias: "scrollHeight", required: false }] }], minWidth: [{ type: i0.Input, args: [{ isSignal: true, alias: "minWidth", required: false }] }], trackBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "trackBy", required: false }] }], ariaDescribedBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaDescribedBy", required: false }] }] } });

const ACTIONS_COLUMN = '__tum_ui_actions__';
const SEARCH_DEBOUNCE_MS = 300;
const HIDE_BELOW_CLASSES = {
    sm: 'tum:hidden tum:sm:table-cell',
    md: 'tum:hidden tum:md:table-cell',
    lg: 'tum:hidden tum:lg:table-cell',
    xl: 'tum:hidden tum:xl:table-cell',
    '2xl': 'tum:hidden tum:2xl:table-cell',
};
/** Server-driven table whose consumer owns rows and responds to query changes. */
class TumUiTableComponent {
    /** Columns displayed in declaration order. Nested field paths use lodash path syntax. */
    columns = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "columns" }] : /* istanbul ignore next */ []));
    /** Rows for the current page. Sorting and filtering are not applied locally. */
    rows = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "rows" }] : /* istanbul ignore next */ []));
    totalRecords = input(0, { ...(ngDevMode ? { debugName: "totalRecords" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    loading = input(false, { ...(ngDevMode ? { debugName: "loading" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /** Optional action template receiving the row as its implicit value. */
    rowActions = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "rowActions" }] : /* istanbul ignore next */ []));
    /** Identity function forwarded to the CDK table. */
    trackBy = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "trackBy" }] : /* istanbul ignore next */ []));
    striped = input(false, { ...(ngDevMode ? { debugName: "striped" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    scrollable = input(false, { ...(ngDevMode ? { debugName: "scrollable" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    scrollHeight = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "scrollHeight" }] : /* istanbul ignore next */ []));
    showSearch = input(true, { ...(ngDevMode ? { debugName: "showSearch" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    searchPlaceholder = input('tumUi.table.searchPlaceholder', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "searchPlaceholder" }] : /* istanbul ignore next */ []));
    emptyMessage = input('tumUi.table.noResults', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "emptyMessage" }] : /* istanbul ignore next */ []));
    pageSize = input(50, { ...(ngDevMode ? { debugName: "pageSize" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    pageSizeOptions = input([10, 20, 50, 100, 200], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "pageSizeOptions" }] : /* istanbul ignore next */ []));
    showRowsPerPage = input(true, { ...(ngDevMode ? { debugName: "showRowsPerPage" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    showCurrentPageReport = input(true, { ...(ngDevMode ? { debugName: "showCurrentPageReport" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    initialSortField = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "initialSortField" }] : /* istanbul ignore next */ []));
    initialSortDirection = input('asc', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "initialSortDirection" }] : /* istanbul ignore next */ []));
    /** Requests a zero-based page with the active page size, sort, and search term. */
    dataRequest = output();
    ACTIONS_COLUMN = ACTIONS_COLUMN;
    faCircleQuestion = faCircleQuestion;
    faMagnifyingGlass = faMagnifyingGlass;
    faSort = faSort;
    faSortDown = faSortDown;
    faSortUp = faSortUp;
    destroyRef = inject(DestroyRef);
    cdkTable = viewChild(CdkTable, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "cdkTable" }] : /* istanbul ignore next */ []));
    page = signal(0, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "page" }] : /* istanbul ignore next */ []));
    pageSizeState = signal(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "pageSizeState" }] : /* istanbul ignore next */ []));
    sortState = signal(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "sortState" }] : /* istanbul ignore next */ []));
    searchTerm = signal('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "searchTerm" }] : /* istanbul ignore next */ []));
    searchTimer;
    effectivePageSize = computed(() => this.pageSizeState() ?? this.pageSize(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "effectivePageSize" }] : /* istanbul ignore next */ []));
    currentPage = computed(() => this.page(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "currentPage" }] : /* istanbul ignore next */ []));
    effectiveTrackBy = computed(() => this.trackBy() ?? ((_, item) => item), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "effectiveTrackBy" }] : /* istanbul ignore next */ []));
    displayedColumns = computed(() => {
        const names = this.columns().map((col, index) => this.columnName(col, index));
        return this.rowActions() ? [...names, ACTIONS_COLUMN] : names;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "displayedColumns" }] : /* istanbul ignore next */ []));
    tableClasses = computed(() => {
        const base = 'tum:w-full tum:border-collapse tum:text-sm';
        return this.striped() ? `${base} tum:[&_tbody_tr:nth-child(odd)]:bg-table-striped-background` : base;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "tableClasses" }] : /* istanbul ignore next */ []));
    constructor() {
        afterNextRender(() => {
            const field = this.initialSortField();
            if (field) {
                this.sortState.set({ field, direction: this.initialSortDirection() });
            }
            this.emitDataRequest();
        });
        effect(() => {
            this.displayedColumns();
            this.cdkTable()?.renderRows();
        });
        effect(() => {
            const total = this.totalRecords();
            const rows = this.effectivePageSize();
            const lastPage = total > 0 ? Math.ceil(total / Math.max(1, rows)) - 1 : 0;
            if (this.page() > lastPage) {
                untracked(() => {
                    this.page.set(lastPage);
                    this.emitDataRequest();
                });
            }
        });
        this.destroyRef.onDestroy(() => clearTimeout(this.searchTimer));
    }
    /** Jump back to the first page and re-request. For consumers that own filtering themselves (`showSearch` off). */
    resetPage() {
        if (this.page() === 0) {
            return;
        }
        this.page.set(0);
        this.emitDataRequest();
    }
    columnName(col, index) {
        return col.field ?? col.headerKey ?? col.header ?? `col-${index}`;
    }
    resolveValue(row, col) {
        return col.field ? get(row, col.field) : undefined;
    }
    cellParams(row, col, rowIndex) {
        return { data: row, col, value: this.resolveValue(row, col), rowIndex };
    }
    columnVisibilityClasses(col) {
        return col.hideBelow ? HIDE_BELOW_CLASSES[col.hideBelow] : '';
    }
    ariaSortFor(col) {
        if (!col.sort || !col.field) {
            return undefined;
        }
        const sort = this.sortState();
        if (!sort || sort.field !== col.field) {
            return 'none';
        }
        return sort.direction === 'asc' ? 'ascending' : 'descending';
    }
    sortDirection(col) {
        const sort = this.sortState();
        if (!sort || sort.field !== col.field) {
            return 'none';
        }
        return sort.direction;
    }
    sortIcon(col) {
        switch (this.sortDirection(col)) {
            case 'asc':
                return this.faSortUp;
            case 'desc':
                return this.faSortDown;
            default:
                return this.faSort;
        }
    }
    onSortClick(col) {
        if (!col.sort || !col.field) {
            return;
        }
        const current = this.sortState();
        this.sortState.set(current && current.field === col.field ? { field: col.field, direction: current.direction === 'asc' ? 'desc' : 'asc' } : { field: col.field, direction: 'asc' });
        this.page.set(0);
        this.emitDataRequest();
    }
    onSearchInput(value) {
        clearTimeout(this.searchTimer);
        this.searchTimer = setTimeout(() => {
            this.searchTerm.set(value);
            this.page.set(0);
            this.emitDataRequest();
        }, SEARCH_DEBOUNCE_MS);
    }
    onPageChange(page) {
        this.page.set(page);
        this.emitDataRequest();
    }
    onPageSizeChange(size) {
        this.pageSizeState.set(size);
        this.page.set(0);
        this.emitDataRequest();
    }
    emitDataRequest() {
        this.dataRequest.emit({
            pageIndex: this.page(),
            pageSize: this.effectivePageSize(),
            sort: this.sortState(),
            searchTerm: this.searchTerm().trim() || undefined,
        });
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTableComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiTableComponent, isStandalone: true, selector: "tum-ui-table", inputs: { columns: { classPropertyName: "columns", publicName: "columns", isSignal: true, isRequired: true, transformFunction: null }, rows: { classPropertyName: "rows", publicName: "rows", isSignal: true, isRequired: true, transformFunction: null }, totalRecords: { classPropertyName: "totalRecords", publicName: "totalRecords", isSignal: true, isRequired: false, transformFunction: null }, loading: { classPropertyName: "loading", publicName: "loading", isSignal: true, isRequired: false, transformFunction: null }, rowActions: { classPropertyName: "rowActions", publicName: "rowActions", isSignal: true, isRequired: false, transformFunction: null }, trackBy: { classPropertyName: "trackBy", publicName: "trackBy", isSignal: true, isRequired: false, transformFunction: null }, striped: { classPropertyName: "striped", publicName: "striped", isSignal: true, isRequired: false, transformFunction: null }, scrollable: { classPropertyName: "scrollable", publicName: "scrollable", isSignal: true, isRequired: false, transformFunction: null }, scrollHeight: { classPropertyName: "scrollHeight", publicName: "scrollHeight", isSignal: true, isRequired: false, transformFunction: null }, showSearch: { classPropertyName: "showSearch", publicName: "showSearch", isSignal: true, isRequired: false, transformFunction: null }, searchPlaceholder: { classPropertyName: "searchPlaceholder", publicName: "searchPlaceholder", isSignal: true, isRequired: false, transformFunction: null }, emptyMessage: { classPropertyName: "emptyMessage", publicName: "emptyMessage", isSignal: true, isRequired: false, transformFunction: null }, pageSize: { classPropertyName: "pageSize", publicName: "pageSize", isSignal: true, isRequired: false, transformFunction: null }, pageSizeOptions: { classPropertyName: "pageSizeOptions", publicName: "pageSizeOptions", isSignal: true, isRequired: false, transformFunction: null }, showRowsPerPage: { classPropertyName: "showRowsPerPage", publicName: "showRowsPerPage", isSignal: true, isRequired: false, transformFunction: null }, showCurrentPageReport: { classPropertyName: "showCurrentPageReport", publicName: "showCurrentPageReport", isSignal: true, isRequired: false, transformFunction: null }, initialSortField: { classPropertyName: "initialSortField", publicName: "initialSortField", isSignal: true, isRequired: false, transformFunction: null }, initialSortDirection: { classPropertyName: "initialSortDirection", publicName: "initialSortDirection", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { dataRequest: "dataRequest" }, viewQueries: [{ propertyName: "cdkTable", first: true, predicate: CdkTable, descendants: true, isSignal: true }], ngImport: i0, template: "@if (showSearch()) {\n    <div class=\"tum:mb-3\">\n        <div class=\"tum:relative\">\n            <fa-icon [icon]=\"faMagnifyingGlass\" class=\"tum:pointer-events-none tum:absolute tum:start-3 tum:top-1/2 tum:-translate-y-1/2 tum:text-muted\" />\n            <input\n                #searchInput\n                type=\"search\"\n                [placeholder]=\"searchPlaceholder() | tumUiTranslate\"\n                [attr.aria-label]=\"searchPlaceholder() | tumUiTranslate\"\n                (input)=\"onSearchInput(searchInput.value)\"\n                class=\"tum:box-border tum:w-full tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1.5 tum:ps-10 tum:pe-3 tum:text-sm tum:text-text\"\n            />\n        </div>\n    </div>\n}\n\n<div\n    class=\"tum:relative tum:w-full tum:max-w-full tum:overflow-x-auto\"\n    [style.overflow-y]=\"scrollable() ? 'auto' : null\"\n    [style.flex]=\"scrollHeight() === 'flex' ? '1 1 0%' : null\"\n    [style.min-height]=\"scrollHeight() === 'flex' ? 0 : null\"\n    [style.max-height]=\"scrollHeight() && scrollHeight() !== 'flex' ? scrollHeight() : null\"\n>\n    <table cdk-table [dataSource]=\"rows()\" [trackBy]=\"effectiveTrackBy()\" [class]=\"tableClasses()\" [attr.aria-busy]=\"loading() ? 'true' : null\">\n        @for (col of columns(); track columnName(col, $index)) {\n            <ng-container [cdkColumnDef]=\"columnName(col, $index)\">\n                <th\n                    cdk-header-cell\n                    *cdkHeaderCellDef\n                    scope=\"col\"\n                    [style.min-width]=\"col.width\"\n                    [attr.aria-sort]=\"ariaSortFor(col)\"\n                    [class]=\"columnVisibilityClasses(col)\"\n                    class=\"tum:whitespace-nowrap tum:border-border tum:bg-content-background tum:px-4 tum:py-2 tum:text-start tum:font-semibold tum:text-text\"\n                >\n                    <span class=\"tum:inline-flex tum:items-center tum:gap-1\">\n                        @if (col.sort && col.field) {\n                            <button\n                                type=\"button\"\n                                class=\"tum:inline-flex tum:cursor-pointer tum:appearance-none tum:items-center tum:gap-2 tum:border-0 tum:bg-transparent tum:p-0 tum:font-semibold tum:text-text tum:hover:text-accent\"\n                                (click)=\"onSortClick(col)\"\n                            >\n                                @if (col.headerKey) {\n                                    <span>{{ col.headerKey | tumUiTranslate }}</span>\n                                } @else {\n                                    <span>{{ col.header }}</span>\n                                }\n                                <fa-icon [icon]=\"sortIcon(col)\" class=\"tum:shrink-0 tum:text-muted\" />\n                            </button>\n                        } @else if (col.headerKey) {\n                            <span>{{ col.headerKey | tumUiTranslate }}</span>\n                        } @else {\n                            <span>{{ col.header }}</span>\n                        }\n                        @if (col.headerTooltip; as headerTooltip) {\n                            @let tooltipText = headerTooltip | tumUiTranslate;\n                            <button\n                                type=\"button\"\n                                class=\"tum:inline-flex tum:cursor-help tum:appearance-none tum:items-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted\"\n                                [tumUiTooltip]=\"tooltipText\"\n                                [attr.aria-label]=\"tooltipText\"\n                            >\n                                <fa-icon [icon]=\"faCircleQuestion\" />\n                            </button>\n                        }\n                    </span>\n                </th>\n                <td cdk-cell *cdkCellDef=\"let row; let i = index\" [class]=\"columnVisibilityClasses(col)\" class=\"tum:border-border tum:px-4 tum:py-2 tum:text-text\">\n                    @if (col.templateRef) {\n                        <ng-container [ngTemplateOutlet]=\"col.templateRef\" [ngTemplateOutletContext]=\"{ $implicit: cellParams(row, col, i) }\" />\n                    } @else {\n                        {{ resolveValue(row, col) }}\n                    }\n                </td>\n            </ng-container>\n        }\n\n        @if (rowActions(); as actions) {\n            <ng-container [cdkColumnDef]=\"ACTIONS_COLUMN\">\n                <th cdk-header-cell *cdkHeaderCellDef scope=\"col\" class=\"tum:border-border tum:bg-content-background tum:px-4 tum:py-2\">\n                    <span class=\"tum:sr-only\">{{ 'tumUi.table.actions' | tumUiTranslate }}</span>\n                </th>\n                <td cdk-cell *cdkCellDef=\"let row\" class=\"tum:border-border tum:px-4 tum:py-2 tum:text-end\">\n                    <ng-container [ngTemplateOutlet]=\"actions\" [ngTemplateOutletContext]=\"{ $implicit: row }\" />\n                </td>\n            </ng-container>\n        }\n\n        <tr cdk-header-row *cdkHeaderRowDef=\"displayedColumns(); sticky: scrollable()\" class=\"tum:bg-content-background\"></tr>\n        <tr cdk-row *cdkRowDef=\"let row; columns: displayedColumns()\"></tr>\n\n        <tr *cdkNoDataRow>\n            <td [attr.colspan]=\"displayedColumns().length\" class=\"tum:bg-content-background tum:px-4 tum:py-6 tum:text-center tum:text-muted\">\n                <span>{{ emptyMessage() | tumUiTranslate }}</span>\n            </td>\n        </tr>\n    </table>\n\n    @if (loading()) {\n        <div class=\"tum:absolute tum:inset-0 tum:flex tum:items-center tum:justify-center tum:bg-content-background/60\" aria-hidden=\"true\">\n            <span class=\"tum:h-6 tum:w-6 tum:animate-spin tum:rounded-full tum:border-2 tum:border-border tum:border-t-primary tum:motion-reduce:animate-none\"></span>\n        </div>\n    }\n</div>\n\n<tum-ui-paginator\n    [totalRecords]=\"totalRecords()\"\n    [page]=\"currentPage()\"\n    [pageSize]=\"effectivePageSize()\"\n    [pageSizeOptions]=\"pageSizeOptions()\"\n    [showRowsPerPage]=\"showRowsPerPage()\"\n    [showCurrentPageReport]=\"showCurrentPageReport()\"\n    [disabled]=\"loading()\"\n    (pageChange)=\"onPageChange($event)\"\n    (pageSizeChange)=\"onPageSizeChange($event)\"\n/>\n", styles: [":host{display:flex;flex-direction:column;height:100%;min-height:0}.cdk-table{width:100%}.cdk-table th,.cdk-table td{border-bottom-width:1px;border-bottom-style:solid}.cdk-table tr:last-child td{border-bottom-width:0}\n"], dependencies: [{ kind: "ngmodule", type: CdkTableModule }, { kind: "component", type: i1$4.CdkTable, selector: "cdk-table, table[cdk-table]", inputs: ["trackBy", "dataSource", "multiTemplateDataRows", "fixedLayout", "recycleRows"], outputs: ["contentChanged"], exportAs: ["cdkTable"] }, { kind: "directive", type: i1$4.CdkRowDef, selector: "[cdkRowDef]", inputs: ["cdkRowDefColumns", "cdkRowDefWhen"] }, { kind: "directive", type: i1$4.CdkCellDef, selector: "[cdkCellDef]" }, { kind: "directive", type: i1$4.CdkHeaderCellDef, selector: "[cdkHeaderCellDef]" }, { kind: "directive", type: i1$4.CdkColumnDef, selector: "[cdkColumnDef]", inputs: ["cdkColumnDef", "sticky", "stickyEnd"] }, { kind: "directive", type: i1$4.CdkCell, selector: "cdk-cell, td[cdk-cell]" }, { kind: "component", type: i1$4.CdkRow, selector: "cdk-row, tr[cdk-row]" }, { kind: "directive", type: i1$4.CdkHeaderCell, selector: "cdk-header-cell, th[cdk-header-cell]" }, { kind: "component", type: i1$4.CdkHeaderRow, selector: "cdk-header-row, tr[cdk-header-row]" }, { kind: "directive", type: i1$4.CdkHeaderRowDef, selector: "[cdkHeaderRowDef]", inputs: ["cdkHeaderRowDef", "cdkHeaderRowDefSticky"] }, { kind: "directive", type: i1$4.CdkNoDataRow, selector: "ng-template[cdkNoDataRow]" }, { kind: "directive", type: NgTemplateOutlet, selector: "[ngTemplateOutlet]", inputs: ["ngTemplateOutletContext", "ngTemplateOutlet", "ngTemplateOutletInjector"] }, { kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }, { kind: "component", type: TumUiPaginatorComponent, selector: "tum-ui-paginator", inputs: ["ariaLabel", "totalRecords", "page", "pageSize", "pageSizeOptions", "disabled", "showCurrentPageReport", "showRowsPerPage"], outputs: ["pageChange", "pageSizeChange"] }, { kind: "directive", type: TumUiTooltipDirective, selector: "[tumUiTooltip]", inputs: ["tumUiTooltip", "tumUiTooltipPlacement", "showDelayMs", "hideDelayMs"] }, { kind: "pipe", type: TumUiTranslatePipe, name: "tumUiTranslate" }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTableComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-table', imports: [CdkTableModule, NgTemplateOutlet, FaIconComponent, TumUiTranslatePipe, TumUiPaginatorComponent, TumUiTooltipDirective], changeDetection: ChangeDetectionStrategy.OnPush, template: "@if (showSearch()) {\n    <div class=\"tum:mb-3\">\n        <div class=\"tum:relative\">\n            <fa-icon [icon]=\"faMagnifyingGlass\" class=\"tum:pointer-events-none tum:absolute tum:start-3 tum:top-1/2 tum:-translate-y-1/2 tum:text-muted\" />\n            <input\n                #searchInput\n                type=\"search\"\n                [placeholder]=\"searchPlaceholder() | tumUiTranslate\"\n                [attr.aria-label]=\"searchPlaceholder() | tumUiTranslate\"\n                (input)=\"onSearchInput(searchInput.value)\"\n                class=\"tum:box-border tum:w-full tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1.5 tum:ps-10 tum:pe-3 tum:text-sm tum:text-text\"\n            />\n        </div>\n    </div>\n}\n\n<div\n    class=\"tum:relative tum:w-full tum:max-w-full tum:overflow-x-auto\"\n    [style.overflow-y]=\"scrollable() ? 'auto' : null\"\n    [style.flex]=\"scrollHeight() === 'flex' ? '1 1 0%' : null\"\n    [style.min-height]=\"scrollHeight() === 'flex' ? 0 : null\"\n    [style.max-height]=\"scrollHeight() && scrollHeight() !== 'flex' ? scrollHeight() : null\"\n>\n    <table cdk-table [dataSource]=\"rows()\" [trackBy]=\"effectiveTrackBy()\" [class]=\"tableClasses()\" [attr.aria-busy]=\"loading() ? 'true' : null\">\n        @for (col of columns(); track columnName(col, $index)) {\n            <ng-container [cdkColumnDef]=\"columnName(col, $index)\">\n                <th\n                    cdk-header-cell\n                    *cdkHeaderCellDef\n                    scope=\"col\"\n                    [style.min-width]=\"col.width\"\n                    [attr.aria-sort]=\"ariaSortFor(col)\"\n                    [class]=\"columnVisibilityClasses(col)\"\n                    class=\"tum:whitespace-nowrap tum:border-border tum:bg-content-background tum:px-4 tum:py-2 tum:text-start tum:font-semibold tum:text-text\"\n                >\n                    <span class=\"tum:inline-flex tum:items-center tum:gap-1\">\n                        @if (col.sort && col.field) {\n                            <button\n                                type=\"button\"\n                                class=\"tum:inline-flex tum:cursor-pointer tum:appearance-none tum:items-center tum:gap-2 tum:border-0 tum:bg-transparent tum:p-0 tum:font-semibold tum:text-text tum:hover:text-accent\"\n                                (click)=\"onSortClick(col)\"\n                            >\n                                @if (col.headerKey) {\n                                    <span>{{ col.headerKey | tumUiTranslate }}</span>\n                                } @else {\n                                    <span>{{ col.header }}</span>\n                                }\n                                <fa-icon [icon]=\"sortIcon(col)\" class=\"tum:shrink-0 tum:text-muted\" />\n                            </button>\n                        } @else if (col.headerKey) {\n                            <span>{{ col.headerKey | tumUiTranslate }}</span>\n                        } @else {\n                            <span>{{ col.header }}</span>\n                        }\n                        @if (col.headerTooltip; as headerTooltip) {\n                            @let tooltipText = headerTooltip | tumUiTranslate;\n                            <button\n                                type=\"button\"\n                                class=\"tum:inline-flex tum:cursor-help tum:appearance-none tum:items-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted\"\n                                [tumUiTooltip]=\"tooltipText\"\n                                [attr.aria-label]=\"tooltipText\"\n                            >\n                                <fa-icon [icon]=\"faCircleQuestion\" />\n                            </button>\n                        }\n                    </span>\n                </th>\n                <td cdk-cell *cdkCellDef=\"let row; let i = index\" [class]=\"columnVisibilityClasses(col)\" class=\"tum:border-border tum:px-4 tum:py-2 tum:text-text\">\n                    @if (col.templateRef) {\n                        <ng-container [ngTemplateOutlet]=\"col.templateRef\" [ngTemplateOutletContext]=\"{ $implicit: cellParams(row, col, i) }\" />\n                    } @else {\n                        {{ resolveValue(row, col) }}\n                    }\n                </td>\n            </ng-container>\n        }\n\n        @if (rowActions(); as actions) {\n            <ng-container [cdkColumnDef]=\"ACTIONS_COLUMN\">\n                <th cdk-header-cell *cdkHeaderCellDef scope=\"col\" class=\"tum:border-border tum:bg-content-background tum:px-4 tum:py-2\">\n                    <span class=\"tum:sr-only\">{{ 'tumUi.table.actions' | tumUiTranslate }}</span>\n                </th>\n                <td cdk-cell *cdkCellDef=\"let row\" class=\"tum:border-border tum:px-4 tum:py-2 tum:text-end\">\n                    <ng-container [ngTemplateOutlet]=\"actions\" [ngTemplateOutletContext]=\"{ $implicit: row }\" />\n                </td>\n            </ng-container>\n        }\n\n        <tr cdk-header-row *cdkHeaderRowDef=\"displayedColumns(); sticky: scrollable()\" class=\"tum:bg-content-background\"></tr>\n        <tr cdk-row *cdkRowDef=\"let row; columns: displayedColumns()\"></tr>\n\n        <tr *cdkNoDataRow>\n            <td [attr.colspan]=\"displayedColumns().length\" class=\"tum:bg-content-background tum:px-4 tum:py-6 tum:text-center tum:text-muted\">\n                <span>{{ emptyMessage() | tumUiTranslate }}</span>\n            </td>\n        </tr>\n    </table>\n\n    @if (loading()) {\n        <div class=\"tum:absolute tum:inset-0 tum:flex tum:items-center tum:justify-center tum:bg-content-background/60\" aria-hidden=\"true\">\n            <span class=\"tum:h-6 tum:w-6 tum:animate-spin tum:rounded-full tum:border-2 tum:border-border tum:border-t-primary tum:motion-reduce:animate-none\"></span>\n        </div>\n    }\n</div>\n\n<tum-ui-paginator\n    [totalRecords]=\"totalRecords()\"\n    [page]=\"currentPage()\"\n    [pageSize]=\"effectivePageSize()\"\n    [pageSizeOptions]=\"pageSizeOptions()\"\n    [showRowsPerPage]=\"showRowsPerPage()\"\n    [showCurrentPageReport]=\"showCurrentPageReport()\"\n    [disabled]=\"loading()\"\n    (pageChange)=\"onPageChange($event)\"\n    (pageSizeChange)=\"onPageSizeChange($event)\"\n/>\n", styles: [":host{display:flex;flex-direction:column;height:100%;min-height:0}.cdk-table{width:100%}.cdk-table th,.cdk-table td{border-bottom-width:1px;border-bottom-style:solid}.cdk-table tr:last-child td{border-bottom-width:0}\n"] }]
        }], ctorParameters: () => [], propDecorators: { columns: [{ type: i0.Input, args: [{ isSignal: true, alias: "columns", required: true }] }], rows: [{ type: i0.Input, args: [{ isSignal: true, alias: "rows", required: true }] }], totalRecords: [{ type: i0.Input, args: [{ isSignal: true, alias: "totalRecords", required: false }] }], loading: [{ type: i0.Input, args: [{ isSignal: true, alias: "loading", required: false }] }], rowActions: [{ type: i0.Input, args: [{ isSignal: true, alias: "rowActions", required: false }] }], trackBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "trackBy", required: false }] }], striped: [{ type: i0.Input, args: [{ isSignal: true, alias: "striped", required: false }] }], scrollable: [{ type: i0.Input, args: [{ isSignal: true, alias: "scrollable", required: false }] }], scrollHeight: [{ type: i0.Input, args: [{ isSignal: true, alias: "scrollHeight", required: false }] }], showSearch: [{ type: i0.Input, args: [{ isSignal: true, alias: "showSearch", required: false }] }], searchPlaceholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "searchPlaceholder", required: false }] }], emptyMessage: [{ type: i0.Input, args: [{ isSignal: true, alias: "emptyMessage", required: false }] }], pageSize: [{ type: i0.Input, args: [{ isSignal: true, alias: "pageSize", required: false }] }], pageSizeOptions: [{ type: i0.Input, args: [{ isSignal: true, alias: "pageSizeOptions", required: false }] }], showRowsPerPage: [{ type: i0.Input, args: [{ isSignal: true, alias: "showRowsPerPage", required: false }] }], showCurrentPageReport: [{ type: i0.Input, args: [{ isSignal: true, alias: "showCurrentPageReport", required: false }] }], initialSortField: [{ type: i0.Input, args: [{ isSignal: true, alias: "initialSortField", required: false }] }], initialSortDirection: [{ type: i0.Input, args: [{ isSignal: true, alias: "initialSortDirection", required: false }] }], dataRequest: [{ type: i0.Output, args: ["dataRequest"] }], cdkTable: [{ type: i0.ViewChild, args: [i0.forwardRef(() => CdkTable), { isSignal: true }] }] } });

let nextGroupId = 0;
class TumUiTabsService {
    groupId = `tum-ui-tabs-${nextGroupId++}`;
    source = signal(signal(undefined), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "source" }] : /* istanbul ignore next */ []));
    /**
     * The value each tab has published, keyed by the tab instance.
     *
     * A tab's `value` is a required input, and the tab list's content query reports a tab declared inside `@if` or
     * `@for` before Angular has applied that binding — reading the input from the list would then throw NG0950. Each
     * tab instead publishes its value from its own change detection, where the input is always available, and the list
     * reads it back from here. A tab missing from this map therefore means "not bound yet", which the list waits for
     * rather than acting on.
     */
    publishedValues = signal(new Map(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "publishedValues" }] : /* istanbul ignore next */ []));
    onSelect = () => { };
    active = computed(() => this.source()(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "active" }] : /* istanbul ignore next */ []));
    register(value, onSelect) {
        this.source.set(value);
        this.onSelect = onSelect;
    }
    select(value) {
        this.onSelect(value);
    }
    /**
     * Publishes a tab's value, or replaces it when the tab's input changes.
     *
     * The unchanged case returns the same map instance, so the signal does not notify and the tab list is not woken for
     * nothing. The check lives inside `update` on purpose: each tab calls this from its own effect, and reading the
     * signal here instead would subscribe every tab's effect to every other tab's value.
     */
    publish(tab, value) {
        this.publishedValues.update((values) => (values.get(tab) === value ? values : new Map(values).set(tab, value)));
    }
    /** Withdraws a destroyed tab's value, so a removed tab cannot keep the list waiting for or matching it. */
    unpublish(tab) {
        this.publishedValues.update((values) => {
            if (!values.has(tab)) {
                return values;
            }
            const remaining = new Map(values);
            remaining.delete(tab);
            return remaining;
        });
    }
    /** The tab's published value, or `undefined` while its `value` input has not been applied yet. */
    valueFor(tab) {
        return this.publishedValues().get(tab);
    }
    tabId(value) {
        return `${this.groupId}-tab-${this.idSegment(value)}`;
    }
    panelId(value) {
        return `${this.groupId}-panel-${this.idSegment(value)}`;
    }
    idSegment(value) {
        const type = typeof value === 'number' ? 'number' : typeof value === 'string' ? 'string' : 'undefined';
        return `${type}-${encodeURIComponent(String(value))}`;
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabsService, deps: [], target: i0.ɵɵFactoryTarget.Injectable });
    static ɵprov = i0.ɵɵngDeclareInjectable({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabsService });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabsService, decorators: [{
            type: Injectable
        }] });

/** Selectable tab associated with the panel that has the same value. */
class TumUiTabComponent {
    tabsService = inject(TumUiTabsService);
    elementRef = inject(ElementRef);
    /** Value that associates this tab with a tab panel. */
    value = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "value" }] : /* istanbul ignore next */ []));
    // eslint-disable-next-line @angular-eslint/no-input-rename -- FocusKeyManager requires disabled to be a boolean property.
    disabledInput = input(false, { ...(ngDevMode ? { debugName: "disabledInput" } : /* istanbul ignore next */ {}), alias: 'disabled', transform: booleanAttribute });
    get disabled() {
        return this.disabledInput();
    }
    constructor() {
        // Publish the value from the tab's own change detection, where the required input is always available. The tab
        // list cannot read the input directly: its content query reports a tab declared inside @if or @for before
        // Angular has applied the binding, and reading it then throws NG0950.
        effect(() => this.tabsService.publish(this, this.value()));
        inject(DestroyRef).onDestroy(() => this.tabsService.unpublish(this));
    }
    active = computed(() => this.tabsService.active() === this.value(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "active" }] : /* istanbul ignore next */ []));
    id = computed(() => this.tabsService.tabId(this.value()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "id" }] : /* istanbul ignore next */ []));
    panelId = computed(() => this.tabsService.panelId(this.value()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "panelId" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => {
        const state = this.active() ? 'tum:text-accent' : 'tum:text-muted tum:hover:text-text';
        const disabled = this.disabled ? 'tum-ui-tab-disabled' : '';
        return `tum-ui-tab tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus ${state} ${disabled}`.trim();
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    onClick() {
        if (!this.disabled) {
            this.tabsService.select(this.value());
        }
    }
    focus(_origin) {
        this.elementRef.nativeElement.focus();
        this.elementRef.nativeElement.scrollIntoView?.({ block: 'nearest', inline: 'nearest' });
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiTabComponent, isStandalone: true, selector: "tum-ui-tab", inputs: { value: { classPropertyName: "value", publicName: "value", isSignal: true, isRequired: true, transformFunction: null }, disabledInput: { classPropertyName: "disabledInput", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null } }, host: { attributes: { "role": "tab" }, listeners: { "click": "onClick()" }, properties: { "class": "hostClasses()", "id": "id()", "attr.aria-selected": "active()", "attr.aria-controls": "panelId()", "attr.aria-disabled": "disabled || undefined", "attr.tabindex": "active() && !disabled ? 0 : -1" } }, ngImport: i0, template: '<ng-content />', isInline: true, styles: [":host{display:inline-flex;align-items:center;position:relative;flex-shrink:0;gap:calc(var(--tumaet-ui-spacing) * 2);padding:calc(var(--tumaet-ui-spacing) * 4) calc(var(--tumaet-ui-spacing) * 4.5);font-weight:600;white-space:nowrap;cursor:pointer;-webkit-user-select:none;user-select:none;background:transparent;outline-color:transparent;transition:color .2s,background .2s,outline-color .2s}:host.tum-ui-tab-disabled{opacity:.6;cursor:default;pointer-events:none}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-tab', template: '<ng-content />', host: {
                        role: 'tab',
                        '[class]': 'hostClasses()',
                        '[id]': 'id()',
                        '[attr.aria-selected]': 'active()',
                        '[attr.aria-controls]': 'panelId()',
                        '[attr.aria-disabled]': 'disabled || undefined',
                        '[attr.tabindex]': 'active() && !disabled ? 0 : -1',
                        '(click)': 'onClick()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{display:inline-flex;align-items:center;position:relative;flex-shrink:0;gap:calc(var(--tumaet-ui-spacing) * 2);padding:calc(var(--tumaet-ui-spacing) * 4) calc(var(--tumaet-ui-spacing) * 4.5);font-weight:600;white-space:nowrap;cursor:pointer;-webkit-user-select:none;user-select:none;background:transparent;outline-color:transparent;transition:color .2s,background .2s,outline-color .2s}:host.tum-ui-tab-disabled{opacity:.6;cursor:default;pointer-events:none}\n"] }]
        }], ctorParameters: () => [], propDecorators: { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: true }] }], disabledInput: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }] } });

/** Scrollable tab-list container with keyboard navigation and an animated selection indicator. */
class TumUiTabListComponent {
    tabsService = inject(TumUiTabsService);
    directionality = inject(Directionality);
    injector = inject(Injector);
    elementRef = inject(ElementRef);
    tabs = contentChildren(TumUiTabComponent, { ...(ngDevMode ? { debugName: "tabs" } : /* istanbul ignore next */ {}), descendants: true });
    keyManager = new FocusKeyManager(this.tabs, this.injector).withWrap().withHomeAndEnd().setFocusOrigin('keyboard');
    keyManagerChange;
    resizeObserver;
    indicatorPosition = signal({ offset: 0, width: 0, animate: false }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "indicatorPosition" }] : /* istanbul ignore next */ []));
    indicatorTransform = computed(() => `translateX(${this.indicatorPosition().offset}px)`, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "indicatorTransform" }] : /* istanbul ignore next */ []));
    indicatorReady = false;
    constructor() {
        this.keyManagerChange = this.keyManager.change.subscribe((index) => {
            const tab = this.tabs()[index];
            if (tab) {
                this.tabsService.select(this.tabsService.valueFor(tab));
            }
        });
        effect(() => {
            const tabs = this.tabs();
            if (!this.allValuesPublished(tabs)) {
                // A tab created by @if or @for is reported by the content query before its `value` binding has been
                // applied. Acting now would select the wrong tab, or overwrite the value the host bound with undefined;
                // this effect re-runs as soon as the missing tab publishes its value.
                return;
            }
            const activeValue = this.tabsService.active();
            const activeIndex = tabs.findIndex((tab) => this.tabsService.valueFor(tab) === activeValue && !tab.disabled);
            if (activeIndex >= 0) {
                this.keyManager.updateActiveItem(activeIndex);
                return;
            }
            const firstEnabledIndex = tabs.findIndex((tab) => !tab.disabled);
            if (firstEnabledIndex >= 0) {
                this.keyManager.updateActiveItem(firstEnabledIndex);
                this.tabsService.select(this.tabsService.valueFor(tabs[firstEnabledIndex]));
            }
        });
        afterRenderEffect(() => {
            const tabs = this.tabs();
            const active = tabs.find((tab) => this.tabsService.valueFor(tab) === this.tabsService.active());
            this.updateIndicator(active);
            this.observeLayout(tabs);
        });
    }
    onKeydown(event) {
        if (event.target instanceof HTMLElement) {
            const eventIndex = this.tabs().findIndex((tab) => tab.elementRef.nativeElement === event.target);
            if (eventIndex >= 0) {
                this.keyManager.updateActiveItem(eventIndex);
            }
        }
        this.keyManager.withHorizontalOrientation(this.directionality.value).onKeydown(event);
    }
    ngOnDestroy() {
        this.resizeObserver?.disconnect();
        this.keyManagerChange.unsubscribe();
        this.keyManager.destroy();
    }
    /**
     * Whether every tab currently in the query has published its value, i.e. whether Angular has applied the `value`
     * binding of each of them. Only then does the list know which tab is which.
     */
    allValuesPublished(tabs) {
        return tabs.every((tab) => this.tabsService.valueFor(tab) !== undefined);
    }
    updateIndicator(active) {
        const width = active?.elementRef.nativeElement.offsetWidth ?? 0;
        this.indicatorPosition.set({
            offset: active?.elementRef.nativeElement.offsetLeft ?? 0,
            width,
            animate: this.indicatorReady,
        });
        this.indicatorReady ||= width > 0;
    }
    observeLayout(tabs) {
        if (typeof ResizeObserver === 'undefined') {
            return;
        }
        this.resizeObserver?.disconnect();
        this.resizeObserver = new ResizeObserver(() => {
            const active = this.tabs().find((tab) => this.tabsService.valueFor(tab) === this.tabsService.active());
            this.updateIndicator(active);
        });
        this.resizeObserver.observe(this.elementRef.nativeElement);
        tabs.forEach((tab) => this.resizeObserver.observe(tab.elementRef.nativeElement));
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabListComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.2.0", version: "22.1.5", type: TumUiTabListComponent, isStandalone: true, selector: "tum-ui-tab-list", host: { attributes: { "role": "tablist" }, listeners: { "keydown": "onKeydown($event)" }, classAttribute: "tum-ui-tab-list tum:relative tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:overflow-x-auto tum:border-b tum:border-border" }, queries: [{ propertyName: "tabs", predicate: TumUiTabComponent, descendants: true, isSignal: true }], ngImport: i0, template: "<ng-content />\n<span\n    class=\"tum-ui-tab-indicator\"\n    [class.tum-ui-tab-indicator-animated]=\"indicatorPosition().animate\"\n    aria-hidden=\"true\"\n    [style.width.px]=\"indicatorPosition().width\"\n    [style.transform]=\"indicatorTransform()\"\n></span>\n", styles: [":host{scrollbar-width:thin}.tum-ui-tab-indicator{position:absolute;inset-block-end:0;left:0;height:2px;background:var(--tumaet-ui-primary-color);pointer-events:none}.tum-ui-tab-indicator-animated{transition:width .25s cubic-bezier(.35,0,.25,1),transform .25s cubic-bezier(.35,0,.25,1)}@media(prefers-reduced-motion:reduce){.tum-ui-tab-indicator{transition:none}}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabListComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-tab-list', host: {
                        role: 'tablist',
                        class: 'tum-ui-tab-list tum:relative tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:overflow-x-auto tum:border-b tum:border-border',
                        '(keydown)': 'onKeydown($event)',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<ng-content />\n<span\n    class=\"tum-ui-tab-indicator\"\n    [class.tum-ui-tab-indicator-animated]=\"indicatorPosition().animate\"\n    aria-hidden=\"true\"\n    [style.width.px]=\"indicatorPosition().width\"\n    [style.transform]=\"indicatorTransform()\"\n></span>\n", styles: [":host{scrollbar-width:thin}.tum-ui-tab-indicator{position:absolute;inset-block-end:0;left:0;height:2px;background:var(--tumaet-ui-primary-color);pointer-events:none}.tum-ui-tab-indicator-animated{transition:width .25s cubic-bezier(.35,0,.25,1),transform .25s cubic-bezier(.35,0,.25,1)}@media(prefers-reduced-motion:reduce){.tum-ui-tab-indicator{transition:none}}\n"] }]
        }], ctorParameters: () => [], propDecorators: { tabs: [{ type: i0.ContentChildren, args: [i0.forwardRef(() => TumUiTabComponent), { ...{ descendants: true }, isSignal: true }] }] } });

/**
 * Content panel shown when its value matches the containing tabs value.
 *
 * By default an inactive panel is destroyed, which is the right default: a tab a user is not looking at should not
 * keep a subscription open or hold a large view alive. Turn on `preserveContent` for a panel whose state the user
 * expects to survive a trip to another tab — scroll position, an expanded row, an in-progress filter — because
 * destroying it re-runs every child constructor and returns the user to the top of a list they had scrolled.
 */
class TumUiTabPanelComponent {
    tabsService = inject(TumUiTabsService);
    /** Value that associates this panel with a tab. */
    value = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "value" }] : /* istanbul ignore next */ []));
    /** Keeps this panel's content in the DOM while another tab is selected, hidden and inert, instead of destroying it. */
    preserveContent = input(false, { ...(ngDevMode ? { debugName: "preserveContent" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    active = computed(() => this.tabsService.active() === this.value(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "active" }] : /* istanbul ignore next */ []));
    rendered = computed(() => this.active() || this.preserveContent(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "rendered" }] : /* istanbul ignore next */ []));
    id = computed(() => this.tabsService.panelId(this.value()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "id" }] : /* istanbul ignore next */ []));
    tabId = computed(() => this.tabsService.tabId(this.value()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "tabId" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabPanelComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiTabPanelComponent, isStandalone: true, selector: "tum-ui-tab-panel", inputs: { value: { classPropertyName: "value", publicName: "value", isSignal: true, isRequired: true, transformFunction: null }, preserveContent: { classPropertyName: "preserveContent", publicName: "preserveContent", isSignal: true, isRequired: false, transformFunction: null } }, host: { attributes: { "role": "tabpanel" }, properties: { "id": "id()", "attr.aria-labelledby": "tabId()", "attr.tabindex": "active() ? 0 : undefined", "hidden": "!active()", "attr.inert": "active() ? null : \"\"", "attr.data-slot": "\"tab-panel\"", "attr.data-state": "active() ? 'active' : 'inactive'" }, classAttribute: "tum-ui-tab-panel tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus" }, ngImport: i0, template: '@if (rendered()) { <ng-content /> }', isInline: true, changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabPanelComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'tum-ui-tab-panel',
                    template: '@if (rendered()) { <ng-content /> }',
                    host: {
                        role: 'tabpanel',
                        class: 'tum-ui-tab-panel tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus',
                        '[id]': 'id()',
                        '[attr.aria-labelledby]': 'tabId()',
                        '[attr.tabindex]': 'active() ? 0 : undefined',
                        '[hidden]': '!active()',
                        // `hidden` takes it out of the accessibility tree; `inert` takes it out of the tab order and out of reach
                        // of find-in-page, which `hidden` alone does not guarantee for preserved content.
                        '[attr.inert]': 'active() ? null : ""',
                        '[attr.data-slot]': '"tab-panel"',
                        '[attr.data-state]': "active() ? 'active' : 'inactive'",
                    },
                    changeDetection: ChangeDetectionStrategy.OnPush,
                }]
        }], propDecorators: { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: true }] }], preserveContent: [{ type: i0.Input, args: [{ isSignal: true, alias: "preserveContent", required: false }] }] } });

/** Layout container for the panels in a tabs composition. */
class TumUiTabPanelsComponent {
    /** Disable when the containing surface already provides content padding. */
    padded = input(true, { ...(ngDevMode ? { debugName: "padded" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabPanelsComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiTabPanelsComponent, isStandalone: true, selector: "tum-ui-tab-panels", inputs: { padded: { classPropertyName: "padded", publicName: "padded", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "attr.data-padded": "padded()" }, classAttribute: "tum-ui-tab-panels" }, ngImport: i0, template: '<ng-content />', isInline: true, styles: [":host{display:block}:host([data-padded=true]){padding:calc(var(--tumaet-ui-spacing) * 3.5) calc(var(--tumaet-ui-spacing) * 4.5) calc(var(--tumaet-ui-spacing) * 4.5)}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabPanelsComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-tab-panels', template: '<ng-content />', host: {
                        class: 'tum-ui-tab-panels',
                        '[attr.data-padded]': 'padded()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{display:block}:host([data-padded=true]){padding:calc(var(--tumaet-ui-spacing) * 3.5) calc(var(--tumaet-ui-spacing) * 4.5) calc(var(--tumaet-ui-spacing) * 4.5)}\n"] }]
        }], propDecorators: { padded: [{ type: i0.Input, args: [{ isSignal: true, alias: "padded", required: false }] }] } });

/** Coordinates an accessible tab list with its associated tab panels. */
class TumUiTabsComponent {
    tabsService = inject(TumUiTabsService);
    /** Value shared by the active tab and tab panel. */
    value = model(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "value" }] : /* istanbul ignore next */ []));
    constructor() {
        this.tabsService.register(this.value, (selected) => this.value.set(selected));
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabsComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiTabsComponent, isStandalone: true, selector: "tum-ui-tabs", inputs: { value: { classPropertyName: "value", publicName: "value", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { value: "valueChange" }, host: { classAttribute: "tum-ui-tabs tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:flex-col" }, providers: [TumUiTabsService], ngImport: i0, template: '<ng-content />', isInline: true, changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTabsComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'tum-ui-tabs',
                    template: '<ng-content />',
                    host: {
                        class: 'tum-ui-tabs tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:flex-col',
                    },
                    providers: [TumUiTabsService],
                    changeDetection: ChangeDetectionStrategy.OnPush,
                }]
        }], ctorParameters: () => [], propDecorators: { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }, { type: i0.Output, args: ["valueChange"] }] } });

const TAG_BASE = 'tum:inline-flex tum:items-center tum:gap-1 tum:px-2 tum:py-1 tum:text-sm tum:font-bold';
const TAG_SEVERITY = {
    secondary: 'tum:bg-hover-background tum:text-text',
    success: '',
    info: '',
    warn: '',
    danger: '',
    contrast: 'tum:bg-contrast-background tum:text-contrast',
};
class TumUiTagComponent {
    severity = input('secondary', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []));
    value = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "value" }] : /* istanbul ignore next */ []));
    rounded = input(false, { ...(ngDevMode ? { debugName: "rounded" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    tagClasses = computed(() => `${TAG_BASE} ${this.rounded() ? 'tum:rounded-full' : 'tum:rounded-md'} ${TAG_SEVERITY[this.severity()]}`.replace(/\s+/g, ' ').trim(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "tagClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTagComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiTagComponent, isStandalone: true, selector: "tum-ui-tag", inputs: { severity: { classPropertyName: "severity", publicName: "severity", isSignal: true, isRequired: false, transformFunction: null }, value: { classPropertyName: "value", publicName: "value", isSignal: true, isRequired: false, transformFunction: null }, rounded: { classPropertyName: "rounded", publicName: "rounded", isSignal: true, isRequired: false, transformFunction: null } }, ngImport: i0, template: "<span [class]=\"tagClasses()\" [attr.data-severity]=\"severity()\">\n    @if (value(); as tagValue) {\n        {{ tagValue }}\n    } @else {\n        <ng-content />\n    }\n</span>\n", styles: [":host span[data-severity=success]{background:color-mix(in srgb,var(--tumaet-ui-state-success) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-success-foreground)}:host span[data-severity=info]{background:color-mix(in srgb,var(--tumaet-ui-state-info) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-info-foreground)}:host span[data-severity=warn]{background:color-mix(in srgb,var(--tumaet-ui-state-warning) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-warning-foreground)}:host span[data-severity=danger]{background:color-mix(in srgb,var(--tumaet-ui-state-danger) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-danger-foreground)}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiTagComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-tag', changeDetection: ChangeDetectionStrategy.OnPush, template: "<span [class]=\"tagClasses()\" [attr.data-severity]=\"severity()\">\n    @if (value(); as tagValue) {\n        {{ tagValue }}\n    } @else {\n        <ng-content />\n    }\n</span>\n", styles: [":host span[data-severity=success]{background:color-mix(in srgb,var(--tumaet-ui-state-success) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-success-foreground)}:host span[data-severity=info]{background:color-mix(in srgb,var(--tumaet-ui-state-info) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-info-foreground)}:host span[data-severity=warn]{background:color-mix(in srgb,var(--tumaet-ui-state-warning) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-warning-foreground)}:host span[data-severity=danger]{background:color-mix(in srgb,var(--tumaet-ui-state-danger) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-danger-foreground)}\n"] }]
        }], propDecorators: { severity: [{ type: i0.Input, args: [{ isSignal: true, alias: "severity", required: false }] }], value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }], rounded: [{ type: i0.Input, args: [{ isSignal: true, alias: "rounded", required: false }] }] } });

class TumUiToggleSwitchComponent {
    hostAriaLabel = inject(new HostAttributeToken('aria-label'), { optional: true });
    hostAriaLabelledBy = inject(new HostAttributeToken('aria-labelledby'), { optional: true });
    disabled = input(false, { ...(ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    inputId = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "inputId" }] : /* istanbul ignore next */ []));
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    ariaLabelledBy = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []));
    changed = output();
    checked = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "checked" }] : /* istanbul ignore next */ []));
    cvaDisabled = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "cvaDisabled" }] : /* istanbul ignore next */ []));
    effectiveDisabled = computed(() => this.disabled() || this.cvaDisabled(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "effectiveDisabled" }] : /* istanbul ignore next */ []));
    effectiveAriaLabel = computed(() => this.ariaLabel() ?? this.hostAriaLabel, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "effectiveAriaLabel" }] : /* istanbul ignore next */ []));
    effectiveAriaLabelledBy = computed(() => this.ariaLabelledBy() ?? this.hostAriaLabelledBy, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "effectiveAriaLabelledBy" }] : /* istanbul ignore next */ []));
    onChange = () => { };
    onTouched = () => { };
    hostClasses = computed(() => {
        const track = this.checked() ? 'tum:bg-primary' : 'tum:bg-control-border';
        return `${track} ${this.effectiveDisabled() ? 'tum:opacity-60' : ''}`.trim();
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    onInputChange(event) {
        const next = event.target.checked;
        this.checked.set(next);
        this.onChange(next);
        this.onTouched();
        this.changed.emit(next);
    }
    onInputBlur() {
        this.onTouched();
    }
    writeValue(value) {
        this.checked.set(!!value);
    }
    registerOnChange(fn) {
        this.onChange = fn;
    }
    registerOnTouched(fn) {
        this.onTouched = fn;
    }
    setDisabledState(isDisabled) {
        this.cvaDisabled.set(isDisabled);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiToggleSwitchComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiToggleSwitchComponent, isStandalone: true, selector: "tum-ui-toggle-switch", inputs: { disabled: { classPropertyName: "disabled", publicName: "disabled", isSignal: true, isRequired: false, transformFunction: null }, inputId: { classPropertyName: "inputId", publicName: "inputId", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null }, ariaLabelledBy: { classPropertyName: "ariaLabelledBy", publicName: "ariaLabelledBy", isSignal: true, isRequired: false, transformFunction: null } }, outputs: { changed: "changed" }, host: { properties: { "class": "hostClasses()", "attr.data-checked": "checked()", "attr.data-disabled": "effectiveDisabled() || null" }, classAttribute: "tum-ui-toggle-switch" }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiToggleSwitchComponent), multi: true }], ngImport: i0, template: "<input\n    type=\"checkbox\"\n    role=\"switch\"\n    class=\"tum-ui-toggle-switch-input\"\n    [id]=\"inputId()\"\n    [checked]=\"checked()\"\n    [disabled]=\"effectiveDisabled()\"\n    [attr.aria-label]=\"effectiveAriaLabel()\"\n    [attr.aria-labelledby]=\"effectiveAriaLabelledBy()\"\n    (change)=\"onInputChange($event)\"\n    (blur)=\"onInputBlur()\"\n/>\n<span class=\"tum-ui-toggle-switch-handle tum:bg-content-background\" aria-hidden=\"true\"></span>\n", styles: [":host{display:inline-block;position:relative;box-sizing:border-box;width:calc(var(--tumaet-ui-spacing) * 10);height:calc(var(--tumaet-ui-spacing) * 6);flex-shrink:0;border:1px solid transparent;border-radius:30px;cursor:pointer;vertical-align:middle;outline-color:transparent;transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}:host([data-disabled=true]){cursor:default}:host(:has(.tum-ui-toggle-switch-input:focus-visible)){outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-toggle-switch-input{position:absolute;z-index:1;inset:0;width:100%;height:100%;margin:0;opacity:0;cursor:pointer}.tum-ui-toggle-switch-input:disabled{cursor:default}.tum-ui-toggle-switch-handle{position:absolute;top:50%;width:calc(var(--tumaet-ui-spacing) * 4);height:calc(var(--tumaet-ui-spacing) * 4);margin-block-start:calc(var(--tumaet-ui-spacing) * -2);inset-inline-start:var(--tumaet-ui-spacing);border-radius:50%;transition:background-color .2s,inset-inline-start .2s}:host([data-checked=true]) .tum-ui-toggle-switch-handle{inset-inline-start:calc(var(--tumaet-ui-spacing) * 5)}@media(prefers-reduced-motion:reduce){.tum-ui-toggle-switch-handle{transition-property:background-color}}@media(forced-colors:active){.tum-ui-toggle-switch-input{appearance:auto;opacity:1}.tum-ui-toggle-switch-handle{display:none}}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiToggleSwitchComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-toggle-switch', host: {
                        // The identity class is static so it is always present; only the state classes are bound.
                        class: 'tum-ui-toggle-switch',
                        '[class]': 'hostClasses()',
                        '[attr.data-checked]': 'checked()',
                        '[attr.data-disabled]': 'effectiveDisabled() || null',
                    }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiToggleSwitchComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<input\n    type=\"checkbox\"\n    role=\"switch\"\n    class=\"tum-ui-toggle-switch-input\"\n    [id]=\"inputId()\"\n    [checked]=\"checked()\"\n    [disabled]=\"effectiveDisabled()\"\n    [attr.aria-label]=\"effectiveAriaLabel()\"\n    [attr.aria-labelledby]=\"effectiveAriaLabelledBy()\"\n    (change)=\"onInputChange($event)\"\n    (blur)=\"onInputBlur()\"\n/>\n<span class=\"tum-ui-toggle-switch-handle tum:bg-content-background\" aria-hidden=\"true\"></span>\n", styles: [":host{display:inline-block;position:relative;box-sizing:border-box;width:calc(var(--tumaet-ui-spacing) * 10);height:calc(var(--tumaet-ui-spacing) * 6);flex-shrink:0;border:1px solid transparent;border-radius:30px;cursor:pointer;vertical-align:middle;outline-color:transparent;transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}:host([data-disabled=true]){cursor:default}:host(:has(.tum-ui-toggle-switch-input:focus-visible)){outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-toggle-switch-input{position:absolute;z-index:1;inset:0;width:100%;height:100%;margin:0;opacity:0;cursor:pointer}.tum-ui-toggle-switch-input:disabled{cursor:default}.tum-ui-toggle-switch-handle{position:absolute;top:50%;width:calc(var(--tumaet-ui-spacing) * 4);height:calc(var(--tumaet-ui-spacing) * 4);margin-block-start:calc(var(--tumaet-ui-spacing) * -2);inset-inline-start:var(--tumaet-ui-spacing);border-radius:50%;transition:background-color .2s,inset-inline-start .2s}:host([data-checked=true]) .tum-ui-toggle-switch-handle{inset-inline-start:calc(var(--tumaet-ui-spacing) * 5)}@media(prefers-reduced-motion:reduce){.tum-ui-toggle-switch-handle{transition-property:background-color}}@media(forced-colors:active){.tum-ui-toggle-switch-input{appearance:auto;opacity:1}.tum-ui-toggle-switch-handle{display:none}}\n"] }]
        }], propDecorators: { disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }], changed: [{ type: i0.Output, args: ["changed"] }] } });

const MEDIA_BASE = 'tum-ui-empty-media tum:flex tum:items-center tum:justify-center tum:text-muted';
const MEDIA_VARIANT = {
    // Bare artwork: an illustration or an image brings its own frame, so the slot only centres it.
    default: '',
    // A single glyph needs a shape around it, or it reads as a stray character rather than a placeholder.
    icon: 'tum:size-12 tum:rounded-xl tum:bg-hover-background tum:text-xl',
};
/**
 * Groups the media, title and description of a {@link TumUiEmptyComponent} so the action below them is separated
 * from the explanation above them by a single gap rather than by four equal ones.
 */
class TumUiEmptyHeaderComponent {
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyHeaderComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "14.0.0", version: "22.1.5", type: TumUiEmptyHeaderComponent, isStandalone: true, selector: "tum-ui-empty-header", host: { properties: { "attr.data-slot": "\"empty-header\"" }, classAttribute: "tum-ui-empty-header tum:flex tum:flex-col tum:items-center tum:gap-2" }, ngImport: i0, template: '<ng-content />', isInline: true, changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyHeaderComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'tum-ui-empty-header',
                    template: '<ng-content />',
                    host: {
                        class: 'tum-ui-empty-header tum:flex tum:flex-col tum:items-center tum:gap-2',
                        '[attr.data-slot]': '"empty-header"',
                    },
                    changeDetection: ChangeDetectionStrategy.OnPush,
                }]
        }] });
/**
 * Leading graphic of an empty state.
 *
 * It is `aria-hidden`: the graphic restates what the title already says, and an unlabelled decorative glyph
 * announced before the explanation is noise.
 */
class TumUiEmptyMediaComponent {
    /** `icon` frames a single glyph in a tinted square; `default` leaves an illustration to bring its own frame. */
    variant = input('default', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "variant" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => `${MEDIA_BASE} ${MEDIA_VARIANT[this.variant()]}`.trimEnd(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyMediaComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiEmptyMediaComponent, isStandalone: true, selector: "tum-ui-empty-media", inputs: { variant: { classPropertyName: "variant", publicName: "variant", isSignal: true, isRequired: false, transformFunction: null } }, host: { attributes: { "aria-hidden": "true" }, properties: { "class": "hostClasses()", "attr.data-slot": "\"empty-media\"", "attr.data-variant": "variant()" } }, ngImport: i0, template: '<ng-content />', isInline: true, changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyMediaComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'tum-ui-empty-media',
                    template: '<ng-content />',
                    host: {
                        '[class]': 'hostClasses()',
                        'aria-hidden': 'true',
                        '[attr.data-slot]': '"empty-media"',
                        '[attr.data-variant]': 'variant()',
                    },
                    changeDetection: ChangeDetectionStrategy.OnPush,
                }]
        }], propDecorators: { variant: [{ type: i0.Input, args: [{ isSignal: true, alias: "variant", required: false }] }] } });
/**
 * The sentence that names what is missing.
 *
 * It renders as emphasised body text and **not** as a heading: an empty state usually replaces the content of a
 * section that already has one, and a second heading at an arbitrary level would corrupt the page outline. Wrap it
 * in your own `<h*>` where the empty state genuinely opens a new section.
 */
class TumUiEmptyTitleComponent {
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyTitleComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "14.0.0", version: "22.1.5", type: TumUiEmptyTitleComponent, isStandalone: true, selector: "tum-ui-empty-title", host: { properties: { "attr.data-slot": "\"empty-title\"" }, classAttribute: "tum-ui-empty-title tum:m-0 tum:text-base tum:font-semibold tum:text-text tum:text-balance" }, ngImport: i0, template: '<ng-content />', isInline: true, changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyTitleComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'tum-ui-empty-title',
                    template: '<ng-content />',
                    host: {
                        class: 'tum-ui-empty-title tum:m-0 tum:text-base tum:font-semibold tum:text-text tum:text-balance',
                        '[attr.data-slot]': '"empty-title"',
                    },
                    changeDetection: ChangeDetectionStrategy.OnPush,
                }]
        }] });
/** Supporting sentence: what would be here, or who can put something here. */
class TumUiEmptyDescriptionComponent {
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyDescriptionComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "14.0.0", version: "22.1.5", type: TumUiEmptyDescriptionComponent, isStandalone: true, selector: "tum-ui-empty-description", host: { properties: { "attr.data-slot": "\"empty-description\"" }, classAttribute: "tum-ui-empty-description tum:m-0 tum:max-w-prose tum:text-sm tum:text-muted tum:text-balance" }, ngImport: i0, template: '<ng-content />', isInline: true, changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyDescriptionComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'tum-ui-empty-description',
                    template: '<ng-content />',
                    host: {
                        class: 'tum-ui-empty-description tum:m-0 tum:max-w-prose tum:text-sm tum:text-muted tum:text-balance',
                        '[attr.data-slot]': '"empty-description"',
                    },
                    changeDetection: ChangeDetectionStrategy.OnPush,
                }]
        }] });
/** Everything a reader can act on: the control that resolves the emptiness, or a link to whoever can. */
class TumUiEmptyContentComponent {
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyContentComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "14.0.0", version: "22.1.5", type: TumUiEmptyContentComponent, isStandalone: true, selector: "tum-ui-empty-content", host: { properties: { "attr.data-slot": "\"empty-content\"" }, classAttribute: "tum-ui-empty-content tum:flex tum:flex-wrap tum:items-center tum:justify-center tum:gap-2 tum:text-sm" }, ngImport: i0, template: '<ng-content />', isInline: true, changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyContentComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'tum-ui-empty-content',
                    template: '<ng-content />',
                    host: {
                        class: 'tum-ui-empty-content tum:flex tum:flex-wrap tum:items-center tum:justify-center tum:gap-2 tum:text-sm',
                        '[attr.data-slot]': '"empty-content"',
                    },
                    changeDetection: ChangeDetectionStrategy.OnPush,
                }]
        }] });

const EMPTY_BASE = 'tum-ui-empty tum:flex tum:flex-col tum:items-center tum:justify-center tum:text-center tum:text-text';
const EMPTY_SIZE = {
    small: 'tum:gap-2 tum:px-3 tum:py-4',
    medium: 'tum:gap-3 tum:px-4 tum:py-8',
    large: 'tum:gap-4 tum:px-6 tum:py-14',
};
/**
 * The place where something would be, when there is nothing there yet.
 *
 * "Empty" is a state a surface is in, not a message it prints, so this component owns the shape and the consumer
 * owns every word: there is no `title` or `description` string input, only slots. Compose it from
 * `tum-ui-empty-header` (with `-media`, `-title` and `-description` inside) and `tum-ui-empty-content` for the
 * action that resolves the emptiness.
 *
 * ```html
 * <tum-ui-empty size="small">
 *     <tum-ui-empty-header>
 *         <tum-ui-empty-media variant="icon"><fa-icon [icon]="faInbox" /></tum-ui-empty-media>
 *         <tum-ui-empty-title>Nothing here yet</tum-ui-empty-title>
 *         <tum-ui-empty-description>Items you add will appear in this list.</tum-ui-empty-description>
 *     </tum-ui-empty-header>
 *     <tum-ui-empty-content><tum-ui-button size="small">Add an item</tum-ui-button></tum-ui-empty-content>
 * </tum-ui-empty>
 * ```
 *
 * **It carries no role, deliberately.** An empty state is ambient: it is what the region looks like, not an event
 * that just happened, so it must not be a live region and must not announce itself. It is also not a heading —
 * `tum-ui-empty-title` renders a paragraph, and a consumer replacing a titled section keeps their own heading
 * above it.
 *
 * Give it an action, or name who has one. An empty state with neither is an apology.
 */
class TumUiEmptyComponent {
    /** Vertical room the placeholder claims. Use `small` inside a card or a panel, `large` for a whole page. */
    size = input('medium', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    effectiveSize = computed(() => this.size(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "effectiveSize" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => `${EMPTY_BASE} ${EMPTY_SIZE[this.effectiveSize()]}`, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiEmptyComponent, isStandalone: true, selector: "tum-ui-empty", inputs: { size: { classPropertyName: "size", publicName: "size", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "class": "hostClasses()", "attr.data-slot": "\"empty\"", "attr.data-size": "effectiveSize()" } }, ngImport: i0, template: '<ng-content />', isInline: true, changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiEmptyComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'tum-ui-empty',
                    template: '<ng-content />',
                    host: {
                        '[class]': 'hostClasses()',
                        '[attr.data-slot]': '"empty"',
                        '[attr.data-size]': 'effectiveSize()',
                    },
                    changeDetection: ChangeDetectionStrategy.OnPush,
                }]
        }], propDecorators: { size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }] } });

/**
 * Applies document typography to projected or rendered HTML. The consumer is responsible for sanitization.
 *
 * Bind `[innerHTML]` directly to this element: block spacing uses direct-child selectors, so an intervening
 * wrapper loses that spacing. The component does not change heading levels or add markup.
 *
 * ```html
 * <tum-ui-prose density="compact" [innerHTML]="renderedMarkdown()"></tum-ui-prose>
 * <article tumUiProse [innerHTML]="renderedMarkdown()"></article>
 * ```
 *
 * `ViewEncapsulation.None` lets styles reach `[innerHTML]` content. All rules are scoped under `.tum-ui-prose`;
 * keeping that class outside `:where()` gives them precedence over bare host-page heading rules.
 */
class TumUiProseComponent {
    /** Block rhythm. `compact` tightens the spacing between blocks for prose inside a panel or a card body. */
    density = input('comfortable', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "density" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiProseComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiProseComponent, isStandalone: true, selector: "tum-ui-prose, [tumUiProse]", inputs: { density: { classPropertyName: "density", publicName: "density", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "attr.data-slot": "\"prose\"", "attr.data-density": "density()" }, classAttribute: "tum-ui-prose tum:block tum:text-text" }, ngImport: i0, template: '<ng-content />', isInline: true, styles: [".tum-ui-prose{--tum-ui-prose-measure: 65ch;--tum-ui-prose-block-gap: calc(var(--tumaet-ui-spacing) * 4);max-width:var(--tum-ui-prose-measure);color:var(--tumaet-ui-text-color);font-family:var(--tumaet-ui-font-family);font-size:var(--tumaet-ui-font-size-base);line-height:var(--tumaet-ui-line-height-base);overflow-wrap:break-word}.tum-ui-prose[data-density=compact]{--tum-ui-prose-block-gap: calc(var(--tumaet-ui-spacing) * 3);font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm)}.tum-ui-prose>*{margin-block:0}.tum-ui-prose>*+*{margin-block-start:var(--tum-ui-prose-block-gap)}.tum-ui-prose>*+h1,.tum-ui-prose>*+h2,.tum-ui-prose>*+h3,.tum-ui-prose>*+h4,.tum-ui-prose>*+h5,.tum-ui-prose>*+h6{margin-block-start:calc(var(--tum-ui-prose-block-gap) * 1.75)}.tum-ui-prose h1,.tum-ui-prose h2,.tum-ui-prose h3,.tum-ui-prose h4,.tum-ui-prose h5,.tum-ui-prose h6{color:var(--tumaet-ui-text-color);font-weight:600;text-wrap:balance}.tum-ui-prose h1{font-size:var(--tumaet-ui-font-size-xl);line-height:var(--tumaet-ui-line-height-xl)}.tum-ui-prose h2{font-size:var(--tumaet-ui-font-size-lg);line-height:var(--tumaet-ui-line-height-lg)}.tum-ui-prose h3{font-size:var(--tumaet-ui-font-size-base);line-height:var(--tumaet-ui-line-height-base)}.tum-ui-prose h4,.tum-ui-prose h5,.tum-ui-prose h6{font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm);text-transform:none}.tum-ui-prose p{margin-block:0}.tum-ui-prose ul,.tum-ui-prose ol{margin-block:0;padding-inline-start:calc(var(--tumaet-ui-spacing) * 6)}.tum-ui-prose ul{list-style:disc}.tum-ui-prose ol{list-style:decimal}.tum-ui-prose li+li{margin-block-start:calc(var(--tumaet-ui-spacing) * 1.5)}.tum-ui-prose li>ul,.tum-ui-prose li>ol{margin-block-start:calc(var(--tumaet-ui-spacing) * 1.5)}.tum-ui-prose li::marker{color:var(--tumaet-ui-muted-color)}.tum-ui-prose a{color:var(--tumaet-ui-accent-color);text-decoration:underline;text-underline-offset:2px}.tum-ui-prose a:hover{color:var(--tumaet-ui-primary-color)}.tum-ui-prose a:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px;border-radius:var(--tumaet-ui-radius-sm)}.tum-ui-prose strong,.tum-ui-prose b{font-weight:600}.tum-ui-prose code{padding:calc(var(--tumaet-ui-spacing) * .5) calc(var(--tumaet-ui-spacing) * 1.5);border-radius:var(--tumaet-ui-radius-sm);background:var(--tumaet-ui-code-background);color:var(--tumaet-ui-code-color);font-family:var(--tumaet-ui-font-family-mono);font-size:.9em}.tum-ui-prose pre{max-width:100%;overflow-x:auto;padding:calc(var(--tumaet-ui-spacing) * 3) calc(var(--tumaet-ui-spacing) * 4);border-radius:var(--tumaet-ui-radius-md);background:var(--tumaet-ui-code-background);color:var(--tumaet-ui-code-color);font-family:var(--tumaet-ui-font-family-mono);font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm);tab-size:4}.tum-ui-prose pre code{padding:0;background:none;font-size:inherit}.tum-ui-prose blockquote{padding-inline-start:calc(var(--tumaet-ui-spacing) * 4);border-inline-start:3px solid var(--tumaet-ui-control-border-color);color:var(--tumaet-ui-muted-color)}.tum-ui-prose hr{height:0;border:0;border-block-start:1px solid var(--tumaet-ui-border-color);opacity:1}.tum-ui-prose table{max-width:100%;border-collapse:collapse;font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm)}.tum-ui-prose th,.tum-ui-prose td{padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 3);border-block-end:1px solid var(--tumaet-ui-border-color);text-align:start;vertical-align:top}.tum-ui-prose th{font-weight:600}.tum-ui-prose img,.tum-ui-prose video{max-width:100%;height:auto;border-radius:var(--tumaet-ui-radius-md)}@media(forced-colors:active){.tum-ui-prose pre,.tum-ui-prose code{border:1px solid CanvasText}}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush, encapsulation: i0.ViewEncapsulation.None });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiProseComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-prose, [tumUiProse]', template: '<ng-content />', encapsulation: ViewEncapsulation.None, host: {
                        class: 'tum-ui-prose tum:block tum:text-text',
                        '[attr.data-slot]': '"prose"',
                        '[attr.data-density]': 'density()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [".tum-ui-prose{--tum-ui-prose-measure: 65ch;--tum-ui-prose-block-gap: calc(var(--tumaet-ui-spacing) * 4);max-width:var(--tum-ui-prose-measure);color:var(--tumaet-ui-text-color);font-family:var(--tumaet-ui-font-family);font-size:var(--tumaet-ui-font-size-base);line-height:var(--tumaet-ui-line-height-base);overflow-wrap:break-word}.tum-ui-prose[data-density=compact]{--tum-ui-prose-block-gap: calc(var(--tumaet-ui-spacing) * 3);font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm)}.tum-ui-prose>*{margin-block:0}.tum-ui-prose>*+*{margin-block-start:var(--tum-ui-prose-block-gap)}.tum-ui-prose>*+h1,.tum-ui-prose>*+h2,.tum-ui-prose>*+h3,.tum-ui-prose>*+h4,.tum-ui-prose>*+h5,.tum-ui-prose>*+h6{margin-block-start:calc(var(--tum-ui-prose-block-gap) * 1.75)}.tum-ui-prose h1,.tum-ui-prose h2,.tum-ui-prose h3,.tum-ui-prose h4,.tum-ui-prose h5,.tum-ui-prose h6{color:var(--tumaet-ui-text-color);font-weight:600;text-wrap:balance}.tum-ui-prose h1{font-size:var(--tumaet-ui-font-size-xl);line-height:var(--tumaet-ui-line-height-xl)}.tum-ui-prose h2{font-size:var(--tumaet-ui-font-size-lg);line-height:var(--tumaet-ui-line-height-lg)}.tum-ui-prose h3{font-size:var(--tumaet-ui-font-size-base);line-height:var(--tumaet-ui-line-height-base)}.tum-ui-prose h4,.tum-ui-prose h5,.tum-ui-prose h6{font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm);text-transform:none}.tum-ui-prose p{margin-block:0}.tum-ui-prose ul,.tum-ui-prose ol{margin-block:0;padding-inline-start:calc(var(--tumaet-ui-spacing) * 6)}.tum-ui-prose ul{list-style:disc}.tum-ui-prose ol{list-style:decimal}.tum-ui-prose li+li{margin-block-start:calc(var(--tumaet-ui-spacing) * 1.5)}.tum-ui-prose li>ul,.tum-ui-prose li>ol{margin-block-start:calc(var(--tumaet-ui-spacing) * 1.5)}.tum-ui-prose li::marker{color:var(--tumaet-ui-muted-color)}.tum-ui-prose a{color:var(--tumaet-ui-accent-color);text-decoration:underline;text-underline-offset:2px}.tum-ui-prose a:hover{color:var(--tumaet-ui-primary-color)}.tum-ui-prose a:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px;border-radius:var(--tumaet-ui-radius-sm)}.tum-ui-prose strong,.tum-ui-prose b{font-weight:600}.tum-ui-prose code{padding:calc(var(--tumaet-ui-spacing) * .5) calc(var(--tumaet-ui-spacing) * 1.5);border-radius:var(--tumaet-ui-radius-sm);background:var(--tumaet-ui-code-background);color:var(--tumaet-ui-code-color);font-family:var(--tumaet-ui-font-family-mono);font-size:.9em}.tum-ui-prose pre{max-width:100%;overflow-x:auto;padding:calc(var(--tumaet-ui-spacing) * 3) calc(var(--tumaet-ui-spacing) * 4);border-radius:var(--tumaet-ui-radius-md);background:var(--tumaet-ui-code-background);color:var(--tumaet-ui-code-color);font-family:var(--tumaet-ui-font-family-mono);font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm);tab-size:4}.tum-ui-prose pre code{padding:0;background:none;font-size:inherit}.tum-ui-prose blockquote{padding-inline-start:calc(var(--tumaet-ui-spacing) * 4);border-inline-start:3px solid var(--tumaet-ui-control-border-color);color:var(--tumaet-ui-muted-color)}.tum-ui-prose hr{height:0;border:0;border-block-start:1px solid var(--tumaet-ui-border-color);opacity:1}.tum-ui-prose table{max-width:100%;border-collapse:collapse;font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm)}.tum-ui-prose th,.tum-ui-prose td{padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 3);border-block-end:1px solid var(--tumaet-ui-border-color);text-align:start;vertical-align:top}.tum-ui-prose th{font-weight:600}.tum-ui-prose img,.tum-ui-prose video{max-width:100%;height:auto;border-radius:var(--tumaet-ui-radius-md)}@media(forced-colors:active){.tum-ui-prose pre,.tum-ui-prose code{border:1px solid CanvasText}}\n"] }]
        }], propDecorators: { density: [{ type: i0.Input, args: [{ isSignal: true, alias: "density", required: false }] }] } });

/**
 * A muted block standing in for content that is on its way.
 *
 * Use it for a **1–10 second** first load, in a box the arriving content will occupy, so the page does not resize
 * around the reader when the data lands. Under a second, show nothing — a placeholder that flashes is worse than a
 * beat of stillness. Past ten seconds, a placeholder stops being honest: show progress instead.
 *
 * ```html
 * <div [attr.aria-busy]="loading() || null">
 *     @if (loading()) {
 *         <span class="tum:sr-only">Loading files</span>
 *         <tum-ui-skeleton lines="3" />
 *     } @else { … }
 * </div>
 * ```
 *
 * **It does not shimmer, and that is a decision, not an omission.** A shimmer is an infinite, auto-starting
 * animation that runs well past five seconds alongside other content, which is what WCAG 2.2.2 is about; and it
 * says nothing a still block does not. The one motion here is the crossfade *out*: give the skeleton and the
 * content the same grid cell and they exchange places without a jump.
 *
 * **The skeleton is `aria-hidden`, and the container carries the announcement.** A placeholder is a picture of
 * absent content, not a status; assistive technology needs `aria-busy` on the region plus a word, which is the
 * consumer's to write because only the consumer knows what is loading.
 */
class TumUiSkeletonComponent {
    /** Any CSS length. Omit it and the placeholder fills its container, which is usually what you want. */
    width = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "width" }] : /* istanbul ignore next */ []));
    /** Any CSS length. Set it to reserve the exact box the arriving content will occupy. */
    height = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "height" }] : /* istanbul ignore next */ []));
    /**
     * Number of stacked text lines. The last line is drawn short, because that is what a paragraph of prose looks
     * like and the difference is what stops a stack of bars reading as a table.
     */
    lines = input(1, { ...(ngDevMode ? { debugName: "lines" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    lineCount = computed(() => {
        const lines = Math.trunc(this.lines());
        return Number.isFinite(lines) && lines > 1 ? lines : 1;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "lineCount" }] : /* istanbul ignore next */ []));
    lineIndices = computed(() => Array.from({ length: this.lineCount() }, (_value, index) => index), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "lineIndices" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiSkeletonComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiSkeletonComponent, isStandalone: true, selector: "tum-ui-skeleton", inputs: { width: { classPropertyName: "width", publicName: "width", isSignal: true, isRequired: false, transformFunction: null }, height: { classPropertyName: "height", publicName: "height", isSignal: true, isRequired: false, transformFunction: null }, lines: { classPropertyName: "lines", publicName: "lines", isSignal: true, isRequired: false, transformFunction: null } }, host: { attributes: { "aria-hidden": "true" }, properties: { "attr.data-slot": "\"skeleton\"", "attr.data-lines": "lineCount()", "style.width": "width()", "style.height": "height()" }, classAttribute: "tum-ui-skeleton" }, ngImport: i0, template: "@for (index of lineIndices(); track index) {\n    <span class=\"tum-ui-skeleton-line\"></span>\n}\n", styles: [":host{--tum-ui-skeleton-color: var(--tumaet-ui-hover-background);--tum-ui-skeleton-line-height: calc(var(--tumaet-ui-spacing) * 3);--tum-ui-skeleton-gap: calc(var(--tumaet-ui-spacing) * 2);display:flex;flex-direction:column;gap:var(--tum-ui-skeleton-gap);min-height:var(--tum-ui-skeleton-line-height);transition:opacity var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard)}.tum-ui-skeleton-line{display:block;flex:1 1 auto;min-height:var(--tum-ui-skeleton-line-height);border-radius:var(--tumaet-ui-radius-md);background:var(--tum-ui-skeleton-color)}:host(:not([data-lines=\"1\"])) .tum-ui-skeleton-line:last-child{max-width:60%}@media(forced-colors:active){.tum-ui-skeleton-line{border:1px solid GrayText;background:none;forced-color-adjust:none}}@media(prefers-reduced-motion:reduce){:host{transition:none}}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiSkeletonComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-skeleton', host: {
                        class: 'tum-ui-skeleton',
                        'aria-hidden': 'true',
                        '[attr.data-slot]': '"skeleton"',
                        '[attr.data-lines]': 'lineCount()',
                        '[style.width]': 'width()',
                        '[style.height]': 'height()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "@for (index of lineIndices(); track index) {\n    <span class=\"tum-ui-skeleton-line\"></span>\n}\n", styles: [":host{--tum-ui-skeleton-color: var(--tumaet-ui-hover-background);--tum-ui-skeleton-line-height: calc(var(--tumaet-ui-spacing) * 3);--tum-ui-skeleton-gap: calc(var(--tumaet-ui-spacing) * 2);display:flex;flex-direction:column;gap:var(--tum-ui-skeleton-gap);min-height:var(--tum-ui-skeleton-line-height);transition:opacity var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard)}.tum-ui-skeleton-line{display:block;flex:1 1 auto;min-height:var(--tum-ui-skeleton-line-height);border-radius:var(--tumaet-ui-radius-md);background:var(--tum-ui-skeleton-color)}:host(:not([data-lines=\"1\"])) .tum-ui-skeleton-line:last-child{max-width:60%}@media(forced-colors:active){.tum-ui-skeleton-line{border:1px solid GrayText;background:none;forced-color-adjust:none}}@media(prefers-reduced-motion:reduce){:host{transition:none}}\n"] }]
        }], propDecorators: { width: [{ type: i0.Input, args: [{ isSignal: true, alias: "width", required: false }] }], height: [{ type: i0.Input, args: [{ isSignal: true, alias: "height", required: false }] }], lines: [{ type: i0.Input, args: [{ isSignal: true, alias: "lines", required: false }] }] } });

/**
 * Compact state indicator: a dot with its state word.
 *
 * The word is the accessible name and is always rendered — hiding it with `showLabel` keeps it available to assistive
 * technology, so colour is never the only signal. Shape carries the states that share the muted colour: `neutral` is a
 * solid dot, `queued` a ring, `unknown` a dashed ring.
 */
class TumUiStatusDotComponent {
    /** Semantic state the dot reports. */
    state = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "state" }] : /* istanbul ignore next */ []));
    /** Translated human state word; it is the accessible name of the indicator. */
    label = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "label" }] : /* istanbul ignore next */ []));
    /** Renders the label visually. When disabled the label stays in the accessibility tree. */
    showLabel = input(true, { ...(ngDevMode ? { debugName: "showLabel" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    /**
     * Announces state changes as a live region. Leave it off unless this dot is the one place a change is reported —
     * a list of dots must not turn into a list of live regions.
     */
    live = input(false, { ...(ngDevMode ? { debugName: "live" } : /* istanbul ignore next */ {}), transform: booleanAttribute });
    labelClasses = computed(() => `tum-ui-status-dot-label ${this.showLabel() ? '' : 'tum:sr-only'}`.trimEnd(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "labelClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiStatusDotComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiStatusDotComponent, isStandalone: true, selector: "tum-ui-status-dot", inputs: { state: { classPropertyName: "state", publicName: "state", isSignal: true, isRequired: true, transformFunction: null }, label: { classPropertyName: "label", publicName: "label", isSignal: true, isRequired: true, transformFunction: null }, showLabel: { classPropertyName: "showLabel", publicName: "showLabel", isSignal: true, isRequired: false, transformFunction: null }, live: { classPropertyName: "live", publicName: "live", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "attr.data-slot": "\"status-dot\"", "attr.role": "live() ? 'status' : null", "attr.data-state": "state()" }, classAttribute: "tum-ui-status-dot tum:inline-flex tum:items-center tum:gap-2 tum:text-sm tum:text-text" }, ngImport: i0, template: "<span class=\"tum-ui-status-dot-indicator\" aria-hidden=\"true\"></span>\n<span [class]=\"labelClasses()\">{{ label() }}</span>\n", styles: [":host{--tum-ui-status-dot-size: calc(var(--tumaet-ui-spacing) * 2);--tum-ui-status-dot-fill: var(--tumaet-ui-muted-color);--tum-ui-status-dot-edge: transparent;--tum-ui-status-dot-edge-style: solid}:host([data-state=running]){--tum-ui-status-dot-fill: var(--tumaet-ui-state-info-foreground)}:host([data-state=success]){--tum-ui-status-dot-fill: var(--tumaet-ui-state-success-foreground)}:host([data-state=warning]){--tum-ui-status-dot-fill: var(--tumaet-ui-state-warning-foreground)}:host([data-state=danger]){--tum-ui-status-dot-fill: var(--tumaet-ui-state-danger-foreground)}:host([data-state=queued]){--tum-ui-status-dot-size: calc(var(--tumaet-ui-spacing) * 2.5);--tum-ui-status-dot-fill: transparent;--tum-ui-status-dot-edge: var(--tumaet-ui-muted-color)}:host([data-state=unknown]){--tum-ui-status-dot-size: calc(var(--tumaet-ui-spacing) * 2.5);--tum-ui-status-dot-fill: transparent;--tum-ui-status-dot-edge: var(--tumaet-ui-muted-color);--tum-ui-status-dot-edge-style: dashed}.tum-ui-status-dot-indicator{display:block;width:var(--tum-ui-status-dot-size);height:var(--tum-ui-status-dot-size);box-sizing:border-box;flex-shrink:0;border:2px var(--tum-ui-status-dot-edge-style) var(--tum-ui-status-dot-edge);border-radius:50%;background:var(--tum-ui-status-dot-fill)}@media(prefers-reduced-motion:no-preference){:host([data-state=queued]) .tum-ui-status-dot-indicator,:host([data-state=running]) .tum-ui-status-dot-indicator{animation:tum-ui-status-dot-pulse var(--tumaet-ui-duration-pulse) ease-in-out infinite}.tum-ui-status-dot-indicator{transition:border-color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard),background-color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard),color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard)}}@keyframes tum-ui-status-dot-pulse{0%,to{opacity:1}50%{opacity:.45}}@media(forced-colors:active){.tum-ui-status-dot-indicator{border-color:CanvasText;background:CanvasText;forced-color-adjust:none}:host([data-state=queued]) .tum-ui-status-dot-indicator,:host([data-state=unknown]) .tum-ui-status-dot-indicator{background:Canvas}:host([data-state=running]) .tum-ui-status-dot-indicator{border-color:Highlight;background:Highlight}}\n"], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiStatusDotComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-status-dot', host: {
                        '[attr.data-slot]': '"status-dot"',
                        class: 'tum-ui-status-dot tum:inline-flex tum:items-center tum:gap-2 tum:text-sm tum:text-text',
                        '[attr.role]': "live() ? 'status' : null",
                        '[attr.data-state]': 'state()',
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<span class=\"tum-ui-status-dot-indicator\" aria-hidden=\"true\"></span>\n<span [class]=\"labelClasses()\">{{ label() }}</span>\n", styles: [":host{--tum-ui-status-dot-size: calc(var(--tumaet-ui-spacing) * 2);--tum-ui-status-dot-fill: var(--tumaet-ui-muted-color);--tum-ui-status-dot-edge: transparent;--tum-ui-status-dot-edge-style: solid}:host([data-state=running]){--tum-ui-status-dot-fill: var(--tumaet-ui-state-info-foreground)}:host([data-state=success]){--tum-ui-status-dot-fill: var(--tumaet-ui-state-success-foreground)}:host([data-state=warning]){--tum-ui-status-dot-fill: var(--tumaet-ui-state-warning-foreground)}:host([data-state=danger]){--tum-ui-status-dot-fill: var(--tumaet-ui-state-danger-foreground)}:host([data-state=queued]){--tum-ui-status-dot-size: calc(var(--tumaet-ui-spacing) * 2.5);--tum-ui-status-dot-fill: transparent;--tum-ui-status-dot-edge: var(--tumaet-ui-muted-color)}:host([data-state=unknown]){--tum-ui-status-dot-size: calc(var(--tumaet-ui-spacing) * 2.5);--tum-ui-status-dot-fill: transparent;--tum-ui-status-dot-edge: var(--tumaet-ui-muted-color);--tum-ui-status-dot-edge-style: dashed}.tum-ui-status-dot-indicator{display:block;width:var(--tum-ui-status-dot-size);height:var(--tum-ui-status-dot-size);box-sizing:border-box;flex-shrink:0;border:2px var(--tum-ui-status-dot-edge-style) var(--tum-ui-status-dot-edge);border-radius:50%;background:var(--tum-ui-status-dot-fill)}@media(prefers-reduced-motion:no-preference){:host([data-state=queued]) .tum-ui-status-dot-indicator,:host([data-state=running]) .tum-ui-status-dot-indicator{animation:tum-ui-status-dot-pulse var(--tumaet-ui-duration-pulse) ease-in-out infinite}.tum-ui-status-dot-indicator{transition:border-color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard),background-color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard),color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard)}}@keyframes tum-ui-status-dot-pulse{0%,to{opacity:1}50%{opacity:.45}}@media(forced-colors:active){.tum-ui-status-dot-indicator{border-color:CanvasText;background:CanvasText;forced-color-adjust:none}:host([data-state=queued]) .tum-ui-status-dot-indicator,:host([data-state=unknown]) .tum-ui-status-dot-indicator{background:Canvas}:host([data-state=running]) .tum-ui-status-dot-indicator{border-color:Highlight;background:Highlight}}\n"] }]
        }], propDecorators: { state: [{ type: i0.Input, args: [{ isSignal: true, alias: "state", required: true }] }], label: [{ type: i0.Input, args: [{ isSignal: true, alias: "label", required: true }] }], showLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "showLabel", required: false }] }], live: [{ type: i0.Input, args: [{ isSignal: true, alias: "live", required: false }] }] } });

/**
 * Publishes the stepper's layout to the steps it projects.
 *
 * A step is declared in the consumer's template, so it cannot read the stepper's `orientation` input directly. The
 * stepper registers that input here once, and each step reads the shared signal back. A step used without a stepper
 * injects nothing and falls back to the vertical default.
 */
class TumUiStepperService {
    source = signal(signal('vertical'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "source" }] : /* istanbul ignore next */ []));
    /** Layout the enclosing stepper currently renders. */
    orientation = computed(() => this.source()(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "orientation" }] : /* istanbul ignore next */ []));
    register(orientation) {
        this.source.set(orientation);
    }
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiStepperService, deps: [], target: i0.ɵɵFactoryTarget.Injectable });
    static ɵprov = i0.ɵɵngDeclareInjectable({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiStepperService });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiStepperService, decorators: [{
            type: Injectable
        }] });

/** States that convey their marker with an icon. `current` renders a running indicator, `pending` a hollow circle. */
const STEP_STATE_ICON = {
    pending: undefined,
    current: undefined,
    complete: faCheck,
    failed: faXmark,
    skipped: faForwardStep,
};
/** Package wording used when the consumer supplies no `stateLabel`, so the state is never colour-only. */
const STEP_STATE_KEY = {
    pending: 'tumUi.step.pending',
    current: 'tumUi.step.current',
    complete: 'tumUi.step.complete',
    failed: 'tumUi.step.failed',
    skipped: 'tumUi.step.skipped',
};
/**
 * One stage of a {@link TumUiStepperComponent} ladder.
 *
 * The step is a status display, not a control: it is neither clickable nor focusable. Its state reaches assistive
 * technology as a hidden word next to the label, never through the marker colour alone. Project detail content in the
 * default slot; it renders under the label in every state.
 *
 * The connector between two markers is drawn by the earlier step but always carries the state of the step it leads
 * into, so a colour never runs past the stage that earned it.
 */
class TumUiStepComponent {
    stepper = inject(TumUiStepperService, { optional: true });
    translator = inject(TUM_UI_TRANSLATOR);
    /** Progress state of this stage. */
    state = input('pending', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "state" }] : /* istanbul ignore next */ []));
    /** Visible stage name. Omit it when projecting a `[tumUiStepLabel]` slot instead; both render in the same line. */
    label = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "label" }] : /* istanbul ignore next */ []));
    /** Overrides the marker icon, including the running indicator of a `current` step. */
    icon = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "icon" }] : /* istanbul ignore next */ []));
    /**
     * Translated state word rendered next to the label for assistive technology only, for example "Running".
     * It defaults to the package wording for `state`; override it when the domain has a better word.
     */
    stateLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "stateLabel" }] : /* istanbul ignore next */ []));
    orientation = computed(() => this.stepper?.orientation() ?? 'vertical', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "orientation" }] : /* istanbul ignore next */ []));
    isInactive = computed(() => this.state() === 'pending' || this.state() === 'skipped', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isInactive" }] : /* istanbul ignore next */ []));
    markerIcon = computed(() => this.icon() ?? STEP_STATE_ICON[this.state()], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "markerIcon" }] : /* istanbul ignore next */ []));
    isRunning = computed(() => !this.markerIcon() && this.state() === 'current', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isRunning" }] : /* istanbul ignore next */ []));
    stateWord = computed(() => {
        const supplied = this.stateLabel()?.trim();
        if (supplied) {
            return supplied;
        }
        this.translator.translationChanges?.();
        return this.translator.translate(STEP_STATE_KEY[this.state()]);
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "stateWord" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiStepComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.0.0", version: "22.1.5", type: TumUiStepComponent, isStandalone: true, selector: "tum-ui-step", inputs: { state: { classPropertyName: "state", publicName: "state", isSignal: true, isRequired: false, transformFunction: null }, label: { classPropertyName: "label", publicName: "label", isSignal: true, isRequired: false, transformFunction: null }, icon: { classPropertyName: "icon", publicName: "icon", isSignal: true, isRequired: false, transformFunction: null }, stateLabel: { classPropertyName: "stateLabel", publicName: "stateLabel", isSignal: true, isRequired: false, transformFunction: null } }, host: { attributes: { "role": "listitem" }, properties: { "attr.data-slot": "\"step\"", "attr.data-state": "state()", "attr.data-orientation": "orientation()", "attr.aria-current": "state() === 'current' ? 'step' : null", "attr.aria-disabled": "isInactive() ? 'true' : null" }, classAttribute: "tum-ui-step" }, ngImport: i0, template: "<span class=\"tum-ui-step-marker-track\" aria-hidden=\"true\">\n    <span class=\"tum-ui-step-marker\">\n        @if (markerIcon(); as icon) {\n            <fa-icon [icon]=\"icon\" />\n        } @else if (isRunning()) {\n            <span class=\"tum-ui-step-running-indicator\"></span>\n            <span class=\"tum-ui-step-running-fallback\"></span>\n        }\n    </span>\n    <span class=\"tum-ui-step-connector\"></span>\n</span>\n<div class=\"tum-ui-step-content\">\n    <span class=\"tum-ui-step-label\">\n        {{ label() }}<ng-content select=\"[tumUiStepLabel]\" />&ngsp;<span class=\"tum:sr-only\">{{ stateWord() }}</span>\n    </span>\n    <div class=\"tum-ui-step-detail\">\n        <ng-content />\n    </div>\n</div>\n", styles: [":host{--tum-ui-step-marker-size: calc(var(--tumaet-ui-spacing) * 6);--tum-ui-step-connector-width: 2px;--tum-ui-step-lead: calc(var(--tumaet-ui-spacing) * 6);--tum-ui-step-edge-color: var(--tumaet-ui-muted-color);--tum-ui-step-ink-color: var(--tumaet-ui-muted-color);--tum-ui-step-label-color: var(--tumaet-ui-text-color);--tum-ui-step-marker-border-style: solid;--tum-ui-step-connector-color: var(--tumaet-ui-muted-color);--tum-ui-step-connector-style: solid;display:grid;grid-template-columns:var(--tum-ui-step-marker-size) 1fr;column-gap:calc(var(--tumaet-ui-spacing) * 3);padding-block-end:var(--tum-ui-step-lead)}:host(:not(:has(+ tum-ui-step))){padding-block-end:0}:host([data-state=current]){--tum-ui-step-edge-color: var(--tumaet-ui-primary-color);--tum-ui-step-ink-color: var(--tumaet-ui-primary-color)}:host([data-state=complete]){--tum-ui-step-edge-color: var(--tumaet-ui-state-success);--tum-ui-step-ink-color: var(--tumaet-ui-state-success)}:host([data-state=failed]){--tum-ui-step-edge-color: var(--tumaet-ui-state-danger);--tum-ui-step-ink-color: var(--tumaet-ui-state-danger)}:host([data-state=skipped]){--tum-ui-step-label-color: var(--tumaet-ui-muted-color)}:host([data-state=skipped]) .tum-ui-step-marker{font-size:calc(var(--tumaet-ui-font-size-xs) * .85)}:host(:has(+ tum-ui-step[data-state=current])){--tum-ui-step-connector-color: var(--tumaet-ui-primary-color)}:host(:has(+ tum-ui-step[data-state=complete])){--tum-ui-step-connector-color: var(--tumaet-ui-state-success)}:host(:has(+ tum-ui-step[data-state=failed])){--tum-ui-step-connector-color: var(--tumaet-ui-state-danger)}:host(:has(+ tum-ui-step[data-state=skipped])){--tum-ui-step-connector-style: dashed}.tum-ui-step-marker-track{position:relative;grid-column:1;grid-row:1;width:var(--tum-ui-step-marker-size)}.tum-ui-step-marker{display:flex;box-sizing:border-box;width:var(--tum-ui-step-marker-size);height:var(--tum-ui-step-marker-size);align-items:center;justify-content:center;border-width:2px;border-style:var(--tum-ui-step-marker-border-style);border-color:var(--tum-ui-step-edge-color);border-radius:50%;background:var(--tumaet-ui-content-background);color:var(--tum-ui-step-ink-color);font-size:var(--tumaet-ui-font-size-xs);line-height:var(--tumaet-ui-line-height-xs)}.tum-ui-step-connector{position:absolute;display:none;box-sizing:border-box;width:var(--tum-ui-step-connector-width);border-inline-start:var(--tum-ui-step-connector-width) var(--tum-ui-step-connector-style) var(--tum-ui-step-connector-color);inset-block-start:var(--tum-ui-step-marker-size);inset-block-end:calc(-1 * var(--tum-ui-step-lead));inset-inline-start:50%;transform:translate(-50%)}.tum-ui-step-content{display:flex;min-width:0;flex-direction:column;gap:var(--tumaet-ui-spacing)}.tum-ui-step-label{color:var(--tum-ui-step-label-color);font-weight:500;overflow-wrap:break-word}.tum-ui-step-detail{min-width:0;color:var(--tumaet-ui-muted-color);font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm)}.tum-ui-step-detail:empty{display:none}:host(:has(+ tum-ui-step)) .tum-ui-step-connector{display:block}:host([data-orientation=horizontal]){min-width:calc(var(--tumaet-ui-spacing) * 40);flex:1 1 0%;grid-template-columns:1fr;grid-template-rows:var(--tum-ui-step-marker-size) auto;padding-block-end:0;padding-inline-end:var(--tum-ui-step-lead);row-gap:calc(var(--tumaet-ui-spacing) * 2)}:host([data-orientation=horizontal]:not(:has(+ tum-ui-step))){padding-inline-end:0}:host([data-orientation=horizontal]) .tum-ui-step-marker-track{grid-row:1;width:auto;height:var(--tum-ui-step-marker-size)}:host([data-orientation=horizontal]) .tum-ui-step-connector{width:auto;height:var(--tum-ui-step-connector-width);border-inline-start:0 none;border-block-start:var(--tum-ui-step-connector-width) var(--tum-ui-step-connector-style) var(--tum-ui-step-connector-color);inset-block-start:50%;inset-block-end:auto;inset-inline:var(--tum-ui-step-marker-size) calc(-1 * var(--tum-ui-step-lead));transform:translateY(-50%)}.tum-ui-step-running-indicator{display:none}.tum-ui-step-running-fallback{display:block;width:calc(var(--tumaet-ui-spacing) * 2.5);height:calc(var(--tumaet-ui-spacing) * 2.5);border-radius:50%;background:currentcolor}@media(prefers-reduced-motion:no-preference){.tum-ui-step-running-indicator{display:block;width:calc(var(--tumaet-ui-spacing) * 3.5);height:calc(var(--tumaet-ui-spacing) * 3.5);border:2px solid color-mix(in srgb,currentcolor 25%,transparent);border-block-start-color:currentcolor;border-radius:50%;animation:tum-ui-step-spin .9s var(--tumaet-ui-easing-linear) infinite}.tum-ui-step-running-fallback{display:none}.tum-ui-step-marker,.tum-ui-step-connector,.tum-ui-step-label{transition:border-color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard),background-color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard),color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard)}}@keyframes tum-ui-step-spin{to{transform:rotate(360deg)}}@media(forced-colors:active){.tum-ui-step-marker{border-color:CanvasText;background:Canvas;color:CanvasText;forced-color-adjust:none}:host([data-state=current]) .tum-ui-step-marker{border-color:Highlight;color:Highlight}:host([data-state=pending]) .tum-ui-step-marker,:host([data-state=skipped]) .tum-ui-step-marker{border-color:GrayText;color:GrayText}.tum-ui-step-running-fallback{background:Highlight;forced-color-adjust:none}.tum-ui-step-running-indicator{border-color:GrayText;border-block-start-color:Highlight;forced-color-adjust:none}.tum-ui-step-connector{border-color:CanvasText;forced-color-adjust:none}}\n"], dependencies: [{ kind: "component", type: FaIconComponent, selector: "fa-icon", inputs: ["icon", "title", "animation", "mask", "flip", "size", "pull", "border", "inverse", "symbol", "rotate", "fixedWidth", "transform", "a11yRole"], outputs: ["iconChange", "titleChange", "animationChange", "maskChange", "flipChange", "sizeChange", "pullChange", "borderChange", "inverseChange", "symbolChange", "rotateChange", "fixedWidthChange", "transformChange", "a11yRoleChange"] }], changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiStepComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-step', imports: [FaIconComponent], host: {
                        '[attr.data-slot]': '"step"',
                        role: 'listitem',
                        class: 'tum-ui-step',
                        '[attr.data-state]': 'state()',
                        '[attr.data-orientation]': 'orientation()',
                        '[attr.aria-current]': "state() === 'current' ? 'step' : null",
                        '[attr.aria-disabled]': "isInactive() ? 'true' : null",
                    }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<span class=\"tum-ui-step-marker-track\" aria-hidden=\"true\">\n    <span class=\"tum-ui-step-marker\">\n        @if (markerIcon(); as icon) {\n            <fa-icon [icon]=\"icon\" />\n        } @else if (isRunning()) {\n            <span class=\"tum-ui-step-running-indicator\"></span>\n            <span class=\"tum-ui-step-running-fallback\"></span>\n        }\n    </span>\n    <span class=\"tum-ui-step-connector\"></span>\n</span>\n<div class=\"tum-ui-step-content\">\n    <span class=\"tum-ui-step-label\">\n        {{ label() }}<ng-content select=\"[tumUiStepLabel]\" />&ngsp;<span class=\"tum:sr-only\">{{ stateWord() }}</span>\n    </span>\n    <div class=\"tum-ui-step-detail\">\n        <ng-content />\n    </div>\n</div>\n", styles: [":host{--tum-ui-step-marker-size: calc(var(--tumaet-ui-spacing) * 6);--tum-ui-step-connector-width: 2px;--tum-ui-step-lead: calc(var(--tumaet-ui-spacing) * 6);--tum-ui-step-edge-color: var(--tumaet-ui-muted-color);--tum-ui-step-ink-color: var(--tumaet-ui-muted-color);--tum-ui-step-label-color: var(--tumaet-ui-text-color);--tum-ui-step-marker-border-style: solid;--tum-ui-step-connector-color: var(--tumaet-ui-muted-color);--tum-ui-step-connector-style: solid;display:grid;grid-template-columns:var(--tum-ui-step-marker-size) 1fr;column-gap:calc(var(--tumaet-ui-spacing) * 3);padding-block-end:var(--tum-ui-step-lead)}:host(:not(:has(+ tum-ui-step))){padding-block-end:0}:host([data-state=current]){--tum-ui-step-edge-color: var(--tumaet-ui-primary-color);--tum-ui-step-ink-color: var(--tumaet-ui-primary-color)}:host([data-state=complete]){--tum-ui-step-edge-color: var(--tumaet-ui-state-success);--tum-ui-step-ink-color: var(--tumaet-ui-state-success)}:host([data-state=failed]){--tum-ui-step-edge-color: var(--tumaet-ui-state-danger);--tum-ui-step-ink-color: var(--tumaet-ui-state-danger)}:host([data-state=skipped]){--tum-ui-step-label-color: var(--tumaet-ui-muted-color)}:host([data-state=skipped]) .tum-ui-step-marker{font-size:calc(var(--tumaet-ui-font-size-xs) * .85)}:host(:has(+ tum-ui-step[data-state=current])){--tum-ui-step-connector-color: var(--tumaet-ui-primary-color)}:host(:has(+ tum-ui-step[data-state=complete])){--tum-ui-step-connector-color: var(--tumaet-ui-state-success)}:host(:has(+ tum-ui-step[data-state=failed])){--tum-ui-step-connector-color: var(--tumaet-ui-state-danger)}:host(:has(+ tum-ui-step[data-state=skipped])){--tum-ui-step-connector-style: dashed}.tum-ui-step-marker-track{position:relative;grid-column:1;grid-row:1;width:var(--tum-ui-step-marker-size)}.tum-ui-step-marker{display:flex;box-sizing:border-box;width:var(--tum-ui-step-marker-size);height:var(--tum-ui-step-marker-size);align-items:center;justify-content:center;border-width:2px;border-style:var(--tum-ui-step-marker-border-style);border-color:var(--tum-ui-step-edge-color);border-radius:50%;background:var(--tumaet-ui-content-background);color:var(--tum-ui-step-ink-color);font-size:var(--tumaet-ui-font-size-xs);line-height:var(--tumaet-ui-line-height-xs)}.tum-ui-step-connector{position:absolute;display:none;box-sizing:border-box;width:var(--tum-ui-step-connector-width);border-inline-start:var(--tum-ui-step-connector-width) var(--tum-ui-step-connector-style) var(--tum-ui-step-connector-color);inset-block-start:var(--tum-ui-step-marker-size);inset-block-end:calc(-1 * var(--tum-ui-step-lead));inset-inline-start:50%;transform:translate(-50%)}.tum-ui-step-content{display:flex;min-width:0;flex-direction:column;gap:var(--tumaet-ui-spacing)}.tum-ui-step-label{color:var(--tum-ui-step-label-color);font-weight:500;overflow-wrap:break-word}.tum-ui-step-detail{min-width:0;color:var(--tumaet-ui-muted-color);font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm)}.tum-ui-step-detail:empty{display:none}:host(:has(+ tum-ui-step)) .tum-ui-step-connector{display:block}:host([data-orientation=horizontal]){min-width:calc(var(--tumaet-ui-spacing) * 40);flex:1 1 0%;grid-template-columns:1fr;grid-template-rows:var(--tum-ui-step-marker-size) auto;padding-block-end:0;padding-inline-end:var(--tum-ui-step-lead);row-gap:calc(var(--tumaet-ui-spacing) * 2)}:host([data-orientation=horizontal]:not(:has(+ tum-ui-step))){padding-inline-end:0}:host([data-orientation=horizontal]) .tum-ui-step-marker-track{grid-row:1;width:auto;height:var(--tum-ui-step-marker-size)}:host([data-orientation=horizontal]) .tum-ui-step-connector{width:auto;height:var(--tum-ui-step-connector-width);border-inline-start:0 none;border-block-start:var(--tum-ui-step-connector-width) var(--tum-ui-step-connector-style) var(--tum-ui-step-connector-color);inset-block-start:50%;inset-block-end:auto;inset-inline:var(--tum-ui-step-marker-size) calc(-1 * var(--tum-ui-step-lead));transform:translateY(-50%)}.tum-ui-step-running-indicator{display:none}.tum-ui-step-running-fallback{display:block;width:calc(var(--tumaet-ui-spacing) * 2.5);height:calc(var(--tumaet-ui-spacing) * 2.5);border-radius:50%;background:currentcolor}@media(prefers-reduced-motion:no-preference){.tum-ui-step-running-indicator{display:block;width:calc(var(--tumaet-ui-spacing) * 3.5);height:calc(var(--tumaet-ui-spacing) * 3.5);border:2px solid color-mix(in srgb,currentcolor 25%,transparent);border-block-start-color:currentcolor;border-radius:50%;animation:tum-ui-step-spin .9s var(--tumaet-ui-easing-linear) infinite}.tum-ui-step-running-fallback{display:none}.tum-ui-step-marker,.tum-ui-step-connector,.tum-ui-step-label{transition:border-color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard),background-color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard),color var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard)}}@keyframes tum-ui-step-spin{to{transform:rotate(360deg)}}@media(forced-colors:active){.tum-ui-step-marker{border-color:CanvasText;background:Canvas;color:CanvasText;forced-color-adjust:none}:host([data-state=current]) .tum-ui-step-marker{border-color:Highlight;color:Highlight}:host([data-state=pending]) .tum-ui-step-marker,:host([data-state=skipped]) .tum-ui-step-marker{border-color:GrayText;color:GrayText}.tum-ui-step-running-fallback{background:Highlight;forced-color-adjust:none}.tum-ui-step-running-indicator{border-color:GrayText;border-block-start-color:Highlight;forced-color-adjust:none}.tum-ui-step-connector{border-color:CanvasText;forced-color-adjust:none}}\n"] }]
        }], propDecorators: { state: [{ type: i0.Input, args: [{ isSignal: true, alias: "state", required: false }] }], label: [{ type: i0.Input, args: [{ isSignal: true, alias: "label", required: false }] }], icon: [{ type: i0.Input, args: [{ isSignal: true, alias: "icon", required: false }] }], stateLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "stateLabel", required: false }] }] } });

const STEPPER_LIST_BASE = 'tum-ui-stepper-list tum:flex tum:m-0 tum:list-none tum:p-0';
const STEPPER_LIST_ORIENTATION = {
    vertical: 'tum:flex-col',
    horizontal: 'tum:flex-row tum:flex-wrap tum:gap-y-6',
};
/**
 * Progress ladder for a multi-stage operation.
 *
 * The stepper is a status display, not a navigation control: its steps are neither clickable nor focusable. Project
 * `tum-ui-step` children in the order they run.
 *
 * The list keeps an explicit `role="list"`, because a flex `<ol>` without markers loses its list semantics in some
 * browsers and every step depends on that list to carry its `role="listitem"`.
 */
class TumUiStepperComponent {
    stepperService = inject(TumUiStepperService);
    /** Layout direction of the ladder. */
    orientation = input('vertical', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "orientation" }] : /* istanbul ignore next */ []));
    /** Accessible name of the step list. */
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    constructor() {
        this.stepperService.register(this.orientation);
    }
    listClasses = computed(() => `${STEPPER_LIST_BASE} ${STEPPER_LIST_ORIENTATION[this.orientation()]}`, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "listClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiStepperComponent, deps: [], target: i0.ɵɵFactoryTarget.Component });
    static ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "17.1.0", version: "22.1.5", type: TumUiStepperComponent, isStandalone: true, selector: "tum-ui-stepper", inputs: { orientation: { classPropertyName: "orientation", publicName: "orientation", isSignal: true, isRequired: false, transformFunction: null }, ariaLabel: { classPropertyName: "ariaLabel", publicName: "ariaLabel", isSignal: true, isRequired: false, transformFunction: null } }, host: { properties: { "attr.data-slot": "\"stepper\"", "attr.data-orientation": "orientation()" }, classAttribute: "tum-ui-stepper tum:block tum:text-text" }, providers: [TumUiStepperService], ngImport: i0, template: "<ol role=\"list\" [class]=\"listClasses()\" [attr.aria-label]=\"ariaLabel()\">\n    <ng-content />\n</ol>\n", changeDetection: i0.ChangeDetectionStrategy.OnPush });
}
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "22.1.5", ngImport: i0, type: TumUiStepperComponent, decorators: [{
            type: Component,
            args: [{ selector: 'tum-ui-stepper', host: {
                        '[attr.data-slot]': '"stepper"',
                        class: 'tum-ui-stepper tum:block tum:text-text',
                        '[attr.data-orientation]': 'orientation()',
                    }, providers: [TumUiStepperService], changeDetection: ChangeDetectionStrategy.OnPush, template: "<ol role=\"list\" [class]=\"listClasses()\" [attr.aria-label]=\"ariaLabel()\">\n    <ng-content />\n</ol>\n" }]
        }], ctorParameters: () => [], propDecorators: { orientation: [{ type: i0.Input, args: [{ isSignal: true, alias: "orientation", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }] } });

/**
 * Generated bundle index. Do not edit.
 */

export { TUM_UI_FORM_FIELD, TUM_UI_TRANSLATOR, TumUiAutoCompleteComponent, TumUiBarChartComponent, TumUiButtonComponent, TumUiButtonDirective, TumUiButtonGroupComponent, TumUiCardComponent, TumUiCheckboxComponent, TumUiChipComponent, TumUiConfirmDialogComponent, TumUiConfirmationService, TumUiDatePickerComponent, TumUiDialogComponent, TumUiDoughnutChartComponent, TumUiEmptyComponent, TumUiEmptyContentComponent, TumUiEmptyDescriptionComponent, TumUiEmptyHeaderComponent, TumUiEmptyMediaComponent, TumUiEmptyTitleComponent, TumUiFormFieldComponent, TumUiIconFieldComponent, TumUiInputDirective, TumUiInputGroupAddonComponent, TumUiInputGroupComponent, TumUiInputNumberComponent, TumUiLineChartComponent, TumUiListComponent, TumUiListItemActionDirective, TumUiListItemDirective, TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective, TumUiMessageComponent, TumUiPaginatorComponent, TumUiPanelComponent, TumUiPopoverComponent, TumUiPopoverTriggerDirective, TumUiProgressBarComponent, TumUiProgressSpinnerComponent, TumUiProseComponent, TumUiRadioButtonComponent, TumUiSearchFieldComponent, TumUiSelectButtonComponent, TumUiSelectComponent, TumUiSkeletonComponent, TumUiStatusDotComponent, TumUiStepComponent, TumUiStepperComponent, TumUiTabComponent, TumUiTabListComponent, TumUiTabPanelComponent, TumUiTabPanelsComponent, TumUiTableComponent, TumUiTableDirective, TumUiTableSortableColumnComponent, TumUiTableVirtualScrollComponent, TumUiTabsComponent, TumUiTagComponent, TumUiToggleSwitchComponent, TumUiTooltipDirective, provideTumUiTranslator };
//# sourceMappingURL=tumaet-ui-angular.mjs.map
