import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';

import { generateExampleTutorialGroupSession } from 'test/helpers/sample/tutorialgroup/tutorialGroupSessionExampleModels';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockDirective, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { By } from '@angular/platform-browser';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { AlertService } from 'app/shared/service/alert.service';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { runOnPushChangeDetection } from 'test/helpers/on-push-change-detection.helper';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupSessionRowComponent } from 'app/tutorialgroup/shared/tutorial-group-sessions-table/tutorial-group-session-row/tutorial-group-session-row.component';

describe('TutorialGroupSessionRowComponent', () => {
    let component: TutorialGroupSessionRowComponent;
    let fixture: ComponentFixture<TutorialGroupSessionRowComponent>;
    let session: TutorialGroupSession;
    let tutorialGroup: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, MockDirective(NgbPopover), ArtemisTranslatePipe, ArtemisDatePipe],
            declarations: [TutorialGroupSessionRowComponent],
            providers: [MockProvider(TutorialGroupSessionService), MockProvider(AlertService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupSessionRowComponent);
        component = fixture.componentInstance;
        session = generateExampleTutorialGroupSession({});
        tutorialGroup = generateExampleTutorialGroup({});
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.componentRef.setInput('session', session);
        fixture.componentRef.setInput('timeZone', 'Europe/Berlin');
        fixture.componentRef.setInput('showIdColumn', true);
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should display session canceled button when sessions are cancelled', () => {
        fixture.componentRef.setInput('session', Object.assign({}, session, { status: TutorialGroupSessionStatus.CANCELLED }));
        fixture.detectChanges();

        const sessionCanceledButton = fixture.debugElement.query(By.css('button.btn-outline-danger'));
        expect(sessionCanceledButton).not.toBeNull();
    });

    it('should save attendance count when input is changed', fakeAsync(() => {
        const tutorialGroupSessionService = TestBed.inject(TutorialGroupSessionService);
        const updateAttendanceCountSpy = jest
            .spyOn(tutorialGroupSessionService, 'updateAttendanceCount')
            .mockReturnValue(of(new HttpResponse<TutorialGroupSession>({ body: Object.assign({}, session, { attendanceCount: 5 }) })));
        const attendanceChangedSpy = jest.spyOn(component.attendanceChanged, 'emit');
        changeAttendanceInputAndSave();

        fixture.whenStable().then(() => {
            expect(updateAttendanceCountSpy).toHaveBeenCalledOnce();
            expect(updateAttendanceCountSpy).toHaveBeenCalledWith(tutorialGroup.course?.id, tutorialGroup.id, session.id, 5);
            expect(attendanceChangedSpy).toHaveBeenCalledOnce();
            expect(attendanceChangedSpy).toHaveBeenCalledWith(Object.assign({}, session, { attendanceCount: 5 }));
            expect(component.attendanceDiffersFromPersistedValue()).toBeFalse();
            expect(component.localSession().attendanceCount).toBe(5);
        });
    }));

    it('should reset attendance count when server call fails', fakeAsync(() => {
        const tutorialGroupSessionService = TestBed.inject(TutorialGroupSessionService);
        const updateAttendanceCountSpy = jest.spyOn(tutorialGroupSessionService, 'updateAttendanceCount').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        const attendanceChangedSpy = jest.spyOn(component.attendanceChanged, 'emit');
        changeAttendanceInputAndSave();

        fixture.whenStable().then(() => {
            expect(updateAttendanceCountSpy).toHaveBeenCalledOnce();
            expect(updateAttendanceCountSpy).toHaveBeenCalledWith(tutorialGroup.course?.id, tutorialGroup.id, session.id, 5);
            expect(attendanceChangedSpy).not.toHaveBeenCalled();
            expect(component.attendanceDiffersFromPersistedValue()).toBeFalse();
            expect(component.localSession().attendanceCount).toBe(component.persistedAttendanceCount);
        });
    }));
    function changeAttendanceInputAndSave() {
        const attendanceCountInput = fixture.debugElement.query(By.css('input'));
        attendanceCountInput.nativeElement.value = 5;
        attendanceCountInput.nativeElement.dispatchEvent(new Event('input'));
        attendanceCountInput.nativeElement.dispatchEvent(new Event('change'));
        runOnPushChangeDetection(fixture);
        expect(component.localSession().attendanceCount).toBe(5);
        expect(component.attendanceDiffersFromPersistedValue()).toBeTrue();

        const saveButton = fixture.debugElement.query(By.css('.input-group button')).nativeElement;
        saveButton.click();
    }
});
