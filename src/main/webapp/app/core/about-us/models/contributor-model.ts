export class ContributorModel {
    fullName: string;
    photoDirectory: string;
    sortBy?: string;
    role?: string;
    website?: string;

    constructor(fullName: string, photoDirectory: string, sortBy?: string, role?: string, website?: string) {
        this.fullName = fullName;
        this.photoDirectory = photoDirectory;
        this.sortBy = sortBy;
        this.role = role;
        this.website = website;
    }

    public getSortIndex(): string {
        return this.sortBy || this.fullName.split(' ').last() || this.fullName;
    }
}
