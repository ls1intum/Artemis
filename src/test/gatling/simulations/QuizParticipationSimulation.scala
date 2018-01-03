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

    // TODO replace exerciseID with id of dynamically created exercise
    val exerciseId = 118

    val feeder: Iterator[Map[String, String]] = Iterator.tabulate(500)(i => Map(
        "username" -> ("<USERNAME>"),   // TODO: generate real username for each value of i (removed for security)
        "password" -> ("<PASSWORD>")    // TODO: generate real password for each value of i (removed for security)
    ))

    val baseURL = "http://localhost:8080"
    val wsBaseURL = "ws://localhost:8080"

    val httpConf: HttpProtocolBuilder = http
        .baseURL(baseURL)
        .wsBaseURL(wsBaseURL)
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
      * @param submissionString the submission as a JSON string
      * @param questionsString  the questions of the exercise as a JSON string
      * @return the submission including the submittedAnswers as a JSON string
      */
    def selectRandomAnswers(submissionString: String, questionsString: String): String = {
        // parse json strings into objects (Map or List)
        val submission = JSON.parseFull(submissionString).get.asInstanceOf[Map[String, Any]]
        val questions = JSON.parseFull(questionsString).get.asInstanceOf[List[Any]]

        // save submitted answers in a List
        var submittedAnswers = List[Map[String, Any]]()

        // iterate through all questions to select answers
        questions.foreach((questionP) => {
            val question = questionP.get.asInstanceOf[Map[String, Any]]

            // create a submitted answer for this question
            var submittedAnswer = Map(
                "question" -> question,
                "type" -> question("type")
            )

            // save selected options in a List
            var selectedOptions = List[Map[String, Any]]()

            // iterate through all answer options of this question
            val answerOptions = question("answerOptions").get.asInstanceOf[List[Any]]
            answerOptions.foreach((answerOptionP) => {
                val answerOption = answerOptionP.get.asInstanceOf[Map[String, Any]]

                // select each answer option with a 50/50 chance
                if (math.random < 0.5) {
                    selectedOptions = answerOption +: selectedOptions
                }
            })

            // add selected options to submitted answer
            submittedAnswer = submittedAnswer + ("selectedOptions" -> selectedOptions)

            // add submitted answer to the List
            submittedAnswers = submittedAnswer +: submittedAnswers
        })

        // add submitted answers to submission
        val result = submission + ("submittedAnswers" -> submittedAnswers)

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
        .pause(2 seconds)
        .exec(http("Authenticated request")
            .get("/api/account")
            .headers(headers_http_authenticated)
            .check(status.is(200)))
        .pause(30 seconds, 40 seconds)

    val startQuiz: ChainBuilder = exec(
        http("Get quiz")
            .get("/api/quiz-exercises/" + exerciseId + "/for-student")
            .headers(headers_http_authenticated)
            .check(status.is(200))
            .check(jsonPath("$.questions").saveAs("questions"))).exitHereIfFailed
        .exec(http("Start Quiz")
            .get("/api/courses/1/exercises/" + exerciseId + "/submissions/my-latest")
            .headers(headers_http_authenticated)
            .check(status.is(200))
            .check(bodyString.saveAs("submission"))
            .check(jsonPath("$.id").saveAs("submissionID"))).exitHereIfFailed
        .pause(5 seconds)

    val workOnQuiz: ChainBuilder = exec(
        ws("Connect WebSocket")
            .open("/websocket/tracker/websocket")).exitHereIfFailed
        .pause(5 seconds)
        .exec(ws("Connect STOMP")
            .sendText("CONNECT\nX-XSRF-TOKEN:${xsrf_token}\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\u0000")
            .check(wsAwait.within(10 seconds).until(1)))
        // TODO: get participation id and subscribe to participation
        //        .exec(ws("Subscribe Participation")
        //            .sendText("[\"SUBSCRIBE\nid:sub-1\ndestination:/topic/participation/13020/newResults\n\n\u0000\"]"))
        .exec(ws("Subscribe Submission")
            .sendText("SUBSCRIBE\nid:sub-1\ndestination:/topic/quizSubmissions/${submissionID}\n\n\u0000"))
        .pause(5 seconds)
        .repeat(20) {
            exec(ws("Send Answers")
                .sendText(session => "SEND\ndestination:/topic/quizSubmissions/${submissionID}/save\n\n" + selectRandomAnswers(session("submission").as[String], session("questions").as[String]) + "\u0000")
                .check(wsListen.within(10 seconds).until(1)))
                .pause(5 seconds)
        }

    val submitQuiz: ChainBuilder =
        pause(5 seconds, 10 seconds)
            .exec(http("Submit Quiz")
                .put("/api/quiz-submissions")
                .headers(headers_http_authenticated)
                .header("Content-Type", "application/json")
                .body(StringBody(session => selectRandomAnswers(session("submission").as[String], session("questions").as[String])))
                .check(status.is(200)))

    val usersNoSubmit: ScenarioBuilder = scenario("Users without submit").exec(login, startQuiz, workOnQuiz)
    val usersSubmit: ScenarioBuilder = scenario("Users with submit").exec(login, startQuiz, workOnQuiz, submitQuiz)

    setUp(
        usersNoSubmit.inject(rampUsers(100) over (30 seconds)),
        usersSubmit.inject(rampUsers(100) over (30 seconds))
    ).protocols(httpConf)

}
