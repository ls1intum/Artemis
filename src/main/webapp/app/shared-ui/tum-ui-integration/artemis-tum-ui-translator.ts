import { Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { TumUiTranslationKey, TumUiTranslationParams, TumUiTranslator, provideTumUiTranslator } from '@tumaet/ui-angular';
import { map, merge } from 'rxjs';

const ARTEMIS_TRANSLATION_KEYS = {
    'tumUi.autocomplete.empty': 'global.search.noResultsFound',
    'tumUi.autocomplete.remove': 'entity.action.remove',
    'tumUi.chip.remove': 'entity.action.remove',
    'tumUi.datePicker.clear': 'global.datePicker.clear',
    'tumUi.datePicker.decrementHour': 'global.datePicker.decrementHour',
    'tumUi.datePicker.decrementMinute': 'global.datePicker.decrementMinute',
    'tumUi.datePicker.dialog': 'global.datePicker.dialog',
    'tumUi.datePicker.done': 'global.datePicker.done',
    'tumUi.datePicker.hour': 'global.datePicker.hour',
    'tumUi.datePicker.incrementHour': 'global.datePicker.incrementHour',
    'tumUi.datePicker.incrementMinute': 'global.datePicker.incrementMinute',
    'tumUi.datePicker.invalid': 'global.datePicker.invalid',
    'tumUi.datePicker.invalidTime': 'global.datePicker.invalidTime',
    'tumUi.datePicker.minute': 'global.datePicker.minute',
    'tumUi.datePicker.open': 'global.datePicker.open',
    'tumUi.datePicker.openTime': 'global.datePicker.openTime',
    'tumUi.datePicker.placeholder': 'global.datePicker.placeholder',
    'tumUi.datePicker.nextMonth': 'global.datePicker.nextMonth',
    'tumUi.datePicker.previousMonth': 'global.datePicker.previousMonth',
    'tumUi.datePicker.time': 'global.datePicker.time',
    'tumUi.datePicker.timeDialog': 'global.datePicker.timeDialog',
    'tumUi.datePicker.timePlaceholder': 'global.datePicker.timePlaceholder',
    'tumUi.datePicker.timeZoneWarning': 'entity.timeZoneWarning',
    'tumUi.dialog.close': 'entity.action.close',
    'tumUi.panel.collapse': 'global.generic.collapse',
    'tumUi.panel.expand': 'global.generic.expand',
    'tumUi.paginator.ariaLabel': 'global.paginator.ariaLabel',
    'tumUi.paginator.currentPageReport': 'global.item-count',
    'tumUi.paginator.first': 'global.paginator.first',
    'tumUi.paginator.last': 'global.paginator.last',
    'tumUi.paginator.next': 'global.paginator.next',
    'tumUi.paginator.previous': 'global.paginator.previous',
    'tumUi.paginator.rowsPerPage': 'global.paginator.rowsPerPage',
    'tumUi.select.clear': 'entity.action.clear',
    'tumUi.select.empty': 'global.generic.emptyList',
    'tumUi.select.filter': 'global.generic.filterOptions',
    'tumUi.select.noResults': 'global.generic.noMatchingOptions',
    'tumUi.table.actions': 'entity.actions',
    'tumUi.table.noResults': 'global.search.noResultsFound',
    'tumUi.table.searchPlaceholder': 'global.search.searchPlaceholder',
} as const satisfies Readonly<Record<TumUiTranslationKey, string>>;

class ArtemisTumUiTranslator implements TumUiTranslator {
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

    translate(key: string, params?: TumUiTranslationParams): string {
        const mappedKey = key in ARTEMIS_TRANSLATION_KEYS ? ARTEMIS_TRANSLATION_KEYS[key as TumUiTranslationKey] : key;
        const translation = this.translateService.instant(mappedKey, params);
        return typeof translation === 'string' ? translation : key;
    }
}

export function provideArtemisTumUiTranslator() {
    return provideTumUiTranslator(ArtemisTumUiTranslator);
}
