import { getUserLabel } from 'app/overview/course-conversations/other/conversation.util';

describe('ConversationUtil', () => {
    it('should return the correct user label', () => {
        const user = { firstName: 'John', lastName: 'Doe', login: 'johndoe' };
        expect(getUserLabel(user)).toBe('John Doe (johndoe)');
    });
});
