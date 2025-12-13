import { HttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { fakeAsync, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';
import { StudentsRoomDistributionDialogComponent } from 'app/exam/manage/students/room-distribution/students-room-distribution-dialog.component';
import { StudentsRoomDistributionService } from 'app/exam/manage/services/students-room-distribution.service';
import { MockStudentsRoomDistributionService } from 'test/helpers/mocks/service/mock-students-room-distribution.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

function dispatchInputEvent(inputElement: HTMLInputElement, value: string) {
    inputElement.value = value;
    inputElement.dispatchEvent(new Event('input'));
}

describe('StudentsRoomDistributionDialogComponent', () => {
    let component: StudentsRoomDistributionDialogComponent;
    let fixture: ComponentFixture<StudentsRoomDistributionDialogComponent>;
    let service: StudentsRoomDistributionService | MockStudentsRoomDistributionService;

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };
    const rooms: RoomForDistributionDTO[] = [
        { id: 1, roomNumber: '1', name: 'one', building: 'AA' },
        { id: 2, roomNumber: '2', alternativeRoomNumber: '002', name: 'two', building: 'AA' },
        { id: 3, roomNumber: '3', alternativeRoomNumber: '003', name: 'three', alternativeName: 'threeee', building: 'AA' },
    ] as RoomForDistributionDTO[];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent, FormsModule],
            declarations: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe), MockComponent(HelpIconComponent)],
            providers: [
                MockProvider(NgbActiveModal),
                MockProvider(HttpClient),
                MockProvider(SessionStorageService),
                MockProvider(LocalStorageService),
                MockProvider(Router),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
                { provide: StudentsRoomDistributionService, useClass: MockStudentsRoomDistributionService },
                provideNoopAnimations(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(StudentsRoomDistributionDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', course.id);
        fixture.componentRef.setInput('exam', exam);
        service = TestBed.inject(StudentsRoomDistributionService) as unknown as MockStudentsRoomDistributionService;

        jest.spyOn(service, 'loadRoomData').mockImplementation(() => {
            (service as MockStudentsRoomDistributionService).availableRooms.set(rooms);
        });

        component.openDialog();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should close the dialog on pressing the close button', () => {
        fixture.detectChanges();
        const button = document.body.querySelector('#cancel-button') as HTMLButtonElement;
        button.click();
        expect(component.dialogVisible()).toBeFalse();
    });

    it('should not have selected rooms and no finish button displayed on open', () => {
        fixture.detectChanges();
        expect(component.hasSelectedRooms()).toBeFalse();
        const button = fixture.debugElement.nativeElement.querySelector('#finish-button');
        expect(button.hidden).toBeTrue();
    });

    it('should request room data from the server on initial opening', () => {
        fixture.detectChanges();

        expect(service.loadRoomData).toHaveBeenCalledOnce();
    });

    it('should show finish button after selecting a room', () => {
        fixture.detectChanges();
        component.pickSelectedRoom({ item: rooms[0] });
        fixture.changeDetectorRef.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#finish-button');
        expect(component.hasSelectedRooms()).toBeTrue();
        expect(button.hidden).toBeFalse();
    });

    it('should remove selected room and hide finish button again', () => {
        fixture.detectChanges();
        component.pickSelectedRoom({ item: rooms[0] });
        fixture.changeDetectorRef.detectChanges();
        expect(component.hasSelectedRooms()).toBeTrue();

        component.removeSelectedRoom(rooms[0]);
        fixture.changeDetectorRef.detectChanges();

        expect(component.hasSelectedRooms()).toBeFalse();
        const button = fixture.debugElement.nativeElement.querySelector('#finish-button');
        expect(button.hidden).toBeTrue();
    });

    it('should not be able to select same room twice', () => {
        component.pickSelectedRoom({ item: rooms[0] });
        component.pickSelectedRoom({ item: rooms[0] });
        expect(component.selectedRooms()).toEqual([rooms[0]]);
    });

    it('should call distributeStudentsAcrossRooms with default arguments and close modal on finish', () => {
        const distributeSpy = jest.spyOn(service, 'distributeStudentsAcrossRooms');

        component.pickSelectedRoom({ item: rooms[0] });
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

    it('should find correct rooms', fakeAsync(() => {
        fixture.detectChanges();
        tick();

        let searchResult: RoomForDistributionDTO[] = [];
        component.search(of('t')).subscribe((rooms) => {
            searchResult = rooms;
        });

        tick(200);

        expect(searchResult).toHaveLength(2);
        expect(searchResult).toContainEqual(rooms[1]);
        expect(searchResult).toContainEqual(rooms[2]);
    }));

    it('should update reserve percentage when typing valid numbers', async () => {
        fixture.detectChanges();
        const input: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#reserveFactor');

        dispatchInputEvent(input, '25');
        fixture.changeDetectorRef.detectChanges();
        expect(input.value).toBe('25');

        input.dispatchEvent(new FocusEvent('focusout'));
        fixture.changeDetectorRef.detectChanges();
        expect(input.value).toBe('25');
    });

    it('should reset reserve factor to latest value when invalid input is entered', () => {
        fixture.detectChanges();
        const input: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#reserveFactor');

        dispatchInputEvent(input, '25');
        fixture.changeDetectorRef.detectChanges();

        dispatchInputEvent(input, '259');
        fixture.changeDetectorRef.detectChanges();
        expect(input.value).toBe('25');

        input.dispatchEvent(new FocusEvent('focusout'));
        fixture.changeDetectorRef.detectChanges();
        expect(input.value).toBe('25');

        dispatchInputEvent(input, '2 5');
        fixture.changeDetectorRef.detectChanges();
        expect(input.value).toBe('25');

        dispatchInputEvent(input, '25a');
        fixture.changeDetectorRef.detectChanges();
        expect(input.value).toBe('25');
    });

    it('should select all text when the input gains focus', async () => {
        fixture.detectChanges();
        const input: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#reserveFactor');
        input.value = '42';
        const selectSpy = jest.spyOn(input, 'select');

        input.dispatchEvent(new FocusEvent('focusin'));
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        expect(selectSpy).toHaveBeenCalledOnce();
    });

    it('should toggle use narrow layouts when switch is pressed', () => {
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowNarrowLayoutsToggle');

        expect(component.allowNarrowLayouts()).toBeFalse();

        checkbox.click();
        fixture.changeDetectorRef.detectChanges();
        expect(component.allowNarrowLayouts()).toBeTrue();

        checkbox.click();
        fixture.changeDetectorRef.detectChanges();
        expect(component.allowNarrowLayouts()).toBeFalse();
    });
});
