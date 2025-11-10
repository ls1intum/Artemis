import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { AboutIrisComponent } from './about-iris.component';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { faRobot } from '@fortawesome/free-solid-svg-icons';

describe('AboutIrisComponent', () => {
    let component: AboutIrisComponent;
    let fixture: ComponentFixture<AboutIrisComponent>;
    let compiled: DebugElement;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockComponent(IrisLogoComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            declarations: [AboutIrisComponent],
        });
        fixture = TestBed.createComponent(AboutIrisComponent);
        component = fixture.componentInstance;
        compiled = fixture.debugElement;
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have correct component properties', () => {
        expect(component.faRobot).toBe(faRobot);
        expect(component.bulletPoints).toBeDefined();
        expect(component.bulletPoints['1']).toBe(2);
        expect(component.bulletPoints['2']).toBe(5);
        expect(component.bulletPoints['3']).toBe(3);
        expect(component.bulletPoints['4']).toBe(5);
        expect(component.objectKeys).toBe(Object.keys);
        expect(component.array).toBe(Array);
    });

    it('should render all required template elements', () => {
        const logoComponent = compiled.query(By.directive(MockComponent(IrisLogoComponent)));
        const titleElement = compiled.query(By.css('h2'));
        const subheadings = compiled.queryAll(By.css('h4'));
        const bulletLists = compiled.queryAll(By.css('ul'));

        expect(logoComponent).toBeTruthy();
        expect(titleElement).toBeTruthy();
        expect(subheadings).toHaveLength(4);
        expect(bulletLists).toHaveLength(4);
    });

    it('should render correct total number of list items', () => {
        const expectedTotal = Object.values(component.bulletPoints).reduce((sum, count) => sum + count, 0);
        const listItems = compiled.queryAll(By.css('li'));
        expect(listItems).toHaveLength(expectedTotal);
    });
});
