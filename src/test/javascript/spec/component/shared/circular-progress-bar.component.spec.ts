import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CircularProgressBarComponent } from 'app/shared/circular-progress-bar/circular-progress-bar.component';
describe('CircularProgressBarComponent', () => {
    let circularProgressBarComponentFixture: ComponentFixture<CircularProgressBarComponent>;
    let circularProgressBarComponent: CircularProgressBarComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [CircularProgressBarComponent],
            providers: [],
            schemas: [],
        })
            .compileComponents()
            .then(() => {
                circularProgressBarComponentFixture = TestBed.createComponent(CircularProgressBarComponent);
                circularProgressBarComponent = circularProgressBarComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        circularProgressBarComponentFixture.detectChanges();
        expect(circularProgressBarComponent).not.toBeNull();
    });

    it('should be completely green when progress is 100%', () => {
        circularProgressBarComponent.progressInPercent = 100;
        circularProgressBarComponent.ngOnChanges();
        circularProgressBarComponentFixture.detectChanges();
        const leftSideCircle = circularProgressBarComponentFixture.debugElement.nativeElement.querySelector('.progress-left > .progress-bar-circle');
        const rightSideCircle = circularProgressBarComponentFixture.debugElement.nativeElement.querySelector('.progress-right > .progress-bar-circle');
        const leftBorderColor = leftSideCircle.style['border-color'];
        const rightBorderColor = rightSideCircle.style['border-color'];
        expect(leftBorderColor).toBe('#00ff00');
        expect(rightBorderColor).toBe('#00ff00');
    });

    it('should be completely red when progress is 0%', () => {
        circularProgressBarComponent.progressInPercent = 0;
        circularProgressBarComponent.ngOnChanges();
        circularProgressBarComponentFixture.detectChanges();
        const leftSideCircle = circularProgressBarComponentFixture.debugElement.nativeElement.querySelector('.progress-left > .progress-bar-circle');
        const rightSideCircle = circularProgressBarComponentFixture.debugElement.nativeElement.querySelector('.progress-right > .progress-bar-circle');
        const leftBorderColor = leftSideCircle.style['border-color'];
        const rightBorderColor = rightSideCircle.style['border-color'];
        expect(leftBorderColor).toBe('#ff0000');
        expect(rightBorderColor).toBe('#ff0000');
    });

    it('should be yellow when progress is 50%', () => {
        circularProgressBarComponent.progressInPercent = 50;
        circularProgressBarComponent.ngOnChanges();
        circularProgressBarComponentFixture.detectChanges();
        const leftSideCircle = circularProgressBarComponentFixture.debugElement.nativeElement.querySelector('.progress-left > .progress-bar-circle');
        const rightSideCircle = circularProgressBarComponentFixture.debugElement.nativeElement.querySelector('.progress-right > .progress-bar-circle');
        const leftBorderColor = leftSideCircle.style['border-color'];
        const rightBorderColor = rightSideCircle.style['border-color'];
        expect(leftBorderColor).toBe('#ffff00');
        expect(rightBorderColor).toBe('#ffff00');
    });
});
