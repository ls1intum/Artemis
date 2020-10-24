// male sure that jest except is available and preferred to prevent TypeScript errors
type JestExpect = <T>(actual: T) => jest.Matchers<T>;
export declare const expect: JestExpect;

// an alternative would be to use the following to combine jest and jasmine expect
// however, this does not work for some reasons any more and leads to an exception
// type JestJasminExpect = <T>(actual: T) => jest.Matchers<T> & jasmine.Matchers<T>;
// export declare const expect: JestJasminExpect;
