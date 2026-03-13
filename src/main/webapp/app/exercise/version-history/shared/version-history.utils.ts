import { TranslateService } from '@ngx-translate/core';

/** Returns a translated "Yes"/"No" label, or `undefined` when the value is not set. */
export function booleanLabel(translateService: TranslateService, value?: boolean): string | undefined {
    if (value === undefined) {
        return undefined;
    }
    return value ? translateService.instant('artemisApp.exercise.yes') : translateService.instant('artemisApp.exercise.no');
}
