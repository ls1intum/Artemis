import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faChevronLeft, faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SPOTLIGHT_STEPS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-spotlight',
    standalone: true,
    imports: [FaIconComponent, ArtemisTranslatePipe],
    styles: `
        :host {
            display: block;
        }

        .spotlight {
            display: grid;
            grid-template-columns: 560px 1fr;
            gap: 40px;
            padding: 40px 160px 40px;
            align-items: center;
            min-height: 412px;
            background: var(--iris-primary-background);
        }

        .spotlight-left {
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            height: 100%;
        }

        .spotlight-text {
            display: flex;
            flex-direction: column;
            gap: 24px;
        }

        .spotlight-title {
            font-size: 32px;
            font-weight: 700;
            color: var(--body-color);
            line-height: 1.5;
            margin: 0;
        }

        .spotlight-description {
            font-size: 16px;
            font-weight: 400;
            color: var(--text-body-secondary);
            line-height: 1.6;
            margin: 0;
        }

        .stepper-nav {
            display: flex;
            align-items: center;
            justify-content: space-between;
            width: 160px;
        }

        .stepper-btn {
            background: none;
            border: none;
            cursor: pointer;
            color: var(--text-body-secondary);
            padding: 4px;
            display: flex;
            align-items: center;
            font-size: 14px;
            transition: color 0.2s;
        }

        .stepper-btn:hover {
            color: var(--primary);
        }

        .stepper-dots {
            display: flex;
            gap: 4px;
            align-items: center;
            padding: 8px;
        }

        .dot {
            width: 8px;
            height: 8px;
            border-radius: 50%;
            background: var(--gray-300);
            transition: all 0.3s;
            border: none;
            padding: 0;
            cursor: pointer;
        }

        .dot.active {
            width: 20px;
            border-radius: 16px;
            background: var(--primary);
        }

        .spotlight-right {
            display: flex;
            align-items: center;
            justify-content: center;
            height: 100%;
            overflow: hidden;
            border-radius: 16px;
        }

        .spotlight-image {
            width: 100%;
            height: 100%;
            object-fit: cover;
            border-radius: 16px;
            background: var(--iris-secondary-background);
            min-height: 300px;
        }

        @media (max-width: 1024px) {
            .spotlight {
                padding: 40px;
                grid-template-columns: 1fr;
            }
        }

        @media (max-width: 768px) {
            .spotlight {
                padding: 40px 20px;
            }

            .spotlight-title {
                font-size: 24px;
            }
        }
    `,
    template: `
        <section class="spotlight">
            <div class="spotlight-left">
                <div class="spotlight-text">
                    <h2 class="spotlight-title">{{ currentStep().titleKey | artemisTranslate }}</h2>
                    <p class="spotlight-description">{{ currentStep().descriptionKey | artemisTranslate }}</p>
                </div>
                <div class="stepper-nav">
                    <button class="stepper-btn" (click)="prev()" aria-label="Previous slide">
                        <fa-icon [icon]="faChevronLeft" />
                    </button>
                    <div class="stepper-dots">
                        @for (step of steps; track step.titleKey; let i = $index) {
                            <button
                                class="dot"
                                [class.active]="i === activeIndex()"
                                (click)="goTo(i)"
                                [attr.aria-label]="'Slide ' + (i + 1)"
                                [attr.aria-current]="i === activeIndex() ? 'step' : undefined"
                            ></button>
                        }
                    </div>
                    <button class="stepper-btn" (click)="next()" aria-label="Next slide">
                        <fa-icon [icon]="faChevronRight" />
                    </button>
                </div>
            </div>
            <div class="spotlight-right">
                <img class="spotlight-image" [src]="currentStep().imageSrc" [alt]="currentStep().titleKey" />
            </div>
        </section>
    `,
})
export class LandingSpotlightComponent implements OnInit {
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;

    private destroyRef = inject(DestroyRef);

    steps = SPOTLIGHT_STEPS;
    activeIndex = signal(0);
    currentStep = computed(() => this.steps[this.activeIndex()]);

    private intervalId: ReturnType<typeof setInterval> | undefined;

    ngOnInit(): void {
        this.startAutoAdvance();
        this.destroyRef.onDestroy(() => this.stopAutoAdvance());
    }

    next(): void {
        this.activeIndex.update((i) => (i + 1) % this.steps.length);
        this.resetAutoAdvance();
    }

    prev(): void {
        this.activeIndex.update((i) => (i - 1 + this.steps.length) % this.steps.length);
        this.resetAutoAdvance();
    }

    goTo(index: number): void {
        this.activeIndex.set(index);
        this.resetAutoAdvance();
    }

    private startAutoAdvance(): void {
        this.intervalId = setInterval(() => {
            this.activeIndex.update((i) => (i + 1) % this.steps.length);
        }, 5000);
    }

    private stopAutoAdvance(): void {
        if (this.intervalId) {
            clearInterval(this.intervalId);
            this.intervalId = undefined;
        }
    }

    private resetAutoAdvance(): void {
        this.stopAutoAdvance();
        this.startAutoAdvance();
    }
}
