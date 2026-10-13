import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { UserMentionAction } from 'app/editor/monaco-editor/model/actions/communication/user-mention.action';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CommunicationService } from 'app/communication/service/communication.service';
import { UserNameAndLoginDTO } from 'app/account/user/user.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorCompleter } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completer.model';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorPosition } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-position.model';
import { TextEditorCompletionItemKind } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completion-item.model';

describe('UserMentionAction', () => {
    const courseId = 42;
    const users: UserNameAndLoginDTO[] = [
        { name: 'Alice Example', login: 'alice' },
        { name: 'Bob Example', login: 'bob' },
    ];
    const range = new TextEditorRange(new TextEditorPosition(1, 1), new TextEditorPosition(1, 3));

    let searchMembersSpy: ReturnType<typeof vi.fn>;
    let currentConversation: ConversationDTO | undefined;
    let isEditingPosting: boolean;
    let postingConversation: ConversationDTO | undefined;
    let action: UserMentionAction;
    let editor: Record<string, ReturnType<typeof vi.fn>>;
    let completionProviderDispose: ReturnType<typeof vi.fn>;
    let actionDispose: ReturnType<typeof vi.fn>;

    const translateService = {
        instant: (key: string) => `translated:${key}`,
    } as unknown as TranslateService;

    const textEditor = () => editor as unknown as TextEditor;

    beforeEach(() => {
        searchMembersSpy = vi.fn().mockReturnValue(of(new HttpResponse<UserNameAndLoginDTO[]>({ body: users, status: 200 })));
        const courseManagementService = { searchMembersForUserMentions: searchMembersSpy } as unknown as CourseManagementService;
        currentConversation = undefined;
        isEditingPosting = false;
        postingConversation = undefined;
        const communicationService = {
            getCourse: () => ({ id: courseId }),
            getCurrentConversation: () => currentConversation,
        } as unknown as CommunicationService;
        completionProviderDispose = vi.fn();
        actionDispose = vi.fn();
        editor = {
            addAction: vi.fn().mockReturnValue({ dispose: actionDispose }),
            addCompleter: vi.fn().mockReturnValue({ dispose: completionProviderDispose }),
            getSelection: vi.fn(),
            getTextAtRange: vi.fn(),
            replaceTextAtRange: vi.fn(),
            triggerCompletion: vi.fn(),
            focus: vi.fn(),
        };
        action = new UserMentionAction(
            courseManagementService,
            communicationService,
            () => isEditingPosting,
            () => postingConversation,
        );
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    const registerAndCaptureCompleter = (): TextEditorCompleter<unknown> => {
        action.register(textEditor(), translateService);
        expect(editor.addCompleter).toHaveBeenCalledOnce();
        return editor.addCompleter.mock.calls[0][0];
    };

    it('should register the action with its translated label and the completer triggered by @', () => {
        const completer = registerAndCaptureCompleter();

        expect(editor.addAction).toHaveBeenCalledWith(action);
        expect(action.id).toBe(UserMentionAction.ID);
        expect(action.label).toBe('translated:artemisApp.communication.editor.user');
        expect(completer.triggerCharacter).toBe('@');
        expect(completer.incomplete).toBe(true);
    });

    it('should map a user to a completion item that inserts the user tag', () => {
        const completer = registerAndCaptureCompleter();

        const item = completer.mapCompletionItem(users[0], range, 'ali', 0);

        expect(item.getLabel()).toBe('@Alice Example');
        expect(item.getDetailText()).toBe('translated:artemisApp.communication.editor.user');
        expect(item.getInsertText()).toBe('[user]Alice Example(alice)[/user]');
        expect(item.getKind()).toBe(TextEditorCompletionItemKind.User);
        expect(item.getRange()).toBe(range);
    });

    it('should map the all-members suggestion to a completion item that stays on top and ends the word', () => {
        const completer = registerAndCaptureCompleter();

        const item = completer.mapCompletionItem('all', range, '', 0);

        expect(item.getLabel()).toBe('@all');
        expect(item.getDetailText()).toBe('translated:artemisApp.communication.editor.allMembers');
        expect(item.getInsertText()).toBe('@all ');
        expect(item.getKind()).toBe(TextEditorCompletionItemKind.User);
        expect(item.getRange()).toBe(range);
        expect(item.getFilterText()).toBeUndefined();
        // digits sort before the "@" of every user suggestion
        expect(item.getSortText()).toBe('0@all');
    });

    it('should search through the completer with the course of the communication service', async () => {
        const completer = registerAndCaptureCompleter();

        await expect(completer.searchItems('ali')).resolves.toEqual(users);

        expect(searchMembersSpy).toHaveBeenCalledExactlyOnceWith(courseId, 'ali');
    });

    it('should insert the @ at the selection, trigger the completion and focus the editor on run', () => {
        const selection = new TextEditorRange(new TextEditorPosition(2, 4), new TextEditorPosition(2, 4));
        editor.getSelection.mockReturnValue(selection);
        editor.getTextAtRange.mockReturnValue('');

        action.run(textEditor());

        expect(editor.replaceTextAtRange).toHaveBeenCalledExactlyOnceWith(selection, UserMentionAction.DEFAULT_INSERT_TEXT);
        expect(editor.triggerCompletion).toHaveBeenCalledOnce();
        expect(editor.focus).toHaveBeenCalledOnce();
    });

    it('should still trigger the completion and focus the editor if there is no selection', () => {
        editor.getSelection.mockReturnValue(undefined);

        action.run(textEditor());

        expect(editor.replaceTextAtRange).not.toHaveBeenCalled();
        expect(editor.triggerCompletion).toHaveBeenCalledOnce();
        expect(editor.focus).toHaveBeenCalledOnce();
    });

    it('should dispose the action and the completion provider', () => {
        action.register(textEditor(), translateService);

        action.dispose();

        expect(actionDispose).toHaveBeenCalledOnce();
        expect(completionProviderDispose).toHaveBeenCalledOnce();
    });

    it('should dispose without error if it was never registered', () => {
        expect(() => action.dispose()).not.toThrow();
        expect(completionProviderDispose).not.toHaveBeenCalled();
    });

    describe('loadUsersForSearchTerm', () => {
        it('should default to an empty search term', async () => {
            await expect(action.loadUsersForSearchTerm()).resolves.toEqual(users);

            expect(searchMembersSpy).toHaveBeenCalledExactlyOnceWith(courseId, '');
        });

        it('should pass the search term to the server', async () => {
            await action.loadUsersForSearchTerm('bo');

            expect(searchMembersSpy).toHaveBeenCalledExactlyOnceWith(courseId, 'bo');
        });

        it('should return no users if the response has no body', async () => {
            searchMembersSpy.mockReturnValue(of(new HttpResponse<UserNameAndLoginDTO[]>({ body: null, status: 204 })));

            await expect(action.loadUsersForSearchTerm('bo')).resolves.toEqual([]);
        });

        it('should propagate a failing search', async () => {
            searchMembersSpy.mockReturnValue(
                new Observable<HttpResponse<UserNameAndLoginDTO[]>>((subscriber) => {
                    subscriber.error(new Error('search failed'));
                }),
            );

            await expect(action.loadUsersForSearchTerm('bo')).rejects.toThrow('search failed');
        });
    });

    describe('loadSuggestionsForSearchTerm', () => {
        it('should default to an empty search term', async () => {
            await action.loadSuggestionsForSearchTerm();

            expect(searchMembersSpy).toHaveBeenCalledExactlyOnceWith(courseId, '');
        });

        it('should suggest @all first in a group chat', async () => {
            currentConversation = new GroupChatDTO();

            await expect(action.loadSuggestionsForSearchTerm('ali')).resolves.toEqual(['all', ...users]);
            expect(searchMembersSpy).toHaveBeenCalledExactlyOnceWith(courseId, 'ali');
        });

        it('should suggest @all even if no user matches the search term', async () => {
            currentConversation = new GroupChatDTO();
            searchMembersSpy.mockReturnValue(of(new HttpResponse<UserNameAndLoginDTO[]>({ body: [], status: 200 })));

            await expect(action.loadSuggestionsForSearchTerm('zzz')).resolves.toEqual(['all']);
        });

        it.each([
            ['a channel', () => new ChannelDTO()],
            ['a one-to-one chat', () => new OneToOneChatDTO()],
            ['no conversation', () => undefined],
        ])('should only suggest users in %s', async (_description, createConversation) => {
            currentConversation = createConversation();

            await expect(action.loadSuggestionsForSearchTerm('ali')).resolves.toEqual(users);
        });

        it('should not suggest @all while editing a posting in a group chat', async () => {
            currentConversation = new GroupChatDTO();
            isEditingPosting = true;

            await expect(action.loadSuggestionsForSearchTerm('ali')).resolves.toEqual(users);
        });

        it('should use the conversation of the posting if it is not the current conversation', async () => {
            currentConversation = undefined;
            postingConversation = new GroupChatDTO();

            await expect(action.loadSuggestionsForSearchTerm('')).resolves.toEqual(['all', ...users]);
        });

        it('should prefer the conversation of the posting over the current conversation', async () => {
            currentConversation = new GroupChatDTO();
            postingConversation = new ChannelDTO();

            await expect(action.loadSuggestionsForSearchTerm('')).resolves.toEqual(users);
        });

        it('should fall back to the current conversation if the posting provides none', async () => {
            currentConversation = new GroupChatDTO();
            postingConversation = undefined;

            await expect(action.loadSuggestionsForSearchTerm('')).resolves.toEqual(['all', ...users]);
        });

        it('should default to not editing and to the current conversation if the callbacks are omitted', async () => {
            const defaultAction = new UserMentionAction(
                { searchMembersForUserMentions: searchMembersSpy } as unknown as CourseManagementService,
                { getCourse: () => ({ id: courseId }), getCurrentConversation: () => new GroupChatDTO() } as unknown as CommunicationService,
            );

            await expect(defaultAction.loadSuggestionsForSearchTerm('')).resolves.toEqual(['all', ...users]);
        });
    });
});
