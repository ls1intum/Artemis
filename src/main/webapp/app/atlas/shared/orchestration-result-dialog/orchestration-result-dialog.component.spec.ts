import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { OrchestrationResultDialogComponent } from 'app/atlas/shared/orchestration-result-dialog/orchestration-result-dialog.component';
import { AppliedActionDTO, AppliedActionType } from 'app/atlas/shared/dto/competency-orchestration-dto';

@Component({
    standalone: true,
    selector: 'jhi-host',
    imports: [OrchestrationResultDialogComponent],
    template: '<jhi-orchestration-result-dialog [visible]="true" [summaryMessage]="msg()" [appliedActions]="actions()" />',
})
class HostComponent {
    msg = signal('');
    actions = signal<AppliedActionDTO[]>([]);
}

describe('OrchestrationResultDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<HostComponent>;
    let dialog: OrchestrationResultDialogComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HostComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
        dialog = fixture.debugElement.query((el) => el.componentInstance instanceof OrchestrationResultDialogComponent).componentInstance;
    });

    describe('weightBandTag', () => {
        it('returns primary band for weight 1.0', () => {
            const tag = dialog['weightBandTag'](1.0);
            expect(tag).toEqual({ translationKey: expect.stringContaining('primary'), weight: 1.0 });
        });

        it('returns partial band for weight 0.5', () => {
            const tag = dialog['weightBandTag'](0.5);
            expect(tag).toEqual({ translationKey: expect.stringContaining('partial'), weight: 0.5 });
        });

        it('returns incidental band for weight 0.3', () => {
            const tag = dialog['weightBandTag'](0.3);
            expect(tag).toEqual({ translationKey: expect.stringContaining('incidental'), weight: 0.3 });
        });

        it('returns undefined for missing weight', () => {
            expect(dialog['weightBandTag'](undefined)).toBeUndefined();
        });

        it('returns undefined for NaN weight', () => {
            expect(dialog['weightBandTag'](Number.NaN)).toBeUndefined();
        });
    });

    describe('actionSeverity', () => {
        it.each([
            [AppliedActionType.Create, 'success'],
            [AppliedActionType.Edit, 'info'],
            [AppliedActionType.Assign, 'info'],
            [AppliedActionType.Unassign, 'warn'],
            [AppliedActionType.Delete, 'danger'],
        ])('maps %s → %s', (type, severity) => {
            expect(dialog['actionSeverity'](type)).toBe(severity);
        });
    });

    describe('hasActions', () => {
        it('is false for empty actions list', () => {
            expect(dialog.hasActions()).toBeFalsy();
        });
    });

    it('renders the summary text inside .summary-box when summary is set', () => {
        fixture.componentInstance.msg.set('Hello competency world.');
        fixture.detectChanges();

        expect(document.querySelector('.summary-box')?.textContent?.trim()).toBe('Hello competency world.');
    });

    it('renders the empty-state translation key when no actions are present', () => {
        expect(document.querySelector('p[jhitranslate="artemisApp.atlasOrchestrator.resultDialog.empty"]')).not.toBeNull();
    });
});
