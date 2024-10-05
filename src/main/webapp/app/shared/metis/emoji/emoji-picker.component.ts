import { Component, EventEmitter, Input, OnDestroy, Output, inject } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Subscription } from 'rxjs';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';

@Component({
    selector: 'jhi-emoji-picker',
    templateUrl: './emoji-picker.component.html',
})
export class EmojiPickerComponent implements OnDestroy {
    private themeService = inject(ThemeService);

    @Input() emojisToShowFilter: (emoji: string | EmojiData) => boolean;
    @Input() categoriesIcons: { [key: string]: string };
    @Input() recent: string[];
    @Output() emojiSelect: EventEmitter<any> = new EventEmitter();

    utils = EmojiUtils;
    singleImageFunction: (emoji: EmojiData | null) => string;

    dark = false;
    themeSubscription: Subscription;

    constructor() {
        const themeService = this.themeService;

        this.themeSubscription = themeService.getCurrentThemeObservable().subscribe((theme) => {
            this.dark = theme === Theme.DARK;
            this.singleImageFunction = this.dark ? EmojiUtils.singleDarkModeEmojiUrlFn : () => '';
        });
    }

    ngOnDestroy(): void {
        this.themeSubscription.unsubscribe();
    }

    onEmojiSelect(event: any) {
        this.emojiSelect.emit(event);
    }
}
