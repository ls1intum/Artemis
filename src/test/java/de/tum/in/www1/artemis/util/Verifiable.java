package de.tum.in.www1.artemis.util;

/**
 * Wrapper interface for bundling multiple {@link org.mockito.Mockito} verifications for execution after all tests ran.
 * You can use this if some external method sets up some mocks for you, but you want to verify the called methods on
 * the mocks (or spies), so you have to return callback method calls, that verify this behavior.
 * Example:
 * <pre>{@code
 * @Service
 * public class FooMockService {
 *     public List<Verifiable> setUpSomeMocks() {
 *          final var verifications = new ArrayList<Verifiable>();
 *          ...
 *          verifications.add(() -> Mockito.verify(...));
 *          ...
 *          verifications.add(() -> Mockito.verify(...));
 *
 *          return verifications;
 *     }
 *
 *     public List<Verifiable> setUpSomeMoreMocks() {...}
 * }
 *
 * class TestBar {
 *     @Test
 *     void testSomeStuff() {
 *          final var verifications = new ArrayList<Verifiable>();
 *          verifications.addAll(fooMockService.setUpSomeMocks());
 *          verifications.addAll(fooMockService.setUpSomeMoreMocks());
 *
 *          mvc.performRequest(...);
 *
 *          for(final var verifiable : verifications) {
 *              verifiable.performVerification();
 *          }
 *     }
 * }
 * }</pre>
 * @see org.mockito.Mockito#verify(Object)
 */
@FunctionalInterface
public interface Verifiable {

    void performVerification() throws Exception;
}
