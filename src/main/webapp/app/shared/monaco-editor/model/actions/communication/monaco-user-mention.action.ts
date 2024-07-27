import { faAt } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { firstValueFrom } from 'rxjs';
import { UserNameAndLoginDTO } from 'app/core/user/user.model';

/**
 * Action to insert a user mention into the editor. Users that type a @ will see a list of available users to mention.
 * Users will be fetched repeatedly as the user types to provide up-to-date results.
 */
export class MonacoUserMentionAction extends MonacoEditorAction {
    disposableCompletionProvider?: monaco.IDisposable;
    debounceTimer?: number;
    pendingResolves: ((value: UserNameAndLoginDTO[]) => void)[] = [];

    static readonly ID = 'monaco-user-mention.action';
    static readonly DEFAULT_INSERT_TEXT = '@';

    constructor(
        private readonly courseManagementService: CourseManagementService,
        private readonly metisService: MetisService,
    ) {
        super(MonacoUserMentionAction.ID, 'artemisApp.metis.editor.user', faAt);
    }

    /**
     * Registers this action in the provided editor. This will register a completion provider that shows the available users.
     * @param editor The editor to register the action in.
     * @param translateService The translate service to use for translations, e.g. the label.
     */
    register(editor: monaco.editor.IStandaloneCodeEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposableCompletionProvider = this.registerCompletionProviderForCurrentModel<UserNameAndLoginDTO>(
            editor,
            this.loadUsersForSearchTerm.bind(this),
            (user: UserNameAndLoginDTO, range: monaco.IRange) => ({
                label: '@' + user.name,
                kind: monaco.languages.CompletionItemKind.User,
                insertText: `[user]${user.name}(${user.login})[/user]`,
                range,
                detail: this.label,
            }),
            '@',
            true,
        );
    }

    /**
     * Types the text '@' into the editor and focuses it. This will trigger the completion provider to show the available users.
     * @param editor The editor to type the text into.
     */
    run(editor: monaco.editor.ICodeEditor) {
        this.typeText(editor, MonacoUserMentionAction.DEFAULT_INSERT_TEXT);
        editor.focus();
    }

    dispose(): void {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }

    async loadUsersForSearchTerm(searchTerm: string): Promise<UserNameAndLoginDTO[]> {
        // Clear the existing timer, if any
        if (this.debounceTimer) {
            window.clearTimeout(this.debounceTimer);
            this.pendingResolves.forEach((resolve) => resolve([]));
            this.pendingResolves = [];
        }

        // Return a new Promise that will resolve after the debounce delay
        return new Promise((resolve) => {
            this.pendingResolves.push(resolve);
            this.debounceTimer = window.setTimeout(async () => {
                const response = await firstValueFrom(this.courseManagementService.searchMembersForUserMentions(this.metisService.getCourse().id!, searchTerm));
                resolve(response.body ?? []);
            }, 200);
        });
    }
}
