import { Component, Input, computed, inject } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';

@Component({
    selector: 'jhi-emoji',
    templateUrl: './emoji.component.html',
    styleUrls: ['./emoji.component.scss'],
})
export class EmojiComponent {
    private themeService = inject(ThemeService);

    utils = EmojiUtils;
    @Input() emoji: string;

    dark = computed(() => this.themeService.currentTheme() === Theme.DARK);
}
