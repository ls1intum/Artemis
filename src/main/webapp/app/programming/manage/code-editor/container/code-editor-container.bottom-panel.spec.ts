import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { facArtemisIntelligence } from 'app/foundation/icons/icons';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { CodeEditorFileService } from 'app/programming/shared/code-editor/services/code-editor-file.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CodeEditorBottomPanel, CodeEditorContainerComponent } from './code-editor-container.component';
import { CodeEditorGridComponent } from 'app/programming/shared/code-editor/layout/code-editor-grid/code-editor-grid.component';
import { CodeEditorActionsComponent } from 'app/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorFileBrowserComponent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorMonacoComponent } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { CodeEditorInstructionsComponent } from 'app/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorBuildOutputComponent } from 'app/programming/manage/code-editor/build-output/code-editor-build-output.component';

@Component({
    imports: [CodeEditorContainerComponent],
    template: `
        <jhi-code-editor-container
            [participation]="participation"
            [buildable]="buildable()"
            [editorBottomPanelTitle]="panelTitle()"
            [editorBottomPanelIcon]="panelIcon()"
            [preferredBottomPanel]="preferredBottomPanel()"
        >
            <div editorBottomPanel data-testid="projected-bottom-panel">Projected panel</div>
        </jhi-code-editor-container>
    `,
})
class TestHostComponent {
    readonly participation = { id: 1, submissions: [] } as Participation;
    readonly buildable = signal(true);
    readonly panelTitle = signal<string | undefined>(undefined);
    readonly panelIcon = signal<typeof facArtemisIntelligence | undefined>(undefined);
    readonly preferredBottomPanel = signal<CodeEditorBottomPanel | undefined>(undefined);
}

describe('CodeEditorContainerComponent bottom panel composition', () => {
    let fixture: ComponentFixture<TestHostComponent>;
    let host: TestHostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TestHostComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: { error: () => undefined } },
                { provide: CodeEditorFileService, useValue: {} },
                { provide: ExerciseReviewCommentService, useValue: { threads: signal([]) } },
            ],
        })
            .overrideComponent(CodeEditorContainerComponent, {
                remove: {
                    imports: [
                        CodeEditorGridComponent,
                        CodeEditorActionsComponent,
                        CodeEditorFileBrowserComponent,
                        CodeEditorMonacoComponent,
                        CodeEditorInstructionsComponent,
                        CodeEditorBuildOutputComponent,
                    ],
                },
                add: {
                    imports: [
                        MockComponent(CodeEditorGridComponent),
                        MockComponent(CodeEditorActionsComponent),
                        MockComponent(CodeEditorFileBrowserComponent),
                        MockComponent(CodeEditorMonacoComponent),
                        MockComponent(CodeEditorInstructionsComponent),
                        MockComponent(CodeEditorBuildOutputComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(TestHostComponent);
        host = fixture.componentInstance;
    });

    it('preserves the original build-output composition when no optional panel is configured', () => {
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('tum-ui-tabs'))).toBeNull();
        expect(fixture.debugElement.queryAll(By.directive(CodeEditorBuildOutputComponent))).toHaveLength(1);
    });

    it('renders build output and projected content as tabs when the optional panel is configured', () => {
        host.panelTitle.set('artemisApp.hyperion.generationActivity.title');
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.css('tum-ui-tab'))).toHaveLength(2);
        const buildOutput = fixture.debugElement.query(By.directive(CodeEditorBuildOutputComponent));
        expect(buildOutput.componentInstance.showHeader()).toBe(false);
    });

    it('renders only the projected tab when build output is unavailable', () => {
        host.panelTitle.set('artemisApp.hyperion.generationActivity.title');
        host.buildable.set(false);
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.css('tum-ui-tab'))).toHaveLength(1);
        expect(fixture.debugElement.query(By.directive(CodeEditorBuildOutputComponent))).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="projected-bottom-panel"]')).not.toBeNull();
    });

    it('keeps the projected panel selected when build output becomes available again', () => {
        host.panelTitle.set('artemisApp.hyperion.generationActivity.title');
        host.buildable.set(false);
        fixture.detectChanges();

        host.buildable.set(true);
        fixture.detectChanges();

        const container = fixture.debugElement.query(By.directive(CodeEditorContainerComponent)).componentInstance as CodeEditorContainerComponent;
        expect(container.activeBottomPanel()).toBe(container.CodeEditorBottomPanel.ADDITIONAL);
    });

    it('gives the optional tab the icon of the surface it belongs to', () => {
        host.panelTitle.set('artemisApp.hyperion.generationActivity.panelTitle');
        host.panelIcon.set(facArtemisIntelligence);
        fixture.detectChanges();

        const tabIcon = fixture.debugElement.query(By.css('[data-testid="editor-bottom-panel-tab"] [data-testid="editor-bottom-panel-tab-icon"]'));
        expect(tabIcon).not.toBeNull();
        expect(tabIcon.componentInstance.icon()).toBe(facArtemisIntelligence);
    });

    it('selects and expands the preferred panel on arrival', () => {
        host.panelTitle.set('artemisApp.hyperion.generationActivity.panelTitle');
        fixture.detectChanges();

        const container = fixture.debugElement.query(By.directive(CodeEditorContainerComponent)).componentInstance as CodeEditorContainerComponent;
        expect(container.activeBottomPanel()).toBe(CodeEditorBottomPanel.BUILD_OUTPUT);

        container.bottomPanelCollapsed.set(true);
        const expandBottomPanel = vi.spyOn(container.grid(), 'expandBottomPanel');
        host.preferredBottomPanel.set(CodeEditorBottomPanel.ADDITIONAL);
        fixture.detectChanges();

        expect(container.activeBottomPanel()).toBe(CodeEditorBottomPanel.ADDITIONAL);
        expect(container.bottomPanelCollapsed()).toBe(false);
        expect(expandBottomPanel).toHaveBeenCalled();
    });

    it('does not override the tab the user picks after arriving on the preferred one', () => {
        host.panelTitle.set('artemisApp.hyperion.generationActivity.panelTitle');
        host.preferredBottomPanel.set(CodeEditorBottomPanel.ADDITIONAL);
        fixture.detectChanges();

        const container = fixture.debugElement.query(By.directive(CodeEditorContainerComponent)).componentInstance as CodeEditorContainerComponent;
        expect(container.activeBottomPanel()).toBe(CodeEditorBottomPanel.ADDITIONAL);

        container.selectBottomPanel(CodeEditorBottomPanel.BUILD_OUTPUT);
        fixture.detectChanges();
        expect(container.activeBottomPanel()).toBe(CodeEditorBottomPanel.BUILD_OUTPUT);

        container.bottomPanelCollapsed.set(true);
        fixture.detectChanges();

        expect(container.activeBottomPanel()).toBe(CodeEditorBottomPanel.BUILD_OUTPUT);
        expect(container.bottomPanelCollapsed()).toBe(true);
    });
});
