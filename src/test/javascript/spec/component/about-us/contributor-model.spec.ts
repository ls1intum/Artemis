import { ContributorModel } from 'app/core/about-us/models/contributor-model';

describe('ContributorModel', () => {
    it('constructor should set class variables accordingly', () => {
        const fullName = 'Full Name';
        const photoDirectory = 'Photo Directory';
        const role = 'ADMIN';
        const website = 'www.website.de';

        const model = new ContributorModel(fullName, photoDirectory, role, website);

        expect(model.fullName).toBe(fullName);
        expect(model.photoDirectory).toBe(photoDirectory);
        expect(model.role).toBe(role);
        expect(model.website).toBe(website);
    });
});
