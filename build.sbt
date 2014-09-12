name := "dungeon-raider-chat"

version := "1.0"

scalaVersion := "2.11.1"

resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2",
  "play" % "play_2.10" % "2.1.0",
  "com.rabbitmq" % "amqp-client" % "3.3.5",
  "org.mongodb" % "casbah_2.10" % "2.7.3",
  "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
  "com.typesafe" % "config" % "1.2.1",
  "org.postgresql" % "postgresql" % "9.3-1102-jdbc41"
)
