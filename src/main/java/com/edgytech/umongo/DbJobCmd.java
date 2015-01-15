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

import com.edgytech.swingfast.ButtonBase;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

/**
 *
 * @author antoine
 */
public class DbJobCmd extends DbJob {

  DB db;
  DBObject cmd;
  BasePanel panel;
  BaseTreeNode node;
  ButtonBase button;

  public DbJobCmd(final DB db, final DBObject cmd, final BasePanel panel, final BaseTreeNode node, final ButtonBase button) {
    id = null;
    label = cmd.keySet().iterator().next();
    this.db = db;
    this.cmd = cmd;
    this.panel = panel;
    this.button = button;
    this.node = node;
  }

  public DbJobCmd(final DB db, final DBObject cmd, final BasePanel panel, final ButtonBase button) {
    id = null;
    label = cmd.keySet().iterator().next();
    this.db = db;
    this.cmd = cmd;
    this.panel = panel;
    this.button = button;
  }

  public DbJobCmd(final DB db, final DBObject cmd) {
    this(db, cmd, null, null);
  }

  public DbJobCmd(final DB db, final String cmdStr) {
    this(db, new BasicDBObject(cmdStr, 1), null, null);
  }

  public DbJobCmd(final DBCollection col, final String cmdStr) {
    this(col.getDB(), new BasicDBObject(cmdStr, col.getName()), null, null);
  }

  @Override
  public Object doRun() {
    final CommandResult res = db.command(cmd);
    // we want to pop up the exception, but still see the results...
    // res.throwOnError();
    return res;
  }

  @Override
  public String getNS() {
    String ns = db.getName();
    final Object value = cmd.get(cmd.keySet().iterator().next());
    if (value instanceof String) {
      // collection command
      ns += "." + value;
    }
    return ns;
  }

  @Override
  public String getShortName() {
    return cmd.keySet().iterator().next();
  }

  @Override
  public DBObject getRoot(final Object result) {
    return cmd;
  }

  @Override
  public ButtonBase getButton() {
    return button;
  }

  @Override
  DB getDB() {
    return db;
  }

  @Override
  DBObject getCommand() {
    return cmd;
  }

  @Override
  BasePanel getPanel() {
    return panel;
  }

  @Override
  public void wrapUp(final Object res) {
    super.wrapUp(res);

    // panel info may need to be refreshed
    if (panel != null) {
      panel.refresh();
    }

    if (node != null) {
      node.structureComponent();
    }
  }
}
