package models

import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.pattern
import play.api.libs.json.Json
import reactivemongo.bson._
import reactivemongo.play.json.BSONFormats._

case class Article(
		                  _id: Option[BSONObjectID],
		                  title: String,
		                  content: String,
		                  publisher: String,
		                  creationDate: Option[DateTime],
		                  updateDate: Option[DateTime])


object Article extends BSONDocumentReader[Article] with BSONDocumentWriter[Article] {

	// 이런식으로 BSONHandler를 implicit으로 선언하면 BSONDateTime과 DateTime을 묵시적으로 자동 변환할 수 있습니다
	implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
		def read(time: BSONDateTime) = new DateTime(time.value)
		def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
	}

	// BSONDocument Reader와 Writer를 Macros를 이용하면 손쉽게 만들 수 있습니다.
	implicit val reader: BSONDocumentReader[Article] = Macros.reader[Article]
	implicit val writer: BSONDocumentWriter[Article] = Macros.writer[Article]

	// Json 변환을 위한 reader/writer foramt도 선언합니다
	implicit val articleFormat = Json.format[Article]

	// BSONDocument <-> Atricle 사이에 변환을 제공합니다
	override def read(bson: BSONDocument): Article = reader.read(bson)
	override def write(article: Article): BSONDocument = writer.write(article)

	val form = Form(
		mapping(
			"_id" -> optional(text verifying pattern(
				"""[a-fA-F0-9]{24}""".r, error = "error.objectId")),
			"title" -> nonEmptyText,
			"content" -> text,
			"publisher" -> nonEmptyText,
			"creationDate" -> optional(longNumber),
			"updateDate" -> optional(longNumber)) {
			(_id, title, content, publisher, creationDate, updateDate) =>
				Article(
					_id.map(BSONObjectID(_)),
					title,
					content,
					publisher,
					creationDate.map(new DateTime(_)),
					updateDate.map(new DateTime(_)))
		} { article =>
			Some(
				(article._id.map(_.stringify),
						article.title,
						article.content,
						article.publisher,
						article.creationDate.map(_.getMillis),
						article.updateDate.map(_.getMillis)))
		})
}