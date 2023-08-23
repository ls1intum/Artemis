import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { TypeAheadUserSearchFieldComponent } from 'app/shared/type-ahead-search-field/type-ahead-user-search-field.component';
import { UserService } from 'app/core/user/user.service';
import { Observable, of } from 'rxjs';
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
    it('should call the user service on search', fakeAsync(() => {
        const searchSpy = jest.spyOn(userService, 'search').mockReturnValue(
            of({
                body: [{ login: 'ge12abc', name: 'abc' }],
            } as unknown as HttpResponse<User[]>),
        );

        component.search(of('ge12abc'));
        tick();
        expect(searchSpy).toHaveBeenCalledExactlyOnceWith('ge12abc');
    }));

    it('should not call the user service on search for string with less than three characters', () => {
        const searchSpy = jest.spyOn(userService, 'search');

        component.search(of('ge'));
        expect(searchSpy).not.toHaveBeenCalled();
        expect(component.searchQueryTooShort).toBeTrue();
    });

    it('should set searchNoResults to true if no users are found', () => {
        jest.spyOn(userService, 'search').mockReturnValue(
            of({
                body: [],
            } as unknown as HttpResponse<User[]>),
        );

        component.search(of('ge12abc'));
        expect(component.searchNoResults).toBeTrue();
    });

    it('should set searchFailed to true if the user service throws an error', () => {
        jest.spyOn(userService, 'search').mockReturnValue(new Error() as unknown as Observable<HttpResponse<User[]>>);
        component.search(of('ge12abc'));
        expect(component.searchFailed).toBeTrue();
    });
});
