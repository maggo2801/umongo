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

import java.util.List;
import java.util.logging.Level;

import com.mongodb.BasicDBList;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;

/**
 *
 * @author antoine
 */
public class ReplSetNode extends BaseTreeNode {

  MongoClient mongo;
  String name;
  String shardName;

  public ReplSetNode(final String name, final MongoClient mongo, final String shardName) {
    this.mongo = mongo;
    this.name = name != null ? name : "Replica Set";
    this.shardName = shardName;
    try {
      xmlLoad(Resource.getXmlDir(), Resource.File.replSetNode, null);
    } catch (final Exception ex) {
      getLogger().log(Level.SEVERE, null, ex);
    }
    markStructured();
  }

  public ReplSetNode(final String name, final List<ServerAddress> addrs, final MongoClientOptions opts, final String shardName) {
    mongo = new MongoClient(addrs, opts);
    this.name = name != null ? name : "Replica Set";
    this.shardName = shardName;
    try {
      xmlLoad(Resource.getXmlDir(), Resource.File.replSetNode, null);
    } catch (final Exception ex) {
      getLogger().log(Level.SEVERE, null, ex);
    }
  }

  @Override
  protected void populateChildren() {
    // need to make a query to update server list
    try {
      mongo.getDatabaseNames();
    } catch (final Exception e) {
      getLogger().log(Level.WARNING, null, e);
    }

    // need to pull servers from configuration to see hidden
    // List<ServerAddress> addrs = mongo.getServerAddressList();
    final DBCollection col = mongo.getDB("local").getCollection("system.replset");
    final DBObject config = col.findOne();
    if (config == null) {
      getLogger().log(Level.WARNING, "No replica set configuration found");
      return;
    }

    final BasicDBList members = (BasicDBList) config.get("members");
    for (int i = 0; i < members.size(); ++i) {
      final String host = (String) ((DBObject) members.get(i)).get("host");
      try {
        // this will create new MongoClient instance, catch any exception
        addChild(new ServerNode(host, mongo.getMongoClientOptions(), true, false));
      } catch (final Exception e) {
        getLogger().log(Level.WARNING, null, e);
      }
    }
  }

  public MongoClient getMongoClient() {
    return mongo;
  }

  public String getName() {
    return name;
  }

  public String getShardName() {
    return shardName;
  }

  @Override
  protected void updateNode() {
    label = "";
    if (shardName != null) {
      label += "Shard: " + shardName + " / ";
    }
    label += "ReplSet: " + getName();
  }

  @Override
  protected void refreshNode() {
  }

  String[] getReplicaNames() {
    final List<ServerAddress> addrs = mongo.getServerAddressList();
    final String[] names = new String[addrs.size()];
    int i = 0;
    for (final ServerAddress addr : addrs) {
      names[i++] = addr.toString();
    }
    return names;
  }

}
