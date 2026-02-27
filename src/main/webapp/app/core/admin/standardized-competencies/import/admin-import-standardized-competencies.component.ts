import { Component, inject, signal } from '@angular/core';
import { faBan, faChevronRight, faFileImport, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import {
    KnowledgeAreaDTO,
    KnowledgeAreaForTree,
    KnowledgeAreaValidators,
    KnowledgeAreasForImportDTO,
    Source,
    SourceValidators,
    StandardizedCompetencyDTO,
    StandardizedCompetencyForTree,
    StandardizedCompetencyValidators,
    convertToKnowledgeAreaForTree,
    sourceToString,
} from 'app/atlas/shared/entities/standardized-competency.model';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { AlertService } from 'app/shared/service/alert.service';
import { AdminStandardizedCompetencyService } from 'app/core/admin/standardized-competencies/admin-standardized-competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ButtonComponent, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { CompetencyTaxonomy, getIcon } from 'app/atlas/shared/entities/competency.model';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbCollapse, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { StandardizedCompetencyDetailComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-detail.component';
import { KnowledgeAreaTreeComponent } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';

interface ImportCount {
    knowledgeAreas: number;
    competencies: number;
}

/**
 * Component for importing standardized competencies from a JSON file.
 * Allows previewing and validating the import data before submission.
 */
@Component({
    selector: 'jhi-admin-import-standardized-competencies',
    templateUrl: './admin-import-standardized-competencies.component.html',
    imports: [
        FontAwesomeModule,
        NgbCollapse,
        NgbTooltipModule,
        ArtemisTranslatePipe,
        TranslateDirective,
        HtmlForMarkdownPipe,
        StandardizedCompetencyDetailComponent,
        KnowledgeAreaTreeComponent,
        ButtonComponent,
        AdminTitleBarTitleDirective,
    ],
})
export class AdminImportStandardizedCompetenciesComponent {
    /** Whether import is loading */
    protected readonly isLoading = signal(false);
    /** Whether the help section is collapsed */
    protected readonly isCollapsed = signal(false);
    /** Selected competency for details view */
    protected readonly selectedCompetency = signal<StandardizedCompetencyForTree | undefined>(undefined);
    /** Title of the knowledge area belonging to the selected competency */
    protected readonly knowledgeAreaTitle = signal('');
    /** Source string for the selected competency */
    protected readonly sourceString = signal('');
    /** Import data from JSON file */
    protected readonly importData = signal<KnowledgeAreasForImportDTO | undefined>(undefined);
    /** Count of knowledge areas and competencies to import */
    protected readonly importCount = signal<ImportCount | undefined>(undefined);
    /** Validation errors found in the parsed import data */
    protected readonly validationErrors = signal<string[]>([]);
    protected dataSource = new MatTreeNestedDataSource<KnowledgeAreaForTree>();
    private fileReader: FileReader = new FileReader();
    private readonly validationTranslationBase = 'artemisApp.standardizedCompetency.manage.import.error.validation';

    //Icons
    protected readonly faFileImport = faFileImport;
    protected readonly faBan = faBan;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faChevronRight = faChevronRight;
    //Other constants
    protected readonly getIcon = getIcon;
    protected readonly ButtonType = ButtonType;
    protected readonly importExample = `\`\`\`
{
    "knowledgeAreas": [{
        "title": "Artificial Intelligence",
        "shortTitle": "AI",
        "description": "AI is a field in computer science...", //(optional)
        "competencies": [{
            "title": "Machine Learning",
            "description": "1. Explain examples of machine learning tasks \\n2. ....", //(optional)
            //(optional) one of REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE
            "taxonomy": "UNDERSTAND",
            //(optional) must match a source below if it exists
            "sourceId": 1,
        }],
        "children": [{
            //nested knowledge areas...
        }],
    }],
    "sources": [{
        "id": 1,
        "title": "Book about machine learning",
        "author": "Doe, Mustermann, et al.",
        "uri": "http://localhost" //(optional)
    }]
}
\`\`\``;

    private alertService = inject(AlertService);
    private adminStandardizedCompetencyService = inject(AdminStandardizedCompetencyService);
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private translateService = inject(TranslateService);

    /**
     * Verifies the file (only .json, smaller than 20 MB) and then tries to read the importData from it
     *
     * @param event the event triggered by changing the file
     */
    onFileChange(event: Event) {
        this.dataSource.data = [];
        const input = event.target as HTMLInputElement;
        if (input.files?.length) {
            const fileList: FileList = input.files;
            if (fileList.length != 1) {
                this.alertService.error('artemisApp.standardizedCompetency.manage.import.error.fileCount');
                return;
            }
            const file = fileList[0];
            if (!file.name.toLowerCase().endsWith('.json')) {
                this.alertService.error('artemisApp.standardizedCompetency.manage.import.error.fileExtension');
                return;
            } else if (file.size > MAX_FILE_SIZE) {
                this.alertService.error('artemisApp.standardizedCompetency.manage.import.error.fileTooBig');
                return;
            } else {
                this.fileReader.readAsText(file);
                this.fileReader.onload = () => this.setImportDataAndCount();
            }
        }
    }

    protected openCompetencyDetails(competency: StandardizedCompetencyForTree, knowledgeAreaTitle: string) {
        const data = this.importData();
        const source = data?.sources.find((source) => source.id === competency.sourceId);
        this.sourceString.set(source ? sourceToString(source) : '');
        this.knowledgeAreaTitle.set(knowledgeAreaTitle);
        this.selectedCompetency.set(competency);
    }

    protected closeCompetencyDetails() {
        this.sourceString.set('');
        this.knowledgeAreaTitle.set('');
        this.selectedCompetency.set(undefined);
    }

    importCompetencies(): void {
        this.isLoading.set(true);
        this.adminStandardizedCompetencyService.importStandardizedCompetencyCatalog(this.importData()!).subscribe({
            next: () => {
                this.isLoading.set(false);
                this.alertService.success('artemisApp.standardizedCompetency.manage.import.success');
                this.router.navigate(['../'], { relativeTo: this.activatedRoute });
            },
            error: (error: HttpErrorResponse) => {
                onError(this.alertService, error);
                this.isLoading.set(false);
            },
        });
    }

    toggleCollapse() {
        this.isCollapsed.update((collapsed) => !collapsed);
    }

    cancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    /**
     * Sets the importData and counts the knowledgeAreas and standardizedCompetencies contained.
     */
    private setImportDataAndCount(): void {
        this.importData.set(undefined);
        this.importCount.set({ knowledgeAreas: 0, competencies: 0 });
        this.validationErrors.set([]);

        let parsedData: KnowledgeAreasForImportDTO | undefined;
        try {
            parsedData = JSON.parse(this.fileReader.result as string);
            this.importData.set(parsedData);
        } catch (e) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.import.error.fileSyntax');
        }
        try {
            if (parsedData) {
                const count = this.countKnowledgeAreasAndCompetencies({ children: parsedData.knowledgeAreas });
                count.knowledgeAreas -= 1;
                this.importCount.set(count);
                this.dataSource.data = parsedData.knowledgeAreas.map((knowledgeArea) => convertToKnowledgeAreaForTree(knowledgeArea));
            }
        } catch (e) {
            parsedData = undefined;
            this.importData.set(undefined);
            this.alertService.error('artemisApp.standardizedCompetency.manage.import.error.fileStructure');
        }
        if (parsedData) {
            const errors = this.validateImportData(parsedData);
            if (errors.length > 0) {
                this.validationErrors.set(errors);
                this.importData.set(undefined);
                this.importCount.set(undefined);
                this.dataSource.data = [];
            } else {
                this.validationErrors.set([]);
            }
        }
    }

    /**
     * Validates the parsed import data against server-side constraints.
     * Returns an array of i18n-translated error strings; empty array means valid.
     */
    private validateImportData(data: KnowledgeAreasForImportDTO): string[] {
        const errors: string[] = [];
        const sourceIds = new Set((data.sources ?? []).map((s) => s.id).filter((id): id is number => id !== undefined));
        (data.sources ?? []).forEach((source, index) => this.validateSource(source, index + 1, errors));
        for (const ka of data.knowledgeAreas ?? []) {
            this.validateKnowledgeArea(ka, errors, sourceIds);
        }
        return errors;
    }

    private validateSource(source: Source, index: number, errors: string[]): void {
        const title = source.title;
        if (!title) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.sourceTitleRequired`, { index }));
        } else {
            if (title.length > SourceValidators.FIELD_MAX) {
                errors.push(this.translateService.instant(`${this.validationTranslationBase}.sourceTitleTooLong`, { title, max: SourceValidators.FIELD_MAX }));
            }
            if (!source.author) {
                errors.push(this.translateService.instant(`${this.validationTranslationBase}.sourceAuthorRequired`, { title }));
            } else if (source.author.length > SourceValidators.FIELD_MAX) {
                errors.push(this.translateService.instant(`${this.validationTranslationBase}.sourceAuthorTooLong`, { title, max: SourceValidators.FIELD_MAX }));
            }
            if (source.uri && source.uri.length > SourceValidators.FIELD_MAX) {
                errors.push(this.translateService.instant(`${this.validationTranslationBase}.sourceUriTooLong`, { title, max: SourceValidators.FIELD_MAX }));
            }
        }
    }

    private validateKnowledgeArea(ka: KnowledgeAreaDTO, errors: string[], sourceIds: Set<number>, path = 'Knowledge area'): void {
        const label = ka.title ? `Knowledge area '${ka.title}'` : path;
        if (!ka.title) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.titleRequired`, { label }));
        } else if (ka.title.length > KnowledgeAreaValidators.TITLE_MAX) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.titleTooLong`, { label, max: KnowledgeAreaValidators.TITLE_MAX }));
        }
        if (!ka.shortTitle) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.shortTitleRequired`, { label }));
        } else if (ka.shortTitle.length > KnowledgeAreaValidators.SHORT_TITLE_MAX) {
            errors.push(
                this.translateService.instant(`${this.validationTranslationBase}.shortTitleTooLong`, { label, value: ka.shortTitle, max: KnowledgeAreaValidators.SHORT_TITLE_MAX }),
            );
        }
        if (ka.description && ka.description.length > KnowledgeAreaValidators.DESCRIPTION_MAX) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.descriptionTooLong`, { label, max: KnowledgeAreaValidators.DESCRIPTION_MAX }));
        }
        for (const child of ka.children ?? []) {
            this.validateKnowledgeArea(child, errors, sourceIds, `Child of ${label}`);
        }
        for (const competency of ka.competencies ?? []) {
            this.validateCompetency(competency, errors, sourceIds, label);
        }
    }

    private validateCompetency(competency: StandardizedCompetencyDTO, errors: string[], sourceIds: Set<number>, parentLabel: string): void {
        const label = competency.title ? `Competency '${competency.title}' in ${parentLabel}` : `Unnamed competency in ${parentLabel}`;
        if (!competency.title) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.titleRequired`, { label }));
        } else if (competency.title.length > StandardizedCompetencyValidators.TITLE_MAX) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.titleTooLong`, { label, max: StandardizedCompetencyValidators.TITLE_MAX }));
        }
        if (competency.description && competency.description.length > StandardizedCompetencyValidators.DESCRIPTION_MAX) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.descriptionTooLong`, { label, max: StandardizedCompetencyValidators.DESCRIPTION_MAX }));
        }
        if (competency.taxonomy && !Object.values(CompetencyTaxonomy).includes(competency.taxonomy)) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.taxonomyInvalid`, { label, value: competency.taxonomy }));
        }
        if (competency.version && competency.version.length > StandardizedCompetencyValidators.VERSION_MAX) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.versionTooLong`, { label, max: StandardizedCompetencyValidators.VERSION_MAX }));
        }
        if (competency.sourceId !== undefined && competency.sourceId !== null && !sourceIds.has(competency.sourceId)) {
            errors.push(this.translateService.instant(`${this.validationTranslationBase}.sourceIdInvalid`, { label, value: competency.sourceId }));
        }
    }

    /**
     * Returns the count of knowledgeAreas and competencies (respectively) contained in this knowledgeArea and its descendants
     *
     * @param knowledgeArea the knowledge area
     * @private
     */
    private countKnowledgeAreasAndCompetencies(knowledgeArea: KnowledgeAreaDTO): ImportCount {
        const competencies = knowledgeArea.competencies?.length ?? 0;
        const descendantCounts = knowledgeArea.children?.map((child) => this.countKnowledgeAreasAndCompetencies(child)) ?? [];
        const descendantSum = descendantCounts.reduce(
            (previous, current) => {
                return {
                    knowledgeAreas: previous.knowledgeAreas + current.knowledgeAreas,
                    competencies: previous.competencies + current.competencies,
                };
            },
            { knowledgeAreas: 0, competencies: 0 },
        );

        return {
            knowledgeAreas: descendantSum.knowledgeAreas + 1,
            competencies: descendantSum.competencies + competencies,
        };
    }
}
