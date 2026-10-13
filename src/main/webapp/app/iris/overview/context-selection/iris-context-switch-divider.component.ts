import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisContextSwitchTransition, iconForEntityMode, parseContextSwitchMarker, routeForContext } from './iris-context.util';

interface ContextSwitchInfo {
    transition: IrisContextSwitchTransition;
    entityIcon: IconProp | undefined;
    entityRoute: string | undefined;
    name: string;
}

@Component({
    selector: 'jhi-iris-context-switch-divider',
    templateUrl: './iris-context-switch-divider.component.html',
    styleUrls: ['./iris-context-switch-divider.component.scss'],
    imports: [RouterLink, FaIconComponent, TranslateDirective],
    changeDetection: ChangeDetectionStrategy.OnPush,
    encapsulation: ViewEncapsulation.None,
})
export class IrisContextSwitchDividerComponent {
    private readonly chatService = inject(IrisChatService);

    readonly message = input.required<IrisMessage>();

    readonly contextSwitch = computed<ContextSwitchInfo>(() => {
        const marker = parseContextSwitchMarker(this.message().content);
        return {
            transition: marker.transition ?? 'added',
            entityIcon: iconForEntityMode(marker.entityMode),
            entityRoute: routeForContext(this.chatService.getCourseId(), marker.entityMode, marker.entityId),
            name: marker.name ?? '',
        };
    });
}
