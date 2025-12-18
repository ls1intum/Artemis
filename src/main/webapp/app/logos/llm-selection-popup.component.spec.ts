import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LLMSelectionModalComponent } from './llm-selection-popup.component';
import { LLMSelectionModalService } from 'app/logos/llm-selection-popup.service';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';
import { Router } from '@angular/router';
import { Subject } from 'rxjs';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('LLMSelectionModalComponent', () => {
    let component: LLMSelectionModalComponent;
    let fixture: ComponentFixture<LLMSelectionModalComponent>;
    let modalService: LLMSelectionModalService;
    let router: Router;
    let openModalSubject: Subject<void>;

    beforeEach(async () => {
        openModalSubject = new Subject<void>();

        const modalServiceMock = {
            openModal$: openModalSubject.asObservable(),
            emitChoice: jest.fn(),
        };

        const themeServiceMock = {
            currentTheme: Theme.LIGHT,
        };

        const routerMock = {
            navigate: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [LLMSelectionModalComponent, TranslateDirective],
            providers: [
                { provide: LLMSelectionModalService, useValue: modalServiceMock },
                { provide: ThemeService, useValue: themeServiceMock },
                { provide: Router, useValue: routerMock },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LLMSelectionModalComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(LLMSelectionModalService);
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with isVisible false', () => {
        expect(component.isVisible).toBeFalse();
    });

    describe('open', () => {
        it('should set isVisible to true', () => {
            component.isVisible = false;
            component.open();
            expect(component.isVisible).toBeTrue();
        });
    });

    describe('close', () => {
        it('should set isVisible to false', () => {
            component.isVisible = true;
            component.close();
            expect(component.isVisible).toBeFalse();
        });
    });

    describe('selectCloud', () => {
        it('should emit cloud choice', () => {
            const choiceSpy = jest.spyOn(component.choice, 'emit');

            component.selectCloud();

            expect(choiceSpy).toHaveBeenCalledWith('cloud');
        });

        it('should call modalService.emitChoice with cloud', () => {
            component.selectCloud();

            expect(modalService.emitChoice).toHaveBeenCalledWith('cloud');
        });

        it('should close the modal', () => {
            const closeSpy = jest.spyOn(component, 'close');

            component.selectCloud();

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should set isVisible to false after selection', () => {
            component.isVisible = true;

            component.selectCloud();

            expect(component.isVisible).toBeFalse();
        });
    });

    describe('selectLocal', () => {
        it('should emit local choice', () => {
            const choiceSpy = jest.spyOn(component.choice, 'emit');

            component.selectLocal();

            expect(choiceSpy).toHaveBeenCalledWith('local');
        });

        it('should call modalService.emitChoice with local', () => {
            component.selectLocal();

            expect(modalService.emitChoice).toHaveBeenCalledWith('local');
        });

        it('should close the modal', () => {
            const closeSpy = jest.spyOn(component, 'close');

            component.selectLocal();

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should set isVisible to false after selection', () => {
            component.isVisible = true;

            component.selectLocal();

            expect(component.isVisible).toBeFalse();
        });
    });

    describe('selectNone', () => {
        it('should emit no_ai choice', () => {
            const choiceSpy = jest.spyOn(component.choice, 'emit');

            component.selectNone();

            expect(choiceSpy).toHaveBeenCalledWith('no_ai');
        });

        it('should call modalService.emitChoice with no_ai', () => {
            component.selectNone();

            expect(modalService.emitChoice).toHaveBeenCalledWith('no_ai');
        });

        it('should close the modal', () => {
            const closeSpy = jest.spyOn(component, 'close');

            component.selectNone();

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should set isVisible to false after selection', () => {
            component.isVisible = true;

            component.selectNone();

            expect(component.isVisible).toBeFalse();
        });
    });

    describe('onBackdropClick', () => {
        it('should emit none choice when backdrop is clicked', () => {
            const choiceSpy = jest.spyOn(component.choice, 'emit');
            const event = { target: document.createElement('div'), currentTarget: document.createElement('div') } as any;
            event.target = event.currentTarget;

            component.onBackdropClick(event);

            expect(choiceSpy).toHaveBeenCalledWith('none');
        });

        it('should call modalService.emitChoice with none', () => {
            const event = { target: document.createElement('div'), currentTarget: document.createElement('div') } as any;
            event.target = event.currentTarget;

            component.onBackdropClick(event);

            expect(modalService.emitChoice).toHaveBeenCalledWith('none');
        });

        it('should close modal when target equals currentTarget', () => {
            const closeSpy = jest.spyOn(component, 'close');
            const event = { target: document.createElement('div'), currentTarget: document.createElement('div') } as any;
            event.target = event.currentTarget;

            component.onBackdropClick(event);

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should not close modal when target does not equal currentTarget', () => {
            const closeSpy = jest.spyOn(component, 'close');
            const target = document.createElement('div');
            const currentTarget = document.createElement('div');
            const event = { target, currentTarget } as any;

            component.onBackdropClick(event);

            expect(closeSpy).not.toHaveBeenCalled();
        });

        it('should NOT emit choice when clicking inside modal content (target !== currentTarget)', () => {
            const choiceSpy = jest.spyOn(component.choice, 'emit');
            const emitChoiceSpy = jest.spyOn(modalService, 'emitChoice');
            const closeSpy = jest.spyOn(component, 'close');

            const target = document.createElement('div');
            const currentTarget = document.createElement('div');
            const event = { target, currentTarget } as any;

            component.onBackdropClick(event);

            expect(choiceSpy).not.toHaveBeenCalled();
            expect(emitChoiceSpy).not.toHaveBeenCalled();
            expect(closeSpy).not.toHaveBeenCalled();
        });

        it('should emit choice and close when clicking backdrop (target === currentTarget)', () => {
            const choiceSpy = jest.spyOn(component.choice, 'emit');
            const emitChoiceSpy = jest.spyOn(modalService, 'emitChoice');
            const closeSpy = jest.spyOn(component, 'close');

            const backdrop = document.createElement('div');
            const event = { target: backdrop, currentTarget: backdrop } as any;

            component.onBackdropClick(event);

            expect(choiceSpy).toHaveBeenCalledWith('none');
            expect(emitChoiceSpy).toHaveBeenCalledWith('none');
            expect(closeSpy).toHaveBeenCalled();
        });
    });

    describe('onLearnMoreClick', () => {
        it('should prevent default event behavior', () => {
            const event = { preventDefault: jest.fn() } as any;

            component.onLearnMoreClick(event);

            expect(event.preventDefault).toHaveBeenCalled();
        });

        it('should navigate to /llm-selection', () => {
            const event = { preventDefault: jest.fn() } as any;

            component.onLearnMoreClick(event);

            expect(router.navigate).toHaveBeenCalledWith(['/llm-selection']);
        });

        it('should close the modal after navigation', () => {
            const closeSpy = jest.spyOn(component, 'close');
            const event = { preventDefault: jest.fn() } as any;

            component.onLearnMoreClick(event);

            expect(closeSpy).toHaveBeenCalled();
        });

        it('should set isVisible to false after learn more click', () => {
            component.isVisible = true;
            const event = { preventDefault: jest.fn() } as any;

            component.onLearnMoreClick(event);

            expect(component.isVisible).toBeFalse();
        });
    });

    describe('Theme constant', () => {
        it('should expose Theme enum', () => {
            expect(component['Theme']).toBe(Theme);
        });
    });
});
