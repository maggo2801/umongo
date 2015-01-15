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

import com.edgytech.swingfast.PopUpMenu;
import com.edgytech.swingfast.TreeNodeLabel;
import com.mongodb.DBObject;

/**
 *
 * @author antoine
 */
public class TreeNodeDocumentField extends TreeNodeLabel {
  String key;
  Object value;

  public TreeNodeDocumentField(final String key, final Object value) {
    this.key = key;
    this.value = value;
    markStructured();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    if (key != null) {
      sb.append(key);
      if (value != null && !DBObject.class.isInstance(value)) {
        sb.append(" (").append(value.getClass().getSimpleName()).append(")");
      }
      sb.append(": ");
    }
    sb.append(MongoUtils.getObjectString(value));
    return sb.toString();
  }

  public String getKey() {
    return key;
  }

  public Object getValue() {
    return value;
  }

  @Override
  public PopUpMenu getPopUpMenu() {
    return (PopUpMenu) UMongo.instance.getGlobalStore().getBoundUnit(GlobalStore.Item.documentFieldMenu);
  }

}
