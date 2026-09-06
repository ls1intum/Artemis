import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of, throwError } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountInformationComponent } from 'app/account/user/settings/account-information/account-information.component';
import { UserSettingsService } from 'app/account/user/settings/directive/user-settings.service';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { TumUiDialogComponent } from '@tumaet/ui-angular';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal/image-cropper-modal.component';

describe('AccountInformationComponent', () => {
    let fixture: ComponentFixture<AccountInformationComponent>;
    let comp: AccountInformationComponent;

    let accountServiceMock: { userIdentity: ReturnType<typeof signal<User | undefined>>; setImageUrl: ReturnType<typeof vi.fn> };
    let userSettingsServiceMock: { updateProfilePicture: ReturnType<typeof vi.fn>; removeProfilePicture: ReturnType<typeof vi.fn> };
    let alertServiceMock: { addAlert: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        accountServiceMock = {
            userIdentity: signal<User | undefined>({ id: 99, internal: true } as User),
            setImageUrl: vi.fn(),
        };
        userSettingsServiceMock = {
            updateProfilePicture: vi.fn(),
            removeProfilePicture: vi.fn(),
        };
        alertServiceMock = {
            addAlert: vi.fn(),
        };

        await TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: UserSettingsService, useValue: userSettingsServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                provideRouter([]),
            ],
        })
            .overrideComponent(AccountInformationComponent, { remove: { imports: [ImageCropperModalComponent] }, add: { imports: [MockComponent(ImageCropperModalComponent)] } })
            .compileComponents();
        fixture = TestBed.createComponent(AccountInformationComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and have current user from signal', () => {
        expect(comp.currentUser()).toEqual({ id: 99, internal: true });
    });

    it('should show the image cropper when setting user image', () => {
        const file = new File([''], 'test.jpg', { type: 'image/jpeg' });
        const event = { currentTarget: { files: [file], value: '' } } as unknown as Event;

        comp.setUserImage(event);

        expect(comp.imageToCrop()).toBe(file);
    });

    it('should configure a small cropper dialog with the profile picture title', () => {
        fixture.detectChanges();

        const dialog = fixture.debugElement.query(By.directive(TumUiDialogComponent)).componentInstance as TumUiDialogComponent;
        expect(dialog.header()).toBe('artemisApp.userSettings.accountInformationPage.profilePicture');
        expect(dialog.size()).toBe('small');
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

        comp.onImageCropped('data:image/jpeg;base64,dGVzdA==');
        expect(comp.imageToCrop()).toBeUndefined();

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
        comp.onImageCropped('data:image/jpeg;base64,dGVzdA==');

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
