package com.aeskreis.dungeonraider.chat

import java.util.UUID
import dispatch._, Defaults._
import play.api.libs.json._
import scala.collection.mutable

/**
 * Created by Adam on 9/4/14.
 */
case class User(id: UUID,
                 username: String) {
  var friends = List[User]()
}

object User {

  implicit val userReads = new Reads[User] {
    override def reads(json: JsValue): JsResult[User] = {
      val id = UUID.fromString((json \ "id").toString())
      val username = (json \ "username").toString()
      JsSuccess(User(id, username))
    }
  }

  def getUserById(id: UUID, success: User => Unit) {
    val hostname = Utils.config.getString("api.hostname")
    val request = url("http://" + hostname + "/user/" + id.toString)
    val user = Http(request OK as.String)
    for (u <- user) {
      val response = Json.parse(u)
      val user = (response \ "user").asOpt[JsObject].getOrElse(null)
      if (user != null) {
        success(Json.fromJson(user).get)
      }
    }
  }

  def getFriendsForUser(id: UUID, success: List[User] => Unit) {
    val hostname = Utils.config.getString("api.hostname")
    val request = url("http://" + hostname + "/user/" + id.toString)
    val user = Http(request OK as.String)
    for (u <- user) {
      val response = Json.parse(u)
      val friends = (response \ "friends").asOpt[List[JsObject]].getOrElse(null)
      if (friends != null) {
        success(friends.map(Json.fromJson(_).get))
      }
    }
  }
}
