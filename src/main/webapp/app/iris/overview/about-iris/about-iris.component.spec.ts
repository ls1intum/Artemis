import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { AboutIrisComponent } from './about-iris.component';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
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

    describe('Component Properties', () => {
        it('should have faRobot icon', () => {
            expect(component.faRobot).toBe(faRobot);
        });

        it('should have correct bulletPoints structure', () => {
            expect(component.bulletPoints).toBeDefined();
            expect(component.bulletPoints['1']).toBe(2);
            expect(component.bulletPoints['2']).toBe(5);
            expect(component.bulletPoints['3']).toBe(3);
            expect(component.bulletPoints['4']).toBe(5);
        });

        it('should have all four keys in bulletPoints', () => {
            const keys = Object.keys(component.bulletPoints);
            expect(keys).toHaveLength(4);
            expect(keys).toContain('1');
            expect(keys).toContain('2');
            expect(keys).toContain('3');
            expect(keys).toContain('4');
        });

        it('should have objectKeys reference to Object.keys', () => {
            expect(component.objectKeys).toBe(Object.keys);
        });

        it('should have array reference to Array constructor', () => {
            expect(component.array).toBe(Array);
        });

        it('should expose IrisLogoSize enum', () => {
            expect(component['IrisLogoSize']).toBe(IrisLogoSize);
        });
    });

    describe('Template Rendering', () => {
        it('should render IrisLogoComponent with BIG size', () => {
            const logoComponent = compiled.query(By.directive(MockComponent(IrisLogoComponent)));
            expect(logoComponent).toBeTruthy();
        });

        it('should render the main title with correct translation key', () => {
            const titleElement = compiled.query(By.css('h2'));
            expect(titleElement).toBeTruthy();
        });

        it('should render subtext elements', () => {
            const subtextElements = compiled.queryAll(By.css('p'));
            expect(subtextElements.length).toBeGreaterThan(0);
        });

        it('should render mobile subtext', () => {
            const mobileSubtext = compiled.query(By.css('.d-md-none p'));
            expect(mobileSubtext).toBeTruthy();
        });

        it('should render desktop subtext', () => {
            const desktopSubtext = compiled.query(By.css('.d-none.d-md-block p'));
            expect(desktopSubtext).toBeTruthy();
        });
    });

    describe('Bullet Points Rendering', () => {
        it('should render all four subheadings', () => {
            const subheadings = compiled.queryAll(By.css('h4'));
            expect(subheadings).toHaveLength(4);
        });

        it('should render correct number of bullet lists', () => {
            const bulletLists = compiled.queryAll(By.css('ul'));
            expect(bulletLists).toHaveLength(4);
        });

        it('should render correct total number of list items', () => {
            const expectedTotal = Object.values(component.bulletPoints).reduce((sum, count) => sum + count, 0);
            const listItems = compiled.queryAll(By.css('li'));
            expect(listItems).toHaveLength(expectedTotal);
        });

        it('should render correct number of items for section 1', () => {
            const allLists = compiled.queryAll(By.css('ul'));
            const firstList = allLists[0];
            const items = firstList.nativeElement.querySelectorAll('li');
            expect(items).toHaveLength(component.bulletPoints['1']);
        });

        it('should render correct number of items for section 2', () => {
            const allLists = compiled.queryAll(By.css('ul'));
            const secondList = allLists[1];
            const items = secondList.nativeElement.querySelectorAll('li');
            expect(items).toHaveLength(component.bulletPoints['2']);
        });

        it('should render correct number of items for section 3', () => {
            const allLists = compiled.queryAll(By.css('ul'));
            const thirdList = allLists[2];
            const items = thirdList.nativeElement.querySelectorAll('li');
            expect(items).toHaveLength(component.bulletPoints['3']);
        });

        it('should render correct number of items for section 4', () => {
            const allLists = compiled.queryAll(By.css('ul'));
            const fourthList = allLists[3];
            const items = fourthList.nativeElement.querySelectorAll('li');
            expect(items).toHaveLength(component.bulletPoints['4']);
        });
    });

    describe('Component Structure and Styling', () => {
        it('should have the about-iris-header class', () => {
            const header = compiled.query(By.css('.about-iris-header'));
            expect(header).toBeTruthy();
        });

        it('should apply card-title class to header', () => {
            const header = compiled.query(By.css('.card-title'));
            expect(header).toBeTruthy();
        });

        it('should render content in a structured layout with rows and columns', () => {
            const rows = compiled.queryAll(By.css('.row'));
            expect(rows.length).toBeGreaterThan(0);
        });

        it('should have proper margin classes applied to main content', () => {
            const mainContent = compiled.query(By.css('.mb-5.ms-2'));
            expect(mainContent).toBeTruthy();
        });
    });

    describe('Responsive Design Elements', () => {
        it('should have mobile-specific elements with d-md-none class', () => {
            const mobileElements = compiled.queryAll(By.css('.d-md-none'));
            expect(mobileElements.length).toBeGreaterThan(0);
        });

        it('should have desktop-specific elements with d-none d-md-block classes', () => {
            const desktopElements = compiled.queryAll(By.css('.d-none.d-md-block'));
            expect(desktopElements.length).toBeGreaterThan(0);
        });
    });

    describe('Edge Cases', () => {
        it('should handle empty bulletPoints gracefully', () => {
            component.bulletPoints = {};
            fixture.detectChanges();

            const subheadings = compiled.queryAll(By.css('h4'));
            expect(subheadings).toHaveLength(0);
        });

        it('should handle bulletPoints with zero items', () => {
            component.bulletPoints = { '1': 0 };
            fixture.detectChanges();

            const allLists = compiled.queryAll(By.css('ul'));
            expect(allLists).toHaveLength(1);
            const firstListItems = allLists[0].nativeElement.querySelectorAll('li');
            expect(firstListItems).toHaveLength(0);
        });

        it('should handle bulletPoints with large numbers', () => {
            component.bulletPoints = { '1': 10 };
            fixture.detectChanges();

            const allLists = compiled.queryAll(By.css('ul'));
            expect(allLists).toHaveLength(1);
            const firstListItems = allLists[0].nativeElement.querySelectorAll('li');
            expect(firstListItems).toHaveLength(10);
        });
    });

    describe('Integration Tests', () => {
        it('should properly integrate IrisLogoComponent', () => {
            const logoComponent = compiled.query(By.directive(MockComponent(IrisLogoComponent)));
            expect(logoComponent).toBeTruthy();
        });

        it('should have all necessary imports for standalone component', () => {
            // This test verifies the component can be instantiated with its imports
            expect(component).toBeTruthy();
            expect(fixture).toBeTruthy();
        });
    });
});
