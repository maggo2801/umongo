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
import java.util.logging.Level;

import javax.swing.JPanel;

import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.ConfirmDialog;
import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.Menu;
import com.edgytech.swingfast.Text;
import com.edgytech.swingfast.XmlComponentUnit;
import com.edgytech.umongo.ServerPanel.Item;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;

/**
 *
 * @author antoine
 */
public class ServerPanel extends BasePanel implements EnumListener<Item> {

  enum Item {

    icon, host, address, maxObjectSize, journaling, replication, clientPorts, rsConfig, rsStatus, rsOplogInfo, refresh, rsStepDown, rsFreeze, rsFreezeTime, initiate, initConfig, rsRemove, rsReconfigure, isMaster, serverStatus, serverBuildInfo, currentOps, currentOpsQuery, killOp, killOpId, getParameter, getParameterValue, setParameter, setParameterValue, setLogLevel, setLogLevelValue, getLog, getLogType, logRotate, replica, shutdown, shutdownForce, shutdownTimeout, shutdownConfirm, fsync, fsyncAndLock,
  }

  public ServerPanel() {
    setEnumBinding(Item.values(), this);
  }

  public ServerNode getServerNode() {
    return (ServerNode) getNode();
  }

  @Override
  protected void updateComponentCustom(final JPanel comp) {
    try {
      final ServerNode node = getServerNode();
      if (node.isConfig) {
        ((Menu) getBoundUnit(Item.replica)).enabled = false;
      }

      final MongoClient svrMongo = node.getServerMongoClient();
      final ServerAddress addr = getServerNode().getServerAddress();
      if (addr != null) {
        setStringFieldValue(Item.host, addr.toString());
        setStringFieldValue(Item.address, addr.getSocketAddress().toString());
      }

      final CommandResult res = svrMongo.getDB("local").command("isMaster");
      final boolean master = res.getBoolean("ismaster");
      final String replication = MongoUtils.makeInfoString("master", master, "secondary", res.getBoolean("secondary"), "passive", res.getBoolean("passive"));
      setStringFieldValue(Item.replication, replication);
      ((Text) getBoundUnit(Item.replication)).showIcon = master;

      setStringFieldValue(Item.maxObjectSize, String.valueOf(svrMongo.getMaxBsonObjectSize()));

      // ((CmdField) getBoundUnit(Item.serverStatus)).updateFromCmd(svrMongo);
      //
      // DBObject svrStatus = ((DocField)
      // getBoundUnit(Item.serverStatus)).getDoc();
      // boolean dur = svrStatus.containsField("dur");
      // ((Text)getBoundUnit(Item.journaling)).setStringValue(dur ? "On" :
      // "Off");
      // ((Text)getBoundUnit(Item.journaling)).showIcon = dur;
    } catch (final Exception e) {
      UMongo.instance.showError(this.getClass().getSimpleName() + " update", e);
    }
  }

  @Override
  public void actionPerformed(final Item enm, final XmlComponentUnit unit, final Object src) {
  }

  public void rsStepDown(final ButtonBase button) {
    final DBObject cmd = new BasicDBObject("replSetStepDown", 1);
    final DB admin = getServerNode().getServerMongoClient().getDB("admin");

    new DbJob() {

      @Override
      public Object doRun() {
        Object res = null;
        try {
          res = admin.command(cmd);
        } catch (final MongoException.Network e) {
          res = "Operation was likely successful, but connection error: " + e.toString();
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
        return "RS Step Down";
      }

      @Override
      public DBObject getRoot(final Object result) {
        return cmd;
      }
    }.addJob();
  }

  public void rsFreeze(final ButtonBase button) {
    final int sec = getIntFieldValue(Item.rsFreezeTime);
    final DBObject cmd = new BasicDBObject("replSetFreeze", sec);
    new DbJobCmd(getServerNode().getServerMongoClient().getDB("admin"), cmd).addJob();
  }

  public void getLog(final ButtonBase button) {
    final DB db = getServerNode().getServerMongoClient().getDB("admin");
    final String type = getStringFieldValue(Item.getLogType);
    final DBObject cmd = new BasicDBObject("getLog", type);
    new DbJob() {

      @Override
      public Object doRun() throws Exception {
        final CommandResult res = db.command(cmd);
        res.throwOnError();
        final StringBuilder sb = new StringBuilder();
        final BasicDBList list = (BasicDBList) res.get("log");
        for (final Object str : list) {
          sb.append(str);
          sb.append("\n");
        }
        return sb.toString();
      }

      @Override
      public String getNS() {
        return db.getName();
      }

      @Override
      public String getShortName() {
        return cmd.keySet().iterator().next();
      }

    }.addJob();
  }

  public void serverStatus(final ButtonBase button) {
    new DbJobCmd(getServerNode().getServerMongoClient().getDB("admin"), "serverStatus").addJob();
  }

  public void serverBuildInfo(final ButtonBase button) {
    new DbJobCmd(getServerNode().getServerMongoClient().getDB("admin"), "buildinfo").addJob();
  }

  public void isMaster(final ButtonBase button) {
    new DbJobCmd(getServerNode().getServerMongoClient().getDB("admin"), "isMaster").addJob();
  }

  public void rsConfig(final ButtonBase button) {
    final DBCollection col = getServerNode().getServerMongoClient().getDB("local").getCollection("system.replset");
    CollectionPanel.doFind(col, null);
  }

  public void rsStatus(final ButtonBase button) {
    new DbJobCmd(getServerNode().getServerMongoClient().getDB("admin"), "replSetGetStatus").addJob();
  }

  public void rsOplogInfo(final ButtonBase button) {
    new DocView(null, "Oplog Info", null, "Oplog of " + getServerNode().getServerAddress(), MongoUtils.getReplicaSetInfo(getServerNode().getServerMongoClient())).addToTabbedDiv();
  }

  public void setParameter(final ButtonBase button) {
    final BasicDBObject cmd = new BasicDBObject("setParameter", 1);
    final DBObject param = ((DocBuilderField) getBoundUnit(Item.setParameterValue)).getDBObject();
    cmd.putAll(param);
    new DbJobCmd(getServerNode().getServerMongoClient().getDB("admin"), cmd).addJob();
  }

  public void getParameter(final ButtonBase button) {
    final BasicDBObject cmd = new BasicDBObject("getParameter", 1);
    final String param = getStringFieldValue(Item.getParameterValue);
    if ("*".equals(param)) {
      cmd.put("getParameter", "*");
    } else {
      cmd.put(param, 1);
    }
    new DbJobCmd(getServerNode().getServerMongoClient().getDB("admin"), cmd).addJob();
  }

  public void setLogLevel(final ButtonBase button) {
    final BasicDBObject cmd = new BasicDBObject("setParameter", 1);
    final int level = getIntFieldValue(Item.setLogLevelValue);
    cmd.put("logLevel", level);
    new DbJobCmd(getServerNode().getServerMongoClient().getDB("admin"), cmd).addJob();
  }

  public void currentOps(final ButtonBase button) {
    final MongoClient mongo = getServerNode().getServerMongoClient();
    final DBObject query = ((DocBuilderField) getBoundUnit(Item.currentOpsQuery)).getDBObject();
    CollectionPanel.doFind(mongo.getDB("admin").getCollection("$cmd.sys.inprog"), query);
  }

  public void killOp(final ButtonBase button) {
    final MongoClient mongo = getServerNode().getServerMongoClient();
    final int opid = getIntFieldValue(Item.killOpId);
    final DBObject query = new BasicDBObject("op", opid);
    CollectionPanel.doFind(mongo.getDB("admin").getCollection("$cmd.sys.killop"), query);
  }

  public void rsRemove(final ButtonBase button) throws Exception {
    final ReplSetNode replset = (ReplSetNode) getServerNode().getParentNode();
    final DBCollection col = replset.getMongoClient().getDB("local").getCollection("system.replset");
    final DBObject config = col.findOne();

    final BasicDBList members = (BasicDBList) config.get("members");
    int i = 0;
    final String myhost = getServerNode().getServerAddress().getHost() + ":" + getServerNode().getServerAddress().getPort();
    for (; i < members.size(); ++i) {
      if (myhost.equals(((DBObject) members.get(i)).get("host"))) {
        break;
      }
    }

    if (i == members.size()) {
      throw new Exception("No such server in configuration");
    }

    members.remove(i);
    ReplSetPanel.reconfigure(replset, config);
  }

  public void initiate(final ButtonBase button) {
    final DBObject config = ((DocBuilderField) getBoundUnit(Item.initConfig)).getDBObject();
    final DBObject cmd = new BasicDBObject("replSetInitiate", config);
    final DB admin = getServerNode().getServerMongoClient().getDB("admin");
    new DbJobCmd(admin, cmd, this, null).addJob();
  }

  public void rsReconfigure(final ButtonBase button) throws Exception {
    final ReplSetNode replset = (ReplSetNode) getServerNode().getParentNode();
    final DBCollection col = replset.getMongoClient().getDB("local").getCollection("system.replset");
    final DBObject config = col.findOne();

    final BasicDBList members = (BasicDBList) config.get("members");
    int i = 0;
    final String myhost = getServerNode().getServerAddress().getHost() + ":" + getServerNode().getServerAddress().getPort();
    for (; i < members.size(); ++i) {
      if (myhost.equals(((DBObject) members.get(i)).get("host"))) {
        break;
      }
    }

    if (i == members.size()) {
      throw new Exception("No such server in configuration");
    }

    final ReplicaDialog dia = UMongo.instance.getGlobalStore().getReplicaDialog();
    final BasicDBObject oldConf = (BasicDBObject) members.get(i);
    dia.updateFromReplicaConfig(oldConf);
    if (!dia.show()) {
      return;
    }
    final BasicDBObject conf = dia.getReplicaConfig(oldConf.getInt("_id"));
    members.put(i, conf);

    ReplSetPanel.reconfigure(replset, config);
  }

  public void shutdown(final ButtonBase button) {
    final BasicDBObject cmd = new BasicDBObject("shutdown", 1);
    final boolean force = getBooleanFieldValue(Item.shutdownForce);
    if (force) {
      cmd.put("force", force);
    }
    final int timeout = getIntFieldValue(Item.shutdownTimeout);
    if (timeout > 0) {
      cmd.put("timeoutSecs", timeout);
    }
    final ConfirmDialog dia = (ConfirmDialog) getBoundUnit(Item.shutdownConfirm);
    if (!dia.show()) {
      return;
    }
    new DbJobCmd(getServerNode().getServerMongoClient().getDB("admin"), cmd).addJob();
  }

  public void logRotate(final ButtonBase button) {
    new DbJobCmd(getServerNode().getServerMongoClient().getDB("admin"), "logRotate").addJob();
  }

  public void fsync(final ButtonBase button) {
    final DBObject cmd = new BasicDBObject("fsync", 1);
    if (false) {
      cmd.put("async", 1);
    }
    final DB admin = getServerNode().getServerMongoClient().getDB("admin");
    new DbJobCmd(admin, cmd).addJob();
  }

  public void fsyncAndLock(final ButtonBase button) {
    if (!UMongo.instance.getGlobalStore().confirmLockingOperation()) {
      return;
    }

    new DbJob() {

      @Override
      public Object doRun() throws IOException {
        final MongoClient mongo = getServerNode().getServerMongoClient();
        final boolean locked = mongo.isLocked();
        if (locked) {
          return mongo.unlock();
        }

        return mongo.fsyncAndLock();
      }

      @Override
      public String getNS() {
        return null;
      }

      @Override
      public String getShortName() {
        return "FSync And Lock";
      }

      @Override
      public void wrapUp(final Object res) {
        try {
          // looks like the unlock doesnt take effect right away
          Thread.sleep(1000);
        } catch (final InterruptedException ex) {
        }

        super.wrapUp(res);
      }
    }.addJob();
  }
}
