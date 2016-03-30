#  Writing Reactive Apps with ReactiveMongo and Play, Pt. 1

> ReactiveMongo는 Scala를 위한 새로운 MongoDB 드라이버 브랜드입니다. 단지 또 다른 비동기 드라이버가 아닌 당신이 매우 확장성있는 어플리션이션을 디자인할 수 있도록 MongoDB의 무한한 라이브 콜렉션과 파일들 같은 특성을 발휘해주는 현대적인 실시간 웹 어플리케이션을 위한 reactive 드라이버 입니다.

Play는 스칼라 웹 어플리케이션을 제작에 있어 메인 레퍼런스가 됬습니다. 그것은 한층 더 나은 동일한 비전과 역량을 공유하는 데이타베이스 드라이버를 사용합니다. 만약 당신이 Play 2.1 베이스에 MongoDB를 백엔드로 한 웹 어플리케이션을 계획하고 있다면 ReactiveMongo가 당신을 위한 드라이버입니다!

이 글은 그러한 프로젝트를 시작하는 과정을 진행합니다. 우리는 article을 관리하는 간단한 어플리케이션을 작성할 것입니다. 각 article은 title, content이 있고 파일을 첨부할 수 있습니다. (그림 파일이나 PDF, archives 등)

## Summary

- Bootstrap
- Configuring SBT
- Model
	- Serializing into BSON / Deserializing from BSON
	- Play Form
- Show a List of Articles
	- Controller
	- View
	- Route
- Run it!
- Go further

## Bootstrap

우리는 당신이 자신의 컴퓨터에 MongoDB를 설치하고 실행중이라고 가정합니다. 만약 그렇지 않으면 MongoDB 사이트의 QuickStart를 보세요.

Play는 현재 2.5 버전까지 나와있지만 ReactiveMongo Play plugin이 아직 2.4 버전까지만 지원하므로 우리는 2.4를 기준으로 작업합니다.

[acrivator](https://www.lightbend.com/community/core-tools/activator-and-sbt)를 이용해서 새로운 Scala 어플리케이션을 만들어 봅시다.

```
$ activator new

Fetching the latest list of templates...

Browse the list of templates: http://typesafe.com/activator/templates
Choose from these featured templates or enter a template name:
  1) minimal-akka-java-seed
  2) minimal-akka-scala-seed
  3) minimal-java
  4) minimal-scala
  5) play-java
  6) play-scala
(hit tab to see a list of all templates)
> 4
Enter a name for your application (just press enter for 'minimal-scala')
> play-reactivemongo-demo-app
OK, application "play-reactivemongo-demo-app" is being created using the "minimal-scala" template.

To run "play-reactivemongo-demo-app" from the command line, "cd play-reactivemongo-demo-app" then:
./play-reactivemongo-demo-app/activator run

To run the test for "play-reactivemongo-demo-app" from the command line, "cd play-reactivemongo-demo-app" then:
./play-reactivemongo-demo-app/activator test

To run the Activator UI for "play-reactivemongo-demo-app" from the command line, "cd play-reactivemongo-demo-app" then:
./play-reactivemongo-demo-app/activator ui

$ cd play-reactivemongo-demo-app
```

생성된 프로젝트 구조를 보면 다음과 같습니다.

```
/
├── LICENSE
├── README.md
├── activator
├── activator-launch-1.3.7.jar
├── build.sbt
├── src
│   ├── main
│   │   └── scala
│   │       └── com.example
│   │           └── Hello.scala
│   └── test
│       └── scala
│           └── HelloSpec.scala
└─ project
    ├── build.properties
    └── plugins.sbt
```

우리가 만들려는 건 Play 웹 어플리케이션이기 때문에 사용하지 않는 /src 디렉토리는 삭제합니다.
LICENSE 파일도 필요없기 때문에 삭제합니다.

## Configuring SBT

우선 Play plugin을 사용할 수 있게 해야합니다.
/project/plugins.sbt 파일을 만듭시다:

```
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.4.6")
```

ReactiveMongo와 ReactiveMongo play plugin을 사용하기 위해 우리는 의존관계를 설정합니다. /build.scala를 수정합시다:

``` scala
lazy val root = (project in file(""))
		.enablePlugins(PlayScala)
		.settings(
			name := "play-reactivemongo-demo-app",
			version := "1.0",
			scalaVersion := "2.11.8",
			routesGenerator := InjectedRoutesGenerator,
			resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
			libraryDependencies ++= Seq(
				"org.reactivemongo" %% "play2-reactivemongo" % "0.11.10"))
```

## Configure MongoDB Connection

더 진행하기 전에 우리는 ReactiveMongo Play plugin을 사용할 수 있게 해야합니다.
/conf/application.conf 파일을 만듭시다:

```
# ReactiveMongo module
play.modules.enabled += "play.modules.reactivemongo.ReactiveMongoModule"

# MongoDB 인스턴스의 uri, 자신의 설정에 맞게 고치도록 합시다
mongodb.uri = "mongodb://localhost:27017/dev"

# ReactiveMongo 설정
mongo-async-driver {
  akka {
    loglevel = WARNING
  }
}
```

## Model

우리의 article들은 title, content 그리고 publisher를 가지고 있습니다. 우리는 날짜 별로 정렬 할 수 있도록 생성 날짜와 수정 날짜를 추가합니다.

/app/models/articles.scala 파일을 만들고 case class Article을 작성합시다:

``` scala
package models

import org.joda.time.DateTime
import reactivemongo.bson._

case class Article(
    id: Option[BSONObjectID],
    title: String,
    content: String,
    publisher: String,
    creationDate: Option[DateTime],
    updateDate: Option[DateTime])
```

id 필드는 BSONObjectID의 Option 입니다. ObjectID는 MongoDB 도큐먼트의 표준 id 타입으로 12 바이트의 고유 값입니다.

#### Serializing into BSON / Deserializing from BSON

이제 우리는 이 case class를 위한 BSON serializer와 deserializer를 작성할 수 있습니다. 이것은 데이터베이스에 저장할 수 있도록 Article의 인스턴스를 BSON 도큐먼트로 변환해줍니다. ReactiveMongo는 BSONReader[T]와 BSONWriter[T] 두가지 trait을 제공하는데 이러한 용도로 구현되어야 합니다.

BSON 도큐먼트를 만드는 건 매우 간단합니다: BSONDocument() 메서드에 (String, BSONValue) 형식의 tuple들을 인자로 넘겨주면 됩니다. 아주 기본적인 도큐먼트는 다음과 같이 작성할 수 있습니다:

``` scala
val bson = BSONDocument(
  "title" -> BSONString("some title"),
  "content" -> BSONString("some content")
)
```

다른 방법으로는 BSONDocument의 getAs 메서드를 사용하면 됩니다:

``` scala
val title :Option[String] = bson.getAs[String]("title")
val content :Option[String] = bson.getAs[String]("content")
```

BSONDocumentReader[Article]과 BSONDocumentWriter[Article]을 같은 파일에 동반 객체로 구현해 봅시다(models/articles.scala):

``` scala
object Article extends AnyRef
		with BSONDocumentReader[Article] with BSONDocumentWriter[Article] {

	// 이런식으로 BSONHandler를 implicit으로 선언하면 BSONDateTime과 DateTime을 묵시적으로 자동 변환할 수 있습니다
	implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
		def read(time: BSONDateTime) = new DateTime(time.value)
		def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
	}

	// Reader와 Writer를 Macros를 이용하면 손쉽게 만들 수 있습니다.
	private implicit val reader: BSONDocumentReader[Article] = Macros.reader[Article]
	private implicit val writer: BSONDocumentWriter[Article] = Macros.writer[Article]

	override def read(bson: BSONDocument): Article = reader.read(bson)

	override def write(article: Article): BSONDocument = writer.write(article)
}
```

---
# [여기까지 작업했음!]
---

#### Play Form

우리는 또한 동반 객체 models.Article에서 HTTP form 데이타 핸들링을 위한 Play Form을 정의합니다. 그것은 우리가 구현을 할 때 유용할 것입니다(그것은 다음 글에서 다룹니다).

``` scala
val form = Form(
  mapping(
    "id" -> optional(of[String] verifying pattern(
      """[a-fA-F0-9]{24}""".r,
      "constraint.objectId",
      "error.objectId")),
    "title" -> nonEmptyText,
    "content" -> text,
    "publisher" -> nonEmptyText,
    "creationDate" -> optional(of[Long]),
    "updateDate" -> optional(of[Long])
  ) { (id, title, content, publisher, creationDate, updateDate) =>
    Article(
      id.map(new BSONObjectID(_)),
      title,
      content,
      publisher,
      creationDate.map(new DateTime(_)),
      updateDate.map(new DateTime(_)))
  } { article =>
    Some(
      (article.id.map(_.stringify),
      article.title,
      article.content,
      article.publisher,
      article.creationDate.map(_.getMillis),
      article.updateDate.map(_.getMillis)))
  }
)
```

#### Show a list of articles

The ReactiveMongo Play Plugin ships with a mixin trait for Controllers, providing some useful methods and a reference to the configured database.

> ReactiveMongo Play plugin은 Controller들을 대한 mixin trait을 포함하고 있는데 구성 데이타베이스에 대한 유용한 메서드들과 레퍼런스를 제공합니다.

##### Controller

``` scala
package controllers

import models._
import play.api._
import play.api.mvc._
import play.api.Play.current
import play.modules.reactivemongo._

import reactivemongo.api._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers._

object Articles extends Controller with MongoController {
  def index = Action {
    Ok()
  }
}
```

We need to retrive all the articles from our collection articles. To do this, we get a reference to this collection and run a basic query:

> 우리는 articles 콜렉션으로부터 모든 article들을 얻을 필요가 있습니다. 이를 위해 우리는 컬렉션에 대한 참조를 얻고 기본 쿼리를 실행합니다:

``` scala
  def index = Action { implicit request =>
    Async {
      implicit val reader = Article.ArticleBSONReader
      val collection = db.collection("articles")
      // empty query to match all the documents
      val query = BSONDocument()
      // the future cursor of documents
      val found = collection.find(query)
      // build (asynchronously) a list containing all the articles
      found.toList.map { articles =>
        Ok(views.html.articles(articles, activeSort))
      }
    }
```

Note the Async method: our controller action is actually return a future result.

> 비동기 메서드를 참고: 우리의 controller action은 현시점에서 future result를 리턴합니다.

##### View

Now, let’s create a view file app/views/index.scala.html for this action result:

> 이제 이 action result에 대한 view 파일 app/views/index.scala.html 을 만듭시다:

```
@(articles: List[models.Article])
@if(articles.isEmpty) {
	<p>No articles available yet.</p>
} else {
  <ul>
    @articles.map { article =>
    <li>
      <a href="#edittoimplement">@article.title</a>
      <em>by @article.publisher</em>
    </li>
    }
</ul>
}
```

##### Route

Let’s declare the matching route in the conf/routes file:

> cnof/routes 파일에 매칭되는 경로를 선언합시다:


```
GET     /                           controllers.Articles.index
```

##### Run it!

Now, you can start play:

> 이제 당신은 Play를 시작할 수 있습니다:

```
$ cd articles
$ ../play-2.1-SNAPSHOT/play
       _            _ 
 _ __ | | __ _ _  _| |
| '_ \| |/ _' | || |_|
|  __/|_|\____|\__ (_)
|_|            |__/ 
             
play! 2.1-SNAPSHOT, http://www.playframework.org

> Type "help play" or "license" for more information.
> Type "exit" or use Ctrl+D to leave this console.

[articles] $ run

[info] Updating {file:/Volumes/Data/code/article/articles/}articles...
[info] Done updating.                                                                  
--- (Running the application from SBT, auto-reloading is enabled) ---

[info] play - Listening for HTTP on /0.0.0.0:9000

(Server started, use Ctrl+D to stop and go back to the console...)
```

… and access http://localhost:9000 ;)

> ... 그리고 http://localhost:9000 으로 접속)

The list you get is empty. That is perfectly normal since our database does not contain any article. Let’s open a mongo console, connect to the database and add an article:

> 당신이 얻은 목록은 비어 있습니다. 우리의 데이타베이스는 아직 어떤 article도 담고 있지 않기 때문에 완벽하게 정상입니다. mongo console을 열고 데이타베이스에 접속해서 article을 추가해봅시다:

```
$ mongo
MongoDB shell version: 2.2.0
connecting to: 127.0.0.1:27017/test
> use reactivemongo-app
switched to db reactivemongo-app
> db.articles.save({ "content" : "some content", "creationDate" : new Date(), "publisher" : "Jack", "title" : "A cool article", "updateDate" : new Date()) })
```

##### Go further

In the next articles, we will continue this application (edition, attachments submission…), and cover more advanced features of ReactiveMongo, such as running complex queries, building indexes, and using GridFS.

> 다음 글에서 우리는 이 어플리케이션과 더 많은 ReactiveMongo의 고급 기능들, 말하자면 복잡한 쿼리, index 구축, GridFS 사용하기를 포함해서 계속 진행합니다.

Meanwhile, you can grab the complete application and start hacking with it.

> 한편 당신은 완성된 어플리케이션을 가지고 조작할 수 있습니다.

Don’t hesitate to post your questions and comments to the ReactiveMongo Google Group.

> ReactiveMongo Google Group에 질문과 의견을 게시하는 것을 망설이지 마세요.