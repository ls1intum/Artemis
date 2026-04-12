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
            grid-template-columns: 400px 1fr;
            gap: 40px;
            padding: 40px 160px 40px;
            align-items: center;
            min-height: 450px;
        }

        .spotlight-left {
            display: flex;
            flex-direction: column;
            justify-content: space-between;
            height: 100%;
        }

        .spotlight-text {
            display: grid;
        }

        .spotlight-text > * {
            grid-area: 1 / 1;
            display: flex;
            flex-direction: column;
            gap: 24px;
            transition: opacity 0.3s ease;
            opacity: 0;
            pointer-events: none;
        }

        .spotlight-text > .active {
            opacity: 1;
            pointer-events: auto;
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
            border-radius: 8px;
            height: 100%;
            overflow: hidden;
            transition: opacity 0.3s ease;
        }

        .spotlight-right.fading {
            opacity: 0;
        }

        .spotlight-media {
            width: 100%;
            height: 100%;
            object-fit: contain;
            min-height: 25rem;
            border: 0;
            box-shadow: none;
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
                    @for (step of steps; track step.titleKey; let i = $index) {
                        <div [class.active]="i === activeIndex()" [attr.aria-hidden]="i !== activeIndex() ? true : null" [attr.tabindex]="i === activeIndex() ? 0 : -1">
                            <h2 class="spotlight-title">{{ step.titleKey | artemisTranslate }}</h2>
                            <p class="spotlight-description">{{ step.descriptionKey | artemisTranslate }}</p>
                        </div>
                    }
                </div>
                <div class="stepper-nav">
                    <button class="stepper-btn" (click)="prev()" [attr.aria-label]="'landing.spotlight.carousel.previous' | artemisTranslate">
                        <fa-icon [icon]="faChevronLeft" />
                    </button>
                    <div class="stepper-dots">
                        @for (step of steps; track step.titleKey; let i = $index) {
                            <button
                                class="dot"
                                [class.active]="i === activeIndex()"
                                (click)="goTo(i)"
                                [attr.aria-label]="'landing.spotlight.carousel.slide' | artemisTranslate: { n: i + 1 }"
                                [attr.aria-current]="i === activeIndex() ? 'step' : undefined"
                            ></button>
                        }
                    </div>
                    <button class="stepper-btn" (click)="next()" [attr.aria-label]="'landing.spotlight.carousel.next' | artemisTranslate">
                        <fa-icon [icon]="faChevronRight" />
                    </button>
                </div>
            </div>
            <div class="spotlight-right" [class.fading]="fading()">
                @if (currentStep().videoSrc; as videoSrc) {
                    <video
                        class="spotlight-media"
                        [src]="videoSrc"
                        [autoplay]="true"
                        [muted]="true"
                        playsinline
                        preload="metadata"
                        [attr.poster]="currentStep().imageSrc"
                        (loadeddata)="onVideoLoaded($event)"
                        (ended)="onVideoEnded()"
                    ></video>
                } @else {
                    <img class="spotlight-media" [src]="currentStep().imageSrc" [alt]="currentStep().titleKey | artemisTranslate" />
                }
            </div>
        </section>
    `,
})
export class LandingSpotlightComponent implements OnInit {
    protected readonly faChevronLeft = faChevronLeft;
    protected readonly faChevronRight = faChevronRight;
    private static readonly imageStepDurationMs = 5000;
    private static readonly fadeDurationMs = 300;

    private destroyRef = inject(DestroyRef);

    steps = SPOTLIGHT_STEPS;
    activeIndex = signal(0);
    currentStep = computed(() => this.steps[this.activeIndex()]);
    fading = signal(false);

    private autoAdvanceTimeoutId: ReturnType<typeof setTimeout> | undefined;
    private fadeTimeoutId: ReturnType<typeof setTimeout> | undefined;

    ngOnInit(): void {
        this.scheduleAutoAdvance();
        this.destroyRef.onDestroy(() => {
            this.clearAutoAdvanceTimeout();
            clearTimeout(this.fadeTimeoutId);
        });
    }

    next(): void {
        this.advanceTo(this.activeIndex() + 1);
    }

    prev(): void {
        this.advanceTo(this.activeIndex() - 1);
    }

    goTo(index: number): void {
        this.advanceTo(index);
    }

    onVideoEnded(): void {
        if (this.fading()) {
            return;
        }
        if (!this.currentStep().videoSrc) {
            return;
        }

        this.advanceTo(this.activeIndex() + 1);
    }

    onVideoLoaded(event: Event): void {
        const videoElement = event.target as HTMLVideoElement | null;
        if (!videoElement) {
            return;
        }

        videoElement.currentTime = 0;
        void videoElement.play().catch(() => {});
    }

    private advanceTo(index: number): void {
        const normalizedIndex = ((index % this.steps.length) + this.steps.length) % this.steps.length;
        if (normalizedIndex === this.activeIndex()) {
            return;
        }

        this.fading.set(true);
        clearTimeout(this.fadeTimeoutId);
        this.fadeTimeoutId = setTimeout(() => {
            this.fadeTimeoutId = undefined;
            this.activeIndex.set(normalizedIndex);
            this.fading.set(false);
            this.scheduleAutoAdvance();
        }, LandingSpotlightComponent.fadeDurationMs);
    }

    private scheduleAutoAdvance(): void {
        this.clearAutoAdvanceTimeout();

        if (this.currentStep().videoSrc) {
            return;
        }

        this.autoAdvanceTimeoutId = setTimeout(() => this.advanceTo(this.activeIndex() + 1), LandingSpotlightComponent.imageStepDurationMs);
    }

    private clearAutoAdvanceTimeout(): void {
        if (!this.autoAdvanceTimeoutId) {
            return;
        }

        clearTimeout(this.autoAdvanceTimeoutId);
        this.autoAdvanceTimeoutId = undefined;
    }
}
