import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { IrisRunState } from 'app/iris/shared/entities/iris-activity.model';
import { IrisRunInfo } from 'app/iris/overview/services/iris-chat.service';

@Component({
    selector: 'jhi-chat-status-bar',
    templateUrl: './chat-status-bar.component.html',
    styleUrl: './chat-status-bar.component.scss',
    imports: [FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChatStatusBarComponent {
    private readonly translateService = inject(TranslateService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

    readonly runInfo = input<IrisRunInfo | undefined>(undefined);

    protected readonly faCircleXmark = faCircleXmark;
    protected readonly isFailed = computed(() => this.runInfo()?.state === IrisRunState.FAILED);
    protected readonly errorMessage = computed(() => {
        this.currentLocale();
        const message = this.runInfo()?.error?.message ?? 'artemisApp.iris.error.internal';
        const translated = this.translateService.instant(message);
        if (typeof translated === 'string' && !translated.startsWith('translation-not-found[')) {
            return translated;
        }
        return message;
    });
}
