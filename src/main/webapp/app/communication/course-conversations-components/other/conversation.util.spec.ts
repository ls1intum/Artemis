import { afterEach, describe, expect, it, vi } from 'vitest';
import { defaultFirstLayerDialogOptions, defaultSecondLayerDialogOptions, getUserLabel } from 'app/communication/course-conversations-components/other/conversation.util';

describe('ConversationUtil', () => {
    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should return the correct user label', () => {
        const user = { firstName: 'John', lastName: 'Doe', login: 'johndoe' };
        expect(getUserLabel(user)).toBe('John Doe (johndoe)');
    });

    it('should open the dialogs of the conversation layout without a header, dismissable, and narrower on the second layer', () => {
        for (const options of [defaultFirstLayerDialogOptions, defaultSecondLayerDialogOptions]) {
            expect(options).toEqual(expect.objectContaining({ showHeader: false, closable: true, closeOnEscape: true, dismissableMask: true }));
        }
        expect(defaultFirstLayerDialogOptions.size).toBe('medium');
        expect(defaultSecondLayerDialogOptions.size).toBe('small');
    });
});
