/**
 * Copyright (C) 2010 EdgyTech LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.edgytech.umongo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;

import org.xml.sax.SAXException;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

/**
 *
 * @author antoine
 */
public class RouterNode extends BaseTreeNode {

  MongoClient mongo;
  ServerAddress addr;
  BasicDBList shards;

  public RouterNode(final ServerAddress addr, final MongoClient mongo) throws IOException, SAXException {
    this.addr = addr;
    this.mongo = mongo;
    xmlLoad(Resource.getXmlDir(), Resource.File.routerNode, null);
  }

  @Override
  protected void populateChildren() {
    CommandResult res = mongo.getDB("admin").command("listShards");
    shards = (BasicDBList) res.get("shards");
    if (shards == null) {
      return;
    }

    for (final Object obj : shards) {
      try {
        final DBObject shard = (DBObject) obj;
        final String shardName = (String) shard.get("_id");
        String hosts = (String) shard.get("host");
        String repl = null;
        final int slash = hosts.indexOf('/');
        if (slash >= 0) {
          repl = hosts.substring(0, slash);
          hosts = hosts.substring(slash + 1);
        }

        final String[] hostList = hosts.split(",");
        final ArrayList<ServerAddress> addrs = new ArrayList<ServerAddress>();
        for (final String host : hostList) {
          final int colon = host.indexOf(':');
          if (colon >= 0) {
            addrs.add(new ServerAddress(host.substring(0, colon), Integer.parseInt(host.substring(colon + 1))));
          } else {
            addrs.add(new ServerAddress(host));
          }
        }

        if (repl != null || addrs.size() > 1) {
          addChild(new ReplSetNode(repl, addrs, mongo.getMongoClientOptions(), shardName));
        } else {
          addChild(new ServerNode(addrs.get(0), mongo.getMongoClientOptions(), false, false));
        }
      } catch (final Exception e) {
        getLogger().log(Level.WARNING, null, e);
      }
    }

    // add config servers
    try {
      res = mongo.getDB("admin").command("getCmdLineOpts");
      final String configStr = (String) ((BasicDBObject) res.get("parsed")).get("configdb");
      final String[] configsvrs = configStr.split(",");
      for (final String host : configsvrs) {
        final int colon = host.indexOf(':');
        ServerAddress addr;
        if (colon >= 0) {
          addr = new ServerAddress(host.substring(0, colon), Integer.parseInt(host.substring(colon + 1)));
        } else {
          addr = new ServerAddress(host);
        }
        addChild(new ServerNode(addr, mongo.getMongoClientOptions(), false, true));
      }
    } catch (final Exception e) {
      getLogger().log(Level.WARNING, null, e);
    }
  }

  public ServerAddress getAddress() {
    return addr;
  }

  public MongoClient getMongoClient() {
    return mongo;
  }

  @Override
  protected void updateNode() {
    label = "MongoS: " + mongo.getConnectPoint();
  }

  @Override
  protected void refreshNode() {
  }

  BasicDBList getShards() {
    return shards;
  }

  String[] getShardNames() {
    if (!shards.isEmpty()) {
      final String[] items = new String[shards.size()];
      for (int i = 0; i < shards.size(); ++i) {
        final DBObject shard = (DBObject) shards.get(i);
        items[i] = shard.get("_id").toString();
      }
      return items;
    }
    return null;
  }
}
