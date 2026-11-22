import { AiModeSelectorComponent } from './ai-mode-selector.component';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LLMSelectionModalComponent } from './llm-selection-popup.component';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { By } from '@angular/platform-browser';
import { TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('LLMSelectionModalComponent', () => {
    let component: LLMSelectionModalComponent;
    let fixture: ComponentFixture<LLMSelectionModalComponent>;
    let modalService: LLMSelectionModalService;
    let router: Router;
    let openModalSubject: Subject<LLMSelectionDecision | undefined>;

    beforeEach(async () => {
        openModalSubject = new Subject<LLMSelectionDecision | undefined>();

        const modalServiceMock = {
            openModal$: openModalSubject.asObservable(),
            emitChoice: vi.fn(),
        };

        const routerMock = {
            navigate: vi.fn(),
        };

        const profileServiceMock = {
            isLLMDeploymentEnabled: vi.fn().mockReturnValue(false),
        };

        await TestBed.configureTestingModule({
            imports: [LLMSelectionModalComponent, TranslateDirective],
            providers: [
                { provide: LLMSelectionModalService, useValue: modalServiceMock },
                { provide: Router, useValue: routerMock },
                { provide: ProfileService, useValue: profileServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LLMSelectionModalComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(LLMSelectionModalService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with isVisible false', () => {
        expect(component.isVisible()).toBe(false);
    });

    describe('open', () => {
        it('should set isVisible to true', () => {
            component.isVisible.set(false);
            component.open();
            expect(component.isVisible()).toBe(true);
        });
    });

    describe('close', () => {
        it('should set isVisible to false', () => {
            component.isVisible.set(true);
            component.close();
            expect(component.isVisible()).toBe(false);
        });
    });

    describe('selectCloud', () => {
        it('should emit cloud choice', () => {
            const choiceSpy = vi.spyOn(component.choice, 'emit');

            component.selectCloud();

            expect(choiceSpy).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('should call modalService.emitChoice with CLOUD_AI', () => {
            component.selectCloud();

            expect(modalService.emitChoice).toHaveBeenCalledWith(LLMSelectionDecision.CLOUD_AI);
        });

        it('should close the modal', () => {
            const closeSpy = vi.spyOn(component, 'close');

            component.selectCloud();

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should set isVisible to false after selection', () => {
            component.isVisible.set(true);

            component.selectCloud();

            expect(component.isVisible()).toBe(false);
        });
    });

    describe('selectLocal', () => {
        it('should emit local choice', () => {
            const choiceSpy = vi.spyOn(component.choice, 'emit');

            component.selectLocal();

            expect(choiceSpy).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
        });

        it('should call modalService.emitChoice with LOCAL_AI', () => {
            component.selectLocal();

            expect(modalService.emitChoice).toHaveBeenCalledWith(LLMSelectionDecision.LOCAL_AI);
        });

        it('should close the modal', () => {
            const closeSpy = vi.spyOn(component, 'close');

            component.selectLocal();

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should set isVisible to false after selection', () => {
            component.isVisible.set(true);

            component.selectLocal();

            expect(component.isVisible()).toBe(false);
        });
    });

    describe('selectNone', () => {
        it('should emit NO_AI choice', () => {
            const choiceSpy = vi.spyOn(component.choice, 'emit');

            component.selectNone();

            expect(choiceSpy).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
        });

        it('should call modalService.emitChoice with NO_AI', () => {
            component.selectNone();

            expect(modalService.emitChoice).toHaveBeenCalledWith(LLMSelectionDecision.NO_AI);
        });

        it('should close the modal', () => {
            const closeSpy = vi.spyOn(component, 'close');

            component.selectNone();

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should set isVisible to false after selection', () => {
            component.isVisible.set(true);

            component.selectNone();

            expect(component.isVisible()).toBe(false);
        });
    });

    describe('onVisibleChange', () => {
        it('should resolve the choice as dismissed and close when the dialog is closed by Escape or the backdrop', () => {
            const choiceSpy = vi.spyOn(component.choice, 'emit');
            const closeSpy = vi.spyOn(component, 'close');
            component.open();

            component.onVisibleChange(false);

            expect(choiceSpy).toHaveBeenCalledWith(LLM_MODAL_DISMISSED);
            expect(modalService.emitChoice).toHaveBeenCalledWith(LLM_MODAL_DISMISSED);
            expect(closeSpy).toHaveBeenCalled();
        });

        it('should not resolve a second time when the popup was already closed by a choice', () => {
            const choiceSpy = vi.spyOn(component.choice, 'emit');
            component.open();
            component.selectCloud();
            choiceSpy.mockClear();
            vi.mocked(modalService.emitChoice).mockClear();

            component.onVisibleChange(false);

            expect(choiceSpy).not.toHaveBeenCalled();
            expect(modalService.emitChoice).not.toHaveBeenCalled();
        });

        it('should ignore the dialog opening', () => {
            const choiceSpy = vi.spyOn(component.choice, 'emit');

            component.onVisibleChange(true);

            expect(choiceSpy).not.toHaveBeenCalled();
        });
    });

    describe('onLearnMoreClick', () => {
        it('should prevent default event behavior', () => {
            const event = { preventDefault: vi.fn() } as any;

            component.onLearnMoreClick(event);

            expect(event.preventDefault).toHaveBeenCalled();
        });

        it('should navigate to /ai-experience-info', () => {
            const event = { preventDefault: vi.fn() } as any;

            component.onLearnMoreClick(event);

            expect(router.navigate).toHaveBeenCalledWith(['/ai-experience-info']);
        });

        it('should close the modal after navigation', () => {
            const closeSpy = vi.spyOn(component, 'close');
            const event = { preventDefault: vi.fn() } as any;

            component.onLearnMoreClick(event);

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should set isVisible to false after learn more click', () => {
            component.isVisible.set(true);
            const event = { preventDefault: vi.fn() } as any;

            component.onLearnMoreClick(event);

            expect(component.isVisible()).toBe(false);
        });
    });

    it.each([LLMSelectionDecision.CLOUD_AI, LLMSelectionDecision.LOCAL_AI, LLMSelectionDecision.NO_AI])(
        'should resolve the shared selector choice %s through the AET dialog',
        (selection) => {
            fixture.detectChanges();
            component.open();
            fixture.detectChanges();
            const choiceSpy = vi.spyOn(component.choice, 'emit');
            const selector = fixture.debugElement.query(By.directive(AiModeSelectorComponent)).componentInstance as AiModeSelectorComponent;

            selector.choice.emit(selection);

            expect(choiceSpy).toHaveBeenCalledWith(selection);
            expect(modalService.emitChoice).toHaveBeenCalledWith(selection);
            expect(component.isVisible()).toBe(false);
        },
    );

    describe('currentSelection', () => {
        it('should set currentSelection when modal is opened with a selection', () => {
            fixture.detectChanges();
            openModalSubject.next(LLMSelectionDecision.CLOUD_AI);

            expect(component.currentSelection()).toBe(LLMSelectionDecision.CLOUD_AI);
        });

        it('should set currentSelection to LOCAL_AI when modal is opened with LOCAL_AI', () => {
            fixture.detectChanges();
            openModalSubject.next(LLMSelectionDecision.LOCAL_AI);

            expect(component.currentSelection()).toBe(LLMSelectionDecision.LOCAL_AI);
        });

        it('should set currentSelection to NO_AI when modal is opened with NO_AI', () => {
            fixture.detectChanges();
            openModalSubject.next(LLMSelectionDecision.NO_AI);

            expect(component.currentSelection()).toBe(LLMSelectionDecision.NO_AI);
        });

        it('should set currentSelection to undefined when modal is opened without selection', () => {
            fixture.detectChanges();
            openModalSubject.next(undefined);

            expect(component.currentSelection()).toBeUndefined();
        });
    });
});

describe('LLM selection dismissal', () => {
    it('clears the shared open state and resolves the choice when the dialog is dismissed', async () => {
        const modalService = new LLMSelectionModalService();
        await TestBed.configureTestingModule({
            imports: [LLMSelectionModalComponent],
            providers: [
                { provide: LLMSelectionModalService, useValue: modalService },
                { provide: ProfileService, useValue: { isLLMDeploymentEnabled: () => false } },
                { provide: Router, useValue: { navigate: vi.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();
        const popup = TestBed.createComponent(LLMSelectionModalComponent);
        popup.detectChanges();
        const result = modalService.open();
        popup.detectChanges();
        expect(modalService.isOpen()).toBe(true);

        // The dialog closes itself on Escape and on a click on its backdrop; closing it is what both do.
        popup.debugElement.query(By.directive(TumAetUiDialogComponent)).componentInstance.close();

        expect(await result).toBe(LLM_MODAL_DISMISSED);
        expect(modalService.isOpen()).toBe(false);
        expect(popup.componentInstance.isVisible()).toBe(false);
    });
});
