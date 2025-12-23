import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { faBan, faChevronRight, faFileImport, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import {
    KnowledgeAreaDTO,
    KnowledgeAreaForTree,
    KnowledgeAreasForImportDTO,
    StandardizedCompetencyForTree,
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
import { NestedTreeControl } from '@angular/cdk/tree';
import { getIcon } from 'app/atlas/shared/entities/competency.model';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbCollapse, NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { StandardizedCompetencyDetailComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-detail.component';
import { KnowledgeAreaTreeComponent } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

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
        StandardizedCompetencyDetailComponent,
        KnowledgeAreaTreeComponent,
        NgbCollapse,
        HtmlForMarkdownPipe,
        ArtemisTranslatePipe,
        NgbTooltipModule,
        TranslateDirective,
        ButtonComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
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
    protected dataSource = new MatTreeNestedDataSource<KnowledgeAreaForTree>();
    protected treeControl = new NestedTreeControl<KnowledgeAreaForTree>((node) => node.children);
    private fileReader: FileReader = new FileReader();

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
            this.importData.set(undefined);
            this.alertService.error('artemisApp.standardizedCompetency.manage.import.error.fileStructure');
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
