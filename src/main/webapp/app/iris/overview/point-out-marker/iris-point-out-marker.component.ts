import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { map } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';
import { faLocationArrow } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { TumUiButtonDirective } from 'app/shared-ui/tum-ui/button/tum-ui-button.directive';
import { IrisMessageContent, getPointOut, isJsonContent } from 'app/iris/shared/entities/iris-content-type.model';
import { IrisPointOut } from 'app/iris/shared/entities/iris-point-out.model';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';

/** A point-out marker together with the label rendered for it. */
interface PointOutMarker {
    data: IrisPointOut;
    label: string;
}

/**
 * Renders the COMMAND markers of a message as clickable navigation chips. Each marker records a
 * point-out Iris performed earlier in the conversation (a slide page and/or a video timestamp in
 * the lecture combined view); clicking one takes the student back to that position, reopening the
 * combined view if it was closed.
 */
@Component({
    selector: 'jhi-iris-point-out-marker',
    templateUrl: './iris-point-out-marker.component.html',
    styleUrl: './iris-point-out-marker.component.scss',
    imports: [TumUiButtonDirective, FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisPointOutMarkerComponent {
    private readonly chatService = inject(IrisChatService);

    private readonly translateService = inject(TranslateService);

    readonly message = input.required<IrisMessage>();

    protected readonly faLocationArrow = faLocationArrow;

    /** Re-computes the labels when the user switches language. */
    private readonly currentLanguage = toSignal(this.translateService.onLangChange.pipe(map((event) => event.lang)), {
        initialValue: this.translateService.getCurrentLang() ?? 'en',
    });

    readonly markers = computed<PointOutMarker[]>(() => {
        // Read so the labels are rebuilt on a language switch.
        this.currentLanguage();
        return this.message()
            .content.map((content) => this.toMarker(content))
            .filter((marker): marker is PointOutMarker => marker !== undefined);
    });

    /**
     * Turns one COMMAND marker into the chip rendered for it, dispatching on the command type it recorded.
     * Supporting a further type means adding a case here; a type without one renders nothing at all.
     * @param content the marker's message content
     * @returns the chip to render, or undefined if this marker has no representation
     */
    private toMarker(content: IrisMessageContent): PointOutMarker | undefined {
        if (!isJsonContent(content)) {
            return undefined;
        }
        // A marker stores the executed command in the same {type, parameters} shape it was sent in.
        const marker = content.attributes;
        switch (marker?.['type']) {
            case 'pointOut': {
                const data = getPointOut(marker);
                return data ? { data, label: this.buildLabel(data) } : undefined;
            }
            default:
                return undefined;
        }
    }

    /**
     * Navigates back to a marker's position, forcing the combined view open if the student closed it.
     * @param data the point-out target recorded on the marker
     */
    protected onMarkerClick(data: IrisPointOut): void {
        this.chatService.navigateToPointOut({ lectureUnitId: data.lectureUnitId, page: data.page, timestamp: data.timestamp, forceOpen: true });
    }

    /**
     * Builds the factual label shown on a marker, e.g.
     * "Navigated to page 3 and timestamp 02:30 in lecture unit Sorting Algorithms".
     * @param data the point-out target
     * @return the translated label
     */
    private buildLabel(data: IrisPointOut): string {
        const targets: string[] = [];
        if (data.page != undefined) {
            targets.push(this.translateService.instant('artemisApp.iris.pointOut.page', { page: data.page }));
        }
        if (data.timestamp != undefined) {
            targets.push(this.translateService.instant('artemisApp.iris.pointOut.timestamp', { time: formatTimestamp(data.timestamp) }));
        }
        const target = targets.join(this.translateService.instant('artemisApp.iris.pointOut.and'));
        if (data.lectureUnitName) {
            return this.translateService.instant('artemisApp.iris.pointOut.label', { target, unit: data.lectureUnitName });
        }
        return this.translateService.instant('artemisApp.iris.pointOut.labelNoUnit', { target });
    }
}

/**
 * Formats a number of seconds as mm:ss, or h:mm:ss for videos of an hour or longer.
 * @param seconds the video position in seconds
 * @return the formatted timestamp
 */
function formatTimestamp(seconds: number): string {
    const total = Math.max(0, Math.floor(seconds));
    const hrs = Math.floor(total / 3600);
    const mins = Math.floor((total % 3600) / 60);
    const secs = total % 60;
    const mm = hrs > 0 ? String(mins).padStart(2, '0') : String(mins);
    const ss = String(secs).padStart(2, '0');
    return hrs > 0 ? `${hrs}:${mm}:${ss}` : `${mm}:${ss}`;
}
