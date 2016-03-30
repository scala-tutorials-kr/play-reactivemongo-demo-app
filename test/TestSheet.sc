import reactivemongo.bson.{BSONString, BSONDocument}

val doc = BSONDocument(
	"title" -> BSONString("some title"),
	"content" -> BSONString("some content"))

doc.getAs[BSONString]("title")

doc.getAs[BSONString]("title").get

doc.getAs[BSONString]("title").get.value