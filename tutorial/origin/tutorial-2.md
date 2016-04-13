#  Writing Reactive Apps with ReactiveMongo and Play, Pt. 2

> ReactiveMongo is a brand new Scala driver for MongoDB. More than just yet-another-async-driver, it's a reactive driver that allows you to design very scalable applications unleashing MongoDB capabilities like streaming infinite live collections and files for modern Realtime Web applications.

> ReactiveMongo는 MongoDB를 연결하는 새로운 Scala driver 입니다. 기존의 비동기 방식의 드라이버보다 더 좋은 점은, 실시간 모던 웹에서 MongoDB의 강점인 무제한 Live Collection/File Streaming 을 잘 활용할 수 있도록, 확장성 있는 어플리케이션을 작성하도록 설계된 Reactive 드라이버라는 것입니다.

Play 2.1 has become the main reference for writing web applications in Scala. It is even better when you use a database driver that shares the same vision and capabilities. If you’re planning to write a Play 2.1-based web application with MongoDB as a backend, then ReactiveMongo is the driver for you!

Play 2.1은 Scala 웹 개발의 주요 방법이 되었습니다. 심지어는 동일하게 보고, 할 수 있는 데이터베이스를 사용한다고 할지라도 더 나은 방법입니다. 만약 MongoDB를 백엔드로 한 Play 2.1 웹 어플리케이션을 구축하려고 한다면, ReactiveMongo는 딱 그에 맞는 드라이버입니다.

This article runs you through the process of starting such a project from scratch. We are going to write a simple application that manages articles. Each article has a title, a content, and may embed some attachments (like pictures, PDFs, archives…).

이 글에는 프로젝트를 시작하는 방법을 적을 예정입니다. 우리는 간단한 기사를 관리하는 어플리케이션을 짤 겁니다. 각각의 기사는 제목과 내용으로 구성되며, 첨부 파일도 포함할 수 있습니다. (그림이나 PDF파일 혹은 압축파일..)

## 목차

- 기사 만들기
- 기사 수정하기
- 기사의 목록을 정렬하기
- 다음주에는...


## 기사 만들기

First, we may create a form to create (and edit) articles. Let’s create a view editArticles.scala.html:

가장 먼저, 우리는 기사를 작성할(그리고 수정할) 폼을 만들어야 합니다. view 파일인 editArticles.scala.html 파일을 만들어 봅시다.:

```html
@(form: Form[models.Article])
@import helper.twitterBootstrap._

<div class="row">
  <div class="span8">
  <h2>Add an article</h2>
  @helper.form(action = routes.Articles.create, 'class -> "form-horizontal") {
    @helper.inputText(form("title"))
    @helper.inputText(form("publisher"))
    @helper.textarea(form("content"))
    <div class="form-actions">
      <input class="btn btn-primary" type="submit">
    </div>
  }
  </div>
</div>
```

This template is a function that takes a Form[models.Article] as a parameter. A Form[T], in Play, is a helper that binds the HTTP params. In this case, it will allow us to get and validate an Article sent by the client. The @helper.xxx functions generate the matching form elements (like <form>, <input>, etc.) and fill them with the values it holds, if any.

이 템플릿은 Form[models.Article]을 파라미터로 받는 함수입니다. 플레이에서 Form[T]은 HTTP 파라미터를 바인딩해 주는 도우미의 역할을 합니다. 이 경우, 클라이언트가 보내는 기사를 받고 검증하는 역할을 합니다. @helper.xxx 함수는 매칭되는 form 및 내부 항목들을 만들어 냅니다. (<form>이나 <input> 같은 태그)

In the previous article, we defined a Form[Article]. We will use it in the controller part.

앞선 글에서, 우리는 Form[Article]을 정의했습니다. 이 것은 컨트롤러에서 사용될 겁니다.

The action to show the creation form is very simple. It uses the form we defined on the Article companion object, without filling it with a value, then renders the view.

폼을 만드는 것은 굉장히 간단합니다. 따로 값을 채워넣는 것 없이, Article의 동반 객체로 만들어진 폼을 사용할 겁니다. 그리고 나서 화면을 구성할 겁니다.

```scala
def showCreationForm = Action {
  Ok(views.html.editArticle(Article.form))
}
```

Now, we may handle the creation itself. The first thing we should do is to fill the Article.form with HTTP form data, then check if it is valid.

이제, 우리는 생성되는 과정을 지켜볼 겁니다. 처음 해야되는 일은 Article.form에 HTTP의 form 데이터를 채워넣어야 하는 것입니다. 그리고 나서 그것이 올바른지 검증하게 됩니다.

```scala
def create = Action { implicit request =>
  Article.form.bindFromRequest.fold(
    errors => Ok(views.html.editArticle(None, errors, None)),
    // if no error, then insert the article into the 'articles' collection
    article => // save it!
  )
}
```

To save a document with ReactiveMongo, we use the collection.insert method of signature insert[T](document: T)(implicit writer: RawBSONWriter[T]): Future[LastError].

ReactiveMongo를 사용하여 문서를 저장하려면, 우리는 collection.insert 메소드를 써야 합니다. 이 메소드의 signature는 다음과 같습니다. insert[T](document: T)(implicit writer: RawBSONWriter[T]): Future[LastError].

Before using this, we may ensure that Article.ArticleBSONWriter is the scope by importing it.

이걸 사용하기 전에, 우리는 Article.ArticleBSONWriter 가 import되어서 scope에 포함되어 있는지 확인해야 합니다.

```scala
import models.Article._
```

Before saving the article, we set the creationDate and the updateDate, then we call collection.insert():

기사를 저장하기 전에, 우리는 creationDate와 updateData를 셋팅해야 합니다. 그리고 나서 collection.insert() 메소드를 호출해야 합니다. :

```scala
val updatedArticle = article.copy(creationDate = Some(new DateTime()), updateDate = Some(new DateTime()))
collection.insert(updatedArticle) // returns a Future[LastError]
```

This snippet returns a Future of LastError. But what we want to get is a Result - if fact, we want to redirect back to the index to show the list of articles. Let’s map our Future[LastError] to a Future[Result]:

이 코드는 LastError 객체를 가지고 있는 Future 객체를 리턴합니다. 하지만 우리가 원하는 것은 이게 아니고 Result객체 입니다. - 사실 우리는 기사의 목록을 보는 곳으로 돌아가야 합니다. Future[LastError] 객체에서 Future[Result] 객체로 매핑해 봅시다.:

```scala
val updatedArticle = article.copy(creationDate = Some(new DateTime()), updateDate = Some(new DateTime()))
collection.insert(updatedArticle).map( _ => // we don't care of the lasterror here (map is called on success)
  Redirect(routes.Articles.index)
)
```

… and we wrap this Future of Result in an AsyncResult that can be handled by a Play action. Our action eventually looks like this:

... 그리고 play에서 다루기 위해 Result를 가지고 있는 Future객체를 한 번 싸야 합니다. 이 과정은 아래 코드와 비슷합니다. :

```scala
def create = Action { implicit request =>
  import models.Article._
  Article.form.bindFromRequest.fold(
    errors => Ok(views.html.editArticle(None, errors, None)),
    // if no error, then insert the article into the 'articles' collection
    article => AsyncResult {
      val updatedArticle = article.copy(creationDate = Some(new DateTime()), updateDate = Some(new DateTime()))
      collection.insert(updatedArticle).map( _ => // we don't care of the lasterror here (map is called on success)
        Redirect(routes.Articles.index)
      )
    }
  )
}
```

The last thing we should do is to declare two routes, one for rendering a creation form and the other for saving the created article.

마지막으로 해야 할 것은 2개의 경로를 정의하는 것입니다. 하나는 폼을 만드는 것이고, 다른 하나는 만들어진 기사를 저장하는 것입니다.

```
GET     /articles/new               controllers.Articles.showCreationForm
POST    /articles/new               controllers.Articles.create
```

Now we can post new articles!

이제 새로운 기사를 등록할 수 있습니다.


    ##What is a LastError?

    ## LastError는 무엇인가요?

    When a write operation is done on a collection, MongoDB does not send back a message to confirm that all went right or not. To be sure that a write operation is successful, one must send a GetLastError command. When it receives such a command, MongoDB waits until the last operation is done and then sends back the result. This result is a LastError message. Of course, ReactiveMongo do all this stuff for you by default - so you don’t need to worry about this.

    Collection에 쓰기 작업이 완료되었을 때, MongoDB는 잘 되었는지의 확인 메시지를 보내지 않습니다. 쓰기 작업이 올바로 완료되었는지 확인을 위해, GetLastError 명령을 전송해야 합니다. 이 명령어를 받으면, MongoDB에서는 마지막 작업이 끝나서 결과를 돌려줄 때까지 기다립니다. 이 결과가 LastError 메시지입니다. 물론, ReactiveMongo는 이런 작업을 기본으로 해 줍니다. - 그래서 이런 작업에 대해 신경쓸 필요가 없습니다.



##기사 수정하기

Editing an article is pretty much the same as creating it, except that the article may already exist in the collection. So, before rendering the edition form, we should fetch the article matching the id. If an article is found, the result will be the rendered template; or else it will be NotFound.

기사를 수정하는 것은 기사를 작성하는 것과 거의 비슷합니다, 한 가지 다른 점이라면 기사가 collection에 이미 존재한다는 것 뿐이지요. 그래서 수정 폼을 표시하기 전에, 기사의 id값을 구해와야 합니다. 기사를 찾으면, 렌더링된 템플릿을 결과로 줄 것이고, 아니면 NotFound라는 값을 줄 겁니다.

```scala
def showEditForm(id: String) = Action {
  implicit val reader = Article.ArticleBSONReader
  Async {
    val objectId = new BSONObjectID(id)
    // get the documents having this id (there will be 0 or 1 result)
    val cursor = collection.find(BSONDocument("_id" -> objectId))
    // ... so we get optionally the matching article, if any
    // let's use for-comprehensions to compose futures (see http://doc.akka.io/docs/akka/2.0.3/scala/futures.html#For_Comprehensions for more information)
    for {
      // get a FUTURE OPTION of article
      maybeArticle <- cursor.headOption
      // if there is some article, return a future of result with the article
      result <- maybeArticle.map { article =>
        Ok(views.html.editArticle(Article.form.fill(article)))
      }.getOrElse(Future(NotFound))
    } yield result
  }
}
```

When an article is found, we call fill(article: Article) on Article.form. This function returns a new Form[Article] instance, holding the data of the given article. So, in the view, we can fill the fields of the edition form with the data of the article.

기사를 찾으면, Article.form에 있는 fill(article: Article) 메소드를 호출합니다. 이 함수는 주어진 기사의 내용을 담고 있는 새로운 Form[Article] 객체를 반환합니다. 그래서 기사의 내용을 채운 화면을 보여줄 수 있습니다.

Now, let’s write the edit action that will perform the update operation. The idea is to create a modifier document and call collection.update to update the article.

이제 update 명령어를 수행하는 수정 action을 작성해 봅시다. modifier document를 생성하고, collection.update를 호출해 기사를 수정하는 방법입니다.

```scala
def edit(id: String) = Action { implicit request =>
  Article.form.bindFromRequest.fold(
    errors => Ok(views.html.editArticle(Some(id), errors, None)),
    article => AsyncResult {
      val objectId = new BSONObjectID(id)
      // create a modifier document, ie a document that contains the update operations to run onto the documents matching the query
      val modifier = BSONDocument(
        // this modifier will set the fields 'updateDate', 'title', 'content', and 'publisher'
        "$set" -> BSONDocument(
          "updateDate" -> BSONDateTime(new DateTime().getMillis),
          "title" -> BSONString(article.title),
          "content" -> BSONString(article.content),
          "publisher" -> BSONString(article.publisher)))
      // ok, let's do the update
      collection.update(BSONDocument("_id" -> objectId), modifier).map { _ =>
        Redirect(routes.Articles.index)
      }
    }
  )
}
```

First, we call Article.form.bindFromRequest that produces a new Form[Article] that will be filled with the HTTP form data. Then we call fold on it, that takes two functions as parameters: the first takes a Form[Article] that contains the errors (because the validation failed), and the other takes a valid instance of Article. This fold method will call either the first or the second function depending on the validation status.

제일 먼저, HTTP의 폼 데이터가 채워진 Form[Article] 객체를 만드는 Article.form.bindFromRequest를 호출할 겁니다. 그리고 그것을 나누어, 2개의 함수에 파라미터로 넘겨줄 겁니다. 첫번째는 validation이 실패했을 때의 에러를 포함한 Form[Article] 객체를 파라미터로 받을 것이고, 나머지 하나는 Arcticle의 유효한 인스턴스를 받을 겁니다. fold 함수는 validation 상태에 따라 첫번째나 두번째 함수를 호출할 겁니다.

If the validation fails, all we do is to render the form again with the errors. Else we perform the update operation and redirect to the articles list.

validation이 실패한다면, 우리가 할 수 있는 것은 에러와 함께 폼을 다시 그리는 것밖에 없습니다. 그렇지 않으면 업데이트를 수행하고 기사 목록으로 이동하게 됩니다.

The modifier val is a document containing all the update operations to run on the matched document. Here, we set updateDate to the current date, and title, content and publisher to their new value.

val이라는 것은 해당 문서에 업데이트 정보를 가지고 있는 문서입니다. 여기서, updateDate는 현재 날짜로 셋팅하고, 제목과, 내용, 그리고 작성자를 새로운 값으로 넣어 봅니다.


Let’s declare the matching routes in the conf/routes file:

conf/routes 파일에 새로운 매칭 경로를 지정해 봅시다.

```
GET     /articles/:id               controllers.Articles.showEditForm(id)
POST    /articles/:id               controllers.Articles.edit(id)
```

Last but not least, since we use the same view for the creation and the edition forms, we should switch the post action url. We will do this by adding a new parameter to our view, id, which is an Option[String]. If there is a value, then we are editing an article that already exists.

마지막에, 우리는 생성과 수정 폼을 같이 썼기 때문에, 우리는 action url로 이동해야 합니다. 이것은 view에 새로운 파라미터를 추가함으로 할 수 있습니다. 그것은 Option[String] 타입의 id값입니다. 만약의 값이 있다면, 우리는 현재 존재하는 기사를 고치고 있는 것입니다.

```html
@(id: Option[String], form: Form[models.Article])
@import helper.twitterBootstrap._

<div class="row">
  <div class="span8">
  <h2>Add an article</h2>
  @helper.form(action = (if(!id.isDefined) routes.Articles.create else routes.Articles.edit(id.get)), 'class -> "form-horizontal") {
    // ...
```

Then the create action becomes:

긔리고 나서 생성하는 부분의 액션은 아래와 같이 바낍니다.)

```scala
def showCreationForm = Action {
  Ok(views.html.editArticle(None, Article.form))
}
```

And the showEditForm action:

그리고 showEditForm 액션은 아래와 같습니다.

```scala
def showEditForm(id: String) = Action {
  // ....
      result <- maybeArticle.map { article =>
        Ok(views.html.editArticle(Some(id), Article.form.fill(article)))
      }.getOrElse(Future(NotFound))
    } yield result
  }
}
```

And we’re done!

이제 다 완성되었습니다.


##Sorting the article list

## 기사의 목록을 정렬하기

Until now, our article list is unsorted. Well, that’s not entirely true: it is sorted by the natural order, ie the insertion order. But what if we want to sort them by publisher? Or last edition date?

현재까지, 기사 목록은 정렬되지 않은 상태로 남아 있다. 정확히 말해 그것 사실은 아니다. : 기사는 자연스러운 순서대로 정렬되어 있다. 작성한 순서대로이다. 그러나 만약 작성자로 정렬하고 싶다면? 마지막 수정날짜로 찾고 싶다면?

Let’s say that our index action, which lists the articles, will accept a ‘sort’ parameter. This parameter is a String composed of a field name, optionally prefixed by a ‘-’ character to reverse the order.

index라는 액션을 살펴보자, 기사의 목록을 보여주는 액션 말이다. 이것은 'sort'라는 파라미터를 받을 수 있다. 이 파라미터는 필드 이름으로 이루어진 문자열이다. 때로 '-'로 시작할때는 역순으로 정렬하는 것을 빼면 말이다.

For example, if we want to sort by updateDate, our URL will look like this:

예를 들어, 만약 updateDate로 정렬하고 싶다면, URL은 아래와 같을 것이다.

```
/?sort=updateDate
```

And if we want to get the more recently edited articles first:

그리고 만약 최근 것을 먼저 오도록 정렬하고 싶다면 :

```
/?sort=-updateDate
```

In MongoDB, such a request would be written this way:

MongoDB에서는 이런 요청은 아래처럼 쓰여진다.

```
db.articles.find({
  $query: {},
  $sort: {
    updateDate: 1 // or -1 for reverse order
  }
})
```

First, let’s define a function that will handle the sort parameter. This function returns an option of BSONDocument: if there is a sort parameter, then some BSONDocument will be returned.

먼저, 정렬 파라미터를 다루는 함수를 하나 만들자. 이 함수는 BSONDocument를 포함하는 Option 객체를 반환한다. 만약 정렬 파라미터가 있고, BSONDocument가 반환된다면..

```scala
private def getSort(request: Request[_]) = {
  request.queryString.get("sort").map { fields =>
    val orderBy = BSONDocument()
    for(field <- fields) {
      val order = if(field.startsWith("-"))
        field.drop(1) -> -1
      else field -> 1

      if(order._1 == "title" || order._1 == "publisher" || order._1 == "creationDate" || order._1 == "updateDate")
        orderBy += order._1 -> BSONInteger(order._2)
    }
    orderBy
  }
}
```

This function is very simple: we get all the values of sort. For each of them, we extract the order (1 or -1) by getting the first character and checking if it is a -. Then, we add this field name and its sort value into the resulting document.

이 함수는 매우 간단하다. 우리는 정렬하는 데 필요한 모든 것을 다 얻었다. 정렬할 때마다 첫번째 문자를 보고 '-'가 있는지를 판단해서 정렬의 순서를 정할 것이다. 그리고 나서 이 필드명과 정렬값을 결과 문서에 추가할 것이다.

That was the hardest part ;) All we need to do now is to use this function and, if there is some sort document, add it to our query. Now, our index action looks like this:

이것은 어려운 부분이다. 우리가 지금 하는 것은 이 함수를 사용하는 것 뿐이다. 만약 정렬 문서가 있다면, 이것을 쿼리에 추가해라, index라는 액션은 아래와 같다.

```scala
// list all articles and sort them
def index = Action { implicit request =>
  Async {
    implicit val reader = Article.ArticleBSONReader
    // empty query to match all the documents
    val query = BSONDocument()
    val sort = getSort(request)
    if(sort.isDefined) {
      // build a selection document with an empty query and a sort subdocument ('$orderby')
      query += "$orderby" -> sort.get
      query += "$query" -> BSONDocument()
    }
    val activeSort = request.queryString.get("sort").flatMap(_.headOption).getOrElse("none")
    // the future cursor of documents
    val found = collection.find(query)
    // build (asynchronously) a list containing all the articles
    found.toList.map { articles =>
      Ok(views.html.articles(articles, activeSort))
    }
  }
}
```

And we can add some links to our list view to sort it:

그리고 리스트를 정렬해서 볼 수 있는 링크를 추가할 수 있다.

```scala
@(articles: List[models.Article])
<ul class="sort">
  <li><a href="?sort=publisher">Sort by publisher</a></li>
  <li><a href="?sort=-publisher">Sort by publisher (reverse)</a></li>
  <li><a href="?sort=updateDate">Sort by update date</a></li>
  <li><a href="?sort=-updateDate">Sort by update date (reverse)</a></li>
</ul>
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

##Coming next week

## 다음주에는

In the next article, we will add the attachments management feature to this application using GridFS.

다음 글에서는, GridFS를 이용해서 첨부파일 관리를 해볼 것이다.

Meanwhile, you can grab the complete application and start hacking with it.

그동안 완벽한 어플리케이션을 만들고, 그것을 가지고 놀고 싶을 것이다.

Don’t hesitate to post your questions and comments to the ReactiveMongo Google Group.

ReactiveMongo Google Group에 질문하고 댓글을 달아라.