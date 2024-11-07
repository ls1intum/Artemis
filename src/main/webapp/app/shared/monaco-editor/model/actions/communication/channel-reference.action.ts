import { faHashtag } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { TextEditorAction } from 'app/shared/monaco-editor/model/actions/text-editor-action.model';
import { ChannelIdAndNameDTO } from 'app/entities/metis/conversation/channel.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { ChannelService } from 'app/shared/metis/conversations/channel.service';
import { firstValueFrom } from 'rxjs';
import { Disposable } from 'app/shared/monaco-editor/model/actions/monaco-editor.util';
import { TextEditor } from 'app/shared/monaco-editor/model/actions/adapter/text-editor.interface';
import { TextEditorRange } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-range.model';
import { TextEditorCompletionItem, TextEditorCompletionItemKind } from 'app/shared/monaco-editor/model/actions/adapter/text-editor-completion-item.model';

/**
 * Action to insert a reference to a channel into the editor. Users that type a # will see a list of available channels to reference.
 */
export class ChannelReferenceAction extends TextEditorAction {
    static readonly ID = 'channel-reference.action';
    static readonly DEFAULT_INSERT_TEXT = '#';

    cachedChannels?: ChannelIdAndNameDTO[];
    disposableCompletionProvider?: Disposable;

    constructor(
        private readonly metisService: MetisService,
        private readonly channelService: ChannelService,
    ) {
        super(ChannelReferenceAction.ID, 'artemisApp.metis.editor.channel', faHashtag);
    }

    /**
     * Registers this action in the provided editor. This will register a completion provider that shows the available channels.
     * @param editor The editor to register the action in.
     * @param translateService The translate service to use for translations, e.g. the label.
     */
    register(editor: TextEditor, translateService: TranslateService) {
        super.register(editor, translateService);
        this.disposableCompletionProvider = this.registerCompletionProviderForCurrentModel<ChannelIdAndNameDTO>(
            editor,
            this.fetchChannels.bind(this),
            (channel: ChannelIdAndNameDTO, range: TextEditorRange) =>
                new TextEditorCompletionItem(`#${channel.name}`, this.label, `[channel]${channel.name}(${channel.id})[/channel]`, TextEditorCompletionItemKind.Default, range),
            '#',
        );
    }

    /**
     * Inserts the text '#' into the editor and focuses it. This method will trigger the completion provider to show the available channels.
     * @param editor The editor to type the text into.
     */
    run(editor: TextEditor) {
        this.replaceTextAtCurrentSelection(editor, ChannelReferenceAction.DEFAULT_INSERT_TEXT);
        editor.triggerCompletion();
        editor.focus();
    }

    async fetchChannels(): Promise<ChannelIdAndNameDTO[]> {
        if (!this.cachedChannels) {
            const response = await firstValueFrom(this.channelService.getPublicChannelsOfCourse(this.metisService.getCourse().id!));
            this.cachedChannels = response.body!;
        }
        return this.cachedChannels;
    }

    dispose(): void {
        super.dispose();
        this.disposableCompletionProvider?.dispose();
        this.cachedChannels = undefined;
    }
}
