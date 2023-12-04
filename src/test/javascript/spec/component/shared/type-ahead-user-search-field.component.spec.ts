import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { TypeAheadUserSearchFieldComponent } from 'app/shared/type-ahead-search-field/type-ahead-user-search-field.component';
import { UserService } from 'app/core/user/user.service';
import { of, throwError } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { FormsModule } from '@angular/forms';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('TypeAheadUserSearchFieldComponent', () => {
    let component: TypeAheadUserSearchFieldComponent;
    let fixture: ComponentFixture<TypeAheadUserSearchFieldComponent>;
    let userService: UserService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(FormsModule)],
            declarations: [TypeAheadUserSearchFieldComponent, MockPipe(ArtemisTranslatePipe), MockDirective(NgbTypeahead), MockDirective(TranslateDirective)],
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
        expect(component.searchQueryTooShort).toBeTrue();
        expect(component.searching).toBeFalse();
    });

    it('should set searchNoResults to true if no users are found', () => {
        jest.spyOn(userService, 'search').mockReturnValue(
            of({
                body: [],
            } as unknown as HttpResponse<User[]>),
        );

        component.search(of('ge12abc')).subscribe();
        expect(component.searchNoResults).toBeTrue();
        expect(component.searching).toBeFalse();
    });

    it('should set searchFailed to true if the user service throws an error', () => {
        jest.spyOn(userService, 'search').mockReturnValue(throwError({ status: 500 }));
        component.search(of('ge12abc')).subscribe();
        expect(component.searchFailed).toBeTrue();
        expect(component.searching).toBeFalse();
    });

    it('should emit the loginOrNameChange event on change with the correct value and update searchQueryTooShort', () => {
        const loginOrNameChangeSpy = jest.spyOn(component.loginOrNameChange, 'emit');
        component.loginOrName = 'ge12abc';
        component.onChange();
        expect(loginOrNameChangeSpy).toHaveBeenCalledExactlyOnceWith('ge12abc');
        expect(component.searchQueryTooShort).toBeFalse();
        jest.resetAllMocks();

        // @ts-ignore, otherwise we cannot set the loginOrName to an object
        component.loginOrName = { login: 'ge12abc' };
        component.onChange();
        expect(loginOrNameChangeSpy).toHaveBeenCalledExactlyOnceWith('ge12abc');

        component.loginOrName = 'ge';
        component.onChange();
        expect(component.searchQueryTooShort).toBeTrue();
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
