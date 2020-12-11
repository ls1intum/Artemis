import { ContributorModel } from 'app/core/about-us/models/contributor-model';

export class AboutUsModel {
    projectManagers: ContributorModel[];
    contributors: ContributorModel[];

    constructor(projectManagers: ContributorModel[], contributors: ContributorModel[]) {
        this.projectManagers = projectManagers;
        this.contributors = contributors;
    }
}
