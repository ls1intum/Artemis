import { ComponentFixture, TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';

import { Competency, CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import {
    ExerciseMetadataConflictItem,
    ExerciseMetadataConflictModalComponent,
    ExerciseMetadataConflictModalResult,
} from 'app/exercise/synchronization/exercise-metadata-conflict-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseMetadataConflictModalComponent', () => {
    let fixture: ComponentFixture<ExerciseMetadataConflictModalComponent>;
    let component: ExerciseMetadataConflictModalComponent;
    let activeModal: NgbActiveModal;

    const createConflict = (field: string, currentValue: unknown = 'local', incomingValue: unknown = 'incoming'): ExerciseMetadataConflictItem => ({
        field,
        labelKey: `label.${field}`,
        currentValue,
        incomingValue,
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseMetadataConflictModalComponent],
            providers: [MockProvider(NgbActiveModal), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseMetadataConflictModalComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
    });

    it('initializes decisions to false for all conflicts', () => {
        component.setConflicts([createConflict('title'), createConflict('shortName')]);

        expect(component.decisions()).toEqual({
            title: false,
            shortName: false,
        });
    });

    it('updates one decision without dropping other fields', () => {
        component.setConflicts([createConflict('title'), createConflict('shortName')]);

        component.updateDecision('shortName', true);

        expect(component.decisions()).toEqual({
            title: false,
            shortName: true,
        });
    });

    it('applies selections and closes modal with mapped decisions', () => {
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.setConflicts([createConflict('title'), createConflict('shortName')]);
        component.updateDecision('shortName', true);

        component.applySelections();

        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith({
            decisions: [
                { field: 'title', useIncoming: false },
                { field: 'shortName', useIncoming: true },
            ],
        } satisfies ExerciseMetadataConflictModalResult);
    });

    it('keeps all local changes and closes modal', () => {
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.setConflicts([createConflict('title'), createConflict('shortName')]);
        component.updateDecision('shortName', true);

        component.keepLocalChanges();

        expect(closeSpy).toHaveBeenCalledWith({
            decisions: [
                { field: 'title', useIncoming: false },
                { field: 'shortName', useIncoming: false },
            ],
        } satisfies ExerciseMetadataConflictModalResult);
    });

    it('dismisses modal on close()', () => {
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');

        component.close();

        expect(dismissSpy).toHaveBeenCalledOnce();
    });

    it('builds authorName from name/fullName/login and supports version id', () => {
        component.setAuthor({ name: 'External Name', login: 'login-x' });
        expect(component.authorName()).toBe('External Name');

        component.setAuthor({ firstName: 'Ada', lastName: 'Lovelace', login: 'ada' });
        expect(component.authorName()).toBe('Ada Lovelace');

        component.setAuthor({ login: 'fallback-login' });
        expect(component.authorName()).toBe('fallback-login');

        component.setVersionId(12);
        expect(component.versionId()).toBe(12);
    });

    it('stores exercise id and type', () => {
        component.setExerciseId(99);
        component.setExerciseType('programming' as any);

        expect(component.exerciseId()).toBe(99);
        expect(component.exerciseType()).toBe('programming');
    });

    it('formats values for all supported input kinds', () => {
        const dayjsValue = dayjs('2026-01-01T10:30:00.000Z');

        expect(component.formatValue(undefined)).toBe('—');
        expect(component.formatValue(null)).toBe('—');
        expect(component.formatValue(dayjsValue)).toBe(dayjsValue.format('YYYY-MM-DD HH:mm'));
        expect(component.formatValue('text')).toBe('text');
        expect(component.formatValue(42)).toBe('42');
        expect(component.formatValue(true)).toBe('true');
        expect(component.formatValue({ a: 1 })).toBe('{"a":1}');

        const circular: Record<string, unknown> = {};
        circular.self = circular;
        expect(component.formatValue(circular)).toContain('[object Object]');
    });

    it('identifies special fields', () => {
        expect(component.isBuildConfigField('programmingData.buildConfig')).toBeTrue();
        expect(component.isBuildConfigField('title')).toBeFalse();
        expect(component.isCategoriesField('categories')).toBeTrue();
        expect(component.isCategoriesField('title')).toBeFalse();
        expect(component.isCompetencyLinksField('competencyLinks')).toBeTrue();
        expect(component.isCompetencyLinksField('title')).toBeFalse();
    });

    it('maps build config entries with current and incoming values', () => {
        const entries = component.getBuildConfigEntries(
            {
                sequentialTestRuns: true,
                timeoutSeconds: 30,
                branchRegex: 'feature/.*',
            },
            {
                sequentialTestRuns: false,
                timeoutSeconds: 60,
                branchRegex: 'main',
            },
        );

        expect(entries).toHaveLength(12);
        expect(entries.find((entry) => entry.labelKey.endsWith('sequentialTestRuns'))).toEqual(expect.objectContaining({ current: true, incoming: false }));
        expect(entries.find((entry) => entry.labelKey.endsWith('timeoutSeconds'))).toEqual(expect.objectContaining({ current: 30, incoming: 60 }));
    });

    it('normalizes categories from arrays, strings and objects', () => {
        const byArray = component.toCategoryEntries([new ExerciseCategory('alpha', '#111'), { category: 'beta', color: '#222' }, { category: '   ', color: '#333' }, 'gamma']);
        expect(byArray.map((category) => category.category)).toEqual(['alpha', 'beta', 'gamma']);

        const byString = component.toCategoryEntries('x, y , , z');
        expect(byString.map((category) => category.category)).toEqual(['x', 'y', 'z']);

        const bySingleObject = component.toCategoryEntries({ category: 'single', color: '#444' });
        expect(bySingleObject).toHaveLength(1);
        expect(bySingleObject[0].category).toBe('single');

        expect(component.toCategoryEntries(undefined)).toEqual([]);
    });

    it('maps competency links to display format and filters missing titles', () => {
        const competencyA = new Competency();
        competencyA.title = 'Comp A';
        const competencyB = new Competency();
        competencyB.title = undefined;

        const links = [new CompetencyExerciseLink(competencyA, undefined, 0.5), new CompetencyExerciseLink(competencyB, undefined, 1)];

        expect(component.toCompetencyDisplay(links)).toEqual([{ title: 'Comp A', weight: 0.5 }]);
        expect(component.toCompetencyDisplay(undefined)).toEqual([]);
        expect(component.toCompetencyDisplay({})).toEqual([]);
    });

    it('renders conflicts and toggles decision via checkbox interaction', () => {
        component.setAuthor({ firstName: 'Ada', lastName: 'Lovelace', login: 'ada' });
        component.setVersionId(3);
        component.setConflicts([createConflict('title'), createConflict('categories', ['a'], ['b'])]);
        fixture.detectChanges();

        const items = fixture.nativeElement.querySelectorAll('.conflict-item');
        expect(items).toHaveLength(2);
        expect(fixture.nativeElement.textContent).toContain('Ada Lovelace');
        expect(fixture.nativeElement.textContent).toContain('#3');

        const checkbox = fixture.nativeElement.querySelector('input.form-check-input') as HTMLInputElement;
        checkbox.click();
        fixture.detectChanges();

        expect(component.decisions().title).toBeTrue();
    });

    it('clicking footer actions triggers modal closing paths', () => {
        const closeSpy = jest.spyOn(activeModal, 'close');
        component.setConflicts([createConflict('title')]);
        fixture.detectChanges();

        const buttons = fixture.nativeElement.querySelectorAll('.modal-footer button') as NodeListOf<HTMLButtonElement>;
        buttons[0].click();
        buttons[1].click();

        expect(closeSpy).toHaveBeenCalledTimes(2);
        expect(closeSpy).toHaveBeenNthCalledWith(1, { decisions: [{ field: 'title', useIncoming: false }] });
        expect(closeSpy).toHaveBeenNthCalledWith(2, { decisions: [{ field: 'title', useIncoming: false }] });
    });
});
