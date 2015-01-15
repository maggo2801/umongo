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

import com.edgytech.swingfast.ComboBox;
import com.edgytech.swingfast.FormDialog;
import com.mongodb.BasicDBObject;
import com.mongodb.Bytes;
import com.mongodb.DBObject;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;

/**
 *
 * @author antoine
 */
public class OptionDialog extends FormDialog {
  enum Item {
    tailable, slaveOk, opLogReplay, noTimeout, awaitData, exhaust, partial, writeFactor, writePolicy, writeTimeout, jsync, rpPreference, rpTag
  }

  enum ReadPref {
    primary, primaryPreferred, secondary, secondaryPreferred, nearest
  }

  public OptionDialog() {
    setEnumBinding(Item.values(), null);
  }

  void update(final int options, final WriteConcern wc, final ReadPreference rp) {
    // reset
    xmlLoadCheckpoint();

    setBooleanFieldValue(Item.tailable, (options & Bytes.QUERYOPTION_TAILABLE) != 0);
    setBooleanFieldValue(Item.slaveOk, (options & Bytes.QUERYOPTION_SLAVEOK) != 0);
    setBooleanFieldValue(Item.opLogReplay, (options & Bytes.QUERYOPTION_OPLOGREPLAY) != 0);
    setBooleanFieldValue(Item.noTimeout, (options & Bytes.QUERYOPTION_NOTIMEOUT) != 0);
    setBooleanFieldValue(Item.awaitData, (options & Bytes.QUERYOPTION_AWAITDATA) != 0);
    setBooleanFieldValue(Item.exhaust, (options & Bytes.QUERYOPTION_EXHAUST) != 0);
    setBooleanFieldValue(Item.partial, (options & Bytes.QUERYOPTION_PARTIAL) != 0);

    final Object w = wc.getWObject();
    final int wInt = (Integer) (w instanceof Integer ? w : 0);
    final String wStr = (String) (w instanceof String ? w : "");
    setIntFieldValue(Item.writeFactor, wInt);
    setStringFieldValue(Item.writePolicy, wStr);
    setIntFieldValue(Item.writeTimeout, wc.getWtimeout());
    // setBooleanFieldValue(Item.fsync, wc.fsync());

    final DBObject rpObj = rp.toDBObject();
    final ComboBox readBox = (ComboBox) getBoundUnit(Item.rpPreference);
    ReadPref rpEnm = ReadPref.primary;
    if (rp != null) {
      rpEnm = ReadPref.valueOf(rp.getName());
    }
    readBox.value = rpEnm.ordinal();
    if (rpObj.containsField("tags")) {
      final List tags = (List) rpObj.get("tags");
      if (tags.size() > 0) {
        ((DocBuilderField) getBoundComponentUnit(Item.rpTag)).setDBObject((DBObject) tags.get(0));
      }
    }
  }

  int getQueryOptions() {
    int options = 0;
    if (getBooleanFieldValue(Item.tailable)) {
      options |= Bytes.QUERYOPTION_TAILABLE;
    }
    if (getBooleanFieldValue(Item.slaveOk)) {
      options |= Bytes.QUERYOPTION_SLAVEOK;
    }
    if (getBooleanFieldValue(Item.opLogReplay)) {
      options |= Bytes.QUERYOPTION_OPLOGREPLAY;
    }
    if (getBooleanFieldValue(Item.noTimeout)) {
      options |= Bytes.QUERYOPTION_NOTIMEOUT;
    }
    if (getBooleanFieldValue(Item.awaitData)) {
      options |= Bytes.QUERYOPTION_AWAITDATA;
    }
    if (getBooleanFieldValue(Item.exhaust)) {
      options |= Bytes.QUERYOPTION_EXHAUST;
    }
    if (getBooleanFieldValue(Item.partial)) {
      options |= Bytes.QUERYOPTION_PARTIAL;
    }
    return options;
  }

  WriteConcern getWriteConcern() {
    final int w = getIntFieldValue(Item.writeFactor);
    final String wPolicy = getStringFieldValue(Item.writePolicy);
    final int wtimeout = getIntFieldValue(Item.writeTimeout);
    // boolean fsync = getBooleanFieldValue(Item.fsync);
    final boolean jsync = getBooleanFieldValue(Item.jsync);
    if (!wPolicy.trim().isEmpty()) {
      return new WriteConcern(wPolicy, wtimeout, false, jsync);
    }
    return new WriteConcern(w, wtimeout, false, jsync);
  }

  ReadPreference getReadPreference() {
    final ComboBox readBox = (ComboBox) getBoundUnit(Item.rpPreference);
    final ReadPref rpEnm = ReadPref.values()[readBox.value];
    final BasicDBObject tag = (BasicDBObject) ((DocBuilderField) getBoundComponentUnit(Item.rpTag)).getDBObject();
    if (tag != null) {
      return ReadPreference.valueOf(rpEnm.name(), tag);
    }
    return ReadPreference.valueOf(rpEnm.name());
  }
}
