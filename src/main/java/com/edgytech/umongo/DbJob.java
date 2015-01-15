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

import java.awt.Component;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import javax.swing.AbstractButton;

import org.bson.types.ObjectId;

import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.Div;
import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.ProgressBar;
import com.edgytech.swingfast.ProgressBarWorker;
import com.edgytech.swingfast.XmlComponentUnit;
import com.edgytech.umongo.DbJob.Item;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;

/**
 *
 * @author antoine
 */
public abstract class DbJob extends Div implements EnumListener<Item> {

  enum Item {

    jobName, progressBar, close
  }

  long startTime, endTime;
  boolean stopped = false;
  ProgressBar _progress;
  ProgressBarWorker _pbw;
  BaseTreeNode node;

  public DbJob() {
    try {
      xmlLoad(Resource.getXmlDir(), Resource.File.dbJob, null);
    } catch (final Exception ex) {
      getLogger().log(Level.SEVERE, null, ex);
    }
    setEnumBinding(Item.values(), this);
  }

  public void start() {
    start(false);
  }

  @Override
  public void start(final boolean determinate) {
    setComponentStringFieldValue(Item.jobName, getTitle());

    // if dialog, save current state
    final ButtonBase<?, ?> button = getButton();
    if (button != null) {
      xmlSaveLocalCopy(button, null, null);
    }

    _progress = (ProgressBar) getBoundUnit(Item.progressBar);
    _progress.determinate = isDeterminate();
    _pbw = new ProgressBarWorker(_progress) {

      @Override
      protected Object doInBackground() throws Exception {
        startTime = System.currentTimeMillis();
        try {
          final Object res = doRun();
          return res;
        } catch (final Exception e) {
          getLogger().log(Level.WARNING, null, e);
          return e;
        } finally {
          endTime = System.currentTimeMillis();
        }
      }

      @Override
      protected void done() {
        Object res = null;
        try {
          res = get();
        } catch (final Exception ex) {
          UMongo.instance.showError(getTitle(), (Exception) res);
        }

        try {
          wrapUp(res);
        } catch (final Exception ex) {
          UMongo.instance.showError(getTitle(), ex);
        }
      }
    };
    _pbw.start();
  }

  public abstract Object doRun() throws Exception;

  public abstract String getNS();

  public abstract String getShortName();

  public DBObject getRoot(final Object result) {
    return null;
  }

  public ButtonBase<?, ?> getButton() {
    return null;
  }

  public boolean isCancelled() {
    if (_pbw != null) {
      return _pbw.isCancelled();
    }
    return false;
  }

  public void cancel() {
    if (_pbw != null) {
      _pbw.cancel(true);
    }
  }

  public void setProgress(final int progress) {
    if (_pbw != null) {
      _pbw.updateProgress(progress);
    }
  }

  public BasicDBObject getDescription(final DBObject root) {
    final BasicDBObject sroot = new BasicDBObject();
    sroot.put("ns", getNS());
    sroot.put("name", getShortName());
    sroot.put("details", root);
    return sroot;
  }

  public void wrapUp(final Object res) {
    UMongo.instance.removeJob(this);

    if (node != null) {
      UMongo.instance.addNodeToRefresh(node);
    }

    if (res == null) {
      return;
    }

    final String title = getTitle();

    final boolean log = UMongo.instance.isLoggingOn();
    final boolean logRes = UMongo.instance.isLoggingFirstResultOn();

    final BasicDBObject sroot = getDescription(getRoot(res));

    BasicDBObject logObj = null;
    if (log) {
      logObj = new BasicDBObject("_id", new ObjectId());
      logObj.put("ns", getNS());
      logObj.put("name", getShortName());
      logObj.put("details", getRoot(res));
    }

    if (res instanceof Iterator) {
      new DocView(null, title, this, sroot, (Iterator) res).addToTabbedDiv();
      if (logRes && res instanceof DBCursor) {
        logObj.put("firstResult", ((DBCursor) res).curr());
      }
    } else if (res instanceof WriteResult) {
      final WriteResult wres = (WriteResult) res;
      final DBObject lasterr = wres.getCachedLastError();
      if (lasterr != null) {
        new DocView(null, title, this, sroot, lasterr).addToTabbedDiv();
      }
      if (logRes) {
        logObj.put("firstResult", lasterr);
      }
    } else if (res instanceof CommandResult) {
      final CommandResult cres = (CommandResult) res;
      if (!cres.ok()) {
        UMongo.instance.showError(title, cres.getException());
      }
      new DocView(null, title, this, sroot, (DBObject) res).addToTabbedDiv();
      if (logRes) {
        logObj.put("firstResult", res.toString());
      }
    } else if (res instanceof List) {
      final List list = (List) res;
      new DocView(null, title, this, sroot, list.iterator()).addToTabbedDiv();
      if (logRes && list.size() > 0) {
        logObj.put("firstResult", list.get(0));
      }
    } else if (res instanceof DBObject) {
      new DocView(null, title, this, sroot, (DBObject) res).addToTabbedDiv();
      if (logRes) {
        logObj.put("firstResult", res);
      }
    } else if (res instanceof String) {
      new TextView(null, title, this, (String) res).addToTabbedDiv();
      // string may be large
      if (logRes) {
        logObj.put("firstResult", MongoUtils.limitString((String) res, 0));
      }
    } else if (res instanceof Exception) {
      UMongo.instance.showError(title, (Exception) res);
      if (logRes) {
        logObj.put("firstResult", res.toString());
      }
    } else {
      final DBObject obj = new BasicDBObject("result", res.toString());
      new DocView(null, title, this, sroot, obj).addToTabbedDiv();
      if (logRes) {
        logObj.put("firstResult", res.toString());
      }
    }

    if (log) {
      UMongo.instance.logActivity(logObj);
    }

    _progress = null;
    _pbw = null;
  }

  public String getTitle() {
    String title = "";
    if (getNS() != null) {
      title += getNS() + " / ";
    }
    if (getShortName() != null) {
      title += getShortName();
    }
    return title;
  }

  @Override
  public void actionPerformed(final Item enm, final XmlComponentUnit unit, final Object src) {
    if (enm == Item.close) {
      // cancel job
    }
  }

  public boolean isDeterminate() {
    return false;
  }

  public void addJob() {
    node = UMongo.instance.getNode();
    UMongo.instance.runJob(this);
  }

  long getRunTime() {
    return endTime - startTime;
  }

  void spawnDialog() {
    if (node == null) {
      return;
    }
    UMongo.instance.getTree().selectNode(node);
    // UMongo.instance.displayNode(node);

    final ButtonBase<?, ?> button = getButton();
    if (button == null) {
      return;
    }
    xmlLoadLocalCopy(button, null, null);
    final Component comp = button.getComponent();
    if (comp != null) {
      ((AbstractButton) comp).doClick();
    }
  }

  DB getDB() {
    return null;
  }

  DBObject getCommand() {
    return null;
  }

  BasePanel getPanel() {
    return null;
  }

  void join() throws InterruptedException, ExecutionException {
    if (_pbw != null) {
      _pbw.get();
    }
  }
}
