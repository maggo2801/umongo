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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.FormDialog;
import com.edgytech.swingfast.ListArea;
import com.edgytech.swingfast.MenuBar;
import com.edgytech.swingfast.ProgressDialog;
import com.edgytech.swingfast.ProgressDialogWorker;
import com.edgytech.swingfast.XmlComponentUnit;
import com.edgytech.umongo.MainMenu.Item;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoURI;
import com.mongodb.ServerAddress;

/**
 *
 * @author antoine
 */
public class MainMenu extends MenuBar implements EnumListener<Item> {

  public enum Item {

    connect, connectPointsList, connectDialog, editConnectPoint, removeConnectPoint, connectProgressDialog, exit, preferences, prefDialog, importFile, about, aboutTextArea, closeAllTabs
  }

  public MainMenu() {
    setEnumBinding(Item.values(), this);
  }

  PreferencesDialog getPreferences() {
    return (PreferencesDialog) getBoundUnit(Item.prefDialog);
  }

  @Override
  public void actionPerformed(final Item enm, final XmlComponentUnit unit, final Object src) {
  }

  public void start() {
  }

  public void importFile(final ButtonBase<?, ?> button) throws IOException {
    final ImportDialog dia = UMongo.instance.getGlobalStore().getImportDialog();
    if (!dia.show()) {
      return;
    }
    final boolean continueOnError = dia.getBooleanFieldValue(ImportDialog.Item.continueOnError);
    final DocumentDeserializer dd = dia.getDocumentDeserializer();

    new DbJob() {
      @Override
      public Object doRun() throws Exception {
        return dd.iterator();
      }

      @Override
      public String getNS() {
        return dd.getFile().getName();
      }

      @Override
      public String getShortName() {
        return "Import";
      }
    }.addJob();
  }

  void refreshConnectPointsList(final String select) {
    final ListArea list = (ListArea) getBoundUnit(Item.connectPointsList);
    list.items = list.xmlGetLocalCopiesIds();
    if (list.items == null || list.items.length == 0) {
      list.items = new String[1];
      list.items[0] = "Default";
    }
    if (select != null) {
      setStringFieldValue(Item.connectPointsList, select);
    }
    list.structureComponent();
  }

  public void connect(final ButtonBase<?, ?> button) {
    refreshConnectPointsList(null);
    final FormDialog dia = (FormDialog) button.getDialog();
    if (dia.show()) {
      final ListArea list = (ListArea) getBoundUnit(Item.connectPointsList);
      final ConnectDialog dialog = (ConnectDialog) getBoundUnit(Item.connectDialog);
      final String id = getComponentStringFieldValue(Item.connectPointsList);
      dialog.setId(id);
      list.xmlLoadLocalCopy(dialog, null, null);
      dialog.setId(Item.connectDialog.name());
      dialog.setName(id);
      connect();
    }
  }

  public void editConnectPoint(final ButtonBase<?, ?> button) {
    final ListArea list = (ListArea) getBoundUnit(Item.connectPointsList);
    final ConnectDialog dialog = (ConnectDialog) getBoundUnit(Item.connectDialog);
    final String id = getComponentStringFieldValue(Item.connectPointsList);
    dialog.setId(id);
    list.xmlLoadLocalCopy(dialog, null, null);
    dialog.setId(Item.connectDialog.name());
    dialog.setName(id);

    if (dialog.show()) {
      // the name may have changed
      final String newId = dialog.getName();
      dialog.setId(newId);
      list.xmlSaveLocalCopy(dialog, null, null);
      dialog.setId(Item.connectDialog.name());
      refreshConnectPointsList(newId);
    }
  }

  public void removeConnectPoint(final ButtonBase<?, ?> button) {
    final ListArea list = (ListArea) getBoundUnit(Item.connectPointsList);
    final String id = getComponentStringFieldValue(Item.connectPointsList);
    if (id != null) {
      list.xmlRemoveLocalCopy(id);
      refreshConnectPointsList(null);
    }
  }

  public void connect() {
    try {
      final ConnectDialog dialog = (ConnectDialog) getBoundUnit(Item.connectDialog);
      final ProgressDialog progress = (ProgressDialog) getBoundUnit(Item.connectProgressDialog);
      MongoClient mongo = null;
      List<String> dbs = new ArrayList<String>();
      String uri = dialog.getStringFieldValue(ConnectDialog.Item.uri);
      if (!uri.trim().isEmpty()) {
        if (!uri.startsWith(MongoURI.MONGODB_PREFIX)) {
          uri = MongoURI.MONGODB_PREFIX + uri;
        }
        final MongoClientURI muri = new MongoClientURI(uri);
        mongo = new MongoClient(muri);
        final String db = muri.getDatabase();
        if (db != null && !db.trim().isEmpty()) {
          dbs.add(db.trim());
        }
      } else {
        final String servers = dialog.getStringFieldValue(ConnectDialog.Item.servers);
        if (servers.trim().isEmpty()) {
          return;
        }
        final String[] serverList = servers.split(",");
        final ArrayList<ServerAddress> addrs = new ArrayList<ServerAddress>();
        for (final String server : serverList) {
          final String[] tmp = server.split(":");
          if (tmp.length > 1) {
            addrs.add(new ServerAddress(tmp[0], Integer.valueOf(tmp[1]).intValue()));
          } else {
            addrs.add(new ServerAddress(tmp[0]));
          }
        }
        if ("Direct".equals(dialog.getStringFieldValue(ConnectDialog.Item.connectionMode))) {
          mongo = new MongoClient(addrs.get(0), dialog.getMongoClientOptions());
        } else {
          mongo = new MongoClient(addrs, dialog.getMongoClientOptions());
        }

        final String sdbs = dialog.getStringFieldValue(ConnectDialog.Item.databases);
        if (!sdbs.trim().isEmpty()) {
          for (final String db : sdbs.split(",")) {
            dbs.add(db.trim());
          }
        }
      }

      if (dbs.size() == 0) {
        dbs = null;
      }

      final String user = dialog.getStringFieldValue(ConnectDialog.Item.user).trim();
      final String password = dialog.getStringFieldValue(ConnectDialog.Item.password);
      if (!user.isEmpty()) {
        // authenticate against all dbs
        if (dbs != null) {
          for (final String db : dbs) {
            mongo.getDB(db).authenticate(user, password.toCharArray());
          }
        } else {
          mongo.getDB("admin").authenticate(user, password.toCharArray());
        }
      }

      final MongoClient fmongo = mongo;
      final List<String> fdbs = dbs;
      // doing in background can mean concurrent modification, but dialog is
      // modal so unlikely
      progress.show(new ProgressDialogWorker(progress) {
        @Override
        protected void finished() {
        }

        @Override
        protected Object doInBackground() throws Exception {
          UMongo.instance.addMongoClient(fmongo, fdbs);
          return null;
        }
      });

    } catch (final Exception ex) {
      UMongo.instance.showError(id, ex);
    }
  }

  public void exit(final ButtonBase<?, ?> button) {
    UMongo.instance.windowClosing(null);
  }

  public void closeAllTabs(final ButtonBase<?, ?> button) {
    UMongo.instance.removeAllTabs();
  }

  public void about(final ButtonBase<?, ?> button) throws IOException {
    final FormDialog dia = (FormDialog) button.getDialog();

    final StringBuilder text = new StringBuilder();
    text.append("<html>");

    final Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources("META-INF/MANIFEST.MF");
    while (urls.hasMoreElements()) {
      final URL url = urls.nextElement();
      if (!url.getPath().contains("umongo")) {
        continue;
      }

      String jar = url.getPath();
      final int end = jar.indexOf(".jar");
      if (end < 0) {
        continue;
      }
      jar = jar.substring(0, end + 4);
      final int start = jar.lastIndexOf("/");
      jar = jar.substring(start + 1);
      text.append("<b>").append(jar).append("</b>").append(":<br/>");
      // text.append(url.getPath()).append("<br/>");
      // InputStream is =
      // getClass().getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF");
      // BufferedReader d = new BufferedReader(new InputStreamReader(is));
      final BufferedReader d = new BufferedReader(new InputStreamReader(url.openStream()));
      String str;
      while ((str = d.readLine()) != null) {
        text.append(str).append("<br/>");
      }
      text.append("<br/>");
      // for (Entry e : man.getMainAttributes().entrySet()) {
      // text.append(e.getKey()).append(": ").append(e.getValue()).append("<br/>");
      // }
    }
    text.append("</html>");
    setStringFieldValue(Item.aboutTextArea, text.toString());
    // is.close();
    dia.show();
  }
}
