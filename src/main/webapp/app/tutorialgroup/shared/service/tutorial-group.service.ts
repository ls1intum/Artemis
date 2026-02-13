import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RawTutorialGroupDTO, TutorialGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { map } from 'rxjs/operators';
import { AlertService } from 'app/shared/service/alert.service';

@Injectable({
    providedIn: 'root',
})
export class TutorialGroupService {
    private httpClient = inject(HttpClient);
    private resourceURL = 'api/tutorialgroup';
    private alertService = inject(AlertService);

    isLoading = signal(false);
    tutorialGroup = signal<TutorialGroupDTO | undefined>(undefined);

    fetchTutorialGroupDTO(courseId: number, tutorialGroupId: number) {
        this.isLoading.set(true);
        this.httpClient
            .get<RawTutorialGroupDTO>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/dto`)
            .pipe(map((rawDto) => new TutorialGroupDTO(rawDto)))
            .subscribe({
                next: (tutorialGroup) => {
                    this.tutorialGroup.set(tutorialGroup);
                    this.isLoading.set(false);
                },
                error: () => {
                    this.alertService.addErrorAlert('Something went wrong while fetching the tutorial group information. Please refresh the page to try again.'); // TODO: create string key
                    this.isLoading.set(false);
                },
            });
    }
}
