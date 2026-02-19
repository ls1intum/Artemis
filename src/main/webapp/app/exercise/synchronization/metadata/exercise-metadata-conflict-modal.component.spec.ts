import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import dayjs from 'dayjs/esm';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { TranslateService } from '@ngx-translate/core';
import { Subject } from 'rxjs';

import { Competency, CompetencyExerciseLink } from 'app/atlas/shared/entities/competency.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
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

    it('dismisses modal on close()', () => {
        component.close();

        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
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

    it('identifies special fields', () => {
        expect(component.isBuildConfigField('programmingData.buildConfig')).toBe(true);
        expect(component.isBuildConfigField('title')).toBe(false);
        expect(component.isCategoriesField('categories')).toBe(true);
        expect(component.isCategoriesField('title')).toBe(false);
        expect(component.isCompetencyLinksField('competencyLinks')).toBe(true);
        expect(component.isCompetencyLinksField('title')).toBe(false);
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

    it('renders conflicts and toggles decision via checkbox interaction', () => {
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
    });
});
