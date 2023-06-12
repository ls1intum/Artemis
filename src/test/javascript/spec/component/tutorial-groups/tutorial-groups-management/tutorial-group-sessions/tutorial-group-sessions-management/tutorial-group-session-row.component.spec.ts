import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';

import { TutorialGroupSessionRowComponent } from 'app/course/tutorial-groups/shared/tutorial-group-sessions-table/tutorial-group-session-row/tutorial-group-session-row.component';
import { generateExampleTutorialGroupSession } from '../../../helpers/tutorialGroupSessionExampleModels';
import { TutorialGroupSession, TutorialGroupSessionStatus } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { By } from '@angular/platform-browser';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { generateExampleTutorialGroup } from '../../../helpers/tutorialGroupExampleModels';
import { NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupSessionService } from 'app/course/tutorial-groups/services/tutorial-group-session.service';
import { AlertService } from 'app/core/util/alert.service';
import { FormsModule } from '@angular/forms';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { runOnPushChangeDetection } from '../../../../../helpers/on-push-change-detection.helper';

describe('TutorialGroupSessionRowComponent', () => {
    let component: TutorialGroupSessionRowComponent;
    let fixture: ComponentFixture<TutorialGroupSessionRowComponent>;
    let session: TutorialGroupSession;
    let tutorialGroup: TutorialGroup;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, MockDirective(NgbPopover)],
            declarations: [TutorialGroupSessionRowComponent, MockPipe(ArtemisDatePipe), MockPipe(ArtemisTranslatePipe)],
            providers: [MockProvider(TutorialGroupSessionService), MockProvider(AlertService)],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupSessionRowComponent);
        component = fixture.componentInstance;
        session = generateExampleTutorialGroupSession({});
        tutorialGroup = generateExampleTutorialGroup({});
        component.session = session;
        component.tutorialGroup = tutorialGroup;
        component.timeZone = 'Europe/Berlin';
        component.showIdColumn = true;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set class of cancelled sessions correctly', () => {
        component.session = { ...session, status: TutorialGroupSessionStatus.CANCELLED };
        component.ngOnChanges();

        // all columns should have the table danger class
        const tableCells = fixture.debugElement.queryAll(By.css('td'));
        tableCells.forEach((tableCell) => {
            expect(tableCell.nativeElement.classList).toContain('table-danger');
        });
    });

    it('should save attendance count when input is changed', fakeAsync(() => {
        const tutorialGroupSessionService = TestBed.inject(TutorialGroupSessionService);
        const updateAttendanceCountSpy = jest
            .spyOn(tutorialGroupSessionService, 'updateAttendanceCount')
            .mockReturnValue(of(new HttpResponse<TutorialGroupSession>({ body: { ...session, attendanceCount: 5 } })));
        const attendanceChangedSpy = jest.spyOn(component.attendanceChanged, 'emit');
        changeAttendanceInputAndSave();

        fixture.whenStable().then(() => {
            expect(updateAttendanceCountSpy).toHaveBeenCalledOnce();
            expect(updateAttendanceCountSpy).toHaveBeenCalledWith(tutorialGroup.course?.id, tutorialGroup.id, session.id, 5);
            expect(attendanceChangedSpy).toHaveBeenCalledOnce();
            expect(attendanceChangedSpy).toHaveBeenCalledWith({ ...session, attendanceCount: 5 });
            expect(component.attendanceDiffersFromPersistedValue).toBeFalse();
            expect(component.session.attendanceCount).toBe(5);
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
            expect(component.attendanceDiffersFromPersistedValue).toBeFalse();
            expect(component.session.attendanceCount).toBe(component.persistedAttendanceCount);
        });
    }));
    function changeAttendanceInputAndSave() {
        const attendanceCountInput = fixture.debugElement.query(By.css('input'));
        attendanceCountInput.nativeElement.value = 5;
        attendanceCountInput.nativeElement.dispatchEvent(new Event('input'));
        attendanceCountInput.nativeElement.dispatchEvent(new Event('change'));
        runOnPushChangeDetection(fixture);
        expect(component.session.attendanceCount).toBe(5);
        expect(component.attendanceDiffersFromPersistedValue).toBeTrue();

        const saveButton = fixture.debugElement.query(By.css('.input-group button')).nativeElement;
        saveButton.click();
    }
});
