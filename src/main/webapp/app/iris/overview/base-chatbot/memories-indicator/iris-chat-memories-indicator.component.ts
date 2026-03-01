import { ChangeDetectionStrategy, Component, Signal, computed, input, signal, viewChild } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBrain } from '@fortawesome/free-solid-svg-icons';
import { MemirisMemory } from 'app/iris/shared/entities/memiris.model';
import { Popover } from 'primeng/popover';
import { TooltipModule } from 'primeng/tooltip';

@Component({
    selector: 'jhi-iris-chat-memories-indicator',
    templateUrl: './iris-chat-memories-indicator.component.html',
    styleUrls: ['./iris-chat-memories-indicator.component.scss'],
    standalone: true,
    imports: [FaIconComponent, Popover, TooltipModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisChatMemoriesIndicatorComponent {
    readonly accessedMemories = input<MemirisMemory[] | undefined>(undefined);
    readonly createdMemories = input<MemirisMemory[] | undefined>(undefined);

    readonly usedCount = computed(() => this.accessedMemories()?.length ?? 0);
    readonly createdCount = computed(() => this.createdMemories()?.length ?? 0);
    readonly show = computed(() => this.usedCount() > 0 || this.createdCount() > 0);

    readonly compactLabel = computed(() => this.buildCompactLabel(this.usedCount(), this.createdCount()));
    readonly tooltipText: Signal<string> = computed(() => this.buildTooltipText(this.usedCount(), this.createdCount()));

    readonly faBrain = faBrain;
    readonly popover = viewChild<Popover>('popover');
    readonly isOpen = signal(false);

    togglePopover(event: MouseEvent): void {
        event.stopPropagation();
        this.popover()?.toggle(event, event.currentTarget);
    }

    onShow(): void {
        this.isOpen.set(true);
    }

    onHide(): void {
        this.isOpen.set(false);
    }

    private buildTooltipText(used: number, created: number): string {
        const parts: string[] = [];
        if (used > 0) {
            parts.push(used === 1 ? '1 memory used' : `${used} memories used`);
        }
        if (created > 0) {
            parts.push(created === 1 ? '1 created' : `${created} created`);
        }
        return parts.join(', ');
    }

    private buildCompactLabel(used: number, created: number): string {
        const parts: string[] = [];
        if (used > 0) {
            parts.push(`${used} used`);
        }
        if (created > 0) {
            parts.push(`${created} created`);
        }
        return parts.join(' · ');
    }
}
