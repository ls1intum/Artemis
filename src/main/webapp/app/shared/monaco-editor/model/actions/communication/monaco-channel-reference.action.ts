import { faHashtag } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { MonacoEditorAction } from 'app/shared/monaco-editor/model/actions/monaco-editor-action.model';
import * as monaco from 'monaco-editor';
import { ChannelIdAndNameDTO } from 'app/entities/metis/conversation/channel.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { firstValueFrom } from 'rxjs';

export class MonacoChannelReferenceAction extends MonacoEditorAction {
    static readonly ID = 'monaco-channel-reference.action';
    static readonly DEFAULT_INSERT_TEXT = '#';

    cachedChannels?: ChannelIdAndNameDTO[];
    disposableCompletionProvider?: monaco.IDisposable;

    constructor(
        private readonly metisService: MetisService,
        private readonly channelService: ChannelService,
    ) {
        super(MonacoChannelReferenceAction.ID, 'artemisApp.metis.channelReferenceCommand', faHashtag);
    }

    register(editor: monaco.editor.IStandaloneCodeEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        const model = editor.getModel();
        if (!model) {
            throw new Error(`A model must be attached to the editor to use the ${this.id} action.`);
        }
        const languageId = model.getLanguageId();
        const modelId = model.id;
        const getChannelFn: () => Promise<ChannelIdAndNameDTO[]> = this.getChannels.bind(this);
        this.disposableCompletionProvider = monaco.languages.registerCompletionItemProvider(languageId, {
            triggerCharacters: ['#'],
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
                // Check if before the word, we have a #
                const beforeWord = model.getValueInRange({
                    startLineNumber: position.lineNumber,
                    startColumn: wordUntilPosition.startColumn - 1,
                    endLineNumber: position.lineNumber,
                    endColumn: wordUntilPosition.startColumn,
                });
                if (wordUntilPosition.word !== MonacoChannelReferenceAction.DEFAULT_INSERT_TEXT) {
                    if (beforeWord !== MonacoChannelReferenceAction.DEFAULT_INSERT_TEXT) {
                        return undefined;
                    }
                }
                return getChannelFn().then((channels) => {
                    return {
                        suggestions: channels.map((ch) => ({
                            label: '# ' + ch.name!,
                            kind: monaco.languages.CompletionItemKind.Constant,
                            insertText: `[channel]${ch.name}(${ch.id})[/channel]`,
                            range,
                            detail: 'Channel',
                        })),
                    };
                });
            },
        });
    }

    run(editor: monaco.editor.ICodeEditor) {
        editor.trigger('keyboard', 'type', { text: MonacoChannelReferenceAction.DEFAULT_INSERT_TEXT });
        editor.focus();
    }

    async getChannels(): Promise<ChannelIdAndNameDTO[]> {
        if (this.cachedChannels) {
            return Promise.resolve(this.cachedChannels);
        }
        const response = await firstValueFrom(this.channelService.getPublicChannelsOfCourse(this.metisService.getCourse().id!));
        if (response?.body) {
            this.cachedChannels = response.body;
            return response.body;
        } else {
            return [];
        }
    }

    dispose() {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
    }
}
