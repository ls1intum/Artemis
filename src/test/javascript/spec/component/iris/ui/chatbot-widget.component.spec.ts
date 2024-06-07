import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import { IrisChatService } from 'app/iris/iris-chat.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';
import { IrisBaseChatbotComponent } from 'app/iris/base-chatbot/iris-base-chatbot.component';

describe('IrisChatbotWidgetComponent', () => {
    let component: IrisChatbotWidgetComponent;
    let fixture: ComponentFixture<IrisChatbotWidgetComponent>;
    let dialog: MatDialog;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [IrisChatbotWidgetComponent, MockComponent(IrisBaseChatbotComponent)],
            providers: [MockProvider(IrisChatService), { provide: MatDialog, useValue: { closeAll: jest.fn() } }, { provide: Router, useValue: { events: of() } }],
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
});
