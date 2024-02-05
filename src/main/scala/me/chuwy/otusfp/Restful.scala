package me.chuwy.otusfp

import cats.data.{Kleisli, OptionT}
import cats.effect.IO.sleep
import cats.effect._
import fs2.Stream
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax.EncoderOps
import io.circe.{Decoder, DecodingFailure, Encoder, JsonObject}
import org.http4s.Method.GET
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.{jsonEncoder, jsonEncoderOf, jsonOf}
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s._
import org.typelevel.ci.CIString

import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object Restful {

  val websocket = WebSocketBuilder[IO].build(x => x)

  type RIO[Env, A] = Kleisli[IO, Env, A]

  val r1 = Kleisli { (s: String) => IO.pure(s.length) }
  val r2 = Kleisli { (s: Int) => IO.pure(s) }
  val r3 = Kleisli { (s: Int) => IO.pure(s.toLong) }

//  val result: Kleisli[IO, String, Int] = for {
//    l1 <- r1
//    l2 <- r2
//  } yield l1 + l2

  def f1: Int => String = ???
  def f2: String => Long = ???

  def run1 = f1.andThen(f2)
  def run2 = r1.andThen(r2).andThen(r3)
  def run3 = r1.run("hello")

  case class User(name: String, id: Int)

  object User {
    implicit val userDecoder: Decoder[User] =
      Decoder.instance { cur =>
        for {
          jsonObject <- cur.as[JsonObject]
          nameOpt <- jsonObject.apply("name").toRight(DecodingFailure("Key name doesn't exist", cur.history))
          name <- nameOpt.as[String]
          idOpt <- jsonObject.apply("id").toRight(DecodingFailure("Key id doesn't exist", cur.history))
          id <- idOpt.as[Option[Int]]
        } yield User(name, id.get)
      }

    implicit val userEncoder: Encoder[User] =
      deriveEncoder[User]

    implicit def userEntityDecoder[F[_]: Concurrent]: EntityDecoder[F, User] =
      jsonOf[F, User]

    implicit def userEntityEncoder[F[_]: Concurrent]: EntityEncoder[F, User] =
      jsonEncoderOf[F, User]
  }

  def authUser: Kleisli[OptionT[IO, *], Request[IO], User] =
    Kleisli { (req: Request[IO]) =>
      req.headers.get(CIString("user")) match {
        case Some(userHeaders) =>
          OptionT.liftF(IO.pure(User(userHeaders.head.value, 1)))
        case None =>
          OptionT.liftF(IO.pure(User("anon", 0)))
      }
    }

  def addHeader(businessLogic: HttpRoutes[IO]): HttpRoutes[IO] = {
    val header: Header.ToRaw = "X-Otus" -> "webinar"
    Kleisli { (req: Request[IO]) =>
      businessLogic.map {         // What's the difference with service(req).map ...
        case Status.Successful(resp) =>
          resp.putHeaders(header)
        case resp => resp
      }.apply(req)
    }
  }

  val serviceOne: HttpRoutes[IO] =
    HttpRoutes.of {
      case GET -> Root / "hello" / name =>
        websocket
      case req @ POST -> Root / "hello" =>
        req.as[User].flatMap { user =>
          Ok(s"Hello, $user")
        }
    }

  val authMiddleware = AuthMiddleware(authUser)

  def authedService: AuthedRoutes[User, IO] =
    AuthedRoutes.of {
      case GET -> Root / "hello" / name as user =>
        user match {
          case User(_, 0) => Ok("You're anonymous")
          case User(userName, _) => Ok(s"You're ${userName} accessing hello/${name}")
        }
    }

  val httpApp: HttpApp[IO] = Router(
    "/" -> addHeader(serviceOne),

    "/auth" -> authMiddleware(authedService)
  ).orNotFound

  val builder =
    BlazeServerBuilder[IO](global)
      .bindHttp(port = 8080, host = "localhost")
      .withHttpApp(httpApp)


  case class Counter(counter: Int)
  implicit val counterEncoder: Encoder[Counter] = deriveEncoder[Counter]
  implicit val counterDecoder: Decoder[Counter] = deriveDecoder[Counter]

  implicit val counterEntityEncoder: EntityEncoder[IO, Counter] = jsonEncoderOf[IO, Counter]
  implicit val counterEntityDecoder: EntityDecoder[IO, Counter] = jsonOf[IO, Counter]

  val counterRef: IO[Ref[IO, Int]] = Ref.of[IO, Int](0)

  def countService(counter: Ref[IO, Int]): HttpRoutes[IO] = HttpRoutes.of {
    case GET -> Root / "counter" =>
      for {
        counterValue <- counter.updateAndGet(_ + 1)
        result <- Ok(Counter(counterValue).asJson)
      } yield result
  }

  object PositiveNum {
    def unapply(num: String): Option[Either[String, Int]] = {
      if(num.nonEmpty) {
        Try(num.toInt).toEither match {
          case Left(value) => Some(Left(value.toString))
          case Right(value) => Some(Right(value))
        }
      } else {
        Some(Left[String, Int]("Отсутствует значение"))
      }
    }
  }
  def slowService: HttpRoutes[IO] = HttpRoutes.of {
    case GET -> Root / "slow" / PositiveNum(chunk) / PositiveNum(total) / PositiveNum(time) =>
      val result = for {
        chunkValue <- chunk
        totalValue <- total
        timeValue <- time
      } yield {
        Stream('A'.toByte)
          .repeatN(totalValue)
          .chunkN(chunkValue)
          .covary[IO]
          .evalMap(chunkElem => sleep(FiniteDuration.apply(timeValue, TimeUnit.SECONDS)).map {_ =>
            chunkElem
          })
      }
      result match {
        case Left(value) => BadRequest(value)
        case Right(value) => Ok(value)
      }
  }

}
