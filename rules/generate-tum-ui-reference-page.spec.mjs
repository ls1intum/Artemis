import { describe, expect, it } from 'vitest';
import { renderReferencePage } from '../supporting_scripts/generate-tum-ui-reference-page.mjs';

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
        expect(page).toContain('"actions-button": "actions-button--docs"');
        expect(page).toContain('"forms-radio-button": "forms-radio-button--docs"');
        expect(page).not.toContain('### Introduction');
        expect(page.match(/Actions: Button/g)).toHaveLength(1);
        expect(page).toContain('custom_edit_url: null');
    });

    it.each([undefined, null, [], 'entries'])('rejects an invalid Storybook index: %j', (entries) => {
        expect(() => renderReferencePage({ entries })).toThrow('Storybook index.json does not contain an entries object');
    });
});
