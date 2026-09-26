import { Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiTranslationKey, TumAetUiTranslationParams, TumAetUiTranslator, provideTumAetUiTranslator } from '@tumaet/ui-angular';
import { map, merge } from 'rxjs';

const ARTEMIS_TRANSLATION_KEYS = {
    'tumAetUi.autocomplete.empty': 'global.search.noResultsFound',
    'tumAetUi.autocomplete.remove': 'entity.action.remove',
    'tumAetUi.chip.remove': 'entity.action.remove',
    'tumAetUi.datePicker.clear': 'global.datePicker.clear',
    'tumAetUi.datePicker.decrementHour': 'global.datePicker.decrementHour',
    'tumAetUi.datePicker.decrementMinute': 'global.datePicker.decrementMinute',
    'tumAetUi.datePicker.dateDialog': 'global.datePicker.dateDialog',
    'tumAetUi.datePicker.dialog': 'global.datePicker.dialog',
    'tumAetUi.datePicker.done': 'global.datePicker.done',
    'tumAetUi.datePicker.hour': 'global.datePicker.hour',
    'tumAetUi.datePicker.incrementHour': 'global.datePicker.incrementHour',
    'tumAetUi.datePicker.incrementMinute': 'global.datePicker.incrementMinute',
    'tumAetUi.datePicker.invalid': 'global.datePicker.invalid',
    'tumAetUi.datePicker.invalidDate': 'global.datePicker.invalidDate',
    'tumAetUi.datePicker.invalidTime': 'global.datePicker.invalidTime',
    'tumAetUi.datePicker.minute': 'global.datePicker.minute',
    'tumAetUi.datePicker.open': 'global.datePicker.open',
    'tumAetUi.datePicker.openTime': 'global.datePicker.openTime',
    'tumAetUi.datePicker.datePlaceholder': 'global.datePicker.datePlaceholder',
    'tumAetUi.datePicker.placeholder': 'global.datePicker.placeholder',
    'tumAetUi.datePicker.nextMonth': 'global.datePicker.nextMonth',
    'tumAetUi.datePicker.previousMonth': 'global.datePicker.previousMonth',
    'tumAetUi.datePicker.time': 'global.datePicker.time',
    'tumAetUi.datePicker.timeDialog': 'global.datePicker.timeDialog',
    'tumAetUi.datePicker.timePlaceholder': 'global.datePicker.timePlaceholder',
    'tumAetUi.datePicker.timeZoneWarning': 'entity.timeZoneWarning',
    'tumAetUi.dialog.close': 'entity.action.close',
    'tumAetUi.panel.collapse': 'global.generic.collapse',
    'tumAetUi.panel.expand': 'global.generic.expand',
    'tumAetUi.paginator.ariaLabel': 'global.paginator.ariaLabel',
    'tumAetUi.paginator.currentPageReport': 'global.item-count',
    'tumAetUi.paginator.first': 'global.paginator.first',
    'tumAetUi.paginator.last': 'global.paginator.last',
    'tumAetUi.paginator.next': 'global.paginator.next',
    'tumAetUi.paginator.previous': 'global.paginator.previous',
    'tumAetUi.paginator.rowsPerPage': 'global.paginator.rowsPerPage',
    'tumAetUi.searchField.clear': 'entity.action.clear',
    'tumAetUi.searchField.placeholder': 'global.search.searchPlaceholder',
    'tumAetUi.select.clear': 'entity.action.clear',
    'tumAetUi.select.empty': 'global.generic.emptyList',
    'tumAetUi.select.filter': 'global.generic.filterOptions',
    'tumAetUi.select.noResults': 'global.generic.noMatchingOptions',
    'tumAetUi.table.actions': 'entity.actions',
    'tumAetUi.table.noResults': 'global.search.noResultsFound',
    'tumAetUi.table.searchPlaceholder': 'global.search.searchPlaceholder',
} as const satisfies Readonly<Record<TumAetUiTranslationKey, string>>;

class ArtemisTumAetUiTranslator implements TumAetUiTranslator {
    private readonly translateService = inject(TranslateService);

    readonly translationChanges: Signal<unknown> = toSignal(
        merge(this.translateService.onLangChange, this.translateService.onTranslationChange, this.translateService.onFallbackLangChange),
        {
            initialValue: undefined,
        },
    );
    readonly locale: Signal<string | undefined> = toSignal(this.translateService.onLangChange.pipe(map((event) => event.lang as string | undefined)), {
        initialValue: this.translateService.getCurrentLang() ?? undefined,
    });

    translate(key: string, params?: TumAetUiTranslationParams): string {
        const mappedKey = key in ARTEMIS_TRANSLATION_KEYS ? ARTEMIS_TRANSLATION_KEYS[key as TumAetUiTranslationKey] : key;
        const translation = this.translateService.instant(mappedKey, params);
        return typeof translation === 'string' ? translation : key;
    }
}

export function provideArtemisTumAetUiTranslator() {
    return provideTumAetUiTranslator(ArtemisTumAetUiTranslator);
}
