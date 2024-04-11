import { Component } from '@angular/core';
import { faUpload } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeAreasForImportDTO } from 'app/entities/competency/standardized-competency.model';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { AlertService } from 'app/core/util/alert.service';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import { HttpErrorResponse } from '@angular/common/http';
import { onError } from 'app/shared/util/global.utils';

@Component({
    selector: 'jhi-admin-import-standardized-competencies',
    templateUrl: './admin-import-standardized-competencies.component.html',
})
export class AdminImportStandardizedCompetenciesComponent {
    //Icons
    protected readonly faUpload = faUpload;

    importData?: KnowledgeAreasForImportDTO;
    fileReader = new FileReader();
    isLoading = false;

    public constructor(
        private alertService: AlertService,
        private adminStandardizedCompetencyService: AdminStandardizedCompetencyService,
    ) {}

    setImportCompetencies(event: Event) {
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
                //TODO: i dont even need this -> directly parse into comps!
                this.fileReader.readAsText(file);
                this.fileReader.onload = () => this.doSomething();
            }
        }
    }

    doSomething() {
        try {
            this.importData = JSON.parse(this.fileReader.result as string) as KnowledgeAreasForImportDTO;
        } catch (e) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.import.error.fileStructure');
        }
    }

    //TODO: verification: for all competencies, see that the source is actually contained.

    uploadCompetencies() {
        if (!this.importData) {
            this.alertService.error('artemisApp.standardizedCompetency.manage.import.error.noCompetencies');
            return;
        }

        this.adminStandardizedCompetencyService.importCompetencies(this.importData).subscribe({
            next: () => {
                this.alertService.success('artemisApp.standardizedCompetency.manage.import.success', { noCompetencies: 0, noKnowledgeAreas: 0 });
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }
}
