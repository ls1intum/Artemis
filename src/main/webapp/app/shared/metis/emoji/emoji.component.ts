import { Component, Input, OnDestroy } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Subscription } from 'rxjs';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';

@Component({
    selector: 'jhi-emoji',
    template: `
        <!-- Using two instances to 'rerender' if the theme changes -->
        <ngx-emoji *ngIf="!dark" [backgroundImageFn]="utils.EMOJI_SHEET_URL" class="emoji" [emoji]="emoji" [size]="16"></ngx-emoji>
        <ngx-emoji *ngIf="dark" [imageUrlFn]="utils.singleDarkModeEmojiUrlFn" [backgroundImageFn]="utils.EMOJI_SHEET_URL" class="emoji" [emoji]="emoji" [size]="16"></ngx-emoji>
    `,
    styles: [
        `
            :host {
                height: 16px;
                display: inline;
            }
        `,
    ],
})
export class EmojiComponent implements OnDestroy {
    utils = EmojiUtils;

    @Input() emoji: string;

    dark = false;
    themeSubscription: Subscription;

    constructor(private themeService: ThemeService) {
        this.themeSubscription = themeService.getCurrentThemeObservable().subscribe((theme) => {
            this.dark = theme === Theme.DARK;
        });
    }

    ngOnDestroy(): void {
        this.themeSubscription.unsubscribe();
    }
}
