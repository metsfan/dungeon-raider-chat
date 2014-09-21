package com.aeskreis.dungeonraider.chat

import java.io._
import java.util.UUID

import org.apache.commons.io.EndianUtils
import play.api.libs.json.{JsObject, Json}

import scala.collection.mutable.{Map => MutableMap}
import com.rabbitmq.client._
import StreamAddins._

/**
 * Created by Adam on 9/4/14.
 */

object ServerCommands extends Enumeration {
  val Connect = Value(0)
  val Message = Value(1)
  val Disconnect = Value(2)
  val JoinChannel = Value(3)
  val FriendRequest = Value(4)
  val FriendResponse = Value(5)
  val StatusChange = Value(6)
}

object ClientCommands extends Enumeration {
  val Connected = Value(0)
  val FriendConnected = Value(1)
  val FriendDisconnected = Value(2)
  val IncomingMessage = Value(3)
  val FriendRequested = Value(4)
  val FriendAccepted = Value(5)
  val FriendStatusChange = Value(6)
}

object MessageType extends Enumeration {
  val Channel = Value(0)
  val Direct = Value(1)
  val Error = Value(2)
}

object SocialStatus extends Enumeration {
  val Offline = Value(0)
  val Available = Value(1)
  val Away = Value(2)
  val Busy = Value(3)
}

class Server(listenPort: Int) {
  val mqFactory = new ConnectionFactory
  mqFactory.setHost("localhost")

  val connection = mqFactory.newConnection()

  val consumerChannel = connection.createChannel()
  val producerChannel = connection.createChannel()

  val onlineUsers = MutableMap[UUID, User]()
  val channels = MutableMap[UUID, ChatChannel]()

  def start = {
    consumerChannel.queueDeclare("game_chat_server", false, false, false, null)
    consumerChannel.exchangeDeclare("game_exchange", "direct", false, false, false, null)
    consumerChannel.queueBind("game_chat_server", "game_exchange", "")

    val consumer = new QueueingConsumer(consumerChannel)
    consumerChannel.basicConsume("game_chat_server", true, consumer)

    while (true) {
      val delivery = consumer.nextDelivery()
      val message = new DataInputStream(new ByteArrayInputStream(delivery.getBody))

      val command = ServerCommands(EndianUtils.readSwappedInteger(message))

      command match {
        case ServerCommands.Connect => onConnect(message)
        case ServerCommands.Message => onMessageReceived(message)
        case ServerCommands.Disconnect => onDisconnect(message)
        case ServerCommands.JoinChannel => onJoinChannel(message)
        case ServerCommands.StatusChange => onStatusChange(message)
        case ServerCommands.FriendRequest => onFriendRequest(message)
        case ServerCommands.FriendResponse => onFriendResponse(message)
      }
    }
  }

  def onConnect(stream: DataInputStream) {
    val userId = stream.readUUID()
    val consumerId = stream.readCString()

    User.getUserById(userId, { user =>
      user.consumerId = consumerId
      user.status = SocialStatus.Available.id
      onlineUsers += (userId -> user)

      User.getFriendsForUser(userId, { friends =>
        user.friends = friends

        val onlineFriends = user.friends.map(user => onlineUsers.getOrElse(user.id, null)).filter(_ != null)

        {
          val message = new ByteArrayOutputStream()
          val writer = new DataOutputStream(message)
          EndianUtils.writeSwappedInteger(writer, ClientCommands.FriendConnected.id)
          writer.writeUUID(userId)
          writer.flush()

          onlineFriends.foreach { friend =>
            producerChannel.basicPublish("", friend.consumerId, false, false, null, message.toByteArray)
          }
        }

        {
          val message = new ByteArrayOutputStream()
          val writer = new DataOutputStream(message)

          EndianUtils.writeSwappedInteger(writer, ClientCommands.Connected.id)
          EndianUtils.writeSwappedInteger(writer, onlineFriends.size)
          onlineFriends.foreach { friend =>
            writer.writeUUID(friend.id)
            EndianUtils.writeSwappedInteger(writer, friend.status)
          }
          writer.flush()

          producerChannel.basicPublish("", user.consumerId, false, false, null, message.toByteArray)
        }
      })
    })
  }

  def onMessageReceived(stream: DataInputStream) {
    val senderId = stream.readUUID()
    val sender = onlineUsers(senderId)
    val channelId = stream.readUUID()
    val messageText = stream.readCString()

    val messageType = MessageType(stream.readChar())
    messageType match {
      case MessageType.Channel => {
        val channel = channels(channelId)
        if (channel != null) {
          channel.sendMessage(sender, messageText, producerChannel)
        }
      }

      case MessageType.Direct => {
        val receiverName = stream.readUTF()
        val pair = onlineUsers.find(_._2.username == receiverName)
        if (pair != null) {
          val receiver = pair.get._2
          val message = new ByteArrayOutputStream()
          EndianUtils.writeSwappedInteger(message, MessageType.Direct.id)
          message.writeUUID(sender.id)
          message.writeCString(sender.username)
          message.writeUUID(receiver.id)
          message.writeCString(receiver.username)
          message.writeCString(messageText)

          producerChannel.basicPublish("", sender.consumerId.toString, false, false, null, message.toByteArray)
          producerChannel.basicPublish("", receiver.consumerId.toString, false, false, null, message.toByteArray)
        } else {
          val message = new ByteArrayOutputStream()
          EndianUtils.writeSwappedInteger(message, MessageType.Error.id)
          message.writeCString(Errors.NOT_ONLINE_ERROR(receiverName))

          producerChannel.basicPublish("", sender.consumerId.toString, false, false, null, message.toByteArray)
        }
      }
    }

  }

  def onDisconnect(stream: DataInputStream) {
    val userId = stream.readUUID()
    val user = onlineUsers(userId)
    val onlineFriends = user.friends.map(user => onlineUsers(user.id)).filter(_ != null)

    {
      val message = new ByteArrayOutputStream()
      val writer = new DataOutputStream(message)
      writer.write(ClientCommands.FriendDisconnected.id)
      writer.writeUUID(userId)
      writer.flush()

      onlineFriends.foreach { friend =>
        producerChannel.basicPublish("", friend.consumerId.toString, false, false, null, message.toByteArray)
      }

      message.close()
      writer.close()
    }
  }

  def onJoinChannel(stream: DataInputStream) {
    val userId = stream.readUUID()
    val user = onlineUsers(userId)

    val channelId = stream.readUUID()
    val channelName = stream.readUTF()

    val channel = channels.getOrElse(channelId, ChatChannel(channelId, channelName))
    channel.addMember(user)
  }

  def onStatusChange(stream: DataInputStream) = {
    val userId = stream.readUUID()
    val user = onlineUsers(userId)

    val status = SocialStatus(stream.readInt())

    val message = new ByteArrayOutputStream()
    val writer = new DataOutputStream(message)
    writer.write(ClientCommands.FriendStatusChange.id)
    writer.writeUUID(userId)
    writer.flush()

    val onlineFriends = user.friends.filter(user => onlineUsers.contains(user.id))
    onlineFriends.foreach { friend =>
      producerChannel.basicPublish("", friend.consumerId.toString, false, false, null, message.toByteArray)
    }
  }

  def onFriendRequest(stream: DataInputStream): Unit = {
    val requesterId = stream.readUUID
    val requestedId = stream.readUUID
    val requesterData = stream.readCString

    val requestedUser = onlineUsers.get(requestedId)
    if (requestedUser.isDefined) {
      val message = new ByteArrayOutputStream
      EndianUtils.writeSwappedInteger(message, ClientCommands.FriendRequested.id)

      val jsObj = Json.parse(requesterData).as[JsObject]
      val userObj = (jsObj \ "user").as[JsObject]
      message.writeCString(userObj.toString())

      val requesterUser = onlineUsers.get(requesterId)
      if (requesterUser.isDefined) {
        EndianUtils.writeSwappedInteger(message, requestedUser.get.status)
      } else {
        EndianUtils.writeSwappedInteger(message, SocialStatus.Offline.id)
      }


      producerChannel.basicPublish("", requestedUser.get.consumerId, null, message.toByteArray)
    }
  }

  def onFriendResponse(stream: DataInputStream): Unit = {
    val requesterId = stream.readUUID
    val requestedId = stream.readUUID
    val requestedData = stream.readCString

    val requesterUser = onlineUsers.get(requesterId)
    if (requesterUser.isDefined) {
      val message = new ByteArrayOutputStream
      EndianUtils.writeSwappedInteger(message, ClientCommands.FriendAccepted.id)
      message.writeCString(requestedData)

      val requestedUser = onlineUsers.get(requestedId)
      if (requesterUser.isDefined) {
        EndianUtils.writeSwappedInteger(message, requestedUser.get.status)
      } else {
        EndianUtils.writeSwappedInteger(message, SocialStatus.Offline.id)
      }

      producerChannel.basicPublish("", requesterUser.get.consumerId, null, message.toByteArray)
    }
  }
}
