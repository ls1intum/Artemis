import { EventEmitter, Injectable } from '@angular/core';

@Injectable({
    providedIn: 'root',
})
export class CourseSidebarService {
    public closeSidebar$: EventEmitter<void> = new EventEmitter();
    public openSidebar$: EventEmitter<void> = new EventEmitter();
    public toggleSidebar$: EventEmitter<void> = new EventEmitter();

    constructor() {}

    public closeSidebar(): void {
        this.closeSidebar$.emit();
    }

    public openSidebar(): void {
        this.openSidebar$.emit();
    }

    public toggleSidebar(): void {
        this.toggleSidebar$.emit();
    }
}
