name := "AkkaScalazStreams"

version := "1.0"

resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"

resolvers += "Akka Snapshots" at "http://repo.akka.io/snapshots/"

libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.1.0"

libraryDependencies += "org.scalaz" %% "scalaz-concurrent" % "7.1.0"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.4-SNAPSHOT"

// wartremoverErrors ++= Warts.allBut(Wart.Any)
