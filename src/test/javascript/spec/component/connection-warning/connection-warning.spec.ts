import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { BehaviorSubject } from 'rxjs';
import { JhiConnectionWarningComponent } from 'app/shared/connection-warning/connection-warning.component';
import { CloseCircleComponent } from 'app/shared/close-circle/close-circle.component';
import { ConnectionState, JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { By } from '@angular/platform-browser';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';

describe('ConnectionWarning', () => {
    let fixture: ComponentFixture<JhiConnectionWarningComponent>;
    let component: JhiConnectionWarningComponent;
    let subject: BehaviorSubject<ConnectionState>;

    beforeEach(() => {
        subject = new BehaviorSubject<ConnectionState>(new ConnectionState(true, true, false));
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [JhiConnectionWarningComponent, CloseCircleComponent, NgbPopover, TranslatePipeMock],
            providers: [
                {
                    provide: JhiWebsocketService,
                    useValue: {
                        connectionState: subject.asObservable(),
                    },
                },
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

        expect(component.disconnected).toBe(false);
        expect(component.popover.isOpen()).toBe(false);

        const warningDiv = fixture.debugElement.query(By.css('.connection-warning'));
        expect(warningDiv).not.toBeNull();
        expect(warningDiv.classes).not.toContainEntry(['disconnected', true]);

        subject.next(new ConnectionState(false, true, false));
        fixture.detectChanges();

        expect(component.disconnected).toBe(true);
        expect(warningDiv.classes).toContainEntry(['disconnected', true]);

        tick(500);
        expect(component.popover.isOpen()).toBe(true);

        subject.next(new ConnectionState(true, true, false));
        fixture.detectChanges();

        tick(100);
        expect(component.disconnected).toBe(false);
        expect(component.popover.isOpen()).toBe(false);
        expect(warningDiv.classes).not.toContainEntry(['disconnected', true]);
    }));
});
