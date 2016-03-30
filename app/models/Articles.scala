package models

import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints.pattern
import reactivemongo.bson._

case class Article(
		                  id: Option[BSONObjectID],
		                  title: String,
		                  content: String,
		                  publisher: String,
		                  creationDate: Option[DateTime],
		                  updateDate: Option[DateTime])

object Article extends AnyRef
		with BSONDocumentReader[Article] with BSONDocumentWriter[Article] {

	// 이런식으로 implicit을 이용해  BSONDateTime과 DateTime을 묵시적으로 변환할 수 있습니다
	implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
		def read(time: BSONDateTime) = new DateTime(time.value)

		def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
	}

	// Reader와 Writer를 Macros를 이용해 만들 수 있습니다
	private implicit val reader: BSONDocumentReader[Article] = Macros.reader[Article]
	private implicit val writer: BSONDocumentWriter[Article] = Macros.writer[Article]

	override def read(bson: BSONDocument): Article = reader.read(bson)

	override def write(article: Article): BSONDocument = writer.write(article)

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
}