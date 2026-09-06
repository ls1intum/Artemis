import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { SshUserSettingsComponent } from 'app/account/user/settings/ssh-settings/ssh-user-settings.component';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { triggerDeleteDialogDelete } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { provideRouter } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';

describe('SshUserSettingsComponent', () => {
    let fixture: ComponentFixture<SshUserSettingsComponent>;
    let comp: SshUserSettingsComponent;
    const mockKey = 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIJxKWdvcbNTWl4vBjsijoY5HN5dpjxU40huy1PFpdd2o comment';
    const mockedUserSshKeys = [
        {
            id: 3,
            publicKey: mockKey,
            label: 'Key label',
            keyHash: 'Key hash',
        } as UserSshPublicKey,
        {
            id: 4,
            publicKey: mockKey,
            label: 'Key label',
            keyHash: 'Key hash 2',
        } as UserSshPublicKey,
    ];
    let alertServiceMock: {
        error: ReturnType<typeof vi.fn>;
        success: ReturnType<typeof vi.fn>;
    };
    let sshServiceMock: {
        deleteSshPublicKey: ReturnType<typeof vi.fn>;
        getSshPublicKeys: ReturnType<typeof vi.fn>;
        sshKeys: UserSshPublicKey[];
    };
    let translateService: TranslateService;

    beforeEach(async () => {
        sshServiceMock = {
            deleteSshPublicKey: vi.fn(),
            getSshPublicKeys: vi.fn(),
            sshKeys: [],
        };
        alertServiceMock = {
            error: vi.fn(),
            success: vi.fn(),
        };
        await TestBed.configureTestingModule({
            imports: [SshUserSettingsComponent],
            providers: [
                { provide: SshUserSettingsService, useValue: sshServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                DialogService,
            ],
        })
            .overrideComponent(SshUserSettingsComponent, {
                set: {
                    imports: [],
                    template: '',
                },
            })
            .compileComponents();
        fixture = TestBed.createComponent(SshUserSettingsComponent);
        comp = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.use('en');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize with User without keys', async () => {
        sshServiceMock.getSshPublicKeys.mockReturnValue(of([] as UserSshPublicKey[]));
        comp.ngOnInit();
        expect(sshServiceMock.getSshPublicKeys).toHaveBeenCalled();
        expect(comp.keyCount()).toBe(0);
    });

    it('should initialize with User with keys', async () => {
        sshServiceMock.getSshPublicKeys.mockReturnValue(of(mockedUserSshKeys as UserSshPublicKey[]));
        comp.ngOnInit();
        expect(sshServiceMock.getSshPublicKeys).toHaveBeenCalled();
        expect(comp.sshPublicKeys()).toHaveLength(2);
        expect(comp.sshPublicKeys()[0].publicKey).toEqual(mockKey);
        expect(comp.keyCount()).toBe(2);
    });

    it('should delete SSH key', async () => {
        sshServiceMock.getSshPublicKeys.mockReturnValue(of(mockedUserSshKeys as UserSshPublicKey[]));
        sshServiceMock.deleteSshPublicKey.mockReturnValue(of(new HttpResponse({ status: 200 })));
        comp.ngOnInit();
        comp.deleteSshKey(mockedUserSshKeys[0]);
        expect(sshServiceMock.deleteSshPublicKey).toHaveBeenCalled();
    });

    it('should fail to load SSH keys', () => {
        sshServiceMock.getSshPublicKeys.mockReturnValue(throwError(() => new HttpResponse({ body: new Blob() })));
        comp.ngOnInit();
        expect(comp.keyCount()).toBe(0);
        expect(alertServiceMock.error).toHaveBeenCalled();
    });

    /**
     * The row actions live in a menu whose content is destroyed the moment an item is activated, so the
     * confirmation the item opens has to outlive it.
     */
    describe('delete from the row menu', () => {
        let templateFixture: ComponentFixture<SshUserSettingsComponent>;
        let deleteDialogService: DeleteDialogService;

        beforeEach(async () => {
            TestBed.resetTestingModule();
            sshServiceMock = {
                deleteSshPublicKey: vi.fn().mockReturnValue(of(new HttpResponse({ status: 200 }))),
                getSshPublicKeys: vi.fn().mockReturnValue(of(mockedUserSshKeys as UserSshPublicKey[])),
                sshKeys: [],
            };
            alertServiceMock = { error: vi.fn(), success: vi.fn() };

            await TestBed.configureTestingModule({
                imports: [SshUserSettingsComponent, FontAwesomeTestingModule],
                providers: [
                    { provide: SshUserSettingsService, useValue: sshServiceMock },
                    { provide: AlertService, useValue: alertServiceMock },
                    { provide: TranslateService, useClass: MockTranslateService },
                    provideRouter([]),
                    DialogService,
                ],
            }).compileComponents();

            deleteDialogService = TestBed.inject(DeleteDialogService);
            vi.spyOn(deleteDialogService, 'openDeleteDialog').mockImplementation(() => {});

            templateFixture = TestBed.createComponent(SshUserSettingsComponent);
            templateFixture.detectChanges();
            await templateFixture.whenStable();
            templateFixture.detectChanges();
        });

        afterEach(() => templateFixture?.destroy());

        const openRowMenu = () => {
            const trigger = templateFixture.nativeElement.querySelector('tbody button[aria-haspopup="menu"]') as HTMLButtonElement;
            expect(trigger).not.toBeNull();
            trigger.click();
            templateFixture.detectChanges();
        };

        const menuItem = (label: string) => [...document.querySelectorAll('[role="menuitem"]')].find((item) => (item.textContent ?? '').includes(label)) as HTMLElement | undefined;

        it('deletes the key after the menu that opened the confirmation is gone', () => {
            openRowMenu();

            const deleteItem = menuItem('deleteSshKey');
            expect(deleteItem).toBeDefined();
            deleteItem!.click();
            templateFixture.detectChanges();

            // Activating the item closes the menu, which is exactly what used to take the delete handler with it.
            expect(document.querySelector('[role="menu"]')).toBeNull();
            expect(deleteDialogService.openDeleteDialog).toHaveBeenCalledOnce();

            const [dialogData] = vi.mocked(deleteDialogService.openDeleteDialog).mock.calls[0];
            triggerDeleteDialogDelete(dialogData.delete);

            expect(sshServiceMock.deleteSshPublicKey).toHaveBeenCalledWith(mockedUserSshKeys[0].id);
        });
    });
});
