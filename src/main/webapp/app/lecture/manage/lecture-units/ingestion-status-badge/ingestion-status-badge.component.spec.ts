import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe } from 'ng-mocks';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { IngestionStatusBadgeComponent } from 'app/lecture/manage/lecture-units/ingestion-status-badge/ingestion-status-badge.component';
import { LectureUnitProcessingStatus, ProcessingPhase } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';

describe('IngestionStatusBadgeComponent', () => {
    let fixture: ComponentFixture<IngestionStatusBadgeComponent>;
    let component: IngestionStatusBadgeComponent;

    const status = (overrides: Partial<LectureUnitProcessingStatus>): LectureUnitProcessingStatus => ({
        lectureUnitId: 1,
        phase: ProcessingPhase.IDLE,
        retryCount: 0,
        ...overrides,
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IngestionStatusBadgeComponent],
        })
            .overrideComponent(IngestionStatusBadgeComponent, {
                remove: { imports: [NgbTooltip, TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(NgbTooltip), MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(IngestionStatusBadgeComponent);
        component = fixture.componentInstance;
    });

    const badge = (): HTMLElement | null => fixture.nativeElement.querySelector('[data-testid="ingestion-status-badge"]');

    it('should render nothing without a status and without awaiting', () => {
        fixture.detectChanges();
        expect(badge()).toBeNull();
    });

    it('should render the queued state for awaiting units', () => {
        fixture.componentRef.setInput('awaiting', true);
        fixture.detectChanges();
        expect(badge()?.getAttribute('data-state')).toBe('queued');
    });

    it.each([
        [ProcessingPhase.TRANSCRIBING, 'transcribing'],
        [ProcessingPhase.INGESTING, 'indexing'],
        [ProcessingPhase.DONE, 'done'],
        [ProcessingPhase.FAILED, 'failed'],
        [ProcessingPhase.SKIPPED, 'skipped'],
    ])('should map phase %s to state %s', (phase: ProcessingPhase, expected: string) => {
        fixture.componentRef.setInput('status', status({ phase }));
        fixture.detectChanges();
        expect(badge()?.getAttribute('data-state')).toBe(expected);
    });

    it('should render the verifying state during the audit stage', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, stageName: 'audit' }));
        fixture.detectChanges();
        expect(badge()?.getAttribute('data-state')).toBe('verifying');
    });

    it.each([
        [ProcessingPhase.TRANSCRIBING, undefined, 'info'],
        [ProcessingPhase.INGESTING, undefined, 'info'],
        [ProcessingPhase.INGESTING, 'audit', 'info'],
        [ProcessingPhase.DONE, undefined, 'success'],
        [ProcessingPhase.FAILED, undefined, 'danger'],
        [ProcessingPhase.SKIPPED, undefined, 'secondary'],
    ])('should map phase %s (stage %s) to severity %s', (phase: ProcessingPhase, stageName: string | undefined, expected: string) => {
        fixture.componentRef.setInput('status', status({ phase, stageName }));
        fixture.detectChanges();
        expect(component.severity()).toBe(expected);
    });

    it('should use the secondary severity for the queued state', () => {
        fixture.componentRef.setInput('awaiting', true);
        fixture.detectChanges();
        expect(component.severity()).toBe('secondary');
    });

    it.each([
        ['vision', 'artemisApp.attachmentVideoUnit.processing.stage.readingSlides'],
        ['segment-summaries', 'artemisApp.attachmentVideoUnit.processing.stage.summarizingSlides'],
        ['embedding', 'artemisApp.attachmentVideoUnit.processingIngesting'],
        ['transcript-summaries', 'artemisApp.attachmentVideoUnit.processing.stage.summarizingTranscript'],
        ['transcript-embedding', 'artemisApp.attachmentVideoUnit.processing.stage.indexingTranscript'],
        ['audit', 'artemisApp.attachmentVideoUnit.processingVerifying'],
    ])('should map the ingesting stage %s to its label key', (stageName: string, expectedKey: string) => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, stageName }));
        fixture.detectChanges();
        expect(component.labelKey()).toBe(expectedKey);
    });

    it('should fall back to the generic indexing label for an unknown stage', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, stageName: 'something-new' }));
        fixture.detectChanges();
        expect(component.labelKey()).toBe('artemisApp.attachmentVideoUnit.processingIngesting');
    });

    it('should label a completed unit as indexed', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.DONE }));
        fixture.detectChanges();
        expect(component.labelKey()).toBe('artemisApp.attachmentVideoUnit.processingComplete');
    });

    it('should render the live stage counter while running', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, stageName: 'vision', stageProgress: 41, stageTotal: 142 }));
        fixture.detectChanges();

        const counter: HTMLElement | null = fixture.nativeElement.querySelector('[data-testid="ingestion-stage-counter"]');
        expect(counter?.textContent?.trim()).toBe('41/142');
    });

    it('should not render a counter for stages without progress or for terminal states', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.TRANSCRIBING }));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="ingestion-stage-counter"]')).toBeNull();

        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.DONE, stageName: 'vision', stageProgress: 41, stageTotal: 142 }));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="ingestion-stage-counter"]')).toBeNull();
    });

    it('should render a live elapsed readout while running', () => {
        vi.useFakeTimers();
        try {
            vi.setSystemTime(new Date('2026-01-01T00:01:30.000Z'));
            const localFixture = TestBed.createComponent(IngestionStatusBadgeComponent);
            localFixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, stageName: 'embedding', startedAt: '2026-01-01T00:00:00.000Z' }));
            localFixture.detectChanges();

            expect(localFixture.componentInstance.elapsed()).toBe('1m 30s');
            const elapsed: HTMLElement | null = localFixture.nativeElement.querySelector('[data-testid="ingestion-elapsed"]');
            expect(elapsed?.textContent?.trim()).toBe('1m 30s');
        } finally {
            vi.useRealTimers();
        }
    });

    it('should not render an elapsed readout for terminal states', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.DONE, startedAt: '2026-01-01T00:00:00.000Z' }));
        fixture.detectChanges();
        expect(component.elapsed()).toBeUndefined();
        expect(fixture.nativeElement.querySelector('[data-testid="ingestion-elapsed"]')).toBeNull();
    });

    it('should overlay a running unit as lost when lease renewals stop arriving', () => {
        fixture.componentRef.setInput(
            'status',
            status({
                phase: ProcessingPhase.INGESTING,
                stageName: 'vision',
                stageProgress: 11,
                stageTotal: 18,
                lastHeartbeatAt: new Date(Date.now() - 60_000).toISOString(),
                receivedAt: Date.now() - 60_000,
            }),
        );
        fixture.detectChanges();
        expect(component.state()).toBe('lost');
        expect(component.isRunning()).toBe(false);
        // The last known counter stays visible ("last seen at 11/18"), the running clock does not.
        expect(component.progressText()).toBe('11/18');
        expect(component.elapsed()).toBeUndefined();
        expect(component.lastSeenAgo()).toBeDefined();
        expect(badge()?.getAttribute('data-state')).toBe('lost');
    });

    it('should keep a running unit running while lease renewals are fresh', () => {
        fixture.componentRef.setInput(
            'status',
            status({
                phase: ProcessingPhase.INGESTING,
                stageName: 'vision',
                stageProgress: 11,
                stageTotal: 18,
                lastHeartbeatAt: new Date().toISOString(),
                receivedAt: Date.now(),
            }),
        );
        fixture.detectChanges();
        expect(component.state()).toBe('indexing');
        expect(component.lostContact()).toBe(false);
    });

    it('should mark a run lost on load when its server heartbeat is minutes old despite a fresh receipt', () => {
        // Initial page-load snapshot of a dead run: receivedAt is fresh, the server stamp is not.
        fixture.componentRef.setInput(
            'status',
            status({
                phase: ProcessingPhase.INGESTING,
                lastHeartbeatAt: new Date(Date.now() - 10 * 60_000).toISOString(),
                receivedAt: Date.now(),
            }),
        );
        fixture.detectChanges();
        expect(component.state()).toBe('lost');
    });

    it('should never mark a legacy run without worker heartbeats as lost', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, receivedAt: Date.now() - 600_000 }));
        fixture.detectChanges();
        expect(component.state()).toBe('indexing');
        expect(component.lostContact()).toBe(false);
    });

    it('should surface an inline retry marker after a transient retry', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, stageName: 'embedding', retryCount: 2 }));
        fixture.detectChanges();

        expect(component.showRetry()).toBe(true);
        const retry: HTMLElement | null = fixture.nativeElement.querySelector('[data-testid="ingestion-retry"]');
        expect(retry?.textContent?.trim()).toContain('2');
    });

    it('should not show a retry marker without retries', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, retryCount: 0 }));
        fixture.detectChanges();
        expect(component.showRetry()).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="ingestion-retry"]')).toBeNull();
    });

    it('should expose the failure reason as the tooltip for failed units', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.FAILED, errorKey: 'artemisApp.attachmentVideoUnit.processing.error.youtubePrivate' }));
        fixture.detectChanges();
        expect(component.tooltipKey()).toBe('artemisApp.attachmentVideoUnit.processing.error.youtubePrivate');
    });

    it('should fall back to the generic failure tooltip without an error key', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.FAILED }));
        fixture.detectChanges();
        expect(component.tooltipKey()).toBe('artemisApp.attachmentVideoUnit.processing.error.processingFailed');
    });

    it('should render the stage counter verbatim even when progress overshoots the total', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, stageName: 'embedding', stageProgress: 200, stageTotal: 142 }));
        fixture.detectChanges();
        expect(component.progressText()).toBe('200/142');
    });
});
