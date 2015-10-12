package rc

import akka.actor.{Status, Props, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{Uri, HttpResponse, HttpMessage, ContentTypes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, Materializer}
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

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

case class PollOptions(index: Int, finiteDuration: FiniteDuration)

case class ConsulResponse[T](
  index: Int,
  knownLeader: Boolean,
  lastContact: Int,
  value: T
)

trait JsonProtocol extends DefaultJsonProtocol {
  implicit val serviceFormatter = jsonFormat5(Service)
  implicit val serviceInfoFormatter = jsonFormat(
    JsonReader.func2Reader {
      case JsObject(fields) =>
        val result = for {
          JsString(node) <- fields.get("Node")
          JsString(address) <- fields.get("Address")
          JsString(sId) <- fields.get("ServiceID")
          JsString(sName) <- fields.get("ServiceName")
          JsString(sAddress) <- fields.get("ServiceAddress")
          JsNumber(sPort) <- fields.get("ServicePort")
        } yield {
          val sTags = fields.get("ServiceTags").collect {
            case JsArray(tags) => tags.map { case JsString(value) => value }.toSet
          }
          ServiceInfo(
            node,
            address,
            Service(sName, Some(sId), sTags, Some(sAddress), Some(sPort.toInt))
          )
        }
        result.getOrElse(deserializationError("Unable to deserialize response"))
    },
    JsonWriter.func2Writer[ServiceInfo](_ => ???)
  )
  implicit val registrationFormatter = jsonFormat3(Registration)
}
object JsonProtocol extends JsonProtocol

class ConsulAPI(host: String, port: Int = 8500)(implicit as: ActorSystem, mat: Materializer) {
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import JsonProtocol._
  import as.dispatcher

  private def header(resp: HttpMessage, name: String): Option[String] = resp
    .headers
    .find(_.name() == name)
    .map(_.value())

  private def checkStatusCode(resp: HttpResponse) =
    if (resp.status.isFailure())
      Future.failed(new Exception(s"Wrong status code: ${resp.status.intValue()}"))
    else
      Future.successful(resp)

  private def checkJsonContentType(resp: HttpResponse) =
    if (resp.entity.contentType() != ContentTypes.`application/json`)
      Future.failed(new Exception(s"Wrong content type: ${resp.entity.contentType()}"))
    else
      Future.successful(resp)

  def register(registration: Registration): Future[JsValue] = {
    Http().singleRequest(Put(
      s"http://$host:$port/v1/catalog/register",
      registration
    ))
    .flatMap(checkJsonContentType)
    .flatMap(checkStatusCode)
    .flatMap { case resp => Unmarshal(resp).to[JsValue] }
  }

  def service(service: String, options: Option[PollOptions] = None): Future[ConsulResponse[Seq[ServiceInfo]]] = {
    val uri: Uri = s"http://$host:$port/v1/catalog/service/$service"
    val query = options.map { opt =>
      Map("index" -> opt.index.toString, "wait" -> (opt.finiteDuration.toSeconds.toString + "s"))
    }.getOrElse(Map.empty)

    Http().singleRequest(Get(uri.withQuery(query)))
      .flatMap(checkJsonContentType)
      .flatMap(checkStatusCode)
      .flatMap {
        case resp =>
          val state = for {
            index <- header(resp, "X-Consul-Index").map(_.toInt)
            knownLeader <- header(resp, "X-Consul-Knownleader").map(_.toBoolean)
            lastContact <- header(resp, "X-Consul-Lastcontact").map(_.toInt)
          } yield (index, knownLeader, lastContact)

          state.map { case (index, knownLeader, lastContact) =>
            Unmarshal(resp)
              .to[JsValue]
              .map(js => ConsulResponse(index, knownLeader, lastContact, js.convertTo[Seq[ServiceInfo]]))
          }.getOrElse(Future.failed(new Exception("Can not get required headers from consul")))
      }
  }
}

object Implicits {
  lazy implicit val as = ActorSystem()
  lazy implicit val materializer = ActorMaterializer()
}

object Streaming {
  private case class ConsulSlowPoller[T](f: Option[PollOptions] => Future[ConsulResponse[T]], pollInterval: FiniteDuration) extends ActorPublisher[T] {
    import akka.pattern.pipe
    import akka.stream.actor.{ActorPublisherMessage => M}
    import context.dispatcher

    var currentIndex: Option[Int] = None

    override def receive: Receive = waitingForDownstream

    def waitingForDownstream: Receive = {
      case M.Request(n) => performRequestIfNeeded()
    }

    def requestInProgress: Receive = {
      case si: ConsulResponse[T] =>
        if(!currentIndex.contains(si.index)) {
          onNext(si.value)
        }
        currentIndex = Some(si.index)
        performRequestIfNeeded()
      case Status.Failure(ex) => onError(ex)
    }

    def performRequestIfNeeded() = {
      if(isActive && totalDemand > 0) {
        f(currentIndex.map(PollOptions(_, pollInterval))) pipeTo self
        context.become(requestInProgress)
      } else {
        context.become(waitingForDownstream)
      }
    }
  }

  def service(api: ConsulAPI, name: String) = {
    Source.actorPublisher[Seq[ServiceInfo]](Props(ConsulSlowPoller(api.service(name, _), 1 second)))
  }
}
