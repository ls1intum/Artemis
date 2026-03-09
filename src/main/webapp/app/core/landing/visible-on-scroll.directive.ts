import { Directive, ElementRef, OnDestroy, OnInit, inject, input } from '@angular/core';

@Directive({
    selector: '[jhiVisibleOnScroll]',
    standalone: true,
})
export class VisibleOnScrollDirective implements OnInit, OnDestroy {
    private el = inject(ElementRef);
    private observer: IntersectionObserver | undefined;

    threshold = input<number>(0.15);

    ngOnInit(): void {
        const root = this.el.nativeElement.closest('.page-wrapper');
        this.observer = new IntersectionObserver(
            (entries) => {
                for (const entry of entries) {
                    if (entry.isIntersecting) {
                        entry.target.classList.add('visible');
                        this.observer?.unobserve(entry.target);
                    }
                }
            },
            { root, threshold: this.threshold() },
        );
        this.observer.observe(this.el.nativeElement);
    }

    ngOnDestroy(): void {
        this.observer?.disconnect();
    }
}
