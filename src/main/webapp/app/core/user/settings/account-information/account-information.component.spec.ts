import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { Subject, of, throwError } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountInformationComponent } from 'app/core/user/settings/account-information/account-information.component';
import { UserSettingsService } from 'app/core/user/settings/directive/user-settings.service';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';

describe('AccountInformationComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<AccountInformationComponent>;
    let comp: AccountInformationComponent;

    let accountServiceMock: { userIdentity: ReturnType<typeof signal<User | undefined>>; setImageUrl: ReturnType<typeof vi.fn> };
    let userSettingsServiceMock: { updateProfilePicture: ReturnType<typeof vi.fn>; removeProfilePicture: ReturnType<typeof vi.fn> };
    let dialogServiceMock: { open: ReturnType<typeof vi.fn> };
    let alertServiceMock: { addAlert: ReturnType<typeof vi.fn> };
    let dialogCloseSubject: Subject<string | undefined>;

    beforeEach(async () => {
        dialogCloseSubject = new Subject<string | undefined>();
        accountServiceMock = {
            userIdentity: signal<User | undefined>({ id: 99, internal: true } as User),
            setImageUrl: vi.fn(),
        };
        userSettingsServiceMock = {
            updateProfilePicture: vi.fn(),
            removeProfilePicture: vi.fn(),
        };
        dialogServiceMock = {
            open: vi.fn().mockReturnValue({
                onClose: dialogCloseSubject.asObservable(),
            } as Partial<DynamicDialogRef>),
        };
        alertServiceMock = {
            addAlert: vi.fn(),
        };

        await TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: UserSettingsService, useValue: userSettingsServiceMock },
                { provide: DialogService, useValue: dialogServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                provideRouter([]),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(AccountInformationComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and have current user from signal', () => {
        expect(comp.currentUser()).toEqual({ id: 99, internal: true });
    });

    it('should open image cropper modal when setting user image', () => {
        const event = { currentTarget: { files: [new File([''], 'test.jpg', { type: 'image/jpeg' })], value: '' } } as unknown as Event;

        comp.setUserImage(event);

        expect(dialogServiceMock.open).toHaveBeenCalled();
    });

    it('should call removeProfilePicture and setImageUrl when deleting user image', () => {
        userSettingsServiceMock.removeProfilePicture.mockReturnValue(of(new HttpResponse({ status: 200 })));

        comp.deleteUserImage();

        expect(userSettingsServiceMock.removeProfilePicture).toHaveBeenCalled();
        expect(accountServiceMock.setImageUrl).toHaveBeenCalledWith(undefined);
    });

    it('should update user image on successful upload via setUserImage flow', () => {
        const userResponse = new HttpResponse<User>({
            body: {
                imageUrl: 'new-image-url',
                internal: false,
            },
        });
        userSettingsServiceMock.updateProfilePicture.mockReturnValue(of(userResponse));

        const file = new File([''], 'test.jpg', { type: 'image/jpeg' });
        const event = { currentTarget: { files: [file], value: '' } } as unknown as Event;

        comp.setUserImage(event);

        // Emit the dialog result via the subject
        dialogCloseSubject.next('data:image/jpeg;base64,dGVzdA==');

        expect(userSettingsServiceMock.updateProfilePicture).toHaveBeenCalled();
        expect(accountServiceMock.setImageUrl).toHaveBeenCalledWith('new-image-url');
    });

    it('should show error alert when image upload fails', () => {
        const errorResponse = new HttpErrorResponse({ error: { title: 'Upload failed' }, status: 400 });
        userSettingsServiceMock.updateProfilePicture.mockReturnValue(throwError(() => errorResponse));

        const file = new File([''], 'test.jpg', { type: 'image/jpeg' });
        const event = { currentTarget: { files: [file], value: '' } } as unknown as Event;

        comp.setUserImage(event);

        // Emit the dialog result via the subject
        dialogCloseSubject.next('data:image/jpeg;base64,dGVzdA==');

        expect(alertServiceMock.addAlert).toHaveBeenCalledWith(expect.objectContaining({ message: 'Upload failed' }));
    });

    it('should show error alert when profile picture removal fails', () => {
        const errorResponse = new HttpErrorResponse({ error: { title: 'Removal failed' }, status: 400 });
        userSettingsServiceMock.removeProfilePicture.mockReturnValue(throwError(() => errorResponse));

        comp.deleteUserImage();

        expect(alertServiceMock.addAlert).toHaveBeenCalledWith(
            expect.objectContaining({
                type: expect.anything(),
                message: 'Removal failed',
                disableTranslation: true,
            }),
        );
    });
});
