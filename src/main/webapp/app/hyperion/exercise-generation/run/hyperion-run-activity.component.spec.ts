import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { HyperionActivityView } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
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
        const fixture = createWith(view({ liveness: { waitingOnModel: true, since: new Date(BASE - 134_000).toISOString() } }));

        expect(text(fixture, 'hyperion-run-activity-liveness')).toContain('generation.activity.thinking');
        expect(text(fixture, 'hyperion-run-activity-liveness')).toContain('2:14');

        tick(60, fixture);

        expect(text(fixture, 'hyperion-run-activity-liveness')).toContain('3:14');
    });

    it('switches to how long ago the last update was once the agent is no longer waiting', () => {
        const fixture = createWith(view({ liveness: { waitingOnModel: false, since: new Date(BASE - 12_000).toISOString() } }));

        const line = text(fixture, 'hyperion-run-activity-liveness')!;
        expect(line).toContain('generation.activity.lastUpdate');
        expect(line).toContain('12s');
        expect(line).not.toContain('thinking');
        // A per-second live region would be a screen-reader denial of service, so this one is explicitly silent.
        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-activity-liveness"]').getAttribute('aria-live')).toBe('off');
    });

    it('stops the clock when the run ends instead of counting for the rest of the session', () => {
        const fixture = createWith(view({ liveness: { waitingOnModel: true, since: new Date(BASE).toISOString() } }));
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
                liveness: { waitingOnModel: false, since: new Date(BASE).toISOString() },
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
});
