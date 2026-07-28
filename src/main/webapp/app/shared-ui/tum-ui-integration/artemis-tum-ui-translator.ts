import { Signal, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { TumUiTranslationParams, TumUiTranslator, provideTumUiTranslator } from '@tumaet/ui-angular';
import { map, merge } from 'rxjs';

const ARTEMIS_TRANSLATION_KEYS: Readonly<Record<string, string>> = {
    'tumUi.datePicker.clear': 'global.datePicker.clear',
    'tumUi.datePicker.decrementHour': 'global.datePicker.decrementHour',
    'tumUi.datePicker.decrementMinute': 'global.datePicker.decrementMinute',
    'tumUi.datePicker.done': 'global.datePicker.done',
    'tumUi.datePicker.hour': 'global.datePicker.hour',
    'tumUi.datePicker.incrementHour': 'global.datePicker.incrementHour',
    'tumUi.datePicker.incrementMinute': 'global.datePicker.incrementMinute',
    'tumUi.datePicker.invalid': 'global.datePicker.invalid',
    'tumUi.datePicker.minute': 'global.datePicker.minute',
    'tumUi.datePicker.open': 'global.datePicker.open',
    'tumUi.datePicker.nextMonth': 'global.datePicker.nextMonth',
    'tumUi.datePicker.previousMonth': 'global.datePicker.previousMonth',
    'tumUi.datePicker.time': 'global.datePicker.time',
    'tumUi.datePicker.timeZoneWarning': 'entity.timeZoneWarning',
    'tumUi.paginator.currentPageReport': 'global.item-count',
    'tumUi.paginator.first': 'global.paginator.first',
    'tumUi.paginator.last': 'global.paginator.last',
    'tumUi.paginator.next': 'global.paginator.next',
    'tumUi.paginator.previous': 'global.paginator.previous',
    'tumUi.paginator.rowsPerPage': 'global.paginator.rowsPerPage',
    'tumUi.table.noResults': 'artemisApp.dataTable.search.noResults',
    'tumUi.table.searchPlaceholder': 'artemisApp.course.exercise.search.searchPlaceholder',
};

class ArtemisTumUiTranslator implements TumUiTranslator {
    private readonly translateService = inject(TranslateService);

    readonly changes: Signal<unknown> = toSignal(merge(this.translateService.onLangChange, this.translateService.onTranslationChange, this.translateService.onFallbackLangChange), {
        initialValue: undefined,
    });
    readonly locale: Signal<string | undefined> = toSignal(this.translateService.onLangChange.pipe(map((event) => event.lang as string | undefined)), {
        initialValue: this.translateService.getCurrentLang() ?? undefined,
    });

    translate(key: string, params?: TumUiTranslationParams): string {
        const translation = this.translateService.instant(ARTEMIS_TRANSLATION_KEYS[key] ?? key, params);
        return typeof translation === 'string' ? translation : key;
    }
}

/** Connects the framework-neutral TUM UI translation contract to Artemis' ngx-translate service. */
export function provideArtemisTumUiTranslator() {
    return provideTumUiTranslator(ArtemisTumUiTranslator);
}
