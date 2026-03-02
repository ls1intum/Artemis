import { clearRemoteSelectionStyles, ensureRemoteSelectionStyle, getColorForClientId } from 'app/exercise/synchronization/services/yjs-utils';

describe('yjs-utils', () => {
    afterEach(() => {
        clearRemoteSelectionStyles();
    });

    it('escapes unsafe CSS content characters in collaborator labels', () => {
        const displayName = 'A\\B"C\nD\rE\tF\fG\u2028H\u2029I';
        ensureRemoteSelectionStyle(7, '#123456', displayName);

        const styleElement = document.getElementById('yjs-remote-selection-styles') as HTMLStyleElement | null;
        expect(styleElement).toBeTruthy();
        const styleText = styleElement?.textContent ?? '';

        // Hover rule uses full label and must contain escaped control characters.
        expect(styleText).toContain('content:"A\\\\B\\"C\\00000AD\\00000DE\\000009F\\00000CG\\002028H\\002029I"');
        expect(styleText).not.toContain('content:"A\\B"C');
        expect(styleText).not.toContain('\u2028');
        expect(styleText).not.toContain('\u2029');
    });

    it('validates color strings and rejects CSS injection attempts', () => {
        const maliciousColor = 'red; } body { visibility: hidden } .x {';
        ensureRemoteSelectionStyle(8, maliciousColor, 'Test User');

        const styleElement = document.getElementById('yjs-remote-selection-styles') as HTMLStyleElement | null;
        expect(styleElement).toBeTruthy();
        const styleText = styleElement?.textContent ?? '';

        // Should not contain the malicious CSS
        expect(styleText).not.toContain('visibility: hidden');
        expect(styleText).not.toContain('} body {');
        // Should use fallback color generated from clientId
        const fallbackColor = getColorForClientId(8);
        expect(styleText).toContain(fallbackColor);
    });

    it('accepts valid hex colors', () => {
        ensureRemoteSelectionStyle(9, '#abc', 'User 1');
        ensureRemoteSelectionStyle(10, '#123456', 'User 2');
        ensureRemoteSelectionStyle(11, '#12345678', 'User 3');

        const styleElement = document.getElementById('yjs-remote-selection-styles') as HTMLStyleElement | null;
        const styleText = styleElement?.textContent ?? '';

        expect(styleText).toContain('#abc');
        expect(styleText).toContain('#123456');
        expect(styleText).toContain('#12345678');
    });

    it('accepts valid hsl colors', () => {
        ensureRemoteSelectionStyle(12, 'hsl(120, 50%, 60%)', 'HSL User');

        const styleElement = document.getElementById('yjs-remote-selection-styles') as HTMLStyleElement | null;
        const styleText = styleElement?.textContent ?? '';

        expect(styleText).toContain('hsl(120, 50%, 60%)');
    });

    it('accepts valid rgb colors', () => {
        ensureRemoteSelectionStyle(13, 'rgb(255, 128, 0)', 'RGB User');

        const styleElement = document.getElementById('yjs-remote-selection-styles') as HTMLStyleElement | null;
        const styleText = styleElement?.textContent ?? '';

        expect(styleText).toContain('rgb(255, 128, 0)');
    });

    it('accepts valid rgba colors', () => {
        ensureRemoteSelectionStyle(14, 'rgba(255, 0, 0, 0.5)', 'RGBA User');

        const styleElement = document.getElementById('yjs-remote-selection-styles') as HTMLStyleElement | null;
        const styleText = styleElement?.textContent ?? '';

        expect(styleText).toContain('rgba(255, 0, 0, 0.5)');
    });

    it('accepts valid hsla colors', () => {
        ensureRemoteSelectionStyle(15, 'hsla(120, 50%, 60%, 0.8)', 'HSLA User');

        const styleElement = document.getElementById('yjs-remote-selection-styles') as HTMLStyleElement | null;
        const styleText = styleElement?.textContent ?? '';

        expect(styleText).toContain('hsla(120, 50%, 60%, 0.8)');
    });

    it('rejects invalid color formats and uses fallback', () => {
        const invalidColors = ['blue', 'url(data:image)', '#gg0000'];

        invalidColors.forEach((color, index) => {
            const clientId = 20 + index;
            ensureRemoteSelectionStyle(clientId, color, `User ${index}`);

            const styleElement = document.getElementById('yjs-remote-selection-styles') as HTMLStyleElement | null;
            const styleText = styleElement?.textContent ?? '';

            // Should not contain the invalid color
            expect(styleText).not.toContain(color);
            // Should use fallback color
            const fallbackColor = getColorForClientId(clientId);
            expect(styleText).toContain(fallbackColor);
        });
    });
});
