import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisLearnerProfileComponent } from './iris-learner-profile.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('IrisLearnerProfileComponent', () => {
    let component: IrisLearnerProfileComponent;
    let fixture: ComponentFixture<IrisLearnerProfileComponent>;
    let accountService: AccountService;

    const mockUser = {
        id: 1,
        login: 'testuser',
        firstName: 'Test',
        lastName: 'User',
        email: 'test@example.com',
        memirisEnabled: true,
    };

    const mockUserWithMemirisDisabled = {
        id: 1,
        login: 'testuser',
        firstName: 'Test',
        lastName: 'User',
        email: 'test@example.com',
        memirisEnabled: false,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [IrisLearnerProfileComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisLearnerProfileComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        accountService.userIdentity.set(mockUser);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('ngOnInit', () => {
        it('should initialize memirisEnabled to true when user has memiris enabled', () => {
            accountService.userIdentity.set(mockUser);
            component.ngOnInit();
            expect(component.memirisEnabled).toBeTrue();
        });

        it('should initialize memirisEnabled to false when user has memiris disabled', () => {
            accountService.userIdentity.set(mockUserWithMemirisDisabled);
            component.ngOnInit();
            expect(component.memirisEnabled).toBeFalse();
        });

        it('should initialize memirisEnabled to false when user identity is null', () => {
            accountService.userIdentity.set(null);
            component.ngOnInit();
            expect(component.memirisEnabled).toBeFalse();
        });

        it('should initialize memirisEnabled to false when user identity is undefined', () => {
            accountService.userIdentity.set(undefined);
            component.ngOnInit();
            expect(component.memirisEnabled).toBeFalse();
        });

        it('should initialize memirisEnabled to false when user memirisEnabled property is undefined', () => {
            const userWithoutMemiris = {
                id: 1,
                login: 'testuser',
                firstName: 'Test',
                lastName: 'User',
                email: 'test@example.com',
            };

            accountService.userIdentity.set(userWithoutMemiris);
            component.ngOnInit();
            expect(component.memirisEnabled).toBeFalse();
        });

        it('should initialize memirisEnabled to false when user memirisEnabled property is null', () => {
            const userWithNullMemiris = {
                id: 1,
                login: 'testuser',
                firstName: 'Test',
                lastName: 'User',
                email: 'test@example.com',
                memirisEnabled: null as any,
            };

            accountService.userIdentity.set(userWithNullMemiris);
            component.ngOnInit();
            expect(component.memirisEnabled).toBeFalse();
        });
    });

    describe('onMemirisEnabledChange', () => {
        beforeEach(() => {
            component.ngOnInit();
        });

        it('should call accountService.setUserEnabledMemiris with true when memirisEnabled is true', () => {
            component.memirisEnabled = true;
            component.onMemirisEnabledChange();
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledWith(true);
        });

        it('should call accountService.setUserEnabledMemiris with false when memirisEnabled is false', () => {
            component.memirisEnabled = false;
            component.onMemirisEnabledChange();
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledWith(false);
        });

        it('should call accountService.setUserEnabledMemiris exactly once', () => {
            component.memirisEnabled = true;
            component.onMemirisEnabledChange();
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledOnce();
        });

        it('should handle multiple consecutive calls correctly', () => {
            component.memirisEnabled = true;
            component.onMemirisEnabledChange();
            component.memirisEnabled = false;
            component.onMemirisEnabledChange();
            component.memirisEnabled = true;
            component.onMemirisEnabledChange();
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledTimes(3);
            expect(accountService.setUserEnabledMemiris).toHaveBeenNthCalledWith(1, true);
            expect(accountService.setUserEnabledMemiris).toHaveBeenNthCalledWith(2, false);
            expect(accountService.setUserEnabledMemiris).toHaveBeenNthCalledWith(3, true);
        });
    });

    describe('Component integration', () => {
        it('should properly initialize and handle toggle changes', () => {
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUserWithMemirisDisabled,
                configurable: true,
            });
            component.ngOnInit();
            expect(component.memirisEnabled).toBeFalse();
            component.memirisEnabled = true;
            component.onMemirisEnabledChange();
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledWith(true);
        });

        it('should handle user identity changes during component lifecycle', () => {
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUser,
                configurable: true,
            });
            component.ngOnInit();
            expect(component.memirisEnabled).toBeTrue();
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUserWithMemirisDisabled,
                configurable: true,
            });
            component.ngOnInit();
            expect(component.memirisEnabled).toBeFalse();
        });
    });

    describe('Edge cases', () => {
        it('should handle accountService being null', () => {
            component.accountService = null as any;
            expect(() => component.ngOnInit()).toThrow();
        });

        it('should handle accountService being undefined', () => {
            component.accountService = undefined as any;
            expect(() => component.ngOnInit()).toThrow();
        });

        it('should handle setUserEnabledMemiris throwing an error', () => {
            jest.spyOn(accountService, 'setUserEnabledMemiris').mockImplementation(() => {
                throw new Error('Service error');
            });
            component.memirisEnabled = true;
            expect(() => component.onMemirisEnabledChange()).toThrow('Service error');
        });

        it('should handle truthy values correctly', () => {
            const userWithTruthyMemiris = {
                id: 1,
                login: 'testuser',
                firstName: 'Test',
                lastName: 'User',
                email: 'test@example.com',
                memirisEnabled: 1 as any,
            };

            accountService.userIdentity.set(userWithTruthyMemiris);
            component.ngOnInit();
            expect(component.memirisEnabled).toBe(1);
        });

        it('should handle falsy values correctly', () => {
            const userWithFalsyMemiris = {
                id: 1,
                login: 'testuser',
                firstName: 'Test',
                lastName: 'User',
                email: 'test@example.com',
                memirisEnabled: 0 as any,
            };

            accountService.userIdentity.set(userWithFalsyMemiris);
            component.ngOnInit();
            expect(component.memirisEnabled).toBe(0);
        });
    });

    describe('Component state management', () => {
        it('should maintain state consistency between ngOnInit and onMemirisEnabledChange', () => {
            accountService.userIdentity.set(mockUser);
            component.ngOnInit();
            const initialState = component.memirisEnabled;
            component.memirisEnabled = !initialState;
            component.onMemirisEnabledChange();
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledWith(!initialState);
        });

        it('should handle rapid state changes', () => {
            component.ngOnInit();
            for (let i = 0; i < 5; i++) {
                component.memirisEnabled = i % 2 === 0;
                component.onMemirisEnabledChange();
            }
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledTimes(5);
        });
    });

    describe('Template integration', () => {
        it('should render the component template correctly', () => {
            fixture.detectChanges();
            const compiled = fixture.nativeElement;
            expect(compiled.querySelector('h4')).toBeTruthy();
            expect(compiled.querySelector('input[type="checkbox"]')).toBeTruthy();
            expect(compiled.querySelector('label')).toBeTruthy();
        });

        it('should trigger onMemirisEnabledChange when checkbox is clicked', () => {
            jest.spyOn(component, 'onMemirisEnabledChange');
            fixture.detectChanges();
            const checkbox = fixture.nativeElement.querySelector('input[type="checkbox"]');
            checkbox.click();
            expect(component.onMemirisEnabledChange).toHaveBeenCalled();
        });

        it('should update component state when checkbox is toggled', () => {
            component.memirisEnabled = false;
            fixture.detectChanges();
            const checkbox = fixture.nativeElement.querySelector('input[type="checkbox"]');
            checkbox.click();
            fixture.detectChanges();
            expect(component.memirisEnabled).toBeTrue();
        });
    });
});
