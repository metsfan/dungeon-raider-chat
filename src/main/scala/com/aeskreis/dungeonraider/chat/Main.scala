package com.aeskreis.dungeonraider.chat

import com.typesafe.config.ConfigFactory

/**
 * Created by Adam on 9/4/14.
 */

object Main {
  def main(args: Array[String]) {

    val conn = Database.getConnection
    val rs = conn.createStatement().executeQuery("SELECT version() as version")
    rs.next()
    println(rs.getString("version"))

    val server = new Server(5200)
    server.start
  }
}
