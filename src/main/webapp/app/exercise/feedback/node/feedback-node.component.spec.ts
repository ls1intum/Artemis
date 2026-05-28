import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FeedbackNodeComponent } from 'app/exercise/feedback/node/feedback-node.component';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { FeedbackGroup } from 'app/exercise/feedback/group/feedback-group';

describe('FeedbackNodeComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<FeedbackNodeComponent>;
    let component: FeedbackNodeComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FeedbackNodeComponent],
        }).compileComponents();

        fixture = TestBed.createComponent(FeedbackNodeComponent);
        component = fixture.componentInstance;
    });

    it('should set specific node type correctly for feedback item', () => {
        fixture.componentRef.setInput('feedbackItemNode', new FeedbackItem());
        fixture.detectChanges();

        expect(component.feedbackItem).toBeDefined();
    });

    it('should set specific node type correctly for feedback group', () => {
        fixture.componentRef.setInput('feedbackItemNode', { members: [] } as unknown as FeedbackGroup);
        fixture.detectChanges();

        expect(component.feedbackItemGroup).toBeDefined();
    });
});
