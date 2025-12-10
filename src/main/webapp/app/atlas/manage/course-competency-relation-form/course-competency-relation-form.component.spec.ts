import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { CourseCompetencyRelationFormComponent, UnionFind } from 'app/atlas/manage/course-competency-relation-form/course-competency-relation-form.component';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { CourseCompetencyApiService } from 'app/atlas/shared/services/course-competency-api.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyRelationDTO, CompetencyRelationType, CourseCompetency, UpdateCourseCompetencyRelationDTO } from 'app/atlas/shared/entities/competency.model';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { of } from 'rxjs';

describe('CourseCompetencyRelationFormComponent', () => {
    let component: CourseCompetencyRelationFormComponent;
    let fixture: ComponentFixture<CourseCompetencyRelationFormComponent>;
    let courseCompetencyApiService: CourseCompetencyApiService;
    let alertService: AlertService;

    let createCourseCompetencyRelationSpy: jest.SpyInstance;
    let updateCourseCompetencyRelationSpy: jest.SpyInstance;
    let deleteCourseCompetencyRelationSpy: jest.SpyInstance;
    let getSuggestedCompetencyRelationsSpy: jest.SpyInstance;

    const courseId = 1;
    const courseCompetencies: CourseCompetency[] = [
        { id: 1, title: 'Competency 1' },
        { id: 2, title: 'Competency 2' },
        { id: 3, title: 'Competency 3' },
    ];
    const relations: CompetencyRelationDTO[] = [
        {
            id: 1,
            tailCompetencyId: 1,
            headCompetencyId: 2,
            relationType: CompetencyRelationType.EXTENDS,
        },
    ];
    const selectedRelationId = 1;

    const newRelation = <CompetencyRelationDTO>{
        id: 2,
        headCompetencyId: 2,
        tailCompetencyId: 3,
        relationType: CompetencyRelationType.EXTENDS,
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetencyRelationFormComponent],
            providers: [
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: CourseCompetencyApiService,
                    useValue: {
                        createCourseCompetencyRelation: jest.fn(),
                        updateCourseCompetencyRelation: jest.fn(),
                        deleteCourseCompetencyRelation: jest.fn(),
                        getSuggestedCompetencyRelations: jest.fn(),
                    },
                },
                {
                    provide: FeatureToggleService,
                    useValue: {
                        getFeatureToggleActive: jest.fn().mockReturnValue(of(true)),
                    },
                },
            ],
        }).compileComponents();

        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        alertService = TestBed.inject(AlertService);

        createCourseCompetencyRelationSpy = jest.spyOn(courseCompetencyApiService, 'createCourseCompetencyRelation').mockResolvedValue(newRelation);
        updateCourseCompetencyRelationSpy = jest.spyOn(courseCompetencyApiService, 'updateCourseCompetencyRelation').mockResolvedValue();
        deleteCourseCompetencyRelationSpy = jest.spyOn(courseCompetencyApiService, 'deleteCourseCompetencyRelation').mockResolvedValue();
        getSuggestedCompetencyRelationsSpy = jest.spyOn(courseCompetencyApiService, 'getSuggestedCompetencyRelations');

        fixture = TestBed.createComponent(CourseCompetencyRelationFormComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('courseCompetencies', courseCompetencies);
        fixture.componentRef.setInput('relations', relations);
        fixture.componentRef.setInput('selectedRelationId', undefined);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should set relationAlreadyExists correctly', () => {
        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(1);
        component.relationType.set(CompetencyRelationType.ASSUMES);

        fixture.detectChanges();

        expect(component.relationAlreadyExists()).toBeTrue();
    });

    it('should set exactRelationAlreadyExists correctly', () => {
        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(1);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        fixture.detectChanges();

        expect(component.exactRelationAlreadyExists()).toBeTrue();
    });

    it('should select relation if selectedRelationId is set', () => {
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        expect(component.headCompetencyId()).toBe(2);
        expect(component.tailCompetencyId()).toBe(1);
        expect(component.relationType()).toBe(CompetencyRelationType.EXTENDS);
    });

    it('should set headCompetencyId if it is undefined', () => {
        component.headCompetencyId.set(undefined);
        component.tailCompetencyId.set(2);

        component.selectCourseCompetency(1);

        expect(component.headCompetencyId()).toBe(1);
        expect(component.tailCompetencyId()).toBeUndefined();
    });

    it('should set tailCompetencyId if headCompetencyId is defined and tailCompetencyId is undefined', () => {
        component.headCompetencyId.set(1);
        component.tailCompetencyId.set(undefined);

        component.selectCourseCompetency(2);

        expect(component.tailCompetencyId()).toBe(2);
    });

    it('should reset headCompetencyId if both headCompetencyId and tailCompetencyId are defined', () => {
        component.headCompetencyId.set(1);
        component.tailCompetencyId.set(2);

        component.selectCourseCompetency(3);

        expect(component.headCompetencyId()).toBe(3);
        expect(component.tailCompetencyId()).toBeUndefined();
    });

    it('should create relation', async () => {
        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(3);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        await component['createRelation']();

        expect(createCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, {
            headCompetencyId: 2,
            tailCompetencyId: 3,
            relationType: CompetencyRelationType.EXTENDS,
        });
        expect(component.headCompetencyId()).toBe(2);
        expect(component.tailCompetencyId()).toBe(3);
        expect(component.relationType()).toBe(CompetencyRelationType.EXTENDS);
        expect(component.selectedRelationId()).toBe(2);
        expect(component.relations()).toEqual([...relations, newRelation]);
    });

    it('should set isLoading correctly when creating a relation', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(3);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        await component['createRelation']();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when creating relation fails', async () => {
        const error = 'Error creating relation';
        createCourseCompetencyRelationSpy.mockRejectedValue(error);
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');

        component.headCompetencyId.set(2);
        component.tailCompetencyId.set(3);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        await component['createRelation']();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should update relation', async () => {
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        component.relationType.set(CompetencyRelationType.ASSUMES);

        await component['updateRelation']();

        expect(updateCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, selectedRelationId, <UpdateCourseCompetencyRelationDTO>{
            newRelationType: CompetencyRelationType.ASSUMES,
        });
        const newRelations = [...relations].map((relation) => {
            if (relation.id === selectedRelationId) {
                return Object.assign({}, relation, { relationType: CompetencyRelationType.ASSUMES });
            }
            return relation;
        });
        expect(component.relations()).toEqual(newRelations);
    });

    it('should set isLoading correctly when updating a relation', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        component.relationType.set(CompetencyRelationType.ASSUMES);

        await component['updateRelation']();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when updating relation fails', async () => {
        updateCourseCompetencyRelationSpy.mockRejectedValue('Error updating relation');
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        component.relationType.set(CompetencyRelationType.ASSUMES);

        await component['updateRelation']();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should select head course competency', () => {
        component['selectHeadCourseCompetency'](2);

        expect(component.headCompetencyId()).toBe(2);
        expect(component.tailCompetencyId()).toBeUndefined();
        expect(component.selectedRelationId()).toBeUndefined();
    });

    it('should set tailCompetencyId and selectedRelationId when an existing relation is found', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const tailId = 1;
        component.headCompetencyId.set(2);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        component['selectTailCourseCompetency'](tailId);

        expect(component.tailCompetencyId()).toBe(1);
        expect(component.selectedRelationId()).toBe(1);
    });

    it('should set tailCompetencyId and clear selectedRelationId when no existing relation is found', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        const tailId = 2;
        component.headCompetencyId.set(3);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        component['selectTailCourseCompetency'](tailId);

        expect(component.tailCompetencyId()).toBe(2);
        expect(component.selectedRelationId()).toBeUndefined();
    });

    it('should not allow to create circular dependencies', () => {
        component.headCompetencyId.set(1);
        component.tailCompetencyId.set(1);
        component.relationType.set(CompetencyRelationType.EXTENDS);

        expect(component['selectableTailCourseCompetencyIds']).not.toContain(1);
        expect(component.showCircularDependencyError()).toBeTrue();
    });

    it('should delete relation', async () => {
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        await component['deleteRelation']();

        expect(deleteCourseCompetencyRelationSpy).toHaveBeenCalledExactlyOnceWith(courseId, selectedRelationId);
        expect(component.relations()).toHaveLength(relations.length - 1);
    });

    it('should set isLoading correctly when deleting a relation', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        await component['deleteRelation']();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when deleting relation fails', async () => {
        deleteCourseCompetencyRelationSpy.mockRejectedValue('Error deleting relation');
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');
        fixture.componentRef.setInput('selectedRelationId', selectedRelationId);

        fixture.detectChanges();

        await component['deleteRelation']();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    describe('AtlasML Relation Suggestions', () => {
        const mockSuggestionResponse = {
            relations: [
                { tail_id: '1', head_id: '2', relation_type: 'EXTENDS' },
                { tail_id: '2', head_id: '3', relation_type: 'REQUIRES' },
                { tail_id: '1', head_id: '3', relation_type: 'MATCHES' },
            ],
        };

        beforeEach(() => {
            fixture.detectChanges();
        });

        it('should show lightbulb button for relation suggestions', () => {
            // First check if any jhi-button elements exist at all
            const allButtons = fixture.debugElement.queryAll(By.css('jhi-button'));
            expect(allButtons.length).toBeGreaterThan(0);

            // Find the button with the correct tooltip
            const btn = allButtons.find((de) => de.componentInstance?.tooltip === 'artemisApp.courseCompetency.relations.suggestions.getAiSuggestionsTooltip');
            expect(btn).toBeTruthy();
            expect(btn?.componentInstance?.disabled).toBeFalse();
        });

        it('should disable lightbulb button when loading suggestions', async () => {
            getSuggestedCompetencyRelationsSpy.mockImplementation(() => new Promise(() => {})); // Never resolves

            component.fetchSuggestions();
            fixture.detectChanges();

            const btn = fixture.debugElement
                .queryAll(By.css('jhi-button'))
                .find((de) => de.componentInstance?.tooltip === 'artemisApp.courseCompetency.relations.suggestions.getAiSuggestionsTooltip');
            expect(btn?.componentInstance?.disabled).toBeTrue();
            expect(component.isLoadingSuggestions()).toBeTrue();
        });

        it('should call API and load suggestions when lightbulb button is clicked', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();

            expect(getSuggestedCompetencyRelationsSpy).toHaveBeenCalledWith(courseId);
            expect(component.suggestedRelations()).toEqual(mockSuggestionResponse.relations);
            expect(component.isLoadingSuggestions()).toBeFalse();
        });

        it('should auto-select only non-existing suggestions when fetched', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();

            // Existing relations (1->2 EXTENDS) should NOT be auto-selected
            expect(component['doesSuggestionAlreadyExist'](mockSuggestionResponse.relations[0])).toBeTrue();
            expect(component.selectedSuggestions().has(0)).toBeFalse();

            // Non-existing suggestions should be selected
            expect(component.selectedSuggestions().has(1)).toBeTrue();
            expect(component.selectedSuggestions().has(2)).toBeTrue();
            expect(component.selectedSuggestions().size).toBe(2);
            expect(component.selectedSuggestionsCount()).toBe(2);
        });

        it('should display suggested relations with correct information', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();
            fixture.detectChanges();

            const suggestionElements = fixture.debugElement.nativeElement.querySelectorAll('.list-group-item');
            expect(suggestionElements).toHaveLength(3);

            // Check first suggestion
            const firstSuggestion = suggestionElements[0];
            expect(firstSuggestion.textContent).toContain('Competency 1');
            expect(firstSuggestion.textContent).toContain('Competency 2');
            expect(firstSuggestion.textContent).toContain('→');
        });

        it('should toggle suggestion selection correctly', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();

            // Initially: index 1 and 2 are selected (non-existing); index 0 is not (existing)
            expect(component['isSuggestionSelected'](1)).toBeTrue();
            expect(component.selectedSuggestionsCount()).toBe(2);

            // Toggle index 1 OFF
            component['toggleSuggestionSelection'](1);
            expect(component['isSuggestionSelected'](1)).toBeFalse();
            expect(component.selectedSuggestionsCount()).toBe(1);

            // Toggle index 1 back ON
            component['toggleSuggestionSelection'](1);
            expect(component['isSuggestionSelected'](1)).toBeTrue();
            expect(component.selectedSuggestionsCount()).toBe(2);
        });

        it('should prevent selection of existing relations', async () => {
            // Mock response includes a relation that already exists (1->2 EXTENDS)
            const responseWithExisting = {
                relations: [
                    { tail_id: '1', head_id: '2', relation_type: 'EXTENDS' }, // This already exists
                    { tail_id: '2', head_id: '3', relation_type: 'REQUIRES' },
                ],
            };
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(responseWithExisting);

            await component.fetchSuggestions();

            expect(component['doesSuggestionAlreadyExist'](responseWithExisting.relations[0])).toBeTrue();
            expect(component['doesSuggestionAlreadyExist'](responseWithExisting.relations[1])).toBeFalse();

            // Should only auto-select non-existing relations
            expect(component.selectedSuggestions().has(0)).toBeFalse();
            expect(component.selectedSuggestions().has(1)).toBeTrue();
        });

        it('should apply suggestion to form when clicked', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();

            const suggestion = mockSuggestionResponse.relations[0];
            component['applySuggestion'](suggestion);

            expect(component.headCompetencyId()).toBe(2);
            expect(component.tailCompetencyId()).toBe(1);
            expect(component.relationType()).toBe(CompetencyRelationType.EXTENDS);
        });

        it('should map REQUIRES relation type to ASSUMES', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();

            const requiresSuggestion = mockSuggestionResponse.relations[1]; // REQUIRES relation
            const uiKey = component['getUiRelationTypeKey'](requiresSuggestion);

            expect(uiKey).toBe('ASSUMES');
        });

        it('should show import suggestions button when suggestions are loaded', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();
            fixture.detectChanges();

            const importButton = fixture.debugElement.query(By.css('[data-testid="add-suggestions-button"]'));
            expect(importButton).toBeTruthy();
        });

        it('should create selected suggestions when import button is clicked', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();

            // Select only a non-existing suggestion (index 2)
            component.selectedSuggestions.set(new Set([2]));

            const mockCreatedRelation = {
                id: 10,
                headCompetencyId: 3,
                tailCompetencyId: 1,
                relationType: CompetencyRelationType.MATCHES,
            };
            createCourseCompetencyRelationSpy.mockResolvedValue(mockCreatedRelation);

            await component['addSelectedSuggestions']();

            expect(createCourseCompetencyRelationSpy).toHaveBeenCalledWith(courseId, {
                headCompetencyId: 3,
                tailCompetencyId: 1,
                relationType: CompetencyRelationType.MATCHES,
            });
        });

        it('should clear suggestions after successful import', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();

            const mockCreatedRelation = {
                id: 10,
                headCompetencyId: 2,
                tailCompetencyId: 1,
                relationType: CompetencyRelationType.EXTENDS,
            };
            createCourseCompetencyRelationSpy.mockResolvedValue(mockCreatedRelation);

            await component['addSelectedSuggestions']();

            expect(component.suggestedRelations()).toEqual([]);
            expect(component.selectedSuggestions().size).toBe(0);
        });

        it('should handle API error gracefully when fetching suggestions', async () => {
            const alertServiceWarningSpy = jest.spyOn(alertService, 'warning');
            getSuggestedCompetencyRelationsSpy.mockRejectedValue(new Error('API Error'));

            await component.fetchSuggestions();

            expect(component.isLoadingSuggestions()).toBeFalse();
            expect(component.suggestedRelations()).toEqual([]);
            expect(alertServiceWarningSpy).toHaveBeenCalledWith('Failed to load suggested relations');
        });

        it('should handle partial failures when importing suggestions', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();

            // Mock first relation creation to succeed, second to fail
            createCourseCompetencyRelationSpy.mockResolvedValueOnce({
                id: 10,
                headCompetencyId: 2,
                tailCompetencyId: 1,
                relationType: CompetencyRelationType.EXTENDS,
            });
            createCourseCompetencyRelationSpy.mockRejectedValueOnce(new Error('Creation failed'));

            const alertServiceErrorSpy = jest.spyOn(alertService, 'error');
            const alertServiceSuccessSpy = jest.spyOn(alertService, 'success');

            await component['addSelectedSuggestions']();

            expect(alertServiceErrorSpy).toHaveBeenCalled();
            expect(alertServiceSuccessSpy).toHaveBeenCalledWith('Successfully added 1 relation(s)');
        });

        it('should not import suggestions if none are selected', async () => {
            getSuggestedCompetencyRelationsSpy.mockResolvedValue(mockSuggestionResponse);

            await component.fetchSuggestions();

            // Deselect all suggestions
            component.selectedSuggestions.set(new Set());

            await component['addSelectedSuggestions']();

            expect(createCourseCompetencyRelationSpy).not.toHaveBeenCalled();
        });
    });
});

describe('UnionFind', () => {
    let unionFind: UnionFind;

    beforeEach(() => {
        unionFind = new UnionFind(5);
    });

    it('should initialize parent and rank arrays correctly', () => {
        expect(unionFind.parent).toEqual([0, 1, 2, 3, 4]);
        expect(unionFind.rank).toEqual([1, 1, 1, 1, 1]);
    });

    it('should find the representative of a set', () => {
        expect(unionFind.find(0)).toBe(0);
        expect(unionFind.find(1)).toBe(1);
    });

    it('should perform union by rank correctly', () => {
        unionFind.union(0, 1);
        expect(unionFind.find(0)).toBe(unionFind.find(1));
    });

    it('should perform path compression correctly', () => {
        unionFind.union(0, 1);
        unionFind.union(1, 2);
        expect(unionFind.find(2)).toBe(0);
        expect(unionFind.parent[2]).toBe(0);
    });

    it('should handle union of already connected components', () => {
        unionFind.union(0, 1);
        unionFind.union(1, 2);
        unionFind.union(0, 2);
        expect(unionFind.find(2)).toBe(0);
    });

    it('should handle union of components with equal rank', () => {
        unionFind.union(0, 1);
        unionFind.union(2, 3);
        unionFind.union(1, 2);
        expect(unionFind.find(3)).toBe(0);
        expect(unionFind.rank[0]).toBe(3); // Corrected expected rank value
    });
});
