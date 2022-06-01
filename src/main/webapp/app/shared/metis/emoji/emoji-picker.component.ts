import { Component, Input, Output, OnDestroy, EventEmitter } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Subscription } from 'rxjs';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';

@Component({
    selector: 'jhi-emoji-picker',
    template: `
        <emoji-mart
            [emojisToShowFilter]="emojisToShowFilter"
            [categoriesIcons]="categoriesIcons"
            [emojiSize]="20"
            [showSingleCategory]="true"
            [showPreview]="false"
            [color]="'#5289c7'"
            [recent]="recent"
            [i18n]="{ search: 'artemisApp.metis.searchEmoji' | artemisTranslate, categories: { recent: 'artemisApp.metis.courseEmojiSelectionCategory' | artemisTranslate } }"
            [darkMode]="dark"
            [imageUrlFn]="singleImageFunction"
            [backgroundImageFn]="utils.EMOJI_SHEET_URL"
            (emojiSelect)="onEmojiSelect($event)"
        ></emoji-mart>
    `,
})
export class EmojiPickerComponent implements OnDestroy {
    @Input() emojisToShowFilter: (emoji: string | EmojiData) => boolean;
    @Input() categoriesIcons: { [key: string]: string };
    @Input() recent: string[];
    @Output() emojiSelect: EventEmitter<any> = new EventEmitter();

    utils = EmojiUtils;
    singleImageFunction: (emoji: EmojiData | null) => string;

    dark = false;
    themeSubscription: Subscription;

    constructor(private themeService: ThemeService) {
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
