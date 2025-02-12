import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountInformationComponent } from 'app/shared/user-settings/account-information/account-information.component';
import { AccountService } from 'app/core/auth/account.service';
import { UserSettingsService } from 'app/shared/user-settings/user-settings.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { of, throwError } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { ArtemisTestModule } from '../../test.module';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';

describe('AccountInformationComponent', () => {
    let fixture: ComponentFixture<AccountInformationComponent>;
    let comp: AccountInformationComponent;

    let accountServiceMock: { getAuthenticationState: jest.Mock };
    let userSettingsServiceMock: { updateProfilePicture: jest.Mock; removeProfilePicture: jest.Mock };
    let modalServiceMock: { open: jest.Mock };
    let alertServiceMock: { addAlert: jest.Mock };

    beforeEach(async () => {
        accountServiceMock = {
            getAuthenticationState: jest.fn(),
        };
        userSettingsServiceMock = {
            updateProfilePicture: jest.fn(),
            removeProfilePicture: jest.fn(),
        };
        modalServiceMock = {
            open: jest.fn(),
        };
        alertServiceMock = {
            addAlert: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: UserSettingsService, useValue: userSettingsServiceMock },
                { provide: NgbModal, useValue: modalServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(AccountInformationComponent);
        comp = fixture.componentInstance;
    });

    beforeEach(() => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 99 } as User));
        comp.ngOnInit();
    });

    it('should initialize and fetch current user', () => {
        expect(accountServiceMock.getAuthenticationState).toHaveBeenCalled();
        expect(comp.currentUser).toEqual({ id: 99 });
    });

    it('should open image cropper modal when setting user image', () => {
        const event = { currentTarget: { files: [new File([''], 'test.jpg', { type: 'image/jpeg' })] } } as unknown as Event;
        modalServiceMock.open.mockReturnValue({ componentInstance: {}, result: Promise.resolve('data:image/jpeg;base64,test') });

        comp.setUserImage(event);

        expect(modalServiceMock.open).toHaveBeenCalled();
    });

    it('should call removeProfilePicture when deleting user image', () => {
        userSettingsServiceMock.removeProfilePicture.mockReturnValue(of(new HttpResponse({ status: 200 })));

        comp.deleteUserImage();

        expect(userSettingsServiceMock.removeProfilePicture).toHaveBeenCalled();
    });

    it('should update user image on successful upload', () => {
        const userResponse = new HttpResponse<User>({
            body: {
                imageUrl: 'new-image-url',
                internal: false,
            },
        });
        userSettingsServiceMock.updateProfilePicture.mockReturnValue(of(userResponse));

        comp['subscribeToUpdateProfilePictureResponse'](userSettingsServiceMock.updateProfilePicture());

        expect(comp.currentUser!.imageUrl).toBe('new-image-url');
    });

    it('should show error alert when image upload fails', () => {
        const errorResponse = new HttpErrorResponse({ error: { title: 'Upload failed' }, status: 400 });
        userSettingsServiceMock.updateProfilePicture.mockReturnValue(throwError(() => errorResponse));

        comp['subscribeToUpdateProfilePictureResponse'](userSettingsServiceMock.updateProfilePicture());

        expect(alertServiceMock.addAlert).toHaveBeenCalledWith(expect.objectContaining({ message: 'Upload failed' }));
    });

    it('should show error alert when profile picture removal fails', () => {
        const errorResponse = new HttpErrorResponse({ error: { title: 'Removal failed' }, status: 400 });

        comp['onProfilePictureRemoveError'](errorResponse);

        expect(alertServiceMock.addAlert).toHaveBeenCalledWith(
            expect.objectContaining({
                type: expect.anything(),
                message: 'Removal failed',
                disableTranslation: true,
            }),
        );
    });

    it('should show error alert when profile picture upload fails', () => {
        const errorResponse = new HttpErrorResponse({ error: { title: 'Upload failed' }, status: 400 });

        comp['onProfilePictureUploadError'](errorResponse);

        expect(alertServiceMock.addAlert).toHaveBeenCalledWith(
            expect.objectContaining({
                type: expect.anything(),
                message: 'Upload failed',
                disableTranslation: true,
            }),
        );
    });
});
