import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { StudentsReseatingDialogComponent } from 'app/exam/manage/students/room-distribution/students-reseating-dialog.component';
import { StudentsRoomDistributionService } from 'app/exam/manage/services/students-room-distribution.service';
import { MockStudentsRoomDistributionService } from 'test/helpers/mocks/service/mock-students-room-distribution.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExamUser } from 'app/exam/shared/entities/exam-user.model';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

describe('StudentsReseatingDialogComponent', () => {
    let component: StudentsReseatingDialogComponent;
    let fixture: ComponentFixture<StudentsReseatingDialogComponent>;
    let service: StudentsRoomDistributionService;

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };
    const rooms: RoomForDistributionDTO[] = [
        { id: 1, roomNumber: '1.0.1', name: 'one', building: 'AA' },
        { id: 2, roomNumber: '2.0.1', alternativeRoomNumber: '002', name: 'two', building: 'AA' },
        {
            id: 3,
            roomNumber: '3.0.1',
            alternativeRoomNumber: '003',
            name: 'three',
            alternativeName: 'threeee',
            building: 'AA',
        },
    ] as RoomForDistributionDTO[];
    const examUser: ExamUser = {
        id: 99,
        plannedRoom: '2.0.1',
        plannedSeat: '4, 4',
        user: { firstName: 'Alice', lastName: 'Student' } as any,
        didCheckImage: false,
        didCheckLogin: false,
        didCheckName: false,
        didCheckRegistrationNumber: false,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: StudentsRoomDistributionService, useClass: MockStudentsRoomDistributionService },
                provideNoopAnimations(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(StudentsReseatingDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', course.id);
        fixture.componentRef.setInput('exam', exam);
        service = TestBed.inject(StudentsRoomDistributionService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('openDialog() should initialize fields correctly and fetch rooms', () => {
        const loadSpy = jest.spyOn(service, 'loadRoomsUsedInExam').mockReturnValue(of(rooms));

        component.openDialog(examUser);
        fixture.detectChanges();

        expect(component.examUser()).toEqual(examUser);
        expect(component.selectedRoomNumber()).toBe('2.0.1');
        expect(component.selectedSeat()).toBe('');
        expect(component.dialogVisible()).toBeTrue();
        expect(loadSpy).toHaveBeenCalledExactlyOnceWith(course.id, exam.id);
        expect(component['roomsUsedInExam']()).toEqual(rooms);
    });

    it('openDialog() should fallback to empty rooms list on error', () => {
        jest.spyOn(service, 'loadRoomsUsedInExam').mockReturnValue(throwError(() => new Error('fail')));

        component.openDialog(examUser);
        fixture.detectChanges();

        expect(component['roomsUsedInExam']()).toEqual([]);
    });

    it('closeDialog() should close the dialog', () => {
        component.dialogVisible.set(true);

        component.closeDialog();

        expect(component.dialogVisible()).toBeFalse();
    });

    it('calling openDialog() twice should update fields to the latest user', () => {
        const userA = { ...examUser, id: 20, plannedRoom: '1.1.1', plannedSeat: 'A1' };
        const userB = { ...examUser, id: 30, plannedRoom: '2.2.2', plannedSeat: 'B2' };

        const loadSpy = jest.spyOn(service, 'loadRoomsUsedInExam').mockReturnValue(of(rooms));

        component.openDialog(userA);
        fixture.detectChanges();

        component.openDialog(userB);
        fixture.detectChanges();

        expect(component.examUser()).toEqual(userB);
        expect(component.selectedRoomNumber()).toBe('2.2.2');
        expect(component.selectedSeat()).toBe('');
        expect(loadSpy).toHaveBeenCalledTimes(2);
    });

    it('should call reseatStudent with correct values', () => {
        jest.spyOn(service, 'loadRoomsUsedInExam').mockReturnValue(of(rooms));
        const reseatSpy = jest.spyOn(service, 'reseatStudent').mockReturnValue(of());

        component.openDialog(examUser);
        fixture.detectChanges();

        component.selectedRoomNumber.set(rooms[0].roomNumber);
        component.selectedSeat.set('20, 13');
        component['attemptReseatAndCloseDialogOnSuccess']();

        expect(reseatSpy).toHaveBeenCalledExactlyOnceWith(component.courseId(), component.exam().id!, examUser.id, rooms[0].roomNumber, '20, 13');
    });

    it('should show if location is known', () => {
        jest.spyOn(service, 'loadRoomsUsedInExam').mockReturnValue(of(rooms));
        jest.spyOn(service, 'loadSeatsOfExamRoom').mockReturnValue(of({ seats: ['A1', 'A2', 'A3', 'A4'] }));

        component.openDialog(examUser);
        fixture.detectChanges();

        component.selectedRoomNumber.set(rooms[0].roomNumber);
        fixture.detectChanges();
        expect(component.selectedRoomIsPersisted()).toBeTrue();
        expect(component.selectedSeatIsPersisted()).toBeFalse();

        component.selectedSeat.set('A3');
        fixture.detectChanges();
        expect(component.selectedRoomIsPersisted()).toBeTrue();
        expect(component.selectedSeatIsPersisted()).toBeTrue();
    });
});
