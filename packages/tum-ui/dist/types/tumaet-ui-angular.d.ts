import { ControlValueAccessor } from "@angular/forms";
import * as i0 from "@angular/core";
import { ElementRef, EnvironmentProviders, InjectionToken, InputSignal, OnDestroy, OnInit, Signal, TemplateRef, TrackByFunction, Type } from "@angular/core";
import { IconDefinition, IconProp } from "@fortawesome/fontawesome-svg-core";
import { FormValueControl } from "@angular/forms/signals";
import dayjs from "dayjs/esm";
import { DialogRole } from "@angular/cdk/dialog";
import * as i1$1 from "@angular/aria/menu";
import { Menu, MenuTrigger } from "@angular/aria/menu";
import { ConnectedPosition, FlexibleConnectedPositionStrategy, OverlayRef } from "@angular/cdk/overlay";
import * as i1 from "@angular/aria/tabs";
import { Tab } from "@angular/aria/tabs";
interface TumUiAutoCompleteSearchEvent {
  originalEvent?: Event;
  query: string;
}
interface TumUiAutoCompleteOptionEvent {
  originalEvent?: Event;
  value: unknown;
}
/** Single- or multi-value ControlValueAccessor with consumer-supplied suggestions. */
export declare class TumUiAutoCompleteComponent implements ControlValueAccessor {
  private readonly overlayService;
  private readonly viewContainerRef;
  private readonly destroyRef;
  private readonly document;
  /** Suggestions supplied in response to a search request. */
  readonly suggestions: import("@angular/core").InputSignal<readonly unknown[]>;
  /** Property name used as the visible label for object values. */
  readonly optionLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly multiple: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly placeholder: import("@angular/core").InputSignal<string | undefined>;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Minimum query length before a search request emits. */
  readonly minLength: import("@angular/core").InputSignalWithTransform<number, unknown>;
  /** Delay between the latest input and a search request. */
  readonly debounceMs: import("@angular/core").InputSignalWithTransform<number, unknown>;
  /** Requests suggestions when the empty input receives focus. */
  readonly completeOnFocus: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly inputId: import("@angular/core").InputSignal<string>;
  readonly name: import("@angular/core").InputSignal<string | undefined>;
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly removeAriaLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Message shown when a completed search returns no suggestions. */
  readonly emptyMessage: import("@angular/core").InputSignal<string | undefined>;
  /** Requests suggestions for the current text query. */
  readonly searchRequested: import("@angular/core").OutputEmitterRef<TumUiAutoCompleteSearchEvent>;
  readonly optionSelected: import("@angular/core").OutputEmitterRef<TumUiAutoCompleteOptionEvent>;
  readonly optionRemoved: import("@angular/core").OutputEmitterRef<TumUiAutoCompleteOptionEvent>;
  protected readonly listboxId: string;
  private readonly container;
  private readonly textInput;
  private readonly panel;
  private overlayRef?;
  protected readonly selectedValues: import("@angular/core").WritableSignal<unknown[]>;
  private readonly singleValue;
  protected readonly query: import("@angular/core").WritableSignal<string>;
  protected readonly isFocused: import("@angular/core").WritableSignal<boolean>;
  private readonly hasSearched;
  protected readonly activeIndex: import("@angular/core").WritableSignal<number>;
  private readonly disabledByForm;
  private debounceTimer?;
  private onChangeCallback;
  private onTouchedCallback;
  protected readonly isDisabled: import("@angular/core").Signal<boolean>;
  private readonly labelKey;
  protected readonly panelVisible: import("@angular/core").Signal<boolean>;
  protected readonly activeOptionId: import("@angular/core").Signal<string | undefined>;
  protected readonly inputPlaceholder: import("@angular/core").Signal<string | undefined>;
  protected readonly inputText: import("@angular/core").Signal<string>;
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
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiAutoCompleteComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiAutoCompleteComponent, "tum-ui-autocomplete", never, {
    "suggestions": {
      "alias": "suggestions";
      "required": false;
      "isSignal": true;
    };
    "optionLabel": {
      "alias": "optionLabel";
      "required": false;
      "isSignal": true;
    };
    "multiple": {
      "alias": "multiple";
      "required": false;
      "isSignal": true;
    };
    "placeholder": {
      "alias": "placeholder";
      "required": false;
      "isSignal": true;
    };
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "minLength": {
      "alias": "minLength";
      "required": false;
      "isSignal": true;
    };
    "debounceMs": {
      "alias": "debounceMs";
      "required": false;
      "isSignal": true;
    };
    "completeOnFocus": {
      "alias": "completeOnFocus";
      "required": false;
      "isSignal": true;
    };
    "inputId": {
      "alias": "inputId";
      "required": false;
      "isSignal": true;
    };
    "name": {
      "alias": "name";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "removeAriaLabel": {
      "alias": "removeAriaLabel";
      "required": false;
      "isSignal": true;
    };
    "emptyMessage": {
      "alias": "emptyMessage";
      "required": false;
      "isSignal": true;
    };
  }, {
    "searchRequested": "searchRequested";
    "optionSelected": "optionSelected";
    "optionRemoved": "optionRemoved";
  }, never, never, true, never>;
}
export declare class TumUiButtonGroupComponent {
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiButtonGroupComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiButtonGroupComponent, "tum-ui-button-group", never, {}, {}, never, ["*"], true, never>;
}
type TumUiButtonSeverity = 'primary' | 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';
type TumUiButtonSize = 'small' | 'default' | 'large';
type TumUiButtonVariant = 'solid' | 'outlined' | 'text';
interface TumUiButtonVariantOptions {
  severity: TumUiButtonSeverity;
  size: TumUiButtonSize;
  variant: TumUiButtonVariant;
}
declare function tumUiButtonClasses(options: TumUiButtonVariantOptions): string;
export declare class TumUiButtonComponent {
  readonly severity: import("@angular/core").InputSignal<TumUiButtonSeverity>;
  readonly size: import("@angular/core").InputSignal<TumUiButtonSize>;
  readonly variant: import("@angular/core").InputSignal<TumUiButtonVariant>;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly rounded: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Replaces the icon with a spinner and disables the button. */
  readonly loading: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly icon: import("@angular/core").InputSignal<IconProp | undefined>;
  readonly type: import("@angular/core").InputSignal<"button" | "submit">;
  /** Accessible name required when projected content does not label the button. */
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly ariaExpanded: import("@angular/core").InputSignal<boolean | undefined>;
  readonly ariaPressed: import("@angular/core").InputSignal<boolean | undefined>;
  readonly ariaControls: import("@angular/core").InputSignal<string | undefined>;
  readonly ariaDescribedBy: import("@angular/core").InputSignal<string | undefined>;
  readonly clicked: import("@angular/core").OutputEmitterRef<MouseEvent>;
  protected readonly faSpinner: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly isDisabled: import("@angular/core").Signal<boolean>;
  protected readonly buttonClasses: import("@angular/core").Signal<string>;
  protected onClick(event: MouseEvent): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiButtonComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiButtonComponent, "tum-ui-button", never, {
    "severity": {
      "alias": "severity";
      "required": false;
      "isSignal": true;
    };
    "size": {
      "alias": "size";
      "required": false;
      "isSignal": true;
    };
    "variant": {
      "alias": "variant";
      "required": false;
      "isSignal": true;
    };
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "rounded": {
      "alias": "rounded";
      "required": false;
      "isSignal": true;
    };
    "loading": {
      "alias": "loading";
      "required": false;
      "isSignal": true;
    };
    "icon": {
      "alias": "icon";
      "required": false;
      "isSignal": true;
    };
    "type": {
      "alias": "type";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "ariaExpanded": {
      "alias": "ariaExpanded";
      "required": false;
      "isSignal": true;
    };
    "ariaPressed": {
      "alias": "ariaPressed";
      "required": false;
      "isSignal": true;
    };
    "ariaControls": {
      "alias": "ariaControls";
      "required": false;
      "isSignal": true;
    };
    "ariaDescribedBy": {
      "alias": "ariaDescribedBy";
      "required": false;
      "isSignal": true;
    };
  }, {
    "clicked": "clicked";
  }, never, ["*"], true, never>;
}
export declare class TumUiButtonDirective {
  readonly severity: import("@angular/core").InputSignal<TumUiButtonSeverity>;
  readonly size: import("@angular/core").InputSignal<TumUiButtonSize>;
  readonly variant: import("@angular/core").InputSignal<TumUiButtonVariant>;
  protected readonly hostClasses: import("@angular/core").Signal<string>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiButtonDirective, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiButtonDirective, "a[tumUiButton], button[tumUiButton]", never, {
    "severity": {
      "alias": "severity";
      "required": false;
      "isSignal": true;
    };
    "size": {
      "alias": "size";
      "required": false;
      "isSignal": true;
    };
    "variant": {
      "alias": "variant";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["*"], true, never>;
}
export declare class TumUiCardComponent {
  readonly header: import("@angular/core").InputSignal<string | undefined>;
  readonly subheader: import("@angular/core").InputSignal<string | undefined>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiCardComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiCardComponent, "tum-ui-card", never, {
    "header": {
      "alias": "header";
      "required": false;
      "isSignal": true;
    };
    "subheader": {
      "alias": "subheader";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["[tumUiCardHeader]", "*", "[tumUiCardFooter]"], true, never>;
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
/** Equal-width bands addressed by index so repeated labels remain distinct categories. */
interface BandScale {
  /** Start coordinate of the band at `index`. */
  position(index: number): number;
  readonly bandwidth: number;
  /** Center coordinate of the band at `index`. */
  center(index: number): number;
}
/** Maps a numeric domain onto a pixel range. */
interface LinearScale {
  (value: number): number;
  readonly domain: readonly [number, number];
  /**
   * @param count approximate number of ticks
   * @param minStep smallest allowed increment; pass 1 for an axis that only ever shows whole
   *        numbers, so that a small range such as 0–3 does not produce half steps
   */
  ticks(count?: number, minStep?: number): number[];
}
declare function bandScale(count: number, size: number, padding?: number): BandScale;
/**
 * The d3-array tick step: the "nicest" round increment of 1, 2 or 5 times a power of ten.
 *
 * `minStep` raises the result to the next multiple of itself. Counts of things are integers, and an
 * axis that labels them with an integer formatter would otherwise render "0, 1, 1, 2, 2, 3" once a
 * half step is collapsed by the formatter.
 */
declare function tickStep(start: number, stop: number, count: number, minStep?: number): number;
/** Extends a domain outwards to the next round tick, matching d3's `scale.nice()`. */
declare function niceDomain(min: number, max: number, count?: number, minStep?: number): [number, number];
declare function linearScale(domain: readonly [number, number], range: readonly [number, number]): LinearScale;
/** Estimates label width in pixels before SVG layout, using an average glyph width of 0.58 em. */
declare function approximateTextWidth(text: string, fontSize: number): number;
/** True when every value is a whole number, meaning the axis should not show fractional ticks. */
declare function allIntegers(values: readonly (number | undefined)[]): boolean;
/** Excludes missing and non-finite values from domain calculations. */
declare function finiteValues(values: readonly (number | undefined | null)[]): number[];
declare const TICK_FONT_SIZE = 11;
declare const AXIS_TITLE_FONT_SIZE = 12;
declare const TICK_GAP = 6;
declare const EDGE_PADDING = 8;
declare const CATEGORY_PADDING = 0.25;
/** Room reserved beyond the end of a bar for its data label. */
declare const DATA_LABEL_GAP = 4;
interface ChartMargin {
  top: number;
  right: number;
  bottom: number;
  left: number;
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
interface ValueTick {
  value: number;
  text: string;
}
interface CartesianFrameInput {
  size: {
    width: number;
    height: number;
  };
  labels: readonly string[];
  valueTicks: readonly ValueTick[];
  /** Categories run down the y axis and values along the x axis. */
  horizontal: boolean;
  valueAxis?: TumUiChartAxisConfig;
  categoryAxis?: TumUiChartAxisConfig;
  xAxisTitle?: string;
  yAxisTitle?: string;
  /** Additional room at the end of the value axis, e.g. for data labels. */
  valueEndPadding?: number;
}
interface CartesianFrame {
  margin: ChartMargin;
  plot: ChartPlot;
  /** Room a single category label may occupy before it has to be truncated. */
  categoryLabelBudget: number;
  /** Rotate category labels when their combined width exceeds the available space. */
  rotateCategoryLabels: boolean;
}
declare function cartesianFrame(input: CartesianFrameInput): CartesianFrame;
/** Shortens a label to the pixels available for it, so it cannot spill over the rest of the page. */
declare function truncateToWidth(text: string, budget: number): string;
declare function valueTickViews(plot: ChartPlot, scale: LinearScale, ticks: readonly ValueTick[], horizontal: boolean): ChartTick[];
declare function categoryTickViews(plot: ChartPlot, categories: BandScale, labels: readonly string[], horizontal: boolean, rotate: boolean, formatter?: (value: number | string) => string, labelBudget?: number): ChartTick[];
declare function gridLineViews(plot: ChartPlot, scale: LinearScale, ticks: readonly ValueTick[], horizontal: boolean): ChartGridLine[];
declare function axisTitleViews(plot: ChartPlot, margin: ChartMargin, xTitle?: string, yTitle?: string): {
  x?: ChartAxisTitle;
  y?: ChartAxisTitle;
};
declare function legendPositionOf(legend: TumUiChartLegendConfig | undefined): TumUiChartLegendPosition | undefined;
/** Where a tooltip should sit, once kept inside the chart's own box. */
interface TooltipPlacement {
  x: number;
  y: number;
  below: boolean;
}
declare function placeTooltip(hovered: {
  x: number;
  y: number;
  hostWidth: number;
  hostHeight: number;
}): TooltipPlacement;
/**
 * The accessible name of a single interactive datum. Where a chart draws more than one series, two
 * data points can share a category and a value, which would leave a keyboard or screen reader user
 * unable to tell which one they are about to select, so the series label leads in that case.
 */
declare function datumAccessibleName(context: TumUiChartDatumContext, multiSeries: boolean): string;
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
export declare class TumUiBarChartComponent implements OnDestroy {
  private readonly hostElement;
  /**
   * The plot is measured on the SVG itself rather than on the host, because a legend is a sibling
   * flex item: measuring the host would size the plot as if the legend's band were still free and
   * paint the axis labels underneath it.
   */
  private readonly canvas;
  readonly labels: import("@angular/core").InputSignal<readonly string[]>;
  readonly series: import("@angular/core").InputSignal<readonly TumUiChartSeries[]>;
  readonly config: import("@angular/core").InputSignal<TumUiBarChartConfig>;
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Names the chart from a visible heading instead of a literal label. */
  readonly ariaLabelledBy: import("@angular/core").InputSignal<string | undefined>;
  /** Marks bars as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
  readonly interactive: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly dataSelect: import("@angular/core").OutputEmitterRef<TumUiChartSelectEvent>;
  /** Names an interactive datum for assistive technology; see {@link datumAccessibleName}. */
  protected accessibleName(context: TumUiChartDatumContext): string;
  private readonly size;
  protected readonly hovered: import("@angular/core").WritableSignal<{
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
  protected readonly plot: import("@angular/core").Signal<ChartPlot>;
  /** Unique per instance, so charts sharing a page do not share a clip path. */
  protected readonly clipId: string;
  private readonly valueScale;
  private readonly categoryScale;
  protected readonly bars: import("@angular/core").Signal<BarView[]>;
  protected readonly gridLines: import("@angular/core").Signal<ChartGridLine[]>;
  protected readonly ticks: import("@angular/core").Signal<ChartTick[]>;
  protected readonly axisTitles: import("@angular/core").Signal<ChartAxisTitle[]>;
  protected readonly legendPosition: import("@angular/core").Signal<import("@tumaet/ui-angular").TumUiChartLegendPosition | undefined>;
  protected readonly legendItems: import("@angular/core").Signal<ChartLegendItem[]>;
  protected readonly tooltip: import("@angular/core").Signal<{
    x: number;
    y: number;
    below: boolean;
    title: string;
    lines: string[];
  } | undefined>;
  protected readonly accessibleRows: import("@angular/core").Signal<{
    label: string;
    values: {
      seriesLabel: string | undefined;
      value: number | undefined;
    }[];
  }[]>;
  protected onBarEnter(bar: BarView, event: MouseEvent): void;
  protected onBarLeave(): void;
  protected onBarSelect(bar: BarView): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiBarChartComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiBarChartComponent, "tum-ui-bar-chart", never, {
    "labels": {
      "alias": "labels";
      "required": true;
      "isSignal": true;
    };
    "series": {
      "alias": "series";
      "required": true;
      "isSignal": true;
    };
    "config": {
      "alias": "config";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "ariaLabelledBy": {
      "alias": "ariaLabelledBy";
      "required": false;
      "isSignal": true;
    };
    "interactive": {
      "alias": "interactive";
      "required": false;
      "isSignal": true;
    };
  }, {
    "dataSelect": "dataSelect";
  }, never, never, true, never>;
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
export declare class TumUiDoughnutChartComponent implements OnDestroy {
  private readonly hostElement;
  private readonly canvas;
  readonly labels: import("@angular/core").InputSignal<readonly string[]>;
  readonly series: import("@angular/core").InputSignal<readonly TumUiChartSeries[]>;
  readonly config: import("@angular/core").InputSignal<TumUiDoughnutChartConfig>;
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Names the chart from a visible heading instead of a literal label. */
  readonly ariaLabelledBy: import("@angular/core").InputSignal<string | undefined>;
  /** Marks slices as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
  readonly interactive: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly dataSelect: import("@angular/core").OutputEmitterRef<TumUiChartSelectEvent>;
  private readonly size;
  protected readonly hovered: import("@angular/core").WritableSignal<{
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
  protected readonly slices: import("@angular/core").Signal<SliceView[]>;
  protected readonly legendPosition: import("@angular/core").Signal<import("@tumaet/ui-angular").TumUiChartLegendPosition | undefined>;
  /** A doughnut's legend names the slices rather than the series, so it follows the categories. */
  protected readonly legendItems: import("@angular/core").Signal<ChartLegendItem[]>;
  protected readonly tooltip: import("@angular/core").Signal<{
    x: number;
    y: number;
    below: boolean;
    title: string;
    lines: string[];
  } | undefined>;
  protected readonly accessibleRows: import("@angular/core").Signal<{
    label: string;
    values: {
      seriesLabel: string | undefined;
      value: number | undefined;
    }[];
  }[]>;
  protected onSliceEnter(slice: SliceView, event: MouseEvent): void;
  protected onSliceLeave(): void;
  protected onSliceSelect(slice: SliceView): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiDoughnutChartComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiDoughnutChartComponent, "tum-ui-doughnut-chart", never, {
    "labels": {
      "alias": "labels";
      "required": true;
      "isSignal": true;
    };
    "series": {
      "alias": "series";
      "required": true;
      "isSignal": true;
    };
    "config": {
      "alias": "config";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "ariaLabelledBy": {
      "alias": "ariaLabelledBy";
      "required": false;
      "isSignal": true;
    };
    "interactive": {
      "alias": "interactive";
      "required": false;
      "isSignal": true;
    };
  }, {
    "dataSelect": "dataSelect";
  }, never, never, true, never>;
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
/**
 * A line chart rendered as inline SVG, with one line per series.
 *
 * Hovering reports every series at the hovered category at once, which is what makes several lines
 * comparable at a glance; a series marked as a reference line is drawn dashed and stays out of the
 * legend, the tooltip and select events.
 */
export declare class TumUiLineChartComponent implements OnDestroy {
  private readonly hostElement;
  private readonly canvas;
  readonly labels: import("@angular/core").InputSignal<readonly string[]>;
  readonly series: import("@angular/core").InputSignal<readonly TumUiChartSeries[]>;
  readonly config: import("@angular/core").InputSignal<TumUiLineChartConfig>;
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Names the chart from a visible heading instead of a literal label. */
  readonly ariaLabelledBy: import("@angular/core").InputSignal<string | undefined>;
  /** Marks points as clickable, which shows a pointer cursor. `dataSelect` is emitted regardless. */
  readonly interactive: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly dataSelect: import("@angular/core").OutputEmitterRef<TumUiChartSelectEvent>;
  protected readonly pointRadius = 3;
  /** Names an interactive datum for assistive technology; see {@link datumAccessibleName}. */
  protected accessibleName(context: TumUiChartDatumContext): string;
  private readonly size;
  protected readonly hovered: import("@angular/core").WritableSignal<{
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
  /** Keep integer-valued series on whole-number ticks. */
  private readonly minTickStep;
  private readonly valueDomain;
  private readonly valueTickLabels;
  private readonly frame;
  protected readonly plot: import("@angular/core").Signal<ChartPlot>;
  /** Unique per instance, so charts sharing a page do not share a clip path. */
  protected readonly clipId: string;
  private readonly valueScale;
  private readonly categoryScale;
  protected readonly lines: import("@angular/core").Signal<LineView[]>;
  protected readonly gridLines: import("@angular/core").Signal<ChartGridLine[]>;
  protected readonly ticks: import("@angular/core").Signal<ChartTick[]>;
  protected readonly axisTitles: import("@angular/core").Signal<ChartAxisTitle[]>;
  protected readonly legendPosition: import("@angular/core").Signal<import("@tumaet/ui-angular").TumUiChartLegendPosition | undefined>;
  protected readonly legendItems: import("@angular/core").Signal<ChartLegendItem[]>;
  /** The x coordinate of the guide drawn through the hovered category. */
  protected readonly guideX: import("@angular/core").Signal<number | undefined>;
  protected readonly tooltip: import("@angular/core").Signal<{
    x: number;
    y: number;
    below: boolean;
    title: string;
    lines: string[];
  } | undefined>;
  protected readonly accessibleRows: import("@angular/core").Signal<{
    label: string;
    values: {
      seriesLabel: string | undefined;
      value: number | undefined;
    }[];
  }[]>;
  /** Resolves a pointer position to the nearest category, so hovering anywhere reports a series. */
  protected onPlotMove(event: MouseEvent): void;
  protected onPlotLeave(): void;
  /** Keyboard activation of a focused point. */
  protected onPointSelect(context: TumUiChartDatumContext): void;
  protected onPlotClick(event: MouseEvent): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiLineChartComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiLineChartComponent, "tum-ui-line-chart", never, {
    "labels": {
      "alias": "labels";
      "required": true;
      "isSignal": true;
    };
    "series": {
      "alias": "series";
      "required": true;
      "isSignal": true;
    };
    "config": {
      "alias": "config";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "ariaLabelledBy": {
      "alias": "ariaLabelledBy";
      "required": false;
      "isSignal": true;
    };
    "interactive": {
      "alias": "interactive";
      "required": false;
      "isSignal": true;
    };
  }, {
    "dataSelect": "dataSelect";
  }, never, never, true, never>;
}
interface TumUiCheckboxChangeEvent {
  originalEvent: Event;
  checked: boolean;
}
export declare class TumUiCheckboxComponent implements ControlValueAccessor {
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly inputId: import("@angular/core").InputSignal<string | undefined>;
  readonly name: import("@angular/core").InputSignal<string | undefined>;
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly checked: import("@angular/core").ModelSignal<boolean>;
  /**
   * Renders the partial-selection dash instead of the tick, for a select-all control whose rows are only
   * partly selected. Purely visual: it never changes `checked`, the model, or what `changed` emits.
   */
  readonly indeterminate: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly changed: import("@angular/core").OutputEmitterRef<TumUiCheckboxChangeEvent>;
  protected readonly faCheck: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faMinus: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly showDash: import("@angular/core").Signal<boolean>;
  protected readonly showTick: import("@angular/core").Signal<boolean>;
  private readonly cvaDisabled;
  protected readonly isDisabled: import("@angular/core").Signal<boolean>;
  protected readonly boxClasses: import("@angular/core").Signal<"tum:bg-disabled-background tum:border-control-border" | "tum:bg-primary tum:border-primary" | "tum:bg-control-background tum:border-control-border">;
  protected readonly iconClasses: import("@angular/core").Signal<"tum:text-disabled" | "tum:text-primary-contrast">;
  private onModelChange;
  private onModelTouched;
  protected onInputChange(event: Event): void;
  protected onBlur(): void;
  writeValue(value: boolean): void;
  registerOnChange(fn: (value: boolean) => void): void;
  registerOnTouched(fn: () => void): void;
  setDisabledState(isDisabled: boolean): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiCheckboxComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiCheckboxComponent, "tum-ui-checkbox", never, {
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "inputId": {
      "alias": "inputId";
      "required": false;
      "isSignal": true;
    };
    "name": {
      "alias": "name";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "checked": {
      "alias": "checked";
      "required": false;
      "isSignal": true;
    };
    "indeterminate": {
      "alias": "indeterminate";
      "required": false;
      "isSignal": true;
    };
  }, {
    "checked": "checkedChange";
    "changed": "changed";
  }, never, never, true, never>;
}
type TumUiChipSize = 'small';
export declare class TumUiChipComponent {
  readonly label: import("@angular/core").InputSignal<string | undefined>;
  readonly removable: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly size: import("@angular/core").InputSignal<"small" | undefined>;
  readonly removeAriaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly removed: import("@angular/core").OutputEmitterRef<Event>;
  protected readonly faXmark: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly chipClasses: import("@angular/core").Signal<string>;
  protected remove(event: Event): void;
  protected onRemoveKeydown(event: KeyboardEvent): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiChipComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiChipComponent, "tum-ui-chip", never, {
    "label": {
      "alias": "label";
      "required": false;
      "isSignal": true;
    };
    "removable": {
      "alias": "removable";
      "required": false;
      "isSignal": true;
    };
    "size": {
      "alias": "size";
      "required": false;
      "isSignal": true;
    };
    "removeAriaLabel": {
      "alias": "removeAriaLabel";
      "required": false;
      "isSignal": true;
    };
  }, {
    "removed": "removed";
  }, never, ["*"], true, never>;
}
/** Renders requests from the nearest `TumUiConfirmationService` as modal decisions. */
export declare class TumUiConfirmDialogComponent {
  private readonly confirmationService;
  /** Static key used to select this dialog's confirmation requests. */
  readonly key: import("@angular/core").InputSignal<string | undefined>;
  protected readonly messageId: string;
  protected readonly request: import("@angular/core").Signal<import("@tumaet/ui-angular").TumUiConfirmationRequest | undefined>;
  protected readonly visible: import("@angular/core").Signal<boolean>;
  protected accept(): void;
  protected reject(): void;
  protected onDialogHide(): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiConfirmDialogComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiConfirmDialogComponent, "tum-ui-confirm-dialog", never, {
    "key": {
      "alias": "key";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, never, true, never>;
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
export declare class TumUiConfirmationService {
  private readonly requests;
  request(key: string | undefined): TumUiConfirmationRequest | undefined;
  confirm(request: TumUiConfirmationRequest): void;
  close(key: string | undefined): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiConfirmationService, never>;
  static ɵprov: i0.ɵɵInjectableDeclaration<any>;
}
/** Date-and-time field with typed input and an accessible calendar dialog; `timeOnly` reduces it to a time. */
export declare class TumUiDatePickerComponent implements FormValueControl<dayjs.Dayjs | undefined> {
  private readonly overlayService;
  private readonly viewContainerRef;
  private readonly destroyRef;
  private readonly document;
  /**
   * Last committed date. Invalid text remains visible without updating it.
   * Observe `inputValidityChange` when validity must react to uncommitted text.
   */
  readonly value: import("@angular/core").ModelSignal<dayjs.Dayjs | undefined>;
  /** Adds an external validation error without discarding the last committed value. */
  readonly invalid: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Hides the visible label while retaining the input's accessible name. */
  readonly hideLabelName: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Hides the built-in validation message without changing validity or `aria-invalid`. */
  readonly hideValidationMessage: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Shows the browser time-zone indicator beside the label. */
  readonly shouldDisplayTimeZoneWarning: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Edit `HH:mm` without a calendar, preserving the value's date or using today when empty. */
  readonly timeOnly: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** ID used to associate the input, label, validation message, and dialog. */
  readonly inputId: import("@angular/core").InputSignal<string>;
  /** Visible label text or package translation key. */
  readonly labelName: import("@angular/core").InputSignal<string | undefined>;
  /** Accessible name used when no visible label is rendered. */
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Emits text-input validity independently of the external `invalid` state. */
  readonly inputValidityChange: import("@angular/core").OutputEmitterRef<boolean>;
  /** Emits when the text input loses focus. */
  readonly touch: import("@angular/core").OutputEmitterRef<void>;
  protected readonly faCalendar: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faXmark: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faGlobe: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faClock: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faChevronUp: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faChevronDown: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected get currentTimeZone(): string;
  private readonly valueKey;
  private readonly isInputValid;
  protected readonly isOpen: import("@angular/core").WritableSignal<boolean>;
  protected readonly panelId: import("@angular/core").Signal<string>;
  protected readonly activeMonth: import("@angular/core").WritableSignal<dayjs.Dayjs>;
  protected readonly timeText: import("@angular/core").WritableSignal<string>;
  protected readonly inputText: import("@angular/core").WritableSignal<string>;
  private readonly panel;
  private readonly dateInput;
  private readonly triggerWrapper;
  private readonly hourField;
  private overlayRef?;
  private restoreFocusElement?;
  private pendingHourFocus;
  protected readonly showErrorBorder: import("@angular/core").Signal<boolean>;
  protected readonly placeholderKey: import("@angular/core").Signal<"tumUi.datePicker.timePlaceholder" | "tumUi.datePicker.placeholder">;
  protected readonly dialogLabelKey: import("@angular/core").Signal<"tumUi.datePicker.timeDialog" | "tumUi.datePicker.dialog">;
  protected readonly invalidMessageKey: import("@angular/core").Signal<"tumUi.datePicker.invalidTime" | "tumUi.datePicker.invalid">;
  protected readonly openLabelKey: import("@angular/core").Signal<"tumUi.datePicker.openTime" | "tumUi.datePicker.open">;
  protected readonly showClear: import("@angular/core").Signal<boolean>;
  protected readonly displayHour: import("@angular/core").Signal<string>;
  protected readonly displayMinute: import("@angular/core").Signal<string>;
  constructor();
  /** Whether the entered text parses successfully and the external `invalid` state is clear. */
  readonly isValid: import("@angular/core").Signal<boolean>;
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
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiDatePickerComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiDatePickerComponent, "tum-ui-date-picker", never, {
    "value": {
      "alias": "value";
      "required": false;
      "isSignal": true;
    };
    "invalid": {
      "alias": "invalid";
      "required": false;
      "isSignal": true;
    };
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "hideLabelName": {
      "alias": "hideLabelName";
      "required": false;
      "isSignal": true;
    };
    "hideValidationMessage": {
      "alias": "hideValidationMessage";
      "required": false;
      "isSignal": true;
    };
    "shouldDisplayTimeZoneWarning": {
      "alias": "shouldDisplayTimeZoneWarning";
      "required": false;
      "isSignal": true;
    };
    "timeOnly": {
      "alias": "timeOnly";
      "required": false;
      "isSignal": true;
    };
    "inputId": {
      "alias": "inputId";
      "required": false;
      "isSignal": true;
    };
    "labelName": {
      "alias": "labelName";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
  }, {
    "value": "valueChange";
    "inputValidityChange": "inputValidityChange";
    "touch": "touch";
  }, never, never, true, never>;
}
type TumUiDialogSize = 'small' | 'medium' | 'large' | 'full';
/** Controlled modal dialog built on Angular CDK Dialog. */
export declare class TumUiDialogComponent implements OnDestroy {
  private readonly dialog;
  private readonly overlay;
  private readonly viewContainerRef;
  /** Controlled open state; dismissal writes `false`. */
  readonly visible: import("@angular/core").ModelSignal<boolean>;
  /** Visible title and default accessible name. */
  readonly header: import("@angular/core").InputSignal<string | undefined>;
  /** Hides the header; an `ariaLabel` is then required. */
  readonly showHeader: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Shows the close button without changing Escape or backdrop behavior. */
  readonly closable: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Allows Escape to close the dialog. */
  readonly closeOnEscape: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Allows a backdrop click to close the dialog. */
  readonly dismissableMask: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Responsive dialog dimensions. Omit for content-sized dialogs. */
  readonly size: import("@angular/core").InputSignal<TumUiDialogSize | undefined>;
  /** Accessible name used when no visible header or header template is present. */
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly closeButtonAriaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly role: import("@angular/core").InputSignal<DialogRole>;
  readonly ariaDescribedBy: import("@angular/core").InputSignal<string | undefined>;
  readonly shown: import("@angular/core").OutputEmitterRef<void>;
  readonly hidden: import("@angular/core").OutputEmitterRef<void>;
  private readonly panel;
  protected readonly headerTemplate: import("@angular/core").Signal<TemplateRef<any> | undefined>;
  protected readonly footerTemplate: import("@angular/core").Signal<TemplateRef<any> | undefined>;
  protected readonly titleId: string;
  protected readonly faXmark: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly labelledBy: import("@angular/core").Signal<string | undefined>;
  protected readonly sizeClasses: import("@angular/core").Signal<string>;
  private dialogRef?;
  private readonly visibilitySync;
  close(): void;
  private open;
  ngOnDestroy(): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiDialogComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiDialogComponent, "tum-ui-dialog", never, {
    "visible": {
      "alias": "visible";
      "required": false;
      "isSignal": true;
    };
    "header": {
      "alias": "header";
      "required": false;
      "isSignal": true;
    };
    "showHeader": {
      "alias": "showHeader";
      "required": false;
      "isSignal": true;
    };
    "closable": {
      "alias": "closable";
      "required": false;
      "isSignal": true;
    };
    "closeOnEscape": {
      "alias": "closeOnEscape";
      "required": false;
      "isSignal": true;
    };
    "dismissableMask": {
      "alias": "dismissableMask";
      "required": false;
      "isSignal": true;
    };
    "size": {
      "alias": "size";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "closeButtonAriaLabel": {
      "alias": "closeButtonAriaLabel";
      "required": false;
      "isSignal": true;
    };
    "role": {
      "alias": "role";
      "required": false;
      "isSignal": true;
    };
    "ariaDescribedBy": {
      "alias": "ariaDescribedBy";
      "required": false;
      "isSignal": true;
    };
  }, {
    "visible": "visibleChange";
    "shown": "shown";
    "hidden": "hidden";
  }, ["headerTemplate", "footerTemplate"], ["*"], true, never>;
}
type TumUiEmptyStateVariant = 'outlined' | 'solid' | 'plain';
/**
 * Presents a consistent empty state for views without content, including empty search results, while leaving
 * actions and documentation links to the host application. Both content slots are optional. Project each action
 * with `[tumUiEmptyStateActions]` and a documentation link with `[tumUiEmptyStateDocumentation]`.
 */
export declare class TumUiEmptyStateComponent {
  /** Decorative icon shown above the empty state content. */
  readonly icon: import("@angular/core").InputSignal<IconProp>;
  /** Visual treatment of the decorative icon container. */
  readonly variant: import("@angular/core").InputSignal<TumUiEmptyStateVariant>;
  /** Heading that describes what content is missing. */
  readonly title: import("@angular/core").InputSignal<string>;
  /** Optional explanation shown below the heading. */
  readonly description: import("@angular/core").InputSignal<string | undefined>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiEmptyStateComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiEmptyStateComponent, "tum-ui-empty-state", never, {
    "icon": {
      "alias": "icon";
      "required": true;
      "isSignal": true;
    };
    "variant": {
      "alias": "variant";
      "required": false;
      "isSignal": true;
    };
    "title": {
      "alias": "title";
      "required": true;
      "isSignal": true;
    };
    "description": {
      "alias": "description";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["[tumUiEmptyStateActions]", "[tumUiEmptyStateDocumentation]"], true, never>;
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
export declare const TUM_UI_FORM_FIELD: InjectionToken<TumUiFormFieldContext | null>;
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
export declare class TumUiFormFieldComponent implements TumUiFormFieldContext {
  /** Label text. Omit it when projecting a `[tumUiFormFieldLabel]` slot instead. */
  readonly label: import("@angular/core").InputSignal<string>;
  /**
   * Id of the control this field labels. Defaults to a generated id, which a TUM UI control adopts unless
   * it carries an id of its own. Set it when the id has to be stable, such as for an end-to-end selector.
   */
  readonly controlId: import("@angular/core").InputSignal<string | undefined>;
  /**
   * Renders the required marker. The marker is decorative: the control still needs its own `required`, which
   * is what assistive technology reports.
   */
  readonly required: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Helper text shown below the control while the field is valid. */
  readonly hint: import("@angular/core").InputSignal<string | undefined>;
  /** Shows the error region and marks the wrapped control invalid. */
  readonly invalid: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Error text. Project a `[tumUiFormFieldError]` slot instead when several messages can apply. */
  readonly error: import("@angular/core").InputSignal<string | undefined>;
  /** Id a wrapped control reported because it brought one of its own. */
  private readonly reportedControlId;
  private readonly fieldId;
  private readonly generatedControlId;
  protected readonly hintId: string;
  protected readonly errorId: string;
  readonly explicitControlId: import("@angular/core").InputSignal<string | undefined>;
  readonly labelTargetId: import("@angular/core").Signal<string>;
  adoptControlId(id: string): void;
  protected readonly showHint: import("@angular/core").Signal<boolean>;
  readonly describedBy: import("@angular/core").Signal<string | undefined>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiFormFieldComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiFormFieldComponent, "tum-ui-form-field", never, {
    "label": {
      "alias": "label";
      "required": false;
      "isSignal": true;
    };
    "controlId": {
      "alias": "controlId";
      "required": false;
      "isSignal": true;
    };
    "required": {
      "alias": "required";
      "required": false;
      "isSignal": true;
    };
    "hint": {
      "alias": "hint";
      "required": false;
      "isSignal": true;
    };
    "invalid": {
      "alias": "invalid";
      "required": false;
      "isSignal": true;
    };
    "error": {
      "alias": "error";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["[tumUiFormFieldLabel]", "*", "[tumUiFormFieldError]"], true, never>;
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
export declare const TUM_UI_TRANSLATOR: InjectionToken<TumUiTranslator>;
export declare function provideTumUiTranslator(translator: Type<TumUiTranslator>): EnvironmentProviders;
type TumUiIconFieldPosition = 'left' | 'right';
export declare class TumUiIconFieldComponent {
  readonly icon: import("@angular/core").InputSignal<IconProp | undefined>;
  readonly iconPosition: import("@angular/core").InputSignal<TumUiIconFieldPosition>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiIconFieldComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiIconFieldComponent, "tum-ui-icon-field", never, {
    "icon": {
      "alias": "icon";
      "required": false;
      "isSignal": true;
    };
    "iconPosition": {
      "alias": "iconPosition";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["*"], true, never>;
}
export declare class TumUiInputGroupAddonComponent {
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiInputGroupAddonComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiInputGroupAddonComponent, "tum-ui-input-group-addon", never, {}, {}, never, ["*"], true, never>;
}
export declare class TumUiInputGroupComponent {
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiInputGroupComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiInputGroupComponent, "tum-ui-input-group", never, {}, {}, never, ["*"], true, never>;
}
/** Numeric input with locale grouping, optional affixes, optional decimals and step controls. */
export declare class TumUiInputNumberComponent implements ControlValueAccessor {
  /** Lower bound applied on blur and stepping. */
  readonly min: import("@angular/core").InputSignal<number | undefined>;
  /** Upper bound applied on blur and stepping. */
  readonly max: import("@angular/core").InputSignal<number | undefined>;
  /** Increment / decrement applied by the stepper buttons and Arrow Up / Down keys. */
  readonly step: import("@angular/core").InputSignalWithTransform<number, unknown>;
  /** Shows increment and decrement controls. */
  readonly showButtons: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Text displayed before the formatted number. */
  readonly prefix: import("@angular/core").InputSignal<string | undefined>;
  /** Text displayed after the formatted number. */
  readonly suffix: import("@angular/core").InputSignal<string | undefined>;
  readonly placeholder: import("@angular/core").InputSignal<string | undefined>;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Marks the field invalid without changing its value. */
  readonly invalid: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Expands the field to the available width. */
  readonly fluid: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Enables locale-specific digit grouping. */
  readonly useGrouping: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /**
   * Maximum fraction digits. `0` (the default) keeps the field integer-only; a positive value lets the user
   * type the locale's decimal separator, and the fraction is truncated — not rounded — to this many digits.
   */
  readonly maxFractionDigits: import("@angular/core").InputSignalWithTransform<number, unknown>;
  /** Locale used for formatting; omit it to use the browser locale. */
  readonly locale: import("@angular/core").InputSignal<string | undefined>;
  /**
   * `id` of the inner `<input>`, so an external `<label for>` associates. Defaults to the id of an enclosing
   * `tum-ui-form-field`, and to a unique per-instance id outside one.
   */
  readonly inputId: import("@angular/core").InputSignal<string | undefined>;
  /** Native input name. */
  readonly name: import("@angular/core").InputSignal<string | undefined>;
  /** Accessible name for the inner `<input>` when there is no visible `<label>`. */
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Element `id` values that label the inner `<input>`. */
  readonly ariaLabelledBy: import("@angular/core").InputSignal<string | undefined>;
  /** Element `id` values that describe the inner `<input>`. */
  readonly ariaDescribedBy: import("@angular/core").InputSignal<string | undefined>;
  private readonly inputRef;
  private readonly cvaValue;
  private readonly cvaDisabled;
  protected readonly isDisabled: import("@angular/core").Signal<boolean>;
  protected readonly faChevronUp: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faChevronDown: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  private readonly numberFormatter;
  private readonly localeNumberSyntax;
  private readonly formattedValue;
  protected readonly displayText: import("@angular/core").WritableSignal<string>;
  protected readonly ariaValueNow: import("@angular/core").Signal<number | undefined>;
  protected readonly ariaValueText: import("@angular/core").Signal<string | null>;
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
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiInputNumberComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiInputNumberComponent, "tum-ui-input-number", never, {
    "min": {
      "alias": "min";
      "required": false;
      "isSignal": true;
    };
    "max": {
      "alias": "max";
      "required": false;
      "isSignal": true;
    };
    "step": {
      "alias": "step";
      "required": false;
      "isSignal": true;
    };
    "showButtons": {
      "alias": "showButtons";
      "required": false;
      "isSignal": true;
    };
    "prefix": {
      "alias": "prefix";
      "required": false;
      "isSignal": true;
    };
    "suffix": {
      "alias": "suffix";
      "required": false;
      "isSignal": true;
    };
    "placeholder": {
      "alias": "placeholder";
      "required": false;
      "isSignal": true;
    };
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "invalid": {
      "alias": "invalid";
      "required": false;
      "isSignal": true;
    };
    "fluid": {
      "alias": "fluid";
      "required": false;
      "isSignal": true;
    };
    "useGrouping": {
      "alias": "useGrouping";
      "required": false;
      "isSignal": true;
    };
    "maxFractionDigits": {
      "alias": "maxFractionDigits";
      "required": false;
      "isSignal": true;
    };
    "locale": {
      "alias": "locale";
      "required": false;
      "isSignal": true;
    };
    "inputId": {
      "alias": "inputId";
      "required": false;
      "isSignal": true;
    };
    "name": {
      "alias": "name";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "ariaLabelledBy": {
      "alias": "ariaLabelledBy";
      "required": false;
      "isSignal": true;
    };
    "ariaDescribedBy": {
      "alias": "ariaDescribedBy";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, never, true, never>;
}
type TumUiInputSize = 'small' | 'large';
interface TumUiInputClassOptions {
  size?: TumUiInputSize;
  invalid: boolean;
}
declare function tumUiInputClasses(options: TumUiInputClassOptions): string;
export declare class TumUiInputDirective {
  private readonly elementRef;
  private readonly formField;
  readonly tumUiInputSize: import("@angular/core").InputSignal<TumUiInputSize | undefined>;
  readonly tumUiInputInvalid: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /**
   * Overrides the element id. Set it from a wrapper that owns the id; a plain `id` attribute on the element
   * works just as well and is left untouched.
   */
  readonly tumUiInputId: import("@angular/core").InputSignal<string | undefined>;
  /** Extra description ids to merge in, for a wrapper component that owns describing text of its own. */
  readonly tumUiInputDescribedBy: import("@angular/core").InputSignal<string | undefined>;
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
  readonly controlId: import("@angular/core").Signal<string>;
  protected readonly describedBy: import("@angular/core").Signal<string | null>;
  protected readonly isInvalid: import("@angular/core").Signal<boolean>;
  protected readonly hostClasses: import("@angular/core").Signal<string>;
  constructor();
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiInputDirective, never>;
  static ɵdir: i0.ɵɵDirectiveDeclaration<TumUiInputDirective, "input[tumUiInput], textarea[tumUiInput], textarea[tumUiTextarea]", never, {
    "tumUiInputSize": {
      "alias": "tumUiInputSize";
      "required": false;
      "isSignal": true;
    };
    "tumUiInputInvalid": {
      "alias": "tumUiInputInvalid";
      "required": false;
      "isSignal": true;
    };
    "tumUiInputId": {
      "alias": "tumUiInputId";
      "required": false;
      "isSignal": true;
    };
    "tumUiInputDescribedBy": {
      "alias": "tumUiInputDescribedBy";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, never, true, never>;
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
export declare class TumUiListItemActionDirective {
  /** Marks this row as the one currently shown, which sets `aria-current="page"`. */
  readonly active: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  protected readonly hostClasses: import("@angular/core").Signal<string>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiListItemActionDirective, never>;
  static ɵdir: i0.ɵɵDirectiveDeclaration<TumUiListItemActionDirective, "a[tumUiListItemAction], button[tumUiListItemAction]", never, {
    "active": {
      "alias": "active";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, never, true, never>;
}
/**
 * A single row of a {@link TumUiListComponent}.
 *
 * Applied to a real `<li>` so the list keeps its native semantics. Put a `[tumUiListItemAction]` link or
 * button inside for a row that navigates or acts; leave it out for a plain content row.
 */
export declare class TumUiListItemDirective {
  /** Places content in a horizontal row instead of a vertical stack. */
  readonly inline: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  private readonly action;
  protected readonly hostClasses: import("@angular/core").Signal<string>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiListItemDirective, never>;
  static ɵdir: i0.ɵɵDirectiveDeclaration<TumUiListItemDirective, "li[tumUiListItem]", never, {
    "inline": {
      "alias": "inline";
      "required": false;
      "isSignal": true;
    };
  }, {}, ["action"], never, true, never>;
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
export declare class TumUiListComponent {
  /** Accessible name for the list. Set it when the list has no visible heading beside it. */
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Id of the visible heading that names the list. Prefer this over `ariaLabel` when a heading exists. */
  readonly ariaLabelledBy: import("@angular/core").InputSignal<string | undefined>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiListComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiListComponent, "tum-ui-list", never, {
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "ariaLabelledBy": {
      "alias": "ariaLabelledBy";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["*"], true, never>;
}
/**
 * A single command or navigation entry inside a {@link TumUiMenuComponent}.
 *
 * Apply it to a `<button>` for an action, or to an `<a>` for navigation so the entry keeps native link
 * behaviour such as opening in a new tab. The Angular Aria menu item owns the `role`, the roving `tabindex`, and
 * `aria-disabled`; the menu closes once an entry runs and then emits `triggered`.
 *
 * `disabled` takes a boolean binding, `[disabled]="true"`. A disabled entry stays focusable, so it can be discovered
 * with the arrow keys and is announced as unavailable, but it cannot be chosen.
 */
export declare class TumUiMenuItemDirective {
  private readonly menuItem;
  private readonly element;
  /** Emits when the entry is chosen with a click, Enter, or Space; never while it is disabled. */
  readonly triggered: import("@angular/core").OutputEmitterRef<void>;
  /** @internal Whether this is the entry aria moves focus to and chooses from the keyboard. */
  isActive(): boolean;
  /** @internal Whether the event target lies inside this entry. */
  contains(target: EventTarget | null): boolean;
  /** @internal Copies the rendered label into the search term aria's typeahead matches against. */
  syncSearchTerm(): void;
  /** @internal Emits `triggered` once the menu has chosen this entry. */
  emitTriggered(): void;
  protected onEnter(event: Event): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiMenuItemDirective, never>;
  static ɵdir: i0.ɵɵDirectiveDeclaration<TumUiMenuItemDirective, "[tumUiMenuItem]", never, {}, {
    "triggered": "triggered";
  }, never, never, true, [{
    directive: typeof i1$1.MenuItem;
    inputs: {
      "disabled": "disabled";
    };
    outputs: {};
  }]>;
}
/** The trigger that opened a menu, provided to the content it renders so the menu can register with it. */
declare const TUM_UI_MENU_TRIGGER: InjectionToken<TumUiMenuTriggerDirective>;
/**
 * Opens a {@link TumUiMenuComponent} from the element it sits on, which is normally a button.
 *
 * Point it at the `ng-template` that holds the menu: `<button [tumUiMenuTrigger]="actions">`. The Angular Aria menu
 * trigger owns `aria-expanded` / `aria-controls`, opening on click, Enter, Space, or ArrowDown (ArrowUp focuses the
 * last entry), closing on Escape or when focus leaves the menu, and returning focus to the trigger. This directive
 * renders the template in a CDK overlay while the trigger is expanded.
 *
 * `disabled` disables the trigger natively, like the `disabled` attribute of a button: it cannot be focused or clicked,
 * and `tumUiButton` shows it as disabled.
 *
 * Aria opens the menu from Enter and Space on keydown and cancels the key, so a keyboard user produces no `click` on
 * the trigger. React to the menu with `menuOpened` and to a chosen entry with its `triggered` output, never with a
 * `(click)` handler on the trigger, which only mouse users would reach.
 *
 * The class extends the aria trigger, which reads its inputs lazily through `this`, for example `this.menu()` inside
 * its computed signals and effects. The `menu` and `softDisabled` overrides below only take effect because aria keeps
 * doing that; if an aria update captures them at construction instead, the menu no longer opens.
 */
export declare class TumUiMenuTriggerDirective extends MenuTrigger<unknown> {
  private readonly injector;
  private readonly viewContainerRef;
  private readonly directionality;
  /**
   * Aria soft-disables a trigger by default, which keeps it focusable and drops the native `disabled` attribute. A
   * menu button in Artemis is an ordinary button, so `disabled` keeps its native meaning.
   */
  readonly softDisabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** The `ng-template` holding the `tum-ui-menu` to open. */
  readonly menuTemplate: InputSignal<TemplateRef<unknown>>;
  /** Overlay positions to try, in order; defaults to below the trigger, aligned to its start edge. */
  readonly tumUiMenuPosition: InputSignal<ConnectedPosition[] | undefined>;
  /** Emits when the menu opens. */
  readonly menuOpened: import("@angular/core").OutputEmitterRef<void>;
  /** Emits when the menu closes. */
  readonly menuClosed: import("@angular/core").OutputEmitterRef<void>;
  /**
   * The menu rendered from the template while the trigger is expanded.
   *
   * Aria wires a trigger to its menu through the `menu` input, but here the menu only exists once the template has
   * been rendered into the overlay, and a directive cannot bind an input of the class it extends. The rendered menu
   * registers itself instead (see `attachMenu`), and aria reads it from this signal, which it only ever calls.
   */
  private readonly renderedMenu;
  /**
   * The menu aria operates on. Do not bind `[menu]` on the trigger: the name stays an input inherited from aria, and
   * the template type check accepts the binding, but it throws at runtime, because the trigger always takes its menu
   * from the template it points at. Narrowing the input so the check rejects it would break the override of aria's type.
   */
  readonly menu: InputSignal<Menu<unknown> | undefined>;
  private overlayRef?;
  constructor();
  /**
   * Registers the menu rendered from the template, and returns the function that withdraws it again.
   *
   * @internal Called by `tum-ui-menu`; not part of the public API.
   */
  attachMenu(menu: Menu<unknown>): () => void;
  /**
   * Closes the menu and moves focus back to the trigger.
   *
   * @internal Called by `tum-ui-menu` when Tab leaves it, so focus continues from the trigger instead of from the
   * overlay at the end of the document.
   */
  closeAndFocus(): void;
  private attachOverlay;
  private detachOverlay;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiMenuTriggerDirective, never>;
  static ɵdir: i0.ɵɵDirectiveDeclaration<TumUiMenuTriggerDirective, "[tumUiMenuTrigger]", never, {
    "softDisabled": {
      "alias": "softDisabled";
      "required": false;
      "isSignal": true;
    };
    "menuTemplate": {
      "alias": "tumUiMenuTrigger";
      "required": true;
      "isSignal": true;
    };
    "tumUiMenuPosition": {
      "alias": "tumUiMenuPosition";
      "required": false;
      "isSignal": true;
    };
  }, {
    "menuOpened": "menuOpened";
    "menuClosed": "menuClosed";
  }, never, never, true, never>;
}
/**
 * Menu surface for a list of actions, opened by {@link TumUiMenuTriggerDirective} and filled with
 * `[tumUiMenuItem]` entries.
 *
 * The roles, roving focus, arrow-key and Home/End navigation, typeahead, and closing on Escape or once an entry runs
 * come from the Angular Aria menu; this component owns the surface styling. Declare it inside the `ng-template` the
 * trigger points at, so nothing renders until the menu opens:
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
export declare class TumUiMenuComponent {
  private readonly menu;
  private readonly trigger;
  private readonly items;
  /** The entry aria chooses if the event being dispatched selects one. */
  private pendingItem?;
  constructor();
  protected onKeydown(event: KeyboardEvent): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiMenuComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiMenuComponent, "tum-ui-menu", never, {}, {}, ["items"], ["*"], true, [{
    directive: typeof i1$1.Menu;
    inputs: {};
    outputs: {};
  }]>;
}
type TumUiMessageSeverity = 'info' | 'success' | 'warn' | 'error' | 'secondary' | 'contrast';
export declare class TumUiMessageComponent {
  readonly severity: import("@angular/core").InputSignal<TumUiMessageSeverity>;
  readonly text: import("@angular/core").InputSignal<string | undefined>;
  readonly icon: import("@angular/core").InputSignal<IconProp | undefined>;
  protected readonly messageRole: import("@angular/core").Signal<"alert" | "status">;
  protected readonly hostClasses: import("@angular/core").Signal<string>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiMessageComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiMessageComponent, "tum-ui-message", never, {
    "severity": {
      "alias": "severity";
      "required": false;
      "isSignal": true;
    };
    "text": {
      "alias": "text";
      "required": false;
      "isSignal": true;
    };
    "icon": {
      "alias": "icon";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["*"], true, never>;
}
/** Controlled paginator using zero-based page indexes. */
export declare class TumUiPaginatorComponent {
  private readonly directionality;
  private readonly destroyRef;
  private readonly direction;
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Total records in the consumer-owned result set. */
  readonly totalRecords: import("@angular/core").InputSignalWithTransform<number, unknown>;
  /** Zero-based active page index. */
  readonly page: import("@angular/core").InputSignalWithTransform<number, unknown>;
  /** Controlled number of records per page. */
  readonly pageSize: import("@angular/core").InputSignalWithTransform<number, unknown>;
  readonly pageSizeOptions: import("@angular/core").InputSignal<number[]>;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly showCurrentPageReport: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly showRowsPerPage: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Requests a zero-based page without mutating `page`. */
  readonly pageChange: import("@angular/core").OutputEmitterRef<number>;
  /** Requests a page size without mutating `pageSize`. */
  readonly pageSizeChange: import("@angular/core").OutputEmitterRef<number>;
  protected readonly firstPageIcon: import("@angular/core").Signal<import("@fortawesome/fontawesome-svg-core").IconDefinition>;
  protected readonly previousPageIcon: import("@angular/core").Signal<import("@fortawesome/fontawesome-svg-core").IconDefinition>;
  protected readonly nextPageIcon: import("@angular/core").Signal<import("@fortawesome/fontawesome-svg-core").IconDefinition>;
  protected readonly lastPageIcon: import("@angular/core").Signal<import("@fortawesome/fontawesome-svg-core").IconDefinition>;
  protected readonly navButtonClasses = "tum:inline-flex tum:h-9 tum:w-9 tum:shrink-0 tum:cursor-pointer tum:appearance-none tum:items-center tum:justify-center tum:rounded-full tum:border-0 tum:bg-transparent tum:text-sm tum:text-muted tum:transition-colors tum:hover:bg-hover-background tum:disabled:pointer-events-none tum:disabled:opacity-50";
  protected readonly selectedPageClasses: string;
  protected readonly totalPages: import("@angular/core").Signal<number>;
  protected readonly clampedPage: import("@angular/core").Signal<number>;
  protected readonly isFirst: import("@angular/core").Signal<boolean>;
  protected readonly isLast: import("@angular/core").Signal<boolean>;
  protected readonly rangeBegin: import("@angular/core").Signal<number>;
  protected readonly rangeEnd: import("@angular/core").Signal<number>;
  protected readonly visiblePages: import("@angular/core").Signal<number[]>;
  constructor();
  protected goToPage(target: number): void;
  protected goToFirst(): void;
  protected goToPrevious(): void;
  protected goToNext(): void;
  protected goToLast(): void;
  protected onPageSizeChange(value: number): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiPaginatorComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiPaginatorComponent, "tum-ui-paginator", never, {
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "totalRecords": {
      "alias": "totalRecords";
      "required": false;
      "isSignal": true;
    };
    "page": {
      "alias": "page";
      "required": false;
      "isSignal": true;
    };
    "pageSize": {
      "alias": "pageSize";
      "required": false;
      "isSignal": true;
    };
    "pageSizeOptions": {
      "alias": "pageSizeOptions";
      "required": false;
      "isSignal": true;
    };
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "showCurrentPageReport": {
      "alias": "showCurrentPageReport";
      "required": false;
      "isSignal": true;
    };
    "showRowsPerPage": {
      "alias": "showRowsPerPage";
      "required": false;
      "isSignal": true;
    };
  }, {
    "pageChange": "pageChange";
    "pageSizeChange": "pageSizeChange";
  }, never, never, true, never>;
}
export declare class TumUiPanelComponent {
  private readonly translator;
  /**
   * Header title text. Omit when projecting a `[tumUiPanelHeader]` slot instead, and set `toggleAriaLabel`
   * alongside it — projected markup does not label the toggle.
   */
  readonly header: import("@angular/core").InputSignal<string>;
  /** Enables disclosure behavior for the projected content. */
  readonly toggleable: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Overrides the toggle name; otherwise the header or package translation is used. */
  readonly toggleAriaLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Controlled disclosure state, applied only when `toggleable` is enabled. */
  readonly collapsed: import("@angular/core").ModelSignal<boolean>;
  protected readonly headerId: string;
  protected readonly contentId: string;
  protected readonly faChevronDown: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faChevronUp: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly isCollapsed: import("@angular/core").Signal<boolean>;
  protected readonly toggleLabelledBy: import("@angular/core").Signal<string | null>;
  protected readonly toggleLabel: import("@angular/core").Signal<string | null>;
  protected toggle(): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiPanelComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiPanelComponent, "tum-ui-panel", never, {
    "header": {
      "alias": "header";
      "required": false;
      "isSignal": true;
    };
    "toggleable": {
      "alias": "toggleable";
      "required": false;
      "isSignal": true;
    };
    "toggleAriaLabel": {
      "alias": "toggleAriaLabel";
      "required": false;
      "isSignal": true;
    };
    "collapsed": {
      "alias": "collapsed";
      "required": false;
      "isSignal": true;
    };
  }, {
    "collapsed": "collapsedChange";
  }, never, ["[tumUiPanelHeader]", "*", "[tumUiPanelFooter]"], true, never>;
}
type TumUiOverlayPlacement = 'top' | 'bottom' | 'left' | 'right';
interface TumUiConnectedOverlayOptions {
  hasBackdrop?: boolean;
  matchOriginWidth?: boolean;
}
declare class TumUiOverlayService {
  private readonly overlay;
  private readonly directionality;
  positionStrategy(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumUiOverlayPlacement): FlexibleConnectedPositionStrategy;
  placementFromPosition(pos: ConnectedPosition): TumUiOverlayPlacement;
  createConnectedOverlay(origin: ElementRef<HTMLElement> | HTMLElement, placement: TumUiOverlayPlacement, options?: TumUiConnectedOverlayOptions): OverlayRef;
  private horizontalPositions;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiOverlayService, never>;
  static ɵprov: i0.ɵɵInjectableDeclaration<any>;
}
/**
 * Anchored panel for rich or interactive content, opened via {@link TumUiPopoverTriggerDirective}. Use the
 * tooltip instead for a short, non-interactive hint.
 *
 * Built on the shared overlay substrate, so it inherits collision-aware positioning with a flipped fallback.
 * Closes on backdrop click and Escape, and traps then restores focus. Renders nothing inline: the projected
 * content is captured in an `ng-template` and portaled on open.
 */
export declare class TumUiPopoverComponent implements OnDestroy {
  private readonly overlayService;
  private readonly viewContainerRef;
  private readonly document;
  readonly placement: import("@angular/core").InputSignal<TumUiOverlayPlacement>;
  /** Accessible name announced for the role="dialog" panel. Required: a dialog must have a name. */
  readonly ariaLabel: import("@angular/core").InputSignal<string>;
  readonly openChange: import("@angular/core").OutputEmitterRef<boolean>;
  private readonly panel;
  private overlayRef?;
  private positionSub?;
  private readonly openState;
  /**
   * Where the panel ended up, which is not always where it was asked to go: CDK flips it when the preferred side
   * has no room. The opening animation grows the panel from the edge nearest its origin, so it has to follow.
   */
  protected readonly appliedPlacement: import("@angular/core").WritableSignal<TumUiOverlayPlacement>;
  /** Whether the popover is currently open. Read-only: drive it through open() / close() / toggle(). */
  readonly isOpen: import("@angular/core").Signal<boolean>;
  /** Open the popover anchored to `origin`. No-op if already open. */
  open(origin: ElementRef<HTMLElement> | HTMLElement): void;
  /**
   * Close the popover and dispose its overlay. No-op if already closed.
   *
   * Closed straight away, though the panel fades out first: callers drive their own state off `isOpen`/`openChange`.
   */
  close(): void;
  /** Fades the panel out, then disposes. Disposes at once under reduced motion or without the animation API. */
  private fadeOutAndDispose;
  /** Open the popover if closed, or close it if open. */
  toggle(origin: ElementRef<HTMLElement> | HTMLElement): void;
  ngOnDestroy(): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiPopoverComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiPopoverComponent, "tum-ui-popover", never, {
    "placement": {
      "alias": "placement";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": true;
      "isSignal": true;
    };
  }, {
    "openChange": "openChange";
  }, never, ["*"], true, never>;
}
/**
 * Wires a trigger element to a {@link TumUiPopoverComponent}: click toggles the popover anchored to
 * the trigger, and the trigger reflects `aria-haspopup`/`aria-expanded` for accessibility.
 *
 * Usage: `<button [tumUiPopoverTrigger]="pop">Details</button> <tum-ui-popover #pop>...</tum-ui-popover>`
 */
export declare class TumUiPopoverTriggerDirective {
  private readonly elementRef;
  readonly popover: import("@angular/core").InputSignal<TumUiPopoverComponent>;
  protected toggle(): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiPopoverTriggerDirective, never>;
  static ɵdir: i0.ɵɵDirectiveDeclaration<TumUiPopoverTriggerDirective, "[tumUiPopoverTrigger]", never, {
    "popover": {
      "alias": "tumUiPopoverTrigger";
      "required": true;
      "isSignal": true;
    };
  }, {}, never, never, true, never>;
}
type TumUiProgressBarSeverity = 'primary' | 'success' | 'warn' | 'danger' | 'info';
type TumUiProgressBarSize = 'small' | 'default';
export declare class TumUiProgressBarComponent {
  readonly value: import("@angular/core").InputSignalWithTransform<number, unknown>;
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly showValue: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Track height. `small` is a slim rail for dense contexts such as table cells and has no room for the inline label. */
  readonly size: import("@angular/core").InputSignal<TumUiProgressBarSize>;
  /** Semantic color of the filled track. */
  readonly severity: import("@angular/core").InputSignal<TumUiProgressBarSeverity>;
  readonly unit: import("@angular/core").InputSignal<string>;
  protected readonly normalizedValue: import("@angular/core").Signal<number>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiProgressBarComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiProgressBarComponent, "tum-ui-progress-bar", never, {
    "value": {
      "alias": "value";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "showValue": {
      "alias": "showValue";
      "required": false;
      "isSignal": true;
    };
    "size": {
      "alias": "size";
      "required": false;
      "isSignal": true;
    };
    "severity": {
      "alias": "severity";
      "required": false;
      "isSignal": true;
    };
    "unit": {
      "alias": "unit";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["*"], true, never>;
}
export declare class TumUiProgressSpinnerComponent {
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiProgressSpinnerComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiProgressSpinnerComponent, "tum-ui-progress-spinner", never, {
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, never, true, never>;
}
interface TumUiRadioButtonSelectEvent {
  originalEvent: MouseEvent;
  value: unknown;
}
/** Native radio control with TUM UI styling and Angular forms integration. */
export declare class TumUiRadioButtonComponent implements ControlValueAccessor {
  /** Value written to the containing form when this option is selected. */
  readonly value: import("@angular/core").InputSignal<unknown>;
  /** Native radio-group name. Radios belong together when they share a form owner and name. */
  readonly name: import("@angular/core").InputSignal<string | undefined>;
  /** ID used to associate a consumer-provided label with the native radio. */
  readonly inputId: import("@angular/core").InputSignal<string | undefined>;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Accessible name used when no associated label is rendered. */
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Emits the originating click and selected option value. */
  readonly selected: import("@angular/core").OutputEmitterRef<TumUiRadioButtonSelectEvent>;
  private readonly cvaValue;
  protected readonly isChecked: import("@angular/core").Signal<boolean>;
  private readonly cvaDisabled;
  protected readonly isDisabled: import("@angular/core").Signal<boolean>;
  protected readonly boxClasses: import("@angular/core").Signal<"tum:bg-disabled-background tum:border-control-border" | "tum:bg-primary tum:border-primary" | "tum:bg-control-background tum:border-control-border">;
  protected readonly iconClasses: import("@angular/core").Signal<"tum:bg-disabled" | "tum:bg-primary-contrast">;
  private onModelChange;
  private onModelTouched;
  protected onInputClick(event: MouseEvent): void;
  protected onBlur(): void;
  writeValue(value: unknown): void;
  registerOnChange(fn: (value: unknown) => void): void;
  registerOnTouched(fn: () => void): void;
  setDisabledState(isDisabled: boolean): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiRadioButtonComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiRadioButtonComponent, "tum-ui-radio-button", never, {
    "value": {
      "alias": "value";
      "required": false;
      "isSignal": true;
    };
    "name": {
      "alias": "name";
      "required": false;
      "isSignal": true;
    };
    "inputId": {
      "alias": "inputId";
      "required": false;
      "isSignal": true;
    };
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
  }, {
    "selected": "selected";
  }, never, never, true, never>;
}
/** Text field for filtering a view: a leading magnifier and a clear control that appears once there is a term. */
export declare class TumUiSearchFieldComponent {
  /** Two-way bindable term. Emits on every keystroke; debounce in the consumer if the term drives a request. */
  readonly value: import("@angular/core").ModelSignal<string>;
  /** Translation key, resolved through the configured translator. */
  readonly placeholder: import("@angular/core").InputSignal<string>;
  /** Translation key for the accessible name. Falls back to the placeholder. */
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly size: import("@angular/core").InputSignal<TumUiInputSize | undefined>;
  protected readonly faMagnifyingGlass: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faXmark: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly accessibleNameKey: import("@angular/core").Signal<string>;
  private readonly inputElement;
  protected onInput(term: string): void;
  /** Clears the term and returns focus to the field, so the reader can keep typing without reaching for the mouse. */
  protected clear(): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiSearchFieldComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiSearchFieldComponent, "tum-ui-search-field", never, {
    "value": {
      "alias": "value";
      "required": false;
      "isSignal": true;
    };
    "placeholder": {
      "alias": "placeholder";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "size": {
      "alias": "size";
      "required": false;
      "isSignal": true;
    };
  }, {
    "value": "valueChange";
  }, never, never, true, never>;
}
type TumUiSelectSize = 'small' | 'large';
/** Single-value ControlValueAccessor backed by a listbox overlay. */
export declare class TumUiSelectComponent implements ControlValueAccessor {
  private readonly overlayService;
  private readonly viewContainerRef;
  private readonly destroyRef;
  private readonly document;
  private readonly injector;
  private readonly formField;
  readonly options: import("@angular/core").InputSignal<readonly unknown[]>;
  /** Property name used as the visible label for object options. */
  readonly optionLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Property name written to the form value; omit it to write the option itself. */
  readonly optionValue: import("@angular/core").InputSignal<string | undefined>;
  readonly placeholder: import("@angular/core").InputSignal<string | undefined>;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly showClear: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Adds a search field above the option list, for option sets too long to scan. */
  readonly filter: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /**
   * Comma-separated property names searched by the filter, for object options whose match should not be
   * limited to the visible label — `"name,login"`, say. Defaults to the label alone.
   */
  readonly filterBy: import("@angular/core").InputSignal<string | undefined>;
  readonly filterPlaceholder: import("@angular/core").InputSignal<string | undefined>;
  readonly size: import("@angular/core").InputSignal<TumUiSelectSize | undefined>;
  /**
   * `id` of the trigger, so an external `<label for>` associates. Defaults to the id of an enclosing
   * `tum-ui-form-field`, and to a unique per-instance id outside one.
   */
  readonly inputId: import("@angular/core").InputSignal<string | undefined>;
  readonly name: import("@angular/core").InputSignal<string | undefined>;
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly clearAriaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly emptyMessage: import("@angular/core").InputSignal<string | undefined>;
  readonly filterAriaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly selectionChange: import("@angular/core").OutputEmitterRef<unknown>;
  protected readonly faChevronDown: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faCheck: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  protected readonly faXmark: import("@fortawesome/fontawesome-svg-core").IconDefinition;
  private readonly fallbackInputId;
  protected readonly resolvedInputId: import("@angular/core").Signal<string>;
  protected readonly describedBy: import("@angular/core").Signal<string | null>;
  protected readonly isInvalid: import("@angular/core").Signal<boolean>;
  protected readonly listboxId: string;
  private readonly trigger;
  private readonly panel;
  private readonly filterInput;
  private overlayRef?;
  protected readonly isOpen: import("@angular/core").WritableSignal<boolean>;
  protected readonly activeIndex: import("@angular/core").WritableSignal<number>;
  protected readonly filterText: import("@angular/core").WritableSignal<string>;
  private readonly selectedValue;
  private readonly disabledByForm;
  private onChangeCallback;
  private onTouchedCallback;
  protected readonly isDisabled: import("@angular/core").Signal<boolean>;
  protected readonly selectedOption: import("@angular/core").Signal<unknown>;
  protected readonly isFiltering: import("@angular/core").Signal<boolean>;
  /** Keyboard navigation, option IDs and aria-activedescendant share this filtered order. */
  protected readonly visibleOptions: import("@angular/core").Signal<readonly unknown[]>;
  protected readonly hasSelection: import("@angular/core").Signal<boolean>;
  protected readonly displayLabel: import("@angular/core").Signal<string>;
  protected readonly showClearButton: import("@angular/core").Signal<boolean>;
  protected readonly triggerClasses: import("@angular/core").Signal<string>;
  protected readonly activeOptionId: import("@angular/core").Signal<string | undefined>;
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
  protected onFilterKeydown(event: KeyboardEvent): void;
  private handleTypeahead;
  private resetTypeahead;
  private scrollOptionIntoView;
  private buildTriggerClasses;
  protected optionClasses(option: unknown, index: number): string;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiSelectComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiSelectComponent, "tum-ui-select", never, {
    "options": {
      "alias": "options";
      "required": false;
      "isSignal": true;
    };
    "optionLabel": {
      "alias": "optionLabel";
      "required": false;
      "isSignal": true;
    };
    "optionValue": {
      "alias": "optionValue";
      "required": false;
      "isSignal": true;
    };
    "placeholder": {
      "alias": "placeholder";
      "required": false;
      "isSignal": true;
    };
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "showClear": {
      "alias": "showClear";
      "required": false;
      "isSignal": true;
    };
    "filter": {
      "alias": "filter";
      "required": false;
      "isSignal": true;
    };
    "filterBy": {
      "alias": "filterBy";
      "required": false;
      "isSignal": true;
    };
    "filterPlaceholder": {
      "alias": "filterPlaceholder";
      "required": false;
      "isSignal": true;
    };
    "size": {
      "alias": "size";
      "required": false;
      "isSignal": true;
    };
    "inputId": {
      "alias": "inputId";
      "required": false;
      "isSignal": true;
    };
    "name": {
      "alias": "name";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "clearAriaLabel": {
      "alias": "clearAriaLabel";
      "required": false;
      "isSignal": true;
    };
    "emptyMessage": {
      "alias": "emptyMessage";
      "required": false;
      "isSignal": true;
    };
    "filterAriaLabel": {
      "alias": "filterAriaLabel";
      "required": false;
      "isSignal": true;
    };
  }, {
    "selectionChange": "selectionChange";
  }, never, never, true, never>;
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
export declare class TumUiSelectButtonComponent implements ControlValueAccessor {
  /** Options that yield neither a primitive label nor an item template are omitted. */
  readonly options: import("@angular/core").InputSignal<readonly unknown[]>;
  /** Object property used as the visible option label. */
  readonly optionLabel: import("@angular/core").InputSignal<string | undefined>;
  /** Object property written to the form value; omit it to write the option. */
  readonly optionValue: import("@angular/core").InputSignal<string | undefined>;
  readonly size: import("@angular/core").InputSignal<TumUiSelectButtonSize | undefined>;
  /** Allows the selected option to be toggled back to `undefined`. */
  readonly allowEmpty: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Optional presentation template; the option remains its implicit context value. */
  readonly itemTemplate: import("@angular/core").InputSignal<TemplateRef<{
    $implicit: TumUiSelectButtonOption;
  }> | undefined>;
  /** Emits the selected value, or `undefined` when cleared. */
  readonly changed: import("@angular/core").OutputEmitterRef<unknown>;
  private readonly value;
  private readonly cvaDisabled;
  protected readonly effectiveDisabled: import("@angular/core").Signal<boolean>;
  protected onChange: (value: unknown) => void;
  protected onTouched: () => void;
  protected readonly normalizedOptions: import("@angular/core").Signal<NormalizedOption[]>;
  protected optionClasses(selected: boolean): string;
  protected select(option: NormalizedOption): void;
  writeValue(value: unknown): void;
  registerOnChange(fn: (value: unknown) => void): void;
  registerOnTouched(fn: () => void): void;
  setDisabledState(isDisabled: boolean): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiSelectButtonComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiSelectButtonComponent, "tum-ui-select-button", never, {
    "options": {
      "alias": "options";
      "required": false;
      "isSignal": true;
    };
    "optionLabel": {
      "alias": "optionLabel";
      "required": false;
      "isSignal": true;
    };
    "optionValue": {
      "alias": "optionValue";
      "required": false;
      "isSignal": true;
    };
    "size": {
      "alias": "size";
      "required": false;
      "isSignal": true;
    };
    "allowEmpty": {
      "alias": "allowEmpty";
      "required": false;
      "isSignal": true;
    };
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "itemTemplate": {
      "alias": "itemTemplate";
      "required": false;
      "isSignal": true;
    };
  }, {
    "changed": "changed";
  }, never, never, true, never>;
}
type TumUiSortDirection$1 = 'asc' | 'desc' | 'none';
export declare class TumUiTableSortableColumnComponent {
  private readonly table;
  readonly field: import("@angular/core").InputSignal<string>;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  protected readonly direction: import("@angular/core").Signal<TumUiSortDirection$1>;
  protected readonly ariaSort: import("@angular/core").Signal<"none" | "ascending" | "descending">;
  protected readonly sortIcon: import("@angular/core").Signal<import("@fortawesome/fontawesome-svg-core").IconDefinition>;
  protected readonly hostClasses: import("@angular/core").Signal<"" | "tum:cursor-pointer tum:select-none tum:hover:bg-hover-background">;
  protected onActivate(): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTableSortableColumnComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiTableSortableColumnComponent, "th[tumUiSortableColumn]", never, {
    "field": {
      "alias": "tumUiSortableColumn";
      "required": true;
      "isSignal": true;
    };
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["*"], true, never>;
}
type TumUiTableSize = 'small' | 'normal' | 'large';
interface TumUiTableSortEvent {
  field: string;
  order: number;
}
export declare class TumUiTableDirective {
  readonly size: import("@angular/core").InputSignal<TumUiTableSize>;
  readonly striped: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly scrollable: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly rowHover: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly sortField: import("@angular/core").InputSignal<string | undefined>;
  readonly sortOrder: import("@angular/core").InputSignalWithTransform<number, unknown>;
  readonly defaultSortOrder: import("@angular/core").InputSignalWithTransform<number, unknown>;
  readonly sortChange: import("@angular/core").OutputEmitterRef<TumUiTableSortEvent>;
  protected readonly hostClasses: import("@angular/core").Signal<string>;
  requestSort(field: string): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTableDirective, never>;
  static ɵdir: i0.ɵɵDirectiveDeclaration<TumUiTableDirective, "table[tumUiTable]", never, {
    "size": {
      "alias": "size";
      "required": false;
      "isSignal": true;
    };
    "striped": {
      "alias": "striped";
      "required": false;
      "isSignal": true;
    };
    "scrollable": {
      "alias": "scrollable";
      "required": false;
      "isSignal": true;
    };
    "rowHover": {
      "alias": "rowHover";
      "required": false;
      "isSignal": true;
    };
    "sortField": {
      "alias": "sortField";
      "required": false;
      "isSignal": true;
    };
    "sortOrder": {
      "alias": "sortOrder";
      "required": false;
      "isSignal": true;
    };
    "defaultSortOrder": {
      "alias": "defaultSortOrder";
      "required": false;
      "isSignal": true;
    };
  }, {
    "sortChange": "sortChange";
  }, never, never, true, never>;
}
/** Fixed-row-height virtual table for large in-memory collections. */
export declare class TumUiTableVirtualScrollComponent<T> {
  readonly items: import("@angular/core").InputSignal<readonly T[]>;
  /** Row height in CSS pixels used by the CDK fixed-size virtual-scroll strategy. */
  readonly itemSize: import("@angular/core").InputSignal<number>;
  readonly rowTemplate: import("@angular/core").InputSignal<TemplateRef<{
    $implicit: T;
    index: number;
  }>>;
  readonly size: import("@angular/core").InputSignal<TumUiTableSize>;
  readonly striped: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly rowHover: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly scrollHeight: import("@angular/core").InputSignal<string>;
  readonly minWidth: import("@angular/core").InputSignal<string | undefined>;
  readonly trackBy: import("@angular/core").InputSignal<TrackByFunction<T> | undefined>;
  readonly ariaDescribedBy: import("@angular/core").InputSignal<string | undefined>;
  protected readonly isFlexHeight: import("@angular/core").Signal<boolean>;
  protected readonly viewportHeight: import("@angular/core").Signal<string | undefined>;
  protected readonly effectiveTrackBy: import("@angular/core").Signal<TrackByFunction<T>>;
  protected readonly headerClasses: import("@angular/core").Signal<string>;
  protected readonly rowClasses: import("@angular/core").Signal<string>;
  protected stripeClass(index: number): string;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTableVirtualScrollComponent<any>, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiTableVirtualScrollComponent<any>, "tum-ui-table-virtual-scroll", never, {
    "items": {
      "alias": "items";
      "required": true;
      "isSignal": true;
    };
    "itemSize": {
      "alias": "itemSize";
      "required": true;
      "isSignal": true;
    };
    "rowTemplate": {
      "alias": "rowTemplate";
      "required": true;
      "isSignal": true;
    };
    "size": {
      "alias": "size";
      "required": false;
      "isSignal": true;
    };
    "striped": {
      "alias": "striped";
      "required": false;
      "isSignal": true;
    };
    "rowHover": {
      "alias": "rowHover";
      "required": false;
      "isSignal": true;
    };
    "scrollHeight": {
      "alias": "scrollHeight";
      "required": false;
      "isSignal": true;
    };
    "minWidth": {
      "alias": "minWidth";
      "required": false;
      "isSignal": true;
    };
    "trackBy": {
      "alias": "trackBy";
      "required": false;
      "isSignal": true;
    };
    "ariaDescribedBy": {
      "alias": "ariaDescribedBy";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["*"], true, never>;
}
interface ColumnDef<T> {
  /** Top-level property or nested path such as `owner.name` or `items[0].label`. */
  field?: (keyof T & string) | (string & {});
  header?: string;
  headerKey?: string;
  /** Translation key for a hint explaining a column whose heading alone is ambiguous, shown behind a help icon. */
  headerTooltip?: string;
  /**
   * Minimum width as any CSS length. Prefer `rem` so a column sized to hold text grows with the reader's font.
   *
   * This is a floor, never a cap: the column claims the width whether or not its cells fill it, and the table can
   * only take the space back from a column that declares none. Set it where a cell would otherwise collapse to
   * nothing, not as a guess at how wide the content wants to be — auto layout already sizes a column to its content
   * and shares out whatever is left. Floors summing past the available width push the table into its scroller.
   */
  width?: string;
  sort?: boolean;
  /**
   * Lets a long heading wrap onto a second line. Headings are nowrap by default, which makes each one a floor its
   * column can never go below, so a heading much wider than the values under it is worth wrapping instead.
   */
  wrapHeader?: boolean;
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
export declare class TumUiTableComponent<T> {
  private readonly destroyRef;
  /** Columns displayed in declaration order. Nested field paths use lodash path syntax. */
  readonly columns: import("@angular/core").InputSignal<ColumnDef<T>[]>;
  /** Rows for the current page. Sorting and filtering are not applied locally. */
  readonly rows: import("@angular/core").InputSignal<T[]>;
  readonly totalRecords: import("@angular/core").InputSignalWithTransform<number, unknown>;
  readonly loading: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  /** Optional action template receiving the row as its implicit value. */
  readonly rowActions: import("@angular/core").InputSignal<TemplateRef<{
    $implicit: T;
  }> | undefined>;
  /** Predicate identifying rows to highlight. */
  readonly rowHighlighted: import("@angular/core").InputSignal<((row: T) => boolean) | undefined>;
  /** Identity function forwarded to the CDK table. */
  readonly trackBy: import("@angular/core").InputSignal<TrackByFunction<T> | undefined>;
  readonly striped: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly scrollable: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly scrollHeight: import("@angular/core").InputSignal<string | undefined>;
  readonly showSearch: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly searchPlaceholder: import("@angular/core").InputSignal<string>;
  readonly emptyMessage: import("@angular/core").InputSignal<string>;
  readonly pageSize: import("@angular/core").InputSignalWithTransform<number, unknown>;
  readonly pageSizeOptions: import("@angular/core").InputSignal<number[]>;
  readonly showRowsPerPage: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly showCurrentPageReport: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly initialSortField: import("@angular/core").InputSignal<string | undefined>;
  readonly initialSortDirection: import("@angular/core").InputSignal<TumUiSortDirection>;
  /** Requests a zero-based page with the active page size, sort, and search term. */
  readonly dataRequest: import("@angular/core").OutputEmitterRef<TumUiTableQueryEvent>;
  protected readonly ACTIONS_COLUMN = "__tum_ui_actions__";
  protected readonly faCircleQuestion: IconDefinition;
  protected readonly faMagnifyingGlass: IconDefinition;
  protected readonly faSort: IconDefinition;
  protected readonly faSortDown: IconDefinition;
  protected readonly faSortUp: IconDefinition;
  private readonly cdkTable;
  private readonly page;
  private readonly pageSizeState;
  private readonly sortState;
  private readonly searchTerm;
  private searchTimer?;
  protected readonly effectivePageSize: import("@angular/core").Signal<number>;
  protected readonly currentPage: import("@angular/core").Signal<number>;
  protected readonly effectiveTrackBy: import("@angular/core").Signal<TrackByFunction<T>>;
  protected readonly displayedColumns: import("@angular/core").Signal<string[]>;
  protected readonly tableClasses: import("@angular/core").Signal<"tum:w-full tum:border-collapse tum:text-sm" | "tum:w-full tum:border-collapse tum:text-sm tum:[&_tbody_tr:nth-child(odd)]:bg-table-striped-background">;
  constructor();
  /** Jump back to the first page and re-request. For consumers that own filtering themselves (`showSearch` off). */
  resetPage(): void;
  protected columnName(col: ColumnDef<T>, index: number): string;
  protected resolveValue(row: T, col: ColumnDef<T>): unknown;
  protected cellParams(row: T, col: ColumnDef<T>, rowIndex: number): CellRendererParams<T>;
  protected columnVisibilityClasses(col: ColumnDef<T>): string;
  /**
   * A header that cannot wrap is a floor the column can never go below, so a long one costs its full width in every
   * layout however little the cells under it hold. `wrapHeader` trades a two-line heading for that width.
   */
  protected headerCellClasses(col: ColumnDef<T>): string;
  protected ariaSortFor(col: ColumnDef<T>): 'ascending' | 'descending' | 'none' | undefined;
  protected sortDirection(col: ColumnDef<T>): 'none' | TumUiSortDirection;
  protected sortIcon(col: ColumnDef<T>): IconDefinition;
  protected onSortClick(col: ColumnDef<T>): void;
  protected onSearchInput(value: string): void;
  protected onPageChange(page: number): void;
  protected onPageSizeChange(size: number): void;
  private emitDataRequest;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTableComponent<any>, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiTableComponent<any>, "tum-ui-table", never, {
    "columns": {
      "alias": "columns";
      "required": true;
      "isSignal": true;
    };
    "rows": {
      "alias": "rows";
      "required": true;
      "isSignal": true;
    };
    "totalRecords": {
      "alias": "totalRecords";
      "required": false;
      "isSignal": true;
    };
    "loading": {
      "alias": "loading";
      "required": false;
      "isSignal": true;
    };
    "rowActions": {
      "alias": "rowActions";
      "required": false;
      "isSignal": true;
    };
    "rowHighlighted": {
      "alias": "rowHighlighted";
      "required": false;
      "isSignal": true;
    };
    "trackBy": {
      "alias": "trackBy";
      "required": false;
      "isSignal": true;
    };
    "striped": {
      "alias": "striped";
      "required": false;
      "isSignal": true;
    };
    "scrollable": {
      "alias": "scrollable";
      "required": false;
      "isSignal": true;
    };
    "scrollHeight": {
      "alias": "scrollHeight";
      "required": false;
      "isSignal": true;
    };
    "showSearch": {
      "alias": "showSearch";
      "required": false;
      "isSignal": true;
    };
    "searchPlaceholder": {
      "alias": "searchPlaceholder";
      "required": false;
      "isSignal": true;
    };
    "emptyMessage": {
      "alias": "emptyMessage";
      "required": false;
      "isSignal": true;
    };
    "pageSize": {
      "alias": "pageSize";
      "required": false;
      "isSignal": true;
    };
    "pageSizeOptions": {
      "alias": "pageSizeOptions";
      "required": false;
      "isSignal": true;
    };
    "showRowsPerPage": {
      "alias": "showRowsPerPage";
      "required": false;
      "isSignal": true;
    };
    "showCurrentPageReport": {
      "alias": "showCurrentPageReport";
      "required": false;
      "isSignal": true;
    };
    "initialSortField": {
      "alias": "initialSortField";
      "required": false;
      "isSignal": true;
    };
    "initialSortDirection": {
      "alias": "initialSortDirection";
      "required": false;
      "isSignal": true;
    };
  }, {
    "dataRequest": "dataRequest";
  }, never, never, true, never>;
}
/**
 * Scrollable tab list with an animated selection indicator.
 *
 * An Angular Aria tab list: it owns the `tablist` role and the keyboard model. The arrow keys move between tabs, following
 * the text direction and wrapping at either end, Home and End jump to the first and last tab, and focusing a tab selects
 * it. The list keeps the selection on an enabled tab: when the bound value matches no tab, or its tab is disabled or
 * removed, it selects the first enabled tab instead.
 */
export declare class TumUiTabListComponent implements OnDestroy {
  private readonly tabsService;
  private readonly tabList;
  private readonly elementRef;
  /**
   * The rendered tabs, in the order they are shown, for placing the indicator. Only their element and selection are
   * read: the query reports a tab declared inside `@if` or `@for` before its `value` binding has been applied.
   */
  private readonly renderedTabs;
  private resizeObserver?;
  protected readonly indicatorPosition: import("@angular/core").WritableSignal<{
    offset: number;
    width: number;
    animate: boolean;
  }>;
  protected readonly indicatorTransform: import("@angular/core").Signal<string>;
  private indicatorReady;
  constructor();
  ngOnDestroy(): void;
  /**
   * Scrolls a focused tab fully into the list. Aria moves focus with `focus()`, which leaves a tab that is already
   * partly visible where it is, so in a narrow, scrolling list the tab the keyboard reached could stay cut off.
   */
  protected revealFocusedTab(event: FocusEvent): void;
  private updateIndicator;
  private observeLayout;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTabListComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiTabListComponent, "tum-ui-tab-list", never, {}, {}, ["renderedTabs"], ["*"], true, [{
    directive: typeof i1.TabList;
    inputs: {};
    outputs: {};
  }]>;
}
/**
 * Content panel shown when its value matches the containing tabs value.
 *
 * The panel element inside is an Angular Aria tab panel: it owns the `tabpanel` role and `aria-labelledby`, and renders
 * the projected content only while its tab is selected. Set `preserveContent` to keep the content while another tab is
 * selected; the inactive panel then stays hidden and inert.
 */
export declare class TumUiTabPanelComponent implements OnInit, OnDestroy {
  private readonly tabsService;
  private removeFromTabs?;
  /** Value that associates this panel with a tab. */
  readonly value: import("@angular/core").InputSignal<string | number>;
  /** Keeps inactive panel content in the DOM. */
  readonly preserveContent: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  protected readonly key: import("@angular/core").Signal<string>;
  protected readonly active: import("@angular/core").Signal<boolean>;
  ngOnInit(): void;
  ngOnDestroy(): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTabPanelComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiTabPanelComponent, "tum-ui-tab-panel", never, {
    "value": {
      "alias": "value";
      "required": true;
      "isSignal": true;
    };
    "preserveContent": {
      "alias": "preserveContent";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["*"], true, never>;
}
/** Layout container for the panels in a tabs composition. */
export declare class TumUiTabPanelsComponent {
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTabPanelsComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiTabPanelsComponent, "tum-ui-tab-panels", never, {}, {}, never, ["*"], true, never>;
}
/**
 * Selectable tab associated with the panel that has the same value.
 *
 * An Angular Aria tab: it owns the `tab` role, `aria-selected`, `aria-controls`, `aria-disabled`, and the roving
 * `tabindex`. A disabled tab stays focusable with the arrow keys and is announced as unavailable, but cannot be selected.
 */
export declare class TumUiTabComponent extends Tab implements OnInit, OnDestroy {
  private readonly tabsService;
  private removeFromTabs?;
  /** Value that associates this tab with a tab panel. */
  readonly tabValue: import("@angular/core").InputSignal<string | number>;
  /**
   * The key aria identifies this tab by: `tabValue` passed through {@link tabKey}, so `1` and `'1'` stay two tabs.
   *
   * Aria keys a tab by the string in its `value` input, while this tab accepts numbers as well. Overriding `value` with
   * a wider input type would break the type of the aria class for every consumer that checks library types, so this
   * override keeps aria's type and moves the input to an internal name that nobody binds; the tab sets it itself.
   *
   * The override relies on how aria reads the input. Aria builds its tab pattern in a field initializer from a copy of
   * `this`, which still holds aria's own, never bound `value` input, and reads `value` only lazily through
   * `this.value()`, in the tab and panel maps and when looking up the selected tab. If an aria update starts reading
   * `value` from that copy, the tabs lose their panels and selection.
   */
  readonly value: import("@angular/core").ModelSignal<string>;
  constructor();
  protected readonly hostClasses: import("@angular/core").Signal<string>;
  ngOnInit(): void;
  ngOnDestroy(): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTabComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiTabComponent, "tum-ui-tab", never, {
    "tabValue": {
      "alias": "value";
      "required": true;
      "isSignal": true;
    };
    "value": {
      "alias": "tumUiTabKey";
      "required": false;
      "isSignal": true;
    };
  }, {
    "value": "tumUiTabKeyChange";
  }, never, ["*"], true, never>;
}
type TumUiTabValue = number | string | undefined;
/**
 * Angular Aria identifies a tab and its panel by one string. TUM UI accepts numbers and strings and keeps `1` apart from
 * `'1'`, so each value is prefixed with its type. The key never leaves the package: `valueChange` reports the value.
 */
declare function tabKey(value: number | string): string;
/** The value a {@link tabKey} was made from. */
declare function tabValue(key: string): number | string;
/** A panel as its tabs container tracks it. */
interface TumUiTabsPanelEntry {
  readonly key: Signal<string>;
}
/** A tab as its tabs container tracks it. */
interface TumUiTabsTabEntry extends TumUiTabsPanelEntry {
  readonly element: HTMLElement;
  readonly disabled: Signal<boolean>;
  readonly selected: Signal<boolean>;
}
/** Selection state shared by one `tum-ui-tabs` and the tabs and panels inside it. */
declare class TumUiTabsService {
  private readonly source;
  private onSelect;
  private readonly tabSet;
  private readonly panelSet;
  /** The value of the selected tab, as bound on `tum-ui-tabs`. */
  readonly active: Signal<TumUiTabValue>;
  /**
   * The tabs in document order, which is also the order the keyboard moves through them. Sorted on every call, because
   * `@for` reorders tabs by moving their elements rather than by recreating them.
   */
  orderedTabs(): TumUiTabsTabEntry[];
  /**
   * Keys of the tabs that have no panel. A tab list without panels is a supported way to switch a view the host renders
   * itself; the container gives each such tab an empty, hidden placeholder panel, which only keeps aria from reporting
   * a missing panel in development mode.
   */
  readonly keysWithoutPanel: Signal<string[]>;
  register(value: Signal<TumUiTabValue>, onSelect: (value: TumUiTabValue) => void): void;
  select(value: TumUiTabValue): void;
  /**
   * Tracks a tab from its `ngOnInit`, once Angular has applied its bindings: a content query reports a tab declared
   * inside `@if` or `@for` before that, and reading its required `value` then throws NG0950.
   *
   * @returns the function that stops tracking it
   */
  addTab(tab: TumUiTabsTabEntry): () => void;
  /** Tracks a panel from its `ngOnInit`, for the same reason as {@link addTab}. */
  addPanel(panel: TumUiTabsPanelEntry): () => void;
  private track;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTabsService, never>;
  static ɵprov: i0.ɵɵInjectableDeclaration<any>;
}
/**
 * Coordinates an accessible tab list with its associated tab panels.
 *
 * Built on the Angular Aria tabs: `tum-ui-tab-list` is the aria tab list, every `tum-ui-tab` an aria tab, and every
 * `tum-ui-tab-panel` holds an aria tab panel. The panels are optional. Without them the tab list switches a view the host
 * renders itself, bound to `value`.
 */
export declare class TumUiTabsComponent {
  protected readonly tabsService: TumUiTabsService;
  /** Value shared by the active tab and tab panel. */
  readonly value: import("@angular/core").ModelSignal<string | number | undefined>;
  constructor();
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTabsComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiTabsComponent, "tum-ui-tabs", never, {
    "value": {
      "alias": "value";
      "required": false;
      "isSignal": true;
    };
  }, {
    "value": "valueChange";
  }, never, ["*"], true, [{
    directive: typeof i1.Tabs;
    inputs: {};
    outputs: {};
  }]>;
}
type TumUiTagSeverity = 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';
export declare class TumUiTagComponent {
  readonly severity: import("@angular/core").InputSignal<TumUiTagSeverity>;
  readonly value: import("@angular/core").InputSignal<string | undefined>;
  readonly rounded: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  protected readonly tagClasses: import("@angular/core").Signal<string>;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTagComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiTagComponent, "tum-ui-tag", never, {
    "severity": {
      "alias": "severity";
      "required": false;
      "isSignal": true;
    };
    "value": {
      "alias": "value";
      "required": false;
      "isSignal": true;
    };
    "rounded": {
      "alias": "rounded";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, ["*"], true, never>;
}
export declare class TumUiToggleSwitchComponent implements ControlValueAccessor {
  private readonly hostAriaLabel;
  private readonly hostAriaLabelledBy;
  readonly disabled: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly inputId: import("@angular/core").InputSignal<string | undefined>;
  readonly ariaLabel: import("@angular/core").InputSignal<string | undefined>;
  readonly ariaLabelledBy: import("@angular/core").InputSignal<string | undefined>;
  readonly changed: import("@angular/core").OutputEmitterRef<boolean>;
  protected readonly checked: import("@angular/core").WritableSignal<boolean>;
  private readonly cvaDisabled;
  protected readonly effectiveDisabled: import("@angular/core").Signal<boolean>;
  protected readonly effectiveAriaLabel: import("@angular/core").Signal<string | null>;
  protected readonly effectiveAriaLabelledBy: import("@angular/core").Signal<string | null>;
  protected onChange: (value: boolean) => void;
  protected onTouched: () => void;
  protected readonly hostClasses: import("@angular/core").Signal<string>;
  protected onInputChange(event: Event): void;
  protected onInputBlur(): void;
  writeValue(value: boolean): void;
  registerOnChange(fn: (value: boolean) => void): void;
  registerOnTouched(fn: () => void): void;
  setDisabledState(isDisabled: boolean): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiToggleSwitchComponent, never>;
  static ɵcmp: i0.ɵɵComponentDeclaration<TumUiToggleSwitchComponent, "tum-ui-toggle-switch", never, {
    "disabled": {
      "alias": "disabled";
      "required": false;
      "isSignal": true;
    };
    "inputId": {
      "alias": "inputId";
      "required": false;
      "isSignal": true;
    };
    "ariaLabel": {
      "alias": "ariaLabel";
      "required": false;
      "isSignal": true;
    };
    "ariaLabelledBy": {
      "alias": "ariaLabelledBy";
      "required": false;
      "isSignal": true;
    };
  }, {
    "changed": "changed";
  }, never, never, true, never>;
}
/** Tooltip shown on hover or focus and associated with its host through `aria-describedby`. */
export declare class TumUiTooltipDirective implements OnDestroy {
  private readonly overlayService;
  private readonly elementRef;
  /** A plain hint, or several items to render as a bulleted list. */
  readonly content: import("@angular/core").InputSignal<string | readonly string[]>;
  readonly placement: import("@angular/core").InputSignal<TumUiOverlayPlacement>;
  /**
   * Whether the tooltip adds itself to the host's `aria-describedby` while open. Turn this off on a host that is
   * already described by permanent markup carrying the same text, so the description is not announced twice.
   */
  readonly describesHost: import("@angular/core").InputSignalWithTransform<boolean, unknown>;
  readonly showDelayMs: import("@angular/core").InputSignalWithTransform<number, unknown>;
  readonly hideDelayMs: import("@angular/core").InputSignalWithTransform<number, unknown>;
  private readonly text;
  private readonly items;
  private readonly isEmpty;
  private overlayRef?;
  private contentRef?;
  private positionSub?;
  private showTimer?;
  private hideTimer?;
  private readonly tooltipId;
  private interactionSub?;
  private appliedPlacement;
  private triggerHovered;
  private tooltipHovered;
  private focused;
  private pointerFocus;
  constructor();
  protected onHoverStart(): void;
  protected onHoverEnd(): void;
  /** Clicking a trigger focuses it, and focus outlives the pointer — so that focus must not hold the tooltip open. */
  protected onPointerDown(): void;
  protected onFocusStart(): void;
  protected onFocusEnd(): void;
  private scheduleHideIfInactive;
  private scheduleShow;
  private scheduleHide;
  protected hideNow(): void;
  private show;
  /**
   * Points the arrow at the host rather than at the middle of the bubble.
   *
   * The overlay is created with `withPush`, so a bubble that would leave the viewport is shoved sideways: an arrow
   * centred on the bubble then points at empty space beside the host. Measure where the host sits along the bubble's
   * edge and report that instead, clamped so the arrow stays clear of the rounded corners.
   */
  private updateArrowOffset;
  private addDescribedBy;
  private removeDescribedBy;
  ngOnDestroy(): void;
  static ɵfac: i0.ɵɵFactoryDeclaration<TumUiTooltipDirective, never>;
  static ɵdir: i0.ɵɵDirectiveDeclaration<TumUiTooltipDirective, "[tumUiTooltip]", never, {
    "content": {
      "alias": "tumUiTooltip";
      "required": true;
      "isSignal": true;
    };
    "placement": {
      "alias": "tumUiTooltipPlacement";
      "required": false;
      "isSignal": true;
    };
    "describesHost": {
      "alias": "tumUiTooltipDescribesHost";
      "required": false;
      "isSignal": true;
    };
    "showDelayMs": {
      "alias": "showDelayMs";
      "required": false;
      "isSignal": true;
    };
    "hideDelayMs": {
      "alias": "hideDelayMs";
      "required": false;
      "isSignal": true;
    };
  }, {}, never, never, true, never>;
}
export type { CellRendererParams, CellTemplateRef, ColumnDef, TumUiAutoCompleteOptionEvent, TumUiAutoCompleteSearchEvent, TumUiBarChartConfig, TumUiButtonSeverity, TumUiButtonSize, TumUiButtonVariant, TumUiChartAxisConfig, TumUiChartDatumContext, TumUiChartLegendConfig, TumUiChartLegendPosition, TumUiChartSelectEvent, TumUiChartSeries, TumUiChartTooltipConfig, TumUiCheckboxChangeEvent, TumUiChipSize, TumUiConfirmationRequest, TumUiDialogSize, TumUiDoughnutChartConfig, TumUiEmptyStateVariant, TumUiFormFieldContext, TumUiIconFieldPosition, TumUiInputSize, TumUiLineChartConfig, TumUiMessageSeverity, TumUiOverlayPlacement, TumUiProgressBarSeverity, TumUiProgressBarSize, TumUiRadioButtonSelectEvent, TumUiSelectButtonOption, TumUiSelectButtonSize, TumUiSelectSize, TumUiSortDirection, TumUiSortState, TumUiTabValue, TumUiTableQueryEvent, TumUiTableSize, TumUiTableSortEvent, TumUiTagSeverity, TumUiTranslationKey, TumUiTranslationParams, TumUiTranslator };