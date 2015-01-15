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

import java.util.HashSet;

import com.edgytech.swingfast.DynamicComboBox;
import com.edgytech.swingfast.FormDialog;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

/**
 *
 * @author antoine
 */
public class TagRangeDialog extends FormDialog {

  enum Item {
    min, max, tag
  }

  public TagRangeDialog() {
    setEnumBinding(Item.values(), null);
  }

  void resetForEdit(final DB config, final BasicDBObject range) {
    xmlLoadCheckpoint();

    ((DocBuilderField) getBoundUnit(Item.min)).setDBObject((DBObject) range.get("min"));
    ((DocBuilderField) getBoundUnit(Item.min)).enabled = false;
    ((DocBuilderField) getBoundUnit(Item.max)).setDBObject((DBObject) range.get("max"));

    ((DynamicComboBox) getBoundUnit(Item.tag)).items = getExistingTags(config);
    setStringFieldValue(Item.tag, range.getString("tag"));

    updateComponent();
  }

  void resetForNew(final DB config, final String ns) {
    xmlLoadCheckpoint();

    final DBObject collection = config.getCollection("collections").findOne(new BasicDBObject("_id", ns));
    final DBObject shardKey = (DBObject) collection.get("key");
    ((DocBuilderField) getBoundUnit(Item.min)).setDBObject(shardKey);
    ((DocBuilderField) getBoundUnit(Item.max)).setDBObject(shardKey);
    ((DynamicComboBox) getBoundUnit(Item.tag)).items = getExistingTags(config);
    updateComponent();
  }

  BasicDBObject getRange(final String ns) {
    final DBObject min = ((DocBuilderField) getBoundUnit(Item.min)).getDBObject();
    final DBObject max = ((DocBuilderField) getBoundUnit(Item.max)).getDBObject();

    final BasicDBObject range = new BasicDBObject("_id", new BasicDBObject("ns", ns).append("min", min));
    range.put("ns", ns);
    range.put("min", min);
    range.put("max", max);
    range.put("tag", getStringFieldValue(Item.tag));
    return range;
  }

  static String[] getExistingTags(final DB config) {
    final DBCursor cur = config.getCollection("shards").find();
    final HashSet<String> tags = new HashSet<String>();
    while (cur.hasNext()) {
      final DBObject shard = cur.next();
      if (shard.containsField("tags")) {
        final BasicDBList list = (BasicDBList) shard.get("tags");
        for (final Object tag : list) {
          tags.add((String) tag);
        }
      }
    }
    return tags.toArray(new String[tags.size()]);
  }
}
