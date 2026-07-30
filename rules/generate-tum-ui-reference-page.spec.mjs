import { describe, expect, it } from 'vitest';
import { renderReferencePage } from '../supporting_scripts/generate-tum-ui-reference-page.mjs';

describe('TUM UI reference page generation', () => {
    it('deduplicates, sorts, and humanizes the documented component titles', () => {
        const page = renderReferencePage({
            entries: {
                button: { type: 'docs', title: 'Actions/Button' },
                intro: { type: 'docs', title: 'Introduction' },
                radio: { type: 'docs', title: 'Forms/Radio Button' },
                duplicate: { type: 'docs', title: 'Actions/Button' },
                state: { type: 'story', title: 'Forms/Radio Button' },
            },
        });

        expect(page).toContain('- Actions — Button\n- Forms — Radio Button');
        expect(page).not.toContain('- Introduction');
        expect(page.match(/Actions — Button/g)).toHaveLength(1);
    });

    it.each([undefined, null, [], 'entries'])('rejects an invalid Storybook index: %j', (entries) => {
        expect(() => renderReferencePage({ entries })).toThrow('Storybook index.json does not contain an entries object');
    });
});
