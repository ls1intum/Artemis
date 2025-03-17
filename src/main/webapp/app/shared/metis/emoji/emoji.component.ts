import { Component, computed, inject, input } from '@angular/core';
import { EmojiModule } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';

@Component({
    selector: 'jhi-emoji',
    templateUrl: './emoji.component.html',
    styleUrls: ['./emoji.component.scss'],
    imports: [EmojiModule],
})
export class EmojiComponent {
    private themeService = inject(ThemeService);

    utils = EmojiUtils;
    emoji = input<string>('');

    dark = computed(() => this.themeService.currentTheme() === Theme.DARK);
}
