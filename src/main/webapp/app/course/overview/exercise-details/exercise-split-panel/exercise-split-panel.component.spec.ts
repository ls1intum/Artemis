import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, ChildrenOutletContexts, Router } from '@angular/router';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { User } from 'app/account/user/user.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { ExerciseSplitPanelComponent } from 'app/course/overview/exercise-details/exercise-split-panel/exercise-split-panel.component';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PanelDirective, ResizablePanelsComponent } from 'app/shared-ui/components/resizable-panels/resizable-panels.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

vi.mock('split.js', () => ({
    default: vi.fn(() => ({ destroy: vi.fn(), getSizes: vi.fn(() => [65, 35]) })),
}));

class ResizeObserverMock {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

const createResult = (partial: Partial<Result>): Result => Object.assign(new Result(), partial);

describe('ExerciseSplitPanelComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseSplitPanelComponent>;
    let component: ExerciseSplitPanelComponent;
    let accountService: MockAccountService;

    beforeEach(async () => {
        vi.stubGlobal('ResizeObserver', ResizeObserverMock);
        await TestBed.configureTestingModule({
            imports: [ExerciseSplitPanelComponent],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: IrisChatService, useValue: { switchTo: vi.fn() } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: ActivatedRoute, useValue: { parent: {}, firstChild: undefined } },
                { provide: TranslateService, useClass: MockTranslateService },
                ChildrenOutletContexts,
            ],
        })
            .overrideComponent(ExerciseSplitPanelComponent, {
                set: {
                    template: `
                        <jhi-resizable-panels [splitOnWide]="!showReadOnlyEditorAsFeedbackTab()">
                            @if (showEditorPanel() && !showReadOnlyEditorAsFeedbackTab()) {
                                <ng-template jhiPanel [label]="editorLabelKey()">Editor</ng-template>
                            }
                            @if (exercise().type !== ExerciseType.QUIZ) {
                                <ng-template jhiPanel [label]="'problemStatement'">Problem Statement</ng-template>
                            }
                            @if (showIris()) {
                                <ng-template jhiPanel [label]="'iris'" [startsCollapsed]="irisPanelStartsCollapsed()">Iris</ng-template>
                            }
                            @if (showReadOnlyEditorAsFeedbackTab()) {
                                <ng-template jhiPanel [label]="editorLabelKey()">Editor</ng-template>
                            }
                        </jhi-resizable-panels>
                    `,
                    imports: [ResizablePanelsComponent, PanelDirective],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseSplitPanelComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService) as unknown as MockAccountService;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.TEXT } as Exercise);
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('irisEnabled', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('should start the Iris panel collapsed for users who opted out of AI', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);

        expect(component.irisPanelStartsCollapsed()).toBe(true);
    });

    it('should not start the Iris panel collapsed for users who accepted AI', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.CLOUD_AI } as User);

        expect(component.irisPanelStartsCollapsed()).toBe(false);
    });

    it('should not start the Iris panel collapsed before the user made an AI selection', () => {
        accountService.userIdentity.set({ selectedLLMUsage: undefined } as User);

        expect(component.irisPanelStartsCollapsed()).toBe(false);
    });

    it('should keep the problem statement open for users who opted out of AI when an editor panel is shown', () => {
        accountService.userIdentity.set({ selectedLLMUsage: LLMSelectionDecision.NO_AI } as User);
        fixture.componentRef.setInput('studentParticipation', { id: 1 } as StudentParticipation);
        fixture.detectChanges();

        const resizablePanels = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;

        expect(component.irisPanelStartsCollapsed()).toBe(false);
        expect(resizablePanels.isRightPanelCollapsed()).toBe(false);
        expect(resizablePanels.activeRightIndex()).toBe(0);
        expect(fixture.nativeElement.querySelector('.collapsed-right-panel')).toBeNull();
        expect(fixture.nativeElement.textContent).toContain('Problem Statement');
    });

    it('should keep wide splitting enabled when online editor is disabled but no read-only editor panel is shown', () => {
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: false } as ProgrammingExercise);
        fixture.componentRef.setInput('studentParticipation', undefined);
        fixture.detectChanges();

        const resizablePanels = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;

        expect(component.showReadOnlyEditorAsFeedbackTab()).toBe(false);
        expect(resizablePanels.splitOnWide()).toBe(true);
    });

    it.each([false, undefined])('should keep wide splitting enabled without read-only editor annotations and allowOnlineEditor is %s', (allowOnlineEditor) => {
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor } as ProgrammingExercise);
        fixture.componentRef.setInput('studentParticipation', { id: 1 } as StudentParticipation);
        fixture.detectChanges();

        const resizablePanels = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;

        expect(component.showReadOnlyEditorAsFeedbackTab()).toBe(false);
        expect(resizablePanels.splitOnWide()).toBe(true);
    });

    it.each([false, undefined])('should disable wide splitting when a failed build can create read-only editor annotations and allowOnlineEditor is %s', (allowOnlineEditor) => {
        const submission = { buildFailed: true } as ProgrammingSubmission;
        const result = createResult({ id: 2, completionDate: dayjs(), submission });
        submission.results = [result];
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor } as ProgrammingExercise);
        fixture.componentRef.setInput('studentParticipation', { id: 1, submissions: [submission] } as StudentParticipation);
        fixture.detectChanges();

        const resizablePanels = fixture.debugElement.query(By.directive(ResizablePanelsComponent)).componentInstance as ResizablePanelsComponent;

        expect(component.showReadOnlyEditorAsFeedbackTab()).toBe(true);
        expect(resizablePanels.splitOnWide()).toBe(false);
    });

    it('should show the read-only editor feedback tab for file-line referenced feedback', () => {
        const feedback = { reference: 'file:src/Main.java_line:4', type: FeedbackType.MANUAL } as Feedback;
        const result = createResult({ id: 2, completionDate: dayjs(), feedbacks: [feedback] });
        const submission = { buildFailed: false, results: [result] } as ProgrammingSubmission;
        result.submission = submission;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: false } as ProgrammingExercise);
        fixture.componentRef.setInput('studentParticipation', { id: 1, submissions: [submission] } as StudentParticipation);
        fixture.detectChanges();

        expect(component.showReadOnlyEditorAsFeedbackTab()).toBe(true);
    });

    it('should show the read-only editor feedback tab for successful Athena feedback results', () => {
        const result = createResult({ id: 2, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true });
        const submission = { buildFailed: false, results: [result] } as ProgrammingSubmission;
        result.submission = submission;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: false } as ProgrammingExercise);
        fixture.componentRef.setInput('studentParticipation', { id: 1, submissions: [submission] } as StudentParticipation);
        fixture.detectChanges();

        expect(component.showReadOnlyEditorAsFeedbackTab()).toBe(true);
    });

    it('should show the read-only editor feedback tab for static code analysis feedback', () => {
        const feedback = { text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, detailText: '{"filePath":"src/Main.java","startLine":4}', type: FeedbackType.AUTOMATIC } as Feedback;
        const result = createResult({ id: 2, completionDate: dayjs(), feedbacks: [feedback] });
        const submission = { buildFailed: false, results: [result] } as ProgrammingSubmission;
        result.submission = submission;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: false } as ProgrammingExercise);
        fixture.componentRef.setInput('studentParticipation', { id: 1, submissions: [submission] } as StudentParticipation);
        fixture.detectChanges();

        expect(component.showReadOnlyEditorAsFeedbackTab()).toBe(true);
    });

    it('should not show the read-only editor feedback tab for unreferenced feedback', () => {
        const feedback = { type: FeedbackType.MANUAL_UNREFERENCED, detailText: 'General feedback' } as Feedback;
        const result = createResult({ id: 2, completionDate: dayjs(), feedbacks: [feedback] });
        const submission = { buildFailed: false, results: [result] } as ProgrammingSubmission;
        result.submission = submission;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: false } as ProgrammingExercise);
        fixture.componentRef.setInput('studentParticipation', { id: 1, submissions: [submission] } as StudentParticipation);
        fixture.detectChanges();

        expect(component.showReadOnlyEditorAsFeedbackTab()).toBe(false);
    });

    it('should not use latest rated result from another participation to show the read-only editor feedback tab', () => {
        const feedback = { reference: 'file:src/Main.java_line:4', type: FeedbackType.MANUAL } as Feedback;
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: false } as ProgrammingExercise);
        fixture.componentRef.setInput('studentParticipation', { id: 1 } as StudentParticipation);
        fixture.componentRef.setInput('latestRatedResult', { id: 2, feedbacks: [feedback] } as Result);
        fixture.detectChanges();

        expect(component.showReadOnlyEditorAsFeedbackTab()).toBe(false);
    });
});
