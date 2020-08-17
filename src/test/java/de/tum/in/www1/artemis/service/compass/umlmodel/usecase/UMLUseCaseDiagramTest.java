package de.tum.in.www1.artemis.service.compass.umlmodel.usecase;

import static com.google.gson.JsonParser.parseString;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.compass.controller.UMLModelParser;
import de.tum.in.www1.artemis.service.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;

public class UMLUseCaseDiagramTest extends AbstractUMLDiagramTest {

    private static final String useCaseModel1 = "{\"version\":\"2.0.0\",\"type\":\"UseCaseDiagram\",\"size\":{\"width\":1260,\"height\":740},\"interactive\":{\"elements\":[],\"relationships\":[]},\"elements\":[{\"id\":\"6fb20a32-3865-45d0-b05f-9e79129b85fa\",\"name\":\"Player\",\"type\":\"UseCaseActor\",\"owner\":null,\"bounds\":{\"x\":20,\"y\":330,\"width\":90,\"height\":140}},{\"id\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"name\":\"Space Invader\",\"type\":\"UseCaseSystem\",\"owner\":null,\"bounds\":{\"x\":150,\"y\":0,\"width\":1100,\"height\":734.6666564941406}},{\"id\":\"54748e19-2632-45ae-aa92-8949dba0ddc1\",\"name\":\"Start Game\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":150,\"y\":180,\"width\":180,\"height\":80}},{\"id\":\"2934a724-ae22-466e-a978-5071d3adaa7c\",\"name\":\"shoot bullet\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":610,\"y\":360.5714416503906,\"width\":140,\"height\":70}},{\"id\":\"5f859a27-07a9-420e-9556-0785f518f8e1\",\"name\":\"Show scores\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":190,\"y\":630,\"width\":170,\"height\":70}},{\"id\":\"f3c1d184-4f82-4b01-b589-b573654b8196\",\"name\":\"steer spacecraft\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":280,\"y\":410,\"width\":150,\"height\":70}},{\"id\":\"19d7e558-66c3-4ce3-a9cd-ecf4bb925070\",\"name\":\"play music\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":770,\"y\":8,\"width\":140,\"height\":70}},{\"id\":\"e2cab966-1a5d-4ab8-8abd-9749b0eb18ee\",\"name\":\"Stop game\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":400,\"y\":596.6666564941406,\"width\":160,\"height\":70}},{\"id\":\"bd0f0228-b645-4363-8d12-906c30ea71d0\",\"name\":\"Stop music\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":800,\"y\":630,\"width\":150,\"height\":60}},{\"id\":\"4fedcf9d-1ae4-4c62-ac7d-812f9e87b904\",\"name\":\"move aliens\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":600,\"y\":220,\"width\":150,\"height\":70}},{\"id\":\"21189e15-cdb9-43da-b137-3ad301daec46\",\"name\":\"evalucate collision of bullet\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":860,\"y\":360,\"width\":230,\"height\":100}},{\"id\":\"ff58f909-2ede-45b0-80b3-a3f91f79af1c\",\"name\":\"destroy aliens\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":1060,\"y\":220,\"width\":170,\"height\":80}},{\"id\":\"c1c18c19-7722-4c4b-88bf-78689353cadb\",\"name\":\"destroy player\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":700,\"y\":470,\"width\":160,\"height\":70}},{\"id\":\"8046dc52-9ab7-4a66-bae3-811aaf67c420\",\"name\":\"change score\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":1040,\"y\":40,\"width\":150,\"height\":60}},{\"id\":\"17b938b8-4af6-4386-b57c-97153258e879\",\"name\":\"Get Date Time\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":210,\"y\":11.333343505859375,\"width\":170,\"height\":70}},{\"id\":\"f5e70c31-7b2d-43c0-a1bd-2da14c90d04a\",\"name\":\"derive assertions\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":430,\"y\":513.3333129882812,\"width\":150,\"height\":70}},{\"id\":\"330c404e-d015-47c6-80b1-718348710260\",\"name\":\"buy weapons\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":290,\"y\":298,\"width\":150,\"height\":60}},{\"id\":\"224dfeaf-38ee-4e5e-95a3-dad1da39f290\",\"name\":\"draw aliens\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":660,\"y\":83.33334350585938,\"width\":150,\"height\":60}},{\"id\":\"38b415cd-42f6-4341-bb8f-c6ffc555602e\",\"name\":\"set level\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":430,\"y\":163.33334350585938,\"width\":140,\"height\":60}},{\"id\":\"8de934ec-d7e1-483d-85b4-a692297e62c9\",\"name\":\"finish level\",\"type\":\"UseCase\",\"owner\":\"5256c951-2e53-4953-a9cc-b7f5ddd35d7c\",\"bounds\":{\"x\":810,\"y\":191.33334350585938,\"width\":150,\"height\":70}},{\"id\":\"a16e425f-16fb-43c4-8253-f692d1250701\",\"name\":\"Environment\",\"type\":\"UseCaseActor\",\"owner\":null,\"bounds\":{\"x\":0,\"y\":64.66665649414062,\"width\":90,\"height\":140}}],\"relationships\":[{\"id\":\"7650c4e9-0d98-43a4-b0e3-46de32e6d3ee\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":110,\"y\":220,\"width\":40,\"height\":180},\"path\":[{\"x\":0,\"y\":180},{\"x\":40,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"6fb20a32-3865-45d0-b05f-9e79129b85fa\"},\"target\":{\"direction\":\"Left\",\"element\":\"54748e19-2632-45ae-aa92-8949dba0ddc1\"}},{\"id\":\"821b05c4-dfa6-4425-84c7-a5e847a733c8\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":330,\"y\":220,\"width\":280,\"height\":175.57144165039062},\"path\":[{\"x\":0,\"y\":0},{\"x\":280,\"y\":175.57144165039062}],\"source\":{\"direction\":\"Right\",\"element\":\"54748e19-2632-45ae-aa92-8949dba0ddc1\"},\"target\":{\"direction\":\"Left\",\"element\":\"2934a724-ae22-466e-a978-5071d3adaa7c\"}},{\"id\":\"2a8851f2-16ba-4dc3-ba4c-3646c45e7569\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":110,\"y\":400,\"width\":80,\"height\":265},\"path\":[{\"x\":0,\"y\":0},{\"x\":80,\"y\":265}],\"source\":{\"direction\":\"Right\",\"element\":\"6fb20a32-3865-45d0-b05f-9e79129b85fa\"},\"target\":{\"direction\":\"Left\",\"element\":\"5f859a27-07a9-420e-9556-0785f518f8e1\"}},{\"id\":\"960a4085-b46d-4177-81fe-dbfd6dc57c59\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":240,\"y\":43,\"width\":530,\"height\":137},\"path\":[{\"x\":0,\"y\":137},{\"x\":530,\"y\":0}],\"source\":{\"direction\":\"Up\",\"element\":\"54748e19-2632-45ae-aa92-8949dba0ddc1\"},\"target\":{\"direction\":\"Left\",\"element\":\"19d7e558-66c3-4ce3-a9cd-ecf4bb925070\"}},{\"id\":\"842cb422-459b-408f-887a-5ffbd3fb3db0\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":110,\"y\":400,\"width\":290,\"height\":231.66665649414062},\"path\":[{\"x\":0,\"y\":0},{\"x\":290,\"y\":231.66665649414062}],\"source\":{\"direction\":\"Right\",\"element\":\"6fb20a32-3865-45d0-b05f-9e79129b85fa\"},\"target\":{\"direction\":\"Left\",\"element\":\"e2cab966-1a5d-4ab8-8abd-9749b0eb18ee\"}},{\"id\":\"913ef42d-7cb9-451d-b51b-79638ec3ed64\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":560,\"y\":631.6666564941406,\"width\":240,\"height\":28.333343505859375},\"path\":[{\"x\":0,\"y\":0},{\"x\":240,\"y\":28.333343505859375}],\"source\":{\"direction\":\"Right\",\"element\":\"e2cab966-1a5d-4ab8-8abd-9749b0eb18ee\"},\"target\":{\"direction\":\"Left\",\"element\":\"bd0f0228-b645-4363-8d12-906c30ea71d0\"}},{\"id\":\"5844dba8-8c8e-477d-95d9-1d1f57ef8310\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":110,\"y\":400,\"width\":170,\"height\":45},\"path\":[{\"x\":0,\"y\":0},{\"x\":170,\"y\":45}],\"source\":{\"direction\":\"Right\",\"element\":\"6fb20a32-3865-45d0-b05f-9e79129b85fa\"},\"target\":{\"direction\":\"Left\",\"element\":\"f3c1d184-4f82-4b01-b589-b573654b8196\"}},{\"id\":\"6889ee90-38f8-4f74-8f6d-0ad863917133\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":750,\"y\":395.5714416503906,\"width\":110,\"height\":14.428558349609375},\"path\":[{\"x\":0,\"y\":0},{\"x\":110,\"y\":14.428558349609375}],\"source\":{\"direction\":\"Right\",\"element\":\"2934a724-ae22-466e-a978-5071d3adaa7c\"},\"target\":{\"direction\":\"Left\",\"element\":\"21189e15-cdb9-43da-b137-3ad301daec46\"}},{\"id\":\"fa6f9fab-3bb7-4f8d-b7fb-bbf1d0f4fb15\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":675,\"y\":290,\"width\":5,\"height\":70.57144165039062},\"path\":[{\"x\":0,\"y\":0},{\"x\":5,\"y\":70.57144165039062}],\"source\":{\"direction\":\"Down\",\"element\":\"4fedcf9d-1ae4-4c62-ac7d-812f9e87b904\"},\"target\":{\"direction\":\"Up\",\"element\":\"2934a724-ae22-466e-a978-5071d3adaa7c\"}},{\"id\":\"134ac8ef-6493-4ba7-a21c-4fece633b234\",\"name\":\"\",\"type\":\"UseCaseExtend\",\"owner\":null,\"bounds\":{\"x\":975,\"y\":260,\"width\":85,\"height\":100},\"path\":[{\"x\":85,\"y\":0},{\"x\":0,\"y\":100}],\"source\":{\"direction\":\"Left\",\"element\":\"ff58f909-2ede-45b0-80b3-a3f91f79af1c\"},\"target\":{\"direction\":\"Up\",\"element\":\"21189e15-cdb9-43da-b137-3ad301daec46\"}},{\"id\":\"57869fb7-ea15-40a7-9f50-e75d3a6c9b15\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":560,\"y\":540,\"width\":220,\"height\":91.66665649414062},\"path\":[{\"x\":220,\"y\":0},{\"x\":0,\"y\":91.66665649414062}],\"source\":{\"direction\":\"Down\",\"element\":\"c1c18c19-7722-4c4b-88bf-78689353cadb\"},\"target\":{\"direction\":\"Right\",\"element\":\"e2cab966-1a5d-4ab8-8abd-9749b0eb18ee\"}},{\"id\":\"9ad738a5-2e2b-40e9-b1b9-044308c65c72\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":910,\"y\":43,\"width\":150,\"height\":217},\"path\":[{\"x\":150,\"y\":217},{\"x\":0,\"y\":0}],\"source\":{\"direction\":\"Left\",\"element\":\"ff58f909-2ede-45b0-80b3-a3f91f79af1c\"},\"target\":{\"direction\":\"Right\",\"element\":\"19d7e558-66c3-4ce3-a9cd-ecf4bb925070\"}},{\"id\":\"6cea1af8-439f-466d-a7fb-6b62b09520fa\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":780,\"y\":78,\"width\":60,\"height\":392},\"path\":[{\"x\":0,\"y\":392},{\"x\":60,\"y\":0}],\"source\":{\"direction\":\"Up\",\"element\":\"c1c18c19-7722-4c4b-88bf-78689353cadb\"},\"target\":{\"direction\":\"Down\",\"element\":\"19d7e558-66c3-4ce3-a9cd-ecf4bb925070\"}},{\"id\":\"788d108e-ed0a-4871-b6da-c3dd10a2bdf1\",\"name\":\"\",\"type\":\"UseCaseExtend\",\"owner\":null,\"bounds\":{\"x\":860,\"y\":460,\"width\":115,\"height\":45},\"path\":[{\"x\":0,\"y\":45},{\"x\":115,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"c1c18c19-7722-4c4b-88bf-78689353cadb\"},\"target\":{\"direction\":\"Down\",\"element\":\"21189e15-cdb9-43da-b137-3ad301daec46\"}},{\"id\":\"33b5be7b-529f-4ef1-bfa9-6ae1fbeac393\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":680,\"y\":78,\"width\":160,\"height\":282.5714416503906},\"path\":[{\"x\":0,\"y\":282.5714416503906},{\"x\":160,\"y\":0}],\"source\":{\"direction\":\"Up\",\"element\":\"2934a724-ae22-466e-a978-5071d3adaa7c\"},\"target\":{\"direction\":\"Down\",\"element\":\"19d7e558-66c3-4ce3-a9cd-ecf4bb925070\"}},{\"id\":\"2ba8fdb8-1850-4048-ac66-af11fa447b24\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":1145,\"y\":70,\"width\":45,\"height\":150},\"path\":[{\"x\":0,\"y\":150},{\"x\":45,\"y\":0}],\"source\":{\"direction\":\"Up\",\"element\":\"ff58f909-2ede-45b0-80b3-a3f91f79af1c\"},\"target\":{\"direction\":\"Right\",\"element\":\"8046dc52-9ab7-4a66-bae3-811aaf67c420\"}},{\"id\":\"4ff8f7d2-a614-4707-a64f-c9c9a4400212\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":110,\"y\":395.5714416503906,\"width\":500,\"height\":4.428558349609375},\"path\":[{\"x\":0,\"y\":4.428558349609375},{\"x\":500,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"6fb20a32-3865-45d0-b05f-9e79129b85fa\"},\"target\":{\"direction\":\"Left\",\"element\":\"2934a724-ae22-466e-a978-5071d3adaa7c\"}},{\"id\":\"41f1c117-baaf-4517-81da-9d4d5f0dedae\",\"name\":\"\",\"type\":\"UseCaseExtend\",\"owner\":null,\"bounds\":{\"x\":430,\"y\":395.5714416503906,\"width\":180,\"height\":49.428558349609375},\"path\":[{\"x\":180,\"y\":0},{\"x\":0,\"y\":49.428558349609375}],\"source\":{\"direction\":\"Left\",\"element\":\"2934a724-ae22-466e-a978-5071d3adaa7c\"},\"target\":{\"direction\":\"Right\",\"element\":\"f3c1d184-4f82-4b01-b589-b573654b8196\"}},{\"id\":\"2c24b04c-1ee7-4545-8eb1-0a2a7ffd953a\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":90,\"y\":46.333343505859375,\"width\":120,\"height\":88.33331298828125},\"path\":[{\"x\":120,\"y\":0},{\"x\":0,\"y\":88.33331298828125}],\"source\":{\"direction\":\"Left\",\"element\":\"17b938b8-4af6-4386-b57c-97153258e879\"},\"target\":{\"direction\":\"Right\",\"element\":\"a16e425f-16fb-43c4-8253-f692d1250701\"}},{\"id\":\"98872e9c-7b7b-4165-9ecd-da567c925b43\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":240,\"y\":81.33334350585938,\"width\":55,\"height\":98.66665649414062},\"path\":[{\"x\":0,\"y\":98.66665649414062},{\"x\":55,\"y\":0}],\"source\":{\"direction\":\"Up\",\"element\":\"54748e19-2632-45ae-aa92-8949dba0ddc1\"},\"target\":{\"direction\":\"Down\",\"element\":\"17b938b8-4af6-4386-b57c-97153258e879\"}},{\"id\":\"beee365d-f111-49f2-be43-4ac1c845506e\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":110,\"y\":400,\"width\":320,\"height\":148.33331298828125},\"path\":[{\"x\":320,\"y\":148.33331298828125},{\"x\":0,\"y\":0}],\"source\":{\"direction\":\"Left\",\"element\":\"f5e70c31-7b2d-43c0-a1bd-2da14c90d04a\"},\"target\":{\"direction\":\"Right\",\"element\":\"6fb20a32-3865-45d0-b05f-9e79129b85fa\"}},{\"id\":\"dcdbfcb5-ef68-4cf3-abe3-6ade9461eeab\",\"name\":\"\",\"type\":\"UseCaseExtend\",\"owner\":null,\"bounds\":{\"x\":355,\"y\":480,\"width\":150,\"height\":33.33331298828125},\"path\":[{\"x\":150,\"y\":33.33331298828125},{\"x\":0,\"y\":0}],\"source\":{\"direction\":\"Up\",\"element\":\"f5e70c31-7b2d-43c0-a1bd-2da14c90d04a\"},\"target\":{\"direction\":\"Down\",\"element\":\"f3c1d184-4f82-4b01-b589-b573654b8196\"}},{\"id\":\"59cdd8fd-f043-4746-b6b6-654b2c5e8b65\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":110,\"y\":328,\"width\":180,\"height\":72},\"path\":[{\"x\":180,\"y\":0},{\"x\":0,\"y\":72}],\"source\":{\"direction\":\"Left\",\"element\":\"330c404e-d015-47c6-80b1-718348710260\"},\"target\":{\"direction\":\"Right\",\"element\":\"6fb20a32-3865-45d0-b05f-9e79129b85fa\"}},{\"id\":\"5a27e46a-e6be-4313-b8a6-0065df684308\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":600,\"y\":143.33334350585938,\"width\":135,\"height\":111.66665649414062},\"path\":[{\"x\":135,\"y\":0},{\"x\":0,\"y\":111.66665649414062}],\"source\":{\"direction\":\"Down\",\"element\":\"224dfeaf-38ee-4e5e-95a3-dad1da39f290\"},\"target\":{\"direction\":\"Left\",\"element\":\"4fedcf9d-1ae4-4c62-ac7d-812f9e87b904\"}},{\"id\":\"ddcabfad-b5c1-416f-8fd0-d85efe2fb739\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":330,\"y\":193.33334350585938,\"width\":100,\"height\":26.666656494140625},\"path\":[{\"x\":0,\"y\":26.666656494140625},{\"x\":100,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"54748e19-2632-45ae-aa92-8949dba0ddc1\"},\"target\":{\"direction\":\"Left\",\"element\":\"38b415cd-42f6-4341-bb8f-c6ffc555602e\"}},{\"id\":\"772bb21d-7dc8-4f6a-b72b-99dc3790506d\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":570,\"y\":113.33334350585938,\"width\":90,\"height\":80},\"path\":[{\"x\":0,\"y\":80},{\"x\":90,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"38b415cd-42f6-4341-bb8f-c6ffc555602e\"},\"target\":{\"direction\":\"Left\",\"element\":\"224dfeaf-38ee-4e5e-95a3-dad1da39f290\"}},{\"id\":\"201fa4e6-8982-4a04-81a8-3de52be3d6f7\",\"name\":\"\",\"type\":\"UseCaseExtend\",\"owner\":null,\"bounds\":{\"x\":960,\"y\":226.33334350585938,\"width\":100,\"height\":33.666656494140625},\"path\":[{\"x\":0,\"y\":0},{\"x\":100,\"y\":33.666656494140625}],\"source\":{\"direction\":\"Right\",\"element\":\"8de934ec-d7e1-483d-85b4-a692297e62c9\"},\"target\":{\"direction\":\"Left\",\"element\":\"ff58f909-2ede-45b0-80b3-a3f91f79af1c\"}},{\"id\":\"a1da243b-0aca-4f7a-bfc8-27f00fe5260d\",\"name\":\"\",\"type\":\"UseCaseExtend\",\"owner\":null,\"bounds\":{\"x\":440,\"y\":226.33334350585938,\"width\":370,\"height\":101.66665649414062},\"path\":[{\"x\":0,\"y\":101.66665649414062},{\"x\":370,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"330c404e-d015-47c6-80b1-718348710260\"},\"target\":{\"direction\":\"Left\",\"element\":\"8de934ec-d7e1-483d-85b4-a692297e62c9\"}},{\"id\":\"aa34b9c8-35bc-465b-82c6-05e1a2ac2783\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":570,\"y\":191.33334350585938,\"width\":315,\"height\":2},\"path\":[{\"x\":315,\"y\":0},{\"x\":0,\"y\":2}],\"source\":{\"direction\":\"Up\",\"element\":\"8de934ec-d7e1-483d-85b4-a692297e62c9\"},\"target\":{\"direction\":\"Right\",\"element\":\"38b415cd-42f6-4341-bb8f-c6ffc555602e\"}}],\"assessments\":[]}";

    private static final String useCaseModel2 = "{\"version\":\"2.0.0\",\"type\":\"UseCaseDiagram\",\"size\":{\"width\":1440,\"height\":660},\"interactive\":{\"elements\":[],\"relationships\":[]},\"elements\":[{\"id\":\"559c80d8-5778-4c65-a57e-a0a7980404ed\",\"name\":\"Student\",\"type\":\"UseCaseActor\",\"owner\":null,\"bounds\":{\"x\":0,\"y\":220,\"width\":90,\"height\":140}},{\"id\":\"abad6b17-9995-428a-bb09-dec355bc4562\",\"name\":\"Lecturer\",\"type\":\"UseCaseActor\",\"owner\":null,\"bounds\":{\"x\":1340,\"y\":230,\"width\":90,\"height\":140}},{\"id\":\"ff4f9689-b3e9-4e30-921d-5e9818386fea\",\"name\":\"TUM Space Invaders\",\"type\":\"UseCaseSystem\",\"owner\":null,\"bounds\":{\"x\":210,\"y\":0,\"width\":1030,\"height\":650}},{\"id\":\"70eeef63-576a-4c39-af6d-1962e01d4ca9\",\"name\":\"Generate Session ID\",\"type\":\"UseCase\",\"owner\":\"ff4f9689-b3e9-4e30-921d-5e9818386fea\",\"bounds\":{\"x\":610,\"y\":78,\"width\":200,\"height\":100}},{\"id\":\"29ce4584-4edf-4053-870d-cc7d801586fb\",\"name\":\"Get statistics\",\"type\":\"UseCase\",\"owner\":\"ff4f9689-b3e9-4e30-921d-5e9818386fea\",\"bounds\":{\"x\":970,\"y\":478,\"width\":200,\"height\":100}},{\"id\":\"4fca8be4-e9e0-4b7c-9424-8bf0b984320f\",\"name\":\"Move character\",\"type\":\"UseCase\",\"owner\":\"ff4f9689-b3e9-4e30-921d-5e9818386fea\",\"bounds\":{\"x\":260,\"y\":259,\"width\":200,\"height\":100}},{\"id\":\"67f8af32-d803-4b36-b69c-bd0bb7b65207\",\"name\":\"Shoot\",\"type\":\"UseCase\",\"owner\":\"ff4f9689-b3e9-4e30-921d-5e9818386fea\",\"bounds\":{\"x\":280,\"y\":400,\"width\":200,\"height\":100}},{\"id\":\"f0c4bf00-fbf6-4e0c-bba3-fda9f09e89a9\",\"name\":\"Check the game score\",\"type\":\"UseCase\",\"owner\":\"ff4f9689-b3e9-4e30-921d-5e9818386fea\",\"bounds\":{\"x\":310,\"y\":530,\"width\":200,\"height\":100}},{\"id\":\"f65dce1d-4699-447a-a207-8498ce73b855\",\"name\":\"Start game\",\"type\":\"UseCase\",\"owner\":\"ff4f9689-b3e9-4e30-921d-5e9818386fea\",\"bounds\":{\"x\":260,\"y\":104,\"width\":200,\"height\":100}},{\"id\":\"d780c3aa-5802-4e44-a699-f8ae590f1cf6\",\"name\":\"Enter Session ID\",\"type\":\"UseCase\",\"owner\":\"ff4f9689-b3e9-4e30-921d-5e9818386fea\",\"bounds\":{\"x\":600,\"y\":214,\"width\":200,\"height\":100}},{\"id\":\"1e38e7bf-cda6-47b8-ba78-3a50a88e279b\",\"name\":\"RemoveEnemy\",\"type\":\"UseCase\",\"owner\":\"ff4f9689-b3e9-4e30-921d-5e9818386fea\",\"bounds\":{\"x\":610,\"y\":470,\"width\":200,\"height\":100}},{\"id\":\"f4c6a960-01f4-44ef-807d-c1ce19ede751\",\"name\":\"Register Lecture\",\"type\":\"UseCase\",\"owner\":\"ff4f9689-b3e9-4e30-921d-5e9818386fea\",\"bounds\":{\"x\":990,\"y\":190,\"width\":200,\"height\":100}}],\"relationships\":[{\"id\":\"6a392c73-2491-448c-8768-38081e23890b\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":460,\"y\":154,\"width\":140,\"height\":110},\"path\":[{\"x\":0,\"y\":0},{\"x\":140,\"y\":110}],\"source\":{\"direction\":\"Right\",\"element\":\"f65dce1d-4699-447a-a207-8498ce73b855\"},\"target\":{\"direction\":\"Left\",\"element\":\"d780c3aa-5802-4e44-a699-f8ae590f1cf6\"}},{\"id\":\"88810c71-c60f-485b-b2f1-99b09ee0764d\",\"name\":\"\",\"type\":\"UseCaseExtend\",\"owner\":null,\"bounds\":{\"x\":480,\"y\":450,\"width\":130,\"height\":70},\"path\":[{\"x\":130,\"y\":70},{\"x\":0,\"y\":0}],\"source\":{\"direction\":\"Left\",\"element\":\"1e38e7bf-cda6-47b8-ba78-3a50a88e279b\"},\"target\":{\"direction\":\"Right\",\"element\":\"67f8af32-d803-4b36-b69c-bd0bb7b65207\"}},{\"id\":\"f84c7d48-a98f-4667-83be-76aded95df10\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":90,\"y\":290,\"width\":170,\"height\":19},\"path\":[{\"x\":170,\"y\":19},{\"x\":0,\"y\":0}],\"source\":{\"direction\":\"Left\",\"element\":\"4fca8be4-e9e0-4b7c-9424-8bf0b984320f\"},\"target\":{\"direction\":\"Right\",\"element\":\"559c80d8-5778-4c65-a57e-a0a7980404ed\"}},{\"id\":\"6e361cd3-5118-459e-998a-49441ce25f7d\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":90,\"y\":290,\"width\":190,\"height\":160},\"path\":[{\"x\":190,\"y\":160},{\"x\":0,\"y\":0}],\"source\":{\"direction\":\"Left\",\"element\":\"67f8af32-d803-4b36-b69c-bd0bb7b65207\"},\"target\":{\"direction\":\"Right\",\"element\":\"559c80d8-5778-4c65-a57e-a0a7980404ed\"}},{\"id\":\"6f5fc10b-84c6-4e13-af62-c1ece1b01d83\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":90,\"y\":290,\"width\":220,\"height\":290},\"path\":[{\"x\":220,\"y\":290},{\"x\":0,\"y\":0}],\"source\":{\"direction\":\"Left\",\"element\":\"f0c4bf00-fbf6-4e0c-bba3-fda9f09e89a9\"},\"target\":{\"direction\":\"Right\",\"element\":\"559c80d8-5778-4c65-a57e-a0a7980404ed\"}},{\"id\":\"b87c5356-4fc7-4ac5-8bf7-6e6c0d7ac9a7\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":90,\"y\":154,\"width\":170,\"height\":136},\"path\":[{\"x\":170,\"y\":0},{\"x\":0,\"y\":136}],\"source\":{\"direction\":\"Left\",\"element\":\"f65dce1d-4699-447a-a207-8498ce73b855\"},\"target\":{\"direction\":\"Right\",\"element\":\"559c80d8-5778-4c65-a57e-a0a7980404ed\"}},{\"id\":\"4f92e093-075f-4781-b202-fbbc74877957\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":1170,\"y\":300,\"width\":170,\"height\":228},\"path\":[{\"x\":0,\"y\":228},{\"x\":170,\"y\":0}],\"source\":{\"direction\":\"Right\",\"element\":\"29ce4584-4edf-4053-870d-cc7d801586fb\"},\"target\":{\"direction\":\"Left\",\"element\":\"abad6b17-9995-428a-bb09-dec355bc4562\"}},{\"id\":\"4c522b8a-9198-43f4-a245-be5ee184209e\",\"name\":\"\",\"type\":\"UseCaseAssociation\",\"owner\":null,\"bounds\":{\"x\":1190,\"y\":240,\"width\":150,\"height\":60},\"path\":[{\"x\":0,\"y\":0},{\"x\":150,\"y\":60}],\"source\":{\"direction\":\"Right\",\"element\":\"f4c6a960-01f4-44ef-807d-c1ce19ede751\"},\"target\":{\"direction\":\"Left\",\"element\":\"abad6b17-9995-428a-bb09-dec355bc4562\"}},{\"id\":\"79e00fc5-5b4b-49ed-8790-d81c41e58e3c\",\"name\":\"\",\"type\":\"UseCaseInclude\",\"owner\":null,\"bounds\":{\"x\":810,\"y\":128,\"width\":180,\"height\":112},\"path\":[{\"x\":180,\"y\":112},{\"x\":0,\"y\":0}],\"source\":{\"direction\":\"Left\",\"element\":\"f4c6a960-01f4-44ef-807d-c1ce19ede751\"},\"target\":{\"direction\":\"Right\",\"element\":\"70eeef63-576a-4c39-af6d-1962e01d4ca9\"}}],\"assessments\":[]}";

    @Test
    void similarityUseCaseDiagram_EqualModels() {
        compareSubmissions(modelingSubmission(useCaseModel1), modelingSubmission(useCaseModel1), 0.8, 1.0);
        compareSubmissions(modelingSubmission(useCaseModel2), modelingSubmission(useCaseModel2), 0.8, 1.0);
    }

    @Test
    void similarityUseCaseDiagram_DifferentModels() {
        compareSubmissions(modelingSubmission(useCaseModel1), modelingSubmission(useCaseModel2), 0.0, 0.1844);
    }

    @Test
    void parseUseCaseDiagramModelCorrectly() throws IOException {
        UMLDiagram diagram = UMLModelParser.buildModelFromJSON(parseString(useCaseModel2).getAsJsonObject(), 1L);
        assertThat(diagram).isInstanceOf(UMLUseCaseDiagram.class);
        UMLUseCaseDiagram useCaseDiagram = (UMLUseCaseDiagram) diagram;
        assertThat(useCaseDiagram.getSystemBoundaryList()).hasSize(1);
        assertThat(useCaseDiagram.getActorList()).hasSize(2);
        assertThat(useCaseDiagram.getUseCaseList()).hasSize(9);
        assertThat(useCaseDiagram.getUseCaseAssociationList()).hasSize(9);

        assertThat(useCaseDiagram.getElementByJSONID("559c80d8-5778-4c65-a57e-a0a7980404ed")).isInstanceOf(UMLActor.class);
        assertThat(useCaseDiagram.getElementByJSONID("67f8af32-d803-4b36-b69c-bd0bb7b65207")).isInstanceOf(UMLUseCase.class);
        assertThat(useCaseDiagram.getElementByJSONID("f84c7d48-a98f-4667-83be-76aded95df10")).isInstanceOf(UMLUseCaseAssociation.class);
    }
}
