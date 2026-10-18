import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { FeatureBonusAwardsComponent } from 'app/assessment/manage/feature-bonus/feature-bonus-awards.component';
import { FeatureBonusService } from 'app/assessment/shared/services/feature-bonus.service';
import { FeatureBonusAward } from 'app/assessment/shared/entities/feature-bonus.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

describe('FeatureBonusAwardsComponent', () => {
    let fixture: ComponentFixture<FeatureBonusAwardsComponent>;
    let component: FeatureBonusAwardsComponent;
    let featureBonusService: FeatureBonusService;

    const enemies: FeatureBonusAward = { id: 1, title: 'Intelligent enemies', justification: 'Simple algorithm', points: 1, createdBy: 'tutor1' };
    const music: FeatureBonusAward = { id: 2, title: 'Music', points: 0.5 };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [MockProvider(TranslateService), MockProvider(FeatureBonusService)],
        })
            .overrideComponent(FeatureBonusAwardsComponent, {
                remove: { imports: [TranslateDirective, ArtemisTranslatePipe] },
                add: { imports: [MockDirective(TranslateDirective), MockPipe(ArtemisTranslatePipe)] },
            })
            .compileComponents();

        featureBonusService = TestBed.inject(FeatureBonusService);
        vi.spyOn(featureBonusService, 'getAwards').mockReturnValue(of([enemies, music]));
        fixture = TestBed.createComponent(FeatureBonusAwardsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('participationId', 42);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('loads the awards of the participation and sums their points', () => {
        expect(featureBonusService.getAwards).toHaveBeenCalledExactlyOnceWith(42);
        expect(fixture.debugElement.queryAll(By.css('[data-testid="feature-bonus-award"]'))).toHaveLength(2);
        expect(component.totalPoints()).toBe(1.5);
    });

    it('only accepts a feature with points above zero', () => {
        expect(component.formValid()).toBe(false);
        component.title.set('Boss fight');
        component.points.set(0);
        expect(component.formValid()).toBe(false);
        component.points.set(2);
        expect(component.formValid()).toBe(true);
        component.title.set('   ');
        expect(component.formValid()).toBe(false);
    });

    it('creates an award and clears the form', () => {
        const created: FeatureBonusAward = { id: 3, title: 'Boss fight', points: 2 };
        vi.spyOn(featureBonusService, 'createAward').mockReturnValue(of(created));
        component.title.set(' Boss fight ');
        component.justification.set('   ');
        component.points.set(2);

        component.save();

        expect(featureBonusService.createAward).toHaveBeenCalledExactlyOnceWith(42, { title: 'Boss fight', justification: undefined, points: 2 });
        expect(component.awards()).toEqual([enemies, music, created]);
        expect(component.title()).toBe('');
        expect(component.points()).toBeUndefined();
    });

    it('updates the edited award in place', () => {
        const updated: FeatureBonusAward = { ...enemies, points: 2.5 };
        vi.spyOn(featureBonusService, 'updateAward').mockReturnValue(of(updated));
        component.edit(enemies);
        expect(component.title()).toBe(enemies.title);
        component.points.set(2.5);

        component.save();

        expect(featureBonusService.updateAward).toHaveBeenCalledExactlyOnceWith(42, enemies.id, { title: enemies.title, justification: enemies.justification, points: 2.5 });
        expect(component.awards()).toEqual([updated, music]);
        expect(component.editedAwardId()).toBeUndefined();
    });

    it('removes a deleted award and stops editing it', () => {
        vi.spyOn(featureBonusService, 'deleteAward').mockReturnValue(of(undefined));
        component.edit(music);

        component.delete(music);

        expect(featureBonusService.deleteAward).toHaveBeenCalledExactlyOnceWith(42, music.id);
        expect(component.awards()).toEqual([enemies]);
        expect(component.editedAwardId()).toBeUndefined();
    });

    it('keeps the form when saving fails', () => {
        vi.spyOn(featureBonusService, 'createAward').mockReturnValue(throwError(() => new Error('forbidden')));
        component.title.set('Boss fight');
        component.points.set(2);

        component.save();

        expect(component.title()).toBe('Boss fight');
        expect(component.busy()).toBe(false);
    });

    it('hides the form and the actions when read only', () => {
        fixture.componentRef.setInput('readOnly', true);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('form'))).toBeNull();
        expect(fixture.debugElement.query(By.css('jhi-confirm-icon'))).toBeNull();
    });
});
