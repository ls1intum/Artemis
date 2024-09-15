import { faAt } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { firstValueFrom } from 'rxjs';
import { UserNameAndLoginDTO } from 'app/core/user/user.model';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorCompletionItem, TextEditorCompletionItemKind } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-completion-item.model';

/**
 * Action to insert a user mention into the editor. Users that type a @ will see a list of available users to mention.
 * Users will be fetched repeatedly as the user types to provide up-to-date results.
 */
export class UserMentionAction extends TextEditorAction {
    disposableCompletionProvider?: Disposable;

    static readonly ID = 'user-mention.action';
    static readonly DEFAULT_INSERT_TEXT = '@';

    constructor(
        private readonly courseManagementService: CourseManagementService,
        private readonly metisService: MetisService,
    ) {
        super(UserMentionAction.ID, 'artemisApp.metis.editor.user', faAt);
    }

    /**
     * Registers this action in the provided editor. This will register a completion provider that shows the available users.
     * @param editor The editor to register the action in.
     * @param translateService The translate service to use for translations, e.g. the label.
     */
    register(editor: TextEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposableCompletionProvider = this.registerCompletionProviderForCurrentModel<UserNameAndLoginDTO>(
            editor,
            this.loadUsersForSearchTerm.bind(this),
            (user: UserNameAndLoginDTO, range: TextEditorRange) =>
                new TextEditorCompletionItem(`@${user.name}`, this.label, `[user]${user.name}(${user.login})[/user]`, TextEditorCompletionItemKind.User, range),
            '@',
            true,
        );
    }

    /**
     * Types the text '@' into the editor and focuses it. This will trigger the completion provider to show the available users.
     * @param editor The editor to type the text into.
     */
    run(editor: TextEditor) {
        this.typeText(editor, UserMentionAction.DEFAULT_INSERT_TEXT);
        editor.focus();
    }

    dispose(): void {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }

    async loadUsersForSearchTerm(searchTerm: string): Promise<UserNameAndLoginDTO[]> {
        const response = await firstValueFrom(this.courseManagementService.searchMembersForUserMentions(this.metisService.getCourse().id!, searchTerm));
        return response.body ?? [];
    }
}
