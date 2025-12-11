import { BreakpointObserver, BreakpointState, Breakpoints } from '@angular/cdk/layout';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisChatbotWidgetComponent } from 'app/iris/overview/exercise-chatbot/widget/chatbot-widget.component';
import { IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { NavigationStart, Router } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { By } from '@angular/platform-browser';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import interact from 'interactjs';

jest.mock('interactjs', () => {
    const interact = jest.fn(() => {
        return {
            resizable: jest.fn().mockReturnThis(),
            draggable: jest.fn().mockReturnThis(),
        };
    });

    (interact as unknown as { modifiers: unknown }).modifiers = {
        restrictEdges: jest.fn((opts: unknown) => ({ type: 'restrictEdges', opts })),
        restrictSize: jest.fn((opts: unknown) => ({ type: 'restrictSize', opts })),
        restrictRect: jest.fn((opts: unknown) => ({ type: 'restrictRect', opts })),
    };

    return { __esModule: true, default: interact };
});

type InteractResizeMoveEvent = {
    target: HTMLElement;
    rect: { width: number; height: number };
    deltaRect: { left: number; top: number };
};

type InteractDragMoveEvent = {
    target: HTMLElement;
    dx: number;
    dy: number;
};

describe('IrisChatbotWidgetComponent', () => {
    let component: IrisChatbotWidgetComponent;
    let fixture: ComponentFixture<IrisChatbotWidgetComponent>;
    let breakpoint$: BehaviorSubject<BreakpointState>;
    let dialog: MatDialog;
    let routerEvents$: Subject<unknown>;

    beforeEach(async () => {
        routerEvents$ = new Subject<unknown>();
        breakpoint$ = new BehaviorSubject<BreakpointState>({
            matches: false,
            breakpoints: { [Breakpoints.Handset]: false },
        });
        await TestBed.configureTestingModule({
            declarations: [IrisChatbotWidgetComponent, MockComponent(IrisBaseChatbotComponent)],
            providers: [
                MockProvider(IrisChatService),
                { provide: MatDialog, useValue: { closeAll: jest.fn() } },
                { provide: Router, useValue: { events: routerEvents$.asObservable() } },
                { provide: MAT_DIALOG_DATA, useValue: { isChatGptWrapper: false } },
                {
                    provide: BreakpointObserver,
                    useValue: {
                        observe: () => breakpoint$.asObservable(),
                        isMatched: (query: string | string[]) => false,
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisChatbotWidgetComponent);
        component = fixture.componentInstance;

        dialog = TestBed.inject(MatDialog);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should call closeAll on dialog when closeChat is called', () => {
        const closeAllSpy = jest.spyOn(dialog, 'closeAll');
        component.closeChat();
        expect(closeAllSpy).toHaveBeenCalled();
    });

    it('should close all dialogs on NavigationStart', () => {
        const closeAllSpy = jest.spyOn(dialog, 'closeAll');

        routerEvents$.next(new NavigationStart(1, '/somewhere'));

        expect(closeAllSpy).toHaveBeenCalled();
    });

    it('should toggle fullSize and call setPositionAndScale when toggleFullSize is called', () => {
        const setPositionAndScaleSpy = jest.spyOn(component, 'setPositionAndScale');
        const initialFullSize = component.fullSize;
        component.toggleFullSize();
        expect(component.fullSize).toBe(!initialFullSize);
        expect(setPositionAndScaleSpy).toHaveBeenCalled();
    });

    it('should add or remove cdk-global-scroll class when toggleScrollLock is called', () => {
        component.toggleScrollLock(true);
        expect(document.body.classList.contains('cdk-global-scroll')).toBeTrue();
        component.toggleScrollLock(false);
        expect(document.body.classList.contains('cdk-global-scroll')).toBeFalse();
    });

    it('should call onResize when window is resized', () => {
        const spy = jest.spyOn(component, 'onResize');
        window.dispatchEvent(new Event('resize'));
        expect(spy).toHaveBeenCalled();
    });

    it('should call setPositionAndScale on initialization', () => {
        const spy = jest.spyOn(component, 'setPositionAndScale');
        component.ngAfterViewInit();
        expect(spy).toHaveBeenCalled();
    });

    it('should add or remove cdk-global-scroll class when mouse enters or leaves container', () => {
        const spy = jest.spyOn(component, 'toggleScrollLock');
        const container = fixture.debugElement.query(By.css('.container'));

        container.triggerEventHandler('mouseenter', null);
        fixture.detectChanges();
        expect(document.body.classList.contains('cdk-global-scroll')).toBeTrue();
        expect(spy).toHaveBeenCalledWith(true);

        container.triggerEventHandler('mouseleave', null);
        fixture.detectChanges();
        expect(document.body.classList.contains('cdk-global-scroll')).toBeFalse();
        expect(spy).toHaveBeenCalledWith(false);
    });

    it('should set isMobile to true when overlay container width is less than 600', () => {
        breakpoint$.next({
            matches: true,
            breakpoints: { [Breakpoints.Handset]: true },
        });
        // Setup DOM
        const overlay = document.createElement('div');
        overlay.className = 'cdk-overlay-container';

        overlay.getBoundingClientRect = jest.fn(() => ({
            x: 0,
            y: 0,
            width: 500,
            height: 600,
            top: 0,
            right: 500,
            bottom: 600,
            left: 0,
            toJSON: () => {},
        }));
        document.body.appendChild(overlay);

        const widget = document.createElement('div');
        widget.className = 'chat-widget';
        document.body.appendChild(widget);

        component.setPositionAndScale();

        expect(component.isMobile()).toBeTrue();

        // Clean up
        document.body.removeChild(overlay);
        document.body.removeChild(widget);
    });

    it('should not throw if cdk-overlay-container or chat-widget is missing in setPositionAndScale', () => {
        expect(() => component.setPositionAndScale()).not.toThrow();
    });

    it('should update widget size/position and fullSize from interactjs move listeners', () => {
        // DOM required by the handlers
        const overlay = document.createElement('div');
        overlay.className = 'cdk-overlay-container';
        overlay.getBoundingClientRect = jest.fn(() => ({
            x: 0,
            y: 0,
            width: 1000,
            height: 1000,
            top: 0,
            right: 1000,
            bottom: 1000,
            left: 0,
            toJSON: () => {},
        }));
        document.body.appendChild(overlay);

        const widget = document.createElement('div');
        widget.className = 'chat-widget';
        widget.setAttribute('data-x', '10');
        widget.setAttribute('data-y', '20');
        document.body.appendChild(widget);

        const interactMock = interact as unknown as jest.Mock;
        interactMock.mockClear();

        component.ngAfterViewInit();

        expect(interactMock).toHaveBeenCalledWith('.chat-widget');

        const chain = interactMock.mock.results[0].value as {
            resizable: jest.Mock;
            draggable: jest.Mock;
        };

        const resizableConfig = chain.resizable.mock.calls[0][0] as {
            listeners: { move: (e: InteractResizeMoveEvent) => void };
        };
        const draggableConfig = chain.draggable.mock.calls[0][0] as {
            listeners: { move: (e: InteractDragMoveEvent) => void };
        };

        // Resize move -> should set width/height, transform, data-x/y, and fullSize
        resizableConfig.listeners.move({
            target: widget,
            rect: { width: 950, height: 900 },
            deltaRect: { left: 5, top: 10 },
        });

        expect(widget.style.width).toBe('950px');
        expect(widget.style.height).toBe('900px');
        expect(widget.style.transform).toBe('translate(15px,30px)');
        expect(widget.getAttribute('data-x')).toBe('15');
        expect(widget.getAttribute('data-y')).toBe('30');
        expect(component.fullSize).toBeTrue();

        // Shrink -> should flip fullSize back
        resizableConfig.listeners.move({
            target: widget,
            rect: { width: 500, height: 500 },
            deltaRect: { left: -2, top: -3 },
        });
        expect(component.fullSize).toBeFalse();

        // Drag move -> should update transform and data-x/y
        draggableConfig.listeners.move({ target: widget, dx: 7, dy: -3 });

        expect(widget.style.transform).toBe(`translate(${widget.getAttribute('data-x')}px, ${widget.getAttribute('data-y')}px)`);

        // cleanup
        overlay.remove();
        widget.remove();
    });
});
