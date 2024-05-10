import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgressBarComponent } from 'src/main/webapp/app/shared/progress-bar/progress-bar.component';
import { By } from '@angular/platform-browser';
import { DebugElement } from '@angular/core';
import { faList } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockModule } from 'ng-mocks';

describe('ProgressBarComponent', () => {
    let component: ProgressBarComponent;
    let fixture: ComponentFixture<ProgressBarComponent>;
    let debugElement: DebugElement;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockModule(FontAwesomeModule)],
            declarations: [ProgressBarComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgressBarComponent);
                component = fixture.componentInstance;
                debugElement = fixture.debugElement;
                fixture.detectChanges();
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should calculate percentage correctly when currentValue and maxValue are positive', () => {
        component.currentValue = 50;
        component.maxValue = 100;
        expect(component.percentage).toBe(50);
    });

    it('should calculate percentage as 0 when currentValue is 0', () => {
        component.currentValue = 0;
        component.maxValue = 100;
        expect(component.percentage).toBe(0);
    });

    it('should calculate percentage as 100 when currentValue is greater than maxValue', () => {
        component.currentValue = 150;
        component.maxValue = 100;
        expect(component.percentage).toBe(100);
    });

    it('should calculate percentage as 0 when maxValue is 0', () => {
        component.currentValue = 50;
        component.maxValue = 0;
        expect(component.percentage).toBe(0);
    });

    it('should calculate percentage as 0 when currentValue and maxValue are 0', () => {
        component.currentValue = 0;
        component.maxValue = 0;
        expect(component.percentage).toBe(0);
    });

    it('should render progress bar correctly', () => {
        component.currentValue = 50;
        component.maxValue = 100;
        fixture.detectChanges();

        const progressBarElement = debugElement.query(By.css('.progress'));
        expect(progressBarElement).toBeTruthy();
        expect(progressBarElement.styles.width).toBe('50%');
    });

    it('should not render icon if not provided', () => {
        const testTitle = 'Test Title';
        component.title = testTitle;
        component.icon = null;
        fixture.detectChanges();

        const iconElement = debugElement.query(By.css('fa-icon'));
        expect(iconElement).toBeNull();
    });

    it('should render icon if provided', () => {
        const testTitle = 'Test Title';
        component.title = testTitle;
        component.icon = faList;
        fixture.detectChanges();

        const iconElement = debugElement.query(By.css('fa-icon'));
        expect(iconElement).toBeTruthy();
    });

    it('should render title correctly', () => {
        const testTitle = 'Test Title';
        component.title = testTitle;
        fixture.detectChanges();

        const titleElement = debugElement.query(By.css('.progress-bar-container'));
        expect(titleElement).toBeTruthy();
        expect(titleElement.nativeElement.textContent).toContain(testTitle);
    });
});
