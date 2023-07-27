import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { By } from '@angular/platform-browser';
import { LearningPathProgressNavComponent } from 'app/course/learning-paths/learning-path-management/learning-path-progress-nav.component';

describe('LearningPathProgressNavComponent', () => {
    let fixture: ComponentFixture<LearningPathProgressNavComponent>;
    let comp: LearningPathProgressNavComponent;
    let onRefreshStub: jest.SpyInstance;
    let onCenterViewStub: jest.SpyInstance;
    let onCloseStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [LearningPathProgressNavComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(LearningPathProgressNavComponent);
                comp = fixture.componentInstance;
                onRefreshStub = jest.spyOn(comp.onRefresh, 'emit');
                onCenterViewStub = jest.spyOn(comp.onCenterView, 'emit');
                onCloseStub = jest.spyOn(comp.onClose, 'emit');
                fixture.detectChanges();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should emit refresh on click', () => {
        const button = fixture.debugElement.query(By.css('#refresh-button'));
        expect(button).not.toBeNull();

        button.nativeElement.click();
        expect(onRefreshStub).toHaveBeenCalledOnce();
    });

    it('should emit center view on click', () => {
        const button = fixture.debugElement.query(By.css('#center-button'));
        expect(button).not.toBeNull();

        button.nativeElement.click();
        expect(onCenterViewStub).toHaveBeenCalledOnce();
    });

    it('should emit close on click', () => {
        const button = fixture.debugElement.query(By.css('#close-button'));
        expect(button).not.toBeNull();

        button.nativeElement.click();
        expect(onCloseStub).toHaveBeenCalledOnce();
    });
});
