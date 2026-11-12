import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of, throwError } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { User as ApiUser } from 'app/openapi/model/user';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountInformationComponent } from 'app/account/user/settings/profile/account-information/account-information.component';
import { UserSettingsService } from 'app/account/user/settings/directive/user-settings.service';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { TumAetUiConfirmationService, TumAetUiDialogComponent } from '@tumaet/ui-angular';
import { ImageCropperModalComponent } from 'app/course/manage/image-cropper-modal/image-cropper-modal.component';
import { AccountSettingsDialogComponent } from 'app/account/settings/account-settings-dialog.component';

describe('AccountInformationComponent', () => {
    let fixture: ComponentFixture<AccountInformationComponent>;
    let comp: AccountInformationComponent;
    let confirmationService: TumAetUiConfirmationService;

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
            .overrideComponent(AccountInformationComponent, {
                remove: { imports: [ImageCropperModalComponent, AccountSettingsDialogComponent] },
                add: { imports: [MockComponent(ImageCropperModalComponent), MockComponent(AccountSettingsDialogComponent)] },
            })
            .compileComponents();
        fixture = TestBed.createComponent(AccountInformationComponent);
        comp = fixture.componentInstance;
        confirmationService = fixture.debugElement.injector.get(TumAetUiConfirmationService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and have current user from signal', () => {
        expect(comp.currentUser()).toEqual({ id: 99, internal: true });
    });

    it('should render account properties as valid definition pairs', () => {
        accountServiceMock.userIdentity.set({ id: 99, name: 'Ada Lovelace', login: 'ada', email: 'ada@tum.de' } as User);
        fixture.detectChanges();

        const definitions: NodeListOf<HTMLDListElement> = fixture.nativeElement.querySelectorAll('dl');
        expect(definitions).toHaveLength(3);
        expect(Array.from(definitions, (definition) => definition.querySelector('dd')?.textContent?.trim())).toEqual(['Ada Lovelace', 'ada', 'ada@tum.de']);
        for (const definition of definitions) {
            expect(definition.querySelectorAll(':scope > dt')).toHaveLength(1);
            expect(definition.querySelectorAll(':scope > dd')).toHaveLength(1);
        }
    });

    it('should open the account settings dialog from the edit button', () => {
        fixture.detectChanges();

        const editButton: HTMLButtonElement = fixture.nativeElement.querySelector('[data-testid="account-settings-edit-button"] button');
        editButton.click();

        expect(comp.accountSettingsDialogVisible()).toBe(true);
    });

    it('should show the image cropper when setting user image', () => {
        const file = new File([''], 'test.jpg', { type: 'image/jpeg' });
        const event = { currentTarget: { files: [file], value: '' } } as unknown as Event;

        comp.setUserImage(event);

        expect(comp.imageToCrop()).toBe(file);
    });

    it('should configure a small cropper dialog with the profile picture title', () => {
        fixture.detectChanges();

        const dialog = fixture.debugElement.query(By.directive(TumAetUiDialogComponent)).componentInstance as TumAetUiDialogComponent;
        expect(dialog.header()).toBe('artemisApp.userSettings.accountInformationPage.profilePicture');
        expect(dialog.size()).toBe('small');
    });

    it('should only remove the profile picture after deletion is confirmed', () => {
        userSettingsServiceMock.removeProfilePicture.mockReturnValue(of(undefined));

        comp.confirmDeleteUserImage();

        expect(userSettingsServiceMock.removeProfilePicture).not.toHaveBeenCalled();

        const confirmationRequest = confirmationService.request('deleteProfilePictureConfirmation');
        expect(confirmationRequest).toBeDefined();
        confirmationRequest!.accept();

        expect(userSettingsServiceMock.removeProfilePicture).toHaveBeenCalled();
        expect(accountServiceMock.setImageUrl).toHaveBeenCalledWith(undefined);
    });

    it('should update user image on successful upload via setUserImage flow', () => {
        const userResponse: ApiUser = { login: 'testuser', imageUrl: 'new-image-url' };
        userSettingsServiceMock.updateProfilePicture.mockReturnValue(of(userResponse));
        comp.imageLoadFailed.set(true);

        const file = new File([''], 'test.jpg', { type: 'image/jpeg' });
        const event = { currentTarget: { files: [file], value: '' } } as unknown as Event;

        comp.setUserImage(event);

        comp.onImageCropped('data:image/jpeg;base64,dGVzdA==');
        expect(comp.imageToCrop()).toBeUndefined();

        expect(userSettingsServiceMock.updateProfilePicture).toHaveBeenCalledExactlyOnceWith(expect.any(Blob));
        expect(userSettingsServiceMock.updateProfilePicture.mock.calls[0][0].type).toBe('image/jpeg');
        expect(comp.imageLoadFailed()).toBe(false);
        expect(accountServiceMock.setImageUrl).toHaveBeenCalledWith('new-image-url');
    });

    it('keeps the image and load failure state when the upload returns no image URL', () => {
        const userResponse: ApiUser = { login: 'testuser' };
        userSettingsServiceMock.updateProfilePicture.mockReturnValue(of(userResponse));
        comp.imageLoadFailed.set(true);

        comp.onImageCropped('data:image/jpeg;base64,dGVzdA==');

        expect(accountServiceMock.setImageUrl).not.toHaveBeenCalled();
        expect(comp.imageLoadFailed()).toBe(true);
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
        expect(accountServiceMock.setImageUrl).not.toHaveBeenCalled();
    });

    it('should show error alert when profile picture removal fails', () => {
        const errorResponse = new HttpErrorResponse({ headers: new HttpHeaders({ 'x-artemisapp-alert': 'Removal failed' }), status: 400 });
        userSettingsServiceMock.removeProfilePicture.mockReturnValue(throwError(() => errorResponse));

        comp.confirmDeleteUserImage();
        confirmationService.request('deleteProfilePictureConfirmation')!.accept();

        expect(accountServiceMock.setImageUrl).not.toHaveBeenCalled();
        expect(alertServiceMock.addAlert).toHaveBeenCalledWith(
            expect.objectContaining({
                type: expect.anything(),
                message: 'Removal failed',
                disableTranslation: true,
            }),
        );
    });
});
