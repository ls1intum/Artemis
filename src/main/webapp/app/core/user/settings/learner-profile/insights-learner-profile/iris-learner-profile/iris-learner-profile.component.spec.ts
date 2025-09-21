import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisLearnerProfileComponent } from './iris-learner-profile.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { IrisMemoriesHttpService } from 'app/iris/overview/services/iris-memories-http.service';
import { provideHttpClient } from '@angular/common/http';

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
                MockProvider(AccountService),
                provideHttpClient(),
                // Mock the IrisMemoriesHttpService to avoid HttpClient dependency in nested component
                MockProvider(IrisMemoriesHttpService, {
                    listUserMemories: jest.fn().mockReturnValue({ subscribe: () => {} } as any),
                    getUserMemory: jest.fn(),
                    deleteUserMemory: jest.fn(),
                }),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisLearnerProfileComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        Object.defineProperty(accountService, 'userIdentity', {
            get: () => mockUser,
            configurable: true,
        });

        jest.spyOn(accountService, 'setUserEnabledMemiris').mockImplementation(() => {});
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    describe('ngOnInit', () => {
        it('should initialize memirisEnabled to true when user has memiris enabled', () => {
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUser,
                configurable: true,
            });
            component.ngOnInit();
            expect(component.memirisEnabled).toBeTrue();
        });

        it('should initialize memirisEnabled to false when user has memiris disabled', () => {
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUserWithMemirisDisabled,
                configurable: true,
            });
            component.ngOnInit();
            expect(component.memirisEnabled).toBeFalse();
        });

        it('should initialize memirisEnabled to false when user identity is null', () => {
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => null,
                configurable: true,
            });
            component.ngOnInit();
            expect(component.memirisEnabled).toBeFalse();
        });

        it('should initialize memirisEnabled to false when user identity is undefined', () => {
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => undefined,
                configurable: true,
            });
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

            Object.defineProperty(accountService, 'userIdentity', {
                get: () => userWithoutMemiris,
                configurable: true,
            });
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

            Object.defineProperty(accountService, 'userIdentity', {
                get: () => userWithNullMemiris,
                configurable: true,
            });
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

            Object.defineProperty(accountService, 'userIdentity', {
                get: () => userWithTruthyMemiris,
                configurable: true,
            });
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

            Object.defineProperty(accountService, 'userIdentity', {
                get: () => userWithFalsyMemiris,
                configurable: true,
            });
            component.ngOnInit();
            expect(component.memirisEnabled).toBe(0);
        });
    });

    describe('Component state management', () => {
        it('should maintain state consistency between ngOnInit and onMemirisEnabledChange', () => {
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUser,
                configurable: true,
            });
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
