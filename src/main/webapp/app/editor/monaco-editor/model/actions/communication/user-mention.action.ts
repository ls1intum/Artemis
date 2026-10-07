import { faAt } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TextEditorAction } from 'app/editor/monaco-editor/model/actions/text-editor-action.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { CommunicationService } from 'app/communication/service/communication.service';
import { firstValueFrom } from 'rxjs';
import { UserNameAndLoginDTO } from 'app/account/user/user.model';
import { getAsGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { Disposable } from 'app/editor/monaco-editor/model/actions/monaco-editor.util';
import { TextEditor } from 'app/editor/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorRange } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorCompletionItem, TextEditorCompletionItemKind } from 'app/editor/monaco-editor/model/actions/adapter/text-editor-completion-item.model';

/**
 * Marks the synthetic suggestion that pings every member of a group chat. It is not a user, hence it is kept apart from the user suggestions.
 */
const ALL_MEMBERS_SUGGESTION = 'all';

type UserMentionSuggestion = UserNameAndLoginDTO | typeof ALL_MEMBERS_SUGGESTION;

/**
 * Action to insert a user mention into the editor. Users that type a @ will see a list of available users to mention.
 * Users will be fetched repeatedly as the user types to provide up-to-date results.
 * In a group chat, the list starts with the suggestion "@all", which notifies every member of the group chat when a new posting is created.
 * The constructor takes a callback that tells whether an existing posting is being edited, in which case no "@all" is suggested.
 */
export class UserMentionAction extends TextEditorAction {
    disposableCompletionProvider?: Disposable;

    static readonly ID = 'user-mention.action';
    static readonly DEFAULT_INSERT_TEXT = '@';
    static readonly ALL_MENTION_TEXT = '@all';
    // Digits sort before the "@" every user suggestion starts with, so @all stays on top of the users
    private static readonly ALL_MENTION_SORT_TEXT = '0@all';

    constructor(
        private readonly courseManagementService: CourseManagementService,
        private readonly communicationService: CommunicationService,
        private readonly isEditingPosting: () => boolean = () => false,
    ) {
        super(UserMentionAction.ID, 'artemisApp.communication.editor.user', faAt);
    }

    /**
     * Registers this action in the provided editor. This will register a completion provider that shows the available users.
     * @param editor The editor to register the action in.
     * @param translateService The translate service to use for translations, e.g. the label.
     */
    override register(editor: TextEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposableCompletionProvider = this.registerCompletionProviderForCurrentModel<UserMentionSuggestion>(
            editor,
            this.loadSuggestionsForSearchTerm.bind(this),
            (suggestion: UserMentionSuggestion, range: TextEditorRange) =>
                suggestion === ALL_MEMBERS_SUGGESTION
                    ? // The token is inserted as typed, followed by a space so it ends the word. The server notifies the members when it finds the token in the message.
                      new TextEditorCompletionItem(
                          UserMentionAction.ALL_MENTION_TEXT,
                          translateService.instant('artemisApp.communication.editor.allMembers'),
                          `${UserMentionAction.ALL_MENTION_TEXT} `,
                          TextEditorCompletionItemKind.User,
                          range,
                          undefined,
                          UserMentionAction.ALL_MENTION_SORT_TEXT,
                      )
                    : new TextEditorCompletionItem(
                          `@${suggestion.name}`,
                          this.label,
                          `[user]${suggestion.name}(${suggestion.login})[/user]`,
                          TextEditorCompletionItemKind.User,
                          range,
                      ),
            '@',
            true,
        );
    }

    /**
     * Inserts the text '@' into the editor and focuses it. This method will trigger the completion provider to show the available users.
     * @param editor The editor to insert the text into.
     */
    run(editor: TextEditor) {
        this.replaceTextAtCurrentSelection(editor, UserMentionAction.DEFAULT_INSERT_TEXT);
        editor.triggerCompletion();
        editor.focus();
    }

    override dispose(): void {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }

    /**
     * Loads the suggestions for the search term. In a group chat, the suggestion that pings all members comes first, unless an existing posting is edited.
     * @param searchTerm The text the user typed after the @.
     */
    async loadSuggestionsForSearchTerm(searchTerm: string = ''): Promise<UserMentionSuggestion[]> {
        const users = await this.loadUsersForSearchTerm(searchTerm);
        // Only group chats support the token, in all other conversations the server treats it as plain text. Editing a posting never notifies anyone, so the suggestion
        // would promise a notification that is not sent.
        const suggestsAllMembers = !this.isEditingPosting() && !!getAsGroupChatDTO(this.communicationService.getCurrentConversation());
        return suggestsAllMembers ? [ALL_MEMBERS_SUGGESTION, ...users] : users;
    }

    async loadUsersForSearchTerm(searchTerm: string = ''): Promise<UserNameAndLoginDTO[]> {
        const response = await firstValueFrom(this.courseManagementService.searchMembersForUserMentions(this.communicationService.getCourse().id!, searchTerm));
        return response.body ?? [];
    }
}
