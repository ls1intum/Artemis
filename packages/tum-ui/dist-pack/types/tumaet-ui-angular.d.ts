import * as _angular_core from '@angular/core';
import { OnDestroy, TemplateRef, InjectionToken, Signal, Type, EnvironmentProviders, ElementRef, TrackByFunction } from '@angular/core';
import { ControlValueAccessor } from '@angular/forms';
import * as _fortawesome_fontawesome_svg_core from '@fortawesome/fontawesome-svg-core';
import { IconProp, IconDefinition } from '@fortawesome/fontawesome-svg-core';
import * as _tumaet_ui_angular from '@tumaet/ui-angular';
import { FormValueControl } from '@angular/forms/signals';
import dayjs from 'dayjs/esm';
import { DialogRole } from '@angular/cdk/dialog';
import * as i1 from '@angular/cdk/menu';
import { FocusOrigin } from '@angular/cdk/a11y';

interface TumUiAutoCompleteSearchEvent {
    originalEvent?: Event;
    query: string;
}
interface TumUiAutoCompleteOptionEvent {
    originalEvent?: Event;
    value: unknown;
}
/** Single- or multi-value ControlValueAccessor with consumer-supplied suggestions. */
declare class TumUiAutoCompleteComponent implements ControlValueAccessor {
    private readonly overlayService;
    private readonly viewContainerRef;
    private readonly destroyRef;
    private readonly document;
    /** Suggestions supplied in response to a search request. */
    readonly suggestions: _angular_core.InputSignal<readonly unknown[]>;
    /** Property name used as the visible label for object values. */
    readonly optionLabel: _angular_core.InputSignal<string | undefined>;
    readonly multiple: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly placeholder: _angular_core.InputSignal<string | undefined>;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Minimum query length before a search request emits. */
    readonly minLength: _angular_core.InputSignalWithTransform<number, unknown>;
    /** Delay between the latest input and a search request. */
    readonly debounceMs: _angular_core.InputSignalWithTransform<number, unknown>;
    /** Requests suggestions when the empty input receives focus. */
    readonly completeOnFocus: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly inputId: _angular_core.InputSignal<string>;
    readonly name: _angular_core.InputSignal<string | undefined>;
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    readonly removeAriaLabel: _angular_core.InputSignal<string | undefined>;
    /** Message shown when a completed search returns no suggestions. */
    readonly emptyMessage: _angular_core.InputSignal<string | undefined>;
    /** Requests suggestions for the current text query. */
    readonly searchRequested: _angular_core.OutputEmitterRef<TumUiAutoCompleteSearchEvent>;
    readonly optionSelected: _angular_core.OutputEmitterRef<TumUiAutoCompleteOptionEvent>;
    readonly optionRemoved: _angular_core.OutputEmitterRef<TumUiAutoCompleteOptionEvent>;
    protected readonly listboxId: string;
    private readonly container;
    private readonly textInput;
    private readonly panel;
    private overlayRef?;
    protected readonly selectedValues: _angular_core.WritableSignal<unknown[]>;
    private readonly singleValue;
    protected readonly query: _angular_core.WritableSignal<string>;
    protected readonly isFocused: _angular_core.WritableSignal<boolean>;
    private readonly hasSearched;
    protected readonly activeIndex: _angular_core.WritableSignal<number>;
    private readonly disabledByForm;
    private debounceTimer?;
    private onChangeCallback;
    private onTouchedCallback;
    protected readonly isDisabled: _angular_core.Signal<boolean>;
    private readonly labelKey;
    protected readonly panelVisible: _angular_core.Signal<boolean>;
    protected readonly activeOptionId: _angular_core.Signal<string | undefined>;
    protected readonly inputPlaceholder: _angular_core.Signal<string | undefined>;
    protected readonly inputText: _angular_core.Signal<string>;
    constructor();
    writeValue(value: unknown): void;
    registerOnChange(fn: (value: unknown) => void): void;
    registerOnTouched(fn: () => void): void;
    setDisabledState(isDisabled: boolean): void;
    protected valueLabel(value: unknown): string;
    private toText;
    private valuesMatch;
    protected isAlreadySelected(option: unknown): boolean;
    protected optionId(index: number): string;
    protected focusInput(): void;
    protected onFocus(event: FocusEvent): void;
    protected onBlur(): void;
    protected onInput(event: Event): void;
    private fireComplete;
    protected onInputKeydown(event: KeyboardEvent): void;
    protected setActive(index: number): void;
    protected selectOption(option: unknown, event?: Event): void;
    protected removeAt(index: number, event?: Event): void;
    private clearInput;
    private openPanel;
    private closePanel;
    protected optionClasses(option: unknown, index: number): string;
    protected containerClasses(): string;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiAutoCompleteComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiAutoCompleteComponent, "tum-ui-autocomplete", never, { "suggestions": { "alias": "suggestions"; "required": false; "isSignal": true; }; "optionLabel": { "alias": "optionLabel"; "required": false; "isSignal": true; }; "multiple": { "alias": "multiple"; "required": false; "isSignal": true; }; "placeholder": { "alias": "placeholder"; "required": false; "isSignal": true; }; "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "minLength": { "alias": "minLength"; "required": false; "isSignal": true; }; "debounceMs": { "alias": "debounceMs"; "required": false; "isSignal": true; }; "completeOnFocus": { "alias": "completeOnFocus"; "required": false; "isSignal": true; }; "inputId": { "alias": "inputId"; "required": false; "isSignal": true; }; "name": { "alias": "name"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "removeAriaLabel": { "alias": "removeAriaLabel"; "required": false; "isSignal": true; }; "emptyMessage": { "alias": "emptyMessage"; "required": false; "isSignal": true; }; }, { "searchRequested": "searchRequested"; "optionSelected": "optionSelected"; "optionRemoved": "optionRemoved"; }, never, never, true, never>;
}

declare class TumUiButtonGroupComponent {
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiButtonGroupComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiButtonGroupComponent, "tum-ui-button-group", never, {}, {}, never, ["*"], true, never>;
}

type TumUiButtonSeverity = 'primary' | 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';
type TumUiButtonSize = 'small' | 'default' | 'large';
type TumUiButtonVariant = 'solid' | 'outlined' | 'text';

declare class TumUiButtonComponent {
    readonly severity: _angular_core.InputSignal<TumUiButtonSeverity>;
    readonly size: _angular_core.InputSignal<TumUiButtonSize>;
    readonly variant: _angular_core.InputSignal<TumUiButtonVariant>;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly rounded: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Replaces the icon with a spinner and disables the button. */
    readonly loading: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly icon: _angular_core.InputSignal<IconProp | undefined>;
    readonly type: _angular_core.InputSignal<"button" | "submit">;
    /** Accessible name required when projected content does not label the button. */
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    readonly ariaExpanded: _angular_core.InputSignal<boolean | undefined>;
    readonly ariaPressed: _angular_core.InputSignal<boolean | undefined>;
    readonly ariaControls: _angular_core.InputSignal<string | undefined>;
    readonly ariaDescribedBy: _angular_core.InputSignal<string | undefined>;
    readonly clicked: _angular_core.OutputEmitterRef<MouseEvent>;
    protected readonly faSpinner: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly isDisabled: _angular_core.Signal<boolean>;
    protected readonly buttonClasses: _angular_core.Signal<string>;
    protected onClick(event: MouseEvent): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiButtonComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiButtonComponent, "tum-ui-button", never, { "severity": { "alias": "severity"; "required": false; "isSignal": true; }; "size": { "alias": "size"; "required": false; "isSignal": true; }; "variant": { "alias": "variant"; "required": false; "isSignal": true; }; "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "rounded": { "alias": "rounded"; "required": false; "isSignal": true; }; "loading": { "alias": "loading"; "required": false; "isSignal": true; }; "icon": { "alias": "icon"; "required": false; "isSignal": true; }; "type": { "alias": "type"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "ariaExpanded": { "alias": "ariaExpanded"; "required": false; "isSignal": true; }; "ariaPressed": { "alias": "ariaPressed"; "required": false; "isSignal": true; }; "ariaControls": { "alias": "ariaControls"; "required": false; "isSignal": true; }; "ariaDescribedBy": { "alias": "ariaDescribedBy"; "required": false; "isSignal": true; }; }, { "clicked": "clicked"; }, never, ["*"], true, never>;
}

declare class TumUiButtonDirective {
    readonly severity: _angular_core.InputSignal<TumUiButtonSeverity>;
    readonly size: _angular_core.InputSignal<TumUiButtonSize>;
    readonly variant: _angular_core.InputSignal<TumUiButtonVariant>;
    protected readonly hostClasses: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiButtonDirective, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiButtonDirective, "a[tumUiButton], button[tumUiButton]", never, { "severity": { "alias": "severity"; "required": false; "isSignal": true; }; "size": { "alias": "size"; "required": false; "isSignal": true; }; "variant": { "alias": "variant"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

declare class TumUiCardComponent {
    readonly header: _angular_core.InputSignal<string | undefined>;
    readonly subheader: _angular_core.InputSignal<string | undefined>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiCardComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiCardComponent, "tum-ui-card", never, { "header": { "alias": "header"; "required": false; "isSignal": true; }; "subheader": { "alias": "subheader"; "required": false; "isSignal": true; }; }, {}, never, ["[tumUiCardHeader]", "*", "[tumUiCardFooter]"], true, never>;
}

/** Configuration of a single chart axis. */
interface TumUiChartAxisConfig {
    /** Axis title rendered next to the ticks. */
    label?: string;
    min?: number;
    max?: number;
    /** Formats tick labels; receives the category label (category axis) or the numeric tick value (value axis). */
    tickFormatter?: (value: number | string) => string;
    /** Hides the axis entirely (ticks, title and grid). Defaults to true. */
    display?: boolean;
}
/**
 * One series of a chart. For a single-series bar chart, `colors` assigns a distinct color per
 * category; for grouped or stacked bars, `color` colors the whole series.
 *
 * Colors may be plain CSS colors or `var(--token)` references — unlike a canvas renderer, SVG
 * consumes custom properties directly, so no resolution step is needed and theme switches apply
 * without re-rendering.
 */
interface TumUiChartSeries {
    label?: string;
    data: readonly (number | undefined)[];
    color?: string;
    colors?: readonly string[];
    /** Arbitrary per-datum metadata, index-aligned with `data`, surfaced in tooltips and select events. */
    meta?: readonly unknown[];
    /**
     * Marks the series as a decorative marker such as an average line. It is drawn dashed and is
     * left out of the legend, the tooltip and select events.
     */
    referenceLine?: boolean;
}
/** A single hovered or clicked datum, passed to tooltip and data-label formatters. */
interface TumUiChartDatumContext {
    seriesIndex: number;
    index: number;
    label: string;
    seriesLabel?: string;
    value: number;
    meta?: unknown;
}
interface TumUiChartTooltipConfig {
    title?: (items: TumUiChartDatumContext[]) => string;
    label?: (item: TumUiChartDatumContext) => string | string[];
    /** Extra lines appended below the per-series lines, e.g. a shared note about the hovered category. */
    afterBody?: (items: TumUiChartDatumContext[]) => string | string[];
}
type TumUiChartLegendPosition = 'top' | 'right' | 'bottom' | 'left';
type TumUiChartLegendConfig = boolean | {
    position?: TumUiChartLegendPosition;
};
interface TumUiBarChartConfig {
    /** Renders horizontal bars: categories run down the y axis, values along the x axis. */
    horizontal?: boolean;
    /** Stacks series on top of each other instead of grouping them side by side. */
    stacked?: boolean;
    /** Treats the value axis as a percentage: ticks get a '%' suffix and the axis is capped at 100. */
    percentScale?: boolean;
    /** Caps a bar's cross-axis thickness in px, for slim summary bars that should not fill the container. */
    maxBarThickness?: number;
    xAxis?: TumUiChartAxisConfig;
    yAxis?: TumUiChartAxisConfig;
    /** Defaults to hidden. */
    legend?: TumUiChartLegendConfig;
    /** `false` disables tooltips; omitting it renders the default `label: value` tooltip. */
    tooltip?: false | TumUiChartTooltipConfig;
    /** Persistent labels drawn at the end of each bar. */
    dataLabels?: {
        formatter: (value: number, context: TumUiChartDatumContext) => string;
    };
}
interface TumUiChartSelectEvent {
    seriesIndex: number;
    index: number;
    label?: string;
    seriesLabel?: string;
    value?: number;
    meta?: unknown;
}
interface TumUiLineChartConfig {
    xAxis?: TumUiChartAxisConfig;
    yAxis?: TumUiChartAxisConfig;
    /** Defaults to hidden. */
    legend?: TumUiChartLegendConfig;
    /** `false` disables tooltips; omitting it renders the default `series: value` tooltip. */
    tooltip?: false | TumUiChartTooltipConfig;
    /** Monotone cubic interpolation instead of straight segments; never overshoots a data point. */
    monotone?: boolean;
    /** Draws straight across missing values instead of leaving a gap in the line. */
    spanGaps?: boolean;
    /** Draws a marker at every data point. Defaults to true. */
    points?: boolean;
}
interface TumUiDoughnutChartConfig {
    /** Width of the ring as a fraction of the radius. Defaults to 0.25; pass 1 for a full pie. */
    arcWidth?: number;
    /** Inset around the arc in px. Defaults to 20. */
    padding?: number;
    /** Defaults to hidden. */
    legend?: TumUiChartLegendConfig;
    tooltip?: false | TumUiChartTooltipConfig;
}

/** The drawing area inside the axis margins. All series coordinates are relative to its origin. */
interface ChartPlot {
    width: number;
    height: number;
    left: number;
    top: number;
}
interface ChartTick {
    key: string;
    text: string;
    x: number;
    y: number;
    anchor: string;
    /** Rotation in degrees around (x, y); 0 for upright labels. */
    rotate: number;
}
interface ChartGridLine {
    key: string;
    x1: number;
    y1: number;
    x2: number;
    y2: number;
}
interface ChartAxisTitle {
    text: string;
    x: number;
    y: number;
    rotate: number;
}
interface ChartLegendItem {
    key: string;
    label: string;
    color: string;
    /** The reader has switched this entry off, so its series or slice is left out of the chart. */
    hidden?: boolean;
}

interface BarView {
    key: string;
    x: number;
    y: number;
    width: number;
    height: number;
    color: string;
    context: TumUiChartDatumContext;
    dataLabel?: {
        x: number;
        y: number;
        anchor: string;
        text: string;
    };
}
declare class TumUiBarChartComponent implements OnDestroy {
    private readonly hostElement;
    /**
     * The plot is measured on the SVG itself rather than on the host, because a legend is a sibling
     * flex item: measuring the host would size the plot as if the legend's band were still free and
     * paint the axis labels underneath it.
     */
    private readonly canvas;
    readonly labels: _angular_core.InputSignal<readonly string[]>;
    readonly series: _angular_core.InputSignal<readonly TumUiChartSeries[]>;
    readonly config: _angular_core.InputSignal<TumUiBarChartConfig>;
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    /** Names the chart from a visible heading instead of a literal label. */
    readonly ariaLabelledBy: _angular_core.InputSignal<string | undefined>;
    /** Marks bars as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
    readonly interactive: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly dataSelect: _angular_core.OutputEmitterRef<TumUiChartSelectEvent>;
    /** Names an interactive datum for assistive technology; see {@link datumAccessibleName}. */
    protected accessibleName(context: TumUiChartDatumContext): string;
    private readonly size;
    protected readonly hovered: _angular_core.WritableSignal<{
        index: number;
        seriesIndex: number;
        x: number;
        y: number;
        hostWidth: number;
        hostHeight: number;
    } | undefined>;
    private resizeObserver?;
    constructor();
    ngOnDestroy(): void;
    /** Series the reader switched off in the legend, by index. */
    private readonly hiddenSeries;
    protected onLegendToggle(key: string): void;
    /** The series actually drawn, paired with their original index so meta and colors stay aligned. */
    private readonly visibleSeries;
    private readonly horizontal;
    private readonly stacked;
    /** The axis carrying the numeric values: x for horizontal bars, y otherwise. */
    private readonly valueAxis;
    private readonly categoryAxis;
    /**
     * Whole numbers on the value axis mean the axis must not step in fractions: a "number of
     * submissions" axis running 0–3 would otherwise be labelled 0, 1, 1, 2, 2, 3 once the caller's
     * integer formatter collapsed the half steps.
     */
    private readonly minTickStep;
    private readonly valueDomain;
    private readonly valueTickLabels;
    private readonly frame;
    protected readonly plot: _angular_core.Signal<ChartPlot>;
    /** Unique per instance, so charts sharing a page do not share a clip path. */
    protected readonly clipId: string;
    private readonly valueScale;
    private readonly categoryScale;
    protected readonly bars: _angular_core.Signal<BarView[]>;
    protected readonly gridLines: _angular_core.Signal<ChartGridLine[]>;
    protected readonly ticks: _angular_core.Signal<ChartTick[]>;
    protected readonly axisTitles: _angular_core.Signal<ChartAxisTitle[]>;
    protected readonly legendPosition: _angular_core.Signal<_tumaet_ui_angular.TumUiChartLegendPosition | undefined>;
    protected readonly legendItems: _angular_core.Signal<ChartLegendItem[]>;
    protected readonly tooltip: _angular_core.Signal<{
        x: number;
        y: number;
        below: boolean;
        title: string;
        lines: string[];
    } | undefined>;
    protected readonly accessibleRows: _angular_core.Signal<{
        label: string;
        values: {
            seriesLabel: string | undefined;
            value: number | undefined;
        }[];
    }[]>;
    protected onBarEnter(bar: BarView, event: MouseEvent): void;
    protected onBarLeave(): void;
    protected onBarSelect(bar: BarView): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiBarChartComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiBarChartComponent, "tum-ui-bar-chart", never, { "labels": { "alias": "labels"; "required": true; "isSignal": true; }; "series": { "alias": "series"; "required": true; "isSignal": true; }; "config": { "alias": "config"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "ariaLabelledBy": { "alias": "ariaLabelledBy"; "required": false; "isSignal": true; }; "interactive": { "alias": "interactive"; "required": false; "isSignal": true; }; }, { "dataSelect": "dataSelect"; }, never, never, true, never>;
}

interface SliceView {
    key: string;
    path: string;
    color: string;
    context: TumUiChartDatumContext;
}
/**
 * A doughnut chart rendered as inline SVG, drawn from the first series. An `arcWidth` of 1 fills the
 * ring completely and produces a pie chart.
 */
declare class TumUiDoughnutChartComponent implements OnDestroy {
    private readonly hostElement;
    private readonly canvas;
    readonly labels: _angular_core.InputSignal<readonly string[]>;
    readonly series: _angular_core.InputSignal<readonly TumUiChartSeries[]>;
    readonly config: _angular_core.InputSignal<TumUiDoughnutChartConfig>;
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    /** Names the chart from a visible heading instead of a literal label. */
    readonly ariaLabelledBy: _angular_core.InputSignal<string | undefined>;
    /** Marks slices as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
    readonly interactive: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly dataSelect: _angular_core.OutputEmitterRef<TumUiChartSelectEvent>;
    private readonly size;
    protected readonly hovered: _angular_core.WritableSignal<{
        index: number;
        x: number;
        y: number;
        hostWidth: number;
        hostHeight: number;
    } | undefined>;
    private resizeObserver?;
    constructor();
    ngOnDestroy(): void;
    /** Slices the reader switched off in the legend, by index. */
    private readonly hiddenSlices;
    protected onLegendToggle(key: string): void;
    private readonly primarySeries;
    protected readonly slices: _angular_core.Signal<SliceView[]>;
    protected readonly legendPosition: _angular_core.Signal<_tumaet_ui_angular.TumUiChartLegendPosition | undefined>;
    /** A doughnut's legend names the slices rather than the series, so it follows the categories. */
    protected readonly legendItems: _angular_core.Signal<ChartLegendItem[]>;
    protected readonly tooltip: _angular_core.Signal<{
        x: number;
        y: number;
        below: boolean;
        title: string;
        lines: string[];
    } | undefined>;
    protected readonly accessibleRows: _angular_core.Signal<{
        label: string;
        values: {
            seriesLabel: string | undefined;
            value: number | undefined;
        }[];
    }[]>;
    protected onSliceEnter(slice: SliceView, event: MouseEvent): void;
    protected onSliceLeave(): void;
    protected onSliceSelect(slice: SliceView): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiDoughnutChartComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiDoughnutChartComponent, "tum-ui-doughnut-chart", never, { "labels": { "alias": "labels"; "required": true; "isSignal": true; }; "series": { "alias": "series"; "required": true; "isSignal": true; }; "config": { "alias": "config"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "ariaLabelledBy": { "alias": "ariaLabelledBy"; "required": false; "isSignal": true; }; "interactive": { "alias": "interactive"; "required": false; "isSignal": true; }; }, { "dataSelect": "dataSelect"; }, never, never, true, never>;
}

interface LineView {
    key: string;
    paths: string[];
    color: string;
    dashed: boolean;
    points: {
        key: string;
        x: number;
        y: number;
        context: TumUiChartDatumContext;
    }[];
}
declare class TumUiLineChartComponent implements OnDestroy {
    private readonly hostElement;
    private readonly canvas;
    readonly labels: _angular_core.InputSignal<readonly string[]>;
    readonly series: _angular_core.InputSignal<readonly TumUiChartSeries[]>;
    readonly config: _angular_core.InputSignal<TumUiLineChartConfig>;
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    /** Names the chart from a visible heading instead of a literal label. */
    readonly ariaLabelledBy: _angular_core.InputSignal<string | undefined>;
    /** Marks points as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
    readonly interactive: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly dataSelect: _angular_core.OutputEmitterRef<TumUiChartSelectEvent>;
    protected readonly pointRadius = 3;
    /** Names an interactive datum for assistive technology; see {@link datumAccessibleName}. */
    protected accessibleName(context: TumUiChartDatumContext): string;
    private readonly size;
    protected readonly hovered: _angular_core.WritableSignal<{
        index: number;
        x: number;
        y: number;
        hostWidth: number;
        hostHeight: number;
    } | undefined>;
    private resizeObserver?;
    constructor();
    ngOnDestroy(): void;
    /** Series the reader switched off in the legend, by index. */
    private readonly hiddenSeries;
    protected onLegendToggle(key: string): void;
    /** The series actually drawn, paired with their original index so meta and colors stay aligned. */
    private readonly visibleSeries;
    /** As for the bar chart: an integer-valued series must not be given fractional ticks. */
    private readonly minTickStep;
    private readonly valueDomain;
    private readonly valueTickLabels;
    private readonly frame;
    protected readonly plot: _angular_core.Signal<ChartPlot>;
    /** Unique per instance, so charts sharing a page do not share a clip path. */
    protected readonly clipId: string;
    private readonly valueScale;
    private readonly categoryScale;
    protected readonly lines: _angular_core.Signal<LineView[]>;
    protected readonly gridLines: _angular_core.Signal<ChartGridLine[]>;
    protected readonly ticks: _angular_core.Signal<ChartTick[]>;
    protected readonly axisTitles: _angular_core.Signal<ChartAxisTitle[]>;
    protected readonly legendPosition: _angular_core.Signal<_tumaet_ui_angular.TumUiChartLegendPosition | undefined>;
    protected readonly legendItems: _angular_core.Signal<ChartLegendItem[]>;
    /** The x coordinate of the guide drawn through the hovered category. */
    protected readonly guideX: _angular_core.Signal<number | undefined>;
    protected readonly tooltip: _angular_core.Signal<{
        x: number;
        y: number;
        below: boolean;
        title: string;
        lines: string[];
    } | undefined>;
    protected readonly accessibleRows: _angular_core.Signal<{
        label: string;
        values: {
            seriesLabel: string | undefined;
            value: number | undefined;
        }[];
    }[]>;
    /** Resolves a pointer position to the nearest category, so hovering anywhere reports a series. */
    protected onPlotMove(event: MouseEvent): void;
    protected onPlotLeave(): void;
    /**
     * Emits the point closest to the click. The hit area covers the plot so that the whole chart is
     * clickable rather than only the few pixels of a marker, which matches how the hover behaves.
     */
    /** Keyboard activation of a focused point, which the plot-wide hit area cannot provide. */
    protected onPointSelect(context: TumUiChartDatumContext): void;
    protected onPlotClick(event: MouseEvent): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiLineChartComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiLineChartComponent, "tum-ui-line-chart", never, { "labels": { "alias": "labels"; "required": true; "isSignal": true; }; "series": { "alias": "series"; "required": true; "isSignal": true; }; "config": { "alias": "config"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "ariaLabelledBy": { "alias": "ariaLabelledBy"; "required": false; "isSignal": true; }; "interactive": { "alias": "interactive"; "required": false; "isSignal": true; }; }, { "dataSelect": "dataSelect"; }, never, never, true, never>;
}

interface TumUiCheckboxChangeEvent {
    originalEvent: Event;
    checked: boolean;
}
declare class TumUiCheckboxComponent implements ControlValueAccessor {
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly inputId: _angular_core.InputSignal<string | undefined>;
    readonly name: _angular_core.InputSignal<string | undefined>;
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    readonly checked: _angular_core.ModelSignal<boolean>;
    /**
     * Renders the partial-selection dash instead of the tick, for a select-all control whose rows are only
     * partly selected. Purely visual: it never changes `checked`, the model, or what `changed` emits.
     */
    readonly indeterminate: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly changed: _angular_core.OutputEmitterRef<TumUiCheckboxChangeEvent>;
    protected readonly faCheck: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faMinus: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly showDash: _angular_core.Signal<boolean>;
    protected readonly showTick: _angular_core.Signal<boolean>;
    private readonly cvaDisabled;
    protected readonly isDisabled: _angular_core.Signal<boolean>;
    protected readonly boxClasses: _angular_core.Signal<"tum:bg-disabled-background tum:border-control-border" | "tum:bg-primary tum:border-primary" | "tum:bg-control-background tum:border-control-border">;
    protected readonly iconClasses: _angular_core.Signal<"tum:text-disabled" | "tum:text-primary-contrast">;
    private onModelChange;
    private onModelTouched;
    protected onInputChange(event: Event): void;
    protected onBlur(): void;
    writeValue(value: boolean): void;
    registerOnChange(fn: (value: boolean) => void): void;
    registerOnTouched(fn: () => void): void;
    setDisabledState(isDisabled: boolean): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiCheckboxComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiCheckboxComponent, "tum-ui-checkbox", never, { "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "inputId": { "alias": "inputId"; "required": false; "isSignal": true; }; "name": { "alias": "name"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "checked": { "alias": "checked"; "required": false; "isSignal": true; }; "indeterminate": { "alias": "indeterminate"; "required": false; "isSignal": true; }; }, { "checked": "checkedChange"; "changed": "changed"; }, never, never, true, never>;
}

type TumUiChipSize = 'small';
declare class TumUiChipComponent {
    readonly label: _angular_core.InputSignal<string | undefined>;
    readonly removable: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly size: _angular_core.InputSignal<"small" | undefined>;
    readonly removeAriaLabel: _angular_core.InputSignal<string | undefined>;
    readonly removed: _angular_core.OutputEmitterRef<Event>;
    protected readonly faXmark: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly chipClasses: _angular_core.Signal<string>;
    protected remove(event: Event): void;
    protected onRemoveKeydown(event: KeyboardEvent): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiChipComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiChipComponent, "tum-ui-chip", never, { "label": { "alias": "label"; "required": false; "isSignal": true; }; "removable": { "alias": "removable"; "required": false; "isSignal": true; }; "size": { "alias": "size"; "required": false; "isSignal": true; }; "removeAriaLabel": { "alias": "removeAriaLabel"; "required": false; "isSignal": true; }; }, { "removed": "removed"; }, never, ["*"], true, never>;
}

/** Renders requests from the nearest `TumUiConfirmationService` as modal decisions. */
declare class TumUiConfirmDialogComponent {
    /** Static key used to select this dialog's confirmation requests. */
    readonly key: _angular_core.InputSignal<string | undefined>;
    private readonly confirmationService;
    protected readonly messageId: string;
    protected readonly request: _angular_core.Signal<_tumaet_ui_angular.TumUiConfirmationRequest | undefined>;
    protected readonly visible: _angular_core.Signal<boolean>;
    protected accept(): void;
    protected reject(): void;
    protected onDialogHide(): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiConfirmDialogComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiConfirmDialogComponent, "tum-ui-confirm-dialog", never, { "key": { "alias": "key"; "required": false; "isSignal": true; }; }, {}, never, never, true, never>;
}

/** Content and callbacks for one confirmation decision. */
interface TumUiConfirmationRequest {
    /** Dialog title. */
    header: string;
    /** Body text shown next to the optional icon. */
    message: string;
    /** Invoked when the user confirms. */
    accept: () => void;
    /** Invoked when the user cancels or dismisses the dialog. */
    reject?: () => void;
    /** Localized confirm-button label. */
    acceptLabel: string;
    /** Localized cancel-button label. */
    rejectLabel: string;
    /** Confirm button severity (default `'primary'`). */
    acceptSeverity?: TumUiButtonSeverity;
    /** Cancel button severity (default `'secondary'`). */
    rejectSeverity?: TumUiButtonSeverity;
    /** Optional leading icon shown before the message. */
    icon?: IconProp;
    /** Routes the request to a dialog with the same key. */
    key?: string;
}
/** Coordinates confirmation requests with dialogs in the same injector scope. */
declare class TumUiConfirmationService {
    private readonly requests;
    request(key: string | undefined): TumUiConfirmationRequest | undefined;
    confirm(request: TumUiConfirmationRequest): void;
    close(key: string | undefined): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiConfirmationService, never>;
    static ɵprov: _angular_core.ɵɵInjectableDeclaration<TumUiConfirmationService>;
}

/** Date-and-time field with typed input and an accessible calendar dialog; `timeOnly` reduces it to a time. */
declare class TumUiDatePickerComponent implements FormValueControl<dayjs.Dayjs | undefined> {
    private readonly overlayService;
    private readonly viewContainerRef;
    private readonly destroyRef;
    private readonly document;
    /**
     * Last committed date. Invalid text remains visible without updating it.
     * Observe `inputValidityChange` when validity must react to uncommitted text.
     */
    readonly value: _angular_core.ModelSignal<dayjs.Dayjs | undefined>;
    /** Adds an external validation error without discarding the last committed value. */
    readonly invalid: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Hides the visible label while retaining the input's accessible name. */
    readonly hideLabelName: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Hides the built-in validation message without changing validity or `aria-invalid`. */
    readonly hideValidationMessage: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Shows the browser time-zone indicator beside the label. */
    readonly shouldDisplayTimeZoneWarning: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /**
     * Reduces the field to a time: the text is `HH:mm`, the dialog drops the calendar, and only the clock is
     * shown. The value stays a full Dayjs — the date is carried over from the value already held, or is
     * today — so a caller that only cares about the time can read it and one that needs a moment still gets a
     * complete one.
     */
    readonly timeOnly: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** ID used to associate the input, label, validation message, and dialog. */
    readonly inputId: _angular_core.InputSignal<string>;
    /** Visible label text or package translation key. */
    readonly labelName: _angular_core.InputSignal<string | undefined>;
    /** Accessible name used when no visible label is rendered. */
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    /** Emits text-input validity independently of the external `invalid` state. */
    readonly inputValidityChange: _angular_core.OutputEmitterRef<boolean>;
    /** Emits when the text input loses focus. */
    readonly touch: _angular_core.OutputEmitterRef<void>;
    protected readonly faCalendar: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faXmark: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faGlobe: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faClock: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faChevronUp: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faChevronDown: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected get currentTimeZone(): string;
    private readonly valueKey;
    private readonly isInputValid;
    protected readonly isOpen: _angular_core.WritableSignal<boolean>;
    protected readonly panelId: _angular_core.Signal<string>;
    protected readonly activeMonth: _angular_core.WritableSignal<dayjs.Dayjs>;
    protected readonly timeText: _angular_core.WritableSignal<string>;
    protected readonly inputText: _angular_core.WritableSignal<string>;
    private readonly panel;
    private readonly dateInput;
    private readonly triggerWrapper;
    private readonly hourField;
    private overlayRef?;
    private restoreFocusElement?;
    private pendingHourFocus;
    protected readonly showErrorBorder: _angular_core.Signal<boolean>;
    protected readonly placeholderKey: _angular_core.Signal<"tumUi.datePicker.timePlaceholder" | "tumUi.datePicker.placeholder">;
    protected readonly dialogLabelKey: _angular_core.Signal<"tumUi.datePicker.timeDialog" | "tumUi.datePicker.dialog">;
    protected readonly invalidMessageKey: _angular_core.Signal<"tumUi.datePicker.invalidTime" | "tumUi.datePicker.invalid">;
    protected readonly openLabelKey: _angular_core.Signal<"tumUi.datePicker.openTime" | "tumUi.datePicker.open">;
    protected readonly showClear: _angular_core.Signal<boolean>;
    protected readonly displayHour: _angular_core.Signal<string>;
    protected readonly displayMinute: _angular_core.Signal<string>;
    constructor();
    /** Whether the entered text parses successfully and the external `invalid` state is clear. */
    readonly isValid: _angular_core.Signal<boolean>;
    protected onInput(raw: string): void;
    protected onBlur(raw: string): void;
    protected stepHour(delta: number): void;
    protected stepMinute(delta: number): void;
    protected onHourInput(input: HTMLInputElement): void;
    protected onMinuteInput(input: HTMLInputElement): void;
    protected onTimeKeydown(event: KeyboardEvent, input: HTMLInputElement, field: 'hour' | 'minute'): void;
    private currentTimeParts;
    private parseTimePart;
    private commitTime;
    protected onDaySelect(day: dayjs.Dayjs): void;
    protected clear(): void;
    protected toggle(): void;
    protected openFromInput(event: Event): void;
    protected open(): void;
    protected close(): void;
    focus(options?: FocusOptions): void;
    private commit;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiDatePickerComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiDatePickerComponent, "tum-ui-date-picker", never, { "value": { "alias": "value"; "required": false; "isSignal": true; }; "invalid": { "alias": "invalid"; "required": false; "isSignal": true; }; "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "hideLabelName": { "alias": "hideLabelName"; "required": false; "isSignal": true; }; "hideValidationMessage": { "alias": "hideValidationMessage"; "required": false; "isSignal": true; }; "shouldDisplayTimeZoneWarning": { "alias": "shouldDisplayTimeZoneWarning"; "required": false; "isSignal": true; }; "timeOnly": { "alias": "timeOnly"; "required": false; "isSignal": true; }; "inputId": { "alias": "inputId"; "required": false; "isSignal": true; }; "labelName": { "alias": "labelName"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; }, { "value": "valueChange"; "inputValidityChange": "inputValidityChange"; "touch": "touch"; }, never, never, true, never>;
}

type TumUiDialogSize = 'small' | 'medium' | 'large' | 'full';
/** Controlled modal dialog built on Angular CDK Dialog. */
declare class TumUiDialogComponent implements OnDestroy {
    private readonly dialog;
    private readonly overlay;
    private readonly viewContainerRef;
    /** Controlled open state; dismissal writes `false`. */
    readonly visible: _angular_core.ModelSignal<boolean>;
    /** Visible title and default accessible name. */
    readonly header: _angular_core.InputSignal<string | undefined>;
    /** Hides the header; an `ariaLabel` is then required. */
    readonly showHeader: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Shows the close button without changing Escape or backdrop behavior. */
    readonly closable: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Allows Escape to close the dialog. */
    readonly closeOnEscape: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Allows a backdrop click to close the dialog. */
    readonly dismissableMask: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Responsive dialog dimensions. Omit for content-sized dialogs. */
    readonly size: _angular_core.InputSignal<TumUiDialogSize | undefined>;
    /** Accessible name used when no visible header or header template is present. */
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    readonly closeButtonAriaLabel: _angular_core.InputSignal<string | undefined>;
    readonly role: _angular_core.InputSignal<DialogRole>;
    readonly ariaDescribedBy: _angular_core.InputSignal<string | undefined>;
    readonly shown: _angular_core.OutputEmitterRef<void>;
    readonly hidden: _angular_core.OutputEmitterRef<void>;
    private readonly panel;
    protected readonly headerTemplate: _angular_core.Signal<TemplateRef<any> | undefined>;
    protected readonly footerTemplate: _angular_core.Signal<TemplateRef<any> | undefined>;
    protected readonly titleId: string;
    protected readonly faXmark: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly labelledBy: _angular_core.Signal<string | undefined>;
    protected readonly sizeClasses: _angular_core.Signal<string>;
    private dialogRef?;
    private readonly visibilitySync;
    close(): void;
    private open;
    ngOnDestroy(): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiDialogComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiDialogComponent, "tum-ui-dialog", never, { "visible": { "alias": "visible"; "required": false; "isSignal": true; }; "header": { "alias": "header"; "required": false; "isSignal": true; }; "showHeader": { "alias": "showHeader"; "required": false; "isSignal": true; }; "closable": { "alias": "closable"; "required": false; "isSignal": true; }; "closeOnEscape": { "alias": "closeOnEscape"; "required": false; "isSignal": true; }; "dismissableMask": { "alias": "dismissableMask"; "required": false; "isSignal": true; }; "size": { "alias": "size"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "closeButtonAriaLabel": { "alias": "closeButtonAriaLabel"; "required": false; "isSignal": true; }; "role": { "alias": "role"; "required": false; "isSignal": true; }; "ariaDescribedBy": { "alias": "ariaDescribedBy"; "required": false; "isSignal": true; }; }, { "visible": "visibleChange"; "shown": "shown"; "hidden": "hidden"; }, ["headerTemplate", "footerTemplate"], ["*"], true, never>;
}

/**
 * Contract a {@link TumUiFormFieldComponent} exposes to the control it wraps, so the control can adopt the
 * field's label target, description, and validity without the consumer repeating any of them.
 *
 * A control opts in by injecting {@link TUM_UI_FORM_FIELD} optionally. A component that renders another TUM
 * UI control inside its own view must shadow the token in `viewProviders` and forward what it needs, so the
 * field's wiring is applied once rather than by both the wrapper and the control nested inside it.
 *
 * Ids resolve in one direction at a time, so the label can never point at an element that is not there:
 * an `explicitControlId` set on the field wins and the control adopts it; otherwise a control that brought
 * an id of its own reports it through {@link adoptControlId} and the field labels that; otherwise the
 * control adopts the field's generated {@link labelTargetId}.
 */
interface TumUiFormFieldContext {
    /** Id the host set explicitly on the field, which overrides any id the control brought. */
    readonly explicitControlId: Signal<string | undefined>;
    /** Id the field's `<label for>` points at; a control without an id of its own adopts it. */
    readonly labelTargetId: Signal<string>;
    /** Space-separated ids of the text currently describing the field, or `undefined` when it shows none. */
    readonly describedBy: Signal<string | undefined>;
    /** Whether the field is currently showing an error, so the control can render its invalid state. */
    readonly invalid: Signal<boolean>;
    /** Reports the id a control brought with it, so the label targets the real control. */
    adoptControlId(id: string): void;
}
declare const TUM_UI_FORM_FIELD: InjectionToken<TumUiFormFieldContext | null>;

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
declare class TumUiFormFieldComponent implements TumUiFormFieldContext {
    /** Label text. Omit it when projecting a `[tumUiFormFieldLabel]` slot instead. */
    readonly label: _angular_core.InputSignal<string>;
    /**
     * Id of the control this field labels. Defaults to a generated id, which a TUM UI control adopts unless
     * it carries an id of its own. Set it when the id has to be stable, such as for an end-to-end selector.
     */
    readonly controlId: _angular_core.InputSignal<string | undefined>;
    /**
     * Renders the required marker. The marker is decorative: the control still needs its own `required`, which
     * is what assistive technology reports.
     */
    readonly required: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Helper text shown below the control while the field is valid. */
    readonly hint: _angular_core.InputSignal<string | undefined>;
    /** Shows the error region and marks the wrapped control invalid. */
    readonly invalid: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Error text. Project a `[tumUiFormFieldError]` slot instead when several messages can apply. */
    readonly error: _angular_core.InputSignal<string | undefined>;
    /** Id a wrapped control reported because it brought one of its own. */
    private readonly reportedControlId;
    private readonly fieldId;
    private readonly generatedControlId;
    protected readonly hintId: string;
    protected readonly errorId: string;
    readonly explicitControlId: _angular_core.InputSignal<string | undefined>;
    readonly labelTargetId: _angular_core.Signal<string>;
    adoptControlId(id: string): void;
    protected readonly showHint: _angular_core.Signal<boolean>;
    readonly describedBy: _angular_core.Signal<string | undefined>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiFormFieldComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiFormFieldComponent, "tum-ui-form-field", never, { "label": { "alias": "label"; "required": false; "isSignal": true; }; "controlId": { "alias": "controlId"; "required": false; "isSignal": true; }; "required": { "alias": "required"; "required": false; "isSignal": true; }; "hint": { "alias": "hint"; "required": false; "isSignal": true; }; "invalid": { "alias": "invalid"; "required": false; "isSignal": true; }; "error": { "alias": "error"; "required": false; "isSignal": true; }; }, {}, never, ["[tumUiFormFieldLabel]", "*", "[tumUiFormFieldError]"], true, never>;
}

type TumUiTranslationParams = Readonly<Record<string, number | string>>;
declare const TUM_UI_DEFAULT_TRANSLATIONS: {
    readonly 'tumUi.autocomplete.empty': "No results found";
    readonly 'tumUi.autocomplete.remove': "Remove";
    readonly 'tumUi.chip.remove': "Remove";
    readonly 'tumUi.datePicker.timeZoneWarning': "The displayed date and time use the {timeZone} time zone.";
    readonly 'tumUi.datePicker.clear': "Clear date";
    readonly 'tumUi.datePicker.decrementHour': "Decrement hour";
    readonly 'tumUi.datePicker.decrementMinute': "Decrement minute";
    readonly 'tumUi.datePicker.dialog': "Choose date and time";
    readonly 'tumUi.datePicker.done': "Done";
    readonly 'tumUi.datePicker.hour': "Hour";
    readonly 'tumUi.datePicker.incrementHour': "Increment hour";
    readonly 'tumUi.datePicker.incrementMinute': "Increment minute";
    readonly 'tumUi.datePicker.invalid': "Enter a valid date and time.";
    readonly 'tumUi.datePicker.invalidTime': "Enter a valid time.";
    readonly 'tumUi.datePicker.minute': "Minute";
    readonly 'tumUi.datePicker.open': "Open calendar";
    readonly 'tumUi.datePicker.openTime': "Open clock";
    readonly 'tumUi.datePicker.placeholder': "DD.MM.YYYY HH:mm";
    readonly 'tumUi.datePicker.nextMonth': "Next month: {month}";
    readonly 'tumUi.datePicker.previousMonth': "Previous month: {month}";
    readonly 'tumUi.datePicker.time': "Time";
    readonly 'tumUi.datePicker.timeDialog': "Choose time";
    readonly 'tumUi.datePicker.timePlaceholder': "HH:mm";
    readonly 'tumUi.dialog.close': "Close";
    readonly 'tumUi.panel.collapse': "Collapse";
    readonly 'tumUi.panel.expand': "Expand";
    readonly 'tumUi.paginator.ariaLabel': "Pagination";
    readonly 'tumUi.paginator.currentPageReport': "Showing {first} to {second} of {total}";
    readonly 'tumUi.paginator.first': "First page";
    readonly 'tumUi.paginator.last': "Last page";
    readonly 'tumUi.paginator.next': "Next page";
    readonly 'tumUi.paginator.previous': "Previous page";
    readonly 'tumUi.paginator.rowsPerPage': "Rows per page";
    readonly 'tumUi.searchField.clear': "Clear search";
    readonly 'tumUi.searchField.placeholder': "Search";
    readonly 'tumUi.select.clear': "Clear selection";
    readonly 'tumUi.select.empty': "No available options";
    readonly 'tumUi.select.filter': "Filter options";
    readonly 'tumUi.select.noResults': "No matching options";
    readonly 'tumUi.step.pending': "Not started";
    readonly 'tumUi.step.current': "In progress";
    readonly 'tumUi.step.complete': "Done";
    readonly 'tumUi.step.failed': "Failed";
    readonly 'tumUi.step.skipped': "Skipped";
    readonly 'tumUi.table.actions': "Actions";
    readonly 'tumUi.table.noResults': "No results found";
    readonly 'tumUi.table.searchPlaceholder': "Search";
};
type TumUiTranslationKey = keyof typeof TUM_UI_DEFAULT_TRANSLATIONS;
interface TumUiTranslator {
    /** Optional signal that changes whenever the active translation catalog changes. */
    readonly translationChanges?: Signal<unknown>;
    /** Optional locale passed to locale-sensitive browser formatting APIs. */
    readonly locale?: Signal<string | undefined>;
    translate(key: string, params?: TumUiTranslationParams): string;
}
declare const TUM_UI_TRANSLATOR: InjectionToken<TumUiTranslator>;
declare function provideTumUiTranslator(translator: Type<TumUiTranslator>): EnvironmentProviders;

type TumUiIconFieldPosition = 'left' | 'right';
declare class TumUiIconFieldComponent {
    readonly icon: _angular_core.InputSignal<IconProp | undefined>;
    readonly iconPosition: _angular_core.InputSignal<TumUiIconFieldPosition>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiIconFieldComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiIconFieldComponent, "tum-ui-icon-field", never, { "icon": { "alias": "icon"; "required": false; "isSignal": true; }; "iconPosition": { "alias": "iconPosition"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

declare class TumUiInputGroupAddonComponent {
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiInputGroupAddonComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiInputGroupAddonComponent, "tum-ui-input-group-addon", never, {}, {}, never, ["*"], true, never>;
}

declare class TumUiInputGroupComponent {
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiInputGroupComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiInputGroupComponent, "tum-ui-input-group", never, {}, {}, never, ["*"], true, never>;
}

/** Numeric input with locale grouping, optional affixes, optional decimals and step controls. */
declare class TumUiInputNumberComponent implements ControlValueAccessor {
    /** Lower bound applied on blur and stepping. */
    readonly min: _angular_core.InputSignal<number | undefined>;
    /** Upper bound applied on blur and stepping. */
    readonly max: _angular_core.InputSignal<number | undefined>;
    /** Increment / decrement applied by the stepper buttons and Arrow Up / Down keys. */
    readonly step: _angular_core.InputSignalWithTransform<number, unknown>;
    /** Shows increment and decrement controls. */
    readonly showButtons: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Text displayed before the formatted number. */
    readonly prefix: _angular_core.InputSignal<string | undefined>;
    /** Text displayed after the formatted number. */
    readonly suffix: _angular_core.InputSignal<string | undefined>;
    readonly placeholder: _angular_core.InputSignal<string | undefined>;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Marks the field invalid without changing its value. */
    readonly invalid: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Expands the field to the available width. */
    readonly fluid: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Enables locale-specific digit grouping. */
    readonly useGrouping: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /**
     * Maximum fraction digits. `0` (the default) keeps the field integer-only; a positive value lets the user
     * type the locale's decimal separator, and the fraction is truncated — not rounded — to this many digits.
     */
    readonly maxFractionDigits: _angular_core.InputSignalWithTransform<number, unknown>;
    /** Locale used for formatting; omit it to use the browser locale. */
    readonly locale: _angular_core.InputSignal<string | undefined>;
    /**
     * `id` of the inner `<input>`, so an external `<label for>` associates. Defaults to the id of an enclosing
     * `tum-ui-form-field`, and to a unique per-instance id outside one.
     */
    readonly inputId: _angular_core.InputSignal<string | undefined>;
    /** Native input name. */
    readonly name: _angular_core.InputSignal<string | undefined>;
    /** Accessible name for the inner `<input>` when there is no visible `<label>`. */
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    /** Element `id` values that label the inner `<input>`. */
    readonly ariaLabelledBy: _angular_core.InputSignal<string | undefined>;
    /** Element `id` values that describe the inner `<input>`. */
    readonly ariaDescribedBy: _angular_core.InputSignal<string | undefined>;
    private readonly inputRef;
    private readonly cvaValue;
    private readonly cvaDisabled;
    protected readonly isDisabled: _angular_core.Signal<boolean>;
    protected readonly faChevronUp: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faChevronDown: _fortawesome_fontawesome_svg_core.IconDefinition;
    private readonly numberFormatter;
    private readonly localeNumberSyntax;
    private readonly formattedValue;
    protected readonly displayText: _angular_core.WritableSignal<string>;
    protected readonly ariaValueNow: _angular_core.Signal<number | undefined>;
    protected readonly ariaValueText: _angular_core.Signal<string | null>;
    private onModelChange;
    private onModelTouched;
    private format;
    private createLocaleNumberSyntax;
    private stripAffixes;
    private parse;
    /**
     * True while the text holds a fraction the user is still entering that reformatting would swallow — a
     * trailing decimal separator (`12.`) or trailing fraction zeros (`12.50`). The raw text is kept until the
     * entry settles on blur, mirroring the lone-minus-sign guard in {@link onInput}.
     */
    private fractionEntryInProgress;
    private clamp;
    private matchAt;
    private toAsciiDigits;
    private digitCount;
    private caretAfterDigits;
    protected onInput(event: Event): void;
    protected onStep(delta: number): void;
    protected onKeydown(event: KeyboardEvent): void;
    protected onBlurHandler(): void;
    writeValue(value: number | undefined): void;
    registerOnChange(fn: (value: number | undefined) => void): void;
    registerOnTouched(fn: () => void): void;
    setDisabledState(isDisabled: boolean): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiInputNumberComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiInputNumberComponent, "tum-ui-input-number", never, { "min": { "alias": "min"; "required": false; "isSignal": true; }; "max": { "alias": "max"; "required": false; "isSignal": true; }; "step": { "alias": "step"; "required": false; "isSignal": true; }; "showButtons": { "alias": "showButtons"; "required": false; "isSignal": true; }; "prefix": { "alias": "prefix"; "required": false; "isSignal": true; }; "suffix": { "alias": "suffix"; "required": false; "isSignal": true; }; "placeholder": { "alias": "placeholder"; "required": false; "isSignal": true; }; "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "invalid": { "alias": "invalid"; "required": false; "isSignal": true; }; "fluid": { "alias": "fluid"; "required": false; "isSignal": true; }; "useGrouping": { "alias": "useGrouping"; "required": false; "isSignal": true; }; "maxFractionDigits": { "alias": "maxFractionDigits"; "required": false; "isSignal": true; }; "locale": { "alias": "locale"; "required": false; "isSignal": true; }; "inputId": { "alias": "inputId"; "required": false; "isSignal": true; }; "name": { "alias": "name"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "ariaLabelledBy": { "alias": "ariaLabelledBy"; "required": false; "isSignal": true; }; "ariaDescribedBy": { "alias": "ariaDescribedBy"; "required": false; "isSignal": true; }; }, {}, never, never, true, never>;
}

type TumUiInputSize = 'small' | 'large';

declare class TumUiInputDirective {
    private readonly elementRef;
    private readonly formField;
    readonly tumUiInputSize: _angular_core.InputSignal<TumUiInputSize | undefined>;
    readonly tumUiInputInvalid: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /**
     * Overrides the element id. Set it from a wrapper that owns the id; a plain `id` attribute on the element
     * works just as well and is left untouched.
     */
    readonly tumUiInputId: _angular_core.InputSignal<string | undefined>;
    /** Extra description ids to merge in, for a wrapper component that owns describing text of its own. */
    readonly tumUiInputDescribedBy: _angular_core.InputSignal<string | undefined>;
    private readonly staticId;
    private readonly staticDescribedBy;
    private readonly fallbackId;
    /** The id this element brought with it, if any, as opposed to one adopted from a form field. */
    private readonly ownId;
    /**
     * Resolved element id. A form field told to label a specific id wins, so the label can never point at an
     * element that is not there; otherwise an id the element brought wins, then the field's, then a generated
     * one.
     */
    readonly controlId: _angular_core.Signal<string>;
    protected readonly describedBy: _angular_core.Signal<string | null>;
    protected readonly isInvalid: _angular_core.Signal<boolean>;
    protected readonly hostClasses: _angular_core.Signal<string>;
    constructor();
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiInputDirective, never>;
    static ɵdir: _angular_core.ɵɵDirectiveDeclaration<TumUiInputDirective, "input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]", never, { "tumUiInputSize": { "alias": "tumUiInputSize"; "required": false; "isSignal": true; }; "tumUiInputInvalid": { "alias": "tumUiInputInvalid"; "required": false; "isSignal": true; }; "tumUiInputId": { "alias": "tumUiInputId"; "required": false; "isSignal": true; }; "tumUiInputDescribedBy": { "alias": "tumUiInputDescribedBy"; "required": false; "isSignal": true; }; }, {}, never, never, true, never>;
}

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
declare class TumUiListItemActionDirective {
    /** Marks this row as the one currently shown, which sets `aria-current="page"`. */
    readonly active: _angular_core.InputSignalWithTransform<boolean, unknown>;
    protected readonly hostClasses: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiListItemActionDirective, never>;
    static ɵdir: _angular_core.ɵɵDirectiveDeclaration<TumUiListItemActionDirective, "a[tumUiListItemAction], button[tumUiListItemAction]", never, { "active": { "alias": "active"; "required": false; "isSignal": true; }; }, {}, never, never, true, never>;
}

/**
 * A single row of a {@link TumUiListComponent}.
 *
 * Applied to a real `<li>` so the list keeps its native semantics. Put a `[tumUiListItemAction]` link or
 * button inside for a row that navigates or acts; leave it out for a plain content row.
 */
declare class TumUiListItemDirective {
    /**
     * Lays the row out on one line — a label beside its value, or a label beside its control — instead of
     * stacking its content. The row owns its direction because the package stylesheet is unlayered and loads
     * after the host's, so an application `flex-row` utility cannot override it.
     */
    readonly inline: _angular_core.InputSignalWithTransform<boolean, unknown>;
    private readonly action;
    protected readonly hostClasses: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiListItemDirective, never>;
    static ɵdir: _angular_core.ɵɵDirectiveDeclaration<TumUiListItemDirective, "li[tumUiListItem]", never, { "inline": { "alias": "inline"; "required": false; "isSignal": true; }; }, {}, ["action"], never, true, never>;
}

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
declare class TumUiListComponent {
    /** Accessible name for the list. Set it when the list has no visible heading beside it. */
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    /** Id of the visible heading that names the list. Prefer this over `ariaLabel` when a heading exists. */
    readonly ariaLabelledBy: _angular_core.InputSignal<string | undefined>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiListComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiListComponent, "tum-ui-list", never, { "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "ariaLabelledBy": { "alias": "ariaLabelledBy"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

/**
 * A single command or navigation entry inside a {@link TumUiMenuComponent}.
 *
 * Apply it to a `<button>` for an action, or to an `<a>` for navigation so the entry keeps native link
 * behaviour such as opening in a new tab. `disabled` and the `triggered` output come from the CDK menu item,
 * which also owns the `role`, roving `tabindex`, and closing the menu once an entry runs.
 */
declare class TumUiMenuItemDirective {
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiMenuItemDirective, never>;
    static ɵdir: _angular_core.ɵɵDirectiveDeclaration<TumUiMenuItemDirective, "[tumUiMenuItem]", never, {}, {}, never, never, true, [{ directive: typeof i1.CdkMenuItem; inputs: { "cdkMenuItemDisabled": "disabled"; }; outputs: { "cdkMenuItemTriggered": "triggered"; }; }]>;
}

/**
 * Opens a {@link TumUiMenuComponent} from the element it sits on, which is normally a button.
 *
 * Point it at the `ng-template` that holds the menu: `<button [tumUiMenuTrigger]="actions">`. The CDK menu
 * trigger owns the overlay, `aria-haspopup` / `aria-expanded`, opening on Enter, Space, or ArrowDown, closing
 * on Escape or an outside click, and restoring focus to the trigger afterwards.
 */
declare class TumUiMenuTriggerDirective {
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiMenuTriggerDirective, never>;
    static ɵdir: _angular_core.ɵɵDirectiveDeclaration<TumUiMenuTriggerDirective, "[tumUiMenuTrigger]", never, {}, {}, never, never, true, [{ directive: typeof i1.CdkMenuTrigger; inputs: { "cdkMenuTriggerFor": "tumUiMenuTrigger"; "cdkMenuPosition": "tumUiMenuPosition"; }; outputs: { "cdkMenuOpened": "menuOpened"; "cdkMenuClosed": "menuClosed"; }; }]>;
}

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
declare class TumUiMenuComponent {
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiMenuComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiMenuComponent, "tum-ui-menu", never, {}, {}, never, ["*"], true, [{ directive: typeof i1.CdkMenu; inputs: {}; outputs: {}; }]>;
}

type TumUiMessageSeverity = 'info' | 'success' | 'warn' | 'error' | 'secondary' | 'contrast';
declare class TumUiMessageComponent {
    readonly severity: _angular_core.InputSignal<TumUiMessageSeverity>;
    /** Disable when a surrounding live announcer already reports this message. */
    readonly announce: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly text: _angular_core.InputSignal<string | undefined>;
    readonly icon: _angular_core.InputSignal<IconProp | undefined>;
    protected readonly messageRole: _angular_core.Signal<"alert" | "status" | null>;
    protected readonly hostClasses: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiMessageComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiMessageComponent, "tum-ui-message", never, { "severity": { "alias": "severity"; "required": false; "isSignal": true; }; "announce": { "alias": "announce"; "required": false; "isSignal": true; }; "text": { "alias": "text"; "required": false; "isSignal": true; }; "icon": { "alias": "icon"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

/** Controlled paginator using zero-based page indexes. */
declare class TumUiPaginatorComponent {
    private readonly directionality;
    private readonly direction;
    private readonly destroyRef;
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    /** Total records in the consumer-owned result set. */
    readonly totalRecords: _angular_core.InputSignalWithTransform<number, unknown>;
    /** Zero-based active page index. */
    readonly page: _angular_core.InputSignalWithTransform<number, unknown>;
    /** Controlled number of records per page. */
    readonly pageSize: _angular_core.InputSignalWithTransform<number, unknown>;
    readonly pageSizeOptions: _angular_core.InputSignal<number[]>;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly showCurrentPageReport: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly showRowsPerPage: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Requests a zero-based page without mutating `page`. */
    readonly pageChange: _angular_core.OutputEmitterRef<number>;
    /** Requests a page size without mutating `pageSize`. */
    readonly pageSizeChange: _angular_core.OutputEmitterRef<number>;
    protected readonly firstPageIcon: _angular_core.Signal<_fortawesome_fontawesome_svg_core.IconDefinition>;
    protected readonly previousPageIcon: _angular_core.Signal<_fortawesome_fontawesome_svg_core.IconDefinition>;
    protected readonly nextPageIcon: _angular_core.Signal<_fortawesome_fontawesome_svg_core.IconDefinition>;
    protected readonly lastPageIcon: _angular_core.Signal<_fortawesome_fontawesome_svg_core.IconDefinition>;
    protected readonly navButtonClasses = "tum:inline-flex tum:h-9 tum:w-9 tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:rounded-full tum:border-0 tum:bg-transparent tum:text-sm tum:text-muted tum:transition-colors tum:hover:bg-hover-background tum:disabled:pointer-events-none tum:disabled:opacity-50";
    protected readonly selectedPageClasses: string;
    protected readonly totalPages: _angular_core.Signal<number>;
    protected readonly clampedPage: _angular_core.Signal<number>;
    protected readonly isFirst: _angular_core.Signal<boolean>;
    protected readonly isLast: _angular_core.Signal<boolean>;
    protected readonly rangeBegin: _angular_core.Signal<number>;
    protected readonly rangeEnd: _angular_core.Signal<number>;
    protected readonly visiblePages: _angular_core.Signal<number[]>;
    constructor();
    protected goToPage(target: number): void;
    protected goToFirst(): void;
    protected goToPrevious(): void;
    protected goToNext(): void;
    protected goToLast(): void;
    protected onPageSizeChange(value: number): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiPaginatorComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiPaginatorComponent, "tum-ui-paginator", never, { "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "totalRecords": { "alias": "totalRecords"; "required": false; "isSignal": true; }; "page": { "alias": "page"; "required": false; "isSignal": true; }; "pageSize": { "alias": "pageSize"; "required": false; "isSignal": true; }; "pageSizeOptions": { "alias": "pageSizeOptions"; "required": false; "isSignal": true; }; "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "showCurrentPageReport": { "alias": "showCurrentPageReport"; "required": false; "isSignal": true; }; "showRowsPerPage": { "alias": "showRowsPerPage"; "required": false; "isSignal": true; }; }, { "pageChange": "pageChange"; "pageSizeChange": "pageSizeChange"; }, never, never, true, never>;
}

declare class TumUiPanelComponent {
    private readonly translator;
    /**
     * Header title text. Omit when projecting a `[tumUiPanelHeader]` slot instead, and set `toggleAriaLabel`
     * alongside it — projected markup does not label the toggle.
     */
    readonly header: _angular_core.InputSignal<string>;
    /** Enables disclosure behavior for the projected content. */
    readonly toggleable: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Overrides the toggle name; otherwise the header or package translation is used. */
    readonly toggleAriaLabel: _angular_core.InputSignal<string | undefined>;
    /** Controlled disclosure state, applied only when `toggleable` is enabled. */
    readonly collapsed: _angular_core.ModelSignal<boolean>;
    protected readonly headerId: string;
    protected readonly contentId: string;
    protected readonly faChevronDown: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faChevronUp: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly isCollapsed: _angular_core.Signal<boolean>;
    protected readonly toggleLabelledBy: _angular_core.Signal<string | null>;
    protected readonly toggleLabel: _angular_core.Signal<string | null>;
    protected toggle(): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiPanelComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiPanelComponent, "tum-ui-panel", never, { "header": { "alias": "header"; "required": false; "isSignal": true; }; "toggleable": { "alias": "toggleable"; "required": false; "isSignal": true; }; "toggleAriaLabel": { "alias": "toggleAriaLabel"; "required": false; "isSignal": true; }; "collapsed": { "alias": "collapsed"; "required": false; "isSignal": true; }; }, { "collapsed": "collapsedChange"; }, never, ["[tumUiPanelHeader]", "*", "[tumUiPanelFooter]"], true, never>;
}

type TumUiOverlayPlacement = 'top' | 'bottom' | 'left' | 'right';

/**
 * Anchored panel for rich or interactive content, opened via {@link TumUiPopoverTriggerDirective}. Use the
 * tooltip instead for a short, non-interactive hint.
 *
 * Built on the shared overlay substrate, so it inherits collision-aware positioning with a flipped fallback.
 * Closes on backdrop click and Escape, and traps then restores focus. Renders nothing inline: the projected
 * content is captured in an `ng-template` and portaled on open.
 */
declare class TumUiPopoverComponent implements OnDestroy {
    private readonly overlayService;
    private readonly viewContainerRef;
    readonly placement: _angular_core.InputSignal<TumUiOverlayPlacement>;
    /** Accessible name announced for the role="dialog" panel. Required: a dialog must have a name. */
    readonly ariaLabel: _angular_core.InputSignal<string>;
    readonly openChange: _angular_core.OutputEmitterRef<boolean>;
    private readonly panel;
    private overlayRef?;
    private readonly openState;
    /** Whether the popover is currently open. Read-only: drive it through open() / close() / toggle(). */
    readonly isOpen: _angular_core.Signal<boolean>;
    /** Open the popover anchored to `origin`. No-op if already open. */
    open(origin: ElementRef<HTMLElement> | HTMLElement): void;
    /** Close the popover and dispose its overlay. No-op if already closed. */
    close(): void;
    /** Open the popover if closed, or close it if open. */
    toggle(origin: ElementRef<HTMLElement> | HTMLElement): void;
    ngOnDestroy(): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiPopoverComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiPopoverComponent, "tum-ui-popover", never, { "placement": { "alias": "placement"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": true; "isSignal": true; }; }, { "openChange": "openChange"; }, never, ["*"], true, never>;
}

/**
 * Wires a trigger element to a {@link TumUiPopoverComponent}: click toggles the popover anchored to
 * the trigger, and the trigger reflects `aria-haspopup`/`aria-expanded` for accessibility.
 *
 * Usage: `<button [tumUiPopoverTrigger]="pop">Details</button> <tum-ui-popover #pop>...</tum-ui-popover>`
 */
declare class TumUiPopoverTriggerDirective {
    private readonly elementRef;
    readonly popover: _angular_core.InputSignal<TumUiPopoverComponent>;
    protected toggle(): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiPopoverTriggerDirective, never>;
    static ɵdir: _angular_core.ɵɵDirectiveDeclaration<TumUiPopoverTriggerDirective, "[tumUiPopoverTrigger]", never, { "popover": { "alias": "tumUiPopoverTrigger"; "required": true; "isSignal": true; }; }, {}, never, never, true, never>;
}

type TumUiProgressBarSeverity = 'primary' | 'success' | 'warn' | 'danger' | 'info';
type TumUiProgressBarSize = 'small' | 'default';
declare class TumUiProgressBarComponent {
    readonly value: _angular_core.InputSignalWithTransform<number, unknown>;
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    readonly showValue: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Track height. `small` is a slim rail for dense contexts such as table cells and has no room for the inline label. */
    readonly size: _angular_core.InputSignal<TumUiProgressBarSize>;
    /** Semantic color of the filled track. */
    readonly severity: _angular_core.InputSignal<TumUiProgressBarSeverity>;
    readonly unit: _angular_core.InputSignal<string>;
    protected readonly normalizedValue: _angular_core.Signal<number>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiProgressBarComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiProgressBarComponent, "tum-ui-progress-bar", never, { "value": { "alias": "value"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "showValue": { "alias": "showValue"; "required": false; "isSignal": true; }; "size": { "alias": "size"; "required": false; "isSignal": true; }; "severity": { "alias": "severity"; "required": false; "isSignal": true; }; "unit": { "alias": "unit"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

declare class TumUiProgressSpinnerComponent {
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiProgressSpinnerComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiProgressSpinnerComponent, "tum-ui-progress-spinner", never, { "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; }, {}, never, never, true, never>;
}

interface TumUiRadioButtonSelectEvent {
    originalEvent: MouseEvent;
    value: unknown;
}
/** Native radio control with TUM UI styling and Angular forms integration. */
declare class TumUiRadioButtonComponent implements ControlValueAccessor {
    /** Value written to the containing form when this option is selected. */
    readonly value: _angular_core.InputSignal<unknown>;
    /** Native radio-group name. Radios belong together when they share a form owner and name. */
    readonly name: _angular_core.InputSignal<string | undefined>;
    /** ID used to associate a consumer-provided label with the native radio. */
    readonly inputId: _angular_core.InputSignal<string | undefined>;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Accessible name used when no associated label is rendered. */
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    /** Emits the originating click and selected option value. */
    readonly selected: _angular_core.OutputEmitterRef<TumUiRadioButtonSelectEvent>;
    private readonly cvaValue;
    protected readonly isChecked: _angular_core.Signal<boolean>;
    private readonly cvaDisabled;
    protected readonly isDisabled: _angular_core.Signal<boolean>;
    protected readonly boxClasses: _angular_core.Signal<"tum:bg-disabled-background tum:border-control-border" | "tum:bg-primary tum:border-primary" | "tum:bg-control-background tum:border-control-border">;
    protected readonly iconClasses: _angular_core.Signal<"tum:bg-disabled" | "tum:bg-primary-contrast">;
    private onModelChange;
    private onModelTouched;
    protected onInputClick(event: MouseEvent): void;
    protected onBlur(): void;
    writeValue(value: unknown): void;
    registerOnChange(fn: (value: unknown) => void): void;
    registerOnTouched(fn: () => void): void;
    setDisabledState(isDisabled: boolean): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiRadioButtonComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiRadioButtonComponent, "tum-ui-radio-button", never, { "value": { "alias": "value"; "required": false; "isSignal": true; }; "name": { "alias": "name"; "required": false; "isSignal": true; }; "inputId": { "alias": "inputId"; "required": false; "isSignal": true; }; "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; }, { "selected": "selected"; }, never, never, true, never>;
}

/** Text field for filtering a view: a leading magnifier and a clear control that appears once there is a term. */
declare class TumUiSearchFieldComponent {
    /** Two-way bindable term. Emits on every keystroke; debounce in the consumer if the term drives a request. */
    readonly value: _angular_core.ModelSignal<string>;
    /** Translation key, resolved through the configured translator. */
    readonly placeholder: _angular_core.InputSignal<string>;
    /** Translation key for the accessible name. Falls back to the placeholder. */
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly size: _angular_core.InputSignal<TumUiInputSize | undefined>;
    protected readonly faMagnifyingGlass: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faXmark: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly accessibleNameKey: _angular_core.Signal<string>;
    private readonly inputElement;
    protected onInput(term: string): void;
    /** Clears the term and returns focus to the field, so the reader can keep typing without reaching for the mouse. */
    protected clear(): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiSearchFieldComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiSearchFieldComponent, "tum-ui-search-field", never, { "value": { "alias": "value"; "required": false; "isSignal": true; }; "placeholder": { "alias": "placeholder"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "size": { "alias": "size"; "required": false; "isSignal": true; }; }, { "value": "valueChange"; }, never, never, true, never>;
}

type TumUiSelectSize = 'small' | 'large';
/** Single-value ControlValueAccessor backed by a listbox overlay. */
declare class TumUiSelectComponent implements ControlValueAccessor {
    private readonly overlayService;
    private readonly viewContainerRef;
    private readonly destroyRef;
    private readonly document;
    private readonly injector;
    private readonly formField;
    readonly options: _angular_core.InputSignal<readonly unknown[]>;
    /** Property name used as the visible label for object options. */
    readonly optionLabel: _angular_core.InputSignal<string | undefined>;
    /** Property name written to the form value; omit it to write the option itself. */
    readonly optionValue: _angular_core.InputSignal<string | undefined>;
    readonly placeholder: _angular_core.InputSignal<string | undefined>;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly showClear: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Adds a search field above the option list, for option sets too long to scan. */
    readonly filter: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /**
     * Comma-separated property names searched by the filter, for object options whose match should not be
     * limited to the visible label — `"name,login"`, say. Defaults to the label alone.
     */
    readonly filterBy: _angular_core.InputSignal<string | undefined>;
    readonly filterPlaceholder: _angular_core.InputSignal<string | undefined>;
    readonly size: _angular_core.InputSignal<TumUiSelectSize | undefined>;
    /**
     * `id` of the trigger, so an external `<label for>` associates. Defaults to the id of an enclosing
     * `tum-ui-form-field`, and to a unique per-instance id outside one.
     */
    readonly inputId: _angular_core.InputSignal<string | undefined>;
    readonly name: _angular_core.InputSignal<string | undefined>;
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    readonly clearAriaLabel: _angular_core.InputSignal<string | undefined>;
    readonly emptyMessage: _angular_core.InputSignal<string | undefined>;
    readonly filterAriaLabel: _angular_core.InputSignal<string | undefined>;
    readonly selectionChange: _angular_core.OutputEmitterRef<unknown>;
    protected readonly faChevronDown: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faCheck: _fortawesome_fontawesome_svg_core.IconDefinition;
    protected readonly faXmark: _fortawesome_fontawesome_svg_core.IconDefinition;
    private readonly fallbackInputId;
    protected readonly resolvedInputId: _angular_core.Signal<string>;
    protected readonly describedBy: _angular_core.Signal<string | null>;
    protected readonly isInvalid: _angular_core.Signal<boolean>;
    protected readonly listboxId: string;
    private readonly trigger;
    private readonly panel;
    private readonly filterInput;
    private overlayRef?;
    protected readonly isOpen: _angular_core.WritableSignal<boolean>;
    protected readonly activeIndex: _angular_core.WritableSignal<number>;
    protected readonly filterText: _angular_core.WritableSignal<string>;
    private readonly selectedValue;
    private readonly disabledByForm;
    private onChangeCallback;
    private onTouchedCallback;
    protected readonly isDisabled: _angular_core.Signal<boolean>;
    protected readonly selectedOption: _angular_core.Signal<unknown>;
    /**
     * The options the panel shows. Everything index-based - the key manager, `aria-activedescendant`, the
     * option ids and every keyboard action - runs over this list rather than `options()`, so an index can
     * never point at an option the user cannot see.
     */
    /** Whether a query is currently narrowing the list, rather than merely present. */
    protected readonly isFiltering: _angular_core.Signal<boolean>;
    protected readonly visibleOptions: _angular_core.Signal<readonly unknown[]>;
    protected readonly hasSelection: _angular_core.Signal<boolean>;
    protected readonly displayLabel: _angular_core.Signal<string>;
    protected readonly showClearButton: _angular_core.Signal<boolean>;
    protected readonly triggerClasses: _angular_core.Signal<string>;
    protected readonly activeOptionId: _angular_core.Signal<string | undefined>;
    private readonly keyManagerOptions;
    private readonly keyManager;
    private typeaheadSequence;
    private pendingFilterFocus;
    private typeaheadReset?;
    constructor();
    writeValue(value: unknown): void;
    registerOnChange(fn: (value: unknown) => void): void;
    registerOnTouched(fn: () => void): void;
    setDisabledState(isDisabled: boolean): void;
    protected label(option: unknown): string;
    private toText;
    /** The strings the filter searches for one option: the named `filterBy` fields, or the visible label. */
    private filterFields;
    private resolveValue;
    private valuesMatch;
    protected isSelected(option: unknown): boolean;
    protected optionId(index: number): string;
    protected toggle(): void;
    private open;
    private close;
    protected selectOption(option: unknown): void;
    protected clear(event: MouseEvent): void;
    protected setActive(index: number): void;
    protected onTriggerKeydown(event: KeyboardEvent): void;
    protected onFilterInput(event: Event): void;
    /**
     * Keys typed in the search field. Everything that moves or commits the selection is forwarded to the
     * same handling the trigger uses; the rest is left to the input.
     */
    protected onFilterKeydown(event: KeyboardEvent): void;
    private handleTypeahead;
    private resetTypeahead;
    private scrollOptionIntoView;
    private buildTriggerClasses;
    protected optionClasses(option: unknown, index: number): string;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiSelectComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiSelectComponent, "tum-ui-select", never, { "options": { "alias": "options"; "required": false; "isSignal": true; }; "optionLabel": { "alias": "optionLabel"; "required": false; "isSignal": true; }; "optionValue": { "alias": "optionValue"; "required": false; "isSignal": true; }; "placeholder": { "alias": "placeholder"; "required": false; "isSignal": true; }; "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "showClear": { "alias": "showClear"; "required": false; "isSignal": true; }; "filter": { "alias": "filter"; "required": false; "isSignal": true; }; "filterBy": { "alias": "filterBy"; "required": false; "isSignal": true; }; "filterPlaceholder": { "alias": "filterPlaceholder"; "required": false; "isSignal": true; }; "size": { "alias": "size"; "required": false; "isSignal": true; }; "inputId": { "alias": "inputId"; "required": false; "isSignal": true; }; "name": { "alias": "name"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "clearAriaLabel": { "alias": "clearAriaLabel"; "required": false; "isSignal": true; }; "emptyMessage": { "alias": "emptyMessage"; "required": false; "isSignal": true; }; "filterAriaLabel": { "alias": "filterAriaLabel"; "required": false; "isSignal": true; }; }, { "selectionChange": "selectionChange"; }, never, never, true, never>;
}

type TumUiSelectButtonOption = unknown;
type TumUiSelectButtonSize = 'small' | 'large';
interface NormalizedOption {
    readonly raw: TumUiSelectButtonOption;
    readonly value: unknown;
    readonly label: string;
    readonly selected: boolean;
}
/** ControlValueAccessor for choosing one value from a small, persistent option set. */
declare class TumUiSelectButtonComponent implements ControlValueAccessor {
    /** Options that yield neither a primitive label nor an item template are omitted. */
    readonly options: _angular_core.InputSignal<readonly unknown[]>;
    /** Object property used as the visible option label. */
    readonly optionLabel: _angular_core.InputSignal<string | undefined>;
    /** Object property written to the form value; omit it to write the option. */
    readonly optionValue: _angular_core.InputSignal<string | undefined>;
    readonly size: _angular_core.InputSignal<TumUiSelectButtonSize | undefined>;
    /** Allows the selected option to be toggled back to `undefined`. */
    readonly allowEmpty: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Optional presentation template; the option remains its implicit context value. */
    readonly itemTemplate: _angular_core.InputSignal<TemplateRef<{
        $implicit: TumUiSelectButtonOption;
    }> | undefined>;
    /** Emits the selected value, or `undefined` when cleared. */
    readonly changed: _angular_core.OutputEmitterRef<unknown>;
    private readonly value;
    private readonly cvaDisabled;
    protected readonly effectiveDisabled: _angular_core.Signal<boolean>;
    protected onChange: (value: unknown) => void;
    protected onTouched: () => void;
    protected readonly normalizedOptions: _angular_core.Signal<NormalizedOption[]>;
    protected optionClasses(selected: boolean): string;
    protected select(option: NormalizedOption): void;
    writeValue(value: unknown): void;
    registerOnChange(fn: (value: unknown) => void): void;
    registerOnTouched(fn: () => void): void;
    setDisabledState(isDisabled: boolean): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiSelectButtonComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiSelectButtonComponent, "tum-ui-select-button", never, { "options": { "alias": "options"; "required": false; "isSignal": true; }; "optionLabel": { "alias": "optionLabel"; "required": false; "isSignal": true; }; "optionValue": { "alias": "optionValue"; "required": false; "isSignal": true; }; "size": { "alias": "size"; "required": false; "isSignal": true; }; "allowEmpty": { "alias": "allowEmpty"; "required": false; "isSignal": true; }; "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "itemTemplate": { "alias": "itemTemplate"; "required": false; "isSignal": true; }; }, { "changed": "changed"; }, never, never, true, never>;
}

type TumUiSortDirection$1 = 'asc' | 'desc' | 'none';
declare class TumUiTableSortableColumnComponent {
    readonly field: _angular_core.InputSignal<string>;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    private readonly table;
    protected readonly direction: _angular_core.Signal<TumUiSortDirection$1>;
    protected readonly ariaSort: _angular_core.Signal<"none" | "ascending" | "descending">;
    protected readonly sortIcon: _angular_core.Signal<_fortawesome_fontawesome_svg_core.IconDefinition>;
    protected readonly hostClasses: _angular_core.Signal<"" | "tum:cursor-pointer tum:select-none tum:hover:bg-hover-background">;
    protected onActivate(): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTableSortableColumnComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiTableSortableColumnComponent, "th[tumUiSortableColumn]", never, { "field": { "alias": "tumUiSortableColumn"; "required": true; "isSignal": true; }; "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

type TumUiTableSize = 'small' | 'normal' | 'large';
interface TumUiTableSortEvent {
    field: string;
    order: number;
}
declare class TumUiTableDirective {
    readonly size: _angular_core.InputSignal<TumUiTableSize>;
    readonly striped: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly scrollable: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly rowHover: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly sortField: _angular_core.InputSignal<string | undefined>;
    readonly sortOrder: _angular_core.InputSignalWithTransform<number, unknown>;
    readonly defaultSortOrder: _angular_core.InputSignalWithTransform<number, unknown>;
    readonly sortChange: _angular_core.OutputEmitterRef<TumUiTableSortEvent>;
    protected readonly hostClasses: _angular_core.Signal<string>;
    requestSort(field: string): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTableDirective, never>;
    static ɵdir: _angular_core.ɵɵDirectiveDeclaration<TumUiTableDirective, "table[tumUiTable]", never, { "size": { "alias": "size"; "required": false; "isSignal": true; }; "striped": { "alias": "striped"; "required": false; "isSignal": true; }; "scrollable": { "alias": "scrollable"; "required": false; "isSignal": true; }; "rowHover": { "alias": "rowHover"; "required": false; "isSignal": true; }; "sortField": { "alias": "sortField"; "required": false; "isSignal": true; }; "sortOrder": { "alias": "sortOrder"; "required": false; "isSignal": true; }; "defaultSortOrder": { "alias": "defaultSortOrder"; "required": false; "isSignal": true; }; }, { "sortChange": "sortChange"; }, never, never, true, never>;
}

/** Fixed-row-height virtual table for large in-memory collections. */
declare class TumUiTableVirtualScrollComponent<T> {
    readonly items: _angular_core.InputSignal<readonly T[]>;
    /** Row height in CSS pixels used by the CDK fixed-size virtual-scroll strategy. */
    readonly itemSize: _angular_core.InputSignal<number>;
    readonly rowTemplate: _angular_core.InputSignal<TemplateRef<{
        $implicit: T;
        index: number;
    }>>;
    readonly size: _angular_core.InputSignal<TumUiTableSize>;
    readonly striped: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly rowHover: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly scrollHeight: _angular_core.InputSignal<string>;
    readonly minWidth: _angular_core.InputSignal<string | undefined>;
    readonly trackBy: _angular_core.InputSignal<TrackByFunction<T> | undefined>;
    readonly ariaDescribedBy: _angular_core.InputSignal<string | undefined>;
    protected readonly isFlexHeight: _angular_core.Signal<boolean>;
    protected readonly viewportHeight: _angular_core.Signal<string | undefined>;
    protected readonly effectiveTrackBy: _angular_core.Signal<TrackByFunction<T>>;
    protected readonly headerClasses: _angular_core.Signal<string>;
    protected readonly rowClasses: _angular_core.Signal<string>;
    protected stripeClass(index: number): string;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTableVirtualScrollComponent<any>, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiTableVirtualScrollComponent<any>, "tum-ui-table-virtual-scroll", never, { "items": { "alias": "items"; "required": true; "isSignal": true; }; "itemSize": { "alias": "itemSize"; "required": true; "isSignal": true; }; "rowTemplate": { "alias": "rowTemplate"; "required": true; "isSignal": true; }; "size": { "alias": "size"; "required": false; "isSignal": true; }; "striped": { "alias": "striped"; "required": false; "isSignal": true; }; "rowHover": { "alias": "rowHover"; "required": false; "isSignal": true; }; "scrollHeight": { "alias": "scrollHeight"; "required": false; "isSignal": true; }; "minWidth": { "alias": "minWidth"; "required": false; "isSignal": true; }; "trackBy": { "alias": "trackBy"; "required": false; "isSignal": true; }; "ariaDescribedBy": { "alias": "ariaDescribedBy"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

interface ColumnDef<T> {
    /** Top-level property or nested path such as `owner.name` or `items[0].label`. */
    field?: (keyof T & string) | (string & {});
    header?: string;
    headerKey?: string;
    /** Translation key for a hint explaining a column whose heading alone is ambiguous, shown behind a help icon. */
    headerTooltip?: string;
    /** Minimum width as any CSS length. Prefer `rem` so a column sized to hold text grows with the reader's font. */
    width?: string;
    sort?: boolean;
    hideBelow?: 'sm' | 'md' | 'lg' | 'xl' | '2xl';
    templateRef?: CellTemplateRef<T>;
}
interface CellRendererParams<T> {
    data: T;
    col: ColumnDef<T>;
    value: unknown;
    rowIndex: number;
}
type CellTemplateRef<T> = TemplateRef<{
    $implicit: CellRendererParams<T>;
}>;
type TumUiSortDirection = 'asc' | 'desc';
interface TumUiSortState {
    field: string;
    direction: TumUiSortDirection;
}
interface TumUiTableQueryEvent {
    pageIndex: number;
    pageSize: number;
    sort?: TumUiSortState;
    searchTerm?: string;
}

/** Server-driven table whose consumer owns rows and responds to query changes. */
declare class TumUiTableComponent<T> {
    /** Columns displayed in declaration order. Nested field paths use lodash path syntax. */
    readonly columns: _angular_core.InputSignal<ColumnDef<T>[]>;
    /** Rows for the current page. Sorting and filtering are not applied locally. */
    readonly rows: _angular_core.InputSignal<T[]>;
    readonly totalRecords: _angular_core.InputSignalWithTransform<number, unknown>;
    readonly loading: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /** Optional action template receiving the row as its implicit value. */
    readonly rowActions: _angular_core.InputSignal<TemplateRef<{
        $implicit: T;
    }> | undefined>;
    /** Identity function forwarded to the CDK table. */
    readonly trackBy: _angular_core.InputSignal<TrackByFunction<T> | undefined>;
    readonly striped: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly scrollable: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly scrollHeight: _angular_core.InputSignal<string | undefined>;
    readonly showSearch: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly searchPlaceholder: _angular_core.InputSignal<string>;
    readonly emptyMessage: _angular_core.InputSignal<string>;
    readonly pageSize: _angular_core.InputSignalWithTransform<number, unknown>;
    readonly pageSizeOptions: _angular_core.InputSignal<number[]>;
    readonly showRowsPerPage: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly showCurrentPageReport: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly initialSortField: _angular_core.InputSignal<string | undefined>;
    readonly initialSortDirection: _angular_core.InputSignal<TumUiSortDirection>;
    /** Requests a zero-based page with the active page size, sort, and search term. */
    readonly dataRequest: _angular_core.OutputEmitterRef<TumUiTableQueryEvent>;
    protected readonly ACTIONS_COLUMN = "__tum_ui_actions__";
    protected readonly faCircleQuestion: IconDefinition;
    protected readonly faMagnifyingGlass: IconDefinition;
    protected readonly faSort: IconDefinition;
    protected readonly faSortDown: IconDefinition;
    protected readonly faSortUp: IconDefinition;
    private readonly destroyRef;
    private readonly cdkTable;
    private readonly page;
    private readonly pageSizeState;
    private readonly sortState;
    private readonly searchTerm;
    private searchTimer?;
    protected readonly effectivePageSize: _angular_core.Signal<number>;
    protected readonly currentPage: _angular_core.Signal<number>;
    protected readonly effectiveTrackBy: _angular_core.Signal<TrackByFunction<T>>;
    protected readonly displayedColumns: _angular_core.Signal<string[]>;
    protected readonly tableClasses: _angular_core.Signal<"tum:w-full tum:border-collapse tum:text-sm" | "tum:w-full tum:border-collapse tum:text-sm tum:[&_tbody_tr:nth-child(odd)]:bg-table-striped-background">;
    constructor();
    /** Jump back to the first page and re-request. For consumers that own filtering themselves (`showSearch` off). */
    resetPage(): void;
    protected columnName(col: ColumnDef<T>, index: number): string;
    protected resolveValue(row: T, col: ColumnDef<T>): unknown;
    protected cellParams(row: T, col: ColumnDef<T>, rowIndex: number): CellRendererParams<T>;
    protected columnVisibilityClasses(col: ColumnDef<T>): string;
    protected ariaSortFor(col: ColumnDef<T>): 'ascending' | 'descending' | 'none' | undefined;
    protected sortDirection(col: ColumnDef<T>): 'none' | TumUiSortDirection;
    protected sortIcon(col: ColumnDef<T>): IconDefinition;
    protected onSortClick(col: ColumnDef<T>): void;
    protected onSearchInput(value: string): void;
    protected onPageChange(page: number): void;
    protected onPageSizeChange(size: number): void;
    private emitDataRequest;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTableComponent<any>, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiTableComponent<any>, "tum-ui-table", never, { "columns": { "alias": "columns"; "required": true; "isSignal": true; }; "rows": { "alias": "rows"; "required": true; "isSignal": true; }; "totalRecords": { "alias": "totalRecords"; "required": false; "isSignal": true; }; "loading": { "alias": "loading"; "required": false; "isSignal": true; }; "rowActions": { "alias": "rowActions"; "required": false; "isSignal": true; }; "trackBy": { "alias": "trackBy"; "required": false; "isSignal": true; }; "striped": { "alias": "striped"; "required": false; "isSignal": true; }; "scrollable": { "alias": "scrollable"; "required": false; "isSignal": true; }; "scrollHeight": { "alias": "scrollHeight"; "required": false; "isSignal": true; }; "showSearch": { "alias": "showSearch"; "required": false; "isSignal": true; }; "searchPlaceholder": { "alias": "searchPlaceholder"; "required": false; "isSignal": true; }; "emptyMessage": { "alias": "emptyMessage"; "required": false; "isSignal": true; }; "pageSize": { "alias": "pageSize"; "required": false; "isSignal": true; }; "pageSizeOptions": { "alias": "pageSizeOptions"; "required": false; "isSignal": true; }; "showRowsPerPage": { "alias": "showRowsPerPage"; "required": false; "isSignal": true; }; "showCurrentPageReport": { "alias": "showCurrentPageReport"; "required": false; "isSignal": true; }; "initialSortField": { "alias": "initialSortField"; "required": false; "isSignal": true; }; "initialSortDirection": { "alias": "initialSortDirection"; "required": false; "isSignal": true; }; }, { "dataRequest": "dataRequest"; }, never, never, true, never>;
}

/** Scrollable tab-list container with keyboard navigation and an animated selection indicator. */
declare class TumUiTabListComponent implements OnDestroy {
    private readonly tabsService;
    private readonly directionality;
    private readonly injector;
    private readonly elementRef;
    private readonly tabs;
    private readonly keyManager;
    private readonly keyManagerChange;
    private resizeObserver?;
    protected readonly indicatorPosition: _angular_core.WritableSignal<{
        offset: number;
        width: number;
        animate: boolean;
    }>;
    protected readonly indicatorTransform: _angular_core.Signal<string>;
    private indicatorReady;
    constructor();
    protected onKeydown(event: KeyboardEvent): void;
    ngOnDestroy(): void;
    /**
     * Whether every tab currently in the query has published its value, i.e. whether Angular has applied the `value`
     * binding of each of them. Only then does the list know which tab is which.
     */
    private allValuesPublished;
    private updateIndicator;
    private observeLayout;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTabListComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiTabListComponent, "tum-ui-tab-list", never, {}, {}, ["tabs"], ["*"], true, never>;
}

/**
 * Content panel shown when its value matches the containing tabs value.
 *
 * By default an inactive panel is destroyed, which is the right default: a tab a user is not looking at should not
 * keep a subscription open or hold a large view alive. Turn on `preserveContent` for a panel whose state the user
 * expects to survive a trip to another tab — scroll position, an expanded row, an in-progress filter — because
 * destroying it re-runs every child constructor and returns the user to the top of a list they had scrolled.
 */
declare class TumUiTabPanelComponent {
    private readonly tabsService;
    /** Value that associates this panel with a tab. */
    readonly value: _angular_core.InputSignal<string | number>;
    /** Keeps this panel's content in the DOM while another tab is selected, hidden and inert, instead of destroying it. */
    readonly preserveContent: _angular_core.InputSignalWithTransform<boolean, unknown>;
    protected readonly active: _angular_core.Signal<boolean>;
    protected readonly rendered: _angular_core.Signal<boolean>;
    protected readonly id: _angular_core.Signal<string>;
    protected readonly tabId: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTabPanelComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiTabPanelComponent, "tum-ui-tab-panel", never, { "value": { "alias": "value"; "required": true; "isSignal": true; }; "preserveContent": { "alias": "preserveContent"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

/** Layout container for the panels in a tabs composition. */
declare class TumUiTabPanelsComponent {
    /** Disable when the containing surface already provides content padding. */
    readonly padded: _angular_core.InputSignalWithTransform<boolean, unknown>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTabPanelsComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiTabPanelsComponent, "tum-ui-tab-panels", never, { "padded": { "alias": "padded"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

/** Selectable tab associated with the panel that has the same value. */
declare class TumUiTabComponent {
    private readonly tabsService;
    readonly elementRef: ElementRef<HTMLElement>;
    /** Value that associates this tab with a tab panel. */
    readonly value: _angular_core.InputSignal<string | number>;
    readonly disabledInput: _angular_core.InputSignalWithTransform<boolean, unknown>;
    get disabled(): boolean;
    constructor();
    protected readonly active: _angular_core.Signal<boolean>;
    protected readonly id: _angular_core.Signal<string>;
    protected readonly panelId: _angular_core.Signal<string>;
    protected readonly hostClasses: _angular_core.Signal<string>;
    protected onClick(): void;
    focus(_origin?: FocusOrigin): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTabComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiTabComponent, "tum-ui-tab", never, { "value": { "alias": "value"; "required": true; "isSignal": true; }; "disabledInput": { "alias": "disabled"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

/** Coordinates an accessible tab list with its associated tab panels. */
declare class TumUiTabsComponent {
    private readonly tabsService;
    /** Value shared by the active tab and tab panel. */
    readonly value: _angular_core.ModelSignal<string | number | undefined>;
    constructor();
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTabsComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiTabsComponent, "tum-ui-tabs", never, { "value": { "alias": "value"; "required": false; "isSignal": true; }; }, { "value": "valueChange"; }, never, ["*"], true, never>;
}

type TumUiTabValue = number | string | undefined;

type TumUiTagSeverity = 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';
declare class TumUiTagComponent {
    readonly severity: _angular_core.InputSignal<TumUiTagSeverity>;
    readonly value: _angular_core.InputSignal<string | undefined>;
    readonly rounded: _angular_core.InputSignalWithTransform<boolean, unknown>;
    protected readonly tagClasses: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTagComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiTagComponent, "tum-ui-tag", never, { "severity": { "alias": "severity"; "required": false; "isSignal": true; }; "value": { "alias": "value"; "required": false; "isSignal": true; }; "rounded": { "alias": "rounded"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

declare class TumUiToggleSwitchComponent implements ControlValueAccessor {
    private readonly hostAriaLabel;
    private readonly hostAriaLabelledBy;
    readonly disabled: _angular_core.InputSignalWithTransform<boolean, unknown>;
    readonly inputId: _angular_core.InputSignal<string | undefined>;
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    readonly ariaLabelledBy: _angular_core.InputSignal<string | undefined>;
    readonly changed: _angular_core.OutputEmitterRef<boolean>;
    protected readonly checked: _angular_core.WritableSignal<boolean>;
    private readonly cvaDisabled;
    protected readonly effectiveDisabled: _angular_core.Signal<boolean>;
    protected readonly effectiveAriaLabel: _angular_core.Signal<string | null>;
    protected readonly effectiveAriaLabelledBy: _angular_core.Signal<string | null>;
    protected onChange: (value: boolean) => void;
    protected onTouched: () => void;
    protected readonly hostClasses: _angular_core.Signal<string>;
    protected onInputChange(event: Event): void;
    protected onInputBlur(): void;
    writeValue(value: boolean): void;
    registerOnChange(fn: (value: boolean) => void): void;
    registerOnTouched(fn: () => void): void;
    setDisabledState(isDisabled: boolean): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiToggleSwitchComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiToggleSwitchComponent, "tum-ui-toggle-switch", never, { "disabled": { "alias": "disabled"; "required": false; "isSignal": true; }; "inputId": { "alias": "inputId"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; "ariaLabelledBy": { "alias": "ariaLabelledBy"; "required": false; "isSignal": true; }; }, { "changed": "changed"; }, never, never, true, never>;
}

/** Tooltip shown on hover or focus and associated with its host through `aria-describedby`. */
declare class TumUiTooltipDirective implements OnDestroy {
    private readonly overlayService;
    private readonly elementRef;
    readonly content: _angular_core.InputSignal<string>;
    readonly placement: _angular_core.InputSignal<TumUiOverlayPlacement>;
    readonly showDelayMs: _angular_core.InputSignalWithTransform<number, unknown>;
    readonly hideDelayMs: _angular_core.InputSignalWithTransform<number, unknown>;
    private overlayRef?;
    private contentRef?;
    private positionSub?;
    private showTimer?;
    private hideTimer?;
    private readonly tooltipId;
    private interactionSub?;
    private triggerHovered;
    private tooltipHovered;
    private focused;
    constructor();
    protected onHoverStart(): void;
    protected onHoverEnd(): void;
    protected onFocusStart(): void;
    protected onFocusEnd(): void;
    private scheduleHideIfInactive;
    private scheduleShow;
    private scheduleHide;
    protected hideNow(): void;
    private show;
    private addDescribedBy;
    private removeDescribedBy;
    ngOnDestroy(): void;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiTooltipDirective, never>;
    static ɵdir: _angular_core.ɵɵDirectiveDeclaration<TumUiTooltipDirective, "[tumUiTooltip]", never, { "content": { "alias": "tumUiTooltip"; "required": true; "isSignal": true; }; "placement": { "alias": "tumUiTooltipPlacement"; "required": false; "isSignal": true; }; "showDelayMs": { "alias": "showDelayMs"; "required": false; "isSignal": true; }; "hideDelayMs": { "alias": "hideDelayMs"; "required": false; "isSignal": true; }; }, {}, never, never, true, never>;
}

/** Presentation of the placeholder's leading graphic. */
type TumUiEmptyMediaVariant = 'default' | 'icon';
/**
 * Groups the media, title and description of a {@link TumUiEmptyComponent} so the action below them is separated
 * from the explanation above them by a single gap rather than by four equal ones.
 */
declare class TumUiEmptyHeaderComponent {
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiEmptyHeaderComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiEmptyHeaderComponent, "tum-ui-empty-header", never, {}, {}, never, ["*"], true, never>;
}
/**
 * Leading graphic of an empty state.
 *
 * It is `aria-hidden`: the graphic restates what the title already says, and an unlabelled decorative glyph
 * announced before the explanation is noise.
 */
declare class TumUiEmptyMediaComponent {
    /** `icon` frames a single glyph in a tinted square; `default` leaves an illustration to bring its own frame. */
    readonly variant: _angular_core.InputSignal<TumUiEmptyMediaVariant>;
    protected readonly hostClasses: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiEmptyMediaComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiEmptyMediaComponent, "tum-ui-empty-media", never, { "variant": { "alias": "variant"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}
/**
 * The sentence that names what is missing.
 *
 * It renders as emphasised body text and **not** as a heading: an empty state usually replaces the content of a
 * section that already has one, and a second heading at an arbitrary level would corrupt the page outline. Wrap it
 * in your own `<h*>` where the empty state genuinely opens a new section.
 */
declare class TumUiEmptyTitleComponent {
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiEmptyTitleComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiEmptyTitleComponent, "tum-ui-empty-title", never, {}, {}, never, ["*"], true, never>;
}
/** Supporting sentence: what would be here, or who can put something here. */
declare class TumUiEmptyDescriptionComponent {
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiEmptyDescriptionComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiEmptyDescriptionComponent, "tum-ui-empty-description", never, {}, {}, never, ["*"], true, never>;
}
/** Everything a reader can act on: the control that resolves the emptiness, or a link to whoever can. */
declare class TumUiEmptyContentComponent {
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiEmptyContentComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiEmptyContentComponent, "tum-ui-empty-content", never, {}, {}, never, ["*"], true, never>;
}

type TumUiEmptySize = 'small' | 'medium' | 'large';
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
declare class TumUiEmptyComponent {
    /** Vertical room the placeholder claims. Use `small` inside a card or a panel, `large` for a whole page. */
    readonly size: _angular_core.InputSignal<TumUiEmptySize>;
    protected readonly effectiveSize: _angular_core.Signal<TumUiEmptySize>;
    protected readonly hostClasses: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiEmptyComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiEmptyComponent, "tum-ui-empty", never, { "size": { "alias": "size"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

/** Block rhythm of the projected document. `compact` is for prose inside a card body or a panel. */
type TumUiProseDensity = 'comfortable' | 'compact';
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
declare class TumUiProseComponent {
    /** Block rhythm. `compact` tightens the spacing between blocks for prose inside a panel or a card body. */
    readonly density: _angular_core.InputSignal<TumUiProseDensity>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiProseComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiProseComponent, "tum-ui-prose, [tumUiProse]", never, { "density": { "alias": "density"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

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
declare class TumUiSkeletonComponent {
    /** Any CSS length. Omit it and the placeholder fills its container, which is usually what you want. */
    readonly width: _angular_core.InputSignal<string | undefined>;
    /** Any CSS length. Set it to reserve the exact box the arriving content will occupy. */
    readonly height: _angular_core.InputSignal<string | undefined>;
    /**
     * Number of stacked text lines. The last line is drawn short, because that is what a paragraph of prose looks
     * like and the difference is what stops a stack of bars reading as a table.
     */
    readonly lines: _angular_core.InputSignalWithTransform<number, unknown>;
    protected readonly lineCount: _angular_core.Signal<number>;
    protected readonly lineIndices: _angular_core.Signal<number[]>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiSkeletonComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiSkeletonComponent, "tum-ui-skeleton", never, { "width": { "alias": "width"; "required": false; "isSignal": true; }; "height": { "alias": "height"; "required": false; "isSignal": true; }; "lines": { "alias": "lines"; "required": false; "isSignal": true; }; }, {}, never, never, true, never>;
}

type TumUiStatusDotState = 'queued' | 'running' | 'success' | 'warning' | 'danger' | 'neutral' | 'unknown';
/**
 * Compact state indicator: a dot with its state word.
 *
 * The word is the accessible name and is always rendered — hiding it with `showLabel` keeps it available to assistive
 * technology, so colour is never the only signal. Shape carries the states that share the muted colour: `neutral` is a
 * solid dot, `queued` a ring, `unknown` a dashed ring.
 */
declare class TumUiStatusDotComponent {
    /** Semantic state the dot reports. */
    readonly state: _angular_core.InputSignal<TumUiStatusDotState>;
    /** Translated human state word; it is the accessible name of the indicator. */
    readonly label: _angular_core.InputSignal<string>;
    /** Renders the label visually. When disabled the label stays in the accessibility tree. */
    readonly showLabel: _angular_core.InputSignalWithTransform<boolean, unknown>;
    /**
     * Announces state changes as a live region. Leave it off unless this dot is the one place a change is reported —
     * a list of dots must not turn into a list of live regions.
     */
    readonly live: _angular_core.InputSignalWithTransform<boolean, unknown>;
    protected readonly labelClasses: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiStatusDotComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiStatusDotComponent, "tum-ui-status-dot", never, { "state": { "alias": "state"; "required": true; "isSignal": true; }; "label": { "alias": "label"; "required": true; "isSignal": true; }; "showLabel": { "alias": "showLabel"; "required": false; "isSignal": true; }; "live": { "alias": "live"; "required": false; "isSignal": true; }; }, {}, never, never, true, never>;
}

type TumUiStepState = 'pending' | 'current' | 'complete' | 'failed' | 'skipped';
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
declare class TumUiStepComponent {
    private readonly stepper;
    private readonly translator;
    /** Progress state of this stage. */
    readonly state: _angular_core.InputSignal<TumUiStepState>;
    /** Visible stage name. Omit it when projecting a `[tumUiStepLabel]` slot instead; both render in the same line. */
    readonly label: _angular_core.InputSignal<string | undefined>;
    /** Overrides the marker icon, including the running indicator of a `current` step. */
    readonly icon: _angular_core.InputSignal<IconProp | undefined>;
    /**
     * Translated state word rendered next to the label for assistive technology only, for example "Running".
     * It defaults to the package wording for `state`; override it when the domain has a better word.
     */
    readonly stateLabel: _angular_core.InputSignal<string | undefined>;
    protected readonly orientation: _angular_core.Signal<_tumaet_ui_angular.TumUiStepperOrientation>;
    protected readonly isInactive: _angular_core.Signal<boolean>;
    protected readonly markerIcon: _angular_core.Signal<IconProp | undefined>;
    protected readonly isRunning: _angular_core.Signal<boolean>;
    protected readonly stateWord: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiStepComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiStepComponent, "tum-ui-step", never, { "state": { "alias": "state"; "required": false; "isSignal": true; }; "label": { "alias": "label"; "required": false; "isSignal": true; }; "icon": { "alias": "icon"; "required": false; "isSignal": true; }; "stateLabel": { "alias": "stateLabel"; "required": false; "isSignal": true; }; }, {}, never, ["[tumUiStepLabel]", "*"], true, never>;
}

type TumUiStepperOrientation = 'vertical' | 'horizontal';
/**
 * Progress ladder for a multi-stage operation.
 *
 * The stepper is a status display, not a navigation control: its steps are neither clickable nor focusable. Project
 * `tum-ui-step` children in the order they run.
 *
 * The list keeps an explicit `role="list"`, because a flex `<ol>` without markers loses its list semantics in some
 * browsers and every step depends on that list to carry its `role="listitem"`.
 */
declare class TumUiStepperComponent {
    private readonly stepperService;
    /** Layout direction of the ladder. */
    readonly orientation: _angular_core.InputSignal<TumUiStepperOrientation>;
    /** Accessible name of the step list. */
    readonly ariaLabel: _angular_core.InputSignal<string | undefined>;
    constructor();
    protected readonly listClasses: _angular_core.Signal<string>;
    static ɵfac: _angular_core.ɵɵFactoryDeclaration<TumUiStepperComponent, never>;
    static ɵcmp: _angular_core.ɵɵComponentDeclaration<TumUiStepperComponent, "tum-ui-stepper", never, { "orientation": { "alias": "orientation"; "required": false; "isSignal": true; }; "ariaLabel": { "alias": "ariaLabel"; "required": false; "isSignal": true; }; }, {}, never, ["*"], true, never>;
}

export { TUM_UI_FORM_FIELD, TUM_UI_TRANSLATOR, TumUiAutoCompleteComponent, TumUiBarChartComponent, TumUiButtonComponent, TumUiButtonDirective, TumUiButtonGroupComponent, TumUiCardComponent, TumUiCheckboxComponent, TumUiChipComponent, TumUiConfirmDialogComponent, TumUiConfirmationService, TumUiDatePickerComponent, TumUiDialogComponent, TumUiDoughnutChartComponent, TumUiEmptyComponent, TumUiEmptyContentComponent, TumUiEmptyDescriptionComponent, TumUiEmptyHeaderComponent, TumUiEmptyMediaComponent, TumUiEmptyTitleComponent, TumUiFormFieldComponent, TumUiIconFieldComponent, TumUiInputDirective, TumUiInputGroupAddonComponent, TumUiInputGroupComponent, TumUiInputNumberComponent, TumUiLineChartComponent, TumUiListComponent, TumUiListItemActionDirective, TumUiListItemDirective, TumUiMenuComponent, TumUiMenuItemDirective, TumUiMenuTriggerDirective, TumUiMessageComponent, TumUiPaginatorComponent, TumUiPanelComponent, TumUiPopoverComponent, TumUiPopoverTriggerDirective, TumUiProgressBarComponent, TumUiProgressSpinnerComponent, TumUiProseComponent, TumUiRadioButtonComponent, TumUiSearchFieldComponent, TumUiSelectButtonComponent, TumUiSelectComponent, TumUiSkeletonComponent, TumUiStatusDotComponent, TumUiStepComponent, TumUiStepperComponent, TumUiTabComponent, TumUiTabListComponent, TumUiTabPanelComponent, TumUiTabPanelsComponent, TumUiTableComponent, TumUiTableDirective, TumUiTableSortableColumnComponent, TumUiTableVirtualScrollComponent, TumUiTabsComponent, TumUiTagComponent, TumUiToggleSwitchComponent, TumUiTooltipDirective, provideTumUiTranslator };
export type { CellRendererParams, CellTemplateRef, ColumnDef, TumUiAutoCompleteOptionEvent, TumUiAutoCompleteSearchEvent, TumUiBarChartConfig, TumUiButtonSeverity, TumUiButtonSize, TumUiButtonVariant, TumUiChartAxisConfig, TumUiChartDatumContext, TumUiChartLegendConfig, TumUiChartLegendPosition, TumUiChartSelectEvent, TumUiChartSeries, TumUiChartTooltipConfig, TumUiCheckboxChangeEvent, TumUiChipSize, TumUiConfirmationRequest, TumUiDialogSize, TumUiDoughnutChartConfig, TumUiEmptyMediaVariant, TumUiEmptySize, TumUiFormFieldContext, TumUiIconFieldPosition, TumUiInputSize, TumUiLineChartConfig, TumUiMessageSeverity, TumUiOverlayPlacement, TumUiProgressBarSeverity, TumUiProgressBarSize, TumUiProseDensity, TumUiRadioButtonSelectEvent, TumUiSelectButtonOption, TumUiSelectButtonSize, TumUiSelectSize, TumUiSortDirection, TumUiSortState, TumUiStatusDotState, TumUiStepState, TumUiStepperOrientation, TumUiTabValue, TumUiTableQueryEvent, TumUiTableSize, TumUiTableSortEvent, TumUiTagSeverity, TumUiTranslationKey, TumUiTranslationParams, TumUiTranslator };
