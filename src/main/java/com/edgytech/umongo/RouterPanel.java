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

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.swing.JPanel;

import org.bson.types.BSONTimestamp;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;

import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.ComboBox;
import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.FormDialog;
import com.edgytech.swingfast.InfoDialog;
import com.edgytech.swingfast.MenuItem;
import com.edgytech.swingfast.XmlComponentUnit;
import com.edgytech.umongo.RouterPanel.Item;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

/**
 *
 * @author antoine
 */
public class RouterPanel extends BasePanel implements EnumListener<Item> {

  enum Item {
    refresh, host, address, shards, listShards, flushConfiguration, addShard, asHost, asShardName, asReplSetName, asMaxSize, removeShard, rsShard, balancer, balStopped, balSecThrottle, balStartTime, balStopTime, regenConfigDB, regenServers, regenDB, regenCollection, regenShardKey, regenKeyUnique, regenRSList, regenRSListArea, regenDeleteChunks, regenConfirm
  }

  public RouterPanel() {
    setEnumBinding(Item.values(), this);
  }

  RouterNode getRouterNode() {
    return (RouterNode) node;
  }

  @Override
  protected void updateComponentCustom(final JPanel comp) {
    try {
      final ServerAddress addr = getRouterNode().getAddress();
      setStringFieldValue(Item.host, addr.getHost() + ":" + addr.getPort());
      setStringFieldValue(Item.address, addr.getSocketAddress().toString());
      ((DocField) getBoundUnit(Item.shards)).setDoc(((RouterNode) node).shards);
    } catch (final Exception e) {
      UMongo.instance.showError(this.getClass().getSimpleName() + " update", e);
    }
  }

  @Override
  public void actionPerformed(final Item enm, final XmlComponentUnit unit, final Object src) {
  }

  public void addShard(final ButtonBase<?, ?> button) {
    final RouterNode router = getRouterNode();
    final String host = getStringFieldValue(Item.asHost);
    final String shardName = getStringFieldValue(Item.asShardName);
    final String replSetName = getStringFieldValue(Item.asReplSetName);
    final int maxsize = getIntFieldValue(Item.asMaxSize);
    String server = host;
    if (!replSetName.isEmpty()) {
      server = replSetName + "/" + server;
    }

    final BasicDBObject cmd = new BasicDBObject("addshard", server);
    if (!shardName.isEmpty()) {
      cmd.put("name", shardName);
    }
    if (maxsize > 0) {
      cmd.put("maxSize", maxsize);
    }
    final DB db = router.getMongoClient().getDB("admin");
    new DbJobCmd(db, cmd, this, null).addJob();
  }

  public void removeShard(final ButtonBase<?, ?> button) {
    final FormDialog dialog = (FormDialog) ((MenuItem<?, ?>) getBoundUnit(Item.removeShard)).getDialog();
    final ComboBox combo = (ComboBox) getBoundUnit(Item.rsShard);
    combo.value = 0;
    combo.items = getRouterNode().getShardNames();
    combo.structureComponent();

    if (!dialog.show()) {
      return;
    }

    final BasicDBObject cmd = new BasicDBObject("removeshard", getStringFieldValue(Item.rsShard));
    final DB db = getRouterNode().getMongoClient().getDB("admin");
    new DbJobCmd(db, cmd, this, null).addJob();
  }

  public void listShards(final ButtonBase<?, ?> button) {
    new DbJobCmd(getRouterNode().getMongoClient().getDB("admin"), "listShards").addJob();
  }

  public void flushConfiguration(final ButtonBase<?, ?> button) {
    new DbJobCmd(getRouterNode().getMongoClient().getDB("admin"), "flushRouterConfig").addJob();
  }

  public void balancer(final ButtonBase<?, ?> button) {
    final MongoClient mongo = getRouterNode().getMongoClient();
    final DB config = mongo.getDB("config");
    final DBCollection settings = config.getCollection("settings");

    final FormDialog diag = (FormDialog) ((MenuItem<?, ?>) getBoundUnit(Item.balancer)).getDialog();
    diag.xmlLoadCheckpoint();

    final BasicDBObject query = new BasicDBObject("_id", "balancer");
    BasicDBObject balDoc = (BasicDBObject) settings.findOne(query);
    if (balDoc != null) {
      if (balDoc.containsField("stopped")) {
        setIntFieldValue(Item.balStopped, balDoc.getBoolean("stopped") ? 1 : 2);
      }
      if (balDoc.containsField("_secondaryThrottle")) {
        setIntFieldValue(Item.balSecThrottle, balDoc.getBoolean("_secondaryThrottle") ? 1 : 2);
      }
      final BasicDBObject window = (BasicDBObject) balDoc.get("activeWindow");
      if (window != null) {
        setStringFieldValue(Item.balStartTime, window.getString("start"));
        setStringFieldValue(Item.balStopTime, window.getString("stop"));
      }
    }

    if (!diag.show()) {
      return;
    }

    if (balDoc == null) {
      balDoc = new BasicDBObject("_id", "balancer");
    }
    final int stopped = getIntFieldValue(Item.balStopped);
    if (stopped > 0) {
      balDoc.put("stopped", stopped == 1 ? true : false);
    } else {
      balDoc.removeField("stopped");
    }
    final int throttle = getIntFieldValue(Item.balSecThrottle);
    if (throttle > 0) {
      balDoc.put("_secondaryThrottle", throttle == 1 ? true : false);
    } else {
      balDoc.removeField("_secondaryThrottle");
    }

    if (!getStringFieldValue(Item.balStartTime).trim().isEmpty()) {
      final BasicDBObject aw = new BasicDBObject();
      aw.put("start", getStringFieldValue(Item.balStartTime).trim());
      aw.put("stop", getStringFieldValue(Item.balStopTime).trim());
      balDoc.put("activeWindow", aw);
    }
    final BasicDBObject newDoc = balDoc;

    new DbJob() {

      @Override
      public Object doRun() throws IOException {
        return settings.update(query, newDoc, true, false);
      }

      @Override
      public String getNS() {
        return settings.getFullName();
      }

      @Override
      public String getShortName() {
        return "Balancer";
      }

      @Override
      public void wrapUp(final Object res) {
        updateComponent();
        super.wrapUp(res);
      }
    }.addJob();
  }

  public void regenConfigDB(final ButtonBase<?, ?> button) throws UnknownHostException {
    final MongoClient cmongo = getRouterNode().getMongoClient();
    final String servers = getStringFieldValue(Item.regenServers);
    final String db = getStringFieldValue(Item.regenDB);
    final String col = getStringFieldValue(Item.regenCollection);
    final String ns = db + "." + col;
    final DBObject shardKey = ((DocBuilderField) getBoundUnit(Item.regenShardKey)).getDBObject();
    final boolean unique = getBooleanFieldValue(Item.regenKeyUnique);
    final BasicDBObject result = new BasicDBObject();
    result.put("ns", ns);
    result.put("shardKey", shardKey);
    result.put("unique", unique);

    // create direct mongo for each replica set
    final String[] serverList = servers.split("\n");
    final List<ServerAddress> list = new ArrayList<ServerAddress>();
    String txt = "";
    String primaryShard = null;
    final BasicDBObject shardList = new BasicDBObject();
    final HashMap<MongoClient, String> mongoToShard = new HashMap<MongoClient, String>();
    sLoop: for (String server : serverList) {
      server = server.trim();
      final String[] tokens = server.split("/");
      if (tokens.length != 2) {
        new InfoDialog(null, "Error", null, "Server format must be like 'hostname:port/shard', one by line").show();
        return;
      }
      server = tokens[0];
      final String shard = tokens[1];
      if (primaryShard == null) {
        primaryShard = shard;
      }
      final ServerAddress addr = new ServerAddress(server);

      // filter out if replset already exists
      for (final MongoClient replset : mongoToShard.keySet()) {
        if (replset.getServerAddressList().contains(addr)) {
          continue sLoop;
        }
      }

      list.clear();
      list.add(addr);
      final MongoClient mongo = new MongoClient(list);
      // UMongo.instance.addMongoClient(mongo, null);
      // make request to force server detection
      mongo.getDatabaseNames();
      mongoToShard.put(mongo, shard);

      String desc = null;
      if (!mongo.getDatabaseNames().contains(db) || !mongo.getDB(db).getCollectionNames().contains(col)) {
        desc = "Collection not present!";
      } else {
        // try to see if shard key has index
        final DBObject index = mongo.getDB(db).getCollection("system.indexes").findOne(new BasicDBObject("key", shardKey));
        if (index != null) {
          desc = "shard key found";
        } else {
          desc = "shard key NOT found!";
        }
      }
      txt += mongo.toString() + " shard=" + shard + " - " + desc + "\n";
      final BasicDBObject shardObj = new BasicDBObject("servers", mongo.toString());
      shardObj.put("status", desc);
      if (shardList.containsField(shard)) {
        new InfoDialog(null, "Error", null, "Duplicate Shard name " + shard).show();
        return;
      }
      shardList.put(shard, shardObj);
    }
    result.put("shards", shardList);

    FormDialog dia = (FormDialog) getBoundUnit(Item.regenRSList);
    dia.setStringFieldValue(Item.regenRSListArea, txt);
    if (!dia.show()) {
      return;
    }

    final DB config = cmongo.getDB("config");

    // add database record
    BasicDBObject doc = new BasicDBObject("_id", db);
    doc.put("partitioned", true);
    doc.put("primary", primaryShard);
    config.getCollection("databases").save(doc);

    // add collection record
    doc = new BasicDBObject("_id", ns);
    doc.put("lastmod", new Date());
    doc.put("dropped", false);
    doc.put("key", shardKey);
    doc.put("unique", unique);
    config.getCollection("collections").save(doc);

    final DBCollection chunks = config.getCollection("chunks");
    final long count = chunks.count(new BasicDBObject("ns", ns));
    if (count > 0) {
      dia = (FormDialog) getBoundUnit(Item.regenDeleteChunks);
      if (dia.show()) {
        chunks.remove(new BasicDBObject("ns", ns));
      } else {
        return;
      }
    }

    // add temp collection to sort chunks with shard key
    final DBCollection tmpchunks = config.getCollection("_tmpchunks_" + col);
    tmpchunks.drop();
    // should be safe environment, and dup keys should be ignored
    tmpchunks.setWriteConcern(WriteConcern.NORMAL);
    // can use shardKey as unique _id
    // tmpchunks.ensureIndex(shardKey, "shardKey", true);

    // create filter for shard fields
    final DBObject shardKeyFilter = new BasicDBObject();
    // final DBObject shardKeyDescend = new BasicDBObject();
    boolean hasId = false;
    for (final String key : shardKey.keySet()) {
      shardKeyFilter.put(key, 1);
      if (key.equals("_id")) {
        hasId = true;
      }
    }
    if (!hasId) {
      shardKeyFilter.put("_id", 0);
    }

    dia = (FormDialog) getBoundUnit(Item.regenConfirm);
    if (!dia.show()) {
      return;
    }

    // now fetch all records from each shard
    final AtomicInteger todo = new AtomicInteger(mongoToShard.size());
    for (final Map.Entry<MongoClient, String> entry : mongoToShard.entrySet()) {
      final MongoClient mongo = entry.getKey();
      final String shard = entry.getValue();
      new DbJob() {

        @Override
        public Object doRun() throws Exception {
          final BasicDBObject shardObj = (BasicDBObject) shardList.get(shard);
          final long count = mongo.getDB(db).getCollection(col).count();
          shardObj.put("count", count);
          final DBCursor cur = mongo.getDB(db).getCollection(col).find(new BasicDBObject(), shardKeyFilter);
          long i = 0;
          int inserted = 0;
          final long start = System.currentTimeMillis();
          while (cur.hasNext() && !isCancelled()) {
            final BasicDBObject key = (BasicDBObject) cur.next();
            setProgress((int) (++i * 100.0f / count));
            try {
              final BasicDBObject entry = new BasicDBObject("_id", key);
              entry.put("_shard", shard);
              tmpchunks.insert(entry);
              ++inserted;
            } catch (final Exception e) {
              getLogger().log(Level.WARNING, e.getMessage(), e);
            }
          }

          if (isCancelled()) {
            shardObj.put("cancelled", true);
          }
          shardObj.put("inserted", inserted);
          shardObj.put("scanTime", System.currentTimeMillis() - start);
          todo.decrementAndGet();
          return null;
        }

        @Override
        public String getNS() {
          return tmpchunks.getFullName();
        }

        @Override
        public String getShortName() {
          return "Scanning " + shard;
        }

        @Override
        public boolean isDeterminate() {
          return true;
        }
      }.addJob();

    }

    new DbJob() {

      @Override
      public Object doRun() throws Exception {
        // wait for all shards to be done
        final long start = System.currentTimeMillis();
        while (todo.get() > 0 && !isCancelled()) {
          Thread.sleep(2000);
        }

        if (isCancelled()) {
          result.put("cancelled", true);
          return result;
        }

        // find highest current timestamp
        DBCursor cur = chunks.find().sort(new BasicDBObject("lastmod", -1)).batchSize(-1);
        BasicDBObject chunk = (BasicDBObject) (cur.hasNext() ? cur.next() : null);
        BSONTimestamp ts = (BSONTimestamp) (chunk != null ? chunk.get("lastmod") : null);

        // now infer chunk ranges
        final long count = tmpchunks.count();
        result.put("uniqueKeys", count);
        int numChunks = 0;
        cur = tmpchunks.find().sort(new BasicDBObject("_id", 1));
        BasicDBObject prev = (BasicDBObject) cur.next();
        BasicDBObject next = null;
        // snap prev to minkey
        final BasicDBObject theid = (BasicDBObject) prev.get("_id");
        for (final String key : shardKey.keySet()) {
          theid.put(key, new MinKey());
        }
        String currentShard = prev.getString("_shard");

        int i = 1;
        while (cur.hasNext()) {
          next = (BasicDBObject) cur.next();
          setProgress((int) (++i * 100.0f / count));
          final String newShard = next.getString("_shard");
          if (newShard.equals(currentShard)) {
            continue;
          }

          // add chunk
          ts = getNextTimestamp(ts);
          chunk = getChunk(ns, shardKey, prev, next, ts);
          chunks.insert(chunk);
          prev = next;
          currentShard = prev.getString("_shard");
          ++numChunks;
        }

        // build max
        next = new BasicDBObject();
        for (final String key : shardKey.keySet()) {
          next.put(key, new MaxKey());
        }
        next = new BasicDBObject("_id", next);
        ts = getNextTimestamp(ts);
        chunk = getChunk(ns, shardKey, prev, next, ts);
        chunks.insert(chunk);
        ++numChunks;
        result.put("numChunks", numChunks);
        result.put("totalTime", System.currentTimeMillis() - start);
        return result;
      }

      @Override
      public String getNS() {
        return chunks.getFullName();
      }

      @Override
      public String getShortName() {
        return "Creating Chunks";
      }

      @Override
      public boolean isDeterminate() {
        return true;
      }
    }.addJob();
  }

  BasicDBObject getChunk(final String ns, final DBObject shardKey, final BasicDBObject min, final BasicDBObject max, final BSONTimestamp ts) {
    final BasicDBObject chunk = new BasicDBObject();
    final BasicDBObject themin = (BasicDBObject) min.get("_id");
    final BasicDBObject themax = (BasicDBObject) max.get("_id");
    String _id = ns;
    for (final String key : shardKey.keySet()) {
      _id += "-" + key + "_";
      final Object val = themin.get(key);
      _id += val != null ? val.toString() : "null";
    }
    chunk.put("_id", _id);
    chunk.put("lastmod", ts);
    chunk.put("ns", ns);
    chunk.put("min", themin);
    chunk.put("max", themax);
    chunk.put("shard", min.getString("_shard"));
    return chunk;
  }

  BSONTimestamp getNextTimestamp(final BSONTimestamp ts) {
    if (ts == null) {
      return new BSONTimestamp(1000, 0);
    }
    return new BSONTimestamp(ts.getTime(), ts.getInc() + 1);
  }
}
