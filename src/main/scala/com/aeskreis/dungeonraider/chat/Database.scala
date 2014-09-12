package com.aeskreis.dungeonraider.chat

import com.jolbox.bonecp.{BoneCP, BoneCPConfig}
import java.sql.Connection

/**
 * Created by Adam on 9/9/14.
 */
object Database {
  private val host = Utils.config.getString("db.host")
  private val port = Utils.config.getString("db.port")
  private val database = Utils.config.getString("db.database")

  Class.forName("org.postgresql.Driver")

  val config = new BoneCPConfig()
  config.setJdbcUrl(s"""jdbc:postgresql://$host:$port/$database""")
  config.setUsername(Utils.config.getString("db.username"))
  config.setPassword(Utils.config.getString("db.password"))
  config.setMaxConnectionsPerPartition(10)
  config.setPartitionCount(1)

  val connectionPool = new BoneCP(config)

  def getConnection: Connection = {
    connectionPool.getConnection
  }
}
