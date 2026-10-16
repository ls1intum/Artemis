import * as i0 from '@angular/core';
import { inject, ElementRef, Injectable, InjectionToken, makeEnvironmentProviders, Pipe, input, booleanAttribute, output, computed, ChangeDetectionStrategy, Component, ViewContainerRef, DestroyRef, numberAttribute, viewChild, TemplateRef, signal, effect, forwardRef, afterNextRender, model, contentChild, Directive, linkedSignal, viewChildren, afterRenderEffect, Injector, untracked, contentChildren, HostAttributeToken } from '@angular/core';
import * as i1$2 from '@angular/forms';
import { NG_VALUE_ACCESSOR, FormsModule } from '@angular/forms';
import { DOCUMENT, NgTemplateOutlet } from '@angular/common';
import { TemplatePortal, ComponentPortal } from '@angular/cdk/portal';
import { Overlay } from '@angular/cdk/overlay';
import { Directionality } from '@angular/cdk/bidi';
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from '@fortawesome/angular-fontawesome';
import { faXmark, faSpinner, faCheck, faMinus, faChevronRight, faChevronLeft, faCalendar, faGlobe, faClock, faChevronUp, faChevronDown, faAnglesRight, faAnglesLeft, faAngleRight, faAngleLeft, faMagnifyingGlass, faSort, faSortDown, faSortUp, faCircleQuestion } from '@fortawesome/free-solid-svg-icons';
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
/**
 * How close an overlay may come to the edge of the viewport. `withPush` shoves a bubble that would not fit back inside,
 * and without a margin it lands flush: against the window edge on one side and against the scrollbar on the other,
 * which reads as the bubble sitting on top of them. The same 8px the bubble keeps from its host.
 */
const VIEWPORT_MARGIN = OFFSET;
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
        return this.overlay.position().flexibleConnectedTo(origin).withPositions(positions).withFlexibleDimensions(false).withPush(true).withViewportMargin(VIEWPORT_MARGIN);
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
    static ɵfac = function TumUiOverlayService_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiOverlayService)(); };
    static ɵprov = /*@__PURE__*/ i0.ɵɵdefineInjectable({ token: TumUiOverlayService, factory: TumUiOverlayService.ɵfac, providedIn: 'root' });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiOverlayService, [{
        type: Injectable,
        args: [{ providedIn: 'root' }]
    }], null, null); })();

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
    static ɵfac = function TumUiTranslatePipe_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTranslatePipe)(); };
    static ɵpipe = /*@__PURE__*/ i0.ɵɵdefinePipe({ name: "tumUiTranslate", type: TumUiTranslatePipe, pure: false });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTranslatePipe, [{
        type: Pipe,
        args: [{
                name: 'tumUiTranslate',
                pure: false,
            }]
    }], null, null); })();

const _c0$z = ["*"];
function TumUiChipComponent_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "span", 0);
    i0.ɵɵtext(1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(ctx);
} }
function TumUiChipComponent_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "span", 0);
    i0.ɵɵprojection(1);
    i0.ɵɵelementEnd();
} }
function TumUiChipComponent_Conditional_3_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "button", 2);
    i0.ɵɵpipe(1, "tumUiTranslate");
    i0.ɵɵlistener("click", function TumUiChipComponent_Conditional_3_Template_button_click_0_listener($event) { i0.ɵɵrestoreView(_r1); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.remove($event)); })("keydown", function TumUiChipComponent_Conditional_3_Template_button_keydown_0_listener($event) { i0.ɵɵrestoreView(_r1); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.onRemoveKeydown($event)); });
    i0.ɵɵelement(2, "fa-icon", 3);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵattribute("aria-label", ctx_r1.removeAriaLabel() ?? i0.ɵɵpipeBind1(1, 2, "tumUi.chip.remove"));
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r1.faXmark);
} }
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
    static ɵfac = function TumUiChipComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiChipComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiChipComponent, selectors: [["tum-ui-chip"]], inputs: { label: [1, "label"], removable: [1, "removable"], size: [1, "size"], removeAriaLabel: [1, "removeAriaLabel"] }, outputs: { removed: "removed" }, ngContentSelectors: _c0$z, decls: 4, vars: 4, consts: [[1, "tum-ui-chip-label", "tum:truncate"], ["type", "button", 1, "tum-ui-chip-remove", "tum:inline-flex", "tum:shrink-0", "tum:cursor-pointer", "tum:appearance-none", "tum:items-center", "tum:justify-center", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:leading-none", "tum:text-inherit"], ["type", "button", 1, "tum-ui-chip-remove", "tum:inline-flex", "tum:shrink-0", "tum:cursor-pointer", "tum:appearance-none", "tum:items-center", "tum:justify-center", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:leading-none", "tum:text-inherit", 3, "click", "keydown"], [3, "icon"]], template: function TumUiChipComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵelementStart(0, "span");
            i0.ɵɵconditionalCreate(1, TumUiChipComponent_Conditional_1_Template, 2, 1, "span", 0)(2, TumUiChipComponent_Conditional_2_Template, 2, 0, "span", 0);
            i0.ɵɵconditionalCreate(3, TumUiChipComponent_Conditional_3_Template, 3, 4, "button", 1);
            i0.ɵɵelementEnd();
        } if (rf & 2) {
            let tmp_1_0;
            i0.ɵɵclassMap(ctx.chipClasses());
            i0.ɵɵadvance();
            i0.ɵɵconditional((tmp_1_0 = ctx.label()) ? 1 : 2, tmp_1_0);
            i0.ɵɵadvance(2);
            i0.ɵɵconditional(ctx.removable() ? 3 : -1);
        } }, dependencies: [FaIconComponent, TumUiTranslatePipe], styles: ["[_nghost-%COMP%]{display:inline-flex;max-width:100%}.tum-ui-chip-remove[_ngcontent-%COMP%]{border-radius:50%}.tum-ui-chip-remove[_ngcontent-%COMP%]:focus-visible{outline:2px solid var(--%NS%tumaet-ui-focus-color);outline-offset:1px}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiChipComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-chip', imports: [FaIconComponent, TumUiTranslatePipe], changeDetection: ChangeDetectionStrategy.OnPush, template: "<span [class]=\"chipClasses()\">\n    @if (label(); as chipLabel) {\n        <span class=\"tum-ui-chip-label tum:truncate\">{{ chipLabel }}</span>\n    } @else {\n        <span class=\"tum-ui-chip-label tum:truncate\"><ng-content /></span>\n    }\n    @if (removable()) {\n        <button\n            type=\"button\"\n            class=\"tum-ui-chip-remove tum:inline-flex tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:leading-none tum:text-inherit\"\n            [attr.aria-label]=\"removeAriaLabel() ?? ('tumUi.chip.remove' | tumUiTranslate)\"\n            (click)=\"remove($event)\"\n            (keydown)=\"onRemoveKeydown($event)\"\n        >\n            <fa-icon [icon]=\"faXmark\" />\n        </button>\n    }\n</span>\n", styles: [":host{display:inline-flex;max-width:100%}.tum-ui-chip-remove{border-radius:50%}.tum-ui-chip-remove:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:1px}\n"] }]
    }], null, { label: [{ type: i0.Input, args: [{ isSignal: true, alias: "label", required: false }] }], removable: [{ type: i0.Input, args: [{ isSignal: true, alias: "removable", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], removeAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "removeAriaLabel", required: false }] }], removed: [{ type: i0.Output, args: ["removed"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiChipComponent, { className: "TumUiChipComponent", filePath: "lib/chip/tum-ui-chip.component.ts", lineNumber: 15 }); })();

const _c0$y = ["container"];
const _c1$8 = ["textInput"];
const _c2$3 = ["panel"];
function TumUiAutoCompleteComponent_Conditional_2_For_1_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "tum-ui-chip", 6);
    i0.ɵɵpipe(1, "tumUiTranslate");
    i0.ɵɵlistener("removed", function TumUiAutoCompleteComponent_Conditional_2_For_1_Template_tum_ui_chip_removed_0_listener($event) { const ɵ$index_4_r2 = i0.ɵɵrestoreView(_r1).$index; const ctx_r2 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r2.removeAt(ɵ$index_4_r2, $event)); });
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const value_r4 = ctx.$implicit;
    const ctx_r2 = i0.ɵɵnextContext(2);
    i0.ɵɵproperty("label", ctx_r2.valueLabel(value_r4))("removable", !ctx_r2.isDisabled())("removeAriaLabel", ctx_r2.removeAriaLabel() ?? i0.ɵɵpipeBind1(1, 3, "tumUi.autocomplete.remove"));
} }
function TumUiAutoCompleteComponent_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵrepeaterCreate(0, TumUiAutoCompleteComponent_Conditional_2_For_1_Template, 2, 5, "tum-ui-chip", 5, i0.ɵɵrepeaterTrackByIndex);
} if (rf & 2) {
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵrepeater(ctx_r2.selectedValues());
} }
function TumUiAutoCompleteComponent_ng_template_5_For_3_Template(rf, ctx) { if (rf & 1) {
    const _r5 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "li", 11);
    i0.ɵɵlistener("mousedown", function TumUiAutoCompleteComponent_ng_template_5_For_3_Template_li_mousedown_0_listener($event) { return $event.preventDefault(); })("click", function TumUiAutoCompleteComponent_ng_template_5_For_3_Template_li_click_0_listener($event) { const option_r6 = i0.ɵɵrestoreView(_r5).$implicit; const ctx_r2 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r2.selectOption(option_r6, $event)); })("mouseenter", function TumUiAutoCompleteComponent_ng_template_5_For_3_Template_li_mouseenter_0_listener() { const ɵ$index_16_r7 = i0.ɵɵrestoreView(_r5).$index; const ctx_r2 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r2.setActive(ɵ$index_16_r7)); });
    i0.ɵɵelementStart(1, "span", 12);
    i0.ɵɵtext(2);
    i0.ɵɵelementEnd()();
} if (rf & 2) {
    const option_r6 = ctx.$implicit;
    const ɵ$index_16_r7 = ctx.$index;
    const ctx_r2 = i0.ɵɵnextContext(2);
    i0.ɵɵclassMap(ctx_r2.optionClasses(option_r6, ɵ$index_16_r7));
    i0.ɵɵproperty("id", ctx_r2.optionId(ɵ$index_16_r7));
    i0.ɵɵattribute("aria-selected", ctx_r2.isAlreadySelected(option_r6) ? "true" : "false");
    i0.ɵɵadvance(2);
    i0.ɵɵtextInterpolate(ctx_r2.valueLabel(option_r6));
} }
function TumUiAutoCompleteComponent_ng_template_5_ForEmpty_4_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "li", 10);
    i0.ɵɵtext(1);
    i0.ɵɵpipe(2, "tumUiTranslate");
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r2 = i0.ɵɵnextContext(2);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate1(" ", ctx_r2.emptyMessage() ?? i0.ɵɵpipeBind1(2, 1, "tumUi.autocomplete.empty"), " ");
} }
function TumUiAutoCompleteComponent_ng_template_5_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "div", 7)(1, "ul", 8);
    i0.ɵɵrepeaterCreate(2, TumUiAutoCompleteComponent_ng_template_5_For_3_Template, 3, 5, "li", 9, i0.ɵɵrepeaterTrackByIndex, false, TumUiAutoCompleteComponent_ng_template_5_ForEmpty_4_Template, 3, 3, "li", 10);
    i0.ɵɵelementEnd()();
} if (rf & 2) {
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵproperty("id", ctx_r2.listboxId);
    i0.ɵɵattribute("aria-label", ctx_r2.ariaLabel())("aria-multiselectable", ctx_r2.multiple() ? "true" : null);
    i0.ɵɵadvance();
    i0.ɵɵrepeater(ctx_r2.suggestions());
} }
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
    static ɵfac = function TumUiAutoCompleteComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiAutoCompleteComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiAutoCompleteComponent, selectors: [["tum-ui-autocomplete"]], viewQuery: function TumUiAutoCompleteComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.container, _c0$y, 5)(ctx.textInput, _c1$8, 5)(ctx.panel, _c2$3, 5, TemplateRef);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance(3);
        } }, hostAttrs: [1, "tum-ui-autocomplete"], inputs: { suggestions: [1, "suggestions"], optionLabel: [1, "optionLabel"], multiple: [1, "multiple"], placeholder: [1, "placeholder"], disabled: [1, "disabled"], minLength: [1, "minLength"], debounceMs: [1, "debounceMs"], completeOnFocus: [1, "completeOnFocus"], inputId: [1, "inputId"], name: [1, "name"], ariaLabel: [1, "ariaLabel"], removeAriaLabel: [1, "removeAriaLabel"], emptyMessage: [1, "emptyMessage"] }, outputs: { searchRequested: "searchRequested", optionSelected: "optionSelected", optionRemoved: "optionRemoved" }, features: [i0.ɵɵProvidersFeature([{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiAutoCompleteComponent), multi: true }])], decls: 7, vars: 12, consts: [["container", ""], ["textInput", ""], ["panel", ""], [3, "click"], ["type", "text", "role", "combobox", "autocomplete", "off", "aria-autocomplete", "list", 1, "tum-ui-autocomplete-input", "tum:min-w-16", "tum:flex-1", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:text-base", "tum:text-inherit", "tum:outline-none", "tum:placeholder:text-muted", 3, "input", "focus", "blur", "keydown", "id", "value", "disabled"], ["size", "small", 3, "label", "removable", "removeAriaLabel"], ["size", "small", 3, "removed", "label", "removable", "removeAriaLabel"], [1, "tum-ui-autocomplete-panel", "tum:box-border", "tum:w-full", "tum:rounded-md", "tum:border", "tum:border-border", "tum:bg-overlay-background", "tum:text-text", "tum:shadow-md"], ["role", "listbox", 1, "tum:m-0", "tum:flex", "tum:max-h-60", "tum:list-none", "tum:flex-col", "tum:gap-0.5", "tum:overflow-y-auto", "tum:p-1", 3, "id"], ["role", "option", 3, "id", "class"], ["role", "option", "aria-selected", "false", "aria-disabled", "true", 1, "tum:px-3", "tum:py-2", "tum:text-muted"], ["role", "option", 3, "mousedown", "click", "mouseenter", "id"], [1, "tum:flex-1", "tum:truncate"]], template: function TumUiAutoCompleteComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵelementStart(0, "div", 3, 0);
            i0.ɵɵlistener("click", function TumUiAutoCompleteComponent_Template_div_click_0_listener() { return ctx.focusInput(); });
            i0.ɵɵconditionalCreate(2, TumUiAutoCompleteComponent_Conditional_2_Template, 2, 0);
            i0.ɵɵelementStart(3, "input", 4, 1);
            i0.ɵɵlistener("input", function TumUiAutoCompleteComponent_Template_input_input_3_listener($event) { return ctx.onInput($event); })("focus", function TumUiAutoCompleteComponent_Template_input_focus_3_listener($event) { return ctx.onFocus($event); })("blur", function TumUiAutoCompleteComponent_Template_input_blur_3_listener() { return ctx.onBlur(); })("keydown", function TumUiAutoCompleteComponent_Template_input_keydown_3_listener($event) { return ctx.onInputKeydown($event); });
            i0.ɵɵelementEnd()();
            i0.ɵɵtemplate(5, TumUiAutoCompleteComponent_ng_template_5_Template, 5, 4, "ng-template", null, 2, i0.ɵɵtemplateRefExtractor);
        } if (rf & 2) {
            i0.ɵɵclassMap(ctx.containerClasses());
            i0.ɵɵadvance(2);
            i0.ɵɵconditional(ctx.multiple() ? 2 : -1);
            i0.ɵɵadvance();
            i0.ɵɵproperty("id", ctx.inputId())("value", ctx.inputText())("disabled", ctx.isDisabled());
            i0.ɵɵattribute("name", ctx.name())("placeholder", ctx.inputPlaceholder())("aria-expanded", ctx.panelVisible() ? "true" : "false")("aria-controls", ctx.panelVisible() ? ctx.listboxId : null)("aria-activedescendant", ctx.activeOptionId())("aria-label", ctx.ariaLabel());
        } }, dependencies: [TumUiChipComponent, TumUiTranslatePipe], styles: ["[_nghost-%COMP%]{display:block;max-width:100%}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiAutoCompleteComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-autocomplete', imports: [TumUiChipComponent, TumUiTranslatePipe], host: {
                    // The application stylesheet excludes TUM UI controls from the JHipster validity accent by this class.
                    class: 'tum-ui-autocomplete',
                }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiAutoCompleteComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<div #container [class]=\"containerClasses()\" (click)=\"focusInput()\">\n    @if (multiple()) {\n        @for (value of selectedValues(); track $index; let i = $index) {\n            <tum-ui-chip\n                size=\"small\"\n                [label]=\"valueLabel(value)\"\n                [removable]=\"!isDisabled()\"\n                [removeAriaLabel]=\"removeAriaLabel() ?? ('tumUi.autocomplete.remove' | tumUiTranslate)\"\n                (removed)=\"removeAt(i, $event)\"\n            />\n        }\n    }\n    <input\n        #textInput\n        type=\"text\"\n        role=\"combobox\"\n        [id]=\"inputId()\"\n        [attr.name]=\"name()\"\n        class=\"tum-ui-autocomplete-input tum:min-w-16 tum:flex-1 tum:border-0 tum:bg-transparent tum:p-0 tum:text-base tum:text-inherit tum:outline-none tum:placeholder:text-muted\"\n        [value]=\"inputText()\"\n        [attr.placeholder]=\"inputPlaceholder()\"\n        [disabled]=\"isDisabled()\"\n        autocomplete=\"off\"\n        aria-autocomplete=\"list\"\n        [attr.aria-expanded]=\"panelVisible() ? 'true' : 'false'\"\n        [attr.aria-controls]=\"panelVisible() ? listboxId : null\"\n        [attr.aria-activedescendant]=\"activeOptionId()\"\n        [attr.aria-label]=\"ariaLabel()\"\n        (input)=\"onInput($event)\"\n        (focus)=\"onFocus($event)\"\n        (blur)=\"onBlur()\"\n        (keydown)=\"onInputKeydown($event)\"\n    />\n</div>\n\n<ng-template #panel>\n    <div class=\"tum-ui-autocomplete-panel tum:box-border tum:w-full tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-md\">\n        <ul\n            [id]=\"listboxId\"\n            role=\"listbox\"\n            [attr.aria-label]=\"ariaLabel()\"\n            [attr.aria-multiselectable]=\"multiple() ? 'true' : null\"\n            class=\"tum:m-0 tum:flex tum:max-h-60 tum:list-none tum:flex-col tum:gap-0.5 tum:overflow-y-auto tum:p-1\"\n        >\n            @for (option of suggestions(); track $index; let i = $index) {\n                <li\n                    [id]=\"optionId(i)\"\n                    role=\"option\"\n                    [attr.aria-selected]=\"isAlreadySelected(option) ? 'true' : 'false'\"\n                    [class]=\"optionClasses(option, i)\"\n                    (mousedown)=\"$event.preventDefault()\"\n                    (click)=\"selectOption(option, $event)\"\n                    (mouseenter)=\"setActive(i)\"\n                >\n                    <span class=\"tum:flex-1 tum:truncate\">{{ valueLabel(option) }}</span>\n                </li>\n            } @empty {\n                <li role=\"option\" aria-selected=\"false\" aria-disabled=\"true\" class=\"tum:px-3 tum:py-2 tum:text-muted\">\n                    {{ emptyMessage() ?? ('tumUi.autocomplete.empty' | tumUiTranslate) }}\n                </li>\n            }\n        </ul>\n    </div>\n</ng-template>\n", styles: [":host{display:block;max-width:100%}\n"] }]
    }], () => [], { suggestions: [{ type: i0.Input, args: [{ isSignal: true, alias: "suggestions", required: false }] }], optionLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "optionLabel", required: false }] }], multiple: [{ type: i0.Input, args: [{ isSignal: true, alias: "multiple", required: false }] }], placeholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "placeholder", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], minLength: [{ type: i0.Input, args: [{ isSignal: true, alias: "minLength", required: false }] }], debounceMs: [{ type: i0.Input, args: [{ isSignal: true, alias: "debounceMs", required: false }] }], completeOnFocus: [{ type: i0.Input, args: [{ isSignal: true, alias: "completeOnFocus", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], name: [{ type: i0.Input, args: [{ isSignal: true, alias: "name", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], removeAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "removeAriaLabel", required: false }] }], emptyMessage: [{ type: i0.Input, args: [{ isSignal: true, alias: "emptyMessage", required: false }] }], searchRequested: [{ type: i0.Output, args: ["searchRequested"] }], optionSelected: [{ type: i0.Output, args: ["optionSelected"] }], optionRemoved: [{ type: i0.Output, args: ["optionRemoved"] }], container: [{ type: i0.ViewChild, args: ['container', { isSignal: true }] }], textInput: [{ type: i0.ViewChild, args: ['textInput', { isSignal: true }] }], panel: [{ type: i0.ViewChild, args: ['panel', { ...{ read: TemplateRef }, isSignal: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiAutoCompleteComponent, { className: "TumUiAutoCompleteComponent", filePath: "lib/autocomplete/tum-ui-autocomplete.component.ts", lineNumber: 52 }); })();

const _c0$x = ["*"];
class TumUiButtonGroupComponent {
    static ɵfac = function TumUiButtonGroupComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiButtonGroupComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiButtonGroupComponent, selectors: [["tum-ui-button-group"]], hostAttrs: ["role", "group", 1, "tum-ui-button-group"], ngContentSelectors: _c0$x, decls: 1, vars: 0, template: function TumUiButtonGroupComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵprojection(0);
        } }, encapsulation: 2 });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiButtonGroupComponent, [{
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
    }], null, null); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiButtonGroupComponent, { className: "TumUiButtonGroupComponent", filePath: "lib/button-group/tum-ui-button-group.component.ts", lineNumber: 12 }); })();

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

const _c0$w = ["*"];
function TumUiButtonComponent_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "fa-icon", 1);
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵproperty("icon", ctx_r0.faSpinner);
} }
function TumUiButtonComponent_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "fa-icon", 2);
} if (rf & 2) {
    i0.ɵɵproperty("icon", ctx);
} }
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
    static ɵfac = function TumUiButtonComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiButtonComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiButtonComponent, selectors: [["tum-ui-button"]], inputs: { severity: [1, "severity"], size: [1, "size"], variant: [1, "variant"], disabled: [1, "disabled"], rounded: [1, "rounded"], loading: [1, "loading"], icon: [1, "icon"], type: [1, "type"], ariaLabel: [1, "ariaLabel"], ariaExpanded: [1, "ariaExpanded"], ariaPressed: [1, "ariaPressed"], ariaControls: [1, "ariaControls"], ariaDescribedBy: [1, "ariaDescribedBy"] }, outputs: { clicked: "clicked" }, ngContentSelectors: _c0$w, decls: 4, vars: 11, consts: [[3, "click", "type", "disabled"], [1, "tum:animate-spin", "tum:motion-reduce:animate-none", 3, "icon"], [3, "icon"]], template: function TumUiButtonComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵelementStart(0, "button", 0);
            i0.ɵɵlistener("click", function TumUiButtonComponent_Template_button_click_0_listener($event) { return ctx.onClick($event); });
            i0.ɵɵconditionalCreate(1, TumUiButtonComponent_Conditional_1_Template, 1, 1, "fa-icon", 1)(2, TumUiButtonComponent_Conditional_2_Template, 1, 1, "fa-icon", 2);
            i0.ɵɵprojection(3);
            i0.ɵɵelementEnd();
        } if (rf & 2) {
            let tmp_9_0;
            i0.ɵɵclassMap(ctx.buttonClasses());
            i0.ɵɵproperty("type", ctx.type())("disabled", ctx.isDisabled());
            i0.ɵɵattribute("aria-busy", ctx.loading() ? "true" : null)("aria-label", ctx.ariaLabel())("aria-expanded", ctx.ariaExpanded())("aria-pressed", ctx.ariaPressed())("aria-controls", ctx.ariaControls())("aria-describedby", ctx.ariaDescribedBy());
            i0.ɵɵadvance();
            i0.ɵɵconditional(ctx.loading() ? 1 : (tmp_9_0 = ctx.icon()) ? 2 : -1, tmp_9_0);
        } }, dependencies: [FaIconComponent], styles: ["[_nghost-%COMP%]{display:inline-flex}.tum-ui-btn.tum-ui-btn-rounded[_ngcontent-%COMP%]{border-radius:9999px}.tum-ui-btn[_ngcontent-%COMP%]{position:relative;appearance:none;cursor:pointer;white-space:nowrap;border-radius:var(--%NS%tumaet-ui-radius-md)}.tum-ui-btn[_ngcontent-%COMP%]:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-btn[_ngcontent-%COMP%]:hover:not(:disabled):after{background-color:color-mix(in srgb,var(--%NS%tumaet-ui-text-color) 5%,transparent)}.tum-ui-btn[_ngcontent-%COMP%]:focus-visible{outline:2px solid var(--%NS%tumaet-ui-focus-color);outline-offset:2px}.tum-ui-btn[_ngcontent-%COMP%]:disabled{cursor:default}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiButtonComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-button', imports: [FaIconComponent], changeDetection: ChangeDetectionStrategy.OnPush, template: "<button\n    [type]=\"type()\"\n    [class]=\"buttonClasses()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-busy]=\"loading() ? 'true' : null\"\n    [attr.aria-label]=\"ariaLabel()\"\n    [attr.aria-expanded]=\"ariaExpanded()\"\n    [attr.aria-pressed]=\"ariaPressed()\"\n    [attr.aria-controls]=\"ariaControls()\"\n    [attr.aria-describedby]=\"ariaDescribedBy()\"\n    (click)=\"onClick($event)\"\n>\n    @if (loading()) {\n        <fa-icon [icon]=\"faSpinner\" class=\"tum:animate-spin tum:motion-reduce:animate-none\" />\n    } @else if (icon(); as buttonIcon) {\n        <fa-icon [icon]=\"buttonIcon\" />\n    }\n    <ng-content />\n</button>\n", styles: [":host{display:inline-flex}.tum-ui-btn.tum-ui-btn-rounded{border-radius:9999px}.tum-ui-btn{position:relative;appearance:none;cursor:pointer;white-space:nowrap;border-radius:var(--tumaet-ui-radius-md)}.tum-ui-btn:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-btn:hover:not(:disabled):after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}.tum-ui-btn:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-btn:disabled{cursor:default}\n"] }]
    }], null, { severity: [{ type: i0.Input, args: [{ isSignal: true, alias: "severity", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], variant: [{ type: i0.Input, args: [{ isSignal: true, alias: "variant", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], rounded: [{ type: i0.Input, args: [{ isSignal: true, alias: "rounded", required: false }] }], loading: [{ type: i0.Input, args: [{ isSignal: true, alias: "loading", required: false }] }], icon: [{ type: i0.Input, args: [{ isSignal: true, alias: "icon", required: false }] }], type: [{ type: i0.Input, args: [{ isSignal: true, alias: "type", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaExpanded: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaExpanded", required: false }] }], ariaPressed: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaPressed", required: false }] }], ariaControls: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaControls", required: false }] }], ariaDescribedBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaDescribedBy", required: false }] }], clicked: [{ type: i0.Output, args: ["clicked"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiButtonComponent, { className: "TumUiButtonComponent", filePath: "lib/button/tum-ui-button.component.ts", lineNumber: 14 }); })();

const _c0$v = ["*"];
class TumUiButtonDirective {
    severity = input('primary', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []));
    size = input('default', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []));
    variant = input('solid', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "variant" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => tumUiButtonClasses({ severity: this.severity(), size: this.size(), variant: this.variant() }), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = function TumUiButtonDirective_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiButtonDirective)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiButtonDirective, selectors: [["a", "tumUiButton", ""], ["button", "tumUiButton", ""]], hostVars: 2, hostBindings: function TumUiButtonDirective_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵclassMap(ctx.hostClasses());
        } }, inputs: { severity: [1, "severity"], size: [1, "size"], variant: [1, "variant"] }, ngContentSelectors: _c0$v, decls: 1, vars: 0, template: function TumUiButtonDirective_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵprojection(0);
        } }, styles: ["[_nghost-%COMP%]{position:relative;border-radius:var(--%NS%tumaet-ui-radius-md);text-decoration:none}[_nghost-%COMP%]:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}[_nghost-%COMP%]:hover:not(:disabled):not([aria-disabled=true]):after{background-color:color-mix(in srgb,var(--%NS%tumaet-ui-text-color) 5%,transparent)}[_nghost-%COMP%]:focus-visible{outline:2px solid var(--%NS%tumaet-ui-focus-color);outline-offset:2px}[_nghost-%COMP%]:disabled{cursor:default}[aria-disabled=true][_nghost-%COMP%]{cursor:not-allowed}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiButtonDirective, [{
        type: Component,
        args: [{ selector: 'a[tumUiButton], button[tumUiButton]', template: '<ng-content />', host: {
                    '[class]': 'hostClasses()',
                }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{position:relative;border-radius:var(--tumaet-ui-radius-md);text-decoration:none}:host:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}:host:hover:not(:disabled):not([aria-disabled=true]):after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}:host:disabled{cursor:default}:host[aria-disabled=true]{cursor:not-allowed}\n"] }]
    }], null, { severity: [{ type: i0.Input, args: [{ isSignal: true, alias: "severity", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], variant: [{ type: i0.Input, args: [{ isSignal: true, alias: "variant", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiButtonDirective, { className: "TumUiButtonDirective", filePath: "lib/button/tum-ui-button.directive.ts", lineNumber: 13 }); })();

const _c0$u = [[["", "tumUiCardHeader", ""]], "*", [["", "tumUiCardFooter", ""]]];
const _c1$7 = ["[tumUiCardHeader]", "*", "[tumUiCardFooter]"];
function TumUiCardComponent_Conditional_2_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "div", 3);
    i0.ɵɵtext(1);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext(2);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(ctx_r0.header());
} }
function TumUiCardComponent_Conditional_2_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "div", 4);
    i0.ɵɵtext(1);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext(2);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(ctx_r0.subheader());
} }
function TumUiCardComponent_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "div", 1);
    i0.ɵɵconditionalCreate(1, TumUiCardComponent_Conditional_2_Conditional_1_Template, 2, 1, "div", 3);
    i0.ɵɵconditionalCreate(2, TumUiCardComponent_Conditional_2_Conditional_2_Template, 2, 1, "div", 4);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵconditional(ctx_r0.header() ? 1 : -1);
    i0.ɵɵadvance();
    i0.ɵɵconditional(ctx_r0.subheader() ? 2 : -1);
} }
class TumUiCardComponent {
    header = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "header" }] : /* istanbul ignore next */ []));
    subheader = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "subheader" }] : /* istanbul ignore next */ []));
    static ɵfac = function TumUiCardComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiCardComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiCardComponent, selectors: [["tum-ui-card"]], hostAttrs: [1, "tum-ui-card", "tum:flex", "tum:flex-col", "tum:rounded-xl", "tum:shadow-sm", "tum:bg-overlay-background", "tum:text-text"], inputs: { header: [1, "header"], subheader: [1, "subheader"] }, ngContentSelectors: _c1$7, decls: 6, vars: 1, consts: [[1, "tum-ui-card-body", "tum:flex", "tum:flex-col", "tum:gap-2", "tum:p-5"], [1, "tum-ui-card-caption", "tum:flex", "tum:flex-col", "tum:gap-2"], [1, "tum-ui-card-content"], [1, "tum-ui-card-title", "tum:text-xl", "tum:font-medium"], [1, "tum-ui-card-subtitle", "tum:text-muted"]], template: function TumUiCardComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef(_c0$u);
            i0.ɵɵprojection(0);
            i0.ɵɵdomElementStart(1, "div", 0);
            i0.ɵɵconditionalCreate(2, TumUiCardComponent_Conditional_2_Template, 3, 2, "div", 1);
            i0.ɵɵdomElementStart(3, "div", 2);
            i0.ɵɵprojection(4, 1);
            i0.ɵɵdomElementEnd();
            i0.ɵɵprojection(5, 2);
            i0.ɵɵdomElementEnd();
        } if (rf & 2) {
            i0.ɵɵadvance(2);
            i0.ɵɵconditional(ctx.header() || ctx.subheader() ? 2 : -1);
        } }, encapsulation: 2 });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiCardComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-card', host: {
                    class: 'tum-ui-card tum:flex tum:flex-col tum:rounded-xl tum:shadow-sm tum:bg-overlay-background tum:text-text',
                }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<ng-content select=\"[tumUiCardHeader]\" />\n<div class=\"tum-ui-card-body tum:flex tum:flex-col tum:gap-2 tum:p-5\">\n    @if (header() || subheader()) {\n        <div class=\"tum-ui-card-caption tum:flex tum:flex-col tum:gap-2\">\n            @if (header()) {\n                <div class=\"tum-ui-card-title tum:text-xl tum:font-medium\">{{ header() }}</div>\n            }\n            @if (subheader()) {\n                <div class=\"tum-ui-card-subtitle tum:text-muted\">{{ subheader() }}</div>\n            }\n        </div>\n    }\n    <div class=\"tum-ui-card-content\">\n        <ng-content />\n    </div>\n    <ng-content select=\"[tumUiCardFooter]\" />\n</div>\n" }]
    }], null, { header: [{ type: i0.Input, args: [{ isSignal: true, alias: "header", required: false }] }], subheader: [{ type: i0.Input, args: [{ isSignal: true, alias: "subheader", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiCardComponent, { className: "TumUiCardComponent", filePath: "lib/card/tum-ui-card.component.ts", lineNumber: 11 }); })();

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

const _forTrack0$7 = ($index, $item) => $item.key;
function TumUiChartAxesComponent_For_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵnamespaceSVG();
    i0.ɵɵdomElement(0, "line", 0);
} if (rf & 2) {
    const gridLine_r1 = ctx.$implicit;
    i0.ɵɵattribute("x1", gridLine_r1.x1)("y1", gridLine_r1.y1)("x2", gridLine_r1.x2)("y2", gridLine_r1.y2);
} }
function TumUiChartAxesComponent_For_3_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵnamespaceSVG();
    i0.ɵɵdomElementStart(0, "text", 1);
    i0.ɵɵtext(1);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const tick_r2 = ctx.$implicit;
    i0.ɵɵattribute("x", tick_r2.x)("y", tick_r2.y)("text-anchor", tick_r2.anchor)("transform", tick_r2.rotate ? "rotate(" + tick_r2.rotate + " " + tick_r2.x + " " + tick_r2.y + ")" : undefined);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate1(" ", tick_r2.text, " ");
} }
function TumUiChartAxesComponent_For_5_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵnamespaceSVG();
    i0.ɵɵdomElementStart(0, "text", 2);
    i0.ɵɵtext(1);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const title_r3 = ctx.$implicit;
    i0.ɵɵattribute("x", title_r3.x)("y", title_r3.y)("transform", title_r3.rotate ? "rotate(" + title_r3.rotate + " " + title_r3.x + " " + title_r3.y + ")" : undefined);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate1(" ", title_r3.text, " ");
} }
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
    static ɵfac = function TumUiChartAxesComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiChartAxesComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiChartAxesComponent, selectors: [["g", "tumUiChartAxes", ""]], inputs: { gridLines: [1, "gridLines"], ticks: [1, "ticks"], titles: [1, "titles"] }, decls: 6, vars: 0, consts: [[1, "tum-ui-chart-grid"], [1, "tum-ui-chart-tick"], ["text-anchor", "middle", 1, "tum-ui-chart-axis-title"]], template: function TumUiChartAxesComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵrepeaterCreate(0, TumUiChartAxesComponent_For_1_Template, 1, 4, ":svg:line", 0, _forTrack0$7);
            i0.ɵɵrepeaterCreate(2, TumUiChartAxesComponent_For_3_Template, 2, 5, ":svg:text", 1, _forTrack0$7);
            i0.ɵɵrepeaterCreate(4, TumUiChartAxesComponent_For_5_Template, 2, 4, ":svg:text", 2, i0.ɵɵrepeaterTrackByIndex);
        } if (rf & 2) {
            i0.ɵɵrepeater(ctx.gridLines());
            i0.ɵɵadvance(2);
            i0.ɵɵrepeater(ctx.ticks());
            i0.ɵɵadvance(2);
            i0.ɵɵrepeater(ctx.titles());
        } }, styles: [".tum-ui-chart-grid[_ngcontent-%COMP%]{stroke:var(--%NS%tumaet-ui-border-color);stroke-width:1;shape-rendering:crispedges}.tum-ui-chart-tick[_ngcontent-%COMP%]{fill:var(--%NS%tumaet-ui-muted-color);font-size:var(--%NS%tumaet-ui-font-size-xs);font-family:inherit}.tum-ui-chart-axis-title[_ngcontent-%COMP%]{fill:var(--%NS%tumaet-ui-text-color);font-size:var(--%NS%tumaet-ui-font-size-sm);font-family:inherit}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiChartAxesComponent, [{
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
    }], null, { gridLines: [{ type: i0.Input, args: [{ isSignal: true, alias: "gridLines", required: false }] }], ticks: [{ type: i0.Input, args: [{ isSignal: true, alias: "ticks", required: false }] }], titles: [{ type: i0.Input, args: [{ isSignal: true, alias: "titles", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiChartAxesComponent, { className: "TumUiChartAxesComponent", filePath: "lib/chart/tum-ui-chart-axes.component.ts", lineNumber: 58 }); })();

const _forTrack0$6 = ($index, $item) => $item.key;
function TumUiChartLegendComponent_For_2_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵdomElementStart(0, "li")(1, "button", 1);
    i0.ɵɵdomListener("click", function TumUiChartLegendComponent_For_2_Template_button_click_1_listener() { const item_r2 = i0.ɵɵrestoreView(_r1).$implicit; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.toggleEntry.emit(item_r2.key)); });
    i0.ɵɵdomElement(2, "span", 2);
    i0.ɵɵdomElementStart(3, "span");
    i0.ɵɵtext(4);
    i0.ɵɵdomElementEnd()()();
} if (rf & 2) {
    const item_r2 = ctx.$implicit;
    i0.ɵɵadvance();
    i0.ɵɵattribute("aria-pressed", !item_r2.hidden);
    i0.ɵɵadvance();
    i0.ɵɵstyleProp("background", item_r2.color);
    i0.ɵɵadvance(2);
    i0.ɵɵtextInterpolate(item_r2.label);
} }
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
    static ɵfac = function TumUiChartLegendComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiChartLegendComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiChartLegendComponent, selectors: [["tum-ui-chart-legend"]], hostAttrs: [1, "tum-ui-chart-legend"], hostVars: 1, hostBindings: function TumUiChartLegendComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("data-position", ctx.position());
        } }, inputs: { items: [1, "items"], position: [1, "position"] }, outputs: { toggleEntry: "toggleEntry" }, decls: 3, vars: 0, consts: [[1, "tum-ui-chart-legend-list"], ["type", "button", 1, "tum-ui-chart-legend-item", 3, "click"], [1, "tum-ui-chart-legend-swatch"]], template: function TumUiChartLegendComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵdomElementStart(0, "ul", 0);
            i0.ɵɵrepeaterCreate(1, TumUiChartLegendComponent_For_2_Template, 5, 4, "li", null, _forTrack0$6);
            i0.ɵɵdomElementEnd();
        } if (rf & 2) {
            i0.ɵɵadvance();
            i0.ɵɵrepeater(ctx.items());
        } }, styles: ["[_nghost-%COMP%]{display:block;align-self:center;font-size:var(--%NS%tumaet-ui-font-size-xs);color:var(--%NS%tumaet-ui-text-color)}.tum-ui-chart-legend-list[_ngcontent-%COMP%]{display:flex;flex-direction:column;gap:calc(var(--%NS%tumaet-ui-spacing) * 1);margin:0;padding:0;list-style:none}[data-position=\"top\"][_nghost-%COMP%]   .tum-ui-chart-legend-list[_ngcontent-%COMP%], [data-position=\"bottom\"][_nghost-%COMP%]   .tum-ui-chart-legend-list[_ngcontent-%COMP%]{flex-direction:row;flex-wrap:wrap;justify-content:center}.tum-ui-chart-legend-item[_ngcontent-%COMP%]{display:flex;align-items:center;gap:calc(var(--%NS%tumaet-ui-spacing) * 1);white-space:nowrap;min-height:24px;padding:0 calc(var(--%NS%tumaet-ui-spacing) * 1);border:0;background:none;color:inherit;font:inherit;cursor:pointer}.tum-ui-chart-legend-item[aria-pressed=false][_ngcontent-%COMP%]{opacity:.45;text-decoration:line-through}.tum-ui-chart-legend-swatch[_ngcontent-%COMP%]{width:10px;height:10px;border-radius:var(--%NS%tumaet-ui-radius-sm);flex:none}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiChartLegendComponent, [{
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
    }], null, { items: [{ type: i0.Input, args: [{ isSignal: true, alias: "items", required: false }] }], position: [{ type: i0.Input, args: [{ isSignal: true, alias: "position", required: false }] }], toggleEntry: [{ type: i0.Output, args: ["toggleEntry"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiChartLegendComponent, { className: "TumUiChartLegendComponent", filePath: "lib/chart/tum-ui-chart-legend.component.ts", lineNumber: 75 }); })();

function TumUiChartTooltipComponent_Conditional_0_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "div", 0);
    i0.ɵɵtext(1);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(ctx_r0.title());
} }
function TumUiChartTooltipComponent_For_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "div");
    i0.ɵɵtext(1);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const line_r2 = ctx.$implicit;
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(line_r2);
} }
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
    static ɵfac = function TumUiChartTooltipComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiChartTooltipComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiChartTooltipComponent, selectors: [["tum-ui-chart-tooltip"]], hostAttrs: ["role", "tooltip", 1, "tum-ui-chart-tooltip", "tum:rounded-md", "tum:border", "tum:border-border", "tum:bg-overlay-background", "tum:px-2", "tum:py-1.5", "tum:text-text", "tum:shadow-lg"], hostVars: 5, hostBindings: function TumUiChartTooltipComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("data-below", ctx.below());
            i0.ɵɵstyleProp("left", ctx.x(), "px")("top", ctx.y(), "px");
        } }, inputs: { title: [1, "title"], lines: [1, "lines"], x: [1, "x"], y: [1, "y"], below: [1, "below"] }, decls: 3, vars: 1, consts: [[1, "tum-ui-chart-tooltip-title"]], template: function TumUiChartTooltipComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵconditionalCreate(0, TumUiChartTooltipComponent_Conditional_0_Template, 2, 1, "div", 0);
            i0.ɵɵrepeaterCreate(1, TumUiChartTooltipComponent_For_2_Template, 2, 1, "div", null, i0.ɵɵrepeaterTrackByIndex);
        } if (rf & 2) {
            i0.ɵɵconditional(ctx.title() ? 0 : -1);
            i0.ɵɵadvance();
            i0.ɵɵrepeater(ctx.lines());
        } }, styles: ["[_nghost-%COMP%]{display:block;position:absolute;z-index:1;transform:translate(-50%,calc(-100% - 12px));max-width:22rem;font-size:var(--%NS%tumaet-ui-font-size-xs);pointer-events:none}[data-below=\"true\"][_nghost-%COMP%]{transform:translate(-50%,12px)}.tum-ui-chart-tooltip-title[_ngcontent-%COMP%]{font-weight:600}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiChartTooltipComponent, [{
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
    }], null, { title: [{ type: i0.Input, args: [{ isSignal: true, alias: "title", required: false }] }], lines: [{ type: i0.Input, args: [{ isSignal: true, alias: "lines", required: false }] }], x: [{ type: i0.Input, args: [{ isSignal: true, alias: "x", required: false }] }], y: [{ type: i0.Input, args: [{ isSignal: true, alias: "y", required: false }] }], below: [{ type: i0.Input, args: [{ isSignal: true, alias: "below", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiChartTooltipComponent, { className: "TumUiChartTooltipComponent", filePath: "lib/chart/tum-ui-chart-tooltip.component.ts", lineNumber: 45 }); })();

function TumUiChartDataTableComponent_For_5_For_4_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "td");
    i0.ɵɵtext(1);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const cell_r1 = ctx.$implicit;
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate2("", cell_r1.seriesLabel ? cell_r1.seriesLabel + ": " : "", "", cell_r1.value);
} }
function TumUiChartDataTableComponent_For_5_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "tr")(1, "th", 0);
    i0.ɵɵtext(2);
    i0.ɵɵdomElementEnd();
    i0.ɵɵrepeaterCreate(3, TumUiChartDataTableComponent_For_5_For_4_Template, 2, 2, "td", null, i0.ɵɵrepeaterTrackByIndex);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const row_r2 = ctx.$implicit;
    i0.ɵɵadvance(2);
    i0.ɵɵtextInterpolate(row_r2.label);
    i0.ɵɵadvance();
    i0.ɵɵrepeater(row_r2.values);
} }
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
    static ɵfac = function TumUiChartDataTableComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiChartDataTableComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiChartDataTableComponent, selectors: [["tum-ui-chart-data-table"]], hostAttrs: [1, "tum-ui-chart-data-table"], inputs: { caption: [1, "caption"], rows: [1, "rows"] }, decls: 6, vars: 1, consts: [["scope", "row"]], template: function TumUiChartDataTableComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵdomElementStart(0, "table")(1, "caption");
            i0.ɵɵtext(2);
            i0.ɵɵdomElementEnd();
            i0.ɵɵdomElementStart(3, "tbody");
            i0.ɵɵrepeaterCreate(4, TumUiChartDataTableComponent_For_5_Template, 5, 1, "tr", null, i0.ɵɵrepeaterTrackByIndex);
            i0.ɵɵdomElementEnd()();
        } if (rf & 2) {
            i0.ɵɵadvance(2);
            i0.ɵɵtextInterpolate1(" ", ctx.caption(), " ");
            i0.ɵɵadvance(2);
            i0.ɵɵrepeater(ctx.rows());
        } }, styles: ["[_nghost-%COMP%]{position:absolute;width:1px;height:1px;margin:-1px;padding:0;overflow:hidden;clip-path:inset(50%);white-space:nowrap;border:0;-webkit-user-select:none;user-select:none}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiChartDataTableComponent, [{
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
    }], null, { caption: [{ type: i0.Input, args: [{ isSignal: true, alias: "caption", required: false }] }], rows: [{ type: i0.Input, args: [{ isSignal: true, alias: "rows", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiChartDataTableComponent, { className: "TumUiChartDataTableComponent", filePath: "lib/chart/tum-ui-chart-data-table.component.ts", lineNumber: 52 }); })();

const _c0$t = ["canvas"];
const _forTrack0$5 = ($index, $item) => $item.key;
function TumUiBarChartComponent_For_10_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵnamespaceSVG();
    i0.ɵɵelementStart(0, "rect", 8);
    i0.ɵɵlistener("mouseenter", function TumUiBarChartComponent_For_10_Template_rect_mouseenter_0_listener($event) { const bar_r2 = i0.ɵɵrestoreView(_r1).$implicit; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onBarEnter(bar_r2, $event)); })("mousemove", function TumUiBarChartComponent_For_10_Template_rect_mousemove_0_listener($event) { const bar_r2 = i0.ɵɵrestoreView(_r1).$implicit; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onBarEnter(bar_r2, $event)); })("mouseleave", function TumUiBarChartComponent_For_10_Template_rect_mouseleave_0_listener() { i0.ɵɵrestoreView(_r1); const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onBarLeave()); })("click", function TumUiBarChartComponent_For_10_Template_rect_click_0_listener() { const bar_r2 = i0.ɵɵrestoreView(_r1).$implicit; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onBarSelect(bar_r2)); })("keydown.enter", function TumUiBarChartComponent_For_10_Template_rect_keydown_enter_0_listener() { const bar_r2 = i0.ɵɵrestoreView(_r1).$implicit; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onBarSelect(bar_r2)); })("keydown.space", function TumUiBarChartComponent_For_10_Template_rect_keydown_space_0_listener($event) { const bar_r2 = i0.ɵɵrestoreView(_r1).$implicit; const ctx_r2 = i0.ɵɵnextContext(); ctx_r2.onBarSelect(bar_r2); return i0.ɵɵresetView($event.preventDefault()); });
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const bar_r2 = ctx.$implicit;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵclassProp("tum-ui-bar-chart-bar-interactive", ctx_r2.interactive());
    i0.ɵɵattribute("x", bar_r2.x)("y", bar_r2.y)("width", bar_r2.width)("height", bar_r2.height)("fill", bar_r2.color)("role", ctx_r2.interactive() ? "button" : undefined)("tabindex", ctx_r2.interactive() ? 0 : undefined)("aria-label", ctx_r2.interactive() ? ctx_r2.accessibleName(bar_r2.context) : undefined);
} }
function TumUiBarChartComponent_For_12_Conditional_0_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵnamespaceSVG();
    i0.ɵɵelementStart(0, "text", 9);
    i0.ɵɵtext(1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const dataLabel_r4 = ctx;
    i0.ɵɵattribute("x", dataLabel_r4.x)("y", dataLabel_r4.y)("text-anchor", dataLabel_r4.anchor);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate1(" ", dataLabel_r4.text, " ");
} }
function TumUiBarChartComponent_For_12_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵconditionalCreate(0, TumUiBarChartComponent_For_12_Conditional_0_Template, 2, 4, ":svg:text", 9);
} if (rf & 2) {
    let tmp_11_0;
    const bar_r5 = ctx.$implicit;
    i0.ɵɵconditional((tmp_11_0 = bar_r5.dataLabel) ? 0 : -1, tmp_11_0);
} }
function TumUiBarChartComponent_Conditional_13_Template(rf, ctx) { if (rf & 1) {
    const _r6 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "tum-ui-chart-legend", 10);
    i0.ɵɵlistener("toggleEntry", function TumUiBarChartComponent_Conditional_13_Template_tum_ui_chart_legend_toggleEntry_0_listener($event) { i0.ɵɵrestoreView(_r6); const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onLegendToggle($event)); });
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵproperty("items", ctx_r2.legendItems())("position", ctx_r2.legendPosition());
} }
function TumUiBarChartComponent_Conditional_14_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "tum-ui-chart-tooltip", 6);
} if (rf & 2) {
    const tooltipView_r7 = ctx;
    i0.ɵɵproperty("title", tooltipView_r7.title)("lines", tooltipView_r7.lines)("x", tooltipView_r7.x)("y", tooltipView_r7.y)("below", tooltipView_r7.below);
} }
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
    static ɵfac = function TumUiBarChartComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiBarChartComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiBarChartComponent, selectors: [["tum-ui-bar-chart"]], viewQuery: function TumUiBarChartComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.canvas, _c0$t, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, hostAttrs: [1, "tum-ui-bar-chart"], inputs: { labels: [1, "labels"], series: [1, "series"], config: [1, "config"], ariaLabel: [1, "ariaLabel"], ariaLabelledBy: [1, "ariaLabelledBy"], interactive: [1, "interactive"] }, outputs: { dataSelect: "dataSelect" }, decls: 16, vars: 16, consts: [["canvas", ""], [1, "tum-ui-chart-layout"], ["role", "img", 1, "tum-ui-chart-canvas"], ["tumUiChartAxes", "", 3, "gridLines", "ticks", "titles"], [1, "tum-ui-bar-chart-bar", 3, "tum-ui-bar-chart-bar-interactive"], [3, "items", "position"], [3, "title", "lines", "x", "y", "below"], [3, "caption", "rows"], [1, "tum-ui-bar-chart-bar", 3, "mouseenter", "mousemove", "mouseleave", "click", "keydown.enter", "keydown.space"], [1, "tum-ui-bar-chart-data-label"], [3, "toggleEntry", "items", "position"]], template: function TumUiBarChartComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵelementStart(0, "div", 1);
            i0.ɵɵnamespaceSVG();
            i0.ɵɵelementStart(1, "svg", 2, 0)(3, "defs")(4, "clipPath");
            i0.ɵɵelement(5, "rect");
            i0.ɵɵelementEnd()();
            i0.ɵɵelementStart(6, "g");
            i0.ɵɵelement(7, "g", 3);
            i0.ɵɵelementStart(8, "g");
            i0.ɵɵrepeaterCreate(9, TumUiBarChartComponent_For_10_Template, 1, 10, ":svg:rect", 4, _forTrack0$5);
            i0.ɵɵelementEnd();
            i0.ɵɵrepeaterCreate(11, TumUiBarChartComponent_For_12_Template, 1, 1, null, null, _forTrack0$5);
            i0.ɵɵelementEnd()();
            i0.ɵɵconditionalCreate(13, TumUiBarChartComponent_Conditional_13_Template, 1, 2, "tum-ui-chart-legend", 5);
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(14, TumUiBarChartComponent_Conditional_14_Template, 1, 5, "tum-ui-chart-tooltip", 6);
            i0.ɵɵnamespaceHTML();
            i0.ɵɵelement(15, "tum-ui-chart-data-table", 7);
        } if (rf & 2) {
            let tmp_16_0;
            i0.ɵɵattribute("data-legend", ctx.legendPosition() ?? "none");
            i0.ɵɵadvance();
            i0.ɵɵattribute("aria-label", ctx.ariaLabel())("aria-labelledby", ctx.ariaLabelledBy())("aria-hidden", ctx.ariaLabel() || ctx.ariaLabelledBy() ? undefined : true);
            i0.ɵɵadvance(3);
            i0.ɵɵattribute("id", ctx.clipId);
            i0.ɵɵadvance();
            i0.ɵɵattribute("width", ctx.plot().width)("height", ctx.plot().height);
            i0.ɵɵadvance();
            i0.ɵɵattribute("transform", "translate(" + ctx.plot().left + " " + ctx.plot().top + ")");
            i0.ɵɵadvance();
            i0.ɵɵproperty("gridLines", ctx.gridLines())("ticks", ctx.ticks())("titles", ctx.axisTitles());
            i0.ɵɵadvance();
            i0.ɵɵattribute("clip-path", "url(#" + ctx.clipId + ")");
            i0.ɵɵadvance();
            i0.ɵɵrepeater(ctx.bars());
            i0.ɵɵadvance(2);
            i0.ɵɵrepeater(ctx.bars());
            i0.ɵɵadvance(2);
            i0.ɵɵconditional(ctx.legendPosition() && ctx.legendItems().length ? 13 : -1);
            i0.ɵɵadvance();
            i0.ɵɵconditional((tmp_16_0 = ctx.tooltip()) ? 14 : -1, tmp_16_0);
            i0.ɵɵadvance();
            i0.ɵɵproperty("caption", ctx.ariaLabel())("rows", ctx.accessibleRows());
        } }, dependencies: [TumUiChartAxesComponent, TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent], styles: ["[_nghost-%COMP%]{display:block;position:relative}@layer base{[_nghost-%COMP%]{width:100%;height:100%}}.tum-ui-chart-layout[_ngcontent-%COMP%]{display:flex;width:100%;height:100%;gap:calc(var(--%NS%tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right][_ngcontent-%COMP%]{flex-direction:row}.tum-ui-chart-layout[data-legend=left][_ngcontent-%COMP%]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom][_ngcontent-%COMP%], .tum-ui-chart-layout[data-legend=none][_ngcontent-%COMP%]{flex-direction:column}.tum-ui-chart-layout[data-legend=top][_ngcontent-%COMP%]{flex-direction:column-reverse}.tum-ui-chart-canvas[_ngcontent-%COMP%]{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-bar-chart-bar-interactive[_ngcontent-%COMP%]{cursor:pointer}.tum-ui-bar-chart-bar[_ngcontent-%COMP%]:hover{filter:brightness(1.08)}.tum-ui-bar-chart-data-label[_ngcontent-%COMP%]{fill:var(--%NS%tumaet-ui-text-color);font-size:var(--%NS%tumaet-ui-font-size-xs);font-family:inherit}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiBarChartComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-bar-chart', imports: [TumUiChartAxesComponent, TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent], changeDetection: ChangeDetectionStrategy.OnPush, host: { class: 'tum-ui-bar-chart' }, template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        <defs>\n            <clipPath [attr.id]=\"clipId\">\n                <rect [attr.width]=\"plot().width\" [attr.height]=\"plot().height\" />\n            </clipPath>\n        </defs>\n        <g [attr.transform]=\"'translate(' + plot().left + ' ' + plot().top + ')'\">\n            <svg:g tumUiChartAxes [gridLines]=\"gridLines()\" [ticks]=\"ticks()\" [titles]=\"axisTitles()\" />\n\n            <!-- A value beyond the configured axis limits is clipped to the plot rather than drawn over the\n                 heading above it. The data labels stay outside this group so they remain readable. -->\n            <g [attr.clip-path]=\"'url(#' + clipId + ')'\">\n                @for (bar of bars(); track bar.key) {\n                    <rect\n                        class=\"tum-ui-bar-chart-bar\"\n                        [class.tum-ui-bar-chart-bar-interactive]=\"interactive()\"\n                        [attr.x]=\"bar.x\"\n                        [attr.y]=\"bar.y\"\n                        [attr.width]=\"bar.width\"\n                        [attr.height]=\"bar.height\"\n                        [attr.fill]=\"bar.color\"\n                        [attr.role]=\"interactive() ? 'button' : undefined\"\n                        [attr.tabindex]=\"interactive() ? 0 : undefined\"\n                        [attr.aria-label]=\"interactive() ? accessibleName(bar.context) : undefined\"\n                        (mouseenter)=\"onBarEnter(bar, $event)\"\n                        (mousemove)=\"onBarEnter(bar, $event)\"\n                        (mouseleave)=\"onBarLeave()\"\n                        (click)=\"onBarSelect(bar)\"\n                        (keydown.enter)=\"onBarSelect(bar)\"\n                        (keydown.space)=\"onBarSelect(bar); $event.preventDefault()\"\n                    />\n                }\n            </g>\n\n            @for (bar of bars(); track bar.key) {\n                @if (bar.dataLabel; as dataLabel) {\n                    <text class=\"tum-ui-bar-chart-data-label\" [attr.x]=\"dataLabel.x\" [attr.y]=\"dataLabel.y\" [attr.text-anchor]=\"dataLabel.anchor\">\n                        {{ dataLabel.text }}\n                    </text>\n                }\n            }\n        </g>\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n", styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-bar-chart-bar-interactive{cursor:pointer}.tum-ui-bar-chart-bar:hover{filter:brightness(1.08)}.tum-ui-bar-chart-data-label{fill:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-xs);font-family:inherit}\n"] }]
    }], () => [], { canvas: [{ type: i0.ViewChild, args: ['canvas', { isSignal: true }] }], labels: [{ type: i0.Input, args: [{ isSignal: true, alias: "labels", required: true }] }], series: [{ type: i0.Input, args: [{ isSignal: true, alias: "series", required: true }] }], config: [{ type: i0.Input, args: [{ isSignal: true, alias: "config", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }], interactive: [{ type: i0.Input, args: [{ isSignal: true, alias: "interactive", required: false }] }], dataSelect: [{ type: i0.Output, args: ["dataSelect"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiBarChartComponent, { className: "TumUiBarChartComponent", filePath: "lib/chart/tum-ui-bar-chart.component.ts", lineNumber: 62 }); })();

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

const _c0$s = ["canvas"];
const _forTrack0$4 = ($index, $item) => $item.key;
function TumUiDoughnutChartComponent_For_4_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵnamespaceSVG();
    i0.ɵɵelementStart(0, "path", 7);
    i0.ɵɵlistener("mouseenter", function TumUiDoughnutChartComponent_For_4_Template_path_mouseenter_0_listener($event) { const slice_r2 = i0.ɵɵrestoreView(_r1).$implicit; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onSliceEnter(slice_r2, $event)); })("mousemove", function TumUiDoughnutChartComponent_For_4_Template_path_mousemove_0_listener($event) { const slice_r2 = i0.ɵɵrestoreView(_r1).$implicit; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onSliceEnter(slice_r2, $event)); })("mouseleave", function TumUiDoughnutChartComponent_For_4_Template_path_mouseleave_0_listener() { i0.ɵɵrestoreView(_r1); const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onSliceLeave()); })("click", function TumUiDoughnutChartComponent_For_4_Template_path_click_0_listener() { const slice_r2 = i0.ɵɵrestoreView(_r1).$implicit; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onSliceSelect(slice_r2)); });
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const slice_r2 = ctx.$implicit;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵclassProp("tum-ui-doughnut-chart-slice-interactive", ctx_r2.interactive());
    i0.ɵɵattribute("d", slice_r2.path)("fill", slice_r2.color);
} }
function TumUiDoughnutChartComponent_Conditional_5_Template(rf, ctx) { if (rf & 1) {
    const _r4 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "tum-ui-chart-legend", 8);
    i0.ɵɵlistener("toggleEntry", function TumUiDoughnutChartComponent_Conditional_5_Template_tum_ui_chart_legend_toggleEntry_0_listener($event) { i0.ɵɵrestoreView(_r4); const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onLegendToggle($event)); });
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵproperty("items", ctx_r2.legendItems())("position", ctx_r2.legendPosition());
} }
function TumUiDoughnutChartComponent_Conditional_6_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "tum-ui-chart-tooltip", 5);
} if (rf & 2) {
    const tooltipView_r5 = ctx;
    i0.ɵɵproperty("title", tooltipView_r5.title)("lines", tooltipView_r5.lines)("x", tooltipView_r5.x)("y", tooltipView_r5.y)("below", tooltipView_r5.below);
} }
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
    static ɵfac = function TumUiDoughnutChartComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiDoughnutChartComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiDoughnutChartComponent, selectors: [["tum-ui-doughnut-chart"]], viewQuery: function TumUiDoughnutChartComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.canvas, _c0$s, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, hostAttrs: [1, "tum-ui-doughnut-chart"], inputs: { labels: [1, "labels"], series: [1, "series"], config: [1, "config"], ariaLabel: [1, "ariaLabel"], ariaLabelledBy: [1, "ariaLabelledBy"], interactive: [1, "interactive"] }, outputs: { dataSelect: "dataSelect" }, decls: 8, vars: 8, consts: [["canvas", ""], [1, "tum-ui-chart-layout"], ["role", "img", 1, "tum-ui-chart-canvas"], [1, "tum-ui-doughnut-chart-slice", 3, "tum-ui-doughnut-chart-slice-interactive"], [3, "items", "position"], [3, "title", "lines", "x", "y", "below"], [3, "caption", "rows"], [1, "tum-ui-doughnut-chart-slice", 3, "mouseenter", "mousemove", "mouseleave", "click"], [3, "toggleEntry", "items", "position"]], template: function TumUiDoughnutChartComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵelementStart(0, "div", 1);
            i0.ɵɵnamespaceSVG();
            i0.ɵɵelementStart(1, "svg", 2, 0);
            i0.ɵɵrepeaterCreate(3, TumUiDoughnutChartComponent_For_4_Template, 1, 4, ":svg:path", 3, _forTrack0$4);
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(5, TumUiDoughnutChartComponent_Conditional_5_Template, 1, 2, "tum-ui-chart-legend", 4);
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(6, TumUiDoughnutChartComponent_Conditional_6_Template, 1, 5, "tum-ui-chart-tooltip", 5);
            i0.ɵɵnamespaceHTML();
            i0.ɵɵelement(7, "tum-ui-chart-data-table", 6);
        } if (rf & 2) {
            let tmp_7_0;
            i0.ɵɵattribute("data-legend", ctx.legendPosition() ?? "none");
            i0.ɵɵadvance();
            i0.ɵɵattribute("aria-label", ctx.ariaLabel())("aria-labelledby", ctx.ariaLabelledBy())("aria-hidden", ctx.ariaLabel() || ctx.ariaLabelledBy() ? undefined : true);
            i0.ɵɵadvance(2);
            i0.ɵɵrepeater(ctx.slices());
            i0.ɵɵadvance(2);
            i0.ɵɵconditional(ctx.legendPosition() && ctx.legendItems().length ? 5 : -1);
            i0.ɵɵadvance();
            i0.ɵɵconditional((tmp_7_0 = ctx.tooltip()) ? 6 : -1, tmp_7_0);
            i0.ɵɵadvance();
            i0.ɵɵproperty("caption", ctx.ariaLabel())("rows", ctx.accessibleRows());
        } }, dependencies: [TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent], styles: ["[_nghost-%COMP%]{display:block;position:relative}@layer base{[_nghost-%COMP%]{width:100%;height:100%}}.tum-ui-chart-layout[_ngcontent-%COMP%]{display:flex;width:100%;height:100%;gap:calc(var(--%NS%tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right][_ngcontent-%COMP%]{flex-direction:row}.tum-ui-chart-layout[data-legend=left][_ngcontent-%COMP%]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom][_ngcontent-%COMP%], .tum-ui-chart-layout[data-legend=none][_ngcontent-%COMP%]{flex-direction:column}.tum-ui-chart-layout[data-legend=top][_ngcontent-%COMP%]{flex-direction:column-reverse}.tum-ui-chart-canvas[_ngcontent-%COMP%]{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-doughnut-chart-slice-interactive[_ngcontent-%COMP%]{cursor:pointer}.tum-ui-doughnut-chart-slice[_ngcontent-%COMP%]:hover{filter:brightness(1.08)}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiDoughnutChartComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-doughnut-chart', imports: [TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent], changeDetection: ChangeDetectionStrategy.OnPush, host: { class: 'tum-ui-doughnut-chart' }, template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        @for (slice of slices(); track slice.key) {\n            <path\n                class=\"tum-ui-doughnut-chart-slice\"\n                [class.tum-ui-doughnut-chart-slice-interactive]=\"interactive()\"\n                [attr.d]=\"slice.path\"\n                [attr.fill]=\"slice.color\"\n                (mouseenter)=\"onSliceEnter(slice, $event)\"\n                (mousemove)=\"onSliceEnter(slice, $event)\"\n                (mouseleave)=\"onSliceLeave()\"\n                (click)=\"onSliceSelect(slice)\"\n            />\n        }\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n", styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-doughnut-chart-slice-interactive{cursor:pointer}.tum-ui-doughnut-chart-slice:hover{filter:brightness(1.08)}\n"] }]
    }], () => [], { canvas: [{ type: i0.ViewChild, args: ['canvas', { isSignal: true }] }], labels: [{ type: i0.Input, args: [{ isSignal: true, alias: "labels", required: true }] }], series: [{ type: i0.Input, args: [{ isSignal: true, alias: "series", required: true }] }], config: [{ type: i0.Input, args: [{ isSignal: true, alias: "config", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }], interactive: [{ type: i0.Input, args: [{ isSignal: true, alias: "interactive", required: false }] }], dataSelect: [{ type: i0.Output, args: ["dataSelect"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiDoughnutChartComponent, { className: "TumUiDoughnutChartComponent", filePath: "lib/chart/tum-ui-doughnut-chart.component.ts", lineNumber: 31 }); })();

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

const _c0$r = ["canvas"];
const _forTrack0$3 = ($index, $item) => $item.key;
function TumUiLineChartComponent_Conditional_8_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵnamespaceSVG();
    i0.ɵɵelement(0, "line", 4);
} if (rf & 2) {
    const x_r1 = ctx;
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵattribute("x1", x_r1)("y1", 0)("x2", x_r1)("y2", ctx_r1.plot().height);
} }
function TumUiLineChartComponent_For_11_For_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵnamespaceSVG();
    i0.ɵɵelement(0, "path", 10);
} if (rf & 2) {
    const path_r3 = ctx.$implicit;
    const line_r4 = i0.ɵɵnextContext().$implicit;
    i0.ɵɵclassProp("tum-ui-line-chart-line-dashed", line_r4.dashed);
    i0.ɵɵattribute("d", path_r3)("stroke", line_r4.color);
} }
function TumUiLineChartComponent_For_11_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵrepeaterCreate(0, TumUiLineChartComponent_For_11_For_1_Template, 1, 4, ":svg:path", 9, i0.ɵɵrepeaterTrackByIndex);
} if (rf & 2) {
    const line_r4 = ctx.$implicit;
    i0.ɵɵrepeater(line_r4.paths);
} }
function TumUiLineChartComponent_For_13_For_1_Template(rf, ctx) { if (rf & 1) {
    const _r5 = i0.ɵɵgetCurrentView();
    i0.ɵɵnamespaceSVG();
    i0.ɵɵelementStart(0, "circle", 12);
    i0.ɵɵlistener("keydown.enter", function TumUiLineChartComponent_For_13_For_1_Template_circle_keydown_enter_0_listener() { const point_r6 = i0.ɵɵrestoreView(_r5).$implicit; const ctx_r1 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r1.onPointSelect(point_r6.context)); })("keydown.space", function TumUiLineChartComponent_For_13_For_1_Template_circle_keydown_space_0_listener($event) { const point_r6 = i0.ɵɵrestoreView(_r5).$implicit; const ctx_r1 = i0.ɵɵnextContext(2); ctx_r1.onPointSelect(point_r6.context); return i0.ɵɵresetView($event.preventDefault()); });
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const point_r6 = ctx.$implicit;
    const line_r7 = i0.ɵɵnextContext().$implicit;
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵclassProp("tum-ui-line-chart-point-interactive", ctx_r1.interactive());
    i0.ɵɵattribute("cx", point_r6.x)("cy", point_r6.y)("r", ctx_r1.pointRadius)("fill", line_r7.color)("role", ctx_r1.interactive() ? "button" : undefined)("tabindex", ctx_r1.interactive() ? 0 : undefined)("aria-label", ctx_r1.interactive() ? ctx_r1.accessibleName(point_r6.context) : undefined);
} }
function TumUiLineChartComponent_For_13_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵrepeaterCreate(0, TumUiLineChartComponent_For_13_For_1_Template, 1, 9, ":svg:circle", 11, _forTrack0$3);
} if (rf & 2) {
    const line_r7 = ctx.$implicit;
    i0.ɵɵrepeater(line_r7.points);
} }
function TumUiLineChartComponent_Conditional_15_Template(rf, ctx) { if (rf & 1) {
    const _r8 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "tum-ui-chart-legend", 13);
    i0.ɵɵlistener("toggleEntry", function TumUiLineChartComponent_Conditional_15_Template_tum_ui_chart_legend_toggleEntry_0_listener($event) { i0.ɵɵrestoreView(_r8); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.onLegendToggle($event)); });
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵproperty("items", ctx_r1.legendItems())("position", ctx_r1.legendPosition());
} }
function TumUiLineChartComponent_Conditional_16_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "tum-ui-chart-tooltip", 7);
} if (rf & 2) {
    const tooltipView_r9 = ctx;
    i0.ɵɵproperty("title", tooltipView_r9.title)("lines", tooltipView_r9.lines)("x", tooltipView_r9.x)("y", tooltipView_r9.y)("below", tooltipView_r9.below);
} }
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
    static ɵfac = function TumUiLineChartComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiLineChartComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiLineChartComponent, selectors: [["tum-ui-line-chart"]], viewQuery: function TumUiLineChartComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.canvas, _c0$r, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, hostAttrs: [1, "tum-ui-line-chart"], inputs: { labels: [1, "labels"], series: [1, "series"], config: [1, "config"], ariaLabel: [1, "ariaLabel"], ariaLabelledBy: [1, "ariaLabelledBy"], interactive: [1, "interactive"] }, outputs: { dataSelect: "dataSelect" }, decls: 18, vars: 21, consts: [["canvas", ""], [1, "tum-ui-chart-layout"], ["role", "img", 1, "tum-ui-chart-canvas"], ["tumUiChartAxes", "", 3, "gridLines", "ticks", "titles"], [1, "tum-ui-line-chart-guide"], [1, "tum-ui-line-chart-hit-area", 3, "mousemove", "mouseleave", "click"], [3, "items", "position"], [3, "title", "lines", "x", "y", "below"], [3, "caption", "rows"], ["fill", "none", 1, "tum-ui-line-chart-line", 3, "tum-ui-line-chart-line-dashed"], ["fill", "none", 1, "tum-ui-line-chart-line"], [1, "tum-ui-line-chart-point", 3, "tum-ui-line-chart-point-interactive"], [1, "tum-ui-line-chart-point", 3, "keydown.enter", "keydown.space"], [3, "toggleEntry", "items", "position"]], template: function TumUiLineChartComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵelementStart(0, "div", 1);
            i0.ɵɵnamespaceSVG();
            i0.ɵɵelementStart(1, "svg", 2, 0)(3, "defs")(4, "clipPath");
            i0.ɵɵelement(5, "rect");
            i0.ɵɵelementEnd()();
            i0.ɵɵelementStart(6, "g");
            i0.ɵɵelement(7, "g", 3);
            i0.ɵɵconditionalCreate(8, TumUiLineChartComponent_Conditional_8_Template, 1, 4, ":svg:line", 4);
            i0.ɵɵelementStart(9, "g");
            i0.ɵɵrepeaterCreate(10, TumUiLineChartComponent_For_11_Template, 2, 0, null, null, _forTrack0$3);
            i0.ɵɵrepeaterCreate(12, TumUiLineChartComponent_For_13_Template, 2, 0, null, null, _forTrack0$3);
            i0.ɵɵelementEnd();
            i0.ɵɵelementStart(14, "rect", 5);
            i0.ɵɵlistener("mousemove", function TumUiLineChartComponent_Template_rect_mousemove_14_listener($event) { return ctx.onPlotMove($event); })("mouseleave", function TumUiLineChartComponent_Template_rect_mouseleave_14_listener() { return ctx.onPlotLeave(); })("click", function TumUiLineChartComponent_Template_rect_click_14_listener($event) { return ctx.onPlotClick($event); });
            i0.ɵɵelementEnd()()();
            i0.ɵɵconditionalCreate(15, TumUiLineChartComponent_Conditional_15_Template, 1, 2, "tum-ui-chart-legend", 6);
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(16, TumUiLineChartComponent_Conditional_16_Template, 1, 5, "tum-ui-chart-tooltip", 7);
            i0.ɵɵnamespaceHTML();
            i0.ɵɵelement(17, "tum-ui-chart-data-table", 8);
        } if (rf & 2) {
            let tmp_12_0;
            let tmp_20_0;
            i0.ɵɵattribute("data-legend", ctx.legendPosition() ?? "none");
            i0.ɵɵadvance();
            i0.ɵɵattribute("aria-label", ctx.ariaLabel())("aria-labelledby", ctx.ariaLabelledBy())("aria-hidden", ctx.ariaLabel() || ctx.ariaLabelledBy() ? undefined : true);
            i0.ɵɵadvance(3);
            i0.ɵɵattribute("id", ctx.clipId);
            i0.ɵɵadvance();
            i0.ɵɵattribute("width", ctx.plot().width)("height", ctx.plot().height);
            i0.ɵɵadvance();
            i0.ɵɵattribute("transform", "translate(" + ctx.plot().left + " " + ctx.plot().top + ")");
            i0.ɵɵadvance();
            i0.ɵɵproperty("gridLines", ctx.gridLines())("ticks", ctx.ticks())("titles", ctx.axisTitles());
            i0.ɵɵadvance();
            i0.ɵɵconditional((tmp_12_0 = ctx.guideX()) ? 8 : -1, tmp_12_0);
            i0.ɵɵadvance();
            i0.ɵɵattribute("clip-path", "url(#" + ctx.clipId + ")");
            i0.ɵɵadvance();
            i0.ɵɵrepeater(ctx.lines());
            i0.ɵɵadvance(2);
            i0.ɵɵrepeater(ctx.lines());
            i0.ɵɵadvance(2);
            i0.ɵɵclassProp("tum-ui-line-chart-hit-area-interactive", ctx.interactive());
            i0.ɵɵattribute("width", ctx.plot().width)("height", ctx.plot().height);
            i0.ɵɵadvance();
            i0.ɵɵconditional(ctx.legendPosition() && ctx.legendItems().length ? 15 : -1);
            i0.ɵɵadvance();
            i0.ɵɵconditional((tmp_20_0 = ctx.tooltip()) ? 16 : -1, tmp_20_0);
            i0.ɵɵadvance();
            i0.ɵɵproperty("caption", ctx.ariaLabel())("rows", ctx.accessibleRows());
        } }, dependencies: [TumUiChartAxesComponent, TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent], styles: ["[_nghost-%COMP%]{display:block;position:relative}@layer base{[_nghost-%COMP%]{width:100%;height:100%}}.tum-ui-chart-layout[_ngcontent-%COMP%]{display:flex;width:100%;height:100%;gap:calc(var(--%NS%tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right][_ngcontent-%COMP%]{flex-direction:row}.tum-ui-chart-layout[data-legend=left][_ngcontent-%COMP%]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom][_ngcontent-%COMP%], .tum-ui-chart-layout[data-legend=none][_ngcontent-%COMP%]{flex-direction:column}.tum-ui-chart-layout[data-legend=top][_ngcontent-%COMP%]{flex-direction:column-reverse}.tum-ui-chart-canvas[_ngcontent-%COMP%]{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-line-chart-line[_ngcontent-%COMP%]{stroke-width:2;fill:none}.tum-ui-line-chart-line-dashed[_ngcontent-%COMP%]{stroke-dasharray:5 5;stroke-width:1.5}.tum-ui-line-chart-point[_ngcontent-%COMP%]{pointer-events:none}.tum-ui-line-chart-point-interactive[_ngcontent-%COMP%]{pointer-events:all;cursor:pointer}.tum-ui-line-chart-guide[_ngcontent-%COMP%]{stroke:var(--%NS%tumaet-ui-border-color);stroke-width:1}.tum-ui-line-chart-hit-area[_ngcontent-%COMP%]{fill:transparent}.tum-ui-line-chart-hit-area-interactive[_ngcontent-%COMP%]{cursor:pointer}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiLineChartComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-line-chart', imports: [TumUiChartAxesComponent, TumUiChartLegendComponent, TumUiChartTooltipComponent, TumUiChartDataTableComponent], changeDetection: ChangeDetectionStrategy.OnPush, host: { class: 'tum-ui-line-chart' }, template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        <defs>\n            <clipPath [attr.id]=\"clipId\">\n                <rect [attr.width]=\"plot().width\" [attr.height]=\"plot().height\" />\n            </clipPath>\n        </defs>\n        <g [attr.transform]=\"'translate(' + plot().left + ' ' + plot().top + ')'\">\n            <svg:g tumUiChartAxes [gridLines]=\"gridLines()\" [ticks]=\"ticks()\" [titles]=\"axisTitles()\" />\n\n            @if (guideX(); as x) {\n                <line class=\"tum-ui-line-chart-guide\" [attr.x1]=\"x\" [attr.y1]=\"0\" [attr.x2]=\"x\" [attr.y2]=\"plot().height\" />\n            }\n\n            <!-- A point outside the configured axis limits is clipped to the plot rather than drawn past the\n                 container. The hit area below stays unclipped so hovering still works at the edges. -->\n            <g [attr.clip-path]=\"'url(#' + clipId + ')'\">\n                @for (line of lines(); track line.key) {\n                    @for (path of line.paths; track $index) {\n                        <path class=\"tum-ui-line-chart-line\" [class.tum-ui-line-chart-line-dashed]=\"line.dashed\" [attr.d]=\"path\" [attr.stroke]=\"line.color\" fill=\"none\" />\n                    }\n                }\n\n                @for (line of lines(); track line.key) {\n                    @for (point of line.points; track point.key) {\n                        <circle\n                            class=\"tum-ui-line-chart-point\"\n                            [class.tum-ui-line-chart-point-interactive]=\"interactive()\"\n                            [attr.cx]=\"point.x\"\n                            [attr.cy]=\"point.y\"\n                            [attr.r]=\"pointRadius\"\n                            [attr.fill]=\"line.color\"\n                            [attr.role]=\"interactive() ? 'button' : undefined\"\n                            [attr.tabindex]=\"interactive() ? 0 : undefined\"\n                            [attr.aria-label]=\"interactive() ? accessibleName(point.context) : undefined\"\n                            (keydown.enter)=\"onPointSelect(point.context)\"\n                            (keydown.space)=\"onPointSelect(point.context); $event.preventDefault()\"\n                        />\n                    }\n                }\n            </g>\n\n            <!--\n              Sits above the series so that hovering anywhere in the plot reports the nearest\n              category, and clicking selects the nearest point rather than only the marker itself.\n            -->\n            <rect\n                class=\"tum-ui-line-chart-hit-area\"\n                [class.tum-ui-line-chart-hit-area-interactive]=\"interactive()\"\n                [attr.width]=\"plot().width\"\n                [attr.height]=\"plot().height\"\n                (mousemove)=\"onPlotMove($event)\"\n                (mouseleave)=\"onPlotLeave()\"\n                (click)=\"onPlotClick($event)\"\n            />\n        </g>\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n", styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-line-chart-line{stroke-width:2;fill:none}.tum-ui-line-chart-line-dashed{stroke-dasharray:5 5;stroke-width:1.5}.tum-ui-line-chart-point{pointer-events:none}.tum-ui-line-chart-point-interactive{pointer-events:all;cursor:pointer}.tum-ui-line-chart-guide{stroke:var(--tumaet-ui-border-color);stroke-width:1}.tum-ui-line-chart-hit-area{fill:transparent}.tum-ui-line-chart-hit-area-interactive{cursor:pointer}\n"] }]
    }], () => [], { canvas: [{ type: i0.ViewChild, args: ['canvas', { isSignal: true }] }], labels: [{ type: i0.Input, args: [{ isSignal: true, alias: "labels", required: true }] }], series: [{ type: i0.Input, args: [{ isSignal: true, alias: "series", required: true }] }], config: [{ type: i0.Input, args: [{ isSignal: true, alias: "config", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }], interactive: [{ type: i0.Input, args: [{ isSignal: true, alias: "interactive", required: false }] }], dataSelect: [{ type: i0.Output, args: ["dataSelect"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiLineChartComponent, { className: "TumUiLineChartComponent", filePath: "lib/chart/tum-ui-line-chart.component.ts", lineNumber: 60 }); })();

function TumUiCheckboxComponent_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "fa-icon", 3);
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵclassMap(ctx_r0.iconClasses());
    i0.ɵɵproperty("icon", ctx_r0.faMinus);
} }
function TumUiCheckboxComponent_Conditional_3_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "fa-icon", 3);
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵclassMap(ctx_r0.iconClasses());
    i0.ɵɵproperty("icon", ctx_r0.faCheck);
} }
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
    static ɵfac = function TumUiCheckboxComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiCheckboxComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiCheckboxComponent, selectors: [["tum-ui-checkbox"]], hostAttrs: [1, "tum-ui-checkbox"], inputs: { disabled: [1, "disabled"], inputId: [1, "inputId"], name: [1, "name"], ariaLabel: [1, "ariaLabel"], checked: [1, "checked"], indeterminate: [1, "indeterminate"] }, outputs: { checked: "checkedChange", changed: "changed" }, features: [i0.ɵɵProvidersFeature([{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiCheckboxComponent), multi: true }])], decls: 4, vars: 9, consts: [["type", "checkbox", 1, "tum-ui-checkbox-input", 3, "change", "blur", "id", "checked", "indeterminate", "disabled"], ["aria-hidden", "true", 1, "tum-ui-checkbox-box"], [1, "tum-ui-checkbox-icon", 3, "icon", "class"], [1, "tum-ui-checkbox-icon", 3, "icon"]], template: function TumUiCheckboxComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵelementStart(0, "input", 0);
            i0.ɵɵlistener("change", function TumUiCheckboxComponent_Template_input_change_0_listener($event) { return ctx.onInputChange($event); })("blur", function TumUiCheckboxComponent_Template_input_blur_0_listener() { return ctx.onBlur(); });
            i0.ɵɵelementEnd();
            i0.ɵɵelementStart(1, "div", 1);
            i0.ɵɵconditionalCreate(2, TumUiCheckboxComponent_Conditional_2_Template, 1, 3, "fa-icon", 2)(3, TumUiCheckboxComponent_Conditional_3_Template, 1, 3, "fa-icon", 2);
            i0.ɵɵelementEnd();
        } if (rf & 2) {
            i0.ɵɵproperty("id", ctx.inputId())("checked", ctx.checked())("indeterminate", ctx.indeterminate())("disabled", ctx.isDisabled());
            i0.ɵɵattribute("name", ctx.name())("aria-label", ctx.ariaLabel());
            i0.ɵɵadvance();
            i0.ɵɵclassMap(ctx.boxClasses());
            i0.ɵɵadvance();
            i0.ɵɵconditional(ctx.showDash() ? 2 : ctx.showTick() ? 3 : -1);
        } }, dependencies: [FaIconComponent], styles: ["[_nghost-%COMP%]{position:relative;display:inline-flex;width:calc(var(--%NS%tumaet-ui-spacing, .25rem) * 5);height:calc(var(--%NS%tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-checkbox-input[_ngcontent-%COMP%]{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none}.tum-ui-checkbox-input[_ngcontent-%COMP%]:disabled{cursor:default}.tum-ui-checkbox-box[_ngcontent-%COMP%]{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:var(--%NS%tumaet-ui-radius-sm);box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--%NS%tumaet-ui-text-color) 5%,transparent);transition:background .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-checkbox-box[_ngcontent-%COMP%]:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-checkbox-icon[_ngcontent-%COMP%]{font-size:var(--%NS%tumaet-ui-font-size-sm);line-height:1}[_nghost-%COMP%]:has(.tum-ui-checkbox-input:hover:not(:disabled))   .tum-ui-checkbox-box[_ngcontent-%COMP%]:after{background-color:color-mix(in srgb,var(--%NS%tumaet-ui-text-color) 5%,transparent)}[_nghost-%COMP%]:has(.tum-ui-checkbox-input:focus-visible)   .tum-ui-checkbox-box[_ngcontent-%COMP%]{outline:2px solid var(--%NS%tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-checkbox-input[_ngcontent-%COMP%]{appearance:auto;opacity:1}.tum-ui-checkbox-box[_ngcontent-%COMP%]{display:none}}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiCheckboxComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-checkbox', imports: [FaIconComponent], host: { class: 'tum-ui-checkbox' }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiCheckboxComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<input\n    type=\"checkbox\"\n    class=\"tum-ui-checkbox-input\"\n    [id]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [checked]=\"checked()\"\n    [indeterminate]=\"indeterminate()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    (change)=\"onInputChange($event)\"\n    (blur)=\"onBlur()\"\n/>\n<div class=\"tum-ui-checkbox-box\" [class]=\"boxClasses()\" aria-hidden=\"true\">\n    @if (showDash()) {\n        <fa-icon [icon]=\"faMinus\" class=\"tum-ui-checkbox-icon\" [class]=\"iconClasses()\" />\n    } @else if (showTick()) {\n        <fa-icon [icon]=\"faCheck\" class=\"tum-ui-checkbox-icon\" [class]=\"iconClasses()\" />\n    }\n</div>\n", styles: [":host{position:relative;display:inline-flex;width:calc(var(--tumaet-ui-spacing, .25rem) * 5);height:calc(var(--tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-checkbox-input{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none}.tum-ui-checkbox-input:disabled{cursor:default}.tum-ui-checkbox-box{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:var(--tumaet-ui-radius-sm);box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent);transition:background .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-checkbox-box:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-checkbox-icon{font-size:var(--tumaet-ui-font-size-sm);line-height:1}:host:has(.tum-ui-checkbox-input:hover:not(:disabled)) .tum-ui-checkbox-box:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:has(.tum-ui-checkbox-input:focus-visible) .tum-ui-checkbox-box{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-checkbox-input{appearance:auto;opacity:1}.tum-ui-checkbox-box{display:none}}\n"] }]
    }], null, { disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], name: [{ type: i0.Input, args: [{ isSignal: true, alias: "name", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], checked: [{ type: i0.Input, args: [{ isSignal: true, alias: "checked", required: false }] }, { type: i0.Output, args: ["checkedChange"] }], indeterminate: [{ type: i0.Input, args: [{ isSignal: true, alias: "indeterminate", required: false }] }], changed: [{ type: i0.Output, args: ["changed"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiCheckboxComponent, { className: "TumUiCheckboxComponent", filePath: "lib/checkbox/tum-ui-checkbox.component.ts", lineNumber: 20 }); })();

const _c0$q = ["header"];
const _c1$6 = ["footer"];
const _c2$2 = ["panel"];
const _c3$1 = ["*"];
function TumUiDialogComponent_ng_template_0_Conditional_1_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementContainer(0, 6);
} if (rf & 2) {
    i0.ɵɵproperty("ngTemplateOutlet", ctx);
} }
function TumUiDialogComponent_ng_template_0_Conditional_1_Conditional_3_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵtext(0);
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext(3);
    i0.ɵɵtextInterpolate1(" ", ctx_r0.header(), " ");
} }
function TumUiDialogComponent_ng_template_0_Conditional_1_Conditional_4_Template(rf, ctx) { if (rf & 1) {
    const _r2 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "button", 8);
    i0.ɵɵpipe(1, "tumUiTranslate");
    i0.ɵɵlistener("click", function TumUiDialogComponent_ng_template_0_Conditional_1_Conditional_4_Template_button_click_0_listener() { i0.ɵɵrestoreView(_r2); const ctx_r0 = i0.ɵɵnextContext(3); return i0.ɵɵresetView(ctx_r0.close()); });
    i0.ɵɵelement(2, "fa-icon", 9);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext(3);
    i0.ɵɵattribute("aria-label", ctx_r0.closeButtonAriaLabel() ?? i0.ɵɵpipeBind1(1, 2, "tumUi.dialog.close"));
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r0.faXmark);
} }
function TumUiDialogComponent_ng_template_0_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "div", 2)(1, "div", 5);
    i0.ɵɵconditionalCreate(2, TumUiDialogComponent_ng_template_0_Conditional_1_Conditional_2_Template, 1, 1, "ng-container", 6)(3, TumUiDialogComponent_ng_template_0_Conditional_1_Conditional_3_Template, 1, 1);
    i0.ɵɵelementEnd();
    i0.ɵɵconditionalCreate(4, TumUiDialogComponent_ng_template_0_Conditional_1_Conditional_4_Template, 3, 4, "button", 7);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    let tmp_4_0;
    const ctx_r0 = i0.ɵɵnextContext(2);
    i0.ɵɵadvance();
    i0.ɵɵproperty("id", ctx_r0.titleId);
    i0.ɵɵadvance();
    i0.ɵɵconditional((tmp_4_0 = ctx_r0.headerTemplate()) ? 2 : 3, tmp_4_0);
    i0.ɵɵadvance(2);
    i0.ɵɵconditional(ctx_r0.closable() ? 4 : -1);
} }
function TumUiDialogComponent_ng_template_0_Conditional_4_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "div", 4);
    i0.ɵɵelementContainer(1, 6);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    i0.ɵɵadvance();
    i0.ɵɵproperty("ngTemplateOutlet", ctx);
} }
function TumUiDialogComponent_ng_template_0_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "div", 1);
    i0.ɵɵconditionalCreate(1, TumUiDialogComponent_ng_template_0_Conditional_1_Template, 5, 3, "div", 2);
    i0.ɵɵelementStart(2, "div", 3);
    i0.ɵɵprojection(3);
    i0.ɵɵelementEnd();
    i0.ɵɵconditionalCreate(4, TumUiDialogComponent_ng_template_0_Conditional_4_Template, 2, 1, "div", 4);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    let tmp_4_0;
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵclassMap(ctx_r0.sizeClasses());
    i0.ɵɵadvance();
    i0.ɵɵconditional(ctx_r0.showHeader() ? 1 : -1);
    i0.ɵɵadvance(3);
    i0.ɵɵconditional((tmp_4_0 = ctx_r0.footerTemplate()) ? 4 : -1, tmp_4_0);
} }
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
    static ɵfac = function TumUiDialogComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiDialogComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiDialogComponent, selectors: [["tum-ui-dialog"]], contentQueries: function TumUiDialogComponent_ContentQueries(rf, ctx, dirIndex) { if (rf & 1) {
            i0.ɵɵcontentQuerySignal(dirIndex, ctx.headerTemplate, _c0$q, 5, TemplateRef)(dirIndex, ctx.footerTemplate, _c1$6, 5, TemplateRef);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance(2);
        } }, viewQuery: function TumUiDialogComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.panel, _c2$2, 5, TemplateRef);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, inputs: { visible: [1, "visible"], header: [1, "header"], showHeader: [1, "showHeader"], closable: [1, "closable"], closeOnEscape: [1, "closeOnEscape"], dismissableMask: [1, "dismissableMask"], size: [1, "size"], ariaLabel: [1, "ariaLabel"], closeButtonAriaLabel: [1, "closeButtonAriaLabel"], role: [1, "role"], ariaDescribedBy: [1, "ariaDescribedBy"] }, outputs: { visible: "visibleChange", shown: "shown", hidden: "hidden" }, ngContentSelectors: _c3$1, decls: 2, vars: 0, consts: [["panel", ""], [1, "tum-ui-dialog", "tum:flex", "tum:max-h-[90dvh]", "tum:max-w-[90dvw]", "tum:flex-col", "tum:overflow-hidden", "tum:rounded-xl", "tum:border", "tum:border-border", "tum:bg-overlay-background", "tum:text-text", "tum:shadow-xl"], [1, "tum-ui-dialog-header", "tum:flex", "tum:shrink-0", "tum:items-center", "tum:justify-between", "tum:gap-2", "tum:p-4"], [1, "tum-ui-dialog-content", "tum:grow", "tum:overflow-y-auto", "tum:px-4", "tum:pb-4"], [1, "tum-ui-dialog-footer", "tum:flex", "tum:shrink-0", "tum:justify-end", "tum:gap-2", "tum:px-4", "tum:pb-4"], [1, "tum-ui-dialog-title", "tum:text-xl", "tum:font-semibold", 3, "id"], [3, "ngTemplateOutlet"], ["type", "button", 1, "tum-ui-dialog-close", "tum:inline-flex", "tum:h-8", "tum:w-8", "tum:shrink-0", "tum:cursor-pointer", "tum:appearance-none", "tum:items-center", "tum:justify-center", "tum:rounded-full", "tum:border-0", "tum:bg-transparent", "tum:text-muted", "tum:transition-colors", "tum:hover:bg-hover-background"], ["type", "button", 1, "tum-ui-dialog-close", "tum:inline-flex", "tum:h-8", "tum:w-8", "tum:shrink-0", "tum:cursor-pointer", "tum:appearance-none", "tum:items-center", "tum:justify-center", "tum:rounded-full", "tum:border-0", "tum:bg-transparent", "tum:text-muted", "tum:transition-colors", "tum:hover:bg-hover-background", 3, "click"], [3, "icon"]], template: function TumUiDialogComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵtemplate(0, TumUiDialogComponent_ng_template_0_Template, 5, 4, "ng-template", null, 0, i0.ɵɵtemplateRefExtractor);
        } }, dependencies: [NgTemplateOutlet, FaIconComponent, TumUiTranslatePipe], encapsulation: 2 });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiDialogComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-dialog', imports: [NgTemplateOutlet, FaIconComponent, TumUiTranslatePipe], changeDetection: ChangeDetectionStrategy.OnPush, template: "<ng-template #panel>\n    <div\n        class=\"tum-ui-dialog tum:flex tum:max-h-[90dvh] tum:max-w-[90dvw] tum:flex-col tum:overflow-hidden tum:rounded-xl tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-xl\"\n        [class]=\"sizeClasses()\"\n    >\n        @if (showHeader()) {\n            <div class=\"tum-ui-dialog-header tum:flex tum:shrink-0 tum:items-center tum:justify-between tum:gap-2 tum:p-4\">\n                <div [id]=\"titleId\" class=\"tum-ui-dialog-title tum:text-xl tum:font-semibold\">\n                    @if (headerTemplate(); as headerTpl) {\n                        <ng-container [ngTemplateOutlet]=\"headerTpl\" />\n                    } @else {\n                        {{ header() }}\n                    }\n                </div>\n                @if (closable()) {\n                    <button\n                        type=\"button\"\n                        class=\"tum-ui-dialog-close tum:inline-flex tum:h-8 tum:w-8 tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:rounded-full tum:border-0 tum:bg-transparent tum:text-muted tum:transition-colors tum:hover:bg-hover-background\"\n                        [attr.aria-label]=\"closeButtonAriaLabel() ?? ('tumUi.dialog.close' | tumUiTranslate)\"\n                        (click)=\"close()\"\n                    >\n                        <fa-icon [icon]=\"faXmark\" />\n                    </button>\n                }\n            </div>\n        }\n\n        <div class=\"tum-ui-dialog-content tum:grow tum:overflow-y-auto tum:px-4 tum:pb-4\">\n            <ng-content />\n        </div>\n\n        @if (footerTemplate(); as footerTpl) {\n            <div class=\"tum-ui-dialog-footer tum:flex tum:shrink-0 tum:justify-end tum:gap-2 tum:px-4 tum:pb-4\">\n                <ng-container [ngTemplateOutlet]=\"footerTpl\" />\n            </div>\n        }\n    </div>\n</ng-template>\n" }]
    }], null, { visible: [{ type: i0.Input, args: [{ isSignal: true, alias: "visible", required: false }] }, { type: i0.Output, args: ["visibleChange"] }], header: [{ type: i0.Input, args: [{ isSignal: true, alias: "header", required: false }] }], showHeader: [{ type: i0.Input, args: [{ isSignal: true, alias: "showHeader", required: false }] }], closable: [{ type: i0.Input, args: [{ isSignal: true, alias: "closable", required: false }] }], closeOnEscape: [{ type: i0.Input, args: [{ isSignal: true, alias: "closeOnEscape", required: false }] }], dismissableMask: [{ type: i0.Input, args: [{ isSignal: true, alias: "dismissableMask", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], closeButtonAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "closeButtonAriaLabel", required: false }] }], role: [{ type: i0.Input, args: [{ isSignal: true, alias: "role", required: false }] }], ariaDescribedBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaDescribedBy", required: false }] }], shown: [{ type: i0.Output, args: ["shown"] }], hidden: [{ type: i0.Output, args: ["hidden"] }], panel: [{ type: i0.ViewChild, args: ['panel', { ...{ read: TemplateRef }, isSignal: true }] }], headerTemplate: [{ type: i0.ContentChild, args: ['header', { ...{ read: TemplateRef }, isSignal: true }] }], footerTemplate: [{ type: i0.ContentChild, args: ['footer', { ...{ read: TemplateRef }, isSignal: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiDialogComponent, { className: "TumUiDialogComponent", filePath: "lib/dialog/tum-ui-dialog.component.ts", lineNumber: 74 }); })();

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
    static ɵfac = function TumUiConfirmationService_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiConfirmationService)(); };
    static ɵprov = /*@__PURE__*/ i0.ɵɵdefineInjectable({ token: TumUiConfirmationService, factory: TumUiConfirmationService.ɵfac });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiConfirmationService, [{
        type: Injectable
    }], null, null); })();

function TumUiConfirmDialogComponent_Conditional_0_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "fa-icon", 4);
} if (rf & 2) {
    const req_r3 = i0.ɵɵnextContext();
    i0.ɵɵproperty("icon", req_r3.icon);
} }
function TumUiConfirmDialogComponent_Conditional_0_ng_template_5_Template(rf, ctx) { if (rf & 1) {
    const _r4 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "tum-ui-button", 6);
    i0.ɵɵlistener("clicked", function TumUiConfirmDialogComponent_Conditional_0_ng_template_5_Template_tum_ui_button_clicked_0_listener() { i0.ɵɵrestoreView(_r4); const ctx_r1 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r1.reject()); });
    i0.ɵɵtext(1);
    i0.ɵɵelementEnd();
    i0.ɵɵelementStart(2, "tum-ui-button", 6);
    i0.ɵɵlistener("clicked", function TumUiConfirmDialogComponent_Conditional_0_ng_template_5_Template_tum_ui_button_clicked_2_listener() { i0.ɵɵrestoreView(_r4); const ctx_r1 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r1.accept()); });
    i0.ɵɵtext(3);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const req_r3 = i0.ɵɵnextContext();
    i0.ɵɵproperty("severity", req_r3.rejectSeverity ?? "secondary");
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(req_r3.rejectLabel);
    i0.ɵɵadvance();
    i0.ɵɵproperty("severity", req_r3.acceptSeverity ?? "primary");
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(req_r3.acceptLabel);
} }
function TumUiConfirmDialogComponent_Conditional_0_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "tum-ui-dialog", 2);
    i0.ɵɵlistener("hidden", function TumUiConfirmDialogComponent_Conditional_0_Template_tum_ui_dialog_hidden_0_listener() { i0.ɵɵrestoreView(_r1); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.onDialogHide()); });
    i0.ɵɵelementStart(1, "div", 3);
    i0.ɵɵconditionalCreate(2, TumUiConfirmDialogComponent_Conditional_0_Conditional_2_Template, 1, 1, "fa-icon", 4);
    i0.ɵɵelementStart(3, "span", 5);
    i0.ɵɵtext(4);
    i0.ɵɵelementEnd()();
    i0.ɵɵtemplate(5, TumUiConfirmDialogComponent_Conditional_0_ng_template_5_Template, 4, 4, "ng-template", null, 0, i0.ɵɵtemplateRefExtractor);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const req_r3 = ctx;
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵproperty("visible", ctx_r1.visible())("closable", true)("header", req_r3.header)("role", "alertdialog")("ariaDescribedBy", ctx_r1.messageId);
    i0.ɵɵadvance(2);
    i0.ɵɵconditional(req_r3.icon ? 2 : -1);
    i0.ɵɵadvance();
    i0.ɵɵproperty("id", ctx_r1.messageId);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(req_r3.message);
} }
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
    static ɵfac = function TumUiConfirmDialogComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiConfirmDialogComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiConfirmDialogComponent, selectors: [["tum-ui-confirm-dialog"]], inputs: { key: [1, "key"] }, decls: 1, vars: 1, consts: [["footer", ""], [3, "visible", "closable", "header", "role", "ariaDescribedBy"], [3, "hidden", "visible", "closable", "header", "role", "ariaDescribedBy"], [1, "tum:flex", "tum:items-center", "tum:gap-4"], [1, "tum:size-8", "tum:shrink-0", "tum:text-2xl", "tum:text-muted", 3, "icon"], [1, "tum-ui-confirm-dialog-message", 3, "id"], [3, "clicked", "severity"]], template: function TumUiConfirmDialogComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵconditionalCreate(0, TumUiConfirmDialogComponent_Conditional_0_Template, 7, 8, "tum-ui-dialog", 1);
        } if (rf & 2) {
            let tmp_0_0;
            i0.ɵɵconditional((tmp_0_0 = ctx.request()) ? 0 : -1, tmp_0_0);
        } }, dependencies: [TumUiDialogComponent, TumUiButtonComponent, FaIconComponent], encapsulation: 2 });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiConfirmDialogComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-confirm-dialog', imports: [TumUiDialogComponent, TumUiButtonComponent, FaIconComponent], changeDetection: ChangeDetectionStrategy.OnPush, template: "@if (request(); as req) {\n    <tum-ui-dialog [visible]=\"visible()\" [closable]=\"true\" [header]=\"req.header\" [role]=\"'alertdialog'\" [ariaDescribedBy]=\"messageId\" (hidden)=\"onDialogHide()\">\n        <div class=\"tum:flex tum:items-center tum:gap-4\">\n            @if (req.icon) {\n                <fa-icon [icon]=\"req.icon\" class=\"tum:size-8 tum:shrink-0 tum:text-2xl tum:text-muted\" />\n            }\n            <span [id]=\"messageId\" class=\"tum-ui-confirm-dialog-message\">{{ req.message }}</span>\n        </div>\n        <ng-template #footer>\n            <tum-ui-button [severity]=\"req.rejectSeverity ?? 'secondary'\" (clicked)=\"reject()\">{{ req.rejectLabel }}</tum-ui-button>\n            <tum-ui-button [severity]=\"req.acceptSeverity ?? 'primary'\" (clicked)=\"accept()\">{{ req.acceptLabel }}</tum-ui-button>\n        </ng-template>\n    </tum-ui-dialog>\n}\n" }]
    }], null, { key: [{ type: i0.Input, args: [{ isSignal: true, alias: "key", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiConfirmDialogComponent, { className: "TumUiConfirmDialogComponent", filePath: "lib/confirm-dialog/tum-ui-confirm-dialog.component.ts", lineNumber: 16 }); })();

function TumUiTooltipContentComponent_Conditional_0_For_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "li", 2);
    i0.ɵɵtext(1);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const item_r1 = ctx.$implicit;
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(item_r1);
} }
function TumUiTooltipContentComponent_Conditional_0_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "ul", 0);
    i0.ɵɵrepeaterCreate(1, TumUiTooltipContentComponent_Conditional_0_For_2_Template, 2, 1, "li", 2, i0.ɵɵrepeaterTrackByIndex);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵrepeater(ctx_r1.items());
} }
function TumUiTooltipContentComponent_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵtext(0);
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵtextInterpolate1(" ", ctx_r1.text(), " ");
} }
const ARROW_BASE = 'tum:absolute tum:h-2 tum:w-2 tum:rotate-45 tum:bg-tooltip-background';
/** Which edge the arrow sits on, and the translate that centres it on its anchor point. */
const ARROW_SIDE = {
    top: 'tum:top-full tum:-translate-x-1/2 tum:-translate-y-1/2',
    bottom: 'tum:bottom-full tum:-translate-x-1/2 tum:translate-y-1/2',
    left: 'tum:left-full tum:-translate-y-1/2 tum:-translate-x-1/2',
    right: 'tum:right-full tum:-translate-y-1/2 tum:translate-x-1/2',
};
/** The anchor point used until {@link TumUiTooltipContentComponent.arrowOffsetPx} says where the host actually is. */
const ARROW_CENTRE = {
    top: 'tum:left-1/2',
    bottom: 'tum:left-1/2',
    left: 'tum:top-1/2',
    right: 'tum:top-1/2',
};
const BUBBLE_BASE = 'tum-ui-tooltip-bubble tum:relative tum:inline-block tum:rounded-md tum:bg-tooltip-background tum:px-3 tum:py-2 tum:text-sm tum:text-tooltip tum:shadow-md';
/** A list of reasons needs more room than a one-line hint, so the two forms clamp differently. */
const BUBBLE_WIDTH = { text: 'tum:max-w-50', list: 'tum:max-w-100' };
class TumUiTooltipContentComponent {
    text = input('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "text" }] : /* istanbul ignore next */ []));
    /** Rendered as a bulleted list instead of {@link text} when non-empty. */
    items = input([], /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "items" }] : /* istanbul ignore next */ []));
    id = input('', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "id" }] : /* istanbul ignore next */ []));
    placement = input('top', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "placement" }] : /* istanbul ignore next */ []));
    /**
     * Where the arrow belongs along the bubble's edge, in pixels from its left or top. The bubble is pushed sideways
     * to stay on screen, so a centred arrow stops pointing at the host; the directive measures the host and reports
     * the point here. Undefined until measured, and then the arrow is centred as before.
     */
    arrowOffsetPx = input(undefined, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "arrowOffsetPx" }] : /* istanbul ignore next */ []));
    isHorizontal = computed(() => this.placement() === 'top' || this.placement() === 'bottom', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isHorizontal" }] : /* istanbul ignore next */ []));
    arrowClasses = computed(() => {
        const anchor = this.arrowOffsetPx() === undefined ? ` ${ARROW_CENTRE[this.placement()]}` : '';
        return `${ARROW_BASE} ${ARROW_SIDE[this.placement()]}${anchor}`;
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "arrowClasses" }] : /* istanbul ignore next */ []));
    arrowStyle = computed(() => {
        const offset = this.arrowOffsetPx();
        if (offset === undefined) {
            return null;
        }
        return { [this.isHorizontal() ? 'left' : 'top']: `${offset}px` };
    }, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "arrowStyle" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => `${BUBBLE_BASE} ${this.items().length ? BUBBLE_WIDTH.list : BUBBLE_WIDTH.text}`, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = function TumUiTooltipContentComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTooltipContentComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiTooltipContentComponent, selectors: [["tum-ui-tooltip-content"]], hostAttrs: ["role", "tooltip", 1, "tum-ui-tooltip-bubble"], hostVars: 3, hostBindings: function TumUiTooltipContentComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("id", ctx.id());
            i0.ɵɵclassMap(ctx.hostClasses());
        } }, inputs: { text: [1, "text"], items: [1, "items"], id: [1, "id"], placement: [1, "placement"], arrowOffsetPx: [1, "arrowOffsetPx"] }, decls: 3, vars: 5, consts: [[1, "tum:list-disc", "tum:ps-4", "tum:text-start"], ["aria-hidden", "true"], [1, "tum:mt-1", "tum:first:mt-0"]], template: function TumUiTooltipContentComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵconditionalCreate(0, TumUiTooltipContentComponent_Conditional_0_Template, 3, 0, "ul", 0)(1, TumUiTooltipContentComponent_Conditional_1_Template, 1, 1);
            i0.ɵɵdomElement(2, "span", 1);
        } if (rf & 2) {
            i0.ɵɵconditional(ctx.items().length ? 0 : 1);
            i0.ɵɵadvance(2);
            i0.ɵɵstyleMap(ctx.arrowStyle());
            i0.ɵɵclassMap(ctx.arrowClasses());
        } }, encapsulation: 2 });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTooltipContentComponent, [{
        type: Component,
        args: [{
                selector: 'tum-ui-tooltip-content',
                template: `
        @if (items().length) {
            <ul class="tum:list-disc tum:ps-4 tum:text-start">
                @for (item of items(); track $index) {
                    <li class="tum:mt-1 tum:first:mt-0">{{ item }}</li>
                }
            </ul>
        } @else {
            {{ text() }}
        }
        <span aria-hidden="true" [class]="arrowClasses()" [style]="arrowStyle()"></span>
    `,
                host: {
                    role: 'tooltip',
                    '[attr.id]': 'id()',
                    // The identifier is also static so the bubble is queryable before the first change detection run.
                    class: 'tum-ui-tooltip-bubble',
                    '[class]': 'hostClasses()',
                },
                changeDetection: ChangeDetectionStrategy.OnPush,
            }]
    }], null, { text: [{ type: i0.Input, args: [{ isSignal: true, alias: "text", required: false }] }], items: [{ type: i0.Input, args: [{ isSignal: true, alias: "items", required: false }] }], id: [{ type: i0.Input, args: [{ isSignal: true, alias: "id", required: false }] }], placement: [{ type: i0.Input, args: [{ isSignal: true, alias: "placement", required: false }] }], arrowOffsetPx: [{ type: i0.Input, args: [{ isSignal: true, alias: "arrowOffsetPx", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiTooltipContentComponent, { className: "TumUiTooltipContentComponent", filePath: "lib/tooltip/tum-ui-tooltip-content.component.ts", lineNumber: 50 }); })();

let nextTooltipId = 0;
/**
 * How close the arrow may get to a corner of the bubble. The arrow is 8px square and rotated, so it needs about six
 * pixels of clearance, and the bubble's own rounding needs a little more.
 */
const ARROW_EDGE_INSET_PX = 12;
/** Tooltip shown on hover or focus and associated with its host through `aria-describedby`. */
class TumUiTooltipDirective {
    overlayService = inject(TumUiOverlayService);
    elementRef = inject(ElementRef);
    /** A plain hint, or several items to render as a bulleted list. */
    content = input.required({ ...(ngDevMode ? { debugName: "content" } : /* istanbul ignore next */ {}), alias: 'tumUiTooltip' });
    placement = input('top', { ...(ngDevMode ? { debugName: "placement" } : /* istanbul ignore next */ {}), alias: 'tumUiTooltipPlacement' });
    /**
     * Whether the tooltip adds itself to the host's `aria-describedby` while open. Turn this off on a host that is
     * already described by permanent markup carrying the same text, so the description is not announced twice.
     */
    describesHost = input(true, { ...(ngDevMode ? { debugName: "describesHost" } : /* istanbul ignore next */ {}), alias: 'tumUiTooltipDescribesHost', transform: booleanAttribute });
    showDelayMs = input(150, { ...(ngDevMode ? { debugName: "showDelayMs" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    hideDelayMs = input(100, { ...(ngDevMode ? { debugName: "hideDelayMs" } : /* istanbul ignore next */ {}), transform: numberAttribute });
    text = computed(() => (Array.isArray(this.content()) ? '' : this.content()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "text" }] : /* istanbul ignore next */ []));
    items = computed(() => (Array.isArray(this.content()) ? this.content() : []), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "items" }] : /* istanbul ignore next */ []));
    // An empty array is truthy, so emptiness has to be asked of the normalised forms rather than of the input.
    isEmpty = computed(() => !this.text() && this.items().length === 0, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "isEmpty" }] : /* istanbul ignore next */ []));
    overlayRef;
    contentRef;
    positionSub;
    showTimer;
    hideTimer;
    tooltipId = `tum-ui-tooltip-${nextTooltipId++}`;
    interactionSub;
    appliedPlacement = 'top';
    triggerHovered = false;
    tooltipHovered = false;
    focused = false;
    pointerFocus = false;
    constructor() {
        effect(() => {
            // Read content unconditionally so changes remain tracked while the tooltip is hidden.
            const [text, items, isEmpty] = [this.text(), this.items(), this.isEmpty()];
            if (isEmpty) {
                this.hideNow();
            }
            else {
                this.contentRef?.setInput('text', text);
                this.contentRef?.setInput('items', items);
            }
        });
    }
    onHoverStart() {
        this.triggerHovered = true;
        this.scheduleShow();
    }
    onHoverEnd() {
        this.triggerHovered = false;
        this.pointerFocus = false;
        this.scheduleHideIfInactive();
    }
    /** Clicking a trigger focuses it, and focus outlives the pointer — so that focus must not hold the tooltip open. */
    onPointerDown() {
        this.pointerFocus = true;
    }
    onFocusStart() {
        if (this.pointerFocus) {
            // Pointer-driven focus: hover alone decides visibility, so the tooltip still hides on mouseleave.
            return;
        }
        this.focused = true;
        this.scheduleShow();
    }
    onFocusEnd() {
        this.focused = false;
        this.pointerFocus = false;
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
        if (this.overlayRef?.hasAttached() || this.isEmpty()) {
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
        this.appliedPlacement = this.placement();
        // CDK may emit the initial flipped position synchronously during attachment.
        this.positionSub = strategy.positionChanges.subscribe((change) => {
            this.appliedPlacement = this.overlayService.placementFromPosition(change.connectionPair);
            this.contentRef?.setInput('placement', this.appliedPlacement);
            this.updateArrowOffset();
        });
        this.contentRef = this.overlayRef.attach(new ComponentPortal(TumUiTooltipContentComponent));
        this.contentRef.setInput('text', this.text());
        this.contentRef.setInput('items', this.items());
        this.contentRef.setInput('id', this.tooltipId);
        this.contentRef.setInput('placement', this.appliedPlacement);
        this.updateArrowOffset();
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
    /**
     * Points the arrow at the host rather than at the middle of the bubble.
     *
     * The overlay is created with `withPush`, so a bubble that would leave the viewport is shoved sideways: an arrow
     * centred on the bubble then points at empty space beside the host. Measure where the host sits along the bubble's
     * edge and report that instead, clamped so the arrow stays clear of the rounded corners.
     */
    updateArrowOffset() {
        const bubble = this.contentRef?.location.nativeElement;
        if (!bubble) {
            return;
        }
        const hostRect = this.elementRef.nativeElement.getBoundingClientRect();
        const bubbleRect = bubble.getBoundingClientRect();
        const horizontal = this.appliedPlacement === 'top' || this.appliedPlacement === 'bottom';
        const hostCentre = horizontal ? hostRect.left + hostRect.width / 2 : hostRect.top + hostRect.height / 2;
        const bubbleStart = horizontal ? bubbleRect.left : bubbleRect.top;
        const bubbleLength = horizontal ? bubbleRect.width : bubbleRect.height;
        // A zero-sized bubble means it has not been laid out yet; the next position change will measure it properly.
        if (bubbleLength === 0) {
            return;
        }
        const offset = Math.min(Math.max(hostCentre - bubbleStart, ARROW_EDGE_INSET_PX), bubbleLength - ARROW_EDGE_INSET_PX);
        this.contentRef?.setInput('arrowOffsetPx', offset);
    }
    addDescribedBy() {
        if (!this.describesHost()) {
            return;
        }
        const host = this.elementRef.nativeElement;
        const tokens = (host.getAttribute('aria-describedby') ?? '').split(' ').filter(Boolean);
        if (!tokens.includes(this.tooltipId)) {
            tokens.push(this.tooltipId);
        }
        host.setAttribute('aria-describedby', tokens.join(' '));
    }
    // Not guarded by describesHost: the filter only ever drops this instance's own id, so removing one that was
    // never added is a no-op, while guarding would strand the id if the input turned off while the tooltip was open.
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
    static ɵfac = function TumUiTooltipDirective_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTooltipDirective)(); };
    static ɵdir = /*@__PURE__*/ i0.ɵɵdefineDirective({ type: TumUiTooltipDirective, selectors: [["", "tumUiTooltip", ""]], hostBindings: function TumUiTooltipDirective_HostBindings(rf, ctx) { if (rf & 1) {
            i0.ɵɵlistener("mouseenter", function TumUiTooltipDirective_mouseenter_HostBindingHandler() { return ctx.onHoverStart(); })("mouseleave", function TumUiTooltipDirective_mouseleave_HostBindingHandler() { return ctx.onHoverEnd(); })("mousedown", function TumUiTooltipDirective_mousedown_HostBindingHandler() { return ctx.onPointerDown(); })("focusin", function TumUiTooltipDirective_focusin_HostBindingHandler() { return ctx.onFocusStart(); })("focusout", function TumUiTooltipDirective_focusout_HostBindingHandler() { return ctx.onFocusEnd(); })("keydown.escape", function TumUiTooltipDirective_keydown_escape_HostBindingHandler() { return ctx.hideNow(); });
        } }, inputs: { content: [1, "tumUiTooltip", "content"], placement: [1, "tumUiTooltipPlacement", "placement"], describesHost: [1, "tumUiTooltipDescribesHost", "describesHost"], showDelayMs: [1, "showDelayMs"], hideDelayMs: [1, "hideDelayMs"] } });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTooltipDirective, [{
        type: Directive,
        args: [{
                selector: '[tumUiTooltip]',
                host: {
                    '(mouseenter)': 'onHoverStart()',
                    '(mouseleave)': 'onHoverEnd()',
                    '(mousedown)': 'onPointerDown()',
                    '(focusin)': 'onFocusStart()',
                    '(focusout)': 'onFocusEnd()',
                    '(keydown.escape)': 'hideNow()',
                },
            }]
    }], () => [], { content: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiTooltip", required: true }] }], placement: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiTooltipPlacement", required: false }] }], describesHost: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiTooltipDescribesHost", required: false }] }], showDelayMs: [{ type: i0.Input, args: [{ isSignal: true, alias: "showDelayMs", required: false }] }], hideDelayMs: [{ type: i0.Input, args: [{ isSignal: true, alias: "hideDelayMs", required: false }] }] }); })();

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

const _c0$p = ["dayButton"];
const _forTrack0$2 = ($index, $item) => $item.valueOf();
function TumUiCalendarComponent_For_12_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "th", 7);
    i0.ɵɵtext(1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const label_r1 = ctx.$implicit;
    const $index_r2 = ctx.$index;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵattribute("aria-label", ctx_r2.weekdayFullLabels()[$index_r2]);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(label_r1);
} }
function TumUiCalendarComponent_For_15_For_2_Template(rf, ctx) { if (rf & 1) {
    const _r4 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "td", 8)(1, "button", 9, 0);
    i0.ɵɵlistener("click", function TumUiCalendarComponent_For_15_For_2_Template_button_click_1_listener() { const day_r5 = i0.ɵɵrestoreView(_r4).$implicit; const ctx_r2 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r2.selectDay(day_r5)); })("keydown", function TumUiCalendarComponent_For_15_For_2_Template_button_keydown_1_listener($event) { const ɵ$index_31_r6 = i0.ɵɵrestoreView(_r4).$index; const ɵ$index_28_r7 = i0.ɵɵnextContext().$index; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onKeydown($event, ɵ$index_28_r7 * 7 + ɵ$index_31_r6)); });
    i0.ɵɵtext(3);
    i0.ɵɵelementEnd()();
} if (rf & 2) {
    const day_r5 = ctx.$implicit;
    const ɵ$index_31_r6 = ctx.$index;
    const ɵ$index_28_r7 = i0.ɵɵnextContext().$index;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵattribute("aria-selected", ctx_r2.isSelected(day_r5) ? "true" : null);
    i0.ɵɵadvance();
    i0.ɵɵclassMap(ctx_r2.dayButtonClasses(day_r5));
    i0.ɵɵattribute("tabindex", ctx_r2.focusedIndex() === ɵ$index_28_r7 * 7 + ɵ$index_31_r6 ? 0 : -1)("aria-label", ctx_r2.dayLabel(day_r5));
    i0.ɵɵadvance(2);
    i0.ɵɵtextInterpolate1(" ", day_r5.date(), " ");
} }
function TumUiCalendarComponent_For_15_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "tr");
    i0.ɵɵrepeaterCreate(1, TumUiCalendarComponent_For_15_For_2_Template, 4, 6, "td", 8, _forTrack0$2);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const week_r8 = ctx.$implicit;
    i0.ɵɵadvance();
    i0.ɵɵrepeater(week_r8);
} }
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
    static ɵfac = function TumUiCalendarComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiCalendarComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiCalendarComponent, selectors: [["tum-ui-calendar"]], viewQuery: function TumUiCalendarComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.dayButtons, _c0$p, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, inputs: { selected: [1, "selected"], activeMonth: [1, "activeMonth"], focusOnInit: [1, "focusOnInit"] }, outputs: { daySelected: "daySelected", monthChange: "monthChange" }, decls: 16, vars: 6, consts: [["dayButton", ""], [1, "tum:w-72", "tum:select-none"], [1, "tum:mb-2", "tum:flex", "tum:items-center", "tum:justify-between"], ["type", "button", 1, "tum:appearance-none", "tum:rounded", "tum:border-0", "tum:bg-transparent", "tum:p-1", "tum:text-text", "tum:hover:bg-hover-background", 3, "click"], [3, "icon"], ["aria-live", "polite", 1, "tum:font-semibold", "tum:text-text"], ["role", "grid", 1, "tum:w-full", "tum:border-collapse", "tum:text-center", "tum:text-sm"], ["scope", "col", 1, "tum:p-1", "tum:font-medium", "tum:text-muted"], ["role", "gridcell", 1, "tum:p-0.5"], ["type", "button", 3, "click", "keydown"]], template: function TumUiCalendarComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵelementStart(0, "div", 1)(1, "div", 2)(2, "button", 3);
            i0.ɵɵlistener("click", function TumUiCalendarComponent_Template_button_click_2_listener() { return ctx.previousMonth(); });
            i0.ɵɵelement(3, "fa-icon", 4);
            i0.ɵɵelementEnd();
            i0.ɵɵelementStart(4, "span", 5);
            i0.ɵɵtext(5);
            i0.ɵɵelementEnd();
            i0.ɵɵelementStart(6, "button", 3);
            i0.ɵɵlistener("click", function TumUiCalendarComponent_Template_button_click_6_listener() { return ctx.nextMonth(); });
            i0.ɵɵelement(7, "fa-icon", 4);
            i0.ɵɵelementEnd()();
            i0.ɵɵelementStart(8, "table", 6)(9, "thead")(10, "tr");
            i0.ɵɵrepeaterCreate(11, TumUiCalendarComponent_For_12_Template, 2, 2, "th", 7, i0.ɵɵrepeaterTrackByIndex);
            i0.ɵɵelementEnd()();
            i0.ɵɵelementStart(13, "tbody");
            i0.ɵɵrepeaterCreate(14, TumUiCalendarComponent_For_15_Template, 3, 0, "tr", null, i0.ɵɵrepeaterTrackByIndex);
            i0.ɵɵelementEnd()()();
        } if (rf & 2) {
            i0.ɵɵadvance(2);
            i0.ɵɵattribute("aria-label", ctx.previousMonthLabel());
            i0.ɵɵadvance();
            i0.ɵɵproperty("icon", ctx.previousMonthIcon());
            i0.ɵɵadvance(2);
            i0.ɵɵtextInterpolate(ctx.monthLabel());
            i0.ɵɵadvance();
            i0.ɵɵattribute("aria-label", ctx.nextMonthLabel());
            i0.ɵɵadvance();
            i0.ɵɵproperty("icon", ctx.nextMonthIcon());
            i0.ɵɵadvance();
            i0.ɵɵattribute("aria-label", ctx.monthLabel());
            i0.ɵɵadvance(3);
            i0.ɵɵrepeater(ctx.weekdayLabels());
            i0.ɵɵadvance(3);
            i0.ɵɵrepeater(ctx.weeks());
        } }, dependencies: [FaIconComponent], encapsulation: 2 });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiCalendarComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-calendar', imports: [FaIconComponent], changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum:w-72 tum:select-none\">\n    <div class=\"tum:mb-2 tum:flex tum:items-center tum:justify-between\">\n        <button\n            type=\"button\"\n            class=\"tum:appearance-none tum:rounded tum:border-0 tum:bg-transparent tum:p-1 tum:text-text tum:hover:bg-hover-background\"\n            (click)=\"previousMonth()\"\n            [attr.aria-label]=\"previousMonthLabel()\"\n        >\n            <fa-icon [icon]=\"previousMonthIcon()\" />\n        </button>\n        <span class=\"tum:font-semibold tum:text-text\" aria-live=\"polite\">{{ monthLabel() }}</span>\n        <button\n            type=\"button\"\n            class=\"tum:appearance-none tum:rounded tum:border-0 tum:bg-transparent tum:p-1 tum:text-text tum:hover:bg-hover-background\"\n            (click)=\"nextMonth()\"\n            [attr.aria-label]=\"nextMonthLabel()\"\n        >\n            <fa-icon [icon]=\"nextMonthIcon()\" />\n        </button>\n    </div>\n    <table role=\"grid\" [attr.aria-label]=\"monthLabel()\" class=\"tum:w-full tum:border-collapse tum:text-center tum:text-sm\">\n        <thead>\n            <tr>\n                @for (label of weekdayLabels(); track $index) {\n                    <th scope=\"col\" [attr.aria-label]=\"weekdayFullLabels()[$index]\" class=\"tum:p-1 tum:font-medium tum:text-muted\">{{ label }}</th>\n                }\n            </tr>\n        </thead>\n        <tbody>\n            @for (week of weeks(); track $index; let w = $index) {\n                <tr>\n                    @for (day of week; track day.valueOf(); let d = $index) {\n                        <td class=\"tum:p-0.5\" role=\"gridcell\" [attr.aria-selected]=\"isSelected(day) ? 'true' : null\">\n                            <button\n                                #dayButton\n                                type=\"button\"\n                                [class]=\"dayButtonClasses(day)\"\n                                [attr.tabindex]=\"focusedIndex() === w * 7 + d ? 0 : -1\"\n                                [attr.aria-label]=\"dayLabel(day)\"\n                                (click)=\"selectDay(day)\"\n                                (keydown)=\"onKeydown($event, w * 7 + d)\"\n                            >\n                                {{ day.date() }}\n                            </button>\n                        </td>\n                    }\n                </tr>\n            }\n        </tbody>\n    </table>\n</div>\n" }]
    }], () => [], { selected: [{ type: i0.Input, args: [{ isSignal: true, alias: "selected", required: false }] }], activeMonth: [{ type: i0.Input, args: [{ isSignal: true, alias: "activeMonth", required: true }] }], focusOnInit: [{ type: i0.Input, args: [{ isSignal: true, alias: "focusOnInit", required: false }] }], daySelected: [{ type: i0.Output, args: ["daySelected"] }], monthChange: [{ type: i0.Output, args: ["monthChange"] }], dayButtons: [{ type: i0.ViewChildren, args: ['dayButton', { isSignal: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiCalendarComponent, { className: "TumUiCalendarComponent", filePath: "lib/date-picker/tum-ui-calendar.component.ts", lineNumber: 29 }); })();

const _c0$o = ["panel"];
const _c1$5 = ["dateInput"];
const _c2$1 = ["triggerWrapper"];
const _c3 = ["hourInput"];
const _c4 = a0 => ({ timeZone: a0 });
function TumUiDatePickerComponent_Conditional_1_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "label", 13);
    i0.ɵɵtext(1);
    i0.ɵɵpipe(2, "tumUiTranslate");
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext(2);
    i0.ɵɵattribute("for", ctx_r1.inputId());
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(i0.ɵɵpipeBind1(2, 2, ctx_r1.labelName()));
} }
function TumUiDatePickerComponent_Conditional_1_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "fa-stack", 14);
    i0.ɵɵpipe(1, "tumUiTranslate");
    i0.ɵɵpipe(2, "tumUiTranslate");
    i0.ɵɵelement(3, "fa-icon", 15)(4, "fa-icon", 16);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext(2);
    i0.ɵɵproperty("tumUiTooltip", i0.ɵɵpipeBind2(1, 4, "tumUi.datePicker.timeZoneWarning", i0.ɵɵpureFunction1(10, _c4, ctx_r1.currentTimeZone)));
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind2(2, 7, "tumUi.datePicker.timeZoneWarning", i0.ɵɵpureFunction1(12, _c4, ctx_r1.currentTimeZone)));
    i0.ɵɵadvance(3);
    i0.ɵɵproperty("icon", ctx_r1.faGlobe);
    i0.ɵɵadvance();
    i0.ɵɵproperty("icon", ctx_r1.faClock);
} }
function TumUiDatePickerComponent_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "div", 6);
    i0.ɵɵconditionalCreate(1, TumUiDatePickerComponent_Conditional_1_Conditional_1_Template, 3, 4, "label", 13);
    i0.ɵɵconditionalCreate(2, TumUiDatePickerComponent_Conditional_1_Conditional_2_Template, 5, 14, "fa-stack", 14);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵconditional(!ctx_r1.hideLabelName() && ctx_r1.labelName() ? 1 : -1);
    i0.ɵɵadvance();
    i0.ɵɵconditional(ctx_r1.shouldDisplayTimeZoneWarning() ? 2 : -1);
} }
function TumUiDatePickerComponent_Conditional_7_Template(rf, ctx) { if (rf & 1) {
    const _r4 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "button", 17);
    i0.ɵɵpipe(1, "tumUiTranslate");
    i0.ɵɵlistener("click", function TumUiDatePickerComponent_Conditional_7_Template_button_click_0_listener() { i0.ɵɵrestoreView(_r4); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.clear()); });
    i0.ɵɵelement(2, "fa-icon", 11);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(1, 2, "tumUi.datePicker.clear"));
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r1.faXmark);
} }
function TumUiDatePickerComponent_Conditional_11_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "span", 12);
    i0.ɵɵtext(1);
    i0.ɵɵpipe(2, "tumUiTranslate");
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵproperty("id", ctx_r1.inputId() + "-error");
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate1(" ", i0.ɵɵpipeBind1(2, 2, ctx_r1.invalidMessageKey()), " ");
} }
function TumUiDatePickerComponent_ng_template_12_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    const _r6 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "tum-ui-calendar", 27);
    i0.ɵɵlistener("daySelected", function TumUiDatePickerComponent_ng_template_12_Conditional_2_Template_tum_ui_calendar_daySelected_0_listener($event) { i0.ɵɵrestoreView(_r6); const ctx_r1 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r1.onDaySelect($event)); })("monthChange", function TumUiDatePickerComponent_ng_template_12_Conditional_2_Template_tum_ui_calendar_monthChange_0_listener($event) { i0.ɵɵrestoreView(_r6); const ctx_r1 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r1.activeMonth.set($event)); });
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext(2);
    i0.ɵɵproperty("selected", ctx_r1.value())("activeMonth", ctx_r1.activeMonth())("focusOnInit", true);
} }
function TumUiDatePickerComponent_ng_template_12_Template(rf, ctx) { if (rf & 1) {
    const _r5 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "div", 18);
    i0.ɵɵpipe(1, "tumUiTranslate");
    i0.ɵɵconditionalCreate(2, TumUiDatePickerComponent_ng_template_12_Conditional_2_Template, 1, 3, "tum-ui-calendar", 19);
    i0.ɵɵelementStart(3, "div", 20);
    i0.ɵɵpipe(4, "tumUiTranslate");
    i0.ɵɵelementStart(5, "div", 21)(6, "button", 22);
    i0.ɵɵpipe(7, "tumUiTranslate");
    i0.ɵɵlistener("click", function TumUiDatePickerComponent_ng_template_12_Template_button_click_6_listener() { i0.ɵɵrestoreView(_r5); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.stepHour(1)); });
    i0.ɵɵelement(8, "fa-icon", 11);
    i0.ɵɵelementEnd();
    i0.ɵɵelementStart(9, "input", 23, 3);
    i0.ɵɵpipe(11, "tumUiTranslate");
    i0.ɵɵlistener("change", function TumUiDatePickerComponent_ng_template_12_Template_input_change_9_listener() { i0.ɵɵrestoreView(_r5); const hourInput_r7 = i0.ɵɵreference(10); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.onHourInput(hourInput_r7)); })("keydown", function TumUiDatePickerComponent_ng_template_12_Template_input_keydown_9_listener($event) { i0.ɵɵrestoreView(_r5); const hourInput_r7 = i0.ɵɵreference(10); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.onTimeKeydown($event, hourInput_r7, "hour")); });
    i0.ɵɵelementEnd();
    i0.ɵɵelementStart(12, "button", 22);
    i0.ɵɵpipe(13, "tumUiTranslate");
    i0.ɵɵlistener("click", function TumUiDatePickerComponent_ng_template_12_Template_button_click_12_listener() { i0.ɵɵrestoreView(_r5); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.stepHour(-1)); });
    i0.ɵɵelement(14, "fa-icon", 11);
    i0.ɵɵelementEnd()();
    i0.ɵɵelementStart(15, "span", 24);
    i0.ɵɵtext(16, ":");
    i0.ɵɵelementEnd();
    i0.ɵɵelementStart(17, "div", 21)(18, "button", 22);
    i0.ɵɵpipe(19, "tumUiTranslate");
    i0.ɵɵlistener("click", function TumUiDatePickerComponent_ng_template_12_Template_button_click_18_listener() { i0.ɵɵrestoreView(_r5); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.stepMinute(1)); });
    i0.ɵɵelement(20, "fa-icon", 11);
    i0.ɵɵelementEnd();
    i0.ɵɵelementStart(21, "input", 23, 4);
    i0.ɵɵpipe(23, "tumUiTranslate");
    i0.ɵɵlistener("change", function TumUiDatePickerComponent_ng_template_12_Template_input_change_21_listener() { i0.ɵɵrestoreView(_r5); const minuteInput_r8 = i0.ɵɵreference(22); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.onMinuteInput(minuteInput_r8)); })("keydown", function TumUiDatePickerComponent_ng_template_12_Template_input_keydown_21_listener($event) { i0.ɵɵrestoreView(_r5); const minuteInput_r8 = i0.ɵɵreference(22); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.onTimeKeydown($event, minuteInput_r8, "minute")); });
    i0.ɵɵelementEnd();
    i0.ɵɵelementStart(24, "button", 22);
    i0.ɵɵpipe(25, "tumUiTranslate");
    i0.ɵɵlistener("click", function TumUiDatePickerComponent_ng_template_12_Template_button_click_24_listener() { i0.ɵɵrestoreView(_r5); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.stepMinute(-1)); });
    i0.ɵɵelement(26, "fa-icon", 11);
    i0.ɵɵelementEnd()()();
    i0.ɵɵelementStart(27, "div", 25)(28, "tum-ui-button", 26);
    i0.ɵɵlistener("clicked", function TumUiDatePickerComponent_ng_template_12_Template_tum_ui_button_clicked_28_listener() { i0.ɵɵrestoreView(_r5); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.close()); });
    i0.ɵɵelementStart(29, "span");
    i0.ɵɵtext(30);
    i0.ɵɵpipe(31, "tumUiTranslate");
    i0.ɵɵelementEnd()()()();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵproperty("id", ctx_r1.panelId());
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(1, 25, ctx_r1.dialogLabelKey()));
    i0.ɵɵadvance(2);
    i0.ɵɵconditional(!ctx_r1.timeOnly() ? 2 : -1);
    i0.ɵɵadvance();
    i0.ɵɵclassMap(ctx_r1.timeOnly() ? "" : "tum:mt-3");
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(4, 27, "tumUi.datePicker.time"));
    i0.ɵɵadvance(3);
    i0.ɵɵproperty("disabled", ctx_r1.disabled());
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(7, 29, "tumUi.datePicker.incrementHour"));
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r1.faChevronUp);
    i0.ɵɵadvance();
    i0.ɵɵproperty("value", ctx_r1.displayHour())("disabled", ctx_r1.disabled());
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(11, 31, "tumUi.datePicker.hour"));
    i0.ɵɵadvance(3);
    i0.ɵɵproperty("disabled", ctx_r1.disabled());
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(13, 33, "tumUi.datePicker.decrementHour"));
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r1.faChevronDown);
    i0.ɵɵadvance(4);
    i0.ɵɵproperty("disabled", ctx_r1.disabled());
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(19, 35, "tumUi.datePicker.incrementMinute"));
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r1.faChevronUp);
    i0.ɵɵadvance();
    i0.ɵɵproperty("value", ctx_r1.displayMinute())("disabled", ctx_r1.disabled());
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(23, 37, "tumUi.datePicker.minute"));
    i0.ɵɵadvance(3);
    i0.ɵɵproperty("disabled", ctx_r1.disabled());
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(25, 39, "tumUi.datePicker.decrementMinute"));
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r1.faChevronDown);
    i0.ɵɵadvance(4);
    i0.ɵɵtextInterpolate(i0.ɵɵpipeBind1(31, 41, "tumUi.datePicker.done"));
} }
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
    static ɵfac = function TumUiDatePickerComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiDatePickerComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiDatePickerComponent, selectors: [["tum-ui-date-picker"]], viewQuery: function TumUiDatePickerComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.panel, _c0$o, 5, TemplateRef)(ctx.dateInput, _c1$5, 5)(ctx.triggerWrapper, _c2$1, 5)(ctx.hourField, _c3, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance(4);
        } }, hostAttrs: [1, "tum-ui-date-picker"], inputs: { value: [1, "value"], invalid: [1, "invalid"], disabled: [1, "disabled"], hideLabelName: [1, "hideLabelName"], hideValidationMessage: [1, "hideValidationMessage"], shouldDisplayTimeZoneWarning: [1, "shouldDisplayTimeZoneWarning"], timeOnly: [1, "timeOnly"], inputId: [1, "inputId"], labelName: [1, "labelName"], ariaLabel: [1, "ariaLabel"] }, outputs: { value: "valueChange", inputValidityChange: "inputValidityChange", touch: "touch" }, decls: 14, vars: 23, consts: [["triggerWrapper", ""], ["dateInput", ""], ["panel", ""], ["hourInput", ""], ["minuteInput", ""], [1, "tum:flex", "tum:flex-col", "tum:gap-1"], [1, "tum:flex", "tum:items-center", "tum:gap-1"], [1, "tum:relative", "tum:flex", "tum:items-center"], ["type", "text", "role", "combobox", "aria-haspopup", "dialog", 1, "tum-ui-date-picker-input", "tum:box-border", "tum:w-full", "tum:rounded-md", "tum:border", "tum:bg-control-background", "tum:py-2", "tum:ps-3", "tum:pe-17", "tum:text-text", "tum:focus-visible:outline", "tum:focus-visible:outline-2", "tum:focus-visible:outline-focus", "tum:focus-visible:outline-offset-2", 3, "input", "blur", "keydown.arrowdown", "id", "value", "disabled", "placeholder"], ["type", "button", 1, "tum:absolute", "tum:end-9", "tum:flex", "tum:h-6", "tum:w-6", "tum:appearance-none", "tum:items-center", "tum:justify-center", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:text-muted", "tum:hover:text-text-hover"], ["type", "button", "tabindex", "-1", "aria-haspopup", "dialog", 1, "tum:absolute", "tum:end-2", "tum:flex", "tum:h-6", "tum:w-6", "tum:appearance-none", "tum:items-center", "tum:justify-center", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:text-muted", "tum:hover:text-accent", 3, "click", "disabled"], [3, "icon"], ["role", "alert", 1, "tum:text-sm", "tum:text-state-danger", 3, "id"], [1, "tum:font-semibold", "tum:text-text"], ["tabindex", "0", "role", "img", 1, "tum:h-4", "tum:w-4", 3, "tumUiTooltip"], ["stackItemSize", "1x", 1, "tum:text-muted", 3, "icon"], ["stackItemSize", "1x", "transform", "shrink-6 down-5 right-5", 1, "tum:text-muted", 3, "icon"], ["type", "button", 1, "tum:absolute", "tum:end-9", "tum:flex", "tum:h-6", "tum:w-6", "tum:appearance-none", "tum:items-center", "tum:justify-center", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:text-muted", "tum:hover:text-text-hover", 3, "click"], ["role", "dialog", "aria-modal", "true", "cdkTrapFocus", "", 1, "tum:rounded-md", "tum:border", "tum:border-border", "tum:bg-overlay-background", "tum:p-3", "tum:shadow-lg", 3, "id"], [3, "selected", "activeMonth", "focusOnInit"], ["role", "group", 1, "tum:flex", "tum:items-center", "tum:justify-center", "tum:gap-2"], [1, "tum:flex", "tum:flex-col", "tum:items-center"], ["type", "button", 1, "tum:flex", "tum:h-6", "tum:w-6", "tum:appearance-none", "tum:items-center", "tum:justify-center", "tum:border-0", "tum:bg-transparent", "tum:p-1", "tum:text-muted", "tum:hover:text-accent", 3, "click", "disabled"], ["type", "text", "inputmode", "numeric", "maxlength", "2", 1, "tum:box-border", "tum:w-9", "tum:appearance-none", "tum:rounded-md", "tum:border", "tum:border-control-border", "tum:bg-control-background", "tum:py-1", "tum:text-center", "tum:text-text", "tum:focus-visible:outline", "tum:focus-visible:outline-2", "tum:focus-visible:outline-focus", "tum:focus-visible:outline-offset-2", 3, "change", "keydown", "value", "disabled"], [1, "tum:pb-0.5", "tum:font-semibold", "tum:text-text"], [1, "tum:mt-3", "tum:flex", "tum:justify-end"], ["severity", "secondary", "variant", "text", "size", "small", 3, "clicked"], [3, "daySelected", "monthChange", "selected", "activeMonth", "focusOnInit"]], template: function TumUiDatePickerComponent_Template(rf, ctx) { if (rf & 1) {
            const _r1 = i0.ɵɵgetCurrentView();
            i0.ɵɵelementStart(0, "div", 5);
            i0.ɵɵconditionalCreate(1, TumUiDatePickerComponent_Conditional_1_Template, 3, 2, "div", 6);
            i0.ɵɵelementStart(2, "div", 7, 0)(4, "input", 8, 1);
            i0.ɵɵpipe(6, "tumUiTranslate");
            i0.ɵɵlistener("input", function TumUiDatePickerComponent_Template_input_input_4_listener() { i0.ɵɵrestoreView(_r1); const dateInput_r3 = i0.ɵɵreference(5); return i0.ɵɵresetView(ctx.onInput(dateInput_r3.value)); })("blur", function TumUiDatePickerComponent_Template_input_blur_4_listener() { i0.ɵɵrestoreView(_r1); const dateInput_r3 = i0.ɵɵreference(5); return i0.ɵɵresetView(ctx.onBlur(dateInput_r3.value)); })("keydown.arrowdown", function TumUiDatePickerComponent_Template_input_keydown_arrowdown_4_listener($event) { return ctx.openFromInput($event); });
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(7, TumUiDatePickerComponent_Conditional_7_Template, 3, 4, "button", 9);
            i0.ɵɵelementStart(8, "button", 10);
            i0.ɵɵpipe(9, "tumUiTranslate");
            i0.ɵɵlistener("click", function TumUiDatePickerComponent_Template_button_click_8_listener() { return ctx.toggle(); });
            i0.ɵɵelement(10, "fa-icon", 11);
            i0.ɵɵelementEnd()();
            i0.ɵɵconditionalCreate(11, TumUiDatePickerComponent_Conditional_11_Template, 3, 4, "span", 12);
            i0.ɵɵelementEnd();
            i0.ɵɵtemplate(12, TumUiDatePickerComponent_ng_template_12_Template, 32, 43, "ng-template", null, 2, i0.ɵɵtemplateRefExtractor);
        } if (rf & 2) {
            i0.ɵɵadvance();
            i0.ɵɵconditional(!ctx.hideLabelName() && ctx.labelName() || ctx.shouldDisplayTimeZoneWarning() ? 1 : -1);
            i0.ɵɵadvance(3);
            i0.ɵɵclassMap(ctx.showErrorBorder() ? "tum:border-state-danger" : "tum:border-control-border");
            i0.ɵɵproperty("id", ctx.inputId())("value", ctx.inputText())("disabled", ctx.disabled())("placeholder", i0.ɵɵpipeBind1(6, 19, ctx.placeholderKey()));
            i0.ɵɵattribute("aria-expanded", ctx.isOpen())("aria-controls", ctx.isOpen() ? ctx.panelId() : null)("aria-invalid", ctx.showErrorBorder())("aria-label", ctx.ariaLabel())("aria-describedby", !ctx.hideValidationMessage() && ctx.showErrorBorder() ? ctx.inputId() + "-error" : null);
            i0.ɵɵadvance(3);
            i0.ɵɵconditional(ctx.showClear() && !ctx.disabled() ? 7 : -1);
            i0.ɵɵadvance();
            i0.ɵɵproperty("disabled", ctx.disabled());
            i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(9, 21, ctx.openLabelKey()))("aria-expanded", ctx.isOpen() ? "true" : "false")("aria-controls", ctx.isOpen() ? ctx.panelId() : null);
            i0.ɵɵadvance(2);
            i0.ɵɵproperty("icon", ctx.timeOnly() ? ctx.faClock : ctx.faCalendar);
            i0.ɵɵadvance();
            i0.ɵɵconditional(!ctx.hideValidationMessage() && ctx.showErrorBorder() ? 11 : -1);
        } }, dependencies: [A11yModule, i1.CdkTrapFocus, FaIconComponent, FaStackComponent, FaStackItemSizeDirective, TumUiButtonComponent, TumUiCalendarComponent, TumUiTooltipDirective, TumUiTranslatePipe], styles: ["[_nghost-%COMP%]{display:block}.tum-ui-date-picker-input[_ngcontent-%COMP%]{border-start-start-radius:var(--%NS%tum-ui-input-group-start-radius, var(--%NS%tumaet-ui-radius-md));border-end-start-radius:var(--%NS%tum-ui-input-group-start-radius, var(--%NS%tumaet-ui-radius-md));border-start-end-radius:var(--%NS%tum-ui-input-group-end-radius, var(--%NS%tumaet-ui-radius-md));border-end-end-radius:var(--%NS%tum-ui-input-group-end-radius, var(--%NS%tumaet-ui-radius-md))}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiDatePickerComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-date-picker', host: {
                    // The application stylesheet excludes TUM UI controls from the JHipster validity accent by this class.
                    class: 'tum-ui-date-picker',
                }, imports: [A11yModule, FaIconComponent, FaStackComponent, FaStackItemSizeDirective, TumUiButtonComponent, TumUiCalendarComponent, TumUiTooltipDirective, TumUiTranslatePipe], changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum:flex tum:flex-col tum:gap-1\">\n    @if ((!hideLabelName() && labelName()) || shouldDisplayTimeZoneWarning()) {\n        <div class=\"tum:flex tum:items-center tum:gap-1\">\n            @if (!hideLabelName() && labelName()) {\n                <label [attr.for]=\"inputId()\" class=\"tum:font-semibold tum:text-text\">{{ labelName()! | tumUiTranslate }}</label>\n            }\n            @if (shouldDisplayTimeZoneWarning()) {\n                <fa-stack\n                    class=\"tum:h-4 tum:w-4\"\n                    tabindex=\"0\"\n                    role=\"img\"\n                    [attr.aria-label]=\"'tumUi.datePicker.timeZoneWarning' | tumUiTranslate: { timeZone: currentTimeZone }\"\n                    [tumUiTooltip]=\"'tumUi.datePicker.timeZoneWarning' | tumUiTranslate: { timeZone: currentTimeZone }\"\n                >\n                    <fa-icon [icon]=\"faGlobe\" stackItemSize=\"1x\" class=\"tum:text-muted\" />\n                    <fa-icon [icon]=\"faClock\" stackItemSize=\"1x\" transform=\"shrink-6 down-5 right-5\" class=\"tum:text-muted\" />\n                </fa-stack>\n            }\n        </div>\n    }\n\n    <div #triggerWrapper class=\"tum:relative tum:flex tum:items-center\">\n        <input\n            #dateInput\n            [id]=\"inputId()\"\n            type=\"text\"\n            role=\"combobox\"\n            [value]=\"inputText()\"\n            [disabled]=\"disabled()\"\n            [placeholder]=\"placeholderKey() | tumUiTranslate\"\n            aria-haspopup=\"dialog\"\n            [attr.aria-expanded]=\"isOpen()\"\n            [attr.aria-controls]=\"isOpen() ? panelId() : null\"\n            [attr.aria-invalid]=\"showErrorBorder()\"\n            [attr.aria-label]=\"ariaLabel()\"\n            [attr.aria-describedby]=\"!hideValidationMessage() && showErrorBorder() ? inputId() + '-error' : null\"\n            (input)=\"onInput(dateInput.value)\"\n            (blur)=\"onBlur(dateInput.value)\"\n            (keydown.arrowdown)=\"openFromInput($event)\"\n            class=\"tum-ui-date-picker-input tum:box-border tum:w-full tum:rounded-md tum:border tum:bg-control-background tum:py-2 tum:ps-3 tum:pe-17 tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n            [class]=\"showErrorBorder() ? 'tum:border-state-danger' : 'tum:border-control-border'\"\n        />\n        @if (showClear() && !disabled()) {\n            <button\n                type=\"button\"\n                class=\"tum:absolute tum:end-9 tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text-hover\"\n                (click)=\"clear()\"\n                [attr.aria-label]=\"'tumUi.datePicker.clear' | tumUiTranslate\"\n            >\n                <fa-icon [icon]=\"faXmark\" />\n            </button>\n        }\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            class=\"tum:absolute tum:end-2 tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-accent\"\n            [disabled]=\"disabled()\"\n            (click)=\"toggle()\"\n            [attr.aria-label]=\"openLabelKey() | tumUiTranslate\"\n            aria-haspopup=\"dialog\"\n            [attr.aria-expanded]=\"isOpen() ? 'true' : 'false'\"\n            [attr.aria-controls]=\"isOpen() ? panelId() : null\"\n        >\n            <fa-icon [icon]=\"timeOnly() ? faClock : faCalendar\" />\n        </button>\n    </div>\n\n    @if (!hideValidationMessage() && showErrorBorder()) {\n        <span [id]=\"inputId() + '-error'\" role=\"alert\" class=\"tum:text-sm tum:text-state-danger\">\n            {{ invalidMessageKey() | tumUiTranslate }}\n        </span>\n    }\n</div>\n\n<ng-template #panel>\n    <div\n        [id]=\"panelId()\"\n        class=\"tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:p-3 tum:shadow-lg\"\n        role=\"dialog\"\n        aria-modal=\"true\"\n        [attr.aria-label]=\"dialogLabelKey() | tumUiTranslate\"\n        cdkTrapFocus\n    >\n        @if (!timeOnly()) {\n            <tum-ui-calendar [selected]=\"value()\" [activeMonth]=\"activeMonth()\" [focusOnInit]=\"true\" (daySelected)=\"onDaySelect($event)\" (monthChange)=\"activeMonth.set($event)\" />\n        }\n        <div\n            class=\"tum:flex tum:items-center tum:justify-center tum:gap-2\"\n            [class]=\"timeOnly() ? '' : 'tum:mt-3'\"\n            role=\"group\"\n            [attr.aria-label]=\"'tumUi.datePicker.time' | tumUiTranslate\"\n        >\n            <div class=\"tum:flex tum:flex-col tum:items-center\">\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepHour(1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.incrementHour' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronUp\" />\n                </button>\n                <input\n                    #hourInput\n                    type=\"text\"\n                    inputmode=\"numeric\"\n                    maxlength=\"2\"\n                    [value]=\"displayHour()\"\n                    [disabled]=\"disabled()\"\n                    (change)=\"onHourInput(hourInput)\"\n                    (keydown)=\"onTimeKeydown($event, hourInput, 'hour')\"\n                    class=\"tum:box-border tum:w-9 tum:appearance-none tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1 tum:text-center tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                    [attr.aria-label]=\"'tumUi.datePicker.hour' | tumUiTranslate\"\n                />\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepHour(-1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.decrementHour' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronDown\" />\n                </button>\n            </div>\n            <span class=\"tum:pb-0.5 tum:font-semibold tum:text-text\">:</span>\n            <div class=\"tum:flex tum:flex-col tum:items-center\">\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepMinute(1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.incrementMinute' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronUp\" />\n                </button>\n                <input\n                    #minuteInput\n                    type=\"text\"\n                    inputmode=\"numeric\"\n                    maxlength=\"2\"\n                    [value]=\"displayMinute()\"\n                    [disabled]=\"disabled()\"\n                    (change)=\"onMinuteInput(minuteInput)\"\n                    (keydown)=\"onTimeKeydown($event, minuteInput, 'minute')\"\n                    class=\"tum:box-border tum:w-9 tum:appearance-none tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1 tum:text-center tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                    [attr.aria-label]=\"'tumUi.datePicker.minute' | tumUiTranslate\"\n                />\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepMinute(-1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.decrementMinute' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronDown\" />\n                </button>\n            </div>\n        </div>\n        <div class=\"tum:mt-3 tum:flex tum:justify-end\">\n            <tum-ui-button severity=\"secondary\" variant=\"text\" size=\"small\" (clicked)=\"close()\">\n                <span>{{ 'tumUi.datePicker.done' | tumUiTranslate }}</span>\n            </tum-ui-button>\n        </div>\n    </div>\n</ng-template>\n", styles: [":host{display:block}.tum-ui-date-picker-input{border-start-start-radius:var(--tum-ui-input-group-start-radius, var(--tumaet-ui-radius-md));border-end-start-radius:var(--tum-ui-input-group-start-radius, var(--tumaet-ui-radius-md));border-start-end-radius:var(--tum-ui-input-group-end-radius, var(--tumaet-ui-radius-md));border-end-end-radius:var(--tum-ui-input-group-end-radius, var(--tumaet-ui-radius-md))}\n"] }]
    }], () => [], { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }, { type: i0.Output, args: ["valueChange"] }], invalid: [{ type: i0.Input, args: [{ isSignal: true, alias: "invalid", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], hideLabelName: [{ type: i0.Input, args: [{ isSignal: true, alias: "hideLabelName", required: false }] }], hideValidationMessage: [{ type: i0.Input, args: [{ isSignal: true, alias: "hideValidationMessage", required: false }] }], shouldDisplayTimeZoneWarning: [{ type: i0.Input, args: [{ isSignal: true, alias: "shouldDisplayTimeZoneWarning", required: false }] }], timeOnly: [{ type: i0.Input, args: [{ isSignal: true, alias: "timeOnly", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], labelName: [{ type: i0.Input, args: [{ isSignal: true, alias: "labelName", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], inputValidityChange: [{ type: i0.Output, args: ["inputValidityChange"] }], touch: [{ type: i0.Output, args: ["touch"] }], panel: [{ type: i0.ViewChild, args: ['panel', { ...{ read: TemplateRef }, isSignal: true }] }], dateInput: [{ type: i0.ViewChild, args: ['dateInput', { isSignal: true }] }], triggerWrapper: [{ type: i0.ViewChild, args: ['triggerWrapper', { isSignal: true }] }], hourField: [{ type: i0.ViewChild, args: ['hourInput', { isSignal: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiDatePickerComponent, { className: "TumUiDatePickerComponent", filePath: "lib/date-picker/tum-ui-date-picker.component.ts", lineNumber: 49 }); })();

const TUM_UI_FORM_FIELD = new InjectionToken('TumUiFormField');

const _c0$n = [[["", "tumUiFormFieldLabel", ""]], "*", [["", "tumUiFormFieldError", ""]]];
const _c1$4 = ["[tumUiFormFieldLabel]", "*", "[tumUiFormFieldError]"];
function TumUiFormFieldComponent_Conditional_3_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "span", 1);
    i0.ɵɵtext(1, "*");
    i0.ɵɵdomElementEnd();
} }
function TumUiFormFieldComponent_Conditional_6_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdomElementStart(0, "span", 3);
    i0.ɵɵtext(1);
    i0.ɵɵdomElementEnd();
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵdomProperty("id", ctx_r0.hintId);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(ctx_r0.hint());
} }
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
    static ɵfac = function TumUiFormFieldComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiFormFieldComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiFormFieldComponent, selectors: [["tum-ui-form-field"]], hostAttrs: [1, "tum-ui-form-field"], inputs: { label: [1, "label"], controlId: [1, "controlId"], required: [1, "required"], hint: [1, "hint"], invalid: [1, "invalid"], error: [1, "error"] }, features: [i0.ɵɵProvidersFeature([{ provide: TUM_UI_FORM_FIELD, useExisting: TumUiFormFieldComponent }])], ngContentSelectors: _c1$4, decls: 10, vars: 7, consts: [[1, "tum-ui-form-field-label", "tum:flex", "tum:items-center", "tum:gap-1", "tum:text-sm", "tum:font-medium", "tum:text-text", 3, "for"], ["aria-hidden", "true", 1, "tum-ui-form-field-required", "tum:text-state-danger"], [1, "tum-ui-form-field-control"], [1, "tum-ui-form-field-hint", "tum:text-sm", "tum:text-muted", 3, "id"], ["role", "alert", 1, "tum-ui-form-field-error", "tum:text-sm", "tum:text-state-danger", 3, "id", "hidden"]], template: function TumUiFormFieldComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef(_c0$n);
            i0.ɵɵdomElementStart(0, "label", 0);
            i0.ɵɵtext(1);
            i0.ɵɵprojection(2);
            i0.ɵɵconditionalCreate(3, TumUiFormFieldComponent_Conditional_3_Template, 2, 0, "span", 1);
            i0.ɵɵdomElementEnd();
            i0.ɵɵdomElementStart(4, "div", 2);
            i0.ɵɵprojection(5, 1);
            i0.ɵɵdomElementEnd();
            i0.ɵɵconditionalCreate(6, TumUiFormFieldComponent_Conditional_6_Template, 2, 2, "span", 3);
            i0.ɵɵdomElementStart(7, "div", 4);
            i0.ɵɵtext(8);
            i0.ɵɵprojection(9, 2);
            i0.ɵɵdomElementEnd();
        } if (rf & 2) {
            i0.ɵɵdomProperty("htmlFor", ctx.labelTargetId());
            i0.ɵɵadvance();
            i0.ɵɵtextInterpolate1(" ", ctx.label());
            i0.ɵɵadvance(2);
            i0.ɵɵconditional(ctx.required() ? 3 : -1);
            i0.ɵɵadvance(3);
            i0.ɵɵconditional(ctx.showHint() ? 6 : -1);
            i0.ɵɵadvance();
            i0.ɵɵdomProperty("id", ctx.errorId)("hidden", !ctx.invalid());
            i0.ɵɵadvance();
            i0.ɵɵtextInterpolate1(" ", ctx.error());
        } }, styles: ["[_nghost-%COMP%]{display:flex;flex-direction:column;gap:calc(var(--%NS%tumaet-ui-spacing) * 1.5)}.tum-ui-form-field-label[_ngcontent-%COMP%]{min-width:0}.tum-ui-form-field-control[_ngcontent-%COMP%]{display:flex;flex-direction:column;min-width:0}[hidden][_ngcontent-%COMP%]{display:none}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiFormFieldComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-form-field', host: {
                    class: 'tum-ui-form-field',
                }, providers: [{ provide: TUM_UI_FORM_FIELD, useExisting: TumUiFormFieldComponent }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<label class=\"tum-ui-form-field-label tum:flex tum:items-center tum:gap-1 tum:text-sm tum:font-medium tum:text-text\" [for]=\"labelTargetId()\">\n    {{ label() }}<ng-content select=\"[tumUiFormFieldLabel]\" />\n    @if (required()) {\n        <span class=\"tum-ui-form-field-required tum:text-state-danger\" aria-hidden=\"true\">*</span>\n    }\n</label>\n<div class=\"tum-ui-form-field-control\">\n    <ng-content />\n</div>\n@if (showHint()) {\n    <span [id]=\"hintId\" class=\"tum-ui-form-field-hint tum:text-sm tum:text-muted\">{{ hint() }}</span>\n}\n<div [id]=\"errorId\" class=\"tum-ui-form-field-error tum:text-sm tum:text-state-danger\" role=\"alert\" [hidden]=\"!invalid()\">\n    {{ error() }}<ng-content select=\"[tumUiFormFieldError]\" />\n</div>\n", styles: [":host{display:flex;flex-direction:column;gap:calc(var(--tumaet-ui-spacing) * 1.5)}.tum-ui-form-field-label{min-width:0}.tum-ui-form-field-control{display:flex;flex-direction:column;min-width:0}[hidden]{display:none}\n"] }]
    }], null, { label: [{ type: i0.Input, args: [{ isSignal: true, alias: "label", required: false }] }], controlId: [{ type: i0.Input, args: [{ isSignal: true, alias: "controlId", required: false }] }], required: [{ type: i0.Input, args: [{ isSignal: true, alias: "required", required: false }] }], hint: [{ type: i0.Input, args: [{ isSignal: true, alias: "hint", required: false }] }], invalid: [{ type: i0.Input, args: [{ isSignal: true, alias: "invalid", required: false }] }], error: [{ type: i0.Input, args: [{ isSignal: true, alias: "error", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiFormFieldComponent, { className: "TumUiFormFieldComponent", filePath: "lib/form-field/tum-ui-form-field.component.ts", lineNumber: 38 }); })();

const _c0$m = ["*"];
function TumUiIconFieldComponent_Conditional_0_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "fa-icon", 0);
} if (rf & 2) {
    i0.ɵɵproperty("icon", ctx);
} }
class TumUiIconFieldComponent {
    icon = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "icon" }] : /* istanbul ignore next */ []));
    iconPosition = input('left', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "iconPosition" }] : /* istanbul ignore next */ []));
    static ɵfac = function TumUiIconFieldComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiIconFieldComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiIconFieldComponent, selectors: [["tum-ui-icon-field"]], hostAttrs: [1, "tum-ui-icon-field"], hostVars: 2, hostBindings: function TumUiIconFieldComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("data-position", ctx.iconPosition())("data-has-icon", ctx.icon() ? "" : null);
        } }, inputs: { icon: [1, "icon"], iconPosition: [1, "iconPosition"] }, ngContentSelectors: _c0$m, decls: 2, vars: 1, consts: [["aria-hidden", "true", 1, "tum-ui-input-icon", "tum:text-muted", 3, "icon"]], template: function TumUiIconFieldComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵconditionalCreate(0, TumUiIconFieldComponent_Conditional_0_Template, 1, 1, "fa-icon", 0);
            i0.ɵɵprojection(1);
        } if (rf & 2) {
            let tmp_0_0;
            i0.ɵɵconditional((tmp_0_0 = ctx.icon()) ? 0 : -1, tmp_0_0);
        } }, dependencies: [FaIconComponent], styles: ["[_nghost-%COMP%]{position:relative;display:block}.tum-ui-input-icon[_ngcontent-%COMP%]{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}[data-position=right][_nghost-%COMP%]   .tum-ui-input-icon[_ngcontent-%COMP%]{inset-inline-end:calc(var(--%NS%tumaet-ui-spacing) * 3)}[_nghost-%COMP%]:not([data-position=right])   .tum-ui-input-icon[_ngcontent-%COMP%]{inset-inline-start:calc(var(--%NS%tumaet-ui-spacing) * 3)}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiIconFieldComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-icon-field', imports: [FaIconComponent], host: {
                    class: 'tum-ui-icon-field',
                    '[attr.data-position]': 'iconPosition()',
                    '[attr.data-has-icon]': 'icon() ? "" : null',
                }, changeDetection: ChangeDetectionStrategy.OnPush, template: "@if (icon(); as fieldIcon) {\n    <fa-icon [icon]=\"fieldIcon\" class=\"tum-ui-input-icon tum:text-muted\" aria-hidden=\"true\" />\n}\n<ng-content />\n", styles: [":host{position:relative;display:block}.tum-ui-input-icon{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}:host[data-position=right] .tum-ui-input-icon{inset-inline-end:calc(var(--tumaet-ui-spacing) * 3)}:host:not([data-position=right]) .tum-ui-input-icon{inset-inline-start:calc(var(--tumaet-ui-spacing) * 3)}\n"] }]
    }], null, { icon: [{ type: i0.Input, args: [{ isSignal: true, alias: "icon", required: false }] }], iconPosition: [{ type: i0.Input, args: [{ isSignal: true, alias: "iconPosition", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiIconFieldComponent, { className: "TumUiIconFieldComponent", filePath: "lib/icon-field/tum-ui-icon-field.component.ts", lineNumber: 19 }); })();

const _c0$l = ["*"];
class TumUiInputGroupAddonComponent {
    static ɵfac = function TumUiInputGroupAddonComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiInputGroupAddonComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiInputGroupAddonComponent, selectors: [["tum-ui-input-group-addon"]], hostAttrs: [1, "tum-ui-input-group-addon", "tum:bg-control-background", "tum:text-muted", "tum:border-y", "tum:first:border-s", "tum:first:rounded-s-md", "tum:last:border-e", "tum:last:rounded-e-md"], ngContentSelectors: _c0$l, decls: 1, vars: 0, template: function TumUiInputGroupAddonComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵprojection(0);
        } }, styles: ["[_nghost-%COMP%]{display:flex;align-items:center;justify-content:center;padding:calc(var(--%NS%tumaet-ui-spacing) * 2);min-width:calc(var(--%NS%tumaet-ui-spacing) * 10);white-space:nowrap;border-color:var(--%NS%tum-ui-input-group-border-color, var(--%NS%tumaet-ui-control-border-color));transition:border-color .2s}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiInputGroupAddonComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-input-group-addon', template: '<ng-content />', host: {
                    class: 'tum-ui-input-group-addon tum:bg-control-background tum:text-muted tum:border-y ' + 'tum:first:border-s tum:first:rounded-s-md tum:last:border-e tum:last:rounded-e-md',
                }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{display:flex;align-items:center;justify-content:center;padding:calc(var(--tumaet-ui-spacing) * 2);min-width:calc(var(--tumaet-ui-spacing) * 10);white-space:nowrap;border-color:var(--tum-ui-input-group-border-color, var(--tumaet-ui-control-border-color));transition:border-color .2s}\n"] }]
    }], null, null); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiInputGroupAddonComponent, { className: "TumUiInputGroupAddonComponent", filePath: "lib/input-group/tum-ui-input-group-addon.component.ts", lineNumber: 13 }); })();

const _c0$k = ["*"];
class TumUiInputGroupComponent {
    static ɵfac = function TumUiInputGroupComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiInputGroupComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiInputGroupComponent, selectors: [["tum-ui-input-group"]], hostAttrs: [1, "tum-ui-input-group"], ngContentSelectors: _c0$k, decls: 1, vars: 0, template: function TumUiInputGroupComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵprojection(0);
        } }, styles: ["[_nghost-%COMP%]{display:flex;align-items:stretch;--%NS%tum-ui-input-group-border-color: var(--%NS%tumaet-ui-control-border-color)}[_nghost-%COMP%]:has(:is(input,select,textarea):enabled:hover){--%NS%tum-ui-input-group-border-color: var(--%NS%tumaet-ui-control-border-hover-color)}[_nghost-%COMP%]:has(:is(input,select,textarea):enabled:focus){--%NS%tum-ui-input-group-border-color: var(--%NS%tumaet-ui-focus-color)}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiInputGroupComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-input-group', template: '<ng-content />', host: {
                    class: 'tum-ui-input-group',
                }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{display:flex;align-items:stretch;--tum-ui-input-group-border-color: var(--tumaet-ui-control-border-color)}:host:has(:is(input,select,textarea):enabled:hover){--tum-ui-input-group-border-color: var(--tumaet-ui-control-border-hover-color)}:host:has(:is(input,select,textarea):enabled:focus){--tum-ui-input-group-border-color: var(--tumaet-ui-focus-color)}\n"] }]
    }], null, null); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiInputGroupComponent, { className: "TumUiInputGroupComponent", filePath: "lib/input-group/tum-ui-input-group.component.ts", lineNumber: 12 }); })();

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
    static ɵfac = function TumUiInputDirective_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiInputDirective)(); };
    static ɵdir = /*@__PURE__*/ i0.ɵɵdefineDirective({ type: TumUiInputDirective, selectors: [["input", "tumUiInput", ""], ["textarea", "tumUiInput", ""], ["textarea", "tumUiTextarea", ""]], hostVars: 5, hostBindings: function TumUiInputDirective_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("id", ctx.controlId())("aria-describedby", ctx.describedBy())("aria-invalid", ctx.isInvalid() || null);
            i0.ɵɵclassMap(ctx.hostClasses());
        } }, inputs: { tumUiInputSize: [1, "tumUiInputSize"], tumUiInputInvalid: [1, "tumUiInputInvalid"], tumUiInputId: [1, "tumUiInputId"], tumUiInputDescribedBy: [1, "tumUiInputDescribedBy"] } });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiInputDirective, [{
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
    }], () => [], { tumUiInputSize: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiInputSize", required: false }] }], tumUiInputInvalid: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiInputInvalid", required: false }] }], tumUiInputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiInputId", required: false }] }], tumUiInputDescribedBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiInputDescribedBy", required: false }] }] }); })();

const _c0$j = ["inputEl"];
function TumUiInputNumberComponent_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "span", 2)(1, "button", 3);
    i0.ɵɵlistener("mousedown", function TumUiInputNumberComponent_Conditional_2_Template_button_mousedown_1_listener($event) { return $event.preventDefault(); })("click", function TumUiInputNumberComponent_Conditional_2_Template_button_click_1_listener() { i0.ɵɵrestoreView(_r1); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.onStep(ctx_r1.step())); });
    i0.ɵɵelement(2, "fa-icon", 4);
    i0.ɵɵelementEnd();
    i0.ɵɵelementStart(3, "button", 5);
    i0.ɵɵlistener("mousedown", function TumUiInputNumberComponent_Conditional_2_Template_button_mousedown_3_listener($event) { return $event.preventDefault(); })("click", function TumUiInputNumberComponent_Conditional_2_Template_button_click_3_listener() { i0.ɵɵrestoreView(_r1); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.onStep(-ctx_r1.step())); });
    i0.ɵɵelement(4, "fa-icon", 4);
    i0.ɵɵelementEnd()();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵproperty("disabled", ctx_r1.isDisabled());
    i0.ɵɵadvance();
    i0.ɵɵproperty("icon", ctx_r1.faChevronUp);
    i0.ɵɵadvance();
    i0.ɵɵproperty("disabled", ctx_r1.isDisabled());
    i0.ɵɵadvance();
    i0.ɵɵproperty("icon", ctx_r1.faChevronDown);
} }
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
    static ɵfac = function TumUiInputNumberComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiInputNumberComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiInputNumberComponent, selectors: [["tum-ui-input-number"]], viewQuery: function TumUiInputNumberComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.inputRef, _c0$j, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, hostAttrs: [1, "tum-ui-input-number"], hostVars: 4, hostBindings: function TumUiInputNumberComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵclassProp("tum-ui-input-number-fluid", ctx.fluid())("tum-ui-input-number-buttons", ctx.showButtons());
        } }, inputs: { min: [1, "min"], max: [1, "max"], step: [1, "step"], showButtons: [1, "showButtons"], prefix: [1, "prefix"], suffix: [1, "suffix"], placeholder: [1, "placeholder"], disabled: [1, "disabled"], invalid: [1, "invalid"], fluid: [1, "fluid"], useGrouping: [1, "useGrouping"], maxFractionDigits: [1, "maxFractionDigits"], locale: [1, "locale"], inputId: [1, "inputId"], name: [1, "name"], ariaLabel: [1, "ariaLabel"], ariaLabelledBy: [1, "ariaLabelledBy"], ariaDescribedBy: [1, "ariaDescribedBy"] }, features: [i0.ɵɵProvidersFeature([{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiInputNumberComponent), multi: true }])], decls: 3, vars: 15, consts: [["inputEl", ""], ["tumUiInput", "", "type", "text", "role", "spinbutton", 1, "tum-ui-input-number-input", 3, "input", "blur", "keydown", "value", "tumUiInputId", "tumUiInputDescribedBy", "disabled", "tumUiInputInvalid"], [1, "tum-ui-input-number-button-group"], ["type", "button", "tabindex", "-1", "aria-hidden", "true", 1, "tum-ui-input-number-button", "tum-ui-input-number-increment", "tum:text-muted", "tum:hover:bg-hover-background", "tum:hover:text-text-hover", 3, "mousedown", "click", "disabled"], [3, "icon"], ["type", "button", "tabindex", "-1", "aria-hidden", "true", 1, "tum-ui-input-number-button", "tum-ui-input-number-decrement", "tum:text-muted", "tum:hover:bg-hover-background", "tum:hover:text-text-hover", 3, "mousedown", "click", "disabled"]], template: function TumUiInputNumberComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵelementStart(0, "input", 1, 0);
            i0.ɵɵlistener("input", function TumUiInputNumberComponent_Template_input_input_0_listener($event) { return ctx.onInput($event); })("blur", function TumUiInputNumberComponent_Template_input_blur_0_listener() { return ctx.onBlurHandler(); })("keydown", function TumUiInputNumberComponent_Template_input_keydown_0_listener($event) { return ctx.onKeydown($event); });
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(2, TumUiInputNumberComponent_Conditional_2_Template, 5, 4, "span", 2);
        } if (rf & 2) {
            i0.ɵɵproperty("value", ctx.displayText())("tumUiInputId", ctx.inputId())("tumUiInputDescribedBy", ctx.ariaDescribedBy())("disabled", ctx.isDisabled())("tumUiInputInvalid", ctx.invalid());
            i0.ɵɵattribute("inputmode", ctx.maxFractionDigits() > 0 ? "decimal" : "numeric")("name", ctx.name())("placeholder", ctx.placeholder())("aria-label", ctx.ariaLabel())("aria-labelledby", ctx.ariaLabelledBy())("aria-valuenow", ctx.ariaValueNow())("aria-valuemin", ctx.min() ?? null)("aria-valuemax", ctx.max() ?? null)("aria-valuetext", ctx.ariaValueText());
            i0.ɵɵadvance(2);
            i0.ɵɵconditional(ctx.showButtons() ? 2 : -1);
        } }, dependencies: [TumUiInputDirective, FaIconComponent], styles: ["[_nghost-%COMP%]{display:inline-flex;position:relative}.tum-ui-input-number-fluid[_nghost-%COMP%]{display:flex;width:100%}.tum-ui-input-number-input[_ngcontent-%COMP%]{flex:1 1 auto}.tum-ui-input-number-fluid[_nghost-%COMP%]   .tum-ui-input-number-input[_ngcontent-%COMP%]{width:1%}.tum-ui-input-number-buttons[_nghost-%COMP%]   .tum-ui-input-number-input[_ngcontent-%COMP%]{padding-inline-end:calc(var(--%NS%tumaet-ui-spacing) * 13)}.tum-ui-input-number-button-group[_ngcontent-%COMP%]{display:flex;flex-direction:column;position:absolute;inset-block-start:1px;inset-inline-end:1px;height:calc(100% - 2px);z-index:1}.tum-ui-input-number-button[_ngcontent-%COMP%]{display:flex;align-items:center;justify-content:center;flex:1 1 auto;width:calc(var(--%NS%tumaet-ui-spacing) * 10);padding:0;border:0;background:transparent;cursor:pointer;transition:background .2s,color .2s}.tum-ui-input-number-button[_ngcontent-%COMP%]:disabled{cursor:auto}.tum-ui-input-number-button[_ngcontent-%COMP%]   fa-icon[_ngcontent-%COMP%]{display:flex;align-items:center;justify-content:center}.tum-ui-input-number-increment[_ngcontent-%COMP%]{border-start-end-radius:calc(var(--%NS%tumaet-ui-radius-md) - 1px)}.tum-ui-input-number-decrement[_ngcontent-%COMP%]{border-end-end-radius:calc(var(--%NS%tumaet-ui-radius-md) - 1px)}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiInputNumberComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-input-number', imports: [TumUiInputDirective, FaIconComponent], host: {
                    class: 'tum-ui-input-number',
                    '[class.tum-ui-input-number-fluid]': 'fluid()',
                    '[class.tum-ui-input-number-buttons]': 'showButtons()',
                }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiInputNumberComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<input\n    #inputEl\n    tumUiInput\n    type=\"text\"\n    [attr.inputmode]=\"maxFractionDigits() > 0 ? 'decimal' : 'numeric'\"\n    class=\"tum-ui-input-number-input\"\n    role=\"spinbutton\"\n    [value]=\"displayText()\"\n    [tumUiInputId]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [attr.placeholder]=\"placeholder()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    [attr.aria-labelledby]=\"ariaLabelledBy()\"\n    [tumUiInputDescribedBy]=\"ariaDescribedBy()\"\n    [attr.aria-valuenow]=\"ariaValueNow()\"\n    [attr.aria-valuemin]=\"min() ?? null\"\n    [attr.aria-valuemax]=\"max() ?? null\"\n    [attr.aria-valuetext]=\"ariaValueText()\"\n    [disabled]=\"isDisabled()\"\n    [tumUiInputInvalid]=\"invalid()\"\n    (input)=\"onInput($event)\"\n    (blur)=\"onBlurHandler()\"\n    (keydown)=\"onKeydown($event)\"\n/>\n@if (showButtons()) {\n    <span class=\"tum-ui-input-number-button-group\">\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            aria-hidden=\"true\"\n            class=\"tum-ui-input-number-button tum-ui-input-number-increment tum:text-muted tum:hover:bg-hover-background tum:hover:text-text-hover\"\n            [disabled]=\"isDisabled()\"\n            (mousedown)=\"$event.preventDefault()\"\n            (click)=\"onStep(step())\"\n        >\n            <fa-icon [icon]=\"faChevronUp\" />\n        </button>\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            aria-hidden=\"true\"\n            class=\"tum-ui-input-number-button tum-ui-input-number-decrement tum:text-muted tum:hover:bg-hover-background tum:hover:text-text-hover\"\n            [disabled]=\"isDisabled()\"\n            (mousedown)=\"$event.preventDefault()\"\n            (click)=\"onStep(-step())\"\n        >\n            <fa-icon [icon]=\"faChevronDown\" />\n        </button>\n    </span>\n}\n", styles: [":host{display:inline-flex;position:relative}:host(.tum-ui-input-number-fluid){display:flex;width:100%}.tum-ui-input-number-input{flex:1 1 auto}:host(.tum-ui-input-number-fluid) .tum-ui-input-number-input{width:1%}:host(.tum-ui-input-number-buttons) .tum-ui-input-number-input{padding-inline-end:calc(var(--tumaet-ui-spacing) * 13)}.tum-ui-input-number-button-group{display:flex;flex-direction:column;position:absolute;inset-block-start:1px;inset-inline-end:1px;height:calc(100% - 2px);z-index:1}.tum-ui-input-number-button{display:flex;align-items:center;justify-content:center;flex:1 1 auto;width:calc(var(--tumaet-ui-spacing) * 10);padding:0;border:0;background:transparent;cursor:pointer;transition:background .2s,color .2s}.tum-ui-input-number-button:disabled{cursor:auto}.tum-ui-input-number-button fa-icon{display:flex;align-items:center;justify-content:center}.tum-ui-input-number-increment{border-start-end-radius:calc(var(--tumaet-ui-radius-md) - 1px)}.tum-ui-input-number-decrement{border-end-end-radius:calc(var(--tumaet-ui-radius-md) - 1px)}\n"] }]
    }], null, { min: [{ type: i0.Input, args: [{ isSignal: true, alias: "min", required: false }] }], max: [{ type: i0.Input, args: [{ isSignal: true, alias: "max", required: false }] }], step: [{ type: i0.Input, args: [{ isSignal: true, alias: "step", required: false }] }], showButtons: [{ type: i0.Input, args: [{ isSignal: true, alias: "showButtons", required: false }] }], prefix: [{ type: i0.Input, args: [{ isSignal: true, alias: "prefix", required: false }] }], suffix: [{ type: i0.Input, args: [{ isSignal: true, alias: "suffix", required: false }] }], placeholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "placeholder", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], invalid: [{ type: i0.Input, args: [{ isSignal: true, alias: "invalid", required: false }] }], fluid: [{ type: i0.Input, args: [{ isSignal: true, alias: "fluid", required: false }] }], useGrouping: [{ type: i0.Input, args: [{ isSignal: true, alias: "useGrouping", required: false }] }], maxFractionDigits: [{ type: i0.Input, args: [{ isSignal: true, alias: "maxFractionDigits", required: false }] }], locale: [{ type: i0.Input, args: [{ isSignal: true, alias: "locale", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], name: [{ type: i0.Input, args: [{ isSignal: true, alias: "name", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }], ariaDescribedBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaDescribedBy", required: false }] }], inputRef: [{ type: i0.ViewChild, args: ['inputEl', { isSignal: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiInputNumberComponent, { className: "TumUiInputNumberComponent", filePath: "lib/input-number/tum-ui-input-number.component.ts", lineNumber: 29 }); })();

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
    static ɵfac = function TumUiListItemActionDirective_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiListItemActionDirective)(); };
    static ɵdir = /*@__PURE__*/ i0.ɵɵdefineDirective({ type: TumUiListItemActionDirective, selectors: [["a", "tumUiListItemAction", ""], ["button", "tumUiListItemAction", ""]], hostVars: 3, hostBindings: function TumUiListItemActionDirective_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("aria-current", ctx.active() ? "page" : null);
            i0.ɵɵclassMap(ctx.hostClasses());
        } }, inputs: { active: [1, "active"] } });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiListItemActionDirective, [{
        type: Directive,
        args: [{
                selector: 'a[tumUiListItemAction], button[tumUiListItemAction]',
                host: {
                    '[class]': 'hostClasses()',
                    '[attr.aria-current]': 'active() ? "page" : null',
                },
            }]
    }], null, { active: [{ type: i0.Input, args: [{ isSignal: true, alias: "active", required: false }] }] }); })();

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
    static ɵfac = function TumUiListItemDirective_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiListItemDirective)(); };
    static ɵdir = /*@__PURE__*/ i0.ɵɵdefineDirective({ type: TumUiListItemDirective, selectors: [["li", "tumUiListItem", ""]], contentQueries: function TumUiListItemDirective_ContentQueries(rf, ctx, dirIndex) { if (rf & 1) {
            i0.ɵɵcontentQuerySignal(dirIndex, ctx.action, TumUiListItemActionDirective, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, hostVars: 2, hostBindings: function TumUiListItemDirective_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵclassMap(ctx.hostClasses());
        } }, inputs: { inline: [1, "inline"] } });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiListItemDirective, [{
        type: Directive,
        args: [{
                selector: 'li[tumUiListItem]',
                host: {
                    '[class]': 'hostClasses()',
                },
            }]
    }], null, { inline: [{ type: i0.Input, args: [{ isSignal: true, alias: "inline", required: false }] }], action: [{ type: i0.ContentChild, args: [i0.forwardRef(() => TumUiListItemActionDirective), { isSignal: true }] }] }); })();

const _c0$i = ["*"];
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
    static ɵfac = function TumUiListComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiListComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiListComponent, selectors: [["tum-ui-list"]], hostAttrs: [1, "tum-ui-list"], inputs: { ariaLabel: [1, "ariaLabel"], ariaLabelledBy: [1, "ariaLabelledBy"] }, ngContentSelectors: _c0$i, decls: 2, vars: 2, consts: [["role", "list", 1, "tum-ui-list-items", "tum:m-0", "tum:flex", "tum:list-none", "tum:flex-col", "tum:p-0"]], template: function TumUiListComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵdomElementStart(0, "ul", 0);
            i0.ɵɵprojection(1);
            i0.ɵɵdomElementEnd();
        } if (rf & 2) {
            i0.ɵɵattribute("aria-label", ctx.ariaLabel())("aria-labelledby", ctx.ariaLabelledBy());
        } }, styles: ["[_nghost-%COMP%]{display:block}.tum-ui-list-items[_ngcontent-%COMP%]{border:1px solid var(--%NS%tumaet-ui-border-color);border-radius:var(--%NS%tumaet-ui-radius-md);overflow:hidden}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiListComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-list', host: {
                    class: 'tum-ui-list',
                }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<ul role=\"list\" class=\"tum-ui-list-items tum:m-0 tum:flex tum:list-none tum:flex-col tum:p-0\" [attr.aria-label]=\"ariaLabel()\" [attr.aria-labelledby]=\"ariaLabelledBy()\">\n    <ng-content />\n</ul>\n", styles: [":host{display:block}.tum-ui-list-items{border:1px solid var(--tumaet-ui-border-color);border-radius:var(--tumaet-ui-radius-md);overflow:hidden}\n"] }]
    }], null, { ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiListComponent, { className: "TumUiListComponent", filePath: "lib/list/tum-ui-list.component.ts", lineNumber: 32 }); })();

/**
 * A single command or navigation entry inside a {@link TumUiMenuComponent}.
 *
 * Apply it to a `<button>` for an action, or to an `<a>` for navigation so the entry keeps native link
 * behaviour such as opening in a new tab. `disabled` and the `triggered` output come from the CDK menu item,
 * which also owns the `role`, roving `tabindex`, and closing the menu once an entry runs.
 */
class TumUiMenuItemDirective {
    static ɵfac = function TumUiMenuItemDirective_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiMenuItemDirective)(); };
    static ɵdir = /*@__PURE__*/ i0.ɵɵdefineDirective({ type: TumUiMenuItemDirective, selectors: [["", "tumUiMenuItem", ""]], hostAttrs: [1, "tum-ui-menu-item", "tum:flex", "tum:cursor-pointer", "tum:items-center", "tum:gap-2", "tum:border-0", "tum:bg-transparent", "tum:px-3", "tum:py-2", "tum:text-start", "tum:text-base", "tum:text-text", "tum:no-underline", "tum:hover:bg-hover-background", "tum:hover:text-text-hover", "tum:focus-visible:bg-highlight-focus-background", "tum:focus-visible:text-highlight", "tum:focus-visible:outline-none", "tum:aria-disabled:pointer-events-none", "tum:aria-disabled:cursor-default", "tum:aria-disabled:text-disabled"], features: [i0.ɵɵHostDirectivesFeature([{ directive: i1$1.CdkMenuItem, inputs: ["cdkMenuItemDisabled", "disabled"], outputs: ["cdkMenuItemTriggered", "triggered"] }])] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiMenuItemDirective, [{
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
    }], null, null); })();

/**
 * Opens a {@link TumUiMenuComponent} from the element it sits on, which is normally a button.
 *
 * Point it at the `ng-template` that holds the menu: `<button [tumUiMenuTrigger]="actions">`. The CDK menu
 * trigger owns the overlay, `aria-haspopup` / `aria-expanded`, opening on Enter, Space, or ArrowDown, closing
 * on Escape or an outside click, and restoring focus to the trigger afterwards.
 */
class TumUiMenuTriggerDirective {
    static ɵfac = function TumUiMenuTriggerDirective_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiMenuTriggerDirective)(); };
    static ɵdir = /*@__PURE__*/ i0.ɵɵdefineDirective({ type: TumUiMenuTriggerDirective, selectors: [["", "tumUiMenuTrigger", ""]], features: [i0.ɵɵHostDirectivesFeature([{ directive: i1$1.CdkMenuTrigger, inputs: ["cdkMenuTriggerFor", "tumUiMenuTrigger", "cdkMenuPosition", "tumUiMenuPosition"], outputs: ["cdkMenuOpened", "menuOpened", "cdkMenuClosed", "menuClosed"] }])] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiMenuTriggerDirective, [{
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
    }], null, null); })();

const _c0$h = ["*"];
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
    static ɵfac = function TumUiMenuComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiMenuComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiMenuComponent, selectors: [["tum-ui-menu"]], hostAttrs: [1, "tum-ui-menu", "tum:flex", "tum:min-w-48", "tum:flex-col", "tum:rounded-md", "tum:border", "tum:border-border", "tum:bg-overlay-background", "tum:py-1", "tum:text-text", "tum:shadow-md"], features: [i0.ɵɵHostDirectivesFeature([i1$1.CdkMenu])], ngContentSelectors: _c0$h, decls: 1, vars: 0, template: function TumUiMenuComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵprojection(0);
        } }, styles: ["[_nghost-%COMP%]{box-sizing:border-box;max-height:inherit;overflow:auto}[_nghost-%COMP%]:focus-visible{outline:none}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiMenuComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-menu', template: '<ng-content />', hostDirectives: [CdkMenu], host: {
                    class: 'tum-ui-menu tum:flex tum:min-w-48 tum:flex-col tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:py-1 tum:text-text tum:shadow-md',
                }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{box-sizing:border-box;max-height:inherit;overflow:auto}:host:focus-visible{outline:none}\n"] }]
    }], null, null); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiMenuComponent, { className: "TumUiMenuComponent", filePath: "lib/menu/tum-ui-menu.component.ts", lineNumber: 34 }); })();

const _c0$g = ["*"];
function TumUiMessageComponent_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "fa-icon", 1);
} if (rf & 2) {
    i0.ɵɵproperty("icon", ctx);
} }
function TumUiMessageComponent_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "span", 2);
    i0.ɵɵtext(1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(ctx);
} }
function TumUiMessageComponent_Conditional_3_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "span", 2);
    i0.ɵɵprojection(1);
    i0.ɵɵelementEnd();
} }
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
    text = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "text" }] : /* istanbul ignore next */ []));
    icon = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "icon" }] : /* istanbul ignore next */ []));
    messageRole = computed(() => (this.severity() === 'error' ? 'alert' : 'status'), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "messageRole" }] : /* istanbul ignore next */ []));
    hostClasses = computed(() => `${MESSAGE_BASE} ${MESSAGE_SEVERITY[this.severity()]}`.trim(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []));
    static ɵfac = function TumUiMessageComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiMessageComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiMessageComponent, selectors: [["tum-ui-message"]], hostVars: 4, hostBindings: function TumUiMessageComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("role", ctx.messageRole())("data-severity", ctx.severity());
            i0.ɵɵclassMap(ctx.hostClasses());
        } }, inputs: { severity: [1, "severity"], text: [1, "text"], icon: [1, "icon"] }, ngContentSelectors: _c0$g, decls: 4, vars: 2, consts: [[1, "tum-ui-message-content"], [1, "tum-ui-message-icon", 3, "icon"], [1, "tum-ui-message-text"]], template: function TumUiMessageComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵelementStart(0, "div", 0);
            i0.ɵɵconditionalCreate(1, TumUiMessageComponent_Conditional_1_Template, 1, 1, "fa-icon", 1);
            i0.ɵɵconditionalCreate(2, TumUiMessageComponent_Conditional_2_Template, 2, 1, "span", 2)(3, TumUiMessageComponent_Conditional_3_Template, 2, 0, "span", 2);
            i0.ɵɵelementEnd();
        } if (rf & 2) {
            let tmp_0_0;
            let tmp_1_0;
            i0.ɵɵadvance();
            i0.ɵɵconditional((tmp_0_0 = ctx.icon()) ? 1 : -1, tmp_0_0);
            i0.ɵɵadvance();
            i0.ɵɵconditional((tmp_1_0 = ctx.text()) ? 2 : 3, tmp_1_0);
        } }, dependencies: [FaIconComponent], styles: ["[_nghost-%COMP%]{display:grid;grid-template-rows:1fr;border-radius:var(--%NS%tumaet-ui-radius-md);outline-width:1px;outline-style:solid;outline-offset:0;font-size:var(--%NS%tumaet-ui-font-size-base);font-weight:500}.tum-ui-message-content[_ngcontent-%COMP%]{display:flex;align-items:center;min-height:0;padding:calc(var(--%NS%tumaet-ui-spacing) * 2) calc(var(--%NS%tumaet-ui-spacing) * 3);gap:calc(var(--%NS%tumaet-ui-spacing) * 2)}.tum-ui-message-icon[_ngcontent-%COMP%]{flex-shrink:0;font-size:var(--%NS%tumaet-ui-font-size-lg)}[data-severity=info][_nghost-%COMP%]{background:color-mix(in srgb,var(--%NS%tumaet-ui-state-info) 10%,var(--%NS%tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--%NS%tumaet-ui-state-info) 40%,var(--%NS%tumaet-ui-content-background));color:var(--%NS%tumaet-ui-state-info-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--%NS%tumaet-ui-state-info),transparent 96%)}[data-severity=success][_nghost-%COMP%]{background:color-mix(in srgb,var(--%NS%tumaet-ui-state-success) 10%,var(--%NS%tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--%NS%tumaet-ui-state-success) 40%,var(--%NS%tumaet-ui-content-background));color:var(--%NS%tumaet-ui-state-success-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--%NS%tumaet-ui-state-success),transparent 96%)}[data-severity=warn][_nghost-%COMP%]{background:color-mix(in srgb,var(--%NS%tumaet-ui-state-warning) 10%,var(--%NS%tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--%NS%tumaet-ui-state-warning) 40%,var(--%NS%tumaet-ui-content-background));color:var(--%NS%tumaet-ui-state-warning-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--%NS%tumaet-ui-state-warning),transparent 96%)}[data-severity=error][_nghost-%COMP%]{background:color-mix(in srgb,var(--%NS%tumaet-ui-state-danger) 10%,var(--%NS%tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--%NS%tumaet-ui-state-danger) 40%,var(--%NS%tumaet-ui-content-background));color:var(--%NS%tumaet-ui-state-danger-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--%NS%tumaet-ui-state-danger),transparent 96%)}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiMessageComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-message', imports: [FaIconComponent], host: {
                    '[attr.role]': 'messageRole()',
                    '[class]': 'hostClasses()',
                    '[attr.data-severity]': 'severity()',
                }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum-ui-message-content\">\n    @if (icon(); as messageIcon) {\n        <fa-icon [icon]=\"messageIcon\" class=\"tum-ui-message-icon\" />\n    }\n    @if (text(); as messageText) {\n        <span class=\"tum-ui-message-text\">{{ messageText }}</span>\n    } @else {\n        <span class=\"tum-ui-message-text\"><ng-content /></span>\n    }\n</div>\n", styles: [":host{display:grid;grid-template-rows:1fr;border-radius:var(--tumaet-ui-radius-md);outline-width:1px;outline-style:solid;outline-offset:0;font-size:var(--tumaet-ui-font-size-base);font-weight:500}.tum-ui-message-content{display:flex;align-items:center;min-height:0;padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 3);gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-message-icon{flex-shrink:0;font-size:var(--tumaet-ui-font-size-lg)}:host[data-severity=info]{background:color-mix(in srgb,var(--tumaet-ui-state-info) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-info) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-info-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-info),transparent 96%)}:host[data-severity=success]{background:color-mix(in srgb,var(--tumaet-ui-state-success) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-success) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-success-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-success),transparent 96%)}:host[data-severity=warn]{background:color-mix(in srgb,var(--tumaet-ui-state-warning) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-warning) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-warning-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-warning),transparent 96%)}:host[data-severity=error]{background:color-mix(in srgb,var(--tumaet-ui-state-danger) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-danger) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-danger-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-danger),transparent 96%)}\n"] }]
    }], null, { severity: [{ type: i0.Input, args: [{ isSignal: true, alias: "severity", required: false }] }], text: [{ type: i0.Input, args: [{ isSignal: true, alias: "text", required: false }] }], icon: [{ type: i0.Input, args: [{ isSignal: true, alias: "icon", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiMessageComponent, { className: "TumUiMessageComponent", filePath: "lib/message/tum-ui-message.component.ts", lineNumber: 30 }); })();

const _c0$f = (a0, a1, a2) => ({ first: a0, second: a1, total: a2 });
function TumUiPaginatorComponent_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "span", 1);
    i0.ɵɵtext(1);
    i0.ɵɵpipe(2, "tumUiTranslate");
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate1(" ", i0.ɵɵpipeBind2(2, 1, "tumUi.paginator.currentPageReport", i0.ɵɵpureFunction3(4, _c0$f, ctx_r0.rangeBegin(), ctx_r0.rangeEnd(), ctx_r0.totalRecords())), " ");
} }
function TumUiPaginatorComponent_For_10_Template(rf, ctx) { if (rf & 1) {
    const _r2 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "button", 2);
    i0.ɵɵlistener("click", function TumUiPaginatorComponent_For_10_Template_button_click_0_listener() { const p_r3 = i0.ɵɵrestoreView(_r2).$implicit; const ctx_r0 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r0.goToPage(p_r3)); });
    i0.ɵɵtext(1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const p_r3 = ctx.$implicit;
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵclassMap(p_r3 === ctx_r0.clampedPage() ? ctx_r0.selectedPageClasses : ctx_r0.navButtonClasses);
    i0.ɵɵproperty("disabled", ctx_r0.disabled());
    i0.ɵɵattribute("aria-current", p_r3 === ctx_r0.clampedPage() ? "page" : null);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate1(" ", p_r3 + 1, " ");
} }
function TumUiPaginatorComponent_Conditional_17_For_6_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "option", 7);
    i0.ɵɵtext(1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const option_r5 = ctx.$implicit;
    i0.ɵɵproperty("ngValue", option_r5);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(option_r5);
} }
function TumUiPaginatorComponent_Conditional_17_Template(rf, ctx) { if (rf & 1) {
    const _r4 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "label", 5)(1, "span");
    i0.ɵɵtext(2);
    i0.ɵɵpipe(3, "tumUiTranslate");
    i0.ɵɵelementEnd();
    i0.ɵɵelementStart(4, "select", 6);
    i0.ɵɵlistener("ngModelChange", function TumUiPaginatorComponent_Conditional_17_Template_select_ngModelChange_4_listener($event) { i0.ɵɵrestoreView(_r4); const ctx_r0 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r0.onPageSizeChange($event)); });
    i0.ɵɵrepeaterCreate(5, TumUiPaginatorComponent_Conditional_17_For_6_Template, 2, 2, "option", 7, i0.ɵɵrepeaterTrackByIdentity);
    i0.ɵɵelementEnd();
    i0.ɵɵcontrolCreate();
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵadvance(2);
    i0.ɵɵtextInterpolate(i0.ɵɵpipeBind1(3, 3, "tumUi.paginator.rowsPerPage"));
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("ngModel", ctx_r0.pageSize())("disabled", ctx_r0.disabled());
    i0.ɵɵcontrol();
    i0.ɵɵadvance();
    i0.ɵɵrepeater(ctx_r0.pageSizeOptions());
} }
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
    static ɵfac = function TumUiPaginatorComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiPaginatorComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiPaginatorComponent, selectors: [["tum-ui-paginator"]], inputs: { ariaLabel: [1, "ariaLabel"], totalRecords: [1, "totalRecords"], page: [1, "page"], pageSize: [1, "pageSize"], pageSizeOptions: [1, "pageSizeOptions"], disabled: [1, "disabled"], showCurrentPageReport: [1, "showCurrentPageReport"], showRowsPerPage: [1, "showRowsPerPage"] }, outputs: { pageChange: "pageChange", pageSizeChange: "pageSizeChange" }, decls: 18, vars: 33, consts: [[1, "tum:flex", "tum:flex-wrap", "tum:items-center", "tum:justify-center", "tum:gap-1", "tum:border-t", "tum:border-border", "tum:pt-2", "tum:text-sm", "tum:text-muted"], ["aria-live", "polite", 1, "tum:px-2"], ["type", "button", 3, "click", "disabled"], [3, "icon"], ["type", "button", 3, "class", "disabled"], [1, "tum:ms-2", "tum:flex", "tum:items-center", "tum:gap-2", "tum:whitespace-nowrap"], [1, "tum:box-border", "tum:h-9", "tum:cursor-pointer", "tum:rounded-md", "tum:border", "tum:border-control-border", "tum:bg-control-background", "tum:px-2.5", "tum:text-sm", "tum:text-text", "tum:focus-visible:outline", "tum:focus-visible:outline-2", "tum:focus-visible:outline-focus", "tum:focus-visible:outline-offset-2", 3, "ngModelChange", "ngModel", "disabled"], [3, "ngValue"]], template: function TumUiPaginatorComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵelementStart(0, "nav", 0);
            i0.ɵɵpipe(1, "tumUiTranslate");
            i0.ɵɵconditionalCreate(2, TumUiPaginatorComponent_Conditional_2_Template, 3, 8, "span", 1);
            i0.ɵɵelementStart(3, "button", 2);
            i0.ɵɵpipe(4, "tumUiTranslate");
            i0.ɵɵlistener("click", function TumUiPaginatorComponent_Template_button_click_3_listener() { return ctx.goToFirst(); });
            i0.ɵɵelement(5, "fa-icon", 3);
            i0.ɵɵelementEnd();
            i0.ɵɵelementStart(6, "button", 2);
            i0.ɵɵpipe(7, "tumUiTranslate");
            i0.ɵɵlistener("click", function TumUiPaginatorComponent_Template_button_click_6_listener() { return ctx.goToPrevious(); });
            i0.ɵɵelement(8, "fa-icon", 3);
            i0.ɵɵelementEnd();
            i0.ɵɵrepeaterCreate(9, TumUiPaginatorComponent_For_10_Template, 2, 5, "button", 4, i0.ɵɵrepeaterTrackByIdentity);
            i0.ɵɵelementStart(11, "button", 2);
            i0.ɵɵpipe(12, "tumUiTranslate");
            i0.ɵɵlistener("click", function TumUiPaginatorComponent_Template_button_click_11_listener() { return ctx.goToNext(); });
            i0.ɵɵelement(13, "fa-icon", 3);
            i0.ɵɵelementEnd();
            i0.ɵɵelementStart(14, "button", 2);
            i0.ɵɵpipe(15, "tumUiTranslate");
            i0.ɵɵlistener("click", function TumUiPaginatorComponent_Template_button_click_14_listener() { return ctx.goToLast(); });
            i0.ɵɵelement(16, "fa-icon", 3);
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(17, TumUiPaginatorComponent_Conditional_17_Template, 7, 5, "label", 5);
            i0.ɵɵelementEnd();
        } if (rf & 2) {
            i0.ɵɵattribute("aria-label", ctx.ariaLabel() ?? i0.ɵɵpipeBind1(1, 23, "tumUi.paginator.ariaLabel"));
            i0.ɵɵadvance(2);
            i0.ɵɵconditional(ctx.showCurrentPageReport() ? 2 : -1);
            i0.ɵɵadvance();
            i0.ɵɵclassMap(ctx.navButtonClasses);
            i0.ɵɵproperty("disabled", ctx.disabled() || ctx.isFirst());
            i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(4, 25, "tumUi.paginator.first"));
            i0.ɵɵadvance(2);
            i0.ɵɵproperty("icon", ctx.firstPageIcon());
            i0.ɵɵadvance();
            i0.ɵɵclassMap(ctx.navButtonClasses);
            i0.ɵɵproperty("disabled", ctx.disabled() || ctx.isFirst());
            i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(7, 27, "tumUi.paginator.previous"));
            i0.ɵɵadvance(2);
            i0.ɵɵproperty("icon", ctx.previousPageIcon());
            i0.ɵɵadvance();
            i0.ɵɵrepeater(ctx.visiblePages());
            i0.ɵɵadvance(2);
            i0.ɵɵclassMap(ctx.navButtonClasses);
            i0.ɵɵproperty("disabled", ctx.disabled() || ctx.isLast());
            i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(12, 29, "tumUi.paginator.next"));
            i0.ɵɵadvance(2);
            i0.ɵɵproperty("icon", ctx.nextPageIcon());
            i0.ɵɵadvance();
            i0.ɵɵclassMap(ctx.navButtonClasses);
            i0.ɵɵproperty("disabled", ctx.disabled() || ctx.isLast());
            i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(15, 31, "tumUi.paginator.last"));
            i0.ɵɵadvance(2);
            i0.ɵɵproperty("icon", ctx.lastPageIcon());
            i0.ɵɵadvance();
            i0.ɵɵconditional(ctx.showRowsPerPage() ? 17 : -1);
        } }, dependencies: [FaIconComponent, FormsModule, i1$2.NgSelectOption, i1$2.ɵNgSelectMultipleOption, i1$2.SelectControlValueAccessor, i1$2.NgControlStatus, i1$2.NgModel, TumUiTranslatePipe], encapsulation: 2 });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiPaginatorComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-paginator', imports: [FaIconComponent, FormsModule, TumUiTranslatePipe], changeDetection: ChangeDetectionStrategy.OnPush, template: "<nav\n    [attr.aria-label]=\"ariaLabel() ?? ('tumUi.paginator.ariaLabel' | tumUiTranslate)\"\n    class=\"tum:flex tum:flex-wrap tum:items-center tum:justify-center tum:gap-1 tum:border-t tum:border-border tum:pt-2 tum:text-sm tum:text-muted\"\n>\n    @if (showCurrentPageReport()) {\n        <span class=\"tum:px-2\" aria-live=\"polite\">\n            {{ 'tumUi.paginator.currentPageReport' | tumUiTranslate: { first: rangeBegin(), second: rangeEnd(), total: totalRecords() } }}\n        </span>\n    }\n\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isFirst()\" (click)=\"goToFirst()\" [attr.aria-label]=\"'tumUi.paginator.first' | tumUiTranslate\">\n        <fa-icon [icon]=\"firstPageIcon()\" />\n    </button>\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isFirst()\" (click)=\"goToPrevious()\" [attr.aria-label]=\"'tumUi.paginator.previous' | tumUiTranslate\">\n        <fa-icon [icon]=\"previousPageIcon()\" />\n    </button>\n\n    @for (p of visiblePages(); track p) {\n        <button\n            type=\"button\"\n            [class]=\"p === clampedPage() ? selectedPageClasses : navButtonClasses\"\n            [disabled]=\"disabled()\"\n            (click)=\"goToPage(p)\"\n            [attr.aria-current]=\"p === clampedPage() ? 'page' : null\"\n        >\n            {{ p + 1 }}\n        </button>\n    }\n\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isLast()\" (click)=\"goToNext()\" [attr.aria-label]=\"'tumUi.paginator.next' | tumUiTranslate\">\n        <fa-icon [icon]=\"nextPageIcon()\" />\n    </button>\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isLast()\" (click)=\"goToLast()\" [attr.aria-label]=\"'tumUi.paginator.last' | tumUiTranslate\">\n        <fa-icon [icon]=\"lastPageIcon()\" />\n    </button>\n\n    @if (showRowsPerPage()) {\n        <label class=\"tum:ms-2 tum:flex tum:items-center tum:gap-2 tum:whitespace-nowrap\">\n            <span>{{ 'tumUi.paginator.rowsPerPage' | tumUiTranslate }}</span>\n            <select\n                class=\"tum:box-border tum:h-9 tum:cursor-pointer tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:px-2.5 tum:text-sm tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                [ngModel]=\"pageSize()\"\n                [disabled]=\"disabled()\"\n                (ngModelChange)=\"onPageSizeChange($event)\"\n            >\n                @for (option of pageSizeOptions(); track option) {\n                    <option [ngValue]=\"option\">{{ option }}</option>\n                }\n            </select>\n        </label>\n    }\n</nav>\n" }]
    }], () => [], { ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], totalRecords: [{ type: i0.Input, args: [{ isSignal: true, alias: "totalRecords", required: false }] }], page: [{ type: i0.Input, args: [{ isSignal: true, alias: "page", required: false }] }], pageSize: [{ type: i0.Input, args: [{ isSignal: true, alias: "pageSize", required: false }] }], pageSizeOptions: [{ type: i0.Input, args: [{ isSignal: true, alias: "pageSizeOptions", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], showCurrentPageReport: [{ type: i0.Input, args: [{ isSignal: true, alias: "showCurrentPageReport", required: false }] }], showRowsPerPage: [{ type: i0.Input, args: [{ isSignal: true, alias: "showRowsPerPage", required: false }] }], pageChange: [{ type: i0.Output, args: ["pageChange"] }], pageSizeChange: [{ type: i0.Output, args: ["pageSizeChange"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiPaginatorComponent, { className: "TumUiPaginatorComponent", filePath: "lib/paginator/tum-ui-paginator.component.ts", lineNumber: 19 }); })();

const _c0$e = [[["", "tumUiPanelHeader", ""]], "*", [["", "tumUiPanelFooter", ""]]];
const _c1$3 = ["[tumUiPanelHeader]", "*", "[tumUiPanelFooter]"];
function TumUiPanelComponent_Conditional_4_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "button", 6);
    i0.ɵɵlistener("click", function TumUiPanelComponent_Conditional_4_Template_button_click_0_listener() { i0.ɵɵrestoreView(_r1); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.toggle()); });
    i0.ɵɵelement(1, "fa-icon", 7);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵattribute("aria-expanded", !ctx_r1.collapsed())("aria-controls", ctx_r1.contentId)("aria-label", ctx_r1.toggleLabel())("aria-labelledby", ctx_r1.toggleLabelledBy());
    i0.ɵɵadvance();
    i0.ɵɵproperty("icon", ctx_r1.collapsed() ? ctx_r1.faChevronDown : ctx_r1.faChevronUp);
} }
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
    static ɵfac = function TumUiPanelComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiPanelComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiPanelComponent, selectors: [["tum-ui-panel"]], hostAttrs: [1, "tum-ui-panel", "tum:border", "tum:border-border", "tum:rounded-md", "tum:bg-content-background", "tum:text-text"], hostVars: 1, hostBindings: function TumUiPanelComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("data-collapsed", ctx.toggleable() && ctx.collapsed());
        } }, inputs: { header: [1, "header"], toggleable: [1, "toggleable"], toggleAriaLabel: [1, "toggleAriaLabel"], collapsed: [1, "collapsed"] }, outputs: { collapsed: "collapsedChange" }, ngContentSelectors: _c1$3, decls: 10, vars: 10, consts: [[1, "tum-ui-panel-header"], [1, "tum-ui-panel-title", 3, "id"], ["type", "button", 1, "tum-ui-panel-toggler"], [1, "tum-ui-panel-content-container", 3, "id"], [1, "tum-ui-panel-content-wrapper"], [1, "tum-ui-panel-content"], ["type", "button", 1, "tum-ui-panel-toggler", 3, "click"], [3, "icon"]], template: function TumUiPanelComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef(_c0$e);
            i0.ɵɵelementStart(0, "div", 0)(1, "span", 1);
            i0.ɵɵtext(2);
            i0.ɵɵprojection(3);
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(4, TumUiPanelComponent_Conditional_4_Template, 2, 5, "button", 2);
            i0.ɵɵelementEnd();
            i0.ɵɵelementStart(5, "div", 3)(6, "div", 4)(7, "div", 5);
            i0.ɵɵprojection(8, 1);
            i0.ɵɵelementEnd();
            i0.ɵɵprojection(9, 2);
            i0.ɵɵelementEnd()();
        } if (rf & 2) {
            i0.ɵɵclassProp("tum-ui-panel-header-toggleable", ctx.toggleable());
            i0.ɵɵadvance();
            i0.ɵɵproperty("id", ctx.headerId);
            i0.ɵɵadvance();
            i0.ɵɵtextInterpolate(ctx.header());
            i0.ɵɵadvance(2);
            i0.ɵɵconditional(ctx.toggleable() ? 4 : -1);
            i0.ɵɵadvance();
            i0.ɵɵproperty("id", ctx.contentId);
            i0.ɵɵattribute("role", ctx.toggleable() && ctx.header().trim() ? "region" : null)("aria-labelledby", ctx.toggleable() && ctx.header().trim() ? ctx.headerId : null)("aria-hidden", ctx.isCollapsed() ? "true" : null)("inert", ctx.isCollapsed() ? "" : null);
        } }, dependencies: [FaIconComponent], styles: ["[_nghost-%COMP%]{display:block}.tum-ui-panel-header[_ngcontent-%COMP%]{display:flex;justify-content:space-between;align-items:center;padding:calc(var(--%NS%tumaet-ui-spacing) * 4.5)}.tum-ui-panel-header-toggleable[_ngcontent-%COMP%]{padding:calc(var(--%NS%tumaet-ui-spacing) * 1.5) calc(var(--%NS%tumaet-ui-spacing) * 4.5)}.tum-ui-panel-title[_ngcontent-%COMP%]{line-height:1;font-weight:600;flex:1;min-width:0}.tum-ui-panel-toggler[_ngcontent-%COMP%]{display:inline-flex;align-items:center;justify-content:center;width:calc(var(--%NS%tumaet-ui-spacing) * 8);height:calc(var(--%NS%tumaet-ui-spacing) * 8);padding:0;border:0;border-radius:50%;background:transparent;color:inherit;cursor:pointer;transition:background-color .15s ease-in-out}.tum-ui-panel-toggler[_ngcontent-%COMP%]:hover{background-color:color-mix(in srgb,currentcolor 8%,transparent)}.tum-ui-panel-toggler[_ngcontent-%COMP%]:focus-visible{outline:2px solid var(--%NS%tumaet-ui-focus-color);outline-offset:1px}.tum-ui-panel-content-container[_ngcontent-%COMP%]{display:grid;grid-template-rows:1fr;transition:grid-template-rows .2s ease-in-out}[data-collapsed=true][_nghost-%COMP%]   .tum-ui-panel-content-container[_ngcontent-%COMP%]{grid-template-rows:0fr}.tum-ui-panel-content-wrapper[_ngcontent-%COMP%]{min-height:0;overflow:hidden}.tum-ui-panel-content[_ngcontent-%COMP%]{padding:0 calc(var(--%NS%tumaet-ui-spacing) * 4.5) calc(var(--%NS%tumaet-ui-spacing) * 4.5)}@media(prefers-reduced-motion:reduce){.tum-ui-panel-content-container[_ngcontent-%COMP%]{transition:none}}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiPanelComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-panel', imports: [FaIconComponent], host: {
                    class: 'tum-ui-panel tum:border tum:border-border tum:rounded-md tum:bg-content-background tum:text-text',
                    '[attr.data-collapsed]': 'toggleable() && collapsed()',
                }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum-ui-panel-header\" [class.tum-ui-panel-header-toggleable]=\"toggleable()\">\n    <span [id]=\"headerId\" class=\"tum-ui-panel-title\">{{ header() }}<ng-content select=\"[tumUiPanelHeader]\" /></span>\n    @if (toggleable()) {\n        <button\n            type=\"button\"\n            class=\"tum-ui-panel-toggler\"\n            [attr.aria-expanded]=\"!collapsed()\"\n            [attr.aria-controls]=\"contentId\"\n            [attr.aria-label]=\"toggleLabel()\"\n            [attr.aria-labelledby]=\"toggleLabelledBy()\"\n            (click)=\"toggle()\"\n        >\n            <fa-icon [icon]=\"collapsed() ? faChevronDown : faChevronUp\" />\n        </button>\n    }\n</div>\n<div\n    [id]=\"contentId\"\n    class=\"tum-ui-panel-content-container\"\n    [attr.role]=\"toggleable() && header().trim() ? 'region' : null\"\n    [attr.aria-labelledby]=\"toggleable() && header().trim() ? headerId : null\"\n    [attr.aria-hidden]=\"isCollapsed() ? 'true' : null\"\n    [attr.inert]=\"isCollapsed() ? '' : null\"\n>\n    <div class=\"tum-ui-panel-content-wrapper\">\n        <div class=\"tum-ui-panel-content\">\n            <ng-content />\n        </div>\n        <ng-content select=\"[tumUiPanelFooter]\" />\n    </div>\n</div>\n", styles: [":host{display:block}.tum-ui-panel-header{display:flex;justify-content:space-between;align-items:center;padding:calc(var(--tumaet-ui-spacing) * 4.5)}.tum-ui-panel-header-toggleable{padding:calc(var(--tumaet-ui-spacing) * 1.5) calc(var(--tumaet-ui-spacing) * 4.5)}.tum-ui-panel-title{line-height:1;font-weight:600;flex:1;min-width:0}.tum-ui-panel-toggler{display:inline-flex;align-items:center;justify-content:center;width:calc(var(--tumaet-ui-spacing) * 8);height:calc(var(--tumaet-ui-spacing) * 8);padding:0;border:0;border-radius:50%;background:transparent;color:inherit;cursor:pointer;transition:background-color .15s ease-in-out}.tum-ui-panel-toggler:hover{background-color:color-mix(in srgb,currentcolor 8%,transparent)}.tum-ui-panel-toggler:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:1px}.tum-ui-panel-content-container{display:grid;grid-template-rows:1fr;transition:grid-template-rows .2s ease-in-out}:host([data-collapsed=true]) .tum-ui-panel-content-container{grid-template-rows:0fr}.tum-ui-panel-content-wrapper{min-height:0;overflow:hidden}.tum-ui-panel-content{padding:0 calc(var(--tumaet-ui-spacing) * 4.5) calc(var(--tumaet-ui-spacing) * 4.5)}@media(prefers-reduced-motion:reduce){.tum-ui-panel-content-container{transition:none}}\n"] }]
    }], null, { header: [{ type: i0.Input, args: [{ isSignal: true, alias: "header", required: false }] }], toggleable: [{ type: i0.Input, args: [{ isSignal: true, alias: "toggleable", required: false }] }], toggleAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "toggleAriaLabel", required: false }] }], collapsed: [{ type: i0.Input, args: [{ isSignal: true, alias: "collapsed", required: false }] }, { type: i0.Output, args: ["collapsedChange"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiPanelComponent, { className: "TumUiPanelComponent", filePath: "lib/panel/tum-ui-panel.component.ts", lineNumber: 19 }); })();

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
    static ɵfac = function TumUiPopoverTriggerDirective_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiPopoverTriggerDirective)(); };
    static ɵdir = /*@__PURE__*/ i0.ɵɵdefineDirective({ type: TumUiPopoverTriggerDirective, selectors: [["", "tumUiPopoverTrigger", ""]], hostVars: 2, hostBindings: function TumUiPopoverTriggerDirective_HostBindings(rf, ctx) { if (rf & 1) {
            i0.ɵɵlistener("click", function TumUiPopoverTriggerDirective_click_HostBindingHandler() { return ctx.toggle(); });
        } if (rf & 2) {
            i0.ɵɵattribute("aria-haspopup", "dialog")("aria-expanded", ctx.popover().isOpen() ? "true" : "false");
        } }, inputs: { popover: [1, "tumUiPopoverTrigger", "popover"] } });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiPopoverTriggerDirective, [{
        type: Directive,
        args: [{
                selector: '[tumUiPopoverTrigger]',
                host: {
                    '(click)': 'toggle()',
                    '[attr.aria-haspopup]': "'dialog'",
                    '[attr.aria-expanded]': "popover().isOpen() ? 'true' : 'false'",
                },
            }]
    }], null, { popover: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiPopoverTrigger", required: true }] }] }); })();

const _c0$d = ["panel"];
const _c1$2 = ["*"];
function TumUiPopoverComponent_ng_template_0_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "div", 1);
    i0.ɵɵprojection(1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵproperty("cdkTrapFocusAutoCapture", true);
    i0.ɵɵattribute("aria-label", ctx_r0.ariaLabel())("data-placement", ctx_r0.appliedPlacement());
} }
/** Shorter than the way in: a closing only has to avoid snapping. */
const CLOSE_DURATION_MS = 100;
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
    document = inject(DOCUMENT);
    placement = input('bottom', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "placement" }] : /* istanbul ignore next */ []));
    /** Accessible name announced for the role="dialog" panel. Required: a dialog must have a name. */
    ariaLabel = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    openChange = output();
    panel = viewChild.required('panel', { ...(ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {}), read: TemplateRef });
    overlayRef;
    positionSub;
    openState = signal(false, /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "openState" }] : /* istanbul ignore next */ []));
    /**
     * Where the panel ended up, which is not always where it was asked to go: CDK flips it when the preferred side
     * has no room. The opening animation grows the panel from the edge nearest its origin, so it has to follow.
     */
    appliedPlacement = signal('bottom', /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "appliedPlacement" }] : /* istanbul ignore next */ []));
    /** Whether the popover is currently open. Read-only: drive it through open() / close() / toggle(). */
    isOpen = this.openState.asReadonly();
    /** Open the popover anchored to `origin`. No-op if already open. */
    open(origin) {
        if (this.isOpen()) {
            return;
        }
        this.overlayRef = this.overlayService.createConnectedOverlay(origin, this.placement(), { hasBackdrop: true });
        this.appliedPlacement.set(this.placement());
        // CDK may emit the flipped position synchronously while attaching, so this is subscribed before the portal.
        const strategy = this.overlayRef.getConfig().positionStrategy;
        this.positionSub = strategy.positionChanges.subscribe((change) => this.appliedPlacement.set(this.overlayService.placementFromPosition(change.connectionPair)));
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
    /**
     * Close the popover and dispose its overlay. No-op if already closed.
     *
     * Closed straight away, though the panel fades out first: callers drive their own state off `isOpen`/`openChange`.
     */
    close() {
        if (!this.isOpen()) {
            return;
        }
        this.positionSub?.unsubscribe();
        this.positionSub = undefined;
        const closing = this.overlayRef;
        this.overlayRef = undefined;
        this.openState.set(false);
        this.openChange.emit(false);
        this.fadeOutAndDispose(closing);
    }
    /** Fades the panel out, then disposes. Disposes at once under reduced motion or without the animation API. */
    fadeOutAndDispose(closing) {
        if (!closing) {
            return;
        }
        const panel = closing.overlayElement?.querySelector('.tum-ui-popover-panel');
        const view = this.document.defaultView;
        const reducedMotion = typeof view?.matchMedia === 'function' && view.matchMedia('(prefers-reduced-motion: reduce)').matches;
        if (!panel || reducedMotion || typeof panel.animate !== 'function') {
            closing.dispose();
            return;
        }
        const animation = panel.animate([{ opacity: 1 }, { opacity: 0 }], { duration: CLOSE_DURATION_MS, easing: 'ease-in', fill: 'forwards' });
        // Both arms: a cancelled fade must not leak the overlay.
        animation.finished.then(() => closing.dispose(), () => closing.dispose());
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
        // Straight out, with no fade: there is no view left to play it in.
        this.positionSub?.unsubscribe();
        this.overlayRef?.dispose();
    }
    static ɵfac = function TumUiPopoverComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiPopoverComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiPopoverComponent, selectors: [["tum-ui-popover"]], viewQuery: function TumUiPopoverComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.panel, _c0$d, 5, TemplateRef);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, inputs: { placement: [1, "placement"], ariaLabel: [1, "ariaLabel"] }, outputs: { openChange: "openChange" }, ngContentSelectors: _c1$2, decls: 2, vars: 0, consts: [["panel", ""], ["role", "dialog", "aria-modal", "true", "tabindex", "0", "cdkTrapFocus", "", 1, "tum-ui-popover-panel", "tum:rounded-md", "tum:border", "tum:border-border", "tum:bg-overlay-background", "tum:p-3", "tum:text-text", "tum:shadow-lg", 3, "cdkTrapFocusAutoCapture"]], template: function TumUiPopoverComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵtemplate(0, TumUiPopoverComponent_ng_template_0_Template, 2, 3, "ng-template", null, 0, i0.ɵɵtemplateRefExtractor);
        } }, dependencies: [A11yModule, i1.CdkTrapFocus], styles: [".tum-ui-popover-panel[_ngcontent-%COMP%]{animation:_ngcontent-%COMP%_tum-ui-popover-in .16s ease-out;transform-origin:top center}.tum-ui-popover-panel[data-placement=top][_ngcontent-%COMP%]{transform-origin:bottom center}.tum-ui-popover-panel[data-placement=left][_ngcontent-%COMP%]{transform-origin:center right}.tum-ui-popover-panel[data-placement=right][_ngcontent-%COMP%]{transform-origin:center left}@keyframes _ngcontent-%COMP%_tum-ui-popover-in{0%{opacity:0;transform:scale(.92)}to{opacity:1;transform:scale(1)}}@media(prefers-reduced-motion:reduce){.tum-ui-popover-panel[_ngcontent-%COMP%]{animation:none}}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiPopoverComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-popover', imports: [A11yModule], changeDetection: ChangeDetectionStrategy.OnPush, template: "<ng-template #panel>\n    <div\n        class=\"tum-ui-popover-panel tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:p-3 tum:text-text tum:shadow-lg\"\n        role=\"dialog\"\n        aria-modal=\"true\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.data-placement]=\"appliedPlacement()\"\n        tabindex=\"0\"\n        cdkTrapFocus\n        [cdkTrapFocusAutoCapture]=\"true\"\n    >\n        <ng-content />\n    </div>\n</ng-template>\n", styles: [".tum-ui-popover-panel{animation:tum-ui-popover-in .16s ease-out;transform-origin:top center}.tum-ui-popover-panel[data-placement=top]{transform-origin:bottom center}.tum-ui-popover-panel[data-placement=left]{transform-origin:center right}.tum-ui-popover-panel[data-placement=right]{transform-origin:center left}@keyframes tum-ui-popover-in{0%{opacity:0;transform:scale(.92)}to{opacity:1;transform:scale(1)}}@media(prefers-reduced-motion:reduce){.tum-ui-popover-panel{animation:none}}\n"] }]
    }], null, { placement: [{ type: i0.Input, args: [{ isSignal: true, alias: "placement", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: true }] }], openChange: [{ type: i0.Output, args: ["openChange"] }], panel: [{ type: i0.ViewChild, args: ['panel', { ...{ read: TemplateRef }, isSignal: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiPopoverComponent, { className: "TumUiPopoverComponent", filePath: "lib/popover/tum-ui-popover.component.ts", lineNumber: 28 }); })();

const _c0$c = ["*"];
function TumUiProgressBarComponent_ProjectionFallback_2_Conditional_0_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵtext(0);
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext(2);
    i0.ɵɵtextInterpolate2(" ", ctx_r0.normalizedValue(), "", ctx_r0.unit(), " ");
} }
function TumUiProgressBarComponent_ProjectionFallback_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵconditionalCreate(0, TumUiProgressBarComponent_ProjectionFallback_2_Conditional_0_Template, 1, 2);
} if (rf & 2) {
    const ctx_r0 = i0.ɵɵnextContext();
    i0.ɵɵconditional(ctx_r0.showValue() && ctx_r0.normalizedValue() !== 0 ? 0 : -1);
} }
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
    static ɵfac = function TumUiProgressBarComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiProgressBarComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiProgressBarComponent, selectors: [["tum-ui-progress-bar"]], hostAttrs: ["role", "progressbar", 1, "tum-ui-progress-bar", "tum:bg-border"], hostVars: 5, hostBindings: function TumUiProgressBarComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("data-size", ctx.size())("aria-valuemin", 0)("aria-valuemax", 100)("aria-valuenow", ctx.normalizedValue())("aria-label", ctx.ariaLabel());
        } }, inputs: { value: [1, "value"], ariaLabel: [1, "ariaLabel"], showValue: [1, "showValue"], size: [1, "size"], severity: [1, "severity"], unit: [1, "unit"] }, ngContentSelectors: _c0$c, decls: 4, vars: 3, consts: [[1, "tum-ui-progress-bar-value"], [1, "tum-ui-progress-bar-label"]], template: function TumUiProgressBarComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵdomElementStart(0, "div", 0)(1, "span", 1);
            i0.ɵɵprojection(2, 0, null, TumUiProgressBarComponent_ProjectionFallback_2_Template, 1, 1);
            i0.ɵɵdomElementEnd()();
        } if (rf & 2) {
            i0.ɵɵstyleProp("width", ctx.normalizedValue(), "%");
            i0.ɵɵattribute("data-severity", ctx.severity());
        } }, styles: ["[_nghost-%COMP%]{display:block;position:relative;overflow:hidden;height:calc(var(--%NS%tumaet-ui-spacing) * 5);border-radius:var(--%NS%tumaet-ui-radius-md)}[data-size=small][_nghost-%COMP%]{height:calc(var(--%NS%tumaet-ui-spacing) * 1.5)}[data-size=small][_nghost-%COMP%]   .tum-ui-progress-bar-label[_ngcontent-%COMP%]{display:none}.tum-ui-progress-bar-value[_ngcontent-%COMP%]{height:100%;width:0;position:absolute;display:flex;align-items:center;justify-content:center;overflow:hidden;background:var(--%NS%tumaet-ui-primary-color);transition:width 1s ease-in-out}.tum-ui-progress-bar-value[data-severity=success][_ngcontent-%COMP%]{background:var(--%NS%tumaet-ui-state-success)}.tum-ui-progress-bar-value[data-severity=warn][_ngcontent-%COMP%]{background:var(--%NS%tumaet-ui-state-warning)}.tum-ui-progress-bar-value[data-severity=danger][_ngcontent-%COMP%]{background:var(--%NS%tumaet-ui-state-danger)}.tum-ui-progress-bar-value[data-severity=info][_ngcontent-%COMP%]{background:var(--%NS%tumaet-ui-state-info)}.tum-ui-progress-bar-label[_ngcontent-%COMP%]{font-size:var(--%NS%tumaet-ui-font-size-xs);font-weight:600;color:var(--%NS%tumaet-ui-primary-contrast-color)}.tum-ui-progress-bar-value[data-severity=success][_ngcontent-%COMP%]   .tum-ui-progress-bar-label[_ngcontent-%COMP%]{color:var(--%NS%tumaet-ui-state-success-contrast)}.tum-ui-progress-bar-value[data-severity=warn][_ngcontent-%COMP%]   .tum-ui-progress-bar-label[_ngcontent-%COMP%]{color:var(--%NS%tumaet-ui-state-warning-contrast)}.tum-ui-progress-bar-value[data-severity=danger][_ngcontent-%COMP%]   .tum-ui-progress-bar-label[_ngcontent-%COMP%]{color:var(--%NS%tumaet-ui-state-danger-contrast)}.tum-ui-progress-bar-value[data-severity=info][_ngcontent-%COMP%]   .tum-ui-progress-bar-label[_ngcontent-%COMP%]{color:var(--%NS%tumaet-ui-state-info-contrast)}@media(prefers-reduced-motion:reduce){.tum-ui-progress-bar-value[_ngcontent-%COMP%]{transition:none}}@media(forced-colors:active){[_nghost-%COMP%]{border:1px solid CanvasText}.tum-ui-progress-bar-value[_ngcontent-%COMP%]{background:Highlight}.tum-ui-progress-bar-label[_ngcontent-%COMP%]{color:HighlightText}}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiProgressBarComponent, [{
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
    }], null, { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], showValue: [{ type: i0.Input, args: [{ isSignal: true, alias: "showValue", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], severity: [{ type: i0.Input, args: [{ isSignal: true, alias: "severity", required: false }] }], unit: [{ type: i0.Input, args: [{ isSignal: true, alias: "unit", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiProgressBarComponent, { className: "TumUiProgressBarComponent", filePath: "lib/progress-bar/tum-ui-progress-bar.component.ts", lineNumber: 21 }); })();

class TumUiProgressSpinnerComponent {
    ariaLabel = input(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []));
    static ɵfac = function TumUiProgressSpinnerComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiProgressSpinnerComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiProgressSpinnerComponent, selectors: [["tum-ui-progress-spinner"]], hostAttrs: ["role", "status", "aria-busy", "true", 1, "tum-ui-progress-spinner"], hostVars: 1, hostBindings: function TumUiProgressSpinnerComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("aria-label", ctx.ariaLabel());
        } }, inputs: { ariaLabel: [1, "ariaLabel"] }, decls: 2, vars: 0, consts: [["viewBox", "25 25 50 50", 1, "tum-ui-progress-spinner-spin"], ["cx", "50", "cy", "50", "r", "20", "fill", "none", "stroke-width", "2", "stroke-miterlimit", "10", 1, "tum-ui-progress-spinner-circle"]], template: function TumUiProgressSpinnerComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵnamespaceSVG();
            i0.ɵɵdomElementStart(0, "svg", 0);
            i0.ɵɵdomElement(1, "circle", 1);
            i0.ɵɵdomElementEnd();
        } }, styles: ["[_nghost-%COMP%]{position:relative;margin:0 auto;width:100px;height:100px;display:inline-block}[_nghost-%COMP%]:before{content:\"\";display:block;padding-top:100%}.tum-ui-progress-spinner-spin[_ngcontent-%COMP%]{height:100%;width:100%;transform-origin:center center;position:absolute;inset:0;margin:auto;animation:_ngcontent-%COMP%_tum-ui-progress-spinner-rotate 2s linear infinite}.tum-ui-progress-spinner-circle[_ngcontent-%COMP%]{stroke-dasharray:89,200;stroke-dashoffset:0;stroke:var(--%NS%tumaet-ui-primary-color);stroke-linecap:round;animation:_ngcontent-%COMP%_tum-ui-progress-spinner-dash 1.5s ease-in-out infinite}@keyframes _ngcontent-%COMP%_tum-ui-progress-spinner-rotate{to{transform:rotate(360deg)}}@keyframes _ngcontent-%COMP%_tum-ui-progress-spinner-dash{0%{stroke-dasharray:1,200;stroke-dashoffset:0}50%{stroke-dasharray:89,200;stroke-dashoffset:-35px}to{stroke-dasharray:89,200;stroke-dashoffset:-124px}}@media(prefers-reduced-motion:reduce){.tum-ui-progress-spinner-spin[_ngcontent-%COMP%], .tum-ui-progress-spinner-circle[_ngcontent-%COMP%]{animation:none}}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiProgressSpinnerComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-progress-spinner', host: {
                    class: 'tum-ui-progress-spinner',
                    role: 'status',
                    'aria-busy': 'true',
                    '[attr.aria-label]': 'ariaLabel()',
                }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<svg class=\"tum-ui-progress-spinner-spin\" viewBox=\"25 25 50 50\">\n    <circle class=\"tum-ui-progress-spinner-circle\" cx=\"50\" cy=\"50\" r=\"20\" fill=\"none\" stroke-width=\"2\" stroke-miterlimit=\"10\" />\n</svg>\n", styles: [":host{position:relative;margin:0 auto;width:100px;height:100px;display:inline-block}:host:before{content:\"\";display:block;padding-top:100%}.tum-ui-progress-spinner-spin{height:100%;width:100%;transform-origin:center center;position:absolute;inset:0;margin:auto;animation:tum-ui-progress-spinner-rotate 2s linear infinite}.tum-ui-progress-spinner-circle{stroke-dasharray:89,200;stroke-dashoffset:0;stroke:var(--tumaet-ui-primary-color);stroke-linecap:round;animation:tum-ui-progress-spinner-dash 1.5s ease-in-out infinite}@keyframes tum-ui-progress-spinner-rotate{to{transform:rotate(360deg)}}@keyframes tum-ui-progress-spinner-dash{0%{stroke-dasharray:1,200;stroke-dashoffset:0}50%{stroke-dasharray:89,200;stroke-dashoffset:-35px}to{stroke-dasharray:89,200;stroke-dashoffset:-124px}}@media(prefers-reduced-motion:reduce){.tum-ui-progress-spinner-spin,.tum-ui-progress-spinner-circle{animation:none}}\n"] }]
    }], null, { ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiProgressSpinnerComponent, { className: "TumUiProgressSpinnerComponent", filePath: "lib/progress-spinner/tum-ui-progress-spinner.component.ts", lineNumber: 15 }); })();

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
    static ɵfac = function TumUiRadioButtonComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiRadioButtonComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiRadioButtonComponent, selectors: [["tum-ui-radio-button"]], hostAttrs: [1, "tum-ui-radio-button"], inputs: { value: [1, "value"], name: [1, "name"], inputId: [1, "inputId"], disabled: [1, "disabled"], ariaLabel: [1, "ariaLabel"] }, outputs: { selected: "selected" }, features: [i0.ɵɵProvidersFeature([{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiRadioButtonComponent), multi: true }])], decls: 3, vars: 9, consts: [["type", "radio", 1, "tum-ui-radio-button-input", 3, "click", "blur", "id", "checked", "disabled"], ["aria-hidden", "true", 1, "tum-ui-radio-button-box"], [1, "tum-ui-radio-button-icon"]], template: function TumUiRadioButtonComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵdomElementStart(0, "input", 0);
            i0.ɵɵdomListener("click", function TumUiRadioButtonComponent_Template_input_click_0_listener($event) { return ctx.onInputClick($event); })("blur", function TumUiRadioButtonComponent_Template_input_blur_0_listener() { return ctx.onBlur(); });
            i0.ɵɵdomElementEnd();
            i0.ɵɵdomElementStart(1, "div", 1);
            i0.ɵɵdomElement(2, "span", 2);
            i0.ɵɵdomElementEnd();
        } if (rf & 2) {
            i0.ɵɵdomProperty("id", ctx.inputId())("checked", ctx.isChecked())("disabled", ctx.isDisabled());
            i0.ɵɵattribute("name", ctx.name())("aria-label", ctx.ariaLabel());
            i0.ɵɵadvance();
            i0.ɵɵclassMap(ctx.boxClasses());
            i0.ɵɵadvance();
            i0.ɵɵclassMap(ctx.iconClasses());
        } }, styles: ["[_nghost-%COMP%]{position:relative;display:inline-flex;width:calc(var(--%NS%tumaet-ui-spacing, .25rem) * 5);height:calc(var(--%NS%tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-radio-button-input[_ngcontent-%COMP%]{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none;border-radius:50%}.tum-ui-radio-button-input[_ngcontent-%COMP%]:disabled{cursor:default}.tum-ui-radio-button-box[_ngcontent-%COMP%]{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:50%;box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--%NS%tumaet-ui-text-color) 5%,transparent);transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-radio-button-box[_ngcontent-%COMP%]:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-radio-button-icon[_ngcontent-%COMP%]{width:calc(var(--%NS%tumaet-ui-spacing, .25rem) * 3);height:calc(var(--%NS%tumaet-ui-spacing, .25rem) * 3);border-radius:50%;visibility:hidden}[_nghost-%COMP%]:has(.tum-ui-radio-button-input:checked)   .tum-ui-radio-button-icon[_ngcontent-%COMP%]{visibility:visible}[_nghost-%COMP%]:has(.tum-ui-radio-button-input:hover:not(:disabled))   .tum-ui-radio-button-box[_ngcontent-%COMP%]:after{background-color:color-mix(in srgb,var(--%NS%tumaet-ui-text-color) 5%,transparent)}[_nghost-%COMP%]:has(.tum-ui-radio-button-input:focus-visible)   .tum-ui-radio-button-box[_ngcontent-%COMP%]{outline:2px solid var(--%NS%tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-radio-button-input[_ngcontent-%COMP%]{appearance:auto;opacity:1}.tum-ui-radio-button-box[_ngcontent-%COMP%]{display:none}}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiRadioButtonComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-radio-button', host: { class: 'tum-ui-radio-button' }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiRadioButtonComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<input\n    type=\"radio\"\n    class=\"tum-ui-radio-button-input\"\n    [id]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [checked]=\"isChecked()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    (click)=\"onInputClick($event)\"\n    (blur)=\"onBlur()\"\n/>\n<div class=\"tum-ui-radio-button-box\" [class]=\"boxClasses()\" aria-hidden=\"true\">\n    <span class=\"tum-ui-radio-button-icon\" [class]=\"iconClasses()\"></span>\n</div>\n", styles: [":host{position:relative;display:inline-flex;width:calc(var(--tumaet-ui-spacing, .25rem) * 5);height:calc(var(--tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-radio-button-input{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none;border-radius:50%}.tum-ui-radio-button-input:disabled{cursor:default}.tum-ui-radio-button-box{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:50%;box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent);transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-radio-button-box:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-radio-button-icon{width:calc(var(--tumaet-ui-spacing, .25rem) * 3);height:calc(var(--tumaet-ui-spacing, .25rem) * 3);border-radius:50%;visibility:hidden}:host:has(.tum-ui-radio-button-input:checked) .tum-ui-radio-button-icon{visibility:visible}:host:has(.tum-ui-radio-button-input:hover:not(:disabled)) .tum-ui-radio-button-box:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:has(.tum-ui-radio-button-input:focus-visible) .tum-ui-radio-button-box{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-radio-button-input{appearance:auto;opacity:1}.tum-ui-radio-button-box{display:none}}\n"] }]
    }], null, { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }], name: [{ type: i0.Input, args: [{ isSignal: true, alias: "name", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], selected: [{ type: i0.Output, args: ["selected"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiRadioButtonComponent, { className: "TumUiRadioButtonComponent", filePath: "lib/radio-button/tum-ui-radio-button.component.ts", lineNumber: 20 }); })();

const _c0$b = ["searchInput"];
function TumUiSearchFieldComponent_Conditional_5_Template(rf, ctx) { if (rf & 1) {
    const _r3 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "button", 4);
    i0.ɵɵpipe(1, "tumUiTranslate");
    i0.ɵɵlistener("click", function TumUiSearchFieldComponent_Conditional_5_Template_button_click_0_listener() { i0.ɵɵrestoreView(_r3); const ctx_r3 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r3.clear()); });
    i0.ɵɵelement(2, "fa-icon", 5);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r3 = i0.ɵɵnextContext();
    i0.ɵɵproperty("disabled", ctx_r3.disabled());
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(1, 3, "tumUi.searchField.clear"));
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r3.faXmark);
} }
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
    static ɵfac = function TumUiSearchFieldComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiSearchFieldComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiSearchFieldComponent, selectors: [["tum-ui-search-field"]], viewQuery: function TumUiSearchFieldComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.inputElement, _c0$b, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, hostAttrs: [1, "tum-ui-search-field"], inputs: { value: [1, "value"], placeholder: [1, "placeholder"], ariaLabel: [1, "ariaLabel"], disabled: [1, "disabled"], size: [1, "size"] }, outputs: { value: "valueChange" }, decls: 6, vars: 11, consts: [["searchInput", ""], ["aria-hidden", "true", 1, "tum-ui-search-field-icon", "tum:text-muted", 3, "icon"], ["tumUiInput", "", "type", "search", 3, "input", "tumUiInputSize", "value", "disabled", "placeholder"], ["type", "button", 1, "tum-ui-search-field-clear", "tum:cursor-pointer", "tum:appearance-none", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:text-muted", "tum:hover:text-text", 3, "disabled"], ["type", "button", 1, "tum-ui-search-field-clear", "tum:cursor-pointer", "tum:appearance-none", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:text-muted", "tum:hover:text-text", 3, "click", "disabled"], [3, "icon"]], template: function TumUiSearchFieldComponent_Template(rf, ctx) { if (rf & 1) {
            const _r1 = i0.ɵɵgetCurrentView();
            i0.ɵɵelement(0, "fa-icon", 1);
            i0.ɵɵelementStart(1, "input", 2, 0);
            i0.ɵɵpipe(3, "tumUiTranslate");
            i0.ɵɵpipe(4, "tumUiTranslate");
            i0.ɵɵlistener("input", function TumUiSearchFieldComponent_Template_input_input_1_listener() { i0.ɵɵrestoreView(_r1); const searchInput_r2 = i0.ɵɵreference(2); return i0.ɵɵresetView(ctx.onInput(searchInput_r2.value)); });
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(5, TumUiSearchFieldComponent_Conditional_5_Template, 3, 5, "button", 3);
        } if (rf & 2) {
            i0.ɵɵproperty("icon", ctx.faMagnifyingGlass);
            i0.ɵɵadvance();
            i0.ɵɵproperty("tumUiInputSize", ctx.size())("value", ctx.value())("disabled", ctx.disabled())("placeholder", i0.ɵɵpipeBind1(3, 7, ctx.placeholder()));
            i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(4, 9, ctx.accessibleNameKey()));
            i0.ɵɵadvance(4);
            i0.ɵɵconditional(ctx.value() ? 5 : -1);
        } }, dependencies: [FaIconComponent, TumUiInputDirective, TumUiTranslatePipe], styles: ["[_nghost-%COMP%]{position:relative;display:block}.tum-ui-search-field-icon[_ngcontent-%COMP%], .tum-ui-search-field-clear[_ngcontent-%COMP%]{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}.tum-ui-search-field-icon[_ngcontent-%COMP%]{inset-inline-start:calc(var(--%NS%tumaet-ui-spacing) * 3);pointer-events:none}.tum-ui-search-field-clear[_ngcontent-%COMP%]{inset-inline-end:calc(var(--%NS%tumaet-ui-spacing) * 2.5)}.tum-ui-input[_ngcontent-%COMP%]{width:100%;padding-inline-start:calc(var(--%NS%tumaet-ui-spacing) * 10);padding-inline-end:calc(var(--%NS%tumaet-ui-spacing) * 8)}.tum-ui-input[_ngcontent-%COMP%]::-webkit-search-cancel-button{display:none}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiSearchFieldComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-search-field', imports: [FaIconComponent, TumUiInputDirective, TumUiTranslatePipe], host: { class: 'tum-ui-search-field' }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<fa-icon [icon]=\"faMagnifyingGlass\" class=\"tum-ui-search-field-icon tum:text-muted\" aria-hidden=\"true\" />\n\n<input\n    #searchInput\n    tumUiInput\n    type=\"search\"\n    [tumUiInputSize]=\"size()\"\n    [value]=\"value()\"\n    [disabled]=\"disabled()\"\n    [placeholder]=\"placeholder() | tumUiTranslate\"\n    [attr.aria-label]=\"accessibleNameKey() | tumUiTranslate\"\n    (input)=\"onInput(searchInput.value)\"\n/>\n\n@if (value()) {\n    <button\n        type=\"button\"\n        class=\"tum-ui-search-field-clear tum:cursor-pointer tum:appearance-none tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text\"\n        [disabled]=\"disabled()\"\n        [attr.aria-label]=\"'tumUi.searchField.clear' | tumUiTranslate\"\n        (click)=\"clear()\"\n    >\n        <fa-icon [icon]=\"faXmark\" />\n    </button>\n}\n", styles: [":host{position:relative;display:block}.tum-ui-search-field-icon,.tum-ui-search-field-clear{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}.tum-ui-search-field-icon{inset-inline-start:calc(var(--tumaet-ui-spacing) * 3);pointer-events:none}.tum-ui-search-field-clear{inset-inline-end:calc(var(--tumaet-ui-spacing) * 2.5)}.tum-ui-input{width:100%;padding-inline-start:calc(var(--tumaet-ui-spacing) * 10);padding-inline-end:calc(var(--tumaet-ui-spacing) * 8)}.tum-ui-input::-webkit-search-cancel-button{display:none}\n"] }]
    }], null, { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }, { type: i0.Output, args: ["valueChange"] }], placeholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "placeholder", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], inputElement: [{ type: i0.ViewChild, args: ['searchInput', { isSignal: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiSearchFieldComponent, { className: "TumUiSearchFieldComponent", filePath: "lib/search-field/tum-ui-search-field.component.ts", lineNumber: 17 }); })();

const _c0$a = ["trigger"];
const _c1$1 = ["panel"];
const _c2 = ["filterInput"];
function TumUiSelectComponent_Conditional_7_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "button", 9);
    i0.ɵɵpipe(1, "tumUiTranslate");
    i0.ɵɵlistener("click", function TumUiSelectComponent_Conditional_7_Template_button_click_0_listener($event) { i0.ɵɵrestoreView(_r1); const ctx_r1 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r1.clear($event)); });
    i0.ɵɵelement(2, "fa-icon", 7);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵattribute("aria-label", ctx_r1.clearAriaLabel() ?? i0.ɵɵpipeBind1(1, 2, "tumUi.select.clear"));
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r1.faXmark);
} }
function TumUiSelectComponent_ng_template_8_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    const _r3 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "div", 11)(1, "input", 15, 2);
    i0.ɵɵpipe(3, "tumUiTranslate");
    i0.ɵɵpipe(4, "tumUiTranslate");
    i0.ɵɵlistener("input", function TumUiSelectComponent_ng_template_8_Conditional_1_Template_input_input_1_listener($event) { i0.ɵɵrestoreView(_r3); const ctx_r1 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r1.onFilterInput($event)); })("keydown", function TumUiSelectComponent_ng_template_8_Conditional_1_Template_input_keydown_1_listener($event) { i0.ɵɵrestoreView(_r3); const ctx_r1 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r1.onFilterKeydown($event)); });
    i0.ɵɵelementEnd()();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext(2);
    i0.ɵɵadvance();
    i0.ɵɵproperty("value", ctx_r1.filterText());
    i0.ɵɵattribute("placeholder", ctx_r1.filterPlaceholder() ?? i0.ɵɵpipeBind1(3, 5, "tumUi.select.filter"))("aria-label", ctx_r1.filterAriaLabel() ?? i0.ɵɵpipeBind1(4, 7, "tumUi.select.filter"))("aria-controls", ctx_r1.listboxId)("aria-activedescendant", ctx_r1.activeOptionId());
} }
function TumUiSelectComponent_ng_template_8_For_4_Conditional_3_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "fa-icon", 17);
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext(3);
    i0.ɵɵproperty("icon", ctx_r1.faCheck);
} }
function TumUiSelectComponent_ng_template_8_For_4_Template(rf, ctx) { if (rf & 1) {
    const _r4 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "li", 16);
    i0.ɵɵlistener("click", function TumUiSelectComponent_ng_template_8_For_4_Template_li_click_0_listener() { const option_r5 = i0.ɵɵrestoreView(_r4).$implicit; const ctx_r1 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r1.selectOption(option_r5)); })("mouseenter", function TumUiSelectComponent_ng_template_8_For_4_Template_li_mouseenter_0_listener() { const ɵ$index_31_r6 = i0.ɵɵrestoreView(_r4).$index; const ctx_r1 = i0.ɵɵnextContext(2); return i0.ɵɵresetView(ctx_r1.setActive(ɵ$index_31_r6)); });
    i0.ɵɵelementStart(1, "span", 5);
    i0.ɵɵtext(2);
    i0.ɵɵelementEnd();
    i0.ɵɵconditionalCreate(3, TumUiSelectComponent_ng_template_8_For_4_Conditional_3_Template, 1, 1, "fa-icon", 17);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const option_r5 = ctx.$implicit;
    const ɵ$index_31_r6 = ctx.$index;
    const ctx_r1 = i0.ɵɵnextContext(2);
    i0.ɵɵclassMap(ctx_r1.optionClasses(option_r5, ɵ$index_31_r6));
    i0.ɵɵproperty("id", ctx_r1.optionId(ɵ$index_31_r6));
    i0.ɵɵattribute("aria-selected", ctx_r1.isSelected(option_r5) ? "true" : "false");
    i0.ɵɵadvance(2);
    i0.ɵɵtextInterpolate(ctx_r1.label(option_r5));
    i0.ɵɵadvance();
    i0.ɵɵconditional(ctx_r1.isSelected(option_r5) ? 3 : -1);
} }
function TumUiSelectComponent_ng_template_8_ForEmpty_5_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "li", 14);
    i0.ɵɵtext(1);
    i0.ɵɵpipe(2, "tumUiTranslate");
    i0.ɵɵpipe(3, "tumUiTranslate");
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext(2);
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate1(" ", ctx_r1.isFiltering() ? i0.ɵɵpipeBind1(2, 1, "tumUi.select.noResults") : ctx_r1.emptyMessage() ?? i0.ɵɵpipeBind1(3, 3, "tumUi.select.empty"), " ");
} }
function TumUiSelectComponent_ng_template_8_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "div", 10);
    i0.ɵɵconditionalCreate(1, TumUiSelectComponent_ng_template_8_Conditional_1_Template, 5, 9, "div", 11);
    i0.ɵɵelementStart(2, "ul", 12);
    i0.ɵɵrepeaterCreate(3, TumUiSelectComponent_ng_template_8_For_4_Template, 4, 6, "li", 13, i0.ɵɵrepeaterTrackByIndex, false, TumUiSelectComponent_ng_template_8_ForEmpty_5_Template, 4, 5, "li", 14);
    i0.ɵɵelementEnd()();
} if (rf & 2) {
    const ctx_r1 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵconditional(ctx_r1.filter() ? 1 : -1);
    i0.ɵɵadvance();
    i0.ɵɵproperty("id", ctx_r1.listboxId);
    i0.ɵɵattribute("aria-label", ctx_r1.ariaLabel());
    i0.ɵɵadvance();
    i0.ɵɵrepeater(ctx_r1.visibleOptions());
} }
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
    static ɵfac = function TumUiSelectComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiSelectComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiSelectComponent, selectors: [["tum-ui-select"]], viewQuery: function TumUiSelectComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.trigger, _c0$a, 5)(ctx.panel, _c1$1, 5, TemplateRef)(ctx.filterInput, _c2, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance(3);
        } }, hostAttrs: [1, "tum-ui-select"], inputs: { options: [1, "options"], optionLabel: [1, "optionLabel"], optionValue: [1, "optionValue"], placeholder: [1, "placeholder"], disabled: [1, "disabled"], showClear: [1, "showClear"], filter: [1, "filter"], filterBy: [1, "filterBy"], filterPlaceholder: [1, "filterPlaceholder"], size: [1, "size"], inputId: [1, "inputId"], name: [1, "name"], ariaLabel: [1, "ariaLabel"], clearAriaLabel: [1, "clearAriaLabel"], emptyMessage: [1, "emptyMessage"], filterAriaLabel: [1, "filterAriaLabel"] }, outputs: { selectionChange: "selectionChange" }, features: [i0.ɵɵProvidersFeature([{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectComponent), multi: true }])], decls: 10, vars: 16, consts: [["trigger", ""], ["panel", ""], ["filterInput", ""], [1, "tum:relative", "tum:inline-flex", "tum:w-full"], ["type", "button", "role", "combobox", "aria-haspopup", "listbox", "aria-autocomplete", "none", 3, "click", "keydown", "id", "disabled"], [1, "tum:flex-1", "tum:truncate"], [1, "tum:pointer-events-none", "tum:absolute", "tum:inset-y-0", "tum:end-0", "tum:flex", "tum:w-10", "tum:items-center", "tum:justify-center", "tum:text-muted"], [3, "icon"], ["type", "button", 1, "tum:absolute", "tum:inset-y-0", "tum:end-10", "tum:flex", "tum:w-6", "tum:appearance-none", "tum:items-center", "tum:justify-center", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:text-muted", "tum:hover:text-text-hover"], ["type", "button", 1, "tum:absolute", "tum:inset-y-0", "tum:end-10", "tum:flex", "tum:w-6", "tum:appearance-none", "tum:items-center", "tum:justify-center", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:text-muted", "tum:hover:text-text-hover", 3, "click"], [1, "tum-ui-select-panel", "tum:box-border", "tum:w-full", "tum:rounded-md", "tum:border", "tum:border-border", "tum:bg-overlay-background", "tum:text-text", "tum:shadow-md"], [1, "tum:border-b", "tum:border-border", "tum:p-1"], ["role", "listbox", 1, "tum:m-0", "tum:flex", "tum:max-h-60", "tum:list-none", "tum:flex-col", "tum:gap-0.5", "tum:overflow-y-auto", "tum:p-1", "tum:outline-none", 3, "id"], ["role", "option", 3, "id", "class"], ["role", "option", "aria-selected", "false", "aria-disabled", "true", 1, "tum:px-3", "tum:py-2", "tum:text-muted"], ["type", "text", 1, "tum-ui-select-filter", "tum:box-border", "tum:w-full", "tum:appearance-none", "tum:rounded-sm", "tum:border", "tum:border-control-border", "tum:bg-control-background", "tum:px-2", "tum:py-1.5", "tum:text-sm", "tum:text-text", "tum:placeholder:text-muted", "tum:focus-visible:outline", "tum:focus-visible:outline-2", "tum:focus-visible:outline-focus", 3, "input", "keydown", "value"], ["role", "option", 3, "click", "mouseenter", "id"], [1, "tum:ms-2", "tum:shrink-0", 3, "icon"]], template: function TumUiSelectComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵelementStart(0, "div", 3)(1, "button", 4, 0);
            i0.ɵɵlistener("click", function TumUiSelectComponent_Template_button_click_1_listener() { return ctx.toggle(); })("keydown", function TumUiSelectComponent_Template_button_keydown_1_listener($event) { return ctx.onTriggerKeydown($event); });
            i0.ɵɵelementStart(3, "span", 5);
            i0.ɵɵtext(4);
            i0.ɵɵelementEnd()();
            i0.ɵɵelementStart(5, "span", 6);
            i0.ɵɵelement(6, "fa-icon", 7);
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(7, TumUiSelectComponent_Conditional_7_Template, 3, 4, "button", 8);
            i0.ɵɵelementEnd();
            i0.ɵɵtemplate(8, TumUiSelectComponent_ng_template_8_Template, 6, 4, "ng-template", null, 1, i0.ɵɵtemplateRefExtractor);
        } if (rf & 2) {
            i0.ɵɵadvance();
            i0.ɵɵclassMap(ctx.triggerClasses());
            i0.ɵɵproperty("id", ctx.resolvedInputId())("disabled", ctx.isDisabled());
            i0.ɵɵattribute("name", ctx.name())("aria-expanded", ctx.isOpen() ? "true" : "false")("aria-controls", ctx.isOpen() ? ctx.listboxId : null)("aria-activedescendant", ctx.isOpen() ? ctx.activeOptionId() : null)("aria-label", ctx.ariaLabel())("aria-describedby", ctx.describedBy())("aria-invalid", ctx.isInvalid() ? "true" : null);
            i0.ɵɵadvance(2);
            i0.ɵɵclassMap(!ctx.hasSelection() ? "tum:text-muted" : "");
            i0.ɵɵadvance();
            i0.ɵɵtextInterpolate(ctx.displayLabel());
            i0.ɵɵadvance(2);
            i0.ɵɵproperty("icon", ctx.faChevronDown);
            i0.ɵɵadvance();
            i0.ɵɵconditional(ctx.showClearButton() ? 7 : -1);
        } }, dependencies: [FaIconComponent, TumUiTranslatePipe], styles: ["[_nghost-%COMP%]{display:inline-flex;max-width:100%}.tum-ui-select-trigger[_ngcontent-%COMP%]{border-radius:var(--%NS%tumaet-ui-radius-md);appearance:none}.tum-ui-select-trigger[_ngcontent-%COMP%]:focus-visible{outline:2px solid var(--%NS%tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-trigger[_ngcontent-%COMP%]:disabled{opacity:1}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiSelectComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-select', imports: [FaIconComponent, TumUiTranslatePipe], host: {
                    // The application stylesheet excludes TUM UI controls from the JHipster validity accent by this class.
                    class: 'tum-ui-select',
                }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<div class=\"tum:relative tum:inline-flex tum:w-full\">\n    <button\n        #trigger\n        type=\"button\"\n        [id]=\"resolvedInputId()\"\n        [class]=\"triggerClasses()\"\n        [disabled]=\"isDisabled()\"\n        [attr.name]=\"name()\"\n        role=\"combobox\"\n        aria-haspopup=\"listbox\"\n        aria-autocomplete=\"none\"\n        [attr.aria-expanded]=\"isOpen() ? 'true' : 'false'\"\n        [attr.aria-controls]=\"isOpen() ? listboxId : null\"\n        [attr.aria-activedescendant]=\"isOpen() ? activeOptionId() : null\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-describedby]=\"describedBy()\"\n        [attr.aria-invalid]=\"isInvalid() ? 'true' : null\"\n        (click)=\"toggle()\"\n        (keydown)=\"onTriggerKeydown($event)\"\n    >\n        <span class=\"tum:flex-1 tum:truncate\" [class]=\"!hasSelection() ? 'tum:text-muted' : ''\">{{ displayLabel() }}</span>\n    </button>\n    <span class=\"tum:pointer-events-none tum:absolute tum:inset-y-0 tum:end-0 tum:flex tum:w-10 tum:items-center tum:justify-center tum:text-muted\">\n        <fa-icon [icon]=\"faChevronDown\" />\n    </span>\n\n    @if (showClearButton()) {\n        <button\n            type=\"button\"\n            class=\"tum:absolute tum:inset-y-0 tum:end-10 tum:flex tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text-hover\"\n            (click)=\"clear($event)\"\n            [attr.aria-label]=\"clearAriaLabel() ?? ('tumUi.select.clear' | tumUiTranslate)\"\n        >\n            <fa-icon [icon]=\"faXmark\" />\n        </button>\n    }\n</div>\n\n<ng-template #panel>\n    <div class=\"tum-ui-select-panel tum:box-border tum:w-full tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-md\">\n        @if (filter()) {\n            <div class=\"tum:border-b tum:border-border tum:p-1\">\n                <input\n                    #filterInput\n                    type=\"text\"\n                    class=\"tum-ui-select-filter tum:box-border tum:w-full tum:appearance-none tum:rounded-sm tum:border tum:border-control-border tum:bg-control-background tum:px-2 tum:py-1.5 tum:text-sm tum:text-text tum:placeholder:text-muted tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus\"\n                    [value]=\"filterText()\"\n                    [attr.placeholder]=\"filterPlaceholder() ?? ('tumUi.select.filter' | tumUiTranslate)\"\n                    [attr.aria-label]=\"filterAriaLabel() ?? ('tumUi.select.filter' | tumUiTranslate)\"\n                    [attr.aria-controls]=\"listboxId\"\n                    [attr.aria-activedescendant]=\"activeOptionId()\"\n                    (input)=\"onFilterInput($event)\"\n                    (keydown)=\"onFilterKeydown($event)\"\n                />\n            </div>\n        }\n        <ul\n            [id]=\"listboxId\"\n            role=\"listbox\"\n            [attr.aria-label]=\"ariaLabel()\"\n            class=\"tum:m-0 tum:flex tum:max-h-60 tum:list-none tum:flex-col tum:gap-0.5 tum:overflow-y-auto tum:p-1 tum:outline-none\"\n        >\n            @for (option of visibleOptions(); track $index; let i = $index) {\n                <li\n                    [id]=\"optionId(i)\"\n                    role=\"option\"\n                    [attr.aria-selected]=\"isSelected(option) ? 'true' : 'false'\"\n                    [class]=\"optionClasses(option, i)\"\n                    (click)=\"selectOption(option)\"\n                    (mouseenter)=\"setActive(i)\"\n                >\n                    <span class=\"tum:flex-1 tum:truncate\">{{ label(option) }}</span>\n                    @if (isSelected(option)) {\n                        <fa-icon [icon]=\"faCheck\" class=\"tum:ms-2 tum:shrink-0\" />\n                    }\n                </li>\n            } @empty {\n                <li role=\"option\" aria-selected=\"false\" aria-disabled=\"true\" class=\"tum:px-3 tum:py-2 tum:text-muted\">\n                    {{ isFiltering() ? ('tumUi.select.noResults' | tumUiTranslate) : (emptyMessage() ?? ('tumUi.select.empty' | tumUiTranslate)) }}\n                </li>\n            }\n        </ul>\n    </div>\n</ng-template>\n", styles: [":host{display:inline-flex;max-width:100%}.tum-ui-select-trigger{border-radius:var(--tumaet-ui-radius-md);appearance:none}.tum-ui-select-trigger:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-trigger:disabled{opacity:1}\n"] }]
    }], () => [], { options: [{ type: i0.Input, args: [{ isSignal: true, alias: "options", required: false }] }], optionLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "optionLabel", required: false }] }], optionValue: [{ type: i0.Input, args: [{ isSignal: true, alias: "optionValue", required: false }] }], placeholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "placeholder", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], showClear: [{ type: i0.Input, args: [{ isSignal: true, alias: "showClear", required: false }] }], filter: [{ type: i0.Input, args: [{ isSignal: true, alias: "filter", required: false }] }], filterBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "filterBy", required: false }] }], filterPlaceholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "filterPlaceholder", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], name: [{ type: i0.Input, args: [{ isSignal: true, alias: "name", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], clearAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "clearAriaLabel", required: false }] }], emptyMessage: [{ type: i0.Input, args: [{ isSignal: true, alias: "emptyMessage", required: false }] }], filterAriaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "filterAriaLabel", required: false }] }], selectionChange: [{ type: i0.Output, args: ["selectionChange"] }], trigger: [{ type: i0.ViewChild, args: ['trigger', { isSignal: true }] }], panel: [{ type: i0.ViewChild, args: ['panel', { ...{ read: TemplateRef }, isSignal: true }] }], filterInput: [{ type: i0.ViewChild, args: ['filterInput', { isSignal: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiSelectComponent, { className: "TumUiSelectComponent", filePath: "lib/select/tum-ui-select.component.ts", lineNumber: 54 }); })();

const _c0$9 = a0 => ({ $implicit: a0 });
const _forTrack0$1 = ($index, $item) => $item.value;
function TumUiSelectButtonComponent_For_1_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementContainer(0, 2);
} if (rf & 2) {
    const option_r2 = i0.ɵɵnextContext().$implicit;
    i0.ɵɵproperty("ngTemplateOutlet", ctx)("ngTemplateOutletContext", i0.ɵɵpureFunction1(2, _c0$9, option_r2.raw));
} }
function TumUiSelectButtonComponent_For_1_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵtext(0);
} if (rf & 2) {
    const option_r2 = i0.ɵɵnextContext().$implicit;
    i0.ɵɵtextInterpolate1(" ", option_r2.label, " ");
} }
function TumUiSelectButtonComponent_For_1_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "button", 1);
    i0.ɵɵlistener("click", function TumUiSelectButtonComponent_For_1_Template_button_click_0_listener() { const option_r2 = i0.ɵɵrestoreView(_r1).$implicit; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.select(option_r2)); });
    i0.ɵɵconditionalCreate(1, TumUiSelectButtonComponent_For_1_Conditional_1_Template, 1, 4, "ng-container", 2)(2, TumUiSelectButtonComponent_For_1_Conditional_2_Template, 1, 1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    let tmp_13_0;
    const option_r2 = ctx.$implicit;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵclassMap(ctx_r2.optionClasses(option_r2.selected));
    i0.ɵɵproperty("disabled", ctx_r2.effectiveDisabled());
    i0.ɵɵattribute("aria-pressed", option_r2.selected);
    i0.ɵɵadvance();
    i0.ɵɵconditional((tmp_13_0 = ctx_r2.itemTemplate()) ? 1 : 2, tmp_13_0);
} }
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
    static ɵfac = function TumUiSelectButtonComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiSelectButtonComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiSelectButtonComponent, selectors: [["tum-ui-select-button"]], hostAttrs: ["role", "group", 1, "tum-ui-select-button"], hostVars: 1, hostBindings: function TumUiSelectButtonComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("aria-disabled", ctx.effectiveDisabled() || null);
        } }, inputs: { options: [1, "options"], optionLabel: [1, "optionLabel"], optionValue: [1, "optionValue"], size: [1, "size"], allowEmpty: [1, "allowEmpty"], disabled: [1, "disabled"], itemTemplate: [1, "itemTemplate"] }, outputs: { changed: "changed" }, features: [i0.ɵɵProvidersFeature([{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectButtonComponent), multi: true }])], decls: 2, vars: 0, consts: [["type", "button", 3, "class", "disabled"], ["type", "button", 3, "click", "disabled"], [3, "ngTemplateOutlet", "ngTemplateOutletContext"]], template: function TumUiSelectButtonComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵrepeaterCreate(0, TumUiSelectButtonComponent_For_1_Template, 3, 5, "button", 0, _forTrack0$1);
        } if (rf & 2) {
            i0.ɵɵrepeater(ctx.normalizedOptions());
        } }, dependencies: [NgTemplateOutlet], styles: ["[_nghost-%COMP%]{display:inline-flex;vertical-align:bottom;-webkit-user-select:none;user-select:none;border-radius:var(--%NS%tumaet-ui-radius-md);outline-color:transparent}.tum-ui-select-button-option[_ngcontent-%COMP%]{position:relative;appearance:none;display:inline-flex;align-items:center;justify-content:center;gap:calc(var(--%NS%tumaet-ui-spacing) * 2);padding:calc(var(--%NS%tumaet-ui-spacing) * 2) calc(var(--%NS%tumaet-ui-spacing) * 4);font-weight:500;white-space:nowrap;cursor:pointer;border-block-width:1px;border-inline-start-width:0;border-inline-end-width:1px;border-style:solid;border-radius:0;transition:background-color .2s,color .2s,border-color .2s,box-shadow .2s,outline-color .2s;outline-color:transparent}.tum-ui-select-button-option[_ngcontent-%COMP%]:first-child{border-inline-start-width:1px;border-start-start-radius:var(--%NS%tumaet-ui-radius-md);border-end-start-radius:var(--%NS%tumaet-ui-radius-md)}.tum-ui-select-button-option[_ngcontent-%COMP%]:last-child{border-start-end-radius:var(--%NS%tumaet-ui-radius-md);border-end-end-radius:var(--%NS%tumaet-ui-radius-md)}.tum-ui-select-button-option[_ngcontent-%COMP%]:disabled{cursor:default}.tum-ui-select-button-option[_ngcontent-%COMP%]:focus-visible{position:relative;z-index:1;outline:2px solid var(--%NS%tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-button-option[_ngcontent-%COMP%]:after{content:\"\";position:absolute;inset:0;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-select-button-option[_ngcontent-%COMP%]:not(:disabled):hover:after{background-color:color-mix(in srgb,var(--%NS%tumaet-ui-text-color) 5%,transparent)}@media(forced-colors:active){.tum-ui-select-button-option[aria-pressed=true][_ngcontent-%COMP%]{color:HighlightText;background:Highlight;border-color:Highlight}}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiSelectButtonComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-select-button', imports: [NgTemplateOutlet], host: {
                    role: 'group',
                    class: 'tum-ui-select-button',
                    '[attr.aria-disabled]': 'effectiveDisabled() || null',
                }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiSelectButtonComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "@for (option of normalizedOptions(); track option.value) {\n    <button type=\"button\" [class]=\"optionClasses(option.selected)\" [attr.aria-pressed]=\"option.selected\" [disabled]=\"effectiveDisabled()\" (click)=\"select(option)\">\n        @if (itemTemplate(); as template) {\n            <ng-container [ngTemplateOutlet]=\"template\" [ngTemplateOutletContext]=\"{ $implicit: option.raw }\" />\n        } @else {\n            {{ option.label }}\n        }\n    </button>\n}\n", styles: [":host{display:inline-flex;vertical-align:bottom;-webkit-user-select:none;user-select:none;border-radius:var(--tumaet-ui-radius-md);outline-color:transparent}.tum-ui-select-button-option{position:relative;appearance:none;display:inline-flex;align-items:center;justify-content:center;gap:calc(var(--tumaet-ui-spacing) * 2);padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 4);font-weight:500;white-space:nowrap;cursor:pointer;border-block-width:1px;border-inline-start-width:0;border-inline-end-width:1px;border-style:solid;border-radius:0;transition:background-color .2s,color .2s,border-color .2s,box-shadow .2s,outline-color .2s;outline-color:transparent}.tum-ui-select-button-option:first-child{border-inline-start-width:1px;border-start-start-radius:var(--tumaet-ui-radius-md);border-end-start-radius:var(--tumaet-ui-radius-md)}.tum-ui-select-button-option:last-child{border-start-end-radius:var(--tumaet-ui-radius-md);border-end-end-radius:var(--tumaet-ui-radius-md)}.tum-ui-select-button-option:disabled{cursor:default}.tum-ui-select-button-option:focus-visible{position:relative;z-index:1;outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-button-option:after{content:\"\";position:absolute;inset:0;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-select-button-option:not(:disabled):hover:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}@media(forced-colors:active){.tum-ui-select-button-option[aria-pressed=true]{color:HighlightText;background:Highlight;border-color:Highlight}}\n"] }]
    }], null, { options: [{ type: i0.Input, args: [{ isSignal: true, alias: "options", required: false }] }], optionLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "optionLabel", required: false }] }], optionValue: [{ type: i0.Input, args: [{ isSignal: true, alias: "optionValue", required: false }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], allowEmpty: [{ type: i0.Input, args: [{ isSignal: true, alias: "allowEmpty", required: false }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], itemTemplate: [{ type: i0.Input, args: [{ isSignal: true, alias: "itemTemplate", required: false }] }], changed: [{ type: i0.Output, args: ["changed"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiSelectButtonComponent, { className: "TumUiSelectButtonComponent", filePath: "lib/select-button/tum-ui-select-button.component.ts", lineNumber: 41 }); })();

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
    static ɵfac = function TumUiTableDirective_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTableDirective)(); };
    static ɵdir = /*@__PURE__*/ i0.ɵɵdefineDirective({ type: TumUiTableDirective, selectors: [["table", "tumUiTable", ""]], hostVars: 2, hostBindings: function TumUiTableDirective_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵclassMap(ctx.hostClasses());
        } }, inputs: { size: [1, "size"], striped: [1, "striped"], scrollable: [1, "scrollable"], rowHover: [1, "rowHover"], sortField: [1, "sortField"], sortOrder: [1, "sortOrder"], defaultSortOrder: [1, "defaultSortOrder"] }, outputs: { sortChange: "sortChange" } });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTableDirective, [{
        type: Directive,
        args: [{
                selector: 'table[tumUiTable]',
                host: {
                    '[class]': 'hostClasses()',
                },
            }]
    }], null, { size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], striped: [{ type: i0.Input, args: [{ isSignal: true, alias: "striped", required: false }] }], scrollable: [{ type: i0.Input, args: [{ isSignal: true, alias: "scrollable", required: false }] }], rowHover: [{ type: i0.Input, args: [{ isSignal: true, alias: "rowHover", required: false }] }], sortField: [{ type: i0.Input, args: [{ isSignal: true, alias: "sortField", required: false }] }], sortOrder: [{ type: i0.Input, args: [{ isSignal: true, alias: "sortOrder", required: false }] }], defaultSortOrder: [{ type: i0.Input, args: [{ isSignal: true, alias: "defaultSortOrder", required: false }] }], sortChange: [{ type: i0.Output, args: ["sortChange"] }] }); })();

const _c0$8 = ["*"];
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
    static ɵfac = function TumUiTableSortableColumnComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTableSortableColumnComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiTableSortableColumnComponent, selectors: [["th", "tumUiSortableColumn", ""]], hostVars: 3, hostBindings: function TumUiTableSortableColumnComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("aria-sort", ctx.ariaSort());
            i0.ɵɵclassMap(ctx.hostClasses());
        } }, inputs: { field: [1, "tumUiSortableColumn", "field"], disabled: [1, "disabled"] }, ngContentSelectors: _c0$8, decls: 3, vars: 2, consts: [["type", "button", 1, "tum-ui-sort-button", 3, "click", "disabled"], ["aria-hidden", "true", 1, "tum-ui-sort-icon", "tum:text-muted", 3, "icon"]], template: function TumUiTableSortableColumnComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵelementStart(0, "button", 0);
            i0.ɵɵlistener("click", function TumUiTableSortableColumnComponent_Template_button_click_0_listener() { return ctx.onActivate(); });
            i0.ɵɵprojection(1);
            i0.ɵɵelement(2, "fa-icon", 1);
            i0.ɵɵelementEnd();
        } if (rf & 2) {
            i0.ɵɵproperty("disabled", ctx.disabled());
            i0.ɵɵadvance(2);
            i0.ɵɵproperty("icon", ctx.sortIcon());
        } }, dependencies: [FaIconComponent], styles: ["[_nghost-%COMP%]{white-space:nowrap}.tum-ui-sort-button[_ngcontent-%COMP%]{display:flex;align-items:center;width:100%;padding:0;border:0;background:transparent;color:inherit;font:inherit;text-align:inherit;cursor:pointer}.tum-ui-sort-button[_ngcontent-%COMP%]:disabled{cursor:default}.tum-ui-sort-icon[_ngcontent-%COMP%]{display:inline-block;width:calc(var(--%NS%tumaet-ui-spacing) * 3.5);height:calc(var(--%NS%tumaet-ui-spacing) * 3.5);margin-inline-start:calc(var(--%NS%tumaet-ui-spacing) * 2);vertical-align:middle;flex-shrink:0}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTableSortableColumnComponent, [{
        type: Component,
        args: [{ selector: 'th[tumUiSortableColumn]', imports: [FaIconComponent], host: {
                    '[class]': 'hostClasses()',
                    '[attr.aria-sort]': 'ariaSort()',
                }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<button type=\"button\" class=\"tum-ui-sort-button\" [disabled]=\"disabled()\" (click)=\"onActivate()\">\n    <ng-content />\n    <fa-icon [icon]=\"sortIcon()\" class=\"tum-ui-sort-icon tum:text-muted\" aria-hidden=\"true\" />\n</button>\n", styles: [":host{white-space:nowrap}.tum-ui-sort-button{display:flex;align-items:center;width:100%;padding:0;border:0;background:transparent;color:inherit;font:inherit;text-align:inherit;cursor:pointer}.tum-ui-sort-button:disabled{cursor:default}.tum-ui-sort-icon{display:inline-block;width:calc(var(--tumaet-ui-spacing) * 3.5);height:calc(var(--tumaet-ui-spacing) * 3.5);margin-inline-start:calc(var(--tumaet-ui-spacing) * 2);vertical-align:middle;flex-shrink:0}\n"] }]
    }], null, { field: [{ type: i0.Input, args: [{ isSignal: true, alias: "tumUiSortableColumn", required: true }] }], disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiTableSortableColumnComponent, { className: "TumUiTableSortableColumnComponent", filePath: "lib/table-directive/tum-ui-table-sortable-column.component.ts", lineNumber: 19 }); })();

const _c0$7 = ["*"];
const _c1 = (a0, a1) => ({ $implicit: a0, index: a1 });
function TumUiTableVirtualScrollComponent_div_4_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "div", 4);
    i0.ɵɵelementContainer(1, 5);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const item_r1 = ctx.$implicit;
    const i_r2 = ctx.index;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵclassMap(ctx_r2.rowClasses() + ctx_r2.stripeClass(i_r2));
    i0.ɵɵstyleProp("height", ctx_r2.itemSize(), "px");
    i0.ɵɵattribute("aria-rowindex", i_r2 + 2);
    i0.ɵɵadvance();
    i0.ɵɵproperty("ngTemplateOutlet", ctx_r2.rowTemplate())("ngTemplateOutletContext", i0.ɵɵpureFunction2(7, _c1, item_r1, i_r2));
} }
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
    static ɵfac = function TumUiTableVirtualScrollComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTableVirtualScrollComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiTableVirtualScrollComponent, selectors: [["tum-ui-table-virtual-scroll"]], inputs: { items: [1, "items"], itemSize: [1, "itemSize"], rowTemplate: [1, "rowTemplate"], size: [1, "size"], striped: [1, "striped"], rowHover: [1, "rowHover"], scrollHeight: [1, "scrollHeight"], minWidth: [1, "minWidth"], trackBy: [1, "trackBy"], ariaDescribedBy: [1, "ariaDescribedBy"] }, ngContentSelectors: _c0$7, decls: 5, vars: 17, consts: [["role", "table", 1, "tum-ui-vs-table", "tum:flex", "tum:min-h-0", "tum:flex-col"], ["role", "row", "aria-rowindex", "1"], ["role", "rowgroup", "tabindex", "0", 1, "tum-ui-vs-viewport", 3, "itemSize"], ["role", "row", 3, "class", "height", 4, "cdkVirtualFor", "cdkVirtualForOf", "cdkVirtualForTrackBy"], ["role", "row"], [3, "ngTemplateOutlet", "ngTemplateOutletContext"]], template: function TumUiTableVirtualScrollComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵelementStart(0, "div", 0)(1, "div", 1);
            i0.ɵɵprojection(2);
            i0.ɵɵelementEnd();
            i0.ɵɵelementStart(3, "cdk-virtual-scroll-viewport", 2);
            i0.ɵɵtemplate(4, TumUiTableVirtualScrollComponent_div_4_Template, 2, 10, "div", 3);
            i0.ɵɵelementEnd()();
        } if (rf & 2) {
            i0.ɵɵstyleProp("flex", ctx.isFlexHeight() ? "1 1 0%" : null)("min-width", ctx.minWidth());
            i0.ɵɵattribute("aria-describedby", ctx.ariaDescribedBy())("aria-rowcount", ctx.items().length + 1);
            i0.ɵɵadvance();
            i0.ɵɵclassMap(ctx.headerClasses());
            i0.ɵɵadvance(2);
            i0.ɵɵstyleProp("flex", ctx.isFlexHeight() ? "1 1 0%" : null)("min-height", ctx.isFlexHeight() ? 0 : null)("height", ctx.viewportHeight());
            i0.ɵɵproperty("itemSize", ctx.itemSize());
            i0.ɵɵadvance();
            i0.ɵɵproperty("cdkVirtualForOf", ctx.items())("cdkVirtualForTrackBy", ctx.effectiveTrackBy());
        } }, dependencies: [ScrollingModule, i1$3.CdkFixedSizeVirtualScroll, i1$3.CdkVirtualForOf, i1$3.CdkVirtualScrollViewport, NgTemplateOutlet], styles: ["[_nghost-%COMP%]{display:flex;flex-direction:column;width:100%;max-width:100%;height:100%;min-height:0;min-width:0;overflow-x:auto}.tum-ui-vs-viewport[_ngcontent-%COMP%]{width:100%}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTableVirtualScrollComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-table-virtual-scroll', imports: [ScrollingModule, NgTemplateOutlet], changeDetection: ChangeDetectionStrategy.OnPush, template: "<div\n    role=\"table\"\n    class=\"tum-ui-vs-table tum:flex tum:min-h-0 tum:flex-col\"\n    [style.flex]=\"isFlexHeight() ? '1 1 0%' : null\"\n    [style.min-width]=\"minWidth()\"\n    [attr.aria-describedby]=\"ariaDescribedBy()\"\n    [attr.aria-rowcount]=\"items().length + 1\"\n>\n    <div role=\"row\" aria-rowindex=\"1\" [class]=\"headerClasses()\">\n        <ng-content />\n    </div>\n\n    <cdk-virtual-scroll-viewport\n        [itemSize]=\"itemSize()\"\n        class=\"tum-ui-vs-viewport\"\n        [style.flex]=\"isFlexHeight() ? '1 1 0%' : null\"\n        [style.min-height]=\"isFlexHeight() ? 0 : null\"\n        [style.height]=\"viewportHeight()\"\n        role=\"rowgroup\"\n        tabindex=\"0\"\n    >\n        <div\n            *cdkVirtualFor=\"let item of items(); let i = index; trackBy: effectiveTrackBy()\"\n            role=\"row\"\n            [attr.aria-rowindex]=\"i + 2\"\n            [class]=\"rowClasses() + stripeClass(i)\"\n            [style.height.px]=\"itemSize()\"\n        >\n            <ng-container [ngTemplateOutlet]=\"rowTemplate()\" [ngTemplateOutletContext]=\"{ $implicit: item, index: i }\" />\n        </div>\n    </cdk-virtual-scroll-viewport>\n</div>\n", styles: [":host{display:flex;flex-direction:column;width:100%;max-width:100%;height:100%;min-height:0;min-width:0;overflow-x:auto}.tum-ui-vs-viewport{width:100%}\n"] }]
    }], null, { items: [{ type: i0.Input, args: [{ isSignal: true, alias: "items", required: true }] }], itemSize: [{ type: i0.Input, args: [{ isSignal: true, alias: "itemSize", required: true }] }], rowTemplate: [{ type: i0.Input, args: [{ isSignal: true, alias: "rowTemplate", required: true }] }], size: [{ type: i0.Input, args: [{ isSignal: true, alias: "size", required: false }] }], striped: [{ type: i0.Input, args: [{ isSignal: true, alias: "striped", required: false }] }], rowHover: [{ type: i0.Input, args: [{ isSignal: true, alias: "rowHover", required: false }] }], scrollHeight: [{ type: i0.Input, args: [{ isSignal: true, alias: "scrollHeight", required: false }] }], minWidth: [{ type: i0.Input, args: [{ isSignal: true, alias: "minWidth", required: false }] }], trackBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "trackBy", required: false }] }], ariaDescribedBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaDescribedBy", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiTableVirtualScrollComponent, { className: "TumUiTableVirtualScrollComponent", filePath: "lib/table-directive/tum-ui-table-virtual-scroll.component.ts", lineNumber: 20 }); })();

const _c0$6 = a0 => ({ $implicit: a0 });
function _forTrack0($index, $item) { /* @ts-ignore */
return this.columnName($item, $index); }
function TumUiTableComponent_Conditional_0_Template(rf, ctx) { if (rf & 1) {
    const _r1 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "div", 1)(1, "div", 10);
    i0.ɵɵelement(2, "fa-icon", 11);
    i0.ɵɵelementStart(3, "input", 12, 0);
    i0.ɵɵpipe(5, "tumUiTranslate");
    i0.ɵɵpipe(6, "tumUiTranslate");
    i0.ɵɵlistener("input", function TumUiTableComponent_Conditional_0_Template_input_input_3_listener() { i0.ɵɵrestoreView(_r1); const searchInput_r2 = i0.ɵɵreference(4); const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onSearchInput(searchInput_r2.value)); });
    i0.ɵɵelementEnd()()();
} if (rf & 2) {
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r2.faMagnifyingGlass);
    i0.ɵɵadvance();
    i0.ɵɵproperty("placeholder", i0.ɵɵpipeBind1(5, 3, ctx_r2.searchPlaceholder()));
    i0.ɵɵattribute("aria-label", i0.ɵɵpipeBind1(6, 5, ctx_r2.searchPlaceholder()));
} }
function TumUiTableComponent_For_4_th_1_Conditional_2_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "span");
    i0.ɵɵtext(1);
    i0.ɵɵpipe(2, "tumUiTranslate");
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const col_r5 = i0.ɵɵnextContext(3).$implicit;
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(i0.ɵɵpipeBind1(2, 1, col_r5.headerKey));
} }
function TumUiTableComponent_For_4_th_1_Conditional_2_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "span");
    i0.ɵɵtext(1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const col_r5 = i0.ɵɵnextContext(3).$implicit;
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(col_r5.header);
} }
function TumUiTableComponent_For_4_th_1_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    const _r4 = i0.ɵɵgetCurrentView();
    i0.ɵɵelementStart(0, "button", 19);
    i0.ɵɵlistener("click", function TumUiTableComponent_For_4_th_1_Conditional_2_Template_button_click_0_listener() { i0.ɵɵrestoreView(_r4); const col_r5 = i0.ɵɵnextContext(2).$implicit; const ctx_r2 = i0.ɵɵnextContext(); return i0.ɵɵresetView(ctx_r2.onSortClick(col_r5)); });
    i0.ɵɵconditionalCreate(1, TumUiTableComponent_For_4_th_1_Conditional_2_Conditional_1_Template, 3, 3, "span")(2, TumUiTableComponent_For_4_th_1_Conditional_2_Conditional_2_Template, 2, 1, "span");
    i0.ɵɵelement(3, "fa-icon", 20);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const col_r5 = i0.ɵɵnextContext(2).$implicit;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵconditional(col_r5.headerKey ? 1 : 2);
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("icon", ctx_r2.sortIcon(col_r5));
} }
function TumUiTableComponent_For_4_th_1_Conditional_3_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "span");
    i0.ɵɵtext(1);
    i0.ɵɵpipe(2, "tumUiTranslate");
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const col_r5 = i0.ɵɵnextContext(2).$implicit;
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(i0.ɵɵpipeBind1(2, 1, col_r5.headerKey));
} }
function TumUiTableComponent_For_4_th_1_Conditional_4_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "span");
    i0.ɵɵtext(1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const col_r5 = i0.ɵɵnextContext(2).$implicit;
    i0.ɵɵadvance();
    i0.ɵɵtextInterpolate(col_r5.header);
} }
function TumUiTableComponent_For_4_th_1_Conditional_5_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵdeclareLet(0);
    i0.ɵɵpipe(1, "tumUiTranslate");
    i0.ɵɵelementStart(2, "button", 18);
    i0.ɵɵelement(3, "fa-icon", 21);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const ctx_r2 = i0.ɵɵnextContext(3);
    const tooltipText_r6 = i0.ɵɵpipeBind1(1, 3, ctx);
    i0.ɵɵadvance(2);
    i0.ɵɵproperty("tumUiTooltip", tooltipText_r6);
    i0.ɵɵattribute("aria-label", tooltipText_r6);
    i0.ɵɵadvance();
    i0.ɵɵproperty("icon", ctx_r2.faCircleQuestion);
} }
function TumUiTableComponent_For_4_th_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "th", 15)(1, "span", 16);
    i0.ɵɵconditionalCreate(2, TumUiTableComponent_For_4_th_1_Conditional_2_Template, 4, 2, "button", 17)(3, TumUiTableComponent_For_4_th_1_Conditional_3_Template, 3, 3, "span")(4, TumUiTableComponent_For_4_th_1_Conditional_4_Template, 2, 1, "span");
    i0.ɵɵconditionalCreate(5, TumUiTableComponent_For_4_th_1_Conditional_5_Template, 4, 5, "button", 18);
    i0.ɵɵelementEnd()();
} if (rf & 2) {
    let tmp_15_0;
    const col_r5 = i0.ɵɵnextContext().$implicit;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵclassMap(ctx_r2.headerCellClasses(col_r5));
    i0.ɵɵstyleProp("min-width", col_r5.width);
    i0.ɵɵattribute("aria-sort", ctx_r2.ariaSortFor(col_r5));
    i0.ɵɵadvance(2);
    i0.ɵɵconditional(col_r5.sort && col_r5.field ? 2 : col_r5.headerKey ? 3 : 4);
    i0.ɵɵadvance(3);
    i0.ɵɵconditional((tmp_15_0 = col_r5.headerTooltip) ? 5 : -1, tmp_15_0);
} }
function TumUiTableComponent_For_4_td_2_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementContainer(0, 23);
} if (rf & 2) {
    const ctx_r6 = i0.ɵɵnextContext();
    const row_r8 = ctx_r6.$implicit;
    const i_r9 = ctx_r6.index;
    const col_r5 = i0.ɵɵnextContext().$implicit;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵproperty("ngTemplateOutlet", col_r5.templateRef)("ngTemplateOutletContext", i0.ɵɵpureFunction1(2, _c0$6, ctx_r2.cellParams(row_r8, col_r5, i_r9)));
} }
function TumUiTableComponent_For_4_td_2_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵtext(0);
} if (rf & 2) {
    const row_r8 = i0.ɵɵnextContext().$implicit;
    const col_r5 = i0.ɵɵnextContext().$implicit;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵtextInterpolate1(" ", ctx_r2.resolveValue(row_r8, col_r5), " ");
} }
function TumUiTableComponent_For_4_td_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "td", 22);
    i0.ɵɵconditionalCreate(1, TumUiTableComponent_For_4_td_2_Conditional_1_Template, 1, 4, "ng-container", 23)(2, TumUiTableComponent_For_4_td_2_Conditional_2_Template, 1, 1);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const col_r5 = i0.ɵɵnextContext().$implicit;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵclassMap(ctx_r2.columnVisibilityClasses(col_r5));
    i0.ɵɵadvance();
    i0.ɵɵconditional(col_r5.templateRef ? 1 : 2);
} }
function TumUiTableComponent_For_4_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementContainerStart(0, 4);
    i0.ɵɵtemplate(1, TumUiTableComponent_For_4_th_1_Template, 6, 7, "th", 13)(2, TumUiTableComponent_For_4_td_2_Template, 3, 3, "td", 14);
    i0.ɵɵelementContainerEnd();
} if (rf & 2) {
    const col_r5 = ctx.$implicit;
    const $index_r10 = ctx.$index;
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵproperty("cdkColumnDef", ctx_r2.columnName(col_r5, $index_r10));
} }
function TumUiTableComponent_Conditional_5_th_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "th", 26)(1, "span", 27);
    i0.ɵɵtext(2);
    i0.ɵɵpipe(3, "tumUiTranslate");
    i0.ɵɵelementEnd()();
} if (rf & 2) {
    i0.ɵɵadvance(2);
    i0.ɵɵtextInterpolate(i0.ɵɵpipeBind1(3, 1, "tumUi.table.actions"));
} }
function TumUiTableComponent_Conditional_5_td_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "td", 28);
    i0.ɵɵelementContainer(1, 23);
    i0.ɵɵelementEnd();
} if (rf & 2) {
    const row_r11 = ctx.$implicit;
    const actions_r12 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵproperty("ngTemplateOutlet", actions_r12)("ngTemplateOutletContext", i0.ɵɵpureFunction1(2, _c0$6, row_r11));
} }
function TumUiTableComponent_Conditional_5_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementContainerStart(0, 4);
    i0.ɵɵtemplate(1, TumUiTableComponent_Conditional_5_th_1_Template, 4, 3, "th", 24)(2, TumUiTableComponent_Conditional_5_td_2_Template, 2, 4, "td", 25);
    i0.ɵɵelementContainerEnd();
} if (rf & 2) {
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵproperty("cdkColumnDef", ctx_r2.ACTIONS_COLUMN);
} }
function TumUiTableComponent_tr_6_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "tr", 29);
} }
function TumUiTableComponent_tr_7_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelement(0, "tr", 30);
} }
function TumUiTableComponent_tr_8_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "tr")(1, "td", 31)(2, "span");
    i0.ɵɵtext(3);
    i0.ɵɵpipe(4, "tumUiTranslate");
    i0.ɵɵelementEnd()()();
} if (rf & 2) {
    const ctx_r2 = i0.ɵɵnextContext();
    i0.ɵɵadvance();
    i0.ɵɵattribute("colspan", ctx_r2.displayedColumns().length);
    i0.ɵɵadvance(2);
    i0.ɵɵtextInterpolate(i0.ɵɵpipeBind1(4, 2, ctx_r2.emptyMessage()));
} }
function TumUiTableComponent_Conditional_9_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵelementStart(0, "div", 8);
    i0.ɵɵelement(1, "span", 32);
    i0.ɵɵelementEnd();
} }
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
    /**
     * A header that cannot wrap is a floor the column can never go below, so a long one costs its full width in every
     * layout however little the cells under it hold. `wrapHeader` trades a two-line heading for that width.
     */
    headerCellClasses(col) {
        const whitespace = col.wrapHeader ? '' : 'tum:whitespace-nowrap';
        return `${this.columnVisibilityClasses(col)} ${whitespace}`.trim();
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
    static ɵfac = function TumUiTableComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTableComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiTableComponent, selectors: [["tum-ui-table"]], viewQuery: function TumUiTableComponent_Query(rf, ctx) { if (rf & 1) {
            i0.ɵɵviewQuerySignal(ctx.cdkTable, CdkTable, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, inputs: { columns: [1, "columns"], rows: [1, "rows"], totalRecords: [1, "totalRecords"], loading: [1, "loading"], rowActions: [1, "rowActions"], trackBy: [1, "trackBy"], striped: [1, "striped"], scrollable: [1, "scrollable"], scrollHeight: [1, "scrollHeight"], showSearch: [1, "showSearch"], searchPlaceholder: [1, "searchPlaceholder"], emptyMessage: [1, "emptyMessage"], pageSize: [1, "pageSize"], pageSizeOptions: [1, "pageSizeOptions"], showRowsPerPage: [1, "showRowsPerPage"], showCurrentPageReport: [1, "showCurrentPageReport"], initialSortField: [1, "initialSortField"], initialSortDirection: [1, "initialSortDirection"] }, outputs: { dataRequest: "dataRequest" }, decls: 11, vars: 26, consts: [["searchInput", ""], [1, "tum:mb-3"], [1, "tum:relative", "tum:w-full", "tum:max-w-full", "tum:overflow-x-auto"], ["cdk-table", "", 3, "dataSource", "trackBy"], [3, "cdkColumnDef"], ["cdk-header-row", "", "class", "tum:bg-content-background", 4, "cdkHeaderRowDef", "cdkHeaderRowDefSticky"], ["cdk-row", "", 4, "cdkRowDef", "cdkRowDefColumns"], [4, "cdkNoDataRow"], ["aria-hidden", "true", 1, "tum:absolute", "tum:inset-0", "tum:flex", "tum:items-center", "tum:justify-center", "tum:bg-content-background/60"], [3, "pageChange", "pageSizeChange", "totalRecords", "page", "pageSize", "pageSizeOptions", "showRowsPerPage", "showCurrentPageReport", "disabled"], [1, "tum:relative"], [1, "tum:pointer-events-none", "tum:absolute", "tum:start-3", "tum:top-1/2", "tum:-translate-y-1/2", "tum:text-muted", 3, "icon"], ["type", "search", 1, "tum:box-border", "tum:w-full", "tum:rounded-md", "tum:border", "tum:border-control-border", "tum:bg-control-background", "tum:py-1.5", "tum:ps-10", "tum:pe-3", "tum:text-sm", "tum:text-text", 3, "input", "placeholder"], ["cdk-header-cell", "", "scope", "col", "class", "tum:border-border tum:bg-content-background tum:px-4 tum:py-2 tum:text-start tum:font-semibold tum:text-text", 3, "min-width", "class", 4, "cdkHeaderCellDef"], ["cdk-cell", "", "class", "tum:border-border tum:px-4 tum:py-2 tum:text-text", 3, "class", 4, "cdkCellDef"], ["cdk-header-cell", "", "scope", "col", 1, "tum:border-border", "tum:bg-content-background", "tum:px-4", "tum:py-2", "tum:text-start", "tum:font-semibold", "tum:text-text"], [1, "tum:inline-flex", "tum:items-center", "tum:gap-1"], ["type", "button", 1, "tum:inline-flex", "tum:cursor-pointer", "tum:appearance-none", "tum:items-center", "tum:gap-2", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:font-semibold", "tum:text-text", "tum:hover:text-accent"], ["type", "button", 1, "tum:inline-flex", "tum:cursor-help", "tum:appearance-none", "tum:items-center", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:text-muted", 3, "tumUiTooltip"], ["type", "button", 1, "tum:inline-flex", "tum:cursor-pointer", "tum:appearance-none", "tum:items-center", "tum:gap-2", "tum:border-0", "tum:bg-transparent", "tum:p-0", "tum:font-semibold", "tum:text-text", "tum:hover:text-accent", 3, "click"], [1, "tum:shrink-0", "tum:text-muted", 3, "icon"], [3, "icon"], ["cdk-cell", "", 1, "tum:border-border", "tum:px-4", "tum:py-2", "tum:text-text"], [3, "ngTemplateOutlet", "ngTemplateOutletContext"], ["cdk-header-cell", "", "scope", "col", "class", "tum:border-border tum:bg-content-background tum:px-4 tum:py-2", 4, "cdkHeaderCellDef"], ["cdk-cell", "", "class", "tum:border-border tum:px-4 tum:py-2 tum:text-end tum:whitespace-nowrap", 4, "cdkCellDef"], ["cdk-header-cell", "", "scope", "col", 1, "tum:border-border", "tum:bg-content-background", "tum:px-4", "tum:py-2"], [1, "tum:sr-only"], ["cdk-cell", "", 1, "tum:border-border", "tum:px-4", "tum:py-2", "tum:text-end", "tum:whitespace-nowrap"], ["cdk-header-row", "", 1, "tum:bg-content-background"], ["cdk-row", ""], [1, "tum:bg-content-background", "tum:px-4", "tum:py-6", "tum:text-center", "tum:text-muted"], [1, "tum:h-6", "tum:w-6", "tum:animate-spin", "tum:rounded-full", "tum:border-2", "tum:border-border", "tum:border-t-primary", "tum:motion-reduce:animate-none"]], template: function TumUiTableComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵconditionalCreate(0, TumUiTableComponent_Conditional_0_Template, 7, 7, "div", 1);
            i0.ɵɵelementStart(1, "div", 2)(2, "table", 3);
            i0.ɵɵrepeaterCreate(3, TumUiTableComponent_For_4_Template, 3, 1, "ng-container", 4, _forTrack0, true);
            i0.ɵɵconditionalCreate(5, TumUiTableComponent_Conditional_5_Template, 3, 1, "ng-container", 4);
            i0.ɵɵtemplate(6, TumUiTableComponent_tr_6_Template, 1, 0, "tr", 5)(7, TumUiTableComponent_tr_7_Template, 1, 0, "tr", 6)(8, TumUiTableComponent_tr_8_Template, 5, 4, "tr", 7);
            i0.ɵɵelementEnd();
            i0.ɵɵconditionalCreate(9, TumUiTableComponent_Conditional_9_Template, 2, 0, "div", 8);
            i0.ɵɵelementEnd();
            i0.ɵɵelementStart(10, "tum-ui-paginator", 9);
            i0.ɵɵlistener("pageChange", function TumUiTableComponent_Template_tum_ui_paginator_pageChange_10_listener($event) { return ctx.onPageChange($event); })("pageSizeChange", function TumUiTableComponent_Template_tum_ui_paginator_pageSizeChange_10_listener($event) { return ctx.onPageSizeChange($event); });
            i0.ɵɵelementEnd();
        } if (rf & 2) {
            let tmp_10_0;
            i0.ɵɵconditional(ctx.showSearch() ? 0 : -1);
            i0.ɵɵadvance();
            i0.ɵɵstyleProp("overflow-y", ctx.scrollable() ? "auto" : null)("flex", ctx.scrollHeight() === "flex" ? "1 1 0%" : null)("min-height", ctx.scrollHeight() === "flex" ? 0 : null)("max-height", ctx.scrollHeight() && ctx.scrollHeight() !== "flex" ? ctx.scrollHeight() : null);
            i0.ɵɵadvance();
            i0.ɵɵclassMap(ctx.tableClasses());
            i0.ɵɵproperty("dataSource", ctx.rows())("trackBy", ctx.effectiveTrackBy());
            i0.ɵɵattribute("aria-busy", ctx.loading() ? "true" : null);
            i0.ɵɵadvance();
            i0.ɵɵrepeater(ctx.columns());
            i0.ɵɵadvance(2);
            i0.ɵɵconditional((tmp_10_0 = ctx.rowActions()) ? 5 : -1, tmp_10_0);
            i0.ɵɵadvance();
            i0.ɵɵproperty("cdkHeaderRowDef", ctx.displayedColumns())("cdkHeaderRowDefSticky", ctx.scrollable());
            i0.ɵɵadvance();
            i0.ɵɵproperty("cdkRowDefColumns", ctx.displayedColumns());
            i0.ɵɵadvance(2);
            i0.ɵɵconditional(ctx.loading() ? 9 : -1);
            i0.ɵɵadvance();
            i0.ɵɵproperty("totalRecords", ctx.totalRecords())("page", ctx.currentPage())("pageSize", ctx.effectivePageSize())("pageSizeOptions", ctx.pageSizeOptions())("showRowsPerPage", ctx.showRowsPerPage())("showCurrentPageReport", ctx.showCurrentPageReport())("disabled", ctx.loading());
        } }, dependencies: [CdkTableModule, i1$4.CdkTable, i1$4.CdkRowDef, i1$4.CdkCellDef, i1$4.CdkHeaderCellDef, i1$4.CdkColumnDef, i1$4.CdkCell, i1$4.CdkRow, i1$4.CdkHeaderCell, i1$4.CdkHeaderRow, i1$4.CdkHeaderRowDef, i1$4.CdkNoDataRow, NgTemplateOutlet, FaIconComponent, TumUiPaginatorComponent, TumUiTooltipDirective, TumUiTranslatePipe], styles: ["[_nghost-%COMP%]{display:flex;flex-direction:column;height:100%;min-height:0}.cdk-table[_ngcontent-%COMP%]{width:100%}.cdk-table[_ngcontent-%COMP%]   th[_ngcontent-%COMP%], .cdk-table[_ngcontent-%COMP%]   td[_ngcontent-%COMP%]{border-bottom-width:1px;border-bottom-style:solid}.cdk-table[_ngcontent-%COMP%]   tr[_ngcontent-%COMP%]:last-child   td[_ngcontent-%COMP%]{border-bottom-width:0}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTableComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-table', imports: [CdkTableModule, NgTemplateOutlet, FaIconComponent, TumUiTranslatePipe, TumUiPaginatorComponent, TumUiTooltipDirective], changeDetection: ChangeDetectionStrategy.OnPush, template: "@if (showSearch()) {\n    <div class=\"tum:mb-3\">\n        <div class=\"tum:relative\">\n            <fa-icon [icon]=\"faMagnifyingGlass\" class=\"tum:pointer-events-none tum:absolute tum:start-3 tum:top-1/2 tum:-translate-y-1/2 tum:text-muted\" />\n            <input\n                #searchInput\n                type=\"search\"\n                [placeholder]=\"searchPlaceholder() | tumUiTranslate\"\n                [attr.aria-label]=\"searchPlaceholder() | tumUiTranslate\"\n                (input)=\"onSearchInput(searchInput.value)\"\n                class=\"tum:box-border tum:w-full tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1.5 tum:ps-10 tum:pe-3 tum:text-sm tum:text-text\"\n            />\n        </div>\n    </div>\n}\n\n<div\n    class=\"tum:relative tum:w-full tum:max-w-full tum:overflow-x-auto\"\n    [style.overflow-y]=\"scrollable() ? 'auto' : null\"\n    [style.flex]=\"scrollHeight() === 'flex' ? '1 1 0%' : null\"\n    [style.min-height]=\"scrollHeight() === 'flex' ? 0 : null\"\n    [style.max-height]=\"scrollHeight() && scrollHeight() !== 'flex' ? scrollHeight() : null\"\n>\n    <table cdk-table [dataSource]=\"rows()\" [trackBy]=\"effectiveTrackBy()\" [class]=\"tableClasses()\" [attr.aria-busy]=\"loading() ? 'true' : null\">\n        @for (col of columns(); track columnName(col, $index)) {\n            <ng-container [cdkColumnDef]=\"columnName(col, $index)\">\n                <th\n                    cdk-header-cell\n                    *cdkHeaderCellDef\n                    scope=\"col\"\n                    [style.min-width]=\"col.width\"\n                    [attr.aria-sort]=\"ariaSortFor(col)\"\n                    [class]=\"headerCellClasses(col)\"\n                    class=\"tum:border-border tum:bg-content-background tum:px-4 tum:py-2 tum:text-start tum:font-semibold tum:text-text\"\n                >\n                    <span class=\"tum:inline-flex tum:items-center tum:gap-1\">\n                        @if (col.sort && col.field) {\n                            <button\n                                type=\"button\"\n                                class=\"tum:inline-flex tum:cursor-pointer tum:appearance-none tum:items-center tum:gap-2 tum:border-0 tum:bg-transparent tum:p-0 tum:font-semibold tum:text-text tum:hover:text-accent\"\n                                (click)=\"onSortClick(col)\"\n                            >\n                                @if (col.headerKey) {\n                                    <span>{{ col.headerKey | tumUiTranslate }}</span>\n                                } @else {\n                                    <span>{{ col.header }}</span>\n                                }\n                                <fa-icon [icon]=\"sortIcon(col)\" class=\"tum:shrink-0 tum:text-muted\" />\n                            </button>\n                        } @else if (col.headerKey) {\n                            <span>{{ col.headerKey | tumUiTranslate }}</span>\n                        } @else {\n                            <span>{{ col.header }}</span>\n                        }\n                        @if (col.headerTooltip; as headerTooltip) {\n                            @let tooltipText = headerTooltip | tumUiTranslate;\n                            <button\n                                type=\"button\"\n                                class=\"tum:inline-flex tum:cursor-help tum:appearance-none tum:items-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted\"\n                                [tumUiTooltip]=\"tooltipText\"\n                                [attr.aria-label]=\"tooltipText\"\n                            >\n                                <fa-icon [icon]=\"faCircleQuestion\" />\n                            </button>\n                        }\n                    </span>\n                </th>\n                <td cdk-cell *cdkCellDef=\"let row; let i = index\" [class]=\"columnVisibilityClasses(col)\" class=\"tum:border-border tum:px-4 tum:py-2 tum:text-text\">\n                    @if (col.templateRef) {\n                        <ng-container [ngTemplateOutlet]=\"col.templateRef\" [ngTemplateOutletContext]=\"{ $implicit: cellParams(row, col, i) }\" />\n                    } @else {\n                        {{ resolveValue(row, col) }}\n                    }\n                </td>\n            </ng-container>\n        }\n\n        @if (rowActions(); as actions) {\n            <ng-container [cdkColumnDef]=\"ACTIONS_COLUMN\">\n                <th cdk-header-cell *cdkHeaderCellDef scope=\"col\" class=\"tum:border-border tum:bg-content-background tum:px-4 tum:py-2\">\n                    <span class=\"tum:sr-only\">{{ 'tumUi.table.actions' | tumUiTranslate }}</span>\n                </th>\n                <!-- Declares no width, so it is the one column auto layout can squeeze: nowrap keeps the controls on one line. -->\n                <td cdk-cell *cdkCellDef=\"let row\" class=\"tum:border-border tum:px-4 tum:py-2 tum:text-end tum:whitespace-nowrap\">\n                    <ng-container [ngTemplateOutlet]=\"actions\" [ngTemplateOutletContext]=\"{ $implicit: row }\" />\n                </td>\n            </ng-container>\n        }\n\n        <tr cdk-header-row *cdkHeaderRowDef=\"displayedColumns(); sticky: scrollable()\" class=\"tum:bg-content-background\"></tr>\n        <tr cdk-row *cdkRowDef=\"let row; columns: displayedColumns()\"></tr>\n\n        <tr *cdkNoDataRow>\n            <td [attr.colspan]=\"displayedColumns().length\" class=\"tum:bg-content-background tum:px-4 tum:py-6 tum:text-center tum:text-muted\">\n                <span>{{ emptyMessage() | tumUiTranslate }}</span>\n            </td>\n        </tr>\n    </table>\n\n    @if (loading()) {\n        <div class=\"tum:absolute tum:inset-0 tum:flex tum:items-center tum:justify-center tum:bg-content-background/60\" aria-hidden=\"true\">\n            <span class=\"tum:h-6 tum:w-6 tum:animate-spin tum:rounded-full tum:border-2 tum:border-border tum:border-t-primary tum:motion-reduce:animate-none\"></span>\n        </div>\n    }\n</div>\n\n<tum-ui-paginator\n    [totalRecords]=\"totalRecords()\"\n    [page]=\"currentPage()\"\n    [pageSize]=\"effectivePageSize()\"\n    [pageSizeOptions]=\"pageSizeOptions()\"\n    [showRowsPerPage]=\"showRowsPerPage()\"\n    [showCurrentPageReport]=\"showCurrentPageReport()\"\n    [disabled]=\"loading()\"\n    (pageChange)=\"onPageChange($event)\"\n    (pageSizeChange)=\"onPageSizeChange($event)\"\n/>\n", styles: [":host{display:flex;flex-direction:column;height:100%;min-height:0}.cdk-table{width:100%}.cdk-table th,.cdk-table td{border-bottom-width:1px;border-bottom-style:solid}.cdk-table tr:last-child td{border-bottom-width:0}\n"] }]
    }], () => [], { columns: [{ type: i0.Input, args: [{ isSignal: true, alias: "columns", required: true }] }], rows: [{ type: i0.Input, args: [{ isSignal: true, alias: "rows", required: true }] }], totalRecords: [{ type: i0.Input, args: [{ isSignal: true, alias: "totalRecords", required: false }] }], loading: [{ type: i0.Input, args: [{ isSignal: true, alias: "loading", required: false }] }], rowActions: [{ type: i0.Input, args: [{ isSignal: true, alias: "rowActions", required: false }] }], trackBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "trackBy", required: false }] }], striped: [{ type: i0.Input, args: [{ isSignal: true, alias: "striped", required: false }] }], scrollable: [{ type: i0.Input, args: [{ isSignal: true, alias: "scrollable", required: false }] }], scrollHeight: [{ type: i0.Input, args: [{ isSignal: true, alias: "scrollHeight", required: false }] }], showSearch: [{ type: i0.Input, args: [{ isSignal: true, alias: "showSearch", required: false }] }], searchPlaceholder: [{ type: i0.Input, args: [{ isSignal: true, alias: "searchPlaceholder", required: false }] }], emptyMessage: [{ type: i0.Input, args: [{ isSignal: true, alias: "emptyMessage", required: false }] }], pageSize: [{ type: i0.Input, args: [{ isSignal: true, alias: "pageSize", required: false }] }], pageSizeOptions: [{ type: i0.Input, args: [{ isSignal: true, alias: "pageSizeOptions", required: false }] }], showRowsPerPage: [{ type: i0.Input, args: [{ isSignal: true, alias: "showRowsPerPage", required: false }] }], showCurrentPageReport: [{ type: i0.Input, args: [{ isSignal: true, alias: "showCurrentPageReport", required: false }] }], initialSortField: [{ type: i0.Input, args: [{ isSignal: true, alias: "initialSortField", required: false }] }], initialSortDirection: [{ type: i0.Input, args: [{ isSignal: true, alias: "initialSortDirection", required: false }] }], dataRequest: [{ type: i0.Output, args: ["dataRequest"] }], cdkTable: [{ type: i0.ViewChild, args: [i0.forwardRef(() => CdkTable), { isSignal: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiTableComponent, { className: "TumUiTableComponent", filePath: "lib/table/tum-ui-table.component.ts", lineNumber: 49 }); })();

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
    static ɵfac = function TumUiTabsService_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTabsService)(); };
    static ɵprov = /*@__PURE__*/ i0.ɵɵdefineInjectable({ token: TumUiTabsService, factory: TumUiTabsService.ɵfac });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTabsService, [{
        type: Injectable
    }], null, null); })();

const _c0$5 = ["*"];
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
    static ɵfac = function TumUiTabComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTabComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiTabComponent, selectors: [["tum-ui-tab"]], hostAttrs: ["role", "tab"], hostVars: 7, hostBindings: function TumUiTabComponent_HostBindings(rf, ctx) { if (rf & 1) {
            i0.ɵɵlistener("click", function TumUiTabComponent_click_HostBindingHandler() { return ctx.onClick(); });
        } if (rf & 2) {
            i0.ɵɵdomProperty("id", ctx.id());
            i0.ɵɵattribute("aria-selected", ctx.active())("aria-controls", ctx.panelId())("aria-disabled", ctx.disabled || undefined)("tabindex", ctx.active() && !ctx.disabled ? 0 : -1);
            i0.ɵɵclassMap(ctx.hostClasses());
        } }, inputs: { value: [1, "value"], disabledInput: [1, "disabled", "disabledInput"] }, ngContentSelectors: _c0$5, decls: 1, vars: 0, template: function TumUiTabComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵprojection(0);
        } }, styles: ["[_nghost-%COMP%]{display:inline-flex;align-items:center;position:relative;flex-shrink:0;gap:calc(var(--%NS%tumaet-ui-spacing) * 2);padding:calc(var(--%NS%tumaet-ui-spacing) * 4) calc(var(--%NS%tumaet-ui-spacing) * 4.5);font-weight:600;white-space:nowrap;cursor:pointer;-webkit-user-select:none;user-select:none;background:transparent;outline-color:transparent;transition:color .2s,background .2s,outline-color .2s}.tum-ui-tab-disabled[_nghost-%COMP%]{opacity:.6;cursor:default;pointer-events:none}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTabComponent, [{
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
    }], () => [], { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: true }] }], disabledInput: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiTabComponent, { className: "TumUiTabComponent", filePath: "lib/tabs/tum-ui-tab.component.ts", lineNumber: 22 }); })();

const _c0$4 = ["*"];
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
    static ɵfac = function TumUiTabListComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTabListComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiTabListComponent, selectors: [["tum-ui-tab-list"]], contentQueries: function TumUiTabListComponent_ContentQueries(rf, ctx, dirIndex) { if (rf & 1) {
            i0.ɵɵcontentQuerySignal(dirIndex, ctx.tabs, TumUiTabComponent, 5);
        } if (rf & 2) {
            i0.ɵɵqueryAdvance();
        } }, hostAttrs: ["role", "tablist", 1, "tum-ui-tab-list", "tum:relative", "tum:flex", "tum:w-full", "tum:min-w-0", "tum:max-w-full", "tum:overflow-x-auto", "tum:border-b", "tum:border-border"], hostBindings: function TumUiTabListComponent_HostBindings(rf, ctx) { if (rf & 1) {
            i0.ɵɵlistener("keydown", function TumUiTabListComponent_keydown_HostBindingHandler($event) { return ctx.onKeydown($event); });
        } }, ngContentSelectors: _c0$4, decls: 2, vars: 6, consts: [["aria-hidden", "true", 1, "tum-ui-tab-indicator"]], template: function TumUiTabListComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵprojection(0);
            i0.ɵɵdomElement(1, "span", 0);
        } if (rf & 2) {
            i0.ɵɵadvance();
            i0.ɵɵstyleProp("width", ctx.indicatorPosition().width, "px")("transform", ctx.indicatorTransform());
            i0.ɵɵclassProp("tum-ui-tab-indicator-animated", ctx.indicatorPosition().animate);
        } }, styles: ["[_nghost-%COMP%]{scrollbar-width:thin}.tum-ui-tab-indicator[_ngcontent-%COMP%]{position:absolute;inset-block-end:0;left:0;height:2px;background:var(--%NS%tumaet-ui-primary-color);pointer-events:none}.tum-ui-tab-indicator-animated[_ngcontent-%COMP%]{transition:width .25s cubic-bezier(.35,0,.25,1),transform .25s cubic-bezier(.35,0,.25,1)}@media(prefers-reduced-motion:reduce){.tum-ui-tab-indicator[_ngcontent-%COMP%]{transition:none}}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTabListComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-tab-list', host: {
                    role: 'tablist',
                    class: 'tum-ui-tab-list tum:relative tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:overflow-x-auto tum:border-b tum:border-border',
                    '(keydown)': 'onKeydown($event)',
                }, changeDetection: ChangeDetectionStrategy.OnPush, template: "<ng-content />\n<span\n    class=\"tum-ui-tab-indicator\"\n    [class.tum-ui-tab-indicator-animated]=\"indicatorPosition().animate\"\n    aria-hidden=\"true\"\n    [style.width.px]=\"indicatorPosition().width\"\n    [style.transform]=\"indicatorTransform()\"\n></span>\n", styles: [":host{scrollbar-width:thin}.tum-ui-tab-indicator{position:absolute;inset-block-end:0;left:0;height:2px;background:var(--tumaet-ui-primary-color);pointer-events:none}.tum-ui-tab-indicator-animated{transition:width .25s cubic-bezier(.35,0,.25,1),transform .25s cubic-bezier(.35,0,.25,1)}@media(prefers-reduced-motion:reduce){.tum-ui-tab-indicator{transition:none}}\n"] }]
    }], () => [], { tabs: [{ type: i0.ContentChildren, args: [i0.forwardRef(() => TumUiTabComponent), { ...{ descendants: true }, isSignal: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiTabListComponent, { className: "TumUiTabListComponent", filePath: "lib/tabs/tum-ui-tab-list.component.ts", lineNumber: 20 }); })();

const _c0$3 = ["*"];
function TumUiTabPanelComponent_Conditional_0_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵprojection(0);
} }
/** Content panel shown when its value matches the containing tabs value. */
class TumUiTabPanelComponent {
    tabsService = inject(TumUiTabsService);
    /** Value that associates this panel with a tab. */
    value = input.required(/* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "value" }] : /* istanbul ignore next */ []));
    active = computed(() => this.tabsService.active() === this.value(), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "active" }] : /* istanbul ignore next */ []));
    id = computed(() => this.tabsService.panelId(this.value()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "id" }] : /* istanbul ignore next */ []));
    tabId = computed(() => this.tabsService.tabId(this.value()), /* @ts-ignore */
    ...(ngDevMode ? [{ debugName: "tabId" }] : /* istanbul ignore next */ []));
    static ɵfac = function TumUiTabPanelComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTabPanelComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiTabPanelComponent, selectors: [["tum-ui-tab-panel"]], hostAttrs: ["role", "tabpanel", 1, "tum-ui-tab-panel", "tum:focus-visible:outline", "tum:focus-visible:outline-2", "tum:focus-visible:outline-focus"], hostVars: 4, hostBindings: function TumUiTabPanelComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵdomProperty("id", ctx.id())("hidden", !ctx.active());
            i0.ɵɵattribute("aria-labelledby", ctx.tabId())("tabindex", ctx.active() ? 0 : undefined);
        } }, inputs: { value: [1, "value"] }, ngContentSelectors: _c0$3, decls: 1, vars: 1, template: function TumUiTabPanelComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵconditionalCreate(0, TumUiTabPanelComponent_Conditional_0_Template, 1, 0);
        } if (rf & 2) {
            i0.ɵɵconditional(ctx.active() ? 0 : -1);
        } }, encapsulation: 2 });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTabPanelComponent, [{
        type: Component,
        args: [{
                selector: 'tum-ui-tab-panel',
                template: '@if (active()) { <ng-content /> }',
                host: {
                    role: 'tabpanel',
                    class: 'tum-ui-tab-panel tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus',
                    '[id]': 'id()',
                    '[attr.aria-labelledby]': 'tabId()',
                    '[attr.tabindex]': 'active() ? 0 : undefined',
                    '[hidden]': '!active()',
                },
                changeDetection: ChangeDetectionStrategy.OnPush,
            }]
    }], null, { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: true }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiTabPanelComponent, { className: "TumUiTabPanelComponent", filePath: "lib/tabs/tum-ui-tab-panel.component.ts", lineNumber: 18 }); })();

const _c0$2 = ["*"];
/** Layout container for the panels in a tabs composition. */
class TumUiTabPanelsComponent {
    static ɵfac = function TumUiTabPanelsComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTabPanelsComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiTabPanelsComponent, selectors: [["tum-ui-tab-panels"]], hostAttrs: [1, "tum-ui-tab-panels"], ngContentSelectors: _c0$2, decls: 1, vars: 0, template: function TumUiTabPanelsComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵprojection(0);
        } }, styles: ["[_nghost-%COMP%]{display:block;padding:calc(var(--%NS%tumaet-ui-spacing) * 3.5) calc(var(--%NS%tumaet-ui-spacing) * 4.5) calc(var(--%NS%tumaet-ui-spacing) * 4.5)}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTabPanelsComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-tab-panels', template: '<ng-content />', host: {
                    class: 'tum-ui-tab-panels',
                }, changeDetection: ChangeDetectionStrategy.OnPush, styles: [":host{display:block;padding:calc(var(--tumaet-ui-spacing) * 3.5) calc(var(--tumaet-ui-spacing) * 4.5) calc(var(--tumaet-ui-spacing) * 4.5)}\n"] }]
    }], null, null); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiTabPanelsComponent, { className: "TumUiTabPanelsComponent", filePath: "lib/tabs/tum-ui-tab-panels.component.ts", lineNumber: 13 }); })();

const _c0$1 = ["*"];
/** Coordinates an accessible tab list with its associated tab panels. */
class TumUiTabsComponent {
    tabsService = inject(TumUiTabsService);
    /** Value shared by the active tab and tab panel. */
    value = model(/* @ts-ignore */
    ...(ngDevMode ? [undefined, { debugName: "value" }] : /* istanbul ignore next */ []));
    constructor() {
        this.tabsService.register(this.value, (selected) => this.value.set(selected));
    }
    static ɵfac = function TumUiTabsComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTabsComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiTabsComponent, selectors: [["tum-ui-tabs"]], hostAttrs: [1, "tum-ui-tabs", "tum:flex", "tum:w-full", "tum:min-w-0", "tum:max-w-full", "tum:flex-col"], inputs: { value: [1, "value"] }, outputs: { value: "valueChange" }, features: [i0.ɵɵProvidersFeature([TumUiTabsService])], ngContentSelectors: _c0$1, decls: 1, vars: 0, template: function TumUiTabsComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵprojection(0);
        } }, encapsulation: 2 });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTabsComponent, [{
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
    }], () => [], { value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }, { type: i0.Output, args: ["valueChange"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiTabsComponent, { className: "TumUiTabsComponent", filePath: "lib/tabs/tum-ui-tabs.component.ts", lineNumber: 14 }); })();

const _c0 = ["*"];
function TumUiTagComponent_Conditional_1_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵtext(0);
} if (rf & 2) {
    i0.ɵɵtextInterpolate1(" ", ctx, " ");
} }
function TumUiTagComponent_Conditional_2_Template(rf, ctx) { if (rf & 1) {
    i0.ɵɵprojection(0);
} }
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
    static ɵfac = function TumUiTagComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiTagComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiTagComponent, selectors: [["tum-ui-tag"]], inputs: { severity: [1, "severity"], value: [1, "value"], rounded: [1, "rounded"] }, ngContentSelectors: _c0, decls: 3, vars: 4, template: function TumUiTagComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵprojectionDef();
            i0.ɵɵdomElementStart(0, "span");
            i0.ɵɵconditionalCreate(1, TumUiTagComponent_Conditional_1_Template, 1, 1)(2, TumUiTagComponent_Conditional_2_Template, 1, 0);
            i0.ɵɵdomElementEnd();
        } if (rf & 2) {
            let tmp_2_0;
            i0.ɵɵclassMap(ctx.tagClasses());
            i0.ɵɵattribute("data-severity", ctx.severity());
            i0.ɵɵadvance();
            i0.ɵɵconditional((tmp_2_0 = ctx.value()) ? 1 : 2, tmp_2_0);
        } }, styles: ["[_nghost-%COMP%]   span[data-severity=success][_ngcontent-%COMP%]{background:color-mix(in srgb,var(--%NS%tumaet-ui-state-success) 20%,var(--%NS%tumaet-ui-content-background));color:var(--%NS%tumaet-ui-state-success-foreground)}[_nghost-%COMP%]   span[data-severity=info][_ngcontent-%COMP%]{background:color-mix(in srgb,var(--%NS%tumaet-ui-state-info) 20%,var(--%NS%tumaet-ui-content-background));color:var(--%NS%tumaet-ui-state-info-foreground)}[_nghost-%COMP%]   span[data-severity=warn][_ngcontent-%COMP%]{background:color-mix(in srgb,var(--%NS%tumaet-ui-state-warning) 20%,var(--%NS%tumaet-ui-content-background));color:var(--%NS%tumaet-ui-state-warning-foreground)}[_nghost-%COMP%]   span[data-severity=danger][_ngcontent-%COMP%]{background:color-mix(in srgb,var(--%NS%tumaet-ui-state-danger) 20%,var(--%NS%tumaet-ui-content-background));color:var(--%NS%tumaet-ui-state-danger-foreground)}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiTagComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-tag', changeDetection: ChangeDetectionStrategy.OnPush, template: "<span [class]=\"tagClasses()\" [attr.data-severity]=\"severity()\">\n    @if (value(); as tagValue) {\n        {{ tagValue }}\n    } @else {\n        <ng-content />\n    }\n</span>\n", styles: [":host span[data-severity=success]{background:color-mix(in srgb,var(--tumaet-ui-state-success) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-success-foreground)}:host span[data-severity=info]{background:color-mix(in srgb,var(--tumaet-ui-state-info) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-info-foreground)}:host span[data-severity=warn]{background:color-mix(in srgb,var(--tumaet-ui-state-warning) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-warning-foreground)}:host span[data-severity=danger]{background:color-mix(in srgb,var(--tumaet-ui-state-danger) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-danger-foreground)}\n"] }]
    }], null, { severity: [{ type: i0.Input, args: [{ isSignal: true, alias: "severity", required: false }] }], value: [{ type: i0.Input, args: [{ isSignal: true, alias: "value", required: false }] }], rounded: [{ type: i0.Input, args: [{ isSignal: true, alias: "rounded", required: false }] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiTagComponent, { className: "TumUiTagComponent", filePath: "lib/tag/tum-ui-tag.component.ts", lineNumber: 22 }); })();

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
    static ɵfac = function TumUiToggleSwitchComponent_Factory(__ngFactoryType__) { return new (__ngFactoryType__ || TumUiToggleSwitchComponent)(); };
    static ɵcmp = /*@__PURE__*/ i0.ɵɵdefineComponent({ type: TumUiToggleSwitchComponent, selectors: [["tum-ui-toggle-switch"]], hostAttrs: [1, "tum-ui-toggle-switch"], hostVars: 4, hostBindings: function TumUiToggleSwitchComponent_HostBindings(rf, ctx) { if (rf & 2) {
            i0.ɵɵattribute("data-checked", ctx.checked())("data-disabled", ctx.effectiveDisabled() || null);
            i0.ɵɵclassMap(ctx.hostClasses());
        } }, inputs: { disabled: [1, "disabled"], inputId: [1, "inputId"], ariaLabel: [1, "ariaLabel"], ariaLabelledBy: [1, "ariaLabelledBy"] }, outputs: { changed: "changed" }, features: [i0.ɵɵProvidersFeature([{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiToggleSwitchComponent), multi: true }])], decls: 2, vars: 5, consts: [["type", "checkbox", "role", "switch", 1, "tum-ui-toggle-switch-input", 3, "change", "blur", "id", "checked", "disabled"], ["aria-hidden", "true", 1, "tum-ui-toggle-switch-handle", "tum:bg-content-background"]], template: function TumUiToggleSwitchComponent_Template(rf, ctx) { if (rf & 1) {
            i0.ɵɵdomElementStart(0, "input", 0);
            i0.ɵɵdomListener("change", function TumUiToggleSwitchComponent_Template_input_change_0_listener($event) { return ctx.onInputChange($event); })("blur", function TumUiToggleSwitchComponent_Template_input_blur_0_listener() { return ctx.onInputBlur(); });
            i0.ɵɵdomElementEnd();
            i0.ɵɵdomElement(1, "span", 1);
        } if (rf & 2) {
            i0.ɵɵdomProperty("id", ctx.inputId())("checked", ctx.checked())("disabled", ctx.effectiveDisabled());
            i0.ɵɵattribute("aria-label", ctx.effectiveAriaLabel())("aria-labelledby", ctx.effectiveAriaLabelledBy());
        } }, styles: ["[_nghost-%COMP%]{display:inline-block;position:relative;box-sizing:border-box;width:calc(var(--%NS%tumaet-ui-spacing) * 10);height:calc(var(--%NS%tumaet-ui-spacing) * 6);flex-shrink:0;border:1px solid transparent;border-radius:30px;cursor:pointer;vertical-align:middle;outline-color:transparent;transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}[data-disabled=true][_nghost-%COMP%]{cursor:default}[_nghost-%COMP%]:has(.tum-ui-toggle-switch-input:focus-visible){outline:2px solid var(--%NS%tumaet-ui-focus-color);outline-offset:2px}.tum-ui-toggle-switch-input[_ngcontent-%COMP%]{position:absolute;z-index:1;inset:0;width:100%;height:100%;margin:0;opacity:0;cursor:pointer}.tum-ui-toggle-switch-input[_ngcontent-%COMP%]:disabled{cursor:default}.tum-ui-toggle-switch-handle[_ngcontent-%COMP%]{position:absolute;top:50%;width:calc(var(--%NS%tumaet-ui-spacing) * 4);height:calc(var(--%NS%tumaet-ui-spacing) * 4);margin-block-start:calc(var(--%NS%tumaet-ui-spacing) * -2);inset-inline-start:var(--%NS%tumaet-ui-spacing);border-radius:50%;transition:background-color .2s,inset-inline-start .2s}[data-checked=true][_nghost-%COMP%]   .tum-ui-toggle-switch-handle[_ngcontent-%COMP%]{inset-inline-start:calc(var(--%NS%tumaet-ui-spacing) * 5)}@media(prefers-reduced-motion:reduce){.tum-ui-toggle-switch-handle[_ngcontent-%COMP%]{transition-property:background-color}}@media(forced-colors:active){.tum-ui-toggle-switch-input[_ngcontent-%COMP%]{appearance:auto;opacity:1}.tum-ui-toggle-switch-handle[_ngcontent-%COMP%]{display:none}}"] });
}
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassMetadata(TumUiToggleSwitchComponent, [{
        type: Component,
        args: [{ selector: 'tum-ui-toggle-switch', host: {
                    // The identity class is static so it is always present; only the state classes are bound.
                    class: 'tum-ui-toggle-switch',
                    '[class]': 'hostClasses()',
                    '[attr.data-checked]': 'checked()',
                    '[attr.data-disabled]': 'effectiveDisabled() || null',
                }, providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TumUiToggleSwitchComponent), multi: true }], changeDetection: ChangeDetectionStrategy.OnPush, template: "<input\n    type=\"checkbox\"\n    role=\"switch\"\n    class=\"tum-ui-toggle-switch-input\"\n    [id]=\"inputId()\"\n    [checked]=\"checked()\"\n    [disabled]=\"effectiveDisabled()\"\n    [attr.aria-label]=\"effectiveAriaLabel()\"\n    [attr.aria-labelledby]=\"effectiveAriaLabelledBy()\"\n    (change)=\"onInputChange($event)\"\n    (blur)=\"onInputBlur()\"\n/>\n<span class=\"tum-ui-toggle-switch-handle tum:bg-content-background\" aria-hidden=\"true\"></span>\n", styles: [":host{display:inline-block;position:relative;box-sizing:border-box;width:calc(var(--tumaet-ui-spacing) * 10);height:calc(var(--tumaet-ui-spacing) * 6);flex-shrink:0;border:1px solid transparent;border-radius:30px;cursor:pointer;vertical-align:middle;outline-color:transparent;transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}:host([data-disabled=true]){cursor:default}:host(:has(.tum-ui-toggle-switch-input:focus-visible)){outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-toggle-switch-input{position:absolute;z-index:1;inset:0;width:100%;height:100%;margin:0;opacity:0;cursor:pointer}.tum-ui-toggle-switch-input:disabled{cursor:default}.tum-ui-toggle-switch-handle{position:absolute;top:50%;width:calc(var(--tumaet-ui-spacing) * 4);height:calc(var(--tumaet-ui-spacing) * 4);margin-block-start:calc(var(--tumaet-ui-spacing) * -2);inset-inline-start:var(--tumaet-ui-spacing);border-radius:50%;transition:background-color .2s,inset-inline-start .2s}:host([data-checked=true]) .tum-ui-toggle-switch-handle{inset-inline-start:calc(var(--tumaet-ui-spacing) * 5)}@media(prefers-reduced-motion:reduce){.tum-ui-toggle-switch-handle{transition-property:background-color}}@media(forced-colors:active){.tum-ui-toggle-switch-input{appearance:auto;opacity:1}.tum-ui-toggle-switch-handle{display:none}}\n"] }]
    }], null, { disabled: [{ type: i0.Input, args: [{ isSignal: true, alias: "disabled", required: false }] }], inputId: [{ type: i0.Input, args: [{ isSignal: true, alias: "inputId", required: false }] }], ariaLabel: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabel", required: false }] }], ariaLabelledBy: [{ type: i0.Input, args: [{ isSignal: true, alias: "ariaLabelledBy", required: false }] }], changed: [{ type: i0.Output, args: ["changed"] }] }); })();
(() => { (typeof ngDevMode === "undefined" || ngDevMode) && i0.ɵsetClassDebugInfo(TumUiToggleSwitchComponent, { className: "TumUiToggleSwitchComponent", filePath: "lib/toggle-switch/tum-ui-toggle-switch.component.ts", lineNumber: 18 }); })();

/**
 * Generated bundle index. Do not edit.
 */

export { TUM_UI_FORM_FIELD, TUM_UI_TRANSLATOR, TumUiAutoCompleteComponent, TumUiBarChartComponent, TumUiButtonComponent, TumUiButtonDirective, TumUiButtonGroupComponent, TumUiCardComponent, TumUiCheckboxComponent, TumUiChipComponent, TumUiConfirmDialogComponent, TumUiConfirmationService, TumUiDatePickerComponent, TumUiDialogComponent, TumUiDoughnutChartComponent, TumUiFormFieldComponent, TumUiIconFieldComponent, TumUiInputDirective, TumUiInputGroupAddonComponent, TumUiInputGroupComponent, TumUiInputNumberComponent, TumUiLineChartComponent, TumUiListComponent, TumUiListItemActionDirective, TumUiListItemDirective, TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective, TumUiMessageComponent, TumUiPaginatorComponent, TumUiPanelComponent, TumUiPopoverComponent, TumUiPopoverTriggerDirective, TumUiProgressBarComponent, TumUiProgressSpinnerComponent, TumUiRadioButtonComponent, TumUiSearchFieldComponent, TumUiSelectButtonComponent, TumUiSelectComponent, TumUiTabComponent, TumUiTabListComponent, TumUiTabPanelComponent, TumUiTabPanelsComponent, TumUiTableComponent, TumUiTableDirective, TumUiTableSortableColumnComponent, TumUiTableVirtualScrollComponent, TumUiTabsComponent, TumUiTagComponent, TumUiToggleSwitchComponent, TumUiTooltipDirective, provideTumUiTranslator };
//# sourceMappingURL=tumaet-ui-angular.mjs.map
