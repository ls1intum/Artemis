import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';

const OVERLAY_STYLES = 'src/main/webapp/content/css/third-party.css';

/** The declarations of the first rule of the stylesheet that has exactly this selector. */
function declarationsOf(css: string, selector: string): Record<string, string> {
    const escaped = selector.replace(/[.*+?^${}()|[\]\\>]/g, '\\$&');
    const rule = new RegExp(`(?:^|\\n)${escaped}\\s*\\{([^}]*)\\}`).exec(css);
    if (!rule) {
        throw new Error(`${OVERLAY_STYLES} has no rule for ${selector}`);
    }
    return Object.fromEntries(
        rule[1]
            .split(';')
            .map((declaration) => declaration.split(':').map((part) => part.trim()))
            .filter(([property, value]) => property && value),
    );
}

describe('layering of the Apollon popovers', () => {
    const css = readFileSync(OVERLAY_STYLES, 'utf8');

    it('paints the portal of a popover opened inside a kit dialog above the overlay container of the dialog', () => {
        // Without a layer of its own the portal is painted behind the dialog, and the pen of a selected class opens a popup nobody can see.
        const overlayContainer = Number(declarationsOf(css, '.cdk-overlay-container')['z-index']);
        const portal = declarationsOf(css, 'body > [data-base-ui-portal]');

        expect(portal['position']).toBe('fixed');
        expect(Number(portal['z-index'])).toBeGreaterThan(overlayContainer);
        // The alert overlay stays on top of everything.
        expect(Number(portal['z-index'])).toBeLessThan(100000);
        expect(portal['pointer-events']).toBe('none');
        expect(declarationsOf(css, 'body > [data-base-ui-portal] > *')['pointer-events']).toBe('auto');
    });
});
