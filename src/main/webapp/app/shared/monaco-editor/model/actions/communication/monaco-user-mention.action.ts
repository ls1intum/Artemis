import { faAt } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MetisService } from 'app/shared/metis/metis.service';
import { firstValueFrom } from 'rxjs';
import { UserNameAndLoginDTO } from 'app/core/user/user.model';

export class MonacoUserMentionAction extends MonacoEditorAction {
    disposableCompletionProvider?: monaco.IDisposable;

    static readonly ID = 'monaco-user-mention.action';
    static readonly DEFAULT_INSERT_TEXT = '@';

    constructor(
        private readonly courseManagementService: CourseManagementService,
        private readonly metisService: MetisService,
    ) {
        super(MonacoUserMentionAction.ID, 'artemisApp.metis.editor.user', faAt);
    }
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
        );
    }

    run(editor: monaco.editor.ICodeEditor) {
        this.typeText(editor, MonacoUserMentionAction.DEFAULT_INSERT_TEXT);
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
