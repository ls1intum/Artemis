import { MetisService } from 'app/shared/metis/metis.service';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { SelectableItem } from 'app/shared/markdown-editor/commands/interactiveSearchCommand';
import { ChannelMentionCommand } from 'app/shared/markdown-editor/commands/courseArtifactReferenceCommands/channelMentionCommand';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { ChannelIdAndNameDTO } from 'app/entities/metis/conversation/channel.model';
import { CourseInformationSharingConfiguration } from 'app/entities/course.model';

describe('ChannelMentionCommand', () => {
    let channelMentionCommand: ChannelMentionCommand;
    let channelServiceMock: Partial<ChannelService>;
    let metisServiceMock: Partial<MetisService>;
    let aceEditorMock: any;
    let selectWithSearchComponent: any;

    beforeEach(() => {
        selectWithSearchComponent = {
            open: () => {},
            updateSearchTerm: () => {},
            close: () => {},
        };

        channelServiceMock = {
            getPublicChannelsOfCourse: () =>
                of(
                    new HttpResponse<ChannelIdAndNameDTO[]>({
                        body: [
                            { name: 'Channel 1', id: 1 },
                            { name: 'Channel 2', id: 2 },
                        ],
                    }),
                ),
        };

        metisServiceMock = {
            getCourse: () => ({ id: 1, courseInformationSharingConfiguration: CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING }),
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
        };

        // Create an instance of ChannelMentionCommand with mock services
        channelMentionCommand = new ChannelMentionCommand(channelServiceMock as ChannelService, metisServiceMock as MetisService);
        channelMentionCommand.setSelectWithSearchComponent(selectWithSearchComponent);
    });

    it('should create an instance of ChannelMentionCommand', () => {
        expect(channelMentionCommand).toBeTruthy();
    });

    it('should perform a channel search and cache result', () => {
        const getChannelsOfCourseSpy = jest.spyOn(channelServiceMock, 'getPublicChannelsOfCourse');

        channelMentionCommand.performSearch('channel').subscribe((response) => {
            expect(response.body).toEqual([
                { name: 'Channel 1', id: 1 },
                { name: 'Channel 2', id: 2 },
            ]);
        });

        channelMentionCommand.performSearch('channel').subscribe();

        expect(getChannelsOfCourseSpy).toHaveBeenCalledExactlyOnceWith(1);
    });

    it('should filter channels based on searchTerm', () => {
        const getChannelsOfCourseSpy = jest.spyOn(channelServiceMock, 'getPublicChannelsOfCourse');

        channelMentionCommand.performSearch('1').subscribe((response) => {
            expect(response.body).toEqual([{ name: 'Channel 1', id: 1 }]);
        });

        channelMentionCommand.performSearch('2').subscribe((response) => {
            expect(response.body).toEqual([{ name: 'Channel 2', id: 2 }]);
        });

        expect(getChannelsOfCourseSpy).toHaveBeenCalledExactlyOnceWith(1);
    });

    it('should insert selection', () => {
        channelMentionCommand.setEditor(aceEditorMock);

        const focusSpy = jest.spyOn(aceEditorMock, 'focus');

        // Simulate open selection menu via triggering command
        aceEditorMock.command.exec(aceEditorMock);

        channelMentionCommand.insertSelection({ name: 'Channel 1', id: 1 } as SelectableItem);

        // The editor is focused twice: Once for the execution of the command, once after the text insertion
        expect(focusSpy).toHaveBeenCalledTimes(2);
    });

    it('should execute the command', () => {
        channelMentionCommand.setEditor(aceEditorMock);

        const execCommandSpy = jest.spyOn(aceEditorMock, 'execCommand');

        channelMentionCommand.execute();

        expect(execCommandSpy).toHaveBeenCalledExactlyOnceWith('#');
    });
});
