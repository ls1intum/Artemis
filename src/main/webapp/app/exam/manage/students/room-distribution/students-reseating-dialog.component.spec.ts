import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
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

    it('should request used rooms on creation', () => {
        const loadSpy = jest.spyOn(service, 'loadRoomsUsedInExam').mockReturnValue(of(rooms));
        fixture.detectChanges();

        expect(loadSpy).toHaveBeenCalledExactlyOnceWith(course.id, exam.id);
        expect(component['roomsUsedInExam']()).toEqual(rooms);
    });

    it('openDialog() should initialize fields correctly', () => {
        component.openDialog(examUser);
        fixture.detectChanges();

        expect(component.examUser()).toEqual(examUser);
        expect(component.selectedRoomNumber()).toBe('2.0.1');
        expect(component.selectedSeat()).toBe('');
        expect(component.dialogVisible()).toBeTrue();
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

    it('calling openDialog() twice should update fields to the latest user and trigger a new seat load for persisted rooms', () => {
        const userA = { ...examUser, id: 20, plannedRoom: rooms[0].roomNumber, plannedSeat: 'A1' };
        const userB = { ...examUser, id: 30, plannedRoom: rooms[1].roomNumber, plannedSeat: 'B2' };

        const loadUsedRoomsSpy = jest.spyOn(service, 'loadRoomsUsedInExam').mockReturnValue(of(rooms));
        const loadSeatsInSelectedRoomSpy = jest.spyOn(service, 'loadSeatsOfExamRoom');

        component.openDialog(userA);
        fixture.detectChanges();

        expect(loadSeatsInSelectedRoomSpy).toHaveBeenCalledExactlyOnceWith(rooms[0].id);

        component.openDialog(userB);
        fixture.detectChanges();

        expect(component.examUser()).toEqual(userB);
        expect(component.selectedRoomNumber()).toBe(rooms[1].roomNumber);
        expect(component.selectedSeat()).toBe('');
        expect(loadUsedRoomsSpy).toHaveBeenCalledOnce();
        expect(loadSeatsInSelectedRoomSpy).toHaveBeenCalledTimes(2);
        expect(loadSeatsInSelectedRoomSpy).toHaveBeenLastCalledWith(rooms[1].id);
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

    it('should format room name correctly', () => {
        const formatted = component['roomFormatter']({
            id: 1,
            name: 'A',
            alternativeName: 'Alt',
            roomNumber: '101',
            alternativeRoomNumber: '102',
            building: 'B',
        });
        expect(formatted).toBe('A (Alt) – 101 (102) - [B]');
    });

    it('should find correct rooms', fakeAsync(() => {
        jest.spyOn(service, 'loadRoomsUsedInExam').mockReturnValue(of(rooms));
        fixture.detectChanges();
        tick();

        let searchResult: RoomForDistributionDTO[] = [];
        component['roomSearch'](of('t')).subscribe((rooms) => {
            searchResult = rooms;
        });

        tick(200);

        expect(searchResult).toHaveLength(2);
        expect(searchResult).toContainEqual(rooms[1]);
        expect(searchResult).toContainEqual(rooms[2]);
    }));

    it('should find correct seats', fakeAsync(() => {
        jest.spyOn(service, 'loadRoomsUsedInExam').mockReturnValue(of(rooms));
        jest.spyOn(service, 'loadSeatsOfExamRoom').mockReturnValue(of({ seats: ['A1', 'A2', 'B1', 'B2', '1, 2', '1, 3'] }));

        component.openDialog(examUser);
        fixture.detectChanges();
        tick();

        let searchResult: string[] = [];
        component['examSeatSearch'](of('2')).subscribe((rooms) => {
            searchResult = rooms;
        });

        tick(200);

        expect(searchResult).toHaveLength(3);
        expect(searchResult).toContainEqual('A2');
        expect(searchResult).toContainEqual('B2');
        expect(searchResult).toContainEqual('1, 2');
    }));

    it('should close the dialog on pressing the close button', () => {
        component.openDialog(examUser);
        fixture.detectChanges();
        const button = document.body.querySelector('#cancel-button') as HTMLButtonElement;
        button.click();
        expect(component.dialogVisible()).toBeFalse();
    });
});
