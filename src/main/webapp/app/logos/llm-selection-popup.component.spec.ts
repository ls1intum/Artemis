import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LLMSelectionModalComponent } from './llm-selection-popup.component';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { LLMSelectionDecision, LLM_MODAL_DISMISSED } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { signal } from '@angular/core';
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

        const themeServiceMock = {
            currentTheme: signal(Theme.LIGHT),
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
                { provide: ThemeService, useValue: themeServiceMock },
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

    describe('onBackdropClick', () => {
        it('should emit NONE choice when backdrop is clicked', () => {
            const choiceSpy = vi.spyOn(component.choice, 'emit');
            const event = { target: document.createElement('div'), currentTarget: document.createElement('div') } as any;
            event.target = event.currentTarget;

            component.onBackdropClick(event);

            expect(choiceSpy).toHaveBeenCalledWith(LLM_MODAL_DISMISSED);
        });

        it('should call modalService.emitChoice with NONE', () => {
            const event = { target: document.createElement('div'), currentTarget: document.createElement('div') } as any;
            event.target = event.currentTarget;

            component.onBackdropClick(event);

            expect(modalService.emitChoice).toHaveBeenCalledWith(LLM_MODAL_DISMISSED);
        });

        it('should close modal when target equals currentTarget', () => {
            const closeSpy = vi.spyOn(component, 'close');
            const event = { target: document.createElement('div'), currentTarget: document.createElement('div') } as any;
            event.target = event.currentTarget;

            component.onBackdropClick(event);

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should not close modal when target does not equal currentTarget', () => {
            const closeSpy = vi.spyOn(component, 'close');
            const target = document.createElement('div');
            const currentTarget = document.createElement('div');
            const event = { target, currentTarget } as any;

            component.onBackdropClick(event);

            expect(closeSpy).not.toHaveBeenCalled();
        });

        it('should NOT emit choice when clicking inside modal content (target !== currentTarget)', () => {
            const choiceSpy = vi.spyOn(component.choice, 'emit');
            const emitChoiceSpy = vi.spyOn(modalService, 'emitChoice');
            const closeSpy = vi.spyOn(component, 'close');

            const target = document.createElement('div');
            const currentTarget = document.createElement('div');
            const event = { target, currentTarget } as any;

            component.onBackdropClick(event);

            expect(choiceSpy).not.toHaveBeenCalled();
            expect(emitChoiceSpy).not.toHaveBeenCalled();
            expect(closeSpy).not.toHaveBeenCalled();
        });

        it('should emit choice and close when clicking backdrop (target === currentTarget)', () => {
            const choiceSpy = vi.spyOn(component.choice, 'emit');
            const emitChoiceSpy = vi.spyOn(modalService, 'emitChoice');
            const closeSpy = vi.spyOn(component, 'close');

            const backdrop = document.createElement('div');
            const event = { target: backdrop, currentTarget: backdrop } as any;

            component.onBackdropClick(event);

            expect(choiceSpy).toHaveBeenCalledWith(LLM_MODAL_DISMISSED);
            expect(emitChoiceSpy).toHaveBeenCalledWith(LLM_MODAL_DISMISSED);
            expect(closeSpy).toHaveBeenCalled();
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

    describe('Theme constant', () => {
        it('should expose Theme enum', () => {
            expect(component['Theme']).toBe(Theme);
        });
    });

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
