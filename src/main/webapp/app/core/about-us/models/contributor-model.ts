export class ContributorModel {
    fullName: string;
    photoDirectory: string;
    role: string;
    website: string;

    constructor(fullName: string, photoDirectory: string, role: string, website: string) {
        this.fullName = fullName;
        this.photoDirectory = photoDirectory;
        this.role = role;
        this.website = website;
    }
}
