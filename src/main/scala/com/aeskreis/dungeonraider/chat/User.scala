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
  var consumerId: String = ""
  var status: Int = SocialStatus.Offline.id
}

object User {

  implicit val userReads = new Reads[User] {
    override def reads(json: JsValue): JsResult[User] = {
      val id = UUID.fromString((json \ "id").as[String])
      val username = (json \ "username").as[String]
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
    val request = url("http://" + hostname + "/friends/" + id.toString)
    val user = Http(request OK as.String)
    for (u <- user) {
      val response = Json.parse(u)
      val friends = (response \ "users").asOpt[List[JsObject]].getOrElse(null)
      if (friends != null) {
        success(friends.map(Json.fromJson(_).get))
      }
    }
  }
}
