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
import com.mongodb.BasicDBList;
import com.mongodb.DBObject;

/**
 *
 * @author antoine
 */
public class DocFieldArray extends DocFieldObject {
  public DocFieldArray(final String id, final String key, final Object value, final DocFieldObject parent) {
    super(id, key, value, parent);
    getJComponentBoundUnit(Item.addKey).visible = false;
  }

  @Override
  protected DBObject createDBObject() {
    return new BasicDBList();
  }

  @Override
  protected void addField(final String key, final Object val) {
    final BasicDBList list = (BasicDBList) value;
    list.add(val);
  }

  @Override
  public void addField(final ButtonBase<?, ?> button) {
    final String type = getStringFieldValue(Item.addType);
    addNewField(null, type);
  }

}
