# Reactive Consul
The goal of project is to provide asynchronous reactive scala API for [consul](http://consul.io)

Reactive Consul contains two API levels:
* high-level stream based API
* asynchronous request based API

# Stream based API
The goal of stream based API is to provide access to state of consul as a data stream. 

The idea of data stream is to provide abstraction over data that changes of time. Like a meter, or a balance of a credit card or so. And the very natural way of presenting a data in consul is presenting it as a data stream. This allows consul clients to work with consul in a reactive way. If you are interesting in more details about reactive streams read [this](http://www.reactive-streams.org/) and [this](http://www.reactivemanifesto.org/) about the reactive methodology.

Reactive Consul based on (akka streams)[http://doc.akka.io/docs/akka-stream-and-http-experimental/current/]. Here it is an example of a `Source` of service state:

```scala
import akka.stream.scaladsl.{Sink, Source}
import rc.{ConsulAPI, ServiceInfo, ConsulControl, Consul}
import rc.Implicits._
val consulApi = new ConsulAPI("localhost")
val myService: Source[Seq[ServiceInfo], ConsulControl] = Consul.service(consulApi, "myService")
myService.to(Sink.foreach(println)).run()
```

Here we create `consulApi` that represents a consul connection and next obtain `Source` withconnection and service name.

Next, having the `Source` of service state you can react on changes in the service configuration in a very reactive way.

## Under the hood
Consul API is HTTP based and does not provide direct reactive communication. But, it possible to simulate reactive interaction with long polling. More details about how it implemented in consul (here)[https://www.consul.io/docs/agent/http.html]. 

The identity of state passed to client as `X-Consul-Index` which used to determine is state changed or no. If we got same `X-Consul-Index` as before state is no changed and stream does not emit new element. If state changed new element emitted, but there is no guarantee, that your service changed, because `X-Consul-Index` is identifies global consul state.

# Request based API
The goal of request based API is provide low level asynchronous API based on single HTTP request.

## Creating `ConsulAPI`
To perform requests you have to create a `ConsulAPI`:

```scala
import rc.ConsulAPI
import rc.Implicits._
val consulApi = new ConsulAPI("localhost") // port 8500 by default
```

Reactive Consul is based on [akka-http](http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/index.html) 
and therefore it requires an `ActorSystem` and a `Materializer`.

If you don't use akka in your project you can simple import predefined `ActorSystem` and `Materializer`:

```scala
import rc.Implicits._
```

If you works with akka and have an access to `ActorSystem` just make it implicitly available. 

## Performing requests

### Registering service

This example should be self explainable:
```scala
val r: Future[JsValue]  = consulApi.register(new Registration(
  "my-node.local",// node
  "10.54.100.77", // address
  Some( // service. May be None
    Service(
      service, 
      Some(id),
      Some(Set("tag1", "tag2")),
      Some(serviceAddress),
      Some(port)
    )
  )
))
```

In result you have a `Future` to track request result.

### Deregistering 

```scala
val r: Future[Unit] = consulApi.deregister(node, Some(service))
```

In result you have a `Future` to track request result.

### Service information

To get information about service user `service` method:

```scala 
val serviceInfo: Future[ConsulResponse[Seq[ServiceInfo]]] = consulApi.service(serviceName)
```

As usual you have a `Future` as a result. In this `Future` you will get:
* `ConsulResponse` that contains information about state and consistency of requested data. It contains information about data index, leader state and last contact. You can read more here - https://www.consul.io/docs/agent/http.html
* `Seq[ServiceInfo]` detailed information about requested service.
