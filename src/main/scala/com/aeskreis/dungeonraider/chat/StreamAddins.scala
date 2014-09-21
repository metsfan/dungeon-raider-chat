package com.aeskreis.dungeonraider.chat

import java.io.{OutputStream, InputStream, DataOutputStream, DataInputStream}
import java.util.UUID

/**
 * Created by Adam on 9/20/2014.
 */

object StreamAddins {
  class UUIDReader(stream: InputStream) {

    def readUUID(): UUID = {
      UUID.fromString(readCString())
    }

    def readCString(): String = {
      val stringBuilder = new StringBuilder

      var char: Byte = 0
      do {
        char = stream.read().toByte
        if (char != 0) {
          stringBuilder.append(char.toChar)
        }
      } while(char != 0)

      stringBuilder.result()
    }
  }

  implicit def streamUUIDReader(stream: InputStream) = new UUIDReader(stream)

  class UUIDWriter(stream: OutputStream) {

    def writeUUID(uuid: UUID) {
      writeCString(uuid.toString)
    }

    def writeCString(string: String) {
      stream.write(string.getBytes("UTF-8"))
      stream.write(0)
    }
  }

  implicit def streamUUIDWriter(stream: OutputStream) = new UUIDWriter(stream)
}

