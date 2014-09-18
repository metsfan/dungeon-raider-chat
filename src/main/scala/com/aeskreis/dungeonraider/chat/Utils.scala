package com.aeskreis.dungeonraider.chat

import java.util.{Scanner, UUID}
import java.io._
import com.typesafe.config.ConfigFactory
import java.nio.ByteBuffer

import org.apache.commons.io.EndianUtils


/**
 * Created by Adam on 9/4/14.
 */
object Utils {

  val config = ConfigFactory.load()

  class UUIDReader(stream: DataInputStream) {

    def readUUID(): UUID = {
      UUID.fromString(readCString())
    }

    def readCString(): String = {
      val stringBuilder = new StringBuilder

      var char: Byte = 0
      do {
        char = stream.readByte()
        if (char != 0) {
          stringBuilder.append(char.toChar)
        }
      } while(char != 0)

      stringBuilder.result()
    }
  }

  implicit def streamUUIDReader(stream: DataInputStream) = new UUIDReader(stream)

  class UUIDWriter(stream: DataOutputStream) {

    def writeUUID(uuid: UUID) {
      writeCString(uuid.toString)
    }

    def writeCString(string: String) {
      stream.write(string.getBytes("UTF-8"))
      stream.writeByte(0)
    }
  }

  implicit def streamUUIDWriter(stream: DataOutputStream) = new UUIDWriter(stream)
}
