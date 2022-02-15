import { ContributorModel } from '../../../../../main/webapp/app/core/about-us/models/contributor-model';

describe('ContributorModel', () => {
    it('constructor should set class variables accordingly', () => {
        const fullName = 'Full Name';
        const photoDirectory = 'Photo Directory';
        const role = 'ADMIN';
        const website = 'www.website.de';

        const model = new ContributorModel(fullName, photoDirectory, role, website);

        expect(model.fullName).toEqual(fullName);
        expect(model.photoDirectory).toEqual(photoDirectory);
        expect(model.role).toEqual(role);
        expect(model.website).toEqual(website);
    });
});
