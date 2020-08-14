package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.service.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

class UMLClassDiagramTest extends AbstractUMLDiagramTest {

    private UMLClassDiagram classDiagram;

    private static final String classModel1 = "{\"version\":\"2.0.0\",\"type\":\"ClassDiagram\",\"size\":{\"width\":1359.7265625,\"height\":551.6875},\"interactive\":{\"elements\":[],\"relationships\":[]},\"elements\":[{\"id\":\"6022e553-115f-46d2-9500-ec5d8821a2cf\",\"name\":\"Developer\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":294.1953125,\"y\":0,\"width\":200,\"height\":190},\"attributes\":[],\"methods\":[\"543d9022-88da-48f9-8174-a397456b8fb5\",\"257edb98-94e1-4244-878b-c3ee207bf286\",\"a9744be7-fdf6-4a68-ad84-0766290b1bea\",\"be6d5148-2804-4aeb-9e73-2a5b7f1a2827\",\"45518376-2634-43e5-9103-3e3064b5dbec\"]},{\"id\":\"543d9022-88da-48f9-8174-a397456b8fb5\",\"name\":\"+ commit()\",\"type\":\"ClassMethod\",\"owner\":\"6022e553-115f-46d2-9500-ec5d8821a2cf\",\"bounds\":{\"x\":294.1953125,\"y\":40,\"width\":200,\"height\":30}},{\"id\":\"257edb98-94e1-4244-878b-c3ee207bf286\",\"name\":\"+ checkout()\",\"type\":\"ClassMethod\",\"owner\":\"6022e553-115f-46d2-9500-ec5d8821a2cf\",\"bounds\":{\"x\":294.1953125,\"y\":70,\"width\":200,\"height\":30}},{\"id\":\"a9744be7-fdf6-4a68-ad84-0766290b1bea\",\"name\":\"+ compile()\",\"type\":\"ClassMethod\",\"owner\":\"6022e553-115f-46d2-9500-ec5d8821a2cf\",\"bounds\":{\"x\":294.1953125,\"y\":100,\"width\":200,\"height\":30}},{\"id\":\"be6d5148-2804-4aeb-9e73-2a5b7f1a2827\",\"name\":\"+ test build()\",\"type\":\"ClassMethod\",\"owner\":\"6022e553-115f-46d2-9500-ec5d8821a2cf\",\"bounds\":{\"x\":294.1953125,\"y\":130,\"width\":200,\"height\":30}},{\"id\":\"45518376-2634-43e5-9103-3e3064b5dbec\",\"name\":\"+ notifyaboutissue()\",\"type\":\"ClassMethod\",\"owner\":\"6022e553-115f-46d2-9500-ec5d8821a2cf\",\"bounds\":{\"x\":294.1953125,\"y\":160,\"width\":200,\"height\":30}},{\"id\":\"63dc1e84-96d0-408a-8aa0-77464e477020\",\"name\":\"Version Control Server\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":287.015625,\"y\":322.66796875,\"width\":200,\"height\":130},\"attributes\":[],\"methods\":[\"5ed4419d-b334-40b3-bb8c-debeca451c9d\",\"085dc0bd-c7aa-4044-a472-bfe1f9e919ce\",\"220f5deb-4026-44e7-a3d4-73420bea5d37\"]},{\"id\":\"5ed4419d-b334-40b3-bb8c-debeca451c9d\",\"name\":\"+ checkout()\",\"type\":\"ClassMethod\",\"owner\":\"63dc1e84-96d0-408a-8aa0-77464e477020\",\"bounds\":{\"x\":287.015625,\"y\":362.66796875,\"width\":200,\"height\":30}},{\"id\":\"085dc0bd-c7aa-4044-a472-bfe1f9e919ce\",\"name\":\"+ compile()\",\"type\":\"ClassMethod\",\"owner\":\"63dc1e84-96d0-408a-8aa0-77464e477020\",\"bounds\":{\"x\":287.015625,\"y\":392.66796875,\"width\":200,\"height\":30}},{\"id\":\"220f5deb-4026-44e7-a3d4-73420bea5d37\",\"name\":\"+ testbuild()\",\"type\":\"ClassMethod\",\"owner\":\"63dc1e84-96d0-408a-8aa0-77464e477020\",\"bounds\":{\"x\":287.015625,\"y\":422.66796875,\"width\":200,\"height\":30}},{\"id\":\"23b6e9cb-4e8d-40a9-9cd0-bcdbaded9352\",\"name\":\"Issue Tracker\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":0,\"y\":232.5078125,\"width\":200,\"height\":70},\"attributes\":[],\"methods\":[\"b9878683-3fc2-402a-9216-ea0b423a511b\"]},{\"id\":\"b9878683-3fc2-402a-9216-ea0b423a511b\",\"name\":\"+ notifyaboutissue()\",\"type\":\"ClassMethod\",\"owner\":\"23b6e9cb-4e8d-40a9-9cd0-bcdbaded9352\",\"bounds\":{\"x\":0,\"y\":272.5078125,\"width\":200,\"height\":30}},{\"id\":\"de9e72f9-bcdb-4ea9-a50a-e981430dceab\",\"name\":\"Release manager \",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":630.48828125,\"y\":3.69140625,\"width\":210,\"height\":100},\"attributes\":[],\"methods\":[\"862cc9e3-8159-477a-b3a5-8a36dcf1f8fe\",\"03479c9f-67e4-436b-a9a4-3edec0044c16\"]},{\"id\":\"862cc9e3-8159-477a-b3a5-8a36dcf1f8fe\",\"name\":\"+ release()\",\"type\":\"ClassMethod\",\"owner\":\"de9e72f9-bcdb-4ea9-a50a-e981430dceab\",\"bounds\":{\"x\":630.48828125,\"y\":43.69140625,\"width\":210,\"height\":30}},{\"id\":\"03479c9f-67e4-436b-a9a4-3edec0044c16\",\"name\":\"+ notifyaboutbuildstatus()\",\"type\":\"ClassMethod\",\"owner\":\"de9e72f9-bcdb-4ea9-a50a-e981430dceab\",\"bounds\":{\"x\":630.48828125,\"y\":73.69140625,\"width\":210,\"height\":30}},{\"id\":\"37b2b067-adbf-4cd0-ac5f-124eee400365\",\"name\":\"Continuous Integration Server\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":623.23046875,\"y\":327.7890625,\"width\":250,\"height\":100},\"attributes\":[],\"methods\":[\"32e9e489-59f9-415b-a7bb-4a79eb8aa94f\",\"f48de5f9-c0d5-4f51-9d44-58e812369677\"]},{\"id\":\"32e9e489-59f9-415b-a7bb-4a79eb8aa94f\",\"name\":\"+ upload()\",\"type\":\"ClassMethod\",\"owner\":\"37b2b067-adbf-4cd0-ac5f-124eee400365\",\"bounds\":{\"x\":623.23046875,\"y\":367.7890625,\"width\":250,\"height\":30}},{\"id\":\"f48de5f9-c0d5-4f51-9d44-58e812369677\",\"name\":\"+ build()\",\"type\":\"ClassMethod\",\"owner\":\"37b2b067-adbf-4cd0-ac5f-124eee400365\",\"bounds\":{\"x\":623.23046875,\"y\":397.7890625,\"width\":250,\"height\":30}},{\"id\":\"3b90c8b3-1044-4ca3-892a-ccd709d739e8\",\"name\":\"User\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":941.6640625,\"y\":7.70703125,\"width\":200,\"height\":130},\"attributes\":[],\"methods\":[\"d8dcb7f0-2771-446e-862d-4271ba63280d\",\"2905df3b-0b58-464c-a59e-6842bb8a4492\",\"531ec27f-0426-4acc-991d-ab99d802c0db\"]},{\"id\":\"d8dcb7f0-2771-446e-862d-4271ba63280d\",\"name\":\"+ givefeedback()\",\"type\":\"ClassMethod\",\"owner\":\"3b90c8b3-1044-4ca3-892a-ccd709d739e8\",\"bounds\":{\"x\":941.6640625,\"y\":47.70703125,\"width\":200,\"height\":30}},{\"id\":\"2905df3b-0b58-464c-a59e-6842bb8a4492\",\"name\":\"+ download()\",\"type\":\"ClassMethod\",\"owner\":\"3b90c8b3-1044-4ca3-892a-ccd709d739e8\",\"bounds\":{\"x\":941.6640625,\"y\":77.70703125,\"width\":200,\"height\":30}},{\"id\":\"531ec27f-0426-4acc-991d-ab99d802c0db\",\"name\":\"+ uploadfeedback()\",\"type\":\"ClassMethod\",\"owner\":\"3b90c8b3-1044-4ca3-892a-ccd709d739e8\",\"bounds\":{\"x\":941.6640625,\"y\":107.70703125,\"width\":200,\"height\":30}},{\"id\":\"0be8aeaf-cb02-429f-809f-910103aa47a6\",\"name\":\"Continuous Delivery Server\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":945.6875,\"y\":316.01171875,\"width\":230,\"height\":100},\"attributes\":[],\"methods\":[\"f0fa6931-6070-4427-9b45-6cfeeb1835fe\",\"175fb797-8ba5-4aa9-8970-2d9b08be19f4\"]},{\"id\":\"f0fa6931-6070-4427-9b45-6cfeeb1835fe\",\"name\":\"+ download()\",\"type\":\"ClassMethod\",\"owner\":\"0be8aeaf-cb02-429f-809f-910103aa47a6\",\"bounds\":{\"x\":945.6875,\"y\":356.01171875,\"width\":230,\"height\":30}},{\"id\":\"175fb797-8ba5-4aa9-8970-2d9b08be19f4\",\"name\":\"+ storefeedback()\",\"type\":\"ClassMethod\",\"owner\":\"0be8aeaf-cb02-429f-809f-910103aa47a6\",\"bounds\":{\"x\":945.6875,\"y\":386.01171875,\"width\":230,\"height\":30}}],\"relationships\":[{\"id\":\"9db365eb-bfa1-4647-948d-2e3997716bd0\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":390.60546875,\"y\":190,\"width\":1,\"height\":132.66796875},\"path\":[{\"x\":0,\"y\":0},{\"x\":0,\"y\":132.66796875}],\"source\":{\"direction\":\"Down\",\"element\":\"6022e553-115f-46d2-9500-ec5d8821a2cf\",\"multiplicity\":\"\",\"role\":\"\"},\"target\":{\"direction\":\"Up\",\"element\":\"63dc1e84-96d0-408a-8aa0-77464e477020\",\"multiplicity\":\"\",\"role\":\"\"}},{\"id\":\"0bcb5aed-f231-468b-a6ce-2233e20961af\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":100,\"y\":95,\"width\":194.1953125,\"height\":137.5078125},\"path\":[{\"x\":194.1953125,\"y\":0},{\"x\":0,\"y\":0},{\"x\":0,\"y\":137.5078125}],\"source\":{\"direction\":\"Left\",\"element\":\"6022e553-115f-46d2-9500-ec5d8821a2cf\",\"multiplicity\":\"\",\"role\":\"\"},\"target\":{\"direction\":\"Up\",\"element\":\"23b6e9cb-4e8d-40a9-9cd0-bcdbaded9352\",\"multiplicity\":\"\",\"role\":\"\"}},{\"id\":\"af872f43-af2e-44c8-9d44-0a149de16111\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":494.1953125,\"y\":53.69140625,\"width\":136.29296875,\"height\":1},\"path\":[{\"x\":0,\"y\":0},{\"x\":136.29296875,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"6022e553-115f-46d2-9500-ec5d8821a2cf\",\"multiplicity\":\"\",\"role\":\"\"},\"target\":{\"direction\":\"Left\",\"element\":\"de9e72f9-bcdb-4ea9-a50a-e981430dceab\",\"multiplicity\":\"\",\"role\":\"\"}},{\"id\":\"0aed9b68-f1ff-432a-908e-105bcd5acfa0\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":487.015625,\"y\":377.7890625,\"width\":136.21484375,\"height\":1},\"path\":[{\"x\":0,\"y\":0},{\"x\":136.21484375,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"63dc1e84-96d0-408a-8aa0-77464e477020\",\"multiplicity\":\"\",\"role\":\"\"},\"target\":{\"direction\":\"Left\",\"element\":\"37b2b067-adbf-4cd0-ac5f-124eee400365\",\"multiplicity\":\"\",\"role\":\"\"}},{\"id\":\"f3f4d6e8-8f51-4055-85b5-1e979f0e7535\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":840.48828125,\"y\":55.69921875,\"width\":101.17578125,\"height\":1},\"path\":[{\"x\":0,\"y\":0},{\"x\":101.17578125,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"de9e72f9-bcdb-4ea9-a50a-e981430dceab\",\"multiplicity\":\"\",\"role\":\"\"},\"target\":{\"direction\":\"Left\",\"element\":\"3b90c8b3-1044-4ca3-892a-ccd709d739e8\",\"multiplicity\":\"\",\"role\":\"\"}}],\"assessments\":[]}";

    private static final String classModel2 = "{\"version\":\"2.0.0\",\"type\":\"ClassDiagram\",\"size\":{\"width\":1015.3333740234375,\"height\":540},\"interactive\":{\"elements\":[],\"relationships\":[]},\"elements\":[{\"id\":\"b8ab2312-7916-4cf0-807a-d9bebe552301\",\"name\":\"Developper\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":130,\"y\":10,\"width\":160,\"height\":40},\"attributes\":[],\"methods\":[]},{\"id\":\"9039b173-28a5-4593-96e1-90331470039a\",\"name\":\"Release Manager\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":350,\"y\":0,\"width\":200,\"height\":40},\"attributes\":[],\"methods\":[]},{\"id\":\"eddc0f0b-4f67-4a57-b21b-d7d28afa60dd\",\"name\":\"User\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":850,\"y\":130,\"width\":140,\"height\":40},\"attributes\":[],\"methods\":[]},{\"id\":\"a2de0962-8b2a-4360-9e01-2671cb580937\",\"name\":\"Device\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":800,\"y\":410,\"width\":200,\"height\":40},\"attributes\":[],\"methods\":[]},{\"id\":\"088e3169-56de-4f66-909e-7767d6ee525e\",\"name\":\"Issue Tracker\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":40,\"y\":410,\"width\":150,\"height\":40},\"attributes\":[],\"methods\":[]},{\"id\":\"bd0e9c5c-8ae3-4e63-b8e6-f9ae17c42314\",\"name\":\"VCS\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":140,\"y\":240,\"width\":140,\"height\":40},\"attributes\":[],\"methods\":[]},{\"id\":\"54a47182-bd7b-45e9-a856-efe18aaff72b\",\"name\":\"CIS\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":390,\"y\":200,\"width\":130,\"height\":100},\"attributes\":[],\"methods\":[\"53eac44e-a2a1-4667-bc69-29b642c65e63\",\"8d835f99-85f8-4e02-b91d-8288707de111\"]},{\"id\":\"53eac44e-a2a1-4667-bc69-29b642c65e63\",\"name\":\"compile()\",\"type\":\"ClassMethod\",\"owner\":\"54a47182-bd7b-45e9-a856-efe18aaff72b\",\"bounds\":{\"x\":390,\"y\":240,\"width\":130,\"height\":30}},{\"id\":\"8d835f99-85f8-4e02-b91d-8288707de111\",\"name\":\"testBuild()\",\"type\":\"ClassMethod\",\"owner\":\"54a47182-bd7b-45e9-a856-efe18aaff72b\",\"bounds\":{\"x\":390,\"y\":270,\"width\":130,\"height\":30}},{\"id\":\"6d1e8b83-e236-41f4-b7c8-6e714846cba8\",\"name\":\"CDS\",\"type\":\"Class\",\"owner\":null,\"bounds\":{\"x\":570,\"y\":310,\"width\":130,\"height\":70},\"attributes\":[],\"methods\":[\"09e80e3a-3fe5-46dd-889e-98d0b5b8cc69\"]},{\"id\":\"09e80e3a-3fe5-46dd-889e-98d0b5b8cc69\",\"name\":\"build()\",\"type\":\"ClassMethod\",\"owner\":\"6d1e8b83-e236-41f4-b7c8-6e714846cba8\",\"bounds\":{\"x\":570,\"y\":350,\"width\":130,\"height\":30}}],\"relationships\":[{\"id\":\"cd4e7274-7376-4d82-b040-0412fad9b8ce\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":455,\"y\":40,\"width\":1,\"height\":160},\"path\":[{\"x\":0,\"y\":0},{\"x\":0,\"y\":160}],\"source\":{\"direction\":\"Down\",\"element\":\"9039b173-28a5-4593-96e1-90331470039a\",\"multiplicity\":\"1\",\"role\":\"\"},\"target\":{\"direction\":\"Up\",\"element\":\"54a47182-bd7b-45e9-a856-efe18aaff72b\",\"multiplicity\":\"1..*\",\"role\":\"release()\"}},{\"id\":\"165c9763-8c50-429a-8fb8-ff36c8577973\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":700,\"y\":345,\"width\":100,\"height\":85},\"path\":[{\"x\":100,\"y\":85},{\"x\":50,\"y\":85},{\"x\":50,\"y\":0},{\"x\":0,\"y\":0}],\"source\":{\"direction\":\"Left\",\"element\":\"a2de0962-8b2a-4360-9e01-2671cb580937\",\"multiplicity\":\"1..*\",\"role\":\"download\"},\"target\":{\"direction\":\"Right\",\"element\":\"6d1e8b83-e236-41f4-b7c8-6e714846cba8\",\"multiplicity\":\"1\",\"role\":\"uploadFeedback\"}},{\"id\":\"20e9fef0-fcbe-4ba5-87f4-80d1e5a8dba5\",\"name\":\"\",\"type\":\"ClassAggregation\",\"owner\":null,\"bounds\":{\"x\":920,\"y\":170,\"width\":1,\"height\":240},\"path\":[{\"x\":0,\"y\":240},{\"x\":0,\"y\":0}],\"source\":{\"direction\":\"Up\",\"element\":\"a2de0962-8b2a-4360-9e01-2671cb580937\",\"multiplicity\":\"1..*\",\"role\":\"giveFeedback()\"},\"target\":{\"direction\":\"Down\",\"element\":\"eddc0f0b-4f67-4a57-b21b-d7d28afa60dd\",\"multiplicity\":\"1..*\",\"role\":\"\"}},{\"id\":\"23335b13-d193-45b7-a119-8a1ccd3a9679\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":635,\"y\":150,\"width\":215,\"height\":160},\"path\":[{\"x\":0,\"y\":160},{\"x\":0,\"y\":0},{\"x\":215,\"y\":0}],\"source\":{\"direction\":\"Up\",\"element\":\"6d1e8b83-e236-41f4-b7c8-6e714846cba8\",\"multiplicity\":\"1\",\"role\":\"\"},\"target\":{\"direction\":\"Left\",\"element\":\"eddc0f0b-4f67-4a57-b21b-d7d28afa60dd\",\"multiplicity\":\"1..*\",\"role\":\"notifyAboutRelease()\"}},{\"id\":\"3e7f7571-28b9-4b3a-a7ff-70204cb386a1\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":455,\"y\":300,\"width\":115,\"height\":45},\"path\":[{\"x\":0,\"y\":0},{\"x\":0,\"y\":45},{\"x\":115,\"y\":45}],\"source\":{\"direction\":\"Down\",\"element\":\"54a47182-bd7b-45e9-a856-efe18aaff72b\",\"multiplicity\":\"1\",\"role\":\"\"},\"target\":{\"direction\":\"Left\",\"element\":\"6d1e8b83-e236-41f4-b7c8-6e714846cba8\",\"multiplicity\":\"1..*\",\"role\":\"upload()\"}},{\"id\":\"d1d3f615-7a1f-4a95-8de7-60d87ecf2415\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":280,\"y\":260,\"width\":110,\"height\":1},\"path\":[{\"x\":0,\"y\":0},{\"x\":110,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"bd0e9c5c-8ae3-4e63-b8e6-f9ae17c42314\",\"multiplicity\":\"1\",\"role\":\"\"},\"target\":{\"direction\":\"Left\",\"element\":\"54a47182-bd7b-45e9-a856-efe18aaff72b\",\"multiplicity\":\"1..*\",\"role\":\"checkout()\"}},{\"id\":\"6a3a44d7-af35-4146-a7d2-e2a59ed133e5\",\"name\":\"\",\"type\":\"ClassAggregation\",\"owner\":null,\"bounds\":{\"x\":210,\"y\":50,\"width\":1,\"height\":190},\"path\":[{\"x\":0,\"y\":0},{\"x\":0,\"y\":190}],\"source\":{\"direction\":\"Down\",\"element\":\"b8ab2312-7916-4cf0-807a-d9bebe552301\",\"multiplicity\":\"1..*\",\"role\":\"\"},\"target\":{\"direction\":\"Up\",\"element\":\"bd0e9c5c-8ae3-4e63-b8e6-f9ae17c42314\",\"multiplicity\":\"1\",\"role\":\"commit()\"}},{\"id\":\"957aeac6-2401-4d74-acf4-75a1db2af606\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":0,\"y\":30,\"width\":130,\"height\":400},\"path\":[{\"x\":40,\"y\":400},{\"x\":0,\"y\":400},{\"x\":0,\"y\":0},{\"x\":130,\"y\":0}],\"source\":{\"direction\":\"Left\",\"element\":\"088e3169-56de-4f66-909e-7767d6ee525e\",\"multiplicity\":\"1\",\"role\":\"\"},\"target\":{\"direction\":\"Left\",\"element\":\"b8ab2312-7916-4cf0-807a-d9bebe552301\",\"multiplicity\":\"1..*\",\"role\":\"notifyAboutIssue()\"}},{\"id\":\"e52181da-6b51-419b-a02e-302ea1de2b40\",\"name\":\"\",\"type\":\"ClassBidirectional\",\"owner\":null,\"bounds\":{\"x\":190,\"y\":380,\"width\":445,\"height\":50},\"path\":[{\"x\":445,\"y\":0},{\"x\":445,\"y\":50},{\"x\":0,\"y\":50}],\"source\":{\"direction\":\"Down\",\"element\":\"6d1e8b83-e236-41f4-b7c8-6e714846cba8\",\"multiplicity\":\"1\",\"role\":\"\"},\"target\":{\"direction\":\"Right\",\"element\":\"088e3169-56de-4f66-909e-7767d6ee525e\",\"multiplicity\":\"1\",\"role\":\"storeFeedBackAsIssue()\"}}],\"assessments\":[]}";

    @Mock
    UMLClass umlClass1;

    @Mock
    UMLClass umlClass2;

    @Mock
    UMLClass umlClass3;

    @Mock
    UMLRelationship umlRelationship1;

    @Mock
    UMLRelationship umlRelationship2;

    @Mock
    UMLRelationship umlRelationship3;

    @Mock
    UMLPackage umlPackage1;

    @Mock
    UMLPackage umlPackage2;

    @Mock
    UMLPackage umlPackage3;

    @Mock
    UMLAttribute umlAttribute;

    @Mock
    UMLMethod umlMethod;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        classDiagram = new UMLClassDiagram(123456789, List.of(umlClass1, umlClass2, umlClass3), List.of(umlRelationship1, umlRelationship2, umlRelationship3),
                List.of(umlPackage1, umlPackage2, umlPackage3));

        when(umlClass1.getElementByJSONID("class1")).thenReturn(umlClass1);
        when(umlClass2.getElementByJSONID("class2")).thenReturn(umlClass2);
        when(umlClass3.getElementByJSONID("class3")).thenReturn(umlClass3);
        when(umlClass1.getElementByJSONID("attribute")).thenReturn(umlAttribute);
        when(umlClass3.getElementByJSONID("method")).thenReturn(umlMethod);
        when(umlRelationship1.getJSONElementID()).thenReturn("relationship1");
        when(umlRelationship2.getJSONElementID()).thenReturn("relationship2");
        when(umlRelationship3.getJSONElementID()).thenReturn("relationship3");
        when(umlPackage1.getJSONElementID()).thenReturn("package1");
        when(umlPackage2.getJSONElementID()).thenReturn("package2");
        when(umlPackage3.getJSONElementID()).thenReturn("package3");
    }

    @Test
    void getElementByJSONID_null() {
        UMLElement element = classDiagram.getElementByJSONID(null);

        assertThat(element).isNull();
    }

    @Test
    void getElementByJSONID_emptyString() {
        UMLElement element = classDiagram.getElementByJSONID("");

        assertThat(element).isNull();
    }

    @Test
    void getElementByJSONID_getClass() {
        UMLElement element = classDiagram.getElementByJSONID("class1");

        assertThat(element).isEqualTo(umlClass1);
    }

    @Test
    void getElementByJSONID_getAttribute() {
        UMLElement element = classDiagram.getElementByJSONID("attribute");

        assertThat(element).isEqualTo(umlAttribute);
    }

    @Test
    void getElementByJSONID_getMethod() {
        UMLElement element = classDiagram.getElementByJSONID("method");

        assertThat(element).isEqualTo(umlMethod);
    }

    @Test
    void getElementByJSONID_getRelationship() {
        UMLElement element = classDiagram.getElementByJSONID("relationship2");

        assertThat(element).isEqualTo(umlRelationship2);
    }

    @Test
    void getElementByJSONID_getPackage() {
        UMLElement element = classDiagram.getElementByJSONID("package3");

        assertThat(element).isEqualTo(umlPackage3);
    }

    @Test
    void getElementByJSONID_notExisting() {
        UMLElement element = classDiagram.getElementByJSONID("nonExistingElement");

        assertThat(element).isNull();
    }

    @Test
    void getModelElements() {
        List<UMLElement> elementList = classDiagram.getModelElements();

        assertThat(elementList).containsExactlyInAnyOrder(umlClass1, umlClass2, umlClass3, umlRelationship1, umlRelationship2, umlRelationship3, umlPackage1, umlPackage2,
                umlPackage3);
    }

    @Test
    void getModelElements_emptyElementLists() {
        classDiagram = new UMLClassDiagram(987654321, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        List<UMLElement> elementList = classDiagram.getModelElements();

        assertThat(elementList).isEmpty();
    }

    @Test
    void similarityClassDiagram_EqualModels() {
        compareSubmissions(new ModelingSubmission().model(classModel1), new ModelingSubmission().model(classModel1), 0.8, 1.0);
        compareSubmissions(new ModelingSubmission().model(classModel2), new ModelingSubmission().model(classModel2), 0.8, 1.0);
    }

    @Test
    void similarityClassDiagram_DifferentModels() {
        compareSubmissions(new ModelingSubmission().model(classModel1), new ModelingSubmission().model(classModel2), 0.0, 0.3095);
    }
}
