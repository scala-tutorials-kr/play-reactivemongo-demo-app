import org.joda.time.DateTime
import reactivemongo.bson._
import models._


val bson = BSONDocument(
	"title" -> BSONString("some title"),
	"content" -> BSONString("some content"),
	"publisher" -> BSONString("some publisher")
)
val article = Article.read(bson)

val articleToBson = Article.write(article)

val title :Option[String] = bson.getAs[String]("title")
val content :Option[String] = bson.getAs[String]("content")