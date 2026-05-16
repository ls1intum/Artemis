import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisJsonMessageContent, isJsonContent } from 'app/iris/shared/entities/iris-content-type.model';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { iconForEntityMode, routeForContext } from './iris-context.util';

type ContextSwitchTransition = 'added' | 'removed' | 'changed';

interface ContextSwitchInfo {
    transition: ContextSwitchTransition;
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
        const jsonContent = this.message().content?.find((c) => isJsonContent(c)) as IrisJsonMessageContent | undefined;
        const transition = jsonContent?.attributes?.['transition'] as ContextSwitchTransition | undefined;
        const entityMode = jsonContent?.attributes?.['entityMode'] as string | undefined;
        const entityId = jsonContent?.attributes?.['entityId'] as number | undefined;
        const name = (jsonContent?.attributes?.['name'] as string | undefined) ?? '';
        return {
            transition: transition ?? 'added',
            entityIcon: iconForEntityMode(entityMode),
            entityRoute: routeForContext(this.chatService.getCourseId(), entityMode as ChatServiceMode | undefined, entityId),
            name,
        };
    });
}
