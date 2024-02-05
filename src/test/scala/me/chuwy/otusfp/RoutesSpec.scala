package me.chuwy.otusfp

import cats.effect.testing.specs2.CatsEffect
import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import me.chuwy.otusfp.Restful.{Counter, counterEntityDecoder}
import org.http4s.client.Client
import org.http4s.implicits.{http4sKleisliResponseSyntaxOptionT, http4sLiteralsSyntax}
import org.http4s.{Method, Request}
import org.specs2.mutable.Specification

import java.nio.charset.StandardCharsets
import java.time.Instant

class RoutesSpec  extends Specification with CatsEffect {
  "Counter" should {
    "can count" in {


      val request: Request[IO] = org.http4s.Request[IO](method = Method.GET, uri"/counter")

      def client(counterRef: Ref[IO, Int]): Client[IO] = Client.fromHttpApp[IO](Restful.countService(counterRef).orNotFound)

      def query(counterRef: Ref[IO, Int]): IO[Counter] = client(counterRef).expect[Counter](request)(counterEntityDecoder)

      (for {
        counter <- Restful.counterRef
        res1 <- query(counter)
        res2 <- query(counter)
      } yield (res1, res2)).unsafeRunSync() must beEqualTo(Counter(1), Counter(2))
    }
  }

  "Stream" should {
    "can slow stream" in {

      val startTime = Instant.now()
      val request: Request[IO] = org.http4s.Request[IO](method = Method.GET, uri"/slow/10/105/1")
      val client: Client[IO] = Client.fromHttpApp[IO](Restful.slowService.orNotFound)
      val response: String = client.stream(request)
        .flatMap(_.body.chunks.map(ch => new String(ch.toArray, StandardCharsets.UTF_8)))
        .compile
        .string
        .unsafeRunSync()
      val stopTime = Instant.now()
      val different: Long = stopTime.getEpochSecond - startTime.getEpochSecond
      response.length must beEqualTo(105)
      different must beGreaterThanOrEqualTo(11L)
    }
  }
}
