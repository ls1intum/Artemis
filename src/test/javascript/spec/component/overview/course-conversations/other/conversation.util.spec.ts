import { getUserLabel } from 'app/communication/course-conversations-components/other/conversation.util';

describe('ConversationUtil', () => {
    it('should return the correct user label', () => {
        const user = { firstName: 'John', lastName: 'Doe', login: 'johndoe' };
        expect(getUserLabel(user)).toBe('John Doe (johndoe)');
    });
});
