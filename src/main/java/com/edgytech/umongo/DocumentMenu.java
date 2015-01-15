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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;

import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.MenuItem;
import com.edgytech.swingfast.PopUpMenu;
import com.edgytech.swingfast.Tree;
import com.edgytech.swingfast.XmlComponentUnit;
import com.edgytech.umongo.DocumentMenu.Item;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

/**
 *
 * @author antoine
 */
public class DocumentMenu extends PopUpMenu implements EnumListener<Item> {

  enum Item {
    update, upUpdate, remove, copyValue
  }

  public DocumentMenu() {
    setEnumBinding(Item.values(), this);
  }

  @Override
  public void actionPerformed(final Item enm, final XmlComponentUnit unit, final Object src) {
  }

  public void update(final ButtonBase<?, ?> button) {
    final DocView dv = (DocView) UMongo.instance.getTabbedResult().getSelectedUnit();
    final TreeNodeDocument node = (TreeNodeDocument) dv.getSelectedNode().getUserObject();
    final DBObject doc = node.getDBObject();

    ((DocBuilderField) getBoundUnit(Item.upUpdate)).setDBObject(doc);
    if (!((MenuItem<?, ?>) getBoundUnit(Item.update)).getDialog().show()) {
      return;
    }

    final DBObject query = doc.containsField("_id") ? new BasicDBObject("_id", doc.get("_id")) : doc;
    final DBObject update = ((DocBuilderField) getBoundUnit(Item.upUpdate)).getDBObject();

    if (dv.getDBCursor() == null) {
      // local data
      final Tree tree = dv.getTree();
      tree.removeChild(node);
      dv.addDocument(update, null);
      tree.structureComponent();
      tree.expandNode(tree.getTreeNode());
      return;
    }

    final DBCollection col = dv.getDBCursor().getCollection();
    new DbJob() {

      @Override
      public Object doRun() {
        return col.update(query, update, false, false);
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Update";
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
        dv.refresh(null);
      }
    }.addJob();
  }

  public void remove(final ButtonBase<?, ?> button) {
    final DocView dv = (DocView) UMongo.instance.getTabbedResult().getSelectedUnit();
    final TreeNodeDocument node = (TreeNodeDocument) dv.getSelectedNode().getUserObject();
    final DBObject doc = node.getDBObject();

    if (dv.getDBCursor() == null) {
      // local data
      final Tree tree = dv.getTree();
      tree.removeChild(node);
      tree.structureComponent();
      tree.expandNode(tree.getTreeNode());
      return;
    }

    // go by _id if possible
    final DBObject query = doc.containsField("_id") ? new BasicDBObject("_id", doc.get("_id")) : doc;
    final DBCollection col = dv.getDBCursor().getCollection();
    new DbJob() {

      @Override
      public Object doRun() {
        return col.remove(query);
      }

      @Override
      public String getNS() {
        return col.getFullName();
      }

      @Override
      public String getShortName() {
        return "Remove";
      }

      @Override
      public void wrapUp(final Object res) {
        super.wrapUp(res);
        dv.refresh(null);
      }

      @Override
      public DBObject getRoot(final Object result) {
        return query;
      }
    }.addJob();
  }

  public void copyValue(final ButtonBase<?, ?> button) {
    final DocView dv = (DocView) UMongo.instance.getTabbedResult().getSelectedUnit();
    final TreeNodeDocument node = (TreeNodeDocument) dv.getSelectedNode().getUserObject();
    final DBObject doc = node.getDBObject();
    final String selection = MongoUtils.getJSON(doc);
    final StringSelection data = new StringSelection(selection);
    final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    clipboard.setContents(data, data);
  }

}
