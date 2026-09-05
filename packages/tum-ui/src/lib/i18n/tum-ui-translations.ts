import { EnvironmentProviders, InjectionToken, Signal, Type, makeEnvironmentProviders } from '@angular/core';

export type TumUiTranslationParams = Readonly<Record<string, number | string>>;

export const TUM_UI_DEFAULT_TRANSLATIONS = {
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
    'tumUi.datePicker.minute': 'Minute',
    'tumUi.datePicker.open': 'Open calendar',
    'tumUi.datePicker.placeholder': 'DD.MM.YYYY HH:mm',
    'tumUi.datePicker.nextMonth': 'Next month: {month}',
    'tumUi.datePicker.previousMonth': 'Previous month: {month}',
    'tumUi.datePicker.time': 'Time',
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
    'tumUi.select.clear': 'Clear selection',
    'tumUi.select.empty': 'No available options',
    'tumUi.select.filter': 'Filter options',
    'tumUi.select.noResults': 'No matching options',
    'tumUi.table.actions': 'Actions',
    'tumUi.table.noResults': 'No results found',
    'tumUi.table.searchPlaceholder': 'Search',
} as const satisfies Readonly<Record<string, string>>;

export type TumUiTranslationKey = keyof typeof TUM_UI_DEFAULT_TRANSLATIONS;

export interface TumUiTranslator {
    /** Optional signal that changes whenever the active translation catalog changes. */
    readonly translationChanges?: Signal<unknown>;
    /** Optional locale passed to locale-sensitive browser formatting APIs. */
    readonly locale?: Signal<string | undefined>;

    translate(key: string, params?: TumUiTranslationParams): string;
}

function interpolate(template: string, params?: TumUiTranslationParams): string {
    if (!params) {
        return template;
    }
    return template.replace(/\{(\w+)\}/g, (match, name: string) => String(params[name] ?? match));
}

const defaultTranslator: TumUiTranslator = {
    translate: (key, params) => interpolate(TUM_UI_DEFAULT_TRANSLATIONS[key as TumUiTranslationKey] ?? key, params),
};

export const TUM_UI_TRANSLATOR = new InjectionToken<TumUiTranslator>('TUM_UI_TRANSLATOR', {
    providedIn: 'root',
    factory: () => defaultTranslator,
});

export function provideTumUiTranslator(translator: Type<TumUiTranslator>): EnvironmentProviders {
    return makeEnvironmentProviders([{ provide: TUM_UI_TRANSLATOR, useClass: translator }]);
}
