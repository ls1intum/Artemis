import { Component } from '@angular/core';
import { faBan, faFileImport } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeAreaDTO, KnowledgeAreasForImportDTO } from 'app/entities/competency/standardized-competency.model';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { AlertService } from 'app/core/util/alert.service';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';
import { ButtonType } from 'app/shared/components/button.component';
import { ActivatedRoute, Router } from '@angular/router';

interface importCount {
    knowledgeAreas: number;
    competencies: number;
}

@Component({
    selector: 'jhi-admin-import-standardized-competencies',
    templateUrl: './admin-import-standardized-competencies.component.html',
})
export class AdminImportStandardizedCompetenciesComponent {
    isLoading = false;
    fileReader: FileReader;
    importData?: KnowledgeAreasForImportDTO;
    count?: importCount;

    //Icons
    protected readonly faFileImport = faFileImport;
    protected readonly faBan = faBan;
    //Other constants
    protected readonly ButtonType = ButtonType;

    public constructor(
        private alertService: AlertService,
        private adminStandardizedCompetencyService: AdminStandardizedCompetencyService,
        private activatedRoute: ActivatedRoute,
        private router: Router,
    ) {
        this.fileReader = this.generateFileReader();
    }

    onFileChange(event: Event) {
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
                this.isLoading = true;
                this.fileReader.readAsText(file);
                this.fileReader.onload = () => this.setImportCompetencies();
            }
        }
    }

    //TODO: comments, TODO: remove file if none selected anymore?

    setImportCompetencies() {
        try {
            this.importData = JSON.parse(this.fileReader.result as string);
            if (!this.importData) {
                this.count = { knowledgeAreas: 0, competencies: 0 };
            } else {
                this.count = this.countKnowledgeAreasAndCompetencies({ children: this.importData.knowledgeAreas });
                this.count.knowledgeAreas -= 1;
            }
        } catch (e) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.import.error.fileFormat');
        } finally {
            this.isLoading = false;
        }
    }

    importCompetencies() {
        this.isLoading = true;
        this.adminStandardizedCompetencyService.importCompetencies(this.importData!).subscribe({
            next: () => {
                this.isLoading = false;
                this.alertService.success('artemisApp.standardizedCompetency.manage.import.success');
                this.router.navigate(['../'], { relativeTo: this.activatedRoute });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    cancel() {
        this.router.navigate(['../'], { relativeTo: this.activatedRoute });
    }

    /**
     * Move file reader creation to separate function to be able to mock
     * https://fromanegg.com/post/2015/04/22/easy-testing-of-code-involving-native-methods-in-javascript/
     */
    private generateFileReader() {
        return new FileReader();
    }

    /**
     * Returns the count of knowledgeAreas and competencies (respectively) contained in this knowledgeArea and its descendants
     *
     * @param knowledgeArea the knowledge area
     * @private
     */
    private countKnowledgeAreasAndCompetencies(knowledgeArea: KnowledgeAreaDTO): importCount {
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
