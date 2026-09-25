import { EnvironmentProviders, InjectionToken, Signal, Type, makeEnvironmentProviders } from '@angular/core';

export type TumAetUiTranslationParams = Readonly<Record<string, number | string>>;

export const TUM_AET_UI_DEFAULT_TRANSLATIONS = {
    'tumAetUi.autocomplete.empty': 'No results found',
    'tumAetUi.autocomplete.remove': 'Remove',
    'tumAetUi.chip.remove': 'Remove',
    'tumAetUi.datePicker.timeZoneWarning': 'The displayed date and time use the {timeZone} time zone.',
    'tumAetUi.datePicker.clear': 'Clear date',
    'tumAetUi.datePicker.decrementHour': 'Decrement hour',
    'tumAetUi.datePicker.decrementMinute': 'Decrement minute',
    'tumAetUi.datePicker.dialog': 'Choose date and time',
    'tumAetUi.datePicker.done': 'Done',
    'tumAetUi.datePicker.hour': 'Hour',
    'tumAetUi.datePicker.incrementHour': 'Increment hour',
    'tumAetUi.datePicker.incrementMinute': 'Increment minute',
    'tumAetUi.datePicker.invalid': 'Enter a valid date and time.',
    'tumAetUi.datePicker.invalidTime': 'Enter a valid time.',
    'tumAetUi.datePicker.minute': 'Minute',
    'tumAetUi.datePicker.open': 'Open calendar',
    'tumAetUi.datePicker.openTime': 'Open clock',
    'tumAetUi.datePicker.placeholder': 'DD.MM.YYYY HH:mm',
    'tumAetUi.datePicker.nextMonth': 'Next month: {month}',
    'tumAetUi.datePicker.previousMonth': 'Previous month: {month}',
    'tumAetUi.datePicker.time': 'Time',
    'tumAetUi.datePicker.timeDialog': 'Choose time',
    'tumAetUi.datePicker.timePlaceholder': 'HH:mm',
    'tumAetUi.dialog.close': 'Close',
    'tumAetUi.panel.collapse': 'Collapse',
    'tumAetUi.panel.expand': 'Expand',
    'tumAetUi.paginator.ariaLabel': 'Pagination',
    'tumAetUi.paginator.currentPageReport': 'Showing {first} to {second} of {total}',
    'tumAetUi.paginator.first': 'First page',
    'tumAetUi.paginator.last': 'Last page',
    'tumAetUi.paginator.next': 'Next page',
    'tumAetUi.paginator.previous': 'Previous page',
    'tumAetUi.paginator.rowsPerPage': 'Rows per page',
    'tumAetUi.searchField.clear': 'Clear search',
    'tumAetUi.searchField.placeholder': 'Search',
    'tumAetUi.select.clear': 'Clear selection',
    'tumAetUi.select.empty': 'No available options',
    'tumAetUi.select.filter': 'Filter options',
    'tumAetUi.select.noResults': 'No matching options',
    'tumAetUi.step.pending': 'Not started',
    'tumAetUi.step.current': 'In progress',
    'tumAetUi.step.complete': 'Done',
    'tumAetUi.step.failed': 'Failed',
    'tumAetUi.step.skipped': 'Skipped',
    'tumAetUi.table.actions': 'Actions',
    'tumAetUi.table.noResults': 'No results found',
    'tumAetUi.table.searchPlaceholder': 'Search',
} as const satisfies Readonly<Record<string, string>>;

export type TumAetUiTranslationKey = keyof typeof TUM_AET_UI_DEFAULT_TRANSLATIONS;

export interface TumAetUiTranslator {
    /** Optional signal that changes whenever the active translation catalog changes. */
    readonly translationChanges?: Signal<unknown>;
    /** Optional locale passed to locale-sensitive browser formatting APIs. */
    readonly locale?: Signal<string | undefined>;

    translate(key: string, params?: TumAetUiTranslationParams): string;
}

function interpolate(template: string, params?: TumAetUiTranslationParams): string {
    if (!params) {
        return template;
    }
    return template.replace(/\{(\w+)\}/g, (match, name: string) => String(params[name] ?? match));
}

const defaultTranslator: TumAetUiTranslator = {
    translate: (key, params) => interpolate(TUM_AET_UI_DEFAULT_TRANSLATIONS[key as TumAetUiTranslationKey] ?? key, params),
};

export const TUM_AET_UI_TRANSLATOR = new InjectionToken<TumAetUiTranslator>('TUM_AET_UI_TRANSLATOR', {
    providedIn: 'root',
    factory: () => defaultTranslator,
});

export function provideTumAetUiTranslator(translator: Type<TumAetUiTranslator>): EnvironmentProviders {
    return makeEnvironmentProviders([{ provide: TUM_AET_UI_TRANSLATOR, useClass: translator }]);
}
