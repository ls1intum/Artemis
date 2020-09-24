package de.tum.in.www1.artemis.constants;

public class ProgrammingSubmissionConstants {

    public final static String TEST_COMMIT = "a6250b6f03c3ae8fa8fb8fdf6bb1dc1c4cc57bad";

    public final static String BITBUCKET_REQUEST = """
            {
               "eventKey":"repo:refs_changed",
               "date":"2019-07-27T17:07:34+0000",
               "actor":{
                  "name":"admin",
                  "emailAddress":"admin@bitbucket.de",
                  "id":1,
                  "displayName":"Admin",
                  "active":true,
                  "slug":"admin",
                  "type":"NORMAL",
                  "links":{
                     "self":[
                        {
                           "href":"http://bitbucket/users/admin"
                        }
                     ]
                  }
               },
               "repository":{
                  "slug":"test201904bprogrammingexercise6-exercise-testuser",
                  "id":422,
                  "name":"test201904bprogrammingexercise6-exercise-testuser",
                  "scmId":"git",
                  "state":"AVAILABLE",
                  "statusMessage":"Available",
                  "forkable":true,
                  "origin":{
                     "slug":"test201904bprogrammingexercise6-exercise",
                     "id":404,
                     "name":"test201904bprogrammingexercise6-exercise",
                     "scmId":"git",
                     "state":"AVAILABLE",
                     "statusMessage":"Available",
                     "forkable":true,
                     "project":{
                        "key":"TEST201904BPROGRAMMINGEXERCISE6",
                        "id":186,
                        "name":"Test201904B ProgrammingExercise6",
                        "public":false,
                        "type":"NORMAL",
                        "links":{
                           "self":[
                              {
                                 "href":"http://bitbucket/projects/TEST201904BPROGRAMMINGEXERCISE6"
                              }
                           ]
                        }
                     },
                     "public":false,
                     "links":{
                        "clone":[
                           {
                              "href":"ssh://git@bitbucket:7999/test201904bprogrammingexercise6/test201904bprogrammingexercise6-exercise.git",
                              "name":"ssh"
                           },
                           {
                              "href":"http://bitbucket/scm/test201904bprogrammingexercise6/test201904bprogrammingexercise6-exercise.git",
                              "name":"http"
                           }
                        ],
                        "self":[
                           {
                              "href":"http://bitbucket/projects/TEST201904BPROGRAMMINGEXERCISE6/repos/test201904bprogrammingexercise6-exercise/browse"
                           }
                        ]
                     }
                  },
                  "project":{
                     "key":"TEST201904BPROGRAMMINGEXERCISE6",
                     "id":186,
                     "name":"Test201904B ProgrammingExercise6",
                     "public":false,
                     "type":"NORMAL",
                     "links":{
                        "self":[
                           {
                              "href":"http://bitbucket/projects/TEST201904BPROGRAMMINGEXERCISE6"
                           }
                        ]
                     }
                  },
                  "public":false,
                  "links":{
                     "clone":[
                        {
                           "href":"http://bitbucket/scm/test201904bprogrammingexercise6/test201904bprogrammingexercise6-exercise-testuser.git",
                           "name":"http"
                        },
                        {
                           "href":"ssh://git@bitbucket:7999/test201904bprogrammingexercise6/test201904bprogrammingexercise6-exercise-testuser.git",
                           "name":"ssh"
                        }
                     ],
                     "self":[
                        {
                           "href":"http://bitbucket/projects/TEST201904BPROGRAMMINGEXERCISE6/repos/test201904bprogrammingexercise6-exercise-testuser/browse"
                        }
                     ]
                  }
               },
               "changes":[
                  {
                     "ref":{
                        "id":"refs/heads/master",
                        "displayId":"master",
                        "type":"BRANCH"
                     },
                     "refId":"refs/heads/master",
                     "fromHash":"e13b41982efdd368a72bda1a32f25b0e3954da78",
                     "toHash":"9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d",
                     "type":"UPDATE"
                  }
               ]
            }""";

    public final static String BAMBOO_REQUEST = """
            {
               "build":{
                  "artifact":false,
                  "number":3,
                  "reason":"Code has changed",
                  "buildCompletedDate":"2019-07-27T17:07:46.642Z[Zulu]",
                  "testSummary":{
                     "duration":66,
                     "ignoredCount":0,
                     "failedCount":13,
                     "existingFailedCount":13,
                     "quarantineCount":0,
                     "successfulCount":0,
                     "description":"13 of 13 failed",
                     "skippedCount":0,
                     "fixedCount":0,
                     "totalCount":13,
                     "newFailedCount":0
                  },
                  "vcs":[
                     {
                        "commits":[
                           {
                              "comment":"BubbleSort.java edited online with Bitbucket",
                              "id":"9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d"
                           }
                        ],
                        "id":"9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d",
                        "repositoryName":"Assignment"
                     },
                     {
                        "commits":[

                        ],
                        "id":"a6250b6f03c3ae8fa8fb8fdf6bb1dc1c4cc57bad",
                        "repositoryName":"tests"
                     }
                  ],
                  "jobs":[
                     {
                        "logs":[

                        ],
                        "skippedTests":[

                        ],
                        "failedTests":[
                           {
                              "name":"testClass[BubbleSort]",
                              "methodName":"Class[bubble sort]",
                              "className":"de.tum.in.www1.ClassTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: the class 'BubbleSort' does not implement the interface 'SortStrategy' as expected. Please implement the interface and its methods.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat de.tum.in.www1.ClassTest.testClass(ClassTest.java:127)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testMethods[Context]",
                              "methodName":"Methods[context]",
                              "className":"de.tum.in.www1.MethodTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name Context in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.MethodTest.testMethods(MethodTest.java:58)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testMethods[Policy]",
                              "methodName":"Methods[policy]",
                              "className":"de.tum.in.www1.MethodTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name Policy in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.MethodTest.testMethods(MethodTest.java:58)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testMethods[SortStrategy]",
                              "methodName":"Methods[sort strategy]",
                              "className":"de.tum.in.www1.MethodTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name SortStrategy in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.MethodTest.testMethods(MethodTest.java:58)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testUseMergeSortForBigList",
                              "methodName":"Use merge sort for big list",
                              "className":"de.tum.in.www1.SortingExampleBehaviorTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: the class 'Context' was not found within the submission. Please implement it properly.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat de.tum.in.www1.BehaviorTest.getClass(BehaviorTest.java:37)\\n\\tat de.tum.in.www1.BehaviorTest.newInstance(BehaviorTest.java:50)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.configurePolicyAndContext(SortingExampleBehaviorTest.java:63)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.testUseMergeSortForBigList(SortingExampleBehaviorTest.java:50)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testConstructors[Policy]",
                              "methodName":"Constructors[policy]",
                              "className":"de.tum.in.www1.ConstructorTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name Policy in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.ConstructorTest.testConstructors(ConstructorTest.java:61)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testMergeSort",
                              "methodName":"Merge sort",
                              "className":"de.tum.in.www1.SortingExampleBehaviorTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: MergeSort does not sort correctly expected:<[Mon Feb 15 00:00:00 GMT 2016, Sat Apr 15 00:00:00 GMT 2017, Fri Sep 15 00:00:00 GMT 2017, Thu Nov 08 00:00:00 GMT 2018]> but was:<[Thu Nov 08 00:00:00 GMT 2018, Sat Apr 15 00:00:00 GMT 2017, Mon Feb 15 00:00:00 GMT 2016, Fri Sep 15 00:00:00 GMT 2017]>\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.failNotEquals(Assert.java:834)\\n\\tat org.junit.Assert.assertEquals(Assert.java:118)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.testMergeSort(SortingExampleBehaviorTest.java:44)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testBubbleSort",
                              "methodName":"Bubble sort",
                              "className":"de.tum.in.www1.SortingExampleBehaviorTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: BubbleSort does not sort correctly expected:<[Mon Feb 15 00:00:00 GMT 2016, Sat Apr 15 00:00:00 GMT 2017, Fri Sep 15 00:00:00 GMT 2017, Thu Nov 08 00:00:00 GMT 2018]> but was:<[Thu Nov 08 00:00:00 GMT 2018, Sat Apr 15 00:00:00 GMT 2017, Mon Feb 15 00:00:00 GMT 2016, Fri Sep 15 00:00:00 GMT 2017]>\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.failNotEquals(Assert.java:834)\\n\\tat org.junit.Assert.assertEquals(Assert.java:118)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.testBubbleSort(SortingExampleBehaviorTest.java:37)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testUseBubbleSortForSmallList",
                              "methodName":"Use bubble sort for small list",
                              "className":"de.tum.in.www1.SortingExampleBehaviorTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: the class 'Context' was not found within the submission. Please implement it properly.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat de.tum.in.www1.BehaviorTest.getClass(BehaviorTest.java:37)\\n\\tat de.tum.in.www1.BehaviorTest.newInstance(BehaviorTest.java:50)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.configurePolicyAndContext(SortingExampleBehaviorTest.java:63)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.testUseBubbleSortForSmallList(SortingExampleBehaviorTest.java:57)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testAttributes[Context]",
                              "methodName":"Attributes[context]",
                              "className":"de.tum.in.www1.AttributeTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name Context in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.AttributeTest.testAttributes(AttributeTest.java:65)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testClass[SortStrategy]",
                              "methodName":"Class[sort strategy]",
                              "className":"de.tum.in.www1.ClassTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name SortStrategy in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.ClassTest.testClass(ClassTest.java:75)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testAttributes[Policy]",
                              "methodName":"Attributes[policy]",
                              "className":"de.tum.in.www1.AttributeTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name Policy in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.AttributeTest.testAttributes(AttributeTest.java:65)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testClass[MergeSort]",
                              "methodName":"Class[merge sort]",
                              "className":"de.tum.in.www1.ClassTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: the class 'MergeSort' does not implement the interface 'SortStrategy' as expected. Please implement the interface and its methods.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat de.tum.in.www1.ClassTest.testClass(ClassTest.java:127)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           }
                        ],
                        "successfulTests":[

                        ],
                        "id":19070980
                     }
                  ],
                  "failedJobs":[
                     {
                        "skippedTests":[

                        ],
                        "failedTests":[
                           {
                              "name":"testClass[BubbleSort]",
                              "methodName":"Class[bubble sort]",
                              "className":"de.tum.in.www1.ClassTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: the class 'BubbleSort' does not implement the interface 'SortStrategy' as expected. Please implement the interface and its methods.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat de.tum.in.www1.ClassTest.testClass(ClassTest.java:127)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testMethods[Context]",
                              "methodName":"Methods[context]",
                              "className":"de.tum.in.www1.MethodTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name Context in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.MethodTest.testMethods(MethodTest.java:58)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testMethods[Policy]",
                              "methodName":"Methods[policy]",
                              "className":"de.tum.in.www1.MethodTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name Policy in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.MethodTest.testMethods(MethodTest.java:58)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testMethods[SortStrategy]",
                              "methodName":"Methods[sort strategy]",
                              "className":"de.tum.in.www1.MethodTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name SortStrategy in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.MethodTest.testMethods(MethodTest.java:58)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testUseMergeSortForBigList",
                              "methodName":"Use merge sort for big list",
                              "className":"de.tum.in.www1.SortingExampleBehaviorTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: the class 'Context' was not found within the submission. Please implement it properly.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat de.tum.in.www1.BehaviorTest.getClass(BehaviorTest.java:37)\\n\\tat de.tum.in.www1.BehaviorTest.newInstance(BehaviorTest.java:50)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.configurePolicyAndContext(SortingExampleBehaviorTest.java:63)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.testUseMergeSortForBigList(SortingExampleBehaviorTest.java:50)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testConstructors[Policy]",
                              "methodName":"Constructors[policy]",
                              "className":"de.tum.in.www1.ConstructorTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name Policy in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.ConstructorTest.testConstructors(ConstructorTest.java:61)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testMergeSort",
                              "methodName":"Merge sort",
                              "className":"de.tum.in.www1.SortingExampleBehaviorTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: MergeSort does not sort correctly expected:<[Mon Feb 15 00:00:00 GMT 2016, Sat Apr 15 00:00:00 GMT 2017, Fri Sep 15 00:00:00 GMT 2017, Thu Nov 08 00:00:00 GMT 2018]> but was:<[Thu Nov 08 00:00:00 GMT 2018, Sat Apr 15 00:00:00 GMT 2017, Mon Feb 15 00:00:00 GMT 2016, Fri Sep 15 00:00:00 GMT 2017]>\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.failNotEquals(Assert.java:834)\\n\\tat org.junit.Assert.assertEquals(Assert.java:118)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.testMergeSort(SortingExampleBehaviorTest.java:44)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testBubbleSort",
                              "methodName":"Bubble sort",
                              "className":"de.tum.in.www1.SortingExampleBehaviorTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: BubbleSort does not sort correctly expected:<[Mon Feb 15 00:00:00 GMT 2016, Sat Apr 15 00:00:00 GMT 2017, Fri Sep 15 00:00:00 GMT 2017, Thu Nov 08 00:00:00 GMT 2018]> but was:<[Thu Nov 08 00:00:00 GMT 2018, Sat Apr 15 00:00:00 GMT 2017, Mon Feb 15 00:00:00 GMT 2016, Fri Sep 15 00:00:00 GMT 2017]>\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.failNotEquals(Assert.java:834)\\n\\tat org.junit.Assert.assertEquals(Assert.java:118)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.testBubbleSort(SortingExampleBehaviorTest.java:37)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testUseBubbleSortForSmallList",
                              "methodName":"Use bubble sort for small list",
                              "className":"de.tum.in.www1.SortingExampleBehaviorTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: the class 'Context' was not found within the submission. Please implement it properly.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat de.tum.in.www1.BehaviorTest.getClass(BehaviorTest.java:37)\\n\\tat de.tum.in.www1.BehaviorTest.newInstance(BehaviorTest.java:50)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.configurePolicyAndContext(SortingExampleBehaviorTest.java:63)\\n\\tat de.tum.in.www1.SortingExampleBehaviorTest.testUseBubbleSortForSmallList(SortingExampleBehaviorTest.java:57)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testAttributes[Context]",
                              "methodName":"Attributes[context]",
                              "className":"de.tum.in.www1.AttributeTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name Context in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.AttributeTest.testAttributes(AttributeTest.java:65)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testClass[SortStrategy]",
                              "methodName":"Class[sort strategy]",
                              "className":"de.tum.in.www1.ClassTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name SortStrategy in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.ClassTest.testClass(ClassTest.java:75)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testAttributes[Policy]",
                              "methodName":"Attributes[policy]",
                              "className":"de.tum.in.www1.AttributeTest",
                              "errors":[
                                 "java.lang.AssertionError: The exercise expects a class with the name Policy in the package de.tum.in.www1. You did not implement the class in the exercise.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat org.junit.Assert.assertTrue(Assert.java:41)\\n\\tat de.tum.in.www1.StructuralTest.findClassForTestType(StructuralTest.java:47)\\n\\tat de.tum.in.www1.AttributeTest.testAttributes(AttributeTest.java:65)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           },
                           {
                              "name":"testClass[MergeSort]",
                              "methodName":"Class[merge sort]",
                              "className":"de.tum.in.www1.ClassTest",
                              "errors":[
                                 "java.lang.AssertionError: Problem: the class 'MergeSort' does not implement the interface 'SortStrategy' as expected. Please implement the interface and its methods.\\n\\tat org.junit.Assert.fail(Assert.java:88)\\n\\tat de.tum.in.www1.ClassTest.testClass(ClassTest.java:127)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)\\n\\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)\\n\\tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)\\n\\tat java.lang.reflect.Method.invoke(Method.java:498)\\n\\tat org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:50)\\n\\tat org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)\\n\\tat org.junit.runners.model.FrameworkMethod.invokeExplosively(FrameworkMethod.java:47)\\n\\tat org.junit.internal.runners.statements.InvokeMethod.evaluate(InvokeMethod.java:17)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:298)\\n\\tat org.junit.internal.runners.statements.FailOnTimeout$CallableStatement.call(FailOnTimeout.java:292)\\n\\tat java.util.concurrent.FutureTask.run(FutureTask.java:266)\\n\\tat java.lang.Thread.run(Thread.java:748)\\n"
                              ]
                           }
                        ],
                        "successfulTests":[

                        ],
                        "id":19070980
                     }
                  ],
                  "successful":false
               },
               "secret":"test123",
               "notificationType":"Completed Plan Notification",
               "plan":{
                  "key":"TEST201904BPROGRAMMINGEXERCISE6-STUDENT1"
               }
            }""";
}
