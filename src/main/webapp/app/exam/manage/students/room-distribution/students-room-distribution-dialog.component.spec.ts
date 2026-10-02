import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { StudentsRoomDistributionDialogComponent } from 'app/exam/manage/students/room-distribution/students-room-distribution-dialog.component';
import { StudentsRoomDistributionService } from 'app/exam/manage/services/students-room-distribution.service';
import { MockStudentsRoomDistributionService } from 'test/helpers/mocks/service/mock-students-room-distribution.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('StudentsRoomDistributionDialogComponent', () => {
    let component: StudentsRoomDistributionDialogComponent;
    let fixture: ComponentFixture<StudentsRoomDistributionDialogComponent>;
    let service: StudentsRoomDistributionService;

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };
    const rooms: RoomForDistributionDTO[] = [
        { id: 1, roomNumber: '1', name: 'one', building: 'AA' },
        { id: 2, roomNumber: '2', alternativeRoomNumber: '002', name: 'two', building: 'AA' },
        { id: 3, roomNumber: '3', alternativeRoomNumber: '003', name: 'three', alternativeName: 'threeee', building: 'AA' },
    ] as RoomForDistributionDTO[];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                FaIconComponent,
                FormsModule,
                StudentsRoomDistributionDialogComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(HelpIconComponent),
            ],
            providers: [
                provideHttpClientTesting(),
                MockProvider(SessionStorageService),
                MockProvider(LocalStorageService),
                provideRouter([]),
                MockProvider(ActivatedRoute),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: StudentsRoomDistributionService, useClass: MockStudentsRoomDistributionService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(StudentsRoomDistributionDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', course.id);
        fixture.componentRef.setInput('exam', exam);
        service = TestBed.inject(StudentsRoomDistributionService);

        component.openDialog();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should close the dialog on pressing the close button', () => {
        fixture.detectChanges();
        const button = document.body.querySelector('#cancel-button') as HTMLButtonElement;
        button.click();
        expect(component.dialogVisible()).toBe(false);
    });

    it('should not have selected rooms and distribute button disabled on first open', () => {
        fixture.detectChanges();
        expect(component.hasSelectedRooms()).toBe(false);
        const button = document.body.querySelector('#finish-button') as HTMLButtonElement;
        expect(button.disabled).toBe(true);
    });

    it('should request room data from the server on initial opening', () => {
        fixture.detectChanges();

        expect(service.loadRoomData).toHaveBeenCalledOnce();
    });

    it('should show finish button after selecting a room', () => {
        fixture.detectChanges();
        component.pickSelectedRoom(rooms[0]);
        fixture.changeDetectorRef.detectChanges();

        const button = document.body.querySelector('#finish-button') as HTMLButtonElement;
        expect(component.hasSelectedRooms()).toBe(true);
        expect(button.hidden).toBe(false);
    });

    it('should remove selected room and disable finish button again', () => {
        fixture.detectChanges();
        component.pickSelectedRoom(rooms[0]);
        fixture.changeDetectorRef.detectChanges();
        expect(component.hasSelectedRooms()).toBe(true);

        component.removeSelectedRoom(rooms[0]);
        fixture.changeDetectorRef.detectChanges();

        expect(component.hasSelectedRooms()).toBe(false);
        const button = document.body.querySelector('#finish-button') as HTMLButtonElement;
        expect(button.disabled).toBe(true);
    });

    it('should not be able to select same room twice', () => {
        component.pickSelectedRoom(rooms[0]);
        component.pickSelectedRoom(rooms[0]);
        expect(component.selectedRooms()).toEqual([rooms[0]]);
    });

    it('should call distributeStudentsAcrossRooms with default arguments and close modal on finish', () => {
        const distributeSpy = vi.spyOn(service, 'distributeStudentsAcrossRooms');

        component.pickSelectedRoom(rooms[0]);
        fixture.changeDetectorRef.detectChanges();

        component.attemptDistributeAndCloseDialog();

        expect(distributeSpy).toHaveBeenCalledWith(course.id, exam.id, [rooms[0].id], 0.1, true);
    });

    it('should format room name correctly', () => {
        const formatted = component.formatter({
            id: 1,
            name: 'A',
            alternativeName: 'Alt',
            roomNumber: '101',
            alternativeRoomNumber: '102',
            building: 'B',
        });
        expect(formatted).toBe('A (Alt) – 101 (102) - [B]');
    });

    it('should suggest the rooms matching the search text', () => {
        (service as unknown as MockStudentsRoomDistributionService).availableRooms.set(rooms);

        component.searchRooms('t');

        expect(component.roomSuggestions().map((suggestion) => suggestion.room)).toEqual([rooms[1], rooms[2]]);
        expect(component.roomSuggestions()[0].label).toBe('two – 2 (002) - [AA]');
    });

    it('should not suggest rooms that are already selected', () => {
        (service as unknown as MockStudentsRoomDistributionService).availableRooms.set(rooms);
        component.pickSelectedRoom(rooms[1]);

        component.searchRooms('');

        expect(component.roomSuggestions().map((suggestion) => suggestion.room)).toEqual([rooms[0], rooms[2]]);
    });

    it('should empty the search field after a room was picked', () => {
        component.roomSearchValue.set(component.formatter(rooms[0]));

        component.pickSelectedRoom(rooms[0]);

        expect(component.roomSearchValue()).toBeUndefined();
    });

    it('should clamp the reserve percentage to 0-100', () => {
        component.setReservePercentage(25);
        expect(component.reservePercentage()).toBe(25);

        component.setReservePercentage(259);
        expect(component.reservePercentage()).toBe(100);

        component.setReservePercentage(-4);
        expect(component.reservePercentage()).toBe(0);

        component.setReservePercentage(null);
        expect(component.reservePercentage()).toBe(0);
    });

    it('should distribute with the configured reserve factor and narrow layouts', () => {
        const distributeSpy = vi.spyOn(service, 'distributeStudentsAcrossRooms');
        component.pickSelectedRoom(rooms[0]);
        component.setReservePercentage(25);
        component.allowNarrowLayouts.set(true);

        component.attemptDistributeAndCloseDialog();

        expect(distributeSpy).toHaveBeenCalledWith(course.id, exam.id, [rooms[0].id], 0.25, false);
    });

    it('should never show percentage >= 100 in the not enough capacity warning message', () => {
        const examWithUsers: Exam = {
            course,
            id: 2,
            title: 'Exam Title',
            examUsers: [] as ExamUser[],
        };
        for (let i = 0; i < 1000; i++) {
            examWithUsers.examUsers!.push({} as ExamUser);
        }

        fixture.componentRef.setInput('exam', examWithUsers);
        (service as unknown as MockStudentsRoomDistributionService).capacityData.set({
            combinedDefaultCapacity: 999,
            combinedMaximumCapacity: 999,
        });
        component.selectedRooms.set([rooms[0]]);

        fixture.changeDetectorRef.detectChanges();
        expect(component.canSeatAllStudents()).toBe(false);

        const warningElement: HTMLElement | null = document.body.querySelector('tumaet-ui-message[severity="warn"]') ?? document.body.querySelector('tumaet-ui-message');

        expect(warningElement).toBeTruthy();

        const text = warningElement!.textContent ?? '';
        expect(text).toBe('artemisApp.exam.examUsers.rooms.notEnoughSeatsForStudents');
        expect(component.seatInfo().percentage).toBe(99);
    });

    it('should pre-select all used rooms on second distribution', () => {
        vi.spyOn(service, 'loadRoomsUsedInExam').mockReturnValue(of([rooms[0], rooms[1]] as RoomForDistributionDTO[]));

        component.openDialog();
        fixture.changeDetectorRef.detectChanges();

        expect(component.hasSelectedRooms()).toBe(true);
        expect(component.selectedRooms()).toHaveLength(2);
        expect(component.selectedRooms()).toContain(rooms[0]);
        expect(component.selectedRooms()).toContain(rooms[1]);
    });

    it('exam room management link should open in a new tab', () => {
        fixture.changeDetectorRef.detectChanges();

        const link = document.body.querySelector<HTMLAnchorElement>('#examRoomManagementLink')!;

        expect(link).toBeTruthy();
        expect(link.href).toContain('/exams/rooms');
        expect(link.target).toBe('_blank');
    });
});
