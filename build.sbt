name := "WikiFeeder"

version := "0.1-SNAPSHOT"

scalaVersion := "2.11.6"

resolvers += Resolver.sonatypeRepo("snapshots")

resolvers += Resolver.sonatypeRepo("releases")

libraryDependencies ++= Seq(
    "com.github.elbywan" %% "neold" % "0.2",
    "com.github.elbywan" %% "xtended-xml" % "0.1-SNAPSHOT",
    "com.github.elbywan" %% "scalargs" % "0.1-SNAPSHOT"
)
    