import { ConversationMemberSearchFilter, ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { UserMentionCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/userMentionCommand';
import { SelectWithSearchComponent } from 'app/shared/markdown-editor/select-with-search/select-with-search.component';

describe('UserMentionCommand', () => {
    let userMentionCommand: UserMentionCommand;
    let conversationServiceMock: Partial<ConversationService>;
    let metisServiceMock: Partial<MetisService>;
    let aceEditorMock: any;

    beforeEach(() => {
        const selectWithSearchComponent = {
            open: jest.fn(() => {}),
        } as SelectWithSearchComponent;

        conversationServiceMock = {
            searchMembersOfConversation: jest.fn(() =>
                of(
                    new HttpResponse<ConversationUserDTO[]>({
                        body: [
                            { name: 'User 1', login: 'user1' },
                            { name: 'User 2', login: 'user2' },
                        ],
                    }),
                ),
            ),
        };

        metisServiceMock = {
            getCourse: () => ({ id: 123 }),
        };

        aceEditorMock = {
            command: undefined,
            commands: {
                addCommand: jest.fn(function (obj: any) {
                    aceEditorMock.command = obj;
                }),
            },
            getCursorPosition: () => ({ row: 0, column: 0 }),
            focus: jest.fn(() => {}),
            session: {
                getDocument: () => ({
                    removeInLine: jest.fn(() => {}),
                }),
            },
            insert: jest.fn(() => {}),
        };

        // Create an instance of UserMentionCommand with mock services
        userMentionCommand = new UserMentionCommand(conversationServiceMock as ConversationService, metisServiceMock as MetisService);
        userMentionCommand.setSelectWithSearchComponent(selectWithSearchComponent);
    });

    it('should create an instance of UserMentionCommand', () => {
        expect(userMentionCommand).toBeTruthy();
    });

    it('should return "@" as the associated input character', () => {
        const associatedInputCharacter = userMentionCommand.getAssociatedInputCharacter();
        expect(associatedInputCharacter).toBe('@');
    });

    it('should perform a user search', () => {
        const searchTerm = 'user';

        userMentionCommand.performSearch(searchTerm).subscribe((response) => {
            expect(response.body).toEqual([
                { name: 'User 1', login: 'user1' },
                { name: 'User 2', login: 'user2' },
            ]);
        });

        expect(conversationServiceMock.searchMembersOfConversation).toHaveBeenCalledWith(
            123, // Course ID (mocked)
            487, // Conversation ID (mocked)
            searchTerm,
            0, // Offset
            10, // Limit
            ConversationMemberSearchFilter.ALL,
        );
    });

    it('should convert a selected user to text', () => {
        const selectedUser: ConversationUserDTO = { name: 'User 1', login: 'user1' };

        const text = userMentionCommand.selectionToText(selectedUser);

        expect(text).toBe('[user]User 1(user1)[/user]');
    });

    it('should insert selection', () => {
        // Simulate open selection menu via triggering command
        userMentionCommand.setEditor(aceEditorMock);
        aceEditorMock.command.exec(aceEditorMock);

        const insertText = jest.spyOn(userMentionCommand, 'insertText');

        userMentionCommand.insertSelection({ name: 'User 1', login: 'user1' });

        expect(insertText).toHaveBeenCalled();
        expect(aceEditorMock.focus).toHaveBeenCalled();
    });

    it('should setEditor', () => {
        userMentionCommand.setEditor(aceEditorMock);

        expect(aceEditorMock.commands.addCommand).toHaveBeenCalled();
    });
});
