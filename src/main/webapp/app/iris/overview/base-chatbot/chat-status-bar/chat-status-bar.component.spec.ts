import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ChatStatusBarComponent } from 'app/iris/overview/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisStageDTO, IrisStageStateDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { By } from '@angular/platform-browser';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

describe('ChatStatusBarComponent', () => {
    let component: ChatStatusBarComponent;
    let fixture: ComponentFixture<ChatStatusBarComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FontAwesomeModule, ChatStatusBarComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(ChatStatusBarComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should handle unfinished stages in ngOnChanges', () => {
        component.stages = [{ name: 'Test Stage', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: 'Test', internal: false }];
        component.ngOnChanges();
        expect(component.open).toBeTrue();
        expect(component.activeStage).toEqual(component.stages[0]);
        expect(component.displayedText).toBe('Test Stage');
    });

    it('should handle all stages finished in ngOnChanges', () => {
        component.stages = [{ name: 'Test Stage', state: IrisStageStateDTO.DONE, weight: 1, message: 'Test', internal: false }];
        component.ngOnChanges();
        expect(component.open).toBeFalse();
        expect(component.activeStage).toBeUndefined();
        expect(component.displayedText).toBeUndefined();
    });

    it('should return true for finished stages in isStageFinished', () => {
        const stage: IrisStageDTO = { name: 'Test Stage', state: IrisStageStateDTO.DONE, weight: 1, message: 'Test', internal: false };
        expect(component.isStageFinished(stage)).toBeTrue();
        stage.state = IrisStageStateDTO.SKIPPED;
        expect(component.isStageFinished(stage)).toBeTrue();
    });

    it('should return false for unfinished stages in isStageFinished', () => {
        const stage: IrisStageDTO = { name: 'Test Stage', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: 'Test', internal: false };
        expect(component.isStageFinished(stage)).toBeFalse();
    });

    it('should render progress bar when stages are present', () => {
        component.stages = [{ name: 'Test Stage', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: 'Test', internal: false }];
        fixture.changeDetectorRef.detectChanges();
        const progressBar = fixture.debugElement.query(By.css('.progress-bar'));
        expect(progressBar).toBeTruthy();
    });

    it('should not render progress bar when stages are not present', () => {
        component.stages = [];
        fixture.changeDetectorRef.detectChanges();
        const progressBarParts = fixture.debugElement.queryAll(By.css('.progress-bar .part'));
        expect(progressBarParts).toHaveLength(0);
    });

    it('should render stage name when stages are present', () => {
        component.stages = [{ name: 'Test Stage', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: 'Test', internal: false }];
        component.ngOnChanges();
        fixture.changeDetectorRef.detectChanges();
        const stageName = fixture.debugElement.query(By.css('.display')).nativeElement.textContent;
        expect(stageName).toContain('Test Stage');
    });

    it('should not render stage name when stages are not present', () => {
        component.stages = [];
        component.ngOnChanges();
        fixture.changeDetectorRef.detectChanges();
        const stageName = fixture.debugElement.query(By.css('.display')).nativeElement.textContent;
        expect(stageName).not.toContain('Test Stage');
    });
});
