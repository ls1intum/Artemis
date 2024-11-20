import { Component, EventEmitter, Input, Output, computed, inject } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';

@Component({
    selector: 'jhi-emoji-picker',
    templateUrl: './emoji-picker.component.html',
})
export class EmojiPickerComponent {
    private themeService = inject(ThemeService);

    @Input() emojisToShowFilter: (emoji: string | EmojiData) => boolean;
    @Input() categoriesIcons: { [key: string]: string };
    @Input() recent: string[];
    @Output() emojiSelect: EventEmitter<any> = new EventEmitter();

    utils = EmojiUtils;
    dark = computed(() => this.themeService.currentTheme() === Theme.DARK);
    singleImageFunction = computed(() => (this.dark() ? EmojiUtils.singleDarkModeEmojiUrlFn : () => ''));

    onEmojiSelect(event: any) {
        this.emojiSelect.emit(event);
    }
}
