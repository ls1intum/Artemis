import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBookmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'div[jhi-saved-posting-banner]',
    imports: [FaIconComponent, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
    host: { '[class.is-saved]': 'consecutive()' },
    styles: '.post-is-saved-message-content { padding-left: 0.25rem; }',
    template: `
        <div class="post-is-saved-message-content">
            <fa-icon [icon]="faBookmark" />
            <span class="text-secondary" jhiTranslate="artemisApp.communication.post.saved"></span>
        </div>
    `,
})
export class SavedPostingBannerComponent {
    readonly consecutive = input(false);
    protected readonly faBookmark = faBookmark;
}
