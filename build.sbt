lazy val root = (project in file(""))
		.enablePlugins(PlayScala)
		.settings(
			name := "play-reactivemongo-demo-app",
			version := "1.0",
			scalaVersion := "2.11.7",
			routesGenerator := InjectedRoutesGenerator,
			resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
			libraryDependencies ++= Seq(
				"org.reactivemongo" %% "play2-reactivemongo" % "0.11.10"))