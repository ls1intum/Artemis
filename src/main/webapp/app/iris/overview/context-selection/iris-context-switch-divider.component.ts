import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { IrisMessageContent, isJsonContent } from 'app/iris/shared/entities/iris-content-type.model';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-session-context.model';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { iconForEntityMode, routeForContext } from './iris-context.util';

/** Transition values are a contract shared with the server and Pyris. */
type IrisContextSwitchTransition = 'added' | 'removed' | 'changed';

/** Server's CTXSWAP marker payload, read with a single cast rather than validated field-by-field. */
interface ContextSwitchMarker {
    transition?: IrisContextSwitchTransition;
    entityMode?: ChatServiceMode;
    entityId?: number;
    name?: string;
}

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
        const contents: IrisMessageContent[] = this.message().content;
        const marker = (contents.find(isJsonContent)?.attributes ?? {}) as ContextSwitchMarker;
        return {
            transition: marker.transition ?? 'added',
            entityIcon: iconForEntityMode(marker.entityMode),
            entityRoute: routeForContext(this.chatService.getCourseId(), marker.entityMode, marker.entityId),
            name: marker.name ?? '',
        };
    });
}
