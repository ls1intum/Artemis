import { Component, computed, inject, input, output } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { PickerModule } from '@ctrl/ngx-emoji-mart';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-emoji-picker',
    templateUrl: './emoji-picker.component.html',
    imports: [PickerModule, ArtemisTranslatePipe],
})
export class EmojiPickerComponent {
    private themeService = inject(ThemeService);

    recent = input<string[]>();
    emojiSelect = output<any>();
    emojisToShowFilter = input<(emoji: string | EmojiData) => boolean>();
    categoriesIcons = input<{ [key: string]: string }>({});

    utils = EmojiUtils;
    dark = computed(() => this.themeService.currentTheme() === Theme.DARK);
    singleImageFunction = computed(() => (this.dark() ? EmojiUtils.singleDarkModeEmojiUrlFn : () => ''));

    onEmojiSelect(event: any) {
        this.emojiSelect.emit(event);
    }
}
