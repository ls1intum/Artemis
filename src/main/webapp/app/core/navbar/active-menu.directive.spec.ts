import { Component, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { ActiveMenuDirective } from './active-menu.directive';

@Component({
    template: '<div [jhiActiveMenu]="language"></div>',
    standalone: true,
    imports: [ActiveMenuDirective],
})
class TestComponent {
    language = 'en';
}

describe('ActiveMenuDirective', () => {
    let component: TestComponent;
    let fixture: ComponentFixture<TestComponent>;
    let directiveElement: DebugElement;
    let translateService: TranslateService;
    let langChangeSubject: Subject<any>;

    beforeEach(async () => {
        langChangeSubject = new Subject();

        await TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), TestComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(TestComponent);
        component = fixture.componentInstance;
        directiveElement = fixture.debugElement.query(By.directive(ActiveMenuDirective));
        translateService = TestBed.inject(TranslateService);

        jest.spyOn(translateService, 'onLangChange', 'get').mockReturnValue(langChangeSubject.asObservable());
        jest.spyOn(translateService, 'getCurrentLang').mockReturnValue('en');
    });

    it('should add active class when language matches', () => {
        component.language = 'en';
        fixture.detectChanges();

        expect(directiveElement.nativeElement.classList.contains('active')).toBeTrue();
    });

    it('should not add active class when language does not match', () => {
        component.language = 'de';
        fixture.detectChanges();

        expect(directiveElement.nativeElement.classList.contains('active')).toBeFalse();
    });

    it('should update active class on language change', () => {
        component.language = 'de';
        fixture.detectChanges();

        expect(directiveElement.nativeElement.classList.contains('active')).toBeFalse();

        langChangeSubject.next({ lang: 'de' });

        expect(directiveElement.nativeElement.classList.contains('active')).toBeTrue();
    });
});
