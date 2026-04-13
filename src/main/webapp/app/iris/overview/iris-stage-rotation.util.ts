import { DestroyRef, WritableSignal, signal, untracked } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';

const ROTATION_KEYS = ['artemisApp.iris.stages.thinking', 'artemisApp.iris.stages.analyzing', 'artemisApp.iris.stages.processing', 'artemisApp.iris.stages.formulating'];

const ROTATION_INTERVAL_MS = 2600;

export function translateLabel(translateService: TranslateService, key: string): string {
    const translated = translateService.instant(key);
    return typeof translated === 'string' && translated.startsWith('translation-not-found[') ? key : translated;
}

/**
 * Shared rotation/display-name logic for Iris stage indicators.
 *
 * Returns `displayName` and `animToggle` signals plus an `update` function
 * that should be called reactively (inside an `effect`) whenever the active
 * stage or locale changes.
 *
 * The caller is responsible for calling `update()` inside an effect that
 * tracks the relevant dependencies (activeStage, currentLocale).
 */
export function createStageRotation(translateService: TranslateService, destroyRef: DestroyRef) {
    const displayName: WritableSignal<string> = signal('');
    const animToggle: WritableSignal<boolean> = signal(false);

    let rotationIntervalId: ReturnType<typeof setInterval> | undefined;
    let rotationIndex = 0;

    function translate(key: string): string {
        return translateLabel(translateService, key);
    }

    function update(stage: IrisStageDTO | undefined): void {
        const name = stage?.message || '';
        const translated = name ? translate(name) : '';

        if (translated) {
            const currentDisplay = untracked(() => displayName());
            const isAlreadyRotating = rotationIntervalId !== undefined;

            if (isAlreadyRotating && translated !== currentDisplay) {
                clearInterval(rotationIntervalId);
                rotationIntervalId = undefined;
            }

            const isRotating = rotationIntervalId !== undefined;

            if (translated !== currentDisplay && !isRotating) {
                displayName.set(translated);
                animToggle.update((v) => !v);
            }
        }

        const shouldRotate = stage?.state === IrisStageStateDTO.IN_PROGRESS;
        const isRotating = rotationIntervalId !== undefined;

        if (shouldRotate && !isRotating) {
            if (!translated) {
                const firstLabel = translate(ROTATION_KEYS[0]);
                displayName.set(firstLabel);
                animToggle.update((v) => !v);
            }
            rotationIndex = 0;
            rotationIntervalId = setInterval(() => {
                rotationIndex = (rotationIndex + 1) % ROTATION_KEYS.length;
                const rotated = translate(ROTATION_KEYS[rotationIndex]);
                displayName.set(rotated);
                animToggle.update((v) => !v);
            }, ROTATION_INTERVAL_MS);
        } else if (!shouldRotate && isRotating) {
            clearInterval(rotationIntervalId);
            rotationIntervalId = undefined;
        } else if (!shouldRotate && !translated) {
            displayName.set('');
        }
    }

    destroyRef.onDestroy(() => {
        if (rotationIntervalId) {
            clearInterval(rotationIntervalId);
        }
    });

    return { displayName, animToggle, update };
}
