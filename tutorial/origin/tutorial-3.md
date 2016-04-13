#Writing Reactive Apps with ReactiveMongo and Play, Pt. 3 - GridFS

>  ReactiveMongo는 MongoDB를 연결하는 새로운 Scala driver 입니다. 기존의 비동기 방식의 드라이버보다 더 좋은 점은, 실시간 모던 웹에서 MongoDB의 강점인 무제한 Live Collection/File Streaming 을 잘 활용할 수 있도록, 확장성 있는 어플리케이션을 작성하도록 설계된 Reactive 드라이버라는 것입니다.

In the previous article, we saw how to insert and update documents, and to do more complex queries like sorting documents.

이전 연재글에서는 문서를 생성하고 고치는 것을 해 보았습니다. 그리고 문서를 정렬하는 것 같이 조금 복잡한 쿼리도 작성해 보았습니다

There is one feature that our application lacks sor far: the ability to upload attachments. For this purpose we will use GridFS, a protocol over MongoDB that handles file storage.

이 어플리케이션에 부족한 게 하나 있습니다. : 첨부파일을 올리는 기능이지요. 이것을 하기 위해 우리는 GridFS를 사용할 겁니다. 이 것은 MongoDB에서 파일을 다루는 프로토콜이지요.

#### How does GriFS work?

#### GridFS는 어떻게 동작합니까?

When you save some file with GridFS, you are actually dealing with two collections: files and chunks. The metadata of the file are saved as a document into the files collection, while the contents are splitted into chunks (usually of 256kB) that are stored into the chunks collection. Then one can find files by performing regular queries on files, and retrieve their contents by merging all their chunks.

GridFS를 이용해 파일을 저장할 때에는 2개의 컬렉션을 다루게 될 겁니다: 파일과 청크이지요. 파일에 메타데이터는 문서 안에 files 컬렉션으로 저장됩니다. 파일의 내용은 조각으로 쪼개져 chunks 컬렉션으로 저장됩니다. (대부분 256kb 단위로 쪼개집니다) 그러면 정규표현식을 files 컬렉션에 날려 파일을 찾고, 그 것의 조각을 모아 파일의 내용을 구해올 수 있습니다.

## Summary

- Using GridFS with ReactiveMongo
	- A Look to ReactiveMongo's GridFS API
- Using ReactiveMongo GridFS Streaming Capability with Play Framework
	- Save a File into GridFS, the Streaming Way
	- Stream a File to the Client
- What's Next?

#### Using GridFS with ReactiveMongo

One of the main design principles of ReactiveMongo is that all the documents can be streamed both from and into MongoDB. The streaming ability is implemented with the Producer/Consumer pattern in its immutable version - as known as the Enumerator/Iteratee pattern:

ReactiveMongo 설계의 기본 원칙중 하나는, 모든 문서는 MongoDB를 거쳐서 접근해야 하는 것입니다(Streaming). Streaming은 변경 불가능한 Producer/Consumer 패턴으로 구현되어 있습니다. - Enumerator/Iterator 패턴에서 알고 있듯이 말이죠.

- an `Iteratee` is a consumer of data: it processes chunks of data. An `Iteratee` instance is in one of the following states: done (meaning that it may not process more chunks), cont (it accepts more data), and error (an error has occurred). After each step, it returns an new instance of Iteratee that may be in a different state;

- `Iteratee`는 데이터를 소비하는 자 입니다. : 그것은 데이터의 조각에 접근합니다. `Iteratee` 인스턴스는 다음 상태 중 하나를 갖습니다: done(더 이상 chunk를 필요로 하지 않음), cont (더 많은 데이터가 필요함 ), error(에러가 발생함). 각각의 과정을 거친 후 서로 다른 상태를 가지고 있는 Iteratee 객체를 리턴합니다

- an `Enumerator` is a producer, or a source, of data: applied on an iteratee instance, it will feed it (depending on its state).

- `Enumerator` 는 생산자 혹은 데이터의 원천입니다. : Iteratee 인스턴스에 적용되며, 그 쪽을 도와줍니다. (그것의 상태에 따라서)

Consider the case of retrieving a lot of documents. Building a huge list of documents in your application may fill up the memory quickly, so this is not an option. You could get a lazy iterator of documents, but the problem is that it’s blocking. And its non-blocking counterpart - an iterator of future documents - may be difficult to handle. That’s where Iteratees and Enumerators come to the rescue. The idea is to see the MongoDB server as the procucer (so, an Enumerator), and your code using these documents as the consumer.

여러 개의 문서를 받아오는 경우를 생각해 봅시다. 어플리케이션에서 많은 양의 문서 목록을 생성하는 작업을 하면 금방 메모리가 가득차게 될 겁니다. 그래서 다른 방법이 필요합니다. lazy 방식의 문서 목록을 받아와야 합니다. 하지만 문제가 하나 더 있습니다. Blocking을 한다는 것이죠. Future객체의 Iterator를 만들어 Non-blocking으로 바꾸는 것은 작업이 어렵습니다. That’s where Iteratees and Enumerators come to the rescue. 해결방법은 MongoDB서버를 생산자로 생각하고, 이 문서를 다루는 코드를 소비자로 생각하는 것입니다

The same vision can apply to GridFS too. When storing a file, GridFS is the consumer (so we will use an Iteratee); when reading one, it is seen as a producer, so an Enumerator.

GridFS의 방식도 동일합니다. 파일을 저장할 때 GridFS는 소비자 입니다. (그래서 Iteratee를 이용합니다)  파일을 읽을 때 이 것은 생산자로 간주됩니다. 그래서 Enumerator를 이용합니다.

Using Iteratees and Enumerators, you can stream files all along, from the client to the server and the database, keeping a low memory usage and in a completely non-blocking way.

Iteratees와 Enumerators를 이용하면서, 클라이언트에서 서버로, 데이터베이스로, 파일만을 단독으로 서비스할 수 있습니다. 메모리를 적게 사용하고 Non-blocking방법을 사용하면서 말이죠.

#### A Look to ReactiveMongo’s GridFS API

The `GridFS.save` is used to get an `Iteratee`:

`GridFS.save` 는 `Iteratee`를 이용합니다.

``` scala
/**
 * Saves a file with the given name.
 *
 * If an id is provided, the matching file metadata will be replaced.
 *
 * @param name the file name.
 * @param id an id for the new file. If none is provided, a new ObjectId will be generated.
 *
 * @return an iteratee to be applied to an enumerator of chunks of bytes.
 */
def save(name: String, id: Option[BSONValue], contentType: Option[String] = None)(implicit ctx: ExecutionContext) :Iteratee[Array[Byte], Future[ReadFileEntry]]
```

This `Iteratee` will be fed by an `Enumerator[Array[Byte]]` - the contents of the file.

여기서 `Iteratee` 는 `Enumerator[Array[Byte]]`를 넘겨줍니다. - 이 것은 파일의 내용입니다.

When the file is entirely saved, a `Future[ReadFileEntry]` may be retrieved. This is the metadata of the file, including the id (generally a `BSONObjectID`).

파일 저장이 완료될 때, `Future[ReadFileEntry]` 객체를 받을 겁니다. 이 것은 id를 포함한 파일의 메타데이터 입니다. (여기서 id는 일반적으로 `BSONObjectID` 입니다.)

``` scala
/**
 * Metadata of a file.
 */
trait FileEntry {
  /** File name */
  val filename: String
  /** length of the file */
  val length: Int
  /** size of the chunks of this file */
  val chunkSize: Int
  /** the date when this file was uploaded. */
  val uploadDate: Option[Long]
  /** the MD5 hash of this file. */
  val md5: Option[String]
  /** mimetype of this file. */
  val contentType: Option[String]
  /** the GridFS store of this file. */
  val gridFS: GridFS
}


/** Metadata of a file that exists as a document in the `files` collection. */
trait ReadFileEntry extends FileEntry {
  /** The id of this file. */
  val id: BSONValue

  // ...
}
```

On a `ReadFileEntry`, we can call a function named `enumerate` that returns an `Enumerator[Array[Byte]]`… and the circle is complete.

`ReadFileEntry`에서, 우리는 `enumerate`라는 함수를 호출합니다. 이 함수는 `Enumerator[Array[Byte]]`를 리턴합니다 그리고 이 한 사이클이 완료되었습니다.

``` scala
/** Produces an enumerator of chunks of bytes from the `chunks` collection. */
def enumerate(implicit ctx: ExecutionContext) :Enumerator[Array[Byte]]
```

For example, here is a way to store a plain old java.io.File into GridFS:

GridFS를 통해서 java.io.File을 저장하는 방법은 아래와 같습니다 :

``` scala
// a function that saves a file into a GridFS storage and returns its id.
def writeFile(file: File, mimeType: Option[String]) :Future[BSONValue] = {
  // on a database instance
  val gfs = new GridFS(db)

  // gets an enumerator from this file
  val enumerator = Enumerator.fromFile(file)

  // gets an iteratee to be fed by the enumerator above
  val iteratee = gfs.save(file.getName(), None, mimeType)

  // we get the final future holding the result (the ReadFileEntry)
  val future = Iteratee.flatten(enumerator(iteratee)).run
  // future is a Future[Future[ReadFileEntry]], so we have to flatten it
  val flattenedFuture = future.flatMap(f => f)
  // map the value of this future to the id
  flattenedFuture.map { readFileEntry =>
    println("Successfully inserted file with id " + readFileEntry.id)
    readFileEntry.id
  }
}
```

#### Using ReactiveMongo GridFS Streaming Capability with Play Framework

So, how can we use this for our attachments?

그래서 이 것을 첨부문서에 어떻게 적용할 수 있을까요?

__Save a file into GridFS, the streaming way__

First, let’s take a look at how file upload is handled in Play Framework. This is achieved with a [BodyParser](https://playframework.com/documentation/2.0/ScalaFileUpload).

먼저 플레이 프레임워크에서 파일이 어떻게 업로드되는지 살펴봅시다. 이 것은 [BodyParser](https://playframework.com/documentation/2.0/ScalaFileUpload) 라는 것을 통해 이루어집니다.

A `BodyParser` is function that handles the body of request. For example, files are often sent with `multipart/form-data` (using a HTML form element).

`BodyParser`는 요청의 body를 다루는 함수입니다. 예를 들면, 보통 파일은 `multipart/form-data` 로 전송됩니다 (html에 form 객체로 전송될 경우)

A regular file upload in Play looks like this:

플레이에서는 보통 파일을 업로드할 때 아래처럼 코드를 작성합니다.:

``` scala
def upload = Action(parse.multipartFormData) { request =>
  request.body.file("file").map { file =>
    // do something with the file...
    Ok("File uploaded")
  }.getOrElse {
    Redirect(routes.Application.index).flashing(
      "error" -> "Missing file"
    )
  }
}
```

Obviously, you don’t need to write your own BodyParser for ReactiveMongo. You can simply use the one that is provided in the [Play ReactiveMongo Plugin](https://github.com/ReactiveMongo/Play-ReactiveMongo). It is defined on the `MongoController` trait, which is a mixin for Controllers.

물론 ReactiveMongo를 통해서 이 BodyParser를 작성할 필요는 없습니다. 이 것은 [Play ReactiveMongo Plugin](https://github.com/ReactiveMongo/Play-ReactiveMongo)을 사용하면 됩니다. 이 것은 `MongoController` 트레이트에 정의되어 있습니다. 이 트레이트는 Controllers에 Mixin 됩니다.

``` scala
/**
 * A mixin for controllers that will provide MongoDB actions.
 */
trait MongoController {
  self :Controller =>
  // ...
  /**
   * Gets a body parser that will save a file sent with multipart/form-data into the given GridFS store.
   */
  def gridFSBodyParser(gfs: GridFS)(implicit ec: ExecutionContext) :BodyParser[MultipartFormData[Future[ReadFileEntry]]]
  // ...
}
```

Thanks to this function, all we need to do in our controller is to provide this `BodyParser` to the Action:

이 함수 덕분에, 우리는 액션에 이 `BodyParser`를 넣기만 하면 됩니다.

```scala
def saveAttachment(id: String) = Action(gridFSBodyParser(gridFS)) { request =>
    val future :Future[ReadFileEntry] = request.body.files.head.ref
    // ...
}
```

Simple, right?

간단하죠, 그렇지 않나요?

In our case, we will need to update the `ReadFileEntry`to add a metadata: the article which this attachment belongs to. This can be achieved with a for-comprehension:

이 경우 메타데이터를 추가하기 위해 `ReadFileEntry`를 좀 보완해야 합니다.: 이 첨부문서가 있는 글의 메타데이터 말이죠. 이 것은 for 문법으로 작성이 가능합니다.

```scala
// save the uploaded file as an attachment of the article with the given id
def saveAttachment(id: String) = Action(gridFSBodyParser(gridFS)) { request =>
  // the reader that allows the 'find' method to return a future Cursor[Article]
  implicit val reader = Article.ArticleBSONReader
  // first, get the attachment matching the given id, and get the first result (if any)
  val cursor = collection.find(BSONDocument("_id" -> new BSONObjectID(id)))
  val uploaded = cursor.headOption

  val futureUpload = for {
    // we filter the future to get it successful only if there is a matching Article
    article <- uploaded.filter(_.isDefined).map(_.get)
    // we wait (non-blocking) for the upload to complete. (This example does not handle multiple files uploads).
    putResult <- request.body.files.head.ref
    // when the upload is complete, we add the article id to the file entry (in order to find the attachments of the article)
    result <- gridFS.files.update(BSONDocument("_id" -> putResult.id), BSONDocument("$set" -> BSONDocument("article" -> article.id.get)))
  } yield result

  Async {
    futureUpload.map {
      case _ => Redirect(routes.Articles.showEditForm(id))
    }.recover {
      case _ => BadRequest
    }
  }
}
```

#### Stream a File to the Client

The Play Plugin also provides a `serve` function that will stream the first `ReadFileEntry` available in the `Cursor` instance given as a parameter:

플레이 플러그인은 `server`라는 함수도 제공합니다. 이 함수는 `Cursor`객체가 처음 접근할 수 있는 `ReadFileEntry`를 파라미터로 받습니다.

```scala
def serve(foundFile: Cursor[ReadFileEntry])(implicit ec: ExecutionContext) :Future[Result]
```

It returns a regular Play `Result`. The only thing to do is to look for an attachment matching the id with `gridFS.find(...)`, and give the resulting `Cursor[ReadFileEntry]` to the serve function:

여기서는 보통 Play에서 사용하는 `Result`객체를 반환합니다. 이제 해야 할 것은 `gridFS.find(...)`를 통해서 매칭된 id를 가지고 첨부파일을 찾는 것입니다. 그리고 serve 함수에 `Cursor[ReadFileEntry]`를 결과로 넘겨주면 됩니다.

```scala
def getAttachment(id: String) = Action {
  Async {
    serve(gridFS.find(BSONDocument("_id" -> new BSONObjectID(id))))
  }
}
```

## What’s Next?

That’s the last article of this series. But we did not cover all the features of ReactiveMongo, like bulk insert, using capped collections, run commands… So many topics that will be covered in future articles :)

이 게 이번 연재의 마지막입니다. 우리는 아직 bulk insert나 capped collections, run command 같은 ReactiveMongo의 특징을 다루지 않았습니다. 그래서 앞으로 이런 것 설명하는 더 많은 글이 올라올 겁니다.

Meanwhile, you can browse the [Scaladoc](http://reactivemongo.org/releases/0.11/api/index.html#package), post your questions and comments to the [ReactiveMongo](https://groups.google.com/forum/#!forum/reactivemongo) Google Group. And, of course, you are invited to grab [the complete application](https://github.com/sgodbillon/reactivemongo-demo-app) and start hacking with it!

[Scaladoc](http://reactivemongo.org/releases/0.11/api/index.html#package)을 참고하시면 됩니다. 질문이나 기타 글이 있으면 [ReactiveMongo](https://groups.google.com/forum/#!forum/reactivemongo) Google Group에 올려주세요. 물론 [이 어플리이션](https://github.com/sgodbillon/reactivemongo-demo-app)에 와서 자유롭게 이용하고 즐기면 됩니다.
