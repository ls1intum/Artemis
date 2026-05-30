import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, ChildrenOutletContexts, Router } from '@angular/router';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountService } from 'app/core/auth/account.service';
import { LLMSelectionDecision } from 'app/core/user/shared/dto/updateLLMSelectionDecision.dto';
import { User } from 'app/core/user/user.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { ExerciseSplitPanelComponent } from 'app/core/course/overview/exercise-details/exercise-split-panel/exercise-split-panel.component';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('ExerciseSplitPanelComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseSplitPanelComponent>;
    let component: ExerciseSplitPanelComponent;
    let accountService: MockAccountService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseSplitPanelComponent],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: IrisChatService, useValue: { switchTo: vi.fn() } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: ActivatedRoute, useValue: { parent: {}, firstChild: undefined } },
                ChildrenOutletContexts,
            ],
        })
            .overrideComponent(ExerciseSplitPanelComponent, {
                set: { template: '', imports: [] },
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
});
