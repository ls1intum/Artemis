import { MetisService } from 'app/shared/metis/metis.service';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { UserMentionCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/userMentionCommand';
import { CourseManagementService } from 'app/course/manage/course-management.service';

describe('UserMentionCommand', () => {
    let userMentionCommand: UserMentionCommand;
    let courseManagementServiceMock: Partial<CourseManagementService>;
    let metisServiceMock: Partial<MetisService>;
    let aceEditorMock: any;

    beforeEach(() => {
        const selectWithSearchComponent = {
            open: jest.fn(() => {}),
        } as any;

        courseManagementServiceMock = {
            searchMembersForUserMentions: jest.fn(() =>
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
        userMentionCommand = new UserMentionCommand(courseManagementServiceMock as CourseManagementService, metisServiceMock as MetisService);
        userMentionCommand.setSelectWithSearchComponent(selectWithSearchComponent);
    });

    it('should create an instance of UserMentionCommand', () => {
        expect(userMentionCommand).toBeTruthy();
    });

    it('should perform a user search', () => {
        const searchTerm = 'user';

        userMentionCommand.performSearch(searchTerm).subscribe((response) => {
            expect(response.body).toEqual([
                { name: 'User 1', login: 'user1' },
                { name: 'User 2', login: 'user2' },
            ]);
        });

        expect(courseManagementServiceMock.searchMembersForUserMentions).toHaveBeenCalledWith(123, searchTerm);
    });

    it('should insert selection', () => {
        // Simulate open selection menu via triggering command
        userMentionCommand.setEditor(aceEditorMock);
        aceEditorMock.command.exec(aceEditorMock);

        userMentionCommand.insertSelection({ name: 'User 1', login: 'user1' });

        expect(aceEditorMock.focus).toHaveBeenCalled();
    });

    it('should setEditor', () => {
        userMentionCommand.setEditor(aceEditorMock);

        expect(aceEditorMock.commands.addCommand).toHaveBeenCalled();
    });
});
