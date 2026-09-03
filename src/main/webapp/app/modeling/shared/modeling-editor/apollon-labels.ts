import { type ApollonLabels, DEFAULT_LABELS } from '@tumaet/apollon';
import { TranslateService } from '@ngx-translate/core';

const APOLLON_TRANSLATION_PREFIX = 'artemisApp.modelingEditor.apollon';

export type ApollonLabelTranslator = Pick<TranslateService, 'instant'>;

const CALLBACK_PARAMETERS = {
    zoomReadout: ['percent'],
    scrollLockHint: ['modifier'],
    deleteAssessmentFor: ['name'],
    assessmentFor: ['type'],
    editTagsFor: ['subject'],
    removeTag: ['tag'],
    deleteMessage: ['label'],
    switchDirection: ['direction'],
    switchDirectionFor: ['label', 'direction'],
    messagePlaceholder: ['index'],
    messageFallbackLabel: ['index'],
    defaultLaneName: ['index'],
    multiplicityLabel: ['name'],
    roleLabel: ['name'],
    editColorsFor: ['label'],
    colorPicker: ['label'],
    stereotypeToggleLabel: ['name'],
} as const satisfies Partial<Record<keyof ApollonLabels, readonly string[]>>;

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function translate(translator: ApollonLabelTranslator, key: string, params?: Record<string, unknown>): string {
    const value = translator.instant(`${APOLLON_TRANSLATION_PREFIX}.${key}`, params);
    return typeof value === 'string' ? value : `${APOLLON_TRANSLATION_PREFIX}.${key}`;
}

function createTranslatedCallback(translator: ApollonLabelTranslator, key: string, parameters: readonly string[]): (...values: unknown[]) => string {
    return (...values) => translate(translator, key, Object.fromEntries(parameters.map((parameter, index) => [parameter, values[index]])));
}

export function createApollonLabels(translator: ApollonLabelTranslator): Partial<ApollonLabels> {
    const translationTree = translator.instant(APOLLON_TRANSLATION_PREFIX);
    if (!isRecord(translationTree)) {
        return {};
    }

    const labels: Partial<ApollonLabels> = {};
    for (const [key, value] of Object.entries(translationTree)) {
        if (key in DEFAULT_LABELS && typeof value === 'string' && typeof DEFAULT_LABELS[key as keyof ApollonLabels] === 'string') {
            (labels as Record<string, unknown>)[key] = value;
        }
    }

    for (const [key, parameters] of Object.entries(CALLBACK_PARAMETERS)) {
        if (typeof translationTree[key] === 'string') {
            (labels as Record<string, unknown>)[key] = createTranslatedCallback(translator, key, parameters);
        }
    }
    if (isRecord(translationTree.nodeTypes) && typeof translationTree.node === 'string') {
        labels.nodeTypeLabel = (nodeType) => {
            if (!nodeType) {
                return translate(translator, 'node');
            }
            const translated = translate(translator, `nodeTypes.${nodeType}`);
            const translationKey = `${APOLLON_TRANSLATION_PREFIX}.nodeTypes.${nodeType}`;
            return translated === translationKey ? DEFAULT_LABELS.nodeTypeLabel(nodeType) : translated;
        };
    }
    if (['stereotypeToggleTooltip', 'show', 'hide'].every((key) => typeof translationTree[key] === 'string')) {
        labels.stereotypeToggleTooltip = (shown, name) =>
            translate(translator, 'stereotypeToggleTooltip', {
                action: translate(translator, shown ? 'hide' : 'show'),
                name,
            });
    }

    return labels;
}
