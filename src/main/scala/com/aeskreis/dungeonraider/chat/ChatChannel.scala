package com.aeskreis.dungeonraider.chat

import java.util.UUID
import org.apache.commons.io.EndianUtils

import scala.collection.mutable
import com.rabbitmq.client._
import java.io.{DataOutputStream, ByteArrayOutputStream}
import StreamAddins._
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
    EndianUtils.writeSwappedInteger(message, MessageType.Channel.id)
    message.writeUUID(id)
    message.writeUUID(sender.id)
    message.writeCString(sender.username)
    message.writeCString(text)

    members foreach { member =>
      channel.basicPublish("", member.id.toString, false, false, null, message.toByteArray)
    }
  }

  def addMember(user: User) {
    members += user
  }
}
