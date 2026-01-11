import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UserService } from 'app/core/user/shared/user.service';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TypeAheadUserSearchFieldComponent } from 'app/core/legal/data-export/type-ahead-search-field/type-ahead-user-search-field.component';

describe('TypeAheadUserSearchFieldComponent', () => {
    let component: TypeAheadUserSearchFieldComponent;
    let fixture: ComponentFixture<TypeAheadUserSearchFieldComponent>;
    let userService: UserService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), TypeAheadUserSearchFieldComponent, MockPipe(ArtemisTranslatePipe), MockDirective(NgbTypeahead), MockDirective(TranslateDirective)],
            providers: [provideHttpClient()],
        });
        fixture = TestBed.createComponent(TypeAheadUserSearchFieldComponent);
        component = fixture.componentInstance;
        userService = TestBed.inject(UserService);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
    it('should call the user service on search', () => {
        const searchSpy = jest.spyOn(userService, 'search').mockReturnValue(
            of({
                body: [{ login: 'ge12abc', name: 'abc' }],
            } as unknown as HttpResponse<User[]>),
        );

        component.search(of('ge12abc')).subscribe();
        expect(searchSpy).toHaveBeenCalledExactlyOnceWith('ge12abc');
    });

    it('should not call the user service on search for string with less than three characters', () => {
        const searchSpy = jest.spyOn(userService, 'search');

        component.search(of('ge')).subscribe();
        expect(searchSpy).not.toHaveBeenCalled();
        expect(component.searchQueryTooShort()).toBeTrue();
        expect(component.searching()).toBeFalse();
    });

    it('should set searchNoResults to true if no users are found', () => {
        jest.spyOn(userService, 'search').mockReturnValue(
            of({
                body: [],
            } as unknown as HttpResponse<User[]>),
        );

        component.search(of('ge12abc')).subscribe();
        expect(component.searchNoResults()).toBeTrue();
        expect(component.searching()).toBeFalse();
    });

    it('should set searchFailed to true if the user service throws an error', () => {
        jest.spyOn(userService, 'search').mockReturnValue(
            throwError(
                () =>
                    new HttpErrorResponse({
                        status: 500,
                    }),
            ),
        );
        component.search(of('ge12abc')).subscribe();
        expect(component.searchFailed()).toBeTrue();
        expect(component.searching()).toBeFalse();
    });

    it('should update loginOrName on change with the correct value and update searchQueryTooShort', () => {
        component.loginOrName.set('ge12abc');
        component.onChange();
        expect(component.loginOrName()).toBe('ge12abc');
        expect(component.searchQueryTooShort()).toBeFalse();

        // When loginOrName is set to an object (user), onChange should extract the login
        component.loginOrName.set({ login: 'ge12abc' } as unknown as string);
        component.onChange();
        expect(component.loginOrName()).toBe('ge12abc');

        component.loginOrName.set('ge');
        component.onChange();
        expect(component.searchQueryTooShort()).toBeTrue();
    });

    it('should format the result correctly', () => {
        const user = { login: 'ge12abc', name: 'abc' } as User;
        expect(component.resultFormatter(user)).toBe('abc (ge12abc)');
    });

    it('should format the input correctly', () => {
        const user = { login: 'ge12abc' } as User;
        expect(component.inputFormatter(user)).toBe('ge12abc');
        const loginString = 'ge12abc';
        expect(component.inputFormatter(loginString)).toBe('ge12abc');
    });
});
