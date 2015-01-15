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
import java.util.logging.Level;

import javax.swing.JPanel;

import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.DynamicComboBox;
import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.FormDialog;
import com.edgytech.swingfast.InfoDialog;
import com.edgytech.swingfast.ListArea;
import com.edgytech.swingfast.Menu;
import com.edgytech.swingfast.MenuItem;
import com.edgytech.swingfast.XmlComponentUnit;
import com.edgytech.swingfast.XmlUnit;
import com.edgytech.umongo.ReplSetPanel.Item;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.Bytes;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;

/**
 *
 * @author antoine
 */
public class ReplSetPanel extends BasePanel implements EnumListener<Item> {

  enum Item {
    refresh, name, replicas, reconfigure, reconfConfig, addReplica, rsConfig, rsStatus, rsOplogInfo, compareReplicas, crStat, queryOplog, qoStart, qoEnd, qoQuery, sharding, manageTags, tagList, addTag, atTag, removeTag
  }

  public ReplSetPanel() {
    setEnumBinding(Item.values(), this);
  }

  public ReplSetNode getReplSetNode() {
    return (ReplSetNode) node;
  }

  @Override
  protected void updateComponentCustom(final JPanel comp) {
    try {
      if (getReplSetNode().getShardName() == null) {
        ((Menu) getBoundUnit(Item.sharding)).enabled = false;
      }

      setStringFieldValue(Item.name, getReplSetNode().getName());
      String replicas = "";
      for (final String replica : getReplSetNode().getReplicaNames()) {
        replicas += replica + ",";
      }
      replicas = replicas.substring(0, replicas.length() - 1);
      setStringFieldValue(Item.replicas, replicas);
    } catch (final Exception e) {
      UMongo.instance.showError(this.getClass().getSimpleName() + " update", e);
    }
  }

  @Override
  public void actionPerformed(final Item enm, final XmlComponentUnit unit, final Object src) {
  }

  public void rsConfig(final ButtonBase<?, ?> button) {
    final DBCollection col = getReplSetNode().getMongoClient().getDB("local").getCollection("system.replset");
    CollectionPanel.doFind(col, null);
  }

  public void rsStatus(final ButtonBase<?, ?> button) {
    new DbJobCmd(getReplSetNode().getMongoClient().getDB("admin"), "replSetGetStatus").addJob();
  }

  public void rsOplogInfo(final ButtonBase<?, ?> button) {
    new DbJob() {

      @Override
      public Object doRun() {
        return MongoUtils.getReplicaSetInfo(getReplSetNode().getMongoClient());
      }

      @Override
      public String getNS() {
        return null;
      }

      @Override
      public String getShortName() {
        return "Oplog Info";
      }
    }.addJob();
  }

  public void reconfigure(final ButtonBase<?, ?> button) {
    final DBCollection col = getReplSetNode().getMongoClient().getDB("local").getCollection("system.replset");
    final DBObject oldConf = col.findOne();
    if (oldConf == null) {
      new InfoDialog(null, "reconfig error", null, "No existing replica set configuration").show();
      return;
    }
    ((DocBuilderField) getBoundUnit(Item.reconfConfig)).setDBObject(oldConf);
    if (!((MenuItem<?, ?>) getBoundUnit(Item.reconfigure)).getDialog().show()) {
      return;
    }

    final DBObject config = ((DocBuilderField) getBoundUnit(Item.reconfConfig)).getDBObject();
    reconfigure(getReplSetNode(), config);
  }

  static public void reconfigure(final ReplSetNode rsNode, final DBObject config) {
    final DBCollection col = rsNode.getMongoClient().getDB("local").getCollection("system.replset");
    final DBObject oldConf = col.findOne();
    final int version = (Integer) oldConf.get("version") + 1;
    config.put("version", version);

    // reconfig usually triggers an error as connections are bounced.. try to
    // absorb it
    final DBObject cmd = new BasicDBObject("replSetReconfig", config);
    final DB admin = rsNode.getMongoClient().getDB("admin");

    new DbJob() {

      @Override
      public Object doRun() {
        Object res = null;
        try {
          res = admin.command(cmd);
        } catch (final MongoException.Network e) {
          res = new BasicDBObject("msg", "Operation was likely successful, but connection was bounced");
        }

        try {
          // sleep a bit since it takes time for driver to see change
          Thread.sleep(6000);
        } catch (final InterruptedException ex) {
          getLogger().log(Level.WARNING, null, ex);
        }
        return res;
      }

      @Override
      public String getNS() {
        return null;
      }

      @Override
      public String getShortName() {
        return "RS Reconfig";
      }

      @Override
      public DBObject getRoot(final Object result) {
        return cmd;
      }

      @Override
      public void wrapUp(final Object res) {
        // try to restructure but changes arent seen for a few seconds
        super.wrapUp(res);
        rsNode.structureComponent();
      }
    }.addJob();
  }

  public void addReplica(final ButtonBase<?, ?> button) {
    final DBCollection col = getReplSetNode().getMongoClient().getDB("local").getCollection("system.replset");
    final DBObject config = col.findOne();
    if (config == null) {
      new InfoDialog(null, "reconfig error", null, "No existing replica set configuration").show();
      return;
    }

    final BasicDBList members = (BasicDBList) config.get("members");
    int max = 0;
    for (int i = 0; i < members.size(); ++i) {
      final int id = (Integer) ((DBObject) members.get(i)).get("_id");
      if (id > max) {
        max = id;
      }
    }

    final ReplicaDialog dia = UMongo.instance.getGlobalStore().getReplicaDialog();
    if (!dia.show()) {
      return;
    }
    final BasicDBObject conf = dia.getReplicaConfig(max + 1);
    members.add(conf);
    reconfigure(getReplSetNode(), config);
  }

  public void compareReplicas(final ButtonBase<?, ?> button) {
    final String stat = getStringFieldValue(Item.crStat);
    new DbJob() {

      @Override
      public Object doRun() {
        final ReplSetNode node = getReplSetNode();
        if (!node.hasChildren()) {
          return null;
        }

        final ArrayList<MongoClient> svrs = new ArrayList<MongoClient>();
        for (final XmlUnit unit : node.getChildren()) {
          final ServerNode svr = (ServerNode) unit;
          final MongoClient svrm = svr.getServerMongoClient();
          try {
            svrm.getDatabaseNames();
          } catch (final Exception e) {
            continue;
          }
          svrs.add(svrm);
        }

        final BasicDBObject res = new BasicDBObject();
        final MongoClient m = getReplSetNode().getMongoClient();
        for (final String dbname : m.getDatabaseNames()) {
          final DB db = m.getDB(dbname);
          final BasicDBObject dbres = new BasicDBObject();
          for (final String colname : db.getCollectionNames()) {
            final DBCollection col = db.getCollection(colname);
            final BasicDBObject colres = new BasicDBObject();
            final BasicDBObject values = new BasicDBObject();
            boolean same = true;
            long ref = -1;
            for (final MongoClient svrm : svrs) {
              final DBCollection svrcol = svrm.getDB(dbname).getCollection(colname);
              long value = 0;
              if (stat.startsWith("Count")) {
                value = svrcol.count();
              } else if (stat.startsWith("Data Size")) {
                final CommandResult stats = svrcol.getStats();
                value = stats.getLong("size");
              }
              values.append(svrm.getConnectPoint(), value);
              if (ref < 0) {
                ref = value;
              } else if (ref != value) {
                same = false;
              }
            }
            if (!same) {
              colres.append("values", values);
              dbres.append(colname, colres);
            }
          }
          if (!dbres.isEmpty()) {
            res.append(dbname, dbres);
          }
        }

        return res;
      }

      @Override
      public String getNS() {
        return "*";
      }

      @Override
      public String getShortName() {
        return "Compare Replicas";
      }
    }.addJob();
  }

  public void queryOplog(final ButtonBase<?, ?> button) {
    final DBCollection oplog = getReplSetNode().getMongoClient().getDB("local").getCollection("oplog.rs");
    final DBObject start = ((DocBuilderField) getBoundUnit(Item.qoStart)).getDBObject();
    final DBObject end = ((DocBuilderField) getBoundUnit(Item.qoEnd)).getDBObject();
    final DBObject extra = ((DocBuilderField) getBoundUnit(Item.qoQuery)).getDBObject();

    final BasicDBObject query = new BasicDBObject();
    final BasicDBObject range = new BasicDBObject();
    if (start != null) {
      range.put("$gte", start.get("ts"));
    }
    if (end != null) {
      range.put("$lte", end.get("ts"));
    }

    query.put("ts", range);
    if (extra != null) {
      query.putAll(extra);
    }

    CollectionPanel.doFind(oplog, query, null, null, 0, 0, 0, false, null, Bytes.QUERYOPTION_OPLOGREPLAY);
  }

  void refreshTagList() {
    final String shardName = getReplSetNode().getShardName();
    if (shardName == null) {
      return;
    }

    final ListArea list = (ListArea) getBoundUnit(Item.tagList);
    final DB db = ((RouterNode) getReplSetNode().getParentNode()).getMongoClient().getDB("config");
    final DBObject shard = db.getCollection("shards").findOne(new BasicDBObject("_id", shardName));
    if (shard.containsField("tags")) {
      final BasicDBList tags = (BasicDBList) shard.get("tags");
      if (tags.size() > 0) {
        final String[] array = new String[tags.size()];
        int i = 0;
        for (final Object tag : tags) {
          array[i++] = (String) tag;
        }
        list.items = array;
        list.structureComponent();
        return;
      }
    }
    list.items = null;
    list.structureComponent();
  }

  public void manageTags(final ButtonBase<?, ?> button) {
    final FormDialog dialog = (FormDialog) ((MenuItem<?, ?>) getBoundUnit(Item.manageTags)).getDialog();
    refreshTagList();
    dialog.show();
  }

  public void addTag(final ButtonBase<?, ?> button) {
    final DB config = ((RouterNode) getReplSetNode().getParentNode()).getMongoClient().getDB("config");
    final DBCollection col = config.getCollection("shards");

    ((DynamicComboBox) getBoundUnit(Item.atTag)).items = TagRangeDialog.getExistingTags(config);
    final FormDialog dia = (FormDialog) button.getDialog();
    if (!dia.show()) {
      return;
    }
    final String tag = getStringFieldValue(Item.atTag);
    final DBObject query = new BasicDBObject("_id", getReplSetNode().getShardName());
    final DBObject update = new BasicDBObject("$addToSet", new BasicDBObject("tags", tag));

    new DbJob() {

      @Override
      public Object doRun() {
        return col.update(query, update);
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Add Tag";
      }

      @Override
      public DBObject getRoot(final Object result) {
        final BasicDBObject obj = new BasicDBObject("query", query);
        obj.put("update", update);
        return obj;
      }

      @Override
      public void wrapUp(final Object res) {
        super.wrapUp(res);
        refreshTagList();
      }
    }.addJob();
  }

  public void removeTag(final ButtonBase<?, ?> button) {
    final DB db = ((RouterNode) getReplSetNode().getParentNode()).getMongoClient().getDB("config");
    final DBCollection col = db.getCollection("shards");
    final String tag = getComponentStringFieldValue(Item.tagList);
    if (tag == null) {
      return;
    }

    final DBObject query = new BasicDBObject("_id", getReplSetNode().getShardName());
    final DBObject update = new BasicDBObject("$pull", new BasicDBObject("tags", tag));

    new DbJob() {

      @Override
      public Object doRun() {
        return col.update(query, update);
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Remove Tag";
      }

      @Override
      public DBObject getRoot(final Object result) {
        final BasicDBObject obj = new BasicDBObject("query", query);
        obj.put("update", update);
        return obj;
      }

      @Override
      public void wrapUp(final Object res) {
        super.wrapUp(res);
        refreshTagList();
      }
    }.addJob();
  }
}
