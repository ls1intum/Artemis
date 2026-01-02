import { ContributorModel } from './contributor-model';

describe('ContributorModel', () => {
    it('should return sortBy when available', () => {
        const contributor = new ContributorModel('John Doe', '/photo/path', 'Doe, John', 'Developer', 'https://example.com');

        expect(contributor.getSortIndex()).toBe('Doe, John');
    });

    it('should return last name from fullName when sortBy is not provided', () => {
        const contributor = new ContributorModel('John Doe', '/photo/path');

        expect(contributor.getSortIndex()).toBe('Doe');
    });

    it('should return fullName when sortBy and last name splitting fails', () => {
        const contributor = new ContributorModel('John', '/photo/path');

        expect(contributor.getSortIndex()).toBe('John');
    });
});
