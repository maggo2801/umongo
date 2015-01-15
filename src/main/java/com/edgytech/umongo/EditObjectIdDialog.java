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

import javax.swing.JDialog;

import org.bson.types.ObjectId;

import com.edgytech.swingfast.EnumListener;
import com.edgytech.swingfast.XmlComponentUnit;
import com.edgytech.umongo.EditObjectIdDialog.Item;
import com.mongodb.DBRef;
import com.mongodb.DBRefBase;

/**
 *
 * @author antoine
 */
public class EditObjectIdDialog extends EditFieldDialog implements EnumListener<Item> {

  enum Item {

    string, time, machine, inc, currentTime, currentMachine, currentInc, namespace
  }

  public EditObjectIdDialog() {
    setEnumBinding(Item.values(), this);
  }

  @Override
  public Object getValue() {
    final String str = getStringFieldValue(Item.string);
    ObjectId id = null;
    if (!str.isEmpty()) {
      id = new ObjectId(str);
    } else {
      final int time = getIntFieldValue(Item.time);
      final int machine = getIntFieldValue(Item.machine);
      final int inc = getIntFieldValue(Item.inc);
      id = new ObjectId(time, machine, inc);
    }
    final String ns = getStringFieldValue(Item.namespace);
    if (ns.trim().isEmpty()) {
      return id;
    }
    return new DBRef(null, ns, id);
  }

  @Override
  public void setValue(final Object value) {
    ObjectId id = null;
    if (value instanceof DBRefBase) {
      final DBRefBase ref = (DBRefBase) value;
      setStringFieldValue(Item.namespace, ref.getRef());
      id = (ObjectId) ref.getId();
    } else {
      id = (ObjectId) value;
    }
    setStringFieldValue(Item.string, id.toString());
    setIntFieldValue(Item.time, id._time());
    setIntFieldValue(Item.machine, id.getMachine());
    setIntFieldValue(Item.inc, id.getInc());
  }

  @Override
  protected void updateComponentCustom(final JDialog old) {
    super.updateComponentCustom(old);
    setStringFieldValue(Item.currentTime, String.valueOf(System.currentTimeMillis() / 1000));
    setStringFieldValue(Item.currentMachine, String.valueOf(ObjectId.getGenMachineId()));
    setStringFieldValue(Item.currentInc, String.valueOf(ObjectId.getCurrentInc()));
  }

  @Override
  public void actionPerformed(final Item enm, final XmlComponentUnit unit, final Object src) {
    switch (enm) {
    case string:
      final String str = getComponentStringFieldValue(Item.string);
      if (!str.isEmpty()) {
        final ObjectId oid = new ObjectId(str);
        setComponentIntFieldValue(Item.time, oid.getTimeSecond());
        setComponentIntFieldValue(Item.machine, oid.getMachine());
        setComponentIntFieldValue(Item.inc, oid.getInc());
      }
      break;
    case time:
    case machine:
    case inc:
      final int time = getComponentIntFieldValue(Item.time);
      final int machine = getComponentIntFieldValue(Item.machine);
      final int inc = getComponentIntFieldValue(Item.inc);
      final ObjectId oid = new ObjectId(time, machine, inc);
      setComponentStringFieldValue(Item.string, oid.toString());
      break;
    }
  }
}
