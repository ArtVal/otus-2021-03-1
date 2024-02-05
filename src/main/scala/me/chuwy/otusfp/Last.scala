package me.chuwy.otusfp

import cats.effect.IO
import scala.concurrent.ExecutionContext.global

import io.circe.{Json, Decoder}
import io.circe.literal._

import me.chuwy.otusfp.Restful.User
import org.http4s.{Request, Uri, Method}
import org.http4s.syntax.all._
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client

object Last {

  val httpReq = Request[IO]().withUri(uri"http://localhost:8080/hello/bob")
  val httpReqWithUser = Request[IO](method = Method.POST)
    .withUri(uri"http://localhost:8080/hello")
    .withEntity(User("Charlie", 100))

  // Encoder = A => Json
  // Decoder Json => A
//  val index = User("Bob", 1)

//  val validJson = json"""{"key": $index}"""

//  val response = BlazeClientBuilder[IO](global)
//    .resource
//    .use { client => client.expect[String](httpReq) }
//    .flatMap(IO.println)

//    .flatMap(client => client.run(httpReq))
//    .use { response =>
//      response.body.compile.to(Array).map(bytes => new String(bytes)).flatMap(IO.println)
//    }


  val request = Client.fromHttpApp(Restful.httpApp).expect[String](httpReq).flatMap(IO.println)

  val port = 8080
  val part = "foo"
  val macroUri = uri"http://localh/api"
  val r = Request[IO]().withUri(macroUri)

  val requestWithUser = Restful.httpApp(r).flatMap(resp => resp.as[String]).flatMap(IO.println)

}
