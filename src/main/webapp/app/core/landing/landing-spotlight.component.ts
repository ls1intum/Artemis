import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, afterNextRender, computed, inject, signal } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SPOTLIGHT_STEPS } from 'app/core/landing/landing-data';

@Component({
    selector: 'jhi-landing-spotlight',
    standalone: true,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe],
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
            justify-content: center;
            gap: 8px;
        }

        .stepper-btn {
            background: none;
            border: none;
            cursor: pointer;
            color: var(--text-body-secondary);
            padding: 4px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 14px;
            transition: color 0.2s;
            min-width: 24px;
            min-height: 24px;
        }

        .stepper-btn:hover {
            color: var(--primary);
        }

        .stepper-btn svg {
            width: 16px;
            height: 16px;
        }

        .stepper-dots {
            display: flex;
            align-items: center;
        }

        /* WCAG 2.5.8 (AA) requires ≥ 24×24 px target size. Visual dot stays 8×8 but the
           clickable area is expanded to 28×28 via padding + content-box clipping. */
        .dot {
            box-sizing: content-box;
            width: 8px;
            height: 8px;
            padding: 10px;
            background-clip: content-box;
            border-radius: 50%;
            background-color: var(--gray-300);
            transition:
                background-color 0.3s,
                width 0.3s;
            border: none;
            cursor: pointer;
        }

        .dot.active {
            width: 20px;
            border-radius: 16px;
            background-color: var(--primary);
        }

        /* A neutral tinted background stops the per-step poster from floating on the page
           background while loading and masks any letterboxing. */
        .spotlight-right {
            position: relative;
            display: flex;
            align-items: center;
            justify-content: center;
            border-radius: 12px;
            width: 100%;
            aspect-ratio: 16 / 9;
            height: auto;
            overflow: hidden;
            background: var(--iris-secondary-background);
            transition: opacity 0.3s ease;
        }

        .spotlight-right.fading {
            opacity: 0;
        }

        .spotlight-media {
            width: 100%;
            height: 100%;
            object-fit: cover;
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
                    <button type="button" class="stepper-btn" (click)="prev()" [attr.aria-label]="'landing.spotlight.carousel.previous' | artemisTranslate">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <polyline points="15 6 9 12 15 18" />
                        </svg>
                    </button>
                    <div class="stepper-dots">
                        @for (step of steps; track step.titleKey; let i = $index) {
                            <button
                                type="button"
                                class="dot"
                                [class.active]="i === activeIndex()"
                                (click)="goTo(i)"
                                [attr.aria-label]="'landing.spotlight.carousel.slide' | artemisTranslate: { n: i + 1 }"
                                [attr.aria-current]="i === activeIndex() ? 'step' : undefined"
                            ></button>
                        }
                    </div>
                    <button type="button" class="stepper-btn" (click)="next()" [attr.aria-label]="'landing.spotlight.carousel.next' | artemisTranslate">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <polyline points="9 6 15 12 9 18" />
                        </svg>
                    </button>
                </div>
            </div>
            <div class="spotlight-right" [class.fading]="fading()">
                @if (videosEnabled() && currentStep().videoSrc; as videoSrc) {
                    <video
                        class="spotlight-media"
                        [src]="videoSrc"
                        [autoplay]="true"
                        [muted]="true"
                        playsinline
                        preload="metadata"
                        disablepictureinpicture
                        disableremoteplayback
                        width="960"
                        height="540"
                        [attr.poster]="currentStep().imageSrc"
                        [attr.aria-label]="currentStep().titleKey | artemisTranslate"
                        (loadeddata)="onVideoLoaded($event)"
                        (ended)="onVideoEnded()"
                    >
                        <track kind="captions" srclang="en" label="English" src="content/images/landing/demo-videos/no-audio.vtt" default />
                    </video>
                } @else {
                    <img
                        class="spotlight-media"
                        [src]="currentStep().imageSrc"
                        [alt]="currentStep().titleKey | artemisTranslate"
                        width="960"
                        height="540"
                        decoding="async"
                        [attr.fetchpriority]="activeIndex() === 0 ? 'high' : null"
                        [attr.loading]="activeIndex() === 0 ? 'eager' : 'lazy'"
                    />
                }
            </div>
        </section>
    `,
})
export class LandingSpotlightComponent implements OnInit {
    private static readonly imageStepDurationMs = 5000;
    private static readonly fadeDurationMs = 300;
    /* Delay video hydration until the main thread is idle so the 1 MB+ webm doesn't
       compete with LCP-critical JS/CSS on slow networks. */
    private static readonly videoHydrationDelayMs = 1500;

    private destroyRef = inject(DestroyRef);

    steps = SPOTLIGHT_STEPS;
    activeIndex = signal(0);
    currentStep = computed(() => this.steps[this.activeIndex()]);
    fading = signal(false);
    videosEnabled = signal(false);

    private autoAdvanceTimeoutId: ReturnType<typeof setTimeout> | undefined;
    private fadeTimeoutId: ReturnType<typeof setTimeout> | undefined;
    private videoHydrationTimeoutId: ReturnType<typeof setTimeout> | undefined;
    private videoHydrationIdleCallbackId: number | undefined;

    constructor() {
        afterNextRender(() => this.hydrateVideosWhenIdle());
    }

    ngOnInit(): void {
        this.scheduleAutoAdvance();
        this.destroyRef.onDestroy(() => {
            this.clearAutoAdvanceTimeout();
            clearTimeout(this.fadeTimeoutId);
            clearTimeout(this.videoHydrationTimeoutId);
            if (this.videoHydrationIdleCallbackId !== undefined) {
                const cancel = (globalThis as { cancelIdleCallback?: (handle: number) => void }).cancelIdleCallback;
                cancel?.(this.videoHydrationIdleCallbackId);
                this.videoHydrationIdleCallbackId = undefined;
            }
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

    private hydrateVideosWhenIdle(): void {
        const enable = () => this.videosEnabled.set(true);
        const ric = (globalThis as { requestIdleCallback?: (cb: () => void, opts?: { timeout: number }) => number }).requestIdleCallback;
        if (typeof ric === 'function') {
            this.videoHydrationIdleCallbackId = ric(
                () => {
                    this.videoHydrationIdleCallbackId = undefined;
                    enable();
                },
                { timeout: LandingSpotlightComponent.videoHydrationDelayMs },
            );
            return;
        }
        this.videoHydrationTimeoutId = setTimeout(enable, LandingSpotlightComponent.videoHydrationDelayMs);
    }
}
