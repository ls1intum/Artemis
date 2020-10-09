// these two lines solve typescript errors, because both, jest and jasmine define expect, simply import this combined expect to make sure Typescript does not complain
type JestExpect = <T>(actual: T) => jest.Matchers<T> & jasmine.Matchers<T>;
export declare const expect: JestExpect;
