package rc

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import spray.json.{JsValue, DefaultJsonProtocol}

import scala.concurrent.Future
import RequestBuilding._

case class Datacenter(name: String) extends AnyVal
case class Service(
  service: String,
  id: Option[String] = None,
  tags: Option[Set[String]] = None,
  address: Option[String] = None,
  port: Option[Int] = None
)
case class Registration(node: String, address: String, service: Option[Service])

case class ServiceInfo(node: String, address: String, service: Service)

case class ConsulResponse[T](
  index: Int,
  knownLeader: Boolean,
  lastContact: Int,
  value: T
)

trait JsonProtocol extends DefaultJsonProtocol {
  implicit val serviceFormatter = jsonFormat5(Service)
  implicit val registrationFormatter = jsonFormat3(Registration)
}
object JsonProtocol extends JsonProtocol

class ConsulAPI(host: String, port: Int = 8500)(implicit as: ActorSystem, mat: Materializer) {
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JsonProtocol._
  import as.dispatcher

  def register(registration: Registration): Future[JsValue] = {
    Http().singleRequest(Put(
      s"http://$host:$port/v1/catalog/register",
      registration
    ))
    .flatMap {
        case resp if resp.status.isFailure() =>
          Future.failed(new Exception(s"Wrong status code: ${resp.status.intValue()}"))
        case resp if resp.entity.contentType() != ContentTypes.`application/json` =>
          Future.failed(new Exception(s"Wrong content type: ${resp.entity.contentType()}"))
        case resp => Unmarshal(resp).to[JsValue]
      }
  }

  def service(service: String): Future[ConsulResponse[ServiceInfo]] = {
    Http().singleRequest(Get(s"http://$host:$port/v1/catalog/service/$service")).flatMap {
      case resp if resp.status.isFailure() =>
        Future.failed(new Exception(s"Wrong status code: ${resp.status.intValue()}"))
      case resp if resp.entity.contentType() != ContentTypes.`application/json` =>
        Future.failed(new Exception(s"Wrong content type: ${resp.entity.contentType()}"))
      case resp =>
        Future.failed(???) //ConsulResponse(resp.headers)
    }
  }

}

object Implicits {
  lazy implicit val as = ActorSystem()
  lazy implicit val materializer = ActorMaterializer()
}