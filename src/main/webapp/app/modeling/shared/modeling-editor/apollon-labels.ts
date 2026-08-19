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

/**
 * Builds Apollon overrides while retaining callbacks for labels with runtime values.
 *
 * Only locales that actually differ from Apollon carry keys: `DEFAULT_LABELS` is
 * already English, so `i18n/en` deliberately defines none and every English string
 * comes from the library. Mirroring them here would be 266 duplicates to keep in
 * sync with every Apollon release.
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

    if (hasString(translationTree, 'zoomReadout')) {
        labels.zoomReadout = (percent) => translate(translator, 'zoomReadout', { percent });
    }
    if (hasString(translationTree, 'deleteAssessmentFor')) {
        labels.deleteAssessmentFor = (name) => translate(translator, 'deleteAssessmentFor', { name });
    }
    if (hasString(translationTree, 'assessmentFor')) {
        labels.assessmentFor = (type) => translate(translator, 'assessmentFor', { type });
    }
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
    if (hasString(translationTree, 'editTagsFor')) {
        labels.editTagsFor = (subject) => translate(translator, 'editTagsFor', { subject });
    }
    if (hasString(translationTree, 'removeTag')) {
        labels.removeTag = (tag) => translate(translator, 'removeTag', { tag });
    }
    if (hasString(translationTree, 'deleteMessage')) {
        labels.deleteMessage = (label) => translate(translator, 'deleteMessage', { label });
    }
    if (hasString(translationTree, 'switchDirection')) {
        labels.switchDirection = (direction) => translate(translator, 'switchDirection', { direction });
    }
    if (hasString(translationTree, 'switchDirectionFor')) {
        labels.switchDirectionFor = (label, direction) => translate(translator, 'switchDirectionFor', { label, direction });
    }
    if (hasString(translationTree, 'messagePlaceholder')) {
        labels.messagePlaceholder = (index) => translate(translator, 'messagePlaceholder', { index });
    }
    if (hasString(translationTree, 'messageFallbackLabel')) {
        labels.messageFallbackLabel = (index) => translate(translator, 'messageFallbackLabel', { index });
    }
    if (hasString(translationTree, 'defaultLaneName')) {
        labels.defaultLaneName = (index) => translate(translator, 'defaultLaneName', { index });
    }
    if (hasString(translationTree, 'multiplicityLabel')) {
        labels.multiplicityLabel = (name) => translate(translator, 'multiplicityLabel', { name });
    }
    if (hasString(translationTree, 'roleLabel')) {
        labels.roleLabel = (name) => translate(translator, 'roleLabel', { name });
    }
    if (hasString(translationTree, 'editColorsFor')) {
        labels.editColorsFor = (label) => translate(translator, 'editColorsFor', { label });
    }
    if (hasString(translationTree, 'colorPicker')) {
        labels.colorPicker = (label) => translate(translator, 'colorPicker', { label });
    }
    if (hasString(translationTree, 'stereotypeToggleLabel')) {
        labels.stereotypeToggleLabel = (name) => translate(translator, 'stereotypeToggleLabel', { name });
    }
    if (hasString(translationTree, 'stereotypeToggleTooltip') && hasString(translationTree, 'show') && hasString(translationTree, 'hide')) {
        labels.stereotypeToggleTooltip = (shown, name) =>
            translate(translator, 'stereotypeToggleTooltip', {
                action: translate(translator, shown ? 'hide' : 'show'),
                name,
            });
    }

    return labels;
}
