import { Directive, EventEmitter, HostListener, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subject, Subscription } from 'rxjs';
import { debounceTime } from 'rxjs/operators';

@Directive({
    selector: '[jhiDebounceClick]',
})
export class DebounceClickDirective implements OnInit, OnDestroy {
    @Input() debounceDuration = 500;

    @Output() debounceClick = new EventEmitter();
    private clicks = new Subject();
    private subscription: Subscription;

    constructor() {}

    ngOnInit() {
        this.subscription = this.clicks.pipe(debounceTime(this.debounceDuration)).subscribe((e) => this.debounceClick.emit(e));
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
    }

    @HostListener('click', ['$event'])
    clickEvent(event: Event) {
        event.preventDefault();
        event.stopPropagation();
        this.clicks.next(event);
    }
}
