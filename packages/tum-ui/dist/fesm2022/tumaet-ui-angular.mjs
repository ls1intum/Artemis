import * as i0 from "@angular/core";
import { ChangeDetectionStrategy, Component, DestroyRef, Directive, ElementRef, HostAttributeToken, Injectable, InjectionToken, Injector, Pipe, Renderer2, Service, TemplateRef, ViewContainerRef, afterNextRender, afterRenderEffect, booleanAttribute, computed, contentChild, contentChildren, effect, forwardRef, inject, input, linkedSignal, makeEnvironmentProviders, model, numberAttribute, output, signal, untracked, viewChild, viewChildren } from "@angular/core";
import * as i1$3 from "@angular/forms";
import { FormsModule, NG_VALUE_ACCESSOR } from "@angular/forms";
import { DOCUMENT, NgTemplateOutlet } from "@angular/common";
import { ComponentPortal, TemplatePortal } from "@angular/cdk/portal";
import { Overlay, STANDARD_DROPDOWN_BELOW_POSITIONS, createFlexibleConnectedPositionStrategy, createOverlayRef, createRepositionScrollStrategy } from "@angular/cdk/overlay";
import { Directionality } from "@angular/cdk/bidi";
import { FaIconComponent, FaStackComponent, FaStackItemSizeDirective } from "@fortawesome/angular-fontawesome";
import { faAngleLeft, faAngleRight, faAnglesLeft, faAnglesRight, faCalendar, faCheck, faChevronDown, faChevronLeft, faChevronRight, faChevronUp, faCircleQuestion, faClock, faGlobe, faMagnifyingGlass, faMinus, faSort, faSortDown, faSortUp, faSpinner, faXmark } from "@fortawesome/free-solid-svg-icons";
import { Dialog } from "@angular/cdk/dialog";
import * as i1$5 from "@angular/cdk/a11y";
import { A11yModule, ListKeyManager } from "@angular/cdk/a11y";
import dayjs from "dayjs/esm";
import { Subscription, fromEvent } from "rxjs";
import customParseFormat from "dayjs/esm/plugin/customParseFormat";
import * as i1$4 from "@angular/aria/menu";
import { Menu, MenuItem, MenuTrigger } from "@angular/aria/menu";
import * as i1$2 from "@angular/cdk/scrolling";
import { ScrollingModule } from "@angular/cdk/scrolling";
import * as i1$1 from "@angular/cdk/table";
import { CdkTable, CdkTableModule } from "@angular/cdk/table";
import { get } from "lodash-es";
import * as i1 from "@angular/aria/tabs";
import { Tab, TabContent, TabList, TabPanel, Tabs } from "@angular/aria/tabs";
const OFFSET = 8;
const VIEWPORT_MARGIN = OFFSET;
const VERTICAL_POSITIONS = {
	top: [{
		originX: "center",
		originY: "top",
		overlayX: "center",
		overlayY: "bottom",
		offsetY: -8
	}, {
		originX: "center",
		originY: "bottom",
		overlayX: "center",
		overlayY: "top",
		offsetY: OFFSET
	}],
	bottom: [{
		originX: "center",
		originY: "bottom",
		overlayX: "center",
		overlayY: "top",
		offsetY: OFFSET
	}, {
		originX: "center",
		originY: "top",
		overlayX: "center",
		overlayY: "bottom",
		offsetY: -8
	}]
};
var TumUiOverlayService = class TumUiOverlayService {
	overlay = inject(Overlay);
	directionality = inject(Directionality);
	positionStrategy(origin, placement) {
		const positions = placement === "top" || placement === "bottom" ? VERTICAL_POSITIONS[placement] : this.horizontalPositions(placement);
		return this.overlay.position().flexibleConnectedTo(origin).withPositions(positions).withFlexibleDimensions(false).withPush(true).withViewportMargin(VIEWPORT_MARGIN);
	}
	placementFromPosition(pos) {
		if (pos.overlayY === "center") {
			const leftEdge = this.directionality.value === "rtl" ? "start" : "end";
			return pos.overlayX === leftEdge ? "left" : "right";
		}
		return pos.overlayY === "bottom" ? "top" : "bottom";
	}
	createConnectedOverlay(origin, placement, options = {}) {
		const originElement = origin instanceof ElementRef ? origin.nativeElement : origin;
		const overlayRef = this.overlay.create({
			panelClass: "tum-ui-overlay",
			positionStrategy: this.positionStrategy(origin, placement),
			scrollStrategy: this.overlay.scrollStrategies.reposition(),
			hasBackdrop: options.hasBackdrop ?? false,
			backdropClass: "cdk-overlay-transparent-backdrop",
			direction: this.directionality,
			width: options.matchOriginWidth ? originElement.getBoundingClientRect().width : void 0
		});
		if (options.matchOriginWidth && typeof ResizeObserver !== "undefined") {
			const resizeObserver = new ResizeObserver(() => overlayRef.updateSize({ width: originElement.getBoundingClientRect().width }));
			const disconnect = () => resizeObserver.disconnect();
			resizeObserver.observe(originElement);
			overlayRef.detachments().subscribe({
				next: disconnect,
				complete: disconnect
			});
		}
		return overlayRef;
	}
	horizontalPositions(placement) {
		const leftEdge = this.directionality.value === "rtl" ? "end" : "start";
		const rightEdge = this.directionality.value === "rtl" ? "start" : "end";
		const left = {
			originX: leftEdge,
			originY: "center",
			overlayX: rightEdge,
			overlayY: "center",
			offsetX: -8
		};
		const right = {
			originX: rightEdge,
			originY: "center",
			overlayX: leftEdge,
			overlayY: "center",
			offsetX: OFFSET
		};
		return placement === "left" ? [left, right] : [right, left];
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiOverlayService,
		deps: [],
		target: i0.ɵɵFactoryTarget.Service
	});
	static ɵprov = i0.ɵɵngDeclareService({
		minVersion: "22.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiOverlayService
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiOverlayService,
	decorators: [{ type: Service }]
});
const TUM_UI_DEFAULT_TRANSLATIONS = {
	"tumUi.autocomplete.empty": "No results found",
	"tumUi.autocomplete.remove": "Remove",
	"tumUi.chip.remove": "Remove",
	"tumUi.datePicker.timeZoneWarning": "The displayed date and time use the {timeZone} time zone.",
	"tumUi.datePicker.clear": "Clear date",
	"tumUi.datePicker.decrementHour": "Decrement hour",
	"tumUi.datePicker.decrementMinute": "Decrement minute",
	"tumUi.datePicker.dialog": "Choose date and time",
	"tumUi.datePicker.done": "Done",
	"tumUi.datePicker.hour": "Hour",
	"tumUi.datePicker.incrementHour": "Increment hour",
	"tumUi.datePicker.incrementMinute": "Increment minute",
	"tumUi.datePicker.invalid": "Enter a valid date and time.",
	"tumUi.datePicker.invalidTime": "Enter a valid time.",
	"tumUi.datePicker.minute": "Minute",
	"tumUi.datePicker.open": "Open calendar",
	"tumUi.datePicker.openTime": "Open clock",
	"tumUi.datePicker.placeholder": "DD.MM.YYYY HH:mm",
	"tumUi.datePicker.nextMonth": "Next month: {month}",
	"tumUi.datePicker.previousMonth": "Previous month: {month}",
	"tumUi.datePicker.time": "Time",
	"tumUi.datePicker.timeDialog": "Choose time",
	"tumUi.datePicker.timePlaceholder": "HH:mm",
	"tumUi.dialog.close": "Close",
	"tumUi.panel.collapse": "Collapse",
	"tumUi.panel.expand": "Expand",
	"tumUi.paginator.ariaLabel": "Pagination",
	"tumUi.paginator.currentPageReport": "Showing {first} to {second} of {total}",
	"tumUi.paginator.first": "First page",
	"tumUi.paginator.last": "Last page",
	"tumUi.paginator.next": "Next page",
	"tumUi.paginator.previous": "Previous page",
	"tumUi.paginator.rowsPerPage": "Rows per page",
	"tumUi.searchField.clear": "Clear search",
	"tumUi.searchField.placeholder": "Search",
	"tumUi.select.clear": "Clear selection",
	"tumUi.select.empty": "No available options",
	"tumUi.select.filter": "Filter options",
	"tumUi.select.noResults": "No matching options",
	"tumUi.table.actions": "Actions",
	"tumUi.table.noResults": "No results found",
	"tumUi.table.searchPlaceholder": "Search"
};
function interpolate(template, params) {
	if (!params) return template;
	return template.replace(/\{(\w+)\}/g, (match, name) => String(params[name] ?? match));
}
const defaultTranslator = { translate: (key, params) => interpolate(TUM_UI_DEFAULT_TRANSLATIONS[key] ?? key, params) };
const TUM_UI_TRANSLATOR = new InjectionToken("TUM_UI_TRANSLATOR", {
	providedIn: "root",
	factory: () => defaultTranslator
});
function provideTumUiTranslator(translator) {
	return makeEnvironmentProviders([{
		provide: TUM_UI_TRANSLATOR,
		useClass: translator
	}]);
}
var TumUiTranslatePipe = class TumUiTranslatePipe {
	translator = inject(TUM_UI_TRANSLATOR);
	transform(key, params) {
		if (!key) return "";
		this.translator.translationChanges?.();
		return this.translator.translate(key, params);
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTranslatePipe,
		deps: [],
		target: i0.ɵɵFactoryTarget.Pipe
	});
	static ɵpipe = i0.ɵɵngDeclarePipe({
		minVersion: "14.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTranslatePipe,
		isStandalone: true,
		name: "tumUiTranslate",
		pure: false
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTranslatePipe,
	decorators: [{
		type: Pipe,
		args: [{
			name: "tumUiTranslate",
			pure: false
		}]
	}]
});
var TumUiChipComponent = class TumUiChipComponent {
	label = input(...ngDevMode ? [void 0, { debugName: "label" }] : /* istanbul ignore next */ []);
	removable = input(false, {
		...ngDevMode ? { debugName: "removable" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	size = input(...ngDevMode ? [void 0, { debugName: "size" }] : /* istanbul ignore next */ []);
	removeAriaLabel = input(...ngDevMode ? [void 0, { debugName: "removeAriaLabel" }] : /* istanbul ignore next */ []);
	removed = output();
	faXmark = faXmark;
	chipClasses = computed(() => {
		const small = this.size() === "small";
		return `tum:inline-flex tum:items-center tum:rounded-2xl tum:bg-hover-background tum:text-text ${small ? "tum:gap-1 tum:text-sm" : "tum:gap-2 tum:text-base"} ${small ? this.removable() ? "tum:py-1 tum:ps-2 tum:pe-1" : "tum:px-2 tum:py-1" : this.removable() ? "tum:py-2 tum:ps-3 tum:pe-2" : "tum:px-3 tum:py-2"}`;
	}, ...ngDevMode ? [{ debugName: "chipClasses" }] : /* istanbul ignore next */ []);
	remove(event) {
		this.removed.emit(event);
	}
	onRemoveKeydown(event) {
		if (event.key === "Backspace" || event.key === "Delete") {
			event.preventDefault();
			this.remove(event);
		}
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiChipComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiChipComponent,
		isStandalone: true,
		selector: "tum-ui-chip",
		inputs: {
			label: {
				classPropertyName: "label",
				publicName: "label",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			removable: {
				classPropertyName: "removable",
				publicName: "removable",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			size: {
				classPropertyName: "size",
				publicName: "size",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			removeAriaLabel: {
				classPropertyName: "removeAriaLabel",
				publicName: "removeAriaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { removed: "removed" },
		ngImport: i0,
		template: "<span [class]=\"chipClasses()\">\n    @if (label(); as chipLabel) {\n        <span class=\"tum-ui-chip-label tum:truncate\">{{ chipLabel }}</span>\n    } @else {\n        <span class=\"tum-ui-chip-label tum:truncate\"><ng-content /></span>\n    }\n    @if (removable()) {\n        <button\n            type=\"button\"\n            class=\"tum-ui-chip-remove tum:inline-flex tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:leading-none tum:text-inherit\"\n            [attr.aria-label]=\"removeAriaLabel() ?? ('tumUi.chip.remove' | tumUiTranslate)\"\n            (click)=\"remove($event)\"\n            (keydown)=\"onRemoveKeydown($event)\"\n        >\n            <fa-icon [icon]=\"faXmark\" />\n        </button>\n    }\n</span>\n",
		styles: [":host{display:inline-flex;max-width:100%}.tum-ui-chip-remove{border-radius:50%}.tum-ui-chip-remove:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:1px}\n"],
		dependencies: [{
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}, {
			kind: "pipe",
			type: TumUiTranslatePipe,
			name: "tumUiTranslate"
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiChipComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-chip",
			imports: [FaIconComponent, TumUiTranslatePipe],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<span [class]=\"chipClasses()\">\n    @if (label(); as chipLabel) {\n        <span class=\"tum-ui-chip-label tum:truncate\">{{ chipLabel }}</span>\n    } @else {\n        <span class=\"tum-ui-chip-label tum:truncate\"><ng-content /></span>\n    }\n    @if (removable()) {\n        <button\n            type=\"button\"\n            class=\"tum-ui-chip-remove tum:inline-flex tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:leading-none tum:text-inherit\"\n            [attr.aria-label]=\"removeAriaLabel() ?? ('tumUi.chip.remove' | tumUiTranslate)\"\n            (click)=\"remove($event)\"\n            (keydown)=\"onRemoveKeydown($event)\"\n        >\n            <fa-icon [icon]=\"faXmark\" />\n        </button>\n    }\n</span>\n",
			styles: [":host{display:inline-flex;max-width:100%}.tum-ui-chip-remove{border-radius:50%}.tum-ui-chip-remove:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:1px}\n"]
		}]
	}],
	propDecorators: {
		label: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "label",
				required: false
			}]
		}],
		removable: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "removable",
				required: false
			}]
		}],
		size: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "size",
				required: false
			}]
		}],
		removeAriaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "removeAriaLabel",
				required: false
			}]
		}],
		removed: [{
			type: i0.Output,
			args: ["removed"]
		}]
	}
});
let nextAutoCompleteId = 0;
var TumUiAutoCompleteComponent = class TumUiAutoCompleteComponent {
	overlayService = inject(TumUiOverlayService);
	viewContainerRef = inject(ViewContainerRef);
	destroyRef = inject(DestroyRef);
	document = inject(DOCUMENT);
	suggestions = input([], ...ngDevMode ? [{ debugName: "suggestions" }] : /* istanbul ignore next */ []);
	optionLabel = input(...ngDevMode ? [void 0, { debugName: "optionLabel" }] : /* istanbul ignore next */ []);
	multiple = input(false, {
		...ngDevMode ? { debugName: "multiple" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	placeholder = input(...ngDevMode ? [void 0, { debugName: "placeholder" }] : /* istanbul ignore next */ []);
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	minLength = input(1, {
		...ngDevMode ? { debugName: "minLength" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	debounceMs = input(300, {
		...ngDevMode ? { debugName: "debounceMs" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	completeOnFocus = input(false, {
		...ngDevMode ? { debugName: "completeOnFocus" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	inputId = input(`tum-ui-autocomplete-${nextAutoCompleteId++}`, ...ngDevMode ? [{ debugName: "inputId" }] : /* istanbul ignore next */ []);
	name = input(...ngDevMode ? [void 0, { debugName: "name" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	removeAriaLabel = input(...ngDevMode ? [void 0, { debugName: "removeAriaLabel" }] : /* istanbul ignore next */ []);
	emptyMessage = input(...ngDevMode ? [void 0, { debugName: "emptyMessage" }] : /* istanbul ignore next */ []);
	searchRequested = output();
	optionSelected = output();
	optionRemoved = output();
	listboxId = `tum-ui-autocomplete-listbox-${nextAutoCompleteId++}`;
	container = viewChild.required("container", ...ngDevMode ? [{ debugName: "container" }] : /* istanbul ignore next */ []);
	textInput = viewChild.required("textInput", ...ngDevMode ? [{ debugName: "textInput" }] : /* istanbul ignore next */ []);
	panel = viewChild.required("panel", {
		...ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {},
		read: TemplateRef
	});
	overlayRef;
	selectedValues = signal([], ...ngDevMode ? [{ debugName: "selectedValues" }] : /* istanbul ignore next */ []);
	singleValue = signal(void 0, ...ngDevMode ? [{ debugName: "singleValue" }] : /* istanbul ignore next */ []);
	query = signal("", ...ngDevMode ? [{ debugName: "query" }] : /* istanbul ignore next */ []);
	isFocused = signal(false, ...ngDevMode ? [{ debugName: "isFocused" }] : /* istanbul ignore next */ []);
	hasSearched = signal(false, ...ngDevMode ? [{ debugName: "hasSearched" }] : /* istanbul ignore next */ []);
	activeIndex = signal(-1, ...ngDevMode ? [{ debugName: "activeIndex" }] : /* istanbul ignore next */ []);
	disabledByForm = signal(false, ...ngDevMode ? [{ debugName: "disabledByForm" }] : /* istanbul ignore next */ []);
	debounceTimer;
	onChangeCallback = () => {};
	onTouchedCallback = () => {};
	isDisabled = computed(() => this.disabled() || this.disabledByForm(), ...ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []);
	labelKey = computed(() => this.optionLabel(), ...ngDevMode ? [{ debugName: "labelKey" }] : /* istanbul ignore next */ []);
	panelVisible = computed(() => this.isFocused() && this.hasSearched() && !this.isDisabled() && (this.query().length >= this.minLength() || this.completeOnFocus()), ...ngDevMode ? [{ debugName: "panelVisible" }] : /* istanbul ignore next */ []);
	activeOptionId = computed(() => this.panelVisible() && this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : void 0, ...ngDevMode ? [{ debugName: "activeOptionId" }] : /* istanbul ignore next */ []);
	inputPlaceholder = computed(() => this.multiple() && this.selectedValues().length > 0 ? void 0 : this.placeholder(), ...ngDevMode ? [{ debugName: "inputPlaceholder" }] : /* istanbul ignore next */ []);
	inputText = computed(() => {
		if (this.multiple()) return this.query();
		const value = this.singleValue();
		return value == void 0 ? "" : this.valueLabel(value);
	}, ...ngDevMode ? [{ debugName: "inputText" }] : /* istanbul ignore next */ []);
	constructor() {
		this.destroyRef.onDestroy(() => {
			this.overlayRef?.dispose();
			if (this.debounceTimer) clearTimeout(this.debounceTimer);
		});
		effect(() => {
			if (this.panelVisible()) this.openPanel();
			else this.closePanel();
		});
		effect(() => {
			const optionCount = this.suggestions().length;
			if (this.activeIndex() >= optionCount) this.activeIndex.set(optionCount > 0 ? optionCount - 1 : -1);
		});
	}
	writeValue(value) {
		if (this.multiple()) this.selectedValues.set(Array.isArray(value) ? [...value] : value == void 0 ? [] : [value]);
		else this.singleValue.set(value ?? void 0);
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
		const raw = key && value !== null && typeof value === "object" ? value[key] : value;
		return this.toText(raw);
	}
	toText(value) {
		switch (typeof value) {
			case "string": return value;
			case "number":
			case "boolean":
			case "bigint": return String(value);
			default: return "";
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
		if (!this.isDisabled()) this.textInput().nativeElement.focus();
	}
	onFocus(event) {
		this.isFocused.set(true);
		if (this.completeOnFocus() && !this.isDisabled()) this.fireComplete(this.query(), event);
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
			const singleVal = value === "" ? void 0 : value;
			this.singleValue.set(singleVal);
			this.onChangeCallback(singleVal);
		}
		if (this.debounceTimer) clearTimeout(this.debounceTimer);
		if (value.length >= this.minLength()) this.debounceTimer = setTimeout(() => this.fireComplete(value, event), this.debounceMs());
		else if (this.completeOnFocus()) this.fireComplete(value, event);
		else this.hasSearched.set(false);
	}
	fireComplete(query, originalEvent) {
		this.searchRequested.emit({
			originalEvent,
			query
		});
		this.hasSearched.set(true);
	}
	onInputKeydown(event) {
		const count = this.suggestions().length;
		switch (event.key) {
			case "ArrowDown":
				if (this.panelVisible() && count > 0) {
					event.preventDefault();
					this.setActive(Math.min(count - 1, this.activeIndex() + 1));
				}
				break;
			case "ArrowUp":
				if (this.panelVisible() && count > 0) {
					event.preventDefault();
					this.setActive(Math.max(0, this.activeIndex() - 1));
				}
				break;
			case "Enter":
				if (this.panelVisible() && this.activeIndex() >= 0 && this.activeIndex() < count) {
					event.preventDefault();
					this.selectOption(this.suggestions()[this.activeIndex()], event);
				}
				break;
			case "Escape":
				if (this.panelVisible()) {
					event.stopPropagation();
					this.hasSearched.set(false);
				}
				break;
			case "Backspace": if (this.multiple() && this.query().length === 0 && this.selectedValues().length > 0) this.removeAt(this.selectedValues().length - 1, event);
		}
	}
	setActive(index) {
		this.activeIndex.set(index);
		this.document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: "nearest" });
	}
	selectOption(option, event) {
		if (this.multiple()) {
			if (!this.isAlreadySelected(option)) {
				const next = [...this.selectedValues(), option];
				this.selectedValues.set(next);
				this.onChangeCallback(next);
				this.optionSelected.emit({
					originalEvent: event,
					value: option
				});
			}
		} else {
			this.singleValue.set(option);
			this.onChangeCallback(option);
			this.optionSelected.emit({
				originalEvent: event,
				value: option
			});
		}
		this.clearInput();
		this.focusInput();
	}
	removeAt(index, event) {
		const current = this.selectedValues();
		if (index < 0 || index >= current.length) return;
		const removed = current[index];
		const next = current.filter((_, i) => i !== index);
		this.selectedValues.set(next);
		this.onChangeCallback(next);
		this.optionRemoved.emit({
			originalEvent: event,
			value: removed
		});
		this.focusInput();
	}
	clearInput() {
		this.query.set("");
		this.hasSearched.set(false);
		this.activeIndex.set(-1);
	}
	openPanel() {
		if (this.overlayRef) return;
		const origin = this.container();
		this.overlayRef = this.overlayService.createConnectedOverlay(origin, "bottom", { matchOriginWidth: true });
		this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
	}
	closePanel() {
		this.overlayRef?.dispose();
		this.overlayRef = void 0;
	}
	optionClasses(option, index) {
		const base = "tum-ui-autocomplete-option tum:flex tum:cursor-pointer tum:items-center tum:px-3 tum:py-2";
		const active = this.activeIndex() === index;
		if (this.isAlreadySelected(option)) return `${base} tum:text-highlight ${active ? "tum:bg-highlight-focus-background" : "tum:bg-highlight-background"}`;
		return `${base} tum:text-text tum:hover:bg-hover-background tum:hover:text-text-hover${active ? " tum:bg-highlight-focus-background tum:text-highlight" : ""}`;
	}
	containerClasses() {
		const base = `tum-ui-autocomplete-container tum:box-border tum:flex tum:w-full tum:cursor-text tum:flex-wrap tum:items-center tum:gap-1 tum:rounded-md tum:border tum:text-base tum:transition-colors tum:focus-within:outline tum:focus-within:outline-2 tum:focus-within:outline-focus tum:focus-within:outline-offset-2 ${this.multiple() && this.selectedValues().length > 0 ? "tum:p-1" : "tum:py-1 tum:px-3"}`;
		let state;
		if (this.isDisabled()) state = "tum:bg-disabled-background tum:text-disabled tum:border-control-border";
		else if (this.isFocused()) state = "tum:bg-control-background tum:text-text tum:border-focus";
		else state = "tum:bg-control-background tum:text-text tum:border-control-border tum:hover:border-control-border-hover";
		return `${base} ${state}`;
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiAutoCompleteComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiAutoCompleteComponent,
		isStandalone: true,
		selector: "tum-ui-autocomplete",
		inputs: {
			suggestions: {
				classPropertyName: "suggestions",
				publicName: "suggestions",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			optionLabel: {
				classPropertyName: "optionLabel",
				publicName: "optionLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			multiple: {
				classPropertyName: "multiple",
				publicName: "multiple",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			placeholder: {
				classPropertyName: "placeholder",
				publicName: "placeholder",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			minLength: {
				classPropertyName: "minLength",
				publicName: "minLength",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			debounceMs: {
				classPropertyName: "debounceMs",
				publicName: "debounceMs",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			completeOnFocus: {
				classPropertyName: "completeOnFocus",
				publicName: "completeOnFocus",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			inputId: {
				classPropertyName: "inputId",
				publicName: "inputId",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			name: {
				classPropertyName: "name",
				publicName: "name",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			removeAriaLabel: {
				classPropertyName: "removeAriaLabel",
				publicName: "removeAriaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			emptyMessage: {
				classPropertyName: "emptyMessage",
				publicName: "emptyMessage",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: {
			searchRequested: "searchRequested",
			optionSelected: "optionSelected",
			optionRemoved: "optionRemoved"
		},
		host: { classAttribute: "tum-ui-autocomplete" },
		providers: [{
			provide: NG_VALUE_ACCESSOR,
			useExisting: forwardRef(() => TumUiAutoCompleteComponent),
			multi: true
		}],
		viewQueries: [
			{
				propertyName: "container",
				first: true,
				predicate: ["container"],
				descendants: true,
				isSignal: true
			},
			{
				propertyName: "textInput",
				first: true,
				predicate: ["textInput"],
				descendants: true,
				isSignal: true
			},
			{
				propertyName: "panel",
				first: true,
				predicate: ["panel"],
				descendants: true,
				read: TemplateRef,
				isSignal: true
			}
		],
		ngImport: i0,
		template: "<div #container [class]=\"containerClasses()\" (click)=\"focusInput()\">\n    @if (multiple()) {\n        @for (value of selectedValues(); track $index; let i = $index) {\n            <tum-ui-chip\n                size=\"small\"\n                [label]=\"valueLabel(value)\"\n                [removable]=\"!isDisabled()\"\n                [removeAriaLabel]=\"removeAriaLabel() ?? ('tumUi.autocomplete.remove' | tumUiTranslate)\"\n                (removed)=\"removeAt(i, $event)\"\n            />\n        }\n    }\n    <input\n        #textInput\n        type=\"text\"\n        role=\"combobox\"\n        [id]=\"inputId()\"\n        [attr.name]=\"name()\"\n        class=\"tum-ui-autocomplete-input tum:min-w-16 tum:flex-1 tum:border-0 tum:bg-transparent tum:p-0 tum:text-base tum:text-inherit tum:outline-none tum:placeholder:text-muted\"\n        [value]=\"inputText()\"\n        [attr.placeholder]=\"inputPlaceholder()\"\n        [disabled]=\"isDisabled()\"\n        autocomplete=\"off\"\n        aria-autocomplete=\"list\"\n        [attr.aria-expanded]=\"panelVisible() ? 'true' : 'false'\"\n        [attr.aria-controls]=\"panelVisible() ? listboxId : null\"\n        [attr.aria-activedescendant]=\"activeOptionId()\"\n        [attr.aria-label]=\"ariaLabel()\"\n        (input)=\"onInput($event)\"\n        (focus)=\"onFocus($event)\"\n        (blur)=\"onBlur()\"\n        (keydown)=\"onInputKeydown($event)\"\n    />\n</div>\n\n<ng-template #panel>\n    <div class=\"tum-ui-autocomplete-panel tum:box-border tum:w-full tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-md\">\n        <ul\n            [id]=\"listboxId\"\n            role=\"listbox\"\n            [attr.aria-label]=\"ariaLabel()\"\n            [attr.aria-multiselectable]=\"multiple() ? 'true' : null\"\n            class=\"tum:m-0 tum:flex tum:max-h-60 tum:list-none tum:flex-col tum:gap-0.5 tum:overflow-y-auto tum:p-1\"\n        >\n            @for (option of suggestions(); track $index; let i = $index) {\n                <li\n                    [id]=\"optionId(i)\"\n                    role=\"option\"\n                    [attr.aria-selected]=\"isAlreadySelected(option) ? 'true' : 'false'\"\n                    [class]=\"optionClasses(option, i)\"\n                    (mousedown)=\"$event.preventDefault()\"\n                    (click)=\"selectOption(option, $event)\"\n                    (mouseenter)=\"setActive(i)\"\n                >\n                    <span class=\"tum:flex-1 tum:truncate\">{{ valueLabel(option) }}</span>\n                </li>\n            } @empty {\n                <li role=\"option\" aria-selected=\"false\" aria-disabled=\"true\" class=\"tum:px-3 tum:py-2 tum:text-muted\">\n                    {{ emptyMessage() ?? ('tumUi.autocomplete.empty' | tumUiTranslate) }}\n                </li>\n            }\n        </ul>\n    </div>\n</ng-template>\n",
		styles: [":host{display:block;max-width:100%}\n"],
		dependencies: [{
			kind: "component",
			type: TumUiChipComponent,
			selector: "tum-ui-chip",
			inputs: [
				"label",
				"removable",
				"size",
				"removeAriaLabel"
			],
			outputs: ["removed"]
		}, {
			kind: "pipe",
			type: TumUiTranslatePipe,
			name: "tumUiTranslate"
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiAutoCompleteComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-autocomplete",
			imports: [TumUiChipComponent, TumUiTranslatePipe],
			host: { class: "tum-ui-autocomplete" },
			providers: [{
				provide: NG_VALUE_ACCESSOR,
				useExisting: forwardRef(() => TumUiAutoCompleteComponent),
				multi: true
			}],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<div #container [class]=\"containerClasses()\" (click)=\"focusInput()\">\n    @if (multiple()) {\n        @for (value of selectedValues(); track $index; let i = $index) {\n            <tum-ui-chip\n                size=\"small\"\n                [label]=\"valueLabel(value)\"\n                [removable]=\"!isDisabled()\"\n                [removeAriaLabel]=\"removeAriaLabel() ?? ('tumUi.autocomplete.remove' | tumUiTranslate)\"\n                (removed)=\"removeAt(i, $event)\"\n            />\n        }\n    }\n    <input\n        #textInput\n        type=\"text\"\n        role=\"combobox\"\n        [id]=\"inputId()\"\n        [attr.name]=\"name()\"\n        class=\"tum-ui-autocomplete-input tum:min-w-16 tum:flex-1 tum:border-0 tum:bg-transparent tum:p-0 tum:text-base tum:text-inherit tum:outline-none tum:placeholder:text-muted\"\n        [value]=\"inputText()\"\n        [attr.placeholder]=\"inputPlaceholder()\"\n        [disabled]=\"isDisabled()\"\n        autocomplete=\"off\"\n        aria-autocomplete=\"list\"\n        [attr.aria-expanded]=\"panelVisible() ? 'true' : 'false'\"\n        [attr.aria-controls]=\"panelVisible() ? listboxId : null\"\n        [attr.aria-activedescendant]=\"activeOptionId()\"\n        [attr.aria-label]=\"ariaLabel()\"\n        (input)=\"onInput($event)\"\n        (focus)=\"onFocus($event)\"\n        (blur)=\"onBlur()\"\n        (keydown)=\"onInputKeydown($event)\"\n    />\n</div>\n\n<ng-template #panel>\n    <div class=\"tum-ui-autocomplete-panel tum:box-border tum:w-full tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-md\">\n        <ul\n            [id]=\"listboxId\"\n            role=\"listbox\"\n            [attr.aria-label]=\"ariaLabel()\"\n            [attr.aria-multiselectable]=\"multiple() ? 'true' : null\"\n            class=\"tum:m-0 tum:flex tum:max-h-60 tum:list-none tum:flex-col tum:gap-0.5 tum:overflow-y-auto tum:p-1\"\n        >\n            @for (option of suggestions(); track $index; let i = $index) {\n                <li\n                    [id]=\"optionId(i)\"\n                    role=\"option\"\n                    [attr.aria-selected]=\"isAlreadySelected(option) ? 'true' : 'false'\"\n                    [class]=\"optionClasses(option, i)\"\n                    (mousedown)=\"$event.preventDefault()\"\n                    (click)=\"selectOption(option, $event)\"\n                    (mouseenter)=\"setActive(i)\"\n                >\n                    <span class=\"tum:flex-1 tum:truncate\">{{ valueLabel(option) }}</span>\n                </li>\n            } @empty {\n                <li role=\"option\" aria-selected=\"false\" aria-disabled=\"true\" class=\"tum:px-3 tum:py-2 tum:text-muted\">\n                    {{ emptyMessage() ?? ('tumUi.autocomplete.empty' | tumUiTranslate) }}\n                </li>\n            }\n        </ul>\n    </div>\n</ng-template>\n",
			styles: [":host{display:block;max-width:100%}\n"]
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		suggestions: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "suggestions",
				required: false
			}]
		}],
		optionLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "optionLabel",
				required: false
			}]
		}],
		multiple: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "multiple",
				required: false
			}]
		}],
		placeholder: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "placeholder",
				required: false
			}]
		}],
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		minLength: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "minLength",
				required: false
			}]
		}],
		debounceMs: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "debounceMs",
				required: false
			}]
		}],
		completeOnFocus: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "completeOnFocus",
				required: false
			}]
		}],
		inputId: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "inputId",
				required: false
			}]
		}],
		name: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "name",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		removeAriaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "removeAriaLabel",
				required: false
			}]
		}],
		emptyMessage: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "emptyMessage",
				required: false
			}]
		}],
		searchRequested: [{
			type: i0.Output,
			args: ["searchRequested"]
		}],
		optionSelected: [{
			type: i0.Output,
			args: ["optionSelected"]
		}],
		optionRemoved: [{
			type: i0.Output,
			args: ["optionRemoved"]
		}],
		container: [{
			type: i0.ViewChild,
			args: ["container", { isSignal: true }]
		}],
		textInput: [{
			type: i0.ViewChild,
			args: ["textInput", { isSignal: true }]
		}],
		panel: [{
			type: i0.ViewChild,
			args: ["panel", {
				read: TemplateRef,
				isSignal: true
			}]
		}]
	}
});
var TumUiButtonGroupComponent = class TumUiButtonGroupComponent {
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiButtonGroupComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "14.0.0",
		version: "22.2.0",
		type: TumUiButtonGroupComponent,
		isStandalone: true,
		selector: "tum-ui-button-group",
		host: {
			attributes: { "role": "group" },
			classAttribute: "tum-ui-button-group"
		},
		ngImport: i0,
		template: "<ng-content />",
		isInline: true,
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiButtonGroupComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-button-group",
			template: "<ng-content />",
			host: {
				role: "group",
				class: "tum-ui-button-group"
			},
			changeDetection: ChangeDetectionStrategy.OnPush
		}]
	}]
});
const BASE$2 = "tum-ui-btn tum:inline-flex tum:appearance-none tum:items-center tum:justify-center tum:gap-2 tum:rounded-md tum:border tum:font-normal tum:transition-colors tum:focus-visible:outline-none tum:disabled:opacity-60 tum:disabled:pointer-events-none";
const SOLID = {
	primary: "tum:bg-primary tum:text-primary-contrast tum:border-primary",
	secondary: "tum:bg-hover-background tum:text-text tum:border-hover-background",
	success: "tum:bg-state-success tum:text-state-success-contrast tum:border-state-success",
	info: "tum:bg-state-info tum:text-state-info-contrast tum:border-state-info",
	warn: "tum:bg-state-warning tum:text-state-warning-contrast tum:border-state-warning",
	danger: "tum:bg-state-danger tum:text-state-danger-contrast tum:border-state-danger",
	contrast: "tum:bg-contrast-background tum:text-contrast tum:border-contrast-background"
};
const OUTLINED = {
	primary: "tum:bg-transparent tum:text-accent tum:border-primary",
	secondary: "tum:bg-transparent tum:text-text tum:border-border",
	success: "tum:bg-transparent tum:text-state-success-foreground tum:border-state-success",
	info: "tum:bg-transparent tum:text-state-info-foreground tum:border-state-info",
	warn: "tum:bg-transparent tum:text-state-warning-foreground tum:border-state-warning",
	danger: "tum:bg-transparent tum:text-state-danger-foreground tum:border-state-danger",
	contrast: "tum:bg-transparent tum:text-contrast-background tum:border-contrast-background"
};
const TEXT = {
	primary: "tum:bg-transparent tum:text-accent tum:border-transparent",
	secondary: "tum:bg-transparent tum:text-muted tum:border-transparent",
	success: "tum:bg-transparent tum:text-state-success-foreground tum:border-transparent",
	info: "tum:bg-transparent tum:text-state-info-foreground tum:border-transparent",
	warn: "tum:bg-transparent tum:text-state-warning-foreground tum:border-transparent",
	danger: "tum:bg-transparent tum:text-state-danger-foreground tum:border-transparent",
	contrast: "tum:bg-transparent tum:text-contrast-background tum:border-transparent"
};
const SIZE = {
	small: "tum:text-sm tum:px-2.5 tum:py-1.5",
	default: "tum:text-base tum:px-3 tum:py-2",
	large: "tum:text-lg tum:px-4 tum:py-2.5"
};
const VARIANTS = {
	solid: SOLID,
	outlined: OUTLINED,
	text: TEXT
};
function tumUiButtonClasses(options) {
	return `${BASE$2} ${VARIANTS[options.variant][options.severity]} ${SIZE[options.size]}`;
}
var TumUiButtonComponent = class TumUiButtonComponent {
	severity = input("primary", ...ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []);
	size = input("default", ...ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []);
	variant = input("solid", ...ngDevMode ? [{ debugName: "variant" }] : /* istanbul ignore next */ []);
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	rounded = input(false, {
		...ngDevMode ? { debugName: "rounded" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	loading = input(false, {
		...ngDevMode ? { debugName: "loading" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	icon = input(void 0, ...ngDevMode ? [{ debugName: "icon" }] : /* istanbul ignore next */ []);
	type = input("button", ...ngDevMode ? [{ debugName: "type" }] : /* istanbul ignore next */ []);
	ariaLabel = input(void 0, ...ngDevMode ? [{ debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	ariaExpanded = input(void 0, ...ngDevMode ? [{ debugName: "ariaExpanded" }] : /* istanbul ignore next */ []);
	ariaPressed = input(void 0, ...ngDevMode ? [{ debugName: "ariaPressed" }] : /* istanbul ignore next */ []);
	ariaControls = input(void 0, ...ngDevMode ? [{ debugName: "ariaControls" }] : /* istanbul ignore next */ []);
	ariaDescribedBy = input(void 0, ...ngDevMode ? [{ debugName: "ariaDescribedBy" }] : /* istanbul ignore next */ []);
	clicked = output();
	faSpinner = faSpinner;
	isDisabled = computed(() => this.disabled() || this.loading(), ...ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []);
	buttonClasses = computed(() => {
		const rounded = this.rounded() ? "tum-ui-btn-rounded" : "";
		return `${tumUiButtonClasses({
			severity: this.severity(),
			size: this.size(),
			variant: this.variant()
		})} ${rounded}`.trim();
	}, ...ngDevMode ? [{ debugName: "buttonClasses" }] : /* istanbul ignore next */ []);
	onClick(event) {
		this.clicked.emit(event);
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiButtonComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiButtonComponent,
		isStandalone: true,
		selector: "tum-ui-button",
		inputs: {
			severity: {
				classPropertyName: "severity",
				publicName: "severity",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			size: {
				classPropertyName: "size",
				publicName: "size",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			variant: {
				classPropertyName: "variant",
				publicName: "variant",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			rounded: {
				classPropertyName: "rounded",
				publicName: "rounded",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			loading: {
				classPropertyName: "loading",
				publicName: "loading",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			icon: {
				classPropertyName: "icon",
				publicName: "icon",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			type: {
				classPropertyName: "type",
				publicName: "type",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaExpanded: {
				classPropertyName: "ariaExpanded",
				publicName: "ariaExpanded",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaPressed: {
				classPropertyName: "ariaPressed",
				publicName: "ariaPressed",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaControls: {
				classPropertyName: "ariaControls",
				publicName: "ariaControls",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaDescribedBy: {
				classPropertyName: "ariaDescribedBy",
				publicName: "ariaDescribedBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { clicked: "clicked" },
		ngImport: i0,
		template: "<button\n    [type]=\"type()\"\n    [class]=\"buttonClasses()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-busy]=\"loading() ? 'true' : null\"\n    [attr.aria-label]=\"ariaLabel()\"\n    [attr.aria-expanded]=\"ariaExpanded()\"\n    [attr.aria-pressed]=\"ariaPressed()\"\n    [attr.aria-controls]=\"ariaControls()\"\n    [attr.aria-describedby]=\"ariaDescribedBy()\"\n    (click)=\"onClick($event)\"\n>\n    @if (loading()) {\n        <fa-icon [icon]=\"faSpinner\" class=\"tum:animate-spin tum:motion-reduce:animate-none\" />\n    } @else if (icon(); as buttonIcon) {\n        <fa-icon [icon]=\"buttonIcon\" />\n    }\n    <ng-content />\n</button>\n",
		styles: [":host{display:inline-flex}.tum-ui-btn.tum-ui-btn-rounded{border-radius:9999px}.tum-ui-btn{position:relative;appearance:none;cursor:pointer;white-space:nowrap;border-radius:var(--tumaet-ui-radius-md)}.tum-ui-btn:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-btn:hover:not(:disabled):after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}.tum-ui-btn:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-btn:disabled{cursor:default}\n"],
		dependencies: [{
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiButtonComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-button",
			imports: [FaIconComponent],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<button\n    [type]=\"type()\"\n    [class]=\"buttonClasses()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-busy]=\"loading() ? 'true' : null\"\n    [attr.aria-label]=\"ariaLabel()\"\n    [attr.aria-expanded]=\"ariaExpanded()\"\n    [attr.aria-pressed]=\"ariaPressed()\"\n    [attr.aria-controls]=\"ariaControls()\"\n    [attr.aria-describedby]=\"ariaDescribedBy()\"\n    (click)=\"onClick($event)\"\n>\n    @if (loading()) {\n        <fa-icon [icon]=\"faSpinner\" class=\"tum:animate-spin tum:motion-reduce:animate-none\" />\n    } @else if (icon(); as buttonIcon) {\n        <fa-icon [icon]=\"buttonIcon\" />\n    }\n    <ng-content />\n</button>\n",
			styles: [":host{display:inline-flex}.tum-ui-btn.tum-ui-btn-rounded{border-radius:9999px}.tum-ui-btn{position:relative;appearance:none;cursor:pointer;white-space:nowrap;border-radius:var(--tumaet-ui-radius-md)}.tum-ui-btn:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-btn:hover:not(:disabled):after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}.tum-ui-btn:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-btn:disabled{cursor:default}\n"]
		}]
	}],
	propDecorators: {
		severity: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "severity",
				required: false
			}]
		}],
		size: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "size",
				required: false
			}]
		}],
		variant: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "variant",
				required: false
			}]
		}],
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		rounded: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "rounded",
				required: false
			}]
		}],
		loading: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "loading",
				required: false
			}]
		}],
		icon: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "icon",
				required: false
			}]
		}],
		type: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "type",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		ariaExpanded: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaExpanded",
				required: false
			}]
		}],
		ariaPressed: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaPressed",
				required: false
			}]
		}],
		ariaControls: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaControls",
				required: false
			}]
		}],
		ariaDescribedBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaDescribedBy",
				required: false
			}]
		}],
		clicked: [{
			type: i0.Output,
			args: ["clicked"]
		}]
	}
});
var TumUiButtonDirective = class TumUiButtonDirective {
	severity = input("primary", ...ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []);
	size = input("default", ...ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []);
	variant = input("solid", ...ngDevMode ? [{ debugName: "variant" }] : /* istanbul ignore next */ []);
	hostClasses = computed(() => tumUiButtonClasses({
		severity: this.severity(),
		size: this.size(),
		variant: this.variant()
	}), ...ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiButtonDirective,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiButtonDirective,
		isStandalone: true,
		selector: "a[tumUiButton], button[tumUiButton]",
		inputs: {
			severity: {
				classPropertyName: "severity",
				publicName: "severity",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			size: {
				classPropertyName: "size",
				publicName: "size",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			variant: {
				classPropertyName: "variant",
				publicName: "variant",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: { properties: { "class": "hostClasses()" } },
		ngImport: i0,
		template: "<ng-content />",
		isInline: true,
		styles: [":host{position:relative;border-radius:var(--tumaet-ui-radius-md);text-decoration:none}:host:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}:host:hover:not(:disabled):not([aria-disabled=true]):after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}:host:disabled{cursor:default}:host[aria-disabled=true]{cursor:not-allowed}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiButtonDirective,
	decorators: [{
		type: Component,
		args: [{
			selector: "a[tumUiButton], button[tumUiButton]",
			template: "<ng-content />",
			host: { "[class]": "hostClasses()" },
			changeDetection: ChangeDetectionStrategy.OnPush,
			styles: [":host{position:relative;border-radius:var(--tumaet-ui-radius-md);text-decoration:none}:host:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}:host:hover:not(:disabled):not([aria-disabled=true]):after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}:host:disabled{cursor:default}:host[aria-disabled=true]{cursor:not-allowed}\n"]
		}]
	}],
	propDecorators: {
		severity: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "severity",
				required: false
			}]
		}],
		size: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "size",
				required: false
			}]
		}],
		variant: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "variant",
				required: false
			}]
		}]
	}
});
var TumUiCardComponent = class TumUiCardComponent {
	header = input(...ngDevMode ? [void 0, { debugName: "header" }] : /* istanbul ignore next */ []);
	subheader = input(...ngDevMode ? [void 0, { debugName: "subheader" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiCardComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiCardComponent,
		isStandalone: true,
		selector: "tum-ui-card",
		inputs: {
			header: {
				classPropertyName: "header",
				publicName: "header",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			subheader: {
				classPropertyName: "subheader",
				publicName: "subheader",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: { classAttribute: "tum-ui-card tum:flex tum:flex-col tum:rounded-xl tum:shadow-sm tum:bg-overlay-background tum:text-text" },
		ngImport: i0,
		template: "<ng-content select=\"[tumUiCardHeader]\" />\n<div class=\"tum-ui-card-body tum:flex tum:flex-col tum:gap-2 tum:p-5\">\n    @if (header() || subheader()) {\n        <div class=\"tum-ui-card-caption tum:flex tum:flex-col tum:gap-2\">\n            @if (header()) {\n                <div class=\"tum-ui-card-title tum:text-xl tum:font-medium\">{{ header() }}</div>\n            }\n            @if (subheader()) {\n                <div class=\"tum-ui-card-subtitle tum:text-muted\">{{ subheader() }}</div>\n            }\n        </div>\n    }\n    <div class=\"tum-ui-card-content\">\n        <ng-content />\n    </div>\n    <ng-content select=\"[tumUiCardFooter]\" />\n</div>\n",
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiCardComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-card",
			host: { class: "tum-ui-card tum:flex tum:flex-col tum:rounded-xl tum:shadow-sm tum:bg-overlay-background tum:text-text" },
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<ng-content select=\"[tumUiCardHeader]\" />\n<div class=\"tum-ui-card-body tum:flex tum:flex-col tum:gap-2 tum:p-5\">\n    @if (header() || subheader()) {\n        <div class=\"tum-ui-card-caption tum:flex tum:flex-col tum:gap-2\">\n            @if (header()) {\n                <div class=\"tum-ui-card-title tum:text-xl tum:font-medium\">{{ header() }}</div>\n            }\n            @if (subheader()) {\n                <div class=\"tum-ui-card-subtitle tum:text-muted\">{{ subheader() }}</div>\n            }\n        </div>\n    }\n    <div class=\"tum-ui-card-content\">\n        <ng-content />\n    </div>\n    <ng-content select=\"[tumUiCardFooter]\" />\n</div>\n"
		}]
	}],
	propDecorators: {
		header: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "header",
				required: false
			}]
		}],
		subheader: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "subheader",
				required: false
			}]
		}]
	}
});
function bandScale(count, size, padding = .25) {
	const step = size / Math.max(count, 1);
	const bandwidth = Math.max(step * (1 - padding), 0);
	const offset = (step - bandwidth) / 2;
	const position = (index) => index * step + offset;
	return {
		position,
		bandwidth,
		center: (index) => position(index) + bandwidth / 2
	};
}
const E10 = Math.sqrt(50);
const E5 = Math.sqrt(10);
const E2 = Math.sqrt(2);
function tickStep(start, stop, count, minStep = 0) {
	const rough = Math.abs(stop - start) / Math.max(count, 1);
	if (!Number.isFinite(rough) || rough === 0) return Math.max(1, minStep);
	const power = Math.pow(10, Math.floor(Math.log10(rough)));
	const error = rough / power;
	let step = power;
	if (error >= E10) step = power * 10;
	else if (error >= E5) step = power * 5;
	else if (error >= E2) step = power * 2;
	return minStep > 0 ? Math.max(minStep, Math.ceil(step / minStep) * minStep) : step;
}
function roundToStep(value, step) {
	const decimals = Math.max(0, -Math.floor(Math.log10(step)) + 1);
	return Number(value.toFixed(Math.min(decimals, 20)));
}
function niceDomain(min, max, count = 5, minStep = 0) {
	if (!Number.isFinite(min) || !Number.isFinite(max)) return [0, 1];
	if (min === max) return min === 0 ? [0, 1] : [Math.min(0, min), Math.max(0, max)];
	const step = tickStep(min, max, count, minStep);
	return [roundToStep(Math.floor(min / step) * step, step), roundToStep(Math.ceil(max / step) * step, step)];
}
function linearScale(domain, range) {
	const [d0, d1] = domain;
	const [r0, r1] = range;
	const span = d1 - d0;
	const scale = ((value) => span === 0 ? r0 : r0 + (value - d0) / span * (r1 - r0));
	scale.domain = domain;
	scale.ticks = (count = 5, minStep = 0) => {
		if (span === 0) return Number.isFinite(d0) ? [d0] : [];
		const step = tickStep(d0, d1, count, minStep);
		const first = Math.ceil(d0 / step);
		const last = Math.floor(d1 / step);
		if (!Number.isFinite(first) || !Number.isFinite(last)) return [];
		const result = [];
		for (let i = first; i <= last; i++) result.push(roundToStep(i * step, step));
		return result;
	};
	return scale;
}
function approximateTextWidth(text, fontSize) {
	return text.length * fontSize * .58;
}
function allIntegers(values) {
	return values.every((value) => value === void 0 || Number.isInteger(value));
}
function finiteValues(values) {
	return values.filter((value) => typeof value === "number" && Number.isFinite(value));
}
const CATEGORY_PADDING = .25;
const MAX_CATEGORY_AXIS_SHARE = .33;
const ROTATED_LABEL_PROJECTION = .72;
function titleAllowance(title) {
	return title ? 18 : 0;
}
function cartesianFrame(input) {
	const valueAxisVisible = input.valueAxis?.display ?? true;
	const categoryAxisVisible = input.categoryAxis?.display ?? true;
	const valueTickWidth = valueAxisVisible ? Math.max(...input.valueTicks.map((tick) => approximateTextWidth(tick.text, 11)), 0) : 0;
	const format = input.categoryAxis?.tickFormatter;
	const categoryLabelWidth = categoryAxisVisible ? Math.max(...input.labels.map((label) => approximateTextWidth(format ? format(label) : label, 11)), 0) : 0;
	const endPadding = input.valueEndPadding ?? 0;
	let margin;
	let rotateCategoryLabels = false;
	let rotatedHeight = 0;
	if (input.horizontal) {
		const categoryAllowance = Math.min(categoryLabelWidth, input.size.width * MAX_CATEGORY_AXIS_SHARE);
		margin = {
			top: 8,
			right: 8 + endPadding,
			bottom: (valueAxisVisible ? 17 : 0) + titleAllowance(input.xAxisTitle),
			left: categoryAllowance + 6 + titleAllowance(input.yAxisTitle)
		};
	} else {
		const available = Math.max(input.size.width - valueTickWidth - 6 - 8, 1);
		const required = input.labels.reduce((sum, label) => sum + approximateTextWidth(format ? format(label) : label, 11) + 8, 0);
		rotateCategoryLabels = categoryAxisVisible && required > available;
		rotatedHeight = Math.min(categoryLabelWidth * ROTATED_LABEL_PROJECTION, Math.max(input.size.height * MAX_CATEGORY_AXIS_SHARE, 0));
		const categoryBandHeight = categoryAxisVisible ? (rotateCategoryLabels ? rotatedHeight : 11) + 6 : 0;
		margin = {
			top: 8 + endPadding,
			right: 8,
			bottom: categoryBandHeight + titleAllowance(input.xAxisTitle),
			left: Math.max(valueTickWidth + 6, rotateCategoryLabels ? rotatedHeight * .7 : 0) + titleAllowance(input.yAxisTitle)
		};
	}
	const plotWidth = Math.max(input.size.width - margin.left - margin.right, 0);
	return {
		margin,
		plot: {
			width: Math.max(input.size.width - margin.left - margin.right, 0),
			height: Math.max(input.size.height - margin.top - margin.bottom, 0),
			left: margin.left,
			top: margin.top
		},
		rotateCategoryLabels,
		categoryLabelBudget: input.horizontal ? Math.max(margin.left - 6 - titleAllowance(input.yAxisTitle), 0) : rotateCategoryLabels ? Math.max(rotatedHeight / ROTATED_LABEL_PROJECTION, 0) : Math.max(plotWidth / Math.max(input.labels.length, 1) - 6, 0)
	};
}
function truncateToWidth(text, budget) {
	if (!Number.isFinite(budget) || approximateTextWidth(text, 11) <= budget) return text;
	const perCharacter = approximateTextWidth("n", 11);
	const fits = Math.max(Math.floor(budget / perCharacter) - 1, 1);
	return `${text.slice(0, fits)}…`;
}
function valueTickViews(plot, scale, ticks, horizontal) {
	return ticks.map((tick) => horizontal ? {
		key: `v${tick.value}`,
		text: tick.text,
		x: scale(tick.value),
		y: plot.height + 6 + 8.8,
		anchor: "middle",
		rotate: 0
	} : {
		key: `v${tick.value}`,
		text: tick.text,
		x: -6,
		y: scale(tick.value) + 11 * .35,
		anchor: "end",
		rotate: 0
	});
}
function categoryTickViews(plot, categories, labels, horizontal, rotate, formatter, labelBudget = Number.POSITIVE_INFINITY) {
	const skip = categoryTickSkip(plot, labels, horizontal, rotate, formatter);
	return labels.flatMap((label, index) => {
		if (index % skip !== 0) return [];
		const center = categories.center(index);
		const text = truncateToWidth(formatter ? formatter(label) : label, labelBudget);
		if (horizontal) return [{
			key: `c${index}`,
			text,
			x: -6,
			y: center + 11 * .35,
			anchor: "end",
			rotate: 0
		}];
		return [{
			key: `c${index}`,
			text,
			x: center,
			y: plot.height + 6 + (rotate ? 4.4 : 8.8),
			anchor: rotate ? "end" : "middle",
			rotate: rotate ? -45 : 0
		}];
	});
}
function categoryTickSkip(plot, labels, horizontal, rotate, formatter) {
	const available = horizontal ? plot.height : plot.width;
	if (!labels.length || available <= 0) return 1;
	const perLabel = horizontal ? 15 : Math.max(...labels.map((label) => approximateTextWidth(formatter ? formatter(label) : label, 11)), 1) + 8;
	const required = labels.length * (rotate ? perLabel / 3 : perLabel);
	return Math.max(Math.ceil(required / available), 1);
}
function gridLineViews(plot, scale, ticks, horizontal) {
	return ticks.map((tick) => {
		const at = scale(tick.value);
		return horizontal ? {
			key: `g${tick.value}`,
			x1: at,
			y1: 0,
			x2: at,
			y2: plot.height
		} : {
			key: `g${tick.value}`,
			x1: 0,
			y1: at,
			x2: plot.width,
			y2: at
		};
	});
}
function axisTitleViews(plot, margin, xTitle, yTitle) {
	return {
		x: xTitle ? {
			text: xTitle,
			x: plot.width / 2,
			y: plot.height + margin.bottom - 2,
			rotate: 0
		} : void 0,
		y: yTitle ? {
			text: yTitle,
			x: -(margin.left - 12),
			y: plot.height / 2,
			rotate: -90
		} : void 0
	};
}
function legendPositionOf(legend) {
	if (!legend) return;
	return typeof legend === "object" ? legend.position ?? "right" : "right";
}
const ASSUMED_TOOLTIP_HALF_WIDTH = 110;
const TOOLTIP_CLEARANCE = 90;
function placeTooltip(hovered) {
	const min = Math.min(118, hovered.hostWidth / 2);
	const max = Math.max(hovered.hostWidth - ASSUMED_TOOLTIP_HALF_WIDTH - 8, min);
	return {
		x: Math.min(Math.max(hovered.x, min), max),
		y: hovered.y,
		below: hovered.y < TOOLTIP_CLEARANCE
	};
}
function datumAccessibleName(context, multiSeries) {
	return `${multiSeries && context.seriesLabel ? `${context.seriesLabel}, ` : ""}${context.label}: ${context.value}`;
}
var TumUiChartAxesComponent = class TumUiChartAxesComponent {
	gridLines = input([], ...ngDevMode ? [{ debugName: "gridLines" }] : /* istanbul ignore next */ []);
	ticks = input([], ...ngDevMode ? [{ debugName: "ticks" }] : /* istanbul ignore next */ []);
	titles = input([], ...ngDevMode ? [{ debugName: "titles" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiChartAxesComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiChartAxesComponent,
		isStandalone: true,
		selector: "g[tumUiChartAxes]",
		inputs: {
			gridLines: {
				classPropertyName: "gridLines",
				publicName: "gridLines",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ticks: {
				classPropertyName: "ticks",
				publicName: "ticks",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			titles: {
				classPropertyName: "titles",
				publicName: "titles",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		ngImport: i0,
		template: `
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
    `,
		isInline: true,
		styles: [".tum-ui-chart-grid{stroke:var(--tumaet-ui-border-color);stroke-width:1;shape-rendering:crispedges}.tum-ui-chart-tick{fill:var(--tumaet-ui-muted-color);font-size:var(--tumaet-ui-font-size-xs);font-family:inherit}.tum-ui-chart-axis-title{fill:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-sm);font-family:inherit}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiChartAxesComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "g[tumUiChartAxes]",
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: `
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
    `,
			styles: [".tum-ui-chart-grid{stroke:var(--tumaet-ui-border-color);stroke-width:1;shape-rendering:crispedges}.tum-ui-chart-tick{fill:var(--tumaet-ui-muted-color);font-size:var(--tumaet-ui-font-size-xs);font-family:inherit}.tum-ui-chart-axis-title{fill:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-sm);font-family:inherit}\n"]
		}]
	}],
	propDecorators: {
		gridLines: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "gridLines",
				required: false
			}]
		}],
		ticks: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ticks",
				required: false
			}]
		}],
		titles: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "titles",
				required: false
			}]
		}]
	}
});
var TumUiChartLegendComponent = class TumUiChartLegendComponent {
	items = input([], ...ngDevMode ? [{ debugName: "items" }] : /* istanbul ignore next */ []);
	position = input("right", ...ngDevMode ? [{ debugName: "position" }] : /* istanbul ignore next */ []);
	toggleEntry = output();
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiChartLegendComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiChartLegendComponent,
		isStandalone: true,
		selector: "tum-ui-chart-legend",
		inputs: {
			items: {
				classPropertyName: "items",
				publicName: "items",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			position: {
				classPropertyName: "position",
				publicName: "position",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { toggleEntry: "toggleEntry" },
		host: {
			properties: { "attr.data-position": "position()" },
			classAttribute: "tum-ui-chart-legend"
		},
		ngImport: i0,
		template: `
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
    `,
		isInline: true,
		styles: [":host{display:block;align-self:center;font-size:var(--tumaet-ui-font-size-xs);color:var(--tumaet-ui-text-color)}.tum-ui-chart-legend-list{display:flex;flex-direction:column;gap:calc(var(--tumaet-ui-spacing) * 1);margin:0;padding:0;list-style:none}:host([data-position=\"top\"]) .tum-ui-chart-legend-list,:host([data-position=\"bottom\"]) .tum-ui-chart-legend-list{flex-direction:row;flex-wrap:wrap;justify-content:center}.tum-ui-chart-legend-item{display:flex;align-items:center;gap:calc(var(--tumaet-ui-spacing) * 1);white-space:nowrap;min-height:24px;padding:0 calc(var(--tumaet-ui-spacing) * 1);border:0;background:none;color:inherit;font:inherit;cursor:pointer}.tum-ui-chart-legend-item[aria-pressed=false]{opacity:.45;text-decoration:line-through}.tum-ui-chart-legend-swatch{width:10px;height:10px;border-radius:var(--tumaet-ui-radius-sm);flex:none}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiChartLegendComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-chart-legend",
			changeDetection: ChangeDetectionStrategy.OnPush,
			host: {
				class: "tum-ui-chart-legend",
				"[attr.data-position]": "position()"
			},
			template: `
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
    `,
			styles: [":host{display:block;align-self:center;font-size:var(--tumaet-ui-font-size-xs);color:var(--tumaet-ui-text-color)}.tum-ui-chart-legend-list{display:flex;flex-direction:column;gap:calc(var(--tumaet-ui-spacing) * 1);margin:0;padding:0;list-style:none}:host([data-position=\"top\"]) .tum-ui-chart-legend-list,:host([data-position=\"bottom\"]) .tum-ui-chart-legend-list{flex-direction:row;flex-wrap:wrap;justify-content:center}.tum-ui-chart-legend-item{display:flex;align-items:center;gap:calc(var(--tumaet-ui-spacing) * 1);white-space:nowrap;min-height:24px;padding:0 calc(var(--tumaet-ui-spacing) * 1);border:0;background:none;color:inherit;font:inherit;cursor:pointer}.tum-ui-chart-legend-item[aria-pressed=false]{opacity:.45;text-decoration:line-through}.tum-ui-chart-legend-swatch{width:10px;height:10px;border-radius:var(--tumaet-ui-radius-sm);flex:none}\n"]
		}]
	}],
	propDecorators: {
		items: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "items",
				required: false
			}]
		}],
		position: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "position",
				required: false
			}]
		}],
		toggleEntry: [{
			type: i0.Output,
			args: ["toggleEntry"]
		}]
	}
});
var TumUiChartTooltipComponent = class TumUiChartTooltipComponent {
	title = input(...ngDevMode ? [void 0, { debugName: "title" }] : /* istanbul ignore next */ []);
	lines = input([], ...ngDevMode ? [{ debugName: "lines" }] : /* istanbul ignore next */ []);
	x = input(0, ...ngDevMode ? [{ debugName: "x" }] : /* istanbul ignore next */ []);
	y = input(0, ...ngDevMode ? [{ debugName: "y" }] : /* istanbul ignore next */ []);
	below = input(false, ...ngDevMode ? [{ debugName: "below" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiChartTooltipComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiChartTooltipComponent,
		isStandalone: true,
		selector: "tum-ui-chart-tooltip",
		inputs: {
			title: {
				classPropertyName: "title",
				publicName: "title",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			lines: {
				classPropertyName: "lines",
				publicName: "lines",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			x: {
				classPropertyName: "x",
				publicName: "x",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			y: {
				classPropertyName: "y",
				publicName: "y",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			below: {
				classPropertyName: "below",
				publicName: "below",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: {
			attributes: { "role": "tooltip" },
			properties: {
				"style.left.px": "x()",
				"style.top.px": "y()",
				"attr.data-below": "below()"
			},
			classAttribute: "tum-ui-chart-tooltip tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:px-2 tum:py-1.5 tum:text-text tum:shadow-lg"
		},
		ngImport: i0,
		template: `
        @if (title()) {
            <div class="tum-ui-chart-tooltip-title">{{ title() }}</div>
        }
        @for (line of lines(); track $index) {
            <div>{{ line }}</div>
        }
    `,
		isInline: true,
		styles: [":host{display:block;position:absolute;z-index:1;transform:translate(-50%,calc(-100% - 12px));max-width:22rem;font-size:var(--tumaet-ui-font-size-xs);pointer-events:none}:host([data-below=\"true\"]){transform:translate(-50%,12px)}.tum-ui-chart-tooltip-title{font-weight:600}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiChartTooltipComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-chart-tooltip",
			changeDetection: ChangeDetectionStrategy.OnPush,
			host: {
				class: "tum-ui-chart-tooltip tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:px-2 tum:py-1.5 tum:text-text tum:shadow-lg",
				role: "tooltip",
				"[style.left.px]": "x()",
				"[style.top.px]": "y()",
				"[attr.data-below]": "below()"
			},
			template: `
        @if (title()) {
            <div class="tum-ui-chart-tooltip-title">{{ title() }}</div>
        }
        @for (line of lines(); track $index) {
            <div>{{ line }}</div>
        }
    `,
			styles: [":host{display:block;position:absolute;z-index:1;transform:translate(-50%,calc(-100% - 12px));max-width:22rem;font-size:var(--tumaet-ui-font-size-xs);pointer-events:none}:host([data-below=\"true\"]){transform:translate(-50%,12px)}.tum-ui-chart-tooltip-title{font-weight:600}\n"]
		}]
	}],
	propDecorators: {
		title: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "title",
				required: false
			}]
		}],
		lines: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "lines",
				required: false
			}]
		}],
		x: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "x",
				required: false
			}]
		}],
		y: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "y",
				required: false
			}]
		}],
		below: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "below",
				required: false
			}]
		}]
	}
});
var TumUiChartDataTableComponent = class TumUiChartDataTableComponent {
	caption = input(...ngDevMode ? [void 0, { debugName: "caption" }] : /* istanbul ignore next */ []);
	rows = input([], ...ngDevMode ? [{ debugName: "rows" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiChartDataTableComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiChartDataTableComponent,
		isStandalone: true,
		selector: "tum-ui-chart-data-table",
		inputs: {
			caption: {
				classPropertyName: "caption",
				publicName: "caption",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			rows: {
				classPropertyName: "rows",
				publicName: "rows",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: { classAttribute: "tum-ui-chart-data-table" },
		ngImport: i0,
		template: `
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
    `,
		isInline: true,
		styles: [":host{position:absolute;width:1px;height:1px;margin:-1px;padding:0;overflow:hidden;clip-path:inset(50%);white-space:nowrap;border:0;-webkit-user-select:none;user-select:none}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiChartDataTableComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-chart-data-table",
			changeDetection: ChangeDetectionStrategy.OnPush,
			host: { class: "tum-ui-chart-data-table" },
			template: `
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
    `,
			styles: [":host{position:absolute;width:1px;height:1px;margin:-1px;padding:0;overflow:hidden;clip-path:inset(50%);white-space:nowrap;border:0;-webkit-user-select:none;user-select:none}\n"]
		}]
	}],
	propDecorators: {
		caption: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "caption",
				required: false
			}]
		}],
		rows: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "rows",
				required: false
			}]
		}]
	}
});
const SERIES_GROUP_PADDING = .08;
function finiteOr0(value) {
	return typeof value === "number" && Number.isFinite(value) ? value : 0;
}
let nextBarChartId = 0;
var TumUiBarChartComponent = class TumUiBarChartComponent {
	hostElement = inject(ElementRef);
	canvas = viewChild.required("canvas", ...ngDevMode ? [{ debugName: "canvas" }] : /* istanbul ignore next */ []);
	labels = input.required(...ngDevMode ? [{ debugName: "labels" }] : /* istanbul ignore next */ []);
	series = input.required(...ngDevMode ? [{ debugName: "series" }] : /* istanbul ignore next */ []);
	config = input({}, ...ngDevMode ? [{ debugName: "config" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	ariaLabelledBy = input(...ngDevMode ? [void 0, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []);
	interactive = input(false, {
		...ngDevMode ? { debugName: "interactive" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	dataSelect = output();
	accessibleName(context) {
		return datumAccessibleName(context, this.series().length > 1);
	}
	size = signal({
		width: 0,
		height: 0
	}, ...ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []);
	hovered = signal(void 0, ...ngDevMode ? [{ debugName: "hovered" }] : /* istanbul ignore next */ []);
	resizeObserver;
	constructor() {
		afterNextRender(() => {
			const element = this.canvas().nativeElement;
			const rect = element.getBoundingClientRect();
			this.size.set({
				width: rect.width,
				height: rect.height
			});
			if (typeof ResizeObserver === "undefined") return;
			this.resizeObserver = new ResizeObserver((entries) => {
				const box = entries[0]?.contentRect;
				if (box) this.size.set({
					width: box.width,
					height: box.height
				});
			});
			this.resizeObserver.observe(element);
		});
	}
	ngOnDestroy() {
		this.resizeObserver?.disconnect();
	}
	hiddenSeries = signal(/* @__PURE__ */ new Set(), ...ngDevMode ? [{ debugName: "hiddenSeries" }] : /* istanbul ignore next */ []);
	onLegendToggle(key) {
		this.hiddenSeries.update((hidden) => {
			const next = new Set(hidden);
			if (!next.delete(key)) next.add(key);
			return next;
		});
	}
	visibleSeries = computed(() => this.series().map((entry, index) => ({
		entry,
		index
	})).filter(({ index }) => !this.hiddenSeries().has(`${index}`)), ...ngDevMode ? [{ debugName: "visibleSeries" }] : /* istanbul ignore next */ []);
	horizontal = computed(() => this.config().horizontal ?? false, ...ngDevMode ? [{ debugName: "horizontal" }] : /* istanbul ignore next */ []);
	stacked = computed(() => this.config().stacked ?? false, ...ngDevMode ? [{ debugName: "stacked" }] : /* istanbul ignore next */ []);
	valueAxis = computed(() => this.horizontal() ? this.config().xAxis : this.config().yAxis, ...ngDevMode ? [{ debugName: "valueAxis" }] : /* istanbul ignore next */ []);
	categoryAxis = computed(() => this.horizontal() ? this.config().yAxis : this.config().xAxis, ...ngDevMode ? [{ debugName: "categoryAxis" }] : /* istanbul ignore next */ []);
	minTickStep = computed(() => allIntegers(this.visibleSeries().flatMap(({ entry }) => [...entry.data])) ? 1 : 0, ...ngDevMode ? [{ debugName: "minTickStep" }] : /* istanbul ignore next */ []);
	valueDomain = computed(() => {
		const axis = this.valueAxis();
		const percent = this.config().percentScale ?? false;
		const visible = this.visibleSeries().map(({ entry }) => entry);
		const totals = this.stacked() ? this.labels().flatMap((_, index) => [visible.reduce((sum, entry) => sum + Math.max(finiteOr0(entry.data[index]), 0), 0), visible.reduce((sum, entry) => sum + Math.min(finiteOr0(entry.data[index]), 0), 0)]) : finiteValues(visible.flatMap((entry) => [...entry.data]));
		const dataMax = totals.length ? Math.max(...totals) : 0;
		const dataMin = totals.length ? Math.min(...totals) : 0;
		const [niceMin, niceMax] = niceDomain(Math.min(0, dataMin), dataMax, 5, this.minTickStep());
		return [axis?.min ?? niceMin, axis?.max ?? (percent ? 100 : niceMax)];
	}, ...ngDevMode ? [{ debugName: "valueDomain" }] : /* istanbul ignore next */ []);
	valueTickLabels = computed(() => {
		if ((this.valueAxis()?.display ?? true) === false) return [];
		const [min, max] = this.valueDomain();
		const percent = this.config().percentScale ?? false;
		const format = this.valueAxis()?.tickFormatter ?? (percent ? (value) => `${value}%` : void 0);
		return linearScale([min, max], [0, 1]).ticks(5, this.minTickStep()).map((value) => ({
			value,
			text: format ? format(value) : `${value}`
		}));
	}, ...ngDevMode ? [{ debugName: "valueTickLabels" }] : /* istanbul ignore next */ []);
	frame = computed(() => cartesianFrame({
		size: this.size(),
		labels: this.categoryAxis()?.display ?? true ? this.labels() : [],
		valueTicks: this.valueTickLabels(),
		horizontal: this.horizontal(),
		valueAxis: this.valueAxis(),
		categoryAxis: this.categoryAxis(),
		xAxisTitle: this.config().xAxis?.label,
		yAxisTitle: this.config().yAxis?.label,
		valueEndPadding: this.config().dataLabels ? 15 : 0
	}), ...ngDevMode ? [{ debugName: "frame" }] : /* istanbul ignore next */ []);
	plot = computed(() => this.frame().plot, ...ngDevMode ? [{ debugName: "plot" }] : /* istanbul ignore next */ []);
	clipId = `tum-ui-bar-chart-clip-${nextBarChartId++}`;
	valueScale = computed(() => {
		const plot = this.plot();
		const [min, max] = this.valueDomain();
		return this.horizontal() ? linearScale([min, max], [0, plot.width]) : linearScale([min, max], [plot.height, 0]);
	}, ...ngDevMode ? [{ debugName: "valueScale" }] : /* istanbul ignore next */ []);
	categoryScale = computed(() => {
		const plot = this.plot();
		return bandScale(this.labels().length, this.horizontal() ? plot.height : plot.width, CATEGORY_PADDING);
	}, ...ngDevMode ? [{ debugName: "categoryScale" }] : /* istanbul ignore next */ []);
	bars = computed(() => {
		const plot = this.plot();
		if (plot.width <= 0 || plot.height <= 0) return [];
		const horizontal = this.horizontal();
		const stacked = this.stacked();
		const labels = this.labels();
		const series = this.visibleSeries();
		const [min, max] = this.valueDomain();
		const categories = this.categoryScale();
		const valueScale = this.valueScale();
		const groupScale = !stacked && series.length > 1 ? bandScale(series.length, categories.bandwidth, SERIES_GROUP_PADDING) : void 0;
		const maxThickness = this.config().maxBarThickness ?? Number.POSITIVE_INFINITY;
		const thickness = Math.min(groupScale ? groupScale.bandwidth : categories.bandwidth, maxThickness);
		const dataLabels = this.config().dataLabels;
		const positiveOffsets = labels.map(() => 0);
		const negativeOffsets = labels.map(() => 0);
		const bars = [];
		series.forEach(({ entry, index: seriesIndex }, drawIndex) => {
			labels.forEach((label, index) => {
				const raw = entry.data[index];
				if (raw === void 0 || raw === null || !Number.isFinite(raw)) return;
				const bandStart = categories.position(index);
				const groupOffset = groupScale ? groupScale.position(drawIndex) : 0;
				const centering = (groupScale ? groupScale.bandwidth : categories.bandwidth) - thickness;
				const crossStart = bandStart + groupOffset + centering / 2;
				const offsets = raw < 0 ? negativeOffsets : positiveOffsets;
				const start = stacked ? offsets[index] : Math.min(Math.max(0, min), max);
				const end = stacked ? offsets[index] + raw : raw;
				if (stacked) offsets[index] = end;
				const from = valueScale(start);
				const to = valueScale(end);
				const context = {
					seriesIndex,
					index,
					label,
					seriesLabel: entry.label,
					value: raw,
					meta: entry.meta?.[index]
				};
				const color = entry.colors?.[index % entry.colors.length] ?? entry.color ?? "var(--tumaet-ui-primary-color)";
				const bar = horizontal ? {
					key: `${seriesIndex}-${index}`,
					x: Math.min(from, to),
					y: crossStart,
					width: Math.abs(to - from),
					height: thickness,
					color,
					context
				} : {
					key: `${seriesIndex}-${index}`,
					x: crossStart,
					y: Math.min(from, to),
					width: thickness,
					height: Math.abs(to - from),
					color,
					context
				};
				if (dataLabels) {
					const text = dataLabels.formatter(raw, context);
					bar.dataLabel = horizontal ? {
						x: bar.x + bar.width + 4,
						y: bar.y + bar.height / 2,
						anchor: "start",
						text
					} : {
						x: bar.x + bar.width / 2,
						y: bar.y - 4,
						anchor: "middle",
						text
					};
				}
				bars.push(bar);
			});
		});
		return bars;
	}, ...ngDevMode ? [{ debugName: "bars" }] : /* istanbul ignore next */ []);
	gridLines = computed(() => this.valueAxis()?.display ?? true ? gridLineViews(this.plot(), this.valueScale(), this.valueTickLabels(), this.horizontal()) : [], ...ngDevMode ? [{ debugName: "gridLines" }] : /* istanbul ignore next */ []);
	ticks = computed(() => {
		const plot = this.plot();
		const horizontal = this.horizontal();
		const value = this.valueAxis()?.display ?? true ? valueTickViews(plot, this.valueScale(), this.valueTickLabels(), horizontal) : [];
		const category = this.categoryAxis()?.display ?? true ? categoryTickViews(plot, this.categoryScale(), this.labels(), horizontal, this.frame().rotateCategoryLabels, this.categoryAxis()?.tickFormatter, this.frame().categoryLabelBudget) : [];
		return [...value, ...category];
	}, ...ngDevMode ? [{ debugName: "ticks" }] : /* istanbul ignore next */ []);
	axisTitles = computed(() => {
		const titles = axisTitleViews(this.plot(), this.frame().margin, this.config().xAxis?.label, this.config().yAxis?.label);
		return [titles.x, titles.y].filter((title) => title !== void 0);
	}, ...ngDevMode ? [{ debugName: "axisTitles" }] : /* istanbul ignore next */ []);
	legendPosition = computed(() => legendPositionOf(this.config().legend), ...ngDevMode ? [{ debugName: "legendPosition" }] : /* istanbul ignore next */ []);
	legendItems = computed(() => this.series().map((entry, index) => ({
		entry,
		index
	})).filter(({ entry }) => entry.label).map(({ entry, index }) => ({
		key: `${index}`,
		label: entry.label,
		color: entry.color ?? "var(--tumaet-ui-primary-color)",
		hidden: this.hiddenSeries().has(`${index}`)
	})), ...ngDevMode ? [{ debugName: "legendItems" }] : /* istanbul ignore next */ []);
	tooltip = computed(() => {
		const hovered = this.hovered();
		const config = this.config().tooltip;
		if (!hovered || config === false) return;
		const bar = this.bars().find((candidate) => candidate.context.index === hovered.index && candidate.context.seriesIndex === hovered.seriesIndex);
		if (!bar) return;
		const context = bar.context;
		const title = config?.title ? config.title([context]) : context.label;
		const raw = config?.label ? config.label(context) : `${context.seriesLabel ? `${context.seriesLabel}: ` : ""}${context.value}`;
		const after = config?.afterBody?.([context]);
		return {
			title,
			lines: [...Array.isArray(raw) ? raw : [raw], ...after ? Array.isArray(after) ? after : [after] : []].filter((line) => line !== ""),
			...placeTooltip(hovered)
		};
	}, ...ngDevMode ? [{ debugName: "tooltip" }] : /* istanbul ignore next */ []);
	accessibleRows = computed(() => this.labels().map((label, index) => ({
		label,
		values: this.series().map((entry) => ({
			seriesLabel: entry.label,
			value: entry.data[index]
		}))
	})), ...ngDevMode ? [{ debugName: "accessibleRows" }] : /* istanbul ignore next */ []);
	onBarEnter(bar, event) {
		const host = this.hostElement.nativeElement.getBoundingClientRect();
		this.hovered.set({
			index: bar.context.index,
			seriesIndex: bar.context.seriesIndex,
			x: event.clientX - host.left,
			y: event.clientY - host.top,
			hostWidth: host.width,
			hostHeight: host.height
		});
	}
	onBarLeave() {
		this.hovered.set(void 0);
	}
	onBarSelect(bar) {
		const { seriesIndex, index, label, seriesLabel, value, meta } = bar.context;
		this.dataSelect.emit({
			seriesIndex,
			index,
			label,
			seriesLabel,
			value,
			meta
		});
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiBarChartComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiBarChartComponent,
		isStandalone: true,
		selector: "tum-ui-bar-chart",
		inputs: {
			labels: {
				classPropertyName: "labels",
				publicName: "labels",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			series: {
				classPropertyName: "series",
				publicName: "series",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			config: {
				classPropertyName: "config",
				publicName: "config",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabelledBy: {
				classPropertyName: "ariaLabelledBy",
				publicName: "ariaLabelledBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			interactive: {
				classPropertyName: "interactive",
				publicName: "interactive",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { dataSelect: "dataSelect" },
		host: { classAttribute: "tum-ui-bar-chart" },
		viewQueries: [{
			propertyName: "canvas",
			first: true,
			predicate: ["canvas"],
			descendants: true,
			isSignal: true
		}],
		ngImport: i0,
		template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        <defs>\n            <clipPath [attr.id]=\"clipId\">\n                <rect [attr.width]=\"plot().width\" [attr.height]=\"plot().height\" />\n            </clipPath>\n        </defs>\n        <g [attr.transform]=\"'translate(' + plot().left + ' ' + plot().top + ')'\">\n            <svg:g tumUiChartAxes [gridLines]=\"gridLines()\" [ticks]=\"ticks()\" [titles]=\"axisTitles()\" />\n\n            <!-- A value beyond the configured axis limits is clipped to the plot rather than drawn over the\n                 heading above it. The data labels stay outside this group so they remain readable. -->\n            <g [attr.clip-path]=\"'url(#' + clipId + ')'\">\n                @for (bar of bars(); track bar.key) {\n                    <rect\n                        class=\"tum-ui-bar-chart-bar\"\n                        [class.tum-ui-bar-chart-bar-interactive]=\"interactive()\"\n                        [attr.x]=\"bar.x\"\n                        [attr.y]=\"bar.y\"\n                        [attr.width]=\"bar.width\"\n                        [attr.height]=\"bar.height\"\n                        [attr.fill]=\"bar.color\"\n                        [attr.role]=\"interactive() ? 'button' : undefined\"\n                        [attr.tabindex]=\"interactive() ? 0 : undefined\"\n                        [attr.aria-label]=\"interactive() ? accessibleName(bar.context) : undefined\"\n                        (mouseenter)=\"onBarEnter(bar, $event)\"\n                        (mousemove)=\"onBarEnter(bar, $event)\"\n                        (mouseleave)=\"onBarLeave()\"\n                        (click)=\"onBarSelect(bar)\"\n                        (keydown.enter)=\"onBarSelect(bar)\"\n                        (keydown.space)=\"onBarSelect(bar); $event.preventDefault()\"\n                    />\n                }\n            </g>\n\n            @for (bar of bars(); track bar.key) {\n                @if (bar.dataLabel; as dataLabel) {\n                    <text class=\"tum-ui-bar-chart-data-label\" [attr.x]=\"dataLabel.x\" [attr.y]=\"dataLabel.y\" [attr.text-anchor]=\"dataLabel.anchor\">\n                        {{ dataLabel.text }}\n                    </text>\n                }\n            }\n        </g>\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n",
		styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-bar-chart-bar-interactive{cursor:pointer}.tum-ui-bar-chart-bar:hover{filter:brightness(1.08)}.tum-ui-bar-chart-data-label{fill:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-xs);font-family:inherit}\n"],
		dependencies: [
			{
				kind: "component",
				type: TumUiChartAxesComponent,
				selector: "g[tumUiChartAxes]",
				inputs: [
					"gridLines",
					"ticks",
					"titles"
				]
			},
			{
				kind: "component",
				type: TumUiChartLegendComponent,
				selector: "tum-ui-chart-legend",
				inputs: ["items", "position"],
				outputs: ["toggleEntry"]
			},
			{
				kind: "component",
				type: TumUiChartTooltipComponent,
				selector: "tum-ui-chart-tooltip",
				inputs: [
					"title",
					"lines",
					"x",
					"y",
					"below"
				]
			},
			{
				kind: "component",
				type: TumUiChartDataTableComponent,
				selector: "tum-ui-chart-data-table",
				inputs: ["caption", "rows"]
			}
		],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiBarChartComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-bar-chart",
			imports: [
				TumUiChartAxesComponent,
				TumUiChartLegendComponent,
				TumUiChartTooltipComponent,
				TumUiChartDataTableComponent
			],
			changeDetection: ChangeDetectionStrategy.OnPush,
			host: { class: "tum-ui-bar-chart" },
			template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        <defs>\n            <clipPath [attr.id]=\"clipId\">\n                <rect [attr.width]=\"plot().width\" [attr.height]=\"plot().height\" />\n            </clipPath>\n        </defs>\n        <g [attr.transform]=\"'translate(' + plot().left + ' ' + plot().top + ')'\">\n            <svg:g tumUiChartAxes [gridLines]=\"gridLines()\" [ticks]=\"ticks()\" [titles]=\"axisTitles()\" />\n\n            <!-- A value beyond the configured axis limits is clipped to the plot rather than drawn over the\n                 heading above it. The data labels stay outside this group so they remain readable. -->\n            <g [attr.clip-path]=\"'url(#' + clipId + ')'\">\n                @for (bar of bars(); track bar.key) {\n                    <rect\n                        class=\"tum-ui-bar-chart-bar\"\n                        [class.tum-ui-bar-chart-bar-interactive]=\"interactive()\"\n                        [attr.x]=\"bar.x\"\n                        [attr.y]=\"bar.y\"\n                        [attr.width]=\"bar.width\"\n                        [attr.height]=\"bar.height\"\n                        [attr.fill]=\"bar.color\"\n                        [attr.role]=\"interactive() ? 'button' : undefined\"\n                        [attr.tabindex]=\"interactive() ? 0 : undefined\"\n                        [attr.aria-label]=\"interactive() ? accessibleName(bar.context) : undefined\"\n                        (mouseenter)=\"onBarEnter(bar, $event)\"\n                        (mousemove)=\"onBarEnter(bar, $event)\"\n                        (mouseleave)=\"onBarLeave()\"\n                        (click)=\"onBarSelect(bar)\"\n                        (keydown.enter)=\"onBarSelect(bar)\"\n                        (keydown.space)=\"onBarSelect(bar); $event.preventDefault()\"\n                    />\n                }\n            </g>\n\n            @for (bar of bars(); track bar.key) {\n                @if (bar.dataLabel; as dataLabel) {\n                    <text class=\"tum-ui-bar-chart-data-label\" [attr.x]=\"dataLabel.x\" [attr.y]=\"dataLabel.y\" [attr.text-anchor]=\"dataLabel.anchor\">\n                        {{ dataLabel.text }}\n                    </text>\n                }\n            }\n        </g>\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n",
			styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-bar-chart-bar-interactive{cursor:pointer}.tum-ui-bar-chart-bar:hover{filter:brightness(1.08)}.tum-ui-bar-chart-data-label{fill:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-xs);font-family:inherit}\n"]
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		canvas: [{
			type: i0.ViewChild,
			args: ["canvas", { isSignal: true }]
		}],
		labels: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "labels",
				required: true
			}]
		}],
		series: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "series",
				required: true
			}]
		}],
		config: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "config",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		ariaLabelledBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabelledBy",
				required: false
			}]
		}],
		interactive: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "interactive",
				required: false
			}]
		}],
		dataSelect: [{
			type: i0.Output,
			args: ["dataSelect"]
		}]
	}
});
const TAU = Math.PI * 2;
const FULL_CIRCLE_EPSILON = 1e-6;
function pointOnCircle(centerX, centerY, radius, angle) {
	return [centerX + radius * Math.sin(angle), centerY - radius * Math.cos(angle)];
}
function arcPath(centerX, centerY, innerRadius, outerRadius, startAngle, endAngle) {
	const sweep = endAngle - startAngle;
	if (sweep <= 0) return "";
	if (sweep >= TAU - FULL_CIRCLE_EPSILON) {
		const half = startAngle + Math.PI;
		return arcPath(centerX, centerY, innerRadius, outerRadius, startAngle, half) + arcPath(centerX, centerY, innerRadius, outerRadius, half, startAngle + TAU);
	}
	const largeArc = sweep > Math.PI ? 1 : 0;
	const [outerStartX, outerStartY] = pointOnCircle(centerX, centerY, outerRadius, startAngle);
	const [outerEndX, outerEndY] = pointOnCircle(centerX, centerY, outerRadius, endAngle);
	if (innerRadius <= 0) return `M${centerX},${centerY}L${outerStartX},${outerStartY}A${outerRadius},${outerRadius} 0 ${largeArc} 1 ${outerEndX},${outerEndY}Z`;
	const [innerEndX, innerEndY] = pointOnCircle(centerX, centerY, innerRadius, endAngle);
	const [innerStartX, innerStartY] = pointOnCircle(centerX, centerY, innerRadius, startAngle);
	return `M${outerStartX},${outerStartY}A${outerRadius},${outerRadius} 0 ${largeArc} 1 ${outerEndX},${outerEndY}L${innerEndX},${innerEndY}A${innerRadius},${innerRadius} 0 ${largeArc} 0 ${innerStartX},${innerStartY}Z`;
}
function sliceAngles(values) {
	const contribution = (value) => Number.isFinite(value) ? Math.max(value, 0) : 0;
	const total = values.reduce((sum, value) => sum + contribution(value), 0);
	if (total <= 0) return values.map(() => ({
		startAngle: 0,
		endAngle: 0
	}));
	let angle = 0;
	return values.map((value) => {
		const startAngle = angle;
		angle += contribution(value) / total * TAU;
		return {
			startAngle,
			endAngle: angle
		};
	});
}
const DEFAULT_ARC_WIDTH = .25;
const DEFAULT_PADDING = 20;
var TumUiDoughnutChartComponent = class TumUiDoughnutChartComponent {
	hostElement = inject(ElementRef);
	canvas = viewChild.required("canvas", ...ngDevMode ? [{ debugName: "canvas" }] : /* istanbul ignore next */ []);
	labels = input.required(...ngDevMode ? [{ debugName: "labels" }] : /* istanbul ignore next */ []);
	series = input.required(...ngDevMode ? [{ debugName: "series" }] : /* istanbul ignore next */ []);
	config = input({}, ...ngDevMode ? [{ debugName: "config" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	ariaLabelledBy = input(...ngDevMode ? [void 0, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []);
	interactive = input(false, {
		...ngDevMode ? { debugName: "interactive" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	dataSelect = output();
	size = signal({
		width: 0,
		height: 0
	}, ...ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []);
	hovered = signal(void 0, ...ngDevMode ? [{ debugName: "hovered" }] : /* istanbul ignore next */ []);
	resizeObserver;
	constructor() {
		afterNextRender(() => {
			const element = this.canvas().nativeElement;
			const rect = element.getBoundingClientRect();
			this.size.set({
				width: rect.width,
				height: rect.height
			});
			if (typeof ResizeObserver === "undefined") return;
			this.resizeObserver = new ResizeObserver((entries) => {
				const box = entries[0]?.contentRect;
				if (box) this.size.set({
					width: box.width,
					height: box.height
				});
			});
			this.resizeObserver.observe(element);
		});
	}
	ngOnDestroy() {
		this.resizeObserver?.disconnect();
	}
	hiddenSlices = signal(/* @__PURE__ */ new Set(), ...ngDevMode ? [{ debugName: "hiddenSlices" }] : /* istanbul ignore next */ []);
	onLegendToggle(key) {
		this.hiddenSlices.update((hidden) => {
			const next = new Set(hidden);
			if (!next.delete(key)) next.add(key);
			return next;
		});
	}
	primarySeries = computed(() => this.series()[0], ...ngDevMode ? [{ debugName: "primarySeries" }] : /* istanbul ignore next */ []);
	slices = computed(() => {
		const { width, height } = this.size();
		const series = this.primarySeries();
		if (!series || width <= 0 || height <= 0) return [];
		const padding = this.config().padding ?? DEFAULT_PADDING;
		const outerRadius = Math.max(Math.min(width, height) / 2 - padding, 0);
		const arcWidth = this.config().arcWidth ?? DEFAULT_ARC_WIDTH;
		const innerRadius = outerRadius * (1 - Math.min(Math.max(arcWidth, 0), 1));
		const centerX = width / 2;
		const centerY = height / 2;
		const values = series.data.map((value, index) => this.hiddenSlices().has(`${index}`) ? 0 : value ?? 0);
		return sliceAngles(values).map((slice, index) => ({
			key: `${index}`,
			path: arcPath(centerX, centerY, innerRadius, outerRadius, slice.startAngle, slice.endAngle),
			color: series.colors?.[index % series.colors.length] ?? series.color ?? "var(--tumaet-ui-primary-color)",
			context: {
				seriesIndex: 0,
				index,
				label: this.labels()[index] ?? "",
				seriesLabel: series.label,
				value: values[index],
				meta: series.meta?.[index]
			}
		}));
	}, ...ngDevMode ? [{ debugName: "slices" }] : /* istanbul ignore next */ []);
	legendPosition = computed(() => legendPositionOf(this.config().legend), ...ngDevMode ? [{ debugName: "legendPosition" }] : /* istanbul ignore next */ []);
	legendItems = computed(() => this.slices().map((slice) => ({
		key: slice.key,
		label: slice.context.label,
		color: slice.color,
		hidden: this.hiddenSlices().has(slice.key)
	})), ...ngDevMode ? [{ debugName: "legendItems" }] : /* istanbul ignore next */ []);
	tooltip = computed(() => {
		const hovered = this.hovered();
		const config = this.config().tooltip;
		if (!hovered || config === false) return;
		const slice = this.slices()[hovered.index];
		if (!slice) return;
		const context = slice.context;
		const title = config?.title ? config.title([context]) : context.label;
		const raw = config?.label ? config.label(context) : `${context.value}`;
		const after = config?.afterBody?.([context]);
		return {
			title,
			lines: [...Array.isArray(raw) ? raw : [raw], ...after ? Array.isArray(after) ? after : [after] : []].filter((line) => line !== ""),
			...placeTooltip(hovered)
		};
	}, ...ngDevMode ? [{ debugName: "tooltip" }] : /* istanbul ignore next */ []);
	accessibleRows = computed(() => this.labels().map((label, index) => ({
		label,
		values: [{
			seriesLabel: this.primarySeries()?.label,
			value: this.primarySeries()?.data[index]
		}]
	})), ...ngDevMode ? [{ debugName: "accessibleRows" }] : /* istanbul ignore next */ []);
	onSliceEnter(slice, event) {
		const host = this.hostElement.nativeElement.getBoundingClientRect();
		this.hovered.set({
			index: slice.context.index,
			x: event.clientX - host.left,
			y: event.clientY - host.top,
			hostWidth: host.width,
			hostHeight: host.height
		});
	}
	onSliceLeave() {
		this.hovered.set(void 0);
	}
	onSliceSelect(slice) {
		const { seriesIndex, index, label, seriesLabel, value, meta } = slice.context;
		this.dataSelect.emit({
			seriesIndex,
			index,
			label,
			seriesLabel,
			value,
			meta
		});
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiDoughnutChartComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiDoughnutChartComponent,
		isStandalone: true,
		selector: "tum-ui-doughnut-chart",
		inputs: {
			labels: {
				classPropertyName: "labels",
				publicName: "labels",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			series: {
				classPropertyName: "series",
				publicName: "series",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			config: {
				classPropertyName: "config",
				publicName: "config",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabelledBy: {
				classPropertyName: "ariaLabelledBy",
				publicName: "ariaLabelledBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			interactive: {
				classPropertyName: "interactive",
				publicName: "interactive",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { dataSelect: "dataSelect" },
		host: { classAttribute: "tum-ui-doughnut-chart" },
		viewQueries: [{
			propertyName: "canvas",
			first: true,
			predicate: ["canvas"],
			descendants: true,
			isSignal: true
		}],
		ngImport: i0,
		template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        @for (slice of slices(); track slice.key) {\n            <path\n                class=\"tum-ui-doughnut-chart-slice\"\n                [class.tum-ui-doughnut-chart-slice-interactive]=\"interactive()\"\n                [attr.d]=\"slice.path\"\n                [attr.fill]=\"slice.color\"\n                (mouseenter)=\"onSliceEnter(slice, $event)\"\n                (mousemove)=\"onSliceEnter(slice, $event)\"\n                (mouseleave)=\"onSliceLeave()\"\n                (click)=\"onSliceSelect(slice)\"\n            />\n        }\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n",
		styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-doughnut-chart-slice-interactive{cursor:pointer}.tum-ui-doughnut-chart-slice:hover{filter:brightness(1.08)}\n"],
		dependencies: [
			{
				kind: "component",
				type: TumUiChartLegendComponent,
				selector: "tum-ui-chart-legend",
				inputs: ["items", "position"],
				outputs: ["toggleEntry"]
			},
			{
				kind: "component",
				type: TumUiChartTooltipComponent,
				selector: "tum-ui-chart-tooltip",
				inputs: [
					"title",
					"lines",
					"x",
					"y",
					"below"
				]
			},
			{
				kind: "component",
				type: TumUiChartDataTableComponent,
				selector: "tum-ui-chart-data-table",
				inputs: ["caption", "rows"]
			}
		],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiDoughnutChartComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-doughnut-chart",
			imports: [
				TumUiChartLegendComponent,
				TumUiChartTooltipComponent,
				TumUiChartDataTableComponent
			],
			changeDetection: ChangeDetectionStrategy.OnPush,
			host: { class: "tum-ui-doughnut-chart" },
			template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        @for (slice of slices(); track slice.key) {\n            <path\n                class=\"tum-ui-doughnut-chart-slice\"\n                [class.tum-ui-doughnut-chart-slice-interactive]=\"interactive()\"\n                [attr.d]=\"slice.path\"\n                [attr.fill]=\"slice.color\"\n                (mouseenter)=\"onSliceEnter(slice, $event)\"\n                (mousemove)=\"onSliceEnter(slice, $event)\"\n                (mouseleave)=\"onSliceLeave()\"\n                (click)=\"onSliceSelect(slice)\"\n            />\n        }\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n",
			styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-doughnut-chart-slice-interactive{cursor:pointer}.tum-ui-doughnut-chart-slice:hover{filter:brightness(1.08)}\n"]
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		canvas: [{
			type: i0.ViewChild,
			args: ["canvas", { isSignal: true }]
		}],
		labels: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "labels",
				required: true
			}]
		}],
		series: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "series",
				required: true
			}]
		}],
		config: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "config",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		ariaLabelledBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabelledBy",
				required: false
			}]
		}],
		interactive: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "interactive",
				required: false
			}]
		}],
		dataSelect: [{
			type: i0.Output,
			args: ["dataSelect"]
		}]
	}
});
function moveAndLine(points) {
	return points.map((point, index) => `${index === 0 ? "M" : "L"}${point.x},${point.y}`).join("");
}
function tangent(previous, point, next) {
	const leftRun = point.x - previous.x;
	const rightRun = next.x - point.x;
	const leftSlope = (point.y - previous.y) / (leftRun || 1);
	const rightSlope = (next.y - point.y) / (rightRun || 1);
	if (leftSlope * rightSlope <= 0) return 0;
	return 1 / ((2 * rightRun + leftRun) / (3 * (leftRun + rightRun)) / leftSlope + (rightRun + 2 * leftRun) / (3 * (leftRun + rightRun)) / rightSlope);
}
function monotoneCubicPath(points) {
	if (points.length < 3) return moveAndLine(points);
	const tangents = points.map((point, index) => {
		if (index === 0) return (points[1].y - point.y) / (points[1].x - point.x || 1);
		if (index === points.length - 1) return (point.y - points[index - 1].y) / (point.x - points[index - 1].x || 1);
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
function linearPath(points) {
	return moveAndLine(points);
}
function segmentsOf(points, spanGaps) {
	const defined = points.filter((point) => point !== void 0);
	if (spanGaps) return defined.length ? [defined] : [];
	const segments = [];
	let current = [];
	for (const point of points) if (point) current.push(point);
	else if (current.length) {
		segments.push(current);
		current = [];
	}
	if (current.length) segments.push(current);
	return segments;
}
const POINT_RADIUS = 3;
const SELECT_RADIUS = 40;
const LINE_CATEGORY_PADDING = 0;
let nextLineChartId = 0;
var TumUiLineChartComponent = class TumUiLineChartComponent {
	hostElement = inject(ElementRef);
	canvas = viewChild.required("canvas", ...ngDevMode ? [{ debugName: "canvas" }] : /* istanbul ignore next */ []);
	labels = input.required(...ngDevMode ? [{ debugName: "labels" }] : /* istanbul ignore next */ []);
	series = input.required(...ngDevMode ? [{ debugName: "series" }] : /* istanbul ignore next */ []);
	config = input({}, ...ngDevMode ? [{ debugName: "config" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	ariaLabelledBy = input(...ngDevMode ? [void 0, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []);
	interactive = input(false, {
		...ngDevMode ? { debugName: "interactive" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	dataSelect = output();
	pointRadius = POINT_RADIUS;
	accessibleName(context) {
		return datumAccessibleName(context, this.series().length > 1);
	}
	size = signal({
		width: 0,
		height: 0
	}, ...ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []);
	hovered = signal(void 0, ...ngDevMode ? [{ debugName: "hovered" }] : /* istanbul ignore next */ []);
	resizeObserver;
	constructor() {
		afterNextRender(() => {
			const element = this.canvas().nativeElement;
			const rect = element.getBoundingClientRect();
			this.size.set({
				width: rect.width,
				height: rect.height
			});
			if (typeof ResizeObserver === "undefined") return;
			this.resizeObserver = new ResizeObserver((entries) => {
				const box = entries[0]?.contentRect;
				if (box) this.size.set({
					width: box.width,
					height: box.height
				});
			});
			this.resizeObserver.observe(element);
		});
	}
	ngOnDestroy() {
		this.resizeObserver?.disconnect();
	}
	hiddenSeries = signal(/* @__PURE__ */ new Set(), ...ngDevMode ? [{ debugName: "hiddenSeries" }] : /* istanbul ignore next */ []);
	onLegendToggle(key) {
		this.hiddenSeries.update((hidden) => {
			const next = new Set(hidden);
			if (!next.delete(key)) next.add(key);
			return next;
		});
	}
	visibleSeries = computed(() => this.series().map((entry, index) => ({
		entry,
		index
	})).filter(({ index }) => !this.hiddenSeries().has(`${index}`)), ...ngDevMode ? [{ debugName: "visibleSeries" }] : /* istanbul ignore next */ []);
	minTickStep = computed(() => allIntegers(this.visibleSeries().flatMap(({ entry }) => [...entry.data])) ? 1 : 0, ...ngDevMode ? [{ debugName: "minTickStep" }] : /* istanbul ignore next */ []);
	valueDomain = computed(() => {
		const axis = this.config().yAxis;
		const values = finiteValues(this.visibleSeries().flatMap(({ entry }) => [...entry.data]));
		const dataMax = values.length ? Math.max(...values) : 0;
		const [niceMin, niceMax] = niceDomain(values.length ? Math.min(...values) : 0, dataMax, 5, this.minTickStep());
		return [axis?.min ?? niceMin, axis?.max ?? niceMax];
	}, ...ngDevMode ? [{ debugName: "valueDomain" }] : /* istanbul ignore next */ []);
	valueTickLabels = computed(() => {
		if ((this.config().yAxis?.display ?? true) === false) return [];
		const [min, max] = this.valueDomain();
		const format = this.config().yAxis?.tickFormatter;
		return linearScale([min, max], [0, 1]).ticks(5, this.minTickStep()).map((value) => ({
			value,
			text: format ? format(value) : `${value}`
		}));
	}, ...ngDevMode ? [{ debugName: "valueTickLabels" }] : /* istanbul ignore next */ []);
	frame = computed(() => cartesianFrame({
		size: this.size(),
		labels: this.config().xAxis?.display ?? true ? this.labels() : [],
		valueTicks: this.valueTickLabels(),
		horizontal: false,
		valueAxis: this.config().yAxis,
		categoryAxis: this.config().xAxis,
		xAxisTitle: this.config().xAxis?.label,
		yAxisTitle: this.config().yAxis?.label
	}), ...ngDevMode ? [{ debugName: "frame" }] : /* istanbul ignore next */ []);
	plot = computed(() => this.frame().plot, ...ngDevMode ? [{ debugName: "plot" }] : /* istanbul ignore next */ []);
	clipId = `tum-ui-line-chart-clip-${nextLineChartId++}`;
	valueScale = computed(() => {
		const [min, max] = this.valueDomain();
		return linearScale([min, max], [this.plot().height, 0]);
	}, ...ngDevMode ? [{ debugName: "valueScale" }] : /* istanbul ignore next */ []);
	categoryScale = computed(() => bandScale(this.labels().length, this.plot().width, LINE_CATEGORY_PADDING), ...ngDevMode ? [{ debugName: "categoryScale" }] : /* istanbul ignore next */ []);
	lines = computed(() => {
		const plot = this.plot();
		if (plot.width <= 0 || plot.height <= 0) return [];
		const categories = this.categoryScale();
		const valueScale = this.valueScale();
		const monotone = this.config().monotone ?? false;
		const spanGaps = this.config().spanGaps ?? false;
		const showPoints = this.config().points ?? true;
		return this.visibleSeries().map(({ entry, index: seriesIndex }) => {
			const positioned = this.labels().map((_, index) => {
				const value = entry.data[index];
				if (value === void 0 || value === null || !Number.isFinite(value)) return;
				return {
					x: categories.center(index),
					y: valueScale(value)
				};
			});
			const build = monotone ? monotoneCubicPath : linearPath;
			const paths = segmentsOf(positioned, spanGaps).map(build);
			const color = entry.color ?? "var(--tumaet-ui-primary-color)";
			return {
				key: `${seriesIndex}`,
				paths,
				color,
				dashed: entry.referenceLine ?? false,
				points: showPoints && !entry.referenceLine ? positioned.flatMap((point, index) => point ? [{
					key: `${seriesIndex}-${index}`,
					x: point.x,
					y: point.y,
					context: {
						seriesIndex,
						index,
						label: this.labels()[index],
						seriesLabel: entry.label,
						value: entry.data[index],
						meta: entry.meta?.[index]
					}
				}] : []) : []
			};
		});
	}, ...ngDevMode ? [{ debugName: "lines" }] : /* istanbul ignore next */ []);
	gridLines = computed(() => this.config().yAxis?.display ?? true ? gridLineViews(this.plot(), this.valueScale(), this.valueTickLabels(), false) : [], ...ngDevMode ? [{ debugName: "gridLines" }] : /* istanbul ignore next */ []);
	ticks = computed(() => {
		const plot = this.plot();
		const value = this.config().yAxis?.display ?? true ? valueTickViews(plot, this.valueScale(), this.valueTickLabels(), false) : [];
		const category = this.config().xAxis?.display ?? true ? categoryTickViews(plot, this.categoryScale(), this.labels(), false, this.frame().rotateCategoryLabels, this.config().xAxis?.tickFormatter, this.frame().categoryLabelBudget) : [];
		return [...value, ...category];
	}, ...ngDevMode ? [{ debugName: "ticks" }] : /* istanbul ignore next */ []);
	axisTitles = computed(() => {
		const titles = axisTitleViews(this.plot(), this.frame().margin, this.config().xAxis?.label, this.config().yAxis?.label);
		return [titles.x, titles.y].filter((title) => title !== void 0);
	}, ...ngDevMode ? [{ debugName: "axisTitles" }] : /* istanbul ignore next */ []);
	legendPosition = computed(() => legendPositionOf(this.config().legend), ...ngDevMode ? [{ debugName: "legendPosition" }] : /* istanbul ignore next */ []);
	legendItems = computed(() => this.series().map((entry, index) => ({
		entry,
		index
	})).filter(({ entry }) => entry.label && !entry.referenceLine).map(({ entry, index }) => ({
		key: `${index}`,
		label: entry.label,
		color: entry.color ?? "var(--tumaet-ui-primary-color)",
		hidden: this.hiddenSeries().has(`${index}`)
	})), ...ngDevMode ? [{ debugName: "legendItems" }] : /* istanbul ignore next */ []);
	guideX = computed(() => {
		const hovered = this.hovered();
		return hovered === void 0 ? void 0 : this.categoryScale().center(hovered.index);
	}, ...ngDevMode ? [{ debugName: "guideX" }] : /* istanbul ignore next */ []);
	tooltip = computed(() => {
		const hovered = this.hovered();
		const config = this.config().tooltip;
		if (!hovered || config === false) return;
		const contexts = [];
		this.visibleSeries().forEach(({ entry, index: seriesIndex }) => {
			const value = entry.data[hovered.index];
			if (entry.referenceLine || value === void 0 || value === null || !Number.isFinite(value)) return;
			contexts.push({
				seriesIndex,
				index: hovered.index,
				label: this.labels()[hovered.index],
				seriesLabel: entry.label,
				value,
				meta: entry.meta?.[hovered.index]
			});
		});
		if (!contexts.length) return;
		const title = config?.title ? config.title(contexts) : this.labels()[hovered.index];
		const lines = contexts.flatMap((context) => {
			const raw = config?.label ? config.label(context) : `${context.seriesLabel ? `${context.seriesLabel}: ` : ""}${context.value}`;
			return Array.isArray(raw) ? raw : [raw];
		});
		const after = config?.afterBody?.(contexts);
		return {
			title,
			lines: [...lines, ...after ? Array.isArray(after) ? after : [after] : []].filter((line) => line !== ""),
			...placeTooltip(hovered)
		};
	}, ...ngDevMode ? [{ debugName: "tooltip" }] : /* istanbul ignore next */ []);
	accessibleRows = computed(() => this.labels().map((label, index) => ({
		label,
		values: this.series().map((entry) => ({
			seriesLabel: entry.label,
			value: entry.data[index]
		}))
	})), ...ngDevMode ? [{ debugName: "accessibleRows" }] : /* istanbul ignore next */ []);
	onPlotMove(event) {
		const labels = this.labels();
		if (!labels.length) return;
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
		this.hovered.set({
			index: nearest,
			x: event.clientX - host.left,
			y: event.clientY - host.top,
			hostWidth: host.width,
			hostHeight: host.height
		});
	}
	onPlotLeave() {
		this.hovered.set(void 0);
	}
	onPointSelect(context) {
		const { seriesIndex, index, label, seriesLabel, value, meta } = context;
		this.dataSelect.emit({
			seriesIndex,
			index,
			label,
			seriesLabel,
			value,
			meta
		});
	}
	onPlotClick(event) {
		if (!this.hovered()) return;
		const canvas = this.canvas().nativeElement.getBoundingClientRect();
		const plot = this.plot();
		const clickX = event.clientX - canvas.left - plot.left;
		const clickY = event.clientY - canvas.top - plot.top;
		let nearest;
		let shortest = Number.POSITIVE_INFINITY;
		for (const line of this.lines()) for (const point of line.points) {
			const distance = Math.hypot(point.x - clickX, point.y - clickY);
			if (distance < shortest) {
				shortest = distance;
				nearest = point.context;
			}
		}
		if (!nearest || shortest > SELECT_RADIUS) return;
		const { seriesIndex, index, label, seriesLabel, value, meta } = nearest;
		this.dataSelect.emit({
			seriesIndex,
			index,
			label,
			seriesLabel,
			value,
			meta
		});
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiLineChartComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiLineChartComponent,
		isStandalone: true,
		selector: "tum-ui-line-chart",
		inputs: {
			labels: {
				classPropertyName: "labels",
				publicName: "labels",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			series: {
				classPropertyName: "series",
				publicName: "series",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			config: {
				classPropertyName: "config",
				publicName: "config",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabelledBy: {
				classPropertyName: "ariaLabelledBy",
				publicName: "ariaLabelledBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			interactive: {
				classPropertyName: "interactive",
				publicName: "interactive",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { dataSelect: "dataSelect" },
		host: { classAttribute: "tum-ui-line-chart" },
		viewQueries: [{
			propertyName: "canvas",
			first: true,
			predicate: ["canvas"],
			descendants: true,
			isSignal: true
		}],
		ngImport: i0,
		template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        <defs>\n            <clipPath [attr.id]=\"clipId\">\n                <rect [attr.width]=\"plot().width\" [attr.height]=\"plot().height\" />\n            </clipPath>\n        </defs>\n        <g [attr.transform]=\"'translate(' + plot().left + ' ' + plot().top + ')'\">\n            <svg:g tumUiChartAxes [gridLines]=\"gridLines()\" [ticks]=\"ticks()\" [titles]=\"axisTitles()\" />\n\n            @if (guideX(); as x) {\n                <line class=\"tum-ui-line-chart-guide\" [attr.x1]=\"x\" [attr.y1]=\"0\" [attr.x2]=\"x\" [attr.y2]=\"plot().height\" />\n            }\n\n            <!-- A point outside the configured axis limits is clipped to the plot rather than drawn past the\n                 container. The hit area below stays unclipped so hovering still works at the edges. -->\n            <g [attr.clip-path]=\"'url(#' + clipId + ')'\">\n                @for (line of lines(); track line.key) {\n                    @for (path of line.paths; track $index) {\n                        <path class=\"tum-ui-line-chart-line\" [class.tum-ui-line-chart-line-dashed]=\"line.dashed\" [attr.d]=\"path\" [attr.stroke]=\"line.color\" fill=\"none\" />\n                    }\n                }\n\n                @for (line of lines(); track line.key) {\n                    @for (point of line.points; track point.key) {\n                        <circle\n                            class=\"tum-ui-line-chart-point\"\n                            [class.tum-ui-line-chart-point-interactive]=\"interactive()\"\n                            [attr.cx]=\"point.x\"\n                            [attr.cy]=\"point.y\"\n                            [attr.r]=\"pointRadius\"\n                            [attr.fill]=\"line.color\"\n                            [attr.role]=\"interactive() ? 'button' : undefined\"\n                            [attr.tabindex]=\"interactive() ? 0 : undefined\"\n                            [attr.aria-label]=\"interactive() ? accessibleName(point.context) : undefined\"\n                            (keydown.enter)=\"onPointSelect(point.context)\"\n                            (keydown.space)=\"onPointSelect(point.context); $event.preventDefault()\"\n                        />\n                    }\n                }\n            </g>\n\n            <!--\n              Sits above the series so that hovering anywhere in the plot reports the nearest\n              category, and clicking selects the nearest point rather than only the marker itself.\n            -->\n            <rect\n                class=\"tum-ui-line-chart-hit-area\"\n                [class.tum-ui-line-chart-hit-area-interactive]=\"interactive()\"\n                [attr.width]=\"plot().width\"\n                [attr.height]=\"plot().height\"\n                (mousemove)=\"onPlotMove($event)\"\n                (mouseleave)=\"onPlotLeave()\"\n                (click)=\"onPlotClick($event)\"\n            />\n        </g>\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n",
		styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-line-chart-line{stroke-width:2;fill:none}.tum-ui-line-chart-line-dashed{stroke-dasharray:5 5;stroke-width:1.5}.tum-ui-line-chart-point{pointer-events:none}.tum-ui-line-chart-point-interactive{pointer-events:all;cursor:pointer}.tum-ui-line-chart-guide{stroke:var(--tumaet-ui-border-color);stroke-width:1}.tum-ui-line-chart-hit-area{fill:transparent}.tum-ui-line-chart-hit-area-interactive{cursor:pointer}\n"],
		dependencies: [
			{
				kind: "component",
				type: TumUiChartAxesComponent,
				selector: "g[tumUiChartAxes]",
				inputs: [
					"gridLines",
					"ticks",
					"titles"
				]
			},
			{
				kind: "component",
				type: TumUiChartLegendComponent,
				selector: "tum-ui-chart-legend",
				inputs: ["items", "position"],
				outputs: ["toggleEntry"]
			},
			{
				kind: "component",
				type: TumUiChartTooltipComponent,
				selector: "tum-ui-chart-tooltip",
				inputs: [
					"title",
					"lines",
					"x",
					"y",
					"below"
				]
			},
			{
				kind: "component",
				type: TumUiChartDataTableComponent,
				selector: "tum-ui-chart-data-table",
				inputs: ["caption", "rows"]
			}
		],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiLineChartComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-line-chart",
			imports: [
				TumUiChartAxesComponent,
				TumUiChartLegendComponent,
				TumUiChartTooltipComponent,
				TumUiChartDataTableComponent
			],
			changeDetection: ChangeDetectionStrategy.OnPush,
			host: { class: "tum-ui-line-chart" },
			template: "<div class=\"tum-ui-chart-layout\" [attr.data-legend]=\"legendPosition() ?? 'none'\">\n    <svg\n        #canvas\n        class=\"tum-ui-chart-canvas\"\n        role=\"img\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-labelledby]=\"ariaLabelledBy()\"\n        [attr.aria-hidden]=\"ariaLabel() || ariaLabelledBy() ? undefined : true\"\n    >\n        <defs>\n            <clipPath [attr.id]=\"clipId\">\n                <rect [attr.width]=\"plot().width\" [attr.height]=\"plot().height\" />\n            </clipPath>\n        </defs>\n        <g [attr.transform]=\"'translate(' + plot().left + ' ' + plot().top + ')'\">\n            <svg:g tumUiChartAxes [gridLines]=\"gridLines()\" [ticks]=\"ticks()\" [titles]=\"axisTitles()\" />\n\n            @if (guideX(); as x) {\n                <line class=\"tum-ui-line-chart-guide\" [attr.x1]=\"x\" [attr.y1]=\"0\" [attr.x2]=\"x\" [attr.y2]=\"plot().height\" />\n            }\n\n            <!-- A point outside the configured axis limits is clipped to the plot rather than drawn past the\n                 container. The hit area below stays unclipped so hovering still works at the edges. -->\n            <g [attr.clip-path]=\"'url(#' + clipId + ')'\">\n                @for (line of lines(); track line.key) {\n                    @for (path of line.paths; track $index) {\n                        <path class=\"tum-ui-line-chart-line\" [class.tum-ui-line-chart-line-dashed]=\"line.dashed\" [attr.d]=\"path\" [attr.stroke]=\"line.color\" fill=\"none\" />\n                    }\n                }\n\n                @for (line of lines(); track line.key) {\n                    @for (point of line.points; track point.key) {\n                        <circle\n                            class=\"tum-ui-line-chart-point\"\n                            [class.tum-ui-line-chart-point-interactive]=\"interactive()\"\n                            [attr.cx]=\"point.x\"\n                            [attr.cy]=\"point.y\"\n                            [attr.r]=\"pointRadius\"\n                            [attr.fill]=\"line.color\"\n                            [attr.role]=\"interactive() ? 'button' : undefined\"\n                            [attr.tabindex]=\"interactive() ? 0 : undefined\"\n                            [attr.aria-label]=\"interactive() ? accessibleName(point.context) : undefined\"\n                            (keydown.enter)=\"onPointSelect(point.context)\"\n                            (keydown.space)=\"onPointSelect(point.context); $event.preventDefault()\"\n                        />\n                    }\n                }\n            </g>\n\n            <!--\n              Sits above the series so that hovering anywhere in the plot reports the nearest\n              category, and clicking selects the nearest point rather than only the marker itself.\n            -->\n            <rect\n                class=\"tum-ui-line-chart-hit-area\"\n                [class.tum-ui-line-chart-hit-area-interactive]=\"interactive()\"\n                [attr.width]=\"plot().width\"\n                [attr.height]=\"plot().height\"\n                (mousemove)=\"onPlotMove($event)\"\n                (mouseleave)=\"onPlotLeave()\"\n                (click)=\"onPlotClick($event)\"\n            />\n        </g>\n    </svg>\n\n    @if (legendPosition() && legendItems().length) {\n        <tum-ui-chart-legend [items]=\"legendItems()\" [position]=\"legendPosition()!\" (toggleEntry)=\"onLegendToggle($event)\" />\n    }\n</div>\n\n@if (tooltip(); as tooltipView) {\n    <tum-ui-chart-tooltip [title]=\"tooltipView.title\" [lines]=\"tooltipView.lines\" [x]=\"tooltipView.x\" [y]=\"tooltipView.y\" [below]=\"tooltipView.below\" />\n}\n\n<tum-ui-chart-data-table [caption]=\"ariaLabel()\" [rows]=\"accessibleRows()\" />\n",
			styles: [":host{display:block;position:relative}@layer base{:host{width:100%;height:100%}}.tum-ui-chart-layout{display:flex;width:100%;height:100%;gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-chart-layout[data-legend=right]{flex-direction:row}.tum-ui-chart-layout[data-legend=left]{flex-direction:row-reverse}.tum-ui-chart-layout[data-legend=bottom],.tum-ui-chart-layout[data-legend=none]{flex-direction:column}.tum-ui-chart-layout[data-legend=top]{flex-direction:column-reverse}.tum-ui-chart-canvas{flex:1 1 auto;min-width:0;min-height:0;overflow:visible}.tum-ui-line-chart-line{stroke-width:2;fill:none}.tum-ui-line-chart-line-dashed{stroke-dasharray:5 5;stroke-width:1.5}.tum-ui-line-chart-point{pointer-events:none}.tum-ui-line-chart-point-interactive{pointer-events:all;cursor:pointer}.tum-ui-line-chart-guide{stroke:var(--tumaet-ui-border-color);stroke-width:1}.tum-ui-line-chart-hit-area{fill:transparent}.tum-ui-line-chart-hit-area-interactive{cursor:pointer}\n"]
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		canvas: [{
			type: i0.ViewChild,
			args: ["canvas", { isSignal: true }]
		}],
		labels: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "labels",
				required: true
			}]
		}],
		series: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "series",
				required: true
			}]
		}],
		config: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "config",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		ariaLabelledBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabelledBy",
				required: false
			}]
		}],
		interactive: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "interactive",
				required: false
			}]
		}],
		dataSelect: [{
			type: i0.Output,
			args: ["dataSelect"]
		}]
	}
});
var TumUiCheckboxComponent = class TumUiCheckboxComponent {
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	inputId = input(...ngDevMode ? [void 0, { debugName: "inputId" }] : /* istanbul ignore next */ []);
	name = input(...ngDevMode ? [void 0, { debugName: "name" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	checked = model(false, ...ngDevMode ? [{ debugName: "checked" }] : /* istanbul ignore next */ []);
	indeterminate = input(false, {
		...ngDevMode ? { debugName: "indeterminate" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	changed = output();
	faCheck = faCheck;
	faMinus = faMinus;
	showDash = computed(() => this.indeterminate(), ...ngDevMode ? [{ debugName: "showDash" }] : /* istanbul ignore next */ []);
	showTick = computed(() => this.checked() && !this.indeterminate(), ...ngDevMode ? [{ debugName: "showTick" }] : /* istanbul ignore next */ []);
	cvaDisabled = signal(false, ...ngDevMode ? [{ debugName: "cvaDisabled" }] : /* istanbul ignore next */ []);
	isDisabled = computed(() => this.disabled() || this.cvaDisabled(), ...ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []);
	boxClasses = computed(() => {
		if (this.isDisabled()) return "tum:bg-disabled-background tum:border-control-border";
		if (this.checked() || this.indeterminate()) return "tum:bg-primary tum:border-primary";
		return "tum:bg-control-background tum:border-control-border";
	}, ...ngDevMode ? [{ debugName: "boxClasses" }] : /* istanbul ignore next */ []);
	iconClasses = computed(() => this.isDisabled() ? "tum:text-disabled" : "tum:text-primary-contrast", ...ngDevMode ? [{ debugName: "iconClasses" }] : /* istanbul ignore next */ []);
	onModelChange = () => {};
	onModelTouched = () => {};
	onInputChange(event) {
		const newChecked = event.target.checked;
		this.checked.set(newChecked);
		this.onModelChange(newChecked);
		this.onModelTouched();
		this.changed.emit({
			originalEvent: event,
			checked: newChecked
		});
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
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiCheckboxComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiCheckboxComponent,
		isStandalone: true,
		selector: "tum-ui-checkbox",
		inputs: {
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			inputId: {
				classPropertyName: "inputId",
				publicName: "inputId",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			name: {
				classPropertyName: "name",
				publicName: "name",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			checked: {
				classPropertyName: "checked",
				publicName: "checked",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			indeterminate: {
				classPropertyName: "indeterminate",
				publicName: "indeterminate",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: {
			checked: "checkedChange",
			changed: "changed"
		},
		host: { classAttribute: "tum-ui-checkbox" },
		providers: [{
			provide: NG_VALUE_ACCESSOR,
			useExisting: forwardRef(() => TumUiCheckboxComponent),
			multi: true
		}],
		ngImport: i0,
		template: "<input\n    type=\"checkbox\"\n    class=\"tum-ui-checkbox-input\"\n    [id]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [checked]=\"checked()\"\n    [indeterminate]=\"indeterminate()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    (change)=\"onInputChange($event)\"\n    (blur)=\"onBlur()\"\n/>\n<div class=\"tum-ui-checkbox-box\" [class]=\"boxClasses()\" aria-hidden=\"true\">\n    @if (showDash()) {\n        <fa-icon [icon]=\"faMinus\" class=\"tum-ui-checkbox-icon\" [class]=\"iconClasses()\" />\n    } @else if (showTick()) {\n        <fa-icon [icon]=\"faCheck\" class=\"tum-ui-checkbox-icon\" [class]=\"iconClasses()\" />\n    }\n</div>\n",
		styles: [":host{position:relative;display:inline-flex;width:calc(var(--tumaet-ui-spacing, .25rem) * 5);height:calc(var(--tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-checkbox-input{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none}.tum-ui-checkbox-input:disabled{cursor:default}.tum-ui-checkbox-box{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:var(--tumaet-ui-radius-sm);box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent);transition:background .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-checkbox-box:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-checkbox-icon{font-size:var(--tumaet-ui-font-size-sm);line-height:1}:host:has(.tum-ui-checkbox-input:hover:not(:disabled)) .tum-ui-checkbox-box:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:has(.tum-ui-checkbox-input:focus-visible) .tum-ui-checkbox-box{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-checkbox-input{appearance:auto;opacity:1}.tum-ui-checkbox-box{display:none}}\n"],
		dependencies: [{
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiCheckboxComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-checkbox",
			imports: [FaIconComponent],
			host: { class: "tum-ui-checkbox" },
			providers: [{
				provide: NG_VALUE_ACCESSOR,
				useExisting: forwardRef(() => TumUiCheckboxComponent),
				multi: true
			}],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<input\n    type=\"checkbox\"\n    class=\"tum-ui-checkbox-input\"\n    [id]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [checked]=\"checked()\"\n    [indeterminate]=\"indeterminate()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    (change)=\"onInputChange($event)\"\n    (blur)=\"onBlur()\"\n/>\n<div class=\"tum-ui-checkbox-box\" [class]=\"boxClasses()\" aria-hidden=\"true\">\n    @if (showDash()) {\n        <fa-icon [icon]=\"faMinus\" class=\"tum-ui-checkbox-icon\" [class]=\"iconClasses()\" />\n    } @else if (showTick()) {\n        <fa-icon [icon]=\"faCheck\" class=\"tum-ui-checkbox-icon\" [class]=\"iconClasses()\" />\n    }\n</div>\n",
			styles: [":host{position:relative;display:inline-flex;width:calc(var(--tumaet-ui-spacing, .25rem) * 5);height:calc(var(--tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-checkbox-input{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none}.tum-ui-checkbox-input:disabled{cursor:default}.tum-ui-checkbox-box{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:var(--tumaet-ui-radius-sm);box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent);transition:background .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-checkbox-box:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-checkbox-icon{font-size:var(--tumaet-ui-font-size-sm);line-height:1}:host:has(.tum-ui-checkbox-input:hover:not(:disabled)) .tum-ui-checkbox-box:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:has(.tum-ui-checkbox-input:focus-visible) .tum-ui-checkbox-box{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-checkbox-input{appearance:auto;opacity:1}.tum-ui-checkbox-box{display:none}}\n"]
		}]
	}],
	propDecorators: {
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		inputId: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "inputId",
				required: false
			}]
		}],
		name: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "name",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		checked: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "checked",
				required: false
			}]
		}, {
			type: i0.Output,
			args: ["checkedChange"]
		}],
		indeterminate: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "indeterminate",
				required: false
			}]
		}],
		changed: [{
			type: i0.Output,
			args: ["changed"]
		}]
	}
});
const DIALOG_SIZE_CLASSES = {
	small: "tum:w-[min(32rem,90dvw)]",
	medium: "tum:w-[min(48rem,90dvw)]",
	large: "tum:w-[min(72rem,90dvw)]",
	full: "tum:h-[90dvh] tum:w-[90dvw]"
};
let nextDialogId = 0;
let openDialogCount = 0;
let previousRootOverflow = "";
let previousRootPaddingRight = "";
function lockPageScroll() {
	if (openDialogCount++ > 0) return;
	const root = document.documentElement;
	previousRootOverflow = root.style.overflow;
	previousRootPaddingRight = root.style.paddingRight;
	const scrollbarWidth = window.innerWidth - root.clientWidth;
	root.style.overflow = "hidden";
	if (scrollbarWidth > 0) root.style.paddingRight = `${scrollbarWidth}px`;
}
function unlockPageScroll() {
	if (openDialogCount === 0 || --openDialogCount > 0) return;
	const root = document.documentElement;
	root.style.overflow = previousRootOverflow;
	root.style.paddingRight = previousRootPaddingRight;
}
var TumUiDialogComponent = class TumUiDialogComponent {
	dialog = inject(Dialog);
	overlay = inject(Overlay);
	viewContainerRef = inject(ViewContainerRef);
	visible = model(false, ...ngDevMode ? [{ debugName: "visible" }] : /* istanbul ignore next */ []);
	header = input(...ngDevMode ? [void 0, { debugName: "header" }] : /* istanbul ignore next */ []);
	showHeader = input(true, {
		...ngDevMode ? { debugName: "showHeader" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	closable = input(true, {
		...ngDevMode ? { debugName: "closable" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	closeOnEscape = input(true, {
		...ngDevMode ? { debugName: "closeOnEscape" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	dismissableMask = input(false, {
		...ngDevMode ? { debugName: "dismissableMask" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	size = input(...ngDevMode ? [void 0, { debugName: "size" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	closeButtonAriaLabel = input(...ngDevMode ? [void 0, { debugName: "closeButtonAriaLabel" }] : /* istanbul ignore next */ []);
	role = input("dialog", ...ngDevMode ? [{ debugName: "role" }] : /* istanbul ignore next */ []);
	ariaDescribedBy = input(...ngDevMode ? [void 0, { debugName: "ariaDescribedBy" }] : /* istanbul ignore next */ []);
	shown = output();
	hidden = output();
	panel = viewChild.required("panel", {
		...ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {},
		read: TemplateRef
	});
	headerTemplate = contentChild("header", {
		...ngDevMode ? { debugName: "headerTemplate" } : /* istanbul ignore next */ {},
		read: TemplateRef
	});
	footerTemplate = contentChild("footer", {
		...ngDevMode ? { debugName: "footerTemplate" } : /* istanbul ignore next */ {},
		read: TemplateRef
	});
	titleId = `tum-ui-dialog-title-${nextDialogId++}`;
	faXmark = faXmark;
	labelledBy = computed(() => this.showHeader() && (this.header()?.trim() || this.headerTemplate()) ? this.titleId : void 0, ...ngDevMode ? [{ debugName: "labelledBy" }] : /* istanbul ignore next */ []);
	sizeClasses = computed(() => {
		const size = this.size();
		return size ? DIALOG_SIZE_CLASSES[size] : "";
	}, ...ngDevMode ? [{ debugName: "sizeClasses" }] : /* istanbul ignore next */ []);
	dialogRef;
	visibilitySync = effect(() => {
		if (this.visible()) this.open();
		else this.dialogRef?.close();
	}, ...ngDevMode ? [{ debugName: "visibilitySync" }] : /* istanbul ignore next */ []);
	close() {
		this.visible.set(false);
	}
	open() {
		if (this.dialogRef) return;
		const ariaLabel = this.ariaLabel()?.trim();
		const labelledBy = this.labelledBy();
		if (!ariaLabel && !labelledBy) throw new Error("tum-ui-dialog requires a visible header, a header template, or ariaLabel");
		const ref = this.dialog.open(this.panel(), {
			panelClass: "tum-ui-overlay",
			viewContainerRef: this.viewContainerRef,
			scrollStrategy: this.overlay.scrollStrategies.noop(),
			hasBackdrop: true,
			backdropClass: "cdk-overlay-dark-backdrop",
			disableClose: true,
			ariaModal: true,
			role: this.role(),
			ariaLabel: ariaLabel ?? null,
			ariaLabelledBy: labelledBy ?? null,
			ariaDescribedBy: this.ariaDescribedBy() ?? null,
			restoreFocus: true
		});
		this.dialogRef = ref;
		lockPageScroll();
		ref.backdropClick.subscribe(() => {
			if (this.dismissableMask()) this.close();
		});
		ref.keydownEvents.subscribe((event) => {
			if (event.key === "Escape" && this.closeOnEscape()) this.close();
		});
		ref.closed.subscribe(() => {
			unlockPageScroll();
			if (this.dialogRef !== ref) return;
			this.dialogRef = void 0;
			if (this.visible()) this.visible.set(false);
			this.hidden.emit();
		});
		this.shown.emit();
	}
	ngOnDestroy() {
		this.visibilitySync.destroy();
		const ref = this.dialogRef;
		this.dialogRef = void 0;
		ref?.close();
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiDialogComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiDialogComponent,
		isStandalone: true,
		selector: "tum-ui-dialog",
		inputs: {
			visible: {
				classPropertyName: "visible",
				publicName: "visible",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			header: {
				classPropertyName: "header",
				publicName: "header",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			showHeader: {
				classPropertyName: "showHeader",
				publicName: "showHeader",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			closable: {
				classPropertyName: "closable",
				publicName: "closable",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			closeOnEscape: {
				classPropertyName: "closeOnEscape",
				publicName: "closeOnEscape",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			dismissableMask: {
				classPropertyName: "dismissableMask",
				publicName: "dismissableMask",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			size: {
				classPropertyName: "size",
				publicName: "size",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			closeButtonAriaLabel: {
				classPropertyName: "closeButtonAriaLabel",
				publicName: "closeButtonAriaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			role: {
				classPropertyName: "role",
				publicName: "role",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaDescribedBy: {
				classPropertyName: "ariaDescribedBy",
				publicName: "ariaDescribedBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: {
			visible: "visibleChange",
			shown: "shown",
			hidden: "hidden"
		},
		queries: [{
			propertyName: "headerTemplate",
			first: true,
			predicate: ["header"],
			descendants: true,
			read: TemplateRef,
			isSignal: true
		}, {
			propertyName: "footerTemplate",
			first: true,
			predicate: ["footer"],
			descendants: true,
			read: TemplateRef,
			isSignal: true
		}],
		viewQueries: [{
			propertyName: "panel",
			first: true,
			predicate: ["panel"],
			descendants: true,
			read: TemplateRef,
			isSignal: true
		}],
		ngImport: i0,
		template: "<ng-template #panel>\n    <div\n        class=\"tum-ui-dialog tum:flex tum:max-h-[90dvh] tum:max-w-[90dvw] tum:flex-col tum:overflow-hidden tum:rounded-xl tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-xl\"\n        [class]=\"sizeClasses()\"\n    >\n        @if (showHeader()) {\n            <div class=\"tum-ui-dialog-header tum:flex tum:shrink-0 tum:items-center tum:justify-between tum:gap-2 tum:p-4\">\n                <div [id]=\"titleId\" class=\"tum-ui-dialog-title tum:text-xl tum:font-semibold\">\n                    @if (headerTemplate(); as headerTpl) {\n                        <ng-container [ngTemplateOutlet]=\"headerTpl\" />\n                    } @else {\n                        {{ header() }}\n                    }\n                </div>\n                @if (closable()) {\n                    <button\n                        type=\"button\"\n                        class=\"tum-ui-dialog-close tum:inline-flex tum:h-8 tum:w-8 tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:rounded-full tum:border-0 tum:bg-transparent tum:text-muted tum:transition-colors tum:hover:bg-hover-background\"\n                        [attr.aria-label]=\"closeButtonAriaLabel() ?? ('tumUi.dialog.close' | tumUiTranslate)\"\n                        (click)=\"close()\"\n                    >\n                        <fa-icon [icon]=\"faXmark\" />\n                    </button>\n                }\n            </div>\n        }\n\n        <div class=\"tum-ui-dialog-content tum:grow tum:overflow-y-auto tum:px-4 tum:pb-4\">\n            <ng-content />\n        </div>\n\n        @if (footerTemplate(); as footerTpl) {\n            <div class=\"tum-ui-dialog-footer tum:flex tum:shrink-0 tum:justify-end tum:gap-2 tum:px-4 tum:pb-4\">\n                <ng-container [ngTemplateOutlet]=\"footerTpl\" />\n            </div>\n        }\n    </div>\n</ng-template>\n",
		dependencies: [
			{
				kind: "directive",
				type: NgTemplateOutlet,
				selector: "[ngTemplateOutlet]",
				inputs: [
					"ngTemplateOutletContext",
					"ngTemplateOutlet",
					"ngTemplateOutletInjector"
				]
			},
			{
				kind: "component",
				type: FaIconComponent,
				selector: "fa-icon",
				inputs: [
					"icon",
					"title",
					"animation",
					"mask",
					"flip",
					"size",
					"pull",
					"border",
					"inverse",
					"symbol",
					"rotate",
					"fixedWidth",
					"transform",
					"a11yRole"
				],
				outputs: [
					"iconChange",
					"titleChange",
					"animationChange",
					"maskChange",
					"flipChange",
					"sizeChange",
					"pullChange",
					"borderChange",
					"inverseChange",
					"symbolChange",
					"rotateChange",
					"fixedWidthChange",
					"transformChange",
					"a11yRoleChange"
				]
			},
			{
				kind: "pipe",
				type: TumUiTranslatePipe,
				name: "tumUiTranslate"
			}
		],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiDialogComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-dialog",
			imports: [
				NgTemplateOutlet,
				FaIconComponent,
				TumUiTranslatePipe
			],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<ng-template #panel>\n    <div\n        class=\"tum-ui-dialog tum:flex tum:max-h-[90dvh] tum:max-w-[90dvw] tum:flex-col tum:overflow-hidden tum:rounded-xl tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-xl\"\n        [class]=\"sizeClasses()\"\n    >\n        @if (showHeader()) {\n            <div class=\"tum-ui-dialog-header tum:flex tum:shrink-0 tum:items-center tum:justify-between tum:gap-2 tum:p-4\">\n                <div [id]=\"titleId\" class=\"tum-ui-dialog-title tum:text-xl tum:font-semibold\">\n                    @if (headerTemplate(); as headerTpl) {\n                        <ng-container [ngTemplateOutlet]=\"headerTpl\" />\n                    } @else {\n                        {{ header() }}\n                    }\n                </div>\n                @if (closable()) {\n                    <button\n                        type=\"button\"\n                        class=\"tum-ui-dialog-close tum:inline-flex tum:h-8 tum:w-8 tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:rounded-full tum:border-0 tum:bg-transparent tum:text-muted tum:transition-colors tum:hover:bg-hover-background\"\n                        [attr.aria-label]=\"closeButtonAriaLabel() ?? ('tumUi.dialog.close' | tumUiTranslate)\"\n                        (click)=\"close()\"\n                    >\n                        <fa-icon [icon]=\"faXmark\" />\n                    </button>\n                }\n            </div>\n        }\n\n        <div class=\"tum-ui-dialog-content tum:grow tum:overflow-y-auto tum:px-4 tum:pb-4\">\n            <ng-content />\n        </div>\n\n        @if (footerTemplate(); as footerTpl) {\n            <div class=\"tum-ui-dialog-footer tum:flex tum:shrink-0 tum:justify-end tum:gap-2 tum:px-4 tum:pb-4\">\n                <ng-container [ngTemplateOutlet]=\"footerTpl\" />\n            </div>\n        }\n    </div>\n</ng-template>\n"
		}]
	}],
	propDecorators: {
		visible: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "visible",
				required: false
			}]
		}, {
			type: i0.Output,
			args: ["visibleChange"]
		}],
		header: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "header",
				required: false
			}]
		}],
		showHeader: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "showHeader",
				required: false
			}]
		}],
		closable: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "closable",
				required: false
			}]
		}],
		closeOnEscape: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "closeOnEscape",
				required: false
			}]
		}],
		dismissableMask: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "dismissableMask",
				required: false
			}]
		}],
		size: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "size",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		closeButtonAriaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "closeButtonAriaLabel",
				required: false
			}]
		}],
		role: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "role",
				required: false
			}]
		}],
		ariaDescribedBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaDescribedBy",
				required: false
			}]
		}],
		shown: [{
			type: i0.Output,
			args: ["shown"]
		}],
		hidden: [{
			type: i0.Output,
			args: ["hidden"]
		}],
		panel: [{
			type: i0.ViewChild,
			args: ["panel", {
				read: TemplateRef,
				isSignal: true
			}]
		}],
		headerTemplate: [{
			type: i0.ContentChild,
			args: ["header", {
				read: TemplateRef,
				isSignal: true
			}]
		}],
		footerTemplate: [{
			type: i0.ContentChild,
			args: ["footer", {
				read: TemplateRef,
				isSignal: true
			}]
		}]
	}
});
var TumUiConfirmationService = class TumUiConfirmationService {
	requests = signal(/* @__PURE__ */ new Map(), ...ngDevMode ? [{ debugName: "requests" }] : /* istanbul ignore next */ []);
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
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiConfirmationService,
		deps: [],
		target: i0.ɵɵFactoryTarget.Injectable
	});
	static ɵprov = i0.ɵɵngDeclareInjectable({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiConfirmationService
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiConfirmationService,
	decorators: [{ type: Injectable }]
});
let nextConfirmDialogId = 0;
var TumUiConfirmDialogComponent = class TumUiConfirmDialogComponent {
	confirmationService = inject(TumUiConfirmationService);
	key = input(...ngDevMode ? [void 0, { debugName: "key" }] : /* istanbul ignore next */ []);
	messageId = `tum-ui-confirm-dialog-message-${nextConfirmDialogId++}`;
	request = computed(() => this.confirmationService.request(this.key()), ...ngDevMode ? [{ debugName: "request" }] : /* istanbul ignore next */ []);
	visible = computed(() => this.request() !== void 0, ...ngDevMode ? [{ debugName: "visible" }] : /* istanbul ignore next */ []);
	accept() {
		const request = this.request();
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
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiConfirmDialogComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiConfirmDialogComponent,
		isStandalone: true,
		selector: "tum-ui-confirm-dialog",
		inputs: { key: {
			classPropertyName: "key",
			publicName: "key",
			isSignal: true,
			isRequired: false,
			transformFunction: null
		} },
		ngImport: i0,
		template: "@if (request(); as req) {\n    <tum-ui-dialog [visible]=\"visible()\" [closable]=\"true\" [header]=\"req.header\" [role]=\"'alertdialog'\" [ariaDescribedBy]=\"messageId\" (hidden)=\"onDialogHide()\">\n        <div class=\"tum:flex tum:items-center tum:gap-4\">\n            @if (req.icon) {\n                <fa-icon [icon]=\"req.icon\" class=\"tum:size-8 tum:shrink-0 tum:text-2xl tum:text-muted\" />\n            }\n            <span [id]=\"messageId\" class=\"tum-ui-confirm-dialog-message\">{{ req.message }}</span>\n        </div>\n        <ng-template #footer>\n            <tum-ui-button [severity]=\"req.rejectSeverity ?? 'secondary'\" (clicked)=\"reject()\">{{ req.rejectLabel }}</tum-ui-button>\n            <tum-ui-button [severity]=\"req.acceptSeverity ?? 'primary'\" (clicked)=\"accept()\">{{ req.acceptLabel }}</tum-ui-button>\n        </ng-template>\n    </tum-ui-dialog>\n}\n",
		dependencies: [
			{
				kind: "component",
				type: TumUiDialogComponent,
				selector: "tum-ui-dialog",
				inputs: [
					"visible",
					"header",
					"showHeader",
					"closable",
					"closeOnEscape",
					"dismissableMask",
					"size",
					"ariaLabel",
					"closeButtonAriaLabel",
					"role",
					"ariaDescribedBy"
				],
				outputs: [
					"visibleChange",
					"shown",
					"hidden"
				]
			},
			{
				kind: "component",
				type: TumUiButtonComponent,
				selector: "tum-ui-button",
				inputs: [
					"severity",
					"size",
					"variant",
					"disabled",
					"rounded",
					"loading",
					"icon",
					"type",
					"ariaLabel",
					"ariaExpanded",
					"ariaPressed",
					"ariaControls",
					"ariaDescribedBy"
				],
				outputs: ["clicked"]
			},
			{
				kind: "component",
				type: FaIconComponent,
				selector: "fa-icon",
				inputs: [
					"icon",
					"title",
					"animation",
					"mask",
					"flip",
					"size",
					"pull",
					"border",
					"inverse",
					"symbol",
					"rotate",
					"fixedWidth",
					"transform",
					"a11yRole"
				],
				outputs: [
					"iconChange",
					"titleChange",
					"animationChange",
					"maskChange",
					"flipChange",
					"sizeChange",
					"pullChange",
					"borderChange",
					"inverseChange",
					"symbolChange",
					"rotateChange",
					"fixedWidthChange",
					"transformChange",
					"a11yRoleChange"
				]
			}
		],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiConfirmDialogComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-confirm-dialog",
			imports: [
				TumUiDialogComponent,
				TumUiButtonComponent,
				FaIconComponent
			],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "@if (request(); as req) {\n    <tum-ui-dialog [visible]=\"visible()\" [closable]=\"true\" [header]=\"req.header\" [role]=\"'alertdialog'\" [ariaDescribedBy]=\"messageId\" (hidden)=\"onDialogHide()\">\n        <div class=\"tum:flex tum:items-center tum:gap-4\">\n            @if (req.icon) {\n                <fa-icon [icon]=\"req.icon\" class=\"tum:size-8 tum:shrink-0 tum:text-2xl tum:text-muted\" />\n            }\n            <span [id]=\"messageId\" class=\"tum-ui-confirm-dialog-message\">{{ req.message }}</span>\n        </div>\n        <ng-template #footer>\n            <tum-ui-button [severity]=\"req.rejectSeverity ?? 'secondary'\" (clicked)=\"reject()\">{{ req.rejectLabel }}</tum-ui-button>\n            <tum-ui-button [severity]=\"req.acceptSeverity ?? 'primary'\" (clicked)=\"accept()\">{{ req.acceptLabel }}</tum-ui-button>\n        </ng-template>\n    </tum-ui-dialog>\n}\n"
		}]
	}],
	propDecorators: { key: [{
		type: i0.Input,
		args: [{
			isSignal: true,
			alias: "key",
			required: false
		}]
	}] }
});
const ARROW_BASE = "tum:absolute tum:h-2 tum:w-2 tum:rotate-45 tum:bg-tooltip-background";
const ARROW_SIDE = {
	top: "tum:top-full tum:-translate-x-1/2 tum:-translate-y-1/2",
	bottom: "tum:bottom-full tum:-translate-x-1/2 tum:translate-y-1/2",
	left: "tum:left-full tum:-translate-y-1/2 tum:-translate-x-1/2",
	right: "tum:right-full tum:-translate-y-1/2 tum:translate-x-1/2"
};
const ARROW_CENTRE = {
	top: "tum:left-1/2",
	bottom: "tum:left-1/2",
	left: "tum:top-1/2",
	right: "tum:top-1/2"
};
const BUBBLE_BASE = "tum-ui-tooltip-bubble tum:relative tum:inline-block tum:rounded-md tum:bg-tooltip-background tum:px-3 tum:py-2 tum:text-sm tum:text-tooltip tum:shadow-md";
const BUBBLE_WIDTH = {
	text: "tum:max-w-50",
	list: "tum:max-w-100"
};
var TumUiTooltipContentComponent = class TumUiTooltipContentComponent {
	text = input("", ...ngDevMode ? [{ debugName: "text" }] : /* istanbul ignore next */ []);
	items = input([], ...ngDevMode ? [{ debugName: "items" }] : /* istanbul ignore next */ []);
	id = input("", ...ngDevMode ? [{ debugName: "id" }] : /* istanbul ignore next */ []);
	placement = input("top", ...ngDevMode ? [{ debugName: "placement" }] : /* istanbul ignore next */ []);
	arrowOffsetPx = input(void 0, ...ngDevMode ? [{ debugName: "arrowOffsetPx" }] : /* istanbul ignore next */ []);
	isHorizontal = computed(() => this.placement() === "top" || this.placement() === "bottom", ...ngDevMode ? [{ debugName: "isHorizontal" }] : /* istanbul ignore next */ []);
	arrowClasses = computed(() => {
		const anchor = this.arrowOffsetPx() === void 0 ? ` ${ARROW_CENTRE[this.placement()]}` : "";
		return `${ARROW_BASE} ${ARROW_SIDE[this.placement()]}${anchor}`;
	}, ...ngDevMode ? [{ debugName: "arrowClasses" }] : /* istanbul ignore next */ []);
	arrowStyle = computed(() => {
		const offset = this.arrowOffsetPx();
		if (offset === void 0) return null;
		return { [this.isHorizontal() ? "left" : "top"]: `${offset}px` };
	}, ...ngDevMode ? [{ debugName: "arrowStyle" }] : /* istanbul ignore next */ []);
	hostClasses = computed(() => `${BUBBLE_BASE} ${this.items().length ? BUBBLE_WIDTH.list : BUBBLE_WIDTH.text}`, ...ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTooltipContentComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiTooltipContentComponent,
		isStandalone: true,
		selector: "tum-ui-tooltip-content",
		inputs: {
			text: {
				classPropertyName: "text",
				publicName: "text",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			items: {
				classPropertyName: "items",
				publicName: "items",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			id: {
				classPropertyName: "id",
				publicName: "id",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			placement: {
				classPropertyName: "placement",
				publicName: "placement",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			arrowOffsetPx: {
				classPropertyName: "arrowOffsetPx",
				publicName: "arrowOffsetPx",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: {
			attributes: { "role": "tooltip" },
			properties: {
				"attr.id": "id()",
				"class": "hostClasses()"
			},
			classAttribute: "tum-ui-tooltip-bubble"
		},
		ngImport: i0,
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
		isInline: true,
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTooltipContentComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-tooltip-content",
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
				role: "tooltip",
				"[attr.id]": "id()",
				class: "tum-ui-tooltip-bubble",
				"[class]": "hostClasses()"
			},
			changeDetection: ChangeDetectionStrategy.OnPush
		}]
	}],
	propDecorators: {
		text: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "text",
				required: false
			}]
		}],
		items: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "items",
				required: false
			}]
		}],
		id: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "id",
				required: false
			}]
		}],
		placement: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "placement",
				required: false
			}]
		}],
		arrowOffsetPx: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "arrowOffsetPx",
				required: false
			}]
		}]
	}
});
let nextTooltipId = 0;
const ARROW_EDGE_INSET_PX = 12;
var TumUiTooltipDirective = class TumUiTooltipDirective {
	overlayService = inject(TumUiOverlayService);
	elementRef = inject(ElementRef);
	content = input.required({
		...ngDevMode ? { debugName: "content" } : /* istanbul ignore next */ {},
		alias: "tumUiTooltip"
	});
	placement = input("top", {
		...ngDevMode ? { debugName: "placement" } : /* istanbul ignore next */ {},
		alias: "tumUiTooltipPlacement"
	});
	describesHost = input(true, {
		...ngDevMode ? { debugName: "describesHost" } : /* istanbul ignore next */ {},
		alias: "tumUiTooltipDescribesHost",
		transform: booleanAttribute
	});
	showDelayMs = input(150, {
		...ngDevMode ? { debugName: "showDelayMs" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	hideDelayMs = input(100, {
		...ngDevMode ? { debugName: "hideDelayMs" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	text = computed(() => Array.isArray(this.content()) ? "" : this.content(), ...ngDevMode ? [{ debugName: "text" }] : /* istanbul ignore next */ []);
	items = computed(() => Array.isArray(this.content()) ? this.content() : [], ...ngDevMode ? [{ debugName: "items" }] : /* istanbul ignore next */ []);
	isEmpty = computed(() => !this.text() && this.items().length === 0, ...ngDevMode ? [{ debugName: "isEmpty" }] : /* istanbul ignore next */ []);
	overlayRef;
	contentRef;
	positionSub;
	showTimer;
	hideTimer;
	tooltipId = `tum-ui-tooltip-${nextTooltipId++}`;
	interactionSub;
	appliedPlacement = "top";
	triggerHovered = false;
	tooltipHovered = false;
	focused = false;
	pointerFocus = false;
	constructor() {
		effect(() => {
			const [text, items, isEmpty] = [
				this.text(),
				this.items(),
				this.isEmpty()
			];
			if (isEmpty) this.hideNow();
			else {
				this.contentRef?.setInput("text", text);
				this.contentRef?.setInput("items", items);
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
	onPointerDown() {
		this.pointerFocus = true;
	}
	onFocusStart() {
		if (this.pointerFocus) return;
		this.focused = true;
		this.scheduleShow();
	}
	onFocusEnd() {
		this.focused = false;
		this.pointerFocus = false;
		this.scheduleHideIfInactive();
	}
	scheduleHideIfInactive() {
		if (this.triggerHovered || this.tooltipHovered || this.focused) return;
		this.scheduleHide();
	}
	scheduleShow() {
		clearTimeout(this.hideTimer);
		clearTimeout(this.showTimer);
		if (this.overlayRef?.hasAttached() || this.isEmpty()) return;
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
		this.positionSub = void 0;
		this.interactionSub?.unsubscribe();
		this.interactionSub = void 0;
		this.tooltipHovered = false;
		this.overlayRef?.dispose();
		this.overlayRef = void 0;
		this.contentRef = void 0;
	}
	show() {
		if (this.overlayRef?.hasAttached()) return;
		this.overlayRef = this.overlayService.createConnectedOverlay(this.elementRef, this.placement());
		const strategy = this.overlayRef.getConfig().positionStrategy;
		this.appliedPlacement = this.placement();
		this.positionSub = strategy.positionChanges.subscribe((change) => {
			this.appliedPlacement = this.overlayService.placementFromPosition(change.connectionPair);
			this.contentRef?.setInput("placement", this.appliedPlacement);
			this.updateArrowOffset();
		});
		this.contentRef = this.overlayRef.attach(new ComponentPortal(TumUiTooltipContentComponent));
		this.contentRef.setInput("text", this.text());
		this.contentRef.setInput("items", this.items());
		this.contentRef.setInput("id", this.tooltipId);
		this.contentRef.setInput("placement", this.appliedPlacement);
		this.updateArrowOffset();
		const contentElement = this.contentRef.location.nativeElement;
		this.interactionSub = new Subscription();
		this.interactionSub.add(fromEvent(contentElement, "mouseenter").subscribe(() => {
			this.tooltipHovered = true;
			clearTimeout(this.hideTimer);
		}));
		this.interactionSub.add(fromEvent(contentElement, "mouseleave").subscribe(() => {
			this.tooltipHovered = false;
			this.scheduleHideIfInactive();
		}));
		this.interactionSub.add(this.overlayRef.keydownEvents().subscribe((event) => {
			if (event.key === "Escape") this.hideNow();
		}));
		this.addDescribedBy();
	}
	updateArrowOffset() {
		const bubble = this.contentRef?.location.nativeElement;
		if (!bubble) return;
		const hostRect = this.elementRef.nativeElement.getBoundingClientRect();
		const bubbleRect = bubble.getBoundingClientRect();
		const horizontal = this.appliedPlacement === "top" || this.appliedPlacement === "bottom";
		const hostCentre = horizontal ? hostRect.left + hostRect.width / 2 : hostRect.top + hostRect.height / 2;
		const bubbleStart = horizontal ? bubbleRect.left : bubbleRect.top;
		const bubbleLength = horizontal ? bubbleRect.width : bubbleRect.height;
		if (bubbleLength === 0) return;
		const offset = Math.min(Math.max(hostCentre - bubbleStart, ARROW_EDGE_INSET_PX), bubbleLength - ARROW_EDGE_INSET_PX);
		this.contentRef?.setInput("arrowOffsetPx", offset);
	}
	addDescribedBy() {
		if (!this.describesHost()) return;
		const host = this.elementRef.nativeElement;
		const tokens = (host.getAttribute("aria-describedby") ?? "").split(" ").filter(Boolean);
		if (!tokens.includes(this.tooltipId)) tokens.push(this.tooltipId);
		host.setAttribute("aria-describedby", tokens.join(" "));
	}
	removeDescribedBy() {
		const host = this.elementRef.nativeElement;
		const tokens = (host.getAttribute("aria-describedby") ?? "").split(" ").filter((token) => token && token !== this.tooltipId);
		if (tokens.length > 0) host.setAttribute("aria-describedby", tokens.join(" "));
		else host.removeAttribute("aria-describedby");
	}
	ngOnDestroy() {
		this.hideNow();
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTooltipDirective,
		deps: [],
		target: i0.ɵɵFactoryTarget.Directive
	});
	static ɵdir = i0.ɵɵngDeclareDirective({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiTooltipDirective,
		isStandalone: true,
		selector: "[tumUiTooltip]",
		inputs: {
			content: {
				classPropertyName: "content",
				publicName: "tumUiTooltip",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			placement: {
				classPropertyName: "placement",
				publicName: "tumUiTooltipPlacement",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			describesHost: {
				classPropertyName: "describesHost",
				publicName: "tumUiTooltipDescribesHost",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			showDelayMs: {
				classPropertyName: "showDelayMs",
				publicName: "showDelayMs",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			hideDelayMs: {
				classPropertyName: "hideDelayMs",
				publicName: "hideDelayMs",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: { listeners: {
			"mouseenter": "onHoverStart()",
			"mouseleave": "onHoverEnd()",
			"mousedown": "onPointerDown()",
			"focusin": "onFocusStart()",
			"focusout": "onFocusEnd()",
			"keydown.escape": "hideNow()"
		} },
		ngImport: i0
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTooltipDirective,
	decorators: [{
		type: Directive,
		args: [{
			selector: "[tumUiTooltip]",
			host: {
				"(mouseenter)": "onHoverStart()",
				"(mouseleave)": "onHoverEnd()",
				"(mousedown)": "onPointerDown()",
				"(focusin)": "onFocusStart()",
				"(focusout)": "onFocusEnd()",
				"(keydown.escape)": "hideNow()"
			}
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		content: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiTooltip",
				required: true
			}]
		}],
		placement: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiTooltipPlacement",
				required: false
			}]
		}],
		describesHost: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiTooltipDescribesHost",
				required: false
			}]
		}],
		showDelayMs: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "showDelayMs",
				required: false
			}]
		}],
		hideDelayMs: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "hideDelayMs",
				required: false
			}]
		}]
	}
});
dayjs.extend(customParseFormat);
const DISPLAY_FORMAT = "DD.MM.YYYY HH:mm";
const TIME_ONLY_FORMAT = "HH:mm";
const DISPLAY_REGEX = /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/;
const TIME_REGEX = /^([01]\d|2[0-3]):[0-5]\d$/;
function displayFormat(timeOnly) {
	return timeOnly ? TIME_ONLY_FORMAT : DISPLAY_FORMAT;
}
function matchesDisplayFormat(text, timeOnly) {
	return timeOnly ? TIME_REGEX.test(text) : DISPLAY_REGEX.test(text);
}
function parseDisplay(text, timeOnly = false, onDate) {
	const trimmed = text.trim();
	if (!trimmed) return;
	const parsed = dayjs(trimmed, displayFormat(timeOnly), true);
	if (!parsed.isValid()) return;
	if (!timeOnly) return parsed;
	return combineDateAndTime(onDate ?? dayjs(), parsed);
}
function formatDisplay(value, timeOnly = false) {
	return value.format(displayFormat(timeOnly));
}
function buildMonthMatrix(month) {
	const startOfMonth = month.startOf("month");
	const offset = (startOfMonth.day() + 6) % 7;
	let cursor = startOfMonth.subtract(offset, "day").startOf("day");
	const weeks = [];
	for (let week = 0; week < 6; week++) {
		const days = [];
		for (let day = 0; day < 7; day++) {
			days.push(cursor);
			cursor = cursor.add(1, "day");
		}
		weeks.push(days);
	}
	return weeks;
}
function combineDateAndTime(date, time) {
	return date.hour(time.hour()).minute(time.minute()).second(0).millisecond(0);
}
function valuesEqual(a, b) {
	if (!a && !b) return true;
	if (!a || !b) return false;
	return a.isSame(b, "minute");
}
var TumUiCalendarComponent = class TumUiCalendarComponent {
	translator = inject(TUM_UI_TRANSLATOR);
	directionality = inject(Directionality);
	destroyRef = inject(DestroyRef);
	direction = signal(this.directionality.value, ...ngDevMode ? [{ debugName: "direction" }] : /* istanbul ignore next */ []);
	selected = input(void 0, ...ngDevMode ? [{ debugName: "selected" }] : /* istanbul ignore next */ []);
	activeMonth = input.required(...ngDevMode ? [{ debugName: "activeMonth" }] : /* istanbul ignore next */ []);
	focusOnInit = input(false, {
		...ngDevMode ? { debugName: "focusOnInit" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	daySelected = output();
	monthChange = output();
	previousMonthIcon = computed(() => this.direction() === "rtl" ? faChevronRight : faChevronLeft, ...ngDevMode ? [{ debugName: "previousMonthIcon" }] : /* istanbul ignore next */ []);
	nextMonthIcon = computed(() => this.direction() === "rtl" ? faChevronLeft : faChevronRight, ...ngDevMode ? [{ debugName: "nextMonthIcon" }] : /* istanbul ignore next */ []);
	weeks = computed(() => buildMonthMatrix(this.activeMonth()), ...ngDevMode ? [{ debugName: "weeks" }] : /* istanbul ignore next */ []);
	flatDays = computed(() => this.weeks().flat(), ...ngDevMode ? [{ debugName: "flatDays" }] : /* istanbul ignore next */ []);
	weekdayLabels = computed(() => this.weeks()[0].map((day) => this.formatDate(day, { weekday: "short" })), ...ngDevMode ? [{ debugName: "weekdayLabels" }] : /* istanbul ignore next */ []);
	weekdayFullLabels = computed(() => this.weeks()[0].map((day) => this.formatDate(day, { weekday: "long" })), ...ngDevMode ? [{ debugName: "weekdayFullLabels" }] : /* istanbul ignore next */ []);
	monthLabel = computed(() => this.formatDate(this.activeMonth(), {
		month: "long",
		year: "numeric"
	}), ...ngDevMode ? [{ debugName: "monthLabel" }] : /* istanbul ignore next */ []);
	previousMonthLabel = computed(() => this.translate("tumUi.datePicker.previousMonth", { month: this.formatDate(this.activeMonth().subtract(1, "month"), {
		month: "long",
		year: "numeric"
	}) }), ...ngDevMode ? [{ debugName: "previousMonthLabel" }] : /* istanbul ignore next */ []);
	nextMonthLabel = computed(() => this.translate("tumUi.datePicker.nextMonth", { month: this.formatDate(this.activeMonth().add(1, "month"), {
		month: "long",
		year: "numeric"
	}) }), ...ngDevMode ? [{ debugName: "nextMonthLabel" }] : /* istanbul ignore next */ []);
	focusedDate = linkedSignal({
		...ngDevMode ? { debugName: "focusedDate" } : /* istanbul ignore next */ {},
		source: () => ({
			month: this.activeMonth(),
			selected: this.selected()
		}),
		computation: ({ month, selected }, previous) => {
			const previousSelected = previous?.source.selected;
			if (selected && (!previousSelected || !selected.isSame(previousSelected, "day"))) return selected;
			if (previous) return month.date(Math.min(previous.value.date(), month.daysInMonth()));
			const today = dayjs();
			return today.isSame(month, "month") ? today : month.startOf("month");
		}
	});
	focusedIndex = computed(() => {
		const index = this.flatDays().findIndex((day) => day.isSame(this.focusedDate(), "day"));
		return index >= 0 ? index : 0;
	}, ...ngDevMode ? [{ debugName: "focusedIndex" }] : /* istanbul ignore next */ []);
	today = dayjs();
	dayButtons = viewChildren("dayButton", ...ngDevMode ? [{ debugName: "dayButtons" }] : /* istanbul ignore next */ []);
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
		const base = "tum:appearance-none tum:border-0 tum:h-8 tum:w-8 tum:rounded-full tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus";
		let color;
		if (this.isSelected(day)) color = "tum:bg-primary tum:text-primary-contrast";
		else if (this.isOtherMonth(day)) color = "tum:bg-transparent tum:text-muted tum:hover:bg-hover-background";
		else color = "tum:bg-transparent tum:text-text tum:hover:bg-hover-background";
		const today = this.isToday(day) && !this.isSelected(day) ? "tum:ring-1 tum:ring-primary" : "";
		return `${base} ${color} ${today}`.trim();
	}
	isSelected(day) {
		const selected = this.selected();
		return !!selected && selected.isSame(day, "day");
	}
	isToday(day) {
		return day.isSame(this.today, "day");
	}
	isOtherMonth(day) {
		return day.month() !== this.activeMonth().month();
	}
	previousMonth() {
		this.monthChange.emit(this.activeMonth().subtract(1, "month"));
	}
	nextMonth() {
		this.monthChange.emit(this.activeMonth().add(1, "month"));
	}
	selectDay(day) {
		this.daySelected.emit(day);
	}
	dayLabel(day) {
		return this.formatDate(day, { dateStyle: "full" });
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
			const targetDay = this.flatDays()[index].add(target - index, "day");
			this.focusedDate.set(targetDay);
			this.restoreFocusAfterRender = true;
			this.monthChange.emit(targetDay.startOf("month"));
		};
		switch (event.key) {
			case "ArrowRight":
				moveTo(index + 1);
				break;
			case "ArrowLeft":
				moveTo(index - 1);
				break;
			case "ArrowDown":
				moveTo(index + 7);
				break;
			case "ArrowUp":
				moveTo(index - 7);
				break;
			case "Home":
				moveTo(index - index % 7);
				break;
			case "End":
				moveTo(index - index % 7 + 6);
				break;
			case "Enter":
			case " ": {
				event.preventDefault();
				const day = this.flatDays()[index];
				if (this.isOtherMonth(day)) this.restoreFocusAfterRender = true;
				this.selectDay(day);
				break;
			}
			case "PageUp":
				event.preventDefault();
				this.restoreFocusAfterRender = true;
				this.monthChange.emit(this.activeMonth().subtract(event.shiftKey ? 1 : 0, "year").subtract(event.shiftKey ? 0 : 1, "month"));
				break;
			case "PageDown":
				event.preventDefault();
				this.restoreFocusAfterRender = true;
				this.monthChange.emit(this.activeMonth().add(event.shiftKey ? 1 : 0, "year").add(event.shiftKey ? 0 : 1, "month"));
		}
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiCalendarComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiCalendarComponent,
		isStandalone: true,
		selector: "tum-ui-calendar",
		inputs: {
			selected: {
				classPropertyName: "selected",
				publicName: "selected",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			activeMonth: {
				classPropertyName: "activeMonth",
				publicName: "activeMonth",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			focusOnInit: {
				classPropertyName: "focusOnInit",
				publicName: "focusOnInit",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: {
			daySelected: "daySelected",
			monthChange: "monthChange"
		},
		viewQueries: [{
			propertyName: "dayButtons",
			predicate: ["dayButton"],
			descendants: true,
			isSignal: true
		}],
		ngImport: i0,
		template: "<div class=\"tum:w-72 tum:select-none\">\n    <div class=\"tum:mb-2 tum:flex tum:items-center tum:justify-between\">\n        <button\n            type=\"button\"\n            class=\"tum:appearance-none tum:rounded tum:border-0 tum:bg-transparent tum:p-1 tum:text-text tum:hover:bg-hover-background\"\n            (click)=\"previousMonth()\"\n            [attr.aria-label]=\"previousMonthLabel()\"\n        >\n            <fa-icon [icon]=\"previousMonthIcon()\" />\n        </button>\n        <span class=\"tum:font-semibold tum:text-text\" aria-live=\"polite\">{{ monthLabel() }}</span>\n        <button\n            type=\"button\"\n            class=\"tum:appearance-none tum:rounded tum:border-0 tum:bg-transparent tum:p-1 tum:text-text tum:hover:bg-hover-background\"\n            (click)=\"nextMonth()\"\n            [attr.aria-label]=\"nextMonthLabel()\"\n        >\n            <fa-icon [icon]=\"nextMonthIcon()\" />\n        </button>\n    </div>\n    <table role=\"grid\" [attr.aria-label]=\"monthLabel()\" class=\"tum:w-full tum:border-collapse tum:text-center tum:text-sm\">\n        <thead>\n            <tr>\n                @for (label of weekdayLabels(); track $index) {\n                    <th scope=\"col\" [attr.aria-label]=\"weekdayFullLabels()[$index]\" class=\"tum:p-1 tum:font-medium tum:text-muted\">{{ label }}</th>\n                }\n            </tr>\n        </thead>\n        <tbody>\n            @for (week of weeks(); track $index; let w = $index) {\n                <tr>\n                    @for (day of week; track day.valueOf(); let d = $index) {\n                        <td class=\"tum:p-0.5\" role=\"gridcell\" [attr.aria-selected]=\"isSelected(day) ? 'true' : null\">\n                            <button\n                                #dayButton\n                                type=\"button\"\n                                [class]=\"dayButtonClasses(day)\"\n                                [attr.tabindex]=\"focusedIndex() === w * 7 + d ? 0 : -1\"\n                                [attr.aria-label]=\"dayLabel(day)\"\n                                (click)=\"selectDay(day)\"\n                                (keydown)=\"onKeydown($event, w * 7 + d)\"\n                            >\n                                {{ day.date() }}\n                            </button>\n                        </td>\n                    }\n                </tr>\n            }\n        </tbody>\n    </table>\n</div>\n",
		dependencies: [{
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiCalendarComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-calendar",
			imports: [FaIconComponent],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<div class=\"tum:w-72 tum:select-none\">\n    <div class=\"tum:mb-2 tum:flex tum:items-center tum:justify-between\">\n        <button\n            type=\"button\"\n            class=\"tum:appearance-none tum:rounded tum:border-0 tum:bg-transparent tum:p-1 tum:text-text tum:hover:bg-hover-background\"\n            (click)=\"previousMonth()\"\n            [attr.aria-label]=\"previousMonthLabel()\"\n        >\n            <fa-icon [icon]=\"previousMonthIcon()\" />\n        </button>\n        <span class=\"tum:font-semibold tum:text-text\" aria-live=\"polite\">{{ monthLabel() }}</span>\n        <button\n            type=\"button\"\n            class=\"tum:appearance-none tum:rounded tum:border-0 tum:bg-transparent tum:p-1 tum:text-text tum:hover:bg-hover-background\"\n            (click)=\"nextMonth()\"\n            [attr.aria-label]=\"nextMonthLabel()\"\n        >\n            <fa-icon [icon]=\"nextMonthIcon()\" />\n        </button>\n    </div>\n    <table role=\"grid\" [attr.aria-label]=\"monthLabel()\" class=\"tum:w-full tum:border-collapse tum:text-center tum:text-sm\">\n        <thead>\n            <tr>\n                @for (label of weekdayLabels(); track $index) {\n                    <th scope=\"col\" [attr.aria-label]=\"weekdayFullLabels()[$index]\" class=\"tum:p-1 tum:font-medium tum:text-muted\">{{ label }}</th>\n                }\n            </tr>\n        </thead>\n        <tbody>\n            @for (week of weeks(); track $index; let w = $index) {\n                <tr>\n                    @for (day of week; track day.valueOf(); let d = $index) {\n                        <td class=\"tum:p-0.5\" role=\"gridcell\" [attr.aria-selected]=\"isSelected(day) ? 'true' : null\">\n                            <button\n                                #dayButton\n                                type=\"button\"\n                                [class]=\"dayButtonClasses(day)\"\n                                [attr.tabindex]=\"focusedIndex() === w * 7 + d ? 0 : -1\"\n                                [attr.aria-label]=\"dayLabel(day)\"\n                                (click)=\"selectDay(day)\"\n                                (keydown)=\"onKeydown($event, w * 7 + d)\"\n                            >\n                                {{ day.date() }}\n                            </button>\n                        </td>\n                    }\n                </tr>\n            }\n        </tbody>\n    </table>\n</div>\n"
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		selected: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "selected",
				required: false
			}]
		}],
		activeMonth: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "activeMonth",
				required: true
			}]
		}],
		focusOnInit: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "focusOnInit",
				required: false
			}]
		}],
		daySelected: [{
			type: i0.Output,
			args: ["daySelected"]
		}],
		monthChange: [{
			type: i0.Output,
			args: ["monthChange"]
		}],
		dayButtons: [{
			type: i0.ViewChildren,
			args: ["dayButton", { isSignal: true }]
		}]
	}
});
let nextDatePickerId = 0;
var TumUiDatePickerComponent = class TumUiDatePickerComponent {
	overlayService = inject(TumUiOverlayService);
	viewContainerRef = inject(ViewContainerRef);
	destroyRef = inject(DestroyRef);
	document = inject(DOCUMENT);
	value = model(void 0, ...ngDevMode ? [{ debugName: "value" }] : /* istanbul ignore next */ []);
	invalid = input(false, {
		...ngDevMode ? { debugName: "invalid" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	hideLabelName = input(false, {
		...ngDevMode ? { debugName: "hideLabelName" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	hideValidationMessage = input(false, {
		...ngDevMode ? { debugName: "hideValidationMessage" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	shouldDisplayTimeZoneWarning = input(true, {
		...ngDevMode ? { debugName: "shouldDisplayTimeZoneWarning" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	timeOnly = input(false, {
		...ngDevMode ? { debugName: "timeOnly" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	inputId = input(`tum-ui-date-picker-${nextDatePickerId++}`, ...ngDevMode ? [{ debugName: "inputId" }] : /* istanbul ignore next */ []);
	labelName = input(...ngDevMode ? [void 0, { debugName: "labelName" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	inputValidityChange = output();
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
	valueKey = computed(() => {
		const current = this.value();
		return current ? formatDisplay(current, this.timeOnly()) : "";
	}, ...ngDevMode ? [{ debugName: "valueKey" }] : /* istanbul ignore next */ []);
	isInputValid = linkedSignal(() => {
		this.valueKey();
		return true;
	}, ...ngDevMode ? [{ debugName: "isInputValid" }] : /* istanbul ignore next */ []);
	isOpen = signal(false, ...ngDevMode ? [{ debugName: "isOpen" }] : /* istanbul ignore next */ []);
	panelId = computed(() => `${this.inputId()}-dialog`, ...ngDevMode ? [{ debugName: "panelId" }] : /* istanbul ignore next */ []);
	activeMonth = signal(dayjs().startOf("month"), ...ngDevMode ? [{ debugName: "activeMonth" }] : /* istanbul ignore next */ []);
	timeText = signal("", ...ngDevMode ? [{ debugName: "timeText" }] : /* istanbul ignore next */ []);
	inputText = linkedSignal(() => this.valueKey(), ...ngDevMode ? [{ debugName: "inputText" }] : /* istanbul ignore next */ []);
	panel = viewChild.required("panel", {
		...ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {},
		read: TemplateRef
	});
	dateInput = viewChild.required("dateInput", ...ngDevMode ? [{ debugName: "dateInput" }] : /* istanbul ignore next */ []);
	triggerWrapper = viewChild.required("triggerWrapper", ...ngDevMode ? [{ debugName: "triggerWrapper" }] : /* istanbul ignore next */ []);
	hourField = viewChild("hourInput", ...ngDevMode ? [{ debugName: "hourField" }] : /* istanbul ignore next */ []);
	overlayRef;
	restoreFocusElement;
	pendingHourFocus = false;
	showErrorBorder = computed(() => this.invalid() || !this.isInputValid(), ...ngDevMode ? [{ debugName: "showErrorBorder" }] : /* istanbul ignore next */ []);
	placeholderKey = computed(() => this.timeOnly() ? "tumUi.datePicker.timePlaceholder" : "tumUi.datePicker.placeholder", ...ngDevMode ? [{ debugName: "placeholderKey" }] : /* istanbul ignore next */ []);
	dialogLabelKey = computed(() => this.timeOnly() ? "tumUi.datePicker.timeDialog" : "tumUi.datePicker.dialog", ...ngDevMode ? [{ debugName: "dialogLabelKey" }] : /* istanbul ignore next */ []);
	invalidMessageKey = computed(() => this.timeOnly() ? "tumUi.datePicker.invalidTime" : "tumUi.datePicker.invalid", ...ngDevMode ? [{ debugName: "invalidMessageKey" }] : /* istanbul ignore next */ []);
	openLabelKey = computed(() => this.timeOnly() ? "tumUi.datePicker.openTime" : "tumUi.datePicker.open", ...ngDevMode ? [{ debugName: "openLabelKey" }] : /* istanbul ignore next */ []);
	showClear = computed(() => !!this.inputText(), ...ngDevMode ? [{ debugName: "showClear" }] : /* istanbul ignore next */ []);
	displayHour = computed(() => TIME_REGEX.test(this.timeText()) ? this.timeText().split(":")[0] : "00", ...ngDevMode ? [{ debugName: "displayHour" }] : /* istanbul ignore next */ []);
	displayMinute = computed(() => TIME_REGEX.test(this.timeText()) ? this.timeText().split(":")[1] : "00", ...ngDevMode ? [{ debugName: "displayMinute" }] : /* istanbul ignore next */ []);
	constructor() {
		this.destroyRef.onDestroy(() => this.overlayRef?.dispose());
		effect(() => this.inputValidityChange.emit(this.isInputValid()));
		effect(() => {
			if (this.disabled()) this.close();
		});
		afterRenderEffect(() => {
			const field = this.hourField()?.nativeElement;
			if (field && this.pendingHourFocus) {
				this.pendingHourFocus = false;
				field.focus();
			}
		});
	}
	isValid = computed(() => !this.invalid() && this.isInputValid(), ...ngDevMode ? [{ debugName: "isValid" }] : /* istanbul ignore next */ []);
	onInput(raw) {
		this.inputText.set(raw);
		const parsed = parseDisplay(raw, this.timeOnly(), this.value());
		if (parsed) this.commit(parsed);
		else if (!raw.trim()) {
			if (this.value() !== void 0) this.value.set(void 0);
			else this.isInputValid.set(true);
		} else this.isInputValid.set(false);
	}
	onBlur(raw) {
		const trimmed = raw.trim();
		if (trimmed && !matchesDisplayFormat(trimmed, this.timeOnly())) this.isInputValid.set(false);
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
		if (parsed === void 0) {
			input.value = this.displayHour();
			return;
		}
		this.commitTime(parsed, this.currentTimeParts().minute);
		input.value = this.displayHour();
	}
	onMinuteInput(input) {
		const parsed = this.parseTimePart(input.value, 59);
		if (parsed === void 0) {
			input.value = this.displayMinute();
			return;
		}
		this.commitTime(this.currentTimeParts().hour, parsed);
		input.value = this.displayMinute();
	}
	onTimeKeydown(event, input, field) {
		const delta = event.key === "ArrowUp" ? 1 : event.key === "ArrowDown" ? -1 : 0;
		if (delta === 0) return;
		event.preventDefault();
		const { hour, minute } = this.currentTimeParts();
		if (field === "hour") {
			const base = this.parseTimePart(input.value, 23) ?? hour;
			this.commitTime((base + delta + 24) % 24, minute);
			input.value = this.displayHour();
		} else {
			const base = this.parseTimePart(input.value, 59) ?? minute;
			this.commitTime(hour, (base + delta + 60) % 60);
			input.value = this.displayMinute();
		}
	}
	currentTimeParts() {
		const text = this.timeText();
		if (!TIME_REGEX.test(text)) return {
			hour: 0,
			minute: 0
		};
		const [hour, minute] = text.split(":").map(Number);
		return {
			hour,
			minute
		};
	}
	parseTimePart(raw, max) {
		const trimmed = raw.trim();
		if (!/^\d{1,2}$/.test(trimmed)) return;
		const value = Number(trimmed);
		return value <= max ? value : void 0;
	}
	commitTime(hour, minute) {
		this.timeText.set(`${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`);
		const base = this.value() ?? dayjs().startOf("day");
		this.commit(base.hour(hour).minute(minute).second(0).millisecond(0));
	}
	onDaySelect(day) {
		const time = this.value() ?? dayjs().startOf("day");
		this.commit(combineDateAndTime(day, time));
	}
	clear() {
		this.isInputValid.set(true);
		this.inputText.set("");
		if (this.value() !== void 0) this.value.set(void 0);
		this.dateInput().nativeElement.focus();
	}
	toggle() {
		if (this.isOpen()) this.close();
		else this.open();
	}
	openFromInput(event) {
		event.preventDefault();
		this.open();
	}
	open() {
		if (this.isOpen() || this.disabled()) return;
		const anchor = this.value() ?? dayjs();
		this.activeMonth.set(anchor.startOf("month"));
		this.timeText.set(this.value()?.format("HH:mm") ?? "");
		const activeElement = this.document.activeElement;
		this.restoreFocusElement = activeElement && typeof activeElement.focus === "function" ? activeElement : void 0;
		this.overlayRef = this.overlayService.createConnectedOverlay(this.triggerWrapper(), "bottom", { hasBackdrop: true });
		this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
		this.overlayRef.backdropClick().subscribe(() => this.close());
		this.overlayRef.keydownEvents().subscribe((event) => {
			if (event.key === "Escape") this.close();
		});
		this.pendingHourFocus = this.timeOnly();
		this.isOpen.set(true);
	}
	close() {
		if (!this.isOpen()) return;
		this.overlayRef?.dispose();
		this.overlayRef = void 0;
		this.isOpen.set(false);
		if (!this.disabled() && this.restoreFocusElement?.isConnected) this.restoreFocusElement.focus();
		this.restoreFocusElement = void 0;
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
		this.activeMonth.set(next.startOf("month"));
		this.timeText.set(next.format("HH:mm"));
		this.value.set(next);
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiDatePickerComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiDatePickerComponent,
		isStandalone: true,
		selector: "tum-ui-date-picker",
		inputs: {
			value: {
				classPropertyName: "value",
				publicName: "value",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			invalid: {
				classPropertyName: "invalid",
				publicName: "invalid",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			hideLabelName: {
				classPropertyName: "hideLabelName",
				publicName: "hideLabelName",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			hideValidationMessage: {
				classPropertyName: "hideValidationMessage",
				publicName: "hideValidationMessage",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			shouldDisplayTimeZoneWarning: {
				classPropertyName: "shouldDisplayTimeZoneWarning",
				publicName: "shouldDisplayTimeZoneWarning",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			timeOnly: {
				classPropertyName: "timeOnly",
				publicName: "timeOnly",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			inputId: {
				classPropertyName: "inputId",
				publicName: "inputId",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			labelName: {
				classPropertyName: "labelName",
				publicName: "labelName",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: {
			value: "valueChange",
			inputValidityChange: "inputValidityChange",
			touch: "touch"
		},
		host: { classAttribute: "tum-ui-date-picker" },
		viewQueries: [
			{
				propertyName: "panel",
				first: true,
				predicate: ["panel"],
				descendants: true,
				read: TemplateRef,
				isSignal: true
			},
			{
				propertyName: "dateInput",
				first: true,
				predicate: ["dateInput"],
				descendants: true,
				isSignal: true
			},
			{
				propertyName: "triggerWrapper",
				first: true,
				predicate: ["triggerWrapper"],
				descendants: true,
				isSignal: true
			},
			{
				propertyName: "hourField",
				first: true,
				predicate: ["hourInput"],
				descendants: true,
				isSignal: true
			}
		],
		ngImport: i0,
		template: "<div class=\"tum:flex tum:flex-col tum:gap-1\">\n    @if ((!hideLabelName() && labelName()) || shouldDisplayTimeZoneWarning()) {\n        <div class=\"tum:flex tum:items-center tum:gap-1\">\n            @if (!hideLabelName() && labelName()) {\n                <label [attr.for]=\"inputId()\" class=\"tum:font-semibold tum:text-text\">{{ labelName()! | tumUiTranslate }}</label>\n            }\n            @if (shouldDisplayTimeZoneWarning()) {\n                <fa-stack\n                    class=\"tum:h-4 tum:w-4\"\n                    tabindex=\"0\"\n                    role=\"img\"\n                    [attr.aria-label]=\"'tumUi.datePicker.timeZoneWarning' | tumUiTranslate: { timeZone: currentTimeZone }\"\n                    [tumUiTooltip]=\"'tumUi.datePicker.timeZoneWarning' | tumUiTranslate: { timeZone: currentTimeZone }\"\n                >\n                    <fa-icon [icon]=\"faGlobe\" stackItemSize=\"1x\" class=\"tum:text-muted\" />\n                    <fa-icon [icon]=\"faClock\" stackItemSize=\"1x\" transform=\"shrink-6 down-5 right-5\" class=\"tum:text-muted\" />\n                </fa-stack>\n            }\n        </div>\n    }\n\n    <div #triggerWrapper class=\"tum:relative tum:flex tum:items-center\">\n        <input\n            #dateInput\n            [id]=\"inputId()\"\n            type=\"text\"\n            role=\"combobox\"\n            [value]=\"inputText()\"\n            [disabled]=\"disabled()\"\n            [placeholder]=\"placeholderKey() | tumUiTranslate\"\n            aria-haspopup=\"dialog\"\n            [attr.aria-expanded]=\"isOpen()\"\n            [attr.aria-controls]=\"isOpen() ? panelId() : null\"\n            [attr.aria-invalid]=\"showErrorBorder()\"\n            [attr.aria-label]=\"ariaLabel()\"\n            [attr.aria-describedby]=\"!hideValidationMessage() && showErrorBorder() ? inputId() + '-error' : null\"\n            (input)=\"onInput(dateInput.value)\"\n            (blur)=\"onBlur(dateInput.value)\"\n            (keydown.arrowdown)=\"openFromInput($event)\"\n            class=\"tum-ui-date-picker-input tum:box-border tum:w-full tum:rounded-md tum:border tum:bg-control-background tum:py-2 tum:ps-3 tum:pe-17 tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n            [class]=\"showErrorBorder() ? 'tum:border-state-danger' : 'tum:border-control-border'\"\n        />\n        @if (showClear() && !disabled()) {\n            <button\n                type=\"button\"\n                class=\"tum:absolute tum:end-9 tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text-hover\"\n                (click)=\"clear()\"\n                [attr.aria-label]=\"'tumUi.datePicker.clear' | tumUiTranslate\"\n            >\n                <fa-icon [icon]=\"faXmark\" />\n            </button>\n        }\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            class=\"tum:absolute tum:end-2 tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-accent\"\n            [disabled]=\"disabled()\"\n            (click)=\"toggle()\"\n            [attr.aria-label]=\"openLabelKey() | tumUiTranslate\"\n            aria-haspopup=\"dialog\"\n            [attr.aria-expanded]=\"isOpen() ? 'true' : 'false'\"\n            [attr.aria-controls]=\"isOpen() ? panelId() : null\"\n        >\n            <fa-icon [icon]=\"timeOnly() ? faClock : faCalendar\" />\n        </button>\n    </div>\n\n    @if (!hideValidationMessage() && showErrorBorder()) {\n        <span [id]=\"inputId() + '-error'\" role=\"alert\" class=\"tum:text-sm tum:text-state-danger\">\n            {{ invalidMessageKey() | tumUiTranslate }}\n        </span>\n    }\n</div>\n\n<ng-template #panel>\n    <div\n        [id]=\"panelId()\"\n        class=\"tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:p-3 tum:shadow-lg\"\n        role=\"dialog\"\n        aria-modal=\"true\"\n        [attr.aria-label]=\"dialogLabelKey() | tumUiTranslate\"\n        cdkTrapFocus\n    >\n        @if (!timeOnly()) {\n            <tum-ui-calendar [selected]=\"value()\" [activeMonth]=\"activeMonth()\" [focusOnInit]=\"true\" (daySelected)=\"onDaySelect($event)\" (monthChange)=\"activeMonth.set($event)\" />\n        }\n        <div\n            class=\"tum:flex tum:items-center tum:justify-center tum:gap-2\"\n            [class]=\"timeOnly() ? '' : 'tum:mt-3'\"\n            role=\"group\"\n            [attr.aria-label]=\"'tumUi.datePicker.time' | tumUiTranslate\"\n        >\n            <div class=\"tum:flex tum:flex-col tum:items-center\">\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepHour(1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.incrementHour' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronUp\" />\n                </button>\n                <input\n                    #hourInput\n                    type=\"text\"\n                    inputmode=\"numeric\"\n                    maxlength=\"2\"\n                    [value]=\"displayHour()\"\n                    [disabled]=\"disabled()\"\n                    (change)=\"onHourInput(hourInput)\"\n                    (keydown)=\"onTimeKeydown($event, hourInput, 'hour')\"\n                    class=\"tum:box-border tum:w-9 tum:appearance-none tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1 tum:text-center tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                    [attr.aria-label]=\"'tumUi.datePicker.hour' | tumUiTranslate\"\n                />\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepHour(-1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.decrementHour' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronDown\" />\n                </button>\n            </div>\n            <span class=\"tum:pb-0.5 tum:font-semibold tum:text-text\">:</span>\n            <div class=\"tum:flex tum:flex-col tum:items-center\">\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepMinute(1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.incrementMinute' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronUp\" />\n                </button>\n                <input\n                    #minuteInput\n                    type=\"text\"\n                    inputmode=\"numeric\"\n                    maxlength=\"2\"\n                    [value]=\"displayMinute()\"\n                    [disabled]=\"disabled()\"\n                    (change)=\"onMinuteInput(minuteInput)\"\n                    (keydown)=\"onTimeKeydown($event, minuteInput, 'minute')\"\n                    class=\"tum:box-border tum:w-9 tum:appearance-none tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1 tum:text-center tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                    [attr.aria-label]=\"'tumUi.datePicker.minute' | tumUiTranslate\"\n                />\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepMinute(-1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.decrementMinute' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronDown\" />\n                </button>\n            </div>\n        </div>\n        <div class=\"tum:mt-3 tum:flex tum:justify-end\">\n            <tum-ui-button severity=\"secondary\" variant=\"text\" size=\"small\" (clicked)=\"close()\">\n                <span>{{ 'tumUi.datePicker.done' | tumUiTranslate }}</span>\n            </tum-ui-button>\n        </div>\n    </div>\n</ng-template>\n",
		styles: [":host{display:block}.tum-ui-date-picker-input{border-start-start-radius:var(--tum-ui-input-group-start-radius, var(--tumaet-ui-radius-md));border-end-start-radius:var(--tum-ui-input-group-start-radius, var(--tumaet-ui-radius-md));border-start-end-radius:var(--tum-ui-input-group-end-radius, var(--tumaet-ui-radius-md));border-end-end-radius:var(--tum-ui-input-group-end-radius, var(--tumaet-ui-radius-md))}\n"],
		dependencies: [
			{
				kind: "ngmodule",
				type: A11yModule
			},
			{
				kind: "directive",
				type: i1$5.CdkTrapFocus,
				selector: "[cdkTrapFocus]",
				inputs: ["cdkTrapFocus", "cdkTrapFocusAutoCapture"],
				exportAs: ["cdkTrapFocus"]
			},
			{
				kind: "component",
				type: FaIconComponent,
				selector: "fa-icon",
				inputs: [
					"icon",
					"title",
					"animation",
					"mask",
					"flip",
					"size",
					"pull",
					"border",
					"inverse",
					"symbol",
					"rotate",
					"fixedWidth",
					"transform",
					"a11yRole"
				],
				outputs: [
					"iconChange",
					"titleChange",
					"animationChange",
					"maskChange",
					"flipChange",
					"sizeChange",
					"pullChange",
					"borderChange",
					"inverseChange",
					"symbolChange",
					"rotateChange",
					"fixedWidthChange",
					"transformChange",
					"a11yRoleChange"
				]
			},
			{
				kind: "component",
				type: FaStackComponent,
				selector: "fa-stack",
				inputs: ["size"]
			},
			{
				kind: "directive",
				type: FaStackItemSizeDirective,
				selector: "fa-icon[stackItemSize],fa-duotone-icon[stackItemSize]",
				inputs: ["stackItemSize", "size"]
			},
			{
				kind: "component",
				type: TumUiButtonComponent,
				selector: "tum-ui-button",
				inputs: [
					"severity",
					"size",
					"variant",
					"disabled",
					"rounded",
					"loading",
					"icon",
					"type",
					"ariaLabel",
					"ariaExpanded",
					"ariaPressed",
					"ariaControls",
					"ariaDescribedBy"
				],
				outputs: ["clicked"]
			},
			{
				kind: "component",
				type: TumUiCalendarComponent,
				selector: "tum-ui-calendar",
				inputs: [
					"selected",
					"activeMonth",
					"focusOnInit"
				],
				outputs: ["daySelected", "monthChange"]
			},
			{
				kind: "directive",
				type: TumUiTooltipDirective,
				selector: "[tumUiTooltip]",
				inputs: [
					"tumUiTooltip",
					"tumUiTooltipPlacement",
					"tumUiTooltipDescribesHost",
					"showDelayMs",
					"hideDelayMs"
				]
			},
			{
				kind: "pipe",
				type: TumUiTranslatePipe,
				name: "tumUiTranslate"
			}
		],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiDatePickerComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-date-picker",
			host: { class: "tum-ui-date-picker" },
			imports: [
				A11yModule,
				FaIconComponent,
				FaStackComponent,
				FaStackItemSizeDirective,
				TumUiButtonComponent,
				TumUiCalendarComponent,
				TumUiTooltipDirective,
				TumUiTranslatePipe
			],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<div class=\"tum:flex tum:flex-col tum:gap-1\">\n    @if ((!hideLabelName() && labelName()) || shouldDisplayTimeZoneWarning()) {\n        <div class=\"tum:flex tum:items-center tum:gap-1\">\n            @if (!hideLabelName() && labelName()) {\n                <label [attr.for]=\"inputId()\" class=\"tum:font-semibold tum:text-text\">{{ labelName()! | tumUiTranslate }}</label>\n            }\n            @if (shouldDisplayTimeZoneWarning()) {\n                <fa-stack\n                    class=\"tum:h-4 tum:w-4\"\n                    tabindex=\"0\"\n                    role=\"img\"\n                    [attr.aria-label]=\"'tumUi.datePicker.timeZoneWarning' | tumUiTranslate: { timeZone: currentTimeZone }\"\n                    [tumUiTooltip]=\"'tumUi.datePicker.timeZoneWarning' | tumUiTranslate: { timeZone: currentTimeZone }\"\n                >\n                    <fa-icon [icon]=\"faGlobe\" stackItemSize=\"1x\" class=\"tum:text-muted\" />\n                    <fa-icon [icon]=\"faClock\" stackItemSize=\"1x\" transform=\"shrink-6 down-5 right-5\" class=\"tum:text-muted\" />\n                </fa-stack>\n            }\n        </div>\n    }\n\n    <div #triggerWrapper class=\"tum:relative tum:flex tum:items-center\">\n        <input\n            #dateInput\n            [id]=\"inputId()\"\n            type=\"text\"\n            role=\"combobox\"\n            [value]=\"inputText()\"\n            [disabled]=\"disabled()\"\n            [placeholder]=\"placeholderKey() | tumUiTranslate\"\n            aria-haspopup=\"dialog\"\n            [attr.aria-expanded]=\"isOpen()\"\n            [attr.aria-controls]=\"isOpen() ? panelId() : null\"\n            [attr.aria-invalid]=\"showErrorBorder()\"\n            [attr.aria-label]=\"ariaLabel()\"\n            [attr.aria-describedby]=\"!hideValidationMessage() && showErrorBorder() ? inputId() + '-error' : null\"\n            (input)=\"onInput(dateInput.value)\"\n            (blur)=\"onBlur(dateInput.value)\"\n            (keydown.arrowdown)=\"openFromInput($event)\"\n            class=\"tum-ui-date-picker-input tum:box-border tum:w-full tum:rounded-md tum:border tum:bg-control-background tum:py-2 tum:ps-3 tum:pe-17 tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n            [class]=\"showErrorBorder() ? 'tum:border-state-danger' : 'tum:border-control-border'\"\n        />\n        @if (showClear() && !disabled()) {\n            <button\n                type=\"button\"\n                class=\"tum:absolute tum:end-9 tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text-hover\"\n                (click)=\"clear()\"\n                [attr.aria-label]=\"'tumUi.datePicker.clear' | tumUiTranslate\"\n            >\n                <fa-icon [icon]=\"faXmark\" />\n            </button>\n        }\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            class=\"tum:absolute tum:end-2 tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-accent\"\n            [disabled]=\"disabled()\"\n            (click)=\"toggle()\"\n            [attr.aria-label]=\"openLabelKey() | tumUiTranslate\"\n            aria-haspopup=\"dialog\"\n            [attr.aria-expanded]=\"isOpen() ? 'true' : 'false'\"\n            [attr.aria-controls]=\"isOpen() ? panelId() : null\"\n        >\n            <fa-icon [icon]=\"timeOnly() ? faClock : faCalendar\" />\n        </button>\n    </div>\n\n    @if (!hideValidationMessage() && showErrorBorder()) {\n        <span [id]=\"inputId() + '-error'\" role=\"alert\" class=\"tum:text-sm tum:text-state-danger\">\n            {{ invalidMessageKey() | tumUiTranslate }}\n        </span>\n    }\n</div>\n\n<ng-template #panel>\n    <div\n        [id]=\"panelId()\"\n        class=\"tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:p-3 tum:shadow-lg\"\n        role=\"dialog\"\n        aria-modal=\"true\"\n        [attr.aria-label]=\"dialogLabelKey() | tumUiTranslate\"\n        cdkTrapFocus\n    >\n        @if (!timeOnly()) {\n            <tum-ui-calendar [selected]=\"value()\" [activeMonth]=\"activeMonth()\" [focusOnInit]=\"true\" (daySelected)=\"onDaySelect($event)\" (monthChange)=\"activeMonth.set($event)\" />\n        }\n        <div\n            class=\"tum:flex tum:items-center tum:justify-center tum:gap-2\"\n            [class]=\"timeOnly() ? '' : 'tum:mt-3'\"\n            role=\"group\"\n            [attr.aria-label]=\"'tumUi.datePicker.time' | tumUiTranslate\"\n        >\n            <div class=\"tum:flex tum:flex-col tum:items-center\">\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepHour(1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.incrementHour' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronUp\" />\n                </button>\n                <input\n                    #hourInput\n                    type=\"text\"\n                    inputmode=\"numeric\"\n                    maxlength=\"2\"\n                    [value]=\"displayHour()\"\n                    [disabled]=\"disabled()\"\n                    (change)=\"onHourInput(hourInput)\"\n                    (keydown)=\"onTimeKeydown($event, hourInput, 'hour')\"\n                    class=\"tum:box-border tum:w-9 tum:appearance-none tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1 tum:text-center tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                    [attr.aria-label]=\"'tumUi.datePicker.hour' | tumUiTranslate\"\n                />\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepHour(-1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.decrementHour' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronDown\" />\n                </button>\n            </div>\n            <span class=\"tum:pb-0.5 tum:font-semibold tum:text-text\">:</span>\n            <div class=\"tum:flex tum:flex-col tum:items-center\">\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepMinute(1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.incrementMinute' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronUp\" />\n                </button>\n                <input\n                    #minuteInput\n                    type=\"text\"\n                    inputmode=\"numeric\"\n                    maxlength=\"2\"\n                    [value]=\"displayMinute()\"\n                    [disabled]=\"disabled()\"\n                    (change)=\"onMinuteInput(minuteInput)\"\n                    (keydown)=\"onTimeKeydown($event, minuteInput, 'minute')\"\n                    class=\"tum:box-border tum:w-9 tum:appearance-none tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1 tum:text-center tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                    [attr.aria-label]=\"'tumUi.datePicker.minute' | tumUiTranslate\"\n                />\n                <button\n                    type=\"button\"\n                    class=\"tum:flex tum:h-6 tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-1 tum:text-muted tum:hover:text-accent\"\n                    [disabled]=\"disabled()\"\n                    (click)=\"stepMinute(-1)\"\n                    [attr.aria-label]=\"'tumUi.datePicker.decrementMinute' | tumUiTranslate\"\n                >\n                    <fa-icon [icon]=\"faChevronDown\" />\n                </button>\n            </div>\n        </div>\n        <div class=\"tum:mt-3 tum:flex tum:justify-end\">\n            <tum-ui-button severity=\"secondary\" variant=\"text\" size=\"small\" (clicked)=\"close()\">\n                <span>{{ 'tumUi.datePicker.done' | tumUiTranslate }}</span>\n            </tum-ui-button>\n        </div>\n    </div>\n</ng-template>\n",
			styles: [":host{display:block}.tum-ui-date-picker-input{border-start-start-radius:var(--tum-ui-input-group-start-radius, var(--tumaet-ui-radius-md));border-end-start-radius:var(--tum-ui-input-group-start-radius, var(--tumaet-ui-radius-md));border-start-end-radius:var(--tum-ui-input-group-end-radius, var(--tumaet-ui-radius-md));border-end-end-radius:var(--tum-ui-input-group-end-radius, var(--tumaet-ui-radius-md))}\n"]
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		value: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "value",
				required: false
			}]
		}, {
			type: i0.Output,
			args: ["valueChange"]
		}],
		invalid: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "invalid",
				required: false
			}]
		}],
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		hideLabelName: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "hideLabelName",
				required: false
			}]
		}],
		hideValidationMessage: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "hideValidationMessage",
				required: false
			}]
		}],
		shouldDisplayTimeZoneWarning: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "shouldDisplayTimeZoneWarning",
				required: false
			}]
		}],
		timeOnly: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "timeOnly",
				required: false
			}]
		}],
		inputId: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "inputId",
				required: false
			}]
		}],
		labelName: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "labelName",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		inputValidityChange: [{
			type: i0.Output,
			args: ["inputValidityChange"]
		}],
		touch: [{
			type: i0.Output,
			args: ["touch"]
		}],
		panel: [{
			type: i0.ViewChild,
			args: ["panel", {
				read: TemplateRef,
				isSignal: true
			}]
		}],
		dateInput: [{
			type: i0.ViewChild,
			args: ["dateInput", { isSignal: true }]
		}],
		triggerWrapper: [{
			type: i0.ViewChild,
			args: ["triggerWrapper", { isSignal: true }]
		}],
		hourField: [{
			type: i0.ViewChild,
			args: ["hourInput", { isSignal: true }]
		}]
	}
});
var TumUiEmptyStateComponent = class TumUiEmptyStateComponent {
	icon = input.required(...ngDevMode ? [{ debugName: "icon" }] : /* istanbul ignore next */ []);
	variant = input("outlined", ...ngDevMode ? [{ debugName: "variant" }] : /* istanbul ignore next */ []);
	title = input.required(...ngDevMode ? [{ debugName: "title" }] : /* istanbul ignore next */ []);
	description = input(...ngDevMode ? [void 0, { debugName: "description" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiEmptyStateComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiEmptyStateComponent,
		isStandalone: true,
		selector: "tum-ui-empty-state",
		inputs: {
			icon: {
				classPropertyName: "icon",
				publicName: "icon",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			variant: {
				classPropertyName: "variant",
				publicName: "variant",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			title: {
				classPropertyName: "title",
				publicName: "title",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			description: {
				classPropertyName: "description",
				publicName: "description",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: {
			properties: { "attr.data-variant": "variant()" },
			classAttribute: "tum-ui-empty-state tum:flex tum:flex-col tum:items-center tum:text-center"
		},
		ngImport: i0,
		template: "<div class=\"tum-ui-empty-state-icon tum:flex tum:shrink-0 tum:items-center tum:justify-center\" aria-hidden=\"true\">\n    <fa-icon [icon]=\"icon()\" />\n</div>\n\n<h2 class=\"tum-ui-empty-state-title\">{{ title() }}</h2>\n\n@if (description(); as descriptionText) {\n    <p class=\"tum-ui-empty-state-description\">{{ descriptionText }}</p>\n}\n\n<div class=\"tum-ui-empty-state-actions tum:flex tum:flex-wrap tum:items-center tum:justify-center\">\n    <ng-content select=\"[tumUiEmptyStateActions]\" />\n</div>\n\n<div class=\"tum-ui-empty-state-documentation\">\n    <ng-content select=\"[tumUiEmptyStateDocumentation]\" />\n</div>\n",
		styles: [":host{min-width:0}.tum-ui-empty-state-icon{width:calc(var(--tumaet-ui-spacing) * 14);height:calc(var(--tumaet-ui-spacing) * 14);border:1px solid var(--tumaet-ui-control-border-color);border-radius:var(--tumaet-ui-radius-xl);background-color:var(--tumaet-ui-content-background);color:var(--tumaet-ui-primary-color);font-size:var(--tumaet-ui-font-size-lg)}:host([data-variant=solid]) .tum-ui-empty-state-icon{border-color:var(--tumaet-ui-primary-color);background-color:var(--tumaet-ui-primary-color);color:var(--tumaet-ui-primary-contrast-color)}:host([data-variant=plain]) .tum-ui-empty-state-icon{width:auto;height:auto;border:0;background-color:transparent}.tum-ui-empty-state-title{margin:calc(var(--tumaet-ui-spacing) * 4) 0 0;color:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-xl);font-weight:600;line-height:var(--tumaet-ui-line-height-xl)}.tum-ui-empty-state-description{max-width:40rem;margin:calc(var(--tumaet-ui-spacing) * 2) 0 0;color:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-base);line-height:var(--tumaet-ui-line-height-base)}.tum-ui-empty-state-actions{max-width:100%;gap:calc(var(--tumaet-ui-spacing) * 4);margin-block-start:calc(var(--tumaet-ui-spacing) * 4)}.tum-ui-empty-state-documentation{margin-block-start:calc(var(--tumaet-ui-spacing) * 4);color:var(--tumaet-ui-muted-color);font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm)}.tum-ui-empty-state-actions:empty,.tum-ui-empty-state-documentation:empty{display:none}\n"],
		dependencies: [{
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiEmptyStateComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-empty-state",
			imports: [FaIconComponent],
			host: {
				class: "tum-ui-empty-state tum:flex tum:flex-col tum:items-center tum:text-center",
				"[attr.data-variant]": "variant()"
			},
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<div class=\"tum-ui-empty-state-icon tum:flex tum:shrink-0 tum:items-center tum:justify-center\" aria-hidden=\"true\">\n    <fa-icon [icon]=\"icon()\" />\n</div>\n\n<h2 class=\"tum-ui-empty-state-title\">{{ title() }}</h2>\n\n@if (description(); as descriptionText) {\n    <p class=\"tum-ui-empty-state-description\">{{ descriptionText }}</p>\n}\n\n<div class=\"tum-ui-empty-state-actions tum:flex tum:flex-wrap tum:items-center tum:justify-center\">\n    <ng-content select=\"[tumUiEmptyStateActions]\" />\n</div>\n\n<div class=\"tum-ui-empty-state-documentation\">\n    <ng-content select=\"[tumUiEmptyStateDocumentation]\" />\n</div>\n",
			styles: [":host{min-width:0}.tum-ui-empty-state-icon{width:calc(var(--tumaet-ui-spacing) * 14);height:calc(var(--tumaet-ui-spacing) * 14);border:1px solid var(--tumaet-ui-control-border-color);border-radius:var(--tumaet-ui-radius-xl);background-color:var(--tumaet-ui-content-background);color:var(--tumaet-ui-primary-color);font-size:var(--tumaet-ui-font-size-lg)}:host([data-variant=solid]) .tum-ui-empty-state-icon{border-color:var(--tumaet-ui-primary-color);background-color:var(--tumaet-ui-primary-color);color:var(--tumaet-ui-primary-contrast-color)}:host([data-variant=plain]) .tum-ui-empty-state-icon{width:auto;height:auto;border:0;background-color:transparent}.tum-ui-empty-state-title{margin:calc(var(--tumaet-ui-spacing) * 4) 0 0;color:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-xl);font-weight:600;line-height:var(--tumaet-ui-line-height-xl)}.tum-ui-empty-state-description{max-width:40rem;margin:calc(var(--tumaet-ui-spacing) * 2) 0 0;color:var(--tumaet-ui-text-color);font-size:var(--tumaet-ui-font-size-base);line-height:var(--tumaet-ui-line-height-base)}.tum-ui-empty-state-actions{max-width:100%;gap:calc(var(--tumaet-ui-spacing) * 4);margin-block-start:calc(var(--tumaet-ui-spacing) * 4)}.tum-ui-empty-state-documentation{margin-block-start:calc(var(--tumaet-ui-spacing) * 4);color:var(--tumaet-ui-muted-color);font-size:var(--tumaet-ui-font-size-sm);line-height:var(--tumaet-ui-line-height-sm)}.tum-ui-empty-state-actions:empty,.tum-ui-empty-state-documentation:empty{display:none}\n"]
		}]
	}],
	propDecorators: {
		icon: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "icon",
				required: true
			}]
		}],
		variant: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "variant",
				required: false
			}]
		}],
		title: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "title",
				required: true
			}]
		}],
		description: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "description",
				required: false
			}]
		}]
	}
});
const TUM_UI_FORM_FIELD = new InjectionToken("TumUiFormField");
let nextFormFieldId = 0;
var TumUiFormFieldComponent = class TumUiFormFieldComponent {
	label = input("", ...ngDevMode ? [{ debugName: "label" }] : /* istanbul ignore next */ []);
	controlId = input(...ngDevMode ? [void 0, { debugName: "controlId" }] : /* istanbul ignore next */ []);
	required = input(false, {
		...ngDevMode ? { debugName: "required" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	hint = input(...ngDevMode ? [void 0, { debugName: "hint" }] : /* istanbul ignore next */ []);
	invalid = input(false, {
		...ngDevMode ? { debugName: "invalid" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	error = input(...ngDevMode ? [void 0, { debugName: "error" }] : /* istanbul ignore next */ []);
	reportedControlId = signal(void 0, ...ngDevMode ? [{ debugName: "reportedControlId" }] : /* istanbul ignore next */ []);
	fieldId = nextFormFieldId++;
	generatedControlId = `tum-ui-form-field-${this.fieldId}-control`;
	hintId = `tum-ui-form-field-${this.fieldId}-hint`;
	errorId = `tum-ui-form-field-${this.fieldId}-error`;
	explicitControlId = this.controlId;
	labelTargetId = computed(() => this.controlId() ?? this.reportedControlId() ?? this.generatedControlId, ...ngDevMode ? [{ debugName: "labelTargetId" }] : /* istanbul ignore next */ []);
	adoptControlId(id) {
		this.reportedControlId.set(id);
	}
	showHint = computed(() => !!this.hint()?.trim() && !this.invalid(), ...ngDevMode ? [{ debugName: "showHint" }] : /* istanbul ignore next */ []);
	describedBy = computed(() => {
		if (this.invalid()) return this.errorId;
		return this.showHint() ? this.hintId : void 0;
	}, ...ngDevMode ? [{ debugName: "describedBy" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiFormFieldComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiFormFieldComponent,
		isStandalone: true,
		selector: "tum-ui-form-field",
		inputs: {
			label: {
				classPropertyName: "label",
				publicName: "label",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			controlId: {
				classPropertyName: "controlId",
				publicName: "controlId",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			required: {
				classPropertyName: "required",
				publicName: "required",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			hint: {
				classPropertyName: "hint",
				publicName: "hint",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			invalid: {
				classPropertyName: "invalid",
				publicName: "invalid",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			error: {
				classPropertyName: "error",
				publicName: "error",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: { classAttribute: "tum-ui-form-field" },
		providers: [{
			provide: TUM_UI_FORM_FIELD,
			useExisting: TumUiFormFieldComponent
		}],
		ngImport: i0,
		template: "<label class=\"tum-ui-form-field-label tum:flex tum:items-center tum:gap-1 tum:text-sm tum:font-medium tum:text-text\" [for]=\"labelTargetId()\">\n    {{ label() }}<ng-content select=\"[tumUiFormFieldLabel]\" />\n    @if (required()) {\n        <span class=\"tum-ui-form-field-required tum:text-state-danger\" aria-hidden=\"true\">*</span>\n    }\n</label>\n<div class=\"tum-ui-form-field-control\">\n    <ng-content />\n</div>\n@if (showHint()) {\n    <span [id]=\"hintId\" class=\"tum-ui-form-field-hint tum:text-sm tum:text-muted\">{{ hint() }}</span>\n}\n<div [id]=\"errorId\" class=\"tum-ui-form-field-error tum:text-sm tum:text-state-danger\" role=\"alert\" [hidden]=\"!invalid()\">\n    {{ error() }}<ng-content select=\"[tumUiFormFieldError]\" />\n</div>\n",
		styles: [":host{display:flex;flex-direction:column;gap:calc(var(--tumaet-ui-spacing) * 1.5)}.tum-ui-form-field-label{min-width:0}.tum-ui-form-field-control{display:flex;flex-direction:column;min-width:0}[hidden]{display:none}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiFormFieldComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-form-field",
			host: { class: "tum-ui-form-field" },
			providers: [{
				provide: TUM_UI_FORM_FIELD,
				useExisting: TumUiFormFieldComponent
			}],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<label class=\"tum-ui-form-field-label tum:flex tum:items-center tum:gap-1 tum:text-sm tum:font-medium tum:text-text\" [for]=\"labelTargetId()\">\n    {{ label() }}<ng-content select=\"[tumUiFormFieldLabel]\" />\n    @if (required()) {\n        <span class=\"tum-ui-form-field-required tum:text-state-danger\" aria-hidden=\"true\">*</span>\n    }\n</label>\n<div class=\"tum-ui-form-field-control\">\n    <ng-content />\n</div>\n@if (showHint()) {\n    <span [id]=\"hintId\" class=\"tum-ui-form-field-hint tum:text-sm tum:text-muted\">{{ hint() }}</span>\n}\n<div [id]=\"errorId\" class=\"tum-ui-form-field-error tum:text-sm tum:text-state-danger\" role=\"alert\" [hidden]=\"!invalid()\">\n    {{ error() }}<ng-content select=\"[tumUiFormFieldError]\" />\n</div>\n",
			styles: [":host{display:flex;flex-direction:column;gap:calc(var(--tumaet-ui-spacing) * 1.5)}.tum-ui-form-field-label{min-width:0}.tum-ui-form-field-control{display:flex;flex-direction:column;min-width:0}[hidden]{display:none}\n"]
		}]
	}],
	propDecorators: {
		label: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "label",
				required: false
			}]
		}],
		controlId: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "controlId",
				required: false
			}]
		}],
		required: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "required",
				required: false
			}]
		}],
		hint: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "hint",
				required: false
			}]
		}],
		invalid: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "invalid",
				required: false
			}]
		}],
		error: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "error",
				required: false
			}]
		}]
	}
});
var TumUiIconFieldComponent = class TumUiIconFieldComponent {
	icon = input(...ngDevMode ? [void 0, { debugName: "icon" }] : /* istanbul ignore next */ []);
	iconPosition = input("left", ...ngDevMode ? [{ debugName: "iconPosition" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiIconFieldComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiIconFieldComponent,
		isStandalone: true,
		selector: "tum-ui-icon-field",
		inputs: {
			icon: {
				classPropertyName: "icon",
				publicName: "icon",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			iconPosition: {
				classPropertyName: "iconPosition",
				publicName: "iconPosition",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: {
			properties: {
				"attr.data-position": "iconPosition()",
				"attr.data-has-icon": "icon() ? \"\" : null"
			},
			classAttribute: "tum-ui-icon-field"
		},
		ngImport: i0,
		template: "@if (icon(); as fieldIcon) {\n    <fa-icon [icon]=\"fieldIcon\" class=\"tum-ui-input-icon tum:text-muted\" aria-hidden=\"true\" />\n}\n<ng-content />\n",
		styles: [":host{position:relative;display:block}.tum-ui-input-icon{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}:host[data-position=right] .tum-ui-input-icon{inset-inline-end:calc(var(--tumaet-ui-spacing) * 3)}:host:not([data-position=right]) .tum-ui-input-icon{inset-inline-start:calc(var(--tumaet-ui-spacing) * 3)}\n"],
		dependencies: [{
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiIconFieldComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-icon-field",
			imports: [FaIconComponent],
			host: {
				class: "tum-ui-icon-field",
				"[attr.data-position]": "iconPosition()",
				"[attr.data-has-icon]": "icon() ? \"\" : null"
			},
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "@if (icon(); as fieldIcon) {\n    <fa-icon [icon]=\"fieldIcon\" class=\"tum-ui-input-icon tum:text-muted\" aria-hidden=\"true\" />\n}\n<ng-content />\n",
			styles: [":host{position:relative;display:block}.tum-ui-input-icon{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}:host[data-position=right] .tum-ui-input-icon{inset-inline-end:calc(var(--tumaet-ui-spacing) * 3)}:host:not([data-position=right]) .tum-ui-input-icon{inset-inline-start:calc(var(--tumaet-ui-spacing) * 3)}\n"]
		}]
	}],
	propDecorators: {
		icon: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "icon",
				required: false
			}]
		}],
		iconPosition: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "iconPosition",
				required: false
			}]
		}]
	}
});
var TumUiInputGroupAddonComponent = class TumUiInputGroupAddonComponent {
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiInputGroupAddonComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "14.0.0",
		version: "22.2.0",
		type: TumUiInputGroupAddonComponent,
		isStandalone: true,
		selector: "tum-ui-input-group-addon",
		host: { classAttribute: "tum-ui-input-group-addon tum:bg-control-background tum:text-muted tum:border-y tum:first:border-s tum:first:rounded-s-md tum:last:border-e tum:last:rounded-e-md" },
		ngImport: i0,
		template: "<ng-content />",
		isInline: true,
		styles: [":host{display:flex;align-items:center;justify-content:center;padding:calc(var(--tumaet-ui-spacing) * 2);min-width:calc(var(--tumaet-ui-spacing) * 10);white-space:nowrap;border-color:var(--tum-ui-input-group-border-color, var(--tumaet-ui-control-border-color));transition:border-color .2s}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiInputGroupAddonComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-input-group-addon",
			template: "<ng-content />",
			host: { class: "tum-ui-input-group-addon tum:bg-control-background tum:text-muted tum:border-y tum:first:border-s tum:first:rounded-s-md tum:last:border-e tum:last:rounded-e-md" },
			changeDetection: ChangeDetectionStrategy.OnPush,
			styles: [":host{display:flex;align-items:center;justify-content:center;padding:calc(var(--tumaet-ui-spacing) * 2);min-width:calc(var(--tumaet-ui-spacing) * 10);white-space:nowrap;border-color:var(--tum-ui-input-group-border-color, var(--tumaet-ui-control-border-color));transition:border-color .2s}\n"]
		}]
	}]
});
var TumUiInputGroupComponent = class TumUiInputGroupComponent {
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiInputGroupComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "14.0.0",
		version: "22.2.0",
		type: TumUiInputGroupComponent,
		isStandalone: true,
		selector: "tum-ui-input-group",
		host: { classAttribute: "tum-ui-input-group" },
		ngImport: i0,
		template: "<ng-content />",
		isInline: true,
		styles: [":host{display:flex;align-items:stretch;--tum-ui-input-group-border-color: var(--tumaet-ui-control-border-color)}:host:has(:is(input,select,textarea):enabled:hover){--tum-ui-input-group-border-color: var(--tumaet-ui-control-border-hover-color)}:host:has(:is(input,select,textarea):enabled:focus){--tum-ui-input-group-border-color: var(--tumaet-ui-focus-color)}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiInputGroupComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-input-group",
			template: "<ng-content />",
			host: { class: "tum-ui-input-group" },
			changeDetection: ChangeDetectionStrategy.OnPush,
			styles: [":host{display:flex;align-items:stretch;--tum-ui-input-group-border-color: var(--tumaet-ui-control-border-color)}:host:has(:is(input,select,textarea):enabled:hover){--tum-ui-input-group-border-color: var(--tumaet-ui-control-border-hover-color)}:host:has(:is(input,select,textarea):enabled:focus){--tum-ui-input-group-border-color: var(--tumaet-ui-focus-color)}\n"]
		}]
	}]
});
const INPUT_BASE = "tum-ui-input tum:box-border tum:appearance-none tum:rounded-md tum:border tum:bg-control-background tum:text-text tum:shadow-xs tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2 tum:transition-colors tum:duration-200 tum:placeholder:text-muted tum:disabled:opacity-100 tum:disabled:bg-disabled-background tum:disabled:text-disabled";
const INPUT_BORDER = "tum:border-control-border tum:enabled:hover:border-control-border-hover tum:enabled:focus:border-focus";
const INPUT_BORDER_INVALID = "tum:border-state-danger";
const INPUT_SIZE = {
	small: "tum:text-sm tum:px-2.5 tum:py-1.5",
	large: "tum:text-lg tum:px-3.5 tum:py-2.5"
};
const INPUT_SIZE_NORMAL = "tum:text-base tum:px-3 tum:py-2";
function tumUiInputClasses(options) {
	const size = options.size ? INPUT_SIZE[options.size] : INPUT_SIZE_NORMAL;
	const border = options.invalid ? INPUT_BORDER_INVALID : INPUT_BORDER;
	return `${INPUT_BASE} ${size} ${border}`;
}
let nextInputId = 0;
var TumUiInputDirective = class TumUiInputDirective {
	elementRef = inject(ElementRef);
	formField = inject(TUM_UI_FORM_FIELD, { optional: true });
	tumUiInputSize = input(void 0, ...ngDevMode ? [{ debugName: "tumUiInputSize" }] : /* istanbul ignore next */ []);
	tumUiInputInvalid = input(false, {
		...ngDevMode ? { debugName: "tumUiInputInvalid" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	tumUiInputId = input(...ngDevMode ? [void 0, { debugName: "tumUiInputId" }] : /* istanbul ignore next */ []);
	tumUiInputDescribedBy = input(...ngDevMode ? [void 0, { debugName: "tumUiInputDescribedBy" }] : /* istanbul ignore next */ []);
	staticId = this.elementRef.nativeElement.getAttribute("id");
	staticDescribedBy = this.elementRef.nativeElement.getAttribute("aria-describedby");
	fallbackId = `tum-ui-input-${nextInputId++}`;
	ownId = computed(() => this.tumUiInputId() ?? this.staticId ?? void 0, ...ngDevMode ? [{ debugName: "ownId" }] : /* istanbul ignore next */ []);
	controlId = computed(() => this.formField?.explicitControlId() ?? this.ownId() ?? this.formField?.labelTargetId() ?? this.fallbackId, ...ngDevMode ? [{ debugName: "controlId" }] : /* istanbul ignore next */ []);
	describedBy = computed(() => {
		const ids = [
			this.tumUiInputDescribedBy(),
			this.staticDescribedBy,
			this.formField?.describedBy()
		].filter(Boolean);
		return ids.length ? ids.join(" ") : null;
	}, ...ngDevMode ? [{ debugName: "describedBy" }] : /* istanbul ignore next */ []);
	isInvalid = computed(() => this.tumUiInputInvalid() || (this.formField?.invalid() ?? false), ...ngDevMode ? [{ debugName: "isInvalid" }] : /* istanbul ignore next */ []);
	hostClasses = computed(() => tumUiInputClasses({
		size: this.tumUiInputSize(),
		invalid: this.isInvalid()
	}), ...ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []);
	constructor() {
		effect(() => {
			const ownId = this.ownId();
			if (ownId) this.formField?.adoptControlId(ownId);
		});
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiInputDirective,
		deps: [],
		target: i0.ɵɵFactoryTarget.Directive
	});
	static ɵdir = i0.ɵɵngDeclareDirective({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiInputDirective,
		isStandalone: true,
		selector: "input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]",
		inputs: {
			tumUiInputSize: {
				classPropertyName: "tumUiInputSize",
				publicName: "tumUiInputSize",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			tumUiInputInvalid: {
				classPropertyName: "tumUiInputInvalid",
				publicName: "tumUiInputInvalid",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			tumUiInputId: {
				classPropertyName: "tumUiInputId",
				publicName: "tumUiInputId",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			tumUiInputDescribedBy: {
				classPropertyName: "tumUiInputDescribedBy",
				publicName: "tumUiInputDescribedBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: { properties: {
			"class": "hostClasses()",
			"attr.id": "controlId()",
			"attr.aria-describedby": "describedBy()",
			"attr.aria-invalid": "isInvalid() || null"
		} },
		ngImport: i0
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiInputDirective,
	decorators: [{
		type: Directive,
		args: [{
			selector: "input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]",
			host: {
				"[class]": "hostClasses()",
				"[attr.id]": "controlId()",
				"[attr.aria-describedby]": "describedBy()",
				"[attr.aria-invalid]": "isInvalid() || null"
			}
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		tumUiInputSize: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiInputSize",
				required: false
			}]
		}],
		tumUiInputInvalid: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiInputInvalid",
				required: false
			}]
		}],
		tumUiInputId: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiInputId",
				required: false
			}]
		}],
		tumUiInputDescribedBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiInputDescribedBy",
				required: false
			}]
		}]
	}
});
var TumUiInputNumberComponent = class TumUiInputNumberComponent {
	min = input(...ngDevMode ? [void 0, { debugName: "min" }] : /* istanbul ignore next */ []);
	max = input(...ngDevMode ? [void 0, { debugName: "max" }] : /* istanbul ignore next */ []);
	step = input(1, {
		...ngDevMode ? { debugName: "step" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	showButtons = input(false, {
		...ngDevMode ? { debugName: "showButtons" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	prefix = input(...ngDevMode ? [void 0, { debugName: "prefix" }] : /* istanbul ignore next */ []);
	suffix = input(...ngDevMode ? [void 0, { debugName: "suffix" }] : /* istanbul ignore next */ []);
	placeholder = input(...ngDevMode ? [void 0, { debugName: "placeholder" }] : /* istanbul ignore next */ []);
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	invalid = input(false, {
		...ngDevMode ? { debugName: "invalid" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	fluid = input(false, {
		...ngDevMode ? { debugName: "fluid" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	useGrouping = input(true, {
		...ngDevMode ? { debugName: "useGrouping" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	maxFractionDigits = input(0, {
		...ngDevMode ? { debugName: "maxFractionDigits" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	locale = input(...ngDevMode ? [void 0, { debugName: "locale" }] : /* istanbul ignore next */ []);
	inputId = input(...ngDevMode ? [void 0, { debugName: "inputId" }] : /* istanbul ignore next */ []);
	name = input(...ngDevMode ? [void 0, { debugName: "name" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	ariaLabelledBy = input(...ngDevMode ? [void 0, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []);
	ariaDescribedBy = input(...ngDevMode ? [void 0, { debugName: "ariaDescribedBy" }] : /* istanbul ignore next */ []);
	inputRef = viewChild.required("inputEl", ...ngDevMode ? [{ debugName: "inputRef" }] : /* istanbul ignore next */ []);
	cvaValue = signal(void 0, ...ngDevMode ? [{ debugName: "cvaValue" }] : /* istanbul ignore next */ []);
	cvaDisabled = signal(false, ...ngDevMode ? [{ debugName: "cvaDisabled" }] : /* istanbul ignore next */ []);
	isDisabled = computed(() => this.disabled() || this.cvaDisabled(), ...ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []);
	faChevronUp = faChevronUp;
	faChevronDown = faChevronDown;
	numberFormatter = computed(() => new Intl.NumberFormat(this.locale(), {
		useGrouping: this.useGrouping(),
		maximumFractionDigits: this.maxFractionDigits()
	}), ...ngDevMode ? [{ debugName: "numberFormatter" }] : /* istanbul ignore next */ []);
	localeNumberSyntax = computed(() => this.createLocaleNumberSyntax(), ...ngDevMode ? [{ debugName: "localeNumberSyntax" }] : /* istanbul ignore next */ []);
	formattedValue = computed(() => this.format(this.cvaValue()), ...ngDevMode ? [{ debugName: "formattedValue" }] : /* istanbul ignore next */ []);
	displayText = linkedSignal(() => this.formattedValue(), ...ngDevMode ? [{ debugName: "displayText" }] : /* istanbul ignore next */ []);
	ariaValueNow = computed(() => this.cvaValue(), ...ngDevMode ? [{ debugName: "ariaValueNow" }] : /* istanbul ignore next */ []);
	ariaValueText = computed(() => this.formattedValue() || null, ...ngDevMode ? [{ debugName: "ariaValueText" }] : /* istanbul ignore next */ []);
	onModelChange = () => {};
	onModelTouched = () => {};
	format(value) {
		if (value === void 0 || value === null || Number.isNaN(value)) return "";
		const formatted = this.numberFormatter().format(value);
		return `${this.prefix() ?? ""}${formatted}${this.suffix() ?? ""}`;
	}
	createLocaleNumberSyntax() {
		const formatter = new Intl.NumberFormat(this.locale(), {
			useGrouping: true,
			maximumFractionDigits: 0
		});
		const digitBySymbol = /* @__PURE__ */ new Map();
		for (let digit = 0; digit <= 9; digit++) {
			const localizedDigit = formatter.formatToParts(digit).filter((part) => part.type === "integer").map((part) => part.value).join("");
			const asciiDigit = String(digit);
			digitBySymbol.set(asciiDigit, asciiDigit);
			digitBySymbol.set(localizedDigit, asciiDigit);
		}
		const digitSymbols = [...digitBySymbol.keys()].sort((left, right) => right.length - left.length);
		const groupSeparators = [...new Set(formatter.formatToParts(123456789).filter((part) => part.type === "group").map((part) => part.value))].sort((left, right) => right.length - left.length);
		const minusSigns = [.../* @__PURE__ */ new Set(["-", ...formatter.formatToParts(-1).filter((part) => part.type === "minusSign").map((part) => part.value)])].sort((left, right) => right.length - left.length);
		return {
			digitBySymbol,
			digitSymbols,
			groupSeparators,
			decimalSeparators: [...new Set(new Intl.NumberFormat(this.locale(), { minimumFractionDigits: 1 }).formatToParts(1.1).filter((part) => part.type === "decimal").map((part) => part.value))].sort((left, right) => right.length - left.length),
			minusSigns
		};
	}
	stripAffixes(text) {
		let body = text;
		const prefix = this.prefix();
		const suffix = this.suffix();
		if (prefix && body.startsWith(prefix)) body = body.slice(prefix.length);
		if (suffix && body.endsWith(suffix)) body = body.slice(0, body.length - suffix.length);
		return body;
	}
	parse(text) {
		const body = this.stripAffixes(text);
		const syntax = this.localeNumberSyntax();
		const maxFractionDigits = this.maxFractionDigits();
		let integerDigits = "";
		let fractionDigits = "";
		let negative = false;
		let inFraction = false;
		for (let index = 0; index < body.length;) {
			const digit = this.matchAt(body, index, syntax.digitSymbols);
			if (digit) {
				if (inFraction) {
					if (fractionDigits.length < maxFractionDigits) fractionDigits += syntax.digitBySymbol.get(digit);
				} else integerDigits += syntax.digitBySymbol.get(digit);
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
			index += groupSeparator?.length ?? (body.codePointAt(index) > 65535 ? 2 : 1);
		}
		if (integerDigits === "" && fractionDigits === "") return;
		const parsed = Number.parseFloat(`${negative ? "-" : ""}${integerDigits || "0"}.${fractionDigits || "0"}`);
		return Number.isNaN(parsed) ? void 0 : parsed;
	}
	fractionEntryInProgress(text) {
		if (this.maxFractionDigits() === 0) return false;
		const body = this.stripAffixes(text);
		const syntax = this.localeNumberSyntax();
		for (let index = 0; index < body.length;) {
			const decimalSeparator = this.matchAt(body, index, syntax.decimalSeparators);
			if (decimalSeparator) {
				const fraction = this.toAsciiDigits(body.slice(index + decimalSeparator.length));
				return fraction === "" || fraction.endsWith("0");
			}
			index += body.codePointAt(index) > 65535 ? 2 : 1;
		}
		return false;
	}
	clamp(value) {
		const min = this.min();
		const max = this.max();
		let clamped = value;
		if (min !== void 0 && clamped < min) clamped = min;
		if (max !== void 0 && clamped > max) clamped = max;
		return clamped;
	}
	matchAt(text, index, candidates) {
		return candidates.find((candidate) => candidate.length > 0 && text.startsWith(candidate, index));
	}
	toAsciiDigits(text) {
		const syntax = this.localeNumberSyntax();
		let digits = "";
		for (let index = 0; index < text.length;) {
			const digit = this.matchAt(text, index, syntax.digitSymbols);
			if (digit) {
				digits += syntax.digitBySymbol.get(digit);
				index += digit.length;
				continue;
			}
			index += text.codePointAt(index) > 65535 ? 2 : 1;
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
			index += text.codePointAt(index) > 65535 ? 2 : 1;
		}
		return count;
	}
	caretAfterDigits(text, digitCount) {
		const start = (this.prefix() ?? "").length;
		if (digitCount <= 0) return start;
		const end = text.length - (this.suffix() ?? "").length;
		const digitSymbols = this.localeNumberSyntax().digitSymbols;
		let seen = 0;
		for (let index = start; index < end;) {
			const digit = this.matchAt(text, index, digitSymbols);
			if (digit) {
				seen++;
				if (seen === digitCount) return index + digit.length;
			}
			index += digit?.length ?? (text.codePointAt(index) > 65535 ? 2 : 1);
		}
		return end;
	}
	onInput(event) {
		const el = event.target;
		const caret = el.selectionStart ?? el.value.length;
		const prefixLength = (this.prefix() ?? "").length;
		const digitsBeforeCaret = this.digitCount(el.value.slice(prefixLength, caret));
		const parsed = this.parse(el.value);
		this.cvaValue.set(parsed);
		this.onModelChange(parsed);
		const syntax = this.localeNumberSyntax();
		if (parsed === void 0 && syntax.minusSigns.some((minusSign) => el.value.includes(minusSign)) && this.digitCount(el.value) === 0) {
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
		if (this.isDisabled()) return;
		const base = this.cvaValue() ?? 0;
		const stepped = Number((base + delta).toFixed(this.maxFractionDigits()));
		const next = this.clamp(stepped);
		this.cvaValue.set(next);
		this.displayText.set(this.format(next));
		this.onModelChange(next);
		this.inputRef().nativeElement.focus();
	}
	onKeydown(event) {
		if (event.key === "ArrowUp") {
			event.preventDefault();
			this.onStep(this.step());
		} else if (event.key === "ArrowDown") {
			event.preventDefault();
			this.onStep(-this.step());
		}
	}
	onBlurHandler() {
		const value = this.cvaValue();
		if (value !== void 0) {
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
		this.cvaValue.set(value ?? void 0);
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
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiInputNumberComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiInputNumberComponent,
		isStandalone: true,
		selector: "tum-ui-input-number",
		inputs: {
			min: {
				classPropertyName: "min",
				publicName: "min",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			max: {
				classPropertyName: "max",
				publicName: "max",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			step: {
				classPropertyName: "step",
				publicName: "step",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			showButtons: {
				classPropertyName: "showButtons",
				publicName: "showButtons",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			prefix: {
				classPropertyName: "prefix",
				publicName: "prefix",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			suffix: {
				classPropertyName: "suffix",
				publicName: "suffix",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			placeholder: {
				classPropertyName: "placeholder",
				publicName: "placeholder",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			invalid: {
				classPropertyName: "invalid",
				publicName: "invalid",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			fluid: {
				classPropertyName: "fluid",
				publicName: "fluid",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			useGrouping: {
				classPropertyName: "useGrouping",
				publicName: "useGrouping",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			maxFractionDigits: {
				classPropertyName: "maxFractionDigits",
				publicName: "maxFractionDigits",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			locale: {
				classPropertyName: "locale",
				publicName: "locale",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			inputId: {
				classPropertyName: "inputId",
				publicName: "inputId",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			name: {
				classPropertyName: "name",
				publicName: "name",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabelledBy: {
				classPropertyName: "ariaLabelledBy",
				publicName: "ariaLabelledBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaDescribedBy: {
				classPropertyName: "ariaDescribedBy",
				publicName: "ariaDescribedBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: {
			properties: {
				"class.tum-ui-input-number-fluid": "fluid()",
				"class.tum-ui-input-number-buttons": "showButtons()"
			},
			classAttribute: "tum-ui-input-number"
		},
		providers: [{
			provide: NG_VALUE_ACCESSOR,
			useExisting: forwardRef(() => TumUiInputNumberComponent),
			multi: true
		}],
		viewQueries: [{
			propertyName: "inputRef",
			first: true,
			predicate: ["inputEl"],
			descendants: true,
			isSignal: true
		}],
		ngImport: i0,
		template: "<input\n    #inputEl\n    tumUiInput\n    type=\"text\"\n    [attr.inputmode]=\"maxFractionDigits() > 0 ? 'decimal' : 'numeric'\"\n    class=\"tum-ui-input-number-input\"\n    role=\"spinbutton\"\n    [value]=\"displayText()\"\n    [tumUiInputId]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [attr.placeholder]=\"placeholder()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    [attr.aria-labelledby]=\"ariaLabelledBy()\"\n    [tumUiInputDescribedBy]=\"ariaDescribedBy()\"\n    [attr.aria-valuenow]=\"ariaValueNow()\"\n    [attr.aria-valuemin]=\"min() ?? null\"\n    [attr.aria-valuemax]=\"max() ?? null\"\n    [attr.aria-valuetext]=\"ariaValueText()\"\n    [disabled]=\"isDisabled()\"\n    [tumUiInputInvalid]=\"invalid()\"\n    (input)=\"onInput($event)\"\n    (blur)=\"onBlurHandler()\"\n    (keydown)=\"onKeydown($event)\"\n/>\n@if (showButtons()) {\n    <span class=\"tum-ui-input-number-button-group\">\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            aria-hidden=\"true\"\n            class=\"tum-ui-input-number-button tum-ui-input-number-increment tum:text-muted tum:hover:bg-hover-background tum:hover:text-text-hover\"\n            [disabled]=\"isDisabled()\"\n            (mousedown)=\"$event.preventDefault()\"\n            (click)=\"onStep(step())\"\n        >\n            <fa-icon [icon]=\"faChevronUp\" />\n        </button>\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            aria-hidden=\"true\"\n            class=\"tum-ui-input-number-button tum-ui-input-number-decrement tum:text-muted tum:hover:bg-hover-background tum:hover:text-text-hover\"\n            [disabled]=\"isDisabled()\"\n            (mousedown)=\"$event.preventDefault()\"\n            (click)=\"onStep(-step())\"\n        >\n            <fa-icon [icon]=\"faChevronDown\" />\n        </button>\n    </span>\n}\n",
		styles: [":host{display:inline-flex;position:relative}:host(.tum-ui-input-number-fluid){display:flex;width:100%}.tum-ui-input-number-input{flex:1 1 auto}:host(.tum-ui-input-number-fluid) .tum-ui-input-number-input{width:1%}:host(.tum-ui-input-number-buttons) .tum-ui-input-number-input{padding-inline-end:calc(var(--tumaet-ui-spacing) * 13)}.tum-ui-input-number-button-group{display:flex;flex-direction:column;position:absolute;inset-block-start:1px;inset-inline-end:1px;height:calc(100% - 2px);z-index:1}.tum-ui-input-number-button{display:flex;align-items:center;justify-content:center;flex:1 1 auto;width:calc(var(--tumaet-ui-spacing) * 10);padding:0;border:0;background:transparent;cursor:pointer;transition:background .2s,color .2s}.tum-ui-input-number-button:disabled{cursor:auto}.tum-ui-input-number-button fa-icon{display:flex;align-items:center;justify-content:center}.tum-ui-input-number-increment{border-start-end-radius:calc(var(--tumaet-ui-radius-md) - 1px)}.tum-ui-input-number-decrement{border-end-end-radius:calc(var(--tumaet-ui-radius-md) - 1px)}\n"],
		dependencies: [{
			kind: "directive",
			type: TumUiInputDirective,
			selector: "input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]",
			inputs: [
				"tumUiInputSize",
				"tumUiInputInvalid",
				"tumUiInputId",
				"tumUiInputDescribedBy"
			]
		}, {
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiInputNumberComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-input-number",
			imports: [TumUiInputDirective, FaIconComponent],
			host: {
				class: "tum-ui-input-number",
				"[class.tum-ui-input-number-fluid]": "fluid()",
				"[class.tum-ui-input-number-buttons]": "showButtons()"
			},
			providers: [{
				provide: NG_VALUE_ACCESSOR,
				useExisting: forwardRef(() => TumUiInputNumberComponent),
				multi: true
			}],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<input\n    #inputEl\n    tumUiInput\n    type=\"text\"\n    [attr.inputmode]=\"maxFractionDigits() > 0 ? 'decimal' : 'numeric'\"\n    class=\"tum-ui-input-number-input\"\n    role=\"spinbutton\"\n    [value]=\"displayText()\"\n    [tumUiInputId]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [attr.placeholder]=\"placeholder()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    [attr.aria-labelledby]=\"ariaLabelledBy()\"\n    [tumUiInputDescribedBy]=\"ariaDescribedBy()\"\n    [attr.aria-valuenow]=\"ariaValueNow()\"\n    [attr.aria-valuemin]=\"min() ?? null\"\n    [attr.aria-valuemax]=\"max() ?? null\"\n    [attr.aria-valuetext]=\"ariaValueText()\"\n    [disabled]=\"isDisabled()\"\n    [tumUiInputInvalid]=\"invalid()\"\n    (input)=\"onInput($event)\"\n    (blur)=\"onBlurHandler()\"\n    (keydown)=\"onKeydown($event)\"\n/>\n@if (showButtons()) {\n    <span class=\"tum-ui-input-number-button-group\">\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            aria-hidden=\"true\"\n            class=\"tum-ui-input-number-button tum-ui-input-number-increment tum:text-muted tum:hover:bg-hover-background tum:hover:text-text-hover\"\n            [disabled]=\"isDisabled()\"\n            (mousedown)=\"$event.preventDefault()\"\n            (click)=\"onStep(step())\"\n        >\n            <fa-icon [icon]=\"faChevronUp\" />\n        </button>\n        <button\n            type=\"button\"\n            tabindex=\"-1\"\n            aria-hidden=\"true\"\n            class=\"tum-ui-input-number-button tum-ui-input-number-decrement tum:text-muted tum:hover:bg-hover-background tum:hover:text-text-hover\"\n            [disabled]=\"isDisabled()\"\n            (mousedown)=\"$event.preventDefault()\"\n            (click)=\"onStep(-step())\"\n        >\n            <fa-icon [icon]=\"faChevronDown\" />\n        </button>\n    </span>\n}\n",
			styles: [":host{display:inline-flex;position:relative}:host(.tum-ui-input-number-fluid){display:flex;width:100%}.tum-ui-input-number-input{flex:1 1 auto}:host(.tum-ui-input-number-fluid) .tum-ui-input-number-input{width:1%}:host(.tum-ui-input-number-buttons) .tum-ui-input-number-input{padding-inline-end:calc(var(--tumaet-ui-spacing) * 13)}.tum-ui-input-number-button-group{display:flex;flex-direction:column;position:absolute;inset-block-start:1px;inset-inline-end:1px;height:calc(100% - 2px);z-index:1}.tum-ui-input-number-button{display:flex;align-items:center;justify-content:center;flex:1 1 auto;width:calc(var(--tumaet-ui-spacing) * 10);padding:0;border:0;background:transparent;cursor:pointer;transition:background .2s,color .2s}.tum-ui-input-number-button:disabled{cursor:auto}.tum-ui-input-number-button fa-icon{display:flex;align-items:center;justify-content:center}.tum-ui-input-number-increment{border-start-end-radius:calc(var(--tumaet-ui-radius-md) - 1px)}.tum-ui-input-number-decrement{border-end-end-radius:calc(var(--tumaet-ui-radius-md) - 1px)}\n"]
		}]
	}],
	propDecorators: {
		min: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "min",
				required: false
			}]
		}],
		max: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "max",
				required: false
			}]
		}],
		step: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "step",
				required: false
			}]
		}],
		showButtons: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "showButtons",
				required: false
			}]
		}],
		prefix: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "prefix",
				required: false
			}]
		}],
		suffix: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "suffix",
				required: false
			}]
		}],
		placeholder: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "placeholder",
				required: false
			}]
		}],
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		invalid: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "invalid",
				required: false
			}]
		}],
		fluid: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "fluid",
				required: false
			}]
		}],
		useGrouping: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "useGrouping",
				required: false
			}]
		}],
		maxFractionDigits: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "maxFractionDigits",
				required: false
			}]
		}],
		locale: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "locale",
				required: false
			}]
		}],
		inputId: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "inputId",
				required: false
			}]
		}],
		name: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "name",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		ariaLabelledBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabelledBy",
				required: false
			}]
		}],
		ariaDescribedBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaDescribedBy",
				required: false
			}]
		}],
		inputRef: [{
			type: i0.ViewChild,
			args: ["inputEl", { isSignal: true }]
		}]
	}
});
const BASE$1 = "tum-ui-list-item-action tum:flex tum:w-full tum:items-center tum:gap-2 tum:border-0 tum:px-4 tum:py-3 tum:text-start tum:text-base tum:no-underline tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:-outline-offset-2";
const INACTIVE = "tum:cursor-pointer tum:bg-transparent tum:text-text tum:hover:bg-hover-background tum:hover:text-text-hover";
const ACTIVE = "tum:cursor-pointer tum:bg-highlight-background tum:font-medium tum:text-highlight";
var TumUiListItemActionDirective = class TumUiListItemActionDirective {
	active = input(false, {
		...ngDevMode ? { debugName: "active" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	hostClasses = computed(() => `${BASE$1} ${this.active() ? ACTIVE : INACTIVE}`, ...ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiListItemActionDirective,
		deps: [],
		target: i0.ɵɵFactoryTarget.Directive
	});
	static ɵdir = i0.ɵɵngDeclareDirective({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiListItemActionDirective,
		isStandalone: true,
		selector: "a[tumUiListItemAction], button[tumUiListItemAction]",
		inputs: { active: {
			classPropertyName: "active",
			publicName: "active",
			isSignal: true,
			isRequired: false,
			transformFunction: null
		} },
		host: { properties: {
			"class": "hostClasses()",
			"attr.aria-current": "active() ? \"page\" : null"
		} },
		ngImport: i0
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiListItemActionDirective,
	decorators: [{
		type: Directive,
		args: [{
			selector: "a[tumUiListItemAction], button[tumUiListItemAction]",
			host: {
				"[class]": "hostClasses()",
				"[attr.aria-current]": "active() ? \"page\" : null"
			}
		}]
	}],
	propDecorators: { active: [{
		type: i0.Input,
		args: [{
			isSignal: true,
			alias: "active",
			required: false
		}]
	}] }
});
const BASE = "tum-ui-list-item tum:flex tum:min-w-0 tum:border-t tum:border-border tum:text-text tum:first:border-t-0";
const STACKED = "tum:flex-col";
const INLINE = "tum:flex-row tum:items-center tum:justify-between tum:gap-3";
const OWN_PADDING = "tum:px-4 tum:py-3";
var TumUiListItemDirective = class TumUiListItemDirective {
	inline = input(false, {
		...ngDevMode ? { debugName: "inline" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	action = contentChild(TumUiListItemActionDirective, ...ngDevMode ? [{ debugName: "action" }] : /* istanbul ignore next */ []);
	hostClasses = computed(() => {
		const direction = this.inline() ? INLINE : STACKED;
		return this.action() ? `${BASE} ${direction}` : `${BASE} ${direction} ${OWN_PADDING}`;
	}, ...ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiListItemDirective,
		deps: [],
		target: i0.ɵɵFactoryTarget.Directive
	});
	static ɵdir = i0.ɵɵngDeclareDirective({
		minVersion: "17.2.0",
		version: "22.2.0",
		type: TumUiListItemDirective,
		isStandalone: true,
		selector: "li[tumUiListItem]",
		inputs: { inline: {
			classPropertyName: "inline",
			publicName: "inline",
			isSignal: true,
			isRequired: false,
			transformFunction: null
		} },
		host: { properties: { "class": "hostClasses()" } },
		queries: [{
			propertyName: "action",
			first: true,
			predicate: TumUiListItemActionDirective,
			descendants: true,
			isSignal: true
		}],
		ngImport: i0
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiListItemDirective,
	decorators: [{
		type: Directive,
		args: [{
			selector: "li[tumUiListItem]",
			host: { "[class]": "hostClasses()" }
		}]
	}],
	propDecorators: {
		inline: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "inline",
				required: false
			}]
		}],
		action: [{
			type: i0.ContentChild,
			args: [i0.forwardRef(() => TumUiListItemActionDirective), { isSignal: true }]
		}]
	}
});
var TumUiListComponent = class TumUiListComponent {
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	ariaLabelledBy = input(...ngDevMode ? [void 0, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiListComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiListComponent,
		isStandalone: true,
		selector: "tum-ui-list",
		inputs: {
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabelledBy: {
				classPropertyName: "ariaLabelledBy",
				publicName: "ariaLabelledBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: { classAttribute: "tum-ui-list" },
		ngImport: i0,
		template: "<ul role=\"list\" class=\"tum-ui-list-items tum:m-0 tum:flex tum:list-none tum:flex-col tum:p-0\" [attr.aria-label]=\"ariaLabel()\" [attr.aria-labelledby]=\"ariaLabelledBy()\">\n    <ng-content />\n</ul>\n",
		styles: [":host{display:block}.tum-ui-list-items{border:1px solid var(--tumaet-ui-border-color);border-radius:var(--tumaet-ui-radius-md);overflow:hidden}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiListComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-list",
			host: { class: "tum-ui-list" },
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<ul role=\"list\" class=\"tum-ui-list-items tum:m-0 tum:flex tum:list-none tum:flex-col tum:p-0\" [attr.aria-label]=\"ariaLabel()\" [attr.aria-labelledby]=\"ariaLabelledBy()\">\n    <ng-content />\n</ul>\n",
			styles: [":host{display:block}.tum-ui-list-items{border:1px solid var(--tumaet-ui-border-color);border-radius:var(--tumaet-ui-radius-md);overflow:hidden}\n"]
		}]
	}],
	propDecorators: {
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		ariaLabelledBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabelledBy",
				required: false
			}]
		}]
	}
});
var TumUiMenuItemDirective = class TumUiMenuItemDirective {
	menuItem = inject(MenuItem);
	element = inject(ElementRef).nativeElement;
	triggered = output();
	isActive() {
		return this.menuItem.active();
	}
	contains(target) {
		return target instanceof Node && this.element.contains(target);
	}
	syncSearchTerm() {
		this.menuItem.searchTerm.set(this.element.textContent?.trim() ?? "");
	}
	emitTriggered() {
		this.triggered.emit();
	}
	onEnter(event) {
		if (this.element instanceof HTMLAnchorElement && this.element.hasAttribute("href") && !this.menuItem.disabled()) event.stopPropagation();
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiMenuItemDirective,
		deps: [],
		target: i0.ɵɵFactoryTarget.Directive
	});
	static ɵdir = i0.ɵɵngDeclareDirective({
		minVersion: "14.0.0",
		version: "22.2.0",
		type: TumUiMenuItemDirective,
		isStandalone: true,
		selector: "[tumUiMenuItem]",
		outputs: { triggered: "triggered" },
		host: {
			attributes: { "ngMenuItem": "" },
			listeners: { "keydown.enter": "onEnter($event)" },
			classAttribute: "tum-ui-menu-item tum:flex tum:cursor-pointer tum:items-center tum:gap-2 tum:border-0 tum:bg-transparent tum:px-3 tum:py-2 tum:text-start tum:text-base tum:text-text tum:no-underline tum:hover:bg-hover-background tum:hover:text-text-hover tum:focus-visible:bg-highlight-focus-background tum:focus-visible:text-highlight tum:focus-visible:outline-none tum:aria-disabled:pointer-events-none tum:aria-disabled:cursor-default tum:aria-disabled:text-disabled"
		},
		hostDirectives: [{
			directive: i1$4.MenuItem,
			inputs: ["disabled", "disabled"]
		}],
		ngImport: i0
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiMenuItemDirective,
	decorators: [{
		type: Directive,
		args: [{
			selector: "[tumUiMenuItem]",
			hostDirectives: [{
				directive: MenuItem,
				inputs: ["disabled"]
			}],
			host: {
				ngMenuItem: "",
				class: "tum-ui-menu-item tum:flex tum:cursor-pointer tum:items-center tum:gap-2 tum:border-0 tum:bg-transparent tum:px-3 tum:py-2 tum:text-start tum:text-base tum:text-text tum:no-underline tum:hover:bg-hover-background tum:hover:text-text-hover tum:focus-visible:bg-highlight-focus-background tum:focus-visible:text-highlight tum:focus-visible:outline-none tum:aria-disabled:pointer-events-none tum:aria-disabled:cursor-default tum:aria-disabled:text-disabled",
				"(keydown.enter)": "onEnter($event)"
			}
		}]
	}],
	propDecorators: { triggered: [{
		type: i0.Output,
		args: ["triggered"]
	}] }
});
const TUM_UI_MENU_TRIGGER = new InjectionToken("TUM_UI_MENU_TRIGGER");
var TumUiMenuTriggerDirective = class TumUiMenuTriggerDirective extends MenuTrigger {
	injector = inject(Injector);
	viewContainerRef = inject(ViewContainerRef);
	directionality = inject(Directionality, { optional: true });
	softDisabled = input(false, {
		...ngDevMode ? { debugName: "softDisabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	menuTemplate = input.required({
		...ngDevMode ? { debugName: "menuTemplate" } : /* istanbul ignore next */ {},
		alias: "tumUiMenuTrigger"
	});
	tumUiMenuPosition = input(void 0, ...ngDevMode ? [{ debugName: "tumUiMenuPosition" }] : /* istanbul ignore next */ []);
	menuOpened = output();
	menuClosed = output();
	renderedMenu = signal(void 0, ...ngDevMode ? [{ debugName: "renderedMenu" }] : /* istanbul ignore next */ []);
	menu = this.renderedMenu.asReadonly();
	overlayRef;
	constructor() {
		super();
		const handleKeydown = this._pattern.onKeydown.bind(this._pattern);
		this._pattern.onKeydown = (event) => {
			if (event.key !== "Escape" || this.expanded()) handleKeydown(event);
		};
		effect(() => {
			const expanded = this.expanded();
			untracked(() => expanded ? this.attachOverlay() : this.detachOverlay());
		});
		inject(DestroyRef).onDestroy(() => this.overlayRef?.dispose());
	}
	attachMenu(menu) {
		this.renderedMenu.set(menu);
		return () => {
			if (this.renderedMenu() === menu) this.renderedMenu.set(void 0);
		};
	}
	closeAndFocus() {
		this.close();
		this.element.focus();
	}
	attachOverlay() {
		if (this.overlayRef?.hasAttached()) return;
		const positionStrategy = createFlexibleConnectedPositionStrategy(this.injector, this.element).withLockedPosition().withFlexibleDimensions(false).withPositions(this.tumUiMenuPosition() ?? STANDARD_DROPDOWN_BELOW_POSITIONS);
		if (this.overlayRef) this.overlayRef.updatePositionStrategy(positionStrategy);
		else this.overlayRef = createOverlayRef(this.injector, {
			positionStrategy,
			scrollStrategy: createRepositionScrollStrategy(this.injector),
			direction: this.directionality ?? void 0
		});
		const menuInjector = Injector.create({
			parent: this.injector,
			providers: [{
				provide: TUM_UI_MENU_TRIGGER,
				useValue: this
			}]
		});
		this.overlayRef.attach(new TemplatePortal(this.menuTemplate(), this.viewContainerRef, void 0, menuInjector));
		this.menuOpened.emit();
	}
	detachOverlay() {
		if (this.overlayRef?.hasAttached()) {
			this.overlayRef.detach();
			this.menuClosed.emit();
		}
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiMenuTriggerDirective,
		deps: [],
		target: i0.ɵɵFactoryTarget.Directive
	});
	static ɵdir = i0.ɵɵngDeclareDirective({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiMenuTriggerDirective,
		isStandalone: true,
		selector: "[tumUiMenuTrigger]",
		inputs: {
			softDisabled: {
				classPropertyName: "softDisabled",
				publicName: "softDisabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			menuTemplate: {
				classPropertyName: "menuTemplate",
				publicName: "tumUiMenuTrigger",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			tumUiMenuPosition: {
				classPropertyName: "tumUiMenuPosition",
				publicName: "tumUiMenuPosition",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: {
			menuOpened: "menuOpened",
			menuClosed: "menuClosed"
		},
		host: { properties: { "attr.aria-haspopup": "\"menu\"" } },
		usesInheritance: true,
		ngImport: i0
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiMenuTriggerDirective,
	decorators: [{
		type: Directive,
		args: [{
			selector: "[tumUiMenuTrigger]",
			host: { "[attr.aria-haspopup]": "\"menu\"" }
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		softDisabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "softDisabled",
				required: false
			}]
		}],
		menuTemplate: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiMenuTrigger",
				required: true
			}]
		}],
		tumUiMenuPosition: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiMenuPosition",
				required: false
			}]
		}],
		menuOpened: [{
			type: i0.Output,
			args: ["menuOpened"]
		}],
		menuClosed: [{
			type: i0.Output,
			args: ["menuClosed"]
		}]
	}
});
var TumUiMenuComponent = class TumUiMenuComponent {
	menu = inject(Menu);
	trigger = inject(TUM_UI_MENU_TRIGGER, { optional: true });
	items = contentChildren(TumUiMenuItemDirective, {
		...ngDevMode ? { debugName: "items" } : /* istanbul ignore next */ {},
		descendants: true
	});
	pendingItem;
	constructor() {
		const element = inject(ElementRef).nativeElement;
		const renderer = inject(Renderer2);
		const detach = this.trigger?.attachMenu(this.menu);
		const noteTarget = (event) => {
			if (event instanceof KeyboardEvent) {
				this.pendingItem = this.items().find((item) => item.isActive());
				if (event.key.length === 1) this.items().forEach((item) => item.syncSearchTerm());
			} else this.pendingItem = this.items().find((item) => item.contains(event.target));
		};
		const removeListeners = [renderer.listen(element, "click", noteTarget, { capture: true }), renderer.listen(element, "keydown", noteTarget, { capture: true })];
		this.menu.itemSelected.subscribe(() => {
			const item = this.pendingItem;
			this.pendingItem = void 0;
			item?.emitTriggered();
		});
		inject(DestroyRef).onDestroy(() => {
			detach?.();
			removeListeners.forEach((remove) => remove());
		});
	}
	onKeydown(event) {
		if (event.key === "Tab" && !event.altKey && !event.ctrlKey && !event.metaKey) this.trigger?.closeAndFocus();
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiMenuComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.2.0",
		version: "22.2.0",
		type: TumUiMenuComponent,
		isStandalone: true,
		selector: "tum-ui-menu",
		host: {
			attributes: { "ngMenu": "" },
			listeners: { "keydown": "onKeydown($event)" },
			classAttribute: "tum-ui-menu tum:flex tum:min-w-48 tum:flex-col tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:py-1 tum:text-text tum:shadow-md"
		},
		queries: [{
			propertyName: "items",
			predicate: TumUiMenuItemDirective,
			descendants: true,
			isSignal: true
		}],
		hostDirectives: [{ directive: i1$4.Menu }],
		ngImport: i0,
		template: "<ng-content />",
		isInline: true,
		styles: [":host{box-sizing:border-box;max-height:inherit;overflow:auto}:host:focus-visible{outline:none}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiMenuComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-menu",
			template: "<ng-content />",
			hostDirectives: [Menu],
			host: {
				ngMenu: "",
				class: "tum-ui-menu tum:flex tum:min-w-48 tum:flex-col tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:py-1 tum:text-text tum:shadow-md",
				"(keydown)": "onKeydown($event)"
			},
			changeDetection: ChangeDetectionStrategy.OnPush,
			styles: [":host{box-sizing:border-box;max-height:inherit;overflow:auto}:host:focus-visible{outline:none}\n"]
		}]
	}],
	ctorParameters: () => [],
	propDecorators: { items: [{
		type: i0.ContentChildren,
		args: [i0.forwardRef(() => TumUiMenuItemDirective), {
			descendants: true,
			isSignal: true
		}]
	}] }
});
const MESSAGE_BASE = "tum-ui-message";
const MESSAGE_SEVERITY = {
	info: "",
	success: "",
	warn: "",
	error: "",
	secondary: "tum:bg-hover-background tum:text-text tum:outline-border",
	contrast: "tum:bg-contrast-background tum:text-contrast tum:outline-contrast-background"
};
var TumUiMessageComponent = class TumUiMessageComponent {
	severity = input("info", ...ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []);
	text = input(...ngDevMode ? [void 0, { debugName: "text" }] : /* istanbul ignore next */ []);
	icon = input(...ngDevMode ? [void 0, { debugName: "icon" }] : /* istanbul ignore next */ []);
	messageRole = computed(() => this.severity() === "error" ? "alert" : "status", ...ngDevMode ? [{ debugName: "messageRole" }] : /* istanbul ignore next */ []);
	hostClasses = computed(() => `${MESSAGE_BASE} ${MESSAGE_SEVERITY[this.severity()]}`.trim(), ...ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiMessageComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiMessageComponent,
		isStandalone: true,
		selector: "tum-ui-message",
		inputs: {
			severity: {
				classPropertyName: "severity",
				publicName: "severity",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			text: {
				classPropertyName: "text",
				publicName: "text",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			icon: {
				classPropertyName: "icon",
				publicName: "icon",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: { properties: {
			"attr.role": "messageRole()",
			"class": "hostClasses()",
			"attr.data-severity": "severity()"
		} },
		ngImport: i0,
		template: "<div class=\"tum-ui-message-content\">\n    @if (icon(); as messageIcon) {\n        <fa-icon [icon]=\"messageIcon\" class=\"tum-ui-message-icon\" />\n    }\n    @if (text(); as messageText) {\n        <span class=\"tum-ui-message-text\">{{ messageText }}</span>\n    } @else {\n        <span class=\"tum-ui-message-text\"><ng-content /></span>\n    }\n</div>\n",
		styles: [":host{display:grid;grid-template-rows:1fr;border-radius:var(--tumaet-ui-radius-md);outline-width:1px;outline-style:solid;outline-offset:0;font-size:var(--tumaet-ui-font-size-base);font-weight:500}.tum-ui-message-content{display:flex;align-items:center;min-height:0;padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 3);gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-message-icon{flex-shrink:0;font-size:var(--tumaet-ui-font-size-lg)}:host[data-severity=info]{background:color-mix(in srgb,var(--tumaet-ui-state-info) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-info) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-info-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-info),transparent 96%)}:host[data-severity=success]{background:color-mix(in srgb,var(--tumaet-ui-state-success) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-success) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-success-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-success),transparent 96%)}:host[data-severity=warn]{background:color-mix(in srgb,var(--tumaet-ui-state-warning) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-warning) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-warning-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-warning),transparent 96%)}:host[data-severity=error]{background:color-mix(in srgb,var(--tumaet-ui-state-danger) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-danger) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-danger-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-danger),transparent 96%)}\n"],
		dependencies: [{
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiMessageComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-message",
			imports: [FaIconComponent],
			host: {
				"[attr.role]": "messageRole()",
				"[class]": "hostClasses()",
				"[attr.data-severity]": "severity()"
			},
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<div class=\"tum-ui-message-content\">\n    @if (icon(); as messageIcon) {\n        <fa-icon [icon]=\"messageIcon\" class=\"tum-ui-message-icon\" />\n    }\n    @if (text(); as messageText) {\n        <span class=\"tum-ui-message-text\">{{ messageText }}</span>\n    } @else {\n        <span class=\"tum-ui-message-text\"><ng-content /></span>\n    }\n</div>\n",
			styles: [":host{display:grid;grid-template-rows:1fr;border-radius:var(--tumaet-ui-radius-md);outline-width:1px;outline-style:solid;outline-offset:0;font-size:var(--tumaet-ui-font-size-base);font-weight:500}.tum-ui-message-content{display:flex;align-items:center;min-height:0;padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 3);gap:calc(var(--tumaet-ui-spacing) * 2)}.tum-ui-message-icon{flex-shrink:0;font-size:var(--tumaet-ui-font-size-lg)}:host[data-severity=info]{background:color-mix(in srgb,var(--tumaet-ui-state-info) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-info) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-info-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-info),transparent 96%)}:host[data-severity=success]{background:color-mix(in srgb,var(--tumaet-ui-state-success) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-success) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-success-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-success),transparent 96%)}:host[data-severity=warn]{background:color-mix(in srgb,var(--tumaet-ui-state-warning) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-warning) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-warning-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-warning),transparent 96%)}:host[data-severity=error]{background:color-mix(in srgb,var(--tumaet-ui-state-danger) 10%,var(--tumaet-ui-content-background));outline-color:color-mix(in srgb,var(--tumaet-ui-state-danger) 40%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-danger-foreground);box-shadow:0 4px 8px color-mix(in srgb,var(--tumaet-ui-state-danger),transparent 96%)}\n"]
		}]
	}],
	propDecorators: {
		severity: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "severity",
				required: false
			}]
		}],
		text: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "text",
				required: false
			}]
		}],
		icon: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "icon",
				required: false
			}]
		}]
	}
});
const PAGE_LINK_SIZE = 5;
const NAV_BUTTON_CLASSES = "tum:inline-flex tum:h-9 tum:w-9 tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:rounded-full tum:border-0 tum:bg-transparent tum:text-sm tum:text-muted tum:transition-colors tum:hover:bg-hover-background tum:disabled:pointer-events-none tum:disabled:opacity-50";
var TumUiPaginatorComponent = class TumUiPaginatorComponent {
	directionality = inject(Directionality);
	destroyRef = inject(DestroyRef);
	direction = signal(this.directionality.value, ...ngDevMode ? [{ debugName: "direction" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	totalRecords = input(0, {
		...ngDevMode ? { debugName: "totalRecords" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	page = input(0, {
		...ngDevMode ? { debugName: "page" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	pageSize = input(50, {
		...ngDevMode ? { debugName: "pageSize" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	pageSizeOptions = input([
		10,
		20,
		50,
		100,
		200
	], ...ngDevMode ? [{ debugName: "pageSizeOptions" }] : /* istanbul ignore next */ []);
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	showCurrentPageReport = input(true, {
		...ngDevMode ? { debugName: "showCurrentPageReport" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	showRowsPerPage = input(true, {
		...ngDevMode ? { debugName: "showRowsPerPage" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	pageChange = output();
	pageSizeChange = output();
	firstPageIcon = computed(() => this.direction() === "rtl" ? faAnglesRight : faAnglesLeft, ...ngDevMode ? [{ debugName: "firstPageIcon" }] : /* istanbul ignore next */ []);
	previousPageIcon = computed(() => this.direction() === "rtl" ? faAngleRight : faAngleLeft, ...ngDevMode ? [{ debugName: "previousPageIcon" }] : /* istanbul ignore next */ []);
	nextPageIcon = computed(() => this.direction() === "rtl" ? faAngleLeft : faAngleRight, ...ngDevMode ? [{ debugName: "nextPageIcon" }] : /* istanbul ignore next */ []);
	lastPageIcon = computed(() => this.direction() === "rtl" ? faAnglesLeft : faAnglesRight, ...ngDevMode ? [{ debugName: "lastPageIcon" }] : /* istanbul ignore next */ []);
	navButtonClasses = NAV_BUTTON_CLASSES;
	selectedPageClasses = NAV_BUTTON_CLASSES.replace("tum:bg-transparent", "tum:bg-primary/15").replace("tum:text-muted", "tum:font-semibold tum:text-accent");
	totalPages = computed(() => Math.max(1, Math.ceil(this.totalRecords() / Math.max(1, this.pageSize()))), ...ngDevMode ? [{ debugName: "totalPages" }] : /* istanbul ignore next */ []);
	clampedPage = computed(() => Math.min(Math.max(0, this.page()), this.totalPages() - 1), ...ngDevMode ? [{ debugName: "clampedPage" }] : /* istanbul ignore next */ []);
	isFirst = computed(() => this.clampedPage() <= 0, ...ngDevMode ? [{ debugName: "isFirst" }] : /* istanbul ignore next */ []);
	isLast = computed(() => this.clampedPage() >= this.totalPages() - 1, ...ngDevMode ? [{ debugName: "isLast" }] : /* istanbul ignore next */ []);
	rangeBegin = computed(() => this.totalRecords() === 0 ? 0 : this.clampedPage() * this.pageSize() + 1, ...ngDevMode ? [{ debugName: "rangeBegin" }] : /* istanbul ignore next */ []);
	rangeEnd = computed(() => Math.min(this.totalRecords(), (this.clampedPage() + 1) * this.pageSize()), ...ngDevMode ? [{ debugName: "rangeEnd" }] : /* istanbul ignore next */ []);
	visiblePages = computed(() => {
		const total = this.totalPages();
		const size = Math.min(PAGE_LINK_SIZE, total);
		let start = Math.max(0, this.clampedPage() - Math.floor(size / 2));
		const end = Math.min(total, start + size);
		start = Math.max(0, end - size);
		return Array.from({ length: end - start }, (_, i) => start + i);
	}, ...ngDevMode ? [{ debugName: "visiblePages" }] : /* istanbul ignore next */ []);
	constructor() {
		const directionChanges = this.directionality.change.subscribe((direction) => this.direction.set(direction));
		this.destroyRef.onDestroy(() => directionChanges.unsubscribe());
	}
	goToPage(target) {
		if (!this.disabled() && target !== this.page() && target >= 0 && target < this.totalPages()) this.pageChange.emit(target);
	}
	goToFirst() {
		if (!this.disabled() && !this.isFirst()) this.pageChange.emit(0);
	}
	goToPrevious() {
		if (!this.disabled() && !this.isFirst()) this.pageChange.emit(this.clampedPage() - 1);
	}
	goToNext() {
		if (!this.disabled() && !this.isLast()) this.pageChange.emit(this.clampedPage() + 1);
	}
	goToLast() {
		if (!this.disabled() && !this.isLast()) this.pageChange.emit(this.totalPages() - 1);
	}
	onPageSizeChange(value) {
		this.pageSizeChange.emit(value);
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiPaginatorComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiPaginatorComponent,
		isStandalone: true,
		selector: "tum-ui-paginator",
		inputs: {
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			totalRecords: {
				classPropertyName: "totalRecords",
				publicName: "totalRecords",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			page: {
				classPropertyName: "page",
				publicName: "page",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			pageSize: {
				classPropertyName: "pageSize",
				publicName: "pageSize",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			pageSizeOptions: {
				classPropertyName: "pageSizeOptions",
				publicName: "pageSizeOptions",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			showCurrentPageReport: {
				classPropertyName: "showCurrentPageReport",
				publicName: "showCurrentPageReport",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			showRowsPerPage: {
				classPropertyName: "showRowsPerPage",
				publicName: "showRowsPerPage",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: {
			pageChange: "pageChange",
			pageSizeChange: "pageSizeChange"
		},
		ngImport: i0,
		template: "<nav\n    [attr.aria-label]=\"ariaLabel() ?? ('tumUi.paginator.ariaLabel' | tumUiTranslate)\"\n    class=\"tum:flex tum:flex-wrap tum:items-center tum:justify-center tum:gap-1 tum:border-t tum:border-border tum:pt-2 tum:text-sm tum:text-muted\"\n>\n    @if (showCurrentPageReport()) {\n        <span class=\"tum:px-2\" aria-live=\"polite\">\n            {{ 'tumUi.paginator.currentPageReport' | tumUiTranslate: { first: rangeBegin(), second: rangeEnd(), total: totalRecords() } }}\n        </span>\n    }\n\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isFirst()\" (click)=\"goToFirst()\" [attr.aria-label]=\"'tumUi.paginator.first' | tumUiTranslate\">\n        <fa-icon [icon]=\"firstPageIcon()\" />\n    </button>\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isFirst()\" (click)=\"goToPrevious()\" [attr.aria-label]=\"'tumUi.paginator.previous' | tumUiTranslate\">\n        <fa-icon [icon]=\"previousPageIcon()\" />\n    </button>\n\n    @for (p of visiblePages(); track p) {\n        <button\n            type=\"button\"\n            [class]=\"p === clampedPage() ? selectedPageClasses : navButtonClasses\"\n            [disabled]=\"disabled()\"\n            (click)=\"goToPage(p)\"\n            [attr.aria-current]=\"p === clampedPage() ? 'page' : null\"\n        >\n            {{ p + 1 }}\n        </button>\n    }\n\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isLast()\" (click)=\"goToNext()\" [attr.aria-label]=\"'tumUi.paginator.next' | tumUiTranslate\">\n        <fa-icon [icon]=\"nextPageIcon()\" />\n    </button>\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isLast()\" (click)=\"goToLast()\" [attr.aria-label]=\"'tumUi.paginator.last' | tumUiTranslate\">\n        <fa-icon [icon]=\"lastPageIcon()\" />\n    </button>\n\n    @if (showRowsPerPage()) {\n        <label class=\"tum:ms-2 tum:flex tum:items-center tum:gap-2 tum:whitespace-nowrap\">\n            <span>{{ 'tumUi.paginator.rowsPerPage' | tumUiTranslate }}</span>\n            <select\n                class=\"tum:box-border tum:h-9 tum:cursor-pointer tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:px-2.5 tum:text-sm tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                [ngModel]=\"pageSize()\"\n                [disabled]=\"disabled()\"\n                (ngModelChange)=\"onPageSizeChange($event)\"\n            >\n                @for (option of pageSizeOptions(); track option) {\n                    <option [ngValue]=\"option\">{{ option }}</option>\n                }\n            </select>\n        </label>\n    }\n</nav>\n",
		dependencies: [
			{
				kind: "component",
				type: FaIconComponent,
				selector: "fa-icon",
				inputs: [
					"icon",
					"title",
					"animation",
					"mask",
					"flip",
					"size",
					"pull",
					"border",
					"inverse",
					"symbol",
					"rotate",
					"fixedWidth",
					"transform",
					"a11yRole"
				],
				outputs: [
					"iconChange",
					"titleChange",
					"animationChange",
					"maskChange",
					"flipChange",
					"sizeChange",
					"pullChange",
					"borderChange",
					"inverseChange",
					"symbolChange",
					"rotateChange",
					"fixedWidthChange",
					"transformChange",
					"a11yRoleChange"
				]
			},
			{
				kind: "ngmodule",
				type: FormsModule
			},
			{
				kind: "directive",
				type: i1$3.NgSelectOption,
				selector: "option",
				inputs: ["ngValue", "value"]
			},
			{
				kind: "directive",
				type: i1$3.ɵNgSelectMultipleOption,
				selector: "option",
				inputs: ["ngValue", "value"]
			},
			{
				kind: "directive",
				type: i1$3.SelectControlValueAccessor,
				selector: "select:not([multiple]):not([ngNoCva])[formControlName],select:not([multiple]):not([ngNoCva])[formControl],select:not([multiple]):not([ngNoCva])[ngModel]",
				inputs: ["compareWith"]
			},
			{
				kind: "directive",
				type: i1$3.NgControlStatus,
				selector: "[formControlName],[ngModel],[formControl]"
			},
			{
				kind: "directive",
				type: i1$3.NgModel,
				selector: "[ngModel]:not([formControlName]):not([formControl])",
				inputs: [
					"name",
					"disabled",
					"ngModel",
					"ngModelOptions"
				],
				outputs: ["ngModelChange"],
				exportAs: ["ngModel"]
			},
			{
				kind: "pipe",
				type: TumUiTranslatePipe,
				name: "tumUiTranslate"
			}
		],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiPaginatorComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-paginator",
			imports: [
				FaIconComponent,
				FormsModule,
				TumUiTranslatePipe
			],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<nav\n    [attr.aria-label]=\"ariaLabel() ?? ('tumUi.paginator.ariaLabel' | tumUiTranslate)\"\n    class=\"tum:flex tum:flex-wrap tum:items-center tum:justify-center tum:gap-1 tum:border-t tum:border-border tum:pt-2 tum:text-sm tum:text-muted\"\n>\n    @if (showCurrentPageReport()) {\n        <span class=\"tum:px-2\" aria-live=\"polite\">\n            {{ 'tumUi.paginator.currentPageReport' | tumUiTranslate: { first: rangeBegin(), second: rangeEnd(), total: totalRecords() } }}\n        </span>\n    }\n\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isFirst()\" (click)=\"goToFirst()\" [attr.aria-label]=\"'tumUi.paginator.first' | tumUiTranslate\">\n        <fa-icon [icon]=\"firstPageIcon()\" />\n    </button>\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isFirst()\" (click)=\"goToPrevious()\" [attr.aria-label]=\"'tumUi.paginator.previous' | tumUiTranslate\">\n        <fa-icon [icon]=\"previousPageIcon()\" />\n    </button>\n\n    @for (p of visiblePages(); track p) {\n        <button\n            type=\"button\"\n            [class]=\"p === clampedPage() ? selectedPageClasses : navButtonClasses\"\n            [disabled]=\"disabled()\"\n            (click)=\"goToPage(p)\"\n            [attr.aria-current]=\"p === clampedPage() ? 'page' : null\"\n        >\n            {{ p + 1 }}\n        </button>\n    }\n\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isLast()\" (click)=\"goToNext()\" [attr.aria-label]=\"'tumUi.paginator.next' | tumUiTranslate\">\n        <fa-icon [icon]=\"nextPageIcon()\" />\n    </button>\n    <button type=\"button\" [class]=\"navButtonClasses\" [disabled]=\"disabled() || isLast()\" (click)=\"goToLast()\" [attr.aria-label]=\"'tumUi.paginator.last' | tumUiTranslate\">\n        <fa-icon [icon]=\"lastPageIcon()\" />\n    </button>\n\n    @if (showRowsPerPage()) {\n        <label class=\"tum:ms-2 tum:flex tum:items-center tum:gap-2 tum:whitespace-nowrap\">\n            <span>{{ 'tumUi.paginator.rowsPerPage' | tumUiTranslate }}</span>\n            <select\n                class=\"tum:box-border tum:h-9 tum:cursor-pointer tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:px-2.5 tum:text-sm tum:text-text tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:outline-offset-2\"\n                [ngModel]=\"pageSize()\"\n                [disabled]=\"disabled()\"\n                (ngModelChange)=\"onPageSizeChange($event)\"\n            >\n                @for (option of pageSizeOptions(); track option) {\n                    <option [ngValue]=\"option\">{{ option }}</option>\n                }\n            </select>\n        </label>\n    }\n</nav>\n"
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		totalRecords: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "totalRecords",
				required: false
			}]
		}],
		page: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "page",
				required: false
			}]
		}],
		pageSize: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "pageSize",
				required: false
			}]
		}],
		pageSizeOptions: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "pageSizeOptions",
				required: false
			}]
		}],
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		showCurrentPageReport: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "showCurrentPageReport",
				required: false
			}]
		}],
		showRowsPerPage: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "showRowsPerPage",
				required: false
			}]
		}],
		pageChange: [{
			type: i0.Output,
			args: ["pageChange"]
		}],
		pageSizeChange: [{
			type: i0.Output,
			args: ["pageSizeChange"]
		}]
	}
});
let nextPanelId = 0;
var TumUiPanelComponent = class TumUiPanelComponent {
	translator = inject(TUM_UI_TRANSLATOR);
	header = input("", ...ngDevMode ? [{ debugName: "header" }] : /* istanbul ignore next */ []);
	toggleable = input(false, {
		...ngDevMode ? { debugName: "toggleable" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	toggleAriaLabel = input(...ngDevMode ? [void 0, { debugName: "toggleAriaLabel" }] : /* istanbul ignore next */ []);
	collapsed = model(false, ...ngDevMode ? [{ debugName: "collapsed" }] : /* istanbul ignore next */ []);
	headerId = `tum-ui-panel-header-${nextPanelId}`;
	contentId = `tum-ui-panel-content-${nextPanelId++}`;
	faChevronDown = faChevronDown;
	faChevronUp = faChevronUp;
	isCollapsed = computed(() => this.toggleable() && this.collapsed(), ...ngDevMode ? [{ debugName: "isCollapsed" }] : /* istanbul ignore next */ []);
	toggleLabelledBy = computed(() => !this.toggleAriaLabel()?.trim() && this.header().trim() ? this.headerId : null, ...ngDevMode ? [{ debugName: "toggleLabelledBy" }] : /* istanbul ignore next */ []);
	toggleLabel = computed(() => {
		const customLabel = this.toggleAriaLabel()?.trim();
		if (customLabel) return customLabel;
		if (this.header().trim()) return null;
		return this.translator.translate(this.collapsed() ? "tumUi.panel.expand" : "tumUi.panel.collapse");
	}, ...ngDevMode ? [{ debugName: "toggleLabel" }] : /* istanbul ignore next */ []);
	toggle() {
		if (this.toggleable()) this.collapsed.update((collapsed) => !collapsed);
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiPanelComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiPanelComponent,
		isStandalone: true,
		selector: "tum-ui-panel",
		inputs: {
			header: {
				classPropertyName: "header",
				publicName: "header",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			toggleable: {
				classPropertyName: "toggleable",
				publicName: "toggleable",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			toggleAriaLabel: {
				classPropertyName: "toggleAriaLabel",
				publicName: "toggleAriaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			collapsed: {
				classPropertyName: "collapsed",
				publicName: "collapsed",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { collapsed: "collapsedChange" },
		host: {
			properties: { "attr.data-collapsed": "toggleable() && collapsed()" },
			classAttribute: "tum-ui-panel tum:border tum:border-border tum:rounded-md tum:bg-content-background tum:text-text"
		},
		ngImport: i0,
		template: "<div class=\"tum-ui-panel-header\" [class.tum-ui-panel-header-toggleable]=\"toggleable()\">\n    <span [id]=\"headerId\" class=\"tum-ui-panel-title\">{{ header() }}<ng-content select=\"[tumUiPanelHeader]\" /></span>\n    @if (toggleable()) {\n        <button\n            type=\"button\"\n            class=\"tum-ui-panel-toggler\"\n            [attr.aria-expanded]=\"!collapsed()\"\n            [attr.aria-controls]=\"contentId\"\n            [attr.aria-label]=\"toggleLabel()\"\n            [attr.aria-labelledby]=\"toggleLabelledBy()\"\n            (click)=\"toggle()\"\n        >\n            <fa-icon [icon]=\"collapsed() ? faChevronDown : faChevronUp\" />\n        </button>\n    }\n</div>\n<div\n    [id]=\"contentId\"\n    class=\"tum-ui-panel-content-container\"\n    [attr.role]=\"toggleable() && header().trim() ? 'region' : null\"\n    [attr.aria-labelledby]=\"toggleable() && header().trim() ? headerId : null\"\n    [attr.aria-hidden]=\"isCollapsed() ? 'true' : null\"\n    [attr.inert]=\"isCollapsed() ? '' : null\"\n>\n    <div class=\"tum-ui-panel-content-wrapper\">\n        <div class=\"tum-ui-panel-content\">\n            <ng-content />\n        </div>\n        <ng-content select=\"[tumUiPanelFooter]\" />\n    </div>\n</div>\n",
		styles: [":host{display:block}.tum-ui-panel-header{display:flex;justify-content:space-between;align-items:center;padding:calc(var(--tumaet-ui-spacing) * 4.5)}.tum-ui-panel-header-toggleable{padding:calc(var(--tumaet-ui-spacing) * 1.5) calc(var(--tumaet-ui-spacing) * 4.5)}.tum-ui-panel-title{line-height:1;font-weight:600;flex:1;min-width:0}.tum-ui-panel-toggler{display:inline-flex;align-items:center;justify-content:center;width:calc(var(--tumaet-ui-spacing) * 8);height:calc(var(--tumaet-ui-spacing) * 8);padding:0;border:0;border-radius:50%;background:transparent;color:inherit;cursor:pointer;transition:background-color .15s ease-in-out}.tum-ui-panel-toggler:hover{background-color:color-mix(in srgb,currentcolor 8%,transparent)}.tum-ui-panel-toggler:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:1px}.tum-ui-panel-content-container{display:grid;grid-template-rows:1fr;transition:grid-template-rows .2s ease-in-out}:host([data-collapsed=true]) .tum-ui-panel-content-container{grid-template-rows:0fr}.tum-ui-panel-content-wrapper{min-height:0;overflow:hidden}.tum-ui-panel-content{padding:0 calc(var(--tumaet-ui-spacing) * 4.5) calc(var(--tumaet-ui-spacing) * 4.5)}@media(prefers-reduced-motion:reduce){.tum-ui-panel-content-container{transition:none}}\n"],
		dependencies: [{
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiPanelComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-panel",
			imports: [FaIconComponent],
			host: {
				class: "tum-ui-panel tum:border tum:border-border tum:rounded-md tum:bg-content-background tum:text-text",
				"[attr.data-collapsed]": "toggleable() && collapsed()"
			},
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<div class=\"tum-ui-panel-header\" [class.tum-ui-panel-header-toggleable]=\"toggleable()\">\n    <span [id]=\"headerId\" class=\"tum-ui-panel-title\">{{ header() }}<ng-content select=\"[tumUiPanelHeader]\" /></span>\n    @if (toggleable()) {\n        <button\n            type=\"button\"\n            class=\"tum-ui-panel-toggler\"\n            [attr.aria-expanded]=\"!collapsed()\"\n            [attr.aria-controls]=\"contentId\"\n            [attr.aria-label]=\"toggleLabel()\"\n            [attr.aria-labelledby]=\"toggleLabelledBy()\"\n            (click)=\"toggle()\"\n        >\n            <fa-icon [icon]=\"collapsed() ? faChevronDown : faChevronUp\" />\n        </button>\n    }\n</div>\n<div\n    [id]=\"contentId\"\n    class=\"tum-ui-panel-content-container\"\n    [attr.role]=\"toggleable() && header().trim() ? 'region' : null\"\n    [attr.aria-labelledby]=\"toggleable() && header().trim() ? headerId : null\"\n    [attr.aria-hidden]=\"isCollapsed() ? 'true' : null\"\n    [attr.inert]=\"isCollapsed() ? '' : null\"\n>\n    <div class=\"tum-ui-panel-content-wrapper\">\n        <div class=\"tum-ui-panel-content\">\n            <ng-content />\n        </div>\n        <ng-content select=\"[tumUiPanelFooter]\" />\n    </div>\n</div>\n",
			styles: [":host{display:block}.tum-ui-panel-header{display:flex;justify-content:space-between;align-items:center;padding:calc(var(--tumaet-ui-spacing) * 4.5)}.tum-ui-panel-header-toggleable{padding:calc(var(--tumaet-ui-spacing) * 1.5) calc(var(--tumaet-ui-spacing) * 4.5)}.tum-ui-panel-title{line-height:1;font-weight:600;flex:1;min-width:0}.tum-ui-panel-toggler{display:inline-flex;align-items:center;justify-content:center;width:calc(var(--tumaet-ui-spacing) * 8);height:calc(var(--tumaet-ui-spacing) * 8);padding:0;border:0;border-radius:50%;background:transparent;color:inherit;cursor:pointer;transition:background-color .15s ease-in-out}.tum-ui-panel-toggler:hover{background-color:color-mix(in srgb,currentcolor 8%,transparent)}.tum-ui-panel-toggler:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:1px}.tum-ui-panel-content-container{display:grid;grid-template-rows:1fr;transition:grid-template-rows .2s ease-in-out}:host([data-collapsed=true]) .tum-ui-panel-content-container{grid-template-rows:0fr}.tum-ui-panel-content-wrapper{min-height:0;overflow:hidden}.tum-ui-panel-content{padding:0 calc(var(--tumaet-ui-spacing) * 4.5) calc(var(--tumaet-ui-spacing) * 4.5)}@media(prefers-reduced-motion:reduce){.tum-ui-panel-content-container{transition:none}}\n"]
		}]
	}],
	propDecorators: {
		header: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "header",
				required: false
			}]
		}],
		toggleable: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "toggleable",
				required: false
			}]
		}],
		toggleAriaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "toggleAriaLabel",
				required: false
			}]
		}],
		collapsed: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "collapsed",
				required: false
			}]
		}, {
			type: i0.Output,
			args: ["collapsedChange"]
		}]
	}
});
var TumUiPopoverTriggerDirective = class TumUiPopoverTriggerDirective {
	elementRef = inject(ElementRef);
	popover = input.required({
		...ngDevMode ? { debugName: "popover" } : /* istanbul ignore next */ {},
		alias: "tumUiPopoverTrigger"
	});
	toggle() {
		this.popover().toggle(this.elementRef);
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiPopoverTriggerDirective,
		deps: [],
		target: i0.ɵɵFactoryTarget.Directive
	});
	static ɵdir = i0.ɵɵngDeclareDirective({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiPopoverTriggerDirective,
		isStandalone: true,
		selector: "[tumUiPopoverTrigger]",
		inputs: { popover: {
			classPropertyName: "popover",
			publicName: "tumUiPopoverTrigger",
			isSignal: true,
			isRequired: true,
			transformFunction: null
		} },
		host: {
			listeners: { "click": "toggle()" },
			properties: {
				"attr.aria-haspopup": "'dialog'",
				"attr.aria-expanded": "popover().isOpen() ? 'true' : 'false'"
			}
		},
		ngImport: i0
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiPopoverTriggerDirective,
	decorators: [{
		type: Directive,
		args: [{
			selector: "[tumUiPopoverTrigger]",
			host: {
				"(click)": "toggle()",
				"[attr.aria-haspopup]": "'dialog'",
				"[attr.aria-expanded]": "popover().isOpen() ? 'true' : 'false'"
			}
		}]
	}],
	propDecorators: { popover: [{
		type: i0.Input,
		args: [{
			isSignal: true,
			alias: "tumUiPopoverTrigger",
			required: true
		}]
	}] }
});
const CLOSE_DURATION_MS = 100;
var TumUiPopoverComponent = class TumUiPopoverComponent {
	overlayService = inject(TumUiOverlayService);
	viewContainerRef = inject(ViewContainerRef);
	document = inject(DOCUMENT);
	placement = input("bottom", ...ngDevMode ? [{ debugName: "placement" }] : /* istanbul ignore next */ []);
	ariaLabel = input.required(...ngDevMode ? [{ debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	openChange = output();
	panel = viewChild.required("panel", {
		...ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {},
		read: TemplateRef
	});
	overlayRef;
	positionSub;
	openState = signal(false, ...ngDevMode ? [{ debugName: "openState" }] : /* istanbul ignore next */ []);
	appliedPlacement = signal("bottom", ...ngDevMode ? [{ debugName: "appliedPlacement" }] : /* istanbul ignore next */ []);
	isOpen = this.openState.asReadonly();
	open(origin) {
		if (this.isOpen()) return;
		this.overlayRef = this.overlayService.createConnectedOverlay(origin, this.placement(), { hasBackdrop: true });
		this.appliedPlacement.set(this.placement());
		const strategy = this.overlayRef.getConfig().positionStrategy;
		this.positionSub = strategy.positionChanges.subscribe((change) => this.appliedPlacement.set(this.overlayService.placementFromPosition(change.connectionPair)));
		this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
		this.overlayRef.backdropClick().subscribe(() => this.close());
		this.overlayRef.keydownEvents().subscribe((event) => {
			if (event.key === "Escape") this.close();
		});
		this.openState.set(true);
		this.openChange.emit(true);
	}
	close() {
		if (!this.isOpen()) return;
		this.positionSub?.unsubscribe();
		this.positionSub = void 0;
		const closing = this.overlayRef;
		this.overlayRef = void 0;
		this.openState.set(false);
		this.openChange.emit(false);
		this.fadeOutAndDispose(closing);
	}
	fadeOutAndDispose(closing) {
		if (!closing) return;
		const panel = closing.overlayElement?.querySelector(".tum-ui-popover-panel");
		const view = this.document.defaultView;
		const reducedMotion = typeof view?.matchMedia === "function" && view.matchMedia("(prefers-reduced-motion: reduce)").matches;
		if (!panel || reducedMotion || typeof panel.animate !== "function") {
			closing.dispose();
			return;
		}
		panel.animate([{ opacity: 1 }, { opacity: 0 }], {
			duration: CLOSE_DURATION_MS,
			easing: "ease-in",
			fill: "forwards"
		}).finished.then(() => closing.dispose(), () => closing.dispose());
	}
	toggle(origin) {
		if (this.isOpen()) this.close();
		else this.open(origin);
	}
	ngOnDestroy() {
		this.positionSub?.unsubscribe();
		this.overlayRef?.dispose();
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiPopoverComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.2.0",
		version: "22.2.0",
		type: TumUiPopoverComponent,
		isStandalone: true,
		selector: "tum-ui-popover",
		inputs: {
			placement: {
				classPropertyName: "placement",
				publicName: "placement",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			}
		},
		outputs: { openChange: "openChange" },
		viewQueries: [{
			propertyName: "panel",
			first: true,
			predicate: ["panel"],
			descendants: true,
			read: TemplateRef,
			isSignal: true
		}],
		ngImport: i0,
		template: "<ng-template #panel>\n    <div\n        class=\"tum-ui-popover-panel tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:p-3 tum:text-text tum:shadow-lg\"\n        role=\"dialog\"\n        aria-modal=\"true\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.data-placement]=\"appliedPlacement()\"\n        tabindex=\"0\"\n        cdkTrapFocus\n        [cdkTrapFocusAutoCapture]=\"true\"\n    >\n        <ng-content />\n    </div>\n</ng-template>\n",
		styles: [".tum-ui-popover-panel{animation:tum-ui-popover-in .16s ease-out;transform-origin:top center}.tum-ui-popover-panel[data-placement=top]{transform-origin:bottom center}.tum-ui-popover-panel[data-placement=left]{transform-origin:center right}.tum-ui-popover-panel[data-placement=right]{transform-origin:center left}@keyframes tum-ui-popover-in{0%{opacity:0;transform:scale(.92)}to{opacity:1;transform:scale(1)}}@media(prefers-reduced-motion:reduce){.tum-ui-popover-panel{animation:none}}\n"],
		dependencies: [{
			kind: "ngmodule",
			type: A11yModule
		}, {
			kind: "directive",
			type: i1$5.CdkTrapFocus,
			selector: "[cdkTrapFocus]",
			inputs: ["cdkTrapFocus", "cdkTrapFocusAutoCapture"],
			exportAs: ["cdkTrapFocus"]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiPopoverComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-popover",
			imports: [A11yModule],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<ng-template #panel>\n    <div\n        class=\"tum-ui-popover-panel tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:p-3 tum:text-text tum:shadow-lg\"\n        role=\"dialog\"\n        aria-modal=\"true\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.data-placement]=\"appliedPlacement()\"\n        tabindex=\"0\"\n        cdkTrapFocus\n        [cdkTrapFocusAutoCapture]=\"true\"\n    >\n        <ng-content />\n    </div>\n</ng-template>\n",
			styles: [".tum-ui-popover-panel{animation:tum-ui-popover-in .16s ease-out;transform-origin:top center}.tum-ui-popover-panel[data-placement=top]{transform-origin:bottom center}.tum-ui-popover-panel[data-placement=left]{transform-origin:center right}.tum-ui-popover-panel[data-placement=right]{transform-origin:center left}@keyframes tum-ui-popover-in{0%{opacity:0;transform:scale(.92)}to{opacity:1;transform:scale(1)}}@media(prefers-reduced-motion:reduce){.tum-ui-popover-panel{animation:none}}\n"]
		}]
	}],
	propDecorators: {
		placement: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "placement",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: true
			}]
		}],
		openChange: [{
			type: i0.Output,
			args: ["openChange"]
		}],
		panel: [{
			type: i0.ViewChild,
			args: ["panel", {
				read: TemplateRef,
				isSignal: true
			}]
		}]
	}
});
var TumUiProgressBarComponent = class TumUiProgressBarComponent {
	value = input(0, {
		...ngDevMode ? { debugName: "value" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	showValue = input(true, {
		...ngDevMode ? { debugName: "showValue" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	size = input("default", ...ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []);
	severity = input("primary", ...ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []);
	unit = input("%", ...ngDevMode ? [{ debugName: "unit" }] : /* istanbul ignore next */ []);
	normalizedValue = computed(() => {
		const value = this.value();
		return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 0;
	}, ...ngDevMode ? [{ debugName: "normalizedValue" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiProgressBarComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiProgressBarComponent,
		isStandalone: true,
		selector: "tum-ui-progress-bar",
		inputs: {
			value: {
				classPropertyName: "value",
				publicName: "value",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			showValue: {
				classPropertyName: "showValue",
				publicName: "showValue",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			size: {
				classPropertyName: "size",
				publicName: "size",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			severity: {
				classPropertyName: "severity",
				publicName: "severity",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			unit: {
				classPropertyName: "unit",
				publicName: "unit",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: {
			attributes: { "role": "progressbar" },
			properties: {
				"attr.data-size": "size()",
				"attr.aria-valuemin": "0",
				"attr.aria-valuemax": "100",
				"attr.aria-valuenow": "normalizedValue()",
				"attr.aria-label": "ariaLabel()"
			},
			classAttribute: "tum-ui-progress-bar tum:bg-border"
		},
		ngImport: i0,
		template: "<div class=\"tum-ui-progress-bar-value\" [attr.data-severity]=\"severity()\" [style.width.%]=\"normalizedValue()\">\n    <span class=\"tum-ui-progress-bar-label\">\n        <ng-content>\n            @if (showValue() && normalizedValue() !== 0) {\n                {{ normalizedValue() }}{{ unit() }}\n            }\n        </ng-content>\n    </span>\n</div>\n",
		styles: [":host{display:block;position:relative;overflow:hidden;height:calc(var(--tumaet-ui-spacing) * 5);border-radius:var(--tumaet-ui-radius-md)}:host([data-size=small]){height:calc(var(--tumaet-ui-spacing) * 1.5)}:host([data-size=small]) .tum-ui-progress-bar-label{display:none}.tum-ui-progress-bar-value{height:100%;width:0;position:absolute;display:flex;align-items:center;justify-content:center;overflow:hidden;background:var(--tumaet-ui-primary-color);transition:width 1s ease-in-out}.tum-ui-progress-bar-value[data-severity=success]{background:var(--tumaet-ui-state-success)}.tum-ui-progress-bar-value[data-severity=warn]{background:var(--tumaet-ui-state-warning)}.tum-ui-progress-bar-value[data-severity=danger]{background:var(--tumaet-ui-state-danger)}.tum-ui-progress-bar-value[data-severity=info]{background:var(--tumaet-ui-state-info)}.tum-ui-progress-bar-label{font-size:var(--tumaet-ui-font-size-xs);font-weight:600;color:var(--tumaet-ui-primary-contrast-color)}.tum-ui-progress-bar-value[data-severity=success] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-success-contrast)}.tum-ui-progress-bar-value[data-severity=warn] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-warning-contrast)}.tum-ui-progress-bar-value[data-severity=danger] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-danger-contrast)}.tum-ui-progress-bar-value[data-severity=info] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-info-contrast)}@media(prefers-reduced-motion:reduce){.tum-ui-progress-bar-value{transition:none}}@media(forced-colors:active){:host{border:1px solid CanvasText}.tum-ui-progress-bar-value{background:Highlight}.tum-ui-progress-bar-label{color:HighlightText}}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiProgressBarComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-progress-bar",
			host: {
				class: "tum-ui-progress-bar tum:bg-border",
				role: "progressbar",
				"[attr.data-size]": "size()",
				"[attr.aria-valuemin]": "0",
				"[attr.aria-valuemax]": "100",
				"[attr.aria-valuenow]": "normalizedValue()",
				"[attr.aria-label]": "ariaLabel()"
			},
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<div class=\"tum-ui-progress-bar-value\" [attr.data-severity]=\"severity()\" [style.width.%]=\"normalizedValue()\">\n    <span class=\"tum-ui-progress-bar-label\">\n        <ng-content>\n            @if (showValue() && normalizedValue() !== 0) {\n                {{ normalizedValue() }}{{ unit() }}\n            }\n        </ng-content>\n    </span>\n</div>\n",
			styles: [":host{display:block;position:relative;overflow:hidden;height:calc(var(--tumaet-ui-spacing) * 5);border-radius:var(--tumaet-ui-radius-md)}:host([data-size=small]){height:calc(var(--tumaet-ui-spacing) * 1.5)}:host([data-size=small]) .tum-ui-progress-bar-label{display:none}.tum-ui-progress-bar-value{height:100%;width:0;position:absolute;display:flex;align-items:center;justify-content:center;overflow:hidden;background:var(--tumaet-ui-primary-color);transition:width 1s ease-in-out}.tum-ui-progress-bar-value[data-severity=success]{background:var(--tumaet-ui-state-success)}.tum-ui-progress-bar-value[data-severity=warn]{background:var(--tumaet-ui-state-warning)}.tum-ui-progress-bar-value[data-severity=danger]{background:var(--tumaet-ui-state-danger)}.tum-ui-progress-bar-value[data-severity=info]{background:var(--tumaet-ui-state-info)}.tum-ui-progress-bar-label{font-size:var(--tumaet-ui-font-size-xs);font-weight:600;color:var(--tumaet-ui-primary-contrast-color)}.tum-ui-progress-bar-value[data-severity=success] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-success-contrast)}.tum-ui-progress-bar-value[data-severity=warn] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-warning-contrast)}.tum-ui-progress-bar-value[data-severity=danger] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-danger-contrast)}.tum-ui-progress-bar-value[data-severity=info] .tum-ui-progress-bar-label{color:var(--tumaet-ui-state-info-contrast)}@media(prefers-reduced-motion:reduce){.tum-ui-progress-bar-value{transition:none}}@media(forced-colors:active){:host{border:1px solid CanvasText}.tum-ui-progress-bar-value{background:Highlight}.tum-ui-progress-bar-label{color:HighlightText}}\n"]
		}]
	}],
	propDecorators: {
		value: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "value",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		showValue: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "showValue",
				required: false
			}]
		}],
		size: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "size",
				required: false
			}]
		}],
		severity: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "severity",
				required: false
			}]
		}],
		unit: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "unit",
				required: false
			}]
		}]
	}
});
var TumUiProgressSpinnerComponent = class TumUiProgressSpinnerComponent {
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiProgressSpinnerComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiProgressSpinnerComponent,
		isStandalone: true,
		selector: "tum-ui-progress-spinner",
		inputs: { ariaLabel: {
			classPropertyName: "ariaLabel",
			publicName: "ariaLabel",
			isSignal: true,
			isRequired: false,
			transformFunction: null
		} },
		host: {
			attributes: {
				"role": "status",
				"aria-busy": "true"
			},
			properties: { "attr.aria-label": "ariaLabel()" },
			classAttribute: "tum-ui-progress-spinner"
		},
		ngImport: i0,
		template: "<svg class=\"tum-ui-progress-spinner-spin\" viewBox=\"25 25 50 50\">\n    <circle class=\"tum-ui-progress-spinner-circle\" cx=\"50\" cy=\"50\" r=\"20\" fill=\"none\" stroke-width=\"2\" stroke-miterlimit=\"10\" />\n</svg>\n",
		styles: [":host{position:relative;margin:0 auto;width:100px;height:100px;display:inline-block}:host:before{content:\"\";display:block;padding-top:100%}.tum-ui-progress-spinner-spin{height:100%;width:100%;transform-origin:center center;position:absolute;inset:0;margin:auto;animation:tum-ui-progress-spinner-rotate 2s linear infinite}.tum-ui-progress-spinner-circle{stroke-dasharray:89,200;stroke-dashoffset:0;stroke:var(--tumaet-ui-primary-color);stroke-linecap:round;animation:tum-ui-progress-spinner-dash 1.5s ease-in-out infinite}@keyframes tum-ui-progress-spinner-rotate{to{transform:rotate(360deg)}}@keyframes tum-ui-progress-spinner-dash{0%{stroke-dasharray:1,200;stroke-dashoffset:0}50%{stroke-dasharray:89,200;stroke-dashoffset:-35px}to{stroke-dasharray:89,200;stroke-dashoffset:-124px}}@media(prefers-reduced-motion:reduce){.tum-ui-progress-spinner-spin,.tum-ui-progress-spinner-circle{animation:none}}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiProgressSpinnerComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-progress-spinner",
			host: {
				class: "tum-ui-progress-spinner",
				role: "status",
				"aria-busy": "true",
				"[attr.aria-label]": "ariaLabel()"
			},
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<svg class=\"tum-ui-progress-spinner-spin\" viewBox=\"25 25 50 50\">\n    <circle class=\"tum-ui-progress-spinner-circle\" cx=\"50\" cy=\"50\" r=\"20\" fill=\"none\" stroke-width=\"2\" stroke-miterlimit=\"10\" />\n</svg>\n",
			styles: [":host{position:relative;margin:0 auto;width:100px;height:100px;display:inline-block}:host:before{content:\"\";display:block;padding-top:100%}.tum-ui-progress-spinner-spin{height:100%;width:100%;transform-origin:center center;position:absolute;inset:0;margin:auto;animation:tum-ui-progress-spinner-rotate 2s linear infinite}.tum-ui-progress-spinner-circle{stroke-dasharray:89,200;stroke-dashoffset:0;stroke:var(--tumaet-ui-primary-color);stroke-linecap:round;animation:tum-ui-progress-spinner-dash 1.5s ease-in-out infinite}@keyframes tum-ui-progress-spinner-rotate{to{transform:rotate(360deg)}}@keyframes tum-ui-progress-spinner-dash{0%{stroke-dasharray:1,200;stroke-dashoffset:0}50%{stroke-dasharray:89,200;stroke-dashoffset:-35px}to{stroke-dasharray:89,200;stroke-dashoffset:-124px}}@media(prefers-reduced-motion:reduce){.tum-ui-progress-spinner-spin,.tum-ui-progress-spinner-circle{animation:none}}\n"]
		}]
	}],
	propDecorators: { ariaLabel: [{
		type: i0.Input,
		args: [{
			isSignal: true,
			alias: "ariaLabel",
			required: false
		}]
	}] }
});
const UNSET = Symbol("tum-ui-radio-unset");
var TumUiRadioButtonComponent = class TumUiRadioButtonComponent {
	value = input(...ngDevMode ? [void 0, { debugName: "value" }] : /* istanbul ignore next */ []);
	name = input(...ngDevMode ? [void 0, { debugName: "name" }] : /* istanbul ignore next */ []);
	inputId = input(...ngDevMode ? [void 0, { debugName: "inputId" }] : /* istanbul ignore next */ []);
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	selected = output();
	cvaValue = signal(UNSET, ...ngDevMode ? [{ debugName: "cvaValue" }] : /* istanbul ignore next */ []);
	isChecked = computed(() => this.cvaValue() !== UNSET && this.cvaValue() === this.value(), ...ngDevMode ? [{ debugName: "isChecked" }] : /* istanbul ignore next */ []);
	cvaDisabled = signal(false, ...ngDevMode ? [{ debugName: "cvaDisabled" }] : /* istanbul ignore next */ []);
	isDisabled = computed(() => this.disabled() || this.cvaDisabled(), ...ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []);
	boxClasses = computed(() => {
		if (this.isDisabled()) return "tum:bg-disabled-background tum:border-control-border";
		if (this.isChecked()) return "tum:bg-primary tum:border-primary";
		return "tum:bg-control-background tum:border-control-border";
	}, ...ngDevMode ? [{ debugName: "boxClasses" }] : /* istanbul ignore next */ []);
	iconClasses = computed(() => this.isDisabled() ? "tum:bg-disabled" : "tum:bg-primary-contrast", ...ngDevMode ? [{ debugName: "iconClasses" }] : /* istanbul ignore next */ []);
	onModelChange = () => {};
	onModelTouched = () => {};
	onInputClick(event) {
		if (this.isDisabled()) return;
		this.cvaValue.set(this.value());
		this.onModelChange(this.value());
		this.onModelTouched();
		this.selected.emit({
			originalEvent: event,
			value: this.value()
		});
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
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiRadioButtonComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiRadioButtonComponent,
		isStandalone: true,
		selector: "tum-ui-radio-button",
		inputs: {
			value: {
				classPropertyName: "value",
				publicName: "value",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			name: {
				classPropertyName: "name",
				publicName: "name",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			inputId: {
				classPropertyName: "inputId",
				publicName: "inputId",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { selected: "selected" },
		host: { classAttribute: "tum-ui-radio-button" },
		providers: [{
			provide: NG_VALUE_ACCESSOR,
			useExisting: forwardRef(() => TumUiRadioButtonComponent),
			multi: true
		}],
		ngImport: i0,
		template: "<input\n    type=\"radio\"\n    class=\"tum-ui-radio-button-input\"\n    [id]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [checked]=\"isChecked()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    (click)=\"onInputClick($event)\"\n    (blur)=\"onBlur()\"\n/>\n<div class=\"tum-ui-radio-button-box\" [class]=\"boxClasses()\" aria-hidden=\"true\">\n    <span class=\"tum-ui-radio-button-icon\" [class]=\"iconClasses()\"></span>\n</div>\n",
		styles: [":host{position:relative;display:inline-flex;width:calc(var(--tumaet-ui-spacing, .25rem) * 5);height:calc(var(--tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-radio-button-input{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none;border-radius:50%}.tum-ui-radio-button-input:disabled{cursor:default}.tum-ui-radio-button-box{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:50%;box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent);transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-radio-button-box:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-radio-button-icon{width:calc(var(--tumaet-ui-spacing, .25rem) * 3);height:calc(var(--tumaet-ui-spacing, .25rem) * 3);border-radius:50%;visibility:hidden}:host:has(.tum-ui-radio-button-input:checked) .tum-ui-radio-button-icon{visibility:visible}:host:has(.tum-ui-radio-button-input:hover:not(:disabled)) .tum-ui-radio-button-box:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:has(.tum-ui-radio-button-input:focus-visible) .tum-ui-radio-button-box{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-radio-button-input{appearance:auto;opacity:1}.tum-ui-radio-button-box{display:none}}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiRadioButtonComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-radio-button",
			host: { class: "tum-ui-radio-button" },
			providers: [{
				provide: NG_VALUE_ACCESSOR,
				useExisting: forwardRef(() => TumUiRadioButtonComponent),
				multi: true
			}],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<input\n    type=\"radio\"\n    class=\"tum-ui-radio-button-input\"\n    [id]=\"inputId()\"\n    [attr.name]=\"name()\"\n    [checked]=\"isChecked()\"\n    [disabled]=\"isDisabled()\"\n    [attr.aria-label]=\"ariaLabel()\"\n    (click)=\"onInputClick($event)\"\n    (blur)=\"onBlur()\"\n/>\n<div class=\"tum-ui-radio-button-box\" [class]=\"boxClasses()\" aria-hidden=\"true\">\n    <span class=\"tum-ui-radio-button-icon\" [class]=\"iconClasses()\"></span>\n</div>\n",
			styles: [":host{position:relative;display:inline-flex;width:calc(var(--tumaet-ui-spacing, .25rem) * 5);height:calc(var(--tumaet-ui-spacing, .25rem) * 5);flex:0 0 auto;vertical-align:bottom;-webkit-user-select:none;user-select:none}.tum-ui-radio-button-input{position:absolute;inset:0;width:100%;height:100%;margin:0;padding:0;z-index:1;opacity:0;cursor:pointer;appearance:none;border-radius:50%}.tum-ui-radio-button-input:disabled{cursor:default}.tum-ui-radio-button-box{box-sizing:border-box;position:relative;display:flex;align-items:center;justify-content:center;width:100%;height:100%;border-width:1px;border-style:solid;border-radius:50%;box-shadow:0 0 transparent,0 0 transparent,0 1px 2px color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent);transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}.tum-ui-radio-button-box:after{content:\"\";position:absolute;inset:0;border-radius:inherit;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-radio-button-icon{width:calc(var(--tumaet-ui-spacing, .25rem) * 3);height:calc(var(--tumaet-ui-spacing, .25rem) * 3);border-radius:50%;visibility:hidden}:host:has(.tum-ui-radio-button-input:checked) .tum-ui-radio-button-icon{visibility:visible}:host:has(.tum-ui-radio-button-input:hover:not(:disabled)) .tum-ui-radio-button-box:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}:host:has(.tum-ui-radio-button-input:focus-visible) .tum-ui-radio-button-box{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}@media(forced-colors:active){.tum-ui-radio-button-input{appearance:auto;opacity:1}.tum-ui-radio-button-box{display:none}}\n"]
		}]
	}],
	propDecorators: {
		value: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "value",
				required: false
			}]
		}],
		name: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "name",
				required: false
			}]
		}],
		inputId: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "inputId",
				required: false
			}]
		}],
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		selected: [{
			type: i0.Output,
			args: ["selected"]
		}]
	}
});
var TumUiSearchFieldComponent = class TumUiSearchFieldComponent {
	value = model("", ...ngDevMode ? [{ debugName: "value" }] : /* istanbul ignore next */ []);
	placeholder = input("tumUi.searchField.placeholder", ...ngDevMode ? [{ debugName: "placeholder" }] : /* istanbul ignore next */ []);
	ariaLabel = input(void 0, ...ngDevMode ? [{ debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	size = input(void 0, ...ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []);
	faMagnifyingGlass = faMagnifyingGlass;
	faXmark = faXmark;
	accessibleNameKey = computed(() => this.ariaLabel() ?? this.placeholder(), ...ngDevMode ? [{ debugName: "accessibleNameKey" }] : /* istanbul ignore next */ []);
	inputElement = viewChild.required("searchInput", ...ngDevMode ? [{ debugName: "inputElement" }] : /* istanbul ignore next */ []);
	onInput(term) {
		this.value.set(term);
	}
	clear() {
		this.value.set("");
		this.inputElement().nativeElement.focus();
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiSearchFieldComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiSearchFieldComponent,
		isStandalone: true,
		selector: "tum-ui-search-field",
		inputs: {
			value: {
				classPropertyName: "value",
				publicName: "value",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			placeholder: {
				classPropertyName: "placeholder",
				publicName: "placeholder",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			size: {
				classPropertyName: "size",
				publicName: "size",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { value: "valueChange" },
		host: { classAttribute: "tum-ui-search-field" },
		viewQueries: [{
			propertyName: "inputElement",
			first: true,
			predicate: ["searchInput"],
			descendants: true,
			isSignal: true
		}],
		ngImport: i0,
		template: "<fa-icon [icon]=\"faMagnifyingGlass\" class=\"tum-ui-search-field-icon tum:text-muted\" aria-hidden=\"true\" />\n\n<input\n    #searchInput\n    tumUiInput\n    type=\"search\"\n    [tumUiInputSize]=\"size()\"\n    [value]=\"value()\"\n    [disabled]=\"disabled()\"\n    [placeholder]=\"placeholder() | tumUiTranslate\"\n    [attr.aria-label]=\"accessibleNameKey() | tumUiTranslate\"\n    (input)=\"onInput(searchInput.value)\"\n/>\n\n@if (value()) {\n    <button\n        type=\"button\"\n        class=\"tum-ui-search-field-clear tum:cursor-pointer tum:appearance-none tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text\"\n        [disabled]=\"disabled()\"\n        [attr.aria-label]=\"'tumUi.searchField.clear' | tumUiTranslate\"\n        (click)=\"clear()\"\n    >\n        <fa-icon [icon]=\"faXmark\" />\n    </button>\n}\n",
		styles: [":host{position:relative;display:block}.tum-ui-search-field-icon,.tum-ui-search-field-clear{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}.tum-ui-search-field-icon{inset-inline-start:calc(var(--tumaet-ui-spacing) * 3);pointer-events:none}.tum-ui-search-field-clear{inset-inline-end:calc(var(--tumaet-ui-spacing) * 2.5)}.tum-ui-input{width:100%;padding-inline-start:calc(var(--tumaet-ui-spacing) * 10);padding-inline-end:calc(var(--tumaet-ui-spacing) * 8)}.tum-ui-input::-webkit-search-cancel-button{display:none}\n"],
		dependencies: [
			{
				kind: "component",
				type: FaIconComponent,
				selector: "fa-icon",
				inputs: [
					"icon",
					"title",
					"animation",
					"mask",
					"flip",
					"size",
					"pull",
					"border",
					"inverse",
					"symbol",
					"rotate",
					"fixedWidth",
					"transform",
					"a11yRole"
				],
				outputs: [
					"iconChange",
					"titleChange",
					"animationChange",
					"maskChange",
					"flipChange",
					"sizeChange",
					"pullChange",
					"borderChange",
					"inverseChange",
					"symbolChange",
					"rotateChange",
					"fixedWidthChange",
					"transformChange",
					"a11yRoleChange"
				]
			},
			{
				kind: "directive",
				type: TumUiInputDirective,
				selector: "input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]",
				inputs: [
					"tumUiInputSize",
					"tumUiInputInvalid",
					"tumUiInputId",
					"tumUiInputDescribedBy"
				]
			},
			{
				kind: "pipe",
				type: TumUiTranslatePipe,
				name: "tumUiTranslate"
			}
		],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiSearchFieldComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-search-field",
			imports: [
				FaIconComponent,
				TumUiInputDirective,
				TumUiTranslatePipe
			],
			host: { class: "tum-ui-search-field" },
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<fa-icon [icon]=\"faMagnifyingGlass\" class=\"tum-ui-search-field-icon tum:text-muted\" aria-hidden=\"true\" />\n\n<input\n    #searchInput\n    tumUiInput\n    type=\"search\"\n    [tumUiInputSize]=\"size()\"\n    [value]=\"value()\"\n    [disabled]=\"disabled()\"\n    [placeholder]=\"placeholder() | tumUiTranslate\"\n    [attr.aria-label]=\"accessibleNameKey() | tumUiTranslate\"\n    (input)=\"onInput(searchInput.value)\"\n/>\n\n@if (value()) {\n    <button\n        type=\"button\"\n        class=\"tum-ui-search-field-clear tum:cursor-pointer tum:appearance-none tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text\"\n        [disabled]=\"disabled()\"\n        [attr.aria-label]=\"'tumUi.searchField.clear' | tumUiTranslate\"\n        (click)=\"clear()\"\n    >\n        <fa-icon [icon]=\"faXmark\" />\n    </button>\n}\n",
			styles: [":host{position:relative;display:block}.tum-ui-search-field-icon,.tum-ui-search-field-clear{position:absolute;top:50%;transform:translateY(-50%);line-height:1;z-index:1}.tum-ui-search-field-icon{inset-inline-start:calc(var(--tumaet-ui-spacing) * 3);pointer-events:none}.tum-ui-search-field-clear{inset-inline-end:calc(var(--tumaet-ui-spacing) * 2.5)}.tum-ui-input{width:100%;padding-inline-start:calc(var(--tumaet-ui-spacing) * 10);padding-inline-end:calc(var(--tumaet-ui-spacing) * 8)}.tum-ui-input::-webkit-search-cancel-button{display:none}\n"]
		}]
	}],
	propDecorators: {
		value: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "value",
				required: false
			}]
		}, {
			type: i0.Output,
			args: ["valueChange"]
		}],
		placeholder: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "placeholder",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		size: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "size",
				required: false
			}]
		}],
		inputElement: [{
			type: i0.ViewChild,
			args: ["searchInput", { isSignal: true }]
		}]
	}
});
const TRIGGER_SIZE = {
	small: "tum:min-h-8 tum:py-1.5 tum:ps-2.5 tum:text-sm",
	default: "tum:min-h-10 tum:py-2 tum:ps-3 tum:text-base",
	large: "tum:min-h-12 tum:py-2.5 tum:ps-3.5 tum:text-lg"
};
let nextSelectId = 0;
const TYPEAHEAD_DEBOUNCE_MS = 500;
var TumUiSelectComponent = class TumUiSelectComponent {
	overlayService = inject(TumUiOverlayService);
	viewContainerRef = inject(ViewContainerRef);
	destroyRef = inject(DestroyRef);
	document = inject(DOCUMENT);
	injector = inject(Injector);
	formField = inject(TUM_UI_FORM_FIELD, { optional: true });
	options = input([], ...ngDevMode ? [{ debugName: "options" }] : /* istanbul ignore next */ []);
	optionLabel = input(...ngDevMode ? [void 0, { debugName: "optionLabel" }] : /* istanbul ignore next */ []);
	optionValue = input(...ngDevMode ? [void 0, { debugName: "optionValue" }] : /* istanbul ignore next */ []);
	placeholder = input(...ngDevMode ? [void 0, { debugName: "placeholder" }] : /* istanbul ignore next */ []);
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	showClear = input(false, {
		...ngDevMode ? { debugName: "showClear" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	filter = input(false, {
		...ngDevMode ? { debugName: "filter" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	filterBy = input(...ngDevMode ? [void 0, { debugName: "filterBy" }] : /* istanbul ignore next */ []);
	filterPlaceholder = input(...ngDevMode ? [void 0, { debugName: "filterPlaceholder" }] : /* istanbul ignore next */ []);
	size = input(...ngDevMode ? [void 0, { debugName: "size" }] : /* istanbul ignore next */ []);
	inputId = input(...ngDevMode ? [void 0, { debugName: "inputId" }] : /* istanbul ignore next */ []);
	name = input(...ngDevMode ? [void 0, { debugName: "name" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	clearAriaLabel = input(...ngDevMode ? [void 0, { debugName: "clearAriaLabel" }] : /* istanbul ignore next */ []);
	emptyMessage = input(...ngDevMode ? [void 0, { debugName: "emptyMessage" }] : /* istanbul ignore next */ []);
	filterAriaLabel = input(...ngDevMode ? [void 0, { debugName: "filterAriaLabel" }] : /* istanbul ignore next */ []);
	selectionChange = output();
	faChevronDown = faChevronDown;
	faCheck = faCheck;
	faXmark = faXmark;
	fallbackInputId = `tum-ui-select-${nextSelectId++}`;
	resolvedInputId = computed(() => this.formField?.explicitControlId() ?? this.inputId() ?? this.formField?.labelTargetId() ?? this.fallbackInputId, ...ngDevMode ? [{ debugName: "resolvedInputId" }] : /* istanbul ignore next */ []);
	describedBy = computed(() => this.formField?.describedBy() ?? null, ...ngDevMode ? [{ debugName: "describedBy" }] : /* istanbul ignore next */ []);
	isInvalid = computed(() => this.formField?.invalid() ?? false, ...ngDevMode ? [{ debugName: "isInvalid" }] : /* istanbul ignore next */ []);
	listboxId = `tum-ui-select-listbox-${nextSelectId++}`;
	trigger = viewChild.required("trigger", ...ngDevMode ? [{ debugName: "trigger" }] : /* istanbul ignore next */ []);
	panel = viewChild.required("panel", {
		...ngDevMode ? { debugName: "panel" } : /* istanbul ignore next */ {},
		read: TemplateRef
	});
	filterInput = viewChild("filterInput", ...ngDevMode ? [{ debugName: "filterInput" }] : /* istanbul ignore next */ []);
	overlayRef;
	isOpen = signal(false, ...ngDevMode ? [{ debugName: "isOpen" }] : /* istanbul ignore next */ []);
	activeIndex = signal(-1, ...ngDevMode ? [{ debugName: "activeIndex" }] : /* istanbul ignore next */ []);
	filterText = signal("", ...ngDevMode ? [{ debugName: "filterText" }] : /* istanbul ignore next */ []);
	selectedValue = signal(void 0, ...ngDevMode ? [{ debugName: "selectedValue" }] : /* istanbul ignore next */ []);
	disabledByForm = signal(false, ...ngDevMode ? [{ debugName: "disabledByForm" }] : /* istanbul ignore next */ []);
	onChangeCallback = () => {};
	onTouchedCallback = () => {};
	isDisabled = computed(() => this.disabled() || this.disabledByForm(), ...ngDevMode ? [{ debugName: "isDisabled" }] : /* istanbul ignore next */ []);
	selectedOption = computed(() => {
		const current = this.selectedValue();
		if (current === void 0 || current === null) return;
		return this.options().find((option) => this.valuesMatch(this.resolveValue(option), current));
	}, ...ngDevMode ? [{ debugName: "selectedOption" }] : /* istanbul ignore next */ []);
	isFiltering = computed(() => this.filter() && this.filterText().trim().length > 0, ...ngDevMode ? [{ debugName: "isFiltering" }] : /* istanbul ignore next */ []);
	visibleOptions = computed(() => {
		if (!this.isFiltering()) return this.options();
		const query = this.filterText().trim().toLocaleLowerCase();
		return this.options().filter((option) => this.filterFields(option).some((field) => field.toLocaleLowerCase().includes(query)));
	}, ...ngDevMode ? [{ debugName: "visibleOptions" }] : /* istanbul ignore next */ []);
	hasSelection = computed(() => this.selectedOption() !== void 0, ...ngDevMode ? [{ debugName: "hasSelection" }] : /* istanbul ignore next */ []);
	displayLabel = computed(() => {
		const option = this.selectedOption();
		return option !== void 0 ? this.label(option) : this.placeholder() ?? "";
	}, ...ngDevMode ? [{ debugName: "displayLabel" }] : /* istanbul ignore next */ []);
	showClearButton = computed(() => this.showClear() && this.hasSelection() && !this.isDisabled(), ...ngDevMode ? [{ debugName: "showClearButton" }] : /* istanbul ignore next */ []);
	triggerClasses = computed(() => `${this.buildTriggerClasses()} ${this.showClearButton() ? "tum:pe-17" : "tum:pe-10"}`, ...ngDevMode ? [{ debugName: "triggerClasses" }] : /* istanbul ignore next */ []);
	activeOptionId = computed(() => this.activeIndex() >= 0 ? this.optionId(this.activeIndex()) : void 0, ...ngDevMode ? [{ debugName: "activeOptionId" }] : /* istanbul ignore next */ []);
	keyManagerOptions = computed(() => this.visibleOptions().map((option) => ({ getLabel: () => this.label(option) })), ...ngDevMode ? [{ debugName: "keyManagerOptions" }] : /* istanbul ignore next */ []);
	keyManager = new ListKeyManager(this.keyManagerOptions, this.injector).withVerticalOrientation().withHomeAndEnd().withTypeAhead(TYPEAHEAD_DEBOUNCE_MS);
	typeaheadSequence = "";
	pendingFilterFocus = false;
	typeaheadReset;
	constructor() {
		effect(() => {
			const ownId = this.inputId();
			if (ownId) this.formField?.adoptControlId(ownId);
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
			if (this.isDisabled()) this.close();
		});
		afterRenderEffect(() => {
			const field = this.filterInput()?.nativeElement;
			if (this.pendingFilterFocus && field) {
				this.pendingFilterFocus = false;
				field.focus();
			}
		});
		effect(() => {
			const optionCount = this.visibleOptions().length;
			if (optionCount === 0) this.keyManager.setActiveItem(-1);
			else if (this.activeIndex() >= optionCount) this.keyManager.setActiveItem(optionCount - 1);
			else if (this.isOpen() && this.activeIndex() < 0) this.keyManager.setFirstItemActive();
		});
	}
	writeValue(value) {
		this.selectedValue.set(value ?? void 0);
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
		const raw = key && option !== null && typeof option === "object" ? option[key] : option;
		return this.toText(raw);
	}
	toText(value) {
		switch (typeof value) {
			case "string": return value;
			case "number":
			case "boolean":
			case "bigint": return String(value);
			default: return "";
		}
	}
	filterFields(option) {
		const keys = this.filterBy()?.split(",").map((key) => key.trim()).filter((key) => key.length > 0);
		if (!keys?.length || option === null || typeof option !== "object") return [this.label(option)];
		return keys.map((key) => this.toText(option[key]));
	}
	resolveValue(option) {
		const key = this.optionValue();
		if (key && option !== null && typeof option === "object") return option[key];
		return option;
	}
	valuesMatch(a, b) {
		return Object.is(a, b) || a === b;
	}
	isSelected(option) {
		const current = this.selectedValue();
		if (current === void 0 || current === null) return false;
		return this.valuesMatch(this.resolveValue(option), current);
	}
	optionId(index) {
		return `${this.listboxId}-option-${index}`;
	}
	toggle() {
		if (this.isDisabled()) return;
		if (this.isOpen()) this.close();
		else this.open();
	}
	open() {
		if (this.isOpen() || this.isDisabled()) return;
		const selectedIndex = this.visibleOptions().findIndex((option) => this.isSelected(option));
		const initialIndex = selectedIndex >= 0 ? selectedIndex : this.visibleOptions().length > 0 ? 0 : -1;
		this.keyManager.setActiveItem(initialIndex);
		const origin = this.trigger();
		this.overlayRef = this.overlayService.createConnectedOverlay(origin, "bottom", {
			hasBackdrop: true,
			matchOriginWidth: true
		});
		this.overlayRef.attach(new TemplatePortal(this.panel(), this.viewContainerRef));
		this.scrollOptionIntoView(initialIndex);
		this.overlayRef.backdropClick().subscribe(() => this.close());
		this.overlayRef.keydownEvents().subscribe((event) => {
			if (event.key === "Escape") this.close();
		});
		this.isOpen.set(true);
		this.pendingFilterFocus = this.filter();
	}
	close(restoreFocus = true) {
		if (!this.isOpen()) return;
		this.overlayRef?.dispose();
		this.overlayRef = void 0;
		this.isOpen.set(false);
		this.filterText.set("");
		this.resetTypeahead();
		this.onTouchedCallback();
		if (restoreFocus && !this.isDisabled()) this.trigger().nativeElement.focus();
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
		this.selectedValue.set(void 0);
		this.onChangeCallback(void 0);
		this.selectionChange.emit(void 0);
		this.onTouchedCallback();
		this.trigger().nativeElement.focus();
	}
	setActive(index) {
		this.keyManager.setActiveItem(index);
	}
	onTriggerKeydown(event) {
		if (this.isDisabled()) return;
		if (!this.isOpen()) {
			if (event.key === "Enter" || event.key === " " || event.key === "Spacebar") {
				event.preventDefault();
				this.open();
				return;
			}
			if (event.key === "ArrowDown" || event.key === "ArrowUp") {
				event.preventDefault();
				this.open();
				return;
			}
			if (event.key === "Home" || event.key === "End") {
				event.preventDefault();
				this.open();
				this.setActive(event.key === "Home" ? 0 : this.visibleOptions().length - 1);
				return;
			}
			if (event.key.length === 1 && event.key !== " " && !event.ctrlKey && !event.metaKey && !event.altKey) {
				this.open();
				if (!this.filter()) this.handleTypeahead(event);
			}
			return;
		}
		const count = this.visibleOptions().length;
		switch (event.key) {
			case "Enter":
			case " ":
			case "Spacebar":
				event.preventDefault();
				if (this.activeIndex() >= 0 && this.activeIndex() < count) this.selectOption(this.visibleOptions()[this.activeIndex()]);
				break;
			case "Escape":
				this.close();
				break;
			case "Tab":
				if (this.activeIndex() >= 0 && this.activeIndex() < count) this.selectOption(this.visibleOptions()[this.activeIndex()]);
				else this.close(false);
				break;
			default: if (event.key.length === 1 && event.key !== " " && !event.ctrlKey && !event.metaKey && !event.altKey) this.handleTypeahead(event);
			else this.keyManager.onKeydown(event);
		}
	}
	onFilterInput(event) {
		this.filterText.set(event.target.value);
		this.keyManager.setActiveItem(this.visibleOptions().length > 0 ? 0 : -1);
	}
	onFilterKeydown(event) {
		if (![
			"ArrowDown",
			"ArrowUp",
			"Home",
			"End",
			"Enter",
			"Escape",
			"Tab"
		].includes(event.key)) return;
		if (event.key === "Home" || event.key === "End") return;
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
			if (nextMatch >= 0) this.keyManager.setActiveItem((start + nextMatch + 1) % options.length);
			this.typeaheadSequence = character;
		} else {
			this.typeaheadSequence += character;
			this.keyManager.onKeydown(event);
		}
		this.typeaheadReset = setTimeout(() => {
			this.typeaheadSequence = "";
		}, TYPEAHEAD_DEBOUNCE_MS);
	}
	resetTypeahead() {
		this.keyManager.cancelTypeahead();
		clearTimeout(this.typeaheadReset);
		this.typeaheadSequence = "";
	}
	scrollOptionIntoView(index) {
		this.document.getElementById(this.optionId(index))?.scrollIntoView?.({ block: "nearest" });
	}
	buildTriggerClasses() {
		const base = "tum-ui-select-trigger tum:box-border tum:flex tum:w-full tum:items-center tum:border tum:text-start tum:transition-colors";
		const size = TRIGGER_SIZE[this.size() ?? "default"];
		let state;
		if (this.isDisabled()) state = "tum:cursor-default tum:bg-disabled-background tum:text-disabled tum:border-control-border";
		else if (this.isInvalid()) state = `tum:cursor-pointer tum:bg-control-background tum:text-text tum:border-state-danger`;
		else if (this.isOpen()) state = "tum:cursor-pointer tum:bg-control-background tum:text-text tum:border-primary";
		else state = "tum:cursor-pointer tum:bg-control-background tum:text-text tum:border-control-border tum:hover:border-control-border-hover";
		return `${base} ${size} ${state}`;
	}
	optionClasses(option, index) {
		const base = "tum-ui-select-option tum:flex tum:cursor-pointer tum:items-center tum:px-3 tum:py-2";
		const active = this.activeIndex() === index;
		if (this.isSelected(option)) return `${base} tum:text-highlight ${active ? "tum:bg-highlight-focus-background" : "tum:bg-highlight-background"}`;
		return `${base} tum:text-text tum:hover:bg-hover-background tum:hover:text-text-hover${active ? " tum:bg-highlight-focus-background tum:text-highlight" : ""}`;
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiSelectComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiSelectComponent,
		isStandalone: true,
		selector: "tum-ui-select",
		inputs: {
			options: {
				classPropertyName: "options",
				publicName: "options",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			optionLabel: {
				classPropertyName: "optionLabel",
				publicName: "optionLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			optionValue: {
				classPropertyName: "optionValue",
				publicName: "optionValue",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			placeholder: {
				classPropertyName: "placeholder",
				publicName: "placeholder",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			showClear: {
				classPropertyName: "showClear",
				publicName: "showClear",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			filter: {
				classPropertyName: "filter",
				publicName: "filter",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			filterBy: {
				classPropertyName: "filterBy",
				publicName: "filterBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			filterPlaceholder: {
				classPropertyName: "filterPlaceholder",
				publicName: "filterPlaceholder",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			size: {
				classPropertyName: "size",
				publicName: "size",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			inputId: {
				classPropertyName: "inputId",
				publicName: "inputId",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			name: {
				classPropertyName: "name",
				publicName: "name",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			clearAriaLabel: {
				classPropertyName: "clearAriaLabel",
				publicName: "clearAriaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			emptyMessage: {
				classPropertyName: "emptyMessage",
				publicName: "emptyMessage",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			filterAriaLabel: {
				classPropertyName: "filterAriaLabel",
				publicName: "filterAriaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { selectionChange: "selectionChange" },
		host: { classAttribute: "tum-ui-select" },
		providers: [{
			provide: NG_VALUE_ACCESSOR,
			useExisting: forwardRef(() => TumUiSelectComponent),
			multi: true
		}],
		viewQueries: [
			{
				propertyName: "trigger",
				first: true,
				predicate: ["trigger"],
				descendants: true,
				isSignal: true
			},
			{
				propertyName: "panel",
				first: true,
				predicate: ["panel"],
				descendants: true,
				read: TemplateRef,
				isSignal: true
			},
			{
				propertyName: "filterInput",
				first: true,
				predicate: ["filterInput"],
				descendants: true,
				isSignal: true
			}
		],
		ngImport: i0,
		template: "<div class=\"tum:relative tum:inline-flex tum:w-full\">\n    <button\n        #trigger\n        type=\"button\"\n        [id]=\"resolvedInputId()\"\n        [class]=\"triggerClasses()\"\n        [disabled]=\"isDisabled()\"\n        [attr.name]=\"name()\"\n        role=\"combobox\"\n        aria-haspopup=\"listbox\"\n        aria-autocomplete=\"none\"\n        [attr.aria-expanded]=\"isOpen() ? 'true' : 'false'\"\n        [attr.aria-controls]=\"isOpen() ? listboxId : null\"\n        [attr.aria-activedescendant]=\"isOpen() ? activeOptionId() : null\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-describedby]=\"describedBy()\"\n        [attr.aria-invalid]=\"isInvalid() ? 'true' : null\"\n        (click)=\"toggle()\"\n        (keydown)=\"onTriggerKeydown($event)\"\n    >\n        <span class=\"tum:flex-1 tum:truncate\" [class]=\"!hasSelection() ? 'tum:text-muted' : ''\">{{ displayLabel() }}</span>\n    </button>\n    <span class=\"tum:pointer-events-none tum:absolute tum:inset-y-0 tum:end-0 tum:flex tum:w-10 tum:items-center tum:justify-center tum:text-muted\">\n        <fa-icon [icon]=\"faChevronDown\" />\n    </span>\n\n    @if (showClearButton()) {\n        <button\n            type=\"button\"\n            class=\"tum:absolute tum:inset-y-0 tum:end-10 tum:flex tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text-hover\"\n            (click)=\"clear($event)\"\n            [attr.aria-label]=\"clearAriaLabel() ?? ('tumUi.select.clear' | tumUiTranslate)\"\n        >\n            <fa-icon [icon]=\"faXmark\" />\n        </button>\n    }\n</div>\n\n<ng-template #panel>\n    <div class=\"tum-ui-select-panel tum:box-border tum:w-full tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-md\">\n        @if (filter()) {\n            <div class=\"tum:border-b tum:border-border tum:p-1\">\n                <input\n                    #filterInput\n                    type=\"text\"\n                    class=\"tum-ui-select-filter tum:box-border tum:w-full tum:appearance-none tum:rounded-sm tum:border tum:border-control-border tum:bg-control-background tum:px-2 tum:py-1.5 tum:text-sm tum:text-text tum:placeholder:text-muted tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus\"\n                    [value]=\"filterText()\"\n                    [attr.placeholder]=\"filterPlaceholder() ?? ('tumUi.select.filter' | tumUiTranslate)\"\n                    [attr.aria-label]=\"filterAriaLabel() ?? ('tumUi.select.filter' | tumUiTranslate)\"\n                    [attr.aria-controls]=\"listboxId\"\n                    [attr.aria-activedescendant]=\"activeOptionId()\"\n                    (input)=\"onFilterInput($event)\"\n                    (keydown)=\"onFilterKeydown($event)\"\n                />\n            </div>\n        }\n        <ul\n            [id]=\"listboxId\"\n            role=\"listbox\"\n            [attr.aria-label]=\"ariaLabel()\"\n            class=\"tum:m-0 tum:flex tum:max-h-60 tum:list-none tum:flex-col tum:gap-0.5 tum:overflow-y-auto tum:p-1 tum:outline-none\"\n        >\n            @for (option of visibleOptions(); track $index; let i = $index) {\n                <li\n                    [id]=\"optionId(i)\"\n                    role=\"option\"\n                    [attr.aria-selected]=\"isSelected(option) ? 'true' : 'false'\"\n                    [class]=\"optionClasses(option, i)\"\n                    (click)=\"selectOption(option)\"\n                    (mouseenter)=\"setActive(i)\"\n                >\n                    <span class=\"tum:flex-1 tum:truncate\">{{ label(option) }}</span>\n                    @if (isSelected(option)) {\n                        <fa-icon [icon]=\"faCheck\" class=\"tum:ms-2 tum:shrink-0\" />\n                    }\n                </li>\n            } @empty {\n                <li role=\"option\" aria-selected=\"false\" aria-disabled=\"true\" class=\"tum:px-3 tum:py-2 tum:text-muted\">\n                    {{ isFiltering() ? ('tumUi.select.noResults' | tumUiTranslate) : (emptyMessage() ?? ('tumUi.select.empty' | tumUiTranslate)) }}\n                </li>\n            }\n        </ul>\n    </div>\n</ng-template>\n",
		styles: [":host{display:inline-flex;max-width:100%}.tum-ui-select-trigger{border-radius:var(--tumaet-ui-radius-md);appearance:none}.tum-ui-select-trigger:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-trigger:disabled{opacity:1}\n"],
		dependencies: [{
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}, {
			kind: "pipe",
			type: TumUiTranslatePipe,
			name: "tumUiTranslate"
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiSelectComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-select",
			imports: [FaIconComponent, TumUiTranslatePipe],
			host: { class: "tum-ui-select" },
			providers: [{
				provide: NG_VALUE_ACCESSOR,
				useExisting: forwardRef(() => TumUiSelectComponent),
				multi: true
			}],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<div class=\"tum:relative tum:inline-flex tum:w-full\">\n    <button\n        #trigger\n        type=\"button\"\n        [id]=\"resolvedInputId()\"\n        [class]=\"triggerClasses()\"\n        [disabled]=\"isDisabled()\"\n        [attr.name]=\"name()\"\n        role=\"combobox\"\n        aria-haspopup=\"listbox\"\n        aria-autocomplete=\"none\"\n        [attr.aria-expanded]=\"isOpen() ? 'true' : 'false'\"\n        [attr.aria-controls]=\"isOpen() ? listboxId : null\"\n        [attr.aria-activedescendant]=\"isOpen() ? activeOptionId() : null\"\n        [attr.aria-label]=\"ariaLabel()\"\n        [attr.aria-describedby]=\"describedBy()\"\n        [attr.aria-invalid]=\"isInvalid() ? 'true' : null\"\n        (click)=\"toggle()\"\n        (keydown)=\"onTriggerKeydown($event)\"\n    >\n        <span class=\"tum:flex-1 tum:truncate\" [class]=\"!hasSelection() ? 'tum:text-muted' : ''\">{{ displayLabel() }}</span>\n    </button>\n    <span class=\"tum:pointer-events-none tum:absolute tum:inset-y-0 tum:end-0 tum:flex tum:w-10 tum:items-center tum:justify-center tum:text-muted\">\n        <fa-icon [icon]=\"faChevronDown\" />\n    </span>\n\n    @if (showClearButton()) {\n        <button\n            type=\"button\"\n            class=\"tum:absolute tum:inset-y-0 tum:end-10 tum:flex tum:w-6 tum:appearance-none tum:items-center tum:justify-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted tum:hover:text-text-hover\"\n            (click)=\"clear($event)\"\n            [attr.aria-label]=\"clearAriaLabel() ?? ('tumUi.select.clear' | tumUiTranslate)\"\n        >\n            <fa-icon [icon]=\"faXmark\" />\n        </button>\n    }\n</div>\n\n<ng-template #panel>\n    <div class=\"tum-ui-select-panel tum:box-border tum:w-full tum:rounded-md tum:border tum:border-border tum:bg-overlay-background tum:text-text tum:shadow-md\">\n        @if (filter()) {\n            <div class=\"tum:border-b tum:border-border tum:p-1\">\n                <input\n                    #filterInput\n                    type=\"text\"\n                    class=\"tum-ui-select-filter tum:box-border tum:w-full tum:appearance-none tum:rounded-sm tum:border tum:border-control-border tum:bg-control-background tum:px-2 tum:py-1.5 tum:text-sm tum:text-text tum:placeholder:text-muted tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus\"\n                    [value]=\"filterText()\"\n                    [attr.placeholder]=\"filterPlaceholder() ?? ('tumUi.select.filter' | tumUiTranslate)\"\n                    [attr.aria-label]=\"filterAriaLabel() ?? ('tumUi.select.filter' | tumUiTranslate)\"\n                    [attr.aria-controls]=\"listboxId\"\n                    [attr.aria-activedescendant]=\"activeOptionId()\"\n                    (input)=\"onFilterInput($event)\"\n                    (keydown)=\"onFilterKeydown($event)\"\n                />\n            </div>\n        }\n        <ul\n            [id]=\"listboxId\"\n            role=\"listbox\"\n            [attr.aria-label]=\"ariaLabel()\"\n            class=\"tum:m-0 tum:flex tum:max-h-60 tum:list-none tum:flex-col tum:gap-0.5 tum:overflow-y-auto tum:p-1 tum:outline-none\"\n        >\n            @for (option of visibleOptions(); track $index; let i = $index) {\n                <li\n                    [id]=\"optionId(i)\"\n                    role=\"option\"\n                    [attr.aria-selected]=\"isSelected(option) ? 'true' : 'false'\"\n                    [class]=\"optionClasses(option, i)\"\n                    (click)=\"selectOption(option)\"\n                    (mouseenter)=\"setActive(i)\"\n                >\n                    <span class=\"tum:flex-1 tum:truncate\">{{ label(option) }}</span>\n                    @if (isSelected(option)) {\n                        <fa-icon [icon]=\"faCheck\" class=\"tum:ms-2 tum:shrink-0\" />\n                    }\n                </li>\n            } @empty {\n                <li role=\"option\" aria-selected=\"false\" aria-disabled=\"true\" class=\"tum:px-3 tum:py-2 tum:text-muted\">\n                    {{ isFiltering() ? ('tumUi.select.noResults' | tumUiTranslate) : (emptyMessage() ?? ('tumUi.select.empty' | tumUiTranslate)) }}\n                </li>\n            }\n        </ul>\n    </div>\n</ng-template>\n",
			styles: [":host{display:inline-flex;max-width:100%}.tum-ui-select-trigger{border-radius:var(--tumaet-ui-radius-md);appearance:none}.tum-ui-select-trigger:focus-visible{outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-trigger:disabled{opacity:1}\n"]
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		options: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "options",
				required: false
			}]
		}],
		optionLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "optionLabel",
				required: false
			}]
		}],
		optionValue: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "optionValue",
				required: false
			}]
		}],
		placeholder: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "placeholder",
				required: false
			}]
		}],
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		showClear: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "showClear",
				required: false
			}]
		}],
		filter: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "filter",
				required: false
			}]
		}],
		filterBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "filterBy",
				required: false
			}]
		}],
		filterPlaceholder: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "filterPlaceholder",
				required: false
			}]
		}],
		size: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "size",
				required: false
			}]
		}],
		inputId: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "inputId",
				required: false
			}]
		}],
		name: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "name",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		clearAriaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "clearAriaLabel",
				required: false
			}]
		}],
		emptyMessage: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "emptyMessage",
				required: false
			}]
		}],
		filterAriaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "filterAriaLabel",
				required: false
			}]
		}],
		selectionChange: [{
			type: i0.Output,
			args: ["selectionChange"]
		}],
		trigger: [{
			type: i0.ViewChild,
			args: ["trigger", { isSignal: true }]
		}],
		panel: [{
			type: i0.ViewChild,
			args: ["panel", {
				read: TemplateRef,
				isSignal: true
			}]
		}],
		filterInput: [{
			type: i0.ViewChild,
			args: ["filterInput", { isSignal: true }]
		}]
	}
});
function isRecord(value) {
	return value !== null && typeof value === "object";
}
function displayLabel(value) {
	return typeof value === "string" || typeof value === "number" || typeof value === "bigint" || typeof value === "boolean" ? String(value) : void 0;
}
var TumUiSelectButtonComponent = class TumUiSelectButtonComponent {
	options = input([], ...ngDevMode ? [{ debugName: "options" }] : /* istanbul ignore next */ []);
	optionLabel = input(...ngDevMode ? [void 0, { debugName: "optionLabel" }] : /* istanbul ignore next */ []);
	optionValue = input(...ngDevMode ? [void 0, { debugName: "optionValue" }] : /* istanbul ignore next */ []);
	size = input(...ngDevMode ? [void 0, { debugName: "size" }] : /* istanbul ignore next */ []);
	allowEmpty = input(true, {
		...ngDevMode ? { debugName: "allowEmpty" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	itemTemplate = input(...ngDevMode ? [void 0, { debugName: "itemTemplate" }] : /* istanbul ignore next */ []);
	changed = output();
	value = signal(void 0, ...ngDevMode ? [{ debugName: "value" }] : /* istanbul ignore next */ []);
	cvaDisabled = signal(false, ...ngDevMode ? [{ debugName: "cvaDisabled" }] : /* istanbul ignore next */ []);
	effectiveDisabled = computed(() => this.disabled() || this.cvaDisabled(), ...ngDevMode ? [{ debugName: "effectiveDisabled" }] : /* istanbul ignore next */ []);
	onChange = () => {};
	onTouched = () => {};
	normalizedOptions = computed(() => {
		const labelKey = this.optionLabel();
		const valueKey = this.optionValue();
		const current = this.value();
		return this.options().flatMap((raw) => {
			const record = isRecord(raw) ? raw : void 0;
			if ((labelKey !== void 0 || valueKey !== void 0) && !record) return [];
			const value = valueKey !== void 0 ? record[valueKey] : raw;
			const label = displayLabel(labelKey !== void 0 ? record[labelKey] : raw);
			return label === void 0 && !this.itemTemplate() ? [] : [{
				raw,
				value,
				label: label ?? "",
				selected: value === current
			}];
		});
	}, ...ngDevMode ? [{ debugName: "normalizedOptions" }] : /* istanbul ignore next */ []);
	optionClasses(selected) {
		return `tum-ui-select-button-option ${this.size() === "small" ? "tum:text-sm" : this.size() === "large" ? "tum:text-lg" : "tum:text-base"} ${selected ? "tum:bg-primary tum:text-primary-contrast tum:border-primary" : "tum:bg-hover-background tum:text-text tum:border-border"} ${this.effectiveDisabled() ? "tum:opacity-60" : ""}`.trim();
	}
	select(option) {
		if (this.effectiveDisabled()) return;
		let next;
		if (option.selected) {
			if (!this.allowEmpty()) return;
			next = void 0;
		} else next = option.value;
		this.value.set(next);
		this.onChange(next);
		this.onTouched();
		this.changed.emit(next);
	}
	writeValue(value) {
		this.value.set(value ?? void 0);
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
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiSelectButtonComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiSelectButtonComponent,
		isStandalone: true,
		selector: "tum-ui-select-button",
		inputs: {
			options: {
				classPropertyName: "options",
				publicName: "options",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			optionLabel: {
				classPropertyName: "optionLabel",
				publicName: "optionLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			optionValue: {
				classPropertyName: "optionValue",
				publicName: "optionValue",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			size: {
				classPropertyName: "size",
				publicName: "size",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			allowEmpty: {
				classPropertyName: "allowEmpty",
				publicName: "allowEmpty",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			itemTemplate: {
				classPropertyName: "itemTemplate",
				publicName: "itemTemplate",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { changed: "changed" },
		host: {
			attributes: { "role": "group" },
			properties: { "attr.aria-disabled": "effectiveDisabled() || null" },
			classAttribute: "tum-ui-select-button"
		},
		providers: [{
			provide: NG_VALUE_ACCESSOR,
			useExisting: forwardRef(() => TumUiSelectButtonComponent),
			multi: true
		}],
		ngImport: i0,
		template: "@for (option of normalizedOptions(); track option.value) {\n    <button type=\"button\" [class]=\"optionClasses(option.selected)\" [attr.aria-pressed]=\"option.selected\" [disabled]=\"effectiveDisabled()\" (click)=\"select(option)\">\n        @if (itemTemplate(); as template) {\n            <ng-container [ngTemplateOutlet]=\"template\" [ngTemplateOutletContext]=\"{ $implicit: option.raw }\" />\n        } @else {\n            {{ option.label }}\n        }\n    </button>\n}\n",
		styles: [":host{display:inline-flex;vertical-align:bottom;-webkit-user-select:none;user-select:none;border-radius:var(--tumaet-ui-radius-md);outline-color:transparent}.tum-ui-select-button-option{position:relative;appearance:none;display:inline-flex;align-items:center;justify-content:center;gap:calc(var(--tumaet-ui-spacing) * 2);padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 4);font-weight:500;white-space:nowrap;cursor:pointer;border-block-width:1px;border-inline-start-width:0;border-inline-end-width:1px;border-style:solid;border-radius:0;transition:background-color .2s,color .2s,border-color .2s,box-shadow .2s,outline-color .2s;outline-color:transparent}.tum-ui-select-button-option:first-child{border-inline-start-width:1px;border-start-start-radius:var(--tumaet-ui-radius-md);border-end-start-radius:var(--tumaet-ui-radius-md)}.tum-ui-select-button-option:last-child{border-start-end-radius:var(--tumaet-ui-radius-md);border-end-end-radius:var(--tumaet-ui-radius-md)}.tum-ui-select-button-option:disabled{cursor:default}.tum-ui-select-button-option:focus-visible{position:relative;z-index:1;outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-button-option:after{content:\"\";position:absolute;inset:0;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-select-button-option:not(:disabled):hover:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}@media(forced-colors:active){.tum-ui-select-button-option[aria-pressed=true]{color:HighlightText;background:Highlight;border-color:Highlight}}\n"],
		dependencies: [{
			kind: "directive",
			type: NgTemplateOutlet,
			selector: "[ngTemplateOutlet]",
			inputs: [
				"ngTemplateOutletContext",
				"ngTemplateOutlet",
				"ngTemplateOutletInjector"
			]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiSelectButtonComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-select-button",
			imports: [NgTemplateOutlet],
			host: {
				role: "group",
				class: "tum-ui-select-button",
				"[attr.aria-disabled]": "effectiveDisabled() || null"
			},
			providers: [{
				provide: NG_VALUE_ACCESSOR,
				useExisting: forwardRef(() => TumUiSelectButtonComponent),
				multi: true
			}],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "@for (option of normalizedOptions(); track option.value) {\n    <button type=\"button\" [class]=\"optionClasses(option.selected)\" [attr.aria-pressed]=\"option.selected\" [disabled]=\"effectiveDisabled()\" (click)=\"select(option)\">\n        @if (itemTemplate(); as template) {\n            <ng-container [ngTemplateOutlet]=\"template\" [ngTemplateOutletContext]=\"{ $implicit: option.raw }\" />\n        } @else {\n            {{ option.label }}\n        }\n    </button>\n}\n",
			styles: [":host{display:inline-flex;vertical-align:bottom;-webkit-user-select:none;user-select:none;border-radius:var(--tumaet-ui-radius-md);outline-color:transparent}.tum-ui-select-button-option{position:relative;appearance:none;display:inline-flex;align-items:center;justify-content:center;gap:calc(var(--tumaet-ui-spacing) * 2);padding:calc(var(--tumaet-ui-spacing) * 2) calc(var(--tumaet-ui-spacing) * 4);font-weight:500;white-space:nowrap;cursor:pointer;border-block-width:1px;border-inline-start-width:0;border-inline-end-width:1px;border-style:solid;border-radius:0;transition:background-color .2s,color .2s,border-color .2s,box-shadow .2s,outline-color .2s;outline-color:transparent}.tum-ui-select-button-option:first-child{border-inline-start-width:1px;border-start-start-radius:var(--tumaet-ui-radius-md);border-end-start-radius:var(--tumaet-ui-radius-md)}.tum-ui-select-button-option:last-child{border-start-end-radius:var(--tumaet-ui-radius-md);border-end-end-radius:var(--tumaet-ui-radius-md)}.tum-ui-select-button-option:disabled{cursor:default}.tum-ui-select-button-option:focus-visible{position:relative;z-index:1;outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-select-button-option:after{content:\"\";position:absolute;inset:0;background-color:transparent;pointer-events:none;transition:background-color .15s ease}.tum-ui-select-button-option:not(:disabled):hover:after{background-color:color-mix(in srgb,var(--tumaet-ui-text-color) 5%,transparent)}@media(forced-colors:active){.tum-ui-select-button-option[aria-pressed=true]{color:HighlightText;background:Highlight;border-color:Highlight}}\n"]
		}]
	}],
	propDecorators: {
		options: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "options",
				required: false
			}]
		}],
		optionLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "optionLabel",
				required: false
			}]
		}],
		optionValue: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "optionValue",
				required: false
			}]
		}],
		size: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "size",
				required: false
			}]
		}],
		allowEmpty: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "allowEmpty",
				required: false
			}]
		}],
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		itemTemplate: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "itemTemplate",
				required: false
			}]
		}],
		changed: [{
			type: i0.Output,
			args: ["changed"]
		}]
	}
});
const SIZE_PADDING = {
	small: "tum:[&_thead_th]:px-2 tum:[&_thead_th]:py-1.5 tum:[&_tbody_td]:px-2 tum:[&_tbody_td]:py-1.5",
	normal: "tum:[&_thead_th]:px-4 tum:[&_thead_th]:py-3 tum:[&_tbody_td]:px-4 tum:[&_tbody_td]:py-3",
	large: "tum:[&_thead_th]:px-5 tum:[&_thead_th]:py-4 tum:[&_tbody_td]:px-5 tum:[&_tbody_td]:py-4"
};
const HEADER_CLASSES = "tum:[&_thead_th]:text-start tum:[&_thead_th]:font-semibold tum:[&_thead_th]:whitespace-nowrap tum:[&_thead_th]:bg-content-background tum:[&_thead_th]:text-text tum:[&_thead_th]:border-b tum:[&_thead_th]:border-border";
const BODY_CLASSES = "tum:[&_tbody_td]:text-text tum:[&_tbody_td]:border-b tum:[&_tbody_td]:border-border";
const STRIPED_CLASSES = "tum:[&_tbody_tr:nth-child(odd)]:bg-table-striped-background";
const HOVER_CLASSES = "tum:[&_tbody_tr:hover]:bg-hover-background";
const SCROLLABLE_CLASSES = "tum:[&_thead_th]:sticky tum:[&_thead_th]:top-0 tum:[&_thead_th]:z-10";
var TumUiTableDirective = class TumUiTableDirective {
	size = input("normal", ...ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []);
	striped = input(false, {
		...ngDevMode ? { debugName: "striped" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	scrollable = input(false, {
		...ngDevMode ? { debugName: "scrollable" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	rowHover = input(false, {
		...ngDevMode ? { debugName: "rowHover" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	sortField = input(void 0, ...ngDevMode ? [{ debugName: "sortField" }] : /* istanbul ignore next */ []);
	sortOrder = input(1, {
		...ngDevMode ? { debugName: "sortOrder" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	defaultSortOrder = input(1, {
		...ngDevMode ? { debugName: "defaultSortOrder" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	sortChange = output();
	hostClasses = computed(() => {
		const parts = [
			"tum-ui-table tum:w-full tum:border-collapse tum:text-sm",
			SIZE_PADDING[this.size()],
			HEADER_CLASSES,
			BODY_CLASSES
		];
		if (this.striped()) parts.push(STRIPED_CLASSES);
		if (this.rowHover()) parts.push(HOVER_CLASSES);
		if (this.scrollable()) parts.push(SCROLLABLE_CLASSES);
		return parts.join(" ");
	}, ...ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []);
	requestSort(field) {
		const order = this.sortField() === field ? this.sortOrder() * -1 : this.defaultSortOrder();
		this.sortChange.emit({
			field,
			order
		});
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTableDirective,
		deps: [],
		target: i0.ɵɵFactoryTarget.Directive
	});
	static ɵdir = i0.ɵɵngDeclareDirective({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiTableDirective,
		isStandalone: true,
		selector: "table[tumUiTable]",
		inputs: {
			size: {
				classPropertyName: "size",
				publicName: "size",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			striped: {
				classPropertyName: "striped",
				publicName: "striped",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			scrollable: {
				classPropertyName: "scrollable",
				publicName: "scrollable",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			rowHover: {
				classPropertyName: "rowHover",
				publicName: "rowHover",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			sortField: {
				classPropertyName: "sortField",
				publicName: "sortField",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			sortOrder: {
				classPropertyName: "sortOrder",
				publicName: "sortOrder",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			defaultSortOrder: {
				classPropertyName: "defaultSortOrder",
				publicName: "defaultSortOrder",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { sortChange: "sortChange" },
		host: { properties: { "class": "hostClasses()" } },
		ngImport: i0
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTableDirective,
	decorators: [{
		type: Directive,
		args: [{
			selector: "table[tumUiTable]",
			host: { "[class]": "hostClasses()" }
		}]
	}],
	propDecorators: {
		size: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "size",
				required: false
			}]
		}],
		striped: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "striped",
				required: false
			}]
		}],
		scrollable: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "scrollable",
				required: false
			}]
		}],
		rowHover: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "rowHover",
				required: false
			}]
		}],
		sortField: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "sortField",
				required: false
			}]
		}],
		sortOrder: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "sortOrder",
				required: false
			}]
		}],
		defaultSortOrder: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "defaultSortOrder",
				required: false
			}]
		}],
		sortChange: [{
			type: i0.Output,
			args: ["sortChange"]
		}]
	}
});
var TumUiTableSortableColumnComponent = class TumUiTableSortableColumnComponent {
	table = inject(TumUiTableDirective);
	field = input.required({
		...ngDevMode ? { debugName: "field" } : /* istanbul ignore next */ {},
		alias: "tumUiSortableColumn"
	});
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	direction = computed(() => {
		if (this.table.sortField() !== this.field()) return "none";
		const order = this.table.sortOrder();
		if (order === 0) return "none";
		return order < 0 ? "desc" : "asc";
	}, ...ngDevMode ? [{ debugName: "direction" }] : /* istanbul ignore next */ []);
	ariaSort = computed(() => {
		switch (this.direction()) {
			case "asc": return "ascending";
			case "desc": return "descending";
			default: return "none";
		}
	}, ...ngDevMode ? [{ debugName: "ariaSort" }] : /* istanbul ignore next */ []);
	sortIcon = computed(() => {
		switch (this.direction()) {
			case "asc": return faSortUp;
			case "desc": return faSortDown;
			default: return faSort;
		}
	}, ...ngDevMode ? [{ debugName: "sortIcon" }] : /* istanbul ignore next */ []);
	hostClasses = computed(() => this.disabled() ? "" : "tum:cursor-pointer tum:select-none tum:hover:bg-hover-background", ...ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []);
	onActivate() {
		this.table.requestSort(this.field());
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTableSortableColumnComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiTableSortableColumnComponent,
		isStandalone: true,
		selector: "th[tumUiSortableColumn]",
		inputs: {
			field: {
				classPropertyName: "field",
				publicName: "tumUiSortableColumn",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: { properties: {
			"class": "hostClasses()",
			"attr.aria-sort": "ariaSort()"
		} },
		ngImport: i0,
		template: "<button type=\"button\" class=\"tum-ui-sort-button\" [disabled]=\"disabled()\" (click)=\"onActivate()\">\n    <ng-content />\n    <fa-icon [icon]=\"sortIcon()\" class=\"tum-ui-sort-icon tum:text-muted\" aria-hidden=\"true\" />\n</button>\n",
		styles: [":host{white-space:nowrap}.tum-ui-sort-button{display:flex;align-items:center;width:100%;padding:0;border:0;background:transparent;color:inherit;font:inherit;text-align:inherit;cursor:pointer}.tum-ui-sort-button:disabled{cursor:default}.tum-ui-sort-icon{display:inline-block;width:calc(var(--tumaet-ui-spacing) * 3.5);height:calc(var(--tumaet-ui-spacing) * 3.5);margin-inline-start:calc(var(--tumaet-ui-spacing) * 2);vertical-align:middle;flex-shrink:0}\n"],
		dependencies: [{
			kind: "component",
			type: FaIconComponent,
			selector: "fa-icon",
			inputs: [
				"icon",
				"title",
				"animation",
				"mask",
				"flip",
				"size",
				"pull",
				"border",
				"inverse",
				"symbol",
				"rotate",
				"fixedWidth",
				"transform",
				"a11yRole"
			],
			outputs: [
				"iconChange",
				"titleChange",
				"animationChange",
				"maskChange",
				"flipChange",
				"sizeChange",
				"pullChange",
				"borderChange",
				"inverseChange",
				"symbolChange",
				"rotateChange",
				"fixedWidthChange",
				"transformChange",
				"a11yRoleChange"
			]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTableSortableColumnComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "th[tumUiSortableColumn]",
			imports: [FaIconComponent],
			host: {
				"[class]": "hostClasses()",
				"[attr.aria-sort]": "ariaSort()"
			},
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<button type=\"button\" class=\"tum-ui-sort-button\" [disabled]=\"disabled()\" (click)=\"onActivate()\">\n    <ng-content />\n    <fa-icon [icon]=\"sortIcon()\" class=\"tum-ui-sort-icon tum:text-muted\" aria-hidden=\"true\" />\n</button>\n",
			styles: [":host{white-space:nowrap}.tum-ui-sort-button{display:flex;align-items:center;width:100%;padding:0;border:0;background:transparent;color:inherit;font:inherit;text-align:inherit;cursor:pointer}.tum-ui-sort-button:disabled{cursor:default}.tum-ui-sort-icon{display:inline-block;width:calc(var(--tumaet-ui-spacing) * 3.5);height:calc(var(--tumaet-ui-spacing) * 3.5);margin-inline-start:calc(var(--tumaet-ui-spacing) * 2);vertical-align:middle;flex-shrink:0}\n"]
		}]
	}],
	propDecorators: {
		field: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiSortableColumn",
				required: true
			}]
		}],
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}]
	}
});
const HEADER_PADDING = {
	small: "tum:px-2 tum:py-1.5",
	normal: "tum:px-4 tum:py-3",
	large: "tum:px-5 tum:py-4"
};
var TumUiTableVirtualScrollComponent = class TumUiTableVirtualScrollComponent {
	items = input.required(...ngDevMode ? [{ debugName: "items" }] : /* istanbul ignore next */ []);
	itemSize = input.required(...ngDevMode ? [{ debugName: "itemSize" }] : /* istanbul ignore next */ []);
	rowTemplate = input.required(...ngDevMode ? [{ debugName: "rowTemplate" }] : /* istanbul ignore next */ []);
	size = input("normal", ...ngDevMode ? [{ debugName: "size" }] : /* istanbul ignore next */ []);
	striped = input(false, {
		...ngDevMode ? { debugName: "striped" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	rowHover = input(false, {
		...ngDevMode ? { debugName: "rowHover" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	scrollHeight = input("flex", ...ngDevMode ? [{ debugName: "scrollHeight" }] : /* istanbul ignore next */ []);
	minWidth = input(void 0, ...ngDevMode ? [{ debugName: "minWidth" }] : /* istanbul ignore next */ []);
	trackBy = input(void 0, ...ngDevMode ? [{ debugName: "trackBy" }] : /* istanbul ignore next */ []);
	ariaDescribedBy = input(void 0, ...ngDevMode ? [{ debugName: "ariaDescribedBy" }] : /* istanbul ignore next */ []);
	isFlexHeight = computed(() => this.scrollHeight() === "flex", ...ngDevMode ? [{ debugName: "isFlexHeight" }] : /* istanbul ignore next */ []);
	viewportHeight = computed(() => this.isFlexHeight() ? void 0 : this.scrollHeight(), ...ngDevMode ? [{ debugName: "viewportHeight" }] : /* istanbul ignore next */ []);
	effectiveTrackBy = computed(() => this.trackBy() ?? ((_, item) => item), ...ngDevMode ? [{ debugName: "effectiveTrackBy" }] : /* istanbul ignore next */ []);
	headerClasses = computed(() => {
		return `tum-ui-vs-header tum:box-border tum:flex tum:text-sm tum:font-semibold tum:text-text tum:bg-content-background tum:border-b tum:border-border ${HEADER_PADDING[this.size()]}`;
	}, ...ngDevMode ? [{ debugName: "headerClasses" }] : /* istanbul ignore next */ []);
	rowClasses = computed(() => {
		return `tum-ui-vs-row tum:box-border tum:flex tum:items-center tum:text-sm tum:text-text tum:border-b tum:border-border${this.rowHover() ? " tum:hover:bg-hover-background" : ""} ${HEADER_PADDING[this.size()]}`;
	}, ...ngDevMode ? [{ debugName: "rowClasses" }] : /* istanbul ignore next */ []);
	stripeClass(index) {
		return this.striped() && index % 2 === 0 ? " tum:bg-table-striped-background" : "";
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTableVirtualScrollComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiTableVirtualScrollComponent,
		isStandalone: true,
		selector: "tum-ui-table-virtual-scroll",
		inputs: {
			items: {
				classPropertyName: "items",
				publicName: "items",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			itemSize: {
				classPropertyName: "itemSize",
				publicName: "itemSize",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			rowTemplate: {
				classPropertyName: "rowTemplate",
				publicName: "rowTemplate",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			size: {
				classPropertyName: "size",
				publicName: "size",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			striped: {
				classPropertyName: "striped",
				publicName: "striped",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			rowHover: {
				classPropertyName: "rowHover",
				publicName: "rowHover",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			scrollHeight: {
				classPropertyName: "scrollHeight",
				publicName: "scrollHeight",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			minWidth: {
				classPropertyName: "minWidth",
				publicName: "minWidth",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			trackBy: {
				classPropertyName: "trackBy",
				publicName: "trackBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaDescribedBy: {
				classPropertyName: "ariaDescribedBy",
				publicName: "ariaDescribedBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		ngImport: i0,
		template: "<div\n    role=\"table\"\n    class=\"tum-ui-vs-table tum:flex tum:min-h-0 tum:flex-col\"\n    [style.flex]=\"isFlexHeight() ? '1 1 0%' : null\"\n    [style.min-width]=\"minWidth()\"\n    [attr.aria-describedby]=\"ariaDescribedBy()\"\n    [attr.aria-rowcount]=\"items().length + 1\"\n>\n    <div role=\"row\" aria-rowindex=\"1\" [class]=\"headerClasses()\">\n        <ng-content />\n    </div>\n\n    <cdk-virtual-scroll-viewport\n        [itemSize]=\"itemSize()\"\n        class=\"tum-ui-vs-viewport\"\n        [style.flex]=\"isFlexHeight() ? '1 1 0%' : null\"\n        [style.min-height]=\"isFlexHeight() ? 0 : null\"\n        [style.height]=\"viewportHeight()\"\n        role=\"rowgroup\"\n        tabindex=\"0\"\n    >\n        <div\n            *cdkVirtualFor=\"let item of items(); let i = index; trackBy: effectiveTrackBy()\"\n            role=\"row\"\n            [attr.aria-rowindex]=\"i + 2\"\n            [class]=\"rowClasses() + stripeClass(i)\"\n            [style.height.px]=\"itemSize()\"\n        >\n            <ng-container [ngTemplateOutlet]=\"rowTemplate()\" [ngTemplateOutletContext]=\"{ $implicit: item, index: i }\" />\n        </div>\n    </cdk-virtual-scroll-viewport>\n</div>\n",
		styles: [":host{display:flex;flex-direction:column;width:100%;max-width:100%;height:100%;min-height:0;min-width:0;overflow-x:auto}.tum-ui-vs-viewport{width:100%}\n"],
		dependencies: [
			{
				kind: "ngmodule",
				type: ScrollingModule
			},
			{
				kind: "directive",
				type: i1$2.CdkFixedSizeVirtualScroll,
				selector: "cdk-virtual-scroll-viewport[itemSize]",
				inputs: [
					"itemSize",
					"minBufferPx",
					"maxBufferPx"
				]
			},
			{
				kind: "directive",
				type: i1$2.CdkVirtualForOf,
				selector: "[cdkVirtualFor][cdkVirtualForOf]",
				inputs: [
					"cdkVirtualForOf",
					"cdkVirtualForTrackBy",
					"cdkVirtualForTemplate",
					"cdkVirtualForTemplateCacheSize"
				]
			},
			{
				kind: "component",
				type: i1$2.CdkVirtualScrollViewport,
				selector: "cdk-virtual-scroll-viewport",
				inputs: ["orientation", "appendOnly"],
				outputs: ["scrolledIndexChange"]
			},
			{
				kind: "directive",
				type: NgTemplateOutlet,
				selector: "[ngTemplateOutlet]",
				inputs: [
					"ngTemplateOutletContext",
					"ngTemplateOutlet",
					"ngTemplateOutletInjector"
				]
			}
		],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTableVirtualScrollComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-table-virtual-scroll",
			imports: [ScrollingModule, NgTemplateOutlet],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<div\n    role=\"table\"\n    class=\"tum-ui-vs-table tum:flex tum:min-h-0 tum:flex-col\"\n    [style.flex]=\"isFlexHeight() ? '1 1 0%' : null\"\n    [style.min-width]=\"minWidth()\"\n    [attr.aria-describedby]=\"ariaDescribedBy()\"\n    [attr.aria-rowcount]=\"items().length + 1\"\n>\n    <div role=\"row\" aria-rowindex=\"1\" [class]=\"headerClasses()\">\n        <ng-content />\n    </div>\n\n    <cdk-virtual-scroll-viewport\n        [itemSize]=\"itemSize()\"\n        class=\"tum-ui-vs-viewport\"\n        [style.flex]=\"isFlexHeight() ? '1 1 0%' : null\"\n        [style.min-height]=\"isFlexHeight() ? 0 : null\"\n        [style.height]=\"viewportHeight()\"\n        role=\"rowgroup\"\n        tabindex=\"0\"\n    >\n        <div\n            *cdkVirtualFor=\"let item of items(); let i = index; trackBy: effectiveTrackBy()\"\n            role=\"row\"\n            [attr.aria-rowindex]=\"i + 2\"\n            [class]=\"rowClasses() + stripeClass(i)\"\n            [style.height.px]=\"itemSize()\"\n        >\n            <ng-container [ngTemplateOutlet]=\"rowTemplate()\" [ngTemplateOutletContext]=\"{ $implicit: item, index: i }\" />\n        </div>\n    </cdk-virtual-scroll-viewport>\n</div>\n",
			styles: [":host{display:flex;flex-direction:column;width:100%;max-width:100%;height:100%;min-height:0;min-width:0;overflow-x:auto}.tum-ui-vs-viewport{width:100%}\n"]
		}]
	}],
	propDecorators: {
		items: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "items",
				required: true
			}]
		}],
		itemSize: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "itemSize",
				required: true
			}]
		}],
		rowTemplate: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "rowTemplate",
				required: true
			}]
		}],
		size: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "size",
				required: false
			}]
		}],
		striped: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "striped",
				required: false
			}]
		}],
		rowHover: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "rowHover",
				required: false
			}]
		}],
		scrollHeight: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "scrollHeight",
				required: false
			}]
		}],
		minWidth: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "minWidth",
				required: false
			}]
		}],
		trackBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "trackBy",
				required: false
			}]
		}],
		ariaDescribedBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaDescribedBy",
				required: false
			}]
		}]
	}
});
const ACTIONS_COLUMN = "__tum_ui_actions__";
const SEARCH_DEBOUNCE_MS = 300;
const HIDE_BELOW_CLASSES = {
	sm: "tum:hidden tum:sm:table-cell",
	md: "tum:hidden tum:md:table-cell",
	lg: "tum:hidden tum:lg:table-cell",
	xl: "tum:hidden tum:xl:table-cell",
	"2xl": "tum:hidden tum:2xl:table-cell"
};
var TumUiTableComponent = class TumUiTableComponent {
	destroyRef = inject(DestroyRef);
	columns = input.required(...ngDevMode ? [{ debugName: "columns" }] : /* istanbul ignore next */ []);
	rows = input.required(...ngDevMode ? [{ debugName: "rows" }] : /* istanbul ignore next */ []);
	totalRecords = input(0, {
		...ngDevMode ? { debugName: "totalRecords" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	loading = input(false, {
		...ngDevMode ? { debugName: "loading" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	rowActions = input(void 0, ...ngDevMode ? [{ debugName: "rowActions" }] : /* istanbul ignore next */ []);
	rowHighlighted = input(void 0, ...ngDevMode ? [{ debugName: "rowHighlighted" }] : /* istanbul ignore next */ []);
	trackBy = input(void 0, ...ngDevMode ? [{ debugName: "trackBy" }] : /* istanbul ignore next */ []);
	striped = input(false, {
		...ngDevMode ? { debugName: "striped" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	scrollable = input(false, {
		...ngDevMode ? { debugName: "scrollable" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	scrollHeight = input(void 0, ...ngDevMode ? [{ debugName: "scrollHeight" }] : /* istanbul ignore next */ []);
	showSearch = input(true, {
		...ngDevMode ? { debugName: "showSearch" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	searchPlaceholder = input("tumUi.table.searchPlaceholder", ...ngDevMode ? [{ debugName: "searchPlaceholder" }] : /* istanbul ignore next */ []);
	emptyMessage = input("tumUi.table.noResults", ...ngDevMode ? [{ debugName: "emptyMessage" }] : /* istanbul ignore next */ []);
	pageSize = input(50, {
		...ngDevMode ? { debugName: "pageSize" } : /* istanbul ignore next */ {},
		transform: numberAttribute
	});
	pageSizeOptions = input([
		10,
		20,
		50,
		100,
		200
	], ...ngDevMode ? [{ debugName: "pageSizeOptions" }] : /* istanbul ignore next */ []);
	showRowsPerPage = input(true, {
		...ngDevMode ? { debugName: "showRowsPerPage" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	showCurrentPageReport = input(true, {
		...ngDevMode ? { debugName: "showCurrentPageReport" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	initialSortField = input(void 0, ...ngDevMode ? [{ debugName: "initialSortField" }] : /* istanbul ignore next */ []);
	initialSortDirection = input("asc", ...ngDevMode ? [{ debugName: "initialSortDirection" }] : /* istanbul ignore next */ []);
	dataRequest = output();
	ACTIONS_COLUMN = ACTIONS_COLUMN;
	faCircleQuestion = faCircleQuestion;
	faMagnifyingGlass = faMagnifyingGlass;
	faSort = faSort;
	faSortDown = faSortDown;
	faSortUp = faSortUp;
	cdkTable = viewChild(CdkTable, ...ngDevMode ? [{ debugName: "cdkTable" }] : /* istanbul ignore next */ []);
	page = signal(0, ...ngDevMode ? [{ debugName: "page" }] : /* istanbul ignore next */ []);
	pageSizeState = signal(void 0, ...ngDevMode ? [{ debugName: "pageSizeState" }] : /* istanbul ignore next */ []);
	sortState = signal(void 0, ...ngDevMode ? [{ debugName: "sortState" }] : /* istanbul ignore next */ []);
	searchTerm = signal("", ...ngDevMode ? [{ debugName: "searchTerm" }] : /* istanbul ignore next */ []);
	searchTimer;
	effectivePageSize = computed(() => this.pageSizeState() ?? this.pageSize(), ...ngDevMode ? [{ debugName: "effectivePageSize" }] : /* istanbul ignore next */ []);
	currentPage = computed(() => this.page(), ...ngDevMode ? [{ debugName: "currentPage" }] : /* istanbul ignore next */ []);
	effectiveTrackBy = computed(() => this.trackBy() ?? ((_, item) => item), ...ngDevMode ? [{ debugName: "effectiveTrackBy" }] : /* istanbul ignore next */ []);
	displayedColumns = computed(() => {
		const names = this.columns().map((col, index) => this.columnName(col, index));
		return this.rowActions() ? [...names, ACTIONS_COLUMN] : names;
	}, ...ngDevMode ? [{ debugName: "displayedColumns" }] : /* istanbul ignore next */ []);
	tableClasses = computed(() => {
		const base = "tum:w-full tum:border-collapse tum:text-sm";
		return this.striped() ? `${base} tum:[&_tbody_tr:nth-child(odd)]:bg-table-striped-background` : base;
	}, ...ngDevMode ? [{ debugName: "tableClasses" }] : /* istanbul ignore next */ []);
	constructor() {
		afterNextRender(() => {
			const field = this.initialSortField();
			if (field) this.sortState.set({
				field,
				direction: this.initialSortDirection()
			});
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
			if (this.page() > lastPage) untracked(() => {
				this.page.set(lastPage);
				this.emitDataRequest();
			});
		});
		this.destroyRef.onDestroy(() => clearTimeout(this.searchTimer));
	}
	resetPage() {
		if (this.page() === 0) return;
		this.page.set(0);
		this.emitDataRequest();
	}
	columnName(col, index) {
		return col.field ?? col.headerKey ?? col.header ?? `col-${index}`;
	}
	resolveValue(row, col) {
		return col.field ? get(row, col.field) : void 0;
	}
	cellParams(row, col, rowIndex) {
		return {
			data: row,
			col,
			value: this.resolveValue(row, col),
			rowIndex
		};
	}
	columnVisibilityClasses(col) {
		return col.hideBelow ? HIDE_BELOW_CLASSES[col.hideBelow] : "";
	}
	headerCellClasses(col) {
		const whitespace = col.wrapHeader ? "" : "tum:whitespace-nowrap";
		return `${this.columnVisibilityClasses(col)} ${whitespace}`.trim();
	}
	ariaSortFor(col) {
		if (!col.sort || !col.field) return;
		const sort = this.sortState();
		if (!sort || sort.field !== col.field) return "none";
		return sort.direction === "asc" ? "ascending" : "descending";
	}
	sortDirection(col) {
		const sort = this.sortState();
		if (!sort || sort.field !== col.field) return "none";
		return sort.direction;
	}
	sortIcon(col) {
		switch (this.sortDirection(col)) {
			case "asc": return this.faSortUp;
			case "desc": return this.faSortDown;
			default: return this.faSort;
		}
	}
	onSortClick(col) {
		if (!col.sort || !col.field) return;
		const current = this.sortState();
		this.sortState.set(current && current.field === col.field ? {
			field: col.field,
			direction: current.direction === "asc" ? "desc" : "asc"
		} : {
			field: col.field,
			direction: "asc"
		});
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
			searchTerm: this.searchTerm().trim() || void 0
		});
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTableComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiTableComponent,
		isStandalone: true,
		selector: "tum-ui-table",
		inputs: {
			columns: {
				classPropertyName: "columns",
				publicName: "columns",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			rows: {
				classPropertyName: "rows",
				publicName: "rows",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			totalRecords: {
				classPropertyName: "totalRecords",
				publicName: "totalRecords",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			loading: {
				classPropertyName: "loading",
				publicName: "loading",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			rowActions: {
				classPropertyName: "rowActions",
				publicName: "rowActions",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			rowHighlighted: {
				classPropertyName: "rowHighlighted",
				publicName: "rowHighlighted",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			trackBy: {
				classPropertyName: "trackBy",
				publicName: "trackBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			striped: {
				classPropertyName: "striped",
				publicName: "striped",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			scrollable: {
				classPropertyName: "scrollable",
				publicName: "scrollable",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			scrollHeight: {
				classPropertyName: "scrollHeight",
				publicName: "scrollHeight",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			showSearch: {
				classPropertyName: "showSearch",
				publicName: "showSearch",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			searchPlaceholder: {
				classPropertyName: "searchPlaceholder",
				publicName: "searchPlaceholder",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			emptyMessage: {
				classPropertyName: "emptyMessage",
				publicName: "emptyMessage",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			pageSize: {
				classPropertyName: "pageSize",
				publicName: "pageSize",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			pageSizeOptions: {
				classPropertyName: "pageSizeOptions",
				publicName: "pageSizeOptions",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			showRowsPerPage: {
				classPropertyName: "showRowsPerPage",
				publicName: "showRowsPerPage",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			showCurrentPageReport: {
				classPropertyName: "showCurrentPageReport",
				publicName: "showCurrentPageReport",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			initialSortField: {
				classPropertyName: "initialSortField",
				publicName: "initialSortField",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			initialSortDirection: {
				classPropertyName: "initialSortDirection",
				publicName: "initialSortDirection",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { dataRequest: "dataRequest" },
		viewQueries: [{
			propertyName: "cdkTable",
			first: true,
			predicate: CdkTable,
			descendants: true,
			isSignal: true
		}],
		ngImport: i0,
		template: "@if (showSearch()) {\n    <div class=\"tum:mb-3\">\n        <div class=\"tum:relative\">\n            <fa-icon [icon]=\"faMagnifyingGlass\" class=\"tum:pointer-events-none tum:absolute tum:start-3 tum:top-1/2 tum:-translate-y-1/2 tum:text-muted\" />\n            <input\n                #searchInput\n                type=\"search\"\n                [placeholder]=\"searchPlaceholder() | tumUiTranslate\"\n                [attr.aria-label]=\"searchPlaceholder() | tumUiTranslate\"\n                (input)=\"onSearchInput(searchInput.value)\"\n                class=\"tum:box-border tum:w-full tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1.5 tum:ps-10 tum:pe-3 tum:text-sm tum:text-text\"\n            />\n        </div>\n    </div>\n}\n\n<div\n    class=\"tum:relative tum:w-full tum:max-w-full tum:overflow-x-auto\"\n    [style.overflow-y]=\"scrollable() ? 'auto' : null\"\n    [style.flex]=\"scrollHeight() === 'flex' ? '1 1 0%' : null\"\n    [style.min-height]=\"scrollHeight() === 'flex' ? 0 : null\"\n    [style.max-height]=\"scrollHeight() && scrollHeight() !== 'flex' ? scrollHeight() : null\"\n>\n    <table cdk-table [dataSource]=\"rows()\" [trackBy]=\"effectiveTrackBy()\" [class]=\"tableClasses()\" [attr.aria-busy]=\"loading() ? 'true' : null\">\n        @for (col of columns(); track columnName(col, $index)) {\n            <ng-container [cdkColumnDef]=\"columnName(col, $index)\">\n                <th\n                    cdk-header-cell\n                    *cdkHeaderCellDef\n                    scope=\"col\"\n                    [style.min-width]=\"col.width\"\n                    [attr.aria-sort]=\"ariaSortFor(col)\"\n                    [class]=\"headerCellClasses(col)\"\n                    class=\"tum:border-border tum:bg-content-background tum:px-4 tum:py-2 tum:text-start tum:font-semibold tum:text-text\"\n                >\n                    <span class=\"tum:inline-flex tum:items-center tum:gap-1\">\n                        @if (col.sort && col.field) {\n                            <button\n                                type=\"button\"\n                                class=\"tum:inline-flex tum:cursor-pointer tum:appearance-none tum:items-center tum:gap-2 tum:border-0 tum:bg-transparent tum:p-0 tum:font-semibold tum:text-text tum:hover:text-accent\"\n                                (click)=\"onSortClick(col)\"\n                            >\n                                @if (col.headerKey) {\n                                    <span>{{ col.headerKey | tumUiTranslate }}</span>\n                                } @else {\n                                    <span>{{ col.header }}</span>\n                                }\n                                <fa-icon [icon]=\"sortIcon(col)\" class=\"tum:shrink-0 tum:text-muted\" />\n                            </button>\n                        } @else if (col.headerKey) {\n                            <span>{{ col.headerKey | tumUiTranslate }}</span>\n                        } @else {\n                            <span>{{ col.header }}</span>\n                        }\n                        @if (col.headerTooltip; as headerTooltip) {\n                            @let tooltipText = headerTooltip | tumUiTranslate;\n                            <button\n                                type=\"button\"\n                                class=\"tum:inline-flex tum:cursor-help tum:appearance-none tum:items-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted\"\n                                [tumUiTooltip]=\"tooltipText\"\n                                [attr.aria-label]=\"tooltipText\"\n                            >\n                                <fa-icon [icon]=\"faCircleQuestion\" />\n                            </button>\n                        }\n                    </span>\n                </th>\n                <td cdk-cell *cdkCellDef=\"let row; let i = index\" [class]=\"columnVisibilityClasses(col)\" class=\"tum:border-border tum:px-4 tum:py-2 tum:text-text\">\n                    @if (col.templateRef) {\n                        <ng-container [ngTemplateOutlet]=\"col.templateRef\" [ngTemplateOutletContext]=\"{ $implicit: cellParams(row, col, i) }\" />\n                    } @else {\n                        {{ resolveValue(row, col) }}\n                    }\n                </td>\n            </ng-container>\n        }\n\n        @if (rowActions(); as actions) {\n            <ng-container [cdkColumnDef]=\"ACTIONS_COLUMN\">\n                <th cdk-header-cell *cdkHeaderCellDef scope=\"col\" class=\"tum:border-border tum:bg-content-background tum:px-4 tum:py-2\">\n                    <span class=\"tum:sr-only\">{{ 'tumUi.table.actions' | tumUiTranslate }}</span>\n                </th>\n                <!-- Declares no width, so it is the one column auto layout can squeeze: nowrap keeps the controls on one line. -->\n                <td cdk-cell *cdkCellDef=\"let row\" class=\"tum:border-border tum:px-4 tum:py-2 tum:text-end tum:whitespace-nowrap\">\n                    <ng-container [ngTemplateOutlet]=\"actions\" [ngTemplateOutletContext]=\"{ $implicit: row }\" />\n                </td>\n            </ng-container>\n        }\n\n        <tr cdk-header-row *cdkHeaderRowDef=\"displayedColumns(); sticky: scrollable()\" class=\"tum:bg-content-background\"></tr>\n        <tr cdk-row *cdkRowDef=\"let row; columns: displayedColumns()\" [class.tum-ui-table-row-highlighted]=\"rowHighlighted()?.(row) ?? false\"></tr>\n\n        <tr *cdkNoDataRow>\n            <td [attr.colspan]=\"displayedColumns().length\" class=\"tum:bg-content-background tum:px-4 tum:py-6 tum:text-center tum:text-muted\">\n                <span>{{ emptyMessage() | tumUiTranslate }}</span>\n            </td>\n        </tr>\n    </table>\n\n    @if (loading()) {\n        <div class=\"tum:absolute tum:inset-0 tum:flex tum:items-center tum:justify-center tum:bg-content-background/60\" aria-hidden=\"true\">\n            <span class=\"tum:h-6 tum:w-6 tum:animate-spin tum:rounded-full tum:border-2 tum:border-border tum:border-t-primary tum:motion-reduce:animate-none\"></span>\n        </div>\n    }\n</div>\n\n<tum-ui-paginator\n    [totalRecords]=\"totalRecords()\"\n    [page]=\"currentPage()\"\n    [pageSize]=\"effectivePageSize()\"\n    [pageSizeOptions]=\"pageSizeOptions()\"\n    [showRowsPerPage]=\"showRowsPerPage()\"\n    [showCurrentPageReport]=\"showCurrentPageReport()\"\n    [disabled]=\"loading()\"\n    (pageChange)=\"onPageChange($event)\"\n    (pageSizeChange)=\"onPageSizeChange($event)\"\n/>\n",
		styles: [":host{display:flex;flex-direction:column;height:100%;min-height:0}.cdk-table{width:100%}.cdk-table th,.cdk-table td{border-bottom-width:1px;border-bottom-style:solid}.cdk-table tr:last-child td{border-bottom-width:0}.cdk-table tr.tum-ui-table-row-highlighted{background-color:var(--tumaet-ui-highlight-background);color:var(--tumaet-ui-highlight-color);font-weight:500}\n"],
		dependencies: [
			{
				kind: "ngmodule",
				type: CdkTableModule
			},
			{
				kind: "component",
				type: i1$1.CdkTable,
				selector: "cdk-table, table[cdk-table]",
				inputs: [
					"trackBy",
					"dataSource",
					"multiTemplateDataRows",
					"fixedLayout",
					"recycleRows"
				],
				outputs: ["contentChanged"],
				exportAs: ["cdkTable"]
			},
			{
				kind: "directive",
				type: i1$1.CdkRowDef,
				selector: "[cdkRowDef]",
				inputs: ["cdkRowDefColumns", "cdkRowDefWhen"]
			},
			{
				kind: "directive",
				type: i1$1.CdkCellDef,
				selector: "[cdkCellDef]"
			},
			{
				kind: "directive",
				type: i1$1.CdkHeaderCellDef,
				selector: "[cdkHeaderCellDef]"
			},
			{
				kind: "directive",
				type: i1$1.CdkColumnDef,
				selector: "[cdkColumnDef]",
				inputs: [
					"cdkColumnDef",
					"sticky",
					"stickyEnd"
				]
			},
			{
				kind: "directive",
				type: i1$1.CdkCell,
				selector: "cdk-cell, td[cdk-cell]"
			},
			{
				kind: "component",
				type: i1$1.CdkRow,
				selector: "cdk-row, tr[cdk-row]"
			},
			{
				kind: "directive",
				type: i1$1.CdkHeaderCell,
				selector: "cdk-header-cell, th[cdk-header-cell]"
			},
			{
				kind: "component",
				type: i1$1.CdkHeaderRow,
				selector: "cdk-header-row, tr[cdk-header-row]"
			},
			{
				kind: "directive",
				type: i1$1.CdkHeaderRowDef,
				selector: "[cdkHeaderRowDef]",
				inputs: ["cdkHeaderRowDef", "cdkHeaderRowDefSticky"]
			},
			{
				kind: "directive",
				type: i1$1.CdkNoDataRow,
				selector: "ng-template[cdkNoDataRow]"
			},
			{
				kind: "directive",
				type: NgTemplateOutlet,
				selector: "[ngTemplateOutlet]",
				inputs: [
					"ngTemplateOutletContext",
					"ngTemplateOutlet",
					"ngTemplateOutletInjector"
				]
			},
			{
				kind: "component",
				type: FaIconComponent,
				selector: "fa-icon",
				inputs: [
					"icon",
					"title",
					"animation",
					"mask",
					"flip",
					"size",
					"pull",
					"border",
					"inverse",
					"symbol",
					"rotate",
					"fixedWidth",
					"transform",
					"a11yRole"
				],
				outputs: [
					"iconChange",
					"titleChange",
					"animationChange",
					"maskChange",
					"flipChange",
					"sizeChange",
					"pullChange",
					"borderChange",
					"inverseChange",
					"symbolChange",
					"rotateChange",
					"fixedWidthChange",
					"transformChange",
					"a11yRoleChange"
				]
			},
			{
				kind: "component",
				type: TumUiPaginatorComponent,
				selector: "tum-ui-paginator",
				inputs: [
					"ariaLabel",
					"totalRecords",
					"page",
					"pageSize",
					"pageSizeOptions",
					"disabled",
					"showCurrentPageReport",
					"showRowsPerPage"
				],
				outputs: ["pageChange", "pageSizeChange"]
			},
			{
				kind: "directive",
				type: TumUiTooltipDirective,
				selector: "[tumUiTooltip]",
				inputs: [
					"tumUiTooltip",
					"tumUiTooltipPlacement",
					"tumUiTooltipDescribesHost",
					"showDelayMs",
					"hideDelayMs"
				]
			},
			{
				kind: "pipe",
				type: TumUiTranslatePipe,
				name: "tumUiTranslate"
			}
		],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTableComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-table",
			imports: [
				CdkTableModule,
				NgTemplateOutlet,
				FaIconComponent,
				TumUiTranslatePipe,
				TumUiPaginatorComponent,
				TumUiTooltipDirective
			],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "@if (showSearch()) {\n    <div class=\"tum:mb-3\">\n        <div class=\"tum:relative\">\n            <fa-icon [icon]=\"faMagnifyingGlass\" class=\"tum:pointer-events-none tum:absolute tum:start-3 tum:top-1/2 tum:-translate-y-1/2 tum:text-muted\" />\n            <input\n                #searchInput\n                type=\"search\"\n                [placeholder]=\"searchPlaceholder() | tumUiTranslate\"\n                [attr.aria-label]=\"searchPlaceholder() | tumUiTranslate\"\n                (input)=\"onSearchInput(searchInput.value)\"\n                class=\"tum:box-border tum:w-full tum:rounded-md tum:border tum:border-control-border tum:bg-control-background tum:py-1.5 tum:ps-10 tum:pe-3 tum:text-sm tum:text-text\"\n            />\n        </div>\n    </div>\n}\n\n<div\n    class=\"tum:relative tum:w-full tum:max-w-full tum:overflow-x-auto\"\n    [style.overflow-y]=\"scrollable() ? 'auto' : null\"\n    [style.flex]=\"scrollHeight() === 'flex' ? '1 1 0%' : null\"\n    [style.min-height]=\"scrollHeight() === 'flex' ? 0 : null\"\n    [style.max-height]=\"scrollHeight() && scrollHeight() !== 'flex' ? scrollHeight() : null\"\n>\n    <table cdk-table [dataSource]=\"rows()\" [trackBy]=\"effectiveTrackBy()\" [class]=\"tableClasses()\" [attr.aria-busy]=\"loading() ? 'true' : null\">\n        @for (col of columns(); track columnName(col, $index)) {\n            <ng-container [cdkColumnDef]=\"columnName(col, $index)\">\n                <th\n                    cdk-header-cell\n                    *cdkHeaderCellDef\n                    scope=\"col\"\n                    [style.min-width]=\"col.width\"\n                    [attr.aria-sort]=\"ariaSortFor(col)\"\n                    [class]=\"headerCellClasses(col)\"\n                    class=\"tum:border-border tum:bg-content-background tum:px-4 tum:py-2 tum:text-start tum:font-semibold tum:text-text\"\n                >\n                    <span class=\"tum:inline-flex tum:items-center tum:gap-1\">\n                        @if (col.sort && col.field) {\n                            <button\n                                type=\"button\"\n                                class=\"tum:inline-flex tum:cursor-pointer tum:appearance-none tum:items-center tum:gap-2 tum:border-0 tum:bg-transparent tum:p-0 tum:font-semibold tum:text-text tum:hover:text-accent\"\n                                (click)=\"onSortClick(col)\"\n                            >\n                                @if (col.headerKey) {\n                                    <span>{{ col.headerKey | tumUiTranslate }}</span>\n                                } @else {\n                                    <span>{{ col.header }}</span>\n                                }\n                                <fa-icon [icon]=\"sortIcon(col)\" class=\"tum:shrink-0 tum:text-muted\" />\n                            </button>\n                        } @else if (col.headerKey) {\n                            <span>{{ col.headerKey | tumUiTranslate }}</span>\n                        } @else {\n                            <span>{{ col.header }}</span>\n                        }\n                        @if (col.headerTooltip; as headerTooltip) {\n                            @let tooltipText = headerTooltip | tumUiTranslate;\n                            <button\n                                type=\"button\"\n                                class=\"tum:inline-flex tum:cursor-help tum:appearance-none tum:items-center tum:border-0 tum:bg-transparent tum:p-0 tum:text-muted\"\n                                [tumUiTooltip]=\"tooltipText\"\n                                [attr.aria-label]=\"tooltipText\"\n                            >\n                                <fa-icon [icon]=\"faCircleQuestion\" />\n                            </button>\n                        }\n                    </span>\n                </th>\n                <td cdk-cell *cdkCellDef=\"let row; let i = index\" [class]=\"columnVisibilityClasses(col)\" class=\"tum:border-border tum:px-4 tum:py-2 tum:text-text\">\n                    @if (col.templateRef) {\n                        <ng-container [ngTemplateOutlet]=\"col.templateRef\" [ngTemplateOutletContext]=\"{ $implicit: cellParams(row, col, i) }\" />\n                    } @else {\n                        {{ resolveValue(row, col) }}\n                    }\n                </td>\n            </ng-container>\n        }\n\n        @if (rowActions(); as actions) {\n            <ng-container [cdkColumnDef]=\"ACTIONS_COLUMN\">\n                <th cdk-header-cell *cdkHeaderCellDef scope=\"col\" class=\"tum:border-border tum:bg-content-background tum:px-4 tum:py-2\">\n                    <span class=\"tum:sr-only\">{{ 'tumUi.table.actions' | tumUiTranslate }}</span>\n                </th>\n                <!-- Declares no width, so it is the one column auto layout can squeeze: nowrap keeps the controls on one line. -->\n                <td cdk-cell *cdkCellDef=\"let row\" class=\"tum:border-border tum:px-4 tum:py-2 tum:text-end tum:whitespace-nowrap\">\n                    <ng-container [ngTemplateOutlet]=\"actions\" [ngTemplateOutletContext]=\"{ $implicit: row }\" />\n                </td>\n            </ng-container>\n        }\n\n        <tr cdk-header-row *cdkHeaderRowDef=\"displayedColumns(); sticky: scrollable()\" class=\"tum:bg-content-background\"></tr>\n        <tr cdk-row *cdkRowDef=\"let row; columns: displayedColumns()\" [class.tum-ui-table-row-highlighted]=\"rowHighlighted()?.(row) ?? false\"></tr>\n\n        <tr *cdkNoDataRow>\n            <td [attr.colspan]=\"displayedColumns().length\" class=\"tum:bg-content-background tum:px-4 tum:py-6 tum:text-center tum:text-muted\">\n                <span>{{ emptyMessage() | tumUiTranslate }}</span>\n            </td>\n        </tr>\n    </table>\n\n    @if (loading()) {\n        <div class=\"tum:absolute tum:inset-0 tum:flex tum:items-center tum:justify-center tum:bg-content-background/60\" aria-hidden=\"true\">\n            <span class=\"tum:h-6 tum:w-6 tum:animate-spin tum:rounded-full tum:border-2 tum:border-border tum:border-t-primary tum:motion-reduce:animate-none\"></span>\n        </div>\n    }\n</div>\n\n<tum-ui-paginator\n    [totalRecords]=\"totalRecords()\"\n    [page]=\"currentPage()\"\n    [pageSize]=\"effectivePageSize()\"\n    [pageSizeOptions]=\"pageSizeOptions()\"\n    [showRowsPerPage]=\"showRowsPerPage()\"\n    [showCurrentPageReport]=\"showCurrentPageReport()\"\n    [disabled]=\"loading()\"\n    (pageChange)=\"onPageChange($event)\"\n    (pageSizeChange)=\"onPageSizeChange($event)\"\n/>\n",
			styles: [":host{display:flex;flex-direction:column;height:100%;min-height:0}.cdk-table{width:100%}.cdk-table th,.cdk-table td{border-bottom-width:1px;border-bottom-style:solid}.cdk-table tr:last-child td{border-bottom-width:0}.cdk-table tr.tum-ui-table-row-highlighted{background-color:var(--tumaet-ui-highlight-background);color:var(--tumaet-ui-highlight-color);font-weight:500}\n"]
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		columns: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "columns",
				required: true
			}]
		}],
		rows: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "rows",
				required: true
			}]
		}],
		totalRecords: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "totalRecords",
				required: false
			}]
		}],
		loading: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "loading",
				required: false
			}]
		}],
		rowActions: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "rowActions",
				required: false
			}]
		}],
		rowHighlighted: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "rowHighlighted",
				required: false
			}]
		}],
		trackBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "trackBy",
				required: false
			}]
		}],
		striped: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "striped",
				required: false
			}]
		}],
		scrollable: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "scrollable",
				required: false
			}]
		}],
		scrollHeight: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "scrollHeight",
				required: false
			}]
		}],
		showSearch: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "showSearch",
				required: false
			}]
		}],
		searchPlaceholder: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "searchPlaceholder",
				required: false
			}]
		}],
		emptyMessage: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "emptyMessage",
				required: false
			}]
		}],
		pageSize: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "pageSize",
				required: false
			}]
		}],
		pageSizeOptions: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "pageSizeOptions",
				required: false
			}]
		}],
		showRowsPerPage: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "showRowsPerPage",
				required: false
			}]
		}],
		showCurrentPageReport: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "showCurrentPageReport",
				required: false
			}]
		}],
		initialSortField: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "initialSortField",
				required: false
			}]
		}],
		initialSortDirection: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "initialSortDirection",
				required: false
			}]
		}],
		dataRequest: [{
			type: i0.Output,
			args: ["dataRequest"]
		}],
		cdkTable: [{
			type: i0.ViewChild,
			args: [i0.forwardRef(() => CdkTable), { isSignal: true }]
		}]
	}
});
function tabKey(value) {
	return `${typeof value === "number" ? "number" : "string"}:${value}`;
}
function tabValue(key) {
	const separator = key.indexOf(":");
	const value = key.slice(separator + 1);
	return key.slice(0, separator) === "number" ? Number(value) : value;
}
var TumUiTabsService = class TumUiTabsService {
	source = signal(signal(void 0), ...ngDevMode ? [{ debugName: "source" }] : /* istanbul ignore next */ []);
	onSelect = () => {};
	tabSet = signal(/* @__PURE__ */ new Set(), ...ngDevMode ? [{ debugName: "tabSet" }] : /* istanbul ignore next */ []);
	panelSet = signal(/* @__PURE__ */ new Set(), ...ngDevMode ? [{ debugName: "panelSet" }] : /* istanbul ignore next */ []);
	active = computed(() => this.source()(), ...ngDevMode ? [{ debugName: "active" }] : /* istanbul ignore next */ []);
	orderedTabs() {
		return [...this.tabSet()].sort((first, second) => first.element.compareDocumentPosition(second.element) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1);
	}
	keysWithoutPanel = computed(() => {
		const panelKeys = new Set([...this.panelSet()].map((panel) => panel.key()));
		return [...this.tabSet()].map((tab) => tab.key()).filter((key) => !panelKeys.has(key));
	}, ...ngDevMode ? [{ debugName: "keysWithoutPanel" }] : /* istanbul ignore next */ []);
	register(value, onSelect) {
		this.source.set(value);
		this.onSelect = onSelect;
	}
	select(value) {
		this.onSelect(value);
	}
	addTab(tab) {
		return this.track(this.tabSet, tab);
	}
	addPanel(panel) {
		return this.track(this.panelSet, panel);
	}
	track(members, member) {
		members.update((current) => new Set(current).add(member));
		return () => members.update((current) => {
			const remaining = new Set(current);
			remaining.delete(member);
			return remaining;
		});
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTabsService,
		deps: [],
		target: i0.ɵɵFactoryTarget.Injectable
	});
	static ɵprov = i0.ɵɵngDeclareInjectable({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTabsService
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTabsService,
	decorators: [{ type: Injectable }]
});
var TumUiTabComponent = class TumUiTabComponent extends Tab {
	tabsService = inject(TumUiTabsService);
	removeFromTabs;
	tabValue = input.required({
		...ngDevMode ? { debugName: "tabValue" } : /* istanbul ignore next */ {},
		alias: "value"
	});
	value = model("", {
		...ngDevMode ? { debugName: "value" } : /* istanbul ignore next */ {},
		alias: "tumUiTabKey"
	});
	constructor() {
		super();
		effect(() => {
			const key = tabKey(this.tabValue());
			untracked(() => this.value.set(key));
		});
	}
	hostClasses = computed(() => {
		return `tum-ui-tab tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus ${this.selected() ? "tum:text-accent" : "tum:text-muted tum:hover:text-text"} ${this.disabled() ? "tum-ui-tab-disabled" : ""}`.trim();
	}, ...ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []);
	ngOnInit() {
		this.value.set(tabKey(this.tabValue()));
		super.ngOnInit();
		this.removeFromTabs = this.tabsService.addTab({
			key: this.value,
			element: this.element,
			disabled: this.disabled,
			selected: this.selected
		});
	}
	ngOnDestroy() {
		this.removeFromTabs?.();
		super.ngOnDestroy();
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTabComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiTabComponent,
		isStandalone: true,
		selector: "tum-ui-tab",
		inputs: {
			tabValue: {
				classPropertyName: "tabValue",
				publicName: "value",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			value: {
				classPropertyName: "value",
				publicName: "tumUiTabKey",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { value: "tumUiTabKeyChange" },
		host: {
			attributes: { "ngTab": "" },
			properties: { "class": "hostClasses()" }
		},
		usesInheritance: true,
		ngImport: i0,
		template: "<ng-content />",
		isInline: true,
		styles: [":host{display:inline-flex;align-items:center;position:relative;flex-shrink:0;gap:calc(var(--tumaet-ui-spacing) * 2);padding:calc(var(--tumaet-ui-spacing) * 4) calc(var(--tumaet-ui-spacing) * 4.5);font-weight:600;white-space:nowrap;cursor:pointer;-webkit-user-select:none;user-select:none;background:transparent;outline-color:transparent;transition:color .2s,background .2s,outline-color .2s}:host.tum-ui-tab-disabled{opacity:.6;cursor:default;pointer-events:none}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTabComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-tab",
			template: "<ng-content />",
			host: {
				ngTab: "",
				"[class]": "hostClasses()"
			},
			changeDetection: ChangeDetectionStrategy.OnPush,
			styles: [":host{display:inline-flex;align-items:center;position:relative;flex-shrink:0;gap:calc(var(--tumaet-ui-spacing) * 2);padding:calc(var(--tumaet-ui-spacing) * 4) calc(var(--tumaet-ui-spacing) * 4.5);font-weight:600;white-space:nowrap;cursor:pointer;-webkit-user-select:none;user-select:none;background:transparent;outline-color:transparent;transition:color .2s,background .2s,outline-color .2s}:host.tum-ui-tab-disabled{opacity:.6;cursor:default;pointer-events:none}\n"]
		}]
	}],
	ctorParameters: () => [],
	propDecorators: {
		tabValue: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "value",
				required: true
			}]
		}],
		value: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "tumUiTabKey",
				required: false
			}]
		}, {
			type: i0.Output,
			args: ["tumUiTabKeyChange"]
		}]
	}
});
var TumUiTabListComponent = class TumUiTabListComponent {
	tabsService = inject(TumUiTabsService);
	tabList = inject(TabList);
	elementRef = inject(ElementRef);
	renderedTabs = contentChildren(TumUiTabComponent, {
		...ngDevMode ? { debugName: "renderedTabs" } : /* istanbul ignore next */ {},
		descendants: true
	});
	resizeObserver;
	indicatorPosition = signal({
		offset: 0,
		width: 0,
		animate: false
	}, ...ngDevMode ? [{ debugName: "indicatorPosition" }] : /* istanbul ignore next */ []);
	indicatorTransform = computed(() => `translateX(${this.indicatorPosition().offset}px)`, ...ngDevMode ? [{ debugName: "indicatorTransform" }] : /* istanbul ignore next */ []);
	indicatorReady = false;
	constructor() {
		effect(() => {
			const active = this.tabsService.active();
			const key = active === void 0 ? void 0 : tabKey(active);
			untracked(() => {
				if (this.tabList.selectedTab() !== key) this.tabList.selectedTab.set(key);
			});
		});
		effect(() => {
			const tabs = this.tabsService.orderedTabs();
			const selected = tabs.find((tab) => tab.selected() && !tab.disabled()) ?? tabs.find((tab) => !tab.disabled());
			if (selected) {
				const value = tabValue(selected.key());
				untracked(() => {
					if (this.tabsService.active() !== value) this.tabsService.select(value);
				});
			}
		});
		afterRenderEffect(() => {
			const tabs = this.renderedTabs();
			this.updateIndicator(tabs.find((tab) => tab.selected()));
			this.observeLayout(tabs);
		});
	}
	ngOnDestroy() {
		this.resizeObserver?.disconnect();
	}
	revealFocusedTab(event) {
		this.renderedTabs().find((candidate) => candidate.element === event.target)?.element.scrollIntoView?.({
			block: "nearest",
			inline: "nearest"
		});
	}
	updateIndicator(active) {
		const width = active?.element.offsetWidth ?? 0;
		this.indicatorPosition.set({
			offset: active?.element.offsetLeft ?? 0,
			width,
			animate: this.indicatorReady
		});
		this.indicatorReady ||= width > 0;
	}
	observeLayout(tabs) {
		if (typeof ResizeObserver === "undefined") return;
		this.resizeObserver?.disconnect();
		this.resizeObserver = new ResizeObserver(() => this.updateIndicator(this.renderedTabs().find((tab) => tab.selected())));
		this.resizeObserver.observe(this.elementRef.nativeElement);
		tabs.forEach((tab) => this.resizeObserver.observe(tab.element));
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTabListComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.2.0",
		version: "22.2.0",
		type: TumUiTabListComponent,
		isStandalone: true,
		selector: "tum-ui-tab-list",
		host: {
			listeners: { "focusin": "revealFocusedTab($event)" },
			classAttribute: "tum-ui-tab-list tum:relative tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:overflow-x-auto tum:border-b tum:border-border"
		},
		queries: [{
			propertyName: "renderedTabs",
			predicate: TumUiTabComponent,
			descendants: true,
			isSignal: true
		}],
		hostDirectives: [{ directive: i1.TabList }],
		ngImport: i0,
		template: "<ng-content />\n<span\n    class=\"tum-ui-tab-indicator\"\n    [class.tum-ui-tab-indicator-animated]=\"indicatorPosition().animate\"\n    aria-hidden=\"true\"\n    [style.width.px]=\"indicatorPosition().width\"\n    [style.transform]=\"indicatorTransform()\"\n></span>\n",
		styles: [":host{scrollbar-width:thin}.tum-ui-tab-indicator{position:absolute;inset-block-end:0;left:0;height:2px;background:var(--tumaet-ui-primary-color);pointer-events:none}.tum-ui-tab-indicator-animated{transition:width .25s cubic-bezier(.35,0,.25,1),transform .25s cubic-bezier(.35,0,.25,1)}@media(prefers-reduced-motion:reduce){.tum-ui-tab-indicator{transition:none}}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTabListComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-tab-list",
			hostDirectives: [TabList],
			host: {
				class: "tum-ui-tab-list tum:relative tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:overflow-x-auto tum:border-b tum:border-border",
				"(focusin)": "revealFocusedTab($event)"
			},
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<ng-content />\n<span\n    class=\"tum-ui-tab-indicator\"\n    [class.tum-ui-tab-indicator-animated]=\"indicatorPosition().animate\"\n    aria-hidden=\"true\"\n    [style.width.px]=\"indicatorPosition().width\"\n    [style.transform]=\"indicatorTransform()\"\n></span>\n",
			styles: [":host{scrollbar-width:thin}.tum-ui-tab-indicator{position:absolute;inset-block-end:0;left:0;height:2px;background:var(--tumaet-ui-primary-color);pointer-events:none}.tum-ui-tab-indicator-animated{transition:width .25s cubic-bezier(.35,0,.25,1),transform .25s cubic-bezier(.35,0,.25,1)}@media(prefers-reduced-motion:reduce){.tum-ui-tab-indicator{transition:none}}\n"]
		}]
	}],
	ctorParameters: () => [],
	propDecorators: { renderedTabs: [{
		type: i0.ContentChildren,
		args: [i0.forwardRef(() => TumUiTabComponent), {
			descendants: true,
			isSignal: true
		}]
	}] }
});
var TumUiTabPanelComponent = class TumUiTabPanelComponent {
	tabsService = inject(TumUiTabsService);
	removeFromTabs;
	value = input.required(...ngDevMode ? [{ debugName: "value" }] : /* istanbul ignore next */ []);
	preserveContent = input(false, {
		...ngDevMode ? { debugName: "preserveContent" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	key = computed(() => tabKey(this.value()), ...ngDevMode ? [{ debugName: "key" }] : /* istanbul ignore next */ []);
	active = computed(() => this.tabsService.active() === this.value(), ...ngDevMode ? [{ debugName: "active" }] : /* istanbul ignore next */ []);
	ngOnInit() {
		this.removeFromTabs = this.tabsService.addPanel({ key: this.key });
	}
	ngOnDestroy() {
		this.removeFromTabs?.();
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTabPanelComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiTabPanelComponent,
		isStandalone: true,
		selector: "tum-ui-tab-panel",
		inputs: {
			value: {
				classPropertyName: "value",
				publicName: "value",
				isSignal: true,
				isRequired: true,
				transformFunction: null
			},
			preserveContent: {
				classPropertyName: "preserveContent",
				publicName: "preserveContent",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		host: {
			properties: {
				"hidden": "!active()",
				"attr.inert": "active() ? null : \"\"",
				"attr.data-state": "active() ? 'active' : 'inactive'"
			},
			classAttribute: "tum-ui-tab-panel tum:block"
		},
		ngImport: i0,
		template: `
        <div
            ngTabPanel
            #panel="ngTabPanel"
            class="tum-ui-tab-panel-content tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus"
            [value]="key()"
            [preserveContent]="preserveContent()"
            [hidden]="!panel.visible() || !active()"
        >
            <ng-template ngTabContent>
                @if (active() || preserveContent()) {
                    <ng-content />
                }
            </ng-template>
        </div>
    `,
		isInline: true,
		dependencies: [{
			kind: "directive",
			type: TabPanel,
			selector: "[ngTabPanel]",
			inputs: ["id", "value"],
			exportAs: ["ngTabPanel"]
		}, {
			kind: "directive",
			type: TabContent,
			selector: "ng-template[ngTabContent]",
			exportAs: ["ngTabContent"]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTabPanelComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-tab-panel",
			imports: [TabPanel, TabContent],
			template: `
        <div
            ngTabPanel
            #panel="ngTabPanel"
            class="tum-ui-tab-panel-content tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus"
            [value]="key()"
            [preserveContent]="preserveContent()"
            [hidden]="!panel.visible() || !active()"
        >
            <ng-template ngTabContent>
                @if (active() || preserveContent()) {
                    <ng-content />
                }
            </ng-template>
        </div>
    `,
			host: {
				class: "tum-ui-tab-panel tum:block",
				"[hidden]": "!active()",
				"[attr.inert]": "active() ? null : \"\"",
				"[attr.data-state]": "active() ? 'active' : 'inactive'"
			},
			changeDetection: ChangeDetectionStrategy.OnPush
		}]
	}],
	propDecorators: {
		value: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "value",
				required: true
			}]
		}],
		preserveContent: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "preserveContent",
				required: false
			}]
		}]
	}
});
var TumUiTabPanelsComponent = class TumUiTabPanelsComponent {
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTabPanelsComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "14.0.0",
		version: "22.2.0",
		type: TumUiTabPanelsComponent,
		isStandalone: true,
		selector: "tum-ui-tab-panels",
		host: { classAttribute: "tum-ui-tab-panels" },
		ngImport: i0,
		template: "<ng-content />",
		isInline: true,
		styles: [":host{display:block;padding:calc(var(--tumaet-ui-spacing) * 3.5) calc(var(--tumaet-ui-spacing) * 4.5) calc(var(--tumaet-ui-spacing) * 4.5)}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTabPanelsComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-tab-panels",
			template: "<ng-content />",
			host: { class: "tum-ui-tab-panels" },
			changeDetection: ChangeDetectionStrategy.OnPush,
			styles: [":host{display:block;padding:calc(var(--tumaet-ui-spacing) * 3.5) calc(var(--tumaet-ui-spacing) * 4.5) calc(var(--tumaet-ui-spacing) * 4.5)}\n"]
		}]
	}]
});
var TumUiTabsComponent = class TumUiTabsComponent {
	tabsService = inject(TumUiTabsService);
	value = model(...ngDevMode ? [void 0, { debugName: "value" }] : /* istanbul ignore next */ []);
	constructor() {
		this.tabsService.register(this.value, (selected) => this.value.set(selected));
	}
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTabsComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiTabsComponent,
		isStandalone: true,
		selector: "tum-ui-tabs",
		inputs: { value: {
			classPropertyName: "value",
			publicName: "value",
			isSignal: true,
			isRequired: false,
			transformFunction: null
		} },
		outputs: { value: "valueChange" },
		host: {
			attributes: { "ngTabs": "" },
			classAttribute: "tum-ui-tabs tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:flex-col"
		},
		providers: [TumUiTabsService],
		hostDirectives: [{ directive: i1.Tabs }],
		ngImport: i0,
		template: `
        <ng-content />
        @for (key of tabsService.keysWithoutPanel(); track key) {
            <div ngTabPanel [value]="key" hidden><ng-template ngTabContent /></div>
        }
    `,
		isInline: true,
		dependencies: [{
			kind: "directive",
			type: TabPanel,
			selector: "[ngTabPanel]",
			inputs: ["id", "value"],
			exportAs: ["ngTabPanel"]
		}, {
			kind: "directive",
			type: TabContent,
			selector: "ng-template[ngTabContent]",
			exportAs: ["ngTabContent"]
		}],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTabsComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-tabs",
			imports: [TabPanel, TabContent],
			template: `
        <ng-content />
        @for (key of tabsService.keysWithoutPanel(); track key) {
            <div ngTabPanel [value]="key" hidden><ng-template ngTabContent /></div>
        }
    `,
			hostDirectives: [Tabs],
			host: {
				ngTabs: "",
				class: "tum-ui-tabs tum:flex tum:w-full tum:min-w-0 tum:max-w-full tum:flex-col"
			},
			providers: [TumUiTabsService],
			changeDetection: ChangeDetectionStrategy.OnPush
		}]
	}],
	ctorParameters: () => [],
	propDecorators: { value: [{
		type: i0.Input,
		args: [{
			isSignal: true,
			alias: "value",
			required: false
		}]
	}, {
		type: i0.Output,
		args: ["valueChange"]
	}] }
});
const TAG_BASE = "tum:inline-flex tum:items-center tum:gap-1 tum:px-2 tum:py-1 tum:text-sm tum:font-bold";
const TAG_SEVERITY = {
	secondary: "tum:bg-hover-background tum:text-text",
	success: "",
	info: "",
	warn: "",
	danger: "",
	contrast: "tum:bg-contrast-background tum:text-contrast"
};
var TumUiTagComponent = class TumUiTagComponent {
	severity = input("secondary", ...ngDevMode ? [{ debugName: "severity" }] : /* istanbul ignore next */ []);
	value = input(...ngDevMode ? [void 0, { debugName: "value" }] : /* istanbul ignore next */ []);
	rounded = input(false, {
		...ngDevMode ? { debugName: "rounded" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	tagClasses = computed(() => `${TAG_BASE} ${this.rounded() ? "tum:rounded-full" : "tum:rounded-md"} ${TAG_SEVERITY[this.severity()]}`.replace(/\s+/g, " ").trim(), ...ngDevMode ? [{ debugName: "tagClasses" }] : /* istanbul ignore next */ []);
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiTagComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.0.0",
		version: "22.2.0",
		type: TumUiTagComponent,
		isStandalone: true,
		selector: "tum-ui-tag",
		inputs: {
			severity: {
				classPropertyName: "severity",
				publicName: "severity",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			value: {
				classPropertyName: "value",
				publicName: "value",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			rounded: {
				classPropertyName: "rounded",
				publicName: "rounded",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		ngImport: i0,
		template: "<span [class]=\"tagClasses()\" [attr.data-severity]=\"severity()\">\n    @if (value(); as tagValue) {\n        {{ tagValue }}\n    } @else {\n        <ng-content />\n    }\n</span>\n",
		styles: [":host span[data-severity=success]{background:color-mix(in srgb,var(--tumaet-ui-state-success) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-success-foreground)}:host span[data-severity=info]{background:color-mix(in srgb,var(--tumaet-ui-state-info) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-info-foreground)}:host span[data-severity=warn]{background:color-mix(in srgb,var(--tumaet-ui-state-warning) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-warning-foreground)}:host span[data-severity=danger]{background:color-mix(in srgb,var(--tumaet-ui-state-danger) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-danger-foreground)}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiTagComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-tag",
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<span [class]=\"tagClasses()\" [attr.data-severity]=\"severity()\">\n    @if (value(); as tagValue) {\n        {{ tagValue }}\n    } @else {\n        <ng-content />\n    }\n</span>\n",
			styles: [":host span[data-severity=success]{background:color-mix(in srgb,var(--tumaet-ui-state-success) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-success-foreground)}:host span[data-severity=info]{background:color-mix(in srgb,var(--tumaet-ui-state-info) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-info-foreground)}:host span[data-severity=warn]{background:color-mix(in srgb,var(--tumaet-ui-state-warning) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-warning-foreground)}:host span[data-severity=danger]{background:color-mix(in srgb,var(--tumaet-ui-state-danger) 20%,var(--tumaet-ui-content-background));color:var(--tumaet-ui-state-danger-foreground)}\n"]
		}]
	}],
	propDecorators: {
		severity: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "severity",
				required: false
			}]
		}],
		value: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "value",
				required: false
			}]
		}],
		rounded: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "rounded",
				required: false
			}]
		}]
	}
});
var TumUiToggleSwitchComponent = class TumUiToggleSwitchComponent {
	hostAriaLabel = inject(new HostAttributeToken("aria-label"), { optional: true });
	hostAriaLabelledBy = inject(new HostAttributeToken("aria-labelledby"), { optional: true });
	disabled = input(false, {
		...ngDevMode ? { debugName: "disabled" } : /* istanbul ignore next */ {},
		transform: booleanAttribute
	});
	inputId = input(...ngDevMode ? [void 0, { debugName: "inputId" }] : /* istanbul ignore next */ []);
	ariaLabel = input(...ngDevMode ? [void 0, { debugName: "ariaLabel" }] : /* istanbul ignore next */ []);
	ariaLabelledBy = input(...ngDevMode ? [void 0, { debugName: "ariaLabelledBy" }] : /* istanbul ignore next */ []);
	changed = output();
	checked = signal(false, ...ngDevMode ? [{ debugName: "checked" }] : /* istanbul ignore next */ []);
	cvaDisabled = signal(false, ...ngDevMode ? [{ debugName: "cvaDisabled" }] : /* istanbul ignore next */ []);
	effectiveDisabled = computed(() => this.disabled() || this.cvaDisabled(), ...ngDevMode ? [{ debugName: "effectiveDisabled" }] : /* istanbul ignore next */ []);
	effectiveAriaLabel = computed(() => this.ariaLabel() ?? this.hostAriaLabel, ...ngDevMode ? [{ debugName: "effectiveAriaLabel" }] : /* istanbul ignore next */ []);
	effectiveAriaLabelledBy = computed(() => this.ariaLabelledBy() ?? this.hostAriaLabelledBy, ...ngDevMode ? [{ debugName: "effectiveAriaLabelledBy" }] : /* istanbul ignore next */ []);
	onChange = () => {};
	onTouched = () => {};
	hostClasses = computed(() => {
		return `${this.checked() ? "tum:bg-primary" : "tum:bg-control-border"} ${this.effectiveDisabled() ? "tum:opacity-60" : ""}`.trim();
	}, ...ngDevMode ? [{ debugName: "hostClasses" }] : /* istanbul ignore next */ []);
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
	static ɵfac = i0.ɵɵngDeclareFactory({
		minVersion: "12.0.0",
		version: "22.2.0",
		ngImport: i0,
		type: TumUiToggleSwitchComponent,
		deps: [],
		target: i0.ɵɵFactoryTarget.Component
	});
	static ɵcmp = i0.ɵɵngDeclareComponent({
		minVersion: "17.1.0",
		version: "22.2.0",
		type: TumUiToggleSwitchComponent,
		isStandalone: true,
		selector: "tum-ui-toggle-switch",
		inputs: {
			disabled: {
				classPropertyName: "disabled",
				publicName: "disabled",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			inputId: {
				classPropertyName: "inputId",
				publicName: "inputId",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabel: {
				classPropertyName: "ariaLabel",
				publicName: "ariaLabel",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			},
			ariaLabelledBy: {
				classPropertyName: "ariaLabelledBy",
				publicName: "ariaLabelledBy",
				isSignal: true,
				isRequired: false,
				transformFunction: null
			}
		},
		outputs: { changed: "changed" },
		host: {
			properties: {
				"class": "hostClasses()",
				"attr.data-checked": "checked()",
				"attr.data-disabled": "effectiveDisabled() || null"
			},
			classAttribute: "tum-ui-toggle-switch"
		},
		providers: [{
			provide: NG_VALUE_ACCESSOR,
			useExisting: forwardRef(() => TumUiToggleSwitchComponent),
			multi: true
		}],
		ngImport: i0,
		template: "<input\n    type=\"checkbox\"\n    role=\"switch\"\n    class=\"tum-ui-toggle-switch-input\"\n    [id]=\"inputId()\"\n    [checked]=\"checked()\"\n    [disabled]=\"effectiveDisabled()\"\n    [attr.aria-label]=\"effectiveAriaLabel()\"\n    [attr.aria-labelledby]=\"effectiveAriaLabelledBy()\"\n    (change)=\"onInputChange($event)\"\n    (blur)=\"onInputBlur()\"\n/>\n<span class=\"tum-ui-toggle-switch-handle tum:bg-content-background\" aria-hidden=\"true\"></span>\n",
		styles: [":host{display:inline-block;position:relative;box-sizing:border-box;width:calc(var(--tumaet-ui-spacing) * 10);height:calc(var(--tumaet-ui-spacing) * 6);flex-shrink:0;border:1px solid transparent;border-radius:30px;cursor:pointer;vertical-align:middle;outline-color:transparent;transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}:host([data-disabled=true]){cursor:default}:host(:has(.tum-ui-toggle-switch-input:focus-visible)){outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-toggle-switch-input{position:absolute;z-index:1;inset:0;width:100%;height:100%;margin:0;opacity:0;cursor:pointer}.tum-ui-toggle-switch-input:disabled{cursor:default}.tum-ui-toggle-switch-handle{position:absolute;top:50%;width:calc(var(--tumaet-ui-spacing) * 4);height:calc(var(--tumaet-ui-spacing) * 4);margin-block-start:calc(var(--tumaet-ui-spacing) * -2);inset-inline-start:var(--tumaet-ui-spacing);border-radius:50%;transition:background-color .2s,inset-inline-start .2s}:host([data-checked=true]) .tum-ui-toggle-switch-handle{inset-inline-start:calc(var(--tumaet-ui-spacing) * 5)}@media(prefers-reduced-motion:reduce){.tum-ui-toggle-switch-handle{transition-property:background-color}}@media(forced-colors:active){.tum-ui-toggle-switch-input{appearance:auto;opacity:1}.tum-ui-toggle-switch-handle{display:none}}\n"],
		changeDetection: i0.ChangeDetectionStrategy.OnPush
	});
};
i0.ɵɵngDeclareClassMetadata({
	minVersion: "12.0.0",
	version: "22.2.0",
	ngImport: i0,
	type: TumUiToggleSwitchComponent,
	decorators: [{
		type: Component,
		args: [{
			selector: "tum-ui-toggle-switch",
			host: {
				class: "tum-ui-toggle-switch",
				"[class]": "hostClasses()",
				"[attr.data-checked]": "checked()",
				"[attr.data-disabled]": "effectiveDisabled() || null"
			},
			providers: [{
				provide: NG_VALUE_ACCESSOR,
				useExisting: forwardRef(() => TumUiToggleSwitchComponent),
				multi: true
			}],
			changeDetection: ChangeDetectionStrategy.OnPush,
			template: "<input\n    type=\"checkbox\"\n    role=\"switch\"\n    class=\"tum-ui-toggle-switch-input\"\n    [id]=\"inputId()\"\n    [checked]=\"checked()\"\n    [disabled]=\"effectiveDisabled()\"\n    [attr.aria-label]=\"effectiveAriaLabel()\"\n    [attr.aria-labelledby]=\"effectiveAriaLabelledBy()\"\n    (change)=\"onInputChange($event)\"\n    (blur)=\"onInputBlur()\"\n/>\n<span class=\"tum-ui-toggle-switch-handle tum:bg-content-background\" aria-hidden=\"true\"></span>\n",
			styles: [":host{display:inline-block;position:relative;box-sizing:border-box;width:calc(var(--tumaet-ui-spacing) * 10);height:calc(var(--tumaet-ui-spacing) * 6);flex-shrink:0;border:1px solid transparent;border-radius:30px;cursor:pointer;vertical-align:middle;outline-color:transparent;transition:background-color .2s,border-color .2s,box-shadow .2s,outline-color .2s}:host([data-disabled=true]){cursor:default}:host(:has(.tum-ui-toggle-switch-input:focus-visible)){outline:2px solid var(--tumaet-ui-focus-color);outline-offset:2px}.tum-ui-toggle-switch-input{position:absolute;z-index:1;inset:0;width:100%;height:100%;margin:0;opacity:0;cursor:pointer}.tum-ui-toggle-switch-input:disabled{cursor:default}.tum-ui-toggle-switch-handle{position:absolute;top:50%;width:calc(var(--tumaet-ui-spacing) * 4);height:calc(var(--tumaet-ui-spacing) * 4);margin-block-start:calc(var(--tumaet-ui-spacing) * -2);inset-inline-start:var(--tumaet-ui-spacing);border-radius:50%;transition:background-color .2s,inset-inline-start .2s}:host([data-checked=true]) .tum-ui-toggle-switch-handle{inset-inline-start:calc(var(--tumaet-ui-spacing) * 5)}@media(prefers-reduced-motion:reduce){.tum-ui-toggle-switch-handle{transition-property:background-color}}@media(forced-colors:active){.tum-ui-toggle-switch-input{appearance:auto;opacity:1}.tum-ui-toggle-switch-handle{display:none}}\n"]
		}]
	}],
	propDecorators: {
		disabled: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "disabled",
				required: false
			}]
		}],
		inputId: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "inputId",
				required: false
			}]
		}],
		ariaLabel: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabel",
				required: false
			}]
		}],
		ariaLabelledBy: [{
			type: i0.Input,
			args: [{
				isSignal: true,
				alias: "ariaLabelledBy",
				required: false
			}]
		}],
		changed: [{
			type: i0.Output,
			args: ["changed"]
		}]
	}
});
export { TUM_UI_FORM_FIELD, TUM_UI_TRANSLATOR, TumUiAutoCompleteComponent, TumUiBarChartComponent, TumUiButtonComponent, TumUiButtonDirective, TumUiButtonGroupComponent, TumUiCardComponent, TumUiCheckboxComponent, TumUiChipComponent, TumUiConfirmDialogComponent, TumUiConfirmationService, TumUiDatePickerComponent, TumUiDialogComponent, TumUiDoughnutChartComponent, TumUiEmptyStateComponent, TumUiFormFieldComponent, TumUiIconFieldComponent, TumUiInputDirective, TumUiInputGroupAddonComponent, TumUiInputGroupComponent, TumUiInputNumberComponent, TumUiLineChartComponent, TumUiListComponent, TumUiListItemActionDirective, TumUiListItemDirective, TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective, TumUiMessageComponent, TumUiPaginatorComponent, TumUiPanelComponent, TumUiPopoverComponent, TumUiPopoverTriggerDirective, TumUiProgressBarComponent, TumUiProgressSpinnerComponent, TumUiRadioButtonComponent, TumUiSearchFieldComponent, TumUiSelectButtonComponent, TumUiSelectComponent, TumUiTabComponent, TumUiTabListComponent, TumUiTabPanelComponent, TumUiTabPanelsComponent, TumUiTableComponent, TumUiTableDirective, TumUiTableSortableColumnComponent, TumUiTableVirtualScrollComponent, TumUiTabsComponent, TumUiTagComponent, TumUiToggleSwitchComponent, TumUiTooltipDirective, provideTumUiTranslator };

//# sourceMappingURL=tumaet-ui-angular.mjs.map