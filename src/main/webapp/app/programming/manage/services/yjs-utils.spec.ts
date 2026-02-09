import { clearRemoteSelectionStyles, ensureRemoteSelectionStyle } from 'app/programming/manage/services/yjs-utils';

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
});
