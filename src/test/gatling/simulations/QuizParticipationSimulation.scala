package simulations

import _root_.io.gatling.core.scenario.Simulation
import io.gatling.core.Predef._
import io.gatling.core.json._
import io.gatling.core.structure.{ChainBuilder, ScenarioBuilder}
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder

import scala.concurrent.duration._
import scala.language.existentials
import scala.util.parsing.json._

class QuizParticipationSimulation extends Simulation {

    // NOTE: update these values to fit the tested exercise
    val exerciseId = 187
    val backgroundPicturePath = "/api/files/drag-and-drop/backgrounds/98/DragAndDropBackground_2018-02-16T11-45-42-684_7f0aa8e4.png"

    // NOTE: Adjust these numbers for desired load
    val numUsersSubmit = 200
    val numUsersNoSubmit = 200

    val feeder: Iterator[Map[String, String]] = Iterator.tabulate(500)(i => Map(
        "username" -> ("<USERNAME>"), // NOTE: generate real username for each value of i (removed for security)
        "password" -> ("<PASSWORD>") // NOTE: generate real password for each value of i (removed for security)
    ))

    val baseURL = "http://localhost:8080"
    val wsBaseURL = "ws://localhost:8080"

    val httpConf: HttpProtocolBuilder = http
        .baseUrl(baseURL)
        .wsBaseUrl(wsBaseURL)
        .inferHtmlResources()
        .acceptHeader("*/*")
        .acceptEncodingHeader("gzip, deflate")
        .acceptLanguageHeader("fr,fr-fr;q=0.8,en-us;q=0.5,en;q=0.3")
        .connectionHeader("keep-alive")
        .userAgentHeader("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.10; rv:33.0) Gecko/20100101 Firefox/33.0")

    val headers_http = Map(
        "Accept" -> """application/json"""
    )

    val headers_http_authenticated = Map(
        "Accept" -> """application/json""",
        "X-XSRF-TOKEN" -> "${xsrf_token}"
    )

    /**
      * Selects random answers and adds submittedAnswers to the submission
      *
      * @param questionsString the questions of the exercise as a JSON string
      * @return the submission including the submittedAnswers as a JSON string
      */
    def selectRandomAnswers(questionsString: String, submit: Boolean): String = {
        // parse json strings into objects (Map or List)
        val questions = JSON.parseFull(questionsString).get.asInstanceOf[List[Any]]

        // save submitted answers in a List
        var submittedAnswers = List[Map[String, Any]]()

        // iterate through all questions to select answers
        questions.foreach((questionP) => {
//            val question = questionP.get.asInstanceOf[Map[String, Any]]
//            val questionType = question("type").get.asInstanceOf[String]

            // create a submitted answer for this question
//            var submittedAnswer = Map(
//                "question" -> question,
//                "type" -> questionType
//            )
//
//            if (questionType.equals("multiple-choice")) {
//                // save selected options in a List
//                var selectedOptions = List[Map[String, Any]]()
//
//                // iterate through all answer options of this question
//                val answerOptions = question("answerOptions").get.asInstanceOf[List[Any]]
//                answerOptions.foreach((answerOptionP) => {
//                    val answerOption = answerOptionP.get.asInstanceOf[Map[String, Any]]
//
//                    // select each answer option with a 50/50 chance
//                    if (math.random < 0.5) {
//                        selectedOptions = answerOption +: selectedOptions
//                    }
//                })
//
//                // add selected options to submitted answer
//                submittedAnswer = submittedAnswer + ("selectedOptions" -> selectedOptions)
//            } else if (questionType.equals("drag-and-drop")) {
//                // save mappings in a List
//                var mappings = List[Map[String, Any]]()
//
//                // extract drag items and drop locations
//                var dragItems = question("dragItems").get.asInstanceOf[List[Any]]
//                var dropLocations = question("dropLocations").get.asInstanceOf[List[Any]]
//
//                while (dragItems.nonEmpty && dropLocations.nonEmpty) {
//                    // create a random mapping
//                    val dragItemIndex = (math.random * dragItems.size).floor.toInt
//                    val dropLocationIndex = (math.random * dropLocations.size).floor.toInt
//
//                    val mapping = Map(
//                        "dragItem" -> dragItems.get(dragItemIndex).get.asInstanceOf[Map[String, Any]],
//                        "dropLocation" -> dropLocations.get(dropLocationIndex).get.asInstanceOf[Map[String, Any]]
//                    )
//
//                    // remove selected elements from lists
//                    dragItems = dragItems.take(dragItemIndex) ++ dragItems.drop(dragItemIndex + 1)
//                    dropLocations = dropLocations.take(dropLocationIndex) ++ dropLocations.drop(dropLocationIndex + 1)
//
//                    // add mapping to mappings
//                    mappings = mapping +: mappings
//                }
//
//                // add mappings to submitted answer
//                submittedAnswer = submittedAnswer + ("mappings" -> mappings)
//            }
//
//            // add submitted answer to the List
//            submittedAnswers = submittedAnswer +: submittedAnswers
        })

        // add submitted answers to submission
        var result: Map[String, Any] = Map("submittedAnswers" -> submittedAnswers)

        if (submit) {
            result = result + ("submitted" -> true)
        }

        // convert back into json string
        Json.stringify(result)
    }

    val login: ChainBuilder = exec(
        http("First unauthenticated request")
            .get("/api/account")
            .headers(headers_http)
            .check(status.is(401))
            .check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .feed(feeder)
        .pause(2 seconds)
        .exec(http("Authentication")
            .post("/api/authentication")
            .headers(headers_http_authenticated)
            .formParam("j_username", "${username}")
            .formParam("j_password", "${password}")
            .formParam("remember-me", "true")
            .formParam("submit", "Login")
            .check(status.is(200))
            .check(headerRegex("Set-Cookie", "XSRF-TOKEN=([^;]*);[\\s]").saveAs("xsrf_token"))).exitHereIfFailed
        .pause(10 seconds)

    val loadDashboard: ChainBuilder = rendezVous(Math.min(numUsersSubmit, numUsersNoSubmit))
        .exec(http("Get dashboard")
            .get("/api/courses/for-dashboard")
            .headers(headers_http_authenticated)
            .check(status.is(200))).exitHereIfFailed
        .pause(5 seconds)

    val startQuiz: ChainBuilder = rendezVous(Math.min(numUsersSubmit, numUsersNoSubmit))
        .exec(http("Get Participation with Quiz")
            .get("/api/courses/1/exercises/" + exerciseId + "/participation")
            .headers(headers_http_authenticated)
            .check(status.is(200))
            .check(jsonPath("$.exercise.questions").saveAs("questions"))
            .resources(
                http("Load Picture").get(backgroundPicturePath).headers(headers_http_authenticated)
            )
        ).exitHereIfFailed
        .pause(5 seconds, 15 seconds)

    val workOnQuiz: ChainBuilder = exec(
        ws("Connect WebSocket")
            .connect("/websocket/websocket")).exitHereIfFailed
        .pause(5 seconds)
        .exec(ws("Connect STOMP")
            .sendText("CONNECT\nX-XSRF-TOKEN:${xsrf_token}\naccept-version:1.2\nheart-beat:10000,10000\n\n\u0000")
            .await(10 seconds)())
        .exec(ws("Subscribe Submission")
            .sendText("SUBSCRIBE\nid:sub-1\ndestination:/user/topic/quizExercise/" + exerciseId + "/submission\n\n\u0000"))
        .pause(5 seconds)
        .repeat(20) {
            exec(ws("Send Answers")
                .sendText(session => "SEND\ndestination:/topic/quizExercise/" + exerciseId + "/submission\n\n" + selectRandomAnswers(session("questions").as[String], false) + "\u0000")
                .await(10 seconds)())
                .pause(5 seconds)
        }

    val submitQuiz: ChainBuilder = rendezVous(numUsersSubmit)
        .exec(ws("Submit Answers")
            .sendText(session => "SEND\ndestination:/topic/quizExercise/" + exerciseId + "/submission\n\n" + selectRandomAnswers(session("questions").as[String], true) + "\u0000")
            .await(10 seconds)())
        .pause(5 seconds)

    val waitForResult: ChainBuilder = pause(10 seconds)
        .exec(ws("Subscribe Participation")
            .sendText("SUBSCRIBE\nid:sub-1\ndestination:/user/topic/quizExercise/" + exerciseId + "/participation\n\n\u0000")
            .await(600 seconds)())


    val usersNoSubmit: ScenarioBuilder = scenario("Users without submit").exec(login, loadDashboard, startQuiz, workOnQuiz, waitForResult)
    val usersSubmit: ScenarioBuilder = scenario("Users with submit").exec(login, loadDashboard, startQuiz, workOnQuiz, submitQuiz, waitForResult)

    setUp(
        usersNoSubmit.inject(rampUsers(numUsersNoSubmit) during (20 seconds)),
        usersSubmit.inject(rampUsers(numUsersSubmit) during (20 seconds))
    ).protocols(httpConf)

}
