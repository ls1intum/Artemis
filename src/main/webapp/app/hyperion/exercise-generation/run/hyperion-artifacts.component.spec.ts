import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionArtifactsComponent } from './hyperion-artifacts.component';
import { HyperionExerciseGenerationApi } from 'app/openapi/api/hyperion-exercise-generation-api';

describe('HyperionArtifactsComponent', () => {
    let fixture: ComponentFixture<HyperionArtifactsComponent>;
    const getRetained = vi.fn();
    beforeEach(() => {
        getRetained.mockClear();
        TestBed.configureTestingModule({
            imports: [HyperionArtifactsComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: HyperionExerciseGenerationApi, useValue: { getRetainedGenerationArtifacts: getRetained } },
            ],
        });
        fixture = TestBed.createComponent(HyperionArtifactsComponent);
        fixture.componentRef.setInput('jobId', 'one');
        fixture.detectChanges();
    });
    const host = () => fixture.nativeElement as HTMLElement;
    const active = () => host().querySelector('tum-ui-tab-panel[data-state="active"]');

    it('opens Exercise design first, before the specification is available', () => {
        expect(host().querySelector('tum-ui-tab')?.getAttribute('data-testid')).toBe('hyperion-artifacts-tab-spec');
        expect(active()?.querySelector('[data-testid="hyperion-artifacts-spec-empty"]')).not.toBeNull();
        expect(host().querySelector('[data-testid="hyperion-artifacts-tab-statement"]')).toBeNull();
    });
    it('renders the approved design as soon as status supplies it, without another request', () => {
        fixture.componentRef.setInput('specDocument', '# Stack contract\n\nPreserve existing operations.');
        fixture.detectChanges();
        expect(active()?.textContent).toContain('Stack contract');
        expect(active()?.textContent).toContain('Preserve existing operations.');
        expect(getRetained).not.toHaveBeenCalled();
    });
    it('lists the problem statement with the other files, with no content controls or repository fetches', () => {
        fixture.componentRef.setInput('files', [
            { type: 'FILE_CHANGE', repo: 'other', path: 'problem-statement.md', action: 'write', turn: 1, timestamp: '' },
            { type: 'FILE_CHANGE', repo: 'solution', path: 'solution/src/Stack.java', action: 'write', turn: 1, timestamp: '' },
        ]);
        fixture.componentRef.setInput('terminal', true);
        fixture.componentRef.setInput('savedToExercise', true);
        host().querySelector<HTMLElement>('[data-testid="hyperion-artifacts-tab-files"]')!.click();
        fixture.detectChanges();
        expect(active()?.textContent).toContain('problem-statement.md');
        expect(active()?.textContent).toContain('Stack.java');
        expect(active()?.querySelector('button')).toBeNull();
        expect(host().querySelector('jhi-hyperion-file-content, jhi-hyperion-repository-preview')).toBeNull();
        expect(getRetained).not.toHaveBeenCalled();
    });
    it('does not move a chosen tab when the spec arrives, but resets to design for a new run', () => {
        host().querySelector<HTMLElement>('[data-testid="hyperion-artifacts-tab-files"]')!.click();
        fixture.detectChanges();
        fixture.componentRef.setInput('specDocument', '# New design');
        fixture.detectChanges();
        expect(active()?.querySelector('jhi-hyperion-file-change-list')).not.toBeNull();
        fixture.componentRef.setInput('jobId', 'two');
        fixture.detectChanges();
        expect(active()?.querySelector('[data-testid="hyperion-artifacts-spec"]')).not.toBeNull();
    });
});
