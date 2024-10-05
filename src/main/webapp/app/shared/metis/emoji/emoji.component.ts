import { Component, Input, OnDestroy, inject } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Subscription } from 'rxjs';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';

@Component({
    selector: 'jhi-emoji',
    templateUrl: './emoji.component.html',
    styleUrls: ['./emoji.component.scss'],
})
export class EmojiComponent implements OnDestroy {
    private themeService = inject(ThemeService);

    utils = EmojiUtils;

    @Input() emoji: string;

    dark = false;
    themeSubscription: Subscription;

    constructor() {
        const themeService = this.themeService;

        this.themeSubscription = themeService.getCurrentThemeObservable().subscribe((theme) => {
            this.dark = theme === Theme.DARK;
        });
    }

    ngOnDestroy(): void {
        this.themeSubscription.unsubscribe();
    }
}
