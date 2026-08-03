import { describe, expect, it } from 'vitest';
import { renderReferencePage, renderReferenceResolver } from '../supporting_scripts/generate-tum-ui-reference-page.mjs';

describe('TUM UI reference page generation', () => {
    it('deduplicates, sorts, and humanizes the documented component titles', () => {
        const page = renderReferencePage({
            entries: {
                button: { id: 'actions-button--docs', type: 'docs', title: 'Actions/Button' },
                intro: { id: 'introduction--docs', type: 'docs', title: 'Introduction' },
                radio: { id: 'forms-radio-button--docs', type: 'docs', title: 'Forms/Radio Button' },
                duplicate: { id: 'actions-button-copy--docs', type: 'docs', title: 'Actions/Button' },
                state: { id: 'forms-radio-button--default', type: 'story', title: 'Forms/Radio Button' },
            },
        });

        expect(page).toContain('### Actions: Button\n\n### Forms: Radio Button');
        expect(page).toContain('<StorybookRedirect />');
        expect(page).not.toContain('### Introduction');
        expect(page.match(/Actions: Button/g)).toHaveLength(1);
        expect(page).toContain('custom_edit_url: null');
    });

    it('generates constant redirect targets from the Storybook index', () => {
        const resolver = renderReferenceResolver({
            entries: {
                button: { id: 'actions-button--docs', type: 'docs', title: 'Actions/Button' },
                radio: { id: 'forms-radio-button--docs', type: 'docs', title: 'Forms/Radio Button' },
            },
        });

        expect(resolver).toContain("case 'actions-button':\n            return 'actions-button--docs';");
        expect(resolver).toContain("case 'forms-radio-button':\n            return 'forms-radio-button--docs';");
        expect(resolver).toContain('default:\n            return DEFAULT_STORY;');
    });

    it.each([undefined, null, [], 'entries'])('rejects an invalid Storybook index: %j', (entries) => {
        expect(() => renderReferencePage({ entries })).toThrow('Storybook index.json does not contain an entries object');
        expect(() => renderReferenceResolver({ entries })).toThrow('Storybook index.json does not contain an entries object');
    });

    it('rejects a documentation id that cannot map to a reference anchor', () => {
        const entries = { button: { id: 'Actions/Button--docs', type: 'docs', title: 'Actions/Button' } };

        expect(() => renderReferencePage({ entries })).toThrow('Unsupported Storybook documentation id: Actions/Button--docs');
    });
});
