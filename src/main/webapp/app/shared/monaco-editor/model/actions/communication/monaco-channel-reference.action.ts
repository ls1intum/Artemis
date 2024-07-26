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
        super(MonacoChannelReferenceAction.ID, 'artemisApp.metis.editor.channel', faHashtag);
    }

    register(editor: monaco.editor.IStandaloneCodeEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposableCompletionProvider = this.registerCompletionProviderForCurrentModel<ChannelIdAndNameDTO>(
            editor,
            this.getChannels.bind(this),
            (channel: ChannelIdAndNameDTO, range: monaco.IRange) => ({
                label: '#' + channel.name,
                kind: monaco.languages.CompletionItemKind.Constant,
                insertText: `[channel]${channel.name}(${channel.id})[/channel]`,
                range,
                detail: this.label,
            }),
            '#',
        );
    }

    run(editor: monaco.editor.ICodeEditor) {
        this.typeText(editor, MonacoChannelReferenceAction.DEFAULT_INSERT_TEXT);
        editor.focus();
    }

    async getChannels(): Promise<ChannelIdAndNameDTO[]> {
        if (!this.cachedChannels) {
            const response = await firstValueFrom(this.channelService.getPublicChannelsOfCourse(this.metisService.getCourse().id!));
            this.cachedChannels = response.body!;
        }
        return this.cachedChannels;
    }

    dispose() {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
        this.cachedChannels = undefined;
    }
}
