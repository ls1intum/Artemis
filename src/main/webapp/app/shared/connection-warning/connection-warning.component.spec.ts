import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { JhiConnectionWarningComponent } from 'app/shared/connection-warning/connection-warning.component';
import { ConnectionState, WebsocketService } from 'app/shared/service/websocket.service';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ConnectionWarning', () => {
    let fixture: ComponentFixture<JhiConnectionWarningComponent>;
    let component: JhiConnectionWarningComponent;
    let subject: BehaviorSubject<ConnectionState>;

    beforeEach(() => {
        subject = new BehaviorSubject<ConnectionState>(new ConnectionState(true, true));
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: WebsocketService,
                    useValue: {
                        connectionState: subject.asObservable(),
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(JhiConnectionWarningComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should show the indicator and popover on dropped connection', fakeAsync(() => {
        fixture.detectChanges();

        expect(component.disconnected).toBeFalse();
        expect(component.popover.isOpen()).toBeFalse();

        const warningDiv = fixture.debugElement.query(By.css('.connection-warning'));
        expect(warningDiv).not.toBeNull();
        expect(warningDiv.classes).not.toContainEntry(['disconnected', true]);

        subject.next(new ConnectionState(false, true));
        fixture.changeDetectorRef.detectChanges();

        expect(component.disconnected).toBeTrue();
        expect(warningDiv.classes).toContainEntry(['disconnected', true]);

        tick(500);
        expect(component.popover.isOpen()).toBeTrue();

        subject.next(new ConnectionState(true, true));
        fixture.changeDetectorRef.detectChanges();

        tick(100);
        expect(component.disconnected).toBeFalse();
        expect(component.popover.isOpen()).toBeFalse();
        expect(warningDiv.classes).not.toContainEntry(['disconnected', true]);
    }));
});
