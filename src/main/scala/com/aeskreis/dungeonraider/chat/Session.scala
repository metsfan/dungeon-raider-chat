package com.aeskreis.dungeonraider.chat

/**
 * Created by Adam on 9/10/14.
*/

import java.util.{Date, UUID}
import play.api.libs.json.JsValue

class Session(id: UUID, user_id: UUID, char_id: UUID, created_at: Date, updated_at: Date,
               online_friends: JsValue)
