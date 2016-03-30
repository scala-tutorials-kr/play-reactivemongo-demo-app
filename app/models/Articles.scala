package models

import org.joda.time.DateTime
import reactivemongo.bson.BSONObjectID

case class Article(
    id: Option[BSONObjectID],
    title: String,
    content: String,
    publisher: String,
    creationDate: Option[DateTime],
    updateDate: Option[DateTime])


