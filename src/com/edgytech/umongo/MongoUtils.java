/**
 *      Copyright (C) 2010 EdgyTech LLC.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.edgytech.umongo;

import java.util.Date;
import java.util.Map;

import javax.swing.tree.DefaultMutableTreeNode;

import org.bson.LazyDBList;
import org.bson.types.BSONTimestamp;
import org.bson.types.ObjectId;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mongodb.BasicDBObject;
import com.mongodb.Bytes;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.LazyDBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSONSerializers;
import com.mongodb.util.ObjectSerializer;

/**
 *
 * @author antoine
 */
public class MongoUtils {

  public static String queryOptionsToString(final int options) {
    String opt = "";
    if ((options & Bytes.QUERYOPTION_TAILABLE) != 0) {
      opt += "TAILABLE ";
    }
    if ((options & Bytes.QUERYOPTION_SLAVEOK) != 0) {
      opt += "SLAVEOK ";
    }
    if ((options & Bytes.QUERYOPTION_OPLOGREPLAY) != 0) {
      opt += "OPLOGREPLAY ";
    }
    if ((options & Bytes.QUERYOPTION_NOTIMEOUT) != 0) {
      opt += "NOTIMEOUT ";
    }
    if ((options & Bytes.QUERYOPTION_AWAITDATA) != 0) {
      opt += "AWAITDATA ";
    }
    if ((options & Bytes.QUERYOPTION_EXHAUST) != 0) {
      opt += "EXHAUST ";
    }
    return opt;
  }

  public static void addChildrenToTreeNode(final DefaultMutableTreeNode node, final DBObject obj) {
    for (final String key : obj.keySet()) {
      final Object val = obj.get(key);
      // if (val == null) {
      // continue;
      // }

      final DefaultMutableTreeNode child = new DefaultMutableTreeNode(new TreeNodeDocumentField(key, val));
      if (val instanceof DBObject) {
        addChildrenToTreeNode(child, (DBObject) val);
      } else if (val instanceof ObjectId) {
        // break it down
        final ObjectId id = (ObjectId) val;
        child.add(new DefaultMutableTreeNode("Time: " + id.getTime() + " = " + new Date(id.getTime()).toString()));
        child.add(new DefaultMutableTreeNode("Machine: " + (id.getMachine() & 0xFFFFFFFFL)));
        child.add(new DefaultMutableTreeNode("Inc: " + (id.getInc() & 0xFFFFFFFFL)));
      }
      node.add(child);
    }
  }

  public static String getObjectString(final Object obj) {
    return getObjectString(obj, 0);
  }

  public static String getObjectString(final Object obj, final int limit) {
    String str;
    if (obj == null) {
      str = "null";
    } else if (obj instanceof DBObject || obj instanceof byte[]) {
      // get rid of annoying scientific format
      str = MongoUtils.getJSON(obj);
    } else if (obj instanceof Double) {
      // get rid of annoying scientific format
      str = String.format("%f", obj);
    } else if (obj instanceof String) {
      // should show quotes to be JSON like
      str = "\"" + obj + "\"";
    } else {
      str = obj.toString();
    }
    return limitString(str, limit);
  }

  public static String limitString(String str, int limit) {
    if (limit <= 0) {
      limit = UMongo.instance.getPreferences().getInlineDocumentLength();
    }
    if (str.length() > limit && limit > 0) {
      final int max = Math.max(0, limit - 3);
      str = str.substring(0, max) + " ..";
    }
    return str;
  }

  static ObjectSerializer getSerializer() {
    return JSONSerializers.getStrict();
  }

  static String getJSONPreview(final Object value) {
    return MongoUtils.limitString(getSerializer().serialize(value), 80);
  }

  static String getJSON(final Object value) {
    return getSerializer().serialize(value);
  }

  public static DBObject getReplicaSetInfo(final MongoClient mongo) {
    final DB db = mongo.getDB("local");
    final DBObject result = new BasicDBObject();
    final DBCollection namespaces = db.getCollection("system.namespaces");
    String oplogName;
    if (namespaces.findOne(new BasicDBObject("name", "local.oplog.rs")) != null) {
      oplogName = "oplog.rs";
    } else if (namespaces.findOne(new BasicDBObject("name", "local.oplog.$main")) != null) {
      oplogName = "oplog.$main";
    } else {
      return null;
    }
    final DBObject olEntry = namespaces.findOne(new BasicDBObject("name", "local." + oplogName));
    if (olEntry != null && olEntry.containsField("options")) {
      final BasicDBObject options = (BasicDBObject) olEntry.get("options");
      final long size = options.getLong("size");
      result.put("logSizeMB", Float.valueOf(String.format("%.2f", size / 1048576f)));
    } else {
      return null;
    }
    final DBCollection oplog = db.getCollection(oplogName);
    final int size = oplog.getStats().getInt("size");
    result.put("usedMB", Float.valueOf(String.format("%.2f", size / 1048576f)));

    final DBCursor firstc = oplog.find().sort(new BasicDBObject("$natural", 1)).limit(1);
    final DBCursor lastc = oplog.find().sort(new BasicDBObject("$natural", -1)).limit(1);
    if (!firstc.hasNext() || !lastc.hasNext()) {
      return null;
    }
    final BasicDBObject first = (BasicDBObject) firstc.next();
    final BasicDBObject last = (BasicDBObject) lastc.next();
    final BSONTimestamp tsfirst = (BSONTimestamp) first.get("ts");
    final BSONTimestamp tslast = (BSONTimestamp) last.get("ts");
    if (tsfirst == null || tslast == null) {
      return null;
    }

    final int ftime = tsfirst.getTime();
    final int ltime = tslast.getTime();
    final int timeDiffSec = ltime - ftime;
    result.put("timeDiff", timeDiffSec);
    result.put("timeDiffHours", Float.valueOf(String.format("%.2f", timeDiffSec / 3600f)));
    result.put("tFirst", new Date(ftime * 1000l));
    result.put("tLast", new Date(ltime * 1000l));
    result.put("now", new Date());
    return result;
  }

  public static boolean isBalancerOn(final MongoClient mongo) {
    final DB config = mongo.getDB("config");
    final DBCollection settings = config.getCollection("settings");
    final BasicDBObject res = (BasicDBObject) settings.findOne(new BasicDBObject("_id", "balancer"));
    if (res == null || !res.containsField("stopped")) {
      return true;
    }
    return !res.getBoolean("stopped");
  }

  static String makeInfoString(final Object... args) {
    String info = "";
    for (int i = 0; i < args.length; i += 2) {
      if (i > 0) {
        info += ", ";
      }
      info += args[i] + "=[" + args[i + 1] + "]";
    }
    return info;
  }

  public static DBObject checkObject(final DBObject o, final boolean canBeNull, final boolean query) {
    if (o == null) {
      if (canBeNull) {
        return null;
      }
      throw new IllegalArgumentException("can't be null");
    }

    if (o.isPartialObject() && !query) {
      throw new IllegalArgumentException("can't save partial objects");
    }

    if (!query) {
      checkKeys(o);
    }
    return o;
  }

  /**
   * Checks key strings for invalid characters.
   */
  public static void checkKeys(final DBObject o) {
    if (o instanceof LazyDBObject || o instanceof LazyDBList) {
      return;
    }

    for (final String s : o.keySet()) {
      validateKey(s);
      final Object inner = o.get(s);
      if (inner instanceof DBObject) {
        checkKeys((DBObject) inner);
      } else if (inner instanceof Map) {
        checkKeys((Map<String, Object>) inner);
      }
    }
  }

  /**
   * Checks key strings for invalid characters.
   */
  public static void checkKeys(final Map<String, Object> o) {
    for (final String s : o.keySet()) {
      validateKey(s);
      final Object inner = o.get(s);
      if (inner instanceof DBObject) {
        checkKeys((DBObject) inner);
      } else if (inner instanceof Map) {
        checkKeys((Map<String, Object>) inner);
      }
    }
  }

  /**
   * Check for invalid key names
   * 
   * @param s
   *          the string field/key to check
   * @exception IllegalArgumentException
   *              if the key is not valid.
   */
  public static void validateKey(final String s) {
    if (s.contains(".")) {
      throw new IllegalArgumentException("fields stored in the db can't have . in them. (Bad Key: '" + s + "')");
    }
    if (s.startsWith("$")) {
      throw new IllegalArgumentException("fields stored in the db can't start with '$' (Bad Key: '" + s + "')");
    }
  }

  private static JsonParser jsonParser;

  public static JsonParser getJsonParser() {
    if (jsonParser == null) {
      jsonParser = new JsonParser();
    }
    return jsonParser;
  }

  private static Gson gson;

  public static Gson getGson() {
    if (gson == null) {
      gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    }
    return gson;
  }
}
