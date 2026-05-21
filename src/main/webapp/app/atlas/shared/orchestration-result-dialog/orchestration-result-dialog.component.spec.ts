import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { OrchestrationResultDialogComponent } from 'app/atlas/shared/orchestration-result-dialog/orchestration-result-dialog.component';

@Component({
    standalone: true,
    selector: 'jhi-host',
    imports: [OrchestrationResultDialogComponent],
    template: '<jhi-orchestration-result-dialog [(visible)]="visible" [summary]="summary()" />',
})
class HostComponent {
    readonly visible = signal(true);
    readonly summary = signal('');
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

    it('close() flips visible to false', () => {
        expect(dialog.visible()).toBeTruthy();
        dialog['close']();
        expect(dialog.visible()).toBeFalsy();
    });

    it('renders the summary text inside .summary-box when summary is set', () => {
        fixture.componentInstance.summary.set('Hello competency world.');
        fixture.detectChanges();

        expect(document.querySelector('.summary-box')?.textContent?.trim()).toBe('Hello competency world.');
    });

    it('renders the empty-state translation key when summary is missing', () => {
        // summary signal already starts at '' from the host component.
        expect(document.querySelector('.summary-box')).toBeNull();
        expect(document.querySelector('p[jhitranslate="artemisApp.atlasOrchestrator.resultDialog.empty"]')).not.toBeNull();
    });
});
