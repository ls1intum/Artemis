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
import { StudentsRoomDistributionDialogComponent } from 'app/exam/manage/students/room-distribution/students-room-distribution-dialog.component';
import { StudentsRoomDistributionService } from 'app/exam/manage/students/room-distribution/students-room-distribution.service';
import { MockStudentsRoomDistributionService } from 'test/helpers/mocks/service/mock-students-room-distribution.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { RoomForDistributionDTO } from 'app/exam/manage/students/room-distribution/students-room-distribution.model';

function dispatchInputEvent(inputElement: HTMLInputElement, value: string) {
    inputElement.value = value;
    inputElement.dispatchEvent(new Event('input'));
}

describe('StudentsRoomDistributionDialogComponent', () => {
    let component: StudentsRoomDistributionDialogComponent;
    let fixture: ComponentFixture<StudentsRoomDistributionDialogComponent>;
    let service: StudentsRoomDistributionService;

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };
    const rooms: RoomForDistributionDTO[] = [
        { id: 1, number: '1', name: 'one', building: 'AA' },
        { id: 2, number: '2', alternativeNumber: '002', name: 'two', building: 'AA' },
        { id: 3, number: '3', alternativeNumber: '003', name: 'three', alternativeName: 'threeee', building: 'AA' },
    ] as RoomForDistributionDTO[];

    let ngbModal: NgbActiveModal;

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
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(StudentsRoomDistributionDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', course.id);
        fixture.componentRef.setInput('exam', exam);
        ngbModal = TestBed.inject(NgbActiveModal);
        service = TestBed.inject(StudentsRoomDistributionService);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should close dialog on pressing the close cross', () => {
        const spyModalDismiss = jest.spyOn(ngbModal, 'dismiss');
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#close-cross');
        button.click();
        expect(spyModalDismiss).toHaveBeenCalledOnce();
    });

    it('should close the dialog on pressing the close button', () => {
        const spyModalDismiss = jest.spyOn(ngbModal, 'dismiss');
        fixture.detectChanges();
        const button = fixture.debugElement.nativeElement.querySelector('#cancel-button');
        button.click();
        expect(spyModalDismiss).toHaveBeenCalledOnce();
    });

    it('should not have selected rooms and no finish button displayed on open', () => {
        fixture.detectChanges();
        expect(component.hasSelectedRooms()).toBeFalse();
        const button = fixture.debugElement.nativeElement.querySelector('#finish-button');
        expect(button.hidden).toBeTrue();
    });

    it('should request room data from the server on initial opening', () => {
        const getRoomDataSpy = jest.spyOn(service, 'getRoomData');
        fixture.detectChanges();
        expect(getRoomDataSpy).toHaveBeenCalledOnce();
    });

    it('should show finish button after selecting a room', () => {
        fixture.detectChanges();
        component.onRoomSelected({ item: rooms[0] });
        fixture.detectChanges();

        const button = fixture.debugElement.nativeElement.querySelector('#finish-button');
        expect(component.hasSelectedRooms()).toBeTrue();
        expect(button.hidden).toBeFalse();
    });

    it('should remove selected room and hide finish button again', () => {
        fixture.detectChanges();
        component.onRoomSelected({ item: rooms[0] });
        fixture.detectChanges();
        expect(component.hasSelectedRooms()).toBeTrue();

        component.removeSelectedRoom(rooms[0]);
        fixture.detectChanges();

        expect(component.hasSelectedRooms()).toBeFalse();
        const button = fixture.debugElement.nativeElement.querySelector('#finish-button');
        expect(button.hidden).toBeTrue();
    });

    it('should not be able to select same room twice', () => {
        component.onRoomSelected({ item: rooms[0] });
        component.onRoomSelected({ item: rooms[0] });
        expect(component.selectedRooms()).toEqual([rooms[0]]);
    });

    it('should call distributeStudentsAcrossRooms with default arguments and close modal on finish', () => {
        const distributeSpy = jest.spyOn(service, 'distributeStudentsAcrossRooms');
        const modalCloseSpy = jest.spyOn(ngbModal, 'close');

        component.onRoomSelected({ item: rooms[0] });
        fixture.detectChanges();

        component.onFinish();

        expect(distributeSpy).toHaveBeenCalledWith(course.id, exam.id, [rooms[0].id], 0.1, true);
        expect(modalCloseSpy).toHaveBeenCalled();
    });

    it('should format room name correctly', () => {
        const formatted = component.formatter({
            id: 1,
            name: 'A',
            alternativeName: 'Alt',
            number: '101',
            alternativeNumber: '102',
            building: 'B',
        });
        expect(formatted).toBe('A (Alt) – 101 (102) - [B]');
    });

    it('should filter rooms correctly with findAllMatchingRoomsForTerm', () => {
        fixture.detectChanges();
        const result = component.findAllMatchingRoomsForTerm('two');
        expect(result).toHaveLength(1);
        expect(result[0].name).toBe('two');
    });

    it('should update reserve percentage when typing valid numbers', async () => {
        fixture.detectChanges();
        const input: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#reserveFactor');

        dispatchInputEvent(input, '25');
        fixture.detectChanges();
        expect(input.value).toBe('25');

        input.dispatchEvent(new FocusEvent('focusout'));
        fixture.detectChanges();
        expect(input.value).toBe('25');
    });

    it('should reset reserve factor to latest value when invalid input is entered', () => {
        fixture.detectChanges();
        const input: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#reserveFactor');

        dispatchInputEvent(input, '25');
        fixture.detectChanges();

        dispatchInputEvent(input, '259');
        fixture.detectChanges();
        expect(input.value).toBe('25');

        input.dispatchEvent(new FocusEvent('focusout'));
        fixture.detectChanges();
        expect(input.value).toBe('25');

        dispatchInputEvent(input, '2 5');
        fixture.detectChanges();
        expect(input.value).toBe('25');

        dispatchInputEvent(input, '25a');
        fixture.detectChanges();
        expect(input.value).toBe('25');
    });

    it('should select all text when the input gains focus', async () => {
        fixture.detectChanges();
        const input: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#reserveFactor');
        input.value = '42';
        const selectSpy = jest.spyOn(input, 'select');

        input.dispatchEvent(new FocusEvent('focusin'));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(selectSpy).toHaveBeenCalledOnce();
    });

    it('should toggle use narrow layouts when switch is pressed', () => {
        fixture.detectChanges();
        const checkbox: HTMLInputElement = fixture.debugElement.nativeElement.querySelector('#allowNarrowLayoutsToggle');

        expect(component.allowNarrowLayouts()).toBeFalse();

        checkbox.click();
        fixture.detectChanges();
        expect(component.allowNarrowLayouts()).toBeTrue();

        checkbox.click();
        fixture.detectChanges();
        expect(component.allowNarrowLayouts()).toBeFalse();
    });
});
