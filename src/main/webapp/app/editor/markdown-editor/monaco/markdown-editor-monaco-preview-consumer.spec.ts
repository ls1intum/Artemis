import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApplicationRef, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { MarkdownEditorMonacoComponent } from 'app/editor/markdown-editor/monaco/markdown-editor-monaco.component';
import { MonacoEditorComponent } from 'app/editor/monaco-editor/monaco-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { RedirectToIrisButtonComponent } from 'app/communication/shared/redirect-to-iris-button/redirect-to-iris-button.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ProgrammingExerciseInstructionService } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ParticipationWebsocketService } from 'app/course/shared/services/participation-websocket.service';
import { ResultService } from 'app/exercise/result/result.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { FileUploaderService } from 'app/foundation/service/file-uploader.service';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { CommunicationService } from 'app/communication/service/communication.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { MockParticipationWebsocketService } from 'test/helpers/mocks/service/mock-participation-websocket.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';

const originalResizeObserver = globalThis.ResizeObserver;

const EXERCISE_ID = 3;
const PROBLEM_STATEMENT_WITH_TASKS =
    '1. [task][Implement Bubble Sort](<testid>1</testid>)\n\nImplement `BubbleSort`.\n\n2. [task][Implement Merge Sort](<testid>2</testid>)\n\nImplement `MergeSort`.';
const PROBLEM_STATEMENT_WITH_PLANTUML = 'Diagram:\n\n@startuml\nclass Policy {\n+configure()\n}\n@enduml';

/** The programming exercise editor projects the instruction preview exactly like this into the markdown editor. */
@Component({
    template: `
        <jhi-markdown-editor-monaco>
            <div id="previewMonaco" class="instructions-preview-wrapper">
                <jhi-programming-exercise-instructions class="instructions-preview" [exercise]="exercise" [participation]="participation" [personalParticipation]="false" />
            </div>
        </jhi-markdown-editor-monaco>
    `,
    imports: [MarkdownEditorMonacoComponent, ProgrammingExerciseInstructionComponent],
})
class InstructionPreviewHostComponent {
    exercise: ProgrammingExercise = {
        id: EXERCISE_ID,
        course: { id: 4 },
        problemStatement: PROBLEM_STATEMENT_WITH_TASKS,
        showTestNamesToStudents: true,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    participation: Participation = { id: 100 };
}

/**
 * Task test statuses and PlantUML diagrams are injected into the rendered instructions with a document query (getElementsByClassName,
 * getElementById). These tests run the real instruction component inside the real markdown editor and prove that the queries find their
 * anchors without the user ever opening the preview tab.
 */
describe('MarkdownEditorMonacoComponent with the programming exercise instructions as preview', () => {
    let hostFixture: ComponentFixture<InstructionPreviewHostComponent>;
    let editor: MarkdownEditorMonacoComponent;
    let httpMock: HttpTestingController;

    /** Lets the asynchronous result loading, the markdown rendering and the injection after the render settle. */
    const settle = async () => {
        await new Promise((resolve) => setTimeout(resolve, 10));
        hostFixture.detectChanges();
        TestBed.inject(ApplicationRef).tick();
        hostFixture.detectChanges();
    };

    const previewWrapper = () => hostFixture.nativeElement.querySelector('.instructions-preview-wrapper') as HTMLElement;

    /** Compares by identity: the very same elements must still be attached, not structurally equal re-rendered copies. */
    const expectSameTaskStatusElements = (expected: Element[]) => {
        const current = Array.from(previewWrapper().querySelectorAll('.task-name'));
        expect(current).toHaveLength(expected.length);
        expected.forEach((element, index) => {
            expect(current[index]).toBe(element);
            expect(element.isConnected).toBe(true);
        });
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), InstructionPreviewHostComponent],
            providers: [
                ProgrammingExerciseTaskExtensionWrapper,
                ProgrammingExercisePlantUmlExtensionWrapper,
                ProgrammingExerciseInstructionService,
                LocalStorageService,
                MockProvider(FileUploaderService),
                MockProvider(AlertService),
                MockProvider(CourseConversationsService),
                MockProvider(CommunicationService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ParticipationWebsocketService, useClass: MockParticipationWebsocketService },
                { provide: DialogService, useClass: MockDialogService },
                { provide: ProgrammingExerciseGradingService, useValue: { getTestCases: () => of() } },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(MarkdownEditorMonacoComponent, {
                remove: { imports: [MonacoEditorComponent, PostingButtonComponent, RedirectToIrisButtonComponent] },
                add: { imports: [MockComponent(MonacoEditorComponent), MockComponent(PostingButtonComponent), MockComponent(RedirectToIrisButtonComponent)] },
            })
            .compileComponents();
        globalThis.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
        httpMock = TestBed.inject(HttpTestingController);
        const latestResult: Result = { id: 1, feedbacks: [{ testCase: { id: 1, testName: 'testBubbleSort' }, positive: true }] };
        vi.spyOn(TestBed.inject(ProgrammingExerciseParticipationService), 'getLatestResultWithFeedback').mockReturnValue(of(latestResult));
    });

    afterEach(() => {
        globalThis.ResizeObserver = originalResizeObserver;
        vi.restoreAllMocks();
    });

    function createHost(problemStatement: string) {
        hostFixture = TestBed.createComponent(InstructionPreviewHostComponent);
        hostFixture.componentInstance.exercise.problemStatement = problemStatement;
        hostFixture.detectChanges();
        editor = hostFixture.debugElement.query(By.directive(MarkdownEditorMonacoComponent)).componentInstance;
    }

    it('should render the task test statuses at startup into the hidden preview before the preview tab is opened', async () => {
        createHost(PROBLEM_STATEMENT_WITH_TASKS);
        await settle();

        expect(editor.activeTab()).toBe(MarkdownEditorMonacoComponent.TAB_EDIT);
        expect(editor.inPreviewMode()).toBe(false);

        // The anchors the injection looks up with a document query exist and received their task status component.
        const firstTask = document.getElementsByClassName(`pe-${EXERCISE_ID}-task-0`);
        const secondTask = document.getElementsByClassName(`pe-${EXERCISE_ID}-task-1`);
        expect(firstTask).toHaveLength(1);
        expect(secondTask).toHaveLength(1);
        expect(firstTask[0].isConnected).toBe(true);
        expect(previewWrapper().contains(firstTask[0])).toBe(true);
        expect(previewWrapper().contains(secondTask[0])).toBe(true);
        expect(firstTask[0].querySelector('.task-name')?.textContent).toBe('Implement Bubble Sort');
        expect(secondTask[0].querySelector('.task-name')?.textContent).toBe('Implement Merge Sort');

        // The latest result passes the test of the first task, the test of the second task is not part of it.
        expect(firstTask[0].querySelector('.test-icon.text-success')).not.toBeNull();
        expect(firstTask[0].querySelector('.success')).not.toBeNull();
        expect(secondTask[0].querySelector('.test-icon.text-secondary')).not.toBeNull();
        expect(secondTask[0].querySelector('.test-icon.text-success')).toBeNull();

        // The step wizard on top of the instructions summarizes both tasks.
        expect(previewWrapper().querySelectorAll('.stepwizard')).toHaveLength(1);
        expect(previewWrapper().querySelectorAll('.btn-circle')).toHaveLength(2);
        expect(previewWrapper().querySelectorAll('.stepwizard-step--success')).toHaveLength(1);
        expect(previewWrapper().querySelectorAll('.stepwizard-step--not-executed')).toHaveLength(1);
        expect(previewWrapper().closest('.hidden')).not.toBeNull();
    });

    it('should keep the rendered task test statuses when the preview tab is opened and left again', async () => {
        createHost(PROBLEM_STATEMENT_WITH_TASKS);
        await settle();
        const taskStatuses = Array.from(previewWrapper().querySelectorAll('.task-name'));
        expect(taskStatuses.map((element) => element.textContent)).toEqual(['Implement Bubble Sort', 'Implement Merge Sort']);

        editor.onTabChange(MarkdownEditorMonacoComponent.TAB_PREVIEW);
        hostFixture.detectChanges();
        expect(previewWrapper().closest('.hidden')).toBeNull();
        expectSameTaskStatusElements(taskStatuses);

        editor.onTabChange(MarkdownEditorMonacoComponent.TAB_EDIT);
        hostFixture.detectChanges();
        expect(previewWrapper().closest('.hidden')).not.toBeNull();
        expectSameTaskStatusElements(taskStatuses);
    });

    it('should inject the PlantUML diagram at startup into the hidden preview before the preview tab is opened', async () => {
        createHost(PROBLEM_STATEMENT_WITH_PLANTUML);
        await settle();

        const request = httpMock.expectOne((req) => req.url === 'api/programming/plantuml/svg');
        expect(request.request.params.get('plantuml')).toContain('class Policy');
        request.flush('<svg xmlns="http://www.w3.org/2000/svg"><text>rendered-diagram</text></svg>');

        const container = document.getElementById(`plantUml-${EXERCISE_ID}-0`);
        expect(container).not.toBeNull();
        expect(container!.isConnected).toBe(true);
        expect(previewWrapper().contains(container)).toBe(true);
        expect(container!.querySelector('svg text')?.textContent).toBe('rendered-diagram');
        expect(previewWrapper().closest('.hidden')).not.toBeNull();
        httpMock.verify();
    });

    it('should not inject anything into the document if there are no anchors for the problem statement', async () => {
        createHost('Just text without tasks or diagrams.');
        await settle();

        httpMock.expectNone('api/programming/plantuml/svg');
        expect(previewWrapper().querySelectorAll('.task-name')).toHaveLength(0);
        expect(previewWrapper().textContent).toContain('Just text without tasks or diagrams.');
        expect(document.getElementById(`plantUml-${EXERCISE_ID}-0`)).toBeNull();
    });
});
