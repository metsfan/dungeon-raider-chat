package com.aeskreis.dungeonraider.chat

import java.util.UUID
import scala.collection.mutable
import com.rabbitmq.client._
import java.io.{DataOutputStream, ByteArrayOutputStream}
import Utils._
import java.io.IOException
import scala.throws

/**
 * Created by Adam on 9/11/14.
 */
case class ChatChannel(id: UUID,
                        name: String) {
  var members = mutable.MutableList[User]()

  def sendMessage(sender: User, text: String, channel: Channel) {
    val message = new ByteArrayOutputStream()
    val writer = new DataOutputStream(message)
    writer.writeChar(MessageType.Channel.id)
    writer.writeUUID(id)
    writer.writeUUID(sender.id)
    writer.writeUTF(sender.username)
    writer.writeUTF(text)
    writer.flush()

    members foreach { member =>
      channel.basicPublish("", member.id.toString, false, false, null, message.toByteArray)
    }
  }

  def addMember(user: User) {
    members += user
  }
}
