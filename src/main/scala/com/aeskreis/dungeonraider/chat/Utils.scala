package com.aeskreis.dungeonraider.chat

import java.util.UUID
import java.io._
import com.typesafe.config.ConfigFactory
import java.nio.ByteBuffer

/**
 * Created by Adam on 9/4/14.
 */
object Utils {

  val config = ConfigFactory.load()

  class UUIDReader(stream: DataInputStream) {

    def readUUID(): UUID = {
      new UUID(stream.readLong(), stream.readLong())
    }
  }

  implicit def streamUUIDReader(stream: DataInputStream) = new UUIDReader(stream)

  class UUIDWriter(stream: DataOutputStream) {

    def writeUUID(uuid: UUID) {
      stream.writeLong(uuid.getMostSignificantBits)
      stream.writeLong(uuid.getLeastSignificantBits)
    }
  }

  implicit def streamUUIDWriter(stream: DataOutputStream) = new UUIDWriter(stream)
}
