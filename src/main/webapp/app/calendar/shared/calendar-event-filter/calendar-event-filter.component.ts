import { Component, HostListener, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronDown, faXmark } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-calendar-event-filter',
    imports: [FaIconComponent],
    templateUrl: './calendar-event-filter.component.html',
    styleUrl: './calendar-event-filter.component.scss',
})
export class CalendarEventFilterComponent {
    isOpen = signal(false);
    options = ['Exam Events', 'Lecture Events', 'Tutorial Events', 'Exercise Events'];
    selected = signal<string[]>(this.options);

    readonly faChevronDown = faChevronDown;
    readonly faXmark = faXmark;

    toggleOpen() {
        this.isOpen.update((v) => !v);
    }

    remove(item: string) {
        this.selected.update((items) => items.filter((i) => i !== item));
    }

    toggle(option: string) {
        this.selected.update((items) => {
            if (items.includes(option)) {
                return items.filter((i) => i !== option);
            } else {
                return [...items, option];
            }
        });
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: MouseEvent) {
        const target = event.target as HTMLElement;
        if (!target.closest('.multi-select')) {
            this.isOpen.set(false);
        }
    }
}
