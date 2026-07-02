import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, ChildrenOutletContexts, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
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

class ResizeObserverMock {
    observe = vi.fn();
    unobserve = vi.fn();
    disconnect = vi.fn();
}

describe('ExerciseSplitPanelComponent', () => {
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
                        <jhi-resizable-panels>
                            @if (showEditorPanel()) {
                                <ng-template jhiPanel [label]="editorLabelKey()">Editor</ng-template>
                            }
                            @if (exercise().type !== ExerciseType.QUIZ) {
                                <ng-template jhiPanel [label]="'problemStatement'">Problem Statement</ng-template>
                            }
                            @if (showIris()) {
                                <ng-template jhiPanel [label]="'iris'" [startsCollapsed]="irisPanelStartsCollapsed()">Iris</ng-template>
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

    it('navigates only when the target route identity changes, not when the participation object is replaced (prevents the navigate-thrash loop on incoming results, #12976)', () => {
        const navigateSpy = vi.mocked(TestBed.inject(Router).navigate);

        // Programming exercise with the online editor: navigating to the code editor is expected on the first run.
        fixture.componentRef.setInput('exercise', { id: 1, type: ExerciseType.PROGRAMMING, allowOnlineEditor: true } as unknown as Exercise);
        fixture.componentRef.setInput('studentParticipation', { id: 5 } as StudentParticipation);
        fixture.detectChanges();
        expect(navigateSpy).toHaveBeenCalledWith(['programming-exercises', 1, 'code-editor', 5], expect.anything());

        navigateSpy.mockClear();

        // An incoming result replaces the participation object but keeps its id. This must NOT re-navigate — otherwise
        // navigation thrashes and re-creates the code-editor subtree in a loop, flooding the server with requests.
        fixture.componentRef.setInput('studentParticipation', { id: 5, submissions: [{ id: 9 }] } as StudentParticipation);
        fixture.detectChanges();
        expect(navigateSpy).not.toHaveBeenCalled();

        // A genuine switch to a different participation still navigates.
        fixture.componentRef.setInput('studentParticipation', { id: 6 } as StudentParticipation);
        fixture.detectChanges();
        expect(navigateSpy).toHaveBeenCalledWith(['programming-exercises', 1, 'code-editor', 6], expect.anything());
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
});
