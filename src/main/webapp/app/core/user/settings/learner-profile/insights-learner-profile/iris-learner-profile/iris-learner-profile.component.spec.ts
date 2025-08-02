import { ComponentFixture, TestBed } from '@angular/core/testing';
import { IrisLearnerProfileComponent } from './iris-learner-profile.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

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
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(AccountService)],
        }).compileComponents();

        fixture = TestBed.createComponent(IrisLearnerProfileComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);

        // Set up default mock user identity
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
            // Arrange
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUser,
                configurable: true,
            });

            // Act
            component.ngOnInit();

            // Assert
            expect(component.memirisEnabled).toBeTrue();
        });

        it('should initialize memirisEnabled to false when user has memiris disabled', () => {
            // Arrange
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUserWithMemirisDisabled,
                configurable: true,
            });

            // Act
            component.ngOnInit();

            // Assert
            expect(component.memirisEnabled).toBeFalse();
        });

        it('should initialize memirisEnabled to false when user identity is null', () => {
            // Arrange
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => null,
                configurable: true,
            });

            // Act
            component.ngOnInit();

            // Assert
            expect(component.memirisEnabled).toBeFalse();
        });

        it('should initialize memirisEnabled to false when user identity is undefined', () => {
            // Arrange
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => undefined,
                configurable: true,
            });

            // Act
            component.ngOnInit();

            // Assert
            expect(component.memirisEnabled).toBeFalse();
        });

        it('should initialize memirisEnabled to false when user memirisEnabled property is undefined', () => {
            // Arrange
            const userWithoutMemiris = {
                id: 1,
                login: 'testuser',
                firstName: 'Test',
                lastName: 'User',
                email: 'test@example.com',
                // memirisEnabled is undefined
            };

            Object.defineProperty(accountService, 'userIdentity', {
                get: () => userWithoutMemiris,
                configurable: true,
            });

            // Act
            component.ngOnInit();

            // Assert
            expect(component.memirisEnabled).toBeFalse();
        });

        it('should initialize memirisEnabled to false when user memirisEnabled property is null', () => {
            // Arrange
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

            // Act
            component.ngOnInit();

            // Assert
            expect(component.memirisEnabled).toBeFalse();
        });
    });

    describe('onMemirisEnabledChange', () => {
        beforeEach(() => {
            component.ngOnInit();
        });

        it('should call accountService.setUserEnabledMemiris with true when memirisEnabled is true', () => {
            // Arrange
            component.memirisEnabled = true;

            // Act
            component.onMemirisEnabledChange();

            // Assert
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledWith(true);
        });

        it('should call accountService.setUserEnabledMemiris with false when memirisEnabled is false', () => {
            // Arrange
            component.memirisEnabled = false;

            // Act
            component.onMemirisEnabledChange();

            // Assert
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledWith(false);
        });

        it('should call accountService.setUserEnabledMemiris exactly once', () => {
            // Arrange
            component.memirisEnabled = true;

            // Act
            component.onMemirisEnabledChange();

            // Assert
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledOnce();
        });

        it('should handle multiple consecutive calls correctly', () => {
            // Arrange
            component.memirisEnabled = true;

            // Act
            component.onMemirisEnabledChange();
            component.memirisEnabled = false;
            component.onMemirisEnabledChange();
            component.memirisEnabled = true;
            component.onMemirisEnabledChange();

            // Assert
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledTimes(3);
            expect(accountService.setUserEnabledMemiris).toHaveBeenNthCalledWith(1, true);
            expect(accountService.setUserEnabledMemiris).toHaveBeenNthCalledWith(2, false);
            expect(accountService.setUserEnabledMemiris).toHaveBeenNthCalledWith(3, true);
        });
    });

    describe('Component integration', () => {
        it('should properly initialize and handle toggle changes', () => {
            // Arrange
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUserWithMemirisDisabled,
                configurable: true,
            });

            // Act - Initialize component
            component.ngOnInit();

            // Assert - Initial state
            expect(component.memirisEnabled).toBeFalse();

            // Act - Toggle to enabled
            component.memirisEnabled = true;
            component.onMemirisEnabledChange();

            // Assert - Service called with new value
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledWith(true);
        });

        it('should handle user identity changes during component lifecycle', () => {
            // Arrange - Start with user having memiris enabled
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUser,
                configurable: true,
            });

            // Act - Initialize component
            component.ngOnInit();

            // Assert - Initial state
            expect(component.memirisEnabled).toBeTrue();

            // Arrange - Change user identity to disabled
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUserWithMemirisDisabled,
                configurable: true,
            });

            // Act - Re-initialize (simulating user change)
            component.ngOnInit();

            // Assert - State updated
            expect(component.memirisEnabled).toBeFalse();
        });
    });

    describe('Edge cases', () => {
        it('should handle accountService being null', () => {
            // Arrange
            component.accountService = null as any;

            // Act & Assert - Should throw error when trying to access userIdentity
            expect(() => component.ngOnInit()).toThrow();
        });

        it('should handle accountService being undefined', () => {
            // Arrange
            component.accountService = undefined as any;

            // Act & Assert - Should throw error when trying to access userIdentity
            expect(() => component.ngOnInit()).toThrow();
        });

        it('should handle setUserEnabledMemiris throwing an error', () => {
            // Arrange
            jest.spyOn(accountService, 'setUserEnabledMemiris').mockImplementation(() => {
                throw new Error('Service error');
            });
            component.memirisEnabled = true;

            // Act & Assert - Should not propagate error
            expect(() => component.onMemirisEnabledChange()).toThrow('Service error');
        });

        it('should handle truthy values correctly', () => {
            // Arrange - Test with truthy values
            const userWithTruthyMemiris = {
                id: 1,
                login: 'testuser',
                firstName: 'Test',
                lastName: 'User',
                email: 'test@example.com',
                memirisEnabled: 1 as any, // truthy but not boolean
            };

            Object.defineProperty(accountService, 'userIdentity', {
                get: () => userWithTruthyMemiris,
                configurable: true,
            });

            // Act
            component.ngOnInit();

            // Assert - Should keep the original truthy value (1)
            expect(component.memirisEnabled).toBe(1);
        });

        it('should handle falsy values correctly', () => {
            // Arrange - Test with falsy values
            const userWithFalsyMemiris = {
                id: 1,
                login: 'testuser',
                firstName: 'Test',
                lastName: 'User',
                email: 'test@example.com',
                memirisEnabled: 0 as any, // falsy but not boolean
            };

            Object.defineProperty(accountService, 'userIdentity', {
                get: () => userWithFalsyMemiris,
                configurable: true,
            });

            // Act
            component.ngOnInit();

            // Assert - Should keep the original falsy value (0)
            expect(component.memirisEnabled).toBe(0);
        });
    });

    describe('Component state management', () => {
        it('should maintain state consistency between ngOnInit and onMemirisEnabledChange', () => {
            // Arrange
            Object.defineProperty(accountService, 'userIdentity', {
                get: () => mockUser,
                configurable: true,
            });

            // Act - Initialize
            component.ngOnInit();
            const initialState = component.memirisEnabled;

            // Act - Change state
            component.memirisEnabled = !initialState;
            component.onMemirisEnabledChange();

            // Assert
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledWith(!initialState);
        });

        it('should handle rapid state changes', () => {
            // Arrange
            component.ngOnInit();

            // Act - Rapid changes
            for (let i = 0; i < 5; i++) {
                component.memirisEnabled = i % 2 === 0;
                component.onMemirisEnabledChange();
            }

            // Assert
            expect(accountService.setUserEnabledMemiris).toHaveBeenCalledTimes(5);
        });
    });

    describe('Template integration', () => {
        it('should render the component template correctly', () => {
            // Act
            fixture.detectChanges();

            // Assert
            const compiled = fixture.nativeElement;
            expect(compiled.querySelector('h4')).toBeTruthy();
            expect(compiled.querySelector('input[type="checkbox"]')).toBeTruthy();
            expect(compiled.querySelector('label')).toBeTruthy();
        });

        it('should trigger onMemirisEnabledChange when checkbox is clicked', () => {
            // Arrange
            jest.spyOn(component, 'onMemirisEnabledChange');
            fixture.detectChanges();

            // Act
            const checkbox = fixture.nativeElement.querySelector('input[type="checkbox"]');
            checkbox.click();

            // Assert
            expect(component.onMemirisEnabledChange).toHaveBeenCalled();
        });

        it('should update component state when checkbox is toggled', () => {
            // Arrange
            component.memirisEnabled = false;
            fixture.detectChanges();

            // Act
            const checkbox = fixture.nativeElement.querySelector('input[type="checkbox"]');
            checkbox.click();
            fixture.detectChanges();

            // Assert
            expect(component.memirisEnabled).toBeTrue();
        });
    });
});
