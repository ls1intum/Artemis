import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { HyperionActivityLiveness, HyperionActivityView, MODEL_WAIT_STALLED_MS, SILENCE_STALLED_MS } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import { HyperionRunActivityComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-activity.component';

/** The real pipe interpolates; the shared mock drops the parameters, and the durations are exactly what is under test. */
class InterpolatingTranslateService extends MockTranslateService {
    override instant(key: string | string[], interpolateParams?: object): string {
        return interpolateParams ? `${key} ${JSON.stringify(interpolateParams)}` : String(key);
    }
}

const BASE = Date.UTC(2026, 0, 1, 12, 0, 0);

function view(partial: Partial<HyperionActivityView> = {}): HyperionActivityView {
    return { counters: [], recent: [], ended: false, empty: false, ...partial };
}

function counter(key: string, count: number) {
    return { key, labelKey: `artemisApp.hyperion.generation.activity.${key}`, count };
}

/** A silence that started `agoMillis` ago, with the threshold that applies to what is being waited on. */
function liveness(waitingOnModel: boolean, agoMillis: number): HyperionActivityLiveness {
    return {
        waitingOnModel,
        since: new Date(BASE - agoMillis).toISOString(),
        stalledAfterMs: waitingOnModel ? MODEL_WAIT_STALLED_MS : SILENCE_STALLED_MS,
    };
}

describe('HyperionRunActivityComponent', () => {
    let currentMillis: number;
    let now: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        vi.useFakeTimers();
        currentMillis = BASE;
        now = vi.fn(() => dayjs(currentMillis));
        TestBed.configureTestingModule({
            imports: [HyperionRunActivityComponent],
            providers: [
                { provide: TranslateService, useClass: InterpolatingTranslateService },
                { provide: ArtemisServerDateService, useValue: { now } },
            ],
        });
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    function createWith(value: HyperionActivityView): ComponentFixture<HyperionRunActivityComponent> {
        const fixture = TestBed.createComponent(HyperionRunActivityComponent);
        fixture.componentRef.setInput('view', value);
        fixture.detectChanges();
        return fixture;
    }

    function text(fixture: ComponentFixture<HyperionRunActivityComponent>, testId: string): string | undefined {
        return (fixture.nativeElement.querySelector(`[data-testid="${testId}"]`) as HTMLElement | null)?.textContent ?? undefined;
    }

    /** Moves both the clock the component reads and the timers its ticker runs on. */
    function tick(seconds: number, fixture: ComponentFixture<HyperionRunActivityComponent>): void {
        currentMillis += seconds * 1000;
        vi.advanceTimersByTime(seconds * 1000);
        fixture.detectChanges();
    }

    it('says how long the agent has been waiting on the model, and keeps counting', () => {
        const fixture = createWith(view({ liveness: liveness(true, 134_000) }));

        expect(text(fixture, 'hyperion-run-activity-liveness')).toContain('generation.activity.thinking');
        expect(text(fixture, 'hyperion-run-activity-liveness')).toContain('2:14');

        tick(60, fixture);

        expect(text(fixture, 'hyperion-run-activity-liveness')).toContain('3:14');
    });

    it('switches to how long ago the last update was once the agent is no longer waiting', () => {
        const fixture = createWith(view({ liveness: liveness(false, 12_000) }));

        const line = text(fixture, 'hyperion-run-activity-liveness')!;
        expect(line).toContain('generation.activity.lastUpdate');
        expect(line).toContain('12s');
        expect(line).not.toContain('thinking');
        // A per-second live region would be a screen-reader denial of service, so this one is explicitly silent.
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-liveness"]').getAttribute('aria-live')).toBe('off');
    });

    it('stops the clock when the run ends instead of counting for the rest of the session', () => {
        const fixture = createWith(view({ liveness: liveness(true, 0) }));
        tick(2, fixture);
        const callsWhileRunning = now.mock.calls.length;

        fixture.componentRef.setInput('view', view({ ended: true, counters: [counter('turn', 4)] }));
        fixture.detectChanges();
        tick(5, fixture);

        expect(text(fixture, 'hyperion-run-activity-liveness')).toBeUndefined();
        expect(now.mock.calls.length).toBe(callsWhileRunning);
    });

    it('labels every number on the meter', () => {
        const fixture = createWith(view({ counters: [counter('attempt', 2), counter('turn', 7), counter('files', 11)] }));

        const meter = fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-meter"]') as HTMLElement;
        expect([...meter.querySelectorAll('li')].map((entry) => entry.getAttribute('data-counter'))).toEqual(['attempt', 'turn', 'files']);
        expect(meter.textContent).toContain('generation.activity.attempt');
        expect(meter.textContent).toContain('"count":11');
    });

    it('lists the recent messages newest first with their time in its own column', () => {
        const fixture = createWith(
            view({
                recent: [
                    { key: 'a', time: '12:00:30', message: 'Wrote Stack.java' },
                    { key: 'b', time: '12:00:10', message: 'Chose a design' },
                ],
            }),
        );

        const entries = [...fixture.nativeElement.querySelectorAll('[data-testid="hyperion-run-activity-recent"] li')] as HTMLElement[];
        expect(entries.map((entry) => entry.textContent?.trim().replace(/\s+/g, ' '))).toEqual(['12:00:30Wrote Stack.java', '12:00:10Chose a design']);
    });

    it('drops the message log at compact density but keeps the clock and the meter', () => {
        const fixture = TestBed.createComponent(HyperionRunActivityComponent);
        fixture.componentRef.setInput(
            'view',
            view({
                liveness: liveness(false, 0),
                counters: [counter('turn', 3)],
                recent: [{ key: 'a', time: '12:00:30', message: 'Wrote Stack.java' }],
            }),
        );
        fixture.componentRef.setInput('density', 'compact');
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-recent"]')).toBeNull();
        expect(fixture.nativeElement.textContent).not.toContain('Wrote Stack.java');
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-liveness"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-meter"]')).not.toBeNull();
    });

    it('renders nothing at all when there is nothing to report', () => {
        const fixture = createWith(view({ empty: true }));

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity"]')).toBeNull();
    });

    describe('stalled', () => {
        function activity(fixture: ComponentFixture<HyperionRunActivityComponent>): HTMLElement {
            return fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity"]') as HTMLElement;
        }

        it('still reads as working just under the silence threshold', () => {
            const fixture = createWith(view({ liveness: liveness(false, SILENCE_STALLED_MS - 1000) }));

            expect(activity(fixture).getAttribute('data-liveness')).toBe('working');
            expect(text(fixture, 'hyperion-run-activity-liveness')).toContain('generation.activity.lastUpdate');
        });

        it('changes tone and says the silence in words once the threshold is crossed', () => {
            // Minute 14 of a hung run must not look like minute 1, so the growing muted number becomes a statement.
            const fixture = createWith(view({ liveness: liveness(false, SILENCE_STALLED_MS - 1000) }));

            tick(2, fixture);

            expect(activity(fixture).getAttribute('data-liveness')).toBe('stalled');
            expect(text(fixture, 'hyperion-run-activity-liveness')).toContain('generation.activity.stalled');
        });

        it('gives a run waiting on one model call the longer threshold, because a long completion is normal work', () => {
            const fixture = createWith(view({ liveness: liveness(true, SILENCE_STALLED_MS + 1000) }));

            expect(activity(fixture).getAttribute('data-liveness')).toBe('working');
            expect(text(fixture, 'hyperion-run-activity-liveness')).toContain('generation.activity.thinking');

            currentMillis += MODEL_WAIT_STALLED_MS;
            vi.advanceTimersByTime(MODEL_WAIT_STALLED_MS);
            fixture.detectChanges();

            expect(activity(fixture).getAttribute('data-liveness')).toBe('stalled');
        });

        it('only promises the run is still connected while the page can still reach the server', () => {
            const fixture = TestBed.createComponent(HyperionRunActivityComponent);
            fixture.componentRef.setInput('view', view({ liveness: liveness(false, SILENCE_STALLED_MS) }));
            fixture.componentRef.setInput('connected', false);
            fixture.detectChanges();

            expect(text(fixture, 'hyperion-run-activity-liveness')).toContain('generation.activity.stalledOffline');
        });

        it('promotes Cancel into the stage row only once the run has actually stalled', () => {
            const fixture = TestBed.createComponent(HyperionRunActivityComponent);
            fixture.componentRef.setInput('view', view({ liveness: liveness(false, SILENCE_STALLED_MS - 1000) }));
            fixture.componentRef.setInput('cancelAvailable', true);
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-cancel"]')).toBeNull();

            tick(2, fixture);

            expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-cancel"]')).not.toBeNull();
        });

        it('never offers Cancel to somebody who may not cancel, however long the silence lasts', () => {
            const fixture = createWith(view({ liveness: liveness(false, SILENCE_STALLED_MS * 10) }));

            expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-cancel"]')).toBeNull();
        });
    });

    describe('motion', () => {
        it('does not fade in the message log of a run that has already finished', () => {
            // `animate.enter` fires for the initial render of a `@for` too, so an unguarded binding would fade in every
            // row at once when a finished run is opened - motion reporting an arrival that did not happen.
            const fixture = createWith(
                view({
                    ended: true,
                    recent: [
                        { key: 'a', time: '12:00:30', message: 'Wrote Stack.java' },
                        { key: 'b', time: '12:00:10', message: 'Chose a design' },
                    ],
                }),
            );

            const rows = [...fixture.nativeElement.querySelectorAll('[data-testid="hyperion-run-activity-recent"] li')] as HTMLElement[];
            expect(rows.every((row) => !row.classList.contains('hyperion-activity-row-entering'))).toBe(true);
        });
    });
});
