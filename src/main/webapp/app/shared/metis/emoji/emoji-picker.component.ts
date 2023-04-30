import { ChangeDetectionStrategy, ChangeDetectorRef, Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Subscription } from 'rxjs';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';

@Component({
    selector: 'jhi-emoji-picker',
    templateUrl: './emoji-picker.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
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

    constructor(private themeService: ThemeService, private cdr: ChangeDetectorRef) {
        this.themeSubscription = themeService.getCurrentThemeObservable().subscribe((theme) => {
            this.dark = theme === Theme.DARK;
            this.singleImageFunction = this.dark ? EmojiUtils.singleDarkModeEmojiUrlFn : () => '';
            this.cdr.detectChanges();
        });
    }

    ngOnDestroy(): void {
        this.themeSubscription.unsubscribe();
    }

    onEmojiSelect(event: any) {
        this.emojiSelect.emit(event);
    }
}
