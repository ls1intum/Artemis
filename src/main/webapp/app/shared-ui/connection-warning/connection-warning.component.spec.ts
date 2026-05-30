import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { JhiConnectionWarningComponent } from 'app/shared-ui/connection-warning/connection-warning.component';
import { ConnectionState, WebsocketService } from 'app/foundation/service/websocket.service';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';

describe('ConnectionWarning', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<JhiConnectionWarningComponent>;
    let component: JhiConnectionWarningComponent;
    let subject: BehaviorSubject<ConnectionState>;

    beforeEach(() => {
        vi.useFakeTimers();

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
        vi.restoreAllMocks();
        vi.useRealTimers();
    });

    it('should show the indicator and popover on dropped connection', () => {
        fixture.detectChanges();

        expect(component.disconnected).toBeFalse();
        expect(component.popover.isOpen()).toBeFalse();

        const warningDiv = fixture.debugElement.query(By.css('.connection-warning'));
        expect(warningDiv).not.toBeNull();
        expect(warningDiv.classes['disconnected']).not.toBeTruthy();

        subject.next(new ConnectionState(false, true));
        fixture.changeDetectorRef.detectChanges();

        expect(component.disconnected).toBeTrue();
        expect(warningDiv.classes['disconnected']).toBeTrue();

        vi.advanceTimersByTime(300);
        expect(component.popover.isOpen()).toBeTrue();

        subject.next(new ConnectionState(true, true));
        fixture.changeDetectorRef.detectChanges();

        vi.advanceTimersByTime(100);
        expect(component.disconnected).toBeFalse();
        expect(component.popover.isOpen()).toBeFalse();
        expect(warningDiv.classes['disconnected']).not.toBeTruthy();
    });
});
