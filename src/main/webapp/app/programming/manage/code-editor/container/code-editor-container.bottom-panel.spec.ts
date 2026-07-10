import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockComponent } from 'ng-mocks';
import { beforeEach, describe, expect, it } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ExerciseReviewCommentService } from 'app/exercise/review/exercise-review-comment.service';
import { CodeEditorFileService } from 'app/programming/shared/code-editor/services/code-editor-file.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CodeEditorContainerComponent } from './code-editor-container.component';
import { CodeEditorGridComponent } from 'app/programming/shared/code-editor/layout/code-editor-grid/code-editor-grid.component';
import { CodeEditorActionsComponent } from 'app/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorFileBrowserComponent } from 'app/programming/manage/code-editor/file-browser/code-editor-file-browser.component';
import { CodeEditorMonacoComponent } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { CodeEditorInstructionsComponent } from 'app/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorBuildOutputComponent } from 'app/programming/manage/code-editor/build-output/code-editor-build-output.component';

@Component({
    imports: [CodeEditorContainerComponent],
    template: `
        <jhi-code-editor-container [participation]="participation" [buildable]="buildable()" [editorBottomPanelTitle]="panelTitle()">
            <div editorBottomPanel data-testid="projected-bottom-panel">Projected panel</div>
        </jhi-code-editor-container>
    `,
})
class TestHostComponent {
    readonly participation = { id: 1, submissions: [] } as Participation;
    readonly buildable = signal(true);
    readonly panelTitle = signal<string | undefined>(undefined);
}

describe('CodeEditorContainerComponent bottom panel composition', () => {
    setupTestBed({ zoneless: true });

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

        expect(fixture.debugElement.query(By.css('p-tabs'))).toBeNull();
        expect(fixture.debugElement.queryAll(By.directive(CodeEditorBuildOutputComponent))).toHaveLength(1);
    });

    it('renders build output and projected content as tabs when the optional panel is configured', () => {
        host.panelTitle.set('artemisApp.hyperion.generationActivity.title');
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.css('p-tab'))).toHaveLength(2);
        const buildOutput = fixture.debugElement.query(By.directive(CodeEditorBuildOutputComponent));
        expect(buildOutput.componentInstance.showHeader).toBe(false);
        expect(fixture.nativeElement.querySelector('[data-testid="projected-bottom-panel"]')).not.toBeNull();
    });

    it('renders only the projected tab when build output is unavailable', () => {
        host.panelTitle.set('artemisApp.hyperion.generationActivity.title');
        host.buildable.set(false);
        fixture.detectChanges();

        expect(fixture.debugElement.queryAll(By.css('p-tab'))).toHaveLength(1);
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
});
