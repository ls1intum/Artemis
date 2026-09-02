import { type ApollonLabels, DEFAULT_LABELS } from '@tumaet/apollon';
import { TranslateService } from '@ngx-translate/core';

const APOLLON_TRANSLATION_PREFIX = 'artemisApp.modelingEditor.apollon';

export type ApollonLabelTranslator = Pick<TranslateService, 'instant'>;

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function translate(translator: ApollonLabelTranslator, key: string, params?: Record<string, unknown>): string {
    const value = translator.instant(`${APOLLON_TRANSLATION_PREFIX}.${key}`, params);
    return typeof value === 'string' ? value : `${APOLLON_TRANSLATION_PREFIX}.${key}`;
}

function hasString(tree: Record<string, unknown>, key: string): boolean {
    return typeof tree[key] === 'string';
}

function setTranslatedCallback(labels: Partial<ApollonLabels>, tree: Record<string, unknown>, key: keyof ApollonLabels, callback: unknown): void {
    if (hasString(tree, key)) {
        (labels as Record<string, unknown>)[key] = callback;
    }
}

/**
 * Builds Apollon overrides while retaining callbacks for labels with runtime values.
 *
 * Only locales that actually differ from Apollon carry keys: `DEFAULT_LABELS` is
 * already English, so `i18n/en` deliberately defines none and every English string
 * comes from the library. The translation consistency check explicitly exempts this
 * namespace; mirroring it would create hundreds of duplicates to synchronize with
 * every Apollon release.
 */
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

    setTranslatedCallback(labels, translationTree, 'zoomReadout', (percent: number) => translate(translator, 'zoomReadout', { percent }));
    setTranslatedCallback(labels, translationTree, 'deleteAssessmentFor', (name: string) => translate(translator, 'deleteAssessmentFor', { name }));
    setTranslatedCallback(labels, translationTree, 'assessmentFor', (type: string) => translate(translator, 'assessmentFor', { type }));
    if (hasString(translationTree, 'scrollLockHint')) {
        // Apollon hands us the platform's zoom key already rendered as a cap
        // ('⌘' or 'Ctrl'), so the sentence can put it wherever German wants it.
        labels.scrollLockHint = (modifier) => translate(translator, 'scrollLockHint', { modifier });
    }
    if (isRecord(translationTree.nodeTypes) && hasString(translationTree, 'node')) {
        labels.nodeTypeLabel = (nodeType) => {
            if (!nodeType) {
                return translate(translator, 'node');
            }
            const translated = translate(translator, `nodeTypes.${nodeType}`);
            const translationKey = `${APOLLON_TRANSLATION_PREFIX}.nodeTypes.${nodeType}`;
            return translated === translationKey ? DEFAULT_LABELS.nodeTypeLabel(nodeType) : translated;
        };
    }
    setTranslatedCallback(labels, translationTree, 'editTagsFor', (subject: string) => translate(translator, 'editTagsFor', { subject }));
    setTranslatedCallback(labels, translationTree, 'removeTag', (tag: string) => translate(translator, 'removeTag', { tag }));
    setTranslatedCallback(labels, translationTree, 'deleteMessage', (label: string) => translate(translator, 'deleteMessage', { label }));
    setTranslatedCallback(labels, translationTree, 'switchDirection', (direction: string) => translate(translator, 'switchDirection', { direction }));
    setTranslatedCallback(labels, translationTree, 'switchDirectionFor', (label: string, direction: string) => translate(translator, 'switchDirectionFor', { label, direction }));
    setTranslatedCallback(labels, translationTree, 'messagePlaceholder', (index: number) => translate(translator, 'messagePlaceholder', { index }));
    setTranslatedCallback(labels, translationTree, 'messageFallbackLabel', (index: number) => translate(translator, 'messageFallbackLabel', { index }));
    setTranslatedCallback(labels, translationTree, 'defaultLaneName', (index: number) => translate(translator, 'defaultLaneName', { index }));
    setTranslatedCallback(labels, translationTree, 'multiplicityLabel', (name: string) => translate(translator, 'multiplicityLabel', { name }));
    setTranslatedCallback(labels, translationTree, 'roleLabel', (name: string) => translate(translator, 'roleLabel', { name }));
    setTranslatedCallback(labels, translationTree, 'editColorsFor', (label: string) => translate(translator, 'editColorsFor', { label }));
    setTranslatedCallback(labels, translationTree, 'colorPicker', (label: string) => translate(translator, 'colorPicker', { label }));
    setTranslatedCallback(labels, translationTree, 'stereotypeToggleLabel', (name: string) => translate(translator, 'stereotypeToggleLabel', { name }));
    if (hasString(translationTree, 'stereotypeToggleTooltip') && hasString(translationTree, 'show') && hasString(translationTree, 'hide')) {
        labels.stereotypeToggleTooltip = (shown, name) =>
            translate(translator, 'stereotypeToggleTooltip', {
                action: translate(translator, shown ? 'hide' : 'show'),
                name,
            });
    }

    return labels;
}
