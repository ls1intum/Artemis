import { DEFAULT_LABELS } from '@tumaet/apollon';
import germanModelingEditor from '../../../../i18n/de/modelingEditor.json';
import { type ApollonLabelTranslator, createApollonLabels } from 'app/modeling/shared/modeling-editor/apollon-labels';
import { describe, expect, it } from 'vitest';

const TRANSLATION_PREFIX = 'artemisApp.modelingEditor.apollon';

function translatorFor(tree: Record<string, unknown>): ApollonLabelTranslator {
    return {
        instant(key: string, params?: Record<string, unknown>): unknown {
            if (key === TRANSLATION_PREFIX) {
                return tree;
            }

            const path = key.replace(`${TRANSLATION_PREFIX}.`, '').split('.');
            let value: unknown = tree;
            for (const segment of path) {
                value = typeof value === 'object' && value !== null ? (value as Record<string, unknown>)[segment] : undefined;
            }

            if (typeof value !== 'string') {
                return key;
            }

            return value.replace(/{{\s*(\w+)\s*}}/g, (_, parameter: string) => String(params?.[parameter] ?? ''));
        },
    };
}

describe('createApollonLabels', () => {
    it('bridges static, interpolated, and node-type labels', () => {
        const translator = translatorFor({
            zoomIn: 'Vergrößern',
            zoomReadout: 'Zoom: {{ percent }} %',
            node: 'Knoten',
            nodeTypes: { class: 'Klasse' },
            stereotypeToggleTooltip: '{{ action }} {{ name }}',
            show: 'anzeigen',
            hide: 'ausblenden',
        });

        const labels = createApollonLabels(translator);

        expect(labels.zoomIn).toBe('Vergrößern');
        expect(labels.zoomReadout?.(125)).toBe('Zoom: 125 %');
        expect(labels.nodeTypeLabel?.('class')).toBe('Klasse');
        expect(labels.nodeTypeLabel?.('unknownNodeType')).toBe(DEFAULT_LABELS.nodeTypeLabel('unknownNodeType'));
        expect(labels.stereotypeToggleTooltip?.(true, 'abstract')).toBe('ausblenden abstract');
        expect(labels.stereotypeToggleTooltip?.(false, 'abstract')).toBe('anzeigen abstract');
    });

    it('leaves missing labels undefined so Apollon can apply its English defaults', () => {
        const labels = createApollonLabels(translatorFor({ zoomIn: 'Vergrößern' }));

        expect(labels.zoomIn).toBe('Vergrößern');
        expect(labels.zoomReadout).toBeUndefined();
        expect(labels.nodeTypeLabel).toBeUndefined();
    });

    it('returns no overrides when the Artemis locale has no Apollon namespace', () => {
        const translator = {
            instant: () => TRANSLATION_PREFIX,
        };

        expect(createApollonLabels(translator)).toEqual({});
    });

    it('keeps the German overrides and every function adapter complete for the installed Apollon release', () => {
        const table = germanModelingEditor.artemisApp.modelingEditor.apollon as Record<string, unknown>;
        const labels = createApollonLabels(translatorFor(table));
        const adapterCases = [
            ['zoomReadout', [137], ['137']],
            ['deleteAssessmentFor', ['DELETE_NAME'], ['DELETE_NAME']],
            ['assessmentFor', ['ASSESSMENT_TYPE'], ['ASSESSMENT_TYPE']],
            ['editTagsFor', ['TAG_SUBJECT'], ['TAG_SUBJECT']],
            ['removeTag', ['TAG_NAME'], ['TAG_NAME']],
            ['deleteMessage', ['MESSAGE_LABEL'], ['MESSAGE_LABEL']],
            ['switchDirection', ['DIRECTION'], ['DIRECTION']],
            ['switchDirectionFor', ['EDGE_LABEL', 'DIRECTION'], ['EDGE_LABEL', 'DIRECTION']],
            ['messagePlaceholder', [17], ['17']],
            ['messageFallbackLabel', [19], ['19']],
            ['defaultLaneName', [23], ['23']],
            ['multiplicityLabel', ['MULTIPLICITY_NAME'], ['MULTIPLICITY_NAME']],
            ['roleLabel', ['ROLE_NAME'], ['ROLE_NAME']],
            ['editColorsFor', ['COLOR_SUBJECT'], ['COLOR_SUBJECT']],
            ['colorPicker', ['COLOR_LABEL'], ['COLOR_LABEL']],
            ['stereotypeToggleLabel', ['STEREOTYPE_NAME'], ['STEREOTYPE_NAME']],
            ['stereotypeToggleTooltip', [false, 'TOOLTIP_NAME'], ['TOOLTIP_NAME']],
            ['scrollLockHint', ['MODIFIER_CAP'], ['MODIFIER_CAP']],
        ] as const;

        for (const [key, defaultLabel] of Object.entries(DEFAULT_LABELS)) {
            if (key === 'nodeTypeLabel') {
                expect(labels.nodeTypeLabel?.('class')).toBe('Klasse');
            } else if (typeof defaultLabel !== 'function') {
                expect(labels[key as keyof typeof labels], `missing German Apollon label: ${key}`).toBeTypeOf('string');
            }
        }

        const expectedFunctionKeys = Object.entries(DEFAULT_LABELS)
            .filter(([, value]) => typeof value === 'function')
            .map(([key]) => key)
            .sort();
        expect([...adapterCases.map(([key]) => key), 'nodeTypeLabel'].sort()).toEqual(expectedFunctionKeys);
        for (const [key, args, expectedFragments] of adapterCases) {
            const adapter = labels[key] as (...values: unknown[]) => string;
            const result = adapter(...args);
            for (const fragment of expectedFragments) {
                expect(result, `${key} dropped interpolation value ${fragment}`).toContain(`${fragment}`);
            }
        }
    });
});
