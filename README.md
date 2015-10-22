# Reactive Consul
The goal of project is to provide asynchronous reactive scala API for [consul](http://consul.io)

Reactive Consul contains two API levels:
* asynchronous request based API
* high-level stream based API

# Request based API
The goal of request based API is provide low level asynchronous API based on single HTTP request.

## Creating `ConsulAPI`
To perform requests you have to create a `ConsulAPI`:

```scala
import rc.ConsulAPI
import rc.Implicits._
val consul = new ConsulAPI("localhost") // port 8500 by default
```

Reactive Consul is based on [akka-http](http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/index.html) 
and therefore it requires an `ActorSystem` and a `Materializer`.

If you dont use akka in your project you can simple import predefined `ActorSystem` and `Materializer`:

```scala
import rc.Implicits._
```

If you works with akka and have an access to `ActorSystem` just make it implicitly available. 

## Performing requests

### Registering service

This example should be self explainable:
```scala
val r: Future[JsValue]  = rc.Registration(
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
)
```

In result you have a `Future` to track request result.
