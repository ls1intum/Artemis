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
    // TODO: refactor to use same method as MonacoChannelReferenceAction
    register(editor: monaco.editor.IStandaloneCodeEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        const model = editor.getModel();
        if (!model) {
            throw new Error(`A model must be attached to the editor to use the ${this.id} action.`);
        }
        const languageId = model.getLanguageId();
        const modelId = model.id;
        const searchFn: (searchTerm: string) => Promise<UserNameAndLoginDTO[]> = this.loadUsersForSearchTerm.bind(this);
        this.disposableCompletionProvider = monaco.languages.registerCompletionItemProvider(languageId, {
            triggerCharacters: ['@'],
            provideCompletionItems: (model: monaco.editor.ITextModel, position: monaco.Position): monaco.languages.ProviderResult<monaco.languages.CompletionList> => {
                if (model.id !== modelId) {
                    return undefined;
                }
                const wordUntilPosition = model.getWordUntilPosition(position);
                const range = {
                    startLineNumber: position.lineNumber,
                    startColumn: wordUntilPosition.startColumn - 1,
                    endLineNumber: position.lineNumber,
                    endColumn: wordUntilPosition.endColumn,
                };
                // Check if before the word, we have a @
                const beforeWord = model.getValueInRange({
                    startLineNumber: position.lineNumber,
                    startColumn: wordUntilPosition.startColumn - 1,
                    endLineNumber: position.lineNumber,
                    endColumn: wordUntilPosition.startColumn,
                });
                if (wordUntilPosition.word !== MonacoUserMentionAction.DEFAULT_INSERT_TEXT) {
                    if (beforeWord !== MonacoUserMentionAction.DEFAULT_INSERT_TEXT) {
                        return undefined;
                    }
                }
                return searchFn(wordUntilPosition.word).then((users) => {
                    return {
                        suggestions: users.map((ch) => ({
                            label: '@' + ch.name!,
                            kind: monaco.languages.CompletionItemKind.User,
                            insertText: `[user]${ch.name}(${ch.login!})[/user]`,
                            range,
                            detail: this.label,
                        })),
                    };
                });
            },
        });
    }

    run(editor: monaco.editor.ICodeEditor) {
        editor.trigger('keyboard', 'type', { text: MonacoUserMentionAction.DEFAULT_INSERT_TEXT });
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
