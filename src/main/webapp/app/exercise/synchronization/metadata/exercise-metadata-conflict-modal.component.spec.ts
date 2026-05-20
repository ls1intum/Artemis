import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import dayjs from 'dayjs/esm';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';

import { Competency, CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import {
    ExerciseMetadataConflictItem,
    ExerciseMetadataConflictModalComponent,
    ExerciseMetadataConflictModalResult,
} from 'app/exercise/synchronization/metadata/exercise-metadata-conflict-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseMetadataConflictModalComponent', () => {
    setupTestBed({ zoneless: true });
    let fixture: ComponentFixture<ExerciseMetadataConflictModalComponent>;
    let component: ExerciseMetadataConflictModalComponent;
    let dialogRef: DynamicDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;

    const createConflict = (field: string, currentValue: unknown = 'local', incomingValue: unknown = 'incoming'): ExerciseMetadataConflictItem => ({
        field,
        labelKey: `label.${field}`,
        currentValue,
        incomingValue,
    });

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            imports: [ExerciseMetadataConflictModalComponent],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: DynamicDialogConfig, useValue: { data: undefined } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseMetadataConflictModalComponent);
        component = fixture.componentInstance;
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

    it('sets all decisions to incoming via setAllDecisions(true)', () => {
        component.setConflicts([createConflict('title'), createConflict('shortName'), createConflict('maxPoints')]);

        component.setAllDecisions(true);

        expect(component.decisions()).toEqual({
            title: true,
            shortName: true,
            maxPoints: true,
        });
    });

    it('sets all decisions to local via setAllDecisions(false)', () => {
        component.setConflicts([createConflict('title'), createConflict('shortName')]);
        component.updateDecision('title', true);
        component.updateDecision('shortName', true);

        component.setAllDecisions(false);

        expect(component.decisions()).toEqual({
            title: false,
            shortName: false,
        });
    });

    it('allLocal returns true when all decisions are false', () => {
        component.setConflicts([createConflict('title'), createConflict('shortName')]);

        expect(component.allLocal()).toBe(true);
        expect(component.allIncoming()).toBe(false);
    });

    it('allIncoming returns true when all decisions are true', () => {
        component.setConflicts([createConflict('title'), createConflict('shortName')]);
        component.setAllDecisions(true);

        expect(component.allIncoming()).toBe(true);
        expect(component.allLocal()).toBe(false);
    });

    it('neither allLocal nor allIncoming when decisions are mixed', () => {
        component.setConflicts([createConflict('title'), createConflict('shortName')]);
        component.updateDecision('shortName', true);

        expect(component.allLocal()).toBe(false);
        expect(component.allIncoming()).toBe(false);
    });

    it('allLocal returns true and allIncoming returns false for empty conflicts', () => {
        component.setConflicts([]);

        expect(component.allLocal()).toBe(true);
        expect(component.allIncoming()).toBe(false);
    });

    it('applies selections and closes modal with mapped decisions', () => {
        component.setConflicts([createConflict('title'), createConflict('shortName')]);
        component.updateDecision('shortName', true);

        component.applySelections();

        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith({
            decisions: [
                { field: 'title', useIncoming: false },
                { field: 'shortName', useIncoming: true },
            ],
        } satisfies ExerciseMetadataConflictModalResult);
    });

    it('keeps all local changes and closes modal', () => {
        component.setConflicts([createConflict('title'), createConflict('shortName')]);
        component.updateDecision('shortName', true);

        component.keepLocalChanges();

        expect(dialogRefCloseSpy).toHaveBeenCalledWith({
            decisions: [
                { field: 'title', useIncoming: false },
                { field: 'shortName', useIncoming: false },
            ],
        } satisfies ExerciseMetadataConflictModalResult);
    });

    it('builds authorName from name/fullName/login', () => {
        component.author.set({ name: 'External Name', login: 'login-x' });
        expect(component.authorName()).toBe('External Name');

        component.author.set({ firstName: 'Ada', lastName: 'Lovelace', login: 'ada' });
        expect(component.authorName()).toBe('Ada Lovelace');

        component.author.set({ login: 'fallback-login' });
        expect(component.authorName()).toBe('fallback-login');
    });

    it('reads data from DynamicDialogConfig on init', async () => {
        const config = TestBed.inject(DynamicDialogConfig);
        config.data = {
            conflicts: [createConflict('title')],
            author: { login: 'test-author' },
            versionId: 42,
            exerciseId: 99,
            exerciseType: 'PROGRAMMING',
        };

        component.ngOnInit();

        expect(component.conflicts()).toHaveLength(1);
        expect(component.author()?.login).toBe('test-author');
        expect(component.versionId()).toBe(42);
        expect(component.exerciseId()).toBe(99);
        expect(component.exerciseType()).toBe('PROGRAMMING');
    });

    it('formats values for all supported input kinds', () => {
        const dayjsValue = dayjs('2026-01-01T10:30:00.000Z');

        expect(component.formatValue(undefined)).toBe('\u2014');
        expect(component.formatValue(null)).toBe('\u2014');
        expect(component.formatValue(dayjsValue)).toBe(dayjsValue.format('YYYY-MM-DD HH:mm'));
        expect(component.formatValue('text')).toBe('text');
        expect(component.formatValue(42)).toBe('42');
        expect(component.formatValue(true)).toBe('true');
        expect(component.formatValue({ a: 1 })).toBe('{"a":1}');

        const circular: Record<string, unknown> = {};
        circular.self = circular;
        expect(component.formatValue(circular)).toContain('[object Object]');
    });

    it('formats IncludedInOverallScore enum values via translation', () => {
        expect(component.formatValue(IncludedInOverallScore.INCLUDED_COMPLETELY)).toBe('artemisApp.exercise.yes');
        expect(component.formatValue(IncludedInOverallScore.INCLUDED_AS_BONUS)).toBe('artemisApp.exercise.bonus');
        expect(component.formatValue(IncludedInOverallScore.NOT_INCLUDED)).toBe('artemisApp.exercise.no');
    });

    it('formats dayjs-like objects that fail isDayjs via duck-typing fallback', () => {
        const fakeDayjs = { format: (fmt: string) => `formatted:${fmt}` };

        expect(component.formatValue(fakeDayjs)).toBe('formatted:YYYY-MM-DD HH:mm');
    });

    it('identifies special fields', () => {
        expect(component.isBuildConfigField('programmingData.buildConfig')).toBe(true);
        expect(component.isBuildConfigField('title')).toBe(false);
        expect(component.isCategoriesField('categories')).toBe(true);
        expect(component.isCategoriesField('title')).toBe(false);
        expect(component.isCompetencyLinksField('competencyLinks')).toBe(true);
        expect(component.isCompetencyLinksField('title')).toBe(false);
        expect(component.isGradingCriteriaField('gradingCriteria')).toBe(true);
        expect(component.isGradingCriteriaField('title')).toBe(false);
    });

    it('maps build config values for a single config object', () => {
        const entries = component.getBuildConfigValues({
            sequentialTestRuns: true,
            timeoutSeconds: 30,
            branchRegex: 'feature/.*',
        });

        expect(entries).toHaveLength(12);
        expect(entries.find((entry) => entry.labelKey.endsWith('sequentialTestRuns'))).toEqual(expect.objectContaining({ value: true }));
        expect(entries.find((entry) => entry.labelKey.endsWith('timeoutSeconds'))).toEqual(expect.objectContaining({ value: 30 }));
        expect(entries.find((entry) => entry.labelKey.endsWith('branchRegex'))).toEqual(expect.objectContaining({ value: 'feature/.*' }));
    });

    it('returns undefined values for missing build config', () => {
        const entries = component.getBuildConfigValues(undefined);

        expect(entries).toHaveLength(12);
        expect(entries.every((entry) => entry.value === undefined)).toBe(true);
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

    it('normalizes JSON-serialized category entries for incoming conflict values', () => {
        const byJsonArrayEntries = component.toCategoryEntries([
            '{"category":"alpha","color":"#123456"}',
            '{"category":"beta","color":"#abcdef"}',
            '{"category":"   ","color":"#000000"}',
        ]);
        expect(byJsonArrayEntries.map((category) => category.category)).toEqual(['alpha', 'beta']);
        expect(byJsonArrayEntries.map((category) => category.color)).toEqual(['#123456', '#abcdef']);

        const byJsonArrayString = component.toCategoryEntries('[{"category":"gamma","color":"#111111"},{"category":"delta","color":"#222222"}]');
        expect(byJsonArrayString.map((category) => category.category)).toEqual(['gamma', 'delta']);
        expect(byJsonArrayString.map((category) => category.color)).toEqual(['#111111', '#222222']);
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

    it('maps grading criteria to display format', () => {
        const instruction = new GradingInstruction();
        instruction.credits = 1.5;
        instruction.gradingScale = 'Good';
        instruction.instructionDescription = 'Well structured';
        instruction.feedback = 'Nice work';

        const criterion = new GradingCriterion();
        criterion.title = 'Code Quality';
        criterion.structuredGradingInstructions = [instruction];

        const emptyTitleCriterion = new GradingCriterion();
        emptyTitleCriterion.title = '';
        emptyTitleCriterion.structuredGradingInstructions = [];

        const result = component.toGradingCriteriaDisplay([criterion, emptyTitleCriterion]);
        expect(result).toHaveLength(2);
        expect(result[0].title).toBe('Code Quality');
        expect(result[0].instructions).toHaveLength(1);
        expect(result[0].instructions[0].credits).toBe(1.5);
        // Empty title falls back to translation key
        expect(result[1].title).toBe('artemisApp.exercise.metadataSync.untitledCriterion');
        expect(result[1].instructions).toHaveLength(0);

        expect(component.toGradingCriteriaDisplay(undefined)).toEqual([]);
        expect(component.toGradingCriteriaDisplay(null)).toEqual([]);
    });

    it('renders conflict tiles with left tile selected by default', () => {
        const config = TestBed.inject(DynamicDialogConfig);
        config.data = {
            conflicts: [createConflict('title'), createConflict('categories', ['a'], ['b'])],
            author: { firstName: 'Ada', lastName: 'Lovelace', login: 'ada' },
            versionId: 3,
        };
        component.ngOnInit();
        fixture.detectChanges();

        const items = fixture.nativeElement.querySelectorAll('.conflict-item');
        expect(items).toHaveLength(2);
        expect(fixture.nativeElement.textContent).toContain('Ada Lovelace');
        expect(fixture.nativeElement.textContent).toContain('#3');

        const tiles = fixture.nativeElement.querySelectorAll('.conflict-tile');
        expect(tiles.length).toBeGreaterThanOrEqual(4);

        // Left tiles (your value) should be selected by default
        const selectedTiles = fixture.nativeElement.querySelectorAll('.conflict-tile--selected');
        expect(selectedTiles.length).toBeGreaterThanOrEqual(2);
    });

    it('renders global toggle buttons', () => {
        const config = TestBed.inject(DynamicDialogConfig);
        config.data = {
            conflicts: [createConflict('title')],
            author: { login: 'test' },
            versionId: 1,
        };
        component.ngOnInit();
        fixture.detectChanges();

        const globalToggle = fixture.nativeElement.querySelector('.global-toggle');
        expect(globalToggle).toBeTruthy();
        const buttons = globalToggle.querySelectorAll('button');
        expect(buttons).toHaveLength(2);
    });
});
