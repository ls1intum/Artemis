import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { LandingSpotlightComponent } from 'app/core/landing/landing-spotlight.component';
import { TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SpotlightStep } from 'app/core/landing/landing-data';

const IMAGE_STEP_DURATION = 5000;
const FADE_DURATION = 300;

const TEST_STEPS: SpotlightStep[] = [
    { titleKey: 'step0.title', descriptionKey: 'step0.desc', imageSrc: 'img0.png', videoSrc: 'video0.webm' },
    { titleKey: 'step1.title', descriptionKey: 'step1.desc', imageSrc: 'img1.png' },
    { titleKey: 'step2.title', descriptionKey: 'step2.desc', imageSrc: 'img2.png', videoSrc: 'video2.webm' },
    { titleKey: 'step3.title', descriptionKey: 'step3.desc', imageSrc: 'img3.png' },
];

describe('LandingSpotlightComponent', () => {
    setupTestBed({ zoneless: true });

    let component: LandingSpotlightComponent;
    let fixture: ComponentFixture<LandingSpotlightComponent>;

    beforeEach(async () => {
        vi.useFakeTimers();

        await TestBed.configureTestingModule({
            imports: [LandingSpotlightComponent],
        })
            .overrideComponent(LandingSpotlightComponent, {
                remove: { imports: [ArtemisTranslatePipe] },
                add: { imports: [TranslatePipeMock] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(LandingSpotlightComponent);
        component = fixture.componentInstance;
        component.steps = TEST_STEPS;
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    it('should have activeIndex 0 on init', () => {
        expect(component.activeIndex()).toBe(0);
        expect(component.currentStep()).toBe(TEST_STEPS[0]);
        expect(component.fading()).toBe(false);
    });

    describe('next()', () => {
        it('should advance to the next step', () => {
            component.ngOnInit();
            component.next();
            vi.advanceTimersByTime(FADE_DURATION);

            expect(component.activeIndex()).toBe(1);
        });

        it('should wrap from last step to first', () => {
            component.ngOnInit();
            component.goTo(TEST_STEPS.length - 1);
            vi.advanceTimersByTime(FADE_DURATION);
            expect(component.activeIndex()).toBe(3);

            component.next();
            vi.advanceTimersByTime(FADE_DURATION);

            expect(component.activeIndex()).toBe(0);
        });
    });

    describe('prev()', () => {
        it('should go to the previous step', () => {
            component.ngOnInit();
            component.goTo(2);
            vi.advanceTimersByTime(FADE_DURATION);
            expect(component.activeIndex()).toBe(2);

            component.prev();
            vi.advanceTimersByTime(FADE_DURATION);

            expect(component.activeIndex()).toBe(1);
        });

        it('should wrap from first step to last', () => {
            component.ngOnInit();
            expect(component.activeIndex()).toBe(0);

            component.prev();
            vi.advanceTimersByTime(FADE_DURATION);

            expect(component.activeIndex()).toBe(TEST_STEPS.length - 1);
        });
    });

    describe('goTo()', () => {
        it('should navigate to the given index', () => {
            component.ngOnInit();
            component.goTo(2);
            vi.advanceTimersByTime(FADE_DURATION);

            expect(component.activeIndex()).toBe(2);
            expect(component.currentStep()).toBe(TEST_STEPS[2]);
        });

        it('should not transition when navigating to current index', () => {
            component.ngOnInit();
            component.goTo(0);
            vi.advanceTimersByTime(FADE_DURATION);

            expect(component.activeIndex()).toBe(0);
            expect(component.fading()).toBe(false);
        });
    });

    describe('fading state', () => {
        it('should set fading to true during transition', () => {
            component.ngOnInit();
            component.next();

            expect(component.fading()).toBe(true);

            vi.advanceTimersByTime(FADE_DURATION);

            expect(component.fading()).toBe(false);
        });

        it('should not set fading when navigating to same index', () => {
            component.ngOnInit();
            component.goTo(0);

            expect(component.fading()).toBe(false);
        });
    });

    describe('auto-advance', () => {
        it('should auto-advance for image-only steps after timeout', () => {
            component.ngOnInit();
            // Navigate to step 1 which is image-only
            component.goTo(1);
            vi.advanceTimersByTime(FADE_DURATION);
            expect(component.activeIndex()).toBe(1);

            // Wait for auto-advance timeout
            vi.advanceTimersByTime(IMAGE_STEP_DURATION);
            // Now fading starts, wait for fade to complete
            vi.advanceTimersByTime(FADE_DURATION);

            expect(component.activeIndex()).toBe(2);
        });

        it('should not auto-advance for video steps', () => {
            component.ngOnInit();
            // Step 0 has videoSrc, should not auto-advance
            expect(component.activeIndex()).toBe(0);

            vi.advanceTimersByTime(IMAGE_STEP_DURATION + FADE_DURATION);

            expect(component.activeIndex()).toBe(0);
        });
    });

    describe('onVideoEnded()', () => {
        it('should advance to next step when video ends', () => {
            component.ngOnInit();
            expect(component.currentStep().videoSrc).toBeDefined();

            component.onVideoEnded();
            vi.advanceTimersByTime(FADE_DURATION);

            expect(component.activeIndex()).toBe(1);
        });

        it('should not advance if currently fading', () => {
            component.ngOnInit();
            // Start a transition to trigger fading
            component.next();
            expect(component.fading()).toBe(true);

            component.onVideoEnded();
            vi.advanceTimersByTime(FADE_DURATION);

            // Should have advanced only once (from next()), not twice
            expect(component.activeIndex()).toBe(1);
        });
    });

    describe('onVideoLoaded()', () => {
        it('should reset video and play', () => {
            const mockVideo = {
                currentTime: 10,
                play: vi.fn().mockResolvedValue(undefined),
            } as unknown as HTMLVideoElement;

            component.onVideoLoaded({ target: mockVideo } as unknown as Event);

            expect(mockVideo.currentTime).toBe(0);
            expect(mockVideo.play).toHaveBeenCalledOnce();
        });

        it('should handle null target gracefully', () => {
            expect(() => component.onVideoLoaded({ target: null } as unknown as Event)).not.toThrow();
        });
    });
});
