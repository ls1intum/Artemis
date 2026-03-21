import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TypeAheadUserSearchFieldComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TypeAheadUserSearchFieldComponent;
    let fixture: ComponentFixture<TypeAheadUserSearchFieldComponent>;
    let userService: UserService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), TypeAheadUserSearchFieldComponent, MockPipe(ArtemisTranslatePipe), MockDirective(NgbTypeahead), MockDirective(TranslateDirective)],
            providers: [provideHttpClient(), { provide: TranslateService, useClass: MockTranslateService }],
        });
        fixture = TestBed.createComponent(TypeAheadUserSearchFieldComponent);
        component = fixture.componentInstance;
        userService = TestBed.inject(UserService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
    it('should call the user service on search', () => {
        const searchSpy = vi.spyOn(userService, 'search').mockReturnValue(
            of({
                body: [{ login: 'ge12abc', name: 'abc' }],
            } as unknown as HttpResponse<User[]>),
        );

        component.search(of('ge12abc')).subscribe();
        expect(searchSpy).toHaveBeenCalledExactlyOnceWith('ge12abc');
    });

    it('should not call the user service on search for string with less than three characters', () => {
        const searchSpy = vi.spyOn(userService, 'search');

        component.search(of('ge')).subscribe();
        expect(searchSpy).not.toHaveBeenCalled();
        expect(component.searchQueryTooShort()).toBe(true);
        expect(component.searching()).toBe(false);
    });

    it('should set searchNoResults to true if no users are found', () => {
        vi.spyOn(userService, 'search').mockReturnValue(
            of({
                body: [],
            } as unknown as HttpResponse<User[]>),
        );

        component.search(of('ge12abc')).subscribe();
        expect(component.searchNoResults()).toBe(true);
        expect(component.searching()).toBe(false);
    });

    it('should set searchFailed to true if the user service throws an error', () => {
        vi.spyOn(userService, 'search').mockReturnValue(
            throwError(
                () =>
                    new HttpErrorResponse({
                        status: 500,
                    }),
            ),
        );
        component.search(of('ge12abc')).subscribe();
        expect(component.searchFailed()).toBe(true);
        expect(component.searching()).toBe(false);
    });

    it('should update loginOrName on change with the correct value and update searchQueryTooShort', () => {
        component.loginOrName.set('ge12abc');
        component.onChange();
        expect(component.loginOrName()).toBe('ge12abc');
        expect(component.searchQueryTooShort()).toBe(false);

        // When loginOrName is set to a User object (from typeahead selection), onChange should extract the login
        component.loginOrName.set({ login: 'ge12abc' } as User);
        component.onChange();
        expect(component.loginOrName()).toBe('ge12abc');

        component.loginOrName.set('ge');
        component.onChange();
        expect(component.searchQueryTooShort()).toBe(true);
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
