import { Directive, Input, Output, HostBinding, HostListener, EventEmitter } from '@angular/core';

@Directive({
    // tslint:disable-next-line:directive-selector
    selector: '[artemisDropdown]',
    exportAs: 'artemisDropdown',
})
export class DropdownDirective {
    toggleElement: any;

    @Input('open') internalOpen = false;
    @Output() openChange = new EventEmitter<boolean>();

    @HostBinding('class.show') get isOpen(): boolean {
        return this.internalOpen;
    }

    @HostListener('keyup.esc')
    onKeyupEsc(): void {
        this.close();
    }

    @HostListener('document:click', ['$event'])
    onDocumentClick(event: MouseEvent): void {
        if (event.button !== 2 && !this.isEventFromToggle(event)) {
            this.close();
        }
    }

    open(): void {
        if (!this.internalOpen) {
            this.internalOpen = true;
            this.openChange.emit(true);
        }
    }

    close(): void {
        if (this.internalOpen) {
            this.internalOpen = false;
            this.openChange.emit(false);
        }
    }

    toggle(): void {
        if (this.isOpen) {
            this.close();
        } else {
            this.open();
        }
    }

    private isEventFromToggle(event: MouseEvent): boolean {
        return this.toggleElement && this.toggleElement.contains(event.target);
    }
}
