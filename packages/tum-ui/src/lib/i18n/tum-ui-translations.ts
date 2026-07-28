import { EnvironmentProviders, InjectionToken, Signal, Type, makeEnvironmentProviders } from '@angular/core';

export type TumUiTranslationParams = Readonly<Record<string, number | string>>;

/**
 * Host-provided translation boundary for TUM UI.
 *
 * Keeping this contract inside the package avoids coupling reusable components to a particular
 * application or i18n framework. Hosts can bridge ngx-translate, Transloco, Angular's built-in i18n,
 * or any other translation system without changing the component library.
 */
export interface TumUiTranslator {
    /**
     * Optional reactive invalidation signal. The translation pipe reads it before translating so
     * zoneless hosts are refreshed when the active language or loaded catalog changes.
     */
    readonly changes?: Signal<unknown>;
    readonly locale?: Signal<string | undefined>;

    translate(key: string, params?: TumUiTranslationParams): string;
}

const defaultTranslations: Readonly<Record<string, string>> = {
    'tumUi.datePicker.timeZoneWarning': 'The displayed date and time use the {timeZone} time zone.',
    'tumUi.datePicker.clear': 'Clear date',
    'tumUi.datePicker.decrementHour': 'Decrement hour',
    'tumUi.datePicker.decrementMinute': 'Decrement minute',
    'tumUi.datePicker.done': 'Done',
    'tumUi.datePicker.hour': 'Hour',
    'tumUi.datePicker.incrementHour': 'Increment hour',
    'tumUi.datePicker.incrementMinute': 'Increment minute',
    'tumUi.datePicker.invalid': 'Enter a valid date and time.',
    'tumUi.datePicker.minute': 'Minute',
    'tumUi.datePicker.open': 'Open calendar',
    'tumUi.datePicker.nextMonth': 'Next month: {month}',
    'tumUi.datePicker.previousMonth': 'Previous month: {month}',
    'tumUi.datePicker.time': 'Time',
    'tumUi.paginator.currentPageReport': 'Showing {first} to {second} of {total}',
    'tumUi.paginator.first': 'First page',
    'tumUi.paginator.last': 'Last page',
    'tumUi.paginator.next': 'Next page',
    'tumUi.paginator.previous': 'Previous page',
    'tumUi.paginator.rowsPerPage': 'Rows per page',
    'tumUi.table.noResults': 'No results found',
    'tumUi.table.searchPlaceholder': 'Search',
};

function interpolate(template: string, params?: TumUiTranslationParams): string {
    if (!params) {
        return template;
    }
    return template.replace(/\{(\w+)\}/g, (match, name: string) => String(params[name] ?? match));
}

const defaultTranslator: TumUiTranslator = {
    translate: (key, params) => interpolate(defaultTranslations[key] ?? key, params),
};

/**
 * Translation adapter token for all package-owned text.
 *
 * The English fallback makes the package functional in isolated consumers such as Storybook while
 * applications can override the token once at bootstrap to use their own translation service.
 */
export const TUM_UI_TRANSLATOR = new InjectionToken<TumUiTranslator>('TUM_UI_TRANSLATOR', {
    providedIn: 'root',
    factory: () => defaultTranslator,
});

/**
 * Configures a host translation adapter at application bootstrap.
 *
 * The adapter is provided by type so it can use Angular dependency injection for the host's
 * translation service.
 */
export function provideTumUiTranslator(translator: Type<TumUiTranslator>): EnvironmentProviders {
    return makeEnvironmentProviders([{ provide: TUM_UI_TRANSLATOR, useClass: translator }]);
}
