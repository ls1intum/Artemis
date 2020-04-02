import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess-new/textblock-feedback-editor/textblock-feedback-editor.component';
import { Feedback } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';

describe('TextblockFeedbackEditorComponent', () => {
    let component: TextblockFeedbackEditorComponent;
    let fixture: ComponentFixture<TextblockFeedbackEditorComponent>;
    let compiled: any;

    const textBlock = { id: 'f6773c4b3c2d057fd3ac11f02df31c0a3e75f800' } as TextBlock;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisSharedModule],
            declarations: [TextblockFeedbackEditorComponent],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(TextblockFeedbackEditorComponent);
        component = fixture.componentInstance;
        component.textBlock = textBlock;
        component.feedback = Feedback.forText(textBlock);
        compiled = fixture.debugElement.nativeElement;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();

        const textarea = compiled.querySelector('textarea');
        expect(textarea).toBeTruthy();

        const input = compiled.querySelector('input');
        expect(input).toBeTruthy();
    });

    it('should show delete button for empty feedback only', () => {
        let button = compiled.querySelector('button');
        expect(button).toBeTruthy();

        component.feedback.credits = 1;
        fixture.detectChanges();
        button = compiled.querySelector('button');
        expect(button).toBeFalsy();

        component.feedback.detailText = 'Lorem Ipsum';
        fixture.detectChanges();
        button = compiled.querySelector('button');
        expect(button).toBeFalsy();

        component.feedback.credits = 0;
        fixture.detectChanges();
        button = compiled.querySelector('button');
        expect(button).toBeFalsy();

        component.feedback.detailText = '';
        fixture.detectChanges();
        button = compiled.querySelector('button');
        expect(button).toBeTruthy();
    });
});
