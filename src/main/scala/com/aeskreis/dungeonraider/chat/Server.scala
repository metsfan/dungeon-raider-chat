package com.aeskreis.dungeonraider.chat

import java.io._
import java.util.UUID
import scala.collection.mutable.{Map => MutableMap}
import com.rabbitmq.client._
import Utils._

/**
 * Created by Adam on 9/4/14.
 */

object ServerCommands extends Enumeration {
  val Connect = Value(0)
  val Message = Value(1)
  val Disconnect = Value(2)
  val JoinChannel = Value(3)
}

object ClientCommands extends Enumeration {
  val Connected = Value(0)
  val FriendConnected = Value(1)
  val FriendDisconnected = Value(2)
  val IncomingMessage = Value(3)
}

object MessageType extends Enumeration {
  val Channel = Value(0)
  val Direct = Value(1)
  val Error = Value(2)
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

      val command = ServerCommands(message.readInt())

      command match {
        case ServerCommands.Connect => onConnect(message)
        case ServerCommands.Message => onMessageReceived(message)
        case ServerCommands.Disconnect => onDisconnect(message)
        case ServerCommands.JoinChannel => onJoinChannel(message)
      }
    }
  }

  def onConnect(stream: DataInputStream) {
    val userId = stream.readUUID()
    User.getUserById(userId, { user =>
      onlineUsers += (userId -> user)

      User.getFriendsForUser(userId, { friends =>
        user.friends = friends

        val onlineFriends = user.friends.filter(user => onlineUsers.contains(user.id))

        {
          val message = new ByteArrayOutputStream()
          val writer = new DataOutputStream(message)
          writer.write(ClientCommands.FriendConnected.id)
          writer.writeUUID(userId)
          writer.flush()

          onlineFriends.foreach { friend =>
            producerChannel.basicPublish("", friend.id.toString, false, false, null, message.toByteArray)
          }
        }

        {
          val message = new ByteArrayOutputStream()
          val writer = new DataOutputStream(message)

          onlineFriends.foreach(friend => writer.writeUUID(friend.id))
          writer.flush()

          producerChannel.basicPublish("", user.id.toString, false, false, null, message.toByteArray)
        }
      })
    })
  }

  def onMessageReceived(stream: DataInputStream) {
    val senderId = stream.readUUID()
    val sender = onlineUsers(senderId)
    val channelId = stream.readUUID()
    val messageText = stream.readUTF()

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
          val writer = new DataOutputStream(message)
          writer.writeChar(MessageType.Direct.id)
          writer.writeUUID(sender.id)
          writer.writeUTF(sender.username)
          writer.writeUUID(receiver.id)
          writer.writeUTF(receiver.username)
          writer.writeUTF(messageText)
          writer.flush()

          producerChannel.basicPublish("", sender.id.toString, false, false, null, message.toByteArray)
          producerChannel.basicPublish("", receiver.id.toString, false, false, null, message.toByteArray)
        } else {
          val message = new ByteArrayOutputStream()
          val writer = new DataOutputStream(message)
          writer.writeChar(MessageType.Error.id)
          writer.writeUTF(Errors.NOT_ONLINE_ERROR(receiverName))

          producerChannel.basicPublish("", sender.id.toString, false, false, null, message.toByteArray)
        }
      }
    }

  }

  def onDisconnect(stream: DataInputStream) {
    val userId = stream.readUUID()
    val user = onlineUsers(userId)
    val onlineFriends = user.friends.filter(user => onlineUsers.contains(user.id))

    {
      val message = new ByteArrayOutputStream()
      val writer = new DataOutputStream(message)
      writer.write(ClientCommands.FriendDisconnected.id)
      writer.writeChars(userId.toString)
      writer.flush()

      onlineFriends.foreach { friend =>
        producerChannel.basicPublish("", friend.id.toString, false, false, null, message.toByteArray)
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
}
