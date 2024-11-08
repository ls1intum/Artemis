import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ChatStatusBarComponent } from 'app/iris/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisStageDTO, IrisStageStateDTO } from 'app/entities/iris/iris-stage-dto.model';
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

    it('should handle unfinished stages in effect', fakeAsync(() => {
        fixture.componentRef.setInput('stages', [{ name: 'Test Stage', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: 'Test' }]);
        tick(500);

        expect(component.open()).toBeTrue();
        expect(component.activeStage()).toEqual(component.stages()!.at(0));
        expect(component.displayedText()).toBe('Test Stage');
    }));

    it('should handle all stages finished in effect', fakeAsync(() => {
        fixture.componentRef.setInput('stages', [{ name: 'Test Stage', state: IrisStageStateDTO.DONE, weight: 1, message: 'Test' }]);
        tick(5000);

        expect(component.open()).toBeFalse();
        expect(component.activeStage()).toBeUndefined();
        expect(component.displayedText()).toBeUndefined();
    }));

    it('should return true for finished stages in isStageFinished', () => {
        const stage: IrisStageDTO = { name: 'Test Stage', state: IrisStageStateDTO.DONE, weight: 1, message: 'Test' };
        expect(component.isStageFinished(stage)).toBeTrue();
        stage.state = IrisStageStateDTO.SKIPPED;
        expect(component.isStageFinished(stage)).toBeTrue();
    });

    it('should return false for unfinished stages in isStageFinished', () => {
        const stage: IrisStageDTO = { name: 'Test Stage', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: 'Test' };
        expect(component.isStageFinished(stage)).toBeFalse();
    });

    it('should render progress bar when stages are present', fakeAsync(() => {
        fixture.componentRef.setInput('stages', [{ name: 'Test Stage', state: IrisStageStateDTO.IN_PROGRESS, weight: 1, message: 'Test' }]);
        tick(500);

        const progressBar = fixture.debugElement.query(By.css('.progress-bar'));
        expect(progressBar).toBeTruthy();
    }));

    it('should not render progress bar when stages are not present', fakeAsync(() => {
        fixture.componentRef.setInput('stages', []);
        tick();

        const progressBarParts = fixture.debugElement.queryAll(By.css('.progress-bar .part'));
        expect(progressBarParts).toHaveLength(0);
    }));

    it('should render stage name when stages are present', fakeAsync(() => {
        fixture.componentRef.setInput('stages', [{ name: 'Test Stage', state: IrisStageStateDTO.NOT_STARTED, weight: 1, message: 'Test' }]);
        tick(5000);
        fixture.detectChanges();

        const stageName = fixture.debugElement.query(By.css('.display')).nativeElement.textContent;
        expect(stageName).toContain('Test Stage');
    }));

    it('should not render stage name when stages are not present', fakeAsync(() => {
        fixture.componentRef.setInput('stages', []);
        tick();
        const stageName = fixture.debugElement.query(By.css('.display')).nativeElement.textContent;
        expect(stageName).not.toContain('Test Stage');
    }));
});
