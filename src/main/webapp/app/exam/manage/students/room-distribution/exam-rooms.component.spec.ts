/**
 * Vitest tests for ExamRoomsComponent.
 * Tests the view for managing exam room data including file uploads,
 * room overview, and data deletion functionality.
 */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

import { ExamRoomsComponent } from 'app/exam/manage/students/room-distribution/exam-rooms.component';
import { AdminExamRoomManagementApi } from 'app/openapi/api/admin-exam-room-management-api';
import { ExamRoomManagementApi } from 'app/openapi/api/exam-room-management-api';
import type { ExamRoom } from 'app/openapi/model/exam-room';
import type { ExamRoomDeletionSummary } from 'app/openapi/model/exam-room-deletion-summary';
import type { ExamRoomOverview } from 'app/openapi/model/exam-room-overview';
import type { ExamRoomUploadInformation } from 'app/openapi/model/exam-room-upload-information';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MAX_FILE_SIZE } from 'app/foundation/constants/input.constants';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('ExamRoomsComponent', () => {
    let component: ExamRoomsComponent;
    let fixture: ComponentFixture<ExamRoomsComponent>;
    let adminApi: AdminExamRoomManagementApi;
    let examApi: ExamRoomManagementApi;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamRoomsComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useClass: MockAlertService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamRoomsComponent);
        component = fixture.componentInstance;
        adminApi = TestBed.inject(AdminExamRoomManagementApi);
        examApi = TestBed.inject(ExamRoomManagementApi);

        // getRoomOverview is called on page load, provide default mock
        vi.spyOn(adminApi, 'getExamRoomOverview').mockReturnValue(of({ newestUniqueExamRooms: [] } as ExamRoomOverview));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /**
     * Helper to simulate file selection on input element.
     */
    function setInputFiles(input: HTMLInputElement, files: File[]) {
        Object.defineProperty(input, 'files', { value: files });
        input.dispatchEvent(new Event('change'));
    }

    /**
     * Creates mock admin overview with a single room.
     */
    function mockServiceWithSingleRoom(api: Pick<AdminExamRoomManagementApi, 'getExamRoomOverview'> | Pick<ExamRoomManagementApi, 'getExamRoomOverview'> = adminApi): ExamRoom {
        const examRoom: ExamRoom = {
            roomNumber: '123.456.789',
            name: 'Audimax',
            building: 'MI',
            numberOfSeats: 50,
            layoutStrategies: [
                {
                    name: 'default',
                    type: 'FIXED_SELECTION',
                    capacity: 30,
                },
            ],
        } as ExamRoom;

        vi.spyOn(api, 'getExamRoomOverview').mockReturnValue(of({ newestUniqueExamRooms: [examRoom] } as ExamRoomOverview));

        return examRoom;
    }

    /**
     * Creates mock for successful file upload.
     */
    function mockServiceUploadSuccess(): ExamRoomUploadInformation {
        const uploadData: ExamRoomUploadInformation = {
            uploadedFileName: 'my_file.zip',
            numberOfUploadedRooms: 1,
            numberOfUploadedSeats: 50,
            uploadedRoomNames: ['Audimax'],
        } as ExamRoomUploadInformation;

        vi.spyOn(adminApi, 'uploadRoomZip').mockReturnValue(of(uploadData));

        return uploadData;
    }

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should load exam room overview on page load', () => {
        fixture.detectChanges();

        // THEN
        expect(adminApi.getExamRoomOverview).toHaveBeenCalledOnce();
        expect(component.hasOverview()).toBe(true);
        expect(component.numberOfAvailable()!.examRooms).toBe(0);
        expect(component.numberOfAvailable()!.examSeats).toBe(0);
        expect(component.hasExamRoomData()).toBe(false);
    });

    it('should load the exam context and hide administration actions for instructors', () => {
        vi.spyOn(TestBed.inject(AccountService), 'isAdmin').mockReturnValue(false);
        TestBed.inject(ActivatedRoute).snapshot.queryParams = { courseId: '1', examId: '2' };
        const room = mockServiceWithSingleRoom(examApi);
        fixture.destroy();
        fixture = TestBed.createComponent(ExamRoomsComponent);
        component = fixture.componentInstance;

        fixture.detectChanges();

        expect(examApi.getExamRoomOverview).toHaveBeenCalledWith(1, 2);
        expect(adminApi.getExamRoomOverview).not.toHaveBeenCalled();
        expect(component.hasExamRoomData()).toBe(true);
        expect(fixture.nativeElement.textContent).toContain(room.name);
        expect(fixture.nativeElement.querySelector('#roomDataFileSelectButton')).toBeNull();
        expect(fixture.nativeElement.querySelector('#roomDataUpload')).toBeNull();
        expect(fixture.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused')).toBeNull();
    });

    it('should properly extract values from room overview', () => {
        // GIVEN
        const uploadedRoom: ExamRoom = mockServiceWithSingleRoom();

        fixture.detectChanges();

        expect(component.hasOverview()).toBe(true);
        expect(adminApi.getExamRoomOverview).toHaveBeenCalledOnce();

        expect(component.numberOfAvailable()!.examRooms).toBe(1);
        expect(component.numberOfAvailable()!.examSeats).toBe(50);

        expect(component.hasExamRoomData()).toBe(true);
        expect(component.examRoomData()).toHaveLength(1);
        expect(component.examRoomData()![0]).toEqual({
            ...uploadedRoom,
            defaultCapacity: 30,
            maxCapacity: 30,
        });
    });

    it('should re-sort the room table when a column header sort is applied', () => {
        const roomA = { roomNumber: '1', name: 'Alpha', building: 'MI', numberOfSeats: 10, layoutStrategies: [] } as ExamRoom;
        const roomB = { roomNumber: '2', name: 'Beta', building: 'MW', numberOfSeats: 20, layoutStrategies: [] } as ExamRoom;
        vi.spyOn(adminApi, 'getExamRoomOverview').mockReturnValue(of({ newestUniqueExamRooms: [roomB, roomA] } as ExamRoomOverview));
        fixture.detectChanges();
        expect(component.examRoomData()!.map((room) => room.name)).toEqual(['Alpha', 'Beta']);

        component['onSortChange']({ field: 'name', order: -1 });

        expect(component.sortAttribute()).toBe('name');
        expect(component.ascending()).toBe(false);
        expect(component.examRoomData()!.map((room) => room.name)).toEqual(['Beta', 'Alpha']);

        component['onSortChange']({ field: 'building', order: 1 });

        expect(component.sortAttribute()).toBe('building');
        expect(component.ascending()).toBe(true);
        expect(component.examRoomData()!.map((room) => room.building)).toEqual(['MI', 'MW']);
    });

    it('should show error message on loadExamRoomOverview fail', () => {
        vi.spyOn(adminApi, 'getExamRoomOverview').mockReturnValue(throwError(() => new Error()));

        fixture.detectChanges();

        // THEN
        expect(adminApi.getExamRoomOverview).toHaveBeenCalledOnce();
        expect(component.hasOverview()).toBe(false);
    });

    it('should reject non-zip files', () => {
        fixture.detectChanges();
        const onFileSelectedSpy = vi.spyOn(component, 'onFileSelectedAcceptZip');
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');

        const nonZipFile = new File(['ignored content'], 'non.zip.txt', { type: 'text/plain' });

        setInputFiles(fileSelectButton, [nonZipFile]);
        fixture.detectChanges();

        expect(onFileSelectedSpy).toHaveBeenCalledOnce();
        expect(component.hasSelectedFile()).toBe(false);
        expect(uploadButton.disabled).toBe(true);
    });

    it('should reject empty input', () => {
        fixture.detectChanges();
        const onFileSelectedSpy = vi.spyOn(component, 'onFileSelectedAcceptZip');
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');

        setInputFiles(fileSelectButton, []);
        fixture.detectChanges();

        expect(onFileSelectedSpy).toHaveBeenCalledOnce();
        expect(component.hasSelectedFile()).toBe(false);
        expect(uploadButton.disabled).toBe(true);
    });

    it('should reject files exceeding max size', () => {
        fixture.detectChanges();
        const onFileSelectedSpy = vi.spyOn(component, 'onFileSelectedAcceptZip');
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');

        const sizeInBytes = MAX_FILE_SIZE + 100;
        const bytes = new Uint8Array(sizeInBytes);
        const oversizedZipFile = new File([bytes], 'my_file.zip', { type: 'application/zip' });

        setInputFiles(fileSelectButton, [oversizedZipFile]);
        fixture.detectChanges();

        expect(onFileSelectedSpy).toHaveBeenCalledOnce();
        expect(component.hasSelectedFile()).toBe(false);
        expect(uploadButton.disabled).toBe(true);
    });

    it('should enable upload button on valid file selection', () => {
        fixture.detectChanges();
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const fileSelectLabel = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelectButton');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges();

        expect(component.hasSelectedFile()).toBe(true);
        expect(fileSelectLabel.textContent.trim()).toBe('my_file.zip');
        expect(uploadButton.disabled).toBe(false);
    });

    it('should make upload service call and refresh overview on valid zip upload', () => {
        fixture.detectChanges();
        mockServiceUploadSuccess();
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges();
        uploadButton.click();
        fixture.detectChanges();

        expect(adminApi.uploadRoomZip).toHaveBeenCalledOnce();
        expect(adminApi.uploadRoomZip).toHaveBeenCalledWith(zipFile);
        expect(component.hasSelectedFile()).toBe(false);
        // Once from initial load, once from upload button click
        expect(adminApi.getExamRoomOverview).toHaveBeenCalledTimes(2);
    });

    it('should not show upload information on failure', () => {
        fixture.detectChanges();
        vi.spyOn(adminApi, 'uploadRoomZip').mockReturnValue(throwError(() => new Error()));
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges();
        uploadButton.click();
        fixture.detectChanges();

        expect(adminApi.uploadRoomZip).toHaveBeenCalledOnce();
        expect(adminApi.uploadRoomZip).toHaveBeenCalledWith(zipFile);
        expect(adminApi.getExamRoomOverview).toHaveBeenCalledOnce();
        expect(component.hasUploadInformation()).toBe(false);
    });

    it('should show upload summary on successful upload', () => {
        fixture.detectChanges();
        const uploadData = mockServiceUploadSuccess();
        const fileSelectButton = fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect');
        const uploadButton = fixture.debugElement.nativeElement.querySelector('#roomDataUpload');
        const zipFile = new File(['ignored content'], 'my_file.zip', { type: 'application/zip' });

        setInputFiles(fileSelectButton, [zipFile]);
        fixture.detectChanges();
        uploadButton.click();
        fixture.detectChanges();

        expect(component.hasUploadInformation()).toBe(true);
        expect(component.uploadInformation()!.uploadedFileName).toEqual(uploadData.uploadedFileName);
        expect(component.uploadInformation()!.numberOfUploadedRooms).toEqual(uploadData.numberOfUploadedRooms);
        expect(component.uploadInformation()!.numberOfUploadedSeats).toEqual(uploadData.numberOfUploadedSeats);
        expect(component.uploadInformation()!.uploadedRoomNames).toEqual(uploadData.uploadedRoomNames);
    });

    it('should call delete outdated and unused service on button click', () => {
        fixture.detectChanges();
        vi.spyOn(adminApi, 'deleteAllOutdatedAndUnusedExamRooms').mockReturnValue(of({ numberOfDeletedExamRooms: 4 } as ExamRoomDeletionSummary));
        const deleteButton = fixture.debugElement.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused');

        deleteButton.click();
        fixture.detectChanges();

        expect(adminApi.deleteAllOutdatedAndUnusedExamRooms).toHaveBeenCalledOnce();
        // Once from initial load, once from button click
        expect(adminApi.getExamRoomOverview).toHaveBeenCalledTimes(2);
    });

    it('should not reload overview if deletion fails', () => {
        fixture.detectChanges();
        vi.spyOn(adminApi, 'deleteAllOutdatedAndUnusedExamRooms').mockReturnValue(throwError(() => new Error()));
        const deleteButton = fixture.debugElement.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused');

        deleteButton.click();
        fixture.detectChanges();

        expect(adminApi.deleteAllOutdatedAndUnusedExamRooms).toHaveBeenCalledOnce();
        // Only once from initial load
        expect(adminApi.getExamRoomOverview).toHaveBeenCalledOnce();
    });

    it('should show deletion summary on successful deletion', () => {
        fixture.detectChanges();
        vi.spyOn(adminApi, 'deleteAllOutdatedAndUnusedExamRooms').mockReturnValue(of({ numberOfDeletedExamRooms: 4 } as ExamRoomDeletionSummary));
        const deleteButton = fixture.debugElement.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused');

        deleteButton.click();
        fixture.detectChanges();

        expect(component.hasDeletionInformation()).toBe(true);
        expect(component.deletionInformation()).toBeDefined();
        expect(component.deletionInformation()!.numberOfDeletedExamRooms).toBe(4);
    });

    describe('requests', () => {
        let httpMock: HttpTestingController;

        beforeEach(() => {
            vi.restoreAllMocks();
            httpMock = TestBed.inject(HttpTestingController);
        });

        afterEach(() => {
            httpMock.verify();
        });

        it('should load the admin overview without query parameters', () => {
            fixture.detectChanges();
            const req = httpMock.expectOne('/api/exam/rooms/admin/overview');
            expect(req.request.method).toBe('GET');
            req.flush({ newestUniqueExamRooms: [{ roomNumber: 'R1', name: 'Room', building: 'B', numberOfSeats: 5, layoutStrategies: [] }] });
            expect(component.numberOfAvailable()).toEqual({ examRooms: 1, examSeats: 5 });
        });

        it('should send the course and exam id for the overview of an exam', () => {
            TestBed.inject(ActivatedRoute).snapshot.queryParams = { courseId: '3', examId: '4' };
            fixture.destroy();
            fixture = TestBed.createComponent(ExamRoomsComponent);
            fixture.detectChanges();
            const req = httpMock.expectOne({ method: 'GET', url: '/api/exam/rooms/overview?courseId=3&examId=4' });
            req.flush({ newestUniqueExamRooms: [] });
        });

        it('should upload the file as multipart form data and reload the overview', () => {
            fixture.detectChanges();
            httpMock.expectOne('/api/exam/rooms/admin/overview').flush({ newestUniqueExamRooms: [] });
            const zipFile = new File(['content'], 'rooms.zip', { type: 'application/zip' });
            setInputFiles(fixture.debugElement.nativeElement.querySelector('#roomDataFileSelect'), [zipFile]);
            fixture.detectChanges();
            fixture.debugElement.nativeElement.querySelector('#roomDataUpload').click();

            const req = httpMock.expectOne('/api/exam/rooms/admin/upload');
            expect(req.request.method).toBe('POST');
            expect(req.request.body instanceof FormData).toBe(true);
            expect(req.request.body.get('file')).toBe(zipFile);
            req.flush({ uploadedFileName: 'rooms.zip', numberOfUploadedRooms: 1, numberOfUploadedSeats: 30, uploadedRoomNames: ['Room'] });

            expect(component.uploadInformation()!.numberOfUploadedRooms).toBe(1);
            httpMock.expectOne('/api/exam/rooms/admin/overview').flush({ newestUniqueExamRooms: [] });
        });

        it('should delete outdated rooms and reload the overview', () => {
            fixture.detectChanges();
            httpMock.expectOne('/api/exam/rooms/admin/overview').flush({ newestUniqueExamRooms: [] });
            fixture.debugElement.nativeElement.querySelector('#roomDataDeleteOutdatedAndUnused').click();

            const req = httpMock.expectOne('/api/exam/rooms/admin/outdated-and-unused');
            expect(req.request.method).toBe('DELETE');
            req.flush({ numberOfDeletedExamRooms: 15 });

            expect(component.deletionInformation()!.numberOfDeletedExamRooms).toBe(15);
            httpMock.expectOne('/api/exam/rooms/admin/overview').flush({ newestUniqueExamRooms: [] });
        });
    });
});
