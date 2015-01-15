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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import com.edgytech.swingfast.XmlUnit;
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
public class MongoNode extends BaseTreeNode {

  MongoClient mongo;
  boolean specifiedDb;
  List<String> dbs;

  public MongoNode(final MongoClient mongo, final List<String> dbs) {
    this.mongo = mongo;
    this.dbs = dbs;
    specifiedDb = dbs != null;

    try {
      xmlLoad(Resource.getXmlDir(), Resource.File.mongoNode, null);
    } catch (final Exception ex) {
      getLogger().log(Level.SEVERE, null, ex);
    }
    markStructured();
  }

  public MongoClient getMongoClient() {
    return mongo;
  }

  @Override
  protected void populateChildren() {
    // first ask list of db, will also trigger discovery of nodes
    List<String> dbnames = new ArrayList<String>();
    try {
      dbnames = mongo.getDatabaseNames();
    } catch (final Exception e) {
      getLogger().log(Level.WARNING, e.getMessage(), e);
    }

    final List<ServerAddress> addrs = mongo.getServerAddressList();

    if (addrs.size() <= 1) {
      // check if mongos
      boolean added = false;
      final ServerAddress addr = addrs.get(0);
      final ServerNode node = new ServerNode(mongo, false, false);
      try {
        final CommandResult res = node.getServerDB().command("isdbgrid");
        if (res.ok()) {
          addChild(new RouterNode(addr, mongo));
          added = true;
        }
      } catch (final Exception e) {
        getLogger().log(Level.INFO, e.getMessage(), e);
      }

      if (mongo.getReplicaSetStatus() != null) {
        // could be replset of 1, check
        try {
          final CommandResult res = node.getServerDB().command(new BasicDBObject("isMaster", 1), mongo.getOptions());
          if (res.containsField("setName")) {
            addChild(new ReplSetNode(mongo.getReplicaSetStatus().getName(), mongo, null));
            added = true;
          }
        } catch (final Exception e) {
          getLogger().log(Level.INFO, e.getMessage(), e);
        }
      }

      if (!added) {
        addChild(node);
      }
    } else {
      addChild(new ReplSetNode(mongo.getReplicaSetStatus().getName(), mongo, null));
    }

    if (specifiedDb) {
      // user specified list of DB
      dbnames = dbs;
    } else {
      dbs = dbnames;
      if (dbnames.isEmpty()) {
        // could not get any dbs, add test at least
        dbnames.add("test");
      }
    }

    if (dbnames != null) {
      // get all DBs to populate map
      for (final String dbname : dbnames) {
        addChild(new DbNode(mongo.getDB(dbname)));
      }
    }
  }

  @Override
  protected void updateNode() {
    label = "Mongo: ?";
    // following op may fail, e.g. if SSL is broken
    label = "Mongo: " + mongo.getConnectPoint();
  }

  @Override
  protected void refreshNode() {
    // do dummy command to pick up exception
    mongo.getDatabaseNames();
  }

  BasicDBList getShards() {
    final XmlUnit child = getChild(0);
    if (child instanceof RouterNode) {
      return ((RouterNode) child).getShards();
    }
    return null;
  }

  String[] getShardNames() {
    final XmlUnit child = getChild(0);
    if (child instanceof RouterNode) {
      return ((RouterNode) child).getShardNames();
    }
    return null;
  }

  List<DBObject> summarizeData() {
    final List<DBObject> global = new ArrayList<DBObject>();
    for (final DbNode node : getChildrenOfClass(DbNode.class)) {
      final List<DBObject> res = node.summarizeData();
      global.addAll(res);
    }
    return global;
  }
}
