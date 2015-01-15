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

import java.util.Date;
import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Pattern;

import org.bson.types.BSONTimestamp;
import org.bson.types.Binary;
import org.bson.types.Code;
import org.bson.types.MaxKey;
import org.bson.types.MinKey;
import org.bson.types.ObjectId;

import com.edgytech.swingfast.BoxPanel;
import com.edgytech.swingfast.ButtonBase;
import com.edgytech.swingfast.Div;
import com.edgytech.swingfast.InfoDialog;
import com.edgytech.swingfast.Text;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 *
 * @author antoine
 */
public class DocFieldObject extends DocFieldText {

  public DocFieldObject(final String id, final String key, final Object value, final DocFieldObject parent) {
    super(id, key, value, parent);
    getJComponentBoundUnit(Item.edit).visible = false;
    getJComponentBoundUnit(Item.addField).visible = true;
    ((Div) getJComponentBoundUnit(Item.fields)).borderSize = 1;
    if (parent == null) {
      field = false;
      getJComponentBoundUnit(Item.up).visible = false;
      getJComponentBoundUnit(Item.down).visible = false;
      getJComponentBoundUnit(Item.remove).visible = false;
    }
  }

  @Override
  protected void structureComponentCustom(final BoxPanel old) {
    final Div fields = (Div) getBoundUnit(Item.fields);
    fields.removeAllChildren();
    final DBObject doc = (DBObject) value;
    if (doc == null || doc.keySet().isEmpty()) {
      fields.addChild(new Text(null, null, "Empty"));
    } else {
      for (final String key : doc.keySet()) {
        final Object val = doc.get(key);
        if (val instanceof BasicDBObject) {
          fields.addChild(new DocFieldObject(key, key, val, this));
        } else if (val instanceof BasicDBList) {
          fields.addChild(new DocFieldArray(key, key, val, this));
        } else {
          fields.addChild(new DocFieldText(key, key, val, this));
        }
      }
    }
    fields.structureComponent();
    super.structureComponentCustom(old);
  }

  @Override
  protected void commitComponentCustom(final BoxPanel comp) {
    final Div fields = (Div) getBoundUnit(Item.fields);
    final DBObject doc = createDBObject();
    if (fields.hasChildren()) {
      for (final Object child : fields.getChildren()) {
        if (child instanceof DocFieldText) {
          final DocFieldText text = (DocFieldText) child;
          doc.put(text.getKey(), text.getValue());
        }
      }
    }
    value = doc;
  }

  void remove(final String key) {
    final DBObject doc = (DBObject) value;
    doc.removeField(key);
    structureComponent();
  }

  void moveUp(final String key) {
    final DBObject doc = (DBObject) value;
    value = createDBObject();
    final Iterator<String> it = doc.keySet().iterator();
    String prev = it.next();
    while (it.hasNext()) {
      String cur = it.next();
      if (cur.equals(key)) {
        addField(cur, doc.get(cur));
        cur = prev;
      } else {
        addField(prev, doc.get(prev));
      }
      prev = cur;
    }
    addField(prev, doc.get(prev));
    structureComponent();
  }

  void moveDown(final String key) {
    final DBObject doc = (DBObject) value;
    value = createDBObject();
    final Iterator<String> it = doc.keySet().iterator();
    while (it.hasNext()) {
      final String cur = it.next();
      if (cur.equals(key) && it.hasNext()) {
        final String next = it.next();
        addField(next, doc.get(next));
      }
      addField(cur, doc.get(cur));
    }
    structureComponent();
  }

  public void addNewField(final String key, final String type) {
    Object val = "";
    if (type.equals("Integer")) {
      val = new Integer(0);
    } else if (type.startsWith("Long")) {
      val = new Long(0);
    } else if (type.equals("Binary")) {
      val = new Binary((byte) 0, new byte[1]);
    } else if (type.startsWith("ObjectId")) {
      val = new ObjectId();
    } else if (type.equals("Boolean")) {
      val = new Boolean(true);
    } else if (type.equals("Code")) {
      val = new Code("");
    } else if (type.equals("Date")) {
      val = new Date();
    } else if (type.startsWith("Double")) {
      val = new Double(0.0);
    } else if (type.equals("Pattern")) {
      val = Pattern.compile("");
    } else if (type.equals("Timestamp")) {
      val = new BSONTimestamp((int) (System.currentTimeMillis() / 1000), 0);
    } else if (type.equals("Document")) {
      val = new BasicDBObject();
    } else if (type.equals("List")) {
      val = new BasicDBList();
    } else if (type.equals("Null")) {
      val = null;
    } else if (type.equals("UUID")) {
      val = UUID.randomUUID();
    } else if (type.equals("MinKey")) {
      val = new MinKey();
    } else if (type.equals("MaxKey")) {
      val = new MaxKey();
    }

    if (value == null) {
      value = createDBObject();
    }
    addField(key, val);
    structureComponent();
  }

  public void addField(final ButtonBase<?, ?> button) {
    final String key = getStringFieldValue(Item.addKey);
    final String type = getStringFieldValue(Item.addType);
    final DBObject doc = (DBObject) value;
    if (key.isEmpty() || doc != null && doc.containsField(key)) {
      new InfoDialog(null, "Invalid Key", null, "Please provide a unique key for this field").show();
      return;
    }
    addNewField(key, type);
  }

  protected void addField(final String key, final Object val) {
    final DBObject doc = (DBObject) value;
    doc.put(key, val);
  }

  protected DBObject createDBObject() {
    return new BasicDBObject();
  }

}
