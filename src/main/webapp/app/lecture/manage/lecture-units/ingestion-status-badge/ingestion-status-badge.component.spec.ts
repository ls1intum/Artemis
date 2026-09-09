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

    it('should render the live stage counter and progress bar while running', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, stageName: 'vision', stageProgress: 41, stageTotal: 142 }));
        fixture.detectChanges();

        const counter: HTMLElement | null = fixture.nativeElement.querySelector('[data-testid="ingestion-stage-counter"]');
        expect(counter?.textContent?.trim()).toBe('41/142');
        const underbar: HTMLElement | null = fixture.nativeElement.querySelector('.stage-underbar');
        expect(underbar?.style.width).toBe('29%');
    });

    it('should not render a counter for stages without progress or for terminal states', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.TRANSCRIBING }));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="ingestion-stage-counter"]')).toBeNull();

        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.DONE, stageName: 'vision', stageProgress: 41, stageTotal: 142 }));
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="ingestion-stage-counter"]')).toBeNull();
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

    it('should cap the progress bar at 100 percent', () => {
        fixture.componentRef.setInput('status', status({ phase: ProcessingPhase.INGESTING, stageName: 'embedding', stageProgress: 200, stageTotal: 142 }));
        fixture.detectChanges();
        expect(component.progressPercent()).toBe(100);
    });
});
