#  Writing Reactive Apps with ReactiveMongo and Play, Pt. 1

> ReactiveMongo is a brand new Scala driver for MongoDB. More than just yet-another-async-driver, it's a reactive driver that allows you to design very scalable applications unleashing MongoDB capabilities like streaming infinite live collections and files for modern Realtime Web applications.

---

> ReactiveMongo는 Scala를 위한 새로운 MongoDB 드라이버 브랜드입니다. 단지 또 다른 비동기 드라이버가 아닌 당신이 매우 확장성있는 어플리션이션을 디자인할 수 있도록 MongoDB의 무한한 라이브 콜렉션과 파일들 같은 특성을 해방하는 현대적인 실시간 웹 어플리케이션을 위한 reactive 드라이버 입니다.

Play 2.1 has become the main reference for writing web applications in Scala. It is even better when you use a database driver that shares the same vision and capabilities. If you’re planning to write a Play 2.1-based web application with MongoDB as a backend, then ReactiveMongo is the driver for you!

> Play 2.1은 스칼라 웹 어플리케이션을 제작에 있어 메인 레퍼런스가 됬습니다. 그것은 한층 더 나은 동일한 비전과 역량을 공유하는 데이타베이스 드라이버를 사용합니다. 만약 당신이 Play 2.1 베이스에 MongoDB를 백엔드로 한 웹 어플리케이션을 계획하고 있다면 ReactiveMongo가 당신을 위한 드라이버입니다!

This article runs you through the process of starting such a project from scratch. We are going to write a simple application that manages articles. Each article has a title, a content, and may embed some attachments (like pictures, PDFs, archives…).

> 이 아티클은 그러한 프로젝트를 시작하는 과정을 진행합니다. 우리는 아티클들을 관리하는 간단한 어플리케이션 작성할 것입니다. 각 아티클은 title, content이 있고 파일을 첨부할 수 있습니다. (그림 파일이나 PDF, archives...)

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

We assume that you have a running instance of MongoDB installed on your machine. If you don’t, read the QuickStart on the MongoDB site.

> 우리는 당신이 자신의 컴퓨터에 MongoDB를 설치하고 실행중이라고 가정합니다. 만약 그렇지 않으면 MongoDB 사이트의 QuickStart를 보세요.

Since Play is currently being refactored to integrate Scala 2.10, we will work with a snapshot. Let’s download it and create a new Scala application:

> Play는 현재 Scala 2.10을 통합하는 리팩토링 중이기 때문에 우리는 스냅샷으로 작업합니다. 그것을 다운로드하고 새로운 Scala 어플리케이션을 만들어 봅시다.

```
$ mkdir reactivemongo-app
$ cd reactivemongo-app
$ curl -O https://bitbucket.org/sgodbillon/repository/src/9f0c4e40cca1/play-2.1-SNAPSHOT.zip
$ unzip play-2.1-SNAPHSOT.zip
$ ./play-2.1-SNAPSHOT/play new articles
       _            _ 
 _ __ | | __ _ _  _| |
| '_ \| |/ _' | || |_|
|  __/|_|\____|\__ (_)
|_|            |__/ 
             
play! 2.1-SNAPSHOT, http://www.playframework.org

The new application will be created in /Volumes/Data/code/article/articles

What is the application name? 
> articles

Which template do you want to use for this new application? 

  1             - Create a simple Scala application
  2             - Create a simple Java application
  3             - Create an empty project
  <g8 template> - Create an app based on the g8 template hosted on Github

> 1
OK, application articles is created.

Have fun!
```

## Configuring SBT

In order to use ReactiveMongo and the ReactiveMongo Play Plugin, we will set up the dependencies. Let’s edit project/Build.scala:

> ReactiveMongo와 ReactiveMongo play plugin을 사용하기 위해 우리는 의존관계를 설정합니다. project/build.scala를 수정합시다:

``` scala
import sbt._
import Keys._
import PlayProject._

object ApplicationBuild extends Build {

  val appName         = "mongo-app"
  val appVersion      = "1.0-SNAPSHOT"

  val appDependencies = Seq(
    "reactivemongo" %% "reactivemongo" % "0.1-SNAPSHOT",
    "play.modules.reactivemongo" %% "play2-reactivemongo" % "0.1-SNAPSHOT"
  )

  val main = PlayProject(appName, appVersion, appDependencies, mainLang = SCALA).settings(
    resolvers += "sgodbillon" at "https://bitbucket.org/sgodbillon/repository/raw/master/snapshots/"
  )
}
```

## Configure MongoDB Connection

Before going further, we should enable the ReactiveMongo Play plugin.
Let’s create a play.plugins file in the conf directory:

> 더 진행하기 전에 우리는 ReactiveMongo Play plugin을 사용할 수 있게 해야합니다.
> conf 디렉토리에 play.plugins 파일을 만듭시다:

```
400:play.modules.reactivemongo.ReactiveMongoPlugin
```

Now we should configure it in the conf/application.conf file:

> 이제 우리는 conf/application.conf 파일을 설정해야합니다:

```
# ReactiveMongo Plugin Config
mongodb.servers = ["localhost:27017"]
mongodb.db = "reactivemongo-app"
```

## Model

Our articles have a title, a content and a publisher. We will add a creation date and an update date to be able to sort them by date.

> 우리의 아티클들은 title, content 그리고 publisher를 가지고 있습니다. 우리는 날짜 별로 정렬 할 수 있도록 생성 날짜와 수정 날짜를 추가합니다.

Let’s create a file models/articles.scala and write a case class Article:

> model/articles.scala 파일을 만들고 case class Article을 작성합시다:

``` scala
package models

import org.joda.time.DateTime
import reactivemongo.bson._
import reactivemongo.bson.handlers._

case class Article(
  id: Option[BSONObjectID],
  title: String,
  content: String,
  publisher: String,
  creationDate: Option[DateTime],
  updateDate: Option[DateTime]
)
```

The id field is an Option of BSONObjectID. An ObjectId is a 12 bytes long unique value that is the standard id type in MongoDB documents.

> id 필드는 BSONObjectID의 Option 입니다. ObjectID는 MongoDB 도큐먼트의 표준 id 타입으로 12 바이트의 고유 값입니다.

#### Serializing into BSON / Deserializing from BSON

Now, we may write the BSON serializer and deserializer for this case class. This enables to transform an Article instance into a BSON document that may be stored into the database and vice versa. ReactiveMongo provides two traits, BSONReader[T] and BSONWriter[T], that should be implemented for this purpose.

> 이제 우리는 이 case class를 위한 BSON serializer와 deserializer를 작성할 수 있습니다. 이것은 데이터베이스에 저장할 수 있도록 Article의 인스턴스를 BSON 도큐먼트로 변환해준다. ReactiveMongo는 BSONReader[T]와 BSONWriter[T] 두가지 trait을 제공하는데 이러한 용도로 구현되어야 한다.

Making a BSON document is pretty easy: the method BSONDocument() takes tuples of (String, BSONValue) as arguments. So, producing a very basic document could be written like this:

> BSON 도큐먼트를 만드는 건 매우 간단합니다: BSONDocument() 메서드에 (String, BSONValue) 형식의 tuple들을 인자로 넘겨주면 됩니다. 아주 기본적인 도큐먼트는 다음과 같이 작성할 수 있습니다:

``` scala
BSONDocument(
  "title" -> BSONString("some title"),
  "content" -> BSONString("some content")
)
```

The opposite can be achieved using the method getAs[BSONValue] on a TraversableBSONDocument:

> 다른 방법으로는 TraversableBSONDocument의 getAs[BSONValue] 메서드를 사용하면 됩니다:

``` scala
val title :Option[String] = doc.getAs[BSONString]("title").map(_.value)
val content :Option[String] = doc.getAs[BSONString]("content").map(_.value)
```

Let’s implement ArticleBSONReader[Article] and ArticleBSONWriter[Article] in the same file (models/articles.scala):

> ArticleBSONReader[Article]과 ArticleBSONWriter[Article]을 같은 파일에 구현해 봅시다(models/articles.scala):

``` scala
object Article {
  implicit object ArticleBSONReader extends BSONReader[Article] {
    def fromBSON(document: BSONDocument) :Article = {
      val doc = document.toTraversable
      Article(
        doc.getAs[BSONObjectID]("_id"),
        doc.getAs[BSONString]("title").get.value,
        doc.getAs[BSONString]("content").get.value,
        doc.getAs[BSONString]("publisher").get.value,
        doc.getAs[BSONDateTime]("creationDate").map(dt => new DateTime(dt.value)),
        doc.getAs[BSONDateTime]("updateDate").map(dt => new DateTime(dt.value)))
    }
  }
  implicit object ArticleBSONWriter extends BSONWriter[Article] {
    def toBSON(article: Article) = {
      val bson = BSONDocument(
        "_id" -> article.id.getOrElse(BSONObjectID.generate),
        "title" -> BSONString(article.title),
        "content" -> BSONString(article.content),
        "publisher" -> BSONString(article.publisher))
      if(article.creationDate.isDefined)
        bson += "creationDate" -> BSONDateTime(article.creationDate.get.getMillis)
      if(article.updateDate.isDefined)
        bson += "updateDate" -> BSONDateTime(article.updateDate.get.getMillis)
      bson
    }
  }
}
```

#### Play Form

We will also define a Play Form to handle HTTP form data submission, in the companion object models.Article. It will be useful when we implement edition (in the next article).

> 우리는 또한 동반 객체 models.Article에서 HTTP form 데이타 핸들링을 위한 Play Form을 정의합니다. 그것은 우리가 구현을 할 때 유용할 것입니다(다음 글에서).

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