import { Component, Input, computed, inject } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';
import { EmojiComponent as EmojiComponent_1 } from '@ctrl/ngx-emoji-mart/ngx-emoji';

@Component({
    selector: 'jhi-emoji',
    templateUrl: './emoji.component.html',
    styleUrls: ['./emoji.component.scss'],
    standalone: true,
    imports: [EmojiComponent_1],
})
export class EmojiComponent {
    private themeService = inject(ThemeService);

    utils = EmojiUtils;
    @Input() emoji: string;

    dark = computed(() => this.themeService.currentTheme() === Theme.DARK);
}
