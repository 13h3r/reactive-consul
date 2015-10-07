import sbt._

resolvers ++= Seq(
  "casino-plugins-releases" at "http://artifactory.billing.test:8081/artifactory/sbt-plugins-release-local",
  "casino-plugins-snapshots" at "http://artifactory.billing.test:8081/artifactory/sbt-plugins-snapshot-local"
)

addSbtPlugin("ru.dgis.casino" %% "sbt-build-info" % "0.2.0")

addSbtPlugin("ru.dgis.casino" %% "casino-sbt-plugin" % "0.10.1")

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"
)

