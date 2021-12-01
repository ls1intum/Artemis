package de.tum.in.www1.artemis.service.connectors;

// TODO: use these constants in text exercise / athene tests
public class TestSubmissionConstants {

    public static final String TEXT_0 = """
            Differences:
            Antipatterns:
            -Have one problem and two solutions(one problematic and one refactored
            -Antipatterns are a sign of bad architecture and bad coding
            Pattern
            -Have one problem and one solutio
            -Patterns are a sign of elaborated architecutre and coding""";

    public static final String TEXT_1 = """
            The main difference between patterns and antipatterns is, that patterns show you a good way to do something and antipatterns show a bad way to do something. \
            Nevertheless patterns may become antipatterns in the course of changing understanding of how good software engineering looks like. One example for that is functional decomposition, \
            which used to be a pattern and good practice. Over the time it turned out that it is not a goog way to solve problems, so it became a antipattern

            A pattern itsself is a proposed solution to a problem that occurs often and in different situations
            In contrast to that a antipattern shows commonly made mistakes when dealing with a certain problem. Nevertheless a refactored solution is aswell proposed.""";

    public static final String TEXT_2 = """
            1.Patterns can evolve into Antipatterns when change occurs\\n\
            2. Pattern has one solution, whereas anti pattern can have subtypes of solution\\n3\
            . Antipattern has negative consequences and symptom, where as patterns looks only into benefits and consequences""";

    public static final String TEXT_3 = """
            Patterns: A way to Model code in differents ways
            Antipattern: A way of how Not to Model code""";

    public static final String TEXT_4 = """
            Antipatterns are used when there are common mistakes in software management and development to find these, \
            while patterns by themselves are used to build software systems in the context of frequent change by reducing complexity and isolating the change
            Another difference is that the antipatterns have problematic solution and then refactored solution, while patterns only have a solution.""";

    public static final String TEXT_5 = """
            - In patterns we have a problem and a solution, in antipatterns we have a problematic solution and a refactored solution instea
            - patterns represent best practices from the industry etc. so proven concepts, whereas antipatterns shed a light on common mistakes during software development etc.""";

    public static final String TEXT_6 = """
            1) Patterns have one solution, antipatterns have to solutions (one problematic and one refactored)
            2) for the coice of patterns code has to be written; for antipatterns, the bad smell code already exists""";

    public static final String TEXT_7 = """
            Design Patterns

            Solutions which are productive and efficient and are developed by Software Engineers over the years of practice and solving problems

            Anti Patterns

            Known solutions which are actually bad or defective to certain kind of problems.""";

    public static final String TEXT_8 = """
            Patterns has one problem and one solution
            Antipatterns have one problematic solution and a solution for that. The antipattern happens when  a solution that is been used for a long time can not apply anymore. """;

    public static final String TEXT_9 = """
            Patterns identify problems and present solutions
            Antipatterns identify problems but two kinds of solutions. One problematic solution and a better refactored version of the solution. \
            Problematic solutions are suggested not to be used because they results in smells or hinder future work.""";
}
