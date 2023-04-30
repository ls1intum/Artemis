import { ChangeDetectionStrategy, ChangeDetectorRef, Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Theme, ThemeService } from 'app/core/theme/theme.service';
import { Subscription } from 'rxjs';
import { EmojiUtils } from 'app/shared/metis/emoji/emoji.utils';

@Component({
    selector: 'jhi-emoji',
    templateUrl: './emoji.component.html',
    styleUrls: ['./emoji.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmojiComponent implements OnInit, OnDestroy {
    utils = EmojiUtils;

    @Input() emoji: string;

    dark = false;
    themeSubscription: Subscription;

    constructor(private themeService: ThemeService, private cdr: ChangeDetectorRef) {}

    ngOnInit(): void {
        this.themeSubscription = this.themeService.getCurrentThemeObservable().subscribe((theme) => {
            this.dark = theme === Theme.DARK;
            this.cdr.markForCheck();
        });
    }

    ngOnDestroy(): void {
        this.themeSubscription.unsubscribe();
    }
}
