import { MetisService } from 'app/shared/metis/metis.service';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { UserMentionCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/userMentionCommand';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { SelectableItem } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';

describe('UserMentionCommand', () => {
    let userMentionCommand: UserMentionCommand;
    let courseManagementServiceMock: Partial<CourseManagementService>;
    let metisServiceMock: Partial<MetisService>;
    let aceEditorMock: any;
    let selectWithSearchComponent: any;

    beforeEach(() => {
        selectWithSearchComponent = {
            open: () => {},
            updateSearchTerm: () => {},
            close: () => {},
        };

        courseManagementServiceMock = {
            searchMembersForUserMentions: () =>
                of(
                    new HttpResponse<ConversationUserDTO[]>({
                        body: [
                            { name: 'User 1', login: 'user1' },
                            { name: 'User 2', login: 'user2' },
                        ],
                    }),
                ),
        };

        metisServiceMock = {
            getCourse: () => ({ id: 123 }),
        };

        aceEditorMock = {
            command: undefined,
            commands: {
                addCommand: (obj: any) => {
                    aceEditorMock.command = obj;
                },
            },
            execCommand: () => {},
            getCursorPosition: () => ({ row: 0, column: 0 }),
            focus: () => {},
            session: {
                getDocument: () => ({
                    removeInLine: () => {},
                }),
                getLine: () => '',
            },
            insert: () => {},
            renderer: {
                textToScreenCoordinates: () => {},
            },
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

        const searchMembersForUserMentionsSpy = jest.spyOn(courseManagementServiceMock, 'searchMembersForUserMentions');

        userMentionCommand.performSearch(searchTerm).subscribe((response) => {
            expect(response.body).toEqual([
                { name: 'User 1', login: 'user1' },
                { name: 'User 2', login: 'user2' },
            ]);
        });

        expect(searchMembersForUserMentionsSpy).toHaveBeenCalledExactlyOnceWith(123, searchTerm);
    });

    it('should insert selection', () => {
        userMentionCommand.setEditor(aceEditorMock);

        const focusSpy = jest.spyOn(aceEditorMock, 'focus');

        // Simulate open selection menu via triggering command
        aceEditorMock.command.exec(aceEditorMock);

        userMentionCommand.insertSelection({ name: 'User 1', login: 'user1' } as SelectableItem);

        // the editor is focues twice: Once for the execution of the command, once after the text insertion
        expect(focusSpy).toHaveBeenCalledTimes(2);
    });

    it('should not be able to call command twice while active', () => {
        userMentionCommand.setEditor(aceEditorMock);

        const openSpy = jest.spyOn(selectWithSearchComponent, 'open');

        // Simulate open selection menu via triggering command
        aceEditorMock.command.exec(aceEditorMock);
        aceEditorMock.command.exec(aceEditorMock);

        expect(openSpy).toHaveBeenCalledOnce();
    });

    it('should add command when setting editor', () => {
        const addCommandSpy = jest.spyOn(aceEditorMock.commands, 'addCommand');

        userMentionCommand.setEditor(aceEditorMock);

        expect(addCommandSpy).toHaveBeenCalledOnce();
    });

    it('should execute the command', () => {
        userMentionCommand.setEditor(aceEditorMock);

        const execCommandSpy = jest.spyOn(aceEditorMock, 'execCommand');

        userMentionCommand.execute();

        expect(execCommandSpy).toHaveBeenCalledExactlyOnceWith('@');
    });

    it('should calculate screen coordinates', () => {
        userMentionCommand.setEditor(aceEditorMock);

        const textToScreenCoordinatesSpy = jest.spyOn(aceEditorMock.renderer, 'textToScreenCoordinates');

        userMentionCommand.getCursorScreenPosition();

        expect(textToScreenCoordinatesSpy).toHaveBeenCalledOnce();
    });

    it('should update search term correctly based on cursor position', () => {
        userMentionCommand.setEditor(aceEditorMock);
        aceEditorMock.command.exec(aceEditorMock);
        aceEditorMock.getCursorPosition = jest.fn(() => ({ row: 0, column: 10 }));
        aceEditorMock.session.getLine = jest.fn(() => 'Hello @user');

        const updateSearchTermSpy = jest.spyOn(selectWithSearchComponent, 'updateSearchTerm');

        userMentionCommand.updateSearchTerm();

        expect(updateSearchTermSpy).toHaveBeenCalledExactlyOnceWith('user');
    });

    it('should not update search term if menu not open', () => {
        userMentionCommand.setEditor(aceEditorMock);

        const updateSearchTermSpy = jest.spyOn(selectWithSearchComponent, 'updateSearchTerm');

        userMentionCommand.updateSearchTerm();

        expect(updateSearchTermSpy).not.toHaveBeenCalled();
    });

    it('should close selectWithSearchComponent if there is no "@" sign', () => {
        userMentionCommand.setEditor(aceEditorMock);
        aceEditorMock.command.exec(aceEditorMock);
        aceEditorMock.getCursorPosition = jest.fn(() => ({ row: 0, column: 6 }));
        aceEditorMock.session.getLine = jest.fn(() => 'Hello ');

        const closeSpy = jest.spyOn(selectWithSearchComponent, 'close');

        userMentionCommand.updateSearchTerm();

        expect(closeSpy).toHaveBeenCalledOnce();
    });
});
