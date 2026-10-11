import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { By } from '@angular/platform-browser';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/foundation/service/alert.service';
import { SshUserSettingsKeyDetailsComponent } from 'app/account/user/settings/ssh-settings/details/ssh-user-settings-key-details.component';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';

describe('SshUserSettingsComponent', () => {
    let fixture: ComponentFixture<SshUserSettingsKeyDetailsComponent>;
    let comp: SshUserSettingsKeyDetailsComponent;
    const mockKey = 'mock-key';
    const invalidKeyFormat = 'invalidKeyFormat';
    const keyAlreadyExists = 'keyAlreadyExists';
    const mockedUserSshKeys = {
        id: 3,
        publicKey: mockKey,
        label: 'Key label',
        keyHash: 'Key hash',
    } as UserSshPublicKey;
    let router: Router;
    let sshServiceMock: {
        getSshPublicKey: ReturnType<typeof vi.fn>;
        addSshPublicKey: ReturnType<typeof vi.fn>;
        addNewSshPublicKey: ReturnType<typeof vi.fn>;
    };
    let alertServiceMock: {
        error: ReturnType<typeof vi.fn>;
        success: ReturnType<typeof vi.fn>;
    };
    let translateService: TranslateService;
    let activatedRoute: MockActivatedRoute;

    beforeEach(async () => {
        sshServiceMock = {
            getSshPublicKey: vi.fn(),
            addSshPublicKey: vi.fn(),
            addNewSshPublicKey: vi.fn(),
        };
        alertServiceMock = {
            error: vi.fn(),
            success: vi.fn(),
        };
        const routerMock = { navigate: vi.fn() };
        await TestBed.configureTestingModule({
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({}),
                },
                { provide: SshUserSettingsService, useValue: sshServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useValue: routerMock },
                { provide: AlertService, useValue: alertServiceMock },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(SshUserSettingsKeyDetailsComponent);
        comp = fixture.componentInstance;
        translateService = TestBed.inject(TranslateService);
        translateService.use('en');

        router = TestBed.inject(Router);
        activatedRoute = TestBed.inject(ActivatedRoute) as unknown as MockActivatedRoute;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize view for adding new keys and save new key', () => {
        sshServiceMock.addNewSshPublicKey.mockReturnValue(of({}));
        comp.ngOnInit();
        expect(sshServiceMock.getSshPublicKey).not.toHaveBeenCalled();
        expect(comp.isLoading()).toBe(false);
        comp.displayedSshKey = mockKey;
        comp.displayedExpiryDate.set(dayjs().add(1, 'day'));
        comp.displayedKeyLabel = 'label';
        comp.validateExpiryDate();
        expect(comp.isExpiryDateValid()).toBe(true);
        comp.saveSshKey();
        expect(alertServiceMock.success).toHaveBeenCalled();
        expect(router.navigate).toHaveBeenCalledWith(['/user-settings/ssh']);
    });

    it('should initialize view for adding new keys and fail saving key', () => {
        const httpError1 = new HttpErrorResponse({
            error: { errorKey: invalidKeyFormat },
            status: 400,
        });
        const httpError2 = new HttpErrorResponse({
            error: { errorKey: keyAlreadyExists },
            status: 400,
        });
        sshServiceMock.addNewSshPublicKey.mockReturnValue(throwError(() => httpError1));
        comp.ngOnInit();
        expect(sshServiceMock.getSshPublicKey).not.toHaveBeenCalled();
        expect(comp.isLoading()).toBe(false);
        comp.displayedSshKey = mockKey;
        comp.displayedExpiryDate.set(dayjs().add(1, 'day'));
        comp.displayedKeyLabel = 'label';
        comp.validateExpiryDate();
        expect(comp.isExpiryDateValid()).toBe(true);
        comp.saveSshKey();
        sshServiceMock.addNewSshPublicKey.mockReturnValue(throwError(() => httpError2));
        comp.saveSshKey();
        expect(alertServiceMock.error).toHaveBeenCalled();
        expect(router.navigate).not.toHaveBeenCalled();
    });

    describe('expiry date', () => {
        beforeEach(() => {
            comp.ngOnInit();
        });

        it('should accept tomorrow and later dates', () => {
            comp.onExpiryDateChange(dayjs().add(1, 'day').startOf('day'));
            expect(comp.isExpiryDateValid()).toBe(true);

            comp.onExpiryDateChange(dayjs().add(30, 'day'));
            expect(comp.isExpiryDateValid()).toBe(true);
            expect(comp.displayedExpiryDate()!.isSame(dayjs().add(30, 'day'), 'day')).toBe(true);
        });

        it('should reject today and earlier dates, because a key that expires today is of no use', () => {
            comp.onExpiryDateChange(dayjs());
            expect(comp.isExpiryDateValid()).toBe(false);

            comp.onExpiryDateChange(dayjs().endOf('day'));
            expect(comp.isExpiryDateValid()).toBe(false);

            comp.onExpiryDateChange(dayjs().subtract(1, 'day'));
            expect(comp.isExpiryDateValid()).toBe(false);
        });

        it('should keep the date at the end of the day, since the key stays valid through the day it expires on', () => {
            comp.onExpiryDateChange(dayjs('2030-05-17T00:00:00'));

            expect(comp.displayedExpiryDate()!.format('YYYY-MM-DD HH:mm:ss')).toBe('2030-05-17 23:59:59');
        });

        it('should reject an empty date', () => {
            comp.onExpiryDateChange(dayjs().add(1, 'day'));
            comp.onExpiryDateChange(undefined);

            expect(comp.isExpiryDateValid()).toBe(false);
            expect(comp.displayedExpiryDate()).toBeUndefined();
        });

        it('should reject a date while the typed text is not a date', () => {
            comp.onExpiryDateChange(dayjs().add(1, 'day'));
            comp.onExpiryTextValidityChange(false);
            expect(comp.isExpiryDateValid()).toBe(false);

            comp.onExpiryTextValidityChange(true);
            expect(comp.isExpiryDateValid()).toBe(true);
        });

        it.each([30, 90, 180])('should set the expiry to the end of the day in %s days with the quick choice', (days) => {
            comp.selectExpiryPreset(days);

            const expected = dayjs().add(days, 'day');
            expect(comp.displayedExpiryDate()!.isSame(expected, 'day')).toBe(true);
            expect(comp.displayedExpiryDate()!.format('HH:mm:ss')).toBe('23:59:59');
            expect(comp.isExpiryDateValid()).toBe(true);
        });

        it('should offer the quick choices above the picker, mark the active one and fill the picker', () => {
            comp.selectedOption = 'useExpiration';
            fixture.detectChanges();

            const presetButton = (days: number) => fixture.debugElement.query(By.css(`[data-testid="ssh-key-expiry-preset-${days}"]`));
            const pickerDebugElement = fixture.debugElement.query(By.directive(TumAetUiDatePickerComponent));
            const picker = pickerDebugElement.componentInstance as TumAetUiDatePickerComponent;
            expect([30, 90, 180].map((days) => presetButton(days) !== null)).toEqual([true, true, true]);
            expect(presetButton(90).nativeElement.compareDocumentPosition(pickerDebugElement.nativeElement) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
            expect(presetButton(30).nativeElement.getAttribute('aria-pressed')).not.toBe('true');

            presetButton(90).query(By.css('button')).nativeElement.click();
            fixture.detectChanges();

            expect(picker.value()!.isSame(dayjs().add(90, 'day'), 'day')).toBe(true);
            expect(presetButton(90).query(By.css('button')).nativeElement.getAttribute('aria-pressed')).toBe('true');
            expect(presetButton(30).query(By.css('button')).nativeElement.getAttribute('aria-pressed')).not.toBe('true');
        });

        it('should grey out the days up to today in the calendar', () => {
            comp.selectedOption = 'useExpiration';
            fixture.detectChanges();

            const picker = fixture.debugElement.query(By.directive(TumAetUiDatePickerComponent)).componentInstance as TumAetUiDatePickerComponent;
            expect(picker.min()!.isSame(dayjs().add(1, 'day'), 'day')).toBe(true);
        });

        it('should explain a date that is too early differently from a missing or unreadable one', () => {
            comp.selectedOption = 'useExpiration';
            fixture.detectChanges();
            const errorText = () => fixture.nativeElement.querySelector('[data-testid="ssh-key-expiry-date-error"]')?.textContent ?? '';

            expect(errorText()).toContain('entity.dateMissingOrNotValid');

            comp.onExpiryDateChange(dayjs());
            fixture.detectChanges();
            expect(errorText()).toContain('artemisApp.userSettings.sshSettingsPage.expiry.mustBeInFuture');

            comp.onExpiryDateChange(dayjs().add(10, 'day'));
            fixture.detectChanges();
            expect(errorText()).toBe('');
        });

        it('should render the picker with the required state and disable saving until the date is valid', () => {
            comp.displayedSshKey = mockKey;
            comp.selectedOption = 'useExpiration';
            fixture.detectChanges();

            const picker = fixture.debugElement.query(By.directive(TumAetUiDatePickerComponent)).componentInstance as TumAetUiDatePickerComponent;
            const saveButton = () => fixture.debugElement.query(By.css('[data-testid="saveSshKeyButton"]')).componentInstance;
            expect(picker.dateOnly()).toBe(true);
            expect(picker.ariaRequired()).toBe(true);
            expect(picker.invalid()).toBe(true);
            expect(fixture.nativeElement.querySelector('[data-testid="ssh-key-expiry-date-error"]')).not.toBeNull();
            expect(saveButton().disabled()).toBe(true);

            picker.value.set(dayjs().add(1, 'day'));
            fixture.detectChanges();

            expect(picker.invalid()).toBe(false);
            expect(fixture.nativeElement.querySelector('[data-testid="ssh-key-expiry-date-error"]')).toBeNull();
            expect(saveButton().disabled()).toBe(false);

            picker.value.set(dayjs().subtract(2, 'day'));
            fixture.detectChanges();

            expect(picker.invalid()).toBe(true);
            expect(saveButton().disabled()).toBe(true);
        });
    });

    it('should initialize key details view with key loaded', async () => {
        sshServiceMock.getSshPublicKey.mockReturnValue(of(mockedUserSshKeys));
        activatedRoute.setParameters({ keyId: 1 });
        comp.ngOnInit();
        expect(sshServiceMock.getSshPublicKey).toHaveBeenCalled();
        expect(comp.isLoading()).toBe(false);
        expect(comp.displayedSshKey).toEqual(mockKey);
        comp.goBack();
        expect(router.navigate).toHaveBeenCalledWith(['/user-settings/ssh']);
    });

    it('should detect Windows', () => {
        vi.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Windows NT 10.0; Win64; x64)');
        comp.ngOnInit();
        expect(comp.copyInstructions()).toBe('cat ~/.ssh/id_ed25519.pub | clip');
    });

    it('should detect MacOS', () => {
        vi.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)');
        comp.ngOnInit();
        expect(comp.copyInstructions()).toBe('pbcopy < ~/.ssh/id_ed25519.pub');
    });

    it('should detect Linux', () => {
        vi.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (X11; Linux x86_64)');
        comp.ngOnInit();
        expect(comp.copyInstructions()).toBe('xclip -selection clipboard < ~/.ssh/id_ed25519.pub');
    });

    it('should detect Android', () => {
        vi.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Linux; Android 10; Pixel 3)');
        comp.ngOnInit();
        expect(comp.copyInstructions()).toBe('termux-clipboard-set < ~/.ssh/id_ed25519.pub');
    });

    it('should detect iOS', () => {
        vi.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (iPhone; CPU iPhone OS 13_5)');
        comp.ngOnInit();
        expect(comp.copyInstructions()).toBe('Ctrl + C');
    });

    it('should return Unknown for unrecognized OS', () => {
        vi.spyOn(window.navigator, 'userAgent', 'get').mockReturnValue('Mozilla/5.0 (Unknown OS)');
        comp.ngOnInit();
        expect(comp.copyInstructions()).toBe('Ctrl + C');
    });
});
