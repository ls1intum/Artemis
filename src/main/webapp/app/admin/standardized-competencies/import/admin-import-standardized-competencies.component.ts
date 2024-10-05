import { Component, inject } from '@angular/core';
import { faBan, faChevronRight, faFileImport, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
import {
    KnowledgeAreaDTO,
    KnowledgeAreaForTree,
    KnowledgeAreasForImportDTO,
    StandardizedCompetencyForTree,
    convertToKnowledgeAreaForTree,
    sourceToString,
} from 'app/entities/competency/standardized-competency.model';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { AlertService } from 'app/core/util/alert.service';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ButtonType } from 'app/shared/components/button.component';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { NestedTreeControl } from '@angular/cdk/tree';
import { getIcon } from 'app/entities/competency.model';

interface ImportCount {
    knowledgeAreas: number;
    competencies: number;
}

@Component({
    selector: 'jhi-admin-import-standardized-competencies',
    templateUrl: './admin-import-standardized-competencies.component.html',
})
export class AdminImportStandardizedCompetenciesComponent {
    private alertService = inject(AlertService);
    private adminStandardizedCompetencyService = inject(AdminStandardizedCompetencyService);
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);

    protected isLoading = false;
    protected isCollapsed = false;
    protected selectedCompetency?: StandardizedCompetencyForTree;
    //the title of the knowledge area belonging to the selected competency
    protected knowledgeAreaTitle = '';
    protected sourceString = '';
    protected importData?: KnowledgeAreasForImportDTO;
    protected importCount?: ImportCount;
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
        "title": "Artifical Intelligence",
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
        const source = this.importData?.sources.find((source) => source.id === competency.sourceId);
        this.sourceString = source ? sourceToString(source) : '';
        this.knowledgeAreaTitle = knowledgeAreaTitle;
        this.selectedCompetency = competency;
    }

    protected closeCompetencyDetails() {
        this.sourceString = '';
        this.knowledgeAreaTitle = '';
        this.selectedCompetency = undefined;
    }

    importCompetencies() {
        this.isLoading = true;
        this.adminStandardizedCompetencyService.importStandardizedCompetencyCatalog(this.importData!).subscribe({
            next: () => {
                this.isLoading = false;
                this.alertService.success('artemisApp.standardizedCompetency.manage.import.success');
                this.router.navigate(['../'], { relativeTo: this.activatedRoute });
            },
            error: (error: HttpErrorResponse) => {
                onError(this.alertService, error);
                this.isLoading = false;
            },
        });
    }

    toggleCollapse() {
        this.isCollapsed = !this.isCollapsed;
    }

    cancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    /**
     * Sets the importData and counts the knowledgeAreas and standardizedCompetencies contained
     * @private
     */
    private setImportDataAndCount() {
        this.importData = undefined;
        this.importCount = { knowledgeAreas: 0, competencies: 0 };

        try {
            this.importData = JSON.parse(this.fileReader.result as string);
        } catch (e) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.import.error.fileSyntax');
        }
        try {
            if (this.importData) {
                this.importCount = this.countKnowledgeAreasAndCompetencies({ children: this.importData.knowledgeAreas });
                this.importCount.knowledgeAreas -= 1;
                this.dataSource.data = this.importData.knowledgeAreas.map((knowledgeArea) => convertToKnowledgeAreaForTree(knowledgeArea));
            }
        } catch (e) {
            this.importData = undefined;
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
