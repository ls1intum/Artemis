import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { activityView } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import { runOutcome, stageStates } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { HyperionRunProgressComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-progress.component';
import { HyperionGenerationActivity, HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

let clock = 0;

function event(partial: Partial<HyperionGenerationEvent> & Pick<HyperionGenerationEvent, 'type'>): HyperionGenerationEvent {
    return { timestamp: new Date(Date.UTC(2026, 0, 1, 12, 0, clock++)).toISOString(), ...partial };
}

function activity(partial: Partial<HyperionGenerationActivity> = {}): HyperionGenerationActivity {
    return { attempt: 1, turn: 1, waitingOnModel: false, modelCalls: 0, toolCalls: 0, filesWritten: 0, ...partial };
}

describe('HyperionRunProgressComponent', () => {
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HyperionRunProgressComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        });
    });

    function createWith(events: readonly HyperionGenerationEvent[], density: 'full' | 'compact' = 'full'): ComponentFixture<HyperionRunProgressComponent> {
        const outcome = runOutcome(events);
        const fixture = TestBed.createComponent(HyperionRunProgressComponent);
        fixture.componentRef.setInput('stages', stageStates(events, outcome));
        fixture.componentRef.setInput('activity', activityView(events, outcome));
        fixture.componentRef.setInput('liveMessage', events.findLast((candidate) => candidate.message)?.message);
        fixture.componentRef.setInput('density', density);
        fixture.detectChanges();
        return fixture;
    }

    function substepStates(fixture: ComponentFixture<HyperionRunProgressComponent>): Record<string, string> {
        const substeps = [...fixture.nativeElement.querySelectorAll('[data-substep]')] as HTMLElement[];
        return Object.fromEntries(substeps.map((substep) => [substep.getAttribute('data-substep'), substep.getAttribute('data-state')]));
    }

    it('renders the design substeps as a nested ladder inside the design step, and nowhere else', () => {
        const fixture = createWith([
            event({ type: 'STARTED', phase: 'PREPARING' }),
            event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ step: 'concept' }) }),
            event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ step: 'spec' }) }),
        ]);

        const design = fixture.nativeElement.querySelector('[data-stage="design"]') as HTMLElement;
        expect(design.querySelectorAll('[data-testid="hyperion-run-substeps"]')).toHaveLength(1);
        expect(fixture.nativeElement.querySelectorAll('[data-testid="hyperion-run-substeps"]')).toHaveLength(1);
        expect(substepStates(fixture)).toEqual({ concept: 'complete', spec: 'current', artifacts: 'pending', statement: 'pending' });
    });

    it('reports the activity under the stage that is running, in one live region only', () => {
        const fixture = createWith([
            event({ type: 'STARTED', phase: 'PREPARING' }),
            event({ type: 'PROGRESS', phase: 'DESIGNING', message: 'Asking the model', activity: activity({ turn: 3, waitingOnModel: true, modelCalls: 2 }) }),
        ]);

        const design = fixture.nativeElement.querySelector('[data-stage="design"]') as HTMLElement;
        expect(design.querySelector('[data-testid="hyperion-run-activity"]')).not.toBeNull();
        expect(design.textContent).toContain('Asking the model');
        expect(fixture.nativeElement.querySelectorAll('[role="status"]')).toHaveLength(1);
        // The ticking clock must stay outside the live region that announces the stage line.
        expect(fixture.nativeElement.querySelector('[role="status"] [data-testid="hyperion-run-activity-liveness"]')).toBeNull();
    });

    it('keeps the last thing the agent said visible under the stage the run stopped in', () => {
        const fixture = createWith([
            event({ type: 'STARTED', phase: 'PREPARING' }),
            event({ type: 'PROGRESS', phase: 'DESIGNING', message: 'Writing tests', activity: activity({ turn: 2 }) }),
            event({ type: 'ERROR', message: 'The model failed' }),
        ]);

        const design = fixture.nativeElement.querySelector('[data-stage="design"]') as HTMLElement;
        expect(design.getAttribute('data-state')).toBe('failed');
        expect(design.querySelector('[data-testid="hyperion-run-activity-recent"]')).not.toBeNull();
        expect(design.querySelector('[data-testid="hyperion-run-activity-liveness"]')).toBeNull();
        expect(substepStates(fixture)).toEqual({});
    });

    it('says what a finished stage got done', () => {
        const fixture = createWith([
            event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ turn: 1, filesWritten: 4 }) }),
            event({ type: 'PROGRESS', phase: 'DESIGNING', activity: activity({ turn: 2, filesWritten: 11 }) }),
            event({ type: 'PROGRESS', phase: 'VERIFYING', activity: activity({ turn: 3, filesWritten: 11 }) }),
        ]);

        const design = fixture.nativeElement.querySelector('[data-stage="design"]') as HTMLElement;
        expect(design.querySelector('[data-testid="hyperion-run-stage-summary"]')?.textContent).toContain('generation.stageSummary');
        // The stage that is running has finished nothing yet, so it claims nothing.
        expect(fixture.nativeElement.querySelector('[data-stage="build"] [data-testid="hyperion-run-stage-summary"]')).toBeNull();
    });

    it('does not preview substeps of a stage the run has not reached', () => {
        const fixture = createWith([event({ type: 'STARTED', phase: 'PREPARING', activity: activity() })]);

        expect(fixture.nativeElement.querySelector('[data-testid="hyperion-run-substeps"]')).toBeNull();
    });
});
