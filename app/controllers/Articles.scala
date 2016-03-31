package controllers

import scala.concurrent.Future
import com.google.inject.Inject

import play.api.mvc.{Action, Controller}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.modules.reactivemongo.{ReactiveMongoApi, ReactiveMongoComponents, MongoController}
import reactivemongo.api.collections.bson.BSONCollection
import reactivemongo.bson.BSONDocument

import models.Article

class Articles @Inject()(val reactiveMongoApi: ReactiveMongoApi)
		extends Controller with MongoController with ReactiveMongoComponents {

	val collection = db[BSONCollection]("articles")

	def index = Action.async { implicit request =>
		// empty query to match all the documents
		val query = BSONDocument()
		// the future cursor of documents
		val cursor = collection.find(query).cursor[Article]()
		// gather all the JsObjects in a list
		val futureList: Future[List[Article]] = cursor.collect[List]()

		futureList.map { articles =>
			Ok(views.html.articles(articles))
		} recover {
			case _ => InternalServerError
		}
	}

}
